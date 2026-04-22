package ru.maltsev.primemarketbackend.order.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.order.api.dto.OrderConversationListResponse;
import ru.maltsev.primemarketbackend.order.api.dto.OrderConversationResponse;
import ru.maltsev.primemarketbackend.order.api.dto.OrderMessageResponse;
import ru.maltsev.primemarketbackend.order.api.dto.OrderMessagesResponse;
import ru.maltsev.primemarketbackend.order.api.dto.SendOrderMessageRequest;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.domain.OrderConversation;
import ru.maltsev.primemarketbackend.order.domain.OrderConversationParticipant;
import ru.maltsev.primemarketbackend.order.domain.OrderDispute;
import ru.maltsev.primemarketbackend.order.domain.OrderMessage;
import ru.maltsev.primemarketbackend.order.repository.OrderConversationParticipantRepository;
import ru.maltsev.primemarketbackend.order.repository.OrderConversationRepository;
import ru.maltsev.primemarketbackend.order.repository.OrderDisputeRepository;
import ru.maltsev.primemarketbackend.order.repository.OrderMessageRepository;
import ru.maltsev.primemarketbackend.order.repository.OrderRepository;
import ru.maltsev.primemarketbackend.security.PermissionCodes;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class OrderConversationService {
    private static final String MAIN_TITLE = "Order chat";
    private static final String SUPPORT_TITLE = "Support";
    private static final String MAIN_OPENED_MESSAGE = "Chat opened for this order";
    private static final String SUPPORT_OPENED_MESSAGE = "Support chat opened";
    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;
    private static final int MAX_MESSAGE_BODY_LENGTH = 4000;
    private static final List<String> ACTIVE_DISPUTE_STATUSES = List.of(
        OrderDispute.STATUS_OPEN,
        OrderDispute.STATUS_IN_REVIEW
    );

    private final OrderRepository orderRepository;
    private final OrderConversationRepository conversationRepository;
    private final OrderConversationParticipantRepository participantRepository;
    private final OrderMessageRepository messageRepository;
    private final OrderDisputeRepository orderDisputeRepository;
    private final OrderAccessService orderAccessService;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createMainConversation(Order order) {
        ensureMainConversation(order);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void ensureSupportConversationsForDispute(Order order) {
        ensureSupportConversation(
            order,
            OrderConversation.TYPE_SUPPORT_BUYER,
            orderAccessService.resolveBuyerUserId(order),
            OrderConversationParticipant.ROLE_BUYER
        );
        ensureSupportConversation(
            order,
            OrderConversation.TYPE_SUPPORT_SELLER,
            orderAccessService.resolveSellerUserId(order),
            OrderConversationParticipant.ROLE_SELLER
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void connectSupportToOrderMainConversation(Order order, Long supportUserId, String systemMessage) {
        OrderConversation mainConversation = ensureMainConversation(order);
        ensureParticipant(mainConversation, supportUserId, OrderConversationParticipant.ROLE_SUPPORT);
        createSystemMessage(mainConversation, systemMessage);
    }

    @Transactional
    public OrderConversationListResponse getOrderConversations(UUID publicOrderId, UserPrincipal principal) {
        Order order = orderRepository.findByPublicIdForUpdate(publicOrderId)
            .orElseThrow(this::orderNotFound);
        Long currentUserId = principal.getUser().getId();
        String currentUserRole = orderAccessService.resolveParticipantRole(order, currentUserId);
        boolean participant = currentUserRole != null;
        boolean supportPassiveAccess = !participant && canPassiveSupportView(order, principal);
        if (!participant && !supportPassiveAccess) {
            throw orderNotFound();
        }

        ensureMainConversation(order);

        List<String> visibleTypes = new ArrayList<>();
        visibleTypes.add(OrderConversation.TYPE_MAIN);
        if (participant) {
            String supportType = resolveSupportConversationType(currentUserRole);
            if (conversationRepository.findByOrderIdAndConversationType(order.getId(), supportType).isPresent()) {
                visibleTypes.add(supportType);
            }
        } else {
            visibleTypes.add(OrderConversation.TYPE_SUPPORT_BUYER);
            visibleTypes.add(OrderConversation.TYPE_SUPPORT_SELLER);
        }

        List<OrderConversationResponse> items = conversationRepository
            .findAllByOrderIdAndConversationTypeIn(order.getId(), visibleTypes)
            .stream()
            .sorted(Comparator.comparingInt(conversation -> typeRank(conversation.getConversationType())))
            .map(this::toConversationResponse)
            .toList();
        return new OrderConversationListResponse(items);
    }

    @Transactional(readOnly = true)
    public OrderMessagesResponse getMessages(
        UUID publicConversationId,
        UUID beforeMessageId,
        Integer requestedSize,
        UserPrincipal principal
    ) {
        OrderConversation conversation = loadAccessibleConversation(publicConversationId, principal, false);
        int size = normalizePageSize(requestedSize);
        List<OrderMessage> messages = loadMessagePage(conversation, beforeMessageId, size);
        return new OrderMessagesResponse(toMessageResponses(conversation.getId(), messages));
    }

    @Transactional
    public OrderMessageResponse sendMessage(
        UUID publicConversationId,
        UserPrincipal principal,
        SendOrderMessageRequest request
    ) {
        OrderConversation conversation = loadAccessibleConversation(publicConversationId, principal, true);
        Long currentUserId = principal.getUser().getId();
        String body = normalizeMessageBody(request);
        if (!participantRepository.existsByConversationIdAndUserId(conversation.getId(), currentUserId)) {
            ensureSupportSenderParticipant(conversation, principal);
        }

        OrderMessage message = messageRepository.saveAndFlush(new OrderMessage(
            UUID.randomUUID(),
            conversation.getId(),
            currentUserId,
            OrderMessage.TYPE_TEXT,
            body
        ));
        conversation.markLastMessageAt(message.getCreatedAt());

        return toMessageResponses(conversation.getId(), List.of(message)).get(0);
    }

    private OrderConversation ensureMainConversation(Order order) {
        OrderConversation conversation = conversationRepository
            .findByOrderIdAndConversationTypeForUpdate(order.getId(), OrderConversation.TYPE_MAIN)
            .orElseGet(() -> {
                OrderConversation created = conversationRepository.saveAndFlush(new OrderConversation(
                    UUID.randomUUID(),
                    order.getId(),
                    OrderConversation.TYPE_MAIN,
                    OrderConversation.STATUS_ACTIVE
                ));
                createSystemMessage(created, MAIN_OPENED_MESSAGE);
                return created;
            });

        ensureParticipant(
            conversation,
            orderAccessService.resolveBuyerUserId(order),
            OrderConversationParticipant.ROLE_BUYER
        );
        ensureParticipant(
            conversation,
            orderAccessService.resolveSellerUserId(order),
            OrderConversationParticipant.ROLE_SELLER
        );
        return conversation;
    }

    private OrderConversation ensureSupportConversation(
        Order order,
        String conversationType,
        Long participantUserId,
        String participantRole
    ) {
        OrderConversation conversation = conversationRepository
            .findByOrderIdAndConversationTypeForUpdate(order.getId(), conversationType)
            .orElseGet(() -> {
                OrderConversation created = conversationRepository.saveAndFlush(new OrderConversation(
                    UUID.randomUUID(),
                    order.getId(),
                    conversationType,
                    OrderConversation.STATUS_ACTIVE
                ));
                createSystemMessage(created, SUPPORT_OPENED_MESSAGE);
                return created;
            });

        ensureParticipant(conversation, participantUserId, participantRole);
        return conversation;
    }

    private void ensureParticipant(OrderConversation conversation, Long userId, String participantRole) {
        if (participantRepository.existsByConversationIdAndUserId(conversation.getId(), userId)) {
            return;
        }
        participantRepository.save(new OrderConversationParticipant(
            conversation.getId(),
            userId,
            participantRole
        ));
    }

    private void createSystemMessage(OrderConversation conversation, String body) {
        messageRepository.save(new OrderMessage(
            UUID.randomUUID(),
            conversation.getId(),
            null,
            OrderMessage.TYPE_SYSTEM,
            body
        ));
    }

    private OrderConversation loadAccessibleConversation(
        UUID publicConversationId,
        UserPrincipal principal,
        boolean forSend
    ) {
        OrderConversation conversation = (forSend
            ? conversationRepository.findByPublicIdForUpdate(publicConversationId)
            : conversationRepository.findByPublicId(publicConversationId))
            .orElseThrow(this::conversationNotFound);
        ensureConversationAccess(conversation, principal, forSend);
        return conversation;
    }

    private void ensureConversationAccess(OrderConversation conversation, UserPrincipal principal, boolean forSend) {
        Long userId = principal.getUser().getId();
        if (participantRepository.existsByConversationIdAndUserId(conversation.getId(), userId)) {
            return;
        }
        if (!principal.hasAuthority(PermissionCodes.ORDER_CHATS_VIEW_ANY)) {
            throw conversationNotFound();
        }
        if (!orderDisputeRepository.existsByOrderId(conversation.getOrderId())) {
            throw conversationNotFound();
        }
        if (!forSend) {
            return;
        }
        if (!principal.hasAuthority(PermissionCodes.ORDER_CHATS_SEND_AS_SUPPORT)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                "ORDER_CHAT_SEND_FORBIDDEN",
                "Current user cannot send support chat messages"
            );
        }

        OrderDispute activeDispute = orderDisputeRepository
            .findTopByOrderIdAndStatusInOrderByCreatedAtDescIdDesc(
                conversation.getOrderId(),
                ACTIVE_DISPUTE_STATUSES
            )
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_DISPUTE_NOT_ACTIVE",
                "Support chat messages are available only while dispute is active"
            ));
        if (OrderConversation.TYPE_MAIN.equals(conversation.getConversationType())
            && !userId.equals(activeDispute.getAssignedSupportUserId())) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_DISPUTE_TAKE_REQUIRED",
                "Take dispute in work before sending messages to the main order chat"
            );
        }
    }

    private void ensureSupportSenderParticipant(OrderConversation conversation, UserPrincipal principal) {
        if (OrderConversation.TYPE_MAIN.equals(conversation.getConversationType())) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_DISPUTE_TAKE_REQUIRED",
                "Take dispute in work before sending messages to the main order chat"
            );
        }
        ensureParticipant(
            conversation,
            principal.getUser().getId(),
            OrderConversationParticipant.ROLE_SUPPORT
        );
    }

    private List<OrderMessage> loadMessagePage(
        OrderConversation conversation,
        UUID beforeMessageId,
        int size
    ) {
        PageRequest pageRequest = PageRequest.of(0, size);
        List<OrderMessage> newestFirst;
        if (beforeMessageId == null) {
            newestFirst = messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                conversation.getId(),
                pageRequest
            );
        } else {
            OrderMessage anchor = messageRepository
                .findByPublicIdAndConversationId(beforeMessageId, conversation.getId())
                .orElseThrow(this::messageNotFound);
            newestFirst = messageRepository.findBefore(
                conversation.getId(),
                anchor.getCreatedAt(),
                anchor.getId(),
                pageRequest
            );
        }

        List<OrderMessage> oldestFirst = new ArrayList<>(newestFirst);
        oldestFirst.sort(Comparator
            .comparing(OrderMessage::getCreatedAt)
            .thenComparing(OrderMessage::getId));
        return oldestFirst;
    }

    private List<OrderMessageResponse> toMessageResponses(Long conversationId, List<OrderMessage> messages) {
        Set<Long> senderUserIds = messages.stream()
            .map(OrderMessage::getSenderUserId)
            .filter(userId -> userId != null)
            .collect(Collectors.toSet());
        Map<Long, User> usersById = new HashMap<>();
        userRepository.findAllById(senderUserIds)
            .forEach(user -> usersById.put(user.getId(), user));
        Map<Long, String> rolesByUserId = participantRepository.findAllByConversationId(conversationId)
            .stream()
            .collect(Collectors.toMap(
                OrderConversationParticipant::getUserId,
                OrderConversationParticipant::getParticipantRole
            ));

        return messages.stream()
            .map(message -> toMessageResponse(message, usersById, rolesByUserId))
            .toList();
    }

    private OrderMessageResponse toMessageResponse(
        OrderMessage message,
        Map<Long, User> usersById,
        Map<Long, String> rolesByUserId
    ) {
        OrderMessageResponse.Sender sender = null;
        if (message.getSenderUserId() != null) {
            User user = usersById.get(message.getSenderUserId());
            sender = new OrderMessageResponse.Sender(
                message.getSenderUserId(),
                rolesByUserId.get(message.getSenderUserId()),
                user == null ? null : user.getUsername()
            );
        }

        return new OrderMessageResponse(
            message.getPublicId(),
            message.getMessageType(),
            message.getBody(),
            sender,
            message.getCreatedAt()
        );
    }

    private OrderConversationResponse toConversationResponse(OrderConversation conversation) {
        return new OrderConversationResponse(
            conversation.getPublicId(),
            conversation.getConversationType(),
            titleFor(conversation.getConversationType()),
            conversation.getLastMessageAt(),
            messageRepository.existsByConversationIdAndMessageTypeAndDeletedAtIsNull(
                conversation.getId(),
                OrderMessage.TYPE_TEXT
            )
        );
    }

    private String normalizeMessageBody(SendOrderMessageRequest request) {
        String body = request == null ? null : request.body();
        if (body == null || body.trim().isEmpty()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_MESSAGE_BODY",
                "Message body must not be empty"
            );
        }
        String normalized = body.trim();
        if (normalized.length() > MAX_MESSAGE_BODY_LENGTH) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "MESSAGE_BODY_TOO_LONG",
                "Message body must be at most " + MAX_MESSAGE_BODY_LENGTH + " characters"
            );
        }
        return normalized;
    }

    private int normalizePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        if (size <= 0 || size > MAX_MESSAGE_PAGE_SIZE) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_PAGE_SIZE",
                "Query parameter 'size' must be between 1 and " + MAX_MESSAGE_PAGE_SIZE
            );
        }
        return size;
    }

    private boolean canPassiveSupportView(Order order, UserPrincipal principal) {
        return principal.hasAuthority(PermissionCodes.ORDER_CHATS_VIEW_ANY)
            && orderDisputeRepository.existsByOrderId(order.getId());
    }

    private String resolveSupportConversationType(String participantRole) {
        String normalized = participantRole.toLowerCase(Locale.ROOT);
        if (OrderConversationParticipant.ROLE_BUYER.equals(normalized)) {
            return OrderConversation.TYPE_SUPPORT_BUYER;
        }
        if (OrderConversationParticipant.ROLE_SELLER.equals(normalized)) {
            return OrderConversation.TYPE_SUPPORT_SELLER;
        }
        throw conversationNotFound();
    }

    private int typeRank(String conversationType) {
        if (OrderConversation.TYPE_MAIN.equals(conversationType)) {
            return 0;
        }
        if (OrderConversation.TYPE_SUPPORT_BUYER.equals(conversationType)) {
            return 1;
        }
        if (OrderConversation.TYPE_SUPPORT_SELLER.equals(conversationType)) {
            return 2;
        }
        return 3;
    }

    private String titleFor(String conversationType) {
        if (OrderConversation.TYPE_MAIN.equals(conversationType)) {
            return MAIN_TITLE;
        }
        return SUPPORT_TITLE;
    }

    private ApiProblemException orderNotFound() {
        return new ApiProblemException(
            HttpStatus.NOT_FOUND,
            "ORDER_NOT_FOUND",
            "Order not found"
        );
    }

    private ApiProblemException conversationNotFound() {
        return new ApiProblemException(
            HttpStatus.NOT_FOUND,
            "ORDER_CONVERSATION_NOT_FOUND",
            "Order conversation not found"
        );
    }

    private ApiProblemException messageNotFound() {
        return new ApiProblemException(
            HttpStatus.NOT_FOUND,
            "ORDER_MESSAGE_NOT_FOUND",
            "Order message not found"
        );
    }
}

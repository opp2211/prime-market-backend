package ru.maltsev.primemarketbackend.order.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.notification.service.NotificationService;
import ru.maltsev.primemarketbackend.order.api.dto.BackofficeDisputesResponse;
import ru.maltsev.primemarketbackend.order.api.dto.CreateOrderDisputeRequest;
import ru.maltsev.primemarketbackend.order.api.dto.OrderDisputeDtos;
import ru.maltsev.primemarketbackend.order.api.dto.OrderDisputeResponse;
import ru.maltsev.primemarketbackend.order.api.dto.OrderReadModelDtos;
import ru.maltsev.primemarketbackend.order.api.dto.ResolveOrderDisputeAmendQuantityRequest;
import ru.maltsev.primemarketbackend.order.api.dto.ResolveOrderDisputeRequest;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.domain.OrderDispute;
import ru.maltsev.primemarketbackend.order.domain.OrderRequest;
import ru.maltsev.primemarketbackend.order.repository.OrderDisputeRepository;
import ru.maltsev.primemarketbackend.order.repository.OrderRepository;
import ru.maltsev.primemarketbackend.order.repository.OrderRequestRepository;
import ru.maltsev.primemarketbackend.security.PermissionCodes;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class OrderDisputeService {
    private static final List<String> ACTIVE_DISPUTE_STATUSES = List.of(
        OrderDispute.STATUS_OPEN,
        OrderDispute.STATUS_IN_REVIEW
    );
    private static final List<String> BACKOFFICE_QUEUE_STATUSES = List.of(
        OrderDispute.STATUS_OPEN,
        OrderDispute.STATUS_IN_REVIEW,
        OrderDispute.STATUS_RESOLVED
    );
    private static final String SUPPORT_JOINED_ORDER_MESSAGE = "Поддержка подключилась к заказу";

    private final OrderRepository orderRepository;
    private final OrderDisputeRepository orderDisputeRepository;
    private final OrderRequestRepository orderRequestRepository;
    private final OrderAccessService orderAccessService;
    private final OrderConversationService orderConversationService;
    private final OrderLifecycleService orderLifecycleService;
    private final OrderSettlementService orderSettlementService;
    private final OrderQuantityAmendService orderQuantityAmendService;
    private final OrderEventWriteService orderEventWriteService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public OrderDisputeResponse openDispute(
        UUID publicOrderId,
        UserPrincipal principal,
        CreateOrderDisputeRequest request
    ) {
        Order order = orderRepository.findByPublicIdForUpdate(publicOrderId)
            .orElseThrow(this::orderNotFound);
        Long currentUserId = principal.getUser().getId();
        if (!orderAccessService.isParticipant(order, currentUserId)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                "ONLY_ORDER_PARTICIPANT_CAN_OPEN_DISPUTE",
                "Only order participant can open a dispute"
            );
        }
        if (!isDisputeEligibleOrderStatus(order)) {
            throw invalidOrderStatus("Dispute cannot be opened from status " + order.getStatus());
        }
        if (orderDisputeRepository.existsByOrderIdAndStatusIn(order.getId(), ACTIVE_DISPUTE_STATUSES)) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_DISPUTE_ALREADY_ACTIVE",
                "An active dispute already exists for this order"
            );
        }

        String openedByRole = orderAccessService.resolveParticipantRole(order, currentUserId);
        OrderDispute dispute = orderDisputeRepository.saveAndFlush(new OrderDispute(
            UUID.randomUUID(),
            order.getId(),
            currentUserId,
            openedByRole,
            normalizeRequired(request.reasonCode(), "reasonCode"),
            normalizeRequired(request.description(), "description")
        ));
        orderConversationService.ensureSupportConversationsForDispute(order);
        orderEventWriteService.recordDisputeOpened(
            order,
            dispute.getId(),
            currentUserId,
            openedByRole,
            dispute.getReasonCode()
        );
        notificationService.notifyDisputeOpened(order, dispute, resolveCounterpartyUserId(order, currentUserId));
        return toResponse(dispute, order, principal);
    }

    @Transactional(readOnly = true)
    public OrderDisputeResponse getOrderDispute(UUID publicOrderId, UserPrincipal principal) {
        Order order = orderAccessService.loadOrder(publicOrderId);
        if (!canViewDispute(order, principal)) {
            throw orderNotFound();
        }
        OrderDispute dispute = orderDisputeRepository.findTopByOrderIdOrderByCreatedAtDescIdDesc(order.getId())
            .orElseThrow(this::disputeNotFound);
        return toResponse(dispute, order, principal);
    }

    @Transactional(readOnly = true)
    public OrderReadModelDtos.Dispute buildOrderDisputeBlock(Order order, UserPrincipal principal) {
        boolean canViewDispute = canViewDispute(order, principal);
        OrderDispute latestDispute = canViewDispute
            ? orderDisputeRepository.findTopByOrderIdOrderByCreatedAtDescIdDesc(order.getId()).orElse(null)
            : null;
        OrderDispute activeDispute = orderDisputeRepository
            .findTopByOrderIdAndStatusInOrderByCreatedAtDescIdDesc(order.getId(), ACTIVE_DISPUTE_STATUSES)
            .orElse(null);

        boolean canOpenDispute = orderAccessService.isParticipant(order, principal.getUser().getId())
            && isDisputeEligibleOrderStatus(order)
            && activeDispute == null;
        if (latestDispute == null) {
            return new OrderReadModelDtos.Dispute(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new OrderReadModelDtos.DisputeAvailableActions(
                    canOpenDispute,
                    false,
                    false,
                    false,
                    false
                )
            );
        }

        if (!canViewDispute) {
            return new OrderReadModelDtos.Dispute(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new OrderReadModelDtos.DisputeAvailableActions(
                    false,
                    false,
                    false,
                    false,
                    false
                )
            );
        }

        boolean canTakeDisputeInWork = canTakeDisputeInWork(latestDispute, principal);
        boolean canResolveDispute = canResolveDispute(latestDispute, order, principal);
        return new OrderReadModelDtos.Dispute(
            true,
            latestDispute.getPublicId(),
            latestDispute.getStatus(),
            latestDispute.getReasonCode(),
            latestDispute.getDescription(),
            latestDispute.getOpenedByRole(),
            latestDispute.getCreatedAt(),
            toReadUserSummary(loadUsersById(Arrays.asList(latestDispute.getAssignedSupportUserId()))
                .get(latestDispute.getAssignedSupportUserId())),
            new OrderReadModelDtos.DisputeAvailableActions(
                canOpenDispute,
                canTakeDisputeInWork,
                canResolveDispute,
                canResolveDispute,
                canResolveDispute
            )
        );
    }

    @Transactional
    public OrderDisputeResponse takeDispute(UUID publicDisputeId, UserPrincipal principal) {
        OrderDispute dispute = loadDisputeForUpdate(publicDisputeId);
        if (!dispute.isOpen()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_DISPUTE_TAKE_NOT_ALLOWED",
                "Only open disputes can be taken in work"
            );
        }

        Order order = loadOrderForUpdate(dispute.getOrderId());
        Instant now = Instant.now();
        dispute.markTakenInWork(principal.getUser().getId(), now);
        orderConversationService.connectSupportToOrderMainConversation(
            order,
            principal.getUser().getId(),
            SUPPORT_JOINED_ORDER_MESSAGE
        );
        orderEventWriteService.recordDisputeTakenInWork(order, dispute.getId(), principal.getUser().getId());
        notificationService.notifyDisputeTakenInWork(order, dispute, resolveParticipantUserIds(order));
        return toResponse(dispute, order, principal);
    }

    @Transactional
    public OrderDisputeResponse resolveCancel(
        UUID publicDisputeId,
        UserPrincipal principal,
        ResolveOrderDisputeRequest request
    ) {
        ResolutionContext context = prepareResolution(
            publicDisputeId,
            request == null ? null : request.note()
        );
        Instant now = Instant.now();
        orderLifecycleService.cancelOrderAndReleaseResources(context.order(), now);
        cancelPendingRequests(context.order().getId(), principal.getUser().getId(), now);
        context.dispute().markResolved(
            principal.getUser().getId(),
            now,
            OrderDispute.RESOLUTION_FORCE_CANCEL,
            context.resolutionNote()
        );
        orderEventWriteService.recordOrderCanceled(context.order(), principal.getUser().getId(), OrderDispute.ROLE_SUPPORT);
        orderEventWriteService.recordDisputeResolved(
            context.order(),
            context.dispute().getId(),
            principal.getUser().getId(),
            OrderDispute.RESOLUTION_FORCE_CANCEL
        );
        orderEventWriteService.recordOrderForceCanceledBySupport(context.order(), principal.getUser().getId());
        notificationService.notifyOrderStatusChanged(context.order(), resolveParticipantUserIds(context.order()));
        return toResponse(context.dispute(), context.order(), principal);
    }

    @Transactional
    public OrderDisputeResponse resolveComplete(
        UUID publicDisputeId,
        UserPrincipal principal,
        ResolveOrderDisputeRequest request
    ) {
        ResolutionContext context = prepareResolution(
            publicDisputeId,
            request == null ? null : request.note()
        );
        Instant now = Instant.now();
        context.order().markCompleted();
        orderSettlementService.settleCompletedOrder(context.order());
        cancelPendingRequests(context.order().getId(), principal.getUser().getId(), now);
        context.dispute().markResolved(
            principal.getUser().getId(),
            now,
            OrderDispute.RESOLUTION_FORCE_COMPLETE,
            context.resolutionNote()
        );
        orderEventWriteService.recordOrderCompleted(context.order(), principal.getUser().getId(), OrderDispute.ROLE_SUPPORT);
        orderEventWriteService.recordDisputeResolved(
            context.order(),
            context.dispute().getId(),
            principal.getUser().getId(),
            OrderDispute.RESOLUTION_FORCE_COMPLETE
        );
        orderEventWriteService.recordOrderForceCompletedBySupport(context.order(), principal.getUser().getId());
        notificationService.notifyOrderStatusChanged(context.order(), resolveParticipantUserIds(context.order()));
        return toResponse(context.dispute(), context.order(), principal);
    }

    @Transactional
    public OrderDisputeResponse resolveAmendQuantityAndComplete(
        UUID publicDisputeId,
        UserPrincipal principal,
        ResolveOrderDisputeAmendQuantityRequest request
    ) {
        ResolutionContext context = prepareResolution(
            publicDisputeId,
            request == null ? null : request.note()
        );
        if (request == null) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request body is required"
            );
        }

        OrderQuantityAmendService.AmendResult amendResult = orderQuantityAmendService.amendQuantity(
            context.order(),
            request.quantity()
        );
        Instant now = Instant.now();
        context.order().markCompleted();
        orderSettlementService.settleCompletedOrder(context.order());
        cancelPendingRequests(context.order().getId(), principal.getUser().getId(), now);
        context.dispute().markResolved(
            principal.getUser().getId(),
            now,
            OrderDispute.RESOLUTION_FORCE_AMEND_QUANTITY_AND_COMPLETE,
            context.resolutionNote()
        );
        orderEventWriteService.recordOrderForceAmendedQuantityBySupport(
            context.order(),
            principal.getUser().getId(),
            amendResult.previousQuantity()
        );
        orderEventWriteService.recordOrderCompleted(context.order(), principal.getUser().getId(), OrderDispute.ROLE_SUPPORT);
        orderEventWriteService.recordDisputeResolved(
            context.order(),
            context.dispute().getId(),
            principal.getUser().getId(),
            OrderDispute.RESOLUTION_FORCE_AMEND_QUANTITY_AND_COMPLETE
        );
        orderEventWriteService.recordOrderForceCompletedBySupport(context.order(), principal.getUser().getId());
        notificationService.notifyOrderStatusChanged(context.order(), resolveParticipantUserIds(context.order()));
        return toResponse(context.dispute(), context.order(), principal);
    }

    @Transactional(readOnly = true)
    public BackofficeDisputesResponse getBackofficeDisputes(UserPrincipal principal) {
        List<OrderDispute> disputes = orderDisputeRepository.findAllByStatusInOrderByCreatedAtDescIdDesc(
            BACKOFFICE_QUEUE_STATUSES
        );
        Map<Long, Order> ordersById = new LinkedHashMap<>();
        orderRepository.findAllById(disputes.stream().map(OrderDispute::getOrderId).toList())
            .forEach(order -> ordersById.put(order.getId(), order));

        List<OrderDisputeResponse> open = disputes.stream()
            .filter(dispute -> OrderDispute.STATUS_OPEN.equals(dispute.getStatus()))
            .map(dispute -> toResponse(dispute, ordersById.get(dispute.getOrderId()), principal))
            .toList();
        List<OrderDisputeResponse> inReview = disputes.stream()
            .filter(dispute -> OrderDispute.STATUS_IN_REVIEW.equals(dispute.getStatus()))
            .map(dispute -> toResponse(dispute, ordersById.get(dispute.getOrderId()), principal))
            .toList();
        List<OrderDisputeResponse> resolved = disputes.stream()
            .filter(dispute -> OrderDispute.STATUS_RESOLVED.equals(dispute.getStatus()))
            .map(dispute -> toResponse(dispute, ordersById.get(dispute.getOrderId()), principal))
            .toList();
        return new BackofficeDisputesResponse(open, inReview, resolved);
    }

    private ResolutionContext prepareResolution(
        UUID publicDisputeId,
        String resolutionNote
    ) {
        OrderDispute dispute = loadDisputeForUpdate(publicDisputeId);
        if (!dispute.isActive()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_DISPUTE_NOT_ACTIVE",
                "Only active disputes can be resolved"
            );
        }
        Order order = loadOrderForUpdate(dispute.getOrderId());
        if (!isDisputeEligibleOrderStatus(order)) {
            throw invalidOrderStatus("Order cannot be resolved from status " + order.getStatus());
        }
        return new ResolutionContext(dispute, order, normalizeOptional(resolutionNote));
    }

    private Long resolveCounterpartyUserId(Order order, Long actorUserId) {
        if (order.getMakerUserId().equals(actorUserId)) {
            return order.getTakerUserId();
        }
        if (order.getTakerUserId().equals(actorUserId)) {
            return order.getMakerUserId();
        }
        throw invalidOrderStatus("Order counterparty is not defined");
    }

    private List<Long> resolveParticipantUserIds(Order order) {
        return List.of(order.getMakerUserId(), order.getTakerUserId());
    }

    private OrderDispute loadDisputeForUpdate(UUID publicDisputeId) {
        return orderDisputeRepository.findByPublicIdForUpdate(publicDisputeId)
            .orElseThrow(this::disputeNotFound);
    }

    private Order loadOrderForUpdate(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(this::orderNotFound);
    }

    private boolean canViewDispute(Order order, UserPrincipal principal) {
        return orderAccessService.isParticipant(order, principal.getUser().getId())
            || principal.hasAuthority(PermissionCodes.ORDER_DISPUTES_VIEW);
    }

    private boolean isDisputeEligibleOrderStatus(Order order) {
        return order.isInProgress() || order.isPartiallyDelivered() || order.isDelivered();
    }

    private boolean canTakeDisputeInWork(OrderDispute dispute, UserPrincipal principal) {
        return dispute.isOpen() && principal.hasAuthority(PermissionCodes.ORDER_DISPUTES_TAKE);
    }

    private boolean canResolveDispute(OrderDispute dispute, Order order, UserPrincipal principal) {
        return dispute.isActive()
            && isDisputeEligibleOrderStatus(order)
            && principal.hasAuthority(PermissionCodes.ORDER_DISPUTES_RESOLVE);
    }

    private void cancelPendingRequests(Long orderId, Long actorUserId, Instant now) {
        for (OrderRequest request : orderRequestRepository.findAllByOrderIdAndStatusOrderByCreatedAtDesc(
            orderId,
            OrderRequest.STATUS_PENDING
        )) {
            request.markCanceled(actorUserId, now);
        }
    }

    private OrderDisputeResponse toResponse(OrderDispute dispute, Order order, UserPrincipal principal) {
        Map<Long, User> usersById = loadUsersById(Arrays.asList(
            dispute.getAssignedSupportUserId(),
            dispute.getResolvedByUserId()
        ));
        boolean canTakeDisputeInWork = canTakeDisputeInWork(dispute, principal);
        boolean canResolveDispute = canResolveDispute(dispute, order, principal);
        return new OrderDisputeResponse(
            dispute.getPublicId(),
            new OrderDisputeDtos.OrderSummary(
                order.getId(),
                order.getPublicId(),
                order.getStatus(),
                order.getTitleSnapshot(),
                orderAccessService.resolveBuyerUsername(order),
                orderAccessService.resolveSellerUsername(order)
            ),
            dispute.getStatus(),
            dispute.getReasonCode(),
            dispute.getDescription(),
            dispute.getOpenedByUserId(),
            dispute.getOpenedByRole(),
            toUserSummary(usersById.get(dispute.getAssignedSupportUserId())),
            dispute.getTakenAt(),
            dispute.getCreatedAt(),
            dispute.getUpdatedAt(),
            dispute.getResolvedAt(),
            toUserSummary(usersById.get(dispute.getResolvedByUserId())),
            dispute.getResolutionType(),
            dispute.getResolutionNote(),
            new OrderDisputeDtos.AvailableActions(
                canTakeDisputeInWork,
                canResolveDispute,
                canResolveDispute,
                canResolveDispute
            )
        );
    }

    private Map<Long, User> loadUsersById(Collection<Long> userIds) {
        Set<Long> normalizedIds = userIds.stream()
            .filter(userId -> userId != null)
            .collect(java.util.stream.Collectors.toSet());
        Map<Long, User> usersById = new HashMap<>();
        userRepository.findAllById(normalizedIds).forEach(user -> usersById.put(user.getId(), user));
        return usersById;
    }

    private OrderDisputeDtos.UserSummary toUserSummary(User user) {
        if (user == null) {
            return null;
        }
        return new OrderDisputeDtos.UserSummary(user.getId(), user.getUsername());
    }

    private OrderReadModelDtos.UserSummary toReadUserSummary(User user) {
        if (user == null) {
            return null;
        }
        return new OrderReadModelDtos.UserSummary(user.getId(), user.getUsername());
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Field '" + fieldName + "' is required"
            );
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    private ApiProblemException orderNotFound() {
        return new ApiProblemException(
            HttpStatus.NOT_FOUND,
            "ORDER_NOT_FOUND",
            "Order not found"
        );
    }

    private ApiProblemException disputeNotFound() {
        return new ApiProblemException(
            HttpStatus.NOT_FOUND,
            "ORDER_DISPUTE_NOT_FOUND",
            "Order dispute not found"
        );
    }

    private ApiProblemException invalidOrderStatus(String message) {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            "INVALID_ORDER_STATUS",
            message
        );
    }

    private record ResolutionContext(
        OrderDispute dispute,
        Order order,
        String resolutionNote
    ) {
    }
}

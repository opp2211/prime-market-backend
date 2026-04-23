package ru.maltsev.primemarketbackend.notification.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.notification.api.dto.NotificationResponse;
import ru.maltsev.primemarketbackend.notification.domain.Notification;
import ru.maltsev.primemarketbackend.notification.domain.NotificationTypes;
import ru.maltsev.primemarketbackend.notification.repository.NotificationRepository;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.domain.OrderConversation;
import ru.maltsev.primemarketbackend.order.domain.OrderDispute;
import ru.maltsev.primemarketbackend.order.domain.OrderRequest;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequest;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForUser(Long userId, Boolean isRead, Pageable pageable) {
        Page<Notification> page = isRead == null
            ? notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
            : notificationRepository.findAllByUserIdAndIsReadOrderByCreatedAtDesc(userId, isRead, pageable);
        return page.map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, UUID publicId) {
        Notification notification = notificationRepository.findByPublicIdAndUserIdForUpdate(publicId, userId)
            .orElseThrow(this::notificationNotFound);
        notification.markRead(Instant.now());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public long markAllRead(Long userId) {
        return notificationRepository.markAllAsRead(userId, Instant.now());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyOrderCreated(Order order) {
        create(
            order.getMakerUserId(),
            NotificationTypes.ORDER_CREATED,
            "Новый ордер",
            "По вашему офферу создан новый ордер.",
            orderPayload(order)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyOrderStatusChanged(Order order, Collection<Long> recipientUserIds) {
        createForRecipients(
            recipientUserIds,
            NotificationTypes.ORDER_STATUS_CHANGED,
            "Статус ордера изменён",
            orderStatusBody(order.getStatus()),
            orderPayload(order)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyOrderMessageReceived(
        Order order,
        OrderConversation conversation,
        Collection<Long> recipientUserIds
    ) {
        createForRecipients(
            recipientUserIds,
            NotificationTypes.ORDER_MESSAGE_RECEIVED,
            "Новое сообщение",
            "У вас новое сообщение по ордеру.",
            conversationPayload(order, conversation)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyOrderRequestCreated(Order order, OrderRequest request, Long recipientUserId) {
        create(
            recipientUserId,
            NotificationTypes.ORDER_REQUEST_CREATED,
            "Новый запрос по ордеру",
            orderRequestCreatedBody(request),
            orderRequestPayload(order, request)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyOrderRequestResolved(Order order, OrderRequest request) {
        create(
            request.getRequestedByUserId(),
            NotificationTypes.ORDER_REQUEST_RESOLVED,
            "Запрос по ордеру обработан",
            orderRequestResolvedBody(request),
            orderRequestPayload(order, request)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyDisputeOpened(Order order, OrderDispute dispute, Long recipientUserId) {
        create(
            recipientUserId,
            NotificationTypes.DISPUTE_OPENED,
            "Открыт спор",
            "Контрагент открыл спор по ордеру.",
            disputePayload(order, dispute)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyDisputeTakenInWork(Order order, OrderDispute dispute, Collection<Long> recipientUserIds) {
        createForRecipients(
            recipientUserIds,
            NotificationTypes.DISPUTE_TAKEN_IN_WORK,
            "Спор взят в работу",
            "Поддержка взяла спор по ордеру в работу.",
            disputePayload(order, dispute)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyDepositConfirmed(DepositRequest request) {
        create(
            request.getUserId(),
            NotificationTypes.DEPOSIT_CONFIRMED,
            "Пополнение подтверждено",
            "Пополнение на %s %s подтверждено.".formatted(
                formatAmount(request.getAmount()),
                request.getCurrencyCodeSnapshot()
            ),
            depositPayload(request)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyDepositRejected(DepositRequest request) {
        create(
            request.getUserId(),
            NotificationTypes.DEPOSIT_REJECTED,
            "Пополнение отклонено",
            "Пополнение на %s %s отклонено.".formatted(
                formatAmount(request.getAmount()),
                request.getCurrencyCodeSnapshot()
            ),
            depositPayload(request)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyWithdrawalCompleted(WithdrawalRequest request) {
        create(
            request.getUserId(),
            NotificationTypes.WITHDRAWAL_COMPLETED,
            "Вывод подтверждён",
            "Вывод на %s %s завершён.".formatted(
                formatAmount(request.getAmount()),
                request.getCurrencyCodeSnapshot()
            ),
            withdrawalPayload(request)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyWithdrawalRejected(WithdrawalRequest request) {
        create(
            request.getUserId(),
            NotificationTypes.WITHDRAWAL_REJECTED,
            "Вывод отклонён",
            "Вывод на %s %s отклонён.".formatted(
                formatAmount(request.getAmount()),
                request.getCurrencyCodeSnapshot()
            ),
            withdrawalPayload(request)
        );
    }

    private void createForRecipients(
        Collection<Long> recipientUserIds,
        String type,
        String title,
        String body,
        ObjectNode payload
    ) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }

        Set<Long> uniqueRecipients = new LinkedHashSet<>(recipientUserIds);
        uniqueRecipients.remove(null);
        for (Long recipientUserId : uniqueRecipients) {
            create(recipientUserId, type, title, body, payload);
        }
    }

    private void create(Long userId, String type, String title, String body, ObjectNode payload) {
        if (userId == null) {
            return;
        }
        notificationRepository.save(new Notification(
            UUID.randomUUID(),
            userId,
            type,
            title,
            body,
            payload
        ));
    }

    private ObjectNode orderPayload(Order order) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("orderPublicId", order.getPublicId().toString());
        return payload;
    }

    private ObjectNode conversationPayload(Order order, OrderConversation conversation) {
        ObjectNode payload = orderPayload(order);
        payload.put("conversationPublicId", conversation.getPublicId().toString());
        return payload;
    }

    private ObjectNode orderRequestPayload(Order order, OrderRequest request) {
        ObjectNode payload = orderPayload(order);
        payload.put("orderRequestPublicId", request.getPublicId().toString());
        return payload;
    }

    private ObjectNode disputePayload(Order order, OrderDispute dispute) {
        ObjectNode payload = orderPayload(order);
        payload.put("disputePublicId", dispute.getPublicId().toString());
        return payload;
    }

    private ObjectNode depositPayload(DepositRequest request) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("depositRequestPublicId", request.getPublicId().toString());
        return payload;
    }

    private ObjectNode withdrawalPayload(WithdrawalRequest request) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("withdrawalRequestPublicId", request.getPublicId().toString());
        return payload;
    }

    private String orderStatusBody(String status) {
        return switch (status) {
            case "in_progress" -> "Контрагент подтвердил готовность по ордеру.";
            case "partially_delivered" -> "Контрагент отметил частичную доставку по ордеру.";
            case "delivered" -> "Контрагент отметил ордер как доставленный.";
            case "completed" -> "Ордер завершён.";
            case "canceled" -> "Ордер отменён.";
            default -> "Статус ордера изменён.";
        };
    }

    private String orderRequestCreatedBody(OrderRequest request) {
        return switch (request.getRequestType()) {
            case OrderRequest.TYPE_CANCEL -> "Контрагент создал запрос на отмену ордера.";
            case OrderRequest.TYPE_AMEND_QUANTITY -> "Контрагент создал запрос на изменение количества в ордере.";
            default -> "Контрагент создал новый запрос по ордеру.";
        };
    }

    private String orderRequestResolvedBody(OrderRequest request) {
        boolean approved = OrderRequest.STATUS_APPROVED.equals(request.getStatus());
        return switch (request.getRequestType()) {
            case OrderRequest.TYPE_CANCEL -> approved
                ? "Ваш запрос на отмену ордера одобрен."
                : "Ваш запрос на отмену ордера отклонён.";
            case OrderRequest.TYPE_AMEND_QUANTITY -> approved
                ? "Ваш запрос на изменение количества в ордере одобрен."
                : "Ваш запрос на изменение количества в ордере отклонён.";
            default -> approved
                ? "Ваш запрос по ордеру одобрен."
                : "Ваш запрос по ордеру отклонён.";
        };
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "0" : amount.toPlainString();
    }

    private ApiProblemException notificationNotFound() {
        return new ApiProblemException(
            HttpStatus.NOT_FOUND,
            "NOTIFICATION_NOT_FOUND",
            "Notification not found"
        );
    }
}

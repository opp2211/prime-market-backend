package ru.maltsev.primemarketbackend.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.domain.OrderEvent;
import ru.maltsev.primemarketbackend.order.repository.OrderEventRepository;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderEventWriteService {
    private static final String EVENT_ORDER_CREATED = "order_created";
    private static final String EVENT_MAKER_CONFIRMED_READY = "maker_confirmed_ready";
    private static final String EVENT_ORDER_CANCELED = "order_canceled";
    private static final String EVENT_ORDER_EXPIRED = "order_expired";
    private static final String EVENT_SELLER_MARKED_PARTIAL_DELIVERY = "seller_marked_partial_delivery";
    private static final String EVENT_SELLER_MARKED_DELIVERED = "seller_marked_delivered";
    private static final String EVENT_BUYER_CONFIRMED_RECEIVED = "buyer_confirmed_received";
    private static final String EVENT_ORDER_COMPLETED = "order_completed";
    private static final String EVENT_CANCEL_REQUESTED = "cancel_requested";
    private static final String EVENT_CANCEL_REQUEST_APPROVED = "cancel_request_approved";
    private static final String EVENT_CANCEL_REQUEST_REJECTED = "cancel_request_rejected";
    private static final String EVENT_AMEND_QUANTITY_REQUESTED = "amend_quantity_requested";
    private static final String EVENT_AMEND_QUANTITY_APPROVED = "amend_quantity_approved";
    private static final String EVENT_AMEND_QUANTITY_REJECTED = "amend_quantity_rejected";
    private static final String ACTOR_ROLE_SYSTEM = "system";
    private static final String ACTOR_ROLE_SELLER = "seller";
    private static final String ACTOR_ROLE_BUYER = "buyer";

    private final OrderEventRepository orderEventRepository;

    public void recordOrderCreated(Order order, Long actorUserId, String actorRole) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("orderedQuantity", order.getOrderedQuantity());
        payload.put("displayTotalAmount", order.getDisplayTotalAmount());
        payload.put("viewerCurrencyCode", order.getViewerCurrencyCodeSnapshot());
        append(order.getId(), EVENT_ORDER_CREATED, actorUserId, actorRole, payload);
    }

    public void recordMakerConfirmedReady(Order order) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("confirmedByRole", order.getMakerRole());
        append(
            order.getId(),
            EVENT_MAKER_CONFIRMED_READY,
            order.getMakerUserId(),
            order.getMakerRole(),
            payload
        );
    }

    public void recordOrderCanceled(Order order, Long actorUserId, String actorRole) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        if (actorRole != null) {
            payload.put("canceledByRole", actorRole);
        }
        append(order.getId(), EVENT_ORDER_CANCELED, actorUserId, actorRole, payload);
    }

    public void recordOrderExpired(Order order) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("reason", "pending_timeout");
        append(order.getId(), EVENT_ORDER_EXPIRED, null, ACTOR_ROLE_SYSTEM, payload);
    }

    public void recordSellerMarkedPartialDelivery(Order order, Long actorUserId) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("deliveredQuantity", order.getDeliveredQuantity());
        payload.put("orderedQuantity", order.getOrderedQuantity());
        append(
            order.getId(),
            EVENT_SELLER_MARKED_PARTIAL_DELIVERY,
            actorUserId,
            ACTOR_ROLE_SELLER,
            payload
        );
    }

    public void recordSellerMarkedDelivered(Order order, Long actorUserId) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("deliveredQuantity", order.getDeliveredQuantity());
        append(
            order.getId(),
            EVENT_SELLER_MARKED_DELIVERED,
            actorUserId,
            ACTOR_ROLE_SELLER,
            payload
        );
    }

    public void recordBuyerConfirmedReceived(Order order, Long actorUserId) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("deliveredQuantity", order.getDeliveredQuantity());
        append(
            order.getId(),
            EVENT_BUYER_CONFIRMED_RECEIVED,
            actorUserId,
            ACTOR_ROLE_BUYER,
            payload
        );
    }

    public void recordOrderCompleted(Order order, Long actorUserId, String actorRole) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("sellerNetAmount", order.getSellerNetAmount());
        payload.put("sellerFeeAmount", order.getSellerFeeAmount());
        payload.put("settlementCurrencyCode", order.getOfferPriceCurrencyCodeSnapshot());
        append(order.getId(), EVENT_ORDER_COMPLETED, actorUserId, actorRole, payload);
    }

    public void recordCancelRequested(Order order, Long requestId, Long actorUserId, String actorRole) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("requestId", requestId);
        append(order.getId(), EVENT_CANCEL_REQUESTED, actorUserId, actorRole, payload);
    }

    public void recordCancelRequestApproved(Order order, Long requestId, Long actorUserId, String actorRole) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("requestId", requestId);
        append(order.getId(), EVENT_CANCEL_REQUEST_APPROVED, actorUserId, actorRole, payload);
    }

    public void recordCancelRequestRejected(Order order, Long requestId, Long actorUserId, String actorRole) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("requestId", requestId);
        append(order.getId(), EVENT_CANCEL_REQUEST_REJECTED, actorUserId, actorRole, payload);
    }

    public void recordAmendQuantityRequested(
        Order order,
        Long requestId,
        Long actorUserId,
        String actorRole,
        java.math.BigDecimal requestedQuantity
    ) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("requestId", requestId);
        payload.put("requestedQuantity", requestedQuantity);
        append(order.getId(), EVENT_AMEND_QUANTITY_REQUESTED, actorUserId, actorRole, payload);
    }

    public void recordAmendQuantityApproved(
        Order order,
        Long requestId,
        Long actorUserId,
        String actorRole,
        java.math.BigDecimal previousQuantity
    ) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("requestId", requestId);
        payload.put("previousQuantity", previousQuantity);
        payload.put("orderedQuantity", order.getOrderedQuantity());
        payload.put("displayTotalAmount", order.getDisplayTotalAmount());
        append(order.getId(), EVENT_AMEND_QUANTITY_APPROVED, actorUserId, actorRole, payload);
    }

    public void recordAmendQuantityRejected(Order order, Long requestId, Long actorUserId, String actorRole) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("requestId", requestId);
        append(order.getId(), EVENT_AMEND_QUANTITY_REJECTED, actorUserId, actorRole, payload);
    }

    private void append(
        Long orderId,
        String eventType,
        Long actorUserId,
        String actorRole,
        JsonNode payload
    ) {
        orderEventRepository.save(new OrderEvent(orderId, eventType, actorUserId, actorRole, payload));
    }
}

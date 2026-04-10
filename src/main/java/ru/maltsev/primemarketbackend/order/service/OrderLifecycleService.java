package ru.maltsev.primemarketbackend.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.offer.domain.Offer;
import ru.maltsev.primemarketbackend.offer.repository.OfferRepository;
import ru.maltsev.primemarketbackend.order.api.dto.OrderResponse;
import ru.maltsev.primemarketbackend.order.domain.OfferReservation;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.repository.OfferReservationRepository;
import ru.maltsev.primemarketbackend.order.repository.OrderRepository;

@Service
@RequiredArgsConstructor
public class OrderLifecycleService {
    private static final String SIDE_SELL = "sell";
    private static final String ROLE_BUYER = "buyer";
    private static final String ROLE_SELLER = "seller";

    private final OrderRepository orderRepository;
    private final OfferReservationRepository offerReservationRepository;
    private final OfferRepository offerRepository;
    private final FundsHoldService fundsHoldService;
    private final OrderSettlementService orderSettlementService;
    private final OrderEventWriteService orderEventWriteService;

    @Transactional
    public OrderResponse confirmReady(UUID publicOrderId, Long actorUserId) {
        Order order = loadOrderForUpdate(publicOrderId);
        if (!order.getMakerUserId().equals(actorUserId)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                "ONLY_MAKER_CAN_CONFIRM_READY",
                "Only maker can confirm ready"
            );
        }

        validatePendingForConfirm(order);
        order.markInProgress();
        orderEventWriteService.recordMakerConfirmedReady(order);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(UUID publicOrderId, Long actorUserId) {
        Order order = loadOrderForUpdate(publicOrderId);
        if (!isParticipant(order, actorUserId)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                "ONLY_ORDER_PARTICIPANT_CAN_CANCEL",
                "Only order participant can cancel order"
            );
        }

        validatePendingForCancel(order);
        String actorRole = resolveParticipantRole(order, actorUserId);
        transitionPendingToTerminal(order, false, Instant.now());
        orderEventWriteService.recordOrderCanceled(order, actorUserId, actorRole);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse markPartiallyDelivered(UUID publicOrderId, Long actorUserId, BigDecimal deliveredQuantity) {
        Order order = loadOrderForUpdate(publicOrderId);
        requireSeller(order, actorUserId, "ONLY_SELLER_CAN_MARK_PARTIALLY_DELIVERED", "Only seller can mark partial delivery");
        validatePartialDelivery(order, deliveredQuantity);

        order.markPartiallyDelivered(deliveredQuantity);
        orderEventWriteService.recordSellerMarkedPartialDelivery(order, actorUserId);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse markDelivered(UUID publicOrderId, Long actorUserId) {
        Order order = loadOrderForUpdate(publicOrderId);
        requireSeller(order, actorUserId, "ONLY_SELLER_CAN_MARK_DELIVERED", "Only seller can mark delivered");
        validateDeliveryUpdateAllowed(order);

        order.markDelivered();
        orderEventWriteService.recordSellerMarkedDelivered(order, actorUserId);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse confirmReceived(UUID publicOrderId, Long actorUserId) {
        Order order = loadOrderForUpdate(publicOrderId);
        requireBuyer(order, actorUserId, "ONLY_BUYER_CAN_CONFIRM_RECEIVED", "Only buyer can confirm received");
        validateConfirmReceivedAllowed(order);

        order.markCompleted();
        orderSettlementService.settleCompletedOrder(order);
        orderEventWriteService.recordBuyerConfirmedReceived(order, actorUserId);
        orderEventWriteService.recordOrderCompleted(order, actorUserId, resolveParticipantRole(order, actorUserId));
        return OrderResponse.from(order);
    }

    @Transactional
    public int expirePendingOrders(Instant now) {
        List<Order> orders = orderRepository.findAllPendingExpiredForUpdate(now);
        for (Order order : orders) {
            transitionPendingToTerminal(order, true, now);
            orderEventWriteService.recordOrderExpired(order);
        }
        return orders.size();
    }

    private Order loadOrderForUpdate(UUID publicOrderId) {
        return orderRepository.findByPublicIdForUpdate(publicOrderId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "ORDER_NOT_FOUND",
                "Order not found"
            ));
    }

    private void validatePendingForConfirm(Order order) {
        if (order.isPending()) {
            if (!order.getExpiresAt().isAfter(Instant.now())) {
                throw orderAlreadyExpired();
            }
            return;
        }
        if (order.isInProgress()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_ALREADY_IN_PROGRESS",
                "Order is already in progress"
            );
        }
        if (order.isCanceled()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_ALREADY_CANCELED",
                "Order is already canceled"
            );
        }
        if (order.isExpired()) {
            throw orderAlreadyExpired();
        }
        throw invalidOrderStatus("Order cannot be confirmed from status " + order.getStatus());
    }

    private void validatePendingForCancel(Order order) {
        if (order.isPending()) {
            if (!order.getExpiresAt().isAfter(Instant.now())) {
                throw orderAlreadyExpired();
            }
            return;
        }
        if (order.isCanceled()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_ALREADY_CANCELED",
                "Order is already canceled"
            );
        }
        if (order.isExpired()) {
            throw orderAlreadyExpired();
        }
        if (order.isInProgress()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "UNSUPPORTED_ORDER_TRANSITION",
                "Cancel is not supported for in_progress orders"
            );
        }
        throw invalidOrderStatus("Order cannot be canceled from status " + order.getStatus());
    }

    private void validatePartialDelivery(Order order, BigDecimal deliveredQuantity) {
        validateDeliveryUpdateAllowed(order);
        if (deliveredQuantity == null
            || deliveredQuantity.compareTo(order.getDeliveredQuantity()) <= 0
            || deliveredQuantity.compareTo(order.getOrderedQuantity()) >= 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_DELIVERED_QUANTITY",
                "Delivered quantity must be greater than current delivered quantity and lower than ordered quantity"
            );
        }
    }

    private void validateDeliveryUpdateAllowed(Order order) {
        if (order.isInProgress() || order.isPartiallyDelivered()) {
            return;
        }
        if (order.isCompleted()) {
            throw orderAlreadyCompleted();
        }
        throw invalidOrderStatus("Order cannot be updated for delivery from status " + order.getStatus());
    }

    private void validateConfirmReceivedAllowed(Order order) {
        if (order.isInProgress() || order.isPartiallyDelivered() || order.isDelivered()) {
            return;
        }
        if (order.isCompleted()) {
            throw orderAlreadyCompleted();
        }
        throw invalidOrderStatus("Order cannot be completed from status " + order.getStatus());
    }

    private void transitionPendingToTerminal(Order order, boolean expired, Instant now) {
        if (!order.isPending()) {
            return;
        }

        OfferReservation reservation = offerReservationRepository.findByOrderIdForUpdate(order.getId())
            .orElseThrow(() -> invalidOrderStatus("Order reservation not found"));
        if (expired) {
            order.markExpired();
        } else {
            order.markCanceled();
        }

        if (SIDE_SELL.equals(order.getOfferSideSnapshot())) {
            fundsHoldService.releaseOrderFundsHold(order.getId(), expired, now);
        } else {
            fundsHoldService.releaseBuyOfferAllocation(order.getId(), expired, now);
        }

        if (reservation.isActive()) {
            if (expired) {
                reservation.markExpired();
            } else {
                reservation.markReleased(now);
            }
        }

        if (!SIDE_SELL.equals(order.getOfferSideSnapshot())) {
            Offer offer = offerRepository.findByIdForUpdate(reservation.getOfferId())
                .orElseThrow(() -> invalidOrderStatus("Offer backing order reservation not found"));
            fundsHoldService.syncBuyOfferFunding(offer);
        }
    }

    private boolean isParticipant(Order order, Long actorUserId) {
        return order.getMakerUserId().equals(actorUserId) || order.getTakerUserId().equals(actorUserId);
    }

    private void requireSeller(Order order, Long actorUserId, String code, String message) {
        if (!resolveSellerUserId(order).equals(actorUserId)) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, code, message);
        }
    }

    private void requireBuyer(Order order, Long actorUserId, String code, String message) {
        if (!resolveBuyerUserId(order).equals(actorUserId)) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, code, message);
        }
    }

    private String resolveParticipantRole(Order order, Long actorUserId) {
        if (order.getMakerUserId().equals(actorUserId)) {
            return order.getMakerRole();
        }
        if (order.getTakerUserId().equals(actorUserId)) {
            return order.getTakerRole();
        }
        throw invalidOrderStatus("Order participant role not found");
    }

    private Long resolveSellerUserId(Order order) {
        if (ROLE_SELLER.equals(order.getMakerRole())) {
            return order.getMakerUserId();
        }
        if (ROLE_SELLER.equals(order.getTakerRole())) {
            return order.getTakerUserId();
        }
        throw invalidOrderStatus("Order seller is not defined");
    }

    private Long resolveBuyerUserId(Order order) {
        if (ROLE_BUYER.equals(order.getMakerRole())) {
            return order.getMakerUserId();
        }
        if (ROLE_BUYER.equals(order.getTakerRole())) {
            return order.getTakerUserId();
        }
        throw invalidOrderStatus("Order buyer is not defined");
    }

    private ApiProblemException invalidOrderStatus(String message) {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            "INVALID_ORDER_STATUS",
            message
        );
    }

    private ApiProblemException orderAlreadyExpired() {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            "ORDER_ALREADY_EXPIRED",
            "Order is already expired"
        );
    }

    private ApiProblemException orderAlreadyCompleted() {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            "ORDER_ALREADY_COMPLETED",
            "Order is already completed"
        );
    }
}

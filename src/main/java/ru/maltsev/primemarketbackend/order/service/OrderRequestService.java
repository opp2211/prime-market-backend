package ru.maltsev.primemarketbackend.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.offer.domain.Offer;
import ru.maltsev.primemarketbackend.offer.repository.OfferRepository;
import ru.maltsev.primemarketbackend.order.api.dto.OrderRequestResponse;
import ru.maltsev.primemarketbackend.order.domain.OfferReservation;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.domain.OrderRequest;
import ru.maltsev.primemarketbackend.order.repository.OfferReservationRepository;
import ru.maltsev.primemarketbackend.order.repository.OrderRepository;
import ru.maltsev.primemarketbackend.order.repository.OrderRequestRepository;

@Service
@RequiredArgsConstructor
public class OrderRequestService {
    private static final String ROLE_BUYER = "buyer";
    private static final String SIDE_SELL = "sell";
    private static final int ORDER_MONEY_SCALE = 8;
    private static final BigDecimal BPS_DIVISOR = new BigDecimal("10000");

    private final OrderRepository orderRepository;
    private final OrderRequestRepository orderRequestRepository;
    private final OfferReservationRepository offerReservationRepository;
    private final OfferRepository offerRepository;
    private final FundsHoldService fundsHoldService;
    private final OrderLifecycleService orderLifecycleService;
    private final OrderEventWriteService orderEventWriteService;

    @Transactional
    public OrderRequestResponse requestCancel(UUID publicOrderId, Long actorUserId) {
        Order order = loadOrderForUpdate(publicOrderId);
        requireParticipant(order, actorUserId);
        validateActiveOrderForRequest(order);

        String actorRole = resolveParticipantRole(order, actorUserId);
        if (!ROLE_BUYER.equals(actorRole)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                "ONLY_BUYER_CAN_REQUEST_CANCEL",
                "Only buyer can request active order cancellation"
            );
        }

        OrderRequest existing = findPendingRequest(order.getId(), OrderRequest.TYPE_CANCEL);
        if (existing != null) {
            return OrderRequestResponse.from(existing);
        }

        OrderRequest request = orderRequestRepository.saveAndFlush(new OrderRequest(
            UUID.randomUUID(),
            order.getId(),
            OrderRequest.TYPE_CANCEL,
            actorUserId,
            actorRole,
            null,
            null
        ));
        orderEventWriteService.recordCancelRequested(order, request.getId(), actorUserId, actorRole);
        return OrderRequestResponse.from(request);
    }

    @Transactional
    public OrderRequestResponse requestAmendQuantity(UUID publicOrderId, Long actorUserId, BigDecimal quantity) {
        Order order = loadOrderForUpdate(publicOrderId);
        requireParticipant(order, actorUserId);
        validateActiveOrderForRequest(order);

        BigDecimal requestedQuantity = requireValidAmendQuantity(quantity);
        validateAmendQuantityAgainstDelivered(order, requestedQuantity);
        validateAmendQuantityAgainstLiveCapacity(order, requestedQuantity);

        String actorRole = resolveParticipantRole(order, actorUserId);
        OrderRequest existing = findPendingRequest(order.getId(), OrderRequest.TYPE_AMEND_QUANTITY);
        if (existing != null) {
            return OrderRequestResponse.from(existing);
        }

        OrderRequest request = orderRequestRepository.saveAndFlush(new OrderRequest(
            UUID.randomUUID(),
            order.getId(),
            OrderRequest.TYPE_AMEND_QUANTITY,
            actorUserId,
            actorRole,
            null,
            requestedQuantity
        ));
        orderEventWriteService.recordAmendQuantityRequested(
            order,
            request.getId(),
            actorUserId,
            actorRole,
            requestedQuantity
        );
        return OrderRequestResponse.from(request);
    }

    @Transactional
    public OrderRequestResponse approve(UUID publicRequestId, Long actorUserId) {
        OrderRequest request = loadRequestForUpdate(publicRequestId);
        ensurePending(request);
        Order order = loadOrderByIdForUpdate(request.getOrderId());
        String actorRole = requireCounterparty(order, request, actorUserId);

        if (request.isCancelRequest()) {
            approveCancelRequest(order, request, actorUserId, actorRole);
        } else if (request.isAmendQuantityRequest()) {
            approveAmendQuantityRequest(order, request, actorUserId, actorRole);
        } else {
            throw invalidOrderStatus("Unsupported order request type " + request.getRequestType());
        }

        return OrderRequestResponse.from(request);
    }

    @Transactional
    public OrderRequestResponse reject(UUID publicRequestId, Long actorUserId) {
        OrderRequest request = loadRequestForUpdate(publicRequestId);
        ensurePending(request);
        Order order = loadOrderByIdForUpdate(request.getOrderId());
        String actorRole = requireCounterparty(order, request, actorUserId);

        Instant now = Instant.now();
        request.markRejected(actorUserId, now);
        if (request.isCancelRequest()) {
            orderEventWriteService.recordCancelRequestRejected(order, request.getId(), actorUserId, actorRole);
        } else if (request.isAmendQuantityRequest()) {
            orderEventWriteService.recordAmendQuantityRejected(order, request.getId(), actorUserId, actorRole);
        }
        return OrderRequestResponse.from(request);
    }

    private void approveCancelRequest(
        Order order,
        OrderRequest request,
        Long actorUserId,
        String actorRole
    ) {
        validateActiveOrderForRequest(order);
        Instant now = Instant.now();
        orderLifecycleService.cancelOrderAndReleaseResources(order, now);
        request.markApproved(actorUserId, now);
        cancelOtherPendingRequests(order.getId(), request.getId(), actorUserId, now);
        orderEventWriteService.recordCancelRequestApproved(order, request.getId(), actorUserId, actorRole);
    }

    private void approveAmendQuantityRequest(
        Order order,
        OrderRequest request,
        Long actorUserId,
        String actorRole
    ) {
        validateActiveOrderForRequest(order);
        BigDecimal requestedQuantity = requireValidAmendQuantity(request.getRequestedQuantity());
        validateAmendQuantityAgainstDelivered(order, requestedQuantity);

        OfferReservation reservation = loadActiveReservation(order);
        Offer offer = loadOfferForUpdate(reservation.getOfferId());
        validateAmendQuantityAgainstLiveCapacity(order, offer, requestedQuantity);

        BigDecimal previousQuantity = order.getOrderedQuantity();
        OrderAmounts amounts = calculateOrderAmounts(order, requestedQuantity);
        if (SIDE_SELL.equals(order.getOfferSideSnapshot())) {
            fundsHoldService.rebalanceOrderFundsHoldForAmend(order.getId(), amounts.displayTotalAmount());
        } else {
            fundsHoldService.changeBuyOfferAllocationAmountForAmend(offer, order.getId(), amounts.sellerGrossAmount());
        }

        reservation.changeQuantity(requestedQuantity);
        order.amendQuantity(
            requestedQuantity,
            amounts.displayTotalAmount(),
            amounts.sellerGrossAmount(),
            amounts.sellerFeeAmount(),
            amounts.sellerNetAmount()
        );
        if (!SIDE_SELL.equals(order.getOfferSideSnapshot())) {
            fundsHoldService.syncBuyOfferFundingForAmend(offer);
        }

        Instant now = Instant.now();
        request.markApproved(actorUserId, now);
        orderEventWriteService.recordAmendQuantityApproved(
            order,
            request.getId(),
            actorUserId,
            actorRole,
            previousQuantity
        );
    }

    private Order loadOrderForUpdate(UUID publicOrderId) {
        return orderRepository.findByPublicIdForUpdate(publicOrderId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "ORDER_NOT_FOUND",
                "Order not found"
            ));
    }

    private Order loadOrderByIdForUpdate(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "ORDER_NOT_FOUND",
                "Order not found"
            ));
    }

    private OrderRequest loadRequestForUpdate(UUID publicRequestId) {
        return orderRequestRepository.findByPublicIdForUpdate(publicRequestId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "ORDER_REQUEST_NOT_FOUND",
                "Order request not found"
            ));
    }

    private void ensurePending(OrderRequest request) {
        if (!request.isPending()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_REQUEST_ALREADY_RESOLVED",
                "Order request is already resolved"
            );
        }
    }

    private void requireParticipant(Order order, Long actorUserId) {
        if (isParticipant(order, actorUserId)) {
            return;
        }
        throw new ApiProblemException(
            HttpStatus.FORBIDDEN,
            "ONLY_ORDER_PARTICIPANT_CAN_REQUEST_CHANGE",
            "Only order participant can request order changes"
        );
    }

    private String requireCounterparty(Order order, OrderRequest request, Long actorUserId) {
        if (!isParticipant(order, actorUserId)) {
            throw onlyCounterpartyCanResolve();
        }
        String actorRole = resolveParticipantRole(order, actorUserId);
        if (actorUserId.equals(request.getRequestedByUserId()) || actorRole.equals(request.getRequestedByRole())) {
            throw onlyCounterpartyCanResolve();
        }
        return actorRole;
    }

    private ApiProblemException onlyCounterpartyCanResolve() {
        return new ApiProblemException(
            HttpStatus.FORBIDDEN,
            "ONLY_COUNTERPARTY_CAN_APPROVE_REQUEST",
            "Only counterparty can resolve order request"
        );
    }

    private void validateActiveOrderForRequest(Order order) {
        if (order.isInProgress() || order.isPartiallyDelivered() || order.isDelivered()) {
            return;
        }
        throw invalidOrderStatus("Order request is not allowed from status " + order.getStatus());
    }

    private BigDecimal requireValidAmendQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_AMEND_QUANTITY",
                "Amend quantity must be positive"
            );
        }
        return quantity.stripTrailingZeros();
    }

    private void validateAmendQuantityAgainstDelivered(Order order, BigDecimal requestedQuantity) {
        if (requestedQuantity.compareTo(order.getDeliveredQuantity()) < 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "AMEND_QUANTITY_BELOW_DELIVERED",
                "Amend quantity cannot be lower than already delivered quantity"
            );
        }
    }

    private void validateAmendQuantityAgainstLiveCapacity(Order order, BigDecimal requestedQuantity) {
        OfferReservation reservation = loadActiveReservation(order);
        Offer offer = loadOfferForUpdate(reservation.getOfferId());
        validateAmendQuantityAgainstLiveCapacity(order, offer, requestedQuantity);
    }

    private void validateAmendQuantityAgainstLiveCapacity(
        Order order,
        Offer offer,
        BigDecimal requestedQuantity
    ) {
        if (offer.getQuantity() == null || offer.getQuantity().signum() <= 0) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "AMEND_QUANTITY_EXCEEDS_AVAILABLE_CAPACITY",
                "Offer has no available capacity"
            );
        }
        BigDecimal otherReservedQuantity = offerReservationRepository.sumQuantityByOfferIdAndStatusExcludingOrder(
            offer.getId(),
            FundsHoldService.STATUS_ACTIVE,
            order.getId()
        );
        BigDecimal maxQuantity = offer.getQuantity().subtract(otherReservedQuantity);
        if (maxQuantity.signum() < 0 || requestedQuantity.compareTo(maxQuantity) > 0) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "AMEND_QUANTITY_EXCEEDS_AVAILABLE_CAPACITY",
                "Amend quantity exceeds current offer capacity"
            );
        }
    }

    private OfferReservation loadActiveReservation(Order order) {
        OfferReservation reservation = offerReservationRepository.findByOrderIdForUpdate(order.getId())
            .orElseThrow(() -> invalidOrderStatus("Order reservation not found"));
        if (!reservation.isActive()) {
            throw invalidOrderStatus("Order reservation is not active");
        }
        return reservation;
    }

    private Offer loadOfferForUpdate(Long offerId) {
        return offerRepository.findByIdForUpdate(offerId)
            .orElseThrow(() -> invalidOrderStatus("Offer backing order reservation not found"));
    }

    private OrderRequest findPendingRequest(Long orderId, String requestType) {
        return orderRequestRepository.findPendingByOrderIdAndRequestTypeForUpdate(
            orderId,
            requestType,
            OrderRequest.STATUS_PENDING
        ).orElse(null);
    }

    private void cancelOtherPendingRequests(Long orderId, Long approvedRequestId, Long actorUserId, Instant now) {
        for (OrderRequest pendingRequest : orderRequestRepository.findAllByOrderIdAndStatusOrderByCreatedAtDesc(
            orderId,
            OrderRequest.STATUS_PENDING
        )) {
            if (!pendingRequest.getId().equals(approvedRequestId)) {
                pendingRequest.markCanceled(actorUserId, now);
            }
        }
    }

    private OrderAmounts calculateOrderAmounts(Order order, BigDecimal requestedQuantity) {
        BigDecimal displayTotalAmount = scaleOrderMoney(
            order.getDisplayUnitPriceAmount().multiply(requestedQuantity)
        );
        BigDecimal sellerGrossAmount = scaleOrderMoney(
            order.getOfferPriceAmountSnapshot().multiply(requestedQuantity)
        );
        BigDecimal sellerFeeAmount = sellerGrossAmount
            .multiply(BigDecimal.valueOf(order.getSellerFeeBpsSnapshot()))
            .divide(BPS_DIVISOR, ORDER_MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal sellerNetAmount = scaleOrderMoney(sellerGrossAmount.subtract(sellerFeeAmount));
        return new OrderAmounts(displayTotalAmount, sellerGrossAmount, sellerFeeAmount, sellerNetAmount);
    }

    private boolean isParticipant(Order order, Long actorUserId) {
        return order.getMakerUserId().equals(actorUserId) || order.getTakerUserId().equals(actorUserId);
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

    private BigDecimal scaleOrderMoney(BigDecimal amount) {
        return amount.setScale(ORDER_MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private ApiProblemException invalidOrderStatus(String message) {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            "INVALID_ORDER_STATUS",
            message
        );
    }

    private record OrderAmounts(
        BigDecimal displayTotalAmount,
        BigDecimal sellerGrossAmount,
        BigDecimal sellerFeeAmount,
        BigDecimal sellerNetAmount
    ) {
    }
}

package ru.maltsev.primemarketbackend.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.offer.domain.Offer;
import ru.maltsev.primemarketbackend.offer.repository.OfferRepository;
import ru.maltsev.primemarketbackend.order.domain.OfferReservation;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.repository.OfferReservationRepository;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderQuantityAmendService {
    private static final String SIDE_SELL = "sell";
    private static final int ORDER_MONEY_SCALE = 8;
    private static final BigDecimal BPS_DIVISOR = new BigDecimal("10000");

    private final OfferReservationRepository offerReservationRepository;
    private final OfferRepository offerRepository;
    private final FundsHoldService fundsHoldService;

    public BigDecimal normalizeRequestedQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_AMEND_QUANTITY",
                "Amend quantity must be positive"
            );
        }
        return quantity.stripTrailingZeros();
    }

    public void validateRequestedQuantity(Order order, BigDecimal requestedQuantity) {
        validateActiveOrder(order);
        if (requestedQuantity.compareTo(order.getDeliveredQuantity()) < 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "AMEND_QUANTITY_BELOW_DELIVERED",
                "Amend quantity cannot be lower than already delivered quantity"
            );
        }

        OfferReservation reservation = loadActiveReservation(order);
        Offer offer = loadOfferForUpdate(reservation.getOfferId());
        validateAgainstLiveCapacity(order, offer, requestedQuantity);
    }

    public AmendResult amendQuantity(Order order, BigDecimal requestedQuantity) {
        BigDecimal normalizedQuantity = normalizeRequestedQuantity(requestedQuantity);
        validateRequestedQuantity(order, normalizedQuantity);

        OfferReservation reservation = loadActiveReservation(order);
        Offer offer = loadOfferForUpdate(reservation.getOfferId());
        BigDecimal previousQuantity = order.getOrderedQuantity();
        OrderAmounts amounts = calculateOrderAmounts(order, normalizedQuantity);

        if (SIDE_SELL.equals(order.getOfferSideSnapshot())) {
            fundsHoldService.rebalanceOrderFundsHoldForAmend(order.getId(), amounts.displayTotalAmount());
        } else {
            fundsHoldService.changeBuyOfferAllocationAmountForAmend(offer, order.getId(), amounts.sellerGrossAmount());
        }

        reservation.changeQuantity(normalizedQuantity);
        order.amendQuantity(
            normalizedQuantity,
            amounts.displayTotalAmount(),
            amounts.sellerGrossAmount(),
            amounts.sellerFeeAmount(),
            amounts.sellerNetAmount()
        );
        if (!SIDE_SELL.equals(order.getOfferSideSnapshot())) {
            fundsHoldService.syncBuyOfferFundingForAmend(offer);
        }

        return new AmendResult(previousQuantity);
    }

    private void validateActiveOrder(Order order) {
        if (order.isInProgress() || order.isPartiallyDelivered() || order.isDelivered()) {
            return;
        }
        throw invalidOrderStatus("Amend quantity is not allowed from status " + order.getStatus());
    }

    private void validateAgainstLiveCapacity(
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

    public record AmendResult(BigDecimal previousQuantity) {
    }

    private record OrderAmounts(
        BigDecimal displayTotalAmount,
        BigDecimal sellerGrossAmount,
        BigDecimal sellerFeeAmount,
        BigDecimal sellerNetAmount
    ) {
    }
}

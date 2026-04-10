package ru.maltsev.primemarketbackend.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.domain.UserAccountTx;
import ru.maltsev.primemarketbackend.account.repository.UserAccountRepository;
import ru.maltsev.primemarketbackend.account.repository.UserAccountTxRepository;
import ru.maltsev.primemarketbackend.account.service.UserAccountService;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.offer.domain.Offer;
import ru.maltsev.primemarketbackend.offer.repository.OfferRepository;
import ru.maltsev.primemarketbackend.order.domain.OfferReservation;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.domain.UserAccountHold;
import ru.maltsev.primemarketbackend.order.domain.UserAccountHoldAllocation;
import ru.maltsev.primemarketbackend.order.repository.OfferReservationRepository;
import ru.maltsev.primemarketbackend.order.repository.UserAccountHoldAllocationRepository;
import ru.maltsev.primemarketbackend.order.repository.UserAccountHoldRepository;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccount;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccountTx;
import ru.maltsev.primemarketbackend.platform.repository.PlatformAccountTxRepository;
import ru.maltsev.primemarketbackend.platform.service.PlatformAccountService;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderSettlementService {
    private static final String SIDE_SELL = "sell";
    private static final String REF_TYPE_ORDER = "order";
    private static final String REF_TYPE_OFFER = "offer";
    private static final String HOLD_REASON_ORDER_FUNDS_HOLD = "order_funds_hold";
    private static final String HOLD_REASON_BUY_OFFER_FUNDS_HOLD = "buy_offer_funds_hold";
    private static final String USER_TX_TYPE_ORDER_SETTLEMENT_DEBIT = "ORDER_SETTLEMENT_DEBIT";
    private static final String USER_TX_TYPE_ORDER_SELLER_PAYOUT = "ORDER_SELLER_PAYOUT";
    private static final String PLATFORM_TX_TYPE_ORDER_FEE = "ORDER_PLATFORM_FEE";
    private static final String USER_TX_REF_ORDER_BUYER_SETTLEMENT = "ORDER_BUYER_SETTLEMENT";
    private static final String USER_TX_REF_ORDER_BUY_OFFER_CONSUMPTION = "ORDER_BUY_OFFER_CONSUMPTION";
    private static final String USER_TX_REF_ORDER_SELLER_PAYOUT = "ORDER_SELLER_PAYOUT";
    private static final String PLATFORM_TX_REF_ORDER_FEE = "ORDER_PLATFORM_FEE";
    private static final int ACCOUNT_MONEY_SCALE = 4;

    private final OfferReservationRepository offerReservationRepository;
    private final OfferRepository offerRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserAccountService userAccountService;
    private final UserAccountTxRepository userAccountTxRepository;
    private final UserAccountHoldRepository userAccountHoldRepository;
    private final UserAccountHoldAllocationRepository userAccountHoldAllocationRepository;
    private final FundsHoldService fundsHoldService;
    private final PlatformAccountService platformAccountService;
    private final PlatformAccountTxRepository platformAccountTxRepository;

    public void settleCompletedOrder(Order order) {
        Instant now = Instant.now();
        OfferReservation reservation = loadActiveReservation(order);
        Offer offer = loadOfferForUpdate(reservation.getOfferId());

        if (SIDE_SELL.equals(order.getOfferSideSnapshot())) {
            settleSellOffer(order, now);
        } else {
            settleBuyOffer(order, offer, now);
        }

        applyOfferCompletion(offer, order);
        reservation.markConsumed(now);
        creditSellerAndPlatform(order);

        if (!SIDE_SELL.equals(order.getOfferSideSnapshot())) {
            fundsHoldService.syncBuyOfferFunding(offer);
        }
    }

    private void settleSellOffer(Order order, Instant now) {
        UserAccountHold hold = userAccountHoldRepository.findByRefForUpdate(
                REF_TYPE_ORDER,
                order.getId(),
                HOLD_REASON_ORDER_FUNDS_HOLD
            )
            .orElseThrow(() -> settlementError("Order funds hold not found"));
        if (!hold.isActive()) {
            throw settlementError("Order funds hold is not active");
        }

        UserAccount buyerAccount = lockUserAccount(hold.getUserAccountId());
        ensureReservedCoverage(buyerAccount, hold.getAmount());
        decreaseReserved(buyerAccount.getId(), hold.getAmount());
        hold.markConsumed(now);
        userAccountTxRepository.save(new UserAccountTx(
            buyerAccount,
            hold.getAmount().negate(),
            USER_TX_TYPE_ORDER_SETTLEMENT_DEBIT,
            USER_TX_REF_ORDER_BUYER_SETTLEMENT,
            order.getId()
        ));
    }

    private void settleBuyOffer(Order order, Offer offer, Instant now) {
        UserAccountHoldAllocation allocation = userAccountHoldAllocationRepository.findByOrderIdForUpdate(order.getId())
            .orElseThrow(() -> settlementError("Buy offer allocation not found"));
        if (!allocation.isActive()) {
            throw settlementError("Buy offer allocation is not active");
        }

        UserAccountHold parentHold = userAccountHoldRepository.findByRefForUpdate(
                REF_TYPE_OFFER,
                offer.getId(),
                HOLD_REASON_BUY_OFFER_FUNDS_HOLD
            )
            .orElseThrow(() -> buyOfferParentHoldInconsistent("Buy offer parent hold not found"));
        if (!parentHold.isActive()) {
            throw buyOfferParentHoldInconsistent("Buy offer parent hold is not active");
        }
        if (!allocation.getUserAccountHoldId().equals(parentHold.getId())) {
            throw buyOfferParentHoldInconsistent("Buy offer allocation references a different parent hold");
        }
        if (parentHold.getAmount().compareTo(allocation.getAmount()) < 0) {
            throw buyOfferParentHoldInconsistent("Buy offer parent hold amount is lower than consumed allocation");
        }

        UserAccount buyerAccount = lockUserAccount(parentHold.getUserAccountId());
        ensureReservedCoverage(buyerAccount, allocation.getAmount());
        decreaseReserved(buyerAccount.getId(), allocation.getAmount());
        allocation.markConsumed(now);

        BigDecimal remainingHoldAmount = parentHold.getAmount().subtract(allocation.getAmount());
        if (remainingHoldAmount.signum() > 0) {
            parentHold.changeAmount(remainingHoldAmount);
        } else {
            parentHold.markReleased(now);
        }

        userAccountTxRepository.save(new UserAccountTx(
            buyerAccount,
            allocation.getAmount().negate(),
            USER_TX_TYPE_ORDER_SETTLEMENT_DEBIT,
            USER_TX_REF_ORDER_BUY_OFFER_CONSUMPTION,
            order.getId()
        ));
    }

    private void applyOfferCompletion(Offer offer, Order order) {
        if (offer.getQuantity() == null) {
            throw settlementError("Offer quantity is missing");
        }

        BigDecimal remainingQuantity = offer.getQuantity().subtract(order.getOrderedQuantity());
        if (remainingQuantity.signum() < 0) {
            throw settlementError("Offer quantity is lower than completed order quantity");
        }

        offer.setQuantity(remainingQuantity);
        if (remainingQuantity.signum() == 0) {
            offer.setMaxTradeQuantity(null);
            return;
        }
        if (offer.getMaxTradeQuantity() != null && offer.getMaxTradeQuantity().compareTo(remainingQuantity) > 0) {
            offer.setMaxTradeQuantity(remainingQuantity);
        }
    }

    private void creditSellerAndPlatform(Order order) {
        BigDecimal grossAmount = scaleAccountMoney(order.getSellerGrossAmount());
        BigDecimal feeAmount = scaleAccountMoney(order.getSellerFeeAmount());
        BigDecimal sellerNetAmount = grossAmount.subtract(feeAmount);
        if (sellerNetAmount.signum() < 0) {
            throw settlementError("Seller net settlement amount cannot be negative");
        }

        if (sellerNetAmount.signum() > 0) {
            UserAccount sellerAccount = userAccountService.getOrCreateAccountForUpdate(
                resolveSellerUserId(order),
                order.getOfferPriceCurrencyCodeSnapshot()
            );
            userAccountTxRepository.save(new UserAccountTx(
                sellerAccount,
                sellerNetAmount,
                USER_TX_TYPE_ORDER_SELLER_PAYOUT,
                USER_TX_REF_ORDER_SELLER_PAYOUT,
                order.getId()
            ));
        }

        if (feeAmount.signum() > 0) {
            PlatformAccount platformAccount = platformAccountService
                .getOrCreateAccountForUpdate(order.getOfferPriceCurrencyCodeSnapshot());
            platformAccountTxRepository.save(new PlatformAccountTx(
                platformAccount,
                feeAmount,
                PLATFORM_TX_TYPE_ORDER_FEE,
                PLATFORM_TX_REF_ORDER_FEE,
                order.getId()
            ));
        }
    }

    private OfferReservation loadActiveReservation(Order order) {
        OfferReservation reservation = offerReservationRepository.findByOrderIdForUpdate(order.getId())
            .orElseThrow(() -> settlementError("Offer reservation not found"));
        if (!reservation.isActive()) {
            throw settlementError("Offer reservation is not active");
        }
        return reservation;
    }

    private Offer loadOfferForUpdate(Long offerId) {
        return offerRepository.findByIdForUpdate(offerId)
            .orElseThrow(() -> settlementError("Offer backing reservation not found"));
    }

    private UserAccount lockUserAccount(Long userAccountId) {
        return userAccountRepository.findById(userAccountId)
            .orElseThrow(() -> settlementError("User account not found"));
    }

    private void ensureReservedCoverage(UserAccount account, BigDecimal amount) {
        if (account.getReserved().compareTo(amount) < 0) {
            throw settlementError("Reserved amount is lower than settlement amount");
        }
    }

    private void decreaseReserved(Long userAccountId, BigDecimal amount) {
        int updatedRows = userAccountRepository.decreaseReserved(userAccountId, amount);
        if (updatedRows != 1) {
            throw settlementError("Failed to decrease reserved amount during settlement");
        }
    }

    private Long resolveSellerUserId(Order order) {
        if ("seller".equals(order.getMakerRole())) {
            return order.getMakerUserId();
        }
        if ("seller".equals(order.getTakerRole())) {
            return order.getTakerUserId();
        }
        throw settlementError("Seller role is not present on order");
    }

    private BigDecimal scaleAccountMoney(BigDecimal amount) {
        return amount.setScale(ACCOUNT_MONEY_SCALE, RoundingMode.UP);
    }

    private ApiProblemException buyOfferParentHoldInconsistent(String message) {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            "BUY_OFFER_PARENT_HOLD_INCONSISTENT",
            message
        );
    }

    private ApiProblemException settlementError(String message) {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            "SETTLEMENT_ERROR",
            message
        );
    }
}

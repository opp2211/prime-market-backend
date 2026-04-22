package ru.maltsev.primemarketbackend.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.repository.UserAccountRepository;
import ru.maltsev.primemarketbackend.account.service.UserAccountService;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.offer.domain.Offer;
import ru.maltsev.primemarketbackend.order.domain.UserAccountHold;
import ru.maltsev.primemarketbackend.order.domain.UserAccountHoldAllocation;
import ru.maltsev.primemarketbackend.order.repository.OfferReservationRepository;
import ru.maltsev.primemarketbackend.order.repository.UserAccountHoldAllocationRepository;
import ru.maltsev.primemarketbackend.order.repository.UserAccountHoldRepository;

@Service
@RequiredArgsConstructor
public class FundsHoldService {
    public static final String STATUS_ACTIVE = "active";
    private static final String STATUS_RELEASED = "released";
    private static final String STATUS_EXPIRED = "expired";
    private static final String SIDE_BUY = "buy";
    private static final String REF_TYPE_OFFER = "offer";
    private static final String REF_TYPE_ORDER = "order";
    private static final String HOLD_REASON_BUY_OFFER_FUNDS_HOLD = "buy_offer_funds_hold";
    private static final String HOLD_REASON_ORDER_FUNDS_HOLD = "order_funds_hold";
    private static final int ACCOUNT_MONEY_SCALE = 4;

    private final UserAccountService userAccountService;
    private final UserAccountRepository userAccountRepository;
    private final UserAccountHoldRepository userAccountHoldRepository;
    private final UserAccountHoldAllocationRepository userAccountHoldAllocationRepository;
    private final OfferReservationRepository offerReservationRepository;

    @Transactional
    public void createOrderFundsHold(
        Long orderId,
        Long buyerUserId,
        String currencyCode,
        BigDecimal amount,
        Instant expiresAt
    ) {
        BigDecimal holdAmount = scaleAccountMoney(amount);
        UserAccount buyerAccount = userAccountService.getOrCreateAccountForUpdate(buyerUserId, currencyCode);
        if (buyerAccount.available().compareTo(holdAmount) < 0) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_FUNDS",
                "Insufficient funds"
            );
        }

        buyerAccount.increaseReserved(holdAmount);
        userAccountHoldRepository.save(new UserAccountHold(
            UUID.randomUUID(),
            REF_TYPE_ORDER,
            orderId,
            buyerAccount.getId(),
            holdAmount,
            STATUS_ACTIVE,
            HOLD_REASON_ORDER_FUNDS_HOLD,
            expiresAt
        ));
    }

    @Transactional
    public void allocateBuyOfferFundsHold(Offer offer, Long orderId, BigDecimal amount, Instant expiresAt) {
        UserAccountHold hold = userAccountHoldRepository.findByRefForUpdate(
                REF_TYPE_OFFER,
                offer.getId(),
                HOLD_REASON_BUY_OFFER_FUNDS_HOLD
            )
            .filter(UserAccountHold::isActive)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.CONFLICT,
                "BUY_OFFER_FUNDS_HOLD_NOT_FOUND",
                "Buy offer funds hold not found"
            ));

        UserAccount holdAccount = lockAccount(hold.getUserAccountId());
        ensureHoldOwnerAndCurrency(holdAccount, offer.getUserId(), offer.getPriceCurrencyCode());

        BigDecimal activeAllocationAmount = activeAllocationAmount(hold);
        if (hold.getAmount().compareTo(activeAllocationAmount) < 0) {
            throw invalidHoldAllocation("Buy offer funds hold is smaller than active allocations");
        }

        BigDecimal allocationAmount = scaleAccountMoney(amount);
        BigDecimal availableAmount = hold.getAmount().subtract(activeAllocationAmount);
        if (availableAmount.compareTo(allocationAmount) < 0) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "BUY_OFFER_FUNDS_HOLD_INSUFFICIENT",
                "Buy offer funds hold is insufficient"
            );
        }

        userAccountHoldAllocationRepository.save(new UserAccountHoldAllocation(
            hold.getId(),
            orderId,
            allocationAmount,
            STATUS_ACTIVE,
            expiresAt
        ));
    }

    @Transactional
    public void releaseOrderFundsHold(Long orderId, boolean expired, Instant now) {
        UserAccountHold hold = userAccountHoldRepository.findByRefForUpdate(
                REF_TYPE_ORDER,
                orderId,
                HOLD_REASON_ORDER_FUNDS_HOLD
            )
            .orElseThrow(() -> invalidHoldAllocation("Order funds hold not found"));
        if (!hold.isActive()) {
            return;
        }

        UserAccount account = lockAccount(hold.getUserAccountId());
        account.decreaseReserved(hold.getAmount());
        if (expired) {
            hold.markExpired();
            return;
        }
        hold.markReleased(now);
    }

    @Transactional
    public void releaseBuyOfferAllocation(Long orderId, boolean expired, Instant now) {
        UserAccountHoldAllocation allocation = userAccountHoldAllocationRepository.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> invalidHoldAllocation("Buy offer allocation not found"));
        if (!allocation.isActive()) {
            return;
        }

        if (expired) {
            allocation.markExpired();
            return;
        }
        allocation.markReleased(now);
    }

    @Transactional
    public void syncBuyOfferFunding(Offer offer) {
        syncBuyOfferFunding(offer, "INSUFFICIENT_FUNDS_FOR_BUY_OFFER");
    }

    @Transactional
    public void syncBuyOfferFundingForAmend(Offer offer) {
        syncBuyOfferFunding(offer, "INSUFFICIENT_FUNDS_FOR_AMEND");
    }

    @Transactional
    public void rebalanceOrderFundsHoldForAmend(Long orderId, BigDecimal amount) {
        UserAccountHold hold = userAccountHoldRepository.findByRefForUpdate(
                REF_TYPE_ORDER,
                orderId,
                HOLD_REASON_ORDER_FUNDS_HOLD
            )
            .orElseThrow(() -> invalidHoldAllocation("Order funds hold not found"));
        if (!hold.isActive()) {
            throw invalidHoldAllocation("Order funds hold is not active");
        }

        UserAccount account = lockAccount(hold.getUserAccountId());
        BigDecimal targetAmount = scaleAccountMoney(amount);
        ensureAvailableDelta(account, hold.getAmount(), targetAmount, "INSUFFICIENT_FUNDS_FOR_AMEND");
        applyReservedDelta(account, hold.getAmount(), targetAmount);
        hold.changeAmount(targetAmount);
    }

    @Transactional
    public void changeBuyOfferAllocationAmountForAmend(Offer offer, Long orderId, BigDecimal amount) {
        UserAccountHoldAllocation allocation = userAccountHoldAllocationRepository.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> buyOfferParentHoldInconsistent("Buy offer allocation not found"));
        if (!allocation.isActive()) {
            throw buyOfferParentHoldInconsistent("Buy offer allocation is not active");
        }

        UserAccountHold hold = userAccountHoldRepository.findByRefForUpdate(
            REF_TYPE_OFFER,
            offer.getId(),
            HOLD_REASON_BUY_OFFER_FUNDS_HOLD
        ).orElseThrow(() -> buyOfferParentHoldInconsistent("Buy offer parent hold not found"));
        if (!hold.isActive()) {
            throw buyOfferParentHoldInconsistent("Buy offer parent hold is not active");
        }
        if (!allocation.getUserAccountHoldId().equals(hold.getId())) {
            throw buyOfferParentHoldInconsistent("Buy offer allocation references a different parent hold");
        }

        UserAccount holdAccount = lockAccount(hold.getUserAccountId());
        ensureHoldOwnerAndCurrency(holdAccount, offer.getUserId(), offer.getPriceCurrencyCode());
        allocation.changeAmount(scaleAccountMoney(amount));
    }

    private void syncBuyOfferFunding(Offer offer, String insufficientFundsCode) {
        UserAccountHold hold = userAccountHoldRepository.findByRefForUpdate(
            REF_TYPE_OFFER,
            offer.getId(),
            HOLD_REASON_BUY_OFFER_FUNDS_HOLD
        ).orElse(null);

        BigDecimal activeReservedQuantity = offerReservationRepository.sumQuantityByOfferIdAndStatus(
            offer.getId(),
            STATUS_ACTIVE
        );
        if (offer.getQuantity() != null && activeReservedQuantity.compareTo(offer.getQuantity()) > 0) {
            throw invalidHoldAllocation("Offer quantity cannot be lower than active reserved capacity");
        }

        BigDecimal activeAllocationAmount = activeAllocationAmount(hold);
        if (!requiresBuyOfferBacking(offer)) {
            reconcileInactiveOrNonBuyOfferHold(hold, activeAllocationAmount, insufficientFundsCode);
            return;
        }

        BigDecimal unreservedQuantity = offer.getQuantity().subtract(activeReservedQuantity);
        BigDecimal targetAmount = activeAllocationAmount.add(
            scaleAccountMoney(offer.getPriceAmount().multiply(unreservedQuantity))
        );
        reconcileActiveBuyOfferHold(offer, hold, activeAllocationAmount, targetAmount, insufficientFundsCode);
    }

    private void reconcileInactiveOrNonBuyOfferHold(
        UserAccountHold hold,
        BigDecimal activeAllocationAmount,
        String insufficientFundsCode
    ) {
        if (hold == null) {
            return;
        }

        UserAccount currentAccount = lockAccount(hold.getUserAccountId());
        reconcileHoldOnCurrentAccount(hold, currentAccount, activeAllocationAmount, insufficientFundsCode);
    }

    private void reconcileActiveBuyOfferHold(
        Offer offer,
        UserAccountHold hold,
        BigDecimal activeAllocationAmount,
        BigDecimal targetAmount,
        String insufficientFundsCode
    ) {
        UserAccount currentAccount = hold == null ? null : lockAccount(hold.getUserAccountId());
        UserAccount targetAccount = resolveTargetAccount(offer, currentAccount, activeAllocationAmount);

        if (hold == null) {
            ensureAvailableDelta(targetAccount, BigDecimal.ZERO, targetAmount, insufficientFundsCode);
            targetAccount.increaseReserved(targetAmount);
            userAccountHoldRepository.save(new UserAccountHold(
                UUID.randomUUID(),
                REF_TYPE_OFFER,
                offer.getId(),
                targetAccount.getId(),
                targetAmount,
                STATUS_ACTIVE,
                HOLD_REASON_BUY_OFFER_FUNDS_HOLD,
                null
            ));
            return;
        }

        if (currentAccount != null && currentAccount.getId().equals(targetAccount.getId())) {
            reconcileHoldOnCurrentAccount(hold, currentAccount, targetAmount, insufficientFundsCode);
            return;
        }

        BigDecimal currentActiveAmount = hold.isActive() ? hold.getAmount() : BigDecimal.ZERO;
        if (currentActiveAmount.signum() > 0 && currentAccount != null) {
            currentAccount.decreaseReserved(currentActiveAmount);
        }
        ensureAvailableDelta(targetAccount, BigDecimal.ZERO, targetAmount, insufficientFundsCode);
        targetAccount.increaseReserved(targetAmount);
        hold.activate(targetAccount.getId(), targetAmount, null);
    }

    private UserAccount resolveTargetAccount(Offer offer, UserAccount currentAccount, BigDecimal activeAllocationAmount) {
        if (currentAccount != null) {
            if (currentAccount.getUser().getId().equals(offer.getUserId())
                && currentAccount.getCurrencyCode().equalsIgnoreCase(offer.getPriceCurrencyCode())) {
                return currentAccount;
            }
            if (activeAllocationAmount.signum() > 0) {
                throw invalidHoldAllocation("Cannot change funded buy offer currency while allocations are active");
            }
        }
        return userAccountService.getOrCreateAccountForUpdate(offer.getUserId(), offer.getPriceCurrencyCode());
    }

    private void reconcileHoldOnCurrentAccount(
        UserAccountHold hold,
        UserAccount account,
        BigDecimal targetAmount,
        String insufficientFundsCode
    ) {
        BigDecimal currentActiveAmount = hold.isActive() ? hold.getAmount() : BigDecimal.ZERO;
        if (targetAmount.signum() == 0) {
            if (currentActiveAmount.signum() > 0) {
                account.decreaseReserved(currentActiveAmount);
            }
            if (!STATUS_RELEASED.equals(hold.getStatus())) {
                hold.markReleased(Instant.now());
            }
            return;
        }

        ensureAvailableDelta(account, currentActiveAmount, targetAmount, insufficientFundsCode);
        applyReservedDelta(account, currentActiveAmount, targetAmount);
        hold.activate(account.getId(), targetAmount, null);
    }

    private BigDecimal activeAllocationAmount(UserAccountHold hold) {
        if (hold == null) {
            return BigDecimal.ZERO.setScale(ACCOUNT_MONEY_SCALE, RoundingMode.UP);
        }
        return scaleAccountMoney(userAccountHoldAllocationRepository.sumAmountByUserAccountHoldIdAndStatus(
            hold.getId(),
            STATUS_ACTIVE
        ));
    }

    private void ensureHoldOwnerAndCurrency(UserAccount account, Long expectedUserId, String expectedCurrencyCode) {
        if (!account.getUser().getId().equals(expectedUserId)
            || !account.getCurrencyCode().equalsIgnoreCase(expectedCurrencyCode)) {
            throw invalidHoldAllocation("Buy offer funds hold does not match offer owner or currency");
        }
    }

    private UserAccount lockAccount(Long userAccountId) {
        return userAccountRepository.findByIdForUpdate(userAccountId)
            .orElseThrow(() -> invalidHoldAllocation("User account hold references missing wallet"));
    }

    private void ensureAvailableDelta(
        UserAccount account,
        BigDecimal currentActiveAmount,
        BigDecimal targetAmount,
        String insufficientFundsCode
    ) {
        BigDecimal delta = targetAmount.subtract(currentActiveAmount);
        if (delta.signum() <= 0) {
            return;
        }
        if (account.available().compareTo(delta) < 0) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                insufficientFundsCode,
                "Insufficient funds"
            );
        }
    }

    private void applyReservedDelta(UserAccount account, BigDecimal currentActiveAmount, BigDecimal targetAmount) {
        BigDecimal delta = targetAmount.subtract(currentActiveAmount);
        if (delta.signum() > 0) {
            account.increaseReserved(delta);
            return;
        }
        if (delta.signum() < 0) {
            account.decreaseReserved(delta.abs());
        }
    }

    private boolean requiresBuyOfferBacking(Offer offer) {
        return SIDE_BUY.equals(offer.getSide())
            && STATUS_ACTIVE.equals(offer.getStatus())
            && offer.getPublishedAt() != null;
    }

    private BigDecimal scaleAccountMoney(BigDecimal amount) {
        return amount.setScale(ACCOUNT_MONEY_SCALE, RoundingMode.UP);
    }

    private ApiProblemException invalidHoldAllocation(String message) {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            "INVALID_HOLD_ALLOCATION",
            message
        );
    }

    private ApiProblemException buyOfferParentHoldInconsistent(String message) {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            "BUY_OFFER_PARENT_HOLD_INCONSISTENT",
            message
        );
    }
}

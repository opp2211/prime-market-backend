package ru.maltsev.primemarketbackend.withdrawal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.domain.UserAccountTx;
import ru.maltsev.primemarketbackend.account.repository.UserAccountRepository;
import ru.maltsev.primemarketbackend.account.repository.UserAccountTxRepository;
import ru.maltsev.primemarketbackend.account.service.UserAccountService;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationActorType;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationEventType;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationType;
import ru.maltsev.primemarketbackend.money.service.MoneyOperationEventService;
import ru.maltsev.primemarketbackend.notification.service.NotificationService;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryAccount;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryTransaction;
import ru.maltsev.primemarketbackend.treasury.repository.TreasuryAccountRepository;
import ru.maltsev.primemarketbackend.treasury.service.TreasuryService;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.ConfirmWithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.CreateWithdrawalPayoutPlanRequest;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.CreateWithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.RejectWithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.domain.PayoutProfile;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalMethod;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalPayoutPlan;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalPayoutPlanStatus;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequestStatus;
import ru.maltsev.primemarketbackend.withdrawal.repository.WithdrawalMethodRepository;
import ru.maltsev.primemarketbackend.withdrawal.repository.WithdrawalPayoutPlanRepository;
import ru.maltsev.primemarketbackend.withdrawal.repository.WithdrawalRequestRepository;

@Service
@RequiredArgsConstructor
public class WithdrawalRequestService {
    private static final String TX_TYPE_WITHDRAWAL = "WITHDRAWAL";
    private static final String REF_TYPE_WITHDRAWAL_REQUEST = "WITHDRAWAL_REQUEST";
    private static final int MONEY_SCALE = 4;

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WithdrawalMethodRepository withdrawalMethodRepository;
    private final PayoutProfileService payoutProfileService;
    private final WithdrawalRequisitesValidator requisitesValidator;
    private final UserAccountService userAccountService;
    private final UserAccountRepository userAccountRepository;
    private final UserAccountTxRepository userAccountTxRepository;
    private final NotificationService notificationService;
    private final MoneyOperationEventService moneyOperationEventService;
    private final TreasuryService treasuryService;
    private final TreasuryAccountRepository treasuryAccountRepository;
    private final WithdrawalPayoutPlanRepository withdrawalPayoutPlanRepository;

    @Transactional
    public WithdrawalRequest create(Long userId, CreateWithdrawalRequest request) {
        String currencyCode = normalizeCurrencyCode(request.currencyCode());
        BigDecimal amount = normalizeMoney(request.amount());
        WithdrawalMethod method = loadActiveMethod(request.withdrawalMethodId());
        validateMethodCurrency(method, currencyCode);
        validateMinAmount(method, amount);

        UserAccount account = userAccountService.getOrCreateAccountForUpdate(userId, currencyCode);
        if (account.available().compareTo(amount) < 0) {
            throw conflict("INSUFFICIENT_FUNDS", "Insufficient available balance");
        }

        ResolvedPayoutData payoutData = resolvePayoutData(userId, method, request);
        account.increaseReserved(amount);

        WithdrawalRequest withdrawalRequest = new WithdrawalRequest(
            userId,
            account.getId(),
            method,
            payoutData.payoutProfile(),
            payoutData.requisites(),
            amount,
            amount
        );
        WithdrawalRequest savedRequest = withdrawalRequestRepository.save(withdrawalRequest);
        record(
            savedRequest,
            MoneyOperationEventType.WITHDRAWAL_CREATED,
            null,
            MoneyOperationActorType.USER,
            userId,
            null,
            null,
            moneyOperationEventService.payload(
                "amount", savedRequest.getAmount(),
                "currency_code", savedRequest.getCurrencyCodeSnapshot(),
                "withdrawal_method_id", method.getId(),
                "withdrawal_method_code", method.getCode(),
                "withdrawal_method_title", method.getTitle()
            )
        );
        return savedRequest;
    }

    @Transactional(readOnly = true)
    public WithdrawalRequest getForUser(String requestCode, Long userId) {
        return withdrawalRequestRepository.findByPublicCodeAndUserId(requestCode, userId)
            .orElseThrow(() -> notFound("WITHDRAWAL_REQUEST_NOT_FOUND", "Withdrawal request not found"));
    }

    @Transactional(readOnly = true)
    public Page<WithdrawalRequest> listForUser(Long userId, String status, Pageable pageable) {
        WithdrawalRequestStatus parsedStatus = parseStatus(status);
        if (parsedStatus == null) {
            return withdrawalRequestRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return withdrawalRequestRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, parsedStatus, pageable);
    }

    @Transactional
    public WithdrawalRequest cancel(String requestCode, Long userId) {
        WithdrawalRequest request = withdrawalRequestRepository.findByPublicCodeAndUserIdForUpdate(requestCode, userId)
            .orElseThrow(() -> notFound("WITHDRAWAL_REQUEST_NOT_FOUND", "Withdrawal request not found"));
        if (request.isCancelled()) {
            return request;
        }
        if (!request.isOpen()) {
            throw conflict(
                "INVALID_STATUS",
                "Cannot cancel withdrawal request in status " + request.getStatus()
            );
        }

        WithdrawalRequestStatus statusBefore = request.getStatus();
        releaseReservedAmount(request);
        request.cancel(Instant.now());
        WithdrawalRequest savedRequest = withdrawalRequestRepository.save(request);
        record(
            savedRequest,
            MoneyOperationEventType.WITHDRAWAL_CANCELLED,
            statusBefore,
            MoneyOperationActorType.USER,
            userId,
            null,
            null,
            Map.of()
        );
        return savedRequest;
    }

    @Transactional(readOnly = true)
    public Page<WithdrawalRequest> listForBackoffice(List<String> statuses, Pageable pageable) {
        Set<WithdrawalRequestStatus> parsedStatuses = parseStatuses(statuses);
        if (parsedStatuses == null || parsedStatuses.isEmpty()) {
            return withdrawalRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return withdrawalRequestRepository.findAllByStatusInOrderByCreatedAtDesc(parsedStatuses, pageable);
    }

    @Transactional(readOnly = true)
    public WithdrawalRequest getForBackoffice(String requestCode) {
        return withdrawalRequestRepository.findByPublicCode(requestCode)
            .orElseThrow(() -> notFound("WITHDRAWAL_REQUEST_NOT_FOUND", "Withdrawal request not found"));
    }

    @Transactional
    public WithdrawalRequest take(String requestCode, Long actorUserId) {
        WithdrawalRequest request = lockRequest(requestCode);
        if (request.isProcessing() && actorUserId.equals(request.getProcessedByUserId())) {
            return request;
        }
        if (!request.isOpen()) {
            throw conflict(
                "INVALID_STATUS",
                "Cannot take withdrawal request in status " + request.getStatus()
            );
        }

        WithdrawalRequestStatus statusBefore = request.getStatus();
        request.markProcessing(actorUserId, Instant.now());
        WithdrawalRequest savedRequest = withdrawalRequestRepository.save(request);
        record(
            savedRequest,
            MoneyOperationEventType.WITHDRAWAL_TAKEN,
            statusBefore,
            MoneyOperationActorType.OPERATOR,
            actorUserId,
            null,
            null,
            Map.of()
        );
        return savedRequest;
    }

    @Transactional
    public WithdrawalRequest planPayout(
        String requestCode,
        Long actorUserId,
        CreateWithdrawalPayoutPlanRequest payload
    ) {
        WithdrawalRequest request = lockRequest(requestCode);
        if (!(request.isOpen() || request.isProcessing())) {
            throw conflict(
                "INVALID_STATUS",
                "Cannot plan payout for withdrawal request in status " + request.getStatus()
            );
        }

        TreasuryAccount treasuryAccount = treasuryAccountRepository.findByIdForUpdate(
            payload.treasuryAccountId()
        ).orElseThrow(() -> notFound("TREASURY_ACCOUNT_NOT_FOUND", "Treasury account not found"));
        if (!treasuryAccount.isActive()) {
            throw conflict("TREASURY_ACCOUNT_INACTIVE", "Treasury account is inactive");
        }

        BigDecimal plannedUserAmount = payload.plannedUserAmount() == null
            ? request.getActualPayoutAmount()
            : normalizeMoney(payload.plannedUserAmount());
        BigDecimal treasuryAmount = payload.treasuryAmount() == null
            ? resolveSameCurrencyTreasuryAmount(treasuryAccount, plannedUserAmount, request.getCurrencyCodeSnapshot())
            : normalizeMoney(payload.treasuryAmount());

        WithdrawalRequestStatus statusBefore = request.getStatus();
        if (request.isOpen()) {
            request.markProcessing(actorUserId, Instant.now());
        }

        WithdrawalPayoutPlan plan = withdrawalPayoutPlanRepository.findByWithdrawalRequestId(request.getId())
            .orElse(null);
        if (plan == null) {
            plan = new WithdrawalPayoutPlan(
                request,
                treasuryAccount,
                plannedUserAmount,
                request.getCurrencyCodeSnapshot(),
                treasuryAmount,
                actorUserId,
                payload.externalReference(),
                payload.operatorComment()
            );
        } else {
            plan.replace(
                treasuryAccount,
                plannedUserAmount,
                treasuryAmount,
                actorUserId,
                payload.externalReference(),
                payload.operatorComment()
            );
        }
        WithdrawalPayoutPlan savedPlan = withdrawalPayoutPlanRepository.saveAndFlush(plan);
        request.attachPayoutPlan(savedPlan);
        WithdrawalRequest savedRequest = withdrawalRequestRepository.save(request);
        record(
            savedRequest,
            MoneyOperationEventType.WITHDRAWAL_PAYOUT_PLANNED,
            statusBefore,
            MoneyOperationActorType.OPERATOR,
            actorUserId,
            null,
            normalizeOptional(payload.operatorComment()),
            moneyOperationEventService.payload(
                "payout_plan_id", savedPlan.getId(),
                "treasury_account_id", treasuryAccount.getId(),
                "treasury_account_code", treasuryAccount.getCode(),
                "planned_user_amount", plannedUserAmount,
                "user_currency_code", request.getCurrencyCodeSnapshot(),
                "treasury_amount", treasuryAmount,
                "treasury_currency_code", treasuryAccount.getCurrencyCode(),
                "external_reference", normalizeOptional(payload.externalReference())
            )
        );
        return savedRequest;
    }

    @Transactional
    public WithdrawalRequest reject(String requestCode, Long actorUserId, RejectWithdrawalRequest payload) {
        WithdrawalRequest request = lockRequest(requestCode);
        if (request.isRejected()) {
            return request;
        }
        if (!(request.isOpen() || request.isProcessing())) {
            throw conflict(
                "INVALID_STATUS",
                "Cannot reject withdrawal request in status " + request.getStatus()
            );
        }

        WithdrawalRequestStatus statusBefore = request.getStatus();
        releaseReservedAmount(request);
        request.reject(
            actorUserId,
            normalizeRequired(payload.rejectionReason(), "rejection_reason"),
            normalizeOptional(payload.operatorComment()),
            Instant.now()
        );
        WithdrawalRequest rejectedRequest = withdrawalRequestRepository.save(request);
        record(
            rejectedRequest,
            MoneyOperationEventType.WITHDRAWAL_REJECTED,
            statusBefore,
            MoneyOperationActorType.OPERATOR,
            actorUserId,
            request.getRejectionReason(),
            request.getOperatorComment(),
            Map.of()
        );
        notificationService.notifyWithdrawalRejected(rejectedRequest);
        return rejectedRequest;
    }

    @Transactional
    public WithdrawalRequest confirm(String requestCode, Long actorUserId, ConfirmWithdrawalRequest payload) {
        WithdrawalRequest request = lockRequest(requestCode);
        if (request.isCompleted()) {
            return request;
        }
        if (!request.isProcessing()) {
            throw conflict(
                "INVALID_STATUS",
                "Cannot confirm withdrawal request in status " + request.getStatus()
            );
        }

        WithdrawalRequestStatus statusBefore = request.getStatus();
        UserAccount account = userAccountRepository.findByIdForUpdate(request.getUserAccountId())
            .orElseThrow(() -> conflict("USER_ACCOUNT_NOT_FOUND", "Withdrawal wallet not found"));
        int updatedRows = userAccountRepository.decreaseReserved(account.getId(), request.getAmount());
        if (updatedRows != 1) {
            throw conflict("RESERVE_RELEASE_FAILED", "Failed to decrease reserved balance");
        }

        userAccountTxRepository.save(new UserAccountTx(
            account,
            request.getAmount().negate(),
            TX_TYPE_WITHDRAWAL,
            REF_TYPE_WITHDRAWAL_REQUEST,
            request.getId()
        ));

        BigDecimal actualPayoutAmount = payload == null || payload.actualPayoutAmount() == null
            ? request.getActualPayoutAmount()
            : normalizeMoney(payload.actualPayoutAmount());
        WithdrawalPayoutPlan payoutPlan = withdrawalPayoutPlanRepository.findByWithdrawalRequestId(request.getId())
            .orElse(null);
        Long resolvedTreasuryAccountId = payload == null ? null : payload.treasuryAccountId();
        BigDecimal resolvedTreasuryAmount = payload == null ? null : payload.treasuryAmount();
        String resolvedTreasuryExternalReference = payload == null ? null : payload.treasuryExternalReference();
        String resolvedOperatorComment = payload == null ? null : normalizeOptional(payload.operatorComment());
        if (payoutPlan != null && payoutPlan.getStatus() == WithdrawalPayoutPlanStatus.PLANNED) {
            if (resolvedTreasuryAccountId == null) {
                resolvedTreasuryAccountId = payoutPlan.getTreasuryAccount().getId();
            }
            if (resolvedTreasuryAmount == null) {
                resolvedTreasuryAmount = payoutPlan.getTreasuryAmount();
            }
            if (resolvedTreasuryExternalReference == null) {
                resolvedTreasuryExternalReference = payoutPlan.getExternalReference();
            }
            if (resolvedOperatorComment == null) {
                resolvedOperatorComment = payoutPlan.getOperatorComment();
            }
        }
        TreasuryTransaction treasuryTransaction = treasuryService.recordWithdrawalOut(
            resolvedTreasuryAccountId,
            resolvedTreasuryAmount,
            actualPayoutAmount,
            request.getCurrencyCodeSnapshot(),
            request.getId(),
            request.getPublicCode(),
            resolvedTreasuryExternalReference,
            resolvedOperatorComment,
            actorUserId,
            moneyOperationEventService.payload(
                "user_debited_amount", request.getAmount(),
                "actual_payout_amount", actualPayoutAmount,
                "user_currency_code", request.getCurrencyCodeSnapshot(),
                "withdrawal_method_id", request.getWithdrawalMethod().getId(),
                "withdrawal_method_code", request.getWithdrawalMethodCodeSnapshot(),
                "withdrawal_method_title", request.getWithdrawalMethodTitleSnapshot()
            )
        );
        request.confirm(
            actorUserId,
            actualPayoutAmount,
            resolvedOperatorComment,
            Instant.now()
        );
        request.attachTreasuryTransaction(treasuryTransaction);
        if (payoutPlan != null && payoutPlan.getStatus() == WithdrawalPayoutPlanStatus.PLANNED) {
            payoutPlan.complete(Instant.now());
            withdrawalPayoutPlanRepository.save(payoutPlan);
        }
        WithdrawalRequest confirmedRequest = withdrawalRequestRepository.save(request);
        Map<String, Object> eventPayload = moneyOperationEventService.payload(
            "debited_amount", request.getAmount(),
            "actual_payout_amount", request.getActualPayoutAmount(),
            "currency_code", request.getCurrencyCodeSnapshot()
        );
        appendTreasuryPayload(eventPayload, treasuryTransaction);
        appendPayoutPlanPayload(eventPayload, payoutPlan);
        record(
            confirmedRequest,
            MoneyOperationEventType.WITHDRAWAL_CONFIRMED,
            statusBefore,
            MoneyOperationActorType.OPERATOR,
            actorUserId,
            null,
            request.getOperatorComment(),
            eventPayload
        );
        notificationService.notifyWithdrawalCompleted(confirmedRequest);
        return confirmedRequest;
    }

    private ResolvedPayoutData resolvePayoutData(Long userId, WithdrawalMethod method, CreateWithdrawalRequest request) {
        if (request.payoutProfileId() != null) {
            if (request.requisites() != null && !request.requisites().isEmpty()) {
                throw validationError("Use either 'payout_profile_id' or 'requisites'");
            }
            if (Boolean.TRUE.equals(request.savePayoutProfile())) {
                throw validationError("Existing payout profile cannot be saved again");
            }

            PayoutProfile profile = payoutProfileService.getActiveForUser(request.payoutProfileId(), userId);
            if (!profile.getWithdrawalMethod().getId().equals(method.getId())) {
                throw validationError("Selected payout profile does not match withdrawal method");
            }
            return new ResolvedPayoutData(profile, profile.getRequisites());
        }

        Map<String, Object> requisites = requisitesValidator.validateAndNormalize(method, request.requisites());
        PayoutProfile payoutProfile = null;
        if (Boolean.TRUE.equals(request.savePayoutProfile())) {
            payoutProfile = payoutProfileService.createForMethod(
                userId,
                method,
                normalizeRequired(request.payoutProfileTitle(), "payout_profile_title"),
                requisites,
                false
            );
        }
        return new ResolvedPayoutData(payoutProfile, requisites);
    }

    private WithdrawalMethod loadActiveMethod(Long withdrawalMethodId) {
        return withdrawalMethodRepository.findByIdAndActiveTrue(withdrawalMethodId)
            .orElseThrow(() -> notFound("WITHDRAWAL_METHOD_NOT_FOUND", "Withdrawal method not found"));
    }

    private void validateMethodCurrency(WithdrawalMethod method, String currencyCode) {
        if (!method.getCurrencyCode().equalsIgnoreCase(currencyCode)) {
            throw validationError("Withdrawal method does not match requested currency");
        }
    }

    private void validateMinAmount(WithdrawalMethod method, BigDecimal amount) {
        if (method.getMinAmount() != null && amount.compareTo(method.getMinAmount()) < 0) {
            throw validationError("Withdrawal amount is below the method minimum");
        }
    }

    private void releaseReservedAmount(WithdrawalRequest request) {
        userAccountRepository.findByIdForUpdate(request.getUserAccountId())
            .orElseThrow(() -> conflict("USER_ACCOUNT_NOT_FOUND", "Withdrawal wallet not found"));
        int updatedRows = userAccountRepository.decreaseReserved(request.getUserAccountId(), request.getAmount());
        if (updatedRows != 1) {
            throw conflict("RESERVE_RELEASE_FAILED", "Failed to release reserved balance");
        }
    }

    private WithdrawalRequest lockRequest(String requestCode) {
        return withdrawalRequestRepository.findByPublicCodeForUpdate(requestCode)
            .orElseThrow(() -> notFound("WITHDRAWAL_REQUEST_NOT_FOUND", "Withdrawal request not found"));
    }

    private BigDecimal resolveSameCurrencyTreasuryAmount(
        TreasuryAccount treasuryAccount,
        BigDecimal plannedUserAmount,
        String requestCurrencyCode
    ) {
        if (!treasuryAccount.getCurrencyCode().equalsIgnoreCase(requestCurrencyCode)) {
            throw validationError(
                "treasury_amount is required when treasury account currency differs from withdrawal currency"
            );
        }
        return plannedUserAmount;
    }

    private void record(
        WithdrawalRequest request,
        MoneyOperationEventType eventType,
        WithdrawalRequestStatus statusBefore,
        MoneyOperationActorType actorType,
        Long actorUserId,
        String publicNote,
        String operatorNote,
        Map<String, Object> payload
    ) {
        moneyOperationEventService.record(
            MoneyOperationType.WITHDRAWAL_REQUEST,
            request.getId(),
            request.getPublicCode(),
            eventType,
            statusBefore == null ? null : statusBefore.name(),
            request.getStatus() == null ? null : request.getStatus().name(),
            actorType,
            actorUserId,
            publicNote,
            operatorNote,
            payload
        );
    }

    private void appendTreasuryPayload(Map<String, Object> payload, TreasuryTransaction transaction) {
        if (transaction == null) {
            return;
        }
        payload.put("treasury_transaction_id", transaction.getId());
        payload.put("treasury_account_id", transaction.getTreasuryAccount().getId());
        payload.put("treasury_account_code", transaction.getTreasuryAccount().getCode());
        payload.put("treasury_amount", transaction.getAmount());
        payload.put("treasury_currency_code", transaction.getTreasuryAccount().getCurrencyCode());
    }

    private void appendPayoutPlanPayload(Map<String, Object> payload, WithdrawalPayoutPlan payoutPlan) {
        if (payoutPlan == null) {
            return;
        }
        payload.put("payout_plan_id", payoutPlan.getId());
        payload.put("payout_plan_status", payoutPlan.getStatus().name());
        payload.put("planned_treasury_amount", payoutPlan.getTreasuryAmount());
        payload.put("planned_treasury_currency_code", payoutPlan.getTreasuryCurrencyCodeSnapshot());
    }

    private WithdrawalRequestStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return WithdrawalRequestStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw validationError("Unknown status " + status);
        }
    }

    private Set<WithdrawalRequestStatus> parseStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }

        Set<WithdrawalRequestStatus> parsed = EnumSet.noneOf(WithdrawalRequestStatus.class);
        for (String raw : statuses) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String token : raw.split(",")) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    parsed.add(WithdrawalRequestStatus.valueOf(trimmed.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    throw validationError("Unknown status " + trimmed);
                }
            }
        }
        return parsed;
    }

    private String normalizeCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw validationError("Field 'currency_code' is required");
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null) {
            throw validationError("Amount is required");
        }
        return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw validationError("Field '%s' is required".formatted(fieldName));
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ApiProblemException validationError(String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail);
    }

    private ApiProblemException notFound(String code, String detail) {
        return new ApiProblemException(HttpStatus.NOT_FOUND, code, detail);
    }

    private ApiProblemException conflict(String code, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, code, detail);
    }

    private record ResolvedPayoutData(
        PayoutProfile payoutProfile,
        Map<String, Object> requisites
    ) {
    }
}

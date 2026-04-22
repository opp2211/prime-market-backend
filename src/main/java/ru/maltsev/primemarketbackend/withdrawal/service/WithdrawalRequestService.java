package ru.maltsev.primemarketbackend.withdrawal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import ru.maltsev.primemarketbackend.withdrawal.api.dto.ConfirmWithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.CreateWithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.RejectWithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.domain.PayoutProfile;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalMethod;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequestStatus;
import ru.maltsev.primemarketbackend.withdrawal.repository.WithdrawalMethodRepository;
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
        return withdrawalRequestRepository.save(withdrawalRequest);
    }

    @Transactional(readOnly = true)
    public WithdrawalRequest getForUser(UUID publicId, Long userId) {
        return withdrawalRequestRepository.findByPublicIdAndUserId(publicId, userId)
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
    public WithdrawalRequest cancel(UUID publicId, Long userId) {
        WithdrawalRequest request = withdrawalRequestRepository.findByPublicIdAndUserIdForUpdate(publicId, userId)
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

        releaseReservedAmount(request);
        request.cancel(Instant.now());
        return withdrawalRequestRepository.save(request);
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
    public WithdrawalRequest getForBackoffice(UUID publicId) {
        return withdrawalRequestRepository.findByPublicId(publicId)
            .orElseThrow(() -> notFound("WITHDRAWAL_REQUEST_NOT_FOUND", "Withdrawal request not found"));
    }

    @Transactional
    public WithdrawalRequest take(UUID publicId, Long actorUserId) {
        WithdrawalRequest request = lockRequest(publicId);
        if (request.isProcessing() && actorUserId.equals(request.getProcessedByUserId())) {
            return request;
        }
        if (!request.isOpen()) {
            throw conflict(
                "INVALID_STATUS",
                "Cannot take withdrawal request in status " + request.getStatus()
            );
        }

        request.markProcessing(actorUserId, Instant.now());
        return withdrawalRequestRepository.save(request);
    }

    @Transactional
    public WithdrawalRequest reject(UUID publicId, Long actorUserId, RejectWithdrawalRequest payload) {
        WithdrawalRequest request = lockRequest(publicId);
        if (request.isRejected()) {
            return request;
        }
        if (!(request.isOpen() || request.isProcessing())) {
            throw conflict(
                "INVALID_STATUS",
                "Cannot reject withdrawal request in status " + request.getStatus()
            );
        }

        releaseReservedAmount(request);
        request.reject(
            actorUserId,
            normalizeRequired(payload.rejectionReason(), "rejection_reason"),
            normalizeOptional(payload.operatorComment()),
            Instant.now()
        );
        return withdrawalRequestRepository.save(request);
    }

    @Transactional
    public WithdrawalRequest confirm(UUID publicId, Long actorUserId, ConfirmWithdrawalRequest payload) {
        WithdrawalRequest request = lockRequest(publicId);
        if (request.isCompleted()) {
            return request;
        }
        if (!request.isProcessing()) {
            throw conflict(
                "INVALID_STATUS",
                "Cannot confirm withdrawal request in status " + request.getStatus()
            );
        }

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
        request.confirm(
            actorUserId,
            actualPayoutAmount,
            payload == null ? null : normalizeOptional(payload.operatorComment()),
            Instant.now()
        );
        return withdrawalRequestRepository.save(request);
    }

    private ResolvedPayoutData resolvePayoutData(Long userId, WithdrawalMethod method, CreateWithdrawalRequest request) {
        if (request.payoutProfilePublicId() != null) {
            if (request.requisites() != null && !request.requisites().isEmpty()) {
                throw validationError("Use either 'payout_profile_public_id' or 'requisites'");
            }
            if (Boolean.TRUE.equals(request.savePayoutProfile())) {
                throw validationError("Existing payout profile cannot be saved again");
            }

            PayoutProfile profile = payoutProfileService.getActiveForUser(request.payoutProfilePublicId(), userId);
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

    private WithdrawalRequest lockRequest(UUID publicId) {
        return withdrawalRequestRepository.findByPublicIdForUpdate(publicId)
            .orElseThrow(() -> notFound("WITHDRAWAL_REQUEST_NOT_FOUND", "Withdrawal request not found"));
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

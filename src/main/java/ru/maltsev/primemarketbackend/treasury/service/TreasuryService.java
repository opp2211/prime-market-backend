package ru.maltsev.primemarketbackend.treasury.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.currency.repository.CurrencyRepository;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationType;
import ru.maltsev.primemarketbackend.treasury.api.dto.CreateTreasuryAccountRequest;
import ru.maltsev.primemarketbackend.treasury.api.dto.CreateTreasuryTransactionRequest;
import ru.maltsev.primemarketbackend.treasury.api.dto.CreateTreasuryTransferRequest;
import ru.maltsev.primemarketbackend.treasury.api.dto.UpdateTreasuryAccountRequest;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryAccount;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryTransaction;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryTransactionType;
import ru.maltsev.primemarketbackend.treasury.repository.TreasuryAccountRepository;
import ru.maltsev.primemarketbackend.treasury.repository.TreasuryTransactionRepository;

@Service
@RequiredArgsConstructor
public class TreasuryService {
    private static final int MONEY_SCALE = 4;

    private final TreasuryAccountRepository treasuryAccountRepository;
    private final TreasuryTransactionRepository treasuryTransactionRepository;
    private final CurrencyRepository currencyRepository;

    @Transactional(readOnly = true)
    public List<TreasuryAccount> listAccounts(Boolean activeOnly) {
        if (Boolean.TRUE.equals(activeOnly)) {
            return treasuryAccountRepository.findAllByActiveTrueOrderByCurrencyCodeAscTitleAscIdAsc();
        }
        return treasuryAccountRepository.findAllByOrderByCurrencyCodeAscTitleAscIdAsc();
    }

    @Transactional
    public TreasuryAccount createAccount(CreateTreasuryAccountRequest request) {
        String code = normalizeCode(request.code());
        String currencyCode = normalizeCurrencyCode(request.currencyCode());
        if (!currencyRepository.existsByCodeIgnoreCaseAndActiveTrue(currencyCode)) {
            throw validationError("Unknown or inactive currency " + currencyCode);
        }
        if (treasuryAccountRepository.existsByCodeIgnoreCase(code)) {
            throw conflict("TREASURY_ACCOUNT_CODE_EXISTS", "Treasury account code already exists");
        }

        TreasuryAccount account = new TreasuryAccount(
            code,
            request.title(),
            currencyCode,
            request.accountType(),
            request.details(),
            request.note(),
            request.active() == null || request.active()
        );
        try {
            return treasuryAccountRepository.save(account);
        } catch (DataIntegrityViolationException ex) {
            throw conflict("TREASURY_ACCOUNT_CREATE_FAILED", "Treasury account cannot be created");
        }
    }

    @Transactional
    public TreasuryAccount updateAccount(UUID publicId, UpdateTreasuryAccountRequest request) {
        TreasuryAccount account = getAccountForUpdate(publicId);
        account.update(
            request.title(),
            request.accountType(),
            request.details(),
            request.note(),
            request.active()
        );
        return treasuryAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Page<TreasuryTransaction> listTransactions(UUID accountPublicId, Pageable pageable) {
        if (accountPublicId == null) {
            return treasuryTransactionRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);
        }
        return treasuryTransactionRepository.findAllByTreasuryAccountPublicIdOrderByCreatedAtDescIdDesc(
            accountPublicId,
            pageable
        );
    }

    @Transactional(readOnly = true)
    public List<TreasuryTransaction> listOperationTransactions(
        MoneyOperationType operationType,
        UUID operationPublicId
    ) {
        return treasuryTransactionRepository.findAllByOperationTypeAndOperationPublicIdOrderByCreatedAtAscIdAsc(
            operationType,
            operationPublicId
        );
    }

    @Transactional
    public TreasuryTransaction recordManualTransaction(CreateTreasuryTransactionRequest request, Long actorUserId) {
        TreasuryAccount account = getActiveAccountForUpdate(request.treasuryAccountPublicId());
        TreasuryTransactionType type = request.transactionType();
        if (type != TreasuryTransactionType.MANUAL_IN
            && type != TreasuryTransactionType.MANUAL_OUT
            && type != TreasuryTransactionType.ADJUSTMENT) {
            throw validationError("Manual treasury endpoint accepts only MANUAL_IN, MANUAL_OUT or ADJUSTMENT");
        }

        BigDecimal signedAmount = resolveManualAmount(type, request.amount());
        return saveTransaction(
            null,
            account,
            signedAmount,
            type,
            null,
            null,
            null,
            request.externalReference(),
            request.description(),
            request.operatorComment(),
            actorUserId,
            request.metadata()
        );
    }

    @Transactional
    public List<TreasuryTransaction> recordTransfer(CreateTreasuryTransferRequest request, Long actorUserId) {
        if (request.fromAccountPublicId().equals(request.toAccountPublicId())) {
            throw validationError("Transfer source and destination must be different");
        }

        TreasuryAccount from = getActiveAccountForUpdate(request.fromAccountPublicId());
        TreasuryAccount to = getActiveAccountForUpdate(request.toAccountPublicId());
        UUID groupPublicId = UUID.randomUUID();
        Map<String, Object> metadata = normalizeMetadata(request.metadata());
        metadata.put("from_currency_code", from.getCurrencyCode());
        metadata.put("to_currency_code", to.getCurrencyCode());

        TreasuryTransaction outgoing = saveTransaction(
            groupPublicId,
            from,
            normalizePositiveMoney(request.fromAmount()).negate(),
            TreasuryTransactionType.TRANSFER_OUT,
            null,
            null,
            null,
            request.externalReference(),
            request.description(),
            request.operatorComment(),
            actorUserId,
            metadata
        );
        TreasuryTransaction incoming = saveTransaction(
            groupPublicId,
            to,
            normalizePositiveMoney(request.toAmount()),
            TreasuryTransactionType.TRANSFER_IN,
            null,
            null,
            null,
            request.externalReference(),
            request.description(),
            request.operatorComment(),
            actorUserId,
            metadata
        );

        return List.of(outgoing, incoming);
    }

    @Transactional
    public TreasuryTransaction recordDepositIn(
        UUID treasuryAccountPublicId,
        BigDecimal treasuryAmount,
        BigDecimal requestAmount,
        String requestCurrencyCode,
        Long operationId,
        UUID operationPublicId,
        String externalReference,
        String operatorComment,
        Long actorUserId,
        Map<String, Object> metadata
    ) {
        if (treasuryAccountPublicId == null) {
            return null;
        }
        TreasuryAccount account = getActiveAccountForUpdate(treasuryAccountPublicId);
        BigDecimal amount = resolveOperationTreasuryAmount(
            account,
            treasuryAmount,
            requestAmount,
            requestCurrencyCode
        );
        return saveTransaction(
            null,
            account,
            amount,
            TreasuryTransactionType.DEPOSIT_IN,
            MoneyOperationType.DEPOSIT_REQUEST,
            operationId,
            operationPublicId,
            externalReference,
            "Deposit request confirmation",
            operatorComment,
            actorUserId,
            metadata
        );
    }

    @Transactional
    public TreasuryTransaction recordWithdrawalOut(
        UUID treasuryAccountPublicId,
        BigDecimal treasuryAmount,
        BigDecimal requestAmount,
        String requestCurrencyCode,
        Long operationId,
        UUID operationPublicId,
        String externalReference,
        String operatorComment,
        Long actorUserId,
        Map<String, Object> metadata
    ) {
        if (treasuryAccountPublicId == null) {
            return null;
        }
        TreasuryAccount account = getActiveAccountForUpdate(treasuryAccountPublicId);
        BigDecimal amount = resolveOperationTreasuryAmount(
            account,
            treasuryAmount,
            requestAmount,
            requestCurrencyCode
        ).negate();
        return saveTransaction(
            null,
            account,
            amount,
            TreasuryTransactionType.WITHDRAWAL_OUT,
            MoneyOperationType.WITHDRAWAL_REQUEST,
            operationId,
            operationPublicId,
            externalReference,
            "Withdrawal request payout",
            operatorComment,
            actorUserId,
            metadata
        );
    }

    private TreasuryTransaction saveTransaction(
        UUID groupPublicId,
        TreasuryAccount account,
        BigDecimal amount,
        TreasuryTransactionType transactionType,
        MoneyOperationType operationType,
        Long operationId,
        UUID operationPublicId,
        String externalReference,
        String description,
        String operatorComment,
        Long actorUserId,
        Map<String, Object> metadata
    ) {
        BigDecimal normalizedAmount = normalizeMoney(amount);
        if (normalizedAmount.signum() == 0) {
            throw validationError("Treasury transaction amount must be non-zero");
        }
        TreasuryTransaction transaction = new TreasuryTransaction(
            groupPublicId,
            account,
            normalizedAmount,
            transactionType,
            operationType,
            operationId,
            operationPublicId,
            externalReference,
            description,
            operatorComment,
            actorUserId,
            normalizeMetadata(metadata)
        );
        return treasuryTransactionRepository.save(transaction);
    }

    private TreasuryAccount getActiveAccountForUpdate(UUID publicId) {
        TreasuryAccount account = getAccountForUpdate(publicId);
        if (!account.isActive()) {
            throw conflict("TREASURY_ACCOUNT_INACTIVE", "Treasury account is inactive");
        }
        return account;
    }

    private TreasuryAccount getAccountForUpdate(UUID publicId) {
        if (publicId == null) {
            throw validationError("Treasury account public id is required");
        }
        return treasuryAccountRepository.findByPublicIdForUpdate(publicId)
            .orElseThrow(() -> notFound("TREASURY_ACCOUNT_NOT_FOUND", "Treasury account not found"));
    }

    private BigDecimal resolveOperationTreasuryAmount(
        TreasuryAccount account,
        BigDecimal treasuryAmount,
        BigDecimal requestAmount,
        String requestCurrencyCode
    ) {
        if (treasuryAmount != null) {
            return normalizePositiveMoney(treasuryAmount);
        }
        if (account.getCurrencyCode().equalsIgnoreCase(normalizeCurrencyCode(requestCurrencyCode))) {
            return normalizePositiveMoney(requestAmount);
        }
        throw validationError("treasury_amount is required when treasury account currency differs from request currency");
    }

    private BigDecimal resolveManualAmount(TreasuryTransactionType type, BigDecimal amount) {
        BigDecimal normalized = normalizeMoney(amount);
        if (normalized.signum() == 0) {
            throw validationError("Treasury transaction amount must be non-zero");
        }
        if (type == TreasuryTransactionType.MANUAL_IN) {
            return normalized.abs();
        }
        if (type == TreasuryTransactionType.MANUAL_OUT) {
            return normalized.abs().negate();
        }
        return normalized;
    }

    private BigDecimal normalizePositiveMoney(BigDecimal amount) {
        BigDecimal normalized = normalizeMoney(amount);
        if (normalized.signum() <= 0) {
            throw validationError("Treasury amount must be positive");
        }
        return normalized;
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null) {
            throw validationError("Treasury amount is required");
        }
        try {
            return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw validationError("Treasury amount scale must be 4 or less");
        }
    }

    private String normalizeCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw validationError("Currency code is required");
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw validationError("Treasury account code is required");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> normalizeMetadata(Map<String, Object> metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                if (key != null && value != null) {
                    result.put(key, value);
                }
            });
        }
        return result;
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
}

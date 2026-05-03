package ru.maltsev.primemarketbackend.platform.service;

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
import ru.maltsev.primemarketbackend.platform.api.dto.CreatePlatformAccountAdjustmentRequest;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccount;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccountCode;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccountTransaction;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccountTransactionType;
import ru.maltsev.primemarketbackend.platform.repository.PlatformAccountRepository;
import ru.maltsev.primemarketbackend.platform.repository.PlatformAccountTransactionRepository;

@Service
@RequiredArgsConstructor
public class PlatformAccountService {
    private static final int MONEY_SCALE = 4;
    public static final String REF_TYPE_USER_CURRENCY_CONVERSION = "USER_CURRENCY_CONVERSION";

    private final PlatformAccountRepository platformAccountRepository;
    private final PlatformAccountTransactionRepository platformAccountTransactionRepository;
    private final CurrencyRepository currencyRepository;

    @Transactional(readOnly = true)
    public List<PlatformAccount> listAccounts() {
        return platformAccountRepository.findAllByOrderByCurrencyCodeAscAccountCodeAscIdAsc();
    }

    @Transactional(readOnly = true)
    public Page<PlatformAccountTransaction> listTransactions(Pageable pageable) {
        return platformAccountTransactionRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);
    }

    @Transactional
    public PlatformAccount getOrCreateAccountForUpdate(String currencyCode) {
        String normalizedCurrency = normalizeCurrencyCode(currencyCode);
        return platformAccountRepository.findByCurrencyCodeIgnoreCase(normalizedCurrency)
            .orElseGet(() -> createAccount(
                PlatformAccountCode.FEES,
                normalizedCurrency,
                "Platform fees " + normalizedCurrency
            ));
    }

    @Transactional
    public PlatformAccountTransaction recordAdjustment(
        CreatePlatformAccountAdjustmentRequest request,
        Long actorUserId
    ) {
        PlatformAccount account = platformAccountRepository.findByPublicId(request.platformAccountPublicId())
            .orElseThrow(() -> notFound("PLATFORM_ACCOUNT_NOT_FOUND", "Platform account not found"));
        if (!account.isActive()) {
            throw conflict("PLATFORM_ACCOUNT_INACTIVE", "Platform account is inactive");
        }
        PlatformAccountTransactionType type = request.transactionType();
        if (type != PlatformAccountTransactionType.ADJUSTMENT
            && type != PlatformAccountTransactionType.MANUAL
            && type != PlatformAccountTransactionType.ROUNDING) {
            throw validationError("Only ADJUSTMENT, MANUAL or ROUNDING can be recorded manually");
        }
        return saveTransaction(
            null,
            account,
            normalizeMoney(request.amount()),
            type,
            null,
            null,
            null,
            request.description(),
            actorUserId,
            request.metadata()
        );
    }

    @Transactional
    public List<PlatformAccountTransaction> recordUserFxConversion(
        Long conversionId,
        UUID conversionPublicId,
        String fromCurrencyCode,
        BigDecimal fromAmount,
        String toCurrencyCode,
        BigDecimal toAmount,
        BigDecimal rate,
        Long actorUserId
    ) {
        PlatformAccount fromAccount = getOrCreateAccountForUpdate(
            PlatformAccountCode.FX_DESK,
            fromCurrencyCode,
            "FX desk " + normalizeCurrencyCode(fromCurrencyCode)
        );
        PlatformAccount toAccount = getOrCreateAccountForUpdate(
            PlatformAccountCode.FX_DESK,
            toCurrencyCode,
            "FX desk " + normalizeCurrencyCode(toCurrencyCode)
        );
        UUID groupPublicId = UUID.randomUUID();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("from_currency_code", normalizeCurrencyCode(fromCurrencyCode));
        metadata.put("to_currency_code", normalizeCurrencyCode(toCurrencyCode));
        metadata.put("from_amount", normalizeMoney(fromAmount));
        metadata.put("to_amount", normalizeMoney(toAmount));
        metadata.put("rate", rate);

        PlatformAccountTransaction incoming = saveTransaction(
            groupPublicId,
            fromAccount,
            normalizeMoney(fromAmount),
            PlatformAccountTransactionType.FX_IN,
            REF_TYPE_USER_CURRENCY_CONVERSION,
            conversionId,
            conversionPublicId,
            "User virtual FX conversion source leg",
            actorUserId,
            metadata
        );
        PlatformAccountTransaction outgoing = saveTransaction(
            groupPublicId,
            toAccount,
            normalizeMoney(toAmount).negate(),
            PlatformAccountTransactionType.FX_OUT,
            REF_TYPE_USER_CURRENCY_CONVERSION,
            conversionId,
            conversionPublicId,
            "User virtual FX conversion target leg",
            actorUserId,
            metadata
        );
        return List.of(incoming, outgoing);
    }

    private PlatformAccount getOrCreateAccountForUpdate(
        PlatformAccountCode accountCode,
        String currencyCode,
        String title
    ) {
        String normalizedCurrency = normalizeCurrencyCode(currencyCode);
        return platformAccountRepository.findByAccountCodeAndCurrencyCodeForUpdate(accountCode, normalizedCurrency)
            .orElseGet(() -> createAccount(accountCode, normalizedCurrency, title));
    }

    private PlatformAccount createAccount(PlatformAccountCode accountCode, String currencyCode, String title) {
        if (!currencyRepository.existsByCodeIgnoreCaseAndActiveTrue(currencyCode)) {
            throw validationError("Unknown or inactive currency " + currencyCode);
        }
        try {
            return platformAccountRepository.saveAndFlush(new PlatformAccount(accountCode, currencyCode, title, null));
        } catch (DataIntegrityViolationException ex) {
            return platformAccountRepository.findByAccountCodeAndCurrencyCodeForUpdate(accountCode, currencyCode)
                .orElseThrow(() -> ex);
        }
    }

    private PlatformAccountTransaction saveTransaction(
        UUID groupPublicId,
        PlatformAccount account,
        BigDecimal amount,
        PlatformAccountTransactionType transactionType,
        String refType,
        Long refId,
        UUID refPublicId,
        String description,
        Long actorUserId,
        Map<String, Object> metadata
    ) {
        BigDecimal normalizedAmount = normalizeMoney(amount);
        if (normalizedAmount.signum() == 0) {
            throw validationError("Platform account transaction amount must be non-zero");
        }
        return platformAccountTransactionRepository.save(new PlatformAccountTransaction(
            groupPublicId,
            account,
            normalizedAmount,
            transactionType,
            refType,
            refId,
            refPublicId,
            description,
            actorUserId,
            normalizeMetadata(metadata)
        ));
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null) {
            throw validationError("Amount is required");
        }
        try {
            return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw validationError("Amount scale must be 4 or less");
        }
    }

    private String normalizeCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw validationError("Currency code is required");
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
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

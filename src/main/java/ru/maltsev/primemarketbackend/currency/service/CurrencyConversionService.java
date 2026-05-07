package ru.maltsev.primemarketbackend.currency.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.domain.UserAccountTx;
import ru.maltsev.primemarketbackend.account.repository.UserAccountRepository;
import ru.maltsev.primemarketbackend.account.repository.UserAccountTxRepository;
import ru.maltsev.primemarketbackend.account.service.UserAccountService;
import ru.maltsev.primemarketbackend.currency.api.dto.CreateCurrencyConversionRequest;
import ru.maltsev.primemarketbackend.currency.domain.CurrencyRate;
import ru.maltsev.primemarketbackend.currency.domain.UserCurrencyConversion;
import ru.maltsev.primemarketbackend.currency.repository.CurrencyRateRepository;
import ru.maltsev.primemarketbackend.currency.repository.CurrencyRepository;
import ru.maltsev.primemarketbackend.currency.repository.UserCurrencyConversionRepository;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.platform.service.PlatformAccountService;

@Service
@RequiredArgsConstructor
public class CurrencyConversionService {
    private static final int MONEY_SCALE = 4;
    private static final int RATE_SCALE = 8;
    private static final String TX_TYPE_CONVERSION_DEBIT = "CURRENCY_CONVERSION_DEBIT";
    private static final String TX_TYPE_CONVERSION_CREDIT = "CURRENCY_CONVERSION_CREDIT";
    private static final String REF_TYPE_CONVERSION = "USER_CURRENCY_CONVERSION";

    private final CurrencyRepository currencyRepository;
    private final CurrencyRateRepository currencyRateRepository;
    private final UserCurrencyConversionRepository conversionRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserAccountTxRepository userAccountTxRepository;
    private final UserAccountService userAccountService;
    private final PlatformAccountService platformAccountService;

    @Transactional
    public UserCurrencyConversion convert(Long userId, CreateCurrencyConversionRequest request) {
        String fromCurrencyCode = normalizeCurrencyCode(request.fromCurrencyCode());
        String toCurrencyCode = normalizeCurrencyCode(request.toCurrencyCode());
        if (fromCurrencyCode.equals(toCurrencyCode)) {
            throw validationError("Conversion currencies must be different");
        }
        requireActiveCurrency(fromCurrencyCode);
        requireActiveCurrency(toCurrencyCode);

        BigDecimal fromAmount = normalizePositiveMoney(request.fromAmount());
        ResolvedRate resolvedRate = resolveRate(fromCurrencyCode, toCurrencyCode);
        BigDecimal toAmount = fromAmount.multiply(resolvedRate.rate()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (toAmount.signum() <= 0) {
            throw validationError("Converted amount is too small");
        }

        UserAccount fromAccount = userAccountRepository
            .findByUserIdAndCurrencyCodeIgnoreCase(userId, fromCurrencyCode)
            .orElseThrow(() -> conflict("SOURCE_WALLET_NOT_FOUND", "Source wallet not found"));
        if (fromAccount.available().compareTo(fromAmount) < 0) {
            throw conflict("INSUFFICIENT_FUNDS", "Insufficient available balance");
        }
        UserAccount toAccount = userAccountService.getOrCreateAccountForUpdate(userId, toCurrencyCode);

        UserCurrencyConversion conversion = conversionRepository.saveAndFlush(new UserCurrencyConversion(
            userId,
            fromAccount.getId(),
            toAccount.getId(),
            fromCurrencyCode,
            toCurrencyCode,
            fromAmount,
            toAmount,
            resolvedRate.rate(),
            resolvedRate.source()
        ));

        userAccountTxRepository.save(new UserAccountTx(
            fromAccount,
            fromAmount.negate(),
            TX_TYPE_CONVERSION_DEBIT,
            REF_TYPE_CONVERSION,
            conversion.getId()
        ));
        userAccountTxRepository.save(new UserAccountTx(
            toAccount,
            toAmount,
            TX_TYPE_CONVERSION_CREDIT,
            REF_TYPE_CONVERSION,
            conversion.getId()
        ));
        platformAccountService.recordUserFxConversion(
            conversion.getId(),
            conversion.getPublicCode(),
            fromCurrencyCode,
            fromAmount,
            toCurrencyCode,
            toAmount,
            resolvedRate.rate(),
            userId
        );
        return conversion;
    }

    private ResolvedRate resolveRate(String fromCurrencyCode, String toCurrencyCode) {
        return currencyRateRepository
            .findByFromCurrencyCodeIgnoreCaseAndToCurrencyCodeIgnoreCase(fromCurrencyCode, toCurrencyCode)
            .map(rate -> new ResolvedRate(rate.getRate().setScale(RATE_SCALE, RoundingMode.UNNECESSARY), rate.getSource()))
            .or(() -> currencyRateRepository
                .findByFromCurrencyCodeIgnoreCaseAndToCurrencyCodeIgnoreCase(toCurrencyCode, fromCurrencyCode)
                .map(this::inverseRate))
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "CURRENCY_RATE_NOT_FOUND",
                "Currency rate not found"
            ));
    }

    private ResolvedRate inverseRate(CurrencyRate rate) {
        return new ResolvedRate(
            BigDecimal.ONE.divide(rate.getRate(), RATE_SCALE, RoundingMode.HALF_UP),
            limitRateSource(rate.getSource() + ":inverse")
        );
    }

    private String limitRateSource(String source) {
        if (source == null || source.isBlank()) {
            return "system";
        }
        return source.length() <= 32 ? source : source.substring(0, 32);
    }

    private void requireActiveCurrency(String currencyCode) {
        if (!currencyRepository.existsByCodeIgnoreCaseAndActiveTrue(currencyCode)) {
            throw validationError("Unknown or inactive currency " + currencyCode);
        }
    }

    private BigDecimal normalizePositiveMoney(BigDecimal amount) {
        if (amount == null) {
            throw validationError("Amount is required");
        }
        try {
            BigDecimal normalized = amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0) {
                throw validationError("Amount must be positive");
            }
            return normalized;
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

    private ApiProblemException validationError(String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail);
    }

    private ApiProblemException conflict(String code, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, code, detail);
    }

    private record ResolvedRate(BigDecimal rate, String source) {
    }
}

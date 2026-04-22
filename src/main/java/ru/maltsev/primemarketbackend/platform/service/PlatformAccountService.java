package ru.maltsev.primemarketbackend.platform.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccount;
import ru.maltsev.primemarketbackend.platform.repository.PlatformAccountRepository;

@Service
@RequiredArgsConstructor
public class PlatformAccountService {
    private final PlatformAccountRepository platformAccountRepository;

    @Transactional
    public PlatformAccount getOrCreateAccountForUpdate(String currencyCode) {
        String normalizedCurrencyCode = normalizeCurrencyCode(currencyCode);
        return platformAccountRepository.findByCurrencyCodeIgnoreCase(normalizedCurrencyCode)
            .orElseGet(() -> createAccount(normalizedCurrencyCode));
    }

    private PlatformAccount createAccount(String currencyCode) {
        PlatformAccount account = new PlatformAccount(currencyCode);
        try {
            return platformAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            return platformAccountRepository.findByCurrencyCodeIgnoreCase(currencyCode)
                .orElseThrow(() -> new ApiProblemException(
                    HttpStatus.CONFLICT,
                    "PLATFORM_ACCOUNT_ERROR",
                    "Failed to create platform account"
                ));
        }
    }

    private String normalizeCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "PLATFORM_ACCOUNT_ERROR",
                "Platform account currency code is required"
            );
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }
}

package ru.maltsev.primemarketbackend.currency.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.currency.api.dto.CurrencyResponse;
import ru.maltsev.primemarketbackend.currency.api.dto.CurrencyRateResponse;
import ru.maltsev.primemarketbackend.currency.repository.CurrencyRepository;
import ru.maltsev.primemarketbackend.currency.repository.CurrencyRateRepository;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;

@Service
@RequiredArgsConstructor
public class CurrencyService {
    private final CurrencyRepository currencyRepository;
    private final CurrencyRateRepository currencyRateRepository;

    public List<CurrencyResponse> getActiveCurrencies() {
        return currencyRepository.findAllByActiveTrueOrderBySortOrderAscCodeAsc()
            .stream()
            .map(CurrencyResponse::from)
            .toList();
    }

    public CurrencyRateResponse getCurrencyRate(String fromCurrencyCode, String toCurrencyCode) {
        String normalizedFrom = normalizeCurrencyCode(fromCurrencyCode, "from");
        String normalizedTo = normalizeCurrencyCode(toCurrencyCode, "to");

        return currencyRateRepository
            .findByFromCurrencyCodeIgnoreCaseAndToCurrencyCodeIgnoreCase(normalizedFrom, normalizedTo)
            .map(CurrencyRateResponse::from)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "CURRENCY_RATE_NOT_FOUND",
                "Currency rate not found"
            ));
    }

    private String normalizeCurrencyCode(String currencyCode, String parameterName) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Query parameter '%s' is required".formatted(parameterName)
            );
        }
        return currencyCode.trim().toUpperCase();
    }
}

package ru.maltsev.primemarketbackend.currency.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import ru.maltsev.primemarketbackend.currency.domain.CurrencyRate;

public record CurrencyRateResponse(
    String fromCurrencyCode,
    String toCurrencyCode,
    BigDecimal rate,
    String source,
    Instant updatedAt
) {
    public static CurrencyRateResponse from(CurrencyRate currencyRate) {
        return new CurrencyRateResponse(
            currencyRate.getFromCurrencyCode(),
            currencyRate.getToCurrencyCode(),
            currencyRate.getRate(),
            currencyRate.getSource(),
            currencyRate.getUpdatedAt()
        );
    }
}

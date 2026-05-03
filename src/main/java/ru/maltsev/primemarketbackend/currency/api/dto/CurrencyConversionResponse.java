package ru.maltsev.primemarketbackend.currency.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ru.maltsev.primemarketbackend.currency.domain.UserCurrencyConversion;

public record CurrencyConversionResponse(
    @JsonProperty("public_id") UUID publicId,
    @JsonProperty("from_currency_code") String fromCurrencyCode,
    @JsonProperty("to_currency_code") String toCurrencyCode,
    @JsonProperty("from_amount") BigDecimal fromAmount,
    @JsonProperty("to_amount") BigDecimal toAmount,
    BigDecimal rate,
    @JsonProperty("rate_source") String rateSource,
    String status,
    @JsonProperty("created_at") Instant createdAt
) {
    public static CurrencyConversionResponse from(UserCurrencyConversion conversion) {
        return new CurrencyConversionResponse(
            conversion.getPublicId(),
            conversion.getFromCurrencyCode(),
            conversion.getToCurrencyCode(),
            conversion.getFromAmount(),
            conversion.getToAmount(),
            conversion.getRate(),
            conversion.getRateSource(),
            conversion.getStatus(),
            conversion.getCreatedAt()
        );
    }
}

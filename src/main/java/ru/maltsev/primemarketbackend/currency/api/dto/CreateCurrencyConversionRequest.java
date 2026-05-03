package ru.maltsev.primemarketbackend.currency.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record CreateCurrencyConversionRequest(
    @JsonProperty("from_currency_code") @NotBlank String fromCurrencyCode,
    @JsonProperty("to_currency_code") @NotBlank String toCurrencyCode,
    @JsonProperty("from_amount")
    @DecimalMin(value = "0.0001", inclusive = true)
    @Digits(integer = 9, fraction = 4)
    BigDecimal fromAmount
) {
}

package ru.maltsev.primemarketbackend.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record UpdatePrimaryCurrencyRequest(
    @JsonProperty("currency_code") @NotBlank String currencyCode
) {
}

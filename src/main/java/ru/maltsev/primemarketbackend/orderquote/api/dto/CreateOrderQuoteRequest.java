package ru.maltsev.primemarketbackend.orderquote.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateOrderQuoteRequest(
    @NotBlank String intent,
    @NotBlank String viewerCurrencyCode,
    @NotNull Long listedOfferVersion,
    @NotNull BigDecimal listedUnitPriceAmount
) {
}

package ru.maltsev.primemarketbackend.offer.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record OfferCreateRequest(
    @NotNull Long gameId,
    @NotNull Long categoryId,
    @NotNull String side,
    String title,
    String description,
    String tradeTerms,
    String priceCurrencyCode,
    BigDecimal priceAmount,
    BigDecimal quantity,
    BigDecimal minTradeQuantity,
    BigDecimal maxTradeQuantity,
    BigDecimal quantityStep,
    @NotNull String status,
    List<@Valid OfferContextRequest> contexts,
    List<@Valid OfferAttributeRequest> attributes,
    List<String> deliveryMethods
) {}

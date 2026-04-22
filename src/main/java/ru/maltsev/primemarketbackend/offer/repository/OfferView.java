package ru.maltsev.primemarketbackend.offer.repository;

import java.math.BigDecimal;
import java.time.Instant;

public record OfferView(
    Long id,
    Long gameId,
    String gameSlug,
    String gameTitle,
    Long categoryId,
    String categorySlug,
    String categoryTitle,
    String side,
    String title,
    String description,
    String tradeTerms,
    String priceCurrencyCode,
    BigDecimal priceAmount,
    BigDecimal quantity,
    BigDecimal minTradeQuantity,
    BigDecimal maxTradeQuantity,
    BigDecimal quantityStep,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant publishedAt
) {
}

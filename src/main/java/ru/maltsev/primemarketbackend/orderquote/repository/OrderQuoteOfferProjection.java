package ru.maltsev.primemarketbackend.orderquote.repository;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderQuoteOfferProjection(
    Long id,
    Long offerVersion,
    Long ownerUserId,
    String ownerUsername,
    boolean ownerActive,
    Long gameId,
    String gameSlug,
    String gameTitle,
    boolean gameActive,
    Long categoryId,
    String categorySlug,
    String categoryTitle,
    boolean categoryActive,
    String side,
    String status,
    String title,
    String description,
    String tradeTerms,
    String priceCurrencyCode,
    BigDecimal priceAmount,
    BigDecimal quantity,
    BigDecimal minTradeQuantity,
    BigDecimal maxTradeQuantity,
    BigDecimal quantityStep,
    Instant publishedAt
) {
}

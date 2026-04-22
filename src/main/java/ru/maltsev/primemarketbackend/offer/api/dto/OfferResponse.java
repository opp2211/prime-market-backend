package ru.maltsev.primemarketbackend.offer.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import ru.maltsev.primemarketbackend.category.api.dto.CategoryResponse;
import ru.maltsev.primemarketbackend.game.api.dto.GameResponse;
import ru.maltsev.primemarketbackend.offer.repository.OfferView;

public record OfferResponse(
    Long id,
    GameResponse game,
    CategoryResponse category,
    Long gameId,
    Long categoryId,
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
    public static OfferResponse from(OfferView view) {
        return new OfferResponse(
            view.id(),
            new GameResponse(view.gameId(), view.gameSlug(), view.gameTitle()),
            new CategoryResponse(view.categoryId(), view.categorySlug(), view.categoryTitle()),
            view.gameId(),
            view.categoryId(),
            view.side(),
            view.title(),
            view.description(),
            view.tradeTerms(),
            view.priceCurrencyCode(),
            view.priceAmount(),
            view.quantity(),
            view.minTradeQuantity(),
            view.maxTradeQuantity(),
            view.quantityStep(),
            view.status(),
            view.createdAt(),
            view.updatedAt(),
            view.publishedAt()
        );
    }
}

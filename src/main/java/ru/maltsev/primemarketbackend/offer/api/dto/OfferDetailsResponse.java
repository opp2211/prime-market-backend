package ru.maltsev.primemarketbackend.offer.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import ru.maltsev.primemarketbackend.category.api.dto.CategoryResponse;
import ru.maltsev.primemarketbackend.game.api.dto.GameResponse;
import ru.maltsev.primemarketbackend.offer.repository.OfferView;

public record OfferDetailsResponse(
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
    Instant publishedAt,
    List<OfferContextResponse> contexts,
    List<OfferAttributeResponse> attributes,
    List<String> deliveryMethods
) {
    public static OfferDetailsResponse from(
        OfferView offer,
        List<OfferContextResponse> contexts,
        List<OfferAttributeResponse> attributes,
        List<String> deliveryMethods
    ) {
        return new OfferDetailsResponse(
            offer.id(),
            new GameResponse(offer.gameId(), offer.gameSlug(), offer.gameTitle()),
            new CategoryResponse(offer.categoryId(), offer.categorySlug(), offer.categoryTitle()),
            offer.gameId(),
            offer.categoryId(),
            offer.side(),
            offer.title(),
            offer.description(),
            offer.tradeTerms(),
            offer.priceCurrencyCode(),
            offer.priceAmount(),
            offer.quantity(),
            offer.minTradeQuantity(),
            offer.maxTradeQuantity(),
            offer.quantityStep(),
            offer.status(),
            offer.createdAt(),
            offer.updatedAt(),
            offer.publishedAt(),
            contexts,
            attributes,
            deliveryMethods
        );
    }
}

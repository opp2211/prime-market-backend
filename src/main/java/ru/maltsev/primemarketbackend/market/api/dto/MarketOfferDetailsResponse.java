package ru.maltsev.primemarketbackend.market.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MarketOfferDetailsResponse(
    Long id,
    String side,
    String action,
    MarketOfferListResponse.Game game,
    MarketOfferListResponse.Category category,
    MarketOfferListResponse.Owner owner,
    String title,
    String description,
    String tradeTerms,
    MarketOfferListResponse.Price price,
    BigDecimal quantity,
    BigDecimal minTradeQuantity,
    BigDecimal maxTradeQuantity,
    BigDecimal quantityStep,
    List<MarketOfferListResponse.Context> contexts,
    List<MarketOfferListResponse.Attribute> attributes,
    List<MarketOfferListResponse.DeliveryMethod> deliveryMethods,
    Instant publishedAt
) {
}

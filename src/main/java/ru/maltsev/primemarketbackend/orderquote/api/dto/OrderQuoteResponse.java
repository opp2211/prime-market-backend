package ru.maltsev.primemarketbackend.orderquote.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.maltsev.primemarketbackend.market.api.dto.MarketOfferListResponse;

public record OrderQuoteResponse(
    UUID quoteId,
    Instant expiresAt,
    boolean priceChanged,
    boolean offerUpdated,
    Long id,
    Long offerVersion,
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

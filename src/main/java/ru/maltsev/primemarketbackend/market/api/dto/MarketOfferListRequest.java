package ru.maltsev.primemarketbackend.market.api.dto;

import java.util.Map;

public record MarketOfferListRequest(
    String gameSlug,
    String categorySlug,
    String intent,
    String viewerCurrencyCode,
    Map<String, String> contextFilters,
    Map<String, String> attributeFilters,
    Integer page,
    Integer size,
    String sort
) {
}

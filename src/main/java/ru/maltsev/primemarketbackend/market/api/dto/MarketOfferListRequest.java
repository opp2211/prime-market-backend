package ru.maltsev.primemarketbackend.market.api.dto;

public record MarketOfferListRequest(
    String gameSlug,
    String categorySlug,
    String intent,
    String viewerCurrencyCode,
    String platform,
    String league,
    String mode,
    String ruthless,
    String currencyType,
    Integer page,
    Integer size,
    String sort
) {
}

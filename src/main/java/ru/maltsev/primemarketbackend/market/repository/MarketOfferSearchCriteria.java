package ru.maltsev.primemarketbackend.market.repository;

import ru.maltsev.primemarketbackend.market.service.MarketIntent;
import ru.maltsev.primemarketbackend.market.service.MarketPriceSort;

public record MarketOfferSearchCriteria(
    Long gameId,
    Long categoryId,
    String offerSide,
    String viewerCurrencyCode,
    MarketIntent intent,
    MarketPriceSort sort,
    String platform,
    String league,
    String mode,
    String ruthless,
    String currencyType,
    int page,
    int size
) {
}

package ru.maltsev.primemarketbackend.market.repository;

import java.util.Map;
import ru.maltsev.primemarketbackend.market.service.MarketIntent;
import ru.maltsev.primemarketbackend.market.service.MarketPriceSort;

public record MarketOfferSearchCriteria(
    Long gameId,
    Long categoryId,
    String offerSide,
    String viewerCurrencyCode,
    MarketIntent intent,
    MarketPriceSort sort,
    Map<String, String> contextFilters,
    Map<String, String> attributeFilters,
    int page,
    int size
) {
}

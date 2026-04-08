package ru.maltsev.primemarketbackend.market.service;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;

public enum MarketPriceSort {
    PRICE_ASC("price_asc"),
    PRICE_DESC("price_desc");

    private final String value;

    MarketPriceSort(String value) {
        this.value = value;
    }

    public static MarketPriceSort resolve(String rawSort, MarketIntent intent) {
        if (rawSort == null || rawSort.isBlank()) {
            return intent == MarketIntent.BUY ? PRICE_ASC : PRICE_DESC;
        }

        String normalized = rawSort.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "price_asc" -> PRICE_ASC;
            case "price_desc" -> PRICE_DESC;
            default -> throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_MARKET_SORT",
                "Unknown market sort " + rawSort
            );
        };
    }
}

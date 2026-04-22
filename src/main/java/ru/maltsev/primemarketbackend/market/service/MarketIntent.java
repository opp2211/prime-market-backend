package ru.maltsev.primemarketbackend.market.service;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;

public enum MarketIntent {
    BUY("buy", "sell"),
    SELL("sell", "buy");

    private final String action;
    private final String offerSide;

    MarketIntent(String action, String offerSide) {
        this.action = action;
        this.offerSide = offerSide;
    }

    public String action() {
        return action;
    }

    public String offerSide() {
        return offerSide;
    }

    public static MarketIntent from(String rawIntent) {
        return from(rawIntent, "Query parameter 'intent' is required");
    }

    public static MarketIntent fromBody(String rawIntent) {
        return from(rawIntent, "Field 'intent' is required");
    }

    private static MarketIntent from(String rawIntent, String missingMessage) {
        if (rawIntent == null || rawIntent.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                missingMessage
            );
        }

        String normalized = rawIntent.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "buy" -> BUY;
            case "sell" -> SELL;
            default -> throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_MARKET_INTENT",
                "Unknown market intent " + rawIntent
            );
        };
    }
}

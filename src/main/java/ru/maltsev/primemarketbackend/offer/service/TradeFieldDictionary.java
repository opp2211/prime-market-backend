package ru.maltsev.primemarketbackend.offer.service;

import java.util.Map;

public final class TradeFieldDictionary {
    private static final Map<String, TradeFieldMeta> FIELDS = Map.of(
        "quantity", new TradeFieldMeta("Quantity", "number"),
        "min-trade-quantity", new TradeFieldMeta("Minimum Trade Quantity", "number"),
        "max-trade-quantity", new TradeFieldMeta("Maximum Trade Quantity", "number"),
        "quantity-step", new TradeFieldMeta("Quantity Step", "number"),
        "trade-terms", new TradeFieldMeta("Trade Terms", "text"),
        "delivery-methods", new TradeFieldMeta("Delivery Methods", "select")
    );

    private TradeFieldDictionary() {
    }

    public static TradeFieldMeta get(String fieldSlug, boolean multiselect) {
        TradeFieldMeta meta = FIELDS.get(fieldSlug);
        if (meta == null) {
            return new TradeFieldMeta(fieldSlug, multiselect ? "multiselect" : "text");
        }
        if ("delivery-methods".equals(fieldSlug)) {
            return new TradeFieldMeta(meta.title(), multiselect ? "multiselect" : "select");
        }
        return meta;
    }

    public record TradeFieldMeta(String title, String dataType) {
    }
}

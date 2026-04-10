package ru.maltsev.primemarketbackend.market.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MarketOfferListResponse(
    List<Item> items,
    int page,
    int size,
    long total
) {
    public record Item(
        Long id,
        Long offerVersion,
        String side,
        String action,
        Game game,
        Category category,
        Owner owner,
        String title,
        Price price,
        BigDecimal quantity,
        BigDecimal minTradeQuantity,
        BigDecimal maxTradeQuantity,
        BigDecimal quantityStep,
        List<Context> contexts,
        List<Attribute> attributes,
        List<DeliveryMethod> deliveryMethods,
        Instant publishedAt
    ) {
    }

    public record Game(
        Long id,
        String slug,
        String title
    ) {
    }

    public record Category(
        Long id,
        String slug,
        String title
    ) {
    }

    public record Owner(String username) {
    }

    public record Price(
        BigDecimal amount,
        String currencyCode,
        BigDecimal rate
    ) {
    }

    public record Context(
        String dimensionSlug,
        String valueSlug,
        String valueTitle
    ) {
    }

    public record Attribute(
        String attributeSlug,
        String optionSlug,
        String optionTitle
    ) {
    }

    public record DeliveryMethod(
        String slug,
        String title
    ) {
    }
}

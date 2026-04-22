package ru.maltsev.primemarketbackend.offer.api.dto;

import ru.maltsev.primemarketbackend.delivery.domain.DeliveryMethod;

public record OfferSchemaDeliveryMethodResponse(String slug, String title) {
    public static OfferSchemaDeliveryMethodResponse from(DeliveryMethod method) {
        String slug = method.getSlug();
        String title = method.getTitle();
        if (title == null || title.isBlank()) {
            title = slug;
        }
        return new OfferSchemaDeliveryMethodResponse(slug, title);
    }
}

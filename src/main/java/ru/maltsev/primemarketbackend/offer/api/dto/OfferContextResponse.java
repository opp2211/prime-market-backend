package ru.maltsev.primemarketbackend.offer.api.dto;

import ru.maltsev.primemarketbackend.offer.domain.OfferContextValue;

public record OfferContextResponse(String dimensionSlug, String valueSlug) {
    public static OfferContextResponse from(OfferContextValue value) {
        return new OfferContextResponse(
            value.getContextDimension().getSlug(),
            value.getContextDimensionValue().getSlug()
        );
    }
}

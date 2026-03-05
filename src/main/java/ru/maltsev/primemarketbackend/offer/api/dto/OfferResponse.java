package ru.maltsev.primemarketbackend.offer.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public record OfferResponse(
        @JsonProperty("public_id") UUID publicId,
        String side,
        BigDecimal price,
        Long quantity,
        @JsonProperty("min_quantity") Long minQuantity,
        Long multiplicity
) {
}
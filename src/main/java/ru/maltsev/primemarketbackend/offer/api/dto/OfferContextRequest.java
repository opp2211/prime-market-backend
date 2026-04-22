package ru.maltsev.primemarketbackend.offer.api.dto;

import jakarta.validation.constraints.NotBlank;

public record OfferContextRequest(
    @NotBlank String dimensionSlug,
    @NotBlank String valueSlug
) {}

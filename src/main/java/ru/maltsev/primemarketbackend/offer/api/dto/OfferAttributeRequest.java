package ru.maltsev.primemarketbackend.offer.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record OfferAttributeRequest(
    @NotBlank String attributeSlug,
    String optionSlug,
    String valueText,
    BigDecimal valueNumber,
    Boolean valueBoolean
) {}

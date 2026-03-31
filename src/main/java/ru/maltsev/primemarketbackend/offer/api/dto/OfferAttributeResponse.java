package ru.maltsev.primemarketbackend.offer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import ru.maltsev.primemarketbackend.offer.domain.OfferAttributeValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferAttributeResponse(
    String attributeSlug,
    String optionSlug,
    String valueText,
    BigDecimal valueNumber,
    Boolean valueBoolean
) {
    public static OfferAttributeResponse from(OfferAttributeValue value) {
        String optionSlug = value.getCategoryAttributeOption() == null
            ? null
            : value.getCategoryAttributeOption().getSlug();
        return new OfferAttributeResponse(
            value.getCategoryAttribute().getSlug(),
            optionSlug,
            value.getValueText(),
            value.getValueNumber(),
            value.getValueBoolean()
        );
    }
}

package ru.maltsev.primemarketbackend.offer.api.dto;

import java.math.BigDecimal;
import ru.maltsev.primemarketbackend.tradefield.domain.CategoryTradeFieldConfig;

public record OfferSchemaTradeFieldResponse(
    String fieldSlug,
    String title,
    String dataType,
    boolean isVisible,
    boolean isRequired,
    boolean isMultiselect,
    String defaultValueText,
    BigDecimal defaultValueNumber
) {
    public static OfferSchemaTradeFieldResponse from(
        CategoryTradeFieldConfig config,
        String title,
        String dataType
    ) {
        return new OfferSchemaTradeFieldResponse(
            config.getFieldSlug(),
            title,
            dataType,
            config.isVisible(),
            config.isRequired(),
            config.isMultiselect(),
            config.getDefaultValueText(),
            config.getDefaultValueNumber()
        );
    }
}

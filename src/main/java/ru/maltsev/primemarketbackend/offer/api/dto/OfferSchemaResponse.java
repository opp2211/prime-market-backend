package ru.maltsev.primemarketbackend.offer.api.dto;

import java.util.List;
import ru.maltsev.primemarketbackend.attribute.api.dto.CategoryAttributeResponse;
import ru.maltsev.primemarketbackend.context.api.dto.ContextResponse;

public record OfferSchemaResponse(
    List<ContextResponse> contexts,
    List<CategoryAttributeResponse> attributes,
    List<OfferSchemaTradeFieldResponse> tradeFields,
    List<OfferSchemaDeliveryMethodResponse> deliveryMethods
) {
}

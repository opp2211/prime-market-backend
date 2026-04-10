package ru.maltsev.primemarketbackend.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.maltsev.primemarketbackend.order.api.dto.OrderReadModelDtos.Attribute;
import ru.maltsev.primemarketbackend.order.api.dto.OrderReadModelDtos.AvailableActions;
import ru.maltsev.primemarketbackend.order.api.dto.OrderReadModelDtos.Category;
import ru.maltsev.primemarketbackend.order.api.dto.OrderReadModelDtos.Context;
import ru.maltsev.primemarketbackend.order.api.dto.OrderReadModelDtos.Counterparty;
import ru.maltsev.primemarketbackend.order.api.dto.OrderReadModelDtos.DeliveryMethod;
import ru.maltsev.primemarketbackend.order.api.dto.OrderReadModelDtos.Game;
import ru.maltsev.primemarketbackend.order.api.dto.OrderReadModelDtos.Price;

public record OrderDetailsResponse(
    Long id,
    UUID publicId,
    String status,
    String myRole,
    String counterpartyRole,
    String makerRole,
    String takerRole,
    Game game,
    Category category,
    Counterparty counterparty,
    String title,
    String description,
    String tradeTerms,
    BigDecimal orderedQuantity,
    BigDecimal deliveredQuantity,
    Price price,
    BigDecimal sellerGrossAmount,
    BigDecimal sellerFeeAmount,
    BigDecimal sellerNetAmount,
    List<Context> contexts,
    List<Attribute> attributes,
    List<DeliveryMethod> deliveryMethods,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt,
    AvailableActions availableActions
) {
}

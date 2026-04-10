package ru.maltsev.primemarketbackend.order.api.dto;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
    @NotNull UUID quoteId,
    BigDecimal quantity
) {
}

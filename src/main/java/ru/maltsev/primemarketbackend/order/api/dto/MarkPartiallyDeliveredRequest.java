package ru.maltsev.primemarketbackend.order.api.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;

public record MarkPartiallyDeliveredRequest(
    @NotNull BigDecimal deliveredQuantity
) {
}

package ru.maltsev.primemarketbackend.order.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ResolveOrderDisputeAmendQuantityRequest(
    @NotNull
    @Positive
    BigDecimal quantity,

    @Size(max = 2000)
    String note
) {
}

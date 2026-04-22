package ru.maltsev.primemarketbackend.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrderDisputeRequest(
    @NotBlank
    @Size(max = 64)
    String reasonCode,

    @NotBlank
    @Size(max = 2000)
    String description
) {
}

package ru.maltsev.primemarketbackend.order.api.dto;

import jakarta.validation.constraints.Size;

public record ResolveOrderDisputeRequest(
    @Size(max = 2000)
    String note
) {
}

package ru.maltsev.primemarketbackend.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ru.maltsev.primemarketbackend.order.domain.OrderRequest;

public record OrderRequestResponse(
    Long id,
    UUID publicId,
    Long orderId,
    String requestType,
    Long requestedByUserId,
    String requestedByRole,
    String status,
    String reason,
    BigDecimal requestedQuantity,
    Instant createdAt,
    Instant updatedAt,
    Instant resolvedAt,
    Long resolvedByUserId
) {
    public static OrderRequestResponse from(OrderRequest request) {
        return new OrderRequestResponse(
            request.getId(),
            request.getPublicId(),
            request.getOrderId(),
            request.getRequestType(),
            request.getRequestedByUserId(),
            request.getRequestedByRole(),
            request.getStatus(),
            request.getReason(),
            request.getRequestedQuantity(),
            request.getCreatedAt(),
            request.getUpdatedAt(),
            request.getResolvedAt(),
            request.getResolvedByUserId()
        );
    }
}

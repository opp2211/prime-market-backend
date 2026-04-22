package ru.maltsev.primemarketbackend.order.api.dto;

import java.time.Instant;
import java.util.UUID;
import ru.maltsev.primemarketbackend.order.api.dto.OrderDisputeDtos.AvailableActions;
import ru.maltsev.primemarketbackend.order.api.dto.OrderDisputeDtos.OrderSummary;
import ru.maltsev.primemarketbackend.order.api.dto.OrderDisputeDtos.UserSummary;

public record OrderDisputeResponse(
    UUID publicId,
    OrderSummary order,
    String status,
    String reasonCode,
    String description,
    Long openedByUserId,
    String openedByRole,
    UserSummary assignedSupportUser,
    Instant takenAt,
    Instant createdAt,
    Instant updatedAt,
    Instant resolvedAt,
    UserSummary resolvedByUser,
    String resolutionType,
    String resolutionNote,
    AvailableActions availableActions
) {
}

package ru.maltsev.primemarketbackend.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public final class OrderDisputeDtos {
    private OrderDisputeDtos() {
    }

    public record UserSummary(
        Long id,
        String username
    ) {
    }

    public record OrderSummary(
        Long id,
        UUID publicId,
        String status,
        String title,
        String buyerUsername,
        String sellerUsername
    ) {
    }

    @Schema(name = "OrderDisputeAvailableActions")
    public record AvailableActions(
        boolean canTakeInWork,
        boolean canResolveCancel,
        boolean canResolveComplete,
        boolean canResolveAmendQuantityAndComplete
    ) {
    }
}

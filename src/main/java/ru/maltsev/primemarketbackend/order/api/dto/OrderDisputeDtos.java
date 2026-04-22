package ru.maltsev.primemarketbackend.order.api.dto;

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

    public record AvailableActions(
        boolean canTakeInWork,
        boolean canResolveCancel,
        boolean canResolveComplete,
        boolean canResolveAmendQuantityAndComplete
    ) {
    }
}

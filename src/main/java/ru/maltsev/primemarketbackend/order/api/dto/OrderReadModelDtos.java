package ru.maltsev.primemarketbackend.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class OrderReadModelDtos {
    private OrderReadModelDtos() {
    }

    public record Game(
        Long id,
        String slug,
        String title
    ) {
    }

    public record Category(
        Long id,
        String slug,
        String title
    ) {
    }

    public record Counterparty(String username) {
    }

    public record Price(
        BigDecimal unitAmount,
        BigDecimal totalAmount,
        String currencyCode
    ) {
    }

    public record Context(
        String dimensionSlug,
        String valueSlug,
        String valueTitle
    ) {
    }

    public record Attribute(
        String attributeSlug,
        String optionSlug,
        String optionTitle
    ) {
    }

    public record DeliveryMethod(
        String slug,
        String title
    ) {
    }

    public record AvailableActions(
        boolean canConfirmReady,
        boolean canCancel,
        boolean canRequestCancel,
        boolean canRequestAmendQuantity,
        boolean canMarkPartiallyDelivered,
        boolean canMarkDelivered,
        boolean canConfirmReceived
    ) {
    }

    public record PendingRequest(
        UUID publicId,
        String requestType,
        Long requestedByUserId,
        String requestedByRole,
        BigDecimal requestedQuantity,
        Instant createdAt,
        boolean canApprove,
        boolean canReject
    ) {
    }
}

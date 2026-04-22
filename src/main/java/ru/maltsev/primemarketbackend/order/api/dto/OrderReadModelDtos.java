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

    public record FinancialSummary(
        String primaryLabel,
        BigDecimal primaryAmount,
        String primaryCurrencyCode,
        BigDecimal dealAmount,
        BigDecimal unitPriceAmount,
        String currencyCode,
        Integer feeRateBps,
        BigDecimal feeRatePercent,
        BigDecimal feeAmount,
        String viewerPerspective
    ) {
    }

    public record FinancialSummaryPreview(
        String primaryLabel,
        BigDecimal primaryAmount,
        String primaryCurrencyCode,
        String viewerPerspective
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

    public record UserSummary(
        Long id,
        String username
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

    public record DisputeAvailableActions(
        boolean canOpenDispute,
        boolean canTakeInWork,
        boolean canResolveCancel,
        boolean canResolveComplete,
        boolean canResolveAmendQuantityAndComplete
    ) {
    }

    public record Dispute(
        boolean exists,
        UUID publicId,
        String status,
        String reasonCode,
        String description,
        String openedByRole,
        Instant createdAt,
        UserSummary assignedSupportUser,
        DisputeAvailableActions availableActions
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

package ru.maltsev.primemarketbackend.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ru.maltsev.primemarketbackend.order.domain.Order;

public record OrderResponse(
    Long id,
    UUID publicId,
    String status,
    String makerRole,
    String takerRole,
    BigDecimal orderedQuantity,
    BigDecimal deliveredQuantity,
    BigDecimal displayUnitPriceAmount,
    BigDecimal displayTotalAmount,
    String viewerCurrencyCode,
    BigDecimal sellerGrossAmount,
    BigDecimal sellerFeeAmount,
    BigDecimal sellerNetAmount,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getPublicId(),
            order.getStatus(),
            order.getMakerRole(),
            order.getTakerRole(),
            order.getOrderedQuantity(),
            order.getDeliveredQuantity(),
            order.getDisplayUnitPriceAmount(),
            order.getDisplayTotalAmount(),
            order.getViewerCurrencyCodeSnapshot(),
            order.getSellerGrossAmount(),
            order.getSellerFeeAmount(),
            order.getSellerNetAmount(),
            order.getExpiresAt(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}

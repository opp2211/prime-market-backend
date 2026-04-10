package ru.maltsev.primemarketbackend.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.maltsev.primemarketbackend.order.api.dto.OrderReadModelDtos.Category;
import ru.maltsev.primemarketbackend.order.api.dto.OrderReadModelDtos.Counterparty;
import ru.maltsev.primemarketbackend.order.api.dto.OrderReadModelDtos.Game;

public record MyOrdersResponse(
    List<Item> items,
    int page,
    int size,
    long total
) {
    public record Item(
        Long id,
        UUID publicId,
        String status,
        String myRole,
        String counterpartyRole,
        Game game,
        Category category,
        String title,
        Counterparty counterparty,
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
    }
}

package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ru.maltsev.primemarketbackend.deposit.domain.DepositPaymentRoute;

public record DepositPaymentRouteResponse(
    @JsonProperty("public_id") UUID publicId,
    @JsonProperty("deposit_method_id") Long depositMethodId,
    @JsonProperty("deposit_method_title") String depositMethodTitle,
    @JsonProperty("deposit_currency_code") String depositCurrencyCode,
    @JsonProperty("treasury_account_public_id") UUID treasuryAccountPublicId,
    @JsonProperty("treasury_account_code") String treasuryAccountCode,
    @JsonProperty("treasury_account_title") String treasuryAccountTitle,
    @JsonProperty("treasury_currency_code") String treasuryCurrencyCode,
    String title,
    @JsonProperty("payment_details") Map<String, Object> paymentDetails,
    @JsonProperty("min_amount") BigDecimal minAmount,
    @JsonProperty("max_amount") BigDecimal maxAmount,
    Integer priority,
    @JsonProperty("is_active") boolean active,
    String note,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt
) {
    public static DepositPaymentRouteResponse from(DepositPaymentRoute route) {
        return new DepositPaymentRouteResponse(
            route.getPublicId(),
            route.getDepositMethod().getId(),
            route.getDepositMethod().getTitle(),
            route.getDepositMethod().getCurrencyCode(),
            route.getTreasuryAccount().getPublicId(),
            route.getTreasuryAccount().getCode(),
            route.getTreasuryAccount().getTitle(),
            route.getTreasuryAccount().getCurrencyCode(),
            route.getTitle(),
            route.getPaymentDetails(),
            route.getMinAmount(),
            route.getMaxAmount(),
            route.getPriority(),
            route.isActive(),
            route.getNote(),
            route.getCreatedAt(),
            route.getUpdatedAt()
        );
    }
}

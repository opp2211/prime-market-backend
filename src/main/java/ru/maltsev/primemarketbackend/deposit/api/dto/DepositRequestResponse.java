package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DepositRequestResponse(
    @JsonProperty("public_id") UUID publicId,
    BigDecimal amount,
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("deposit_method_id") Long depositMethodId,
    @JsonProperty("deposit_method_title") String depositMethodTitle,
    DepositRequestStatus status,
    @JsonProperty("payment_details") String paymentDetails,
    @JsonProperty("details_issued_at") Instant detailsIssuedAt,
    @JsonProperty("user_marked_paid_at") Instant userMarkedPaidAt,
    @JsonProperty("confirmed_at") Instant confirmedAt,
    @JsonProperty("rejected_at") Instant rejectedAt,
    @JsonProperty("reject_reason") String rejectReason,
    @JsonProperty("cancelled_at") Instant cancelledAt,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt
) {
    public static DepositRequestResponse from(DepositRequest request) {
        return new DepositRequestResponse(
            request.getPublicId(),
            request.getAmount(),
            request.getCurrencyCodeSnapshot(),
            request.getDepositMethod().getId(),
            request.getDepositMethodTitleSnapshot(),
            request.getStatus(),
            request.getPaymentDetails(),
            request.getDetailsIssuedAt(),
            request.getUserMarkedPaidAt(),
            request.getConfirmedAt(),
            request.getRejectedAt(),
            request.getRejectReason(),
            request.getCancelledAt(),
            request.getCreatedAt(),
            request.getUpdatedAt()
        );
    }
}

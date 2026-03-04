package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminDepositRequestResponse(
    @JsonProperty("public_id") UUID publicId,
    @JsonProperty("user_id") Long userId,
    BigDecimal amount,
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("deposit_method_title") String depositMethodTitle,
    DepositRequestStatus status,
    @JsonProperty("payment_details") String paymentDetails,
    @JsonProperty("details_issued_at") Instant detailsIssuedAt,
    @JsonProperty("user_marked_paid_at") Instant userMarkedPaidAt,
    @JsonProperty("confirmed_at") Instant confirmedAt,
    @JsonProperty("rejected_at") Instant rejectedAt,
    @JsonProperty("reject_reason") String rejectReason,
    @JsonProperty("cancelled_at") Instant cancelledAt,
    @JsonProperty("created_at") Instant createdAt
) {
    public static AdminDepositRequestResponse from(DepositRequest request) {
        return new AdminDepositRequestResponse(
            request.getPublicId(),
            request.getUserId(),
            request.getAmount(),
            request.getDepositMethod().getCurrencyCode(),
            request.getDepositMethod().getTitle(),
            request.getStatus(),
            request.getPaymentDetails(),
            request.getDetailsIssuedAt(),
            request.getUserMarkedPaidAt(),
            request.getConfirmedAt(),
            request.getRejectedAt(),
            request.getRejectReason(),
            request.getCancelledAt(),
            request.getCreatedAt()
        );
    }
}

package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequestStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ru.maltsev.primemarketbackend.deposit.repository.AdminDepositRequestQueueRow;

public record AdminDepositRequestShortResponse(
        @JsonProperty("public_id") UUID publicId,
        BigDecimal amount,
        @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("deposit_method_id") Long depositMethodId,
        @JsonProperty("deposit_method_title") String depositMethodTitle,
        DepositRequestStatus status,
        UserSummary user,
        @JsonProperty("details_issued_at") Instant detailsIssuedAt,
        @JsonProperty("user_marked_paid_at") Instant userMarkedPaidAt,
        @JsonProperty("confirmed_at") Instant confirmedAt,
        @JsonProperty("rejected_at") Instant rejectedAt,
        @JsonProperty("cancelled_at") Instant cancelledAt,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {
    public static AdminDepositRequestShortResponse from(AdminDepositRequestQueueRow row) {
        return new AdminDepositRequestShortResponse(
                row.publicId(),
                row.amount(),
                row.currencyCode(),
                row.depositMethodId(),
                row.depositMethodTitle(),
                row.status(),
                new UserSummary(row.userId(), row.username()),
                row.detailsIssuedAt(),
                row.userMarkedPaidAt(),
                row.confirmedAt(),
                row.rejectedAt(),
                row.cancelledAt(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    public record UserSummary(
        Long id,
        String username
    ) {
    }
}

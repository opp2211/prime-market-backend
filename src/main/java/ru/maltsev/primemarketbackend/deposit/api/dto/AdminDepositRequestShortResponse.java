package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminDepositRequestShortResponse(
        @JsonProperty("public_id") UUID publicId,
        BigDecimal amount,
        @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("deposit_method_title") String depositMethodTitle,
        DepositRequestStatus status,
        @JsonProperty("created_at") Instant createdAt
) {
    public static AdminDepositRequestShortResponse from(DepositRequest request) {
        return new AdminDepositRequestShortResponse(
                request.getPublicId(),
                request.getAmount(),
                request.getDepositMethod().getCurrencyCode(),
                request.getDepositMethod().getTitle(),
                request.getStatus(),
                request.getCreatedAt()
        );
    }
}

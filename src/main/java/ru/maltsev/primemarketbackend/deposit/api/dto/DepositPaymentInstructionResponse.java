package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ru.maltsev.primemarketbackend.deposit.domain.DepositPaymentInstruction;
import ru.maltsev.primemarketbackend.deposit.domain.DepositPaymentInstructionStatus;

public record DepositPaymentInstructionResponse(
    @JsonProperty("public_id") UUID publicId,
    @JsonProperty("deposit_payment_route_public_id") UUID depositPaymentRoutePublicId,
    @JsonProperty("treasury_account_public_id") UUID treasuryAccountPublicId,
    @JsonProperty("treasury_account_code") String treasuryAccountCode,
    @JsonProperty("treasury_account_title") String treasuryAccountTitle,
    @JsonProperty("payment_details") Map<String, Object> paymentDetails,
    BigDecimal amount,
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("treasury_amount") BigDecimal treasuryAmount,
    @JsonProperty("treasury_currency_code") String treasuryCurrencyCode,
    DepositPaymentInstructionStatus status,
    @JsonProperty("expires_at") Instant expiresAt,
    @JsonProperty("issued_at") Instant issuedAt,
    @JsonProperty("issued_by_user_id") Long issuedByUserId,
    @JsonProperty("operator_comment") String operatorComment
) {
    public static DepositPaymentInstructionResponse from(DepositPaymentInstruction instruction) {
        if (instruction == null) {
            return null;
        }
        return new DepositPaymentInstructionResponse(
            instruction.getPublicId(),
            instruction.getDepositPaymentRoute() == null ? null : instruction.getDepositPaymentRoute().getPublicId(),
            instruction.getTreasuryAccount() == null ? null : instruction.getTreasuryAccount().getPublicId(),
            instruction.getTreasuryAccount() == null ? null : instruction.getTreasuryAccount().getCode(),
            instruction.getTreasuryAccount() == null ? null : instruction.getTreasuryAccount().getTitle(),
            instruction.getPaymentDetailsSnapshot(),
            instruction.getAmount(),
            instruction.getCurrencyCodeSnapshot(),
            instruction.getTreasuryAmount(),
            instruction.getTreasuryCurrencyCodeSnapshot(),
            instruction.getStatus(),
            instruction.getExpiresAt(),
            instruction.getIssuedAt(),
            instruction.getIssuedByUserId(),
            instruction.getOperatorComment()
        );
    }
}

package ru.maltsev.primemarketbackend.treasury.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryTransactionType;

public record CreateTreasuryTransactionRequest(
    @JsonProperty("treasury_account_public_id") @NotNull UUID treasuryAccountPublicId,
    @NotNull @Digits(integer = 15, fraction = 4) BigDecimal amount,
    @JsonProperty("transaction_type") @NotNull TreasuryTransactionType transactionType,
    @JsonProperty("external_reference") String externalReference,
    String description,
    @JsonProperty("operator_comment") String operatorComment,
    Map<String, Object> metadata
) {
}

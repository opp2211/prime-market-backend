package ru.maltsev.primemarketbackend.withdrawal.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record CreateWithdrawalRequest(
    @JsonProperty("currency_code") @NotBlank String currencyCode,
    @JsonProperty("withdrawal_method_id") @NotNull Long withdrawalMethodId,
    @JsonProperty("payout_profile_public_id") UUID payoutProfilePublicId,
    Map<String, Object> requisites,
    @NotNull @DecimalMin("0.0001") @Digits(integer = 9, fraction = 4) BigDecimal amount,
    @JsonProperty("save_payout_profile") Boolean savePayoutProfile,
    @JsonProperty("payout_profile_title") String payoutProfileTitle
) {
}

package ru.maltsev.primemarketbackend.withdrawal.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ru.maltsev.primemarketbackend.withdrawal.domain.PayoutProfile;

public record PayoutProfileResponse(
    @JsonProperty("public_id") UUID publicId,
    String title,
    @JsonProperty("withdrawal_method_id") Long withdrawalMethodId,
    @JsonProperty("withdrawal_method_code") String withdrawalMethodCode,
    @JsonProperty("withdrawal_method_title") String withdrawalMethodTitle,
    @JsonProperty("currency_code") String currencyCode,
    Map<String, Object> requisites,
    @JsonProperty("is_default") boolean isDefault,
    @JsonProperty("is_active") boolean isActive,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt
) {
    public static PayoutProfileResponse from(PayoutProfile profile) {
        return new PayoutProfileResponse(
            profile.getPublicId(),
            profile.getTitle(),
            profile.getWithdrawalMethod().getId(),
            profile.getWithdrawalMethod().getCode(),
            profile.getWithdrawalMethod().getTitle(),
            profile.getWithdrawalMethod().getCurrencyCode(),
            profile.getRequisites(),
            profile.isDefaultProfile(),
            profile.isActive(),
            profile.getCreatedAt(),
            profile.getUpdatedAt()
        );
    }
}

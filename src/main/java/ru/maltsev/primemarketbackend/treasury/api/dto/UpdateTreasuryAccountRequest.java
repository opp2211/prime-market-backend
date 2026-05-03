package ru.maltsev.primemarketbackend.treasury.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryAccountType;

public record UpdateTreasuryAccountRequest(
    String title,
    @JsonProperty("account_type") TreasuryAccountType accountType,
    Map<String, Object> details,
    String note,
    @JsonProperty("is_active") Boolean active
) {
}

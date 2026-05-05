package ru.maltsev.primemarketbackend.account.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record WalletWorkSummaryResponse(
    List<WalletWorkItemResponse> reserves,
    @JsonProperty("pending_deposits") List<WalletWorkItemResponse> pendingDeposits
) {
}

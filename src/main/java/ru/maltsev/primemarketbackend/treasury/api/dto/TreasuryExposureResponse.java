package ru.maltsev.primemarketbackend.treasury.api.dto;

import java.time.Instant;
import java.util.List;

public record TreasuryExposureResponse(
    Instant generatedAt,
    List<TreasuryExposureRowResponse> rows
) {
}

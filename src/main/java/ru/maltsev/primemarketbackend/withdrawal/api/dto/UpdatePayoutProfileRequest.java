package ru.maltsev.primemarketbackend.withdrawal.api.dto;

import java.util.Map;

public record UpdatePayoutProfileRequest(
    String title,
    Map<String, Object> requisites
) {
}

package ru.maltsev.primemarketbackend.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmEmailChangeRequest(
    @NotBlank String token
) {
}

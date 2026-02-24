package ru.maltsev.primemarketbackend.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmPasswordChangeRequest(
    @NotBlank String token
) {
}

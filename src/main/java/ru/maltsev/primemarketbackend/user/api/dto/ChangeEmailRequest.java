package ru.maltsev.primemarketbackend.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeEmailRequest(
    @Email @NotBlank String newEmail,
    @NotBlank String currentPassword
) {
}

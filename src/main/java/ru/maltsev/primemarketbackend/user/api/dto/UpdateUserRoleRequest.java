package ru.maltsev.primemarketbackend.user.api.dto;

import jakarta.validation.constraints.NotNull;
import ru.maltsev.primemarketbackend.user.domain.UserRole;

public record UpdateUserRoleRequest(
    @NotNull UserRole role
) {
}

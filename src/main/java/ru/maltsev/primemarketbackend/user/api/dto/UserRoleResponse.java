package ru.maltsev.primemarketbackend.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.domain.UserRole;

public record UserRoleResponse(
    @JsonProperty("user_id") Long userId,
    UserRole role
) {
    public static UserRoleResponse from(User user) {
        return new UserRoleResponse(user.getId(), user.getRole());
    }
}

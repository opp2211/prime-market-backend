package ru.maltsev.primemarketbackend.user.api.dto;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import ru.maltsev.primemarketbackend.user.domain.Permission;
import ru.maltsev.primemarketbackend.user.domain.Role;
import ru.maltsev.primemarketbackend.user.domain.User;

public record UserProfileResponse(
    Long id,
    String username,
    String email,
    boolean active,
    Instant createdAt,
    Set<String> roles,
    Set<String> permissions
) {
    public static UserProfileResponse from(User user) {
        Set<String> roles = user.getRoles().stream()
            .map(Role::getCode)
            .collect(Collectors.toUnmodifiableSet());
        Set<String> permissions = user.getPermissions().stream()
            .map(Permission::getCode)
            .collect(Collectors.toUnmodifiableSet());
        return new UserProfileResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.isActive(),
            user.getCreatedAt(),
            roles,
            permissions
        );
    }
}

package ru.maltsev.primemarketbackend.user.api.dto;

import java.time.Instant;
import ru.maltsev.primemarketbackend.user.domain.User;

public record UserProfileResponse(
    Long id,
    String username,
    String email,
    boolean active,
    Instant createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.isActive(),
            user.getCreatedAt()
        );
    }
}

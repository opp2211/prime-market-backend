package ru.maltsev.primemarketbackend.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.domain.UserRole;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserAdminService {
    private final UserRepository userRepository;

    @Transactional
    public User updateRole(Long userId, UserRole role) {
        if (role == null) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "ROLE_REQUIRED",
                "Role is required"
            );
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "User not found"
            ));
        user.setRole(role);
        return userRepository.save(user);
    }
}

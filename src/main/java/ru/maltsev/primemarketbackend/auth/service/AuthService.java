package ru.maltsev.primemarketbackend.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.maltsev.primemarketbackend.auth.api.dto.LoginRequest;
import ru.maltsev.primemarketbackend.auth.api.dto.RegisterRequest;
import ru.maltsev.primemarketbackend.security.jwt.JwtService;
import ru.maltsev.primemarketbackend.security.refresh.RefreshToken;
import ru.maltsev.primemarketbackend.security.refresh.RefreshTokenService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String CONSTRAINT_USERS_EMAIL = "ux_users_email";
    private static final String CONSTRAINT_USERS_USERNAME = "ux_users_username";
    private static final String CONSTRAINT_USERS_USERNAME_LENGTH = "username_length";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthTokens register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        String password = normalizePassword(request.password());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already in use");
        }

        User user = new User(username, email, passwordEncoder.encode(password));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            String constraint = findConstraintName(ex);
            if (CONSTRAINT_USERS_EMAIL.equals(constraint)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
            }
            if (CONSTRAINT_USERS_USERNAME.equals(constraint)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already in use");
            }
            if (CONSTRAINT_USERS_USERNAME_LENGTH.equals(constraint)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username length must be at least 3");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflict");
        }

        String accessToken = jwtService.generateToken(new UserPrincipal(user));
        String refreshToken = refreshTokenService.issueToken(user);
        return new AuthTokens(accessToken, refreshToken);
    }

    public AuthTokens login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        String password = normalizePassword(request.password());

        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, password)
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String accessToken = jwtService.generateToken(principal);
        String refreshToken = refreshTokenService.issueToken(principal.getUser());
        return new AuthTokens(accessToken, refreshToken);
    }

    public AuthTokens refresh(String refreshToken) {
        RefreshToken storedToken = refreshTokenService.getValidToken(refreshToken);
        User user = storedToken.getUser();
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is inactive");
        }
        String accessToken = jwtService.generateToken(new UserPrincipal(user));
        String rotatedRefreshToken = refreshTokenService.rotateToken(storedToken);
        return new AuthTokens(accessToken, rotatedRefreshToken);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revokeToken(refreshToken);
    }

    private String normalizeEmail(String email) {
        return requireNonBlank(email, "Email").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        return requireNonBlank(username, "Username").trim();
    }

    private String normalizePassword(String password) {
        return requireNonBlank(password, "Password");
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }

    private String findConstraintName(DataIntegrityViolationException ex) {
        Throwable mostSpecific = ex.getMostSpecificCause();
        if (mostSpecific instanceof ConstraintViolationException constraintViolation) {
            return constraintViolation.getConstraintName();
        }
        return null;
    }
}

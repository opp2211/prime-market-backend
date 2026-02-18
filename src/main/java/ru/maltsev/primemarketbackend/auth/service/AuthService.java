package ru.maltsev.primemarketbackend.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import ru.maltsev.primemarketbackend.auth.api.dto.StatusResponse;
import ru.maltsev.primemarketbackend.config.EmailProperties;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.exception.EmailNotVerifiedException;
import ru.maltsev.primemarketbackend.security.jwt.JwtService;
import ru.maltsev.primemarketbackend.security.refresh.RefreshToken;
import ru.maltsev.primemarketbackend.security.refresh.RefreshTokenService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

import java.util.Locale;
import org.springframework.security.authentication.DisabledException;

@Service
@Slf4j
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
    private final EmailVerificationService emailVerificationService;
    private final EmailProperties emailProperties;

    @Transactional
    public ResponseEntity<StatusResponse> register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        String password = normalizePassword(request.password());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "EMAIL_ALREADY_IN_USE", "Email already in use");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "USERNAME_ALREADY_IN_USE", "Username already in use");
        }

        User user = new User(username, email, passwordEncoder.encode(password));
        boolean verificationRequired = emailProperties.verificationRequired();
        if (verificationRequired) {
            user.setActive(false);
        }
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            String constraint = findConstraintName(ex);
            log.warn("Register failed due to data integrity violation. constraint={}", constraint, ex);
            if (CONSTRAINT_USERS_EMAIL.equals(constraint)) {
                throw new ApiProblemException(HttpStatus.CONFLICT, "EMAIL_ALREADY_IN_USE", "Email already in use");
            }
            if (CONSTRAINT_USERS_USERNAME.equals(constraint)) {
                throw new ApiProblemException(HttpStatus.CONFLICT, "USERNAME_ALREADY_IN_USE", "Username already in use");
            }
            if (CONSTRAINT_USERS_USERNAME_LENGTH.equals(constraint)) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "USERNAME_TOO_SHORT",
                    "Username length must be at least 3"
                );
            }
            throw new ApiProblemException(HttpStatus.CONFLICT, "CONFLICT", "Conflict");
        }

        if (verificationRequired) {
            emailVerificationService.sendVerification(user);
            return ResponseEntity.accepted().body(StatusResponse.emailVerificationRequired());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(StatusResponse.registered());
    }

    public AuthTokens login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        String password = normalizePassword(request.password());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (DisabledException ex) {
            throw new EmailNotVerifiedException();
        }

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

    public AuthTokens verifyEmail(String token) {
        User user = emailVerificationService.verify(token);
        String accessToken = jwtService.generateToken(new UserPrincipal(user));
        String refreshToken = refreshTokenService.issueToken(user);
        return new AuthTokens(accessToken, refreshToken);
    }

    public void resendVerification(String email) {
        emailVerificationService.resend(email);
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
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                fieldName + " is required"
            );
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

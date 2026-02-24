package ru.maltsev.primemarketbackend.user.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import ru.maltsev.primemarketbackend.config.EmailProperties;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.user.change.EmailChangeToken;
import ru.maltsev.primemarketbackend.user.change.EmailChangeTokenRepository;
import ru.maltsev.primemarketbackend.user.change.PasswordChangeToken;
import ru.maltsev.primemarketbackend.user.change.PasswordChangeTokenRepository;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
public class UserProfileService {
    private static final int TOKEN_BYTES = 64;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;
    private final EmailChangeTokenRepository emailChangeTokenRepository;
    private final PasswordChangeTokenRepository passwordChangeTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void requestEmailChange(User user, String rawNewEmail, String rawCurrentPassword) {
        String newEmail = normalizeEmail(rawNewEmail);
        verifyCurrentPassword(user, rawCurrentPassword);

        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "EMAIL_SAME_AS_CURRENT",
                "Email must be different from current"
            );
        }
        if (userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "EMAIL_ALREADY_IN_USE", "Email already in use");
        }

        emailChangeTokenRepository.deleteByUserId(user.getId());
        String rawToken = generateToken();
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(emailProperties.changeEmailTtl());
        emailChangeTokenRepository.save(new EmailChangeToken(user, tokenHash, newEmail, expiresAt));

        sendEmailChangeEmail(newEmail, buildLink(emailProperties.changeEmailBaseUrl(), rawToken));
    }

    @Transactional
    public void confirmEmailChange(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email change token is required");
        }
        String tokenHash = hashToken(rawToken);
        EmailChangeToken token = emailChangeTokenRepository.findByTokenHashWithUser(tokenHash)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email change token is invalid"));

        Instant now = Instant.now();
        if (token.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Email change token is expired");
        }

        if (token.isConfirmed()) {
            return;
        }

        User user = token.getUser();
        String newEmail = token.getNewEmail();
        if (!newEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "EMAIL_ALREADY_IN_USE", "Email already in use");
        }

        user.setEmail(newEmail);
        userRepository.save(user);
        token.confirm(now);
        emailChangeTokenRepository.save(token);
    }

    @Transactional
    public void requestPasswordChange(User user, String rawCurrentPassword, String rawNewPassword) {
        verifyCurrentPassword(user, rawCurrentPassword);
        String newPassword = requireNonBlank(rawNewPassword, "New password");
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "PASSWORD_SAME_AS_CURRENT",
                "Password must be different from current"
            );
        }

        passwordChangeTokenRepository.deleteByUserId(user.getId());
        String rawToken = generateToken();
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(emailProperties.changePasswordTtl());
        String newPasswordHash = passwordEncoder.encode(newPassword);
        passwordChangeTokenRepository.save(new PasswordChangeToken(user, tokenHash, newPasswordHash, expiresAt));

        sendPasswordChangeEmail(user.getEmail(), buildLink(emailProperties.changePasswordBaseUrl(), rawToken));
    }

    @Transactional
    public void confirmPasswordChange(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password change token is required");
        }
        String tokenHash = hashToken(rawToken);
        PasswordChangeToken token = passwordChangeTokenRepository.findByTokenHashWithUser(tokenHash)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password change token is invalid"));

        Instant now = Instant.now();
        if (token.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Password change token is expired");
        }

        if (token.isConfirmed()) {
            return;
        }

        User user = token.getUser();
        user.setPasswordHash(token.getNewPasswordHash());
        userRepository.save(user);
        token.confirm(now);
        passwordChangeTokenRepository.save(token);
    }

    private void verifyCurrentPassword(User user, String rawCurrentPassword) {
        String currentPassword = requireNonBlank(rawCurrentPassword, "Current password");
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
        }
    }

    private String normalizeEmail(String email) {
        return requireNonBlank(email, "Email").trim().toLowerCase(Locale.ROOT);
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

    private void sendEmailChangeEmail(String to, String link) {
        sendEmail(to, "Confirm your email change", "Please confirm your email change by clicking the link: " + link);
    }

    private void sendPasswordChangeEmail(String to, String link) {
        sendEmail(to, "Confirm your password change", "Please confirm your password change by clicking the link: " + link);
    }

    private void sendEmail(String to, String subject, String body) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);

            if (StringUtils.hasText(emailProperties.fromName())) {
                helper.setFrom(new InternetAddress(emailProperties.from(), emailProperties.fromName()));
            } else {
                helper.setFrom(emailProperties.from());
            }

            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to send email", ex);
        }
    }

    private String buildLink(String baseUrl, String rawToken) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}

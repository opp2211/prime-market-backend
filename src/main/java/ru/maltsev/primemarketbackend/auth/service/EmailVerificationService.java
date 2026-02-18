package ru.maltsev.primemarketbackend.auth.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import ru.maltsev.primemarketbackend.auth.verification.EmailVerificationToken;
import ru.maltsev.primemarketbackend.auth.verification.EmailVerificationTokenRepository;
import ru.maltsev.primemarketbackend.config.EmailProperties;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final int TOKEN_BYTES = 64;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void sendVerification(User user) {
        if (user.isActive()) {
            return;
        }
        if (user.getId() == null) {
            userRepository.saveAndFlush(user);
        }
        tokenRepository.deleteByUserId(user.getId());
        String rawToken = generateToken();
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(emailProperties.verificationTtl());
        tokenRepository.save(new EmailVerificationToken(user, tokenHash, expiresAt));
        sendVerificationEmail(user.getEmail(), buildVerificationLink(rawToken));
    }

    @Transactional
    public User verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification token is required");
        }
        String tokenHash = hashToken(rawToken);
        EmailVerificationToken token = tokenRepository.findByTokenHashWithUser(tokenHash)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification token is invalid"));

        Instant now = Instant.now();
        if (token.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Verification token is expired");
        }

        if (!token.isConfirmed()) {
            token.confirm(now);
            tokenRepository.save(token);
        }

        User user = token.getUser();
        if (!user.isActive()) {
            user.setActive(true);
            userRepository.save(user);
        }
        return user;
    }

    @Transactional
    public void resend(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (email == null) {
            return;
        }
        userRepository.findByEmailIgnoreCase(email)
            .filter(user -> !user.isActive())
            .ifPresent(this::sendVerification);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void sendVerificationEmail(String to, String link) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject("Confirm your email");
            helper.setText(buildVerificationBody(link), false);

            if (StringUtils.hasText(emailProperties.fromName())) {
                helper.setFrom(new InternetAddress(emailProperties.from(), emailProperties.fromName()));
            } else {
                helper.setFrom(emailProperties.from());
            }

            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to send verification email", ex);
        }
    }

    private String buildVerificationBody(String link) {
        return "Please confirm your email by clicking the link: " + link;
    }

    private String buildVerificationLink(String rawToken) {
        String baseUrl = emailProperties.verificationBaseUrl();
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

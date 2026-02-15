package ru.maltsev.primemarketbackend.auth.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.auth.api.dto.AuthResponse;
import ru.maltsev.primemarketbackend.auth.api.dto.LoginRequest;
import ru.maltsev.primemarketbackend.auth.api.dto.RegisterRequest;
import ru.maltsev.primemarketbackend.auth.service.AuthTokens;
import ru.maltsev.primemarketbackend.auth.service.AuthService;
import ru.maltsev.primemarketbackend.security.jwt.JwtProperties;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthTokens tokens = authService.register(request);
        return buildResponse(tokens);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthTokens tokens = authService.login(request);
        return buildResponse(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String refreshToken = extractRefreshToken(request);
        AuthTokens tokens = authService.refresh(refreshToken);
        return buildResponse(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String refreshToken = extractRefreshToken(request);
        authService.logout(refreshToken);
        ResponseCookie clearedCookie = buildClearedRefreshCookie();
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, clearedCookie.toString())
            .build();
    }

    private ResponseEntity<AuthResponse> buildResponse(AuthTokens tokens) {
        ResponseCookie refreshCookie = buildRefreshCookie(tokens.refreshToken());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
            .body(AuthResponse.of(tokens.accessToken()));
    }

    private ResponseCookie buildRefreshCookie(String refreshToken) {
        long maxAgeSeconds = jwtProperties.refreshTokenTtl().getSeconds();
        return ResponseCookie.from(jwtProperties.refreshTokenCookieName(), refreshToken)
            .httpOnly(true)
            .secure(jwtProperties.refreshTokenCookieSecure())
            .path(jwtProperties.refreshTokenCookiePath())
            .sameSite(jwtProperties.refreshTokenCookieSameSite())
            .maxAge(maxAgeSeconds)
            .build();
    }

    private ResponseCookie buildClearedRefreshCookie() {
        return ResponseCookie.from(jwtProperties.refreshTokenCookieName(), "")
            .httpOnly(true)
            .secure(jwtProperties.refreshTokenCookieSecure())
            .path(jwtProperties.refreshTokenCookiePath())
            .sameSite(jwtProperties.refreshTokenCookieSameSite())
            .maxAge(0)
            .build();
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        return Arrays.stream(cookies)
            .filter(cookie -> jwtProperties.refreshTokenCookieName().equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }
}

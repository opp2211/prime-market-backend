package ru.maltsev.primemarketbackend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
    @NotBlank String from,
    String fromName,
    @NotBlank String verificationBaseUrl,
    @NotNull Duration verificationTtl,
    boolean verificationRequired,
    @NotBlank String changeEmailBaseUrl,
    @NotNull Duration changeEmailTtl,
    @NotBlank String changePasswordBaseUrl,
    @NotNull Duration changePasswordTtl
) {
}

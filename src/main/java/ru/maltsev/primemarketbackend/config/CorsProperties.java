package ru.maltsev.primemarketbackend.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {
    private List<String> allowedOrigins = new ArrayList<>(List.of(
        "http://localhost:5173",
        "http://127.0.0.1:5173"
    ));
    private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PATCH", "OPTIONS"));
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
    private boolean allowCredentials = true;
    private Duration maxAge = Duration.ofHours(1);

}

package ru.maltsev.primemarketbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.assets")
public record AssetsProperties(String baseUrl) {
}

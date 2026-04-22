package ru.maltsev.primemarketbackend.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractPostgresIntegrationTest {
    private static final String DB_URL = read("DB_URL", "jdbc:postgresql://localhost:5432/prime-market-db");
    private static final String DB_USERNAME = read("DB_USERNAME", "postgres");
    private static final String DB_PASSWORD = read("DB_PASSWORD", "postgres");
    private static final String SCHEMA = "test_" + UUID.randomUUID().toString().replace("-", "");

    static {
        execute("create schema if not exists " + SCHEMA);
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            execute("drop schema if exists " + SCHEMA + " cascade")
        ));
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> appendCurrentSchema(DB_URL, SCHEMA));
        registry.add("spring.datasource.username", () -> DB_USERNAME);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev-migration");
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add(
            "security.jwt.secret",
            () -> "test-secret-key-test-secret-key-test-secret-key-test-secret-key"
        );
        registry.add("app.email.verification-required", () -> "false");
    }

    private static void execute(String sql) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to execute test database SQL: " + sql, ex);
        }
    }

    private static String appendCurrentSchema(String jdbcUrl, String schema) {
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + "currentSchema=" + schema;
    }

    private static String read(String key, String fallback) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return fallback;
    }
}

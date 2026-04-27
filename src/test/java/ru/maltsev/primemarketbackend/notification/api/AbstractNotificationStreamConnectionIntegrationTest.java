package ru.maltsev.primemarketbackend.notification.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.maltsev.primemarketbackend.notification.service.NotificationSseRegistry;
import ru.maltsev.primemarketbackend.security.jwt.JwtService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

abstract class AbstractNotificationStreamConnectionIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private NotificationSseRegistry notificationSseRegistry;

    @Autowired
    private DataSource dataSource;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private final List<StreamConnection> openStreams = new ArrayList<>();

    @BeforeEach
    void resetState() {
        notificationSseRegistry.disconnectAll();
        jdbcTemplate.execute("truncate table notifications restart identity cascade");
        jdbcTemplate.update("delete from users where email like ?", "%.notification-open-in-view.example.test");
    }

    @AfterEach
    void cleanupStreams() {
        for (StreamConnection stream : openStreams) {
            stream.close();
        }
        openStreams.clear();
        notificationSseRegistry.disconnectAll();
    }

    protected void assertConnectionHeldWhileStreamIsAlive(boolean expectedHeld) throws Exception {
        User user = createUser("stream-open-in-view");
        awaitActiveConnections(0);

        StreamConnection stream = openStream(user);
        assertThat(notificationSseRegistry.connectionCount(user.getId())).isEqualTo(1);

        if (expectedHeld) {
            awaitActiveConnections(1);
            return;
        }

        awaitActiveConnections(0);
    }

    protected HikariPoolMXBean hikariPool() {
        return ((HikariDataSource) dataSource).getHikariPoolMXBean();
    }

    protected int activeConnections() {
        return hikariPool().getActiveConnections();
    }

    protected User createUser(String slug) {
        String uniqueSlug = slug + "-" + UUID.randomUUID().toString().substring(0, 8);
        String username = uniqueSlug;
        if (username.length() > 24) {
            String hash = Integer.toUnsignedString(uniqueSlug.hashCode(), 36);
            int prefixLength = Math.max(1, 24 - hash.length() - 1);
            username = uniqueSlug.substring(0, prefixLength) + "-" + hash;
        }

        User user = new User(username, username + "@notification-open-in-view.example.test", "password-hash");
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private StreamConnection openStream(User user) throws Exception {
        String token = jwtService.generateToken(new UserPrincipal(user));
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/notifications/stream"))
            .header("Authorization", "Bearer " + token)
            .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
            .GET()
            .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);

        StreamConnection stream = new StreamConnection(response.body());
        openStreams.add(stream);
        stream.awaitContains("stream.connected");
        stream.awaitContains("notifications.unread_count");
        return stream;
    }

    private void awaitActiveConnections(int expectedActiveConnections) throws InterruptedException {
        Instant deadline = Instant.now().plus(AWAIT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (activeConnections() == expectedActiveConnections) {
                return;
            }
            Thread.sleep(50L);
        }

        throw new AssertionError(
            "Expected active connections %d but got %d (idle=%d, total=%d)".formatted(
                expectedActiveConnections,
                hikariPool().getActiveConnections(),
                hikariPool().getIdleConnections(),
                hikariPool().getTotalConnections()
            )
        );
    }

    private static final class StreamConnection implements AutoCloseable {
        private final InputStream inputStream;
        private final StringBuilder body = new StringBuilder();
        private final Thread readerThread;

        private StreamConnection(InputStream inputStream) {
            this.inputStream = inputStream;
            this.readerThread = new Thread(this::readStream, "notification-stream-test-reader");
            this.readerThread.setDaemon(true);
            this.readerThread.start();
        }

        private void readStream() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (body) {
                        body.append(line).append('\n');
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private void awaitContains(String expected) throws InterruptedException {
            Instant deadline = Instant.now().plus(AWAIT_TIMEOUT);
            while (Instant.now().isBefore(deadline)) {
                synchronized (body) {
                    if (body.indexOf(expected) >= 0) {
                        return;
                    }
                }
                Thread.sleep(25L);
            }

            throw new AssertionError("SSE stream does not contain '%s'. Body: %s".formatted(expected, body));
        }

        @Override
        public void close() {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }

            try {
                readerThread.join(1000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

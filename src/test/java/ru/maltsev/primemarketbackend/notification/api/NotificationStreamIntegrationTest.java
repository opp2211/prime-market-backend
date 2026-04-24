package ru.maltsev.primemarketbackend.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.maltsev.primemarketbackend.notification.domain.NotificationTypes;
import ru.maltsev.primemarketbackend.notification.service.NotificationService;
import ru.maltsev.primemarketbackend.notification.service.NotificationSseRegistry;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class NotificationStreamIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationSseRegistry notificationSseRegistry;

    @BeforeEach
    void resetState() {
        notificationSseRegistry.disconnectAll();
        jdbcTemplate.execute("truncate table notifications restart identity cascade");
        jdbcTemplate.update("delete from users where email like ?", "%.notification-stream.example.test");
    }

    @AfterEach
    void cleanupStreams() {
        notificationSseRegistry.disconnectAll();
    }

    @Test
    void streamRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications/stream"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanConnectAndReceiveBootstrapUnreadCount() throws Exception {
        User user = createUser("stream-owner");
        User otherUser = createUser("stream-other");
        notificationService.createNotification(
            user.getId(),
            NotificationTypes.ORDER_CREATED,
            "Own notification 1",
            "Own body 1",
            payload("bootstrap-own-1")
        );
        notificationService.createNotification(
            user.getId(),
            NotificationTypes.ORDER_STATUS_CHANGED,
            "Own notification 2",
            "Own body 2",
            payload("bootstrap-own-2")
        );
        notificationService.createNotification(
            otherUser.getId(),
            NotificationTypes.ORDER_CREATED,
            "Other notification",
            "Other body",
            payload("bootstrap-other")
        );

        MvcResult stream = openStream(user);

        awaitStreamContains(stream, "stream.connected");
        awaitStreamContains(stream, "notifications.unread_count");
        awaitStreamContains(stream, "\"count\":2");
        assertThat(stream.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(notificationSseRegistry.connectionCount(user.getId())).isEqualTo(1);
    }

    @Test
    void createdNotificationIsDeliveredToActiveSubscriber() throws Exception {
        User user = createUser("stream-created");
        MvcResult stream = openStream(user);

        awaitStreamContains(stream, "\"count\":0");

        UUID publicId = notificationService.createNotification(
            user.getId(),
            NotificationTypes.ORDER_CREATED,
            "Live notification",
            "Live notification body",
            payload("created-live")
        );

        awaitStreamContains(stream, "notification.created");
        awaitStreamContains(stream, publicId.toString());
        awaitStreamContains(stream, "\"count\":1");
    }

    @Test
    void createdNotificationEventReturnsPlainPayloadObject() throws Exception {
        User user = createUser("stream-created-payload");
        MvcResult stream = openStream(user);
        String orderPublicId = UUID.randomUUID().toString();
        String conversationPublicId = UUID.randomUUID().toString();

        awaitStreamContains(stream, "\"count\":0");

        UUID publicId = notificationService.createNotification(
            user.getId(),
            NotificationTypes.ORDER_MESSAGE_RECEIVED,
            "Payload notification",
            "Payload notification body",
            payload(orderPublicId, conversationPublicId)
        );

        JsonNode event = awaitEventData(stream, "notification.created");

        assertThat(event.path("publicId").asText()).isEqualTo(publicId.toString());
        assertPlainNotificationPayload(event.path("payload"), orderPublicId, conversationPublicId);
    }

    @Test
    void markReadPublishesUnreadCountUpdate() throws Exception {
        User user = createUser("stream-mark-read");
        UUID firstNotification = notificationService.createNotification(
            user.getId(),
            NotificationTypes.ORDER_CREATED,
            "First unread",
            "First unread body",
            payload("mark-read-1")
        );
        notificationService.createNotification(
            user.getId(),
            NotificationTypes.ORDER_STATUS_CHANGED,
            "Second unread",
            "Second unread body",
            payload("mark-read-2")
        );

        MvcResult stream = openStream(user);
        awaitStreamContains(stream, "\"count\":2");

        mockMvc.perform(post("/api/notifications/{publicId}/read", firstNotification).with(auth(user)))
            .andExpect(status().isOk());

        awaitStreamContains(stream, "\"count\":1");
    }

    @Test
    void readAllPublishesUnreadCountUpdate() throws Exception {
        User user = createUser("stream-read-all");
        notificationService.createNotification(
            user.getId(),
            NotificationTypes.ORDER_CREATED,
            "Unread one",
            "Unread one body",
            payload("read-all-1")
        );
        notificationService.createNotification(
            user.getId(),
            NotificationTypes.ORDER_STATUS_CHANGED,
            "Unread two",
            "Unread two body",
            payload("read-all-2")
        );

        MvcResult stream = openStream(user);
        awaitStreamContains(stream, "\"count\":2");

        mockMvc.perform(post("/api/notifications/read-all").with(auth(user)))
            .andExpect(status().isOk());

        awaitStreamContains(stream, "\"count\":0");
    }

    private User createUser(String slug) {
        String username = slug;
        if (username.length() > 24) {
            String hash = Integer.toUnsignedString(slug.hashCode(), 36);
            int prefixLength = Math.max(1, 24 - hash.length() - 1);
            username = slug.substring(0, prefixLength) + "-" + hash;
        }

        User user = new User(username, username + "@notification-stream.example.test", "password-hash");
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private RequestPostProcessor auth(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        ));
    }

    private MvcResult openStream(User user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notifications/stream").with(auth(user)))
            .andExpect(request().asyncStarted())
            .andReturn();
        awaitStreamContains(result, "stream.connected");
        return result;
    }

    private ObjectNode payload(String suffix) {
        return JsonNodeFactory.instance.objectNode()
            .put("orderPublicId", UUID.nameUUIDFromBytes(("payload-" + suffix).getBytes()).toString())
            .put("createdAtHint", Instant.now().toString());
    }

    private ObjectNode payload(String orderPublicId, String conversationPublicId) {
        return JsonNodeFactory.instance.objectNode()
            .put("orderPublicId", orderPublicId)
            .put("conversationPublicId", conversationPublicId);
    }

    private void awaitStreamContains(MvcResult result, String expected) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            String body = result.getResponse().getContentAsString();
            if (body.contains(expected)) {
                return;
            }
            Thread.sleep(50L);
        }

        throw new AssertionError("SSE stream does not contain '%s'. Body: %s".formatted(
            expected,
            result.getResponse().getContentAsString()
        ));
    }

    private JsonNode awaitEventData(MvcResult result, String eventName) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            String body = result.getResponse().getContentAsString();
            String data = extractLatestEventData(body, eventName);
            if (data != null) {
                return objectMapper.readTree(data);
            }
            Thread.sleep(50L);
        }

        throw new AssertionError("SSE stream does not contain data for '%s'. Body: %s".formatted(
            eventName,
            result.getResponse().getContentAsString()
        ));
    }

    private String extractLatestEventData(String body, String eventName) {
        int eventIndex = body.lastIndexOf("event:" + eventName);
        if (eventIndex < 0) {
            eventIndex = body.lastIndexOf("event: " + eventName);
        }
        if (eventIndex < 0) {
            return null;
        }

        int dataIndex = body.indexOf("data:", eventIndex);
        if (dataIndex < 0) {
            return null;
        }

        int valueStart = dataIndex + "data:".length();
        int valueEnd = body.indexOf('\n', valueStart);
        if (valueEnd < 0) {
            return null;
        }

        return body.substring(valueStart, valueEnd).trim();
    }

    private void assertPlainNotificationPayload(JsonNode payload, String orderPublicId, String conversationPublicId) {
        assertThat(payload.isObject()).isTrue();
        assertThat(payload.size()).isEqualTo(2);
        assertThat(payload.path("orderPublicId").asText()).isEqualTo(orderPublicId);
        assertThat(payload.path("conversationPublicId").asText()).isEqualTo(conversationPublicId);
        assertThat(payload.has("array")).isFalse();
        assertThat(payload.has("containerNode")).isFalse();
        assertThat(payload.has("nodeType")).isFalse();
    }
}

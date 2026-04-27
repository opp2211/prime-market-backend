package ru.maltsev.primemarketbackend.notification.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "app.notifications.sse.keepalive-interval=1h",
        "app.notifications.sse.emitter-timeout=5m",
        "app.orders.pending-expire-sweep-delay=1h"
    }
)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationStreamConnectionLifecycleIntegrationTest extends AbstractNotificationStreamConnectionIntegrationTest {
    @Autowired
    private Environment environment;

    @Test
    void notificationStreamDoesNotHoldJdbcConnectionWhenOpenInViewDisabled() throws Exception {
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertConnectionHeldWhileStreamIsAlive(false);
    }
}

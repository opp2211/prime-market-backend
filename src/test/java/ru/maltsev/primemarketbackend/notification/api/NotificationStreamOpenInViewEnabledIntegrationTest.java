package ru.maltsev.primemarketbackend.notification.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.jpa.open-in-view=true",
        "app.notifications.sse.keepalive-interval=1h",
        "app.notifications.sse.emitter-timeout=5m",
        "app.orders.pending-expire-sweep-delay=1h"
    }
)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationStreamOpenInViewEnabledIntegrationTest extends AbstractNotificationStreamConnectionIntegrationTest {
    @Test
    void notificationStreamKeepsJdbcConnectionBorrowedWhileSseRequestIsAlive() throws Exception {
        assertConnectionHeldWhileStreamIsAlive(true);
    }
}

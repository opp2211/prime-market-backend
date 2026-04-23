package ru.maltsev.primemarketbackend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class NotificationSseRegistryTest {
    @Test
    void completionRemovesEmitterFromRegistry() throws Exception {
        NotificationSseRegistry registry = new NotificationSseRegistry(properties());
        NotificationSseRegistry.NotificationSseSession session = registry.register(101L);

        assertThat(registry.connectionCount(101L)).isEqualTo(1);

        session.emitter().complete();

        awaitUntil(() -> registry.connectionCount(101L) == 0);
    }

    @Test
    void errorRemovesEmitterFromRegistry() throws Exception {
        NotificationSseRegistry registry = new NotificationSseRegistry(properties());
        NotificationSseRegistry.NotificationSseSession session = registry.register(202L);

        assertThat(registry.connectionCount(202L)).isEqualTo(1);

        session.emitter().completeWithError(new IllegalStateException("boom"));

        awaitUntil(() -> registry.connectionCount(202L) == 0);
    }

    private NotificationStreamProperties properties() {
        return new NotificationStreamProperties(Duration.ofMinutes(30), Duration.ofSeconds(25));
    }

    private void awaitUntil(BooleanSupplier condition) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25L);
        }

        throw new AssertionError("Condition was not met before timeout");
    }
}

package ru.maltsev.primemarketbackend.order.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {
    private final OrderLifecycleService orderLifecycleService;

    @Scheduled(fixedDelayString = "${app.orders.pending-expire-sweep-delay:1m}")
    public void expirePendingOrders() {
        orderLifecycleService.expirePendingOrders(Instant.now());
    }
}

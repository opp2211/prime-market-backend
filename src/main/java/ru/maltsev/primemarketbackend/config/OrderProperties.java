package ru.maltsev.primemarketbackend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.orders")
public record OrderProperties(
    @DefaultValue("PT15M") Duration pendingTtl,
    @DefaultValue("PT1M") Duration pendingExpireSweepDelay,
    @DefaultValue("100") int defaultSellerFeeBps
) {
}

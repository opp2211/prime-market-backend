package ru.maltsev.primemarketbackend.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_account_hold_allocations")
public class UserAccountHoldAllocation {
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_RELEASED = "released";
    private static final String STATUS_CONSUMED = "consumed";
    private static final String STATUS_EXPIRED = "expired";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_account_hold_id", nullable = false)
    private Long userAccountHoldId;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false, precision = 13, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public UserAccountHoldAllocation(
        Long userAccountHoldId,
        Long orderId,
        BigDecimal amount,
        String status,
        Instant expiresAt
    ) {
        this.userAccountHoldId = userAccountHoldId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public void changeAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void markReleased(Instant releasedAt) {
        status = STATUS_RELEASED;
        this.releasedAt = releasedAt;
    }

    public void markConsumed(Instant consumedAt) {
        status = STATUS_CONSUMED;
        this.consumedAt = consumedAt;
    }

    public void markExpired() {
        status = STATUS_EXPIRED;
    }
}

package ru.maltsev.primemarketbackend.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_account_holds")
public class UserAccountHold {
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_RELEASED = "released";
    private static final String STATUS_CONSUMED = "consumed";
    private static final String STATUS_EXPIRED = "expired";
    private static final String REF_TYPE_OFFER = "offer";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "ref_type", nullable = false, length = 16)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(name = "user_account_id", nullable = false)
    private Long userAccountId;

    @Column(nullable = false, precision = 13, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false, length = 32)
    private String reason;

    @Column(name = "expires_at")
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

    public UserAccountHold(
        UUID publicId,
        String refType,
        Long refId,
        Long userAccountId,
        BigDecimal amount,
        String status,
        String reason,
        Instant expiresAt
    ) {
        this.publicId = publicId;
        this.refType = refType;
        this.refId = refId;
        this.userAccountId = userAccountId;
        this.amount = amount;
        this.status = status;
        this.reason = reason;
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public void activate(Long userAccountId, BigDecimal amount, Instant expiresAt) {
        this.userAccountId = userAccountId;
        this.amount = amount;
        this.status = STATUS_ACTIVE;
        this.expiresAt = expiresAt;
        this.releasedAt = null;
        this.consumedAt = null;
    }

    public void changeAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void markReleased(Instant releasedAt) {
        this.status = STATUS_RELEASED;
        this.releasedAt = releasedAt;
        if (REF_TYPE_OFFER.equals(refType)) {
            this.expiresAt = null;
        }
    }

    public void markConsumed(Instant consumedAt) {
        this.status = STATUS_CONSUMED;
        this.consumedAt = consumedAt;
        if (REF_TYPE_OFFER.equals(refType)) {
            this.expiresAt = null;
        }
    }

    public void markExpired() {
        this.status = STATUS_EXPIRED;
        if (REF_TYPE_OFFER.equals(refType)) {
            this.expiresAt = null;
        }
    }
}

package ru.maltsev.primemarketbackend.withdrawal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payout_profiles")
public class PayoutProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "withdrawal_method_id", nullable = false)
    private WithdrawalMethod withdrawalMethod;

    @Column(nullable = false)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> requisites = new LinkedHashMap<>();

    @Column(name = "is_default", nullable = false)
    private boolean defaultProfile;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    public PayoutProfile(
        Long userId,
        WithdrawalMethod withdrawalMethod,
        String title,
        Map<String, Object> requisites,
        boolean defaultProfile
    ) {
        this.userId = userId;
        this.withdrawalMethod = withdrawalMethod;
        this.title = title;
        this.requisites = new LinkedHashMap<>(requisites);
        this.defaultProfile = defaultProfile;
        this.active = true;
    }

    public void update(String title, Map<String, Object> requisites) {
        this.title = title;
        this.requisites = new LinkedHashMap<>(requisites);
    }

    public void markDefault() {
        this.defaultProfile = true;
    }

    public void clearDefault() {
        this.defaultProfile = false;
    }

    public void deactivate() {
        this.active = false;
        this.defaultProfile = false;
    }

    @PrePersist
    private void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }
}

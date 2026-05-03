package ru.maltsev.primemarketbackend.money.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "money_operation_events")
public class MoneyOperationEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 64)
    private MoneyOperationType operationType;

    @Column(name = "operation_id", nullable = false)
    private Long operationId;

    @Column(name = "operation_public_id", nullable = false)
    private UUID operationPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 96)
    private MoneyOperationEventType eventType;

    @Column(name = "status_before", length = 64)
    private String statusBefore;

    @Column(name = "status_after", length = 64)
    private String statusAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 32)
    private MoneyOperationActorType actorType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "public_note")
    private String publicNote;

    @Column(name = "operator_note")
    private String operatorNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    public MoneyOperationEvent(
        MoneyOperationType operationType,
        Long operationId,
        UUID operationPublicId,
        MoneyOperationEventType eventType,
        String statusBefore,
        String statusAfter,
        MoneyOperationActorType actorType,
        Long actorUserId,
        String publicNote,
        String operatorNote,
        Map<String, Object> payload
    ) {
        this.operationType = operationType;
        this.operationId = operationId;
        this.operationPublicId = operationPublicId;
        this.eventType = eventType;
        this.statusBefore = statusBefore;
        this.statusAfter = statusAfter;
        this.actorType = actorType;
        this.actorUserId = actorUserId;
        this.publicNote = publicNote;
        this.operatorNote = operatorNote;
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }

    @PrePersist
    private void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }
}

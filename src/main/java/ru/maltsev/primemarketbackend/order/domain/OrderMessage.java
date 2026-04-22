package ru.maltsev.primemarketbackend.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_messages")
public class OrderMessage {
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_user_id")
    private Long senderUserId;

    @Column(name = "message_type", nullable = false, length = 16)
    private String messageType;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public OrderMessage(
        UUID publicId,
        Long conversationId,
        Long senderUserId,
        String messageType,
        String body
    ) {
        this.publicId = publicId;
        this.conversationId = conversationId;
        this.senderUserId = senderUserId;
        this.messageType = messageType;
        this.body = body;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}

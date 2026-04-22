package ru.maltsev.primemarketbackend.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "order_conversations")
public class OrderConversation {
    public static final String TYPE_MAIN = "order_main";
    public static final String TYPE_SUPPORT_BUYER = "order_support_buyer";
    public static final String TYPE_SUPPORT_SELLER = "order_support_seller";
    public static final String STATUS_ACTIVE = "active";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "conversation_type", nullable = false, length = 32)
    private String conversationType;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    public OrderConversation(
        UUID publicId,
        Long orderId,
        String conversationType,
        String status
    ) {
        this.publicId = publicId;
        this.orderId = orderId;
        this.conversationType = conversationType;
        this.status = status;
    }

    public void markLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }
}

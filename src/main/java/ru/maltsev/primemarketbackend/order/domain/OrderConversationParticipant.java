package ru.maltsev.primemarketbackend.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_conversation_participants")
public class OrderConversationParticipant {
    public static final String ROLE_BUYER = "buyer";
    public static final String ROLE_SELLER = "seller";
    public static final String ROLE_SUPPORT = "support";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "participant_role", nullable = false, length = 16)
    private String participantRole;

    @Column(name = "joined_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant joinedAt;

    public OrderConversationParticipant(
        Long conversationId,
        Long userId,
        String participantRole
    ) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.participantRole = participantRole;
    }
}

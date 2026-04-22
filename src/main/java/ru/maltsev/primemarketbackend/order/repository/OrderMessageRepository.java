package ru.maltsev.primemarketbackend.order.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.order.domain.OrderMessage;

public interface OrderMessageRepository extends JpaRepository<OrderMessage, Long> {
    Optional<OrderMessage> findByPublicIdAndConversationId(UUID publicId, Long conversationId);

    boolean existsByConversationIdAndMessageTypeAndDeletedAtIsNull(
        Long conversationId,
        String messageType
    );

    List<OrderMessage> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
        Long conversationId,
        Pageable pageable
    );

    @Query("""
        select m
        from OrderMessage m
        where m.conversationId = :conversationId
          and m.deletedAt is null
          and (
              m.createdAt < :createdAt
              or (m.createdAt = :createdAt and m.id < :id)
          )
        order by m.createdAt desc, m.id desc
        """)
    List<OrderMessage> findBefore(
        @Param("conversationId") Long conversationId,
        @Param("createdAt") Instant createdAt,
        @Param("id") Long id,
        Pageable pageable
    );
}

package ru.maltsev.primemarketbackend.order.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.order.domain.OrderConversation;

public interface OrderConversationRepository extends JpaRepository<OrderConversation, Long> {
    Optional<OrderConversation> findByPublicId(UUID publicId);

    Optional<OrderConversation> findByOrderIdAndConversationType(Long orderId, String conversationType);

    List<OrderConversation> findAllByOrderIdAndConversationTypeIn(
        Long orderId,
        Collection<String> conversationTypes
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OrderConversation c where c.publicId = :publicId")
    Optional<OrderConversation> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select c
        from OrderConversation c
        where c.orderId = :orderId
          and c.conversationType = :conversationType
        """)
    Optional<OrderConversation> findByOrderIdAndConversationTypeForUpdate(
        @Param("orderId") Long orderId,
        @Param("conversationType") String conversationType
    );
}

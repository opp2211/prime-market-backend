package ru.maltsev.primemarketbackend.order.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.order.domain.OrderConversationParticipant;

public interface OrderConversationParticipantRepository
    extends JpaRepository<OrderConversationParticipant, Long> {

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    List<OrderConversationParticipant> findAllByConversationId(Long conversationId);
}

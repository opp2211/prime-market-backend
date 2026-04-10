package ru.maltsev.primemarketbackend.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.order.api.dto.OrderEventsResponse;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.domain.OrderEvent;
import ru.maltsev.primemarketbackend.order.repository.OrderEventReadQueryRepository;
import ru.maltsev.primemarketbackend.order.repository.OrderReadQueryRepository;

@Service
@RequiredArgsConstructor
public class OrderEventReadService {
    private final OrderReadQueryRepository orderReadQueryRepository;
    private final OrderEventReadQueryRepository orderEventReadQueryRepository;

    @Transactional(readOnly = true)
    public OrderEventsResponse getOrderEvents(UUID publicOrderId, Long currentUserId) {
        Order order = orderReadQueryRepository.findParticipantOrder(publicOrderId, currentUserId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "ORDER_NOT_FOUND",
                "Order not found"
            ));

        List<OrderEventsResponse.Item> items = orderEventReadQueryRepository
            .findAllByOrderIdOrderedOldestFirst(order.getId())
            .stream()
            .map(this::toItem)
            .toList();
        return new OrderEventsResponse(items);
    }

    private OrderEventsResponse.Item toItem(OrderEvent event) {
        JsonNode payload = event.getPayload() == null
            ? JsonNodeFactory.instance.objectNode()
            : event.getPayload().deepCopy();
        return new OrderEventsResponse.Item(
            event.getId(),
            event.getEventType(),
            new OrderEventsResponse.Actor(event.getActorUserId(), event.getActorRole()),
            payload,
            event.getCreatedAt()
        );
    }
}

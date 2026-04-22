package ru.maltsev.primemarketbackend.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.order.domain.OrderEvent;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
}

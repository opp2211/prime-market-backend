package ru.maltsev.primemarketbackend.order.api;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.order.api.dto.OrderEventsResponse;
import ru.maltsev.primemarketbackend.order.service.OrderEventReadService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderEventReadController {
    private final OrderEventReadService orderEventReadService;

    @GetMapping("/{orderId}/events")
    public ResponseEntity<OrderEventsResponse> getOrderEvents(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID orderId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderEventsResponse response = orderEventReadService.getOrderEvents(orderId, principal.getUser().getId());
        return ResponseEntity.ok(response);
    }
}

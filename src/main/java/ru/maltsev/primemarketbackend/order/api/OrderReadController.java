package ru.maltsev.primemarketbackend.order.api;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.order.api.dto.MyOrdersRequest;
import ru.maltsev.primemarketbackend.order.api.dto.MyOrdersResponse;
import ru.maltsev.primemarketbackend.order.api.dto.OrderDetailsResponse;
import ru.maltsev.primemarketbackend.order.service.OrderReadService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderReadController {
    private final OrderReadService orderReadService;

    @GetMapping("/my/orders")
    public ResponseEntity<MyOrdersResponse> getMyOrders(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        MyOrdersResponse response = orderReadService.getMyOrders(
            principal.getUser().getId(),
            new MyOrdersRequest(status, role, page, size)
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderDetailsResponse> getOrderDetails(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID orderId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderDetailsResponse response = orderReadService.getOrderDetails(orderId, principal);
        return ResponseEntity.ok(response);
    }
}

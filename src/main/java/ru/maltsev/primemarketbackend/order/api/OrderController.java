package ru.maltsev.primemarketbackend.order.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
import ru.maltsev.primemarketbackend.order.api.dto.CreateOrderRequest;
import ru.maltsev.primemarketbackend.order.api.dto.MarkPartiallyDeliveredRequest;
import ru.maltsev.primemarketbackend.order.api.dto.OrderRequestResponse;
import ru.maltsev.primemarketbackend.order.api.dto.OrderResponse;
import ru.maltsev.primemarketbackend.order.api.dto.RequestAmendQuantityRequest;
import ru.maltsev.primemarketbackend.order.service.OrderLifecycleService;
import ru.maltsev.primemarketbackend.order.service.OrderRequestService;
import ru.maltsev.primemarketbackend.order.service.OrderService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final OrderLifecycleService orderLifecycleService;
    private final OrderRequestService orderRequestService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateOrderRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderResponse response = orderService.createOrder(principal.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{orderId}/confirm-ready")
    public ResponseEntity<OrderResponse> confirmReady(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID orderId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderResponse response = orderLifecycleService.confirmReady(orderId, principal.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID orderId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderResponse response = orderLifecycleService.cancel(orderId, principal.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/request-cancel")
    public ResponseEntity<OrderRequestResponse> requestCancel(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID orderId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderRequestResponse response = orderRequestService.requestCancel(orderId, principal.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{orderId}/request-amend-quantity")
    public ResponseEntity<OrderRequestResponse> requestAmendQuantity(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID orderId,
        @RequestBody(required = false) RequestAmendQuantityRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderRequestResponse response = orderRequestService.requestAmendQuantity(
            orderId,
            principal.getUser().getId(),
            request == null ? null : request.quantity()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{orderId}/mark-partially-delivered")
    public ResponseEntity<OrderResponse> markPartiallyDelivered(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID orderId,
        @Valid @RequestBody MarkPartiallyDeliveredRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderResponse response = orderLifecycleService.markPartiallyDelivered(
            orderId,
            principal.getUser().getId(),
            request.deliveredQuantity()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/mark-delivered")
    public ResponseEntity<OrderResponse> markDelivered(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID orderId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderResponse response = orderLifecycleService.markDelivered(orderId, principal.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/confirm-received")
    public ResponseEntity<OrderResponse> confirmReceived(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID orderId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderResponse response = orderLifecycleService.confirmReceived(orderId, principal.getUser().getId());
        return ResponseEntity.ok(response);
    }
}

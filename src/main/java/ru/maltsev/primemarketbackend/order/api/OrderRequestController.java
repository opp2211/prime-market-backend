package ru.maltsev.primemarketbackend.order.api;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.order.api.dto.OrderRequestResponse;
import ru.maltsev.primemarketbackend.order.service.OrderRequestService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/order-requests")
@RequiredArgsConstructor
public class OrderRequestController {
    private final OrderRequestService orderRequestService;

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<OrderRequestResponse> approve(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID requestId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(orderRequestService.approve(requestId, principal.getUser().getId()));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<OrderRequestResponse> reject(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID requestId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(orderRequestService.reject(requestId, principal.getUser().getId()));
    }
}

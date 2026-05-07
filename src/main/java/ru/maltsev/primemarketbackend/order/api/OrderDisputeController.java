package ru.maltsev.primemarketbackend.order.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.order.api.dto.CreateOrderDisputeRequest;
import ru.maltsev.primemarketbackend.order.api.dto.OrderDisputeResponse;
import ru.maltsev.primemarketbackend.order.api.dto.ResolveOrderDisputeAmendQuantityRequest;
import ru.maltsev.primemarketbackend.order.api.dto.ResolveOrderDisputeRequest;
import ru.maltsev.primemarketbackend.order.service.OrderDisputeService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderDisputeController {
    private final OrderDisputeService orderDisputeService;

    @PostMapping("/orders/{orderCode}/disputes")
    public ResponseEntity<OrderDisputeResponse> openDispute(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable String orderCode,
        @Valid @RequestBody CreateOrderDisputeRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderDisputeResponse response = orderDisputeService.openDispute(orderCode, principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders/{orderCode}/dispute")
    public ResponseEntity<OrderDisputeResponse> getOrderDispute(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable String orderCode
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(orderDisputeService.getOrderDispute(orderCode, principal));
    }

    @PostMapping("/order-disputes/{disputeCode}/take")
    @PreAuthorize("hasAuthority('ORDER_DISPUTES_TAKE')")
    public ResponseEntity<OrderDisputeResponse> takeDispute(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable String disputeCode
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(orderDisputeService.takeDispute(disputeCode, principal));
    }

    @PostMapping("/order-disputes/{disputeCode}/resolve-cancel")
    @PreAuthorize("hasAuthority('ORDER_DISPUTES_RESOLVE')")
    public ResponseEntity<OrderDisputeResponse> resolveCancel(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable String disputeCode,
        @RequestBody(required = false) ResolveOrderDisputeRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(orderDisputeService.resolveCancel(disputeCode, principal, request));
    }

    @PostMapping("/order-disputes/{disputeCode}/resolve-complete")
    @PreAuthorize("hasAuthority('ORDER_DISPUTES_RESOLVE')")
    public ResponseEntity<OrderDisputeResponse> resolveComplete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable String disputeCode,
        @RequestBody(required = false) ResolveOrderDisputeRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(orderDisputeService.resolveComplete(disputeCode, principal, request));
    }

    @PostMapping("/order-disputes/{disputeCode}/resolve-amend-quantity-and-complete")
    @PreAuthorize("hasAuthority('ORDER_DISPUTES_RESOLVE')")
    public ResponseEntity<OrderDisputeResponse> resolveAmendQuantityAndComplete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable String disputeCode,
        @Valid @RequestBody ResolveOrderDisputeAmendQuantityRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(orderDisputeService.resolveAmendQuantityAndComplete(disputeCode, principal, request));
    }
}

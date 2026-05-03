package ru.maltsev.primemarketbackend.deposit.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.deposit.api.dto.CreateDepositPaymentRouteRequest;
import ru.maltsev.primemarketbackend.deposit.api.dto.DepositPaymentRouteResponse;
import ru.maltsev.primemarketbackend.deposit.api.dto.UpdateDepositPaymentRouteRequest;
import ru.maltsev.primemarketbackend.deposit.service.DepositPaymentRouteService;
import ru.maltsev.primemarketbackend.security.PermissionCodes;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/backoffice/deposit-payment-routes")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + PermissionCodes.TREASURY_VIEW + "') or hasAuthority('" + PermissionCodes.DEPOSIT_APPROVE + "')")
public class BackofficeDepositPaymentRouteController {
    private final DepositPaymentRouteService depositPaymentRouteService;

    @GetMapping
    public ResponseEntity<List<DepositPaymentRouteResponse>> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(name = "deposit_method_id", required = false) Long depositMethodId,
        @RequestParam(name = "active_only", required = false) Boolean activeOnly
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(depositPaymentRouteService.listRoutes(depositMethodId, activeOnly).stream()
            .map(DepositPaymentRouteResponse::from)
            .toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.TREASURY_MANAGE + "')")
    public ResponseEntity<DepositPaymentRouteResponse> create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateDepositPaymentRouteRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(DepositPaymentRouteResponse.from(depositPaymentRouteService.createRoute(request)));
    }

    @PatchMapping("/{publicId}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.TREASURY_MANAGE + "')")
    public ResponseEntity<DepositPaymentRouteResponse> update(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId,
        @Valid @RequestBody UpdateDepositPaymentRouteRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(DepositPaymentRouteResponse.from(
            depositPaymentRouteService.updateRoute(publicId, request)
        ));
    }
}

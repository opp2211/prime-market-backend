package ru.maltsev.primemarketbackend.deposit.api;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.deposit.api.dto.CreateDepositRequest;
import ru.maltsev.primemarketbackend.deposit.api.dto.DepositRequestResponse;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.deposit.service.DepositRequestService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/deposit-requests")
@RequiredArgsConstructor
public class DepositRequestController {
    private final DepositRequestService depositRequestService;

    @PostMapping
    public ResponseEntity<DepositRequestResponse> create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateDepositRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        DepositRequest depositRequest = depositRequestService.create(principal.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DepositRequestResponse.from(depositRequest));
    }

    @GetMapping
    public ResponseEntity<Page<DepositRequestResponse>> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) String status,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Page<DepositRequestResponse> response = depositRequestService
            .listForUser(principal.getUser().getId(), status, pageable)
            .map(DepositRequestResponse::from);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<DepositRequestResponse> get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        DepositRequest depositRequest = depositRequestService.getForUser(publicId, principal.getUser().getId());
        return ResponseEntity.ok(DepositRequestResponse.from(depositRequest));
    }

    @PostMapping("/{publicId}/mark-paid")
    public ResponseEntity<DepositRequestResponse> markPaid(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        DepositRequest depositRequest = depositRequestService.markPaid(publicId, principal.getUser().getId());
        return ResponseEntity.ok(DepositRequestResponse.from(depositRequest));
    }

    @PostMapping("/{publicId}/cancel")
    public ResponseEntity<DepositRequestResponse> cancel(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        DepositRequest depositRequest = depositRequestService.cancel(publicId, principal.getUser().getId());
        return ResponseEntity.ok(DepositRequestResponse.from(depositRequest));
    }
}

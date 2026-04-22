package ru.maltsev.primemarketbackend.withdrawal.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.security.PermissionCodes;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.BackofficeWithdrawalRequestResponse;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.ConfirmWithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.RejectWithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.service.WithdrawalRequestService;

@RestController
@RequestMapping("/api/backoffice/withdrawal-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + PermissionCodes.WITHDRAWAL_REQUESTS_VIEW + "')")
public class BackofficeWithdrawalRequestController {
    private final WithdrawalRequestService withdrawalRequestService;

    @GetMapping
    public ResponseEntity<Page<BackofficeWithdrawalRequestResponse>> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) List<String> status,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(withdrawalRequestService.listForBackoffice(status, pageable)
            .map(BackofficeWithdrawalRequestResponse::from));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<BackofficeWithdrawalRequestResponse> get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(BackofficeWithdrawalRequestResponse.from(
            withdrawalRequestService.getForBackoffice(publicId)
        ));
    }

    @PostMapping("/{publicId}/take")
    @PreAuthorize("hasAuthority('" + PermissionCodes.WITHDRAWAL_REQUESTS_TAKE + "')")
    public ResponseEntity<BackofficeWithdrawalRequestResponse> take(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        WithdrawalRequest request = withdrawalRequestService.take(publicId, principal.getUser().getId());
        return ResponseEntity.ok(BackofficeWithdrawalRequestResponse.from(request));
    }

    @PostMapping("/{publicId}/reject")
    @PreAuthorize("hasAuthority('" + PermissionCodes.WITHDRAWAL_REQUESTS_REJECT + "')")
    public ResponseEntity<BackofficeWithdrawalRequestResponse> reject(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId,
        @Valid @RequestBody RejectWithdrawalRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        WithdrawalRequest withdrawalRequest = withdrawalRequestService.reject(
            publicId,
            principal.getUser().getId(),
            request
        );
        return ResponseEntity.ok(BackofficeWithdrawalRequestResponse.from(withdrawalRequest));
    }

    @PostMapping("/{publicId}/confirm")
    @PreAuthorize("hasAuthority('" + PermissionCodes.WITHDRAWAL_REQUESTS_CONFIRM + "')")
    public ResponseEntity<BackofficeWithdrawalRequestResponse> confirm(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId,
        @Valid @RequestBody(required = false) ConfirmWithdrawalRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        WithdrawalRequest withdrawalRequest = withdrawalRequestService.confirm(
            publicId,
            principal.getUser().getId(),
            request
        );
        return ResponseEntity.ok(BackofficeWithdrawalRequestResponse.from(withdrawalRequest));
    }
}

package ru.maltsev.primemarketbackend.deposit.api;

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
import ru.maltsev.primemarketbackend.deposit.api.dto.AdminDepositRequestResponse;
import ru.maltsev.primemarketbackend.deposit.api.dto.AdminDepositRequestShortResponse;
import ru.maltsev.primemarketbackend.deposit.api.dto.IssueDetailsRequest;
import ru.maltsev.primemarketbackend.deposit.api.dto.RejectDepositRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.deposit.service.DepositRequestService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/admin/deposit-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('DEPOSIT_APPROVE')")
public class DepositRequestAdminController {
    private final DepositRequestService depositRequestService;

    @GetMapping
    public ResponseEntity<Page<AdminDepositRequestShortResponse>> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) List<String> status,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Page<AdminDepositRequestShortResponse> response = depositRequestService
            .listForAdmin(status, pageable)
            .map(AdminDepositRequestShortResponse::from);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<AdminDepositRequestResponse> get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        DepositRequest depositRequest = depositRequestService.getByPublicIdForAdmin(publicId);
        return ResponseEntity.ok(AdminDepositRequestResponse.from(depositRequest));
    }

    @PostMapping("/{publicId}/issue-details")
    public ResponseEntity<AdminDepositRequestResponse> issueDetails(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId,
        @Valid @RequestBody IssueDetailsRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        DepositRequest depositRequest = depositRequestService.issueDetails(publicId, request.paymentDetails());
        return ResponseEntity.ok(AdminDepositRequestResponse.from(depositRequest));
    }

    @PostMapping("/{publicId}/confirm")
    public ResponseEntity<AdminDepositRequestResponse> confirm(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        DepositRequest depositRequest = depositRequestService.confirm(publicId);
        return ResponseEntity.ok(AdminDepositRequestResponse.from(depositRequest));
    }

    @PostMapping("/{publicId}/reject")
    public ResponseEntity<AdminDepositRequestResponse> reject(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId,
        @Valid @RequestBody RejectDepositRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        DepositRequest depositRequest = depositRequestService.reject(publicId, request.rejectReason());
        return ResponseEntity.ok(AdminDepositRequestResponse.from(depositRequest));
    }
}

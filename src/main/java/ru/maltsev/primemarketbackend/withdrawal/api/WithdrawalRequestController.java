package ru.maltsev.primemarketbackend.withdrawal.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.CreateWithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.WithdrawalRequestResponse;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.service.WithdrawalRequestService;

@RestController
@RequestMapping("/api/withdrawal-requests")
@RequiredArgsConstructor
public class WithdrawalRequestController {
    private final WithdrawalRequestService withdrawalRequestService;

    @PostMapping
    @ApiResponse(
        responseCode = "201",
        description = "Created",
        content = @Content(schema = @Schema(implementation = WithdrawalRequestResponse.class))
    )
    public ResponseEntity<WithdrawalRequestResponse> create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateWithdrawalRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        WithdrawalRequest withdrawalRequest = withdrawalRequestService.create(principal.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(WithdrawalRequestResponse.from(withdrawalRequest));
    }

    @GetMapping
    @Operation(
        summary = "List user withdrawal requests",
        description = "Supports only the canonical single-value `status` filter plus standard pageable query params `page`, `size`, and `sort`. `statuses` and `currency_code` filters are not supported by this endpoint."
    )
    public ResponseEntity<Page<WithdrawalRequestResponse>> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @Parameter(
            description = "Canonical single-value request status. Omit the parameter to return requests of all statuses.",
            schema = @Schema(
                type = "string",
                allowableValues = { "OPEN", "PROCESSING", "COMPLETED", "CANCELLED", "REJECTED" }
            )
        )
        @RequestParam(required = false) String status,
        @ParameterObject
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(withdrawalRequestService
            .listForUser(principal.getUser().getId(), status, pageable)
            .map(WithdrawalRequestResponse::from));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<WithdrawalRequestResponse> get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(WithdrawalRequestResponse.from(
            withdrawalRequestService.getForUser(publicId, principal.getUser().getId())
        ));
    }

    @PostMapping("/{publicId}/cancel")
    public ResponseEntity<WithdrawalRequestResponse> cancel(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        WithdrawalRequest withdrawalRequest = withdrawalRequestService.cancel(publicId, principal.getUser().getId());
        return ResponseEntity.ok(WithdrawalRequestResponse.from(withdrawalRequest));
    }
}

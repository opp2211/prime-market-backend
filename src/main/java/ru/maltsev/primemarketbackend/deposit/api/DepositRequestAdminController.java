package ru.maltsev.primemarketbackend.deposit.api;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
import ru.maltsev.primemarketbackend.deposit.api.dto.ConfirmDepositRequest;
import ru.maltsev.primemarketbackend.deposit.api.dto.IssueDetailsRequest;
import ru.maltsev.primemarketbackend.deposit.api.dto.RejectDepositRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.deposit.service.DepositRequestService;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationEvent;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationType;
import ru.maltsev.primemarketbackend.money.service.MoneyOperationEventService;
import ru.maltsev.primemarketbackend.security.PermissionCodes;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryTransaction;
import ru.maltsev.primemarketbackend.treasury.service.TreasuryService;

@RestController
@RequestMapping({"/api/admin/deposit-requests", "/api/backoffice/deposit-requests"})
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + PermissionCodes.DEPOSIT_APPROVE + "')")
public class DepositRequestAdminController {
    private final DepositRequestService depositRequestService;
    private final MoneyOperationEventService moneyOperationEventService;
    private final TreasuryService treasuryService;

    @GetMapping
    public ResponseEntity<Page<AdminDepositRequestShortResponse>> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @Parameter(
            description = "Canonical multi-value status filter. Supports repeated `status` params and comma-separated values in a single parameter.",
            example = "PENDING_DETAILS,WAITING_PAYMENT",
            array = @ArraySchema(schema = @Schema(
                type = "string",
                allowableValues = {
                    "PENDING_DETAILS",
                    "WAITING_PAYMENT",
                    "PAYMENT_VERIFICATION",
                    "CONFIRMED",
                    "REJECTED",
                    "EXPIRED",
                    "CANCELLED"
                }
            ))
        )
        @RequestParam(required = false) List<String> status,
        @ParameterObject
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Page<AdminDepositRequestShortResponse> response = depositRequestService.listShortForAdmin(status, pageable);
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
        return ResponseEntity.ok(AdminDepositRequestResponse.from(
            depositRequest,
            eventsFor(depositRequest),
            treasuryTransactionsFor(depositRequest)
        ));
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

        DepositRequest depositRequest = depositRequestService.issueDetails(
            publicId,
            principal.getUser().getId(),
            request.paymentDetails(),
            request.operatorComment()
        );
        return ResponseEntity.ok(AdminDepositRequestResponse.from(
            depositRequest,
            eventsFor(depositRequest),
            treasuryTransactionsFor(depositRequest)
        ));
    }

    @PostMapping("/{publicId}/confirm")
    public ResponseEntity<AdminDepositRequestResponse> confirm(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId,
        @RequestBody(required = false) ConfirmDepositRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        DepositRequest depositRequest = depositRequestService.confirm(
            publicId,
            principal.getUser().getId(),
            request == null ? null : request.confirmationReference(),
            request == null ? null : request.operatorComment(),
            request == null ? null : request.treasuryAccountPublicId(),
            request == null ? null : request.treasuryAmount(),
            request == null ? null : request.treasuryExternalReference()
        );
        return ResponseEntity.ok(AdminDepositRequestResponse.from(
            depositRequest,
            eventsFor(depositRequest),
            treasuryTransactionsFor(depositRequest)
        ));
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

        DepositRequest depositRequest = depositRequestService.reject(
            publicId,
            principal.getUser().getId(),
            request.rejectReason(),
            request.operatorComment()
        );
        return ResponseEntity.ok(AdminDepositRequestResponse.from(
            depositRequest,
            eventsFor(depositRequest),
            treasuryTransactionsFor(depositRequest)
        ));
    }

    private List<MoneyOperationEvent> eventsFor(DepositRequest request) {
        return moneyOperationEventService.list(MoneyOperationType.DEPOSIT_REQUEST, request.getPublicId());
    }

    private List<TreasuryTransaction> treasuryTransactionsFor(DepositRequest request) {
        return treasuryService.listOperationTransactions(MoneyOperationType.DEPOSIT_REQUEST, request.getPublicId());
    }
}

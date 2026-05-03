package ru.maltsev.primemarketbackend.treasury.api;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.security.PermissionCodes;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.treasury.api.dto.CreateTreasuryAccountRequest;
import ru.maltsev.primemarketbackend.treasury.api.dto.CreateTreasuryTransactionRequest;
import ru.maltsev.primemarketbackend.treasury.api.dto.CreateTreasuryTransferRequest;
import ru.maltsev.primemarketbackend.treasury.api.dto.TreasuryAccountResponse;
import ru.maltsev.primemarketbackend.treasury.api.dto.TreasuryExposureResponse;
import ru.maltsev.primemarketbackend.treasury.api.dto.TreasuryTransactionResponse;
import ru.maltsev.primemarketbackend.treasury.api.dto.TreasuryTransferResponse;
import ru.maltsev.primemarketbackend.treasury.api.dto.UpdateTreasuryAccountRequest;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryTransaction;
import ru.maltsev.primemarketbackend.treasury.service.TreasuryExposureService;
import ru.maltsev.primemarketbackend.treasury.service.TreasuryService;

@RestController
@RequestMapping("/api/backoffice/treasury")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + PermissionCodes.TREASURY_VIEW + "')")
public class BackofficeTreasuryController {
    private final TreasuryService treasuryService;
    private final TreasuryExposureService treasuryExposureService;

    @GetMapping("/accounts")
    public ResponseEntity<List<TreasuryAccountResponse>> listAccounts(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(name = "active_only", required = false) Boolean activeOnly
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(treasuryService.listAccounts(activeOnly).stream()
            .map(TreasuryAccountResponse::from)
            .toList());
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasAuthority('" + PermissionCodes.TREASURY_MANAGE + "')")
    public ResponseEntity<TreasuryAccountResponse> createAccount(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateTreasuryAccountRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(TreasuryAccountResponse.from(treasuryService.createAccount(request)));
    }

    @PatchMapping("/accounts/{publicId}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.TREASURY_MANAGE + "')")
    public ResponseEntity<TreasuryAccountResponse> updateAccount(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId,
        @RequestBody UpdateTreasuryAccountRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(TreasuryAccountResponse.from(treasuryService.updateAccount(publicId, request)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<TreasuryTransactionResponse>> listTransactions(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(name = "account_public_id", required = false) UUID accountPublicId,
        @ParameterObject
        @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(treasuryService.listTransactions(accountPublicId, pageable)
            .map(TreasuryTransactionResponse::from));
    }

    @GetMapping("/exposure")
    public ResponseEntity<TreasuryExposureResponse> exposure(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(treasuryExposureService.buildExposureReport());
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.TREASURY_MANAGE + "')")
    public ResponseEntity<TreasuryTransactionResponse> createTransaction(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateTreasuryTransactionRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(TreasuryTransactionResponse.from(
                treasuryService.recordManualTransaction(request, principal.getUser().getId())
            ));
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAuthority('" + PermissionCodes.TREASURY_MANAGE + "')")
    public ResponseEntity<TreasuryTransferResponse> createTransfer(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateTreasuryTransferRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<TreasuryTransaction> transactions = treasuryService.recordTransfer(request, principal.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new TreasuryTransferResponse(
                TreasuryTransactionResponse.from(transactions.get(0)),
                TreasuryTransactionResponse.from(transactions.get(1))
            ));
    }
}

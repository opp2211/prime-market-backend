package ru.maltsev.primemarketbackend.platform.api;

import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.platform.api.dto.CreatePlatformAccountAdjustmentRequest;
import ru.maltsev.primemarketbackend.platform.api.dto.PlatformAccountResponse;
import ru.maltsev.primemarketbackend.platform.api.dto.PlatformAccountTransactionResponse;
import ru.maltsev.primemarketbackend.platform.service.PlatformAccountService;
import ru.maltsev.primemarketbackend.security.PermissionCodes;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/backoffice/platform-accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + PermissionCodes.PLATFORM_ACCOUNTS_VIEW + "') or hasAuthority('" + PermissionCodes.TREASURY_VIEW + "')")
public class BackofficePlatformAccountController {
    private final PlatformAccountService platformAccountService;

    @GetMapping
    public ResponseEntity<List<PlatformAccountResponse>> listAccounts(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(platformAccountService.listAccounts().stream()
            .map(PlatformAccountResponse::from)
            .toList());
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<PlatformAccountTransactionResponse>> listTransactions(
        @AuthenticationPrincipal UserPrincipal principal,
        @ParameterObject
        @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(platformAccountService.listTransactions(pageable)
            .map(PlatformAccountTransactionResponse::from));
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.PLATFORM_ACCOUNTS_MANAGE + "') or hasAuthority('" + PermissionCodes.TREASURY_MANAGE + "')")
    public ResponseEntity<PlatformAccountTransactionResponse> createAdjustment(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreatePlatformAccountAdjustmentRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PlatformAccountTransactionResponse.from(
                platformAccountService.recordAdjustment(request, principal.getUser().getId())
            ));
    }
}

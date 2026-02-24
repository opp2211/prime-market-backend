package ru.maltsev.primemarketbackend.account.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.account.api.dto.UserAccountResponse;
import ru.maltsev.primemarketbackend.account.api.dto.UserAccountTxResponse;
import ru.maltsev.primemarketbackend.account.service.UserAccountService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class UserAccountController {
    private final UserAccountService userAccountService;

    @GetMapping("/me")
    public ResponseEntity<Map<String, UserAccountResponse>> getMyWallets(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long userId = principal.getUser().getId();
        Map<String, UserAccountResponse> responseMap = userAccountService.getUserAccountsWithPositiveBalance(userId);
        return ResponseEntity.ok(responseMap);
    }

    @GetMapping("/me/txs")
    public ResponseEntity<Page<UserAccountTxResponse>> getMyTransactions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long userId = principal.getUser().getId();
        Page<UserAccountTxResponse> response = userAccountService.getUserAccountTxs(
                userId,
                currency,
                type,
                from,
                to,
                pageable
        );
        return ResponseEntity.ok(response);
    }
}

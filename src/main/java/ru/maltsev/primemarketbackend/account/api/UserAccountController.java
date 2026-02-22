package ru.maltsev.primemarketbackend.account.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.account.api.dto.UserAccountResponse;
import ru.maltsev.primemarketbackend.account.service.UserAccountService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

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
        Map<String, UserAccountResponse> responseMap = userAccountService.getUserAccounts(userId);
        return ResponseEntity.ok(responseMap);
    }
}

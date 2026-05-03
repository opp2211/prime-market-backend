package ru.maltsev.primemarketbackend.deposit.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.deposit.api.dto.DepositMethodResponse;
import ru.maltsev.primemarketbackend.deposit.service.DepositMethodService;
import ru.maltsev.primemarketbackend.security.PermissionCodes;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/backoffice/deposit-methods")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + PermissionCodes.TREASURY_VIEW + "') or hasAuthority('" + PermissionCodes.DEPOSIT_APPROVE + "')")
public class BackofficeDepositMethodController {
    private final DepositMethodService depositMethodService;

    @GetMapping
    public ResponseEntity<List<DepositMethodResponse>> list(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(depositMethodService.getAllDepositMethods());
    }
}

package ru.maltsev.primemarketbackend.order.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.order.api.dto.BackofficeDisputesResponse;
import ru.maltsev.primemarketbackend.order.service.OrderDisputeService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/backoffice/disputes")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ORDER_DISPUTES_VIEW')")
public class BackofficeOrderDisputeController {
    private final OrderDisputeService orderDisputeService;

    @GetMapping
    public ResponseEntity<BackofficeDisputesResponse> getDisputes(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(orderDisputeService.getBackofficeDisputes(principal));
    }
}

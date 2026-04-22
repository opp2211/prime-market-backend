package ru.maltsev.primemarketbackend.withdrawal.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.CreatePayoutProfileRequest;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.PayoutProfileResponse;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.UpdatePayoutProfileRequest;
import ru.maltsev.primemarketbackend.withdrawal.domain.PayoutProfile;
import ru.maltsev.primemarketbackend.withdrawal.service.PayoutProfileService;

@RestController
@RequestMapping("/api/payout-profiles")
@RequiredArgsConstructor
public class PayoutProfileController {
    private final PayoutProfileService payoutProfileService;

    @GetMapping
    public ResponseEntity<List<PayoutProfileResponse>> list(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(payoutProfileService.list(principal.getUser().getId()).stream()
            .map(PayoutProfileResponse::from)
            .toList());
    }

    @PostMapping
    public ResponseEntity<PayoutProfileResponse> create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreatePayoutProfileRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PayoutProfile profile = payoutProfileService.create(principal.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PayoutProfileResponse.from(profile));
    }

    @PatchMapping("/{publicId}")
    public ResponseEntity<PayoutProfileResponse> update(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId,
        @RequestBody UpdatePayoutProfileRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PayoutProfile profile = payoutProfileService.update(principal.getUser().getId(), publicId, request);
        return ResponseEntity.ok(PayoutProfileResponse.from(profile));
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        payoutProfileService.delete(principal.getUser().getId(), publicId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{publicId}/make-default")
    public ResponseEntity<PayoutProfileResponse> makeDefault(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PayoutProfile profile = payoutProfileService.makeDefault(principal.getUser().getId(), publicId);
        return ResponseEntity.ok(PayoutProfileResponse.from(profile));
    }
}

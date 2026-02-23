package ru.maltsev.primemarketbackend.user.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.auth.api.dto.StatusResponse;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.user.api.dto.ChangeEmailRequest;
import ru.maltsev.primemarketbackend.user.api.dto.ChangePasswordRequest;
import ru.maltsev.primemarketbackend.user.api.dto.ConfirmEmailChangeRequest;
import ru.maltsev.primemarketbackend.user.api.dto.ConfirmPasswordChangeRequest;
import ru.maltsev.primemarketbackend.user.api.dto.UserProfileResponse;
import ru.maltsev.primemarketbackend.user.service.UserProfileService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {
    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(UserProfileResponse.from(principal.getUser()));
    }

    @PostMapping("/me/email-change")
    public ResponseEntity<StatusResponse> requestEmailChange(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody ChangeEmailRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        userProfileService.requestEmailChange(principal.getUser(), request.newEmail(), request.currentPassword());
        return ResponseEntity.accepted().body(StatusResponse.sent());
    }

    @PostMapping("/me/password-change")
    public ResponseEntity<StatusResponse> requestPasswordChange(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody ChangePasswordRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        userProfileService.requestPasswordChange(principal.getUser(), request.currentPassword(), request.newPassword());
        return ResponseEntity.accepted().body(StatusResponse.sent());
    }

    @PostMapping("/email-change/confirm")
    public ResponseEntity<Void> confirmEmailChange(@Valid @RequestBody ConfirmEmailChangeRequest request) {
        userProfileService.confirmEmailChange(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-change/confirm")
    public ResponseEntity<Void> confirmPasswordChange(@Valid @RequestBody ConfirmPasswordChangeRequest request) {
        userProfileService.confirmPasswordChange(request.token());
        return ResponseEntity.noContent().build();
    }
}

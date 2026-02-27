package ru.maltsev.primemarketbackend.user.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.user.api.dto.UpdateUserRoleRequest;
import ru.maltsev.primemarketbackend.user.api.dto.UserRoleResponse;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.service.UserAdminService;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {
    private final UserAdminService userAdminService;

    @PatchMapping("/{userId}/role")
    public ResponseEntity<UserRoleResponse> updateRole(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long userId,
        @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userAdminService.updateRole(userId, request.role());
        return ResponseEntity.ok(UserRoleResponse.from(user));
    }
}

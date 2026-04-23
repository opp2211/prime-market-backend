package ru.maltsev.primemarketbackend.notification.api;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.notification.api.dto.MarkAllNotificationsReadResponse;
import ru.maltsev.primemarketbackend.notification.api.dto.NotificationResponse;
import ru.maltsev.primemarketbackend.notification.api.dto.UnreadNotificationsCountResponse;
import ru.maltsev.primemarketbackend.notification.service.NotificationService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) Boolean isRead,
        @ParameterObject
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(notificationService.listForUser(principal.getUser().getId(), isRead, pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadNotificationsCountResponse> unreadCount(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(new UnreadNotificationsCountResponse(
            notificationService.countUnread(principal.getUser().getId())
        ));
    }

    @PostMapping("/{publicId}/read")
    public ResponseEntity<NotificationResponse> markRead(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID publicId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(notificationService.markRead(principal.getUser().getId(), publicId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<MarkAllNotificationsReadResponse> markAllRead(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(new MarkAllNotificationsReadResponse(
            notificationService.markAllRead(principal.getUser().getId())
        ));
    }
}

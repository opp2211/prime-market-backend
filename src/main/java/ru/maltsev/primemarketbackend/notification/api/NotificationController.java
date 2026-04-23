package ru.maltsev.primemarketbackend.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.maltsev.primemarketbackend.notification.api.dto.MarkAllNotificationsReadResponse;
import ru.maltsev.primemarketbackend.notification.api.dto.NotificationResponse;
import ru.maltsev.primemarketbackend.notification.api.dto.UnreadNotificationsCountResponse;
import ru.maltsev.primemarketbackend.notification.service.NotificationService;
import ru.maltsev.primemarketbackend.notification.service.NotificationStreamService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationStreamService notificationStreamService;

    @Operation(
        summary = "Subscribe to live notification updates",
        description = "Opens a user-level Server-Sent Events stream for new notifications and unread count updates."
    )
    @ApiResponse(
        responseCode = "200",
        description = "SSE stream with notification events",
        content = @Content(
            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
            schema = @Schema(type = "string"),
            examples = @ExampleObject(
                value = """
                    event: stream.connected
                    data: {"connectionId":"2cb4c52f-c5ee-4033-b9d4-f2bcd0f8d53c","connectedAt":"2026-04-23T10:15:30Z"}

                    event: notifications.unread_count
                    data: {"count":5}

                    event: notification.created
                    data: {"publicId":"311f9519-a43a-4305-9d26-bd0941961ec7","type":"order_created","title":"Order created","body":"A new order has been created.","payload":{"orderPublicId":"55c1327d-749e-4d9f-9f95-c56dcafec1d9"},"isRead":false,"createdAt":"2026-04-23T10:16:00Z","readAt":null}
                    """
            )
        )
    )
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("X-Accel-Buffering", "no")
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(notificationStreamService.subscribe(principal.getUser().getId()));
    }

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

package ru.maltsev.primemarketbackend.order.api;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.order.api.dto.OrderConversationListResponse;
import ru.maltsev.primemarketbackend.order.api.dto.OrderMessageResponse;
import ru.maltsev.primemarketbackend.order.api.dto.OrderMessagesResponse;
import ru.maltsev.primemarketbackend.order.api.dto.SendOrderMessageRequest;
import ru.maltsev.primemarketbackend.order.service.OrderConversationService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderConversationController {
    private final OrderConversationService orderConversationService;

    @GetMapping("/orders/{orderId}/conversations")
    public ResponseEntity<OrderConversationListResponse> getOrderConversations(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID orderId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderConversationListResponse response = orderConversationService.getOrderConversations(orderId, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/order-conversations/{conversationId}/messages")
    public ResponseEntity<OrderMessagesResponse> getMessages(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID conversationId,
        @RequestParam(required = false) UUID before,
        @RequestParam(required = false) Integer size
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderMessagesResponse response = orderConversationService.getMessages(
            conversationId,
            before,
            size,
            principal
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/order-conversations/{conversationId}/messages")
    public ResponseEntity<OrderMessageResponse> sendMessage(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID conversationId,
        @RequestBody(required = false) SendOrderMessageRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OrderMessageResponse response = orderConversationService.sendMessage(
            conversationId,
            principal,
            request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

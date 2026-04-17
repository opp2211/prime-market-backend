package ru.maltsev.primemarketbackend.order.api.dto;

import java.util.List;

public record OrderConversationListResponse(
    List<OrderConversationResponse> items
) {
}

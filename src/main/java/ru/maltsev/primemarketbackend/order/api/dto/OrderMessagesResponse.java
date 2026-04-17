package ru.maltsev.primemarketbackend.order.api.dto;

import java.util.List;

public record OrderMessagesResponse(
    List<OrderMessageResponse> items
) {
}

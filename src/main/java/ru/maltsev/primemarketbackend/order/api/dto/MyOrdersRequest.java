package ru.maltsev.primemarketbackend.order.api.dto;

public record MyOrdersRequest(
    String status,
    String role,
    Integer page,
    Integer size
) {
}

package ru.maltsev.primemarketbackend.order.api.dto;

import java.util.List;

public record BackofficeDisputesResponse(
    List<OrderDisputeResponse> open,
    List<OrderDisputeResponse> inReview,
    List<OrderDisputeResponse> resolved
) {
}

package ru.maltsev.primemarketbackend.order.api.dto;

import java.math.BigDecimal;

public record RequestAmendQuantityRequest(BigDecimal quantity) {
}

package ru.maltsev.primemarketbackend.money.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationActorType;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationEvent;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationEventType;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationType;

public record MoneyOperationEventResponse(
    Long id,
    @JsonProperty("operation_type") MoneyOperationType operationType,
    @JsonProperty("operation_code") String operationCode,
    @JsonProperty("event_type") MoneyOperationEventType eventType,
    @JsonProperty("status_before") String statusBefore,
    @JsonProperty("status_after") String statusAfter,
    @JsonProperty("actor_type") MoneyOperationActorType actorType,
    @JsonProperty("actor_user_id") Long actorUserId,
    @JsonProperty("public_note") String publicNote,
    @JsonProperty("operator_note") String operatorNote,
    Map<String, Object> payload,
    @JsonProperty("created_at") Instant createdAt
) {
    public static MoneyOperationEventResponse from(MoneyOperationEvent event) {
        return new MoneyOperationEventResponse(
            event.getId(),
            event.getOperationType(),
            event.getOperationCode(),
            event.getEventType(),
            event.getStatusBefore(),
            event.getStatusAfter(),
            event.getActorType(),
            event.getActorUserId(),
            event.getPublicNote(),
            event.getOperatorNote(),
            event.getPayload(),
            event.getCreatedAt()
        );
    }
}

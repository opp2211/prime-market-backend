package ru.maltsev.primemarketbackend.money.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationActorType;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationEvent;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationEventType;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationType;
import ru.maltsev.primemarketbackend.money.repository.MoneyOperationEventRepository;

@Service
@RequiredArgsConstructor
public class MoneyOperationEventService {
    private final MoneyOperationEventRepository eventRepository;

    public MoneyOperationEvent record(
        MoneyOperationType operationType,
        Long operationId,
        String operationCode,
        MoneyOperationEventType eventType,
        String statusBefore,
        String statusAfter,
        MoneyOperationActorType actorType,
        Long actorUserId,
        String publicNote,
        String operatorNote,
        Map<String, Object> payload
    ) {
        return eventRepository.save(new MoneyOperationEvent(
            operationType,
            operationId,
            operationCode,
            eventType,
            statusBefore,
            statusAfter,
            actorType,
            actorUserId,
            normalize(publicNote),
            normalize(operatorNote),
            normalizePayload(payload)
        ));
    }

    @Transactional(readOnly = true)
    public List<MoneyOperationEvent> list(MoneyOperationType operationType, String operationCode) {
        return eventRepository.findAllByOperationTypeAndOperationCodeOrderByCreatedAtAscIdAsc(
            operationType,
            operationCode
        );
    }

    public Map<String, Object> payload(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (values == null) {
            return result;
        }
        for (int index = 0; index + 1 < values.length; index += 2) {
            Object key = values[index];
            Object value = values[index + 1];
            if (key == null || value == null) {
                continue;
            }
            result.put(String.valueOf(key), value);
        }
        return result;
    }

    private Map<String, Object> normalizePayload(Map<String, Object> payload) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (payload == null) {
            return result;
        }
        payload.forEach((key, value) -> {
            if (key != null && value != null) {
                result.put(key, value);
            }
        });
        return result;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

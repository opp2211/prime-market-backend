package ru.maltsev.primemarketbackend.withdrawal.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalMethod;

@Service
public class WithdrawalRequisitesValidator {
    public Map<String, Object> validateAndNormalize(WithdrawalMethod method, Map<String, Object> requisites) {
        if (requisites == null || requisites.isEmpty()) {
            throw validationError("Withdrawal requisites are required");
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : requisites.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof String stringValue) {
                String trimmed = stringValue.trim();
                if (!trimmed.isEmpty()) {
                    normalized.put(key, trimmed);
                }
                continue;
            }
            if (value instanceof Number || value instanceof Boolean) {
                normalized.put(key, value.toString());
                continue;
            }
            throw validationError("Requisite '%s' must be a scalar value".formatted(key));
        }

        requireFields(normalized, requiredFieldsFromSchema(method.getRequisitesSchema()));
        validateByMethodCode(method.getCode(), normalized);
        return normalized;
    }

    private void validateByMethodCode(String methodCode, Map<String, Object> requisites) {
        String normalizedCode = methodCode == null ? "" : methodCode.trim().toUpperCase(Locale.ROOT);
        switch (normalizedCode) {
            case "SBP" -> requireFields(requisites, List.of("phoneNumber", "bankName", "recipientName"));
            case "ONCHAIN_USDT" -> requireFields(requisites, List.of("address", "network"));
            case "BINANCE_UID", "BYBIT_UID" -> requireFields(requisites, List.of("uid"));
            default -> {
            }
        }
    }

    private List<String> requiredFieldsFromSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return List.of();
        }

        Object fields = schema.get("fields");
        if (!(fields instanceof Iterable<?> iterable)) {
            return List.of();
        }

        List<String> requiredFields = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> field)) {
                continue;
            }
            Object required = field.get("required");
            Object name = field.get("name");
            if (Boolean.TRUE.equals(required) && name instanceof String stringName && !stringName.isBlank()) {
                requiredFields.add(stringName);
            }
        }
        return requiredFields;
    }

    private void requireFields(Map<String, Object> requisites, List<String> fields) {
        List<String> missing = fields.stream()
            .filter(field -> {
                Object value = requisites.get(field);
                return !(value instanceof String stringValue) || stringValue.isBlank();
            })
            .distinct()
            .toList();
        if (!missing.isEmpty()) {
            throw validationError("Missing required requisites: " + String.join(", ", missing));
        }
    }

    private ApiProblemException validationError(String detail) {
        return new ApiProblemException(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            detail
        );
    }
}

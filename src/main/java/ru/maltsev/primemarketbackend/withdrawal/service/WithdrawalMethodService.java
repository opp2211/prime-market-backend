package ru.maltsev.primemarketbackend.withdrawal.service;

import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.WithdrawalMethodResponse;
import ru.maltsev.primemarketbackend.withdrawal.repository.WithdrawalMethodRepository;

@Service
@RequiredArgsConstructor
public class WithdrawalMethodService {
    private final WithdrawalMethodRepository withdrawalMethodRepository;

    public List<WithdrawalMethodResponse> getActiveMethodsByCurrency(String currencyCode) {
        String normalizedCurrencyCode = normalizeCurrencyCode(currencyCode);
        return withdrawalMethodRepository.findAllByCurrencyCodeIgnoreCaseAndActiveTrueOrderByIdAsc(normalizedCurrencyCode)
            .stream()
            .map(WithdrawalMethodResponse::from)
            .toList();
    }

    private String normalizeCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Query parameter 'currency_code' is required"
            );
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }
}

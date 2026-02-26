package ru.maltsev.primemarketbackend.deposit.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.deposit.api.dto.DepositMethodResponse;
import ru.maltsev.primemarketbackend.deposit.repository.DepositMethodRepository;

@Service
@RequiredArgsConstructor
public class DepositMethodService {
    private final DepositMethodRepository depositMethodRepository;

    public List<DepositMethodResponse> getActiveDepositMethodsByCurrency(String currencyCode) {
        return depositMethodRepository.findAllByCurrencyCodeAndActiveTrueOrderByIdAsc(currencyCode)
            .stream()
            .map(DepositMethodResponse::from)
            .toList();
    }
}

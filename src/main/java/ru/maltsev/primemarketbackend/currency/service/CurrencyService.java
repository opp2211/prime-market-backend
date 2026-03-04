package ru.maltsev.primemarketbackend.currency.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.currency.api.dto.CurrencyResponse;
import ru.maltsev.primemarketbackend.currency.repository.CurrencyRepository;

@Service
@RequiredArgsConstructor
public class CurrencyService {
    private final CurrencyRepository currencyRepository;

    public List<CurrencyResponse> getActiveCurrencies() {
        return currencyRepository.findAllByActiveTrueOrderByCodeAsc()
            .stream()
            .map(CurrencyResponse::from)
            .toList();
    }
}
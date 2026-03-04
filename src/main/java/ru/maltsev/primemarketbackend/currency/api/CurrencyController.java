package ru.maltsev.primemarketbackend.currency.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.currency.api.dto.CurrencyResponse;
import ru.maltsev.primemarketbackend.currency.service.CurrencyService;

@RestController
@RequestMapping("/api/currencies")
@RequiredArgsConstructor
public class CurrencyController {
    private final CurrencyService currencyService;

    @GetMapping
    public ResponseEntity<List<CurrencyResponse>> getActiveCurrencies() {
        List<CurrencyResponse> response = currencyService.getActiveCurrencies();
        return ResponseEntity.ok(response);
    }
}
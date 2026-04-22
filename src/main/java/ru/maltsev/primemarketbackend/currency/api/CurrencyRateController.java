package ru.maltsev.primemarketbackend.currency.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.currency.api.dto.CurrencyRateResponse;
import ru.maltsev.primemarketbackend.currency.service.CurrencyService;

@RestController
@RequestMapping("/api/currency-rates")
@RequiredArgsConstructor
public class CurrencyRateController {
    private final CurrencyService currencyService;

    @GetMapping
    public ResponseEntity<CurrencyRateResponse> getCurrencyRate(
        @RequestParam(name = "from", required = false) String fromCurrencyCode,
        @RequestParam(name = "to", required = false) String toCurrencyCode
    ) {
        CurrencyRateResponse response = currencyService.getCurrencyRate(fromCurrencyCode, toCurrencyCode);
        return ResponseEntity.ok(response);
    }
}

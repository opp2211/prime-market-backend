package ru.maltsev.primemarketbackend.currency.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.currency.api.dto.CreateCurrencyConversionRequest;
import ru.maltsev.primemarketbackend.currency.api.dto.CurrencyConversionResponse;
import ru.maltsev.primemarketbackend.currency.domain.UserCurrencyConversion;
import ru.maltsev.primemarketbackend.currency.service.CurrencyConversionService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/currency-conversions")
@RequiredArgsConstructor
public class CurrencyConversionController {
    private final CurrencyConversionService currencyConversionService;

    @PostMapping
    public ResponseEntity<CurrencyConversionResponse> convert(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateCurrencyConversionRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserCurrencyConversion conversion = currencyConversionService.convert(principal.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CurrencyConversionResponse.from(conversion));
    }
}

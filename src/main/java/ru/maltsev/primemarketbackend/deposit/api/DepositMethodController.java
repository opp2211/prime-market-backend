package ru.maltsev.primemarketbackend.deposit.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.deposit.api.dto.DepositMethodResponse;
import ru.maltsev.primemarketbackend.deposit.service.DepositMethodService;

@RestController
@RequestMapping("/api/deposit-methods")
@RequiredArgsConstructor
public class DepositMethodController {
    private final DepositMethodService depositMethodService;

    @GetMapping
    public ResponseEntity<List<DepositMethodResponse>> getActiveDepositMethods(
        @RequestParam("currency_code") String currencyCode
    ) {
        List<DepositMethodResponse> response = depositMethodService.getActiveDepositMethodsByCurrency(currencyCode);
        return ResponseEntity.ok(response);
    }
}

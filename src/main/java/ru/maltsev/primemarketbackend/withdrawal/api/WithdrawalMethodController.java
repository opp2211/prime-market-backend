package ru.maltsev.primemarketbackend.withdrawal.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.WithdrawalMethodResponse;
import ru.maltsev.primemarketbackend.withdrawal.service.WithdrawalMethodService;

@RestController
@RequestMapping("/api/withdrawal-methods")
@RequiredArgsConstructor
public class WithdrawalMethodController {
    private final WithdrawalMethodService withdrawalMethodService;

    @GetMapping
    public ResponseEntity<List<WithdrawalMethodResponse>> getActiveWithdrawalMethods(
        @RequestParam("currency_code") String currencyCode
    ) {
        return ResponseEntity.ok(withdrawalMethodService.getActiveMethodsByCurrency(currencyCode));
    }
}

package ru.maltsev.primemarketbackend.orderquote.api;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.orderquote.api.dto.CreateOrderQuoteRequest;
import ru.maltsev.primemarketbackend.orderquote.api.dto.OrderQuoteResponse;
import ru.maltsev.primemarketbackend.orderquote.service.OrderQuoteService;

@RestController
@RequiredArgsConstructor
public class OrderQuoteController {
    private final OrderQuoteService orderQuoteService;

    @PostMapping("/api/market/offers/{offerId}/quote")
    public ResponseEntity<OrderQuoteResponse> createQuote(
        @PathVariable Long offerId,
        @Valid @RequestBody CreateOrderQuoteRequest request
    ) {
        return ResponseEntity.ok(orderQuoteService.createQuote(offerId, request));
    }

    @PostMapping("/api/order-quotes/{quoteId}/refresh")
    public ResponseEntity<OrderQuoteResponse> refreshQuote(@PathVariable UUID quoteId) {
        return ResponseEntity.ok(orderQuoteService.refreshQuote(quoteId));
    }
}

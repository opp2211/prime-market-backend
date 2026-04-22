package ru.maltsev.primemarketbackend.offer.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferResponse;
import ru.maltsev.primemarketbackend.offer.service.OfferService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/my/offers")
@RequiredArgsConstructor
public class MyOfferController {
    private final OfferService offerService;

    @GetMapping
    public ResponseEntity<List<OfferResponse>> listMyOffers(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<OfferResponse> response = offerService.listViewsForUser(principal.getUser().getId())
            .stream()
            .map(OfferResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }
}

package ru.maltsev.primemarketbackend.offer.api;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferCreateRequest;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferAttributeResponse;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferDetailsResponse;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferContextResponse;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferResponse;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferUpdateRequest;
import ru.maltsev.primemarketbackend.offer.domain.Offer;
import ru.maltsev.primemarketbackend.offer.repository.OfferView;
import ru.maltsev.primemarketbackend.offer.service.OfferService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
public class OfferController {
    private final OfferService offerService;

    @PostMapping
    public ResponseEntity<OfferResponse> create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody OfferCreateRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Offer offer = offerService.create(principal.getUser().getId(), request);
        OfferView response = offerService.getViewForUser(offer.getId(), principal.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(OfferResponse.from(response));
    }

    @GetMapping("/{offerId}")
    public ResponseEntity<OfferDetailsResponse> getMyOffer(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long offerId
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OfferView offer = offerService.getViewForUser(offerId, principal.getUser().getId());
        List<OfferContextResponse> contexts = offerService.getContextValues(offerId)
            .stream()
            .map(OfferContextResponse::from)
            .toList();
        List<OfferAttributeResponse> attributes = offerService.getAttributeValues(offerId)
            .stream()
            .map(OfferAttributeResponse::from)
            .toList();
        List<String> deliveryMethods = offerService.getDeliveryMethods(offerId);
        return ResponseEntity.ok(OfferDetailsResponse.from(offer, contexts, attributes, deliveryMethods));
    }

    @PatchMapping("/{offerId}")
    public ResponseEntity<OfferResponse> update(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long offerId,
        @Valid @RequestBody OfferUpdateRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Offer offer = offerService.update(offerId, principal.getUser().getId(), request);
        OfferView response = offerService.getViewForUser(offer.getId(), principal.getUser().getId());
        return ResponseEntity.ok(OfferResponse.from(response));
    }
}

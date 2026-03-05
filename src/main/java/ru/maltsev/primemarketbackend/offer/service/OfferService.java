package ru.maltsev.primemarketbackend.offer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import ru.maltsev.primemarketbackend.currency.domain.FxRate;
import ru.maltsev.primemarketbackend.currency.repository.FxRateRepository;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferResponse;
import ru.maltsev.primemarketbackend.offer.domain.Offer;
import ru.maltsev.primemarketbackend.offer.domain.OfferSide;
import ru.maltsev.primemarketbackend.offer.domain.OfferStatus;
import ru.maltsev.primemarketbackend.offer.repository.OfferRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OfferService {

    private static final int PRICE_SCALE = 4;

    private final OfferRepository offerRepository;
    private final FxRateRepository fxRateRepository;

    public List<OfferResponse> listOffers(Long productId, Long serverId, String side, String currencyCode) {
        OfferSide offerSide = parseSide(side);
        String targetCurrencyCode = normalizeCurrencyCode(currencyCode);

        List<Offer> offers = offerRepository.findOfferByProductIdAndServerIdAndStatusAndSide(
                productId,
                serverId,
                OfferStatus.ACTIVE,
                offerSide
        );

        if (offers.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now();
        Map<String, BigDecimal> cachedRates = new HashMap<>();
        Set<String> missingRates = new HashSet<>();
        List<OfferWithPrice> converted = new ArrayList<>();

        for (Offer offer : offers) {
            String offerCode = normalizeCurrencyCode(offer.getCurrencyCode());
            BigDecimal convertedPrice = convertPrice(offerSide, offer.getUnitPrice(), offerCode, targetCurrencyCode, now,
                    cachedRates, missingRates);
            if (convertedPrice == null) {
                continue;
            }
            converted.add(new OfferWithPrice(offer, convertedPrice));
        }

        if (converted.isEmpty()) {
            return List.of();
        }

        Comparator<OfferWithPrice> comparator = Comparator.comparing(OfferWithPrice::price);
        if (offerSide == OfferSide.BUY) {
            comparator = comparator.reversed();
        }
        comparator = comparator.thenComparing(o -> o.offer().getPublicId());

        return converted.stream()
                .sorted(comparator)
                .map(item -> toResponse(item.offer(), item.price()))
                .toList();
    }

    private OfferResponse toResponse(Offer offer, BigDecimal price) {
        return new OfferResponse(
                offer.getPublicId(),
                offer.getSide().name().toLowerCase(Locale.ROOT),
                price,
                offer.getQuantity(),
                offer.getMinQuantity(),
                offer.getMultiplicity()
        );
    }

    private BigDecimal convertPrice(OfferSide side,
                                    BigDecimal unitPrice,
                                    String offerCode,
                                    String targetCode,
                                    Instant now,
                                    Map<String, BigDecimal> cachedRates,
                                    Set<String> missingRates) {
        if (offerCode.equals(targetCode)) {
            return unitPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        }

        if (missingRates.contains(offerCode)) {
            return null;
        }

        BigDecimal rate = cachedRates.get(offerCode);
        if (rate == null) {
            FxRate fxRate = switch (side) {
                case SELL -> fxRateRepository
                        .findFirstByBaseCodeAndQuoteCodeAndValidToGreaterThanEqualOrderByValidToDesc(targetCode, offerCode, now)
                        .orElse(null);
                case BUY -> fxRateRepository
                        .findFirstByBaseCodeAndQuoteCodeAndValidToGreaterThanEqualOrderByValidToDesc(offerCode, targetCode, now)
                        .orElse(null);
            };
            if (fxRate == null) {
                missingRates.add(offerCode);
                return null;
            }
            rate = fxRate.getRate();
            cachedRates.put(offerCode, rate);
        }

        return switch (side) {
            case SELL -> unitPrice
                    .divide(rate, PRICE_SCALE + 8, RoundingMode.HALF_UP)
                    .setScale(PRICE_SCALE, RoundingMode.HALF_UP);
            case BUY -> unitPrice
                    .multiply(rate)
                    .setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        };

    }

    private OfferSide parseSide(String side) {
        if (!StringUtils.hasText(side)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "side is required");
        }
        try {
            return OfferSide.valueOf(side.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid side: " + side);
        }
    }

    private String normalizeCurrencyCode(String currencyCode) {
        if (!StringUtils.hasText(currencyCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencyCode is required");
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private record OfferWithPrice(Offer offer, BigDecimal price) {
    }
}

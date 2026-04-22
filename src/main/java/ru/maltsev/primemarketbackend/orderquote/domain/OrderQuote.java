package ru.maltsev.primemarketbackend.orderquote.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_quotes")
public class OrderQuote {
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_EXPIRED = "expired";
    private static final String STATUS_CONSUMED = "consumed";
    private static final String STATUS_INVALIDATED = "invalidated";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "offer_id", nullable = false)
    private Long offerId;

    @Column(name = "offer_version_snapshot", nullable = false)
    private Long offerVersionSnapshot;

    @Column(nullable = false, length = 8)
    private String intent;

    @Column(name = "viewer_currency_code", nullable = false, length = 5)
    private String viewerCurrencyCode;

    @Column(name = "offer_side_snapshot", nullable = false, length = 8)
    private String offerSideSnapshot;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "owner_username_snapshot", nullable = false, length = 24)
    private String ownerUsernameSnapshot;

    @Column(name = "game_id_snapshot", nullable = false)
    private Long gameIdSnapshot;

    @Column(name = "game_slug_snapshot", nullable = false, length = 64)
    private String gameSlugSnapshot;

    @Column(name = "game_title_snapshot", nullable = false, length = 100)
    private String gameTitleSnapshot;

    @Column(name = "category_id_snapshot", nullable = false)
    private Long categoryIdSnapshot;

    @Column(name = "category_slug_snapshot", nullable = false, length = 64)
    private String categorySlugSnapshot;

    @Column(name = "category_title_snapshot", nullable = false, length = 100)
    private String categoryTitleSnapshot;

    @Column(name = "title_snapshot", length = 200)
    private String titleSnapshot;

    @Column(name = "description_snapshot")
    private String descriptionSnapshot;

    @Column(name = "trade_terms_snapshot")
    private String tradeTermsSnapshot;

    @Column(name = "published_at_snapshot", nullable = false)
    private Instant publishedAtSnapshot;

    @Column(name = "quantity_snapshot", nullable = false, precision = 18, scale = 8)
    private BigDecimal quantitySnapshot;

    @Column(name = "min_trade_quantity_snapshot", precision = 18, scale = 8)
    private BigDecimal minTradeQuantitySnapshot;

    @Column(name = "max_trade_quantity_snapshot", precision = 18, scale = 8)
    private BigDecimal maxTradeQuantitySnapshot;

    @Column(name = "quantity_step_snapshot", precision = 18, scale = 8)
    private BigDecimal quantityStepSnapshot;

    @Column(name = "offer_price_currency_code_snapshot", nullable = false, length = 5)
    private String offerPriceCurrencyCodeSnapshot;

    @Column(name = "offer_price_amount_snapshot", nullable = false, precision = 18, scale = 8)
    private BigDecimal offerPriceAmountSnapshot;

    @Column(name = "fx_from_currency_code", nullable = false, length = 5)
    private String fxFromCurrencyCode;

    @Column(name = "fx_to_currency_code", nullable = false, length = 5)
    private String fxToCurrencyCode;

    @Column(name = "fx_rate", nullable = false, precision = 20, scale = 8)
    private BigDecimal fxRate;

    @Column(name = "display_unit_price_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal displayUnitPriceAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contexts_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode contextsSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode attributesSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_methods_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode deliveryMethodsSnapshot;

    @Column(nullable = false, length = 16)
    private String status = STATUS_ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    public OrderQuote(
        UUID publicId,
        Long offerId,
        Long offerVersionSnapshot,
        String intent,
        String viewerCurrencyCode,
        String offerSideSnapshot,
        Long ownerUserId,
        String ownerUsernameSnapshot,
        Long gameIdSnapshot,
        String gameSlugSnapshot,
        String gameTitleSnapshot,
        Long categoryIdSnapshot,
        String categorySlugSnapshot,
        String categoryTitleSnapshot,
        String titleSnapshot,
        String descriptionSnapshot,
        String tradeTermsSnapshot,
        Instant publishedAtSnapshot,
        BigDecimal quantitySnapshot,
        BigDecimal minTradeQuantitySnapshot,
        BigDecimal maxTradeQuantitySnapshot,
        BigDecimal quantityStepSnapshot,
        String offerPriceCurrencyCodeSnapshot,
        BigDecimal offerPriceAmountSnapshot,
        String fxFromCurrencyCode,
        String fxToCurrencyCode,
        BigDecimal fxRate,
        BigDecimal displayUnitPriceAmount,
        JsonNode contextsSnapshot,
        JsonNode attributesSnapshot,
        JsonNode deliveryMethodsSnapshot,
        Instant expiresAt
    ) {
        this.publicId = publicId;
        this.offerId = offerId;
        this.offerVersionSnapshot = offerVersionSnapshot;
        this.intent = intent;
        this.viewerCurrencyCode = viewerCurrencyCode;
        this.offerSideSnapshot = offerSideSnapshot;
        this.ownerUserId = ownerUserId;
        this.ownerUsernameSnapshot = ownerUsernameSnapshot;
        this.gameIdSnapshot = gameIdSnapshot;
        this.gameSlugSnapshot = gameSlugSnapshot;
        this.gameTitleSnapshot = gameTitleSnapshot;
        this.categoryIdSnapshot = categoryIdSnapshot;
        this.categorySlugSnapshot = categorySlugSnapshot;
        this.categoryTitleSnapshot = categoryTitleSnapshot;
        this.titleSnapshot = titleSnapshot;
        this.descriptionSnapshot = descriptionSnapshot;
        this.tradeTermsSnapshot = tradeTermsSnapshot;
        this.publishedAtSnapshot = publishedAtSnapshot;
        this.quantitySnapshot = quantitySnapshot;
        this.minTradeQuantitySnapshot = minTradeQuantitySnapshot;
        this.maxTradeQuantitySnapshot = maxTradeQuantitySnapshot;
        this.quantityStepSnapshot = quantityStepSnapshot;
        this.offerPriceCurrencyCodeSnapshot = offerPriceCurrencyCodeSnapshot;
        this.offerPriceAmountSnapshot = offerPriceAmountSnapshot;
        this.fxFromCurrencyCode = fxFromCurrencyCode;
        this.fxToCurrencyCode = fxToCurrencyCode;
        this.fxRate = fxRate;
        this.displayUnitPriceAmount = displayUnitPriceAmount;
        this.contextsSnapshot = contextsSnapshot;
        this.attributesSnapshot = attributesSnapshot;
        this.deliveryMethodsSnapshot = deliveryMethodsSnapshot;
        this.expiresAt = expiresAt;
        this.status = STATUS_ACTIVE;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public boolean isConsumed() {
        return STATUS_CONSUMED.equals(status);
    }

    public boolean isInvalidated() {
        return STATUS_INVALIDATED.equals(status);
    }

    public void markExpired() {
        status = STATUS_EXPIRED;
    }

    public void markInvalidated() {
        status = STATUS_INVALIDATED;
    }

    public void markConsumed() {
        status = STATUS_CONSUMED;
    }
}

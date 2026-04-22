package ru.maltsev.primemarketbackend.order.domain;

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
@Table(name = "orders")
public class Order {
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_IN_PROGRESS = "in_progress";
    private static final String STATUS_PARTIALLY_DELIVERED = "partially_delivered";
    private static final String STATUS_DELIVERED = "delivered";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_CANCELED = "canceled";
    private static final String STATUS_EXPIRED = "expired";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "order_quote_id", nullable = false, unique = true)
    private Long orderQuoteId;

    @Column(name = "maker_user_id", nullable = false)
    private Long makerUserId;

    @Column(name = "taker_user_id", nullable = false)
    private Long takerUserId;

    @Column(name = "maker_role", nullable = false, length = 8)
    private String makerRole;

    @Column(name = "taker_role", nullable = false, length = 8)
    private String takerRole;

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

    @Column(name = "offer_side_snapshot", nullable = false, length = 8)
    private String offerSideSnapshot;

    @Column(name = "intent_snapshot", nullable = false, length = 8)
    private String intentSnapshot;

    @Column(name = "owner_username_snapshot", nullable = false, length = 24)
    private String ownerUsernameSnapshot;

    @Column(name = "taker_username_snapshot", nullable = false, length = 24)
    private String takerUsernameSnapshot;

    @Column(name = "title_snapshot", length = 200)
    private String titleSnapshot;

    @Column(name = "description_snapshot")
    private String descriptionSnapshot;

    @Column(name = "trade_terms_snapshot")
    private String tradeTermsSnapshot;

    @Column(name = "ordered_quantity", nullable = false, precision = 18, scale = 8)
    private BigDecimal orderedQuantity;

    @Column(name = "delivered_quantity", nullable = false, precision = 18, scale = 8)
    private BigDecimal deliveredQuantity;

    @Column(name = "offer_price_currency_code_snapshot", nullable = false, length = 5)
    private String offerPriceCurrencyCodeSnapshot;

    @Column(name = "offer_price_amount_snapshot", nullable = false, precision = 18, scale = 8)
    private BigDecimal offerPriceAmountSnapshot;

    @Column(name = "viewer_currency_code_snapshot", nullable = false, length = 5)
    private String viewerCurrencyCodeSnapshot;

    @Column(name = "fx_from_currency_code", nullable = false, length = 5)
    private String fxFromCurrencyCode;

    @Column(name = "fx_to_currency_code", nullable = false, length = 5)
    private String fxToCurrencyCode;

    @Column(name = "fx_rate", nullable = false, precision = 20, scale = 8)
    private BigDecimal fxRate;

    @Column(name = "display_unit_price_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal displayUnitPriceAmount;

    @Column(name = "display_total_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal displayTotalAmount;

    @Column(name = "seller_gross_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal sellerGrossAmount;

    @Column(name = "seller_fee_bps_snapshot", nullable = false)
    private int sellerFeeBpsSnapshot;

    @Column(name = "seller_fee_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal sellerFeeAmount;

    @Column(name = "seller_net_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal sellerNetAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contexts_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode contextsSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode attributesSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_methods_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode deliveryMethodsSnapshot;

    @Column(name = "cancel_requested_by_role", length = 8)
    private String cancelRequestedByRole;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    public Order(
        UUID publicId,
        Long orderQuoteId,
        Long makerUserId,
        Long takerUserId,
        String makerRole,
        String takerRole,
        Long gameIdSnapshot,
        String gameSlugSnapshot,
        String gameTitleSnapshot,
        Long categoryIdSnapshot,
        String categorySlugSnapshot,
        String categoryTitleSnapshot,
        String offerSideSnapshot,
        String intentSnapshot,
        String ownerUsernameSnapshot,
        String takerUsernameSnapshot,
        String titleSnapshot,
        String descriptionSnapshot,
        String tradeTermsSnapshot,
        BigDecimal orderedQuantity,
        BigDecimal deliveredQuantity,
        String offerPriceCurrencyCodeSnapshot,
        BigDecimal offerPriceAmountSnapshot,
        String viewerCurrencyCodeSnapshot,
        String fxFromCurrencyCode,
        String fxToCurrencyCode,
        BigDecimal fxRate,
        BigDecimal displayUnitPriceAmount,
        BigDecimal displayTotalAmount,
        BigDecimal sellerGrossAmount,
        int sellerFeeBpsSnapshot,
        BigDecimal sellerFeeAmount,
        BigDecimal sellerNetAmount,
        JsonNode contextsSnapshot,
        JsonNode attributesSnapshot,
        JsonNode deliveryMethodsSnapshot,
        String status,
        Instant expiresAt
    ) {
        this.publicId = publicId;
        this.orderQuoteId = orderQuoteId;
        this.makerUserId = makerUserId;
        this.takerUserId = takerUserId;
        this.makerRole = makerRole;
        this.takerRole = takerRole;
        this.gameIdSnapshot = gameIdSnapshot;
        this.gameSlugSnapshot = gameSlugSnapshot;
        this.gameTitleSnapshot = gameTitleSnapshot;
        this.categoryIdSnapshot = categoryIdSnapshot;
        this.categorySlugSnapshot = categorySlugSnapshot;
        this.categoryTitleSnapshot = categoryTitleSnapshot;
        this.offerSideSnapshot = offerSideSnapshot;
        this.intentSnapshot = intentSnapshot;
        this.ownerUsernameSnapshot = ownerUsernameSnapshot;
        this.takerUsernameSnapshot = takerUsernameSnapshot;
        this.titleSnapshot = titleSnapshot;
        this.descriptionSnapshot = descriptionSnapshot;
        this.tradeTermsSnapshot = tradeTermsSnapshot;
        this.orderedQuantity = orderedQuantity;
        this.deliveredQuantity = deliveredQuantity;
        this.offerPriceCurrencyCodeSnapshot = offerPriceCurrencyCodeSnapshot;
        this.offerPriceAmountSnapshot = offerPriceAmountSnapshot;
        this.viewerCurrencyCodeSnapshot = viewerCurrencyCodeSnapshot;
        this.fxFromCurrencyCode = fxFromCurrencyCode;
        this.fxToCurrencyCode = fxToCurrencyCode;
        this.fxRate = fxRate;
        this.displayUnitPriceAmount = displayUnitPriceAmount;
        this.displayTotalAmount = displayTotalAmount;
        this.sellerGrossAmount = sellerGrossAmount;
        this.sellerFeeBpsSnapshot = sellerFeeBpsSnapshot;
        this.sellerFeeAmount = sellerFeeAmount;
        this.sellerNetAmount = sellerNetAmount;
        this.contextsSnapshot = contextsSnapshot;
        this.attributesSnapshot = attributesSnapshot;
        this.deliveryMethodsSnapshot = deliveryMethodsSnapshot;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equals(status);
    }

    public boolean isPartiallyDelivered() {
        return STATUS_PARTIALLY_DELIVERED.equals(status);
    }

    public boolean isDelivered() {
        return STATUS_DELIVERED.equals(status);
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    public boolean isCanceled() {
        return STATUS_CANCELED.equals(status);
    }

    public boolean isExpired() {
        return STATUS_EXPIRED.equals(status);
    }

    public void markInProgress() {
        status = STATUS_IN_PROGRESS;
    }

    public void markPartiallyDelivered(BigDecimal deliveredQuantity) {
        this.deliveredQuantity = deliveredQuantity;
        status = STATUS_PARTIALLY_DELIVERED;
    }

    public void markDelivered() {
        deliveredQuantity = orderedQuantity;
        status = STATUS_DELIVERED;
    }

    public void markCompleted() {
        deliveredQuantity = orderedQuantity;
        status = STATUS_COMPLETED;
    }

    public void markCanceled() {
        status = STATUS_CANCELED;
    }

    public void markExpired() {
        status = STATUS_EXPIRED;
    }

    public void amendQuantity(
        BigDecimal orderedQuantity,
        BigDecimal displayTotalAmount,
        BigDecimal sellerGrossAmount,
        BigDecimal sellerFeeAmount,
        BigDecimal sellerNetAmount
    ) {
        this.orderedQuantity = orderedQuantity;
        this.displayTotalAmount = displayTotalAmount;
        this.sellerGrossAmount = sellerGrossAmount;
        this.sellerFeeAmount = sellerFeeAmount;
        this.sellerNetAmount = sellerNetAmount;
        syncDeliveryStatusAfterQuantityAmend();
    }

    private void syncDeliveryStatusAfterQuantityAmend() {
        if (deliveredQuantity.compareTo(orderedQuantity) == 0) {
            status = STATUS_DELIVERED;
            return;
        }
        if (deliveredQuantity.signum() > 0) {
            status = STATUS_PARTIALLY_DELIVERED;
            return;
        }
        status = STATUS_IN_PROGRESS;
    }
}

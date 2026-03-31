package ru.maltsev.primemarketbackend.offer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "offers")
public class Offer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 8)
    private String side;

    @Column(length = 200)
    private String title;

    @Column
    private String description;

    @Column(name = "trade_terms")
    private String tradeTerms;

    @Column(name = "price_currency_code", length = 5)
    private String priceCurrencyCode;

    @Column(name = "price_amount", precision = 18, scale = 8)
    private BigDecimal priceAmount;

    @Column(precision = 18, scale = 8)
    private BigDecimal quantity;

    @Column(name = "min_trade_quantity", precision = 18, scale = 8)
    private BigDecimal minTradeQuantity;

    @Column(name = "max_trade_quantity", precision = 18, scale = 8)
    private BigDecimal maxTradeQuantity;

    @Column(name = "quantity_step", precision = 18, scale = 8)
    private BigDecimal quantityStep;

    @Column(nullable = false, length = 16)
    private String status = "draft";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public Offer(Long userId, Long gameId, Long categoryId, String side, String status) {
        this.userId = userId;
        this.gameId = gameId;
        this.categoryId = categoryId;
        this.side = side;
        this.status = status;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTradeTerms(String tradeTerms) {
        this.tradeTerms = tradeTerms;
    }

    public void setPriceCurrencyCode(String priceCurrencyCode) {
        this.priceCurrencyCode = priceCurrencyCode;
    }

    public void setPriceAmount(BigDecimal priceAmount) {
        this.priceAmount = priceAmount;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setMinTradeQuantity(BigDecimal minTradeQuantity) {
        this.minTradeQuantity = minTradeQuantity;
    }

    public void setMaxTradeQuantity(BigDecimal maxTradeQuantity) {
        this.maxTradeQuantity = maxTradeQuantity;
    }

    public void setQuantityStep(BigDecimal quantityStep) {
        this.quantityStep = quantityStep;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}

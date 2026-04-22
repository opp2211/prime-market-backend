package ru.maltsev.primemarketbackend.offer.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

public class OfferUpdateRequest {
    private Long gameId;
    private Long categoryId;
    private String side;
    private String title;
    private String description;
    private String tradeTerms;
    private String priceCurrencyCode;
    private BigDecimal priceAmount;
    private BigDecimal quantity;
    private BigDecimal minTradeQuantity;
    private BigDecimal maxTradeQuantity;
    private BigDecimal quantityStep;
    private String status;
    private List<@Valid OfferContextRequest> contexts;
    private List<@Valid OfferAttributeRequest> attributes;
    private List<String> deliveryMethods;

    @JsonIgnore
    private boolean gameIdPresent;
    @JsonIgnore
    private boolean categoryIdPresent;
    @JsonIgnore
    private boolean sidePresent;
    @JsonIgnore
    private boolean titlePresent;
    @JsonIgnore
    private boolean descriptionPresent;
    @JsonIgnore
    private boolean tradeTermsPresent;
    @JsonIgnore
    private boolean priceCurrencyCodePresent;
    @JsonIgnore
    private boolean priceAmountPresent;
    @JsonIgnore
    private boolean quantityPresent;
    @JsonIgnore
    private boolean minTradeQuantityPresent;
    @JsonIgnore
    private boolean maxTradeQuantityPresent;
    @JsonIgnore
    private boolean quantityStepPresent;
    @JsonIgnore
    private boolean statusPresent;
    @JsonIgnore
    private boolean contextsPresent;
    @JsonIgnore
    private boolean attributesPresent;
    @JsonIgnore
    private boolean deliveryMethodsPresent;

    public Long gameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
        this.gameIdPresent = true;
    }

    public Long categoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
        this.categoryIdPresent = true;
    }

    public String side() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
        this.sidePresent = true;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    public String tradeTerms() {
        return tradeTerms;
    }

    public void setTradeTerms(String tradeTerms) {
        this.tradeTerms = tradeTerms;
        this.tradeTermsPresent = true;
    }

    public String priceCurrencyCode() {
        return priceCurrencyCode;
    }

    public void setPriceCurrencyCode(String priceCurrencyCode) {
        this.priceCurrencyCode = priceCurrencyCode;
        this.priceCurrencyCodePresent = true;
    }

    public BigDecimal priceAmount() {
        return priceAmount;
    }

    public void setPriceAmount(BigDecimal priceAmount) {
        this.priceAmount = priceAmount;
        this.priceAmountPresent = true;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
        this.quantityPresent = true;
    }

    public BigDecimal minTradeQuantity() {
        return minTradeQuantity;
    }

    public void setMinTradeQuantity(BigDecimal minTradeQuantity) {
        this.minTradeQuantity = minTradeQuantity;
        this.minTradeQuantityPresent = true;
    }

    public BigDecimal maxTradeQuantity() {
        return maxTradeQuantity;
    }

    public void setMaxTradeQuantity(BigDecimal maxTradeQuantity) {
        this.maxTradeQuantity = maxTradeQuantity;
        this.maxTradeQuantityPresent = true;
    }

    public BigDecimal quantityStep() {
        return quantityStep;
    }

    public void setQuantityStep(BigDecimal quantityStep) {
        this.quantityStep = quantityStep;
        this.quantityStepPresent = true;
    }

    public String status() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.statusPresent = true;
    }

    public List<OfferContextRequest> contexts() {
        return contexts;
    }

    public void setContexts(List<OfferContextRequest> contexts) {
        this.contexts = contexts;
        this.contextsPresent = true;
    }

    public List<OfferAttributeRequest> attributes() {
        return attributes;
    }

    public void setAttributes(List<OfferAttributeRequest> attributes) {
        this.attributes = attributes;
        this.attributesPresent = true;
    }

    public List<String> deliveryMethods() {
        return deliveryMethods;
    }

    public void setDeliveryMethods(List<String> deliveryMethods) {
        this.deliveryMethods = deliveryMethods;
        this.deliveryMethodsPresent = true;
    }

    public boolean gameIdPresent() {
        return gameIdPresent;
    }

    public boolean categoryIdPresent() {
        return categoryIdPresent;
    }

    public boolean sidePresent() {
        return sidePresent;
    }

    public boolean titlePresent() {
        return titlePresent;
    }

    public boolean descriptionPresent() {
        return descriptionPresent;
    }

    public boolean tradeTermsPresent() {
        return tradeTermsPresent;
    }

    public boolean priceCurrencyCodePresent() {
        return priceCurrencyCodePresent;
    }

    public boolean priceAmountPresent() {
        return priceAmountPresent;
    }

    public boolean quantityPresent() {
        return quantityPresent;
    }

    public boolean minTradeQuantityPresent() {
        return minTradeQuantityPresent;
    }

    public boolean maxTradeQuantityPresent() {
        return maxTradeQuantityPresent;
    }

    public boolean quantityStepPresent() {
        return quantityStepPresent;
    }

    public boolean statusPresent() {
        return statusPresent;
    }

    public boolean contextsPresent() {
        return contextsPresent;
    }

    public boolean attributesPresent() {
        return attributesPresent;
    }

    public boolean deliveryMethodsPresent() {
        return deliveryMethodsPresent;
    }
}

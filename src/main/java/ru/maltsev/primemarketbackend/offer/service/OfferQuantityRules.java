package ru.maltsev.primemarketbackend.offer.service;

import java.math.BigDecimal;

public final class OfferQuantityRules {
    private OfferQuantityRules() {
    }

    public static EffectiveLimits calculateEffectiveLimits(
        BigDecimal availableQuantity,
        BigDecimal rawMinTradeQuantity,
        BigDecimal rawMaxTradeQuantity,
        BigDecimal quantityStep
    ) {
        BigDecimal normalizedAvailable = nonNegative(availableQuantity);
        BigDecimal rawMax = rawMaxTradeQuantity == null ? normalizedAvailable : rawMaxTradeQuantity.min(normalizedAvailable);
        BigDecimal effectiveMax = roundDownToStep(nonNegative(rawMax), quantityStep);
        BigDecimal effectiveMin = rawMinTradeQuantity == null ? null : roundUpToStep(rawMinTradeQuantity, quantityStep);
        boolean orderCreationAvailable = effectiveMax.signum() > 0
            && (effectiveMin == null || effectiveMin.compareTo(effectiveMax) <= 0);
        return new EffectiveLimits(normalizedAvailable, effectiveMin, effectiveMax, orderCreationAvailable);
    }

    public static boolean isAlignedToStep(BigDecimal value, BigDecimal quantityStep) {
        if (value == null || quantityStep == null) {
            return true;
        }
        if (quantityStep.signum() <= 0) {
            return false;
        }
        return value.remainder(quantityStep).compareTo(BigDecimal.ZERO) == 0;
    }

    public static BigDecimal roundDownToStep(BigDecimal value, BigDecimal quantityStep) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (quantityStep == null) {
            return value;
        }
        if (quantityStep.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (value.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return value.divideToIntegralValue(quantityStep).multiply(quantityStep);
    }

    public static BigDecimal roundUpToStep(BigDecimal value, BigDecimal quantityStep) {
        if (value == null || quantityStep == null) {
            return value;
        }
        if (quantityStep.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (value.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal roundedDown = roundDownToStep(value, quantityStep);
        if (roundedDown.compareTo(value) == 0) {
            return roundedDown;
        }
        return roundedDown.add(quantityStep);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    public record EffectiveLimits(
        BigDecimal availableQuantity,
        BigDecimal effectiveMinTradeQuantity,
        BigDecimal effectiveMaxTradeQuantity,
        boolean orderCreationAvailable
    ) {
    }
}

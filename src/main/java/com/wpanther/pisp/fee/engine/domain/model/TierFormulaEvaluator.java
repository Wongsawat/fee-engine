package com.wpanther.pisp.fee.engine.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class TierFormulaEvaluator {

    private TierFormulaEvaluator() {}

    public static BigDecimal compute(Tier tier, BigDecimal txnAmount, String currency) {
        int scale = Math.max(java.util.Currency.getInstance(currency).getDefaultFractionDigits(), 0);
        BigDecimal raw = switch (tier.getRateType()) {
            case FIXED      -> tier.getAmount().orElseThrow();
            case PERCENTAGE -> txnAmount.multiply(tier.getPercentage().orElseThrow());
            case HYBRID     -> tier.getAmount().orElseThrow()
                                   .add(txnAmount.multiply(tier.getPercentage().orElseThrow()));
            case GREATER_OF -> {
                BigDecimal pct = txnAmount.multiply(tier.getPercentage().orElseThrow());
                yield pct.max(tier.getAmount().orElseThrow());
            }
        };
        return raw.setScale(scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal bracketAmount(Tier tier, BigDecimal txnAmount) {
        return txnAmount.min(tier.getMax()).subtract(tier.getMin());
    }
}

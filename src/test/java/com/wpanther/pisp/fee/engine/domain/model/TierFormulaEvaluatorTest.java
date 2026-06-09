package com.wpanther.pisp.fee.engine.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class TierFormulaEvaluatorTest {

    // --- compute: FIXED ---

    @Test
    void fixed_returnsFlatAmount() {
        Tier tier = new Tier(BigDecimal.ZERO, new BigDecimal("1000"),
                TierRateType.FIXED, new BigDecimal("5.00"), null);
        assertThat(TierFormulaEvaluator.compute(tier, new BigDecimal("500"), "GBP"))
                .isEqualByComparingTo("5.00");
    }

    @Test
    void fixed_ignoresTxnAmount() {
        Tier tier = new Tier(BigDecimal.ZERO, new BigDecimal("1000"),
                TierRateType.FIXED, new BigDecimal("5.00"), null);
        assertThat(TierFormulaEvaluator.compute(tier, new BigDecimal("999"), "GBP"))
                .isEqualByComparingTo("5.00");
    }

    // --- compute: PERCENTAGE ---

    @Test
    void percentage_computesRateOnAmount() {
        Tier tier = new Tier(BigDecimal.ZERO, new BigDecimal("10000"),
                TierRateType.PERCENTAGE, null, new BigDecimal("0.03"));
        assertThat(TierFormulaEvaluator.compute(tier, new BigDecimal("10000"), "GBP"))
                .isEqualByComparingTo("300.00");
    }

    @Test
    void percentage_scalesForJPY() {
        Tier tier = new Tier(BigDecimal.ZERO, new BigDecimal("10000"),
                TierRateType.PERCENTAGE, null, new BigDecimal("0.03"));
        assertThat(TierFormulaEvaluator.compute(tier, new BigDecimal("10000"), "JPY"))
                .isEqualByComparingTo("300");
    }

    // --- compute: HYBRID ---

    @Test
    void hybrid_addsFlatPlusPercentage() {
        Tier tier = new Tier(BigDecimal.ZERO, new BigDecimal("10000"),
                TierRateType.HYBRID, new BigDecimal("2.00"), new BigDecimal("0.005"));
        // 2.00 + 1000 * 0.005 = 2.00 + 5.00 = 7.00
        assertThat(TierFormulaEvaluator.compute(tier, new BigDecimal("1000"), "GBP"))
                .isEqualByComparingTo("7.00");
    }

    // --- compute: GREATER_OF ---

    @Test
    void greaterOf_returnsPercentageWhenHigher() {
        Tier tier = new Tier(BigDecimal.ZERO, new BigDecimal("10000"),
                TierRateType.GREATER_OF, new BigDecimal("5.00"), new BigDecimal("0.01"));
        // max(5.00, 1000 * 0.01 = 10.00) = 10.00
        assertThat(TierFormulaEvaluator.compute(tier, new BigDecimal("1000"), "GBP"))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void greaterOf_returnsFloorWhenHigher() {
        Tier tier = new Tier(BigDecimal.ZERO, new BigDecimal("10000"),
                TierRateType.GREATER_OF, new BigDecimal("5.00"), new BigDecimal("0.01"));
        // max(5.00, 200 * 0.01 = 2.00) = 5.00
        assertThat(TierFormulaEvaluator.compute(tier, new BigDecimal("200"), "GBP"))
                .isEqualByComparingTo("5.00");
    }

    @Test
    void greaterOf_returnsFloorWhenExactlyEqual() {
        Tier tier = new Tier(BigDecimal.ZERO, new BigDecimal("10000"),
                TierRateType.GREATER_OF, new BigDecimal("5.00"), new BigDecimal("0.01"));
        // max(5.00, 500 * 0.01 = 5.00) = 5.00
        assertThat(TierFormulaEvaluator.compute(tier, new BigDecimal("500"), "GBP"))
                .isEqualByComparingTo("5.00");
    }

    // --- bracketAmount ---

    @Test
    void bracketAmount_midTier() {
        Tier tier = new Tier(new BigDecimal("10000"), new BigDecimal("50000"),
                TierRateType.PERCENTAGE, null, new BigDecimal("0.02"));
        // min(30000, 50000) - 10000 = 20000
        assertThat(TierFormulaEvaluator.bracketAmount(tier, new BigDecimal("30000")))
                .isEqualByComparingTo("20000");
    }

    @Test
    void bracketAmount_beyondLastTierMax() {
        Tier tier = new Tier(new BigDecimal("50000"), new BigDecimal("999999999"),
                TierRateType.PERCENTAGE, null, new BigDecimal("0.01"));
        // min(60000, 999999999) - 50000 = 10000
        assertThat(TierFormulaEvaluator.bracketAmount(tier, new BigDecimal("60000")))
                .isEqualByComparingTo("10000");
    }

    @Test
    void bracketAmount_atTierMaxEdge() {
        Tier tier = new Tier(BigDecimal.ZERO, new BigDecimal("10000"),
                TierRateType.PERCENTAGE, null, new BigDecimal("0.03"));
        // min(10000, 10000) - 0 = 10000
        assertThat(TierFormulaEvaluator.bracketAmount(tier, new BigDecimal("10000")))
                .isEqualByComparingTo("10000");
    }

    @Test
    void fixed_inStepContext_computeIgnoresBracketAmount() {
        Tier tier = new Tier(BigDecimal.ZERO, new BigDecimal("10000"),
                TierRateType.FIXED, new BigDecimal("5.00"), null);
        BigDecimal bracket = TierFormulaEvaluator.bracketAmount(tier, new BigDecimal("7000"));
        // bracket = 7000, but FIXED ignores it
        assertThat(TierFormulaEvaluator.compute(tier, bracket, "GBP"))
                .isEqualByComparingTo("5.00");
    }
}

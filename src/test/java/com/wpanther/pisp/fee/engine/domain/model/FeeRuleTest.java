package com.wpanther.pisp.fee.engine.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeeRuleTest {

    private FeeRule rule(BigDecimal minFee, BigDecimal maxFee) {
        return new FeeRule("CHARGEType002", ChargeBearer.BorneByDebtor, FeeType.PERCENTAGE,
                null, new BigDecimal("0.01"), minFee, maxFee, List.of(), "GBP", null);
    }

    @Test
    void returnsFeeUnchangedWhenWithinBounds() {
        assertThat(rule(new BigDecimal("1"), new BigDecimal("50")).applyBounds(new BigDecimal("10")))
                .isEqualByComparingTo("10");
    }

    @Test
    void raisesFeeToFloorWhenBelowMin() {
        assertThat(rule(new BigDecimal("5"), null).applyBounds(new BigDecimal("2")))
                .isEqualByComparingTo("5");
    }

    @Test
    void capsFeeToMaxWhenAboveMax() {
        assertThat(rule(null, new BigDecimal("50")).applyBounds(new BigDecimal("80")))
                .isEqualByComparingTo("50");
    }

    @Test
    void leavesFeeUnchangedWhenNoBounds() {
        assertThat(rule(null, null).applyBounds(new BigDecimal("123.45")))
                .isEqualByComparingTo("123.45");
    }

    @Test
    void floorOnlyDoesNotCap() {
        assertThat(rule(new BigDecimal("5"), null).applyBounds(new BigDecimal("100")))
                .isEqualByComparingTo("100");
    }

    @Test
    void capOnlyDoesNotFloor() {
        assertThat(rule(null, new BigDecimal("50")).applyBounds(new BigDecimal("2")))
                .isEqualByComparingTo("2");
    }

    @Test
    void equalMinAndMaxFixesFee() {
        assertThat(rule(new BigDecimal("7"), new BigDecimal("7")).applyBounds(new BigDecimal("3")))
                .isEqualByComparingTo("7");
    }
}

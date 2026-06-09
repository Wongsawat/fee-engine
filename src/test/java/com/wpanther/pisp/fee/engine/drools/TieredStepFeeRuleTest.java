package com.wpanther.pisp.fee.engine.drools;

import com.wpanther.pisp.fee.engine.domain.model.*;
import com.wpanther.pisp.fee.engine.support.DroolsTestSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TieredStepFeeRuleTest extends DroolsTestSupport {

    private static final List<Tier> PCT_TIERS = List.of(
            new Tier(BigDecimal.ZERO, new BigDecimal("10000"),
                    TierRateType.PERCENTAGE, null, new BigDecimal("0.03")),
            new Tier(new BigDecimal("10000"), new BigDecimal("50000"),
                    TierRateType.PERCENTAGE, null, new BigDecimal("0.02")),
            new Tier(new BigDecimal("50000"), new BigDecimal("999999999"),
                    TierRateType.PERCENTAGE, null, new BigDecimal("0.01")));

    @Test
    void step_accumulatesAcrossAllThreeTiers() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("60000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);
        FeeRule rule = new FeeRule("STEP_FEE", ChargeBearer.BorneByDebtor, FeeType.TIERED_STEP,
                null, null, null, null, PCT_TIERS, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("1200.00");
    }

    @Test
    void step_amountWithinFirstTierOnly() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("5000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);
        FeeRule rule = new FeeRule("STEP_FEE", ChargeBearer.BorneByDebtor, FeeType.TIERED_STEP,
                null, null, null, null, PCT_TIERS, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("150.00");
    }

    @Test
    void step_amountExactlyAtTierBoundary_doesNotEnterNextTier() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("10000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);
        FeeRule rule = new FeeRule("STEP_FEE", ChargeBearer.BorneByDebtor, FeeType.TIERED_STEP,
                null, null, null, null, PCT_TIERS, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("300.00");
    }

    @Test
    void step_fixedTiers_sumsFlatAmountPerEnteredTier() {
        List<Tier> fixedTiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("1000"),
                        TierRateType.FIXED, new BigDecimal("5.00"), null),
                new Tier(new BigDecimal("1000"), new BigDecimal("999999999"),
                        TierRateType.FIXED, new BigDecimal("10.00"), null));

        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("5000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);
        FeeRule rule = new FeeRule("FIXED_STEP", ChargeBearer.BorneByDebtor, FeeType.TIERED_STEP,
                null, null, null, null, fixedTiers, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("15.00");
    }

    @Test
    void step_hybridTiers_usesBracketInPercentageComponent() {
        List<Tier> hybridTiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("10000"),
                        TierRateType.HYBRID, new BigDecimal("2.00"), new BigDecimal("0.01")),
                new Tier(new BigDecimal("10000"), new BigDecimal("999999999"),
                        TierRateType.HYBRID, new BigDecimal("1.00"), new BigDecimal("0.005")));

        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("15000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);
        FeeRule rule = new FeeRule("HYBRID_STEP", ChargeBearer.BorneByDebtor, FeeType.TIERED_STEP,
                null, null, null, null, hybridTiers, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("128.00");
    }

    @Test
    void step_greaterOfTiers_evaluatedPerBracket() {
        List<Tier> goTiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("10000"),
                        TierRateType.GREATER_OF, new BigDecimal("50.00"), new BigDecimal("0.01")),
                new Tier(new BigDecimal("10000"), new BigDecimal("999999999"),
                        TierRateType.GREATER_OF, new BigDecimal("5.00"), new BigDecimal("0.02")));

        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("15000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);
        FeeRule rule = new FeeRule("GO_STEP", ChargeBearer.BorneByDebtor, FeeType.TIERED_STEP,
                null, null, null, null, goTiers, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void step_borneByCreditor() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByCreditor,
                new InstructedAmount(new BigDecimal("60000.00"), "GBP"),
                null, new AccountRef("SortCodeAccountNumber", "CRED001"), null);
        FeeRule rule = new FeeRule("STEP_FEE", ChargeBearer.BorneByCreditor, FeeType.TIERED_STEP,
                null, null, null, null, PCT_TIERS, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).chargeBearer()).isEqualTo(ChargeBearer.BorneByCreditor);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("1200.00");
    }

    @Test
    void step_shared_debtorAndCreditorGroupedIntoSeparateCharges() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.Shared,
                new InstructedAmount(new BigDecimal("60000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "DEB001"),
                new AccountRef("SortCodeAccountNumber", "CRED001"), null);

        FeeRule debtorRule = new FeeRule("STEP_FEE", ChargeBearer.BorneByDebtor, FeeType.TIERED_STEP,
                null, null, null, null, PCT_TIERS, "GBP", null, 0);
        FeeRule creditorRule = new FeeRule("STEP_FEE", ChargeBearer.BorneByCreditor, FeeType.TIERED_STEP,
                null, null, null, null, PCT_TIERS, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(debtorRule, creditorRule));

        assertThat(charges).hasSize(2);
        assertThat(charges).anyMatch(c -> c.chargeBearer() == ChargeBearer.BorneByDebtor
                && c.amount().amount().compareTo(new BigDecimal("1200.00")) == 0);
        assertThat(charges).anyMatch(c -> c.chargeBearer() == ChargeBearer.BorneByCreditor
                && c.amount().amount().compareTo(new BigDecimal("1200.00")) == 0);
    }
}

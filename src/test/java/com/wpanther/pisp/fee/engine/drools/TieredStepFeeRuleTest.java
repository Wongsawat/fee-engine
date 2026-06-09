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

    @Test
    void step_amountBelowAllTierMinimums_returnsEmpty() {
        List<Tier> tiers = List.of(
                new Tier(new BigDecimal("100"), new BigDecimal("1000"),
                        TierRateType.FIXED, new BigDecimal("5.00"), null));

        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("50.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);
        FeeRule rule = new FeeRule("MIN_STEP", ChargeBearer.BorneByDebtor, FeeType.TIERED_STEP,
                null, null, null, null, tiers, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).isEmpty();
    }

    @Test
    void step_jpyCurrency_scalesToZeroDecimalPlaces() {
        // JPY has 0 default fraction digits — FeeSessionRunner must scale the accumulated total accordingly
        List<Tier> tiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("10000"),
                        TierRateType.PERCENTAGE, null, new BigDecimal("0.03")),
                new Tier(new BigDecimal("10000"), new BigDecimal("999999999"),
                        TierRateType.PERCENTAGE, null, new BigDecimal("0.01")));

        // 15000 JPY: tier1 bracket=10000 → 10000*3%=300; tier2 bracket=5000 → 5000*1%=50 → total=350
        FeeRequest request = new FeeRequest(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("15000"), "JPY"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);
        FeeRule rule = new FeeRule("JPY_STEP", ChargeBearer.BorneByDebtor, FeeType.TIERED_STEP,
                null, null, null, null, tiers, "JPY", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().currency()).isEqualTo("JPY");
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("350");
        assertThat(charges.get(0).amount().amount().scale()).isEqualTo(0);
    }

    @Test
    void step_mixedFormulaTiers_fixedThenPercentage() {
        // Tier 1: FIXED $5.00 for amounts entering [0, 1000) bracket
        // Tier 2: PERCENTAGE 2% on the bracket within [1000, 999999999)
        // Amount $15,000: enters both tiers
        //   tier1 bracket = min(15000, 1000) - 0 = 1000; compute(FIXED, 1000) = 5.00
        //   tier2 bracket = min(15000, 999999999) - 1000 = 14000; compute(PCT, 14000) = 14000*0.02 = 280.00
        //   total = 285.00
        List<Tier> tiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("1000"),
                        TierRateType.FIXED, new BigDecimal("5.00"), null),
                new Tier(new BigDecimal("1000"), new BigDecimal("999999999"),
                        TierRateType.PERCENTAGE, null, new BigDecimal("0.02")));

        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("15000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);
        FeeRule rule = new FeeRule("MIXED_STEP", ChargeBearer.BorneByDebtor, FeeType.TIERED_STEP,
                null, null, null, null, tiers, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).chargeType()).isEqualTo("MIXED_STEP");
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("285.00");
    }

    @Test
    void step_twoRulesWithSameChargeType_contributionsAccumulatedIntoSingleCharge() {
        // Two TIERED_STEP rules with the same chargeType fire against the same request.
        // All their TierContributions share the same grouping key (chargeBearer:chargeType)
        // and must be summed into exactly one output charge — not two charges, not first-wins.
        List<Tier> tiersAt1Pct = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("999999999"),
                        TierRateType.PERCENTAGE, null, new BigDecimal("0.01")));
        List<Tier> tiersAt2Pct = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("999999999"),
                        TierRateType.PERCENTAGE, null, new BigDecimal("0.02")));

        // 1000 * 1% = 10.00 from rule1; 1000 * 2% = 20.00 from rule2 → total 30.00
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("1000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);
        FeeRule rule1 = new FeeRule("MULTI_STEP", ChargeBearer.BorneByDebtor, FeeType.TIERED_STEP,
                null, null, null, null, tiersAt1Pct, "GBP", null, 0);
        FeeRule rule2 = new FeeRule("MULTI_STEP", ChargeBearer.BorneByDebtor, FeeType.TIERED_STEP,
                null, null, null, null, tiersAt2Pct, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule1, rule2));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).chargeType()).isEqualTo("MULTI_STEP");
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("30.00");
    }
}

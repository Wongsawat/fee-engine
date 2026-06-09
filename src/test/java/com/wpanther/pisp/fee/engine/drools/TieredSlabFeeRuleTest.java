package com.wpanther.pisp.fee.engine.drools;

import com.wpanther.pisp.fee.engine.domain.model.*;
import com.wpanther.pisp.fee.engine.support.DroolsTestSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TieredSlabFeeRuleTest extends DroolsTestSupport {

    private static final List<Tier> FIXED_TIERS = List.of(
            new Tier(BigDecimal.ZERO, new BigDecimal("1000"), TierRateType.FIXED,
                    new BigDecimal("0.50"), null),
            new Tier(new BigDecimal("1000"), new BigDecimal("999999999"), TierRateType.FIXED,
                    new BigDecimal("2.00"), null));

    @Test
    void fixedSlab_selectsLowerTierForSmallAmount() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.BACS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("500.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType003", ChargeBearer.BorneByDebtor, FeeType.TIERED_SLAB,
                null, null, null, null, FIXED_TIERS, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("0.50");
    }

    @Test
    void fixedSlab_selectsUpperTierForLargeAmount() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.BACS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("5000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType003", ChargeBearer.BorneByDebtor, FeeType.TIERED_SLAB,
                null, null, null, null, FIXED_TIERS, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("2.00");
    }

    @Test
    void fixedSlab_exactBoundaryUsesUpperTier() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.BACS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("1000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType003", ChargeBearer.BorneByDebtor, FeeType.TIERED_SLAB,
                null, null, null, null, FIXED_TIERS, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("2.00");
    }

    @Test
    void fixedSlab_producesNoChargeWhenAmountFallsInGap() {
        List<Tier> gappedTiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("500"), TierRateType.FIXED,
                        new BigDecimal("0.50"), null));

        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.BACS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("600.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType003", ChargeBearer.BorneByDebtor, FeeType.TIERED_SLAB,
                null, null, null, null, gappedTiers, "GBP", null, 0);

        assertThat(fireRules(request, List.of(rule))).isEmpty();
    }

    @Test
    void percentageSlab_appliesRateToFullAmount_lowerTier() {
        List<Tier> tiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("10000"), TierRateType.PERCENTAGE,
                        null, new BigDecimal("0.03")),
                new Tier(new BigDecimal("10000"), new BigDecimal("999999999"), TierRateType.PERCENTAGE,
                        null, new BigDecimal("0.01")));

        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("5000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType006", ChargeBearer.BorneByDebtor, FeeType.TIERED_SLAB,
                null, null, null, null, tiers, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("150.00");
    }

    @Test
    void percentageSlab_appliesRateToFullAmount_upperTier() {
        List<Tier> tiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("10000"), TierRateType.PERCENTAGE,
                        null, new BigDecimal("0.03")),
                new Tier(new BigDecimal("10000"), new BigDecimal("999999999"), TierRateType.PERCENTAGE,
                        null, new BigDecimal("0.01")));

        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("60000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType006", ChargeBearer.BorneByDebtor, FeeType.TIERED_SLAB,
                null, null, null, null, tiers, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("600.00");
    }

    @Test
    void hybridSlab_appliesBasePlusPercentageToFullAmount() {
        List<Tier> tiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("999999999"), TierRateType.HYBRID,
                        new BigDecimal("2.00"), new BigDecimal("0.005")));

        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("1000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("HYBRID_FEE", ChargeBearer.BorneByDebtor, FeeType.TIERED_SLAB,
                null, null, null, null, tiers, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("7.00");
    }

    @Test
    void greaterOfSlab_returnsPercentageWhenHigher() {
        List<Tier> tiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("999999999"), TierRateType.GREATER_OF,
                        new BigDecimal("5.00"), new BigDecimal("0.01")));

        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("1000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("GO_FEE", ChargeBearer.BorneByDebtor, FeeType.TIERED_SLAB,
                null, null, null, null, tiers, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void greaterOfSlab_returnsFloorWhenHigher() {
        List<Tier> tiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("999999999"), TierRateType.GREATER_OF,
                        new BigDecimal("5.00"), new BigDecimal("0.01")));

        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("200.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("GO_FEE", ChargeBearer.BorneByDebtor, FeeType.TIERED_SLAB,
                null, null, null, null, tiers, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("5.00");
    }

    @Test
    void mixedFormulaSlab_differentAmountsHitDifferentTiers() {
        List<Tier> tiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("1000"), TierRateType.FIXED,
                        new BigDecimal("5.00"), null),
                new Tier(new BigDecimal("1000"), new BigDecimal("999999999"), TierRateType.PERCENTAGE,
                        null, new BigDecimal("0.02")));

        FeeRule rule = new FeeRule("MIXED", ChargeBearer.BorneByDebtor, FeeType.TIERED_SLAB,
                null, null, null, null, tiers, "GBP", null, 0);

        FeeRequest lowRequest = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("500.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        assertThat(fireRules(lowRequest, List.of(rule)).get(0).amount().amount())
                .isEqualByComparingTo("5.00");

        FeeRequest highRequest = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("5000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        assertThat(fireRules(highRequest, List.of(rule)).get(0).amount().amount())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void slabFee_borneByCreditor() {
        List<Tier> tiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("999999999"), TierRateType.FIXED,
                        new BigDecimal("3.00"), null));

        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByCreditor,
                new InstructedAmount(new BigDecimal("500.00"), "GBP"),
                null, new AccountRef("SortCodeAccountNumber", "CRED001"), null);

        FeeRule rule = new FeeRule("CRED_FEE", ChargeBearer.BorneByCreditor, FeeType.TIERED_SLAB,
                null, null, null, null, tiers, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).chargeBearer()).isEqualTo(ChargeBearer.BorneByCreditor);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("3.00");
    }
}

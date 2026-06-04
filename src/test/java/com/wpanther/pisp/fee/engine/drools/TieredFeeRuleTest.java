package com.wpanther.pisp.fee.engine.drools;

import com.wpanther.pisp.fee.engine.domain.model.*;
import com.wpanther.pisp.fee.engine.support.DroolsTestSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TieredFeeRuleTest extends DroolsTestSupport {

    private static final List<Tier> TIERS = List.of(
            new Tier(BigDecimal.ZERO, new BigDecimal("1000"), new BigDecimal("0.50")),
            new Tier(new BigDecimal("1000"), new BigDecimal("999999999"), new BigDecimal("2.00")));

    @Test
    void selectsLowerTierForSmallAmount() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.BACS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("500.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType003", ChargeBearer.BorneByDebtor, FeeType.TIERED,
                null, null, null, null, TIERS, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("0.50");
    }

    @Test
    void selectsUpperTierForLargeAmount() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.BACS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("5000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType003", ChargeBearer.BorneByDebtor, FeeType.TIERED,
                null, null, null, null, TIERS, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("2.00");
    }

    @Test
    void exactTierBoundaryUsesUpperTier() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.BACS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("1000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType003", ChargeBearer.BorneByDebtor, FeeType.TIERED,
                null, null, null, null, TIERS, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("2.00");
    }

    @Test
    void producesNoChargeWhenAmountFallsInTierGap() {
        List<Tier> gappedTiers = List.of(
                new Tier(BigDecimal.ZERO, new BigDecimal("500"), new BigDecimal("0.50")));

        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.BACS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("600.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType003", ChargeBearer.BorneByDebtor, FeeType.TIERED,
                null, null, null, null, gappedTiers, "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).isEmpty();
    }
}

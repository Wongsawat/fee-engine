package com.wpanther.pisp.fee.engine.drools;

import com.wpanther.pisp.fee.engine.domain.model.*;
import com.wpanther.pisp.fee.engine.support.DroolsTestSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FreeFeeRuleTest extends DroolsTestSupport {

    @Test
    void freeFeeProducesZeroAmountChargeForDebtor() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType004", ChargeBearer.BorneByDebtor, FeeType.FREE,
                null, null, null, null, List.of(), "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        Charge charge = charges.get(0);
        assertThat(charge.chargeBearer()).isEqualTo(ChargeBearer.BorneByDebtor);
        assertThat(charge.chargeType()).isEqualTo("CHARGEType004");
        assertThat(charge.amount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(charge.amount().amount().scale()).isEqualTo(2); // GBP = 2 fraction digits
        assertThat(charge.amount().currency()).isEqualTo("GBP");
        assertThat(charge.chargingParty().identification()).isEqualTo("12345678901234");
    }

    @Test
    void freeFeeProducesZeroAmountChargeForCreditor() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByCreditor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                null, new AccountRef("SortCodeAccountNumber", "98765432109876"), null);

        FeeRule rule = new FeeRule("CHARGEType004", ChargeBearer.BorneByCreditor, FeeType.FREE,
                null, null, null, null, List.of(), "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        Charge charge = charges.get(0);
        assertThat(charge.chargeBearer()).isEqualTo(ChargeBearer.BorneByCreditor);
        assertThat(charge.chargeType()).isEqualTo("CHARGEType004");
        assertThat(charge.amount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(charge.amount().amount().scale()).isEqualTo(2);
        assertThat(charge.amount().currency()).isEqualTo("GBP");
        assertThat(charge.chargingParty().identification()).isEqualTo("98765432109876");
    }

    @Test
    void freeFeeDoesNotFireWhenDebtorAccountAbsent() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                null, null, null);

        FeeRule rule = new FeeRule("CHARGEType004", ChargeBearer.BorneByDebtor, FeeType.FREE,
                null, null, null, null, List.of(), "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).isEmpty();
    }
}

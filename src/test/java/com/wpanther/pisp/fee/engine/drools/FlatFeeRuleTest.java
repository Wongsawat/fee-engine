package com.wpanther.pisp.fee.engine.drools;

import com.wpanther.pisp.fee.engine.domain.model.*;
import com.wpanther.pisp.fee.engine.support.DroolsTestSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlatFeeRuleTest extends DroolsTestSupport {

    @Test
    void flatFeeProducesChargeWithConfiguredAmount() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType001", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("1.50"), null, null, null, List.of(), "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        Charge charge = charges.get(0);
        assertThat(charge.chargeBearer()).isEqualTo(ChargeBearer.BorneByDebtor);
        assertThat(charge.chargeType()).isEqualTo("CHARGEType001");
        assertThat(charge.amount().amount()).isEqualByComparingTo("1.50");
        assertThat(charge.amount().currency()).isEqualTo("GBP");
        assertThat(charge.chargingParty().identification()).isEqualTo("12345678901234");
    }

    @Test
    void flatFeeDoesNotFireWhenDebtorAccountAbsent() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                null, null, null);

        FeeRule rule = new FeeRule("CHARGEType001", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("1.50"), null, null, null, List.of(), "GBP", null, 0);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).isEmpty();
    }
}

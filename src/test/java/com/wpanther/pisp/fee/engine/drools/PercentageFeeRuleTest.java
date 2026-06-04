package com.wpanther.pisp.fee.engine.drools;

import com.wpanther.pisp.fee.engine.domain.model.*;
import com.wpanther.pisp.fee.engine.support.DroolsTestSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PercentageFeeRuleTest extends DroolsTestSupport {

    @Test
    void percentageFeeCalculatesCorrectAmount() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.CHAPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("500.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType002", ChargeBearer.BorneByDebtor, FeeType.PERCENTAGE,
                null, new BigDecimal("0.002"), null, null, List.of(), "GBP", null);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("1.00");
    }

    @Test
    void percentageFeeRoundsHalfUp() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.CHAPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("123.45"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        FeeRule rule = new FeeRule("CHARGEType002", ChargeBearer.BorneByDebtor, FeeType.PERCENTAGE,
                null, new BigDecimal("0.002"), null, null, List.of(), "GBP", null);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("0.25");
    }

    @Test
    void percentageFeeUsesCorrectScaleForThreeDecimalCurrency() {
        FeeRequest request = new FeeRequest(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.000"), "KWD"),
                new AccountRef("IBAN", "KW81CBKU0000000000001234560101"), null, null);

        FeeRule rule = new FeeRule("CHARGEType002", ChargeBearer.BorneByDebtor, FeeType.PERCENTAGE,
                null, new BigDecimal("0.002"), null, null, List.of(), "KWD", null);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges.get(0).amount().amount().scale()).isEqualTo(3);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("0.200");
    }

    @Test
    void percentageFeeRaisedToMinFloor() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.CHAPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);
        // 0.2% of 100 = 0.20, floored to minFee 1.00
        FeeRule rule = new FeeRule("CHARGEType002", ChargeBearer.BorneByDebtor, FeeType.PERCENTAGE,
                null, new BigDecimal("0.002"), new BigDecimal("1.00"), null, List.of(), "GBP", null);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("1.00");
        assertThat(charges.get(0).amount().amount().scale()).isEqualTo(2);
    }

    @Test
    void percentageFeeCappedToMax() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.CHAPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100000.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);
        // 1% of 100000 = 1000.00, capped to maxFee 50.00
        FeeRule rule = new FeeRule("CHARGEType002", ChargeBearer.BorneByDebtor, FeeType.PERCENTAGE,
                null, new BigDecimal("0.01"), null, new BigDecimal("50.00"), List.of(), "GBP", null);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("50.00");
        assertThat(charges.get(0).amount().amount().scale()).isEqualTo(2);
    }

    @Test
    void percentageFeeWithinBoundsUnchangedForCreditor() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.CHAPS, ChargeBearer.BorneByCreditor,
                new InstructedAmount(new BigDecimal("10000.00"), "GBP"),
                null, new AccountRef("SortCodeAccountNumber", "98765432109876"), null);
        // 1% of 10000 = 100.00, within [1.00, 500.00]
        FeeRule rule = new FeeRule("CHARGEType002", ChargeBearer.BorneByCreditor, FeeType.PERCENTAGE,
                null, new BigDecimal("0.01"), new BigDecimal("1.00"), new BigDecimal("500.00"), List.of(), "GBP", null);

        List<Charge> charges = fireRules(request, List.of(rule));

        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("100.00");
    }
}

package com.wpanther.pisp.fee.engine.application.service;

import com.wpanther.pisp.fee.engine.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeeSessionRunnerTest {

    private FeeSessionRunner runner;

    @BeforeEach
    void setup() {
        KieContainer kieContainer = KieServices.Factory.get().getKieClasspathContainer();
        runner = new FeeSessionRunner(kieContainer);
    }

    @Test
    void run_flatRule_returnsSingleCharge() {
        FeeRule rule = new FeeRule("CHARGE1", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("5.00"), null, null, null, List.of(), "GBP", null, 0);
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        List<Charge> charges = runner.run(request, List.of(rule));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).chargeType()).isEqualTo("CHARGE1");
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("5.00");
    }

    @Test
    void run_noRules_returnsEmpty() {
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        List<Charge> charges = runner.run(request, List.of());

        assertThat(charges).isEmpty();
    }

    @Test
    void run_duplicateRulesWithSameChargeType_deduplicates() {
        FeeRule r1 = new FeeRule("CHARGE1", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("5.00"), null, null, null, List.of(), "GBP", null, 0);
        FeeRule r2 = new FeeRule("CHARGE1", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("9.00"), null, null, null, List.of(), "GBP", null, 0);
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "12345678901234"), null, null);

        List<Charge> charges = runner.run(request, List.of(r1, r2));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("5.00");
    }
}

package com.wpanther.pisp.fee.engine.application.service;

import com.wpanther.pisp.fee.engine.application.port.in.DryRunFeeCalculationUseCase;
import com.wpanther.pisp.fee.engine.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DryRunFeeCalculationServiceTest {

    private DryRunFeeCalculationService service;
    private KieContainer kieContainer;

    @BeforeEach
    void setup() {
        kieContainer = KieServices.Factory.get().getKieClasspathContainer();
        service = new DryRunFeeCalculationService(kieContainer);
    }

    @Test
    void dryRunFlatRuleProducesCharge() {
        FeeRule rule = new FeeRule("CHARGEType001", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("1.50"), null, List.of(), "GBP");
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "123"), null);

        List<Charge> charges = service.dryRun(new DryRunFeeCalculationUseCase.DryRunCommand(rule, request));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).chargeType()).isEqualTo("CHARGEType001");
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("1.50");
    }

    @Test
    void dryRunFreeRuleProducesZeroCharge() {
        FeeRule rule = new FeeRule("CHARGEType004", ChargeBearer.BorneByDebtor, FeeType.FREE,
                null, null, List.of(), "GBP");
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "123"), null);

        List<Charge> charges = service.dryRun(new DryRunFeeCalculationUseCase.DryRunCommand(rule, request));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void dryRunReturnsEmptyWhenNoPaymentContext() {
        FeeRule rule = new FeeRule("CHARGEType001", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("1.50"), null, List.of(), "GBP");

        List<Charge> charges = service.dryRun(new DryRunFeeCalculationUseCase.DryRunCommand(rule, null));

        assertThat(charges).isEmpty();
    }

    @Test
    void dryRunPercentageRuleProducesCorrectCharge() {
        FeeRule rule = new FeeRule("CHARGEType002", ChargeBearer.BorneByDebtor, FeeType.PERCENTAGE,
                null, new BigDecimal("0.01"), List.of(), "GBP");
        FeeRequest request = new FeeRequest(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("200.00"), "GBP"),
                new AccountRef("SortCodeAccountNumber", "123"), null);

        List<Charge> charges = service.dryRun(new DryRunFeeCalculationUseCase.DryRunCommand(rule, request));

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).amount().amount()).isEqualByComparingTo("2.00");
    }
}

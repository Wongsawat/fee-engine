package com.wpanther.pisp.fee.engine.application.service;

import com.wpanther.pisp.fee.engine.application.port.in.CalculateFeesUseCase;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleRepository;
import com.wpanther.pisp.fee.engine.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CalculateFeesServiceTest {

    private FeeRuleRepository feeRuleRepository;
    private CalculateFeesService service;

    @BeforeEach
    void setup() {
        feeRuleRepository = mock(FeeRuleRepository.class);
        KieContainer kieContainer = KieServices.Factory.get().getKieClasspathContainer();
        service = new CalculateFeesService(feeRuleRepository, kieContainer);
    }

    @Test
    void returnsEmptyListWhenNoRulesMatch() {
        when(feeRuleRepository.findMatching(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        List<Charge> result = service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "123")),
                Optional.empty()));

        assertThat(result).isEmpty();
    }

    @Test
    void passesDebtorAccountIdToRepositoryWhenBorneByDebtor() {
        when(feeRuleRepository.findMatching(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "DEBTOR_ID")),
                Optional.of(new AccountRef("SortCodeAccountNumber", "CREDITOR_ID"))));

        verify(feeRuleRepository).findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.of("DEBTOR_ID"));
    }

    @Test
    void passesCreditorAccountIdToRepositoryWhenBorneByCreditor() {
        when(feeRuleRepository.findMatching(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByCreditor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "DEBTOR_ID")),
                Optional.of(new AccountRef("SortCodeAccountNumber", "CREDITOR_ID"))));

        verify(feeRuleRepository).findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByCreditor,
                "GBP", Optional.of("CREDITOR_ID"));
    }

    @Test
    void returnsImmediatelyForFollowingServiceLevel() {
        List<Charge> result = service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.FollowingServiceLevel,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.empty(), Optional.empty()));

        assertThat(result).isEmpty();
        verifyNoInteractions(feeRuleRepository);
    }

    @Test
    void returnsFlatChargeWhenFlatRuleMatches() {
        FeeRule rule = new FeeRule("CHARGEType001", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("1.50"), null, List.of(), "GBP");
        when(feeRuleRepository.findMatching(any(), any(), any(), any(), any()))
                .thenReturn(List.of(rule));

        List<Charge> result = service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "DEBTOR_ID")),
                Optional.empty()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chargeType()).isEqualTo("CHARGEType001");
        assertThat(result.get(0).chargeBearer()).isEqualTo(ChargeBearer.BorneByDebtor);
        assertThat(result.get(0).amount().amount()).isEqualByComparingTo("1.50");
    }

    @Test
    void sharedBearerLooksUpBothDebtorAndCreditorRules() {
        FeeRule debtorRule = new FeeRule("CHARGEType001", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("1.50"), null, List.of(), "GBP");
        FeeRule creditorRule = new FeeRule("CHARGEType002", ChargeBearer.BorneByCreditor, FeeType.FLAT,
                new BigDecimal("0.50"), null, List.of(), "GBP");
        when(feeRuleRepository.findMatching(any(), any(), eq(ChargeBearer.BorneByDebtor), any(), any()))
                .thenReturn(List.of(debtorRule));
        when(feeRuleRepository.findMatching(any(), any(), eq(ChargeBearer.BorneByCreditor), any(), any()))
                .thenReturn(List.of(creditorRule));

        List<Charge> result = service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.Shared,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "DEBTOR_ID")),
                Optional.of(new AccountRef("SortCodeAccountNumber", "CREDITOR_ID"))));

        verify(feeRuleRepository).findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.of("DEBTOR_ID"));
        verify(feeRuleRepository).findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByCreditor,
                "GBP", Optional.of("CREDITOR_ID"));
        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(c -> c.chargeBearer() == ChargeBearer.BorneByDebtor);
        assertThat(result).anyMatch(c -> c.chargeBearer() == ChargeBearer.BorneByCreditor);
    }
}

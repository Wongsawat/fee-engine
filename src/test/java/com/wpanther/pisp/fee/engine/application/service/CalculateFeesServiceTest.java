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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(feeRuleRepository.findMatching(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        List<Charge> result = service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "123")),
                Optional.empty(), Optional.empty()));

        assertThat(result).isEmpty();
    }

    @Test
    void passesDebtorAccountIdToRepositoryWhenBorneByDebtor() {
        when(feeRuleRepository.findMatching(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "DEBTOR_ID")),
                Optional.of(new AccountRef("SortCodeAccountNumber", "CREDITOR_ID")),
                Optional.empty()));

        verify(feeRuleRepository).findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.of("DEBTOR_ID"));
    }

    @Test
    void passesCreditorAccountIdToRepositoryWhenBorneByCreditor() {
        when(feeRuleRepository.findMatching(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByCreditor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "DEBTOR_ID")),
                Optional.of(new AccountRef("SortCodeAccountNumber", "CREDITOR_ID")),
                Optional.empty()));

        verify(feeRuleRepository).findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByCreditor,
                "GBP", Optional.empty(), Optional.of("CREDITOR_ID"));
    }

    @Test
    void returnsImmediatelyForFollowingServiceLevel() {
        List<Charge> result = service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.FollowingServiceLevel,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.empty(), Optional.empty(), Optional.empty()));

        assertThat(result).isEmpty();
        verifyNoInteractions(feeRuleRepository);
    }

    @Test
    void returnsFlatChargeWhenFlatRuleMatches() {
        FeeRule rule = new FeeRule("CHARGEType001", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("1.50"), null, null, null, List.of(), "GBP", null);
        when(feeRuleRepository.findMatching(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(rule));

        List<Charge> result = service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "DEBTOR_ID")),
                Optional.empty(), Optional.empty()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chargeType()).isEqualTo("CHARGEType001");
        assertThat(result.get(0).chargeBearer()).isEqualTo(ChargeBearer.BorneByDebtor);
        assertThat(result.get(0).amount().amount()).isEqualByComparingTo("1.50");
    }

    @Test
    void throwsForUnknownCurrencyCode() {
        assertThatThrownBy(() -> service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "XYZ"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "123")),
                Optional.empty(), Optional.empty())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("XYZ");
    }

    @Test
    void sharedBearerRetainsBothChargesWhenSameChargeTypeHasDifferentBearer() {
        FeeRule debtorRule = new FeeRule("TRANSFER_FEE", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("1.50"), null, null, null, List.of(), "GBP", null);
        FeeRule creditorRule = new FeeRule("TRANSFER_FEE", ChargeBearer.BorneByCreditor, FeeType.FLAT,
                new BigDecimal("0.50"), null, null, null, List.of(), "GBP", null);
        when(feeRuleRepository.findMatching(any(), any(), eq(ChargeBearer.BorneByDebtor), any(), any(), any()))
                .thenReturn(List.of(debtorRule));
        when(feeRuleRepository.findMatching(any(), any(), eq(ChargeBearer.BorneByCreditor), any(), any(), any()))
                .thenReturn(List.of(creditorRule));

        List<Charge> result = service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.Shared,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "DEBTOR_ID")),
                Optional.of(new AccountRef("SortCodeAccountNumber", "CREDITOR_ID")),
                Optional.empty()));

        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(c -> c.chargeBearer() == ChargeBearer.BorneByDebtor
                && c.chargeType().equals("TRANSFER_FEE"));
        assertThat(result).anyMatch(c -> c.chargeBearer() == ChargeBearer.BorneByCreditor
                && c.chargeType().equals("TRANSFER_FEE"));
    }

    @Test
    void sharedBearerLooksUpBothDebtorAndCreditorRules() {
        FeeRule debtorRule = new FeeRule("CHARGEType001", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("1.50"), null, null, null, List.of(), "GBP", null);
        FeeRule creditorRule = new FeeRule("CHARGEType002", ChargeBearer.BorneByCreditor, FeeType.FLAT,
                new BigDecimal("0.50"), null, null, null, List.of(), "GBP", null);
        when(feeRuleRepository.findMatching(any(), any(), eq(ChargeBearer.BorneByDebtor), any(), any(), any()))
                .thenReturn(List.of(debtorRule));
        when(feeRuleRepository.findMatching(any(), any(), eq(ChargeBearer.BorneByCreditor), any(), any(), any()))
                .thenReturn(List.of(creditorRule));

        List<Charge> result = service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.Shared,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "DEBTOR_ID")),
                Optional.of(new AccountRef("SortCodeAccountNumber", "CREDITOR_ID")),
                Optional.empty()));

        verify(feeRuleRepository).findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.of("DEBTOR_ID"));
        verify(feeRuleRepository).findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByCreditor,
                "GBP", Optional.empty(), Optional.of("CREDITOR_ID"));
        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(c -> c.chargeBearer() == ChargeBearer.BorneByDebtor);
        assertThat(result).anyMatch(c -> c.chargeBearer() == ChargeBearer.BorneByCreditor);
    }

    @Test
    void returnsFreeChargeWithZeroAmountWhenFreeRuleMatches() {
        FeeRule rule = new FeeRule("CHARGEType004", ChargeBearer.BorneByDebtor, FeeType.FREE,
                null, null, null, null, List.of(), "GBP", null);
        when(feeRuleRepository.findMatching(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(rule));

        List<Charge> result = service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "DEBTOR_ID")),
                Optional.empty(), Optional.empty()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chargeType()).isEqualTo("CHARGEType004");
        assertThat(result.get(0).chargeBearer()).isEqualTo(ChargeBearer.BorneByDebtor);
        assertThat(result.get(0).amount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.get(0).amount().currency()).isEqualTo("GBP");
    }

    @Test
    void sharedBearerWithFreeRuleProducesOneZeroChargePerBearer() {
        FeeRule debtorFreeRule = new FeeRule("CHARGEType004", ChargeBearer.BorneByDebtor, FeeType.FREE,
                null, null, null, null, List.of(), "GBP", null);
        FeeRule creditorFreeRule = new FeeRule("CHARGEType004", ChargeBearer.BorneByCreditor, FeeType.FREE,
                null, null, null, null, List.of(), "GBP", null);
        when(feeRuleRepository.findMatching(any(), any(), eq(ChargeBearer.BorneByDebtor), any(), any(), any()))
                .thenReturn(List.of(debtorFreeRule));
        when(feeRuleRepository.findMatching(any(), any(), eq(ChargeBearer.BorneByCreditor), any(), any(), any()))
                .thenReturn(List.of(creditorFreeRule));

        List<Charge> result = service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.Shared,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "DEBTOR_ID")),
                Optional.of(new AccountRef("SortCodeAccountNumber", "CREDITOR_ID")),
                Optional.empty()));

        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(c -> c.chargeBearer() == ChargeBearer.BorneByDebtor
                && c.amount().amount().compareTo(BigDecimal.ZERO) == 0);
        assertThat(result).anyMatch(c -> c.chargeBearer() == ChargeBearer.BorneByCreditor
                && c.amount().amount().compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void flatRuleWinsOverFreeRuleForSameChargeTypeAndBearer() {
        FeeRule flatRule = new FeeRule("CHARGEType004", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("2.00"), null, null, null, List.of(), "GBP", null);
        FeeRule freeRule = new FeeRule("CHARGEType004", ChargeBearer.BorneByDebtor, FeeType.FREE,
                null, null, null, null, List.of(), "GBP", null);
        when(feeRuleRepository.findMatching(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(flatRule, freeRule));

        List<Charge> result = service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "DEBTOR_ID")),
                Optional.empty(), Optional.empty()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chargeType()).isEqualTo("CHARGEType004");
        assertThat(result.get(0).amount().amount()).isEqualByComparingTo("2.00");
    }

    @Test
    void passesDestinationCountryToRepositoryForInternationalPayment() {
        when(feeRuleRepository.findMatching(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("500.00"), "USD"),
                Optional.of(new AccountRef("IBAN", "GB29NWBK60161331926819")),
                Optional.empty(), Optional.of("IN")));

        verify(feeRuleRepository).findMatching(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                "USD", Optional.of("IN"), Optional.of("GB29NWBK60161331926819"));
    }

    @Test
    void passesEmptyDestinationCountryForDomesticPayment() {
        when(feeRuleRepository.findMatching(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                new InstructedAmount(new BigDecimal("100.00"), "GBP"),
                Optional.of(new AccountRef("SortCodeAccountNumber", "12345678901234")),
                Optional.empty(), Optional.empty()));

        verify(feeRuleRepository).findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.of("12345678901234"));
    }

    @Test
    void sharedBearerPropagatesDestinationCountryToBothLookups() {
        when(feeRuleRepository.findMatching(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.calculate(new CalculateFeesUseCase.Command(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.Shared,
                new InstructedAmount(new BigDecimal("500.00"), "USD"),
                Optional.of(new AccountRef("IBAN", "DEBTOR_ID")),
                Optional.of(new AccountRef("IBAN", "CREDITOR_ID")),
                Optional.of("IN")));

        verify(feeRuleRepository).findMatching(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                "USD", Optional.of("IN"), Optional.of("DEBTOR_ID"));
        verify(feeRuleRepository).findMatching(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByCreditor,
                "USD", Optional.of("IN"), Optional.of("CREDITOR_ID"));
    }
}

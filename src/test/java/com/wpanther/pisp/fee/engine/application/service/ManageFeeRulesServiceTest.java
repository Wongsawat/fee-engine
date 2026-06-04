package com.wpanther.pisp.fee.engine.application.service;

import com.wpanther.pisp.fee.engine.application.port.in.ManageFeeRulesUseCase;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleDetails;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleRepository;
import com.wpanther.pisp.fee.engine.domain.exception.FeeRuleNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ManageFeeRulesServiceTest {

    private FeeRuleRepository repository;
    private ManageFeeRulesService service;
    private UUID ruleId;

    @BeforeEach
    void setup() {
        repository = mock(FeeRuleRepository.class);
        service = new ManageFeeRulesService(repository);
        ruleId = UUID.randomUUID();
    }

    private FeeRuleDetails flatDetails() {
        return new FeeRuleDetails(ruleId, "DOMESTIC", "FPS", "BorneByDebtor", null,
                "CHARGEType001", "FLAT", new BigDecimal("1.50"), null, null, null, null, "GBP",
                true, 0, Instant.now(), "system", Instant.now(), "system");
    }

    @Test
    void createsFeeRule() {
        when(repository.save(any())).thenAnswer(inv -> {
            var d = (FeeRuleDetails) inv.getArgument(0);
            return new FeeRuleDetails(UUID.randomUUID(), d.paymentType(), d.scheme(),
                    d.chargeBearer(), d.accountIdentification(), d.chargeType(), d.feeType(),
                    d.flatAmount(), d.percentage(),
                    d.minFee(), d.maxFee(),
                    d.tiers(), d.currency(), d.active(),
                    0, Instant.now(), "system", Instant.now(), "system");
        });

        ManageFeeRulesUseCase.CreateCommand cmd = new ManageFeeRulesUseCase.CreateCommand(
                "DOMESTIC", "FPS", "BorneByDebtor", null, "CHARGEType001", "FLAT",
                new BigDecimal("1.50"), null, null, null, null, "GBP");
        FeeRuleDetails result = service.create(cmd);

        assertThat(result.id()).isNotNull();
        assertThat(result.version()).isEqualTo(0);
        assertThat(result.createdBy()).isEqualTo("system");
        verify(repository).save(any());
    }

    @Test
    void updatesFeeRule() {
        when(repository.findById(ruleId)).thenReturn(Optional.of(flatDetails()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ManageFeeRulesUseCase.UpdateCommand cmd = new ManageFeeRulesUseCase.UpdateCommand(
                ruleId, "DOMESTIC", "FPS", "BorneByDebtor", null, "CHARGEType001", "FLAT",
                new BigDecimal("2.00"), null, null, null, null, "GBP", 0L);
        FeeRuleDetails result = service.update(cmd);

        assertThat(result.flatAmount()).isEqualByComparingTo("2.00");
        verify(repository).save(any());
    }

    @Test
    void updateThrowsWhenRuleNotFound() {
        when(repository.findById(ruleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new ManageFeeRulesUseCase.UpdateCommand(
                ruleId, "DOMESTIC", "FPS", "BorneByDebtor", null, "CHARGEType001", "FLAT",
                new BigDecimal("2.00"), null, null, null, null, "GBP", 0L)))
                .isInstanceOf(FeeRuleNotFoundException.class);
    }

    @Test
    void findsFeeRuleById() {
        when(repository.findById(ruleId)).thenReturn(Optional.of(flatDetails()));

        FeeRuleDetails result = service.findById(ruleId);

        assertThat(result.chargeType()).isEqualTo("CHARGEType001");
    }

    @Test
    void findByIdThrowsWhenNotFound() {
        when(repository.findById(ruleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(ruleId))
                .isInstanceOf(FeeRuleNotFoundException.class);
    }

    @Test
    void findAllWithFilters() {
        var page = new PageImpl<>(List.of(flatDetails()));
        when(repository.findByFilters(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        Page<FeeRuleDetails> result = service.findAll("DOMESTIC", null, null, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void togglesStatus() {
        when(repository.findById(ruleId)).thenReturn(Optional.of(flatDetails()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FeeRuleDetails result = service.toggleStatus(ruleId, false, 0L);

        assertThat(result.active()).isFalse();
        verify(repository).save(argThat(d -> !d.active()));
    }

    @Test
    void toggleStatusThrowsWhenNotFound() {
        when(repository.findById(ruleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleStatus(ruleId, false, 0L))
                .isInstanceOf(FeeRuleNotFoundException.class);
    }

    @Test
    void updateThrowsOnStaleVersion() {
        var current = new FeeRuleDetails(ruleId, "DOMESTIC", "FPS", "BorneByDebtor", null,
                "CHARGEType001", "FLAT", new BigDecimal("1.50"), null, null, null, null, "GBP",
                true, 2, Instant.now(), "system", Instant.now(), "system");
        when(repository.findById(ruleId)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.update(new ManageFeeRulesUseCase.UpdateCommand(
                ruleId, "DOMESTIC", "FPS", "BorneByDebtor", null, "CHARGEType001", "FLAT",
                new BigDecimal("2.00"), null, null, null, null, "GBP", 0L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void toggleStatusThrowsOnStaleVersion() {
        var current = new FeeRuleDetails(ruleId, "DOMESTIC", "FPS", "BorneByDebtor", null,
                "CHARGEType001", "FLAT", new BigDecimal("1.50"), null, null, null, null, "GBP",
                true, 2, Instant.now(), "system", Instant.now(), "system");
        when(repository.findById(ruleId)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.toggleStatus(ruleId, false, 0L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private FeeRuleDetails percentageDetailsWithCaps() {
        return new FeeRuleDetails(ruleId, "DOMESTIC", "FPS", "BorneByDebtor", null,
                "CHARGEType002", "PERCENTAGE", null, new BigDecimal("0.01"),
                new BigDecimal("1.00"), new BigDecimal("50.00"), null, "GBP",
                true, 0, Instant.now(), "system", Instant.now(), "system");
    }

    @Test
    void createPassesCapsToRepository() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var cmd = new ManageFeeRulesUseCase.CreateCommand(
                "DOMESTIC", "FPS", "BorneByDebtor", null, "CHARGEType002", "PERCENTAGE",
                null, new BigDecimal("0.01"), new BigDecimal("1.00"), new BigDecimal("50.00"),
                null, "GBP");

        FeeRuleDetails result = service.create(cmd);

        assertThat(result.minFee()).isEqualByComparingTo("1.00");
        assertThat(result.maxFee()).isEqualByComparingTo("50.00");
    }

    @Test
    void updateChangesCaps() {
        when(repository.findById(ruleId)).thenReturn(Optional.of(percentageDetailsWithCaps()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var cmd = new ManageFeeRulesUseCase.UpdateCommand(
                ruleId, "DOMESTIC", "FPS", "BorneByDebtor", null, "CHARGEType002", "PERCENTAGE",
                null, new BigDecimal("0.01"), new BigDecimal("2.00"), new BigDecimal("80.00"),
                null, "GBP", 0L);

        FeeRuleDetails result = service.update(cmd);

        assertThat(result.minFee()).isEqualByComparingTo("2.00");
        assertThat(result.maxFee()).isEqualByComparingTo("80.00");
    }

    @Test
    void toggleStatusPreservesCaps() {
        when(repository.findById(ruleId)).thenReturn(Optional.of(percentageDetailsWithCaps()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FeeRuleDetails result = service.toggleStatus(ruleId, false, 0L);

        assertThat(result.active()).isFalse();
        assertThat(result.minFee()).isEqualByComparingTo("1.00");
        assertThat(result.maxFee()).isEqualByComparingTo("50.00");
    }
}

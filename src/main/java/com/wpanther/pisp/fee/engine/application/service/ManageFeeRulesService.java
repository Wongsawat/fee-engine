package com.wpanther.pisp.fee.engine.application.service;

import com.wpanther.pisp.fee.engine.application.port.in.ManageFeeRulesUseCase;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleDetails;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleRepository;
import com.wpanther.pisp.fee.engine.domain.exception.FeeRuleNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ManageFeeRulesService implements ManageFeeRulesUseCase {

    private final FeeRuleRepository feeRuleRepository;

    public ManageFeeRulesService(FeeRuleRepository feeRuleRepository) {
        this.feeRuleRepository = feeRuleRepository;
    }

    @Override
    public FeeRuleDetails create(CreateCommand command) {
        var details = new FeeRuleDetails(
                null, command.paymentType(), command.scheme(), command.chargeBearer(),
                command.accountIdentification(), command.destinationCountry(),
                command.chargeType(), command.feeType(),
                command.flatAmount(), command.percentage(),
                command.minFee(), command.maxFee(),
                command.tiers(), command.currency(),
                command.priority(), true, 0, null, null, null, null);
        return feeRuleRepository.save(details);
    }

    @Transactional
    @Override
    public FeeRuleDetails update(UpdateCommand command) {
        FeeRuleDetails existing = feeRuleRepository.findById(command.id())
                .orElseThrow(() -> new FeeRuleNotFoundException(command.id()));
        if (existing.version() != command.version()) {
            throw new ObjectOptimisticLockingFailureException(FeeRuleDetails.class, command.id());
        }
        var updated = new FeeRuleDetails(
                existing.id(), command.paymentType(), command.scheme(), command.chargeBearer(),
                command.accountIdentification(), command.destinationCountry(),
                command.chargeType(), command.feeType(),
                command.flatAmount(), command.percentage(),
                command.minFee(), command.maxFee(),
                command.tiers(), command.currency(),
                command.priority(), existing.active(), existing.version(),
                existing.createdAt(), existing.createdBy(), existing.updatedAt(), existing.updatedBy());
        return feeRuleRepository.save(updated);
    }

    @Override
    public FeeRuleDetails findById(UUID id) {
        return feeRuleRepository.findById(id)
                .orElseThrow(() -> new FeeRuleNotFoundException(id));
    }

    @Override
    public Page<FeeRuleDetails> findAll(String paymentType, String scheme, String chargeBearer,
                                         String feeType, String currency, String accountIdentification,
                                         Boolean active, Pageable pageable) {
        return feeRuleRepository.findByFilters(paymentType, scheme, chargeBearer, feeType,
                currency, accountIdentification, active, pageable);
    }

    @Transactional
    @Override
    public FeeRuleDetails toggleStatus(UUID id, boolean active, long version) {
        FeeRuleDetails existing = feeRuleRepository.findById(id)
                .orElseThrow(() -> new FeeRuleNotFoundException(id));
        if (existing.version() != version) {
            throw new ObjectOptimisticLockingFailureException(FeeRuleDetails.class, id);
        }
        var toggled = new FeeRuleDetails(
                existing.id(), existing.paymentType(), existing.scheme(), existing.chargeBearer(),
                existing.accountIdentification(), existing.destinationCountry(),
                existing.chargeType(), existing.feeType(),
                existing.flatAmount(), existing.percentage(),
                existing.minFee(), existing.maxFee(),
                existing.tiers(), existing.currency(),
                existing.priority(), active, existing.version(),
                existing.createdAt(), existing.createdBy(), existing.updatedAt(), existing.updatedBy());
        return feeRuleRepository.save(toggled);
    }
}

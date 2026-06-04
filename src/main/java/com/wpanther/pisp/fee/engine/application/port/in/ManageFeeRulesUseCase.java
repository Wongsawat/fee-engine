package com.wpanther.pisp.fee.engine.application.port.in;

import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ManageFeeRulesUseCase {

    record CreateCommand(
        String paymentType, String scheme, String chargeBearer,
        String accountIdentification, String destinationCountry,
        String chargeType, String feeType,
        BigDecimal flatAmount, BigDecimal percentage,
        BigDecimal minFee, BigDecimal maxFee,
        List<FeeRuleDetails.TierInfo> tiers, String currency
    ) {}

    record UpdateCommand(
        UUID id,
        String paymentType, String scheme, String chargeBearer,
        String accountIdentification, String destinationCountry,
        String chargeType, String feeType,
        BigDecimal flatAmount, BigDecimal percentage,
        BigDecimal minFee, BigDecimal maxFee,
        List<FeeRuleDetails.TierInfo> tiers, String currency, long version
    ) {}

    FeeRuleDetails create(CreateCommand command);
    FeeRuleDetails update(UpdateCommand command);
    FeeRuleDetails findById(UUID id);
    Page<FeeRuleDetails> findAll(String paymentType, String scheme, String chargeBearer,
                                 String feeType, String currency, String accountIdentification,
                                 Boolean active, Pageable pageable);
    FeeRuleDetails toggleStatus(UUID id, boolean active, long version);
}

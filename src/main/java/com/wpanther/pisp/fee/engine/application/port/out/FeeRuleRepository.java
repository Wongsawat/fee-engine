package com.wpanther.pisp.fee.engine.application.port.out;

import com.wpanther.pisp.fee.engine.domain.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeRuleRepository {
    List<FeeRule> findMatching(PaymentType paymentType, PaymentScheme scheme,
                               ChargeBearer chargeBearer, String currency,
                               Optional<String> accountIdentification);

    FeeRuleDetails save(FeeRuleDetails details);
    Optional<FeeRuleDetails> findById(UUID id);
    Page<FeeRuleDetails> findByFilters(String paymentType, String scheme, String chargeBearer,
                                       String feeType, String currency, String accountIdentification,
                                       Boolean active, Pageable pageable);
}

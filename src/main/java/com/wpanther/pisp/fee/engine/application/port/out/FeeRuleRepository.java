package com.wpanther.pisp.fee.engine.application.port.out;

import com.wpanther.pisp.fee.engine.domain.model.*;
import java.util.List;
import java.util.Optional;

public interface FeeRuleRepository {
    List<FeeRule> findMatching(PaymentType paymentType, PaymentScheme scheme,
                               ChargeBearer chargeBearer, String currency,
                               Optional<String> accountIdentification);
}

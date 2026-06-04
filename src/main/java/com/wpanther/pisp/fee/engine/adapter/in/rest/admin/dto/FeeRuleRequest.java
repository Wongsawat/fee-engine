package com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public interface FeeRuleRequest {
    String feeType();
    BigDecimal flatAmount();
    BigDecimal percentage();
    BigDecimal minFee();
    BigDecimal maxFee();
    List<TierDto> tiers();
    String chargeBearer();
}

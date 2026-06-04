package com.wpanther.pisp.fee.engine.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeeRuleDetails(
    UUID id,
    String paymentType,
    String scheme,
    String chargeBearer,
    String accountIdentification,
    String destinationCountry,
    String chargeType,
    String feeType,
    BigDecimal flatAmount,
    BigDecimal percentage,
    BigDecimal minFee,
    BigDecimal maxFee,
    List<TierInfo> tiers,
    String currency,
    boolean active,
    long version,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy
) {
    public record TierInfo(BigDecimal min, BigDecimal max, BigDecimal amount) {}
}

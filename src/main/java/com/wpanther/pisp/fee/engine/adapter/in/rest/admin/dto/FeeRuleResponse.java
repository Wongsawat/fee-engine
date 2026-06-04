package com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeeRuleResponse(
    UUID id,
    String paymentType,
    String scheme,
    String chargeBearer,
    String accountIdentification,
    String chargeType,
    String feeType,
    BigDecimal flatAmount,
    BigDecimal percentage,
    BigDecimal minFee,
    BigDecimal maxFee,
    List<TierDto> tiers,
    String currency,
    boolean active,
    long version,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy
) {}

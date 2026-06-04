package com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto;

import com.wpanther.pisp.fee.engine.infrastructure.validation.ValidFeeRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

@ValidFeeRule
public record UpdateFeeRuleRequest(
    @NotBlank String paymentType,
    @NotBlank String scheme,
    @NotBlank String chargeBearer,
    String accountIdentification,
    String destinationCountry,
    @NotBlank String chargeType,
    @NotBlank String feeType,
    BigDecimal flatAmount,
    BigDecimal percentage,
    BigDecimal minFee,
    BigDecimal maxFee,
    @Valid List<TierDto> tiers,
    @NotBlank String currency,
    Integer priority,
    @NotNull Long version
) implements FeeRuleRequest {}

package com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TierDto(
    @NotNull BigDecimal min,
    @NotNull BigDecimal max,
    @NotNull String rateType,
    BigDecimal amount,
    BigDecimal percentage
) {}

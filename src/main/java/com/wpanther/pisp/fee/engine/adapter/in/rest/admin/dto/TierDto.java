package com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TierDto(
    @NotNull BigDecimal min,
    @NotNull BigDecimal max,
    @NotNull @Positive BigDecimal amount
) {}

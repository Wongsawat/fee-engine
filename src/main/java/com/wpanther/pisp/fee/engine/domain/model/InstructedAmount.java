package com.wpanther.pisp.fee.engine.domain.model;

import java.math.BigDecimal;

public record InstructedAmount(BigDecimal amount, String currency) {}

package com.wpanther.pisp.fee.engine.application.service;

import com.wpanther.pisp.fee.engine.domain.model.AccountRef;
import com.wpanther.pisp.fee.engine.domain.model.ChargeBearer;
import java.math.BigDecimal;

public record TierContribution(
        ChargeBearer chargeBearer,
        String chargeType,
        BigDecimal amount,
        String currency,
        AccountRef chargingParty) {}

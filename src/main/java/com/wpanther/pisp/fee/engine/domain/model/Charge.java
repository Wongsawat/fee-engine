package com.wpanther.pisp.fee.engine.domain.model;

public record Charge(ChargeBearer chargeBearer, String chargeType,
                     InstructedAmount amount, AccountRef chargingParty) {}

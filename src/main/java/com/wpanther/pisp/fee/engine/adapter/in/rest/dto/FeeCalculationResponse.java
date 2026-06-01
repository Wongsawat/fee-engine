package com.wpanther.pisp.fee.engine.adapter.in.rest.dto;

import java.util.List;

public record FeeCalculationResponse(List<ChargeDto> charges) {

    public record ChargeDto(
        String chargeBearer,
        String type,
        AmountDto amount,
        AccountDto chargingParty
    ) {}

    public record AmountDto(String amount, String currency) {}

    public record AccountDto(String schemeName, String identification) {}
}

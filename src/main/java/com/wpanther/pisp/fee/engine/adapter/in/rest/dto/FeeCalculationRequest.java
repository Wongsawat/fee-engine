package com.wpanther.pisp.fee.engine.adapter.in.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record FeeCalculationRequest(
    @NotBlank String paymentType,
    @NotBlank String scheme,
    @NotBlank String chargeBearer,
    @NotNull @Valid AmountDto instructedAmount,
    @Valid AccountDto debtorAccount,
    @Valid AccountDto creditorAccount
) {
    public record AmountDto(@NotNull @Positive BigDecimal amount, @NotBlank String currency) {}
    public record AccountDto(@NotBlank String schemeName, @NotBlank String identification) {}
}

package com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto;

import com.wpanther.pisp.fee.engine.adapter.in.rest.dto.FeeCalculationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record DryRunRequest(
    @NotNull @Valid CreateFeeRuleRequest rule,
    @Valid FeeCalculationRequest.AmountDto instructedAmount,
    @Valid FeeCalculationRequest.AccountDto debtorAccount,
    @Valid FeeCalculationRequest.AccountDto creditorAccount
) {}

package com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto;

import com.wpanther.pisp.fee.engine.adapter.in.rest.dto.FeeCalculationResponse;

import java.util.List;

public record DryRunResponse(
    List<FeeCalculationResponse.ChargeDto> charges
) {}

package com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto;

import jakarta.validation.constraints.NotNull;

public record StatusToggleRequest(
    @NotNull Boolean active,
    @NotNull Long version
) {}

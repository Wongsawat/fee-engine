package com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto;

import java.util.List;

public record FeeRulePageResponse(
    List<FeeRuleResponse> content,
    PageInfo page
) {
    public record PageInfo(int number, int size, long totalElements, int totalPages) {}

    public static FeeRulePageResponse from(org.springframework.data.domain.Page<FeeRuleResponse> springPage) {
        return new FeeRulePageResponse(
                springPage.getContent(),
                new PageInfo(springPage.getNumber(), springPage.getSize(),
                        springPage.getTotalElements(), springPage.getTotalPages()));
    }
}

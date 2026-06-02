package com.wpanther.pisp.fee.engine.adapter.in.rest.admin;

import com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto.*;
import com.wpanther.pisp.fee.engine.application.port.in.ManageFeeRulesUseCase;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/admin/fee-rules")
public class FeeRuleAdminController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "paymentType", "scheme", "chargeBearer", "feeType", "currency");

    private final ManageFeeRulesUseCase manageFeeRulesUseCase;
    private final FeeRuleDtoMapper mapper;

    public FeeRuleAdminController(ManageFeeRulesUseCase manageFeeRulesUseCase,
                                   FeeRuleDtoMapper mapper) {
        this.manageFeeRulesUseCase = manageFeeRulesUseCase;
        this.mapper = mapper;
    }

    @GetMapping
    public FeeRulePageResponse list(
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) String scheme,
            @RequestParam(required = false) String chargeBearer,
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String accountIdentification,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        String[] parts = sort.split(",", 2);
        String field = parts[0].replaceFirst("^[+-]", "");
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            field = "createdAt";
        }
        Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort parsedSort = Sort.by(dir, field);
        Page<FeeRuleDetails> result = manageFeeRulesUseCase.findAll(
                paymentType, scheme, chargeBearer, feeType, currency,
                accountIdentification, active, PageRequest.of(page, size, parsedSort));
        return FeeRulePageResponse.from(result.map(mapper::toResponse));
    }

    @GetMapping("/{id}")
    public FeeRuleResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(manageFeeRulesUseCase.findById(id));
    }

    @PostMapping
    public ResponseEntity<FeeRuleResponse> create(
            @RequestBody @Valid CreateFeeRuleRequest request) {
        FeeRuleDetails saved = manageFeeRulesUseCase.create(mapper.toCreateCommand(request));
        return ResponseEntity.created(URI.create("/admin/fee-rules/" + saved.id()))
                .body(mapper.toResponse(saved));
    }

    @PutMapping("/{id}")
    public FeeRuleResponse update(@PathVariable UUID id,
                                   @RequestBody @Valid UpdateFeeRuleRequest request) {
        return mapper.toResponse(manageFeeRulesUseCase.update(mapper.toUpdateCommand(request, id)));
    }

    @PatchMapping("/{id}/status")
    public FeeRuleResponse toggleStatus(@PathVariable UUID id,
                                         @RequestBody @Valid StatusToggleRequest request) {
        return mapper.toResponse(
                manageFeeRulesUseCase.toggleStatus(id, request.active(), request.version()));
    }
}

package com.wpanther.pisp.fee.engine.adapter.in.rest.admin;

import com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto.*;
import com.wpanther.pisp.fee.engine.application.port.in.DryRunFeeCalculationUseCase;
import com.wpanther.pisp.fee.engine.domain.model.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/fee-rules")
public class DryRunFeeCalculationController {

    private final DryRunFeeCalculationUseCase dryRunUseCase;
    private final FeeRuleDtoMapper mapper;

    public DryRunFeeCalculationController(DryRunFeeCalculationUseCase dryRunUseCase,
                                           FeeRuleDtoMapper mapper) {
        this.dryRunUseCase = dryRunUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/dry-run")
    public ResponseEntity<DryRunResponse> dryRun(
            @RequestBody @Valid DryRunRequest request) {
        if (request.instructedAmount() == null) {
            return ResponseEntity.ok(new DryRunResponse(List.of()));
        }
        FeeRule rule = mapper.toFeeRule(request.rule());
        FeeRequest feeRequest = mapper.toFeeRequest(request);
        List<Charge> charges = dryRunUseCase.dryRun(
                new DryRunFeeCalculationUseCase.DryRunCommand(rule, feeRequest));
        return ResponseEntity.ok(new DryRunResponse(mapper.toChargeDtos(charges)));
    }
}

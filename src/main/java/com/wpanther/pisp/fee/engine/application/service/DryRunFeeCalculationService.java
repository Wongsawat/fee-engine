package com.wpanther.pisp.fee.engine.application.service;

import com.wpanther.pisp.fee.engine.application.port.in.DryRunFeeCalculationUseCase;
import com.wpanther.pisp.fee.engine.domain.model.Charge;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DryRunFeeCalculationService implements DryRunFeeCalculationUseCase {

    private final FeeSessionRunner feeSessionRunner;

    public DryRunFeeCalculationService(FeeSessionRunner feeSessionRunner) {
        this.feeSessionRunner = feeSessionRunner;
    }

    @Override
    public List<Charge> dryRun(DryRunCommand command) {
        if (command.feeRequest() == null) return List.of();
        return feeSessionRunner.run(command.feeRequest(), List.of(command.rule()));
    }
}

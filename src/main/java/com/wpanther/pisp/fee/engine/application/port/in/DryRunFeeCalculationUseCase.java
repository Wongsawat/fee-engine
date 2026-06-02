package com.wpanther.pisp.fee.engine.application.port.in;

import com.wpanther.pisp.fee.engine.domain.model.*;

import java.util.List;

public interface DryRunFeeCalculationUseCase {

    record DryRunCommand(FeeRule rule, FeeRequest feeRequest) {}

    List<Charge> dryRun(DryRunCommand command);
}

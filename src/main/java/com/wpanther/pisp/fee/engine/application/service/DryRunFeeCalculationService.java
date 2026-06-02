package com.wpanther.pisp.fee.engine.application.service;

import com.wpanther.pisp.fee.engine.application.port.in.DryRunFeeCalculationUseCase;
import com.wpanther.pisp.fee.engine.domain.model.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DryRunFeeCalculationService implements DryRunFeeCalculationUseCase {

    private final KieContainer kieContainer;

    public DryRunFeeCalculationService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    @Override
    public List<Charge> dryRun(DryRunCommand command) {
        if (command.feeRequest() == null) return List.of();

        KieSession session = kieContainer.newKieSession("FeeSession");
        try {
            List<Charge> charges = new ArrayList<>();
            session.setGlobal("charges", charges);
            session.insert(command.feeRequest());
            session.insert(command.rule());
            session.fireAllRules();
            return List.copyOf(charges);
        } finally {
            session.dispose();
        }
    }
}

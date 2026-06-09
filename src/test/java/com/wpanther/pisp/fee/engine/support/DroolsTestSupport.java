package com.wpanther.pisp.fee.engine.support;

import com.wpanther.pisp.fee.engine.application.service.FeeSessionRunner;
import com.wpanther.pisp.fee.engine.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;

import java.util.List;

public abstract class DroolsTestSupport {

    protected KieContainer kieContainer;
    private FeeSessionRunner sessionRunner;

    @BeforeEach
    void setupKie() {
        kieContainer = KieServices.Factory.get().getKieClasspathContainer();
        sessionRunner = new FeeSessionRunner(kieContainer);
    }

    protected List<Charge> fireRules(FeeRequest request, List<FeeRule> rules) {
        return sessionRunner.run(request, rules);
    }
}

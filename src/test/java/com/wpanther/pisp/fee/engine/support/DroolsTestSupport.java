package com.wpanther.pisp.fee.engine.support;

import com.wpanther.pisp.fee.engine.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import java.util.ArrayList;
import java.util.List;

public abstract class DroolsTestSupport {

    protected KieContainer kieContainer;

    @BeforeEach
    void setupKie() {
        kieContainer = KieServices.Factory.get().getKieClasspathContainer();
    }

    protected List<Charge> fireRules(FeeRequest request, List<FeeRule> rules) {
        KieSession session = kieContainer.newKieSession("FeeSession");
        try {
            List<Charge> charges = new ArrayList<>();
            session.setGlobal("charges", charges);
            session.insert(request);
            rules.forEach(session::insert);
            session.fireAllRules();
            return List.copyOf(charges);
        } finally {
            session.dispose();
        }
    }
}

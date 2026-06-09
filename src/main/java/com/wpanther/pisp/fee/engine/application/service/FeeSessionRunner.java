package com.wpanther.pisp.fee.engine.application.service;

import com.wpanther.pisp.fee.engine.domain.model.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class FeeSessionRunner {

    private final KieContainer kieContainer;

    public FeeSessionRunner(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public List<Charge> run(FeeRequest request, List<FeeRule> rules) {
        KieSession session = kieContainer.newKieSession("FeeSession");
        try {
            List<Charge> charges = new ArrayList<>();
            List<TierContribution> tierContributions = new ArrayList<>();
            session.setGlobal("charges", charges);
            session.setGlobal("tierContributions", tierContributions);
            session.insert(request);
            rules.forEach(session::insert);
            session.fireAllRules();

            var result = new LinkedHashMap<String, Charge>();
            charges.forEach(c -> result.putIfAbsent(key(c.chargeBearer(), c.chargeType()), c));

            tierContributions.stream()
                    .collect(Collectors.groupingBy(
                            (TierContribution c) -> key(c.chargeBearer(), c.chargeType()),
                            LinkedHashMap::new,
                            Collectors.toList()))
                    .forEach((k, list) -> {
                        TierContribution first = list.get(0);
                        BigDecimal total = list.stream()
                                .map(TierContribution::amount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        int scale = Math.max(
                                java.util.Currency.getInstance(first.currency()).getDefaultFractionDigits(), 0);
                        total = total.setScale(scale, RoundingMode.HALF_UP);
                        result.putIfAbsent(k, new Charge(
                                first.chargeBearer(), first.chargeType(),
                                new InstructedAmount(total, first.currency()),
                                first.chargingParty()));
                    });

            return List.copyOf(result.values());
        } finally {
            session.dispose();
        }
    }

    private static String key(ChargeBearer cb, String chargeType) {
        return cb.name() + ':' + chargeType;
    }
}

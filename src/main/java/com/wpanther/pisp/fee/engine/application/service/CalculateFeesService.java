package com.wpanther.pisp.fee.engine.application.service;

import com.wpanther.pisp.fee.engine.application.port.in.CalculateFeesUseCase;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleRepository;
import com.wpanther.pisp.fee.engine.domain.model.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CalculateFeesService implements CalculateFeesUseCase {

    private final FeeRuleRepository feeRuleRepository;
    private final KieContainer kieContainer;

    public CalculateFeesService(FeeRuleRepository feeRuleRepository,
                                KieContainer kieContainer) {
        this.feeRuleRepository = feeRuleRepository;
        this.kieContainer = kieContainer;
    }

    @Override
    public List<Charge> calculate(Command command) {
        if (command.chargeBearer() == ChargeBearer.FollowingServiceLevel) return List.of();

        String currency = command.instructedAmount().currency();
        try {
            java.util.Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown currency code: " + currency);
        }

        if (command.chargeBearer() == ChargeBearer.Shared) {
            List<FeeRule> debtorRules = feeRuleRepository.findMatching(
                    command.paymentType(), command.scheme(), ChargeBearer.BorneByDebtor,
                    currency, command.destinationCountry(),
                    command.debtorAccount().map(AccountRef::identification));
            List<FeeRule> creditorRules = feeRuleRepository.findMatching(
                    command.paymentType(), command.scheme(), ChargeBearer.BorneByCreditor,
                    currency, command.destinationCountry(),
                    command.creditorAccount().map(AccountRef::identification));
            List<FeeRule> allRules = new ArrayList<>();
            allRules.addAll(debtorRules);
            allRules.addAll(creditorRules);
            return allRules.isEmpty() ? List.of() : fireSession(command, allRules);
        }

        Optional<String> accountId = command.chargeBearer() == ChargeBearer.BorneByDebtor
                ? command.debtorAccount().map(AccountRef::identification)
                : command.creditorAccount().map(AccountRef::identification);

        List<FeeRule> rules = feeRuleRepository.findMatching(
                command.paymentType(), command.scheme(), command.chargeBearer(),
                currency, command.destinationCountry(), accountId);

        if (rules.isEmpty()) return List.of();
        return fireSession(command, rules);
    }

    private List<Charge> fireSession(Command command, List<FeeRule> rules) {
        FeeRequest feeRequest = new FeeRequest(
                command.paymentType(), command.scheme(), command.chargeBearer(),
                command.instructedAmount(),
                command.debtorAccount().orElse(null),
                command.creditorAccount().orElse(null),
                command.destinationCountry().orElse(null));

        KieSession session = kieContainer.newKieSession("FeeSession");
        try {
            List<Charge> charges = new ArrayList<>();
            session.setGlobal("charges", charges);
            session.insert(feeRequest);
            rules.forEach(session::insert);
            session.fireAllRules();
            var seen = new java.util.LinkedHashMap<String, Charge>();
            charges.forEach(c -> seen.putIfAbsent(c.chargeBearer().name() + ':' + c.chargeType(), c));
            return List.copyOf(seen.values());
        } finally {
            session.dispose();
        }
    }
}

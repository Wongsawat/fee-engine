package com.wpanther.pisp.fee.engine.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pisp.fee.engine.adapter.out.persistence.jpa.FeeRuleEntity;
import com.wpanther.pisp.fee.engine.adapter.out.persistence.jpa.FeeRuleJpaRepository;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleRepository;
import com.wpanther.pisp.fee.engine.domain.model.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
public class FeeRuleRepositoryAdapter implements FeeRuleRepository {

    private final FeeRuleJpaRepository jpaRepo;
    private final ObjectMapper objectMapper;

    public FeeRuleRepositoryAdapter(FeeRuleJpaRepository jpaRepo, ObjectMapper objectMapper) {
        this.jpaRepo = jpaRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<FeeRule> findMatching(PaymentType paymentType, PaymentScheme scheme,
                                      ChargeBearer chargeBearer, String currency,
                                      Optional<String> accountIdentification) {
        List<FeeRuleEntity> all = jpaRepo.findActive(
                paymentType.name(), scheme.name(), chargeBearer.name(), currency);

        boolean hasSpecificRule = accountIdentification.isPresent() && all.stream()
                .anyMatch(e -> accountIdentification.get().equals(e.getAccountIdentification()));

        return all.stream()
                .filter(e -> {
                    if (e.getAccountIdentification() == null) return !hasSpecificRule;
                    return accountIdentification
                            .map(id -> id.equals(e.getAccountIdentification()))
                            .orElse(false);
                })
                .map(this::toDomain)
                .toList();
    }

    private FeeRule toDomain(FeeRuleEntity e) {
        List<Tier> tiers = List.of();
        if (e.getTiers() != null) {
            try {
                List<Map<String, Object>> raw = objectMapper.convertValue(
                        e.getTiers(), new TypeReference<>() {});
                tiers = raw.stream().map(m -> new Tier(
                        toBigDecimal(m.get("min")),
                        toBigDecimal(m.get("max")),
                        toBigDecimal(m.get("amount"))
                )).toList();
            } catch (Exception ex) {
                throw new IllegalStateException(
                    "Failed to parse tiers for fee rule '" + e.getChargeType() + "'", ex);
            }
            validateTiers(tiers, e.getChargeType());
        }
        return new FeeRule(
                e.getChargeType(), ChargeBearer.valueOf(e.getChargeBearer()),
                FeeType.valueOf(e.getFeeType()),
                e.getFlatAmount(), e.getPercentage(),
                tiers, e.getCurrency());
    }

    private void validateTiers(List<Tier> tiers, String chargeType) {
        for (Tier t : tiers) {
            if (t.getMin().compareTo(t.getMax()) >= 0)
                throw new IllegalStateException(
                    "Fee rule '" + chargeType + "': tier min (" + t.getMin() +
                    ") must be less than max (" + t.getMax() + ")");
            if (t.getAmount().compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalStateException(
                    "Fee rule '" + chargeType + "': tier amount must be positive, got " +
                    t.getAmount());
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) throw new IllegalArgumentException("tier field value must not be null");
        if (value instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(value.toString());
    }
}

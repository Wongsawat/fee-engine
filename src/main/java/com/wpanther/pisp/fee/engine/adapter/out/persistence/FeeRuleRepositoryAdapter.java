package com.wpanther.pisp.fee.engine.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pisp.fee.engine.adapter.out.persistence.jpa.FeeRuleEntity;
import com.wpanther.pisp.fee.engine.adapter.out.persistence.jpa.FeeRuleJpaRepository;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleDetails;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleRepository;
import com.wpanther.pisp.fee.engine.domain.exception.FeeRuleNotFoundException;
import com.wpanther.pisp.fee.engine.domain.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.*;

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
                                      Optional<String> destinationCountry,
                                      Optional<String> accountIdentification) {
        List<FeeRuleEntity> all = jpaRepo.findActive(
                paymentType.name(), scheme.name(), chargeBearer.name(), currency);

        String country = destinationCountry.orElse(null);
        String account = accountIdentification.orElse(null);

        List<FeeRuleEntity> candidates = all.stream()
                .filter(e -> {
                    boolean countryOk = country == null
                            ? e.getDestinationCountry() == null
                            : (e.getDestinationCountry() == null
                               || country.equals(e.getDestinationCountry()));
                    boolean accountOk = account == null
                            ? e.getAccountIdentification() == null
                            : (e.getAccountIdentification() == null
                               || account.equals(e.getAccountIdentification()));
                    return countryOk && accountOk;
                })
                .toList();

        return candidates.stream()
                .collect(Collectors.groupingBy(FeeRuleEntity::getChargeType))
                .values().stream()
                .map(group -> group.stream()
                        .min(Comparator
                                .comparingInt(FeeRuleEntity::getPriority).reversed()
                                .thenComparingInt(e -> cascadeLevelOf(e, country, account)))
                        .orElseThrow())
                .map(this::toDomain)
                .toList();
    }

    private static int cascadeLevelOf(FeeRuleEntity e, String country, String account) {
        boolean specificCountry = country != null && country.equals(e.getDestinationCountry());
        boolean specificAccount = account != null && account.equals(e.getAccountIdentification());
        if (specificCountry && specificAccount) return 1;
        if (specificCountry)                    return 2;
        if (specificAccount)                    return 3;
        return 4;
    }

    @Override
    public FeeRuleDetails save(FeeRuleDetails details) {
        FeeRuleEntity entity;
        if (details.id() != null) {
            entity = jpaRepo.findById(details.id())
                    .orElseThrow(() -> new FeeRuleNotFoundException(details.id()));
        } else {
            entity = new FeeRuleEntity();
        }
        entity.setPaymentType(details.paymentType());
        entity.setScheme(details.scheme());
        entity.setChargeBearer(details.chargeBearer());
        entity.setAccountIdentification(details.accountIdentification());
        entity.setDestinationCountry(details.destinationCountry());
        entity.setChargeType(details.chargeType());
        entity.setFeeType(details.feeType());
        entity.setFlatAmount(details.flatAmount());
        entity.setPercentage(details.percentage());
        entity.setMinFee(details.minFee());
        entity.setMaxFee(details.maxFee());
        entity.setTiers(toJsonNode(details.tiers()));
        entity.setCurrency(details.currency());
        entity.setPriority(details.priority());
        entity.setActive(details.active());
        return toDetails(jpaRepo.save(entity));
    }

    @Override
    public Optional<FeeRuleDetails> findById(UUID id) {
        return jpaRepo.findById(id).map(this::toDetails);
    }

    @Override
    public Page<FeeRuleDetails> findByFilters(String paymentType, String scheme, String chargeBearer,
                                              String feeType, String currency, String accountIdentification,
                                              Boolean active, Pageable pageable) {
        return jpaRepo.findByFilters(paymentType, scheme, chargeBearer, feeType,
                currency, accountIdentification, active, pageable)
                .map(this::toDetails);
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
                e.getMinFee(), e.getMaxFee(),
                tiers, e.getCurrency(), e.getDestinationCountry(),
                e.getPriority());
    }

    private FeeRuleDetails toDetails(FeeRuleEntity e) {
        List<FeeRuleDetails.TierInfo> tiers = null;
        if (e.getTiers() != null) {
            List<Map<String, Object>> raw = objectMapper.convertValue(
                    e.getTiers(), new TypeReference<>() {});
            tiers = raw.stream().map(m -> new FeeRuleDetails.TierInfo(
                    toBigDecimal(m.get("min")),
                    toBigDecimal(m.get("max")),
                    toBigDecimal(m.get("amount"))
            )).toList();
        }
        return new FeeRuleDetails(
                e.getId(), e.getPaymentType(), e.getScheme(), e.getChargeBearer(),
                e.getAccountIdentification(), e.getDestinationCountry(),
                e.getChargeType(), e.getFeeType(),
                e.getFlatAmount(), e.getPercentage(),
                e.getMinFee(), e.getMaxFee(),
                tiers, e.getCurrency(),
                e.getPriority(), e.isActive(), e.getVersion(),
                e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy());
    }

    private JsonNode toJsonNode(List<FeeRuleDetails.TierInfo> tiers) {
        if (tiers == null) return null;
        var list = tiers.stream().map(t ->
                Map.of("min", (Object) t.min(), "max", t.max(), "amount", t.amount())
        ).toList();
        return objectMapper.valueToTree(list);
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

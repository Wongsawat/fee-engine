package com.wpanther.pisp.fee.engine.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pisp.fee.engine.adapter.out.persistence.jpa.FeeRuleEntity;
import com.wpanther.pisp.fee.engine.domain.model.*;
import com.wpanther.pisp.fee.engine.support.FeeRuleEntityFixtures;
import com.wpanther.pisp.fee.engine.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({FeeRuleRepositoryAdapter.class, JacksonAutoConfiguration.class})
class FeeRuleRepositoryAdapterTest extends PostgresTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired FeeRuleRepositoryAdapter adapter;
    @Autowired com.wpanther.pisp.fee.engine.adapter.out.persistence.jpa.FeeRuleJpaRepository jpaRepo;

    @BeforeEach
    void clear() { jpaRepo.deleteAll(); }

    @Test
    void returnsMatchingFlatRule() {
        jpaRepo.save(FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null));

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor, "GBP", Optional.empty());

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getFeeType()).isEqualTo(FeeType.FLAT);
        assertThat(rules.get(0).getChargeType()).isEqualTo("CHARGEType001");
    }

    @Test
    void specificAccountRuleOverridesAnyAccountRule() {
        jpaRepo.save(FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null));
        jpaRepo.save(FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", "123"));

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor, "GBP", Optional.of("123"));

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getChargeType()).isEqualTo("CHARGEType001");
        assertThat(rules.get(0).getChargeBearer()).isEqualTo(ChargeBearer.BorneByDebtor);
    }

    @Test
    void returnsAnyAccountRuleWhenNoSpecificRuleForAccount() {
        jpaRepo.save(FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null));

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor, "GBP", Optional.of("UNKNOWN"));

        assertThat(rules).hasSize(1);
    }

    @Test
    void doesNotReturnRulesWithDifferentCurrency() {
        jpaRepo.save(FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null));

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor, "EUR", Optional.empty());

        assertThat(rules).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoRulesMatch() {
        List<FeeRule> rules = adapter.findMatching(
                PaymentType.FILE, PaymentScheme.BACS, ChargeBearer.BorneByDebtor, "GBP", Optional.empty());
        assertThat(rules).isEmpty();
    }

    @Test
    void ignoresInactiveRules() {
        var entity = FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null);
        entity.setActive(false);
        jpaRepo.save(entity);

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor, "GBP", Optional.empty());
        assertThat(rules).isEmpty();
    }

    @Test
    void throwsWhenTierHasMinGreaterThanMax() throws Exception {
        FeeRuleEntity entity = new FeeRuleEntity();
        entity.setId(UUID.randomUUID());
        entity.setPaymentType("DOMESTIC");
        entity.setScheme("FPS");
        entity.setChargeBearer("BorneByDebtor");
        entity.setChargeType("CHARGEType003");
        entity.setFeeType("TIERED");
        entity.setTiers(MAPPER.readTree("[{\"min\":1000,\"max\":100,\"amount\":0.50}]"));
        entity.setCurrency("GBP");
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        jpaRepo.save(entity);

        assertThatThrownBy(() -> adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor, "GBP", Optional.empty()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("min");
    }

    @Test
    void throwsWhenTierHasNonPositiveAmount() throws Exception {
        FeeRuleEntity entity = new FeeRuleEntity();
        entity.setId(UUID.randomUUID());
        entity.setPaymentType("DOMESTIC");
        entity.setScheme("FPS");
        entity.setChargeBearer("BorneByDebtor");
        entity.setChargeType("CHARGEType003");
        entity.setFeeType("TIERED");
        entity.setTiers(MAPPER.readTree("[{\"min\":0,\"max\":1000,\"amount\":-1}]"));
        entity.setCurrency("GBP");
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        jpaRepo.save(entity);

        assertThatThrownBy(() -> adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor, "GBP", Optional.empty()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("amount");
    }
}

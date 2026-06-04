package com.wpanther.pisp.fee.engine.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pisp.fee.engine.adapter.out.persistence.jpa.FeeRuleEntity;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleDetails;
import com.wpanther.pisp.fee.engine.domain.model.*;
import com.wpanther.pisp.fee.engine.infrastructure.security.AuditorAwareImpl;
import com.wpanther.pisp.fee.engine.support.FeeRuleEntityFixtures;
import com.wpanther.pisp.fee.engine.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({FeeRuleRepositoryAdapter.class, JacksonAutoConfiguration.class, AuditorAwareImpl.class})
class FeeRuleRepositoryAdapterTest extends PostgresTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired FeeRuleRepositoryAdapter adapter;
    @Autowired com.wpanther.pisp.fee.engine.adapter.out.persistence.jpa.FeeRuleJpaRepository jpaRepo;
    @Autowired jakarta.persistence.EntityManager entityManager;

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
    void returnsFreeRuleWithNoMonetaryFields() {
        jpaRepo.save(FeeRuleEntityFixtures.freeRule("DOMESTIC", "FPS", "BorneByDebtor"));

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor, "GBP", Optional.empty());

        assertThat(rules).hasSize(1);
        FeeRule rule = rules.get(0);
        assertThat(rule.getFeeType()).isEqualTo(FeeType.FREE);
        assertThat(rule.getChargeType()).isEqualTo("CHARGEType004");
        assertThat(rule.getFlatAmount()).isEmpty();
        assertThat(rule.getPercentage()).isEmpty();
        assertThat(rule.getTiers()).isEmpty();
    }

    @Test
    void rejectsFreeFeeRuleWithFlatAmount() {
        var entity = FeeRuleEntityFixtures.freeRule("DOMESTIC", "FPS", "BorneByDebtor");
        entity.setFlatAmount(new BigDecimal("1.00"));

        // saveAndFlush is required: @DataJpaTest wraps each test in a rolled-back transaction, so
        // Hibernate never auto-flushes unless a subsequent query forces it. save() alone queues the
        // INSERT in the first-level cache and it is never sent to Postgres; flush() sends the INSERT
        // immediately so PostgreSQL evaluates the check constraint before the rollback.
        assertThatThrownBy(() -> jpaRepo.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsFreeFeeRuleWithPercentage() {
        var entity = FeeRuleEntityFixtures.freeRule("DOMESTIC", "FPS", "BorneByDebtor");
        entity.setPercentage(new BigDecimal("0.01"));

        assertThatThrownBy(() -> jpaRepo.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsFreeFeeRuleWithTiers() throws Exception {
        var entity = FeeRuleEntityFixtures.freeRule("DOMESTIC", "FPS", "BorneByDebtor");
        entity.setTiers(MAPPER.readTree("[{\"min\":0,\"max\":1000,\"amount\":0.50}]"));

        assertThatThrownBy(() -> jpaRepo.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void throwsWhenTierHasMinGreaterThanMax() throws Exception {
        FeeRuleEntity entity = new FeeRuleEntity();
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
    void throwsWhenTierFieldIsNull() throws Exception {
        FeeRuleEntity entity = new FeeRuleEntity();
        entity.setPaymentType("DOMESTIC");
        entity.setScheme("FPS");
        entity.setChargeBearer("BorneByDebtor");
        entity.setChargeType("CHARGEType003");
        entity.setFeeType("TIERED");
        entity.setTiers(MAPPER.readTree("[{\"min\":0,\"max\":1000}]")); // missing "amount"
        entity.setCurrency("GBP");
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        jpaRepo.save(entity);

        assertThatThrownBy(() -> adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor, "GBP", Optional.empty()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CHARGEType003");
    }

    @Test
    void throwsWhenTierHasNonPositiveAmount() throws Exception {
        FeeRuleEntity entity = new FeeRuleEntity();
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

    @Test
    void optimisticLockingRejectsStaleUpdate() {
        var entity = FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null);
        jpaRepo.saveAndFlush(entity);

        // First update increments version to 1
        var copy1 = jpaRepo.findById(entity.getId()).orElseThrow();
        copy1.setFlatAmount(new BigDecimal("5.00"));
        jpaRepo.saveAndFlush(copy1);

        // Detach to get a stale snapshot, then force stale version
        var stale = jpaRepo.findById(entity.getId()).orElseThrow();
        entityManager.detach(stale);
        stale.setFlatAmount(new BigDecimal("10.00"));
        stale.setVersion(0);
        assertThatThrownBy(() -> jpaRepo.saveAndFlush(stale))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void savesAndRetrievesFeeRule() {
        var details = new FeeRuleDetails(
                null, "DOMESTIC", "FPS", "BorneByDebtor", null,
                "CHARGEType001", "FLAT", new BigDecimal("2.50"), null,
                null, null, null, "GBP", true, 0,
                null, null, null, null);

        FeeRuleDetails saved = adapter.save(details);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isEqualTo(0);
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.flatAmount()).isEqualByComparingTo("2.50");

        var found = adapter.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().flatAmount()).isEqualByComparingTo("2.50");
    }

    @Test
    void findByFiltersReturnsPaginatedResults() {
        jpaRepo.saveAndFlush(FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null));
        jpaRepo.saveAndFlush(FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "BACS", "BorneByDebtor", null));
        jpaRepo.saveAndFlush(FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null));

        var page = adapter.findByFilters("DOMESTIC", null, null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allSatisfy(d ->
                assertThat(d.paymentType()).isEqualTo("DOMESTIC"));
    }

    @Test
    void findByFiltersByAccountIdentification() {
        jpaRepo.saveAndFlush(FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", "ACC1"));
        jpaRepo.saveAndFlush(FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null));

        var page = adapter.findByFilters("DOMESTIC", "FPS", "BorneByDebtor", null, "GBP", "ACC1", null,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).accountIdentification()).isEqualTo("ACC1");
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void rejectsCapsOnFlatRule() {
        var entity = FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null);
        entity.setMinFee(new BigDecimal("1.00"));

        assertThatThrownBy(() -> jpaRepo.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMinFeeGreaterThanMaxFee() {
        var entity = FeeRuleEntityFixtures.percentageFeeRule("DOMESTIC", "FPS", "BorneByDebtor");
        entity.setMinFee(new BigDecimal("50.00"));
        entity.setMaxFee(new BigDecimal("1.00"));

        assertThatThrownBy(() -> jpaRepo.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNonPositiveMinFee() {
        var entity = FeeRuleEntityFixtures.percentageFeeRule("DOMESTIC", "FPS", "BorneByDebtor");
        entity.setMinFee(BigDecimal.ZERO);

        assertThatThrownBy(() -> jpaRepo.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNonPositiveMaxFee() {
        var entity = FeeRuleEntityFixtures.percentageFeeRule("DOMESTIC", "FPS", "BorneByDebtor");
        entity.setMaxFee(BigDecimal.ZERO);

        assertThatThrownBy(() -> jpaRepo.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsValidCappedPercentageRule() {
        var entity = FeeRuleEntityFixtures.percentageFeeRule("DOMESTIC", "FPS", "BorneByDebtor");
        entity.setMinFee(new BigDecimal("1.00"));
        entity.setMaxFee(new BigDecimal("50.00"));
        jpaRepo.saveAndFlush(entity);

        var found = jpaRepo.findById(entity.getId()).orElseThrow();
        assertThat(found.getMinFee()).isEqualByComparingTo("1.00");
        assertThat(found.getMaxFee()).isEqualByComparingTo("50.00");
    }

    @Test
    void savesAndRetrievesCapsViaAdapter() {
        var details = new FeeRuleDetails(
                null, "DOMESTIC", "FPS", "BorneByDebtor", null,
                "CHARGEType002", "PERCENTAGE", null, new BigDecimal("0.01"),
                new BigDecimal("1.00"), new BigDecimal("50.00"), null, "GBP", true, 0,
                null, null, null, null);

        FeeRuleDetails saved = adapter.save(details);
        var found = adapter.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().minFee()).isEqualByComparingTo("1.00");
        assertThat(found.get().maxFee()).isEqualByComparingTo("50.00");
    }

    @Test
    void findMatchingReturnsCapsOnDomainRule() {
        var e = FeeRuleEntityFixtures.percentageFeeRule("DOMESTIC", "FPS", "BorneByDebtor");
        e.setMinFee(new BigDecimal("1.00"));
        e.setMaxFee(new BigDecimal("50.00"));
        jpaRepo.saveAndFlush(e);

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor, "GBP", Optional.empty());

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getMinFee()).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("1.00"));
        assertThat(rules.get(0).getMaxFee()).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("50.00"));
    }

    @Test
    void rejectsLowercaseDestinationCountry() {
        var entity = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        entity.setDestinationCountry("in");

        assertThatThrownBy(() -> jpaRepo.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsThreeLetterDestinationCountry() {
        var entity = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        entity.setDestinationCountry("GBR");

        assertThatThrownBy(() -> jpaRepo.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDestinationCountryOnDomesticRule() {
        var entity = FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null);
        entity.setDestinationCountry("IN");

        assertThatThrownBy(() -> jpaRepo.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateActiveCountryRule() {
        var first = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", "ACC1");
        first.setDestinationCountry("IN");
        jpaRepo.saveAndFlush(first);

        var second = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", "ACC1");
        second.setDestinationCountry("IN");

        assertThatThrownBy(() -> jpaRepo.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsValidDestinationCountryOnInternationalRule() {
        var entity = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        entity.setDestinationCountry("IN");
        jpaRepo.saveAndFlush(entity);

        var found = jpaRepo.findById(entity.getId()).orElseThrow();
        assertThat(found.getDestinationCountry()).isEqualTo("IN");
    }
}

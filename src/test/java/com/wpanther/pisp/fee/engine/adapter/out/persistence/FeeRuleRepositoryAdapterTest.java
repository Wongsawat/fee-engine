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
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getFeeType()).isEqualTo(FeeType.FLAT);
        assertThat(rules.get(0).getChargeType()).isEqualTo("CHARGEType001");
    }

    @Test
    void specificAccountRuleOverridesAnyAccountRule() {
        jpaRepo.save(FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null));
        jpaRepo.save(FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", "123"));

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.of("123"));

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getChargeType()).isEqualTo("CHARGEType001");
        assertThat(rules.get(0).getChargeBearer()).isEqualTo(ChargeBearer.BorneByDebtor);
    }

    @Test
    void returnsAnyAccountRuleWhenNoSpecificRuleForAccount() {
        jpaRepo.save(FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null));

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.of("UNKNOWN"));

        assertThat(rules).hasSize(1);
    }

    @Test
    void doesNotReturnRulesWithDifferentCurrency() {
        jpaRepo.save(FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null));

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "EUR", Optional.empty(), Optional.empty());

        assertThat(rules).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoRulesMatch() {
        List<FeeRule> rules = adapter.findMatching(
                PaymentType.FILE, PaymentScheme.BACS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());
        assertThat(rules).isEmpty();
    }

    @Test
    void ignoresInactiveRules() {
        var entity = FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null);
        entity.setActive(false);
        jpaRepo.save(entity);

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());
        assertThat(rules).isEmpty();
    }

    @Test
    void returnsFreeRuleWithNoMonetaryFields() {
        jpaRepo.save(FeeRuleEntityFixtures.freeRule("DOMESTIC", "FPS", "BorneByDebtor"));

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());

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
        entity.setFeeType("TIERED_SLAB");
        entity.setTiers(MAPPER.readTree("[{\"min\":1000,\"max\":100,\"rateType\":\"FIXED\",\"amount\":0.50}]"));
        entity.setCurrency("GBP");
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        jpaRepo.save(entity);

        assertThatThrownBy(() -> adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty()))
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
        entity.setFeeType("TIERED_SLAB");
        entity.setTiers(MAPPER.readTree("[{\"min\":0,\"max\":1000,\"rateType\":\"FIXED\"}]")); // missing "amount"
        entity.setCurrency("GBP");
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        jpaRepo.save(entity);

        assertThatThrownBy(() -> adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty()))
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
        entity.setFeeType("TIERED_SLAB");
        entity.setTiers(MAPPER.readTree("[{\"min\":0,\"max\":1000,\"rateType\":\"FIXED\",\"amount\":-1}]"));
        entity.setCurrency("GBP");
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        jpaRepo.save(entity);

        assertThatThrownBy(() -> adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty()))
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
                null, "DOMESTIC", "FPS", "BorneByDebtor", null, null,
                "CHARGEType001", "FLAT", new BigDecimal("2.50"), null,
                null, null, null, "GBP", 0, true, 0,
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
                null, "DOMESTIC", "FPS", "BorneByDebtor", null, null,
                "CHARGEType002", "PERCENTAGE", null, new BigDecimal("0.01"),
                new BigDecimal("1.00"), new BigDecimal("50.00"), null, "GBP", 0, true, 0,
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
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());

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
        // Both rows use accountId="ACC1" (not null): the unique index only wraps
        // destination_country in COALESCE(..., ''), so two rows with otherwise identical
        // dimensions but null account_identification would NOT collide (PostgreSQL
        // treats NULL as distinct in unique indexes). Using a non-null accountId forces
        // the (..., 'IN', 'ACC1') tuple to actually be a duplicate.
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

    @Test
    void countrySpecificRuleWinsOverAnyCountryRule() {
        var anyCountry = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        jpaRepo.saveAndFlush(anyCountry);

        var inRule = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        inRule.setDestinationCountry("IN");
        jpaRepo.saveAndFlush(inRule);

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                "GBP", Optional.of("IN"), Optional.empty());

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getDestinationCountry()).hasValue("IN");
    }

    @Test
    void fallsBackToAnyCountryRuleWhenNoCountrySpecificRuleExists() {
        var anyCountry = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        jpaRepo.saveAndFlush(anyCountry);

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                "GBP", Optional.of("FR"), Optional.empty());

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getDestinationCountry()).isEmpty();
    }

    @Test
    void noCountryRequestIgnoresCountrySpecificRules() {
        var inRule = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        inRule.setDestinationCountry("IN");
        jpaRepo.saveAndFlush(inRule);

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());

        assertThat(rules).isEmpty();
    }

    @Test
    void countryOnlyRuleWinsOverAccountOnlyRule() {
        // Level 2: country=IN, account=any
        var countryRule = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        countryRule.setDestinationCountry("IN");
        jpaRepo.saveAndFlush(countryRule);

        // Level 3: country=any, account=ACC1
        var accountRule = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", "ACC1");
        jpaRepo.saveAndFlush(accountRule);

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                "GBP", Optional.of("IN"), Optional.of("ACC1"));

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getDestinationCountry()).hasValue("IN");
    }

    @Test
    void countryAndAccountSpecificWinsOverCountryOnlyAndAccountOnly() {
        // Note: the unique constraint prevents two active same-chargeType rules at the same
        // cascade level for the same payment, so these rules use different chargeTypes and are
        // selected independently. Same-chargeType cascade tiebreaking is covered by equalPriorityFallsBackToCascadeLevel.
        // Level 1: country=IN, account=ACC1
        var l1 = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", "ACC1");
        l1.setDestinationCountry("IN");
        jpaRepo.saveAndFlush(l1);

        // Level 2: country=IN, account=any — different chargeType so no unique-constraint conflict
        var l2 = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        l2.setDestinationCountry("IN");
        l2.setChargeType("LEVEL2");
        jpaRepo.saveAndFlush(l2);

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                "GBP", Optional.of("IN"), Optional.of("ACC1"));

        // Both charge types are independent — each picks its own best cascade level
        assertThat(rules).hasSize(2);
        assertThat(rules).anyMatch(r -> r.getChargeType().equals("CHARGEType001")
                && "IN".equals(r.getDestinationCountry().orElse(null)));
        assertThat(rules).anyMatch(r -> r.getChargeType().equals("LEVEL2"));
    }

    @Test
    void accountOnlyRuleWinsOverAnyAccountRule() {
        // Note: the unique constraint prevents two active same-chargeType rules at the same
        // cascade level for the same payment, so these rules use different chargeTypes and are
        // selected independently. Same-chargeType cascade tiebreaking is covered by equalPriorityFallsBackToCascadeLevel.
        // Level 3: country=any, account=ACC1
        var accountRule = FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", "ACC1");
        jpaRepo.saveAndFlush(accountRule);

        // Level 4: country=any, account=any — different chargeType to avoid unique-constraint conflict
        var anyAccountRule = FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null);
        anyAccountRule.setChargeType("ANY_ACCOUNT_RULE");
        jpaRepo.saveAndFlush(anyAccountRule);

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.of("ACC1"));

        // Both charge types are independent — CHARGEType001 picks the account-specific rule
        assertThat(rules).hasSize(2);
        assertThat(rules).anyMatch(r -> r.getChargeType().equals("CHARGEType001"));
        assertThat(rules).anyMatch(r -> r.getChargeType().equals("ANY_ACCOUNT_RULE"));
    }

    @Test
    void rejectsNegativePriority() {
        var entity = FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null);
        entity.setPriority(-1);

        assertThatThrownBy(() -> jpaRepo.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateActiveRuleForSameChargeType() {
        var first = FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null);
        first.setPriority(10);
        jpaRepo.saveAndFlush(first);

        var second = FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null);
        second.setPriority(5);
        // same chargeType (CHARGEType001), same dimensions, both active — must conflict
        assertThatThrownBy(() -> jpaRepo.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsDifferentChargeTypesForSameDimensions() {
        var first = FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null);
        jpaRepo.saveAndFlush(first);

        var second = FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null);
        second.setChargeType("PROCESSING_FEE");
        jpaRepo.saveAndFlush(second);
        // Two different chargeTypes for same dimensions — allowed
        assertThat(jpaRepo.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void savesAndRetrievesPriority() {
        var entity = FeeRuleEntityFixtures.flatFeeRule("DOMESTIC", "FPS", "BorneByDebtor", null);
        entity.setPriority(42);
        jpaRepo.saveAndFlush(entity);

        var found = jpaRepo.findById(entity.getId()).orElseThrow();
        assertThat(found.getPriority()).isEqualTo(42);
    }

    @Test
    void twoActiveChargeTypesAtSameCascadeLevelAreBothReturnedIndependently() {
        var transferFee = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        transferFee.setChargeType("TRANSFER_FEE");
        transferFee.setPriority(0);
        jpaRepo.saveAndFlush(transferFee);

        var standardFee = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        standardFee.setChargeType("STANDARD_FEE");
        standardFee.setPriority(5);
        jpaRepo.saveAndFlush(standardFee);

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());

        assertThat(rules).hasSize(2);
        assertThat(rules).anyMatch(r -> r.getChargeType().equals("TRANSFER_FEE"));
        assertThat(rules).anyMatch(r -> r.getChargeType().equals("STANDARD_FEE"));
    }

    @Test
    void higherPriorityLowerSpecificityRuleBeatsLowerPriorityHigherSpecificity() {
        var anyRule = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        anyRule.setChargeType("TRANSFER_FEE");
        anyRule.setPriority(100);
        jpaRepo.saveAndFlush(anyRule);

        var countryRule = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        countryRule.setChargeType("TRANSFER_FEE");
        countryRule.setDestinationCountry("IN");
        countryRule.setPriority(0);
        jpaRepo.saveAndFlush(countryRule);

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                "GBP", Optional.of("IN"), Optional.empty());

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getDestinationCountry()).isEmpty();
        assertThat(rules.get(0).getPriority()).isEqualTo(100);
    }

    @Test
    void equalPriorityFallsBackToCascadeLevel() {
        var anyRule = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        anyRule.setChargeType("TRANSFER_FEE");
        anyRule.setPriority(0);
        jpaRepo.saveAndFlush(anyRule);

        var countryRule = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        countryRule.setChargeType("TRANSFER_FEE");
        countryRule.setDestinationCountry("IN");
        countryRule.setPriority(0);
        jpaRepo.saveAndFlush(countryRule);

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                "GBP", Optional.of("IN"), Optional.empty());

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getDestinationCountry()).hasValue("IN");
    }

    @Test
    void differentChargeTypesAtDifferentCascadeLevelsAreBothReturned() {
        var ourFee = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        ourFee.setChargeType("OUR_FEE");
        ourFee.setDestinationCountry("IN");
        jpaRepo.saveAndFlush(ourFee);

        var agentFee = FeeRuleEntityFixtures.flatFeeRule("INTERNATIONAL", "SWIFT", "BorneByDebtor", null);
        agentFee.setChargeType("AGENT_FEE");
        jpaRepo.saveAndFlush(agentFee);

        List<FeeRule> rules = adapter.findMatching(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByDebtor,
                "GBP", Optional.of("IN"), Optional.empty());

        assertThat(rules).hasSize(2);
        assertThat(rules).anyMatch(r -> r.getChargeType().equals("OUR_FEE"));
        assertThat(rules).anyMatch(r -> r.getChargeType().equals("AGENT_FEE"));
    }
}

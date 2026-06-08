package com.wpanther.pisp.fee.engine.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleDetails;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleRepository;
import com.wpanther.pisp.fee.engine.domain.model.ChargeBearer;
import com.wpanther.pisp.fee.engine.domain.model.FeeRule;
import com.wpanther.pisp.fee.engine.domain.model.PaymentScheme;
import com.wpanther.pisp.fee.engine.domain.model.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Caching decorator for {@link FeeRuleRepository}.
 * <p>
 * Caches {@link #findMatching} results in an in-process Caffeine cache.
 * {@link #save} invalidates the cache after the JPA transaction commits
 * (via {@link TransactionSynchronization#afterCommit()}) to prevent
 * stale-repopulation races.
 * <p>
 * Admin read operations ({@link #findById}, {@link #findByFilters}) pass through
 * uncached — they return version/audit fields that must always be fresh.
 */
public class CachingFeeRuleRepository implements FeeRuleRepository {

    private final FeeRuleRepository delegate;
    private final Cache<MatchingRuleKey, List<FeeRule>> cache;

    public CachingFeeRuleRepository(FeeRuleRepository delegate,
                                    Cache<MatchingRuleKey, List<FeeRule>> cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public List<FeeRule> findMatching(PaymentType paymentType, PaymentScheme scheme,
                                      ChargeBearer chargeBearer, String currency,
                                      Optional<String> destinationCountry,
                                      Optional<String> accountIdentification) {
        String normalizedCurrency = normalize(currency);
        String normalizedCountry = normalize(destinationCountry.orElse(null));
        String normalizedAccount = normalize(accountIdentification.orElse(null));

        MatchingRuleKey key = new MatchingRuleKey(
                paymentType, scheme, chargeBearer,
                normalizedCurrency, normalizedCountry, normalizedAccount);

        return cache.get(key, k -> delegate.findMatching(
                paymentType, scheme, chargeBearer, normalizedCurrency,
                Optional.ofNullable(normalizedCountry),
                Optional.ofNullable(normalizedAccount)));
    }

    @Override
    public FeeRuleDetails save(FeeRuleDetails details) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            cache.invalidateAll();
                        }
                    });
        } else {
            cache.invalidateAll();
        }
        return delegate.save(details);
    }

    @Override
    public Optional<FeeRuleDetails> findById(UUID id) {
        return delegate.findById(id);
    }

    @Override
    public Page<FeeRuleDetails> findByFilters(String paymentType, String scheme,
                                               String chargeBearer, String feeType,
                                               String currency, String accountIdentification,
                                               Boolean active, Pageable pageable) {
        return delegate.findByFilters(paymentType, scheme, chargeBearer, feeType,
                currency, accountIdentification, active, pageable);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}

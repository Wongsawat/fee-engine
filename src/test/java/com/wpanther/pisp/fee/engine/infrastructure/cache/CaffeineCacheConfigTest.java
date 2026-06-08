package com.wpanther.pisp.fee.engine.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import com.wpanther.pisp.fee.engine.domain.model.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineCacheConfigTest {

    private Cache<MatchingRuleKey, List<FeeRule>> cache;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder().recordStats().build();
        registry = new SimpleMeterRegistry();
    }

    @Test
    void invalidateOnStartup_clearsCacheOnApplicationReady() {
        // Pre-populate cache
        var key = new MatchingRuleKey(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", null, null);
        cache.put(key, List.of(new FeeRule("SERVICE_FEE", ChargeBearer.BorneByDebtor,
                FeeType.FLAT, new BigDecimal("1.50"), null, null, null,
                List.of(), "GBP", null, 1)));
        assertThat(cache.asMap()).hasSize(1);

        // Call the actual config method — exercises the same code path as the @EventListener
        new CaffeineCacheConfig().invalidateOnStartup(cache);

        assertThat(cache.asMap()).isEmpty();
    }

    @Test
    void meterBinderRegistersMetrics() {
        // Create and bind the MeterBinder (mirrors the config bean)
        MeterBinder binder = reg -> CaffeineCacheMetrics.monitor(reg, cache, "feeRuleMatching");
        binder.bindTo(registry);

        // Trigger a cache miss so stats are recorded
        var key = new MatchingRuleKey(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", null, null);
        cache.get(key, k -> List.of());

        // Verify meters exist
        assertThat(registry.find("cache.gets").tag("cache", "feeRuleMatching").meters())
                .isNotEmpty();
    }
}

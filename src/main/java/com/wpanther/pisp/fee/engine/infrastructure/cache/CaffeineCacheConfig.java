package com.wpanther.pisp.fee.engine.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wpanther.pisp.fee.engine.domain.model.FeeRule;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnProperty(name = "fee.cache.enabled", havingValue = "true", matchIfMissing = true)
public class CaffeineCacheConfig {

    private final Cache<MatchingRuleKey, List<FeeRule>> cache;

    public CaffeineCacheConfig(
            @Lazy @Qualifier("feeRuleMatchingCache") Cache<MatchingRuleKey, List<FeeRule>> cache) {
        this.cache = cache;
    }

    @Bean("feeRuleMatchingCache")
    public Cache<MatchingRuleKey, List<FeeRule>> feeRuleMatchingCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    @Bean
    public MeterBinder feeRuleCacheMetrics() {
        return registry -> CaffeineCacheMetrics.monitor(registry, cache, "feeRuleMatching");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void invalidateOnStartup() {
        cache.invalidateAll();
    }
}

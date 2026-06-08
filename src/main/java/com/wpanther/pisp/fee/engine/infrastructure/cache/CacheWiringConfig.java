package com.wpanther.pisp.fee.engine.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.wpanther.pisp.fee.engine.adapter.out.persistence.FeeRuleRepositoryAdapter;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleDetails;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleRepository;
import com.wpanther.pisp.fee.engine.domain.model.FeeRule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CacheWiringConfig {

    @Bean
    @ConditionalOnBean(name = "feeRuleMatchingCache")
    public FeeRuleRepository feeRuleRepository(
            FeeRuleRepositoryAdapter adapter,
            @Qualifier("feeRuleMatchingCache") Cache<MatchingRuleKey, List<FeeRule>> cache) {
        return new CachingFeeRuleRepository(adapter, cache);
    }

    @Bean
    @ConditionalOnMissingBean(FeeRuleRepository.class)
    public FeeRuleRepository uncachedFeeRuleRepository(FeeRuleRepositoryAdapter adapter) {
        return adapter;
    }
}

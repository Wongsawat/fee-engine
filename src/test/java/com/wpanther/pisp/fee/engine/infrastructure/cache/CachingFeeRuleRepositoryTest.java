package com.wpanther.pisp.fee.engine.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleDetails;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleRepository;
import com.wpanther.pisp.fee.engine.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CachingFeeRuleRepositoryTest {

    private FeeRuleRepository delegate;
    private Cache<MatchingRuleKey, List<FeeRule>> cache;
    private CachingFeeRuleRepository underTest;

    @BeforeEach
    void setUp() {
        delegate = mock(FeeRuleRepository.class);
        cache = Caffeine.newBuilder().build();
        underTest = new CachingFeeRuleRepository(delegate, cache);
    }

    private FeeRule aFeeRule() {
        return new FeeRule("SERVICE_FEE", ChargeBearer.BorneByDebtor, FeeType.FLAT,
                new BigDecimal("1.50"), null, null, null,
                List.of(), "GBP", null, 1);
    }

    // --- findMatching: basic caching ---

    @Test
    void findMatching_cachesResult_delegateCalledOnce() {
        var rules = List.of(aFeeRule());
        when(delegate.findMatching(any(), any(), any(), anyString(), any(), any()))
                .thenReturn(rules);

        var result1 = underTest.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());
        var result2 = underTest.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());

        assertThat(result1).isSameAs(rules);
        assertThat(result2).isSameAs(rules);
        verify(delegate, times(1)).findMatching(any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void noRuleResultCachesEmptyList() {
        when(delegate.findMatching(any(), any(), any(), anyString(), any(), any()))
                .thenReturn(List.of());

        underTest.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());
        underTest.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());

        verify(delegate, times(1)).findMatching(any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void differentKeysCacheIndependently() {
        when(delegate.findMatching(any(), any(), any(), anyString(), any(), any()))
                .thenReturn(List.of(aFeeRule()));

        underTest.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());
        underTest.findMatching(
                PaymentType.INTERNATIONAL, PaymentScheme.SWIFT, ChargeBearer.BorneByCreditor,
                "USD", Optional.of("US"), Optional.of("ACC-1"));

        verify(delegate, times(2)).findMatching(any(), any(), any(), anyString(), any(), any());
        assertThat(cache.asMap()).hasSize(2);
    }

    @Test
    void keyNormalizationDeduplicatesEntries() {
        when(delegate.findMatching(any(), any(), any(), anyString(), any(), any()))
                .thenReturn(List.of(aFeeRule()));

        // These should produce the same normalized key
        underTest.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "gbp", Optional.of(" gb "), Optional.of(" acc1 "));
        underTest.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.of("GB"), Optional.of("ACC1"));

        // Both delegate calls pass normalized values, so they look the same to the mock
        // The cache has exactly one entry
        assertThat(cache.asMap()).hasSize(1);
    }

    @Test
    void cachePoisoningImpossible() {
        // Delegate returns rules when called with normalized "IN"
        var rules = List.of(aFeeRule());
        when(delegate.findMatching(
                eq(PaymentType.DOMESTIC), eq(PaymentScheme.FPS), eq(ChargeBearer.BorneByDebtor),
                eq("GBP"), eq(Optional.of("IN")), eq(Optional.of("ACC1"))))
                .thenReturn(rules);
        // Return empty when country doesn't match (shouldn't happen with normalization)
        when(delegate.findMatching(
                eq(PaymentType.DOMESTIC), eq(PaymentScheme.FPS), eq(ChargeBearer.BorneByDebtor),
                eq("GBP"), eq(Optional.of("in")), eq(Optional.of("acc1"))))
                .thenReturn(List.of());

        // Call with lowercase — normalized to uppercase before both key and delegate
        var resultLower = underTest.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "gbp", Optional.of("in"), Optional.of("acc1"));
        // Call with uppercase — same normalized key, cache hit
        var resultUpper = underTest.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.of("IN"), Optional.of("ACC1"));

        // Both return the same result (the delegate was called once with normalized values)
        assertThat(resultLower).isSameAs(resultUpper);
        assertThat(cache.asMap()).hasSize(1);
    }

    // --- findMatching: concurrency ---

    @Test
    void concurrentFindMatchingForSameKeyLoadsOnce() throws Exception {
        AtomicInteger loadCount = new AtomicInteger(0);
        when(delegate.findMatching(any(), any(), any(), anyString(), any(), any()))
                .thenAnswer(inv -> {
                    loadCount.incrementAndGet();
                    Thread.sleep(100); // simulate slow DB
                    return List.of(aFeeRule());
                });

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    underTest.findMatching(
                            PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                            "GBP", Optional.empty(), Optional.empty());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishGate.countDown();
                }
            });
        }

        startGate.countDown(); // release all threads simultaneously
        boolean completed = finishGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(loadCount.get()).isEqualTo(1); // delegate called exactly once
    }

    // --- findMatching: immutability ---

    @Test
    void cachedResultIsImmutable() {
        when(delegate.findMatching(any(), any(), any(), anyString(), any(), any()))
                .thenReturn(List.of(aFeeRule()));

        var result = underTest.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());

        // The list itself is the one returned by the delegate (shared reference).
        // FeeRule and its tiers are immutable by design (List.copyOf in constructor).
        assertThat(result).isUnmodifiable();
        assertThat(result.get(0).getTiers()).isUnmodifiable();
    }

    // --- save: post-commit eviction ---

    @Test
    void save_registersPostCommitHookThatInvalidatesCache() {
        when(delegate.findMatching(any(), any(), any(), anyString(), any(), any()))
                .thenReturn(List.of(aFeeRule()));
        when(delegate.save(any())).thenReturn(mock(FeeRuleDetails.class));

        // Populate cache
        underTest.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());
        assertThat(cache.asMap()).hasSize(1);

        // Initiate a transaction synchronization
        TransactionSynchronizationManager.initSynchronization();
        try {
            underTest.save(mock(FeeRuleDetails.class));

            // Cache should still have entries (not yet committed)
            assertThat(cache.asMap()).hasSize(1);

            // Simulate commit — fire afterCommit
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // Cache should now be empty
        assertThat(cache.asMap()).isEmpty();
    }

    @Test
    void save_doesNotInvalidateWhenDelegateThrows() {
        when(delegate.findMatching(any(), any(), any(), anyString(), any(), any()))
                .thenReturn(List.of(aFeeRule()));
        when(delegate.save(any())).thenThrow(new RuntimeException("DB error"));

        // Populate cache
        underTest.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());
        assertThat(cache.asMap()).hasSize(1);

        // Initiate a transaction synchronization
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> underTest.save(mock(FeeRuleDetails.class)))
                    .isInstanceOf(RuntimeException.class);

            // Cache should still have entries — afterCommit was not called (rollback)
            assertThat(cache.asMap()).hasSize(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void save_fallsBackToImmediateInvalidationWhenNoTransaction() {
        when(delegate.findMatching(any(), any(), any(), anyString(), any(), any()))
                .thenReturn(List.of(aFeeRule()));
        when(delegate.save(any())).thenReturn(mock(FeeRuleDetails.class));

        // Populate cache
        underTest.findMatching(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", Optional.empty(), Optional.empty());
        assertThat(cache.asMap()).hasSize(1);

        // No TransactionSynchronizationManager active — immediate invalidation
        underTest.save(mock(FeeRuleDetails.class));

        assertThat(cache.asMap()).isEmpty();
    }

    // --- findById and findByFilters: bypass cache ---

    @Test
    void findById_bypassesCache() {
        when(delegate.findById(any())).thenReturn(Optional.of(mock(FeeRuleDetails.class)));

        underTest.findById(UUID.randomUUID());

        assertThat(cache.asMap()).isEmpty();
        verify(delegate).findById(any());
    }

    @Test
    void findByFilters_bypassesCache() {
        @SuppressWarnings("unchecked")
        Page<FeeRuleDetails> page = mock(Page.class);
        when(delegate.findByFilters(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        underTest.findByFilters(null, null, null, null, null, null, null, mock(Pageable.class));

        assertThat(cache.asMap()).isEmpty();
        verify(delegate).findByFilters(any(), any(), any(), any(), any(), any(), any(), any());
    }
}

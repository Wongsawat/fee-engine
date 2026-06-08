package com.wpanther.pisp.fee.engine.infrastructure.cache;

import com.wpanther.pisp.fee.engine.domain.model.ChargeBearer;
import com.wpanther.pisp.fee.engine.domain.model.PaymentScheme;
import com.wpanther.pisp.fee.engine.domain.model.PaymentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingRuleKeyTest {

    @Test
    void equalKeysHaveSameHashCode() {
        var key1 = new MatchingRuleKey(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", "GB", "ACC-001");
        var key2 = new MatchingRuleKey(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", "GB", "ACC-001");
        assertThat(key1).isEqualTo(key2);
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    }

    @Test
    void differentParamsAreNotEqual() {
        var key1 = new MatchingRuleKey(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", "GB", "ACC-001");
        var key2 = new MatchingRuleKey(
                PaymentType.INTERNATIONAL, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", "GB", "ACC-001");
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void nullOptionalFieldsAreEqual() {
        var key1 = new MatchingRuleKey(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", null, null);
        var key2 = new MatchingRuleKey(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", null, null);
        assertThat(key1).isEqualTo(key2);
    }

    @Test
    void toStringMasksAccountIdentification() {
        var key = new MatchingRuleKey(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", "GB", "ACC-00123");
        String str = key.toString();
        assertThat(str).contains("****0123");
        assertThat(str).doesNotContain("ACC-00123");
    }

    @Test
    void toStringMasksShortAccount() {
        var key = new MatchingRuleKey(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", "GB", "AB");
        String str = key.toString();
        assertThat(str).contains("****");
        assertThat(str).doesNotContain("AB");
    }

    @Test
    void toStringHandlesNullAccount() {
        var key = new MatchingRuleKey(
                PaymentType.DOMESTIC, PaymentScheme.FPS, ChargeBearer.BorneByDebtor,
                "GBP", "GB", null);
        String str = key.toString();
        assertThat(str).contains("acc=null");
    }
}

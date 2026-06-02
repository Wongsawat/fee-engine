package com.wpanther.pisp.fee.engine.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pisp.fee.engine.adapter.out.persistence.jpa.FeeRuleEntity;
import java.math.BigDecimal;
import java.time.Instant;

public class FeeRuleEntityFixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static FeeRuleEntity flatFeeRule(String paymentType, String scheme,
                                             String chargeBearer, String accountId) {
        var e = new FeeRuleEntity();
        e.setPaymentType(paymentType);
        e.setScheme(scheme);
        e.setChargeBearer(chargeBearer);
        e.setAccountIdentification(accountId);
        e.setChargeType("CHARGEType001");
        e.setFeeType("FLAT");
        e.setFlatAmount(new BigDecimal("1.50"));
        e.setCurrency("GBP");
        e.setActive(true);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    public static FeeRuleEntity percentageFeeRule(String paymentType, String scheme,
                                                   String chargeBearer) {
        var e = new FeeRuleEntity();
        e.setPaymentType(paymentType);
        e.setScheme(scheme);
        e.setChargeBearer(chargeBearer);
        e.setAccountIdentification(null);
        e.setChargeType("CHARGEType002");
        e.setFeeType("PERCENTAGE");
        e.setPercentage(new BigDecimal("0.002"));
        e.setCurrency("GBP");
        e.setActive(true);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    public static FeeRuleEntity tieredFeeRule(String paymentType, String scheme,
                                               String chargeBearer) throws Exception {
        var e = new FeeRuleEntity();
        e.setPaymentType(paymentType);
        e.setScheme(scheme);
        e.setChargeBearer(chargeBearer);
        e.setAccountIdentification(null);
        e.setChargeType("CHARGEType003");
        e.setFeeType("TIERED");
        e.setTiers(MAPPER.readTree(
            "[{\"min\":0,\"max\":1000,\"amount\":0.50}," +
            " {\"min\":1000,\"max\":999999999,\"amount\":2.00}]"));
        e.setCurrency("GBP");
        e.setActive(true);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    public static FeeRuleEntity freeRule(String paymentType, String scheme, String chargeBearer) {
        var e = new FeeRuleEntity();
        e.setPaymentType(paymentType);
        e.setScheme(scheme);
        e.setChargeBearer(chargeBearer);
        e.setAccountIdentification(null);
        e.setChargeType("CHARGEType004");
        e.setFeeType("FREE");
        e.setCurrency("GBP");
        e.setActive(true);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }
}

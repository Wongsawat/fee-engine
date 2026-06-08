package com.wpanther.pisp.fee.engine.infrastructure.cache;

import com.wpanther.pisp.fee.engine.domain.model.ChargeBearer;
import com.wpanther.pisp.fee.engine.domain.model.PaymentScheme;
import com.wpanther.pisp.fee.engine.domain.model.PaymentType;

/**
 * Cache key for fee-rule matching lookups.
 * String components (currency, destinationCountry, accountIdentification) are
 * expected to be pre-normalized (trimmed, uppercased) by the caller.
 */
public record MatchingRuleKey(
        PaymentType paymentType,
        PaymentScheme scheme,
        ChargeBearer chargeBearer,
        String currency,
        String destinationCountry,
        String accountIdentification
) {
    @Override
    public String toString() {
        return "MatchingRuleKey[pt=%s, sc=%s, cb=%s, cur=%s, c=%s, acc=%s]".formatted(
                paymentType, scheme, chargeBearer, currency,
                destinationCountry,
                maskAccount(accountIdentification));
    }

    private static String maskAccount(String account) {
        if (account == null) return "null";
        if (account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}

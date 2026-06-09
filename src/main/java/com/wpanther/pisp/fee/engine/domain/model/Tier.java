package com.wpanther.pisp.fee.engine.domain.model;

import java.math.BigDecimal;
import java.util.Optional;

public final class Tier {
    private final BigDecimal min;
    private final BigDecimal max;
    private final TierRateType rateType;
    private final BigDecimal amount;
    private final BigDecimal percentage;

    public Tier(BigDecimal min, BigDecimal max, TierRateType rateType,
                BigDecimal amount, BigDecimal percentage) {
        this.min = min;
        this.max = max;
        this.rateType = rateType;
        this.amount = amount;
        this.percentage = percentage;
    }

    /** @deprecated use 5-arg constructor */
    @Deprecated
    public Tier(BigDecimal min, BigDecimal max, BigDecimal amount) {
        this(min, max, TierRateType.FIXED, amount, null);
    }

    public BigDecimal getMin()                      { return min; }
    public BigDecimal getMax()                      { return max; }
    public TierRateType getRateType()               { return rateType; }
    public Optional<BigDecimal> getAmount()         { return Optional.ofNullable(amount); }
    public Optional<BigDecimal> getPercentage()     { return Optional.ofNullable(percentage); }
}

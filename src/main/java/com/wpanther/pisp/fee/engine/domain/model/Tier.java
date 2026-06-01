package com.wpanther.pisp.fee.engine.domain.model;

import java.math.BigDecimal;

public final class Tier {
    private final BigDecimal min;
    private final BigDecimal max;
    private final BigDecimal amount;

    public Tier(BigDecimal min, BigDecimal max, BigDecimal amount) {
        this.min = min;
        this.max = max;
        this.amount = amount;
    }

    public BigDecimal getMin()    { return min; }
    public BigDecimal getMax()    { return max; }
    public BigDecimal getAmount() { return amount; }
}

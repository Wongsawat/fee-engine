package com.wpanther.pisp.fee.engine.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public final class FeeRule {
    private final String chargeType;
    private final ChargeBearer chargeBearer;
    private final FeeType feeType;
    private final BigDecimal flatAmount;
    private final BigDecimal percentage;
    private final List<Tier> tiers;
    private final String currency;

    public FeeRule(String chargeType, ChargeBearer chargeBearer, FeeType feeType,
                   BigDecimal flatAmount, BigDecimal percentage,
                   List<Tier> tiers, String currency) {
        this.chargeType = chargeType;
        this.chargeBearer = chargeBearer;
        this.feeType = feeType;
        this.flatAmount = flatAmount;
        this.percentage = percentage;
        this.tiers = tiers == null ? List.of() : List.copyOf(tiers);
        this.currency = currency;
    }

    public String getChargeType()                { return chargeType; }
    public ChargeBearer getChargeBearer()        { return chargeBearer; }
    public FeeType getFeeType()                  { return feeType; }
    public Optional<BigDecimal> getFlatAmount()  { return Optional.ofNullable(flatAmount); }
    public Optional<BigDecimal> getPercentage()  { return Optional.ofNullable(percentage); }
    public List<Tier> getTiers()                 { return tiers; }
    public String getCurrency()                  { return currency; }
}

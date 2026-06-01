package com.wpanther.pisp.fee.engine.adapter.out.persistence.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fee_rules")
public class FeeRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "payment_type", nullable = false) private String paymentType;
    @Column(name = "scheme", nullable = false) private String scheme;
    @Column(name = "charge_bearer", nullable = false) private String chargeBearer;
    @Column(name = "account_identification") private String accountIdentification;
    @Column(name = "charge_type", nullable = false) private String chargeType;
    @Column(name = "fee_type", nullable = false) private String feeType;
    @Column(name = "flat_amount") private BigDecimal flatAmount;
    @Column(name = "percentage") private BigDecimal percentage;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb") private JsonNode tiers;
    @Column(name = "currency", nullable = false) private String currency;
    @Column(name = "active", nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String v) { this.paymentType = v; }
    public String getScheme() { return scheme; }
    public void setScheme(String v) { this.scheme = v; }
    public String getChargeBearer() { return chargeBearer; }
    public void setChargeBearer(String v) { this.chargeBearer = v; }
    public String getAccountIdentification() { return accountIdentification; }
    public void setAccountIdentification(String v) { this.accountIdentification = v; }
    public String getChargeType() { return chargeType; }
    public void setChargeType(String v) { this.chargeType = v; }
    public String getFeeType() { return feeType; }
    public void setFeeType(String v) { this.feeType = v; }
    public BigDecimal getFlatAmount() { return flatAmount; }
    public void setFlatAmount(BigDecimal v) { this.flatAmount = v; }
    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal v) { this.percentage = v; }
    public JsonNode getTiers() { return tiers; }
    public void setTiers(JsonNode v) { this.tiers = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}

CREATE TABLE fee_rules (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_type           VARCHAR(50)   NOT NULL,
    scheme                 VARCHAR(20)   NOT NULL,
    charge_bearer          VARCHAR(30)   NOT NULL,
    account_identification VARCHAR(50)   NULL,
    charge_type            VARCHAR(50)   NOT NULL,
    fee_type               VARCHAR(20)   NOT NULL,
    flat_amount            NUMERIC(18,5) NULL,
    percentage             NUMERIC(8,5)  NULL
                               CONSTRAINT chk_percentage_range CHECK (percentage > 0 AND percentage <= 1),
    tiers                  JSONB         NULL,
    currency               VARCHAR(3)    NOT NULL,
    active                 BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMP     NOT NULL,
    updated_at             TIMESTAMP     NOT NULL,
    CONSTRAINT chk_charge_bearer
        CHECK (charge_bearer IN ('BorneByDebtor', 'BorneByCreditor')),
    CONSTRAINT chk_flat_requires_amount
        CHECK (fee_type != 'FLAT' OR flat_amount IS NOT NULL),
    CONSTRAINT chk_percentage_requires_rate
        CHECK (fee_type != 'PERCENTAGE' OR percentage IS NOT NULL),
    CONSTRAINT chk_tiered_requires_tiers
        CHECK (fee_type != 'TIERED' OR tiers IS NOT NULL),
    CONSTRAINT chk_flat_amount_positive
        CHECK (flat_amount IS NULL OR flat_amount > 0)
);

CREATE INDEX idx_fee_rules_lookup
    ON fee_rules (payment_type, scheme, charge_bearer, currency, account_identification)
    WHERE active = TRUE;

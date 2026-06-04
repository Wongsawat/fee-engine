ALTER TABLE fee_rules ADD COLUMN destination_country VARCHAR(2) NULL;

ALTER TABLE fee_rules ADD CONSTRAINT chk_destination_country_format
    CHECK (destination_country IS NULL OR destination_country ~ '^[A-Z]{2}$');

ALTER TABLE fee_rules ADD CONSTRAINT chk_destination_country_international_only
    CHECK (destination_country IS NULL OR payment_type IN (
        'INTERNATIONAL', 'INTERNATIONAL_SCHEDULED', 'INTERNATIONAL_STANDING_ORDER'));

DROP INDEX IF EXISTS idx_fee_rules_lookup;
CREATE INDEX idx_fee_rules_lookup
    ON fee_rules (payment_type, scheme, charge_bearer, currency,
                  destination_country, account_identification)
    WHERE active = TRUE;

DROP INDEX IF EXISTS uniq_active_fee_rules;
CREATE UNIQUE INDEX uniq_active_fee_rules
    ON fee_rules (payment_type, scheme, charge_bearer, currency,
                  COALESCE(destination_country, ''), account_identification)
    WHERE active = TRUE;

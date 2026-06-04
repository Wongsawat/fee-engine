ALTER TABLE fee_rules ADD COLUMN priority INTEGER NOT NULL DEFAULT 0;

ALTER TABLE fee_rules ADD CONSTRAINT chk_priority_non_negative
    CHECK (priority >= 0);

DROP INDEX IF EXISTS uniq_active_fee_rules;
CREATE UNIQUE INDEX uniq_active_fee_rules
    ON fee_rules (payment_type, scheme, charge_bearer, currency,
                  COALESCE(destination_country, ''), COALESCE(account_identification, ''),
                  charge_type)
    WHERE active = TRUE;

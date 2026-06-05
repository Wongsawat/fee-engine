ALTER TABLE fee_rules ADD COLUMN priority INTEGER NOT NULL DEFAULT 0;

ALTER TABLE fee_rules ADD CONSTRAINT chk_priority_non_negative
    CHECK (priority >= 0);

-- V6 used bare destination_country/account_identification (NULLs are distinct in PG unique indexes,
-- so multiple null-account rules per chargeType could coexist). COALESCE normalises NULLs to ''
-- so exactly one active rule per (dimensions, chargeType) is enforced. charge_type is added to
-- support per-chargeType priority selection introduced in this migration.
DROP INDEX IF EXISTS uniq_active_fee_rules;
CREATE UNIQUE INDEX uniq_active_fee_rules
    ON fee_rules (payment_type, scheme, charge_bearer, currency,
                  COALESCE(destination_country, ''), COALESCE(account_identification, ''),
                  charge_type)
    WHERE active = TRUE;

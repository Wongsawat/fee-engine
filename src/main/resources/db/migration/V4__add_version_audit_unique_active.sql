-- Optimistic locking (monorepo convention: @Version on all JPA entities)
ALTER TABLE fee_rules ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Audit trail — which service client made the change
ALTER TABLE fee_rules ADD COLUMN created_by VARCHAR(255);
ALTER TABLE fee_rules ADD COLUMN updated_by VARCHAR(255);

-- Prevent overlapping active rules with identical lookup dimensions
CREATE UNIQUE INDEX uniq_active_fee_rules
  ON fee_rules (payment_type, scheme, charge_bearer, currency, account_identification)
  WHERE active = TRUE;

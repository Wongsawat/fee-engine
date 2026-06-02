-- NOT VALID: skips the table scan for existing rows (safe — no FREE rows existed before this migration).
-- V3 runs VALIDATE CONSTRAINT in a separate transaction to avoid a full-table AccessExclusiveLock at deploy time.
ALTER TABLE fee_rules
    ADD CONSTRAINT chk_free_no_amount
        CHECK (fee_type != 'FREE' OR
               (flat_amount IS NULL AND percentage IS NULL AND tiers IS NULL))
        NOT VALID;

-- Validates chk_free_no_amount added NOT VALID in V2. Run off-peak: uses ShareUpdateExclusiveLock,
-- weaker than the AccessExclusiveLock that a plain ADD CONSTRAINT would take. Safe to interrupt —
-- V2's NOT VALID constraint continues enforcing the rule on all new writes if this migration is rolled back.
ALTER TABLE fee_rules
    VALIDATE CONSTRAINT chk_free_no_amount;

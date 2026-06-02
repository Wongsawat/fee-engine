ALTER TABLE fee_rules
    ADD CONSTRAINT chk_free_no_amount
        CHECK (fee_type != 'FREE' OR
               (flat_amount IS NULL AND percentage IS NULL AND tiers IS NULL))
        NOT VALID;

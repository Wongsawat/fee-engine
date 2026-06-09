-- V8: Replace TIERED fee_type with TIERED_SLAB and TIERED_STEP
-- Statement ordering is significant: step 2 WHERE clause targets 'TIERED_SLAB'
-- which only exists after step 1 renames it.

-- 1. Rename existing TIERED rows to TIERED_SLAB
UPDATE fee_rules
SET fee_type = 'TIERED_SLAB'
WHERE fee_type = 'TIERED';

-- 2. Backfill rateType = 'FIXED' into every tier object for migrated rows
UPDATE fee_rules
SET tiers = (
    SELECT jsonb_agg(tier || '{"rateType":"FIXED"}')
    FROM jsonb_array_elements(tiers) AS tier
)
WHERE fee_type = 'TIERED_SLAB'
  AND tiers IS NOT NULL;

-- 3. Drop old constraint that referenced the now-gone 'TIERED' literal
ALTER TABLE fee_rules
    DROP CONSTRAINT chk_tiered_requires_tiers;

-- 4. Add equivalent constraints for the two new fee type values
ALTER TABLE fee_rules
    ADD CONSTRAINT chk_tiered_slab_requires_tiers
        CHECK (fee_type != 'TIERED_SLAB' OR tiers IS NOT NULL);

ALTER TABLE fee_rules
    ADD CONSTRAINT chk_tiered_step_requires_tiers
        CHECK (fee_type != 'TIERED_STEP' OR tiers IS NOT NULL);

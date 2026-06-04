ALTER TABLE fee_rules ADD COLUMN min_fee NUMERIC(18,5) NULL;
ALTER TABLE fee_rules ADD COLUMN max_fee NUMERIC(18,5) NULL;

ALTER TABLE fee_rules ADD CONSTRAINT chk_min_fee_positive
    CHECK (min_fee IS NULL OR min_fee > 0);
ALTER TABLE fee_rules ADD CONSTRAINT chk_max_fee_positive
    CHECK (max_fee IS NULL OR max_fee > 0);
ALTER TABLE fee_rules ADD CONSTRAINT chk_min_le_max
    CHECK (min_fee IS NULL OR max_fee IS NULL OR min_fee <= max_fee);
ALTER TABLE fee_rules ADD CONSTRAINT chk_caps_only_percentage
    CHECK ((min_fee IS NULL AND max_fee IS NULL) OR fee_type = 'PERCENTAGE');

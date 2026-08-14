-- Allow inserts that omit audit columns (ops SQL / Failsafe seed inserts).
ALTER TABLE rate_limit_rules
    ALTER COLUMN created_at SET DEFAULT NOW(),
    ALTER COLUMN updated_at SET DEFAULT NOW();

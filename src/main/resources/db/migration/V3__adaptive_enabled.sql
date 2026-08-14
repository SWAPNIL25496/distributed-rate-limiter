-- Adaptive limits kill-switch per rule (ADR 0008 / Phase 8).
ALTER TABLE rate_limit_rules
    ADD COLUMN adaptive_enabled BOOLEAN NOT NULL DEFAULT TRUE;

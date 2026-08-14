-- Baseline migration for the distributed rate limiter.
--
-- Intentionally creates no objects: it only establishes the Flyway schema history
-- so later phases append forward migrations (rate_limit_rules lands in Phase 2).
SELECT 1;

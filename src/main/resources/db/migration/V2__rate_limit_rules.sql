-- Rate limit rules system of record (Postgres). Adaptive column lands in Phase 8.
CREATE TABLE rate_limit_rules (
    id                  BIGSERIAL PRIMARY KEY,
    identifier          VARCHAR(255) NOT NULL,
    namespace           VARCHAR(255) NOT NULL,
    algorithm           VARCHAR(32)  NOT NULL,
    burst_capacity      INTEGER,
    refill_per_second   DOUBLE PRECISION,
    limit_count         INTEGER,
    window_seconds      INTEGER,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_rate_limit_rules_identifier_namespace UNIQUE (identifier, namespace),
    CONSTRAINT ck_rate_limit_rules_algorithm CHECK (algorithm IN ('TOKEN_BUCKET', 'SLIDING_WINDOW'))
);

CREATE INDEX idx_rate_limit_rules_enabled ON rate_limit_rules (enabled);

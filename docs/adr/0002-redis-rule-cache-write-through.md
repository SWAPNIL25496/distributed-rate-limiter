# ADR 0002: Shared Redis rule cache (write-through + cache-aside)

## Status

Proposed

## Context

Every evaluate must resolve the rule for `(identifier, namespace)`. Hitting Postgres on every request adds latency and load. A **per-JVM** cache (e.g. Caffeine-only) would diverge across the two Compose API replicas after CRUD unless pub/sub invalidation is added. The service already requires Redis for quota counters.

## Decision

Use a **shared Redis rule cache**:

- Key: `rl:v1:rule:{identifier}:{namespace}` (versioned; unsharded — see [ADR 0010](0010-shard-ready-redis-keys.md))
- **Cache-aside** on evaluate/observe: Redis get → on miss load Postgres → populate cache
- **Write-through** on create/update: persist Postgres, then set Redis key
- On delete/disable: update Postgres, then **delete** (or overwrite) the Redis key
- **TTL 60 seconds** as a safety net (not the primary freshness mechanism)
- **No Caffeine-only SoR** in v1

## Consequences

- Both API replicas see the same cached rule without custom invalidation buses.
- Brief inconsistency windows are bounded by write-through success and TTL; document Redis-set-after-Postgres failure as an operational edge (retry/log).
- Evaluate still depends on Redis for cache hits; miss path needs Postgres.

## Alternatives considered

- **Postgres on every evaluate** — simplest consistency; worse hot-path latency.
- **Local Caffeine only** — fast; diverges across replicas without pub/sub.
- **Caffeine + Redis pub/sub invalidation** — correct but more moving parts than needed for v1.
- **Redis as rule SoR** — rejected in ADR 0001.

# Reflection — Distributed Rate Limiter

Personal take-home write-up for the distributed rate limiter (Phases 1–9). Planning SoT: [`docs/sdd/distributed-rate-limiter.md`](docs/sdd/distributed-rate-limiter.md).

## 1. Correctness under contention

Evaluate admission runs inside **Redis Lua** on a single counter key per shard (`rl:v1:tb|sw:{identifier}:{namespace}:{shardId}`). Redis executes scripts atomically per key, so concurrent evaluates from multiple API replicas cannot over-admit beyond the effective per-shard budget. With v1 `counter-shards=1`, that is the full effective limit.

Cross-replica coherence does **not** rely on sticky sessions: both Compose `api1`/`api2` and DigitalOcean App Platform instances share the same Redis for counters, rule cache, and adaptive keys.

Java engines in `limiter/` mirror Lua math for unit/parity tests; **Lua is the production source of truth**.

## 2. Known limitations

| Area | Bound |
|------|--------|
| Sliding window | Counter-based rolling window ≈ perfect request log; precision vs memory trade-off documented in algorithm choice (ADR 0003) |
| Rule cache | Write-through on CRUD + 60s TTL; brief stale risk only if write-through fails and TTL has not expired |
| Adaptive | Discrete tiers (0.25× / 0.5× / 1.0×); TTL 120s auto-relax; trusted to API-key holders |
| Sharding | Keys are shard-ready; v1 uses N=1. Raising N splits budget across shards (Σ ≤ effective limit) |
| Datastore outage | Redis down → evaluate/observe/adaptive unavailable; Postgres down → CRUD fails, evaluate may continue on cached rules |

## 3. Decision log (pointers)

Durable decisions live in Accepted ADRs:

- [0001](docs/adr/0001-postgres-rules-redis-counters.md) Postgres rules / Redis counters  
- [0002](docs/adr/0002-redis-rule-cache-write-through.md) Redis rule cache write-through  
- [0003](docs/adr/0003-pluggable-algorithms.md) Token bucket + sliding window  
- [0004](docs/adr/0004-lua-atomic-evaluation.md) Lua atomic evaluate  
- [0005](docs/adr/0005-rest-api-only.md) REST + springdoc  
- [0006](docs/adr/0006-api-key-auth.md) `X-API-Key` (no Spring Security)  
- [0007](docs/adr/0007-admin-thymeleaf-ui.md) Thymeleaf admin UI  
- [0008](docs/adr/0008-adaptive-limits.md) Adaptive feedback multipliers  
- [0009](docs/adr/0009-digitalocean-deploy.md) Separate datastores + App Platform Dockerfile  
- [0010](docs/adr/0010-shard-ready-redis-keys.md) Shard-ready counter keys  

Admin UI, adaptive limits, and DigitalOcean deploy artifacts are **in v1** as Phases 7–9 (not deferred to this residual list).

## 4. Residual backlog (out of v1)

Items intentionally **not** shipped; do **not** park UI / adaptive / DO here:

- Fixed-window algorithm
- JWT / RBAC (beyond shared API key)
- Prometheus / Grafana metrics export
- GraphQL or gRPC public API
- MySQL as rules SoR
- Redis Cluster hash-tag co-location of counter shards
- Continuous / PID-style adaptive controllers

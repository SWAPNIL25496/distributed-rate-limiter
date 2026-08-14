# ADR 0004: Lua atomic evaluation under contention

## Status

Accepted (2026-08-14, on SDD approval)

## Context

Multiple API replicas (and concurrent clients) will evaluate the same `(identifier, namespace)` counter key. A naive read-modify-write from Java (GET tokens → compute → SET) races and can **over-admit** under contention. The PDF requires multi-instance correctness and at least one concurrent-access test.

## Decision

Perform quota **consume** (and the counter reads needed for consistent observe) via **Redis Lua scripts** executed with `EVAL`/`EVALSHA` against **one** algorithm state key per call (`rl:v1:tb:…:{shardId}` or `rl:v1:sw:…:{shardId}` — see [ADR 0010](0010-shard-ready-redis-keys.md)).

- One round-trip per evaluate; Redis runs the script atomically per **shard** key.
- Scripts encode token-bucket refill/consume or sliding-window counter update and return `allowed`, `remaining`, and reset-related fields for that shard; the service applies per-shard budget so global admits stay ≤ effective limit.
- Concurrent evaluate integration tests (Testcontainers Redis) prove total allows do not exceed the configured limit under parallel load (with `counter-shards=1` in v1).

Document race bounds and residual approximation (especially counter-based sliding window) in README/`REFLECTION.md`.

## Consequences

- Closes classic check-then-act races across replicas.
- Lua must stay small, versioned in classpath, and covered by tests.
- Evaluate availability depends on Redis; Postgres alone cannot admit traffic.
- Interview-friendly contention story: Redis single-threaded script execution serializes updates per key.

## Alternatives considered

- **Java GET + SET without lock** — simplest; incorrect under contention.
- **Redis `WATCH`/`MULTI` optimistic transactions** — workable; more round-trips and retry logic than Lua.
- **Redlock / distributed locks around Java math** — heavier; easy to get wrong; unnecessary when state lives in Redis.
- **Postgres row locks for counters** — durable; poorer hot-path fit given Redis already required for shared cache.

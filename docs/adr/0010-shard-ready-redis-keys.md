# ADR 0010: Shard-ready Redis counter keys

## Status

Proposed

## Context

A single Redis key per `(identifier, namespace)` for quota state is correct and simple, but under extreme load one hot tenant or one busy API namespace concentrates all evaluates onto **one key** (and, on Redis Cluster, one hash slot / one shard CPU). That becomes the bottleneck long before Postgres or the app tier.

We need a key layout that:

1. Works today with **one** counter shard (v1 default).
2. Can raise shard count later **without** renaming the scheme.
3. Spreads naturally across Redis Cluster slots when scaled (each Lua touch is a **single** key).

## Decision

### Key layout (versioned)

| Purpose | Key pattern |
|---------|-------------|
| Rule cache | `rl:v1:rule:{identifier}:{namespace}` |
| Token bucket counter | `rl:v1:tb:{identifier}:{namespace}:{shardId}` |
| Sliding window counter | `rl:v1:sw:{identifier}:{namespace}:{shardId}` |
| Adaptive multiplier | `rl:v1:adapt:{identifier}:{namespace}` |

- `{shardId}` is a zero-based integer string: `0` .. `N-1`.
- Config: `app.rate-limit.counter-shards` (**default `1`** in v1). Changing `N` later does not change the pattern.
- **No Redis Cluster hash tags** (`{...}`) in these keys. Each evaluate Lua script receives **exactly one** `KEYS[1]` (the chosen shard). Independent key hashes let different tenants **and** different shards land on different Cluster slots.

### Shard selection and capacity (when `N > 1`)

1. On evaluate, pick `shardId = floor(hash(stable_or_random) % N)` — prefer a per-request random or hash of a request id so load spreads; document choice in README.
2. Pass **per-shard effective limit** into Lua:  
   `shardLimit = floor(effectiveLimit / N)` with remainder `effectiveLimit % N` assigned to shards `0 .. remainder-1` (those shards get `+1`).  
   Total admits across shards ≤ `effectiveLimit` (never over-admit vs global budget).
3. On observe, **aggregate** shards `0..N-1` (sum consumed / remaining; resetAt = earliest or algorithm-documented aggregate).

Rule cache and adaptive keys stay **unsharded** (low QPS vs evaluate; one logical value per tenant/namespace).

### v1 behaviour

Ship with `counter-shards = 1` (only `...:0` keys). Unit/IT assert single-shard semantics. Document how to raise `N` for hot tenants in README/`REFLECTION.md` (ops note; may require flush or dual-read migration if raising `N` live).

## Consequences

- Hot-tenant write load can be split across `N` keys/slots without changing API or Postgres schema.
- Observe becomes O(N) Redis reads (or one Lua that only runs when N is small); keep `N` modest (e.g. 1–16).
- Raising `N` live without migration can temporarily under-admit until old keys expire — document as ops limitation.
- Lua remains single-key atomic (ADR 0004 still holds **per shard**).

## Alternatives considered

- **Single key forever** — simplest; fails hot-tenant / Cluster slot skew at scale.
- **Redis Cluster hash tag `{identifier}` on all keys** — keeps one tenant’s keys on one slot (good for multi-key Lua); **worsens** hot-tenant slot concentration — rejected for counter keys.
- **Local in-process buffering before Redis** — helps throughput; weakens multi-instance exactness unless carefully designed — deferred.
- **Separate Redis instance per tenant** — operationally heavy; not for v1.

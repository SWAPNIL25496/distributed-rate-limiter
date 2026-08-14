# ADR 0008: Adaptive limits via downstream error-rate feedback

## Status

Proposed

## Context

The PDF stretch list includes adaptive limits: a feedback signal (downstream error rate) that temporarily tightens or relaxes limits per tenant. Base algorithms stay token bucket and sliding window ([ADR 0003](0003-pluggable-algorithms.md)). Feedback must be shared across API replicas and expire automatically so a stuck “tight” state cannot last forever.

## Decision

Add **adaptive limits** in v1 with these locks:

| Item | Lock |
|------|------|
| API | `POST /api/v1/adaptive/feedback` — body `{ "identifier", "namespace", "downstreamErrorRate": 0.0–1.0 }`; `X-API-Key` required |
| Redis key | `rl:v1:adapt:{identifier}:{namespace}` stores multiplier + last `errorRate`; **TTL 120s** (unsharded; [ADR 0010](0010-shard-ready-redis-keys.md)) |
| Multiplier map | `errorRate >= 0.5` → **0.25×**; `>= 0.2` → **0.5×**; else **1.0×** |
| Evaluate | When rule `adaptive_enabled` is true, apply multiplier to effective burst/limit; missing key ⇒ **1.0×**; minimum effective limit **1** when base ≥ 1 |
| Observe | Include `adaptiveMultiplier`, `effectiveLimit`, `downstreamErrorRate` (nullable when no adapt state / disabled) |
| Rule flag | Postgres `adaptive_enabled BOOLEAN NOT NULL DEFAULT TRUE` (disable per rule) |

Unit + integration tests cover tighten and relax behaviour.

## Consequences

- Shared Redis adapt keys keep Compose/DO replicas coherent without sticky sessions.
- TTL auto-relaxes after feedback stops; operators can disable per rule.
- Evaluate/Lua path must accept effective (scaled) limits; observe schema grows three fields.
- Feedback is trusted to API-key holders (same trust model as rule CRUD).

## Alternatives considered

- **Permanent adaptive config in Postgres** — durable but slower to relax; rejected in favor of ephemeral Redis TTL.
- **Global (not per-tenant) multiplier** — simpler; fails per-tenant PDF intent.
- **Continuous controller / PID** — overkill for take-home; discrete tiers locked.
- **Defer to REFLECTION §4** — rejected; now in v1 (Phase 8).

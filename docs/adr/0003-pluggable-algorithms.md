# ADR 0003: Pluggable algorithms (token bucket + sliding window)

## Status

Proposed

## Context

The problem statement requires at least two rate-limit algorithms and explicit support for **burst + sustained** rates. A single fixed-window counter is insufficient for the locked completeness bar. Algorithms must be testable in pure unit tests and executable atomically in Redis (ADR 0004).

## Decision

Introduce a pluggable **`RateLimitAlgorithm`** strategy dispatched by rule `algorithm`:

| Algorithm | Parameters | Role |
|-----------|------------|------|
| **TOKEN_BUCKET** | `burst_capacity`, `refill_per_second` (tokens/sec) | Burst + sustained |
| **SLIDING_WINDOW** | `limit`, `window_seconds` | Precise windowed quota |

v1 implements **only** these two. Sliding window uses a **counter-based** approximation (not a full per-request log). Fixed-window remains deferred to `REFLECTION.md` §4. Adaptive multipliers wrap these engines at evaluate time ([ADR 0008](0008-adaptive-limits.md)); they are not a third algorithm.

Pure Java engines live under `com.example.ratelimiter.limiter` for unit tests; Redis Lua adapters implement the same semantics for multi-instance evaluate.

## Consequences

- Clear extension point for a future third algorithm.
- Validation must be algorithm-specific on CRUD.
- Counter-based sliding window precision bounds must be documented in README/REFLECTION.
- Dual implementation surface (pure + Lua) requires parity tests.

## Alternatives considered

- **Fixed-window only** — simple; weak burst/sustained story; PDF asks for ≥2.
- **Token bucket only** — meets burst/sustained; fails “≥2 algorithms” bar.
- **Full request-log sliding window** — highest fidelity; higher memory and Lua complexity for v1.
- **Leaky bucket as second algo** — valid; sliding window chosen for clearer “windowed quota” demos.

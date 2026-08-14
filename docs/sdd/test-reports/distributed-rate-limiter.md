# Test report — distributed-rate-limiter

## Phase 1 — Scaffold (2026-08-14)

| Check | Result |
|-------|--------|
| `./mvnw clean test` (Java 21) | **PASS** — 9 tests, 0 failures (`RateLimiterApplicationTest` 2, `ApiKeyAuthFilterTest` 7) |
| Surefire / Failsafe split | **PASS** — `*Test` local; `*IT` deferred to CI |
| AC-9 Compose two-replica | **CI-pending** (no Docker in this container) |
| AC-10 CI `./mvnw verify` | **CI-pending** until phase branch push |
| Dockerfile build | **CI-pending / operator** |

Notes: ECS structured logging observed in test output. API-key filter covers 401 + public health/swagger.

## Phase 2 — Rules CRUD + Redis write-through (2026-08-14)

| Check | Result |
|-------|--------|
| `./mvnw test` | **PASS** — BUILD SUCCESS, 25 tests, 0 failures |
| AC-2 rules API (MockMvc) | **PASS** — `RuleControllerTest` |
| AC-1 Postgres→Redis write-through IT | **CI-pending** (no Docker in this container) |

## Phase 3 — Rate engines (2026-08-14)

| Check | Result |
|-------|--------|
| `./mvnw test` | **PASS** — BUILD SUCCESS, 33 tests, 0 failures |
| TokenBucketEngineTest | **PASS** — 4 tests |
| SlidingWindowEngineTest | **PASS** — 4 tests |

Notes: Unit-level engine coverage; no Docker needed for this phase.

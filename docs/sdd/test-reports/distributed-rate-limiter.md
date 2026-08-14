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

## Phase 4 — Evaluate API + Lua (2026-08-14)

| Check | Result |
|-------|--------|
| `./mvnw test` | **PASS** — BUILD SUCCESS, 50 tests, 0 failures |
| AC-3 / AC-4 evaluate API (MockMvc) | **PASS** — MockMvc coverage |
| AC-5 / Lua parity EvaluateIT | **CI-pending** (no Docker in this container) |

## Phase 5 — Observe + concurrent evaluate (2026-08-14)

| Check | Result |
|-------|--------|
| `./mvnw test` | **PASS** — BUILD SUCCESS, 57 tests, 0 failures |
| Observe API (MockMvc) | **PASS** — `QuotaControllerTest` (5 tests) |
| ConcurrentEvaluateIT | **CI-pending** (no Docker in this container) |

## Phase 6 — README / REFLECTION / architecture (2026-08-14)

| Check | Result |
|-------|--------|
| Doc review (AC-11–13) | **PASS** — `README.md`, `REFLECTION.md`, `docs/architecture/request-lifecycle.md` |
| Residual §4 excludes UI/adaptive/DO | **PASS** — deferred to Phases 7–9 |
| `./mvnw test` (post batch 8→7→9→6) | **PASS** — BUILD SUCCESS, **83** tests, 0 failures |

Notes: Documentation-only phase; suite green after Phases 7–9 landed in same batch.

## Phase 7 — Admin Thymeleaf UI (2026-08-14)

| Check | Result |
|-------|--------|
| `./mvnw test` | **PASS** — included in **83**/0 batch result |
| AC-15 AdminUiTest (Surefire / MockMvc) | **PASS** — `AdminUiTest` (login session, rules/quotas pages, public CSS) |
| Admin IT (Docker) | **CI-pending** (no Docker in this container) |

## Phase 8 — Adaptive limits (2026-08-14)

| Check | Result |
|-------|--------|
| `./mvnw test` | **PASS** — included in **83**/0 batch result |
| Unit + MockMvc (AC-16 / AC-17) | **PASS** — `AdaptiveLimitsTest`, `AdaptiveFeedbackServiceTest`, `AdaptiveFeedbackControllerTest`, `QuotaServiceTest` |
| AdaptiveIT (Testcontainers) | **CI-pending** (no Docker in this container; Failsafe / `./mvnw verify`) |

## Phase 9 — DigitalOcean deploy artifacts (2026-08-14)

| Check | Result |
|-------|--------|
| Doc review (AC-18) | **PASS** — `.do/app.yaml`, `deploy/digitalocean/README.md`, `docs/architecture/digitalocean.md` |
| `./mvnw test` (no DO account required) | **PASS** — BUILD SUCCESS, **83** tests, 0 failures |
| Live DO deploy / cross-instance demo | **Operator / CI-pending** |

Notes: Batch order executed 8→7→9→6; single Surefire evidence row applies across Phases 6–9.

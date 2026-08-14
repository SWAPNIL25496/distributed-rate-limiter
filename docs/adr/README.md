# Architectural Decision Records

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-postgres-rules-redis-counters.md) | Postgres rules SoR + Redis quota counters | Accepted |
| [0002](0002-redis-rule-cache-write-through.md) | Shared Redis rule cache (write-through + cache-aside) | Accepted |
| [0003](0003-pluggable-algorithms.md) | Pluggable token bucket + sliding window | Accepted |
| [0004](0004-lua-atomic-evaluation.md) | Lua atomic evaluation under contention | Accepted |
| [0005](0005-rest-api-only.md) | REST public API (+ Thymeleaf admin companion) | Accepted |
| [0006](0006-api-key-auth.md) | API key auth (`X-API-Key`) via custom filter; no Spring Security | Accepted |
| [0007](0007-admin-thymeleaf-ui.md) | Admin Thymeleaf UI companion (`/drl/admin`, `/drl/admin/quotas`, `/drl/admin/login`) | Accepted |
| [0008](0008-adaptive-limits.md) | Adaptive limits via error-rate feedback | Accepted |
| [0009](0009-digitalocean-deploy.md) | DO deploy; separate Postgres/Redis; env host/user/password | Accepted |
| [0010](0010-shard-ready-redis-keys.md) | Shard-ready Redis counter keys (hot-tenant / Cluster) | Accepted |

Convention: `.cursor/rules/agent/architectural-decisions.mdc`

Planning SoT for phased delivery: [`../sdd/distributed-rate-limiter.md`](../sdd/distributed-rate-limiter.md) — **Approved** 2026-08-14; all ten ADRs Accepted with it.

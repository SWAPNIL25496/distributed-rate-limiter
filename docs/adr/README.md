# Architectural Decision Records

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-postgres-rules-redis-counters.md) | Postgres rules SoR + Redis quota counters | Proposed |
| [0002](0002-redis-rule-cache-write-through.md) | Shared Redis rule cache (write-through + cache-aside) | Proposed |
| [0003](0003-pluggable-algorithms.md) | Pluggable token bucket + sliding window | Proposed |
| [0004](0004-lua-atomic-evaluation.md) | Lua atomic evaluation under contention | Proposed |
| [0005](0005-rest-api-only.md) | REST public API (+ Thymeleaf admin companion) | Proposed |
| [0006](0006-api-key-auth.md) | API key authentication (`X-API-Key`) | Proposed |
| [0007](0007-admin-thymeleaf-ui.md) | Admin Thymeleaf UI companion | Proposed |
| [0008](0008-adaptive-limits.md) | Adaptive limits via error-rate feedback | Proposed |
| [0009](0009-digitalocean-deploy.md) | DO deploy; separate Postgres/Redis; env host/user/password | Proposed |
| [0010](0010-shard-ready-redis-keys.md) | Shard-ready Redis counter keys (hot-tenant / Cluster) | Proposed |

Convention: `.cursor/rules/agent/architectural-decisions.mdc`

Planning SoT for phased delivery: [`../sdd/distributed-rate-limiter.md`](../sdd/distributed-rate-limiter.md) (**DRAFT** until approved). On SDD approval, mark these ADRs **Accepted**.

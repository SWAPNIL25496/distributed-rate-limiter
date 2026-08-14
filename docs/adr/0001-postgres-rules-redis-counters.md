# ADR 0001: Postgres rules SoR + Redis quota counters

## Status

Accepted (2026-08-14, on SDD approval)

## Context

A distributed rate limiter needs durable runtime configuration (rules with burst/sustained parameters) and hot, shared quota state that multiple API replicas can update correctly. Storing everything only in Redis loses durable config across Redis flushes and complicates audit. Storing counters only in Postgres makes evaluate latency and contention harder under multi-instance load. The take-home requires horizontal scalability and multi-instance correctness.

## Decision

Split storage by concern:

| Concern | Store |
|---------|--------|
| Rate-limit **rules** (system of record) | **PostgreSQL 16** via Spring Data JPA + **Flyway** |
| **Quota consumption** (tokens / windows / counters) | **Redis 7**, mutated by Lua on evaluate; **shard-ready keys** ([ADR 0010](0010-shard-ready-redis-keys.md)) |

Compose runs **two API replicas** that connect to Postgres and Redis via **environment** (host / username / password). Local Compose may include optional Postgres/Redis containers, or apps may point at **separately deployed** hosts. Production (DigitalOcean): datastores are provisioned **separately** from app machines ([ADR 0009](0009-digitalocean-deploy.md)). Rules are never “Redis-only SoR” in v1.

## Consequences

- CRUD survives process restart; schema evolves via Flyway.
- Evaluate hot path depends on Redis availability for counters (and typically for cached rules — see ADR 0002).
- Operators must run both datastores locally and in CI (Testcontainers).
- Clear interview narrative: durable config vs ephemeral/shared quota state.

## Alternatives considered

- **Rules + counters only in Redis** — fast demo; weak durability and recovery for config.
- **Rules + counters only in Postgres** — durable; weaker multi-instance evaluate atomicity/latency without careful locking.
- **MySQL instead of Postgres** — valid SQL choice; Postgres locked for this build ([`build-plan-stack.md`](../sdd/build-plan-stack.md)).
- **Per-instance in-memory counters** — fails multi-instance correctness.

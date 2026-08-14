# ADR 0006: API key authentication (`X-API-Key`)

## Status

Proposed

## Context

The rate limiter exposes mutating rule APIs and evaluate/observe endpoints that affect or reveal quota state. The take-home does not mandate a specific auth scheme. JWT adds issuer/claims complexity; an open API is unsafe even for local Compose demos shared on a network. A shared secret is enough for a single-tenant v1.

## Decision

Protect application API endpoints with a shared **API key** supplied as header **`X-API-Key`**, configured via environment (e.g. `APP_API_KEY` / `app.api-key`). Reject missing/invalid keys with `401`.

**Protected (locked):**

- `POST /api/v1/evaluate`
- `GET /api/v1/quotas/**`
- `/api/v1/rules/**`

**Public (locked):**

- `GET /actuator/health` — no API key, for Compose healthchecks

JWT and richer RBAC stay in `REFLECTION.md` §4. Swagger UI may stay open for demo convenience; document that choice in Phase 2 README notes.

## Consequences

- Fast to implement and explain in the write-up and live session.
- Single shared secret is not multi-tenant or rotatable without config change.
- `curl` and integration tests must send the header.

## Alternatives considered

- **No auth in v1** — simplest demo; unacceptable if the port is reachable beyond localhost.
- **JWT (OAuth2 resource server)** — better long-term; heavier for take-home and deferred explicitly.
- **Basic auth** — workable; API key header is clearer for machine clients and Compose env wiring.

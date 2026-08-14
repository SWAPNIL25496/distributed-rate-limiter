# ADR 0006: API key authentication (`X-API-Key`)

## Status

Accepted (2026-08-14, on SDD approval)

## Context

The rate limiter exposes mutating rule APIs and evaluate/observe endpoints that affect or reveal quota state. The take-home does not mandate a specific auth scheme. JWT adds issuer/claims complexity; an open API is unsafe even for local Compose demos shared on a network. A shared secret is enough for a single-tenant v1.

## Decision

Protect application API endpoints with a shared **API key** supplied as header **`X-API-Key`**, configured via environment (e.g. `APP_API_KEY` / `app.api-key`). Reject missing/invalid keys with `401`.

Enforcement mechanism (locked): a custom **`OncePerRequestFilter`** registered in `config/`. `spring-boot-starter-security` is **not** a v1 dependency — the single-key check does not justify the filter-chain surface, and the Phase 7 UI session is a plain `HttpSession` attribute.

**Protected (locked):**

- `POST /api/v1/evaluate`
- `GET /api/v1/quotas/**`
- `/api/v1/rules/**`
- `POST /api/v1/adaptive/feedback`
- `/drl/admin/**` except `/drl/admin/login` (session attribute set from the API-key form)

**Public (locked):**

- `GET /actuator/health` — no API key, for Compose healthchecks
- `GET /swagger-ui.html`, `GET /v3/api-docs/**` — open for demo

JWT and richer RBAC stay in `REFLECTION.md` §4.

## Consequences

- Fast to implement and explain in the write-up and live session.
- Single shared secret is not multi-tenant or rotatable without config change.
- `curl` and integration tests must send the header.

## Alternatives considered

- **No auth in v1** — simplest demo; unacceptable if the port is reachable beyond localhost.
- **JWT (OAuth2 resource server)** — better long-term; heavier for take-home and deferred explicitly.
- **Basic auth** — workable; API key header is clearer for machine clients and Compose env wiring.

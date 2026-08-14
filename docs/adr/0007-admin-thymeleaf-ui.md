# ADR 0007: Admin Thymeleaf UI companion

## Status

Accepted (2026-08-14, on SDD approval)

## Context

The PDF stretch list includes an admin dashboard showing real-time quota utilization per tenant. REST + springdoc remain the public API contract ([ADR 0005](0005-rest-api-only.md)). Operators still need a simple browser surface for demos without requiring a separate SPA stack.

## Decision

Ship a **Thymeleaf** admin companion UI in v1 under a dedicated `/drl/admin` prefix (keeps the operator surface clearly separated from `/api/v1/**` and leaves the bare root free):

| Item | Lock |
|------|------|
| Paths | `/drl/admin` (rules/tenants), `/drl/admin/quotas` (utilization), `/drl/admin/login` (API-key form) |
| Package | `com.example.ratelimiter.web` |
| Dependency | `spring-boot-starter-thymeleaf` |
| Data | Tenants/rules + live utilization via observe API (server-side calls and/or simple JS poll to `/api/v1/quotas/...`) |
| Auth | Same `X-API-Key` entered at `/drl/admin/login`; held in `HttpSession` for subsequent UI requests (no Spring Security — [ADR 0006](0006-api-key-auth.md)) |

REST remains the public API; Thymeleaf is **not** a second public API contract and does **not** imply GraphQL.

## Consequences

- Demo-friendly utilization view without a frontend build pipeline.
- UI package sits beside `controller/`; security must accept session cookie derived from the API key.
- Slightly larger Boot surface (`thymeleaf` starter) and MockMvc coverage for pages.
- Public contract docs stay focused on `/api/v1/*` ([ADR 0005](0005-rest-api-only.md)).

## Alternatives considered

- **No UI / Swagger only** — insufficient once stretch UI is in v1 scope.
- **SPA (React/Vue)** — richer UX; heavier for a take-home; rejected for v1.
- **Server-rendered without Thymeleaf (Mustache/Freemarker)** — viable; Thymeleaf locked for Boot familiarity.
- **GraphQL admin API** — out of scope; REST observe/rules already suffice.

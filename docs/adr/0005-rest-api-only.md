# ADR 0005: REST public API (+ admin UI companion)

## Status

Proposed

## Context

Clients need quota evaluation, observation, runtime rule configuration, and (in v1) adaptive feedback, plus OpenAPI docs for demos. An admin dashboard UI is also in v1 scope as a **companion** surface ([ADR 0007](0007-admin-thymeleaf-ui.md)). The take-home evaluates clarity of data model and distributed-systems judgment more than protocol fashion.

## Decision

Expose a **REST** API as the **public API contract** in v1, documented with **springdoc-openapi** (Swagger UI), consumed by `curl`, gateways, and evaluators.

Primary resources:

| Path | Role |
|------|------|
| `POST /api/v1/evaluate` | Allow/deny + remaining + reset |
| `GET /api/v1/quotas/{identifier}` | Observe consumption / remaining / reset (+ adaptive fields) |
| `POST /api/v1/adaptive/feedback` | Downstream error-rate feedback ([ADR 0008](0008-adaptive-limits.md)) |
| `/api/v1/rules` | Rule CRUD |

**Thymeleaf** admin pages under `/ui` are an operator companion only — they call the same REST observe/rules semantics and do **not** replace or dual-publish the API (no GraphQL).

GraphQL and gRPC remain out of v1.

## Consequences

- Straightforward resource model and Swagger UX.
- MockMvc tests stay simple for `/api/v1/*`.
- No gateway complexity for field graphs or protobuf contracts in v1.
- Admin UI may add session-cookie handling for the same API key without changing the public REST contract.

## Alternatives considered

- **GraphQL** — useful for custom client graphs; overkill for shallow Evaluate/Rule/Quota resources.
- **gRPC** — strong for high-QPS internal RPC; awkward for take-home Swagger story.
- **REST only, no admin UI** — previous non-goal; superseded for UI by [ADR 0007](0007-admin-thymeleaf-ui.md) once stretch UI entered v1.
- **REST + GraphQL simultaneously** — doubles surface area without v1 payoff.

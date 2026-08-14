# ADR 0009: DigitalOcean deploy with separately provisioned datastores

## Status

Proposed

## Context

The PDF asks for DigitalOcean deployment with ≥2 app replicas behind a load balancer. Production practice (and requester lock) is to **deploy Postgres and Redis separately** from application machines, then connect using **host, username, and password** supplied as environment/secrets on each app instance. Apps must not embed datastore credentials in the image. Local Compose may still run optional Postgres/Redis containers for convenience, using the same env-based connection model.

## Decision

| Item | Lock |
|------|------|
| Datastores | **Separate** Managed PostgreSQL + Managed Redis (or equivalent separately hosted instances) |
| App tier | **2** instances behind HTTPS LB / App Platform load balancing |
| Connection | Env on app machines: `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD`, `SPRING_DATA_REDIS_HOST` / `PORT` / `PASSWORD`, `APP_API_KEY` |
| Deliverables | `deploy/digitalocean/README.md` + App Platform/Droplet spec; [`docs/architecture/digitalocean.md`](../architecture/digitalocean.md) |
| Health | `/actuator/health` |
| Live deploy | **Operator-run** when credentials exist; **CI does not require** DO |
| Local proof | Compose with **2** APIs; optional local DB/Redis **or** point env at external hosts |

Phase 9 ships artifacts + checklist, not a mandatory CI cloud deploy.

## Consequences

- Same 12-factor config locally and in production.
- Operators provision DB/Redis first, then wire apps — clear separation of concerns.
- Firewall/VPC must allow app → Postgres and app → Redis; Redis should require a password in non-local envs.
- Interview story: separate datastores + env credentials + multi-instance apps sharing Redis.

## Alternatives considered

- **Bundling Postgres/Redis on the same Droplet as the app** — rejected for production shape; optional only for local Compose convenience.
- **Credentials baked into image / committed `.env`** — rejected; secrets via machine/App Platform env only.
- **CI auto-deploy to DO** — rejected; no DO account required in CI.
- **Single app instance** — fails ≥2 replica / LB demonstration.

# Distributed Rate Limiter

Horizontally scalable rate-limit REST service (Java 21, Spring Boot 4.0.x). Postgres is the durable system of record for rules; Redis holds the shared rule cache, quota counters (Lua), and adaptive multipliers. Two API replicas share Redis so admission stays correct across instances.

Authoritative plan: [`docs/sdd/distributed-rate-limiter.md`](docs/sdd/distributed-rate-limiter.md) · ADRs: [`docs/adr/`](docs/adr/) · Architecture: [`docs/architecture/`](docs/architecture/) · Write-up: [`REFLECTION.md`](REFLECTION.md)

## Prerequisites

- **Java 21** (LTS)
- Maven Wrapper (`./mvnw`) — no global Maven required
- For local multi-replica demo: Docker + Docker Compose
- Env-based config only (no hardcoded hosts/secrets)

## Quick start (Compose)

```bash
# Optional: override placeholders (default APP_API_KEY=change-me)
export APP_API_KEY=change-me

docker compose up --build
```

| Service | Host port |
|---------|-----------|
| api1 | `8080` |
| api2 | `8081` |
| Postgres 16 (optional local) | `5432` |
| Redis 7 (optional local) | `6379` |

Health (public): `GET http://localhost:8080/actuator/health`  
Swagger: `http://localhost:8080/swagger-ui.html`  
Admin UI: `http://localhost:8080/drl/admin/login` (same API key → session)

Protected APIs require header `X-API-Key: <APP_API_KEY>`.

Point `SPRING_DATASOURCE_*` / `SPRING_DATA_REDIS_*` at external hosts and run `docker compose up --no-deps api1 api2` to match the production “separate datastores” shape.

## API overview

| Method | Path | Auth |
|--------|------|------|
| `POST` | `/api/v1/evaluate` | API key |
| `GET` | `/api/v1/quotas/{identifier}?namespace=` | API key |
| `POST/GET/PUT/DELETE` | `/api/v1/rules` | API key |
| `POST` | `/api/v1/adaptive/feedback` | API key |
| `GET` | `/actuator/health` | public |
| UI | `/drl/admin`, `/drl/admin/quotas`, `/drl/admin/login` | session API key |

### Adaptive feedback

```bash
curl -s -X POST http://localhost:8080/api/v1/adaptive/feedback \
  -H "X-API-Key: change-me" -H "Content-Type: application/json" \
  -d '{"identifier":"tenant-42","namespace":"checkout","downstreamErrorRate":0.55}'
```

Mapping: `≥0.5` → 0.25×, `≥0.2` → 0.5×, else 1.0×. Redis key TTL **120s**. Per-rule `adaptiveEnabled` (default true) ignores adapt state when false.

## Algorithms

| Algorithm | Semantics |
|-----------|-----------|
| `TOKEN_BUCKET` | Burst up to `burstCapacity`; refill at `refillPerSecond`; evaluate consumes 1 token when allowed |
| `SLIDING_WINDOW` | At most `limit` allows in a rolling `windowSeconds` (counter-based approximation) |

Production path: Redis **Lua** scripts (atomic per counter key). Java engines in `limiter/` exist for unit/parity tests. Shard-ready keys (`…:{shardId}`); v1 `app.rate-limit.counter-shards=1`.

## Tests

```bash
./mvnw test     # Surefire *Test — no Docker required
./mvnw verify   # Failsafe *IT — Testcontainers Postgres + Redis (CI)
```

## Admin UI

1. Open `/drl/admin/login` and enter `APP_API_KEY`.
2. **Rules** — `/drl/admin` lists tenants/rules.
3. **Quotas** — `/drl/admin/quotas` shows live utilization via observe (polls every 3s).

## DigitalOcean

Separate Managed Postgres + Redis; **2** App Platform instances; Dockerfile build (Java is not a buildpack language). Runbook: [`deploy/digitalocean/README.md`](deploy/digitalocean/README.md) · spec: [`.do/app.yaml`](.do/app.yaml) · topology: [`docs/architecture/digitalocean.md`](docs/architecture/digitalocean.md).

## Known limitations

- Sliding-window counters approximate a perfect request log (documented in `REFLECTION.md`).
- Rule cache TTL 60s is a safety net; CRUD write-through is the primary coherence path.
- Adaptive tighten auto-relaxes after 120s without feedback; feedback is trusted to API-key holders.
- No Prometheus, JWT/RBAC, GraphQL, or fixed-window algorithm in v1 (see `REFLECTION.md` §4).

## Docs map

| Doc | Role |
|-----|------|
| [`docs/sdd/distributed-rate-limiter.md`](docs/sdd/distributed-rate-limiter.md) | Approved plan |
| [`docs/adr/`](docs/adr/) | Accepted ADRs 0001–0010 |
| [`docs/architecture/request-lifecycle.md`](docs/architecture/request-lifecycle.md) | Evaluate / observe / CRUD sequences |
| [`REFLECTION.md`](REFLECTION.md) | Races, limits, residual backlog |

# DigitalOcean deploy runbook

Production-shaped deploy for the Distributed Rate Limiter ([ADR 0009](../../docs/adr/0009-digitalocean-deploy.md)).

**Shape:** separately provisioned Managed PostgreSQL 16 + Managed Redis 7; **2** App Platform instances behind the platform HTTPS load balancer; apps connect with host / username / password from App Platform secrets — never baked into the image.

CI does **not** require a DigitalOcean account. Live deploy is operator-run when credentials exist.

## Why a Dockerfile

App Platform Cloud Native Buildpacks cover Node, Python, Ruby, PHP, Go, and .NET — **not Java**. This repo’s root [`Dockerfile`](../../Dockerfile) is the build source (`dockerfile_path: Dockerfile` in [`.do/app.yaml`](../../.do/app.yaml)). DigitalOcean builds the image from GitHub; you do not need a local Docker daemon to deploy.

## Prerequisites

1. DigitalOcean account + `doctl` (optional) or App Platform UI.
2. GitHub repo connected to App Platform.
3. Provision **before** creating the app:
   - **Managed PostgreSQL 16** (note host, port, database, user, password; SSL as required by DO).
   - **Managed Redis 7** (note host, port, password).
4. Network: app → Postgres and app → Redis allowed (trusted sources / VPC as applicable).

## Connection env (secrets)

Set these on **each** app instance (App Platform encrypted env / secrets). Placeholders only — never commit real values.

| Variable | Purpose |
|----------|---------|
| `SPRING_DATASOURCE_URL` | JDBC URL, e.g. `jdbc:postgresql://HOST:25060/defaultdb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Postgres username |
| `SPRING_DATASOURCE_PASSWORD` | Postgres password |
| `SPRING_DATA_REDIS_HOST` | Redis host |
| `SPRING_DATA_REDIS_PORT` | Redis port (often `25061` on DO Managed Redis) |
| `SPRING_DATA_REDIS_PASSWORD` | Redis password |
| `APP_API_KEY` | Shared API key for `/api/v1/**` and admin login |

Optional: `APP_RATE_LIMIT_COUNTER_SHARDS` (default `1`).

## App Platform steps

1. Create an App from this GitHub repo.
2. Use **Dockerfile** build (`dockerfile_path: Dockerfile`) — see [`.do/app.yaml`](../../.do/app.yaml) as a starting spec.
3. Set **instance count = 2** (same component, horizontal scale).
4. HTTP port **8080**; health check path **`/actuator/health`**.
5. Wire the secrets table above (no datastore credentials in the image).
6. Deploy; wait until both instances are healthy.

Alternatively: `doctl apps create --spec .do/app.yaml` after substituting secret values / DB connection strings via App Platform secret store.

## Cross-instance demo checklist

Operator-run smoke after deploy:

1. Confirm `GET https://<app-url>/actuator/health` is healthy (DB + Redis components up).
2. Create a rule via `POST /api/v1/rules` with `X-API-Key` (Swagger at `/swagger-ui.html` also works).
3. Hammer `POST /api/v1/evaluate` through the **public HTTPS URL** (LB) from two clients / shells — both instances share Redis counters, so total allows ≤ configured limit.
4. `GET /api/v1/quotas/{identifier}?namespace=` — consumed / remaining coherent across calls.
5. Optional: open `/drl/admin/login`, sign in with the same API key, watch utilization on `/drl/admin/quotas`.
6. Optional: `POST /api/v1/adaptive/feedback` with a high `downstreamErrorRate`; confirm subsequent evaluate admits fewer requests and observe shows adaptive fields.

## Local proof (no DO account)

```bash
docker compose up --build
# api1 :8080, api2 :8081 — same env model, optional local Postgres/Redis
./mvnw test          # Surefire, no Docker
./mvnw verify        # Failsafe + Testcontainers (CI)
```

## Architecture

See [`docs/architecture/digitalocean.md`](../../docs/architecture/digitalocean.md).

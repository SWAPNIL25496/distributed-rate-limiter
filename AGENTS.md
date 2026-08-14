# Distributed Rate Limiter — Agent defaults

Personal SDD-enabled repo. Tracking is branch / PR only.

## Planned work

1. `architect-planner` → `docs/sdd/<slug>.md` + `docs/adr/`
2. User approves SDD (clear `DRAFT`)
3. `phase-executor` one phase per pass on `<slug>/phase-N`
4. `doc-sync` (Status / Notes; preserve `PR`)
5. `test-runner` → `docs/sdd/test-reports/<slug>.md`
6. `doc-sync` again → `Verified`
7. Parent: **one commit per verified phase**; **push / PR only when the user asks**

## Docs

- SDD: `docs/sdd/` (primary: `docs/sdd/distributed-rate-limiter.md` — **DRAFT** until approved)
- Stack precursor: `docs/sdd/build-plan-stack.md` (locked choices; plan SoT is the full SDD)
- ADR: `docs/adr/` (0001–0010 Proposed → Accepted on SDD approval)
- Architecture: `docs/architecture/request-lifecycle.md`, `docs/architecture/digitalocean.md`
- Test reports: `docs/sdd/test-reports/`
- Investigation: `docs/investigation/`
- Problem brief: `problemStatement/`
- Write-up: `REFLECTION.md` (Phase 6; residual backlog in §4 — **not** UI / adaptive / DO; those are Phases 7–9)
- Authoritative plan for the repo: `docs/sdd/distributed-rate-limiter.md` + `docs/adr/`

## Stack reminders

Java 25, Spring Boot 4.0.x, Maven, package-by-layer (`com.example.ratelimiter` incl. `web/`), Postgres 16 + JPA + Flyway (rules SoR), Redis 7 (rule cache + **shard-ready** counters + adaptive keys, Lua evaluate), REST + springdoc, Thymeleaf admin UI (`/ui`), adaptive feedback API, `X-API-Key`, **2** API replicas, Postgres/Redis **deployed separately** and connected via env host/username/password, DigitalOcean artifacts (Phase 9), no Prometheus / no JWT / no GraphQL in v1.

Phases **7–9** (in v1): admin Thymeleaf UI → adaptive limits → DigitalOcean deploy docs/spec.

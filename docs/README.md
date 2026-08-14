# Documentation

SDD-enabled personal Distributed Rate Limiter repo. Tracking is branch / PR only.

| Path | Purpose |
|------|---------|
| [`sdd/`](sdd/) | Feature Software Design Documents |
| [`sdd/distributed-rate-limiter.md`](sdd/distributed-rate-limiter.md) | Primary feature SDD — **DRAFT** (approve before phases); v1 includes UI + adaptive + DO (Phases 7–9) |
| [`sdd/build-plan-stack.md`](sdd/build-plan-stack.md) | Locked stack precursor (pointer → full SDD) |
| [`sdd/test-reports/`](sdd/test-reports/) | Verification reports (filled by `test-runner`) |
| [`adr/`](adr/) | Architectural Decision Records (0001–0009 Proposed) |
| [`architecture/request-lifecycle.md`](architecture/request-lifecycle.md) | Mermaid request lifecycle (2 APIs + Postgres + Redis + adaptive) |
| [`architecture/digitalocean.md`](architecture/digitalocean.md) | DigitalOcean App Platform topology (Phase 9) |
| [`investigation/`](investigation/) | Code investigation reports |
| [`../problemStatement/`](../problemStatement/) | Original PDF brief |
| Root `REFLECTION.md` | Write-up / races / limitations + residual §4 backlog — Phase 6 |

## Workflow

1. `architect-planner` → `docs/sdd/<slug>.md` + ADRs (mark `DRAFT` until approved)
2. User approves SDD (clear `DRAFT`); ADRs → Accepted
3. `phase-executor` → one phase on `<slug>/phase-N`
4. `doc-sync` → Status / Notes (preserve `PR`)
5. `test-runner` → `docs/sdd/test-reports/<slug>.md`
6. `doc-sync` again → Verified + link report
7. Parent: per-phase commit; push / stacked PR **only when asked**

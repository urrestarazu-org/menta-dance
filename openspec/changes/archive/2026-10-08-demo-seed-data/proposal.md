# Proposal: Demo seed data and role test users (#379)

## Intent

There is no repeatable way to show or test the app end to end with realistic data. A `demo` profile seeds the 4 README users and a coherent catalog on startup. It is idempotent, follows module boundaries, and can never run in `prod`.

## Scope

### In Scope
- `DemoProfileGuard` (api:app): fails startup when `demo` is active together with `prod`/`production`/`staging`. Profile matching is case-insensitive.
- `application-demo.yml`: README default passwords and dev secrets, defined only here and overridable by env vars.
- Auth: ACTIVE users admin/profesor/alumno/recepcion@menta.dance (ADMIN/INSTRUCTOR/STUDENT/RECEPTIONIST).
- Virtual: "Bachata Sensual" and "Bachata Tradicional", published, 2 modules each, free + restricted lessons, local Bunny.
- Physical: "Bachata Sensual Presencial" (Tue/Thu 19:00, cap 12) and "Bachata Tradicional Presencial" (Sat 11:00, cap 15). Sessions for the next 4 weeks plus one rolling session ~10 min ahead (D1). Student holds a confirmed seat in the next Sensual session. QR reader secret comes from `DEMO_QR_READER_SECRET`: skipped with a WARN when unset, hash rotated when it changes (D3).
- Billing: prices of 25000/20000 ARS. "Plan Virtual" (both virtual courses) and "Plan Completo" (all 4 courses). Student gets a 90-day TRIAL on Plan Completo (D2).
- An ArchUnit rule: `..infrastructure.demo..` depends only on its own module and `api:shared`. Plus README/docs updates.

### Out of Scope
- Flyway data migrations, a global "empty DB" gate (D4: idempotency is per entity), stubbing Mercado Pago under `demo`, BFF/Android changes.
- Any coupling between the seat and the subscription. The code does not have one, so none is invented.

## Capabilities

### New Capabilities
- `demo-seed-data`: demo profile guard, seeded users, catalog, plans, subscription, seat and reader, idempotency.

### Modified Capabilities
- `local-bunny-net`: the local adapter is also selected under `demo`, with the same fail-closed rule.

## Approach

This is Approach 1 from the exploration. Each module owns an `ApplicationRunner` + `Ordered` fixture under `infrastructure/demo`, using in-ports where they exist and its own repositories otherwise. Fixed UUIDs live in `api:shared`. Every entity is upserted by a stable key. Run order: Auth, then Virtual/Physical, then Billing.

## Slice Plan (chained; total ~1,360 lines vs 800 budget)

| # | Slice | ~Lines |
|---|---|---|
| 1 | Guard, yml, shared ids, Auth users, ArchUnit rule | 420 |
| 2 | Virtual content + Bunny `@Profile` | 260 |
| 3 | Physical courses, sessions, seat, reader | 340 |
| 4 | Billing pricing, plans, trial + docs | 340 |

Delivery risk: the total exceeds the budget. Chained PRs are needed under `ask-on-risk`, and the orchestrator must confirm this.

## Affected Areas

| Area | Impact |
|---|---|
| `api/app/.../DemoProfileGuard`, `application-demo.yml` | New |
| `api/shared/.../demo` ids | New |
| `api/{auth,virtual,physical,billing}/.../infrastructure/demo` | New |
| `VirtualConfiguration(Test)` | Modified |
| ArchUnit tests, `README.md`, `docs/26-LOCAL-DEV-SETUP-HOWTO.md` | Modified |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `demo` reaches prod | Low | Central guard + test that fails when `demo` and `prod` are active together |
| Proof-token secret has no default | High | Demo-only value in `application-demo.yml` |
| JPA seeding bypasses invariants | Med | Use in-ports first; JPA only for plans and the reader |
| Coverage gates (0.90+) | Med | Mockito tests per fixture |
| Rolling session/trial go stale | Med | Session reused/replaced on every start; trial assigned only when the student has no active subscription |
| Real Mercado Pago active | Low | Documented; out of scope |

## Rollback Plan

Revert the slice PRs. Production paths are unaffected without `demo`. Seeded rows can be dropped by recreating the local DB.

## Success Criteria

- [ ] `bootRun` with `demo`: all 4 users can log in.
- [ ] A second start creates no duplicates.
- [ ] The catalog, plans, trial and seat are visible. The reader works when the env secret is set.
- [ ] A test fails when `demo` + `prod` are active together.
- [ ] `./gradlew check` is green (ArchUnit, JaCoCo, Checkstyle, SpotBugs).

## Proposal question round

The product questions were already answered (decisions D1-D5, Engram `sdd/demo-seed-data/decisions`). Schedules, prices and capacities are the agent's proposal; the user only renamed the courses.

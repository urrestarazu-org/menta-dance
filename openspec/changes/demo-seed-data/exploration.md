# Exploration: demo-seed-data (GitHub issue #379)

Canonical copy of Engram `sdd/demo-seed-data/explore` (#1732). Read-only investigation on `origin/develop`.

## Current State

### (a) E2E fixture pattern
- Profile-scoped `ApplicationRunner` + `Ordered` in `infrastructure/e2e` per module.
- Auth: `E2eCatalogContentAuthFixture.java:22-64` (creates the user only if `findByEmail` is empty; own prod guard `{prod, production, staging}`).
- Virtual (use-case style): `E2eCatalogContentVirtualFixture.java:23-85` (in-ports only, idempotent by title).
- Virtual (fixed-id style): `E2eBunnyNetVirtualFixture.java:31-110` (own JPA repos, `existsById` guard).
- Billing: `E2eMercadoPagoBillingFixture.java:18-51`, `E2eBunnyNetBillingFixture.java:20-50` (duplicates the Virtual UUID literal on purpose).
- Design intent: `openspec/changes/local-catalog-content-e2e/design.md:47-69,140-142` (module-owned fixtures, upsert by stable key, no foreign repos, no Flyway seed).
- Reusable: runner shape, idempotency by stable key, Mockito test shape, fixed UUIDs. Not reusable: `e2e-*` gating. Nothing seeds physical, subscriptions, seats or devices.

### (b) Auth
- `User.create(email, hash, role)` yields an ACTIVE user with no outbox/email (`User.java:74-89`). `User.rehydrate` allows a chosen id.
- Roles: ADMIN, INSTRUCTOR, STUDENT, RECEPTIONIST.
- Only shared ports: id→exists, id→email. No email→id lookup.
- README credentials: `README.md:316-325`, `docs/26-LOCAL-DEV-SETUP-HOWTO.md:928-937`.

### (c) Creation paths
- Virtual: course/module/lesson/publish use cases exist; publish needs ≥1 module and ≥1 complete lesson. Ids are random.
- Local Bunny signer is selected only by `@Profile("e2e-bunny-net")` (`VirtualConfiguration.java:128-150`); `demo` needs the expression extended (+ `VirtualConfigurationTest`).
- Physical course/session use cases exist; domain constructors accept fixed ids.
- Physical price lives in Billing: `UpdatePhysicalCoursePricingUseCase` (needs the course to exist).
- Plans: no create-plan use case; seed via `PlanJpaEntity` + `PlanPaymentMethodJpaEntity` + `PlanCourseJpaEntity`.
- Active subscription: `AssignTrialSubscriptionUseCase` → ACTIVE, type TRIAL, expires after N days.
- Seat: `PhysicalCapacityAssignmentPort.assign` takes any non-null `paymentId`; a repeat call throws → pre-check `existsConfirmedAssignment`.
- QR issuance only within [scheduledAt−30m, scheduledAt+2h].
- QR reader: `RegisterPhysicalDeviceUseCase` always generates a random secret → env-supplied secret needs `PhysicalDeviceRepository.save` + `DeviceSecretHasher` inside Physical.

### (d) Profiles in api:app
- No `application-prod.yml` or `application-demo.yml`. Prod guards are scattered across Auth/Billing/Virtual with the same literal set.
- `BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET` has no default (`application.yml:177`) → `bootRun` with `demo` fails unless supplied.

### (e) Gates
- ArchUnit allows `..infrastructure.demo..`; add a rule: demo fixtures depend on no other `com.menta.<module>` except shared.
- JaCoCo layered LINE gates include fixtures: auth 1.00/0.85, billing 0.85/0.85, virtual 0.95/0.90, physical 0.95/0.90, shared 0.85, app 0.90.
- Checkstyle: line length 120, indent 4, Javadoc on top-level types. SpotBugs/FindSecBugs HIGH.
- Default passwords go in `application-demo.yml`, not Java constants.

### (f) Ordering
1. Auth users. 2. Virtual + Physical content (seat and device in Physical). 3. Billing (plans → links → pricing → trial).
The seat has no code coupling to a subscription.

## Approaches
1. **Module-owned fixtures + fixed UUIDs in `api:shared` + one central guard in `api:app`** (recommended). Medium.
2. Same with UUID literals and per-module guard copies. Medium+.
3. Orchestrator in `api:app` calling all in-ports. High; violates the module-boundary criterion.

## Open product questions
1. QR window: rolling session ~10 min out (stale after ~2.5h) or manual session?
2. Student subscription: TRIAL (N days) acceptable, or must look paid?
3. `DEMO_QR_READER_SECRET` unset: skip with WARN or fail startup? Changed on re-run: rotate or keep?
4. "DB empty": per-entity idempotent (recommended) or global zero-users gate?
5. Concrete catalog (plans ↔ courses, physical days/times/prices/capacity, number of plans, instructor ownership).

## Risks
- `bootRun` demo needs the proof-token secret.
- `@Profile` change touches `VirtualConfigurationTest`.
- Real Mercado Pago adapters remain active under `demo`.
- Fixed-id JPA seeding bypasses use-case invariants.
- Coverage gates (0.90) include new fixtures.
- Trial expires; the guard must use `Environment.getActiveProfiles`, case-insensitive.
- Issue text "seat needs subscription" has no code coupling.

## Size estimate
| Slice | ~Lines |
|---|---|
| Guard, yml, shared ids, Auth users | 370 |
| Virtual content + Bunny profile change | 260 |
| Physical courses, seat, device | 340 |
| Billing plans, pricing, trial | 290 |
| ArchUnit + docs | 100 |

Total ≈ 1,300–1,400 lines > 800 budget → 3–4 chained slices.

## Ready for Proposal
Yes, after the product questions.

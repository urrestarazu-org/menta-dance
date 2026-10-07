# Design: Demo seed data and role test users (#379)

## Technical Approach

Exploration Approach 1. A central guard in `api:app` aborts context refresh. One fixed-id holder lives in `api:shared`. Each module owns `ApplicationRunner + Ordered` fixtures under `com.menta.<module>.infrastructure.demo`, annotated `@Profile("demo")`. Each entity is upserted by a stable key. The pattern follows the `infrastructure/e2e` fixtures.

## Architecture Decisions

| Topic | Choice | Rejected | Rationale |
|---|---|---|---|
| Guard | `com.menta.app.demo.DemoProfileGuard`, an unconditional `@Component` that validates `Environment.getActiveProfiles()` in its constructor. Matching is case-insensitive (`Locale.ROOT`): if `demo` is active together with `prod`/`production`/`staging`, it throws `IllegalStateException`. | `ApplicationRunner` (runs after the web server has started); `EnvironmentPostProcessor` (needs `spring.factories` and is harder to test); per-fixture copies | A throw in a constructor aborts refresh. Spring runs runners only after refresh, so no fixture can execute. The guard has no `@Profile`, so a typo in a profile name cannot disable it. |
| Fixed ids | `com.menta.shared.demo.DemoIds` (`final`, private constructor, `public static final UUID` only). It holds the 4 users and the 4 courses, which are the only ids that cross module boundaries. | Duplicated literals as in e2e; all ids in shared | No logic. Ids private to one module (modules, lessons, plans, rolling session, device, seat payment) stay in that module's fixture. |
| Ordering | `com.menta.shared.demo.DemoSeedOrder`: `AUTH = HIGHEST_PRECEDENCE + 100`, `CONTENT = +200` (Virtual and Physical), `BILLING = +300`. These are `int` constants with no Spring dependency. | Imports between fixtures | No module imports another module. A shared test asserts `AUTH < CONTENT < BILLING`, and each fixture test asserts its own constant. A wrong order fails loudly: billing pricing throws `PhysicalCourseNotFoundException`, and the trial throws `UserNotFoundException`. |
| Transactions | Fixtures are **not** `@Transactional`. | Wrapping the whole run | `AssignCapacityUseCase` uses `REQUIRES_NEW`. A session that is still uncommitted would be invisible to it and would cause `SessionNotFoundException`. |
| Errors | Any exception aborts startup, except one case: a blank QR secret logs a WARN and skips the reader (D3). | Swallowing errors | Partial demo data is worse than an explicit failure. |

### Per-entity idempotency

| Entity | Key | Path |
|---|---|---|
| Users | email; the id must equal `DemoIds` | `UserRepository.findByEmail`, then `User.rehydrate(fixedId, ..., ACTIVE)`, hashed with `PasswordEncoderPort`. The same email with a different id throws ("recreate the local DB"). Existing passwords are never reset. |
| Virtual course/module/lesson | fixed id, checked with `existsById` per entity | Virtual JPA repositories (the `E2eBunnyNetVirtualFixture` style), `CourseStatus.PUBLISHED`. Module 1 is `preview=true` with a `free=true` lesson. Module 2 is restricted. |
| Physical course | `DemoIds` course id | `PhysicalCourseRepository.findById`, then `save(new PhysicalCourse(...))` with `professorId = INSTRUCTOR` |
| Regular sessions | `(courseId, scheduledAt)` | `findManaged(course, today, today+28d)`. Any missing slot is created with `saveAll(PhysicalSession.create(...))`. Times are in UTC, following `BatchCreatePhysicalSessionsUseCaseImpl`. |
| Rolling session | fixed private id | See below |
| Seat | `existsConfirmedAssignment(session, STUDENT)` | `PhysicalCapacityAssignmentPort.assign(new CapacityAssignmentCommand(session, STUDENT, SEAT_PAYMENT_ID))`. `paymentId` is a fixed private UUID that the assign path never reads. |
| QR reader | fixed private `DeviceId` | `PhysicalDeviceRepository.findById`. If absent, `save(new PhysicalDevice(..., hasher.hash(secret), ACTIVE, null, now, now))`. If the hash differs and the device is `ACTIVE`, `rotateSecret`. If `REVOKED`, log a WARN and leave it. No audit row is written (`PhysicalDeviceAuditSnapshot` is package-private). |
| Plans | fixed private plan id, `existsById` | `PlanJpaRepository`. Links are checked with `findByPlanId` before every `PlanPaymentMethodJpaEntity` (MERCADO_PAGO, BANK_TRANSFER) and `PlanCourseJpaEntity`. |
| Physical pricing | `PhysicalCoursePricingRepository.findByCourseId` | Absent → `UpdatePhysicalCoursePricingUseCase.update(..., ADMIN, true)` |
| Trial | `SubscriptionRepository.findCurrentByUserId(STUDENT)` empty | `AssignTrialSubscriptionUseCase.assign(new AssignTrialCommand(STUDENT, completoId, ADMIN, true, "Demo seed", 90))` |

### Rolling session (D1)

`findById(ROLLING_ID)`:
- Absent: save a new session `(ROLLING_ID, SENSUAL, now+10m, cap 12, SCHEDULED, notes)`.
- `now ∈ [scheduledAt − before, scheduledAt + after − 30m]`: reuse it. `before` and `after` come from `QrProperties`, defaulting to 30m and 2h.
- Otherwise: `save(session.withSchedule(now+10m))`. The id and seat do not change, so nothing accumulates.

After that, the fixture ensures a seat on the rolling session **and** on the next regular Sensual session.

## Data Flow

    refresh: DemoProfileGuard ctor ──(throws on demo+prod)──X
    runners: Auth(100) → Virtual, Physical catalog, QR reader (200) → Billing (300)
             users        courses/sessions/seats/device             plans → links → pricing → trial

## application-demo.yml (api:app)

`demo.users.{admin,instructor,student,receptionist}-password: ${DEMO_<ROLE>_PASSWORD:<README value>}`. `billing.bank-transfer.proof.token-secret: ${BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET:demo-only-...}`. `demo.qr-reader.secret: ${DEMO_QR_READER_SECRET:}`. The empty default is deliberate: an unresolved placeholder would fail startup, which contradicts D3. Passwords stay out of Java, which avoids FindSecBugs `HARD_CODE_PASSWORD`.

## File Changes (slices ≤400 lines; the review budget is 400, not 800)

| # | Files | ~Lines |
|---|---|---|
| 1 | `app/.../demo/DemoProfileGuard` (+Test), `application-demo.yml`, `shared/.../demo/DemoIds`, `DemoSeedOrder` (+Test), `auth/.../infrastructure/demo/DemoUserFixture` (+Test) | 330 |
| 2 | `virtual/.../demo/DemoVirtualCatalogFixture` (+Test), `VirtualConfiguration` (`@Profile("e2e-bunny-net \| demo")` / `"!e2e-bunny-net & !demo"`, generic guard message), `VirtualConfigurationTest` (demo selects the local adapter; demo+prod fails) | 300 |
| 3 | `physical/.../demo/DemoPhysicalCatalogFixture` (+Test): courses, sessions, rolling session, seats | 380 |
| 4 | `physical/.../demo/DemoQrReaderFixture` (+Test) | 190 |
| 5 | `billing/.../demo/DemoBillingFixture` (+Test) | 330 |
| 6 | ArchUnit rules in `app/.../ArchitectureTest`, `README.md`, `docs/26-LOCAL-DEV-SETUP-HOWTO.md` | 130 |

Every slice is green on its own: later slices only add runners, and compile-time dependencies flow only toward `DemoIds` in slice 1.

## Testing Strategy (Strict TDD, RED first)

| Unit | Approach |
|---|---|
| Guard | Uses `MockEnvironment`. Cases: `demo+prod`, `DEMO+Production`, and `demo+staging` all throw; `demo` alone, `prod` alone, and no profiles pass. |
| Each fixture | Mockito tests: first run creates; second run is a no-op (`never().save`); asserts `getOrder()`. Auth also covers the id-mismatch throw. Physical covers rolling absent/reuse/reschedule and seat present/absent. The reader covers blank secret (WARN, no repository calls), create, rotate, same hash, and REVOKED. Billing covers trial skipped when a current subscription exists. |
| ArchUnit | Imports `com.menta`. Rule: `com.menta.shared.demo..` is used only from `..infrastructure.demo..`, and nothing outside `..demo..` depends on `..infrastructure.demo..`. |
| Gates | Checkstyle (120 columns, Javadoc on top-level types). SpotBugs: final classes, constructor injection, the secret is never logged. JaCoCo infra floors (0.85/0.90) are met by full branch tests. JaCoCo filters the private constructor of `DemoIds`. |

## Threat Matrix

N/A: no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary.

## Proposal corrections

1. Virtual cannot use its in-ports: they generate random ids, and Billing needs fixed course ids. Virtual therefore seeds through JPA with fixed ids, and Physical seeds through its domain repository ports.
2. `PhysicalCourse` has a single `dayOfWeek`. Sensual stores `TUESDAY`, and the fixture generates both Tuesday and Thursday sessions directly.
3. QR issuance requires a seat on the rolling session itself, not only on the next regular session.
4. The ArchUnit rule as proposed is already enforced at compile time, because modules depend only on `:api:shared`. It is replaced with the reverse-dependency rules.

## Migration / Rollout

No migration required. Only data that is gated by the `demo` profile is added.

## Open Questions

- [ ] Plan prices and durations are not decided. Proposed: Plan Virtual 30000 ARS / 30 days, Plan Completo 60000 ARS / 30 days. Physical surcharge proposed at 20%.
- [ ] Once the rolling session has an attendance row, a later check-in after restart replays the existing attendance (idempotent). A fresh check-in requires recreating the DB. Accept this?

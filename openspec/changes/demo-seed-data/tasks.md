# Tasks: Demo seed data and role test users (#379)

Strict TDD: every task pair is RED (failing test first, run and observe failure) then GREEN (minimal production code). REFACTOR only inside the same slice.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1660 total (S1 330, S2 300, S3 380, S4 190, S5 330, S6 130) |
| 400-line budget risk | High as a single PR; Low per slice (max 380) |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 -> PR 2 -> PR 3 -> PR 4 -> PR 5 -> PR 6 |
| Delivery strategy | auto-chain |
| Chain strategy | feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

Budget used by the design: 400 changed lines per PR (additions + deletions), not 800. Slice 3 (380) has only 20 lines of headroom: if it overflows, move the rolling-session logic into its own slice before opening the PR.

### Branch / PR chain

| PR | Branch | Base | Link |
|----|--------|------|------|
| 1 | `feature/379-demo-seed-1-guard-auth` | `develop` | `Refs #379` |
| 2 | `feature/379-demo-seed-2-virtual` | PR 1 branch | `Refs #379` |
| 3 | `feature/379-demo-seed-3-physical-catalog` | PR 2 branch | `Refs #379` |
| 4 | `feature/379-demo-seed-4-qr-reader` | PR 3 branch | `Refs #379` |
| 5 | `feature/379-demo-seed-5-billing` | PR 4 branch | `Refs #379` |
| 6 | `feature/379-demo-seed-6-archunit-docs` | PR 5 branch | `Closes #379` |

Rules: a child PR must show only its own diff (wrong base -> retarget). When a parent merges, retarget the child to `develop` and rebase. Check `gh pr list --head <branch>` before creating each PR. Track each PR on project board 1.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Guard, demo yml, shared ids/order, 4 users | PR 1 | `./gradlew :api:app:test --tests "*DemoProfileGuardTest" :api:auth:test --tests "*DemoUserFixtureTest" :api:shared:test --tests "*DemoSeedOrderTest"` | `bootRun` with `demo` on an empty DB: 4 users log in; `demo,prod` aborts | Revert PR 1 (nothing else depends on it at runtime) |
| 2 | Local Bunny under `demo` + virtual catalog | PR 2 | `./gradlew :api:virtual:test --tests "*VirtualConfigurationTest" --tests "*DemoVirtualCatalogFixtureTest"` | `bootRun` demo: list published courses, request free lesson stream | Revert PR 2; `@Profile` change in `VirtualConfiguration` reverts with it |
| 3 | Physical courses, sessions, rolling, seat | PR 3 | `./gradlew :api:physical:test --tests "*DemoPhysicalCatalogFixtureTest"` | `bootRun` demo twice: counts stable, one rolling session | Revert PR 3 |
| 4 | QR reader device seed | PR 4 | `./gradlew :api:physical:test --tests "*DemoQrReaderFixtureTest"` | `bootRun` with and without `DEMO_QR_READER_SECRET` | Revert PR 4 |
| 5 | Plans, prices, links, trial | PR 5 | `./gradlew :api:billing:test --tests "*DemoBillingFixtureTest"` | `bootRun` demo: plans, prices, trial subscription present; second start no duplicates | Revert PR 5 |
| 6 | ArchUnit rules + docs | PR 6 | `./gradlew :api:app:test --tests "*ArchitectureTest"` | N/A: rules and docs only | Revert PR 6 |

## Slice 1 (PR 1, ~330 lines): Guard, config, shared ids, Auth users

- [x] 1.1 RED `api/app/src/test/java/com/menta/app/demo/DemoProfileGuardTest.java`: `MockEnvironment`; `demo+prod`, `DEMO+Production`, `demo+staging` throw `IllegalStateException`; `demo` alone, `prod` alone, none pass. Covers: Demo profile guard (all 3 scenarios).
- [x] 1.2 GREEN `api/app/src/main/java/com/menta/app/demo/DemoProfileGuard.java`: unconditional `@Component`, constructor validates `getActiveProfiles()` with `Locale.ROOT`, Javadoc, final class.
- [x] 1.3 RED `api/shared/src/test/java/com/menta/shared/demo/DemoSeedOrderTest.java`: asserts `AUTH < CONTENT < BILLING`. Covers: Run order, "Order respected".
- [x] 1.4 GREEN `api/shared/src/main/java/com/menta/shared/demo/DemoSeedOrder.java` (`HIGHEST_PRECEDENCE + 100/200/300`) and `DemoIds.java` (4 user UUIDs, 4 course UUIDs, private constructor). Add JaCoCo exclusion for `DemoIds` private constructor if the gate flags it.
- [x] 1.5 GREEN `api/app/src/main/resources/application-demo.yml`: `demo.users.{admin,instructor,student,receptionist}-password: ${DEMO_<ROLE>_PASSWORD:<README default>}`, `billing.bank-transfer.proof.token-secret` with env override, `demo.qr-reader.secret: ${DEMO_QR_READER_SECRET:}`. Covers: Demo-only configuration. Add a test in `DemoProfileGuardTest` or a small `ApplicationDemoYmlTest` that the file loads with defaults and env override, and that default `application.yml` has none of these keys.
- [x] 1.6 RED `api/auth/src/test/java/com/menta/auth/infrastructure/demo/DemoUserFixtureTest.java`: first run creates 4 ACTIVE users with fixed ids/roles and hashed passwords; second run `never().save`; same email with a different id throws; existing password not reset; no outbox, no email; `getOrder() == AUTH`. Covers: Seeded role users, Per-entity idempotency, Default password / Env var override.
- [x] 1.7 GREEN `api/auth/src/main/java/com/menta/auth/infrastructure/demo/DemoUserFixture.java`: `@Profile("demo")`, `ApplicationRunner + Ordered`, not `@Transactional`, `UserRepository.findByEmail` -> `User.rehydrate(fixedId, ..., ACTIVE)` via `PasswordEncoderPort`; passwords from `@Value`; constructor injection with `@RequiredArgsConstructor`.
- [x] 1.8 REFACTOR: run Checkstyle/SpotBugs, fix Javadoc/line length; confirm no password literal in Java (FindSecBugs `HARD_CODE_PASSWORD`).

Exit gate: `./gradlew :api:shared:check :api:auth:check :api:app:check` green, coverage floors (auth 100/85, app flat 85) met.
Manual: `SPRING_PROFILES_ACTIVE=demo ./gradlew :api:app:bootRun` on an empty DB, log in as each of the 4 users; restart (no duplicates, no failure); `SPRING_PROFILES_ACTIVE=demo,prod` aborts before the server accepts requests.

## Slice 2 (PR 2, ~300 lines): Virtual catalog + Bunny profile

- [x] 2.1 RED `api/virtual/src/test/java/com/menta/virtual/.../VirtualConfigurationTest.java`: `demo` resolves `LocalBunnyNetSignatureService` (one bean); `demo+e2e-bunny-net` one local bean; no profile resolves `StringFormatBunnyNetSignatureService`; `demo+prod|production|staging` fails (case-insensitive). Covers: local-bunny-net delta (all 5 scenarios).
- [x] 2.2 GREEN `api/virtual/src/main/java/com/menta/virtual/.../VirtualConfiguration.java`: `@Profile("e2e-bunny-net | demo")` / `"!e2e-bunny-net & !demo"`; generic guard message.
- [x] 2.3 RED `api/virtual/src/test/java/com/menta/virtual/infrastructure/demo/DemoVirtualCatalogFixtureTest.java`: first run saves 2 PUBLISHED courses, 2 modules each (module 1 `preview=true` with a free lesson, module 2 restricted), fixed ids from `DemoIds`; second run `never().save` (`existsById` per entity); partial DB creates only missing; `getOrder() == CONTENT`; video refs resolve with the local adapter. Covers: Virtual catalog, Per-entity idempotency (second start, partial).
- [x] 2.4 GREEN `api/virtual/src/main/java/com/menta/virtual/infrastructure/demo/DemoVirtualCatalogFixture.java`: `@Profile("demo")`, Virtual JPA repositories (style of `E2eBunnyNetVirtualFixture`), private fixed module/lesson ids.
- [x] 2.5 REFACTOR: Checkstyle/SpotBugs clean.

Exit gate: `./gradlew :api:virtual:check` (floors 95/90) and `:api:app:check`.
Manual: bootRun `demo`; list published courses (2, 2 modules each); request a free lesson stream and get a local signed URL with no Bunny credential.

## Slice 3 (PR 3, ~380 lines): Physical catalog, sessions, rolling session, seats

- [x] 3.1 RED `api/physical/src/test/java/com/menta/physical/infrastructure/demo/DemoPhysicalCatalogFixtureTest.java` (courses): absent -> `save(new PhysicalCourse(...))` with `professorId = INSTRUCTOR` for Sensual (TUESDAY 19:00, cap 12) and Tradicional (SATURDAY 11:00, cap 15); present -> no save. Covers: Physical catalog.
- [x] 3.2 GREEN `.../DemoPhysicalCatalogFixture.java` course upsert via `PhysicalCourseRepository.findById`.
- [x] 3.3 RED regular sessions: `findManaged(course, today, today+28d)` empty creates Tue+Thu (Sensual) and Sat (Tradicional) slots in UTC for 4 weeks via `saveAll`; partial existing creates only missing slots; full -> no save. Covers: Four weeks of sessions, Partially seeded.
- [x] 3.4 GREEN session generation keyed by `(courseId, scheduledAt)`.
- [x] 3.5 RED rolling session (D1): absent -> save `(ROLLING_ID, SENSUAL, now+10m, cap 12, SCHEDULED)`; `now` inside `[scheduledAt - before, scheduledAt + after - 30m]` -> reuse, no save; outside -> `save(withSchedule(now+10m))` with the same id; `QrProperties` defaults 30m/2h. Covers: Rolling session is current, Restart does not accumulate.
- [x] 3.6 GREEN rolling-session logic using an injected `Clock`.
- [x] 3.7 RED seats: `existsConfirmedAssignment` false -> `assign(new CapacityAssignmentCommand(session, STUDENT, SEAT_PAYMENT_ID))` for the rolling session and the next regular Sensual session; true -> no `assign`; no dependency on subscriptions; `getOrder() == CONTENT`. Covers: Student seat (both scenarios), Seat independent of subscription.
- [x] 3.8 GREEN seat assignment via `PhysicalCapacityAssignmentPort`; fixture NOT `@Transactional` (`AssignCapacityUseCase` uses `REQUIRES_NEW`).
- [x] 3.9 REFACTOR: Checkstyle/SpotBugs; split helper methods if the class is too long.

Exit gate: `./gradlew :api:physical:check` (floors 95/90) and `:api:app:check`; line count of the PR <= 400, else split before opening.
Manual: bootRun `demo` twice; sessions for 4 weeks, exactly one rolling session ~10 min ahead, student seat present, counts unchanged on the second run.

## Slice 4 (PR 4, ~190 lines): QR reader device

- [ ] 4.1 RED `.../DemoQrReaderFixtureTest.java`: blank secret -> WARN logged, zero repository calls; set + absent -> `save(new PhysicalDevice(..., hash, ACTIVE, ...))`, no plaintext stored; same hash -> no save; different hash + ACTIVE -> `rotateSecret`; REVOKED -> WARN, no change; `getOrder() == CONTENT`. Covers: QR reader secret (3 scenarios).
- [ ] 4.2 GREEN `api/physical/src/main/java/com/menta/physical/infrastructure/demo/DemoQrReaderFixture.java`: `@Value("${demo.qr-reader.secret:}")`, private fixed `DeviceId`, `PhysicalDeviceRepository`, hasher; the secret is never logged.
- [ ] 4.3 REFACTOR: SpotBugs (no secret in logs), Checkstyle.

Exit gate: `./gradlew :api:physical:check` and `:api:app:check`.
Manual: bootRun `demo` without the variable (WARN, starts); with `DEMO_QR_READER_SECRET=s1`, then `s2` (hash rotates, `s1` no longer verifies).

## Slice 5 (PR 5, ~330 lines): Billing plans, prices, trial

Prices (approved): Plan Virtual 30000 ARS / 30 days; Plan Completo 60000 ARS / 30 days; physical individual surcharge 20%; physical prices 25000 (Sensual) / 20000 (Tradicional).

- [ ] 5.1 RED `.../DemoBillingFixtureTest.java` (plans): absent plan (`existsById` false) -> save with the approved price/duration; present -> none; payment-method links (MERCADO_PAGO, BANK_TRANSFER) and course links checked with `findByPlanId` before each save; Plan Virtual links exactly the 2 virtual courses, Plan Completo all 4. Covers: Billing prices and plans, Second start.
- [ ] 5.2 GREEN `api/billing/src/main/java/com/menta/billing/infrastructure/demo/DemoBillingFixture.java` plan/link upserts via `PlanJpaRepository`, `PlanPaymentMethodJpaEntity`, `PlanCourseJpaEntity`.
- [ ] 5.3 RED physical pricing: `findByCourseId` absent -> `UpdatePhysicalCoursePricingUseCase.update(..., ADMIN, true)` with 25000 / 20000 ARS and the 20% individual surcharge; present -> none. Covers: Plans and prices.
- [ ] 5.4 GREEN pricing step.
- [ ] 5.5 RED trial: `findCurrentByUserId(STUDENT)` empty -> `AssignTrialSubscriptionUseCase.assign(new AssignTrialCommand(STUDENT, completoId, ADMIN, true, "Demo seed", 90))`; current subscription exists -> no assign; `getOrder() == BILLING`. Covers: Student trial subscription (both scenarios).
- [ ] 5.6 GREEN trial step; not `@Transactional`; any exception aborts startup (no swallowing).
- [ ] 5.7 REFACTOR: Checkstyle/SpotBugs; extract private methods if over complexity limits.

Exit gate: `./gradlew :api:billing:check` (floors 85/85) and `:api:app:check`.
Manual: full bootRun `demo` on an empty DB: plans with links, prices, ACTIVE TRIAL on Plan Completo expiring in 90 days; restart: no duplicates, no second subscription.

## Slice 6 (PR 6, ~130 lines): ArchUnit + docs

- [ ] 6.1 RED `api/app/src/test/java/com/menta/app/ArchitectureTest.java`: rules (a) `com.menta.shared.demo..` used only from `..infrastructure.demo..`; (b) nothing outside `..demo..` depends on `..infrastructure.demo..`; (c) no `infrastructure.demo` class depends on another business module. Prove (c) fails with a temporary violating fixture, then remove it. Covers: ArchUnit rule.
- [ ] 6.2 GREEN make the rules pass (no production change expected).
- [ ] 6.3 RED/verify no seed migrations: test or grep-based check that no module `db/migration` file inserts demo data. Covers: No seed migrations.
- [ ] 6.4 GREEN `README.md`: `demo` profile, role credentials, env vars (`DEMO_*_PASSWORD`, `DEMO_QR_READER_SECRET`, `BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET`), real Mercado Pago adapters stay active, guard behavior. Covers: Documentation.
- [ ] 6.5 GREEN `docs/26-LOCAL-DEV-SETUP-HOWTO.md`: same content in Spanish, with run commands.
- [ ] 6.6 Verify the open question "rolling session attendance replay after restart" is documented as accepted behavior (fresh check-in requires recreating the DB).

Exit gate: `./gradlew build` (all modules, Checkstyle, SpotBugs, JaCoCo) green; `./gradlew :api:app:test --tests "*ArchitectureTest"`.
Manual: follow the README steps verbatim on an empty DB (4 users, catalog, QR, plans, trial).

## Cross-slice notes

- Slices are independent at runtime: each adds runners only; compile dependencies point only toward `DemoIds`/`DemoSeedOrder` (slice 1).
- Code in English, documentation and user-facing text in Spanish; use Lombok for new classes.
- Open questions to confirm during apply (do not block): none beyond the approved prices; the rolling-session replay behavior is documented in 6.6.

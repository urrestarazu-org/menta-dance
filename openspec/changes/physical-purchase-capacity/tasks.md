# Tasks: Physical Purchase and Automatic Capacity Assignment (#41, US-PHYSICAL-004)

## Fixed Facts (do not reopen)

- Expired quote → **410 GONE** (design A7 / spec), following
  `PasswordResetTokenExpiredException` → `HttpStatus.GONE`. **409** is exclusive
  to the visibly-full case (D5/A6) on the same route; validity is checked
  before availability.
- Monthly coverage **extends forward, never truncates**, bounded by the
  `COVERAGE_LOOKAHEAD = 120 days` horizon (design A4). A shortfall that
  persists past the horizon resolves to `EXCEPTION` via the existing residual
  path — this is not a new decision to make during implementation.
- `Purchase.physicalSessionId` (singular, `Purchase.java:16`, 58 lines total)
  becomes an ordered `List<String> physicalSessionIds` via a child table
  `billing_purchase_sessions` (design A1).
- `AssignCapacityUseCase` (75 lines) gains `assignAll(...)` under one
  `REQUIRES_NEW` transaction around the whole ordered loop; the existing
  `assign(...)` becomes a singleton delegation — one implementation of the
  capacity invariant, not two (design A3).
- `PaymentTarget.Physical`'s reference becomes the **quoteId**, not a
  sessionId (design A5). No production code constructs it today except
  `PaymentJpaMapper:52` rereading from the DB — but three production
  **consumers** must be updated, including their javadoc, not just renamed:
  `PublishPhysicalPaymentCompletedUseCase:95`, `PaymentCompletedOutboxPayload`,
  `PhysicalCapacityAssignmentOutboxEventHandler:86`.
- Stable claim order is `(scheduledAt ASC, sessionId ASC)`, enforced in the
  `shared` command's compact constructor (design A2) — not left to caller
  discipline.

## Review Workload Forecast

Grounded in `wc -l` on the actual closest precedents (no invented numbers):

| Precedent | Lines | Comparable new/modified artifact |
|---|---|---|
| `CapacityAssignmentCommand.java` | 34 | `MultiSessionCapacityAssignmentCommand` (nested `SessionClaim` + total-order invariant) → ~55-75 |
| `CapacityAssignmentCommandTest.java` | 58 | Its invariant test (empty/dup/non-ascending/valid) → ~70-95 |
| `Purchase.java` | 58 | List rework: factories, `assigned()`/`exception()` carry the list → +30-45 |
| `PurchaseTest.java` | 41 | Ordered-list assertions, empty-list rejection → +30-40 |
| `PurchaseJpaEntity.java` | 51 | Drop column, add `@OneToMany` ordered by `position` → +25-35 |
| `PurchaseJpaMapper.java` | 25 | List mapping, order-preserving → +15-25 |
| New `PurchaseSessionJpaEntity.java` | — (mirrors `PurchaseJpaEntity`) | ~45-55 new |
| `V7__physical_courses.sql` (schema-heavy) | 64 | V20 create+backfill+drop → ~60-90 |
| `V14__billing_checkout.sql` (schema-heavy) | 139 | Upper bound if V20 needs more than one child table | — |
| `SubscriptionTrialMigrationIntegrationTest.java` (migration-parity precedent) | 236 | New parity + guarded-reverse test → ~180-240 |
| `AssignCapacityUseCase.java` | 75 | `assignAll` + `assign` delegation, same file → +40-60 |
| `AssignCapacityUseCaseTest.java` | 129 | Ordered-claim + partial-failure assertions → +60-90 |
| `PhysicalCapacityAssignmentOutboxEventHandler.java` | 123 | Coverage resolution + N-command dispatch → +50-80 |
| `PhysicalCapacityAssignmentOutboxEventHandlerTest.java` | 218 | New coverage/N-session branches → +80-120 |
| `CreateSubscriptionCheckoutUseCaseImpl.java` | 153 | `CreatePhysicalPurchaseCheckoutUseCaseImpl` (quote validity + 409 branch, one fewer external call) → ~130-170 |
| `CreateSubscriptionCheckoutUseCaseImplTest.java` | 377 | Comparable state-branch density, one fewer provider mock → ~250-320 |
| `PhysicalSessionManagementIntegrationTest.java:381` (concurrency clone) | 435 | Extended overlapping-MONTHLY concurrency test → +90-130 net |
| — (no exact precedent) | — | New `PhysicalPurchaseIntegrationTest` (4 scenarios) → ~280-380, sized against `CreateSubscriptionCheckoutUseCaseImplTest`'s scenario density |
| — (pure function, no I/O precedent of this shape) | — | `CoveragePlannerTest` (6+ cases: exact-N, gap-skip, horizon boundary, `requireAvailable`, tiebreak) → ~120-170 |

**Estimate**: roughly **2100-2900 changed lines** across the whole change —
well above any prior change in this project (previous changes ran 3-5 PRs at
~1350-1600 lines total). This is proportional to crossing four modules
(`shared`, `physical`, `billing`, `app`) plus a schema migration with a
guarded reverse, not padding.

```text
Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High
```

Chain strategy is `pending` — the delivery-strategy decision (stacked vs.
feature-branch-chain) is cached by the orchestrator per Section E of
`sdd-phase-common.md`; do not assume one here.

### Suggested Work Units

Eight PRs. This is more than this project's historical 3-5, by honest
architectural seam count, not by line-count padding. **PR 1 (schema) carries
the largest risk and is deliberately placed first**, before anything else is
built on top of the new shape — it is the only non-trivial revert
(`V21` guard aborts if any multi-session purchase already exists), so it must
be reviewable and revertible in total isolation while the rest of the change
still doesn't exist yet. Everything else follows design.md's own
"Dependency Order" section, which already separates independent seams
(`shared` command, `physical` assignAll, `CoveragePlanner`) from strictly
sequential ones (wiring, endpoint, tests).

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | Billing schema 1:N: V20/V21, entity, mapper, `Purchase` list | PR 1 (~450-620) | `./gradlew :api:billing:test --tests "*PurchaseTest*" --tests "*PurchaseJpaMapperTest*"` + `./gradlew :api:app:test --tests "*PurchaseSessionsMigrationIntegrationTest*"` | Testcontainers migration test (real MySQL, real Flyway) | Revert 5 files + 2 migrations; nothing downstream references the list yet |
| 2 | `shared` ordered multi-session command + invariant | PR 2 (~125-170) | `./gradlew :api:shared:test --tests "*MultiSessionCapacityAssignmentCommandTest*"` | N/A — pure record, no I/O | Revert 1 new file; unused until PR 3 |
| 3 | `api:physical` `assignAll` + `assign` delegation | PR 3 (~100-150) | `./gradlew :api:physical:test --tests "*AssignCapacityUseCaseTest*"` + `*AssignCapacityAdapterIntegrationTest*` | Testcontainers all-or-nothing assertion (extend existing class) | Revert port/impl changes; `assign` reverts to its own body, N=1 behavior unchanged |
| 4 | `billing` `CoveragePlanner` (pure function) | PR 4 (~120-170) | `./gradlew :api:billing:test --tests "*CoveragePlannerTest*"` | N/A — pure function over `ScheduledSessionSnapshot`, no mocks | Revert 1 new file; unused until PR 6/7 |
| 5 | `PaymentTarget.Physical` → quoteId + `CreatePurchaseFromPaymentEventUseCase` | PR 5 (~80-130) | `./gradlew :api:billing:test --tests "*PaymentTargetTest*" --tests "*CreatePurchaseFromPaymentEventUseCaseTest*"` | N/A | Revert 2 modified files; consumers still compile against PR 1's list shape |
| 6 | `api:app` wiring: outbox handler + adapter `assignAll` | PR 6 (~200-280) | `./gradlew :api:app:test --tests "*PhysicalCapacityAssignmentOutboxEventHandlerTest*"` | Extended `PhysicalSessionManagementIntegrationTest` concurrency clone (Testcontainers + `CountDownLatch`) | Revert 2 modified files; #115's proven N=1 flow is restored (degenerates cleanly) |
| 7 | `billing` checkout use case + controller + `409`/`410` | PR 7 (~350-460) | `./gradlew :api:billing:test --tests "*CreatePhysicalPurchaseCheckoutUseCaseImplTest*" --tests "*PhysicalPurchaseControllerTest*"` | N/A at unit level; exercised end-to-end in PR 8 | Purely additive; delete controller + use case, no existing caller depends on it |
| 8 | OpenAPI + end-to-end integration suite | PR 8 (~350-450) | `./gradlew :api:app:test --tests "*PhysicalPurchaseIntegrationTest*"` | Full Testcontainers scenarios 1, 2, 5, 6 | Revert the new test class + OpenAPI diff; no production code depends on it |

## PR 1 — Billing schema: `Purchase` becomes 1:N (highest risk)

**Branch**: `feature/physical-purchase-capacity-schema` off `develop`.
Risk: this is the only non-trivial revert in the whole change (design A1) —
review it in isolation, before anything else depends on the new shape.

- [x] 1.1 RED: create `PurchaseSessionsMigrationIntegrationTest.java`
      (`api/app/src/test/.../integration/billing/`, mirrors
      `SubscriptionTrialMigrationIntegrationTest.java`): asserts
      `COUNT(billing_purchases) == COUNT(DISTINCT purchase_id)` and
      `COUNT(billing_purchase_sessions) == COUNT(billing_purchases)` on a
      fixture seeded with pre-V20 singular rows, plus a case where the V21
      guard aborts when a multi-session purchase exists.
- [x] 1.2 Verify RED: fails because V20/V21 don't exist yet, not a fixture typo.
- [x] 1.3 GREEN: create `V20__billing_purchase_sessions.sql` — create
      `billing_purchase_sessions(purchase_id, position, physical_session_id)`,
      `INSERT ... SELECT id, 0, physical_session_id FROM billing_purchases`,
      drop the singular column.
- [x] 1.4 GREEN: create `V21__revert_billing_purchase_sessions.sql` — re-add
      the column, backfill from `position = 0`, guard via the
      `CHECK`-constrained scratch-table insert (design A1), drop the child
      table.
- [x] 1.5 Verify GREEN: `PurchaseSessionsMigrationIntegrationTest` green,
      including the guard-abort case.
- [x] 1.6 RED: extend `domain/model/PurchaseTest.java` — ordered list
      preserved through `assigned()`/`exception()`; empty list rejected at
      construction.
- [x] 1.7 Verify RED: fails on the still-singular `Purchase.java` field.
- [x] 1.8 GREEN: modify `domain/model/Purchase.java` — replace
      `physicalSessionId` with ordered `List<String> physicalSessionIds`;
      update factories and `assigned()`/`exception()` to carry the list.
- [x] 1.9 Verify GREEN: `PurchaseTest` suite green.
- [x] 1.10 Create `infrastructure/persistence/entity/PurchaseSessionJpaEntity.java`
      — child row `(purchase_id, position, physical_session_id)`.
- [x] 1.11 Modify `infrastructure/persistence/entity/PurchaseJpaEntity.java`
      — drop the singular column field, add `@OneToMany` ordered by
      `position`.
- [x] 1.12 Modify `infrastructure/persistence/mapper/PurchaseJpaMapper.java`
      — order-preserving list mapping both directions.
- [x] 1.13 Extend `PurchaseRepositoryAdapterTest.java` — round-trip an N>1
      session list, order preserved after reload.
- [x] 1.14 Run `./gradlew :api:billing:test :api:billing:jacocoTestCoverageVerification :api:app:test` — confirms `api:billing` domain+application 100%, infrastructure 85%, and the migration test passes against real MySQL.

## PR 2 — `shared`: ordered multi-session command

**Branch**: `feature/physical-purchase-capacity-shared-command` off `develop`
(cut after PR 1 merges; independent of PR 1's content, sequenced second so
downstream PRs have one stable base).

- [x] 2.1 RED: create `MultiSessionCapacityAssignmentCommandTest.java`
      (`api/shared/.../physical/`, mirrors `CapacityAssignmentCommandTest.java`):
      rejects empty claims, duplicate `sessionId`, and a non-ascending
      `(scheduledAt, sessionId)` list; accepts a correctly ordered list;
      defensive-copies the input list.
- [x] 2.2 Verify RED: fails on the missing class, not a typo.
- [x] 2.3 GREEN: create `MultiSessionCapacityAssignmentCommand.java` — record
      with nested `SessionClaim(sessionId, scheduledAt)`, `studentId`,
      `paymentId`; compact constructor enforces design A2's total order
      (`List.copyOf`, non-null, non-empty, no duplicate `sessionId`, strictly
      ascending by `(scheduledAt, sessionId)`).
- [x] 2.4 Verify GREEN: suite green.
- [x] 2.5 Run `./gradlew :api:shared:test` — no coverage floor regression.

## PR 3 — `api:physical`: ordered all-or-nothing `assignAll`

**Branch**: `feature/physical-purchase-capacity-assign-all` off `develop`
(cut after PR 2 merges; needs PR 2's command type).

- [x] 3.1 RED: extend `AssignCapacityUseCaseTest.java` — ordered claims are
      attempted in list order; a failure at claim k throws and attempts no
      k+1 insert; N=1 through `assignAll` matches today's `assign` behavior.
- [x] 3.2 Verify RED: fails on the missing `assignAll` method.
- [x] 3.3 GREEN: modify `AssignCapacityUseCase.java` — add
      `assignAll(MultiSessionCapacityAssignmentCommand)` under one
      `@Transactional(propagation = REQUIRES_NEW)` around the ordered loop,
      each iteration reading the live invariant, inserting, and calling
      `flush()` per insert (matches
      `SubscriptionRepositoryAdapter.saveNewCheckout:62-72`); reimplement
      `assign(...)` as a singleton-list delegation to `assignAll`.
- [x] 3.4 Modify `application/port/in/PhysicalCapacityAssignmentPort.java` —
      add `assignAll(...)`; create `application/usecase/CapacityAssignments.java`
      — typed result `record CapacityAssignments(List<UUID> assignedSessionIds)`.
- [x] 3.5 Verify GREEN: `AssignCapacityUseCaseTest` suite green, including all
      pre-existing single-session cases.
- [x] 3.6 RED: extend `AssignCapacityAdapterIntegrationTest.java` — 3-session
      set, session 3 full ⇒ zero `physical_capacity_assignments` rows for
      that student (assert literal `COUNT(*) = 0`); success ⇒ exactly N rows.
- [x] 3.7 Verify RED then GREEN against the real DB.
- [x] 3.8 Verify `PhysicalArchitectureTest` still passes — no dependency on
      any `com.menta.billing.*` type from the new method.
- [x] 3.9 Run `./gradlew :api:physical:test :api:physical:jacocoTestCoverageVerification` — confirms 95%/90% floors.

## PR 4 — `billing`: `CoveragePlanner` (pure function)

**Branch**: `feature/physical-purchase-capacity-coverage-planner` off
`develop` (cut after PR 3 merges for a stable base; independent of PR 2/3's
content per design's dependency order — can be reviewed in parallel).

- [x] 4.1 RED: create `CoveragePlannerTest.java`
      (`api/billing/.../application/usecase/`): exactly N sessions picked;
      forward extension past a calendar gap never truncates (design A4);
      shortfall inside the `120`-day horizon returns "cannot fill"; horizon
      boundary inclusive/exclusive; `requireAvailable=true` filters
      `availableSpots = 0` (feeds A6's `409`); order is
      `(scheduledAt, sessionId)` with a same-instant tiebreak.
- [x] 4.2 Verify RED: fails on the missing class.
- [x] 4.3 GREEN: create `CoveragePlanner.java` — pure
      `planMonthly(List<ScheduledSessionSnapshot>, Instant, int scheduledSessionCount, boolean requireAvailable)`
      and `planIndividual(List<ScheduledSessionSnapshot>, String selectedSessionId, boolean requireAvailable)`,
      no Spring, no JPA, `COVERAGE_LOOKAHEAD = 120 days` constant. Two named
      entry points instead of one method with a purchase-type flag, mirroring
      this module's own `PhysicalCourseQuote.monthly()`/`.individual()`
      precedent. Result is the sealed `CoveragePlanner.Plan`
      (`Complete(List<EligibleSession>)` / `Insufficient()`), mirroring
      `CurrentSubscriptionResult`'s sealed-variant style.
- [x] 4.4 Verify GREEN: suite green.
- [x] 4.5 Verify `BillingArchitectureTest` — `CoveragePlanner` imports no
      Spring/JPA type.
- [x] 4.6 Run `./gradlew :api:billing:test :api:billing:jacocoTestCoverageVerification` — confirms 100% domain+application.

## PR 5 — `PaymentTarget.Physical` → quoteId + purchase creation

**Branch**: `feature/physical-purchase-capacity-payment-target` off
`develop` (cut after PR 4 merges; needs PR 1's `Purchase` list shape).

- [x] 5.1 RED: extend `PaymentTargetTest.java` — `Physical`'s reference is
      documented and treated as a `quoteId`, not a `sessionId`; javadoc
      updated on the type itself.
- [x] 5.2 GREEN: modify `domain/model/PaymentTarget.java` — update javadoc
      only (no field/shape change; the column stays `VARCHAR`, only the
      meaning of new values changes per design A5).
      **Deviation**: interpreted "no field/shape change" as referring to the
      DB persistence shape (column stays `VARCHAR`, no migration — matching
      design A5's own wording), not the Java record's accessor name. Renamed
      the record component `sessionId` → `quoteId` (compact-constructor
      variable and message too) so the accessor itself is honest, per this
      session's explicit direction ("el código no debe mentir"). This forced
      an atomic fix of every `.sessionId()` caller across `api:billing` and
      `api:app` (see 5.4/5.5 and the outbox-handler note below) since Java
      cannot compile a partially-renamed public accessor.
- [x] 5.3 RED: extend `PublishPhysicalPaymentCompletedUseCaseTest.java` —
      the outbox payload's `targetReference` carries the quoteId, not the
      old session id; update the test fixture accordingly.
- [x] 5.4 GREEN: modify `PublishPhysicalPaymentCompletedUseCase.java:95` —
      pass the quoteId to the payload; update its javadoc to stop describing
      the field as a session id.
- [x] 5.5 Modify `PaymentCompletedOutboxPayload.java` javadoc — `targetReference`
      now documented as the quoteId, not the sessionId. Field name
      `targetReference` was already generic (not misleading) — kept as is,
      per this session's explicit "only rename if the current name would be
      misleading" guidance.
- [x] 5.6 RED: extend `CreatePurchaseFromPaymentEventUseCaseTest.java` —
      accepts the resolved eligible-session list (not a single id); creates
      one `Purchase` covering all of them; re-delivery for the same
      `payment_id` is idempotent and yields the same session set (spec
      scenario 5).
- [x] 5.7 GREEN: modify `CreatePurchaseFromPaymentEventUseCase.java` — accept
      the resolved session list; `payment_id` idempotency key unchanged.
      Also updated `PurchaseCreationFromEventPort`'s signature (necessary
      companion change for the port/impl pair to compile).
- [x] 5.9 (added — explicit user direction this session, not in the original
      task list) Light-touch update to
      `PhysicalCapacityAssignmentOutboxEventHandler.java:86,98` — renamed the
      consumed value from `physical.sessionId()` to `physical.quoteId()`
      (required for compilation after 5.2's rename) via an explicit local
      `quoteId` variable, added a `TODO(#41 PR6)` comment and a javadoc
      section stating that real multi-session resolution via
      `CoveragePlanner` + `assignAll` is PR6's job — this PR keeps the exact
      N=1 pass-through behavior unchanged. Updated
      `PurchaseCreationFromEventPort`'s new second argument at this call
      site (`List.of(quoteId)`, singleton, matching prior behavior) and the
      3 `createPurchaseFromPaymentEvent(any())` mock verifications in
      `PhysicalCapacityAssignmentOutboxEventHandlerTest.java` to
      `(any(), any())` — no functional/logic change, compile-compatibility
      only. **Found a 4th production call site not named in this session's
      three**: `PaymentJpaMapper.java:58` (`physical.sessionId()`, the
      domain→entity write-side mapping, sibling to the already-known
      read-side `PaymentJpaMapper.java:52`) — updated to `.quoteId()`.
- [x] 5.8 Run `./gradlew :api:billing:test :api:billing:jacocoTestCoverageVerification` — 100%/85% floors hold.

## PR 6 — `api:app`: wiring the outbox handler to N sessions

**Branch**: `feature/physical-purchase-capacity-outbox-wiring` off `develop`
(cut after PR 5 merges; needs PRs 1, 3, 4, 5).

- [ ] 6.1 RED: extend `PhysicalCapacityAssignmentOutboxEventHandlerTest.java`
      — resolves the quote via `PaymentTarget.Physical`'s reference; MONTHLY
      calls `CoveragePlanner.plan(...)` with `confirmedAt`; INDIVIDUAL uses
      `[selectedSessionId]`; a coverage shortfall inside the horizon routes
      to `MarkPurchaseExceptionPort` with `TARGET_NOT_SCHEDULED`, never a
      partial assignment.
- [ ] 6.2 Verify RED: fails on the still-single-session handler, not a typo.
- [ ] 6.3 GREEN: modify `PhysicalCapacityAssignmentOutboxEventHandler.java:86`
      — desestructure the quote reference explicitly as `quoteId` (javadoc
      updated, not a silent rename); load the quote; resolve coverage;
      build one `MultiSessionCapacityAssignmentCommand`; call
      `createPurchaseFromPaymentEvent` then `assignAll`.
- [ ] 6.4 Modify `api/app/.../billing/PhysicalCapacityAssignmentAdapter.java`
      — add `assignAll` delegation to the physical port.
- [ ] 6.5 Verify GREEN: handler test suite green, including all pre-existing
      single-session cases (scenario 2 regression).
- [ ] 6.6 RED: clone
      `PhysicalSessionManagementIntegrationTest.concurrent_payments_for_same_session_capacity_one_resolves_one_is_exception:381`
      into the same class, extended to two overlapping `MONTHLY` purchase
      sets over the same sessions, using the `CountDownLatch` starting-gun
      pattern from `SubscriptionCheckoutIntegrationTest:384` (the only latch
      precedent in the repo) so both writers interleave.
- [ ] 6.7 Verify RED: fails without the stable `(scheduledAt, sessionId)`
      order enforced by PR 2 — confirms the deadlock-avoidance claim is
      actually exercised, not assumed.
- [ ] 6.8 Verify GREEN: no deadlock exception surfaces; one purchase
      `ASSIGNED` with N rows, the other `EXCEPTION` with zero rows.
- [ ] 6.9 Run `./gradlew :api:app:test` — full outbox + concurrency suite
      green.

## PR 7 — `billing`: checkout endpoint

**Branch**: `feature/physical-purchase-capacity-checkout` off `develop`
(cut after PR 6 merges; needs PR 4's planner and PR 5's `PaymentTarget`
shape — could theoretically branch off PR 5, but sequenced after PR 6 to
keep `develop` linear for this change).

- [ ] 7.1 RED: create `CreatePhysicalPurchaseCheckoutUseCaseImplTest.java`
      (Mockito + fixed `Clock`, mirrors `CreateSubscriptionCheckoutUseCaseImplTest.java`):
      expired quote → `PhysicalCourseQuoteExpiredException`, no
      `paymentRepository.save` call; visibly-full quote (planner with
      `requireAvailable=true` cannot fill) → `PhysicalCapacityUnavailableException`,
      no `paymentRepository.save`; happy path resolves user from the
      authenticated principal (never the request body), writes `Payment`
      before calling the provider; same idempotency key replays identical
      checkout data, creates no second `Payment`.
- [ ] 7.2 Verify RED: fails on the missing use case class.
- [ ] 7.3 GREEN: create `domain/exception/PhysicalCapacityUnavailableException.java`
      — `BusinessException`, code `CAPACITY_UNAVAILABLE`.
- [ ] 7.4 GREEN: create `CreatePhysicalPurchaseCheckoutUseCaseImpl.java` +
      its in-port + DTOs — validity check first (410 before 409, design A7),
      then `CoveragePlanner.plan(..., clock.now(), requireAvailable=true)`,
      then `Payment.awaitingProvider(PaymentTarget.Physical(quoteId))` + MP
      preference.
- [ ] 7.5 Verify GREEN: suite green.
- [ ] 7.6 RED: create `PhysicalPurchaseControllerTest.java` — 401 without a
      token (threat-matrix routing case, design's Threat Matrix section);
      missing `quoteId`/`paymentMethod`/idempotency key rejected before any
      `Payment` row; expired quote → `410` with a quote-expired ProblemDetail
      code; visibly-full quote → `409` with a `CAPACITY_UNAVAILABLE`
      ProblemDetail code, distinct from `410`.
- [ ] 7.7 Verify RED: fails on the missing controller.
- [ ] 7.8 GREEN: create `PhysicalPurchaseController.java` + exception
      handler entries + request/response DTOs — `POST
      /api/v1/billing/physical/purchases`; resolve `actingUserId` from the
      token exactly as `SubscriptionController` does.
- [ ] 7.9 Modify `BillingConfiguration.java` — wire the new use-case bean.
- [ ] 7.10 Verify `BillingArchitectureTest` — the checkout use case
      references no `com.menta.physical.*` type.
- [ ] 7.11 Run `./gradlew :api:billing:test :api:billing:jacocoTestCoverageVerification` — 100%/85% floors hold.

## PR 8 — OpenAPI + end-to-end integration

**Branch**: `feature/physical-purchase-capacity-e2e` off `develop` (cut
after PR 7 merges; needs everything).

- [ ] 8.1 Modify `api/openapi/billing-v1.yaml` — document
      `POST /api/v1/billing/physical/purchases`, its request/response
      shapes, `410` and `409` ProblemDetail responses.
- [ ] 8.2 RED: create `PhysicalPurchaseIntegrationTest.java`
      (`api/app/.../integration/physical/` or `billing/`, structured like
      `PresentialPurchaseExceptionPathIntegrationTest`): scenario 1 —
      monthly buy → confirm → N `ASSIGNED`; scenario 2 — individual N=1;
      scenario 5 — duplicate webhook consumes zero extra spots; scenario 6 —
      unfillable coverage ⇒ `Payment COMPLETED` + `Purchase EXCEPTION`; plus
      the checkout-level `409` on a visibly-full quote.
- [ ] 8.3 Verify RED: fails for the intended end-to-end reason (routes not
      yet exercised together in this Testcontainers context), not a fixture
      typo.
- [ ] 8.4 Verify GREEN: all listed scenarios pass together.
- [ ] 8.5 Run `./gradlew test check` (full monorepo build) — confirms every
      module's JaCoCo layer threshold holds (`billing` 100%/85%, `physical`
      95%/90%) and all ArchUnit suites pass.

## Out of Scope (confirmed in proposal.md)

- Scenarios 3 and 4 (capacity holds, guaranteed `409 CAPACITY_UNAVAILABLE`) —
  split to #208; `PhysicalCapacityHoldJpaEntity` gets no write path here.
- Any automatic refund, credit, or `EXCEPTION` notification — tracked in
  #209.
- Any BFF page or Android screen — API-only, per the #29 → #177 precedent.
- Changes to check-in, quote creation, or `PhysicalCoursePricing`.

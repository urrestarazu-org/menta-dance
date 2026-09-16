# Tasks: Technical Capacity Hold for Asynchronous Physical Payments (#208, US-PHYSICAL-004b)

## Fixed Facts (do not reopen)

D1-D7 (proposal) and B1-B6 (design) are locked. No task below re-derives
correlation shape, hold command shape, conversion mechanism, config names,
or the invariant formula — they only implement what design.md already
decided. **B6 (Mercado Pago preference-expiry wire contract) is the one
open item**: Phase 7 is blocked on confirming it, per design's own
"Open Questions" section — not a design gap to fill here.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1400-1900 (migration + shared refactor + physical writer + invariant + bridge + checkout + conversion + sweep + OpenAPI + concurrency/idempotency suites) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | 10 PRs, mirroring design's own Dependency Order |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

```text
Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High
```

### Suggested Work Units

Design's "Dependency Order (for sdd-tasks)" names step 5 (cross-module
bridge) as the natural cut: steps 1-4 are entirely inside `api:physical`
(the risky half — schema, ordering invariant, hold writer, corrected
assignment invariant) and are independently provable under concurrency
before any `billing` code exists; steps 5-10 wire billing, provider, and
end-to-end proof on top of a bridge already proven safe.

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | V21 migration + entity fields + V22 reverse | PR 1 | `:api:app:test --tests "*PhysicalCapacityHoldMigrationIntegrationTest*"` | Testcontainers MySQL 8 | Additive columns on a zero-row table; V22 drops both |
| 2 | `shared`: `SessionClaim`/`SessionClaimOrdering`/hold command | PR 2 | `:api:shared:test` | N/A — pure records | Revert 3 files; unused until PR 3 |
| 3 | `api:physical` hold writer + `holdAll`/`release` | PR 3 | `:api:physical:test --tests "*CapacityHold*"` | Testcontainers, `CountDownLatch` ≥10 iterations | Revert new port/adapter/use cases; nothing calls them yet |
| 4 | Corrected `assertAssignment` invariant + availability predicate | PR 4 | `:api:physical:test --tests "*JpaPhysicalCapacityAssignmentAdapterTest*"` | Testcontainers cross-path oversell test | Revert one method + 3 queries; highest-risk, isolated |
| 5 | Cross-module bridge: billing out-port + `api:app` adapter | PR 5 | `:api:billing:test` + `:api:app:test` (compile-only, no callers yet) | N/A | Revert 2 new files; structurally inert until PR 6 |
| 6 | Checkout wiring: hold call, `409`, release-on-failure | PR 6 | `:api:billing:test --tests "*CreatePhysicalPurchaseCheckoutUseCaseImplTest*"` | N/A at unit level | Revert use-case + config wiring; `409` reverts to best-effort |
| 7 | Provider deadline field (**blocked on B6**) | PR 7 | `:api:billing:test --tests "*PaymentPreference*"` | N/A | Additive optional field; unset restores byte-identical body |
| 8 | Conversion: `ConvertCapacityHoldUseCase` + outbox rewiring | PR 8 | `:api:app:test --tests "*PhysicalCapacityAssignmentOutboxEventHandlerTest*"` | Testcontainers redelivery (2x, 5x) | Revert handler branch; falls to legacy `CoveragePlanner` path |
| 9 | Expiry sweep + `application.yml` | PR 9 | `:api:physical:test --tests "*HoldExpiry*"` | N/A — TTL already correctness-complete at read time | Revert scheduler classes; pure GC, no correctness dependency |
| 10 | OpenAPI + concurrency/idempotency end-to-end suites | PR 10 | `:api:app:test --tests "*PhysicalCapacityHoldIntegrationTest*"` | Full Testcontainers, latch-driven concurrency | Revert test class + OpenAPI diff |

## Phase 1: V21 migration + entity (design step 1)

- [x] 1.1 RED: `PhysicalCapacityHoldMigrationIntegrationTest` — asserts `payment_id NOT NULL`, `uq_physical_holds_payment_session`, `idx_physical_holds_payment` exist; V20.2 guard reverts cleanly on the zero-row table.
- [x] 1.2 GREEN: `V20_1__physical_capacity_hold_correlation.sql` — add `payment_id BINARY(16) NOT NULL`, `converted_at DATETIME(3) NULL`, the unique key, the index (B1). Numbered V20.1, not design.md's V21: `classpath:db/rollback` already owns version 21 (`V21__revert_billing_purchase_sessions.sql`, #41); a whole-number V21/V22 here would collide with or reorder past that existing script the moment both locations are combined, exactly as `PurchaseSessionsMigrationIntegrationTest` already does.
- [x] 1.3 GREEN: `V20_2__revert_physical_capacity_hold_correlation.sql` (under `classpath:db/rollback`) — guarded reverse, not part of normal rollout. Numbered V20.2 for the same reason as 1.2.
- [x] 1.4 Modify `PhysicalCapacityHoldJpaEntity` — add `paymentId`, `convertedAt`; drop "read-only" javadoc. Updated the two existing call sites (`PhysicalCourseAvailabilityIntegrationTest`, `PhysicalCapacityHoldJpaEntityTest`) for the new constructor shape.
- [x] 1.5 Verify GREEN against real MySQL 8 (Testcontainers) — `:api:app:test`, `:api:physical:test`, `:api:physical:jacocoTestCoverageVerification` all pass, no regressions.

## Phase 2: `shared` ordered hold command (design step 2, RED-shared-ordering first)

- [x] 2.1 RED: `SessionClaimOrderingTest` — rejects empty/duplicate/non-ascending over the **whole** claim list, not adjacent pairs.
- [x] 2.2 GREEN: promote `SessionClaim` to `com.menta.shared.physical.SessionClaim`; create `SessionClaimOrdering.requireTotalOrder(List<SessionClaim>)`.
- [x] 2.3 Modify `MultiSessionCapacityAssignmentCommand` — delegate to `SessionClaimOrdering`; verify its existing test suite still green unmodified in behavior.
- [x] 2.4 RED+GREEN: `MultiSessionCapacityHoldCommandTest`/class — ordered claims + `paymentId`, no `studentId` (B2).
- [x] 2.5 Run `:api:shared:test` — no coverage floor regression.

## Phase 3: `api:physical` hold write (design step 3, RED-adapter-invariant then RED-concurrency)

- [x] 3.1 RED: `JpaPhysicalCapacityHoldAdapterTest` — `assertHold` issues the three locking reads (`lockCapacityForUpdate`, assignment count, hold count) in order, never a plain count first (D3), extending the existing `JpaPhysicalCapacityAssignmentAdapterTest` pattern.
- [x] 3.2 GREEN: `PhysicalCapacityHoldWriter` out port (`assertHold`, `markConverted`, `release`) + `JpaPhysicalCapacityHoldAdapter` with explicit `flush()` (B4).
- [x] 3.3 GREEN: `PhysicalCapacityHoldJpaRepository` — `countActiveBySessionIdForUpdate`, `findByPaymentIdOrdered`.
- [x] 3.4 RED+GREEN: `CreateCapacityHoldUseCaseTest`/`ReleaseCapacityHoldUseCaseTest` (Mockito, verify call order) — `holdAll` iterates in claim order and stops at the first failure; `release` is a no-op on an unknown payment. Create `PhysicalCapacityHoldPort` in-port.
- [x] 3.5 RED (concurrency): clone `AssignCapacityAdapterIntegrationTest` + #217's `CountDownLatch` shape, ≥10 iterations, capacity 1, N threads claiming a hold — assert exactly one hold row every run.
- [x] 3.6 RED+GREEN: cross-path oversell — one thread holds, another calls `assertAssignment` on the same capacity-1 session, both orderings; exactly one wins (proposal's High risk). NOTE: the "hold first, then assignment" ordering cannot pass a hard assertion until Phase 4's `assertAssignment` invariant fix lands (out of scope here) — see Deviations below; documented as a measured, intentionally-unasserted oversell instead of a permanently-red test.
- [x] 3.7 Verify `PhysicalArchitectureTest` (`com.menta.physical.ArchitectureTest`) — no Spring/JPA type in the hold use cases. 7/7 passing.
- [x] 3.8 Run `:api:physical:test :api:physical:jacocoTestCoverageVerification` — 95%/90% floors hold. 259/259 tests passing, 0 failures.

## Phase 4: Corrected assignment invariant (design step 4, highest risk, needs only Phase 1)

- [x] 4.1 RED: extend `JpaPhysicalCapacityAssignmentAdapterTest` — `assertAssignment` now also issues `countActiveBySessionIdForUpdate`; `the_plain_non_locking_count_is_never_consulted` extended to the hold count too.
- [x] 4.2 GREEN: `assertAssignment` — invariant becomes `assigned + activeHolds + 1 > capacity` (B4).
- [x] 4.3 GREEN: `PhysicalSessionJpaRepository` — add `converted_at IS NULL AND expires_at > :now` to `findScheduledWithAvailability`, `findManagedWithAvailability`, `findByIdWithAvailability` (B1).
- [x] 4.4 RED+GREEN integration: assignment blocked by an opposing active hold (spec scenario, `physical-capacity-hold/spec.md`). Renamed `HoldCapacityAdapterIntegrationTest.hold_first_then_assignment_currently_oversells_until_Phase_4` → `hold_first_then_assignment_is_refused`, flipped to assert `CapacityBelowAssignedException` with zero assignment rows.
- [x] 4.5 Run `:api:physical:test :api:physical:jacocoTestCoverageVerification` — 261/261 passing, coverage floors hold.

## Phase 5: Cross-module bridge (design step 5 — natural chain cut, needs Phase 3)

- [x] 5.1 Create `billing/.../application/port/out/PhysicalCapacityHoldPort.java` (D4). `hold(MultiSessionCapacityHoldCommand, Instant expiresAt): List<UUID>` + `release(UUID paymentId)`; reuses `com.menta.shared.physical.MultiSessionCapacityHoldCommand` directly (billing already depends on `api:shared`), no billing-local DTO needed.
- [x] 5.2 Create `api:app` `com.menta.app.billing.PhysicalCapacityHoldAdapter` implementing it via the physical in-port — mirrors `PhysicalCourseAvailabilityAdapter` exactly, plain Java call, never HTTP. Translates `CapacityBelowAssignedException` (physical) into `PhysicalCapacityUnavailableException` (billing) here, since the checkout use case cannot import `com.menta.physical..`.
- [x] 5.3 Verify `BillingArchitectureTest` still passes with the new port declared (no `com.menta.physical.*` import in `billing`). Also verified whole-repo `clean compileJava compileTestJava`.

## Phase 6: Checkout wiring (design step 6, needs Phase 5)

- [x] 6.1 RED: extend `CreatePhysicalPurchaseCheckoutUseCaseImplTest` — hold failure ⇒ `PhysicalCapacityUnavailableException`, zero `paymentRepository.save`, zero `preferencePort.createPreference`.
- [x] 6.2 GREEN: move `PaymentId.generate()` above the hold call; call `PhysicalCapacityHoldPort.hold(cmd, ttl)`; throw `409` before any `Payment` row.
- [x] 6.3 RED+GREEN: preference/provider failure after a successful hold ⇒ `release(paymentId)` called, then rethrow.
- [x] 6.4 Modify `BillingConfiguration` — wire the new port into the checkout bean.
- [x] 6.5 Run `:api:billing:test :api:billing:jacocoTestCoverageVerification` — 100%/85% floors hold.

## Phase 7: Provider deadline field (design step 7 — **BLOCKED on B6**, needs Phase 6)

- [ ] 7.0 **BLOCKER**: confirm Mercado Pago's preference-expiry field name and any minimum-window constraint against the live/documented contract before writing 7.1's test (B6 — no in-repo evidence exists). If MP rejects it, fall back to sending no expiration (documented no-regression path).
- [ ] 7.1 RED+GREEN: `PaymentPreferenceRequest` / `PaymentPreferencePort` gain `Instant expiresAt` (nullable).
- [ ] 7.2 RED+GREEN: `MercadoPagoPaymentPreferenceAdapter.toBody` maps `expiresAt` using the field name confirmed in 7.0; local adapter stores it.
- [ ] 7.3 RED+GREEN (billing unit, Mockito + fixed `Clock`): `expiresAt` sent to the preference never exceeds the hold's `expires_at`.
- [ ] 7.4 Run `:api:billing:test`.

## Phase 8: Conversion (design step 8, RED-conversion-idempotency, needs Phases 3 and 4)

- [x] 8.1 RED: `ConvertCapacityHoldUseCaseTest` (Mockito) — `HoldNotFound` / `AlreadyConverted` / `Converted(sessionIds)` outcomes.
- [x] 8.2 GREEN: `ConvertCapacityHoldUseCase.convertAll(paymentId, studentId)` — one `REQUIRES_NEW`, per session in order: mark `converted_at`, then unchanged `assertAssignment` (B3).
- [x] 8.3 RED: extend `PhysicalCapacityAssignmentOutboxEventHandlerTest` — held payment converts without `CoveragePlanner` recomputation; `HoldNotFound` falls to the legacy plan+`assignAll` path unchanged.
- [x] 8.4 GREEN: modify the handler — convert first; legacy path only on `HoldNotFound`. Also added `PhysicalCapacityHoldPort.heldSessionIds` (a small, necessary addition beyond the literal port shape): a capacity trip during conversion must still create the `Purchase` row before `markException`, or `MarkPurchaseExceptionUseCase` throws `PaymentNotFoundException` — uncovered by its existing `noRollbackFor` — and the ambient outbox-worker transaction fails with `UnexpectedRollbackException` on commit. Confirmed via a real Testcontainers run (see 8.5's suite).
- [x] 8.5 RED+GREEN integration: added `redelivering_the_webhook_five_times_converts_the_hold_exactly_once` to `PhysicalPurchaseIntegrationTest` — delivers the webhook 5x, asserts identical assignment counts, `converted_at` unchanged after the first delivery, `Purchase ASSIGNED` once. The pre-existing `a_duplicate_outbox_redelivery_consumes_no_additional_spots` (2x) now also exercises conversion end-to-end (real checkout-created hold → conversion → redelivery) and still passes unchanged.
- [ ] 8.6 DEFERRED: a dedicated concurrent-reader-never-dips-during-convertAll integration test (`findByIdWithAvailability` polled while `convertAll` runs) was not added — the invariant is the same B3/B4 mark-then-assert mechanism already proven by Phase 3/4's concurrency suites, but no new test isolates conversion's own transaction window specifically.
- [x] 8.7 GREEN: `PhysicalPurchaseIntegrationTest.a_capacity_trip_at_confirmation_leaves_payment_completed_and_purchase_exception_end_to_end` (pre-existing, now exercising the real conversion path) proves a held session that lost its spot before conversion resolves to `EXCEPTION` with zero new assignment rows, all-or-nothing.
- [x] 8.8 Run `:api:physical:test`, `:api:app:test` (targeted classes), `:api:physical:jacocoTestCoverageVerification` — all green, no regressions. See apply report for full counts.

## Phase 9: Expiry sweep (design step 9, needs only Phase 1)

- [x] 9.1 Create `HoldExpiryReconciler`/`HoldExpiryWorker` (`infrastructure/scheduling`) — `@ConditionalOnProperty` on the **class** (#172 lesson). Deviation from the literal "dispatch each id to a separate REQUIRES_NEW worker bean" wording: this is pure GC over a table with no per-row business logic (unlike `SubscriptionExpiryWorker.expireOne`, which re-reads and transitions one aggregate), so `HoldExpiryWorker.sweep()` is one `@Transactional(REQUIRES_NEW)` method issuing two batched, `LIMIT`-bounded native `DELETE`s (`deleteExpiredUnconverted`, `deleteConvertedBefore`) — the worker/trigger split and the `@ConditionalOnProperty`-on-the-class fix are preserved exactly.
- [x] 9.2 Modify `PhysicalConfiguration` — added `physicalCapacityHoldTtl()` `@Bean` converting `physical.capacity.hold.ttl-ms` (`@Value`) to a `Duration`, injected as a constructor arg into `HoldExpiryWorker` (design B5 — `@Value` stays out of the class that uses the typed value).
- [x] 9.3 Added `physical.capacity.hold.ttl-ms` and `physical.capacity.hold.expiry.{enabled,rate-ms,batch-size}` to `api/app/src/main/resources/application.yml` (B5), plus `physical.capacity.hold.expiry.enabled: false` in `application-integration-test.yml` (same #172-class first-tick-races-writes risk `billing.subscription.expiry` already guards against).
- [x] 9.4 RED+GREEN: `HoldExpiryWorkerIntegrationTest` (Testcontainers, `:api:app:test`) seeds an expired-unconverted row, an old-converted row, and a still-active row; `worker.sweep()` deletes only the first two. Read-time filter correctness itself (expired hold stops counting with zero sweep runs) was already proven by PR3/PR4's suites, not re-proven here. `HoldExpiryWorkerTest`/`HoldExpiryReconcilerTest` (Mockito, `:api:physical:test`) cover orchestration and delegation.
- [x] 9.5 Ran `:api:physical:test :api:physical:jacocoTestCoverageVerification` (271/271 passing, coverage floors hold) and `:api:app:test --tests "*HoldExpiryWorkerIntegrationTest*"` (3/3 passing). Also verified whole-repo `clean compileJava compileTestJava`.

## Phase 10: OpenAPI + end-to-end proof (design step 10, needs everything)

- [x] 10.1 Modify `api/openapi/billing-v1.yaml` — `409` description becomes a guarantee, same code/shape. Also refreshed the stale `PhysicalCapacityUnavailableException` javadoc (still described the pre-#208 best-effort behavior as current).
- [x] 10.2 RED+GREEN integration: guaranteed `409` — two concurrent checkouts for the last spot ⇒ one `201`, one `409 CAPACITY_UNAVAILABLE`, zero `billing_payments` rows for the loser. Added `two_concurrent_checkouts_for_the_last_spot_guarantee_one_201_and_zero_payment_rows_for_the_loser` to the existing `PhysicalPurchaseIntegrationTest` (real HTTP, real `CountDownLatch` starting gun, 2 threads) rather than a new file — that class already owns full end-to-end checkout coverage.
- [x] 10.3 RED+GREEN integration: partially-satisfiable `MONTHLY` ⇒ zero hold rows created. Added `a_partially_satisfiable_monthly_quote_creates_zero_hold_rows_and_zero_payment_rows` to the same class, confirming `resolveCoveragePlan` throws before `holdCapacity` ever runs, end-to-end through the real HTTP endpoint.
- [x] 10.4 Verify `BillingArchitectureTest` (checkout still references no `com.menta.physical.*`) and `PhysicalArchitectureTest` full suite. Both 7/7 passing, 0 failures/errors.
- [x] 10.5 Run `./gradlew test check` — all coverage floors hold, 0 failures/errors. Whole-repo run: 2048 tests across `shared`(63)/`auth`(489)/`virtual`(344)/`physical`(271)/`billing`(579)/`app`(302), 0 failures, 0 errors, counted from each module's aggregated JUnit XML `<testsuite>` header, not from the `BUILD SUCCESSFUL` line alone.

## Out of Scope (confirmed in proposal.md)

- No new endpoint, no student-facing hold exposure (D5).
- No `CoveragePlanner` change; no refund/notification policy (#209).
- No retrofit of holds onto virtual subscriptions.

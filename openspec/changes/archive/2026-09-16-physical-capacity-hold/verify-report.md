```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:2c0e7e772375a44a12189c1f7b2646e67823dddf60d883de55d3b1ee45577c7a
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 11/11
scenarios: 18/18
test_command: ./gradlew test check
test_exit_code: 0
test_output_hash: sha256:3bb7f258069c50920f10a31f66027e54f4907f0ffb8dafd63d10a89eaf4f42be
build_command: ./gradlew jacocoTestCoverageVerification
build_exit_code: 0
build_output_hash: sha256:4e30081a5273c23fac584904355d4e46c81f7e1ab8c3b6f286409de89a9b0401
```

## Verification Report

**Change**: physical-capacity-hold (#208, US-PHYSICAL-004b)
**Version**: develop @ 2ab9e0509c69d87fa742e6d63b454c7a9b35cc9f
**Mode**: Strict TDD

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 62 (Phases 1-10 + Out of Scope) |
| Tasks complete (in scope: 1.x-6.x, 8.x-10.x) | All checked `[x]` except 8.6 (documented deferral) |
| Tasks incomplete (deferred, in scope) | 1 (8.6 — concurrent-reader integration test, documented rationale) |
| Tasks incomplete (Phase 7, explicitly out of scope for this archive) | 5 (7.0-7.4, blocked on B6 / Mercado Pago contract, tracked in #233) |

Phase 7 (7.0-7.4) is correctly unchecked and documented as blocked on confirming
Mercado Pago's preference-expiry wire contract (B6) — this is an explicit,
tracked deferral (#233), not an implementation gap, and is excluded from this
verification's pass/fail per the verification scope.

Task 8.6 (a dedicated concurrent-reader-never-dips-during-`convertAll`
integration test) is unchecked with an inline rationale: the invariant it
would isolate is the same B3/B4 mark-then-assert mechanism already proven by
Phase 3/4's concurrency suites. Flagged as WARNING, not CRITICAL — the
underlying invariant is proven, only one additional isolating test is
missing.

### Build & Tests Execution
**Build**: ✅ Passed
```text
./gradlew jacocoTestCoverageVerification --console=plain
BUILD SUCCESSFUL — all 6 module coverage-verification tasks (shared, auth,
virtual, physical, billing, app) + bff UP-TO-DATE / passed, no violations.
```

**Tests**: ✅ 2048 passed / ❌ 0 failed / ⚠️ 0 skipped
```text
./gradlew test check --console=plain → BUILD SUCCESSFUL
Parsed from real JUnit XML <testsuite> headers (not the summary line):
  shared:   63 tests, 0 failures, 0 errors, 0 skipped (11 XML files)
  auth:    489 tests, 0 failures, 0 errors, 0 skipped (112 XML files)
  virtual: 344 tests, 0 failures, 0 errors, 0 skipped (77 XML files)
  physical:271 tests, 0 failures, 0 errors, 0 skipped (63 XML files)
  billing: 579 tests, 0 failures, 0 errors, 0 skipped (101 XML files)
  app:     302 tests, 0 failures, 0 errors, 0 skipped (66 XML files)
  TOTAL:  2048 tests, 0 failures, 0 errors — matches tasks.md 10.5 claim exactly
```

**Coverage**: ✅ Above floor for all 6 gated modules (shared, auth, virtual,
physical, billing, app), verified via `jacocoTestCoverageVerification`
(layered BUNDLE gate, per `docs/14-TEST-STRATEGY.md`). Whole-module LINE
totals as corroboration (not the gating metric itself): physical 97.1%
(1034/1065), billing 95.4% (2244/2352).

### Spec Compliance Matrix

**`physical-capacity-hold/spec.md`** (7 requirements, 10 scenarios)

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Hold creation atomic all-or-nothing | Every session has capacity — full hold | `JpaPhysicalCapacityHoldAdapterTest > assertHold_saves_one_row_when_...`, `CreateCapacityHoldUseCaseTest > holdAll_claims_every_session_in_order_...` | ✅ COMPLIANT |
| Hold creation atomic all-or-nothing | One session full — zero hold rows | `JpaPhysicalCapacityHoldAdapterTest > assertHold_writes_nothing_when_...`, `CreateCapacityHoldUseCaseTest > holdAll_stops_at_the_first_failure_...`, `HoldCapacityAdapterIntegrationTest > exactly_one_hold_survives_N_concurrent_claims_...` | ✅ COMPLIANT |
| Active holds count on every assignment write path | Assignment blocked by opposing active hold | `HoldCapacityAdapterIntegrationTest > hold_first_then_assignment_is_refused`, `HoldCapacityAdapterIntegrationTest > assignment_first_then_hold_is_refused`, `JpaPhysicalCapacityAssignmentAdapterTest` (extended invariant) | ✅ COMPLIANT |
| Correlation reference per hold batch | Hold batch retrievable by reference | `JpaPhysicalCapacityHoldAdapterTest` (`findByPaymentIdOrdered` mocked call-order tests) | ✅ COMPLIANT |
| TTL expiry without sweep | Expired hold invisible before sweep | `PhysicalCourseAvailabilityIntegrationTest > computes_available_spots_as_a_live_count_of_assignments_and_active_holds` (seeds an explicitly-expired hold, asserts it does not count) | ✅ COMPLIANT |
| Explicit release on checkout failure | Failed checkout after hold frees the spot | `CreatePhysicalPurchaseCheckoutUseCaseImplTest > a_provider_failure_after_a_successful_hold_releases_it_before_rethrowing`, `ReleaseCapacityHoldUseCaseTest`, `JpaPhysicalCapacityHoldAdapterTest > release_deletes_every_hold_row_for_the_payment` | ✅ COMPLIANT |
| Conversion uses held set, idempotent | First conversion → one assignment per held session | `ConvertCapacityHoldUseCaseTest > convertAll_marks_converted_before_asserting_assignment_per_session_in_order`, `PhysicalPurchaseIntegrationTest > monthly_purchase_confirmed_assigns_every_covered_session_end_to_end` | ✅ COMPLIANT |
| Conversion uses held set, idempotent | Redelivered conversion is a no-op | `ConvertCapacityHoldUseCaseTest > convertAll_returns_AlreadyConverted_when_every_row_is_already_converted`, `PhysicalPurchaseIntegrationTest > redelivering_the_webhook_five_times_converts_the_hold_exactly_once` | ✅ COMPLIANT |
| Conversion uses held set, idempotent | Held session vanishes before conversion — all-or-nothing | `PhysicalPurchaseIntegrationTest > a_capacity_trip_at_confirmation_leaves_payment_completed_and_purchase_exception_end_to_end`, `ConvertCapacityHoldUseCaseTest > convertAll_propagates_CapacityBelowAssigned_and_stops_at_the_first_failure` | ✅ COMPLIANT |
| Hold invisible to the student | No API surface exposes a hold | No new controller/endpoint/BFF/Android surface found in a repo-wide search; no dedicated automated route-enumeration test exists | ⚠️ PARTIAL (static evidence only) |

**`physical-purchase-checkout/spec.md`** (2 requirements, 3 scenarios)

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Visibly-full quote rejected 409 (now a guarantee) | Full quote rejected before charging | `PhysicalPurchaseIntegrationTest > checkout_is_rejected_with_409_when_the_quoted_session_is_visibly_full_and_creates_no_payment`, `a_partially_satisfiable_monthly_quote_creates_zero_hold_rows_and_zero_payment_rows` | ✅ COMPLIANT |
| Visibly-full quote rejected 409 (now a guarantee) | Passing the check is now a real guarantee | `PhysicalPurchaseIntegrationTest > two_concurrent_checkouts_for_the_last_spot_guarantee_one_201_and_zero_payment_rows_for_the_loser` | ✅ COMPLIANT |
| Checkout creates no capacity assignment (now creates a hold) | Pending checkout leaves assignments untouched but holds spots | `CreatePhysicalPurchaseCheckoutUseCaseImplTest > the_hold_is_claimed_with_the_quotes_ordered_sessions_and_a_ttl_bound_expiry`, `PhysicalPurchaseIntegrationTest` (checkout suite) | ✅ COMPLIANT |

**`presential-purchase-fulfillment/spec.md`** (2 requirements, 5 scenarios)

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Coverage computed at confirmation, held purchase uses held set | Monthly coverage counts forward from confirmation (no hold) | `PhysicalCapacityAssignmentOutboxEventHandlerTest > no_hold_for_the_payment_still_resolves_coverage_and_calls_assignAll`, `monthly_quote_resolves_ordered_claims_and_calls_assignAll` | ✅ COMPLIANT |
| Coverage computed at confirmation, held purchase uses held set | Held purchase uses hold's session set, not recomputed | `PhysicalCapacityAssignmentOutboxEventHandlerTest > converted_hold_creates_purchase_and_marks_assigned_without_recomputation` | ✅ COMPLIANT |
| Confirmation converts existing hold | Held payment converts without a fresh capacity claim | `PhysicalCapacityAssignmentOutboxEventHandlerTest > converted_hold_creates_purchase_and_marks_assigned_without_recomputation` | ✅ COMPLIANT |
| Confirmation converts existing hold | Redelivery of held, already-converted event is idempotent | `PhysicalCapacityAssignmentOutboxEventHandlerTest > already_converted_hold_is_a_redelivery_noop`, `PhysicalPurchaseIntegrationTest > a_duplicate_outbox_redelivery_consumes_no_additional_spots` | ✅ COMPLIANT |
| Confirmation converts existing hold | Held session vanishes before confirmation — EXCEPTION not partial | `PhysicalCapacityAssignmentOutboxEventHandlerTest > capacity_trip_during_conversion_routes_to_exception_with_zero_assignment_rows`, `redelivery_after_conversion_capacity_trip_on_an_already_ASSIGNED_purchase_is_a_safe_noop` | ✅ COMPLIANT |

**Compliance summary**: 18/18 scenarios compliant; 17/18 via a passing covering test, 1/18 (student-invisibility) via static/structural evidence only (no dedicated automated test) — see WARNING 1.

### Correctness (Static Evidence)
| Requirement | Status | Notes |
|---|---|---|
| Two sequential locking reads, no plain read first (D3) | ✅ Implemented | `assertHold`/`assertAssignment` both verified via `the_plain_non_locking_*_count_is_never_consulted` tests |
| `assigned + activeHolds + 1 > capacity` on both write paths (B4) | ✅ Implemented | `JpaPhysicalCapacityAssignmentAdapter` and `JpaPhysicalCapacityHoldAdapter` both extended |
| Correlation is 2 columns on `physical_capacity_holds`, not a child table (B1) | ✅ Implemented | V20_1/V20_2 migrations (renumbered from design's V21/V22 to avoid a real collision with `classpath:db/rollback`'s existing V21, documented in task 1.2) |
| Hold command has no `studentId` (B2) | ✅ Implemented | `MultiSessionCapacityHoldCommand(claims, paymentId)`, `SessionClaimOrdering` shared with the assignment command |
| Conversion marks-then-asserts in one transaction, no intermediate window (B3) | ✅ Implemented | `ConvertCapacityHoldUseCase.convertAll` — one `REQUIRES_NEW` |
| Config names (B5) | ⚠️ Deviation | Physical's own TTL/sweep config matches design (`physical.capacity.hold.*`); billing's checkout-side TTL argument uses `billing.physical.capacity.hold.ttl-ms` instead of reusing `physical.capacity.hold.ttl-ms` — documented in-code rationale: billing cannot depend on `api:physical`'s config namespace across the module boundary. Same 30-min value, functionally equivalent |
| Provider deadline bounded by hold expiry (B6) | ➖ Deferred | Phase 7 blocked on confirming Mercado Pago's wire contract — tracked in #233, explicitly out of scope for this verification |

### Coherence (Design)
| Decision | Followed? | Notes |
|---|---|---|
| D3/B4 concurrency pattern (two locking reads, no plain read) | ✅ Yes | |
| D4 cross-module bridge (billing out-port + `api:app` adapter) | ✅ Yes | `BillingArchitectureTest` passes, no `com.menta.physical.*` in billing |
| D5 hold invisible/technical only | ✅ Yes | No new endpoint; see PARTIAL scenario note above |
| D7 no recomputation at confirmation for held purchases | ✅ Yes | `ConvertCapacityHoldUseCase` + outbox handler branch on `HoldNotFound` |
| B5 `@ConditionalOnProperty` on the scheduler class (#172 lesson) | ✅ Yes | `HoldExpiryWorkerTest`/`HoldExpiryReconcilerTest` + `holdExpiryBeansAreScannedOnce` integration test |
| Migration versioned V20.1/V20.2 instead of literal V21/V22 | ✅ Yes, documented deviation | Avoids real collision with `classpath:db/rollback`'s existing V21; explained in task 1.2 |

### Issues Found

**CRITICAL**: None

**WARNING**:
1. Scenario "No API surface exposes a hold" (`physical-capacity-hold/spec.md`) has no dedicated automated test — verified only by static/structural absence-of-endpoint evidence.
2. Task 8.6 (concurrent-reader-never-dips-during-`convertAll` integration test) is unchecked; the underlying invariant is proven by Phase 3/4's concurrency suites, but no test isolates conversion's own transaction window specifically.
3. Config key deviation: billing's hold-TTL argument uses `billing.physical.capacity.hold.ttl-ms` rather than design B5's literal `physical.capacity.hold.ttl-ms`. Documented, functionally equivalent, does not break any spec scenario.

**SUGGESTION**: None

### Verdict
PASS WITH WARNINGS
All in-scope requirements (1.x-6.x, 8.x-10.x) are implemented and covered by
passing tests with 0 failures/0 errors across 2048 tests and all 6 coverage
floors holding; Phase 7 is correctly deferred and out of scope; the 3 WARNINGs
are non-blocking and do not represent gaps against any spec scenario.

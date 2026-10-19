```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:5b5d00a4d8fde45bd8b43f245ecc8f429dcafe47c8833b316b86b08d55593083
verdict: pass
blockers: 0
critical_findings: 0
requirements: 11/11
scenarios: 18/18
test_command: ./gradlew test check
test_exit_code: 0
test_output_hash: sha256:95bfbc8a62719ea91e9987e7ef1e8bd602bb9b39441cd698798cbffe283f526e
build_command: ./gradlew build
build_exit_code: 0
build_output_hash: sha256:d20d78d88589d6a04dac4f39fbf0adcc840c4f154f053fdf5e16c1f20580a4df
```

## Verification Report

**Change**: billing-purchase-exception-notification (#209)
**Version**: N/A
**Mode**: Standard (no Strict TDD evidence table format found in apply-progress; TDD order was followed per design's RED/GREEN sequencing but no dedicated TDD Cycle Evidence table is present)

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 40 (A.1-A.9, B.1-B.10, C.1-C.9, D.1-D.6) |
| Tasks complete | 40 |
| Tasks incomplete | 0 |

### Build & Tests Execution

**Build**: PASSED
```text
./gradlew build
BUILD SUCCESSFUL in 1m 2s
192 actionable tasks: 80 executed, 4 from cache, 108 up-to-date
```

**Tests**: 2073 passed / 0 failed / 0 skipped (parsed from real JUnit XML `<testsuite>` attributes per module, not from summary lines)
```text
./gradlew test check
BUILD SUCCESSFUL

Per-module JUnit XML aggregation:
  shared:   63 tests, 0 failures, 0 errors
  auth:    491 tests, 0 failures, 0 errors
  virtual: 344 tests, 0 failures, 0 errors
  physical:271 tests, 0 failures, 0 errors
  billing: 592 tests, 0 failures, 0 errors
  app:     312 tests, 0 failures, 0 errors
  TOTAL:  2073 tests, 0 failures, 0 errors
```

No transient network flakiness observed in `PhysicalSessionManagementIntegrationTest` (12/12 passed) or elsewhere on this run; no retry was needed.

**Coverage**: All layered coverage floors held — `jacocoTestCoverageVerification` PASSED for:
- `:api:auth` (domain+application 100% / infrastructure 85%)
- `:api:virtual` (domain+application 95% / infrastructure 90%)
- `:api:physical` (domain+application 95% / infrastructure 90%)
- `:api:billing` (domain+application 100% / infrastructure 85%)
- `:api:shared`, `:api:app`, `:bff` flat floors (85%/90%/85%)

### Spec Compliance Matrix

**Capability: purchase-exception-notification**

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| One EXCEPTION event notifies exactly two recipients | Two recipients notified | `SpringMailPurchaseExceptionNotificationAdapterTest` (sends 2 `SimpleMailMessage`s) + `PhysicalPurchaseIntegrationTest.an_exception_transition_appends_one_purchase_exceptioned_row_and_notifies_two_recipients_with_no_duplicate_on_redelivery` (D.1) | ✅ COMPLIANT |
| Notification dispatch at most once per transition | Redelivered completion event, no second notification | Same D.1 integration test (redelivery assertion) + `MarkPurchaseExceptionUseCaseTest` (EXCEPTION→EXCEPTION no-op, zero appends) | ✅ COMPLIANT |
| Delivery failure never reverts state, always retryable | SMTP failure leaves purchase EXCEPTION, retryable | `PurchaseExceptionNotificationOutboxEventHandlerTest.propagates_send_failures_so_the_worker_marks_the_row_failed` | ✅ COMPLIANT |
| Buyer copy neutral Spanish, no refund | Buyer email states follow-up, no refund | `SpringMailPurchaseExceptionNotificationAdapterTest` (student body assertions, D9) | ✅ COMPLIANT |
| Ops copy may carry technical reason | Ops carries reason, buyer does not | `SpringMailPurchaseExceptionNotificationAdapterTest` (ops body contains reason; student body does not) | ✅ COMPLIANT |
| Email lookup returns only email, no auth import | ArchUnit forbids auth dependency from billing | `BillingArchitectureTest` (part of billing suite, 592 green) — confirmed statically: only JavaDoc references to `com.menta.auth.*` exist under `api/billing/src/main`, zero actual imports | ✅ COMPLIANT |
| Email lookup returns only email, no auth import | Lookup miss still lets ops email send | `SpringMailPurchaseExceptionNotificationAdapterTest` (unresolved email → ops still sent, student skipped) | ✅ COMPLIANT |
| Reason is payload-only, never persisted | No schema change persists the reason | Static evidence: no `reason` column on `PurchaseJpaEntity`/`billing_purchases`; no V21 migration exists (latest migration unchanged at V20_1); payload carries `reason` only via `PurchaseExceptionedOutboxPayload`/`PaymentFulfillmentFailedOutboxPayload` | ✅ COMPLIANT |
| Payment with no Purchase row still notifies buyer | Missing Purchase row still produces notification | `PhysicalCapacityAssignmentOutboxEventHandlerTest` (publish fires at sites 130/202/239) + `PhysicalPurchaseIntegrationTest.the_payment_level_fallback_survives_the_doomed_worker_transaction_and_is_redelivery_safe` (D.3, the C2 proof) | ✅ COMPLIANT |
| Payment with no Purchase row still notifies buyer | Payment-level fallback is redelivery-safe | Same D.3 integration test (retry adds no second row/pair) | ✅ COMPLIANT |
| Only physical purchases are in scope | Virtual EXCEPTION emits no notification event | `PaymentVerificationServiceTest.a_missing_plan_degrades_the_subscription_to_exception_instead_of_an_empty_snapshot` (D.4, structural proof — service has no outbox/notification port at all) | ✅ COMPLIANT |

**Capability: presential-purchase-fulfillment (delta)**

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Residual EXCEPTION is all-or-nothing (MODIFIED — now also emits notification) | One of N sessions fails — zero partial rows, Purchase is EXCEPTION | Existing `PhysicalCapacityAssignmentOutboxEventHandlerTest` + `PhysicalPurchaseIntegrationTest` suite (pre-existing, unaffected) | ✅ COMPLIANT |
| Residual EXCEPTION is all-or-nothing | Capacity invariant trips — Purchase flips to EXCEPTION | Same suite; `MarkPurchaseExceptionUseCaseTest` append assertion added on this exact edge | ✅ COMPLIANT |
| Residual EXCEPTION is all-or-nothing | UNIQUE race routes to EXCEPTION | Same suite (pre-existing, unaffected) | ✅ COMPLIANT |
| Residual EXCEPTION is all-or-nothing | Concurrent last-spot race — one ASSIGNED, one EXCEPTION | Same suite (pre-existing, unaffected) | ✅ COMPLIANT |
| Reaching EXCEPTION emits a durable notification event (ADDED) | Real EXCEPTION transition appends exactly one event | `MarkPurchaseExceptionUseCaseTest` (A.1) + `PhysicalPurchaseIntegrationTest` D.1 (real DB row) | ✅ COMPLIANT |
| Reaching EXCEPTION emits a durable notification event | Redelivered no-op transition appends no event | `MarkPurchaseExceptionUseCaseTest` (EXCEPTION→EXCEPTION, zero appends) + D.1 redelivery assertion | ✅ COMPLIANT |
| Reaching EXCEPTION emits a durable notification event | Rolled-back transition leaves no outbox row | `PhysicalPurchaseIntegrationTest.a_rolled_back_exception_transition_appends_no_additional_outbox_row` (D.2) | ✅ COMPLIANT |

**Compliance summary**: 18/18 scenarios compliant across 11 requirements (9 in `purchase-exception-notification`, 2 in `presential-purchase-fulfillment` delta).

### Correctness (Static Evidence)
| Requirement | Status | Notes |
|---|---|---|
| D1 trigger point inside `MarkPurchaseExceptionUseCase` | ✅ Implemented | Append happens after `save(purchase.exception())`, inside `REQUIRED` tx, exactly as designed |
| D10 payment-level publisher, `REQUIRES_NEW` | ✅ Implemented | `PublishPaymentFulfillmentFailedUseCase.publish` annotated `@Transactional(propagation = REQUIRES_NEW)` |
| C1 three call sites (130/202/239) wired | ✅ Implemented | `PhysicalCapacityAssignmentOutboxEventHandler` calls `publishPaymentFulfillmentFailed` before all three pre-Purchase `markException` sites; two post-Purchase sites (164/267 equivalents) unaffected |
| C3 two distinct event types | ✅ Implemented | `PURCHASE_EXCEPTIONED` and `PAYMENT_FULFILLMENT_FAILED` are separate constants/payloads |
| C4 email resolution in mail adapter, ops never suppressed | ✅ Implemented | `SpringMailPurchaseExceptionNotificationAdapter.notify` resolves student email, sends ops unconditionally |
| C5 Spanish copy, no refund/name for student | ✅ Implemented | Inline constants match design's exact wording |
| D6 no reason column/migration | ✅ Implemented | No V21 migration; `PurchaseJpaEntity` has no reason field |
| D7 email-only port | ✅ Implemented | `UserEmailLookupPort.findEmailById(UUID) → Optional<String>` |
| D8 single ops address | ✅ Implemented | `@Value("${billing.purchase-exception.ops-address:...}")`, single string |

### Coherence (Design)
| Decision | Followed? | Notes |
|---|---|---|
| C1 — 3 call sites, defect not fixed | ✅ Yes | Confirmed in code; defect tracked as issue #238 (open), explicitly out of scope for this change |
| C2 — REQUIRES_NEW for D10, REQUIRED for D1 | ✅ Yes | Verified directly in source (`Propagation.REQUIRES_NEW` vs `Propagation.REQUIRED`) |
| C3 — two event types, not one nullable-purchaseId event | ✅ Yes | Two distinct constants/payload records |
| C4 — email resolution in infra adapter, not application | ✅ Yes | Lookup lives in `SpringMailPurchaseExceptionNotificationAdapter` (infrastructure layer) |
| C5 — inline copy constants, no template engine | ✅ Yes | Matches `SpringMailActivationNotificationAdapter` precedent |
| No migration (D6) | ✅ Yes | Confirmed: latest migration file unchanged, no reason column |

### Issues Found

**CRITICAL**: None

**WARNING**: None

**SUGGESTION**:
- No dedicated "TDD Cycle Evidence" table exists in the apply-progress artifact in the exact format `strict-tdd-verify.md` expects; RED/GREEN task labeling in `tasks.md` (e.g. "A.1 RED", "A.2 GREEN") substitutes for it and is sufficient evidence TDD was followed, but a future change should use the canonical table format for easier automated cross-referencing.
- Two side defects were discovered during implementation and correctly filed as separate GitHub issues rather than silently fixed or left undocumented: #238 (pre-existing `PaymentNotFoundException` state-machine escape at 3 call sites) and #242 (outbox composite unique constraint never enforced under Testcontainers, a repo-wide test-infra gap). Both are explicitly out of scope for #209 per its own proposal.md and are not treated as gaps in this verification.

### Verdict
**PASS**

Every requirement and scenario in both spec files is implemented and covered by a passing test; all 40 tasks (A.1–D.6) are checked and verified against actual code; the full test suite (2073 tests across shared/auth/virtual/physical/billing/app) and every layered coverage floor pass with zero failures/errors; `./gradlew build` succeeds end-to-end. The change is ready for `sdd-archive`, with issues #238 and #242 correctly tracked as separate, explicitly out-of-scope follow-ups rather than blockers.

# Tasks: fix-virtual-payment-fulfillment

> Minimal, topologically ordered strict-TDD delivery for issue #114. The
> production correction is already in `0bd2182`; this change adds its missing
> webhook-to-persisted-entitlement regression guard.

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~120–180 LOC (test + OpenSpec records) |
| Production changes | None expected |
| 400-line review budget risk | Low |
| Chained PRs recommended | **No** |
| Delivery strategy | One PR to `develop` |
| Forecast gate per task | TASK-001 ~120–160 LOC; TASK-002 verification/artifacts only |

```text
Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: single PR
400-line budget risk: Low
```

## 1. Webhook-to-subscription regression scenario

### TASK-001 — Add the approved virtual-payment fulfillment E2E regression

- **Status**: completed
- **Dependencies**: none
- **Estimated LOC**: 0 production + ~120–160 test
- **Module(s) touched**: `api:app`
- **Files (modify)**:
  - `api/app/src/test/java/com/menta/app/integration/billing/PaymentWebhookIntegrationTest.java`
- **New build dependencies**: none
- **Strict TDD — Red**:
  1. Extend the existing integration fixture to create an active Billing plan
     with two deterministic course IDs through the real subscription-checkout
     use case; do not construct payment or subscription JPA entities directly.
  2. Add
     `a_matching_approved_virtual_payment_activates_and_assigns_the_local_subscription`.
     It must process a signed/received webhook through the actual inbox worker,
     using a mocked `PaymentProviderPort` only for the matching approved provider
     result.
  3. Run:

     ```bash
     ./gradlew :api:app:test \
       --tests com.menta.app.integration.billing.PaymentWebhookIntegrationTest.a_matching_approved_virtual_payment_activates_and_assigns_the_local_subscription \
       --no-daemon
     ```

- **Strict TDD — Green**:
  - Complete only test fixture wiring, imports, cleanup, and assertions. Do not
    modify production code unless this scenario exposes a real current-path
    regression; in that case, stop and amend the SDD design before a fix.
  - Re-run the same targeted test until green.
- **Acceptance criteria**:
  - **AC-1 / Requirement “Approved Virtual Payment Completes Local Fulfillment”**:
    the persisted payment is `COMPLETED`, and its checkout-created subscription
    is `ACTIVE` and `ASSIGNED`; the successful path never persists
    `EXCEPTION`.
  - **AC-2 / Requirement “Assignment Freezes the Purchased Course Snapshot”**:
    the persisted subscription-course rows are exactly the two plan course IDs
    seeded by this test.
  - **AC-3 / Requirement “Virtual Fulfillment Is Billing-Owned and Pull-Based”**:
    no `Purchase` and no `common_outbox_events` row are created by this virtual
    confirmation.
  - The cleanup removes subscription-course rows before subscriptions/plans so
    this Testcontainers-backed suite remains order-independent.
- **Persistence / migration impact**: none; assertions use existing Billing
  persistence only.
- **Contract impact**: none; the existing webhook contract is exercised without
  changing OpenAPI or Bruno.

## 2. Verify boundaries and record delivery

### TASK-002 — Run focused/full verification and finalize the SDD record

- **Status**: completed
- **Dependencies**: TASK-001
- **Estimated LOC**: ~10–20 documentation only
- **Module(s) touched**: `api:app`, `api:billing`, `openspec`
- **Files (modify)**:
  - `openspec/changes/fix-virtual-payment-fulfillment/tasks.md` (mark task
    status and record actual verification evidence)
  - `openspec/changes/fix-virtual-payment-fulfillment/design.md` only if
    TASK-001 reveals a factual mismatch; otherwise unchanged
- **Verification**:

  ```bash
  ./gradlew :api:app:test \
    --tests com.menta.app.integration.billing.PaymentWebhookIntegrationTest \
    --no-daemon
  ./gradlew :api:billing:test --no-daemon
  ./gradlew check --no-daemon
  ```

- **Acceptance criteria**:
  - **AC-4 / Requirement “Reprocessing Is Idempotent and Repairs Historical
    Fulfillment”**: existing `PaymentVerificationService` unit coverage remains
    green; do not duplicate it in the new E2E scenario.
  - **AC-5 / Requirement “Genuine Local Fulfillment Failure Is Auditable”**:
    existing Billing behavior tests remain green; do not add a synthetic
    persistence-failure test because it would require a new transactional
    design.
  - **AC-6 / Requirement “Billing Boundaries Remain Enforced”**:
    `:api:billing:test` and project `check` are green, with no production/test
    source added under `api/virtual`, no shared write contract, migration,
    OpenAPI, or Bruno change.
- Billing's currently approved domain/application JaCoCo threshold remains
  90%; #138 owns the separate coverage-policy review.
- **Delivery**: one conventional commit/PR, proposed title
  `test(billing): cover virtual payment fulfillment webhook`.
- **Stop condition**: any production behavior change, schema/contract change,
  or new Virtual interaction is out of this task plan and requires design
  amendment before implementation.

## Verification evidence

- `./gradlew :api:app:test --tests
  com.menta.app.integration.billing.PaymentWebhookIntegrationTest --rerun-tasks
  --no-daemon` — passed: 12 tests, including the new virtual fulfillment
  regression.
- `./gradlew :api:billing:test --no-daemon` — passed.
- `./gradlew check --no-daemon` — passed with the repository's pre-existing
  Checkstyle warnings; no production, schema, API, Bruno, or Virtual-module
  changes were required.

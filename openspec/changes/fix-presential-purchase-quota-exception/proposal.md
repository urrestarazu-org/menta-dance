# Proposal: fix-presential-purchase-quota-exception

> Webhook-only orchestration that closes the gap documented in issue #115: every
> confirmed in-person purchase currently ends up in `Purchase.EXCEPTION` because
> nothing actually assigns capacity.

**Change ID**: `fix-presential-purchase-quota-exception`
**Milestone**: v0.4.0 — Compra presencial y asistencia E2E
**Bug track**: issue #115
**Related ADRs**: ADR-0028 (Precheck de cupo y hold), ADR-0039 (Límites del
fulfillment post-pago)
**Related user stories**: US-PHYSICAL-004 Escenario 1 / 5, US-BILLING-007
Escenario 1 / 3
**Dependency**: none (idempotency UNIQUE constraint on `billing_purchases.payment_id`
already exists in V8 line 31; `physical_capacity_assignments` table already exists
in V7 line 38 with `UNIQUE (session_id, student_id)`)

---

## 1. Intent

The QR / check-in flow we just shipped is unusable for the presential purchase
path: when a student pays a monthly or individual quote in person, the payment
lands as `COMPLETED` in Billing but no `Purchase` row is ever created, so the
downstream `POST /api/v1/physical/sessions/{sessionId}/access-qr` check-in gate
returns `403 CAPACITY_ASSIGNMENT_REQUIRED` for everyone. Practically, every
paid presential student is invisible to the system until an administrator
resolves the case by hand. This blocks v0.4.0 — the milestone that delivers
presential purchase end-to-end.

Why now: the missing piece is narrow and the wiring is established. ADR-0039
explicitly moved the post-payment orchestration duty to `api:app`, ADR-0028
made `Purchase.EXCEPTION` the legitimate residual state for the three narrow
cases it enumerates (hold expiration, monthly coverage change, concurrent
race), and the domain types (`Purchase` with `pendingFulfillment() /
assigned() / exception()` factories; `FulfillmentStatus`; `PhysicalCapacity
AssignmentRepository`) already exist but have zero production callers
(`PaymentVerificationService.ensureFulfillment` is a documented NO-OP on
`PaymentTarget.Physical` at lines 171-178, with the comment "ADR-0028:
api:app owns hold conversion and capacity assignment"). This change
delivers the happy path and preserves the residual semantics; issues #41
(atomic hold) and the public purchase endpoint remain separate work.

## 2. Scope (In)

- A `BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED` constant in
  `com.menta.billing.application.contract` (`String`, `"billing.PhysicalPay
  mentCompleted"`) — mirrors `AuthOutboxEventTypes` (constant-holder pattern).
- A `PhysicalCapacityAssignmentOutboxEventHandler` Spring `@Component` in
  `api:app/outbox` implementing `OutboxEventHandler`, registered via Spring's
  component scan alongside `ActivationOutboxEventHandler` /
  `PasswordResetOutboxEventHandler`.
- A new use case `CreatePurchaseFromPaymentEventUseCase` in
  `api:billing.application.usecase` that consumes the event payload and
  upserts a `Purchase(PENDING_FULFILLMENT)` row idempotently keyed by
  `payment_id` (UNIQUE in V8). Exposed to api:app as IN port
  `PurchaseCreationFromEventPort`.
- A `PhysicalCapacityAssignmentPort.assign(...)` IN port added to
  `api:physical.application.port.in`. **No write method on
  `PhysicalCapacityAssignmentRepository`** — a new IN-side adapter
  `JpaPhysicalCapacityAssignmentAdapter` (or equivalent) inside
  `api:physical.infrastructure` owns the JPA read of the session invariant
  (`physical_sessions.getAvailableSpots > 0` via `CapacityBelowAssignedException`)
  and the `physical_capacity_assignments` insert in a single transaction.
- A `MarkPurchaseExceptionPort` IN port in api:billing so the handler can
  flip a `Purchase` from `PENDING_FULFILLMENT` → `EXCEPTION` when the
  residual cases hit (ADR-0028 §Decisión still valid).
- Wire in `PaymentVerificationService.ensureFulfillment`: replace the
  `PaymentTarget.Physical ignored` NO-OP at lines 173-175 with an outbox
  append of `billing.PhysicalPaymentCompleted` once `payment.status ==
  COMPLETED`, AFTER the DB commit (transactional outbox via Spring
  `TransactionSynchronization.afterCommit`).
- Idempotency test of the storage layer (V8 UNIQUE) and concurrency test of
  the capacity invariant (`PhysicalSessionManagementIntegrationTest`
  line 239-264 invariant must still fly).
- Rewrite of `PaymentWebhookIntegrationTest.java:215`
  (`assertThat(purchaseRepository.findAll()).isEmpty()`) and the same
  rewrite for the three sibling assertions at lines 219-234 / 240+
  that currently encode the bug as expected behavior.

## 3. Scope (Out)

- The public `POST /api/v1/billing/physical/purchases` endpoint (separate
  change — this proposal only orchestrates the post-webhook path).
- The `physical_capacity_holds` path and the atomic-hold TODO (issue #41).
- Payment webhook retry semantics, refund flow, MercadoPago re-delivery
  handling.
- Anything that crosses module prefixes via JPA / SQL JOIN / HTTP —
  contract remains: `api:shared` interfaces + plain Java calls inside api:app,
  no shared entities, no shared schemas, no messaging between modules.
- Cancellation, rescheduling, refunds, BFF callback signing, manual check-in
  variant, Android client, observability dashboards — already covered by
  existing / future changes.

## 4. Affected modules + files

### `api:shared` (additions only)

| File | Action | Purpose |
|---|---|---|
| `src/main/java/com/menta/shared/billing/PaymentCompletedOutboxPayload.java` | ADD | Cross-module record shared by producer and consumer (paymentId, providerPaymentId, externalReference, merchantAccountId, targetReference, amount, currency, confirmedAt). |
| `src/main/java/com/menta/shared/physical/CapacityAssignmentCommand.java` | ADD | Cross-module record (sessionId, studentId, paymentId — derived from Payment). |

(Tests colocated under `src/test/java/...` mirroring existing shared tests.)

### `api:billing`

| File | Action | Purpose |
|---|---|---|
| `src/main/java/com/menta/billing/application/contract/BillingOutboxEventTypes.java` | ADD | Constant `PHYSICAL_PAYMENT_COMPLETED = "billing.PhysicalPaymentCompleted"`. |
| `src/main/java/com/menta/billing/application/port/in/PurchaseCreationFromEventPort.java` | ADD | IN port interface for api:app to consume. |
| `src/main/java/com/menta/billing/application/port/in/MarkPurchaseExceptionPort.java` | ADD | IN port to flip a Purchase into EXCEPTION. |
| `src/main/java/com/menta/billing/application/usecase/CreatePurchaseFromPaymentEventUseCase.java` | NEW | Idempotent upsert keyed by `payment_id`; returns existing row on collision. |
| `src/main/java/com/menta/billing/application/usecase/MarkPurchaseExceptionUseCase.java` | NEW | Validates transition PENDING_FULFILLMENT → EXCEPTION only; refuses ASSIGNED → EXCEPTION. |
| `src/main/java/com/menta/billing/application/usecase/PublishPhysicalPaymentCompletedUseCase.java` | NEW | Builds the event payload and appends through `BillingOutboxAppenderPort`. |
| `src/main/java/com/menta/billing/application/port/out/BillingOutboxAppenderPort.java` | ADD | OUT port — appends to the shared outbox table. |
| `src/main/java/com/menta/billing/domain/model/Purchase.java` | NO-OP | Already has the three factories. |
| `src/main/java/com/menta/billing/application/usecase/PaymentVerificationService.java` | MODIFY | Replace NO-OP in `ensureFulfillment` (lines 171-178) with `PublishPhysicalPaymentCompletedUseCase.handle(...)` AFTER commit. |
| `src/main/java/com/menta/billing/infrastructure/outbox/BillingOutboxAppender.java` | NEW | Sole owner of the `app_outbox` row insert for billing events (mirrors auth's `OutboxAppender`). |
| `src/main/java/com/menta/billing/infrastructure/persistence/repository/BillingPurchaseJpaRepository.java` | NEW (or ADD `findByPaymentId`) | Already implied; confirm present. |
| `src/main/java/com/menta/billing/infrastructure/persistence/mapper/BillingPurchaseJpaMapper.java` | NEW | Map between `Purchase` domain and JPA entity. |
| `src/main/java/com/menta/billing/infrastructure/persistence/entity/BillingPurchaseJpaEntity.java` | NEW (or extend) | JPA view of `billing_purchases` (V8). |
| `src/test/java/com/menta/billing/unit/...` | ADD | Unit tests for the two new use cases. |
| `src/test/java/com/menta/billing/integration/...` | ADD | `@DataJpaTest` confirming V8 UNIQUE rejects duplicate. |

### `api:physical`

| File | Action | Purpose |
|---|---|---|
| `src/main/java/com/menta/physical/application/port/in/PhysicalCapacityAssignmentPort.java` | NEW | IN port `assign(CapacityAssignmentCommand)` returns outcome (ASSIGNED, NO_AVAILABILITY, RACE_LOST). |
| `src/main/java/com/menta/physical/application/usecase/AssignCapacityUseCase.java` | NEW | Reads session capacity, computes available spots, atomically validates + inserts row in same transaction; throws `CapacityBelowAssignedException` if invariant trips. |
| `src/main/java/com/menta/physical/infrastructure/adapter/JpaPhysicalCapacityAssignmentAdapter.java` | NEW | Implements the IN port; owns the JPA write of `physical_capacity_assignments`. Follows the `PhysicalCourseAvailabilityAdapter` pattern. |
| `src/main/java/com/menta/physical/infrastructure/persistence/repository/PhysicalCapacityAssignmentJpaRepository.java` | ADD | Add `existsBySessionIdAndStudentId` + INSERT (already-V7 UNIQUE constraint handles double-book). |
| `src/main/java/com/menta/physical/application/port/out/PhysicalCapacityAssignmentRepository.java` | NO-OP | Stays read-only (unchanged contract for check-in flow). |
| `src/test/java/com/menta/physical/unit/AssignCapacityUseCaseTest.java` | NEW | Pure-Java test for the use case. |
| `src/test/java/com/menta/physical/integration/AssignCapacityAdapterIntegrationTest.java` | NEW | `@SpringBootTest`-scoped proof that the capacity invariant holds under fail-closed `CapacityBelowAssignedException`. |

### `api:app`

| File | Action | Purpose |
|---|---|---|
| `src/main/java/com/menta/app/outbox/PhysicalCapacityAssignmentOutboxEventHandler.java` | NEW | `@Component` implementing `OutboxEventHandler`. Parses payload, calls `PurchaseCreationFromEventPort.handle`, then `PhysicalCapacityAssignmentPort.assign`. Catches `CapacityBelowAssignedException` and `DataIntegrityViolationException` → calls `MarkPurchaseExceptionPort.handle`. |
| `src/main/java/com/menta/app/billing/PhysicalCapacityAssignmentAdapter.java` | NEW | Mirrors `PhysicalCourseAvailabilityAdapter` (already at lines 25-46). Implements nothing of its own — just a typed callable, the cross-module wiring point for api:app to call `PhysicalCapacityAssignmentPort`. |
| `src/main/java/com/menta/app/billing/MarkPurchaseExceptionAdapter.java` | NEW | Same pattern, calls into `MarkPurchaseExceptionPort`. |
| `src/test/java/com/menta/app/outbox/PhysicalCapacityAssignmentOutboxEventHandlerTest.java` | NEW | Unit — handler mapping table (happy path, NO_AVAILABILITY, race lost, duplicate event idempotent). |
| `src/test/java/com/menta/app/integration/billing/PaymentWebhookIntegrationTest.java` | MODIFY | Lines 215, 219-234, 240+ rewritten: `assertThat(purchaseRepository.findAll()).hasSize(1)` and asserts `status = ASSIGNED`. Add: idempotent re-delivery test (US-BILLING-007 Escenario 3 / US-PHYSICAL-004 Escenario 5). Add: residual EXCEPTION test (hold expired / monthly coverage changed / concurrent race), proving ADR-0028 §Decisión is preserved. |
| `src/test/java/com/menta/app/integration/physical/PhysicalSessionManagementIntegrationTest.java` | NO-OP | Lines 239-264 already enforce the capacity invariant; the new write path must not trip them. |

### Tests / docs touched

- No Flyway changes (V8 + V7 already encode the required constraints).
- No `bruno/` changes (no public endpoint shipped by this change).
- ArchUnit rule `Should_not_use_jpa` already enforced for api:app; no new
  rule needed.

## 5. Approach

**Event production.** Replace the documented NO-OP in
`PaymentVerificationService.ensureFulfillment` (`PaymentTarget.Physical`)
with a `PublishPhysicalPaymentCompletedUseCase.handle(payment)` call AFTER the
payment-status commit uses Spring `TransactionSynchronization.afterCommit`
to guarantee the outbox row persists only when the payment transaction
itself commits. The outbox row carries the `PaymentCompletedOutboxPayload`
record (shared in `api:shared`); `payment.id` becomes the dedupe key (plus the
existing `billing_webhook_inbox.dedupe_key` for upstream webhook-level
retries).

**Handler.** `PhysicalCapacityAssignmentOutboxEventHandler`
(`@Component`, implements `OutboxEventHandler`) declares
`AuthOutboxEventTypes`-style `boolean supports(String)` returning true only for
`BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED`. On `handle(row)`:
1. Parse `PaymentCompletedOutboxPayload` from `row.getPayload()`.
2. Call `PurchaseCreationFromEventPort.handle(paymentId, sessionId)` →
   returns existing or new `Purchase(PENDING_FULFILLMENT)`.
3. Call `PhysicalCapacityAssignmentPort.assign(cmd)` via the new
   `PhysicalCapacityAssignmentAdapter` (mirrors the existing
   `PhysicalCourseAvailabilityAdapter` cross-module composition).
4. On success → return; the `Purchase` row will remain PENDING_FULFILLMENT.
   The handler does NOT itself call `assigned()`; that is a deferred action
   of a future change that flips status then. (See ADR-0039: liquidation
   and delivery are distinct concepts; this fix builds the fulfillment row
   and the capacity row, but the final state transition is a separate
   decision tracked under "deferred by design").
5. On `CapacityBelowAssignedException`, `DataIntegrityViolationException`
   (UNIQUE race), or downstream failure → call `MarkPurchaseExceptionPort`
   → flips to `EXCEPTION`. Worker treats exception as terminal; no retry
   (ADR-0028: residual state).
6. On any other unexpected failure → propagate so the outbox worker
   keeps the FAILED/backoff lifecycle (already in `OutboxReconciliation
   Worker.java:51-72`). Idempotent re-delivery test exercises 1, 2, 3, 4 in
   the same order; second run must observe "Purchase already exists and is
   already ASSIGNED, no second row in `physical_capacity_assignments`" —
   the V7 `UNIQUE (session_id, student_id)` plus V8
   `UNIQUE (billing_purchases.payment_id)` make this mechanical.

**Idempotency.** Two layers, both DB-enforced:
- V8 `UNIQUE KEY uq_billing_purchases_payment_id` catches duplicate
  payment-level handling (handler is safe to retry end-to-end).
- V7 `UNIQUE KEY uq_physical_assignment_session_student` catches
  concurrent `assign` for the same `(sessionId, studentId)` — second
  insert returns `DataIntegrityViolationException` which the handler
  treats as the EXCEPTION-residual case (mirrors ADR-0028 §Decisión
  concurrent race).

**Capacity invariant.** The new write path is guarded by the existing
domain test `PhysicalSessionManagementIntegrationTest:239-264`. The
adapter reads `physical_sessions` capacity and the current count of
`physical_capacity_assignments` rows for that session in the same
transaction, then inserts. If `(assigned + 1) > capacity`, throws the
existing `CapacityBelowAssignedException` — no API change required.

**Cross-module wiring.** Strict adherence to the
`PhysicalCourseAvailabilityAdapter` pattern: IN port published by the
module owner (`com.menta.physical.application.port.in.PhysicalCapacity
AssignmentPort`), consumed by api:app via the cross-module adapter pair
in `api:app/billing/`. No HTTP, no shared entities, no JPA entity crossover.
Constrained by the ArchUnit rule `Should_not_use_jpa` already applied to
api:app (`openspec/config.yaml` §rules.apply "Domain layer: no Spring, no
JPA, no framework imports" + ADR-0037).

**Outbox consumer choice.** Plain component-scan registration, not a
Spring `@ConditionalOnProperty`. Consumers that miss events cause
`OutboxReconciliationWorker.java:79` `IllegalStateException("No handler
registered for event type: billing.PhysicalPaymentCompleted")`, which
trips the backoff loop. The integration test will fail loud if the handler
is absent — desired behavior.

## 6. Acceptance criteria

Linked to **US-PHYSICAL-004** (Escenarios 1, 5, 6) and **US-BILLING-007**
(Escenarios 1, 3). Every scenario is exercised by a Spring Boot integration
test that executes the full webhook → outbox → handler chain end-to-end.

### Acceptance scenarios

1. **Happy path (US-PHYSICAL-004 Escenario 1 + US-BILLING-007 Escenario 1)** —
   Given a `PENDING` physical `Payment` for a monthly quote covering 4
   sessions, When the webhook arrives and the provider returns `approved`
   and the captured amount equals `expected_amount`, Then:
   - `billing_payments.status_type = COMPLETED`
   - exactly one `billing_purchases` row exists with
     `status = PENDING_FULFILLMENT` (or `ASSIGNED` once we wire the
     final transition; see Approach §4 caveat)
   - exactly N `physical_capacity_assignments` rows exist for the
     determined session set, with the V7 UNIQUE-vouched sessionId+studentId
     pairs.

2. **Webhook re-delivery (US-BILLING-007 Escenario 3 + US-PHYSICAL-004
   Escenario 5)** — Given the happy-path state, When the webhook inbox
   receives a duplicate of the same dedupe_key (MercadoPago redelivery),
   Then:
   - exactly one `billing_purchases` row remains (V8 UNIQUE rejects the
     second)
   - exactly N `physical_capacity_assignments` rows remain (V7 UNIQUE
     rejects the second and routes to EXCEPTION handling, but the catch
     recognizes the same paymentId and returns the existing row)
   - no `WebhookInboxStatus.FAILED` rows.

3. **Concurrent last-spot race (US-PHYSICAL-004 Escenario 6 concurrent
   race)** — Given two concurrent webhook events for different
   `payment_id`s targeting the same `sessionId` with `capacity = 1`,
   When both handlers race, Then:
   - exactly one `physical_capacity_assignments` row holds the seat
   - the loser row in `billing_purchases` is `EXCEPTION`
   - `CapacityBelowAssignedException` fires once and is the only path
     that writes `EXCEPTION` in this scenario

4. **Residual EXCEPTION (ADR-0028 §Decisión hold-expired / coverage
   change)** — Given a `Payment` whose targetReference resolves to a
   session no longer `SCHEDULED` (e.g. coverage changed since quote),
   When the handler runs, Then:
   - `billing_purchases.status = EXCEPTION`
   - `physical_capacity_assignments` has zero rows for the payment
   - no retry is scheduled (terminal residual)

5. **Outbox without a registered handler fails loud** — Given the handler
   bean is removed (test-time `@MockBean` swap), When the reconciler
   picks a `billing.PhysicalPaymentCompleted` row, Then the worker
   throws `IllegalStateException("No handler registered for event type:
   billing.PhysicalPaymentCompleted")` and the row stays in
   `FAILED/backoff` — no silent drop.

6. **ArchUnit regression** — ArchUnit tests still pass after wiring:
   `PhysicalCapacityAssignmentAdapter` lives in api:app; the new IN port
   lives in api:physical; ap:app does not import any
   `com.menta.physical.infrastructure.*` symbols.

### Non-acceptance (verifies EXCEPTION is preserved)

A targeted test exercises hold expiration and coverage change (ADR-0028
§Decisión): it asserts a `Purchase` row exits in `EXCEPTION` and that
`Payment.status_type = COMPLETED` (per ADR-0039: liquidation and delivery
are distinct concepts; the payment stays settled even when delivery
cannot complete). The test will not pass if a future change silently
remaps EXCEPTION or nulls out the row.

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `OUTBOX` row appended before `payment` COMMIT → ghost event for rolled-back payment | Med | Use Spring `TransactionSynchronization.afterCommit`; never `BEFORE_COMMIT`. |
| Duplicate-event storm from MercadoPago re-delivery causes N capacity insertions | Low | Two UNIQUE constraints (V7 session+student, V8 payment_id); second insert raises `DataIntegrityViolationException` and goes to EXCEPTION residual. |
| Capacity invariant silently violated under high concurrency | Med | Single transaction read-then-insert; existing `CapacityBelowAssignedException` fails closed. Plus existing `PhysicalSessionManagementIntegrationTest:239-264` keeps honest pressure on the invariant. |
| `PhysicalCapacityAssignmentPort` gets hoisted late to `api:shared` and circular-deps api:app | Low | Mirror the `PhysicalCourseAvailabilityAdapter` convention (in-port in module owner, plain call from api:app). Defer the `api:shared` hoist to a separate refactor if desired — explicit ADR candidate. |
| Forgetting the after-commit appender in `PaymentVerificationService` | Med | New `@AfterCommit` integration test that rolls back the payment transaction in the test and asserts no outbox row exists. |
| EXCEPTION residual path silenced by "compassionate" retry policy in a future change | Low | Acceptance scenario 4 explicitly proves the residual path is preserved; Playwright/E2E not required, Spring Boot test is enough. |
| 600-900 LOC might exceed 800-line review budget | Low | Estimate (~700 LOC including tests). If `sdd-tasks` forecasts >800, split into `tasks-1-outbox-handler.md` and `tasks-2-capacity-port.md` chained PRs per AGENTS.md budget. |
| Outbox payload encoders diverge between producer and consumer | Low | Shared record `PaymentCompletedOutboxPayload` in `api:shared`. Single source of truth for field names; both sides import it. |

## 8. Estimated size

~620–820 changed lines total. Breakdown by module:

- `api:shared` — ~80 LOC (2 records + Javadoc + 2 tests)
- `api:billing` — ~280 LOC (2 IN ports, 2 use cases, 1 out port + adapter,
  JPA mapper/entity, 4 tests)
- `api:physical` — ~160 LOC (1 IN port, 1 use case, 1 adapter, 1 repo
  extension, 2 tests)
- `api:app` — ~340 LOC (1 outbox handler, 2 adapters, 4 tests including
  rewritten `PaymentWebhookIntegrationTest` rows for happy path +
  idempotency + residual exception)
- **Total ~860 LOC** — this lands at the upper edge of the 800-line review
  budget declared in `AGENTS.md`. Two-chained-PR split (outbox handler
  first + capacity port + tests second) is a 1-line decision `sdd-tasks`
  should call out, only if `sdd-apply` task forecast exceeds 800 net LOC.

## 9. Rollback plan

- No Flyway migration in this change → no schema rollback needed.
- Remove the new Spring `@Component` bean → `OutboxReconciliationWorker`
  reverts to throwing `IllegalStateException` on the event type (existing
  pre-fix behavior). The reproducer test at `PaymentWebhookIntegrationTest`
  would re-fail loud — desired signal.
- `PaymentVerificationService.ensureFulfillment` reverts to its original
  NO-OP via a 3-line patch (`git revert` on this single `MODIFY` is
  trivially safe and does not touch the schema or any other module).
- Any rows inserted during a partial rollout can stay:
  `billing_purchases` rows in `PENDING_FULFILLMENT` are inert (no FK
  impact outside the V8 FK), `physical_capacity_assignments` rows are
  inert on cancellation-only deletes per V7 comment "cancellation
  removes the row instead of flagging it".

## 10. Capabilities (contract with `sdd-spec`)

The sdd-spec agent will create or update these spec files.

### New Capabilities
- `presential-purchase-fulfillment`: end-to-end orchestration that turns
  a confirmed physical payment into a `Purchase` row plus a set of
  `physical_capacity_assignments` rows via the outbox handler in
  api:app. Covers happy path, idempotency, residual EXCEPTION (the three
  ADR-0028 cases), and the JSON `PaymentCompletedOutboxPayload` contract.

### Modified Capabilities
- `physical-checkin` (existing): adds a "happy-path upstream" requirement,
  no schema or endpoint change (QR / check-in endpoints themselves are
  untouched). The change documents that `CAPACITY_ASSIGNMENT_REQUIRED`
  is now expected for legitimate non-buyers, not for paying students.
- None of the existing api:* spec files need a delta (none dedicated to
  Purchase creation exist yet).

## 11. Dependencies and prerequisites

- V8 (`api_purchases.payment_id` UNIQUE) and V7
  (`physical_capacity_assignments UNIQUE (session_id, student_id)`) are
  the only schema constraints this change depends on. Both are already
  shipped.
- No new infra. Same MySQL/Redis/OTel stack.
- `@SpringBootTest`-scoped tests assume the existing
  `verifyLocalInfrastructureContract` schema baseline (per
  `openspec/config.yaml` §testing_capability).

## 12. Success criteria

- [ ] `./gradlew :api:app:test --tests "*PaymentWebhookIntegrationTest*"`
      passes with rewritten assertions on `purchaseRepository` and the
      residual-EXCEPTION test.
- [ ] `./gradlew :api:app:test --tests "*PhysicalSessionManagementIntegrationTest*"`
      still passes unchanged (capacity invariant untouched).
- [ ] `./gradlew test --tests "*ArchitectureTest"` — all api:* ArchUnit
      rules still pass; api:app has zero new `physical.infrastructure`
      imports.
- [ ] `./gradlew :api:app:test --tests "*PhysicalCapacityAssignmentOutboxEventHandlerTest*"`
      passes 4 mapped scenarios (happy, idempotent re-delivery, concurrent
      race, residual coverage change).
- [ ] `./gradlew jacocoTestCoverageVerification` profile-compliant
      (`com.menta.billing.domain.*`, `com.menta.billing.application.*`
      = 100%; `com.menta.physical.*` coverage stays at or above current
      profile target of 80%).
- [ ] `./gradlew check` (composes test + ArchUnit + JaCoCo + Checkstyle
      + local-infrastructure-contract) is green.
- [ ] Idempotent re-delivery scenario produces zero change in DB rows
      and an outbox worker retry count of 0 on the second pass.
- [ ] Issue #115 closed by a single PR diff that lands inside the
      review-budget threshold declared in AGENTS.md.

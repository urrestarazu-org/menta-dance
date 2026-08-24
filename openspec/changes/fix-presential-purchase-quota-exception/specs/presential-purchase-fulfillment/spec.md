# Presential Purchase Fulfillment Specification

## Purpose

Turn a confirmed physical `Payment` (status `COMPLETED`) into one `billing_purchases` row plus the matching `physical_capacity_assignments` rows, dispatched through the `app_outbox` event `billing.PhysicalPaymentCompleted`. The flow MUST keep ADR-0028's residual `EXCEPTION` cases (hold expired, monthly coverage change, concurrent last-spot race) reachable as the terminal state, while unblocking `POST /api/v1/physical/sessions/{sessionId}/access-qr` for every paying student.

## Requirements

### Requirement: Physical payments publish one committed outbox event

When a `PaymentTarget.Physical` payment reaches `PaymentStatus.Completed`, Billing MUST append exactly one `app_outbox` row with `event_type = billing.PhysicalPaymentCompleted` AFTER the payment-status DB commit, carrying `paymentId, providerPaymentId, externalReference, merchantAccountId, targetReference, amount, currency, confirmedAt`. Virtual payments MUST NOT publish this event.

#### Scenario: Completed physical payment appends one outbox row

- GIVEN a `PaymentTarget.Physical` payment whose provider returns `approved` with amount equal to `expected_amount`
- WHEN the payment-status row commits to `COMPLETED`
- THEN exactly one `app_outbox` row exists with `event_type = billing.PhysicalPaymentCompleted` and the `paymentId` field equals the payment's id

#### Scenario: Completed virtual payment publishes no physical event

- GIVEN a `PaymentTarget.Virtual` payment whose provider returns `approved`
- WHEN the payment-status row commits to `COMPLETED`
- THEN zero rows exist in `app_outbox` with `event_type = billing.PhysicalPaymentCompleted`
- AND the existing subscription activation path remains the only fulfillment side-effect

### Requirement: Payment rollback writes no ghost event or purchase

A payment whose DB transaction is rolled back MUST leave no `app_outbox` row with `event_type = billing.PhysicalPaymentCompleted` and no `billing_purchases` row for that `payment_id`.

#### Scenario: Rolled-back payment leaves empty outbox and empty purchases

- GIVEN a payment whose DB transaction is rolled back after the provider returns `approved` but before commit
- WHEN the rollback completes
- THEN no `app_outbox` row references that `payment_id` under `billing.PhysicalPaymentCompleted`
- AND no `billing_purchases` row exists for that `payment_id`

### Requirement: Outbox handler creates one Purchase per payment (idempotent)

A `billing.PhysicalPaymentCompleted` event MUST result in at most one `billing_purchases` row per `payment_id`. Re-delivery with the same `payment_id` MUST surface the existing row, never a second insert.

#### Scenario: First-time event creates a PENDING_FULFILLMENT purchase

- GIVEN an `app_outbox` row whose `paymentId` is `P1` and no `billing_purchases` row references `P1`
- WHEN the outbox handler dispatches the event
- THEN exactly one `billing_purchases` row exists for `P1` with `status = PENDING_FULFILLMENT`

#### Scenario: Re-delivery with same payment_id is idempotent

- GIVEN an existing `billing_purchases` row for `payment_id = P1` in any non-EXCEPTION status
- WHEN the reconciler delivers another event bearing `payment_id = P1`
- THEN exactly one `billing_purchases` row still exists for `P1`
- AND zero new `physical_capacity_assignments` rows are inserted

### Requirement: Successful assignCapacity makes a Purchase ASSIGNABLE

A successful `PhysicalCapacityAssignmentPort.assign(...)` for `(sessionId, studentId)` MUST result in one `physical_capacity_assignments` row and a `billing_purchases.status = ASSIGNED`, so the QR check-in gate serves that student.

#### Scenario: Capacity available — the Assignment unblocks QR

- GIVEN a session whose current `physical_capacity_assignments` count is below its `capacity`
- WHEN the outbox handler completes `assignCapacity` for `(sessionId, studentId)`
- THEN one `physical_capacity_assignments` row exists for that pair
- AND the `billing_purchases` row has `status = ASSIGNED`
- AND `POST /api/v1/physical/sessions/{sessionId}/access-qr` for that student returns 200 with `qrCredentials`, `expiresAt`, and `refreshAfterSeconds=30`

### Requirement: Residual EXCEPTION preserves the three documented failure modes

When the handler hits a residual failure — V7 UNIQUE conflict on `(sessionId, studentId)`, `CapacityBelowAssignedException`, or a target session no longer `SCHEDULED` — it MUST flip `billing_purchases` to `EXCEPTION`, leave `billing_payments.status_type = COMPLETED` (ADR-0039: liquidation and delivery are distinct), insert ZERO `physical_capacity_assignments` rows for that payment, and NOT schedule a retry. Future changes MUST NOT silently remap or null out this `EXCEPTION` state.

#### Scenario: Capacity invariant trips — Purchase flips to EXCEPTION

- GIVEN a session whose current `physical_capacity_assignments` count equals its `capacity`
- WHEN the handler runs `assignCapacity` for a fresh `(sessionId, studentId)`
- THEN `CapacityBelowAssignedException` is thrown
- AND the handler routes through `MarkPurchaseExceptionPort`
- AND the `billing_purchases` row is `status = EXCEPTION`
- AND `billing_payments.status_type` remains `COMPLETED`

#### Scenario: UNIQUE race on (sessionId, studentId) routes to EXCEPTION

- GIVEN a `physical_capacity_assignments` row already exists for `(sessionId, studentId)`
- WHEN the INSERT for that same pair violates the V7 UNIQUE constraint
- THEN `DataIntegrityViolationException` is raised
- AND the handler routes through `MarkPurchaseExceptionPort`
- AND no second `physical_capacity_assignments` row is persisted

#### Scenario: Hold-expired / monthly-coverage-changed residual flips to EXCEPTION

- GIVEN a payment whose `targetReference` no longer resolves to a `SCHEDULED` session (e.g. hold expired or monthly coverage changed since the quote)
- WHEN the outbox handler runs
- THEN the `billing_purchases` row has `status = EXCEPTION`
- AND zero rows exist in `physical_capacity_assignments` for that payment
- AND `billing_payments.status_type` remains `COMPLETED`

#### Scenario: Concurrent last-spot race — exactly one ASSIGNED and one EXCEPTION

- GIVEN a session with `capacity = 1` and zero existing `physical_capacity_assignments` rows
- WHEN two outbox handlers race for distinct `payment_id`s targeting the same `sessionId`
- THEN exactly one `physical_capacity_assignments` row exists for that session
- AND the winning `billing_purchases` row is `status = ASSIGNED`
- AND the losing `billing_purchases` row is `status = EXCEPTION`

### Requirement: Absent outbox handler fails loud — no silent drop

Removing (or failing to register) the `PhysicalCapacityAssignmentOutboxEventHandler` Spring bean MUST make the reconciler throw `IllegalStateException`, never silently mark the event `COMPLETED`.

#### Scenario: Reconciler rejects an event with no registered handler

- GIVEN an `app_outbox` row with `event_type = billing.PhysicalPaymentCompleted` and no registered handler bean
- WHEN the reconciler picks that row
- THEN it throws `IllegalStateException("No handler registered for event type: billing.PhysicalPaymentCompleted")`
- AND the row stays `FAILED` with a future `next_retry_at`
- AND zero `billing_purchases` or `physical_capacity_assignments` rows are created from that event

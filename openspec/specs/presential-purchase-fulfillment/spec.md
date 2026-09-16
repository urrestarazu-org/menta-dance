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

### Requirement: Coverage period and eligible sessions are computed at confirmation, never at quote time

When `CreatePurchaseFromPaymentEventUseCase` handles a
`billing.PhysicalPaymentCompleted` event for a payment with **no** active
capacity hold, it MUST compute the purchase's eligible session set from the
event's `confirmedAt` timestamp, exactly as before. For a payment **with**
an active hold, the eligible session set MUST instead be exactly the
sessions that hold already reserved at checkout time — `CoveragePlanner`
MUST NOT be re-run against `confirmedAt` for a held purchase.

(Previously: always computed from `confirmedAt` via `CoveragePlanner`, with
no hold-aware branch, because no production write path created holds.)

#### Scenario: Monthly coverage counts forward from confirmation (no hold)

- GIVEN a MONTHLY quote with `scheduledSessionCount = 4` confirmed at time
  `T`, whose payment has no active hold
- WHEN the outbox handler computes coverage
- THEN the eligible sessions are the 4 nearest `SCHEDULED` sessions of the
  course at or after `T`

#### Scenario: Held purchase uses the hold's session set, not a recomputed one

- GIVEN a payment with an active hold batch covering sessions `{S1, S2,
  S3}`, confirmed at time `T`
- WHEN the outbox handler computes coverage
- THEN the eligible session set is exactly `{S1, S2, S3}`
- AND `CoveragePlanner` is not invoked with `confirmedAt` for this payment

### Requirement: Monthly coverage extends forward and is never truncated

The course calendar can change between quote creation and payment
confirmation. When fewer than `scheduledSessionCount` `SCHEDULED` sessions
remain in the immediate weeks after `confirmedAt`, coverage MUST keep
counting forward until the full count is reached, bounded by a lookahead
horizon. It MUST NOT deliver a smaller set.

The buyer paid the full monthly price with no proration
(`PhysicalCourseQuote.monthly()`), so full price owes a full count of
classes. Truncating would deliver fewer classes for the same money, and —
under the all-or-nothing rule below — would convert an ordinary calendar
edit into an `EXCEPTION` on an already-settled payment.

A shortfall that persists past the horizon is a genuine impossibility, not
a scheduling delay, and resolves through the existing residual path.

#### Scenario: Coverage skips a gap in the calendar

- GIVEN a MONTHLY quote with `scheduledSessionCount = 4` confirmed at `T`
- AND the course has only 2 `SCHEDULED` sessions in the following two weeks,
  then resumes afterwards
- WHEN the outbox handler computes coverage
- THEN the eligible set still contains exactly 4 sessions
- AND it includes sessions beyond the gap

#### Scenario: Not enough sessions exist within the horizon

- GIVEN a MONTHLY quote with `scheduledSessionCount = 4` confirmed at `T`
- AND fewer than 4 `SCHEDULED` sessions exist within the lookahead horizon
  after `T`
- WHEN the outbox handler computes coverage
- THEN no partial assignment is persisted
- AND the `Purchase` resolves to `EXCEPTION` with a not-schedulable code
- AND the `Payment` remains `COMPLETED`

### Requirement: Sessions are claimed in a stable, deterministic order

When assigning the N eligible sessions of one `Purchase`,
`AssignCapacityUseCase` MUST claim them in a stable order, identical across
retries of the same purchase and across concurrent handlers.

#### Scenario: Retry claims sessions in the same order

- GIVEN a purchase with eligible sessions `{S1, S2, S3}`
- WHEN the assignment is attempted twice (e.g. after a transient failure)
- THEN both attempts claim `S1`, `S2`, `S3` in the same relative order

### Requirement: Confirmation converts an existing hold instead of claiming from scratch

When the confirmed payment's correlation reference matches an active hold
batch, the outbox handler MUST convert that batch's hold rows into
`physical_capacity_assignments` rows for the same session set, atomically
consuming the hold rows as it inserts the assignment rows, rather than
running `AssignCapacityUseCase`'s fresh two-locking-read claim. Redelivery
of the same event MUST convert at most once.

#### Scenario: Held payment converts without a fresh capacity claim

- GIVEN a `COMPLETED` payment with an active hold batch for `{S1, S2}`
- WHEN the outbox handler processes its `billing.PhysicalPaymentCompleted`
  event
- THEN 2 `physical_capacity_assignments` rows are inserted, one per held
  session
- AND the `billing_purchases` row is `ASSIGNED`
- AND no fresh capacity claim is attempted for `S1` or `S2`

#### Scenario: Redelivery of a held, already-converted event is idempotent

- GIVEN a payment whose hold batch was already converted to assignments
- WHEN the reconciler redelivers the same
  `billing.PhysicalPaymentCompleted` event
- THEN no additional `physical_capacity_assignments` rows are inserted
- AND the `billing_purchases` row remains `ASSIGNED`

#### Scenario: A held session vanishes before confirmation — EXCEPTION, not partial

- GIVEN a held payment whose reserved set includes a session no longer
  `SCHEDULED` at confirmation
- WHEN the outbox handler attempts conversion
- THEN zero `physical_capacity_assignments` rows are persisted for that
  payment
- AND the `billing_purchases` row is `EXCEPTION`
- AND `billing_payments.status_type` remains `COMPLETED`

### Requirement: `api:physical` receives only neutral commands, never Payment or Purchase types

`AssignCapacityUseCase` and `PhysicalCapacityAssignmentPort` MUST accept
only `api:shared` neutral commands (session/student ids, payment id as an
opaque string) and MUST NOT reference any `com.menta.billing.*` type.

#### Scenario: ArchUnit forbids a billing dependency from physical assignment

- GIVEN `AssignCapacityUseCase` in `api:physical`
- WHEN ArchUnit rules run
- THEN no dependency on any `com.menta.billing.*` type is found

### Requirement: Check-in eligibility is keyed on assignment rows, not on Purchase status

`POST /api/v1/physical/sessions/{sessionId}/access-qr` MUST determine
eligibility solely from a `physical_capacity_assignments` row existing for
`(sessionId, studentId)`. It MUST NOT read `billing_purchases` directly or
indirectly. `Purchase.ASSIGNED` is a consequence of those rows existing, not
a gate check-in re-checks itself.

#### Scenario: Check-in succeeds from the assignment row alone

- GIVEN a `physical_capacity_assignments` row for `(S, U)` exists
- WHEN `U` requests `access-qr` for session `S`
- THEN the request succeeds regardless of any `billing_purchases` read

### Requirement: Outbox handler creates one Purchase per payment covering all its eligible sessions (idempotent)

A `billing.PhysicalPaymentCompleted` event MUST result in at most one
`billing_purchases` row per `payment_id`, covering every session in that
purchase's computed eligible-session set (one session for `INDIVIDUAL`, N
sessions for `MONTHLY`). Re-delivery with the same `payment_id` MUST surface
the existing row and its existing session set, never a second insert and
never additional sessions.

#### Scenario: First-time event creates a PENDING_FULFILLMENT purchase

- GIVEN an `app_outbox` row whose `paymentId` is `P1` and no
  `billing_purchases` row references `P1`
- WHEN the outbox handler dispatches the event
- THEN exactly one `billing_purchases` row exists for `P1` with
  `status = PENDING_FULFILLMENT`, covering its full eligible-session set

#### Scenario: Re-delivery with same payment_id is idempotent, yields the same session set

- GIVEN an existing `billing_purchases` row for `payment_id = P1` in any
  non-EXCEPTION status, already covering sessions `{S1, S2, S3}`
- WHEN the reconciler delivers another event bearing `payment_id = P1`
- THEN exactly one `billing_purchases` row still exists for `P1`, still
  covering `{S1, S2, S3}`
- AND zero new `physical_capacity_assignments` rows are inserted

### Requirement: A Purchase reaches ASSIGNED only when every eligible session is assigned

A `Purchase` MUST transition to `ASSIGNED` only when
`PhysicalCapacityAssignmentPort.assign(...)` succeeds for every session in
its eligible-session set, inserting one `physical_capacity_assignments` row
per session. `INDIVIDUAL` (N=1) is the degenerate case of this same rule.

#### Scenario: All N eligible sessions available — Purchase is ASSIGNED

- GIVEN a MONTHLY purchase with N eligible sessions each with capacity
  available
- WHEN the outbox handler completes assignment for all N sessions
- THEN N `physical_capacity_assignments` rows exist, one per eligible
  session
- AND the `billing_purchases` row has `status = ASSIGNED`
- AND `POST /access-qr` succeeds for every one of those sessions

#### Scenario: Individual purchase (N=1) unblocks QR as before

- GIVEN a session whose current `physical_capacity_assignments` count is
  below its `capacity`
- WHEN the outbox handler completes `assignCapacity` for the quote's single
  `(sessionId, studentId)`
- THEN one `physical_capacity_assignments` row exists for that pair
- AND the `billing_purchases` row has `status = ASSIGNED`
- AND `POST /access-qr` for that student returns 200

### Requirement: Residual EXCEPTION is all-or-nothing across every eligible session

When any one of the N eligible-session assignments fails — V7 UNIQUE
conflict, `CapacityBelowAssignedException`, or a target session no longer
`SCHEDULED` — the handler MUST abort the entire set for that payment: it
MUST insert ZERO `physical_capacity_assignments` rows (no partial subset
persisted), flip `billing_purchases` to `EXCEPTION`, leave
`billing_payments.status_type = COMPLETED` (ADR-0039), and NOT schedule a
retry. No automatic refund or notification is triggered by this state.

#### Scenario: One of N sessions fails — zero partial rows, Purchase is EXCEPTION

- GIVEN a MONTHLY purchase with 3 eligible sessions, two with available
  capacity and one already full
- WHEN the handler attempts to assign all 3
- THEN zero `physical_capacity_assignments` rows are persisted for that
  payment
- AND the `billing_purchases` row is `status = EXCEPTION`
- AND `billing_payments.status_type` remains `COMPLETED`

#### Scenario: Capacity invariant trips — Purchase flips to EXCEPTION

- GIVEN a session whose current `physical_capacity_assignments` count
  equals its `capacity`
- WHEN the handler runs `assignCapacity` for a fresh `(sessionId,
  studentId)` in that purchase's eligible set
- THEN `CapacityBelowAssignedException` is thrown
- AND the handler routes through `MarkPurchaseExceptionPort`
- AND the `billing_purchases` row is `status = EXCEPTION`

#### Scenario: UNIQUE race on (sessionId, studentId) routes to EXCEPTION

- GIVEN a `physical_capacity_assignments` row already exists for
  `(sessionId, studentId)`
- WHEN the INSERT for that same pair violates the V7 UNIQUE constraint
- THEN `DataIntegrityViolationException` is raised
- AND the handler routes through `MarkPurchaseExceptionPort`
- AND no second `physical_capacity_assignments` row is persisted

#### Scenario: Concurrent last-spot race — exactly one ASSIGNED and one EXCEPTION

- GIVEN a session with `capacity = 1` and zero existing
  `physical_capacity_assignments` rows
- WHEN two outbox handlers race for distinct `payment_id`s targeting the
  same `sessionId`
- THEN exactly one `physical_capacity_assignments` row exists for that
  session
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

# Delta for Presential Purchase Fulfillment

Base spec: `openspec/changes/fix-presential-purchase-quota-exception/specs/presential-purchase-fulfillment/spec.md`
(unpublished to `openspec/specs/` — treated here as current truth per proposal Risks).
Requirements "Physical payments publish one committed outbox event", "Payment
rollback writes no ghost event or purchase", and "Absent outbox handler fails
loud" are untouched by this change and are not repeated here.

## ADDED Requirements

### Requirement: Coverage period and eligible sessions are computed at confirmation, never at quote time

When `CreatePurchaseFromPaymentEventUseCase` handles a
`billing.PhysicalPaymentCompleted` event, it MUST compute the purchase's
eligible session set from the event's `confirmedAt` timestamp, never from
any value fixed at quote creation. For `MONTHLY`, the eligible sessions MUST
be the next `scheduledSessionCount` (snapshotted on the quote) `SCHEDULED`
sessions of the course, counted forward from `confirmedAt`. For
`INDIVIDUAL`, the eligible session MUST be exactly the quote's
`selectedSessionId`.

#### Scenario: Monthly coverage counts forward from confirmation

- GIVEN a MONTHLY quote with `scheduledSessionCount = 4` confirmed at time `T`
- WHEN the outbox handler computes coverage
- THEN the eligible sessions are the 4 nearest `SCHEDULED` sessions of the
  course at or after `T`
- AND none of them were fixed at quote-creation time

#### Scenario: Individual coverage is the single quoted session

- GIVEN an INDIVIDUAL quote whose `selectedSessionId` is `S`
- WHEN the outbox handler computes coverage
- THEN the eligible session set is exactly `{S}`

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

## MODIFIED Requirements

### Requirement: Outbox handler creates one Purchase per payment covering all its eligible sessions (idempotent)

A `billing.PhysicalPaymentCompleted` event MUST result in at most one
`billing_purchases` row per `payment_id`, covering every session in that
purchase's computed eligible-session set (one session for `INDIVIDUAL`, N
sessions for `MONTHLY`). Re-delivery with the same `payment_id` MUST surface
the existing row and its existing session set, never a second insert and
never additional sessions.

(Previously: assumed exactly one session per purchase, with no eligible-set
computation.)

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

(Previously: a single successful `assign(...)` call was sufficient, since
exactly one session existed per purchase.)

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

(Previously: described only a single-session failure directly causing
EXCEPTION, with no all-or-nothing requirement across multiple sessions.)

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

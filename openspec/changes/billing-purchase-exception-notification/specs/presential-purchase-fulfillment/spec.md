# Delta for Presential Purchase Fulfillment

## MODIFIED Requirements

### Requirement: Residual EXCEPTION is all-or-nothing across every eligible session

When any one of the N eligible-session assignments fails — V7 UNIQUE
conflict, `CapacityBelowAssignedException`, or a target session no longer
`SCHEDULED` — the handler MUST abort the entire set for that payment: it
MUST insert ZERO `physical_capacity_assignments` rows (no partial subset
persisted), flip `billing_purchases` to `EXCEPTION`, leave
`billing_payments.status_type = COMPLETED` (ADR-0039), and NOT schedule a
retry. No automatic refund is triggered by this state. Reaching
`EXCEPTION` through this path additionally emits a durable
`billing.PurchaseExceptioned` notification event, atomically with the
state transition — see the "Reaching EXCEPTION emits a durable
notification event" requirement below.

(Previously: "No automatic refund or notification is triggered by this
state" — notification is now triggered; refund remains untriggered.)

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

## ADDED Requirements

### Requirement: Reaching EXCEPTION emits a durable notification event

`MarkPurchaseExceptionUseCase` MUST append exactly one `app_outbox` row
with `event_type = billing.PurchaseExceptioned` in the same transaction
as `purchaseRepository.save(purchase.exception())`, for every real
`PENDING_FULFILLMENT → EXCEPTION` transition. The `EXCEPTION → EXCEPTION`
no-op MUST short-circuit before any append, so a redelivered triggering
event produces no duplicate row. A rolled-back transition MUST leave no
`billing.PurchaseExceptioned` row. The state-machine transition rules
themselves (which edges are legal) are unchanged by this requirement.

#### Scenario: Real EXCEPTION transition appends exactly one event

- GIVEN a `Purchase` in `PENDING_FULFILLMENT`
- WHEN `MarkPurchaseExceptionUseCase` transitions it to `EXCEPTION`
- THEN exactly one `app_outbox` row exists with
  `event_type = billing.PurchaseExceptioned` for that purchase
- AND it commits atomically with the `billing_purchases` status change

#### Scenario: Redelivered no-op transition appends no event

- GIVEN a `Purchase` already `EXCEPTION`
- WHEN `MarkPurchaseExceptionUseCase` is invoked again for the same
  purchase (redelivered triggering event)
- THEN no additional `app_outbox` row is appended
- AND the existing `billing_purchases` row is unchanged

#### Scenario: Rolled-back transition leaves no outbox row

- GIVEN a `MarkPurchaseExceptionUseCase` call whose transaction is rolled
  back after the state change but before commit
- WHEN the rollback completes
- THEN no `app_outbox` row with `event_type = billing.PurchaseExceptioned`
  exists for that purchase
- AND the `billing_purchases` row retains its pre-call status

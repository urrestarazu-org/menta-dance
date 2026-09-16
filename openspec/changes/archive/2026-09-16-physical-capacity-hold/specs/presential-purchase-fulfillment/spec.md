# Delta for Presential Purchase Fulfillment

## MODIFIED Requirements

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

## ADDED Requirements

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

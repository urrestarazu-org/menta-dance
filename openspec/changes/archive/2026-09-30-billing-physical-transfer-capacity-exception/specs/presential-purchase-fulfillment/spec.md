# Delta for Presential Purchase Fulfillment

## MODIFIED Requirements

### Requirement: Coverage period and eligible sessions are computed at confirmation, never at quote time

When `CreatePurchaseFromPaymentEventUseCase` handles a
`billing.PhysicalPaymentCompleted` event for a payment with **no** active
capacity hold, it MUST compute the purchase's eligible session set from the
event's `confirmedAt` timestamp. For a payment **with** an active hold, the
eligible session set MUST instead be exactly the sessions that hold already
reserved at checkout time — `CoveragePlanner` MUST NOT be re-run against
`confirmedAt` for a held purchase.

Every `BANK_TRANSFER` physical purchase follows the no-hold branch: under D1
(`bank-transfer-physical-purchase`), no hold is ever created for that rail, so
this is not a residual or fallback case for it — it is the permanent, intended
capacity path for every bank-transfer physical purchase, run by the same
already-shipped handler code with no new fulfillment logic.

(Previously: framed as a residual case for a payment that somehow reached
confirmation without a hold, alongside the hold-aware branch added by #208. It
is now the intended, first-class path for the entire `BANK_TRANSFER` rail, not
just a fallback.)

#### Scenario: Monthly coverage counts forward from confirmation (no hold)

- GIVEN a MONTHLY quote with `scheduledSessionCount = 4` confirmed at time `T`,
  whose payment has no active hold
- WHEN the outbox handler computes coverage
- THEN the eligible sessions are the 4 nearest `SCHEDULED` sessions of the
  course at or after `T`

#### Scenario: Held purchase uses the hold's session set, not a recomputed one

- GIVEN a payment with an active hold batch covering sessions `{S1, S2, S3}`,
  confirmed at time `T`
- WHEN the outbox handler computes coverage
- THEN the eligible session set is exactly `{S1, S2, S3}`
- AND `CoveragePlanner` is not invoked with `confirmedAt` for this payment

#### Scenario: Approved bank-transfer purchase with available capacity reaches ASSIGNED

- GIVEN a `BANK_TRANSFER` physical `Payment`, approved by an admin, whose
  eligible sessions computed at `confirmedAt` all have capacity available
- WHEN the outbox handler processes its `billing.PhysicalPaymentCompleted`
  event
- THEN one `physical_capacity_assignments` row is inserted per eligible
  session
- AND the `billing_purchases` row is `ASSIGNED`
- AND no new fulfillment code runs beyond the existing no-hold handler path

#### Scenario: Approved bank-transfer purchase with unavailable capacity reaches EXCEPTION

- GIVEN a `BANK_TRANSFER` physical `Payment`, approved by an admin, whose
  eligible sessions computed at `confirmedAt` include one that is no longer
  available
- WHEN the outbox handler processes its `billing.PhysicalPaymentCompleted`
  event
- THEN zero `physical_capacity_assignments` rows are persisted for that
  payment
- AND the `billing_purchases` row is `EXCEPTION`
- AND `billing_payments.status_type` remains `COMPLETED`

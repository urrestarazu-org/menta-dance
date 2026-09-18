# Delta for Billing Subscriptions

## ADDED Requirements

### Requirement: Checkout accepts BANK_TRANSFER

`POST /api/v1/billing/subscriptions` MUST accept `paymentMethod: BANK_TRANSFER` for an
authenticated student with no current `ACTIVE` or `PENDING` subscription, routing it to the
bank-transfer creation path (see the `bank-transfer-subscription` capability) instead of
Mercado Pago Checkout Pro. On success it MUST return `201` with a new `Subscription` in
`PENDING` and its `Payment` in `AwaitingManualVerification`.

#### Scenario: BANK_TRANSFER subscription request succeeds

- GIVEN an authenticated student with no current `ACTIVE` or `PENDING` subscription
- WHEN they call `POST /api/v1/billing/subscriptions` with `paymentMethod: BANK_TRANSFER` for
  an available plan
- THEN the response is `201`
- AND a `Subscription` is created in `PENDING` with a `Payment` in `AwaitingManualVerification`

#### Scenario: Existing active or pending subscription blocks the request

- GIVEN a student with an `ACTIVE` or `PENDING` subscription
- WHEN they call `POST /api/v1/billing/subscriptions` with `paymentMethod: BANK_TRANSFER`
- THEN the system rejects the request exactly as it does today for `MERCADO_PAGO` in the same
  situation
- AND no new `Subscription` or `Payment` row is created

### Requirement: Mercado Pago checkout is unaffected (regression)

This change MUST NOT alter the status codes, response shape, or side effects of
`POST /api/v1/billing/subscriptions` when `paymentMethod: MERCADO_PAGO`.

#### Scenario: Mercado Pago checkout is byte-identical

- GIVEN a student eligible to check out
- WHEN they call `POST /api/v1/billing/subscriptions` with `paymentMethod: MERCADO_PAGO`
- THEN the response, `Subscription`, and `Payment` state are identical to this change's absence

### Requirement: System-initiated cancellation of a PENDING subscription

The system MUST be able to cancel a `Subscription` whose status is `PENDING`, as a transition
distinct from the existing `ACTIVE`-only cancellation. This transition MUST be usable by a
system-initiated actor and MUST result in `status = CANCELLED` for a subscription that never
granted access.

(This is a characterization requirement, not new domain work: `Subscription.cancelled()` already
performs an idempotent `PENDING|ACTIVE → CANCELLED` transition guarded by
`SubscriptionStatus.occupiesUserSlot()`, and `PaymentVerificationService.releaseFulfillment`
already calls it for this exact situation. `cancel(UUID, String, Instant)` is a separate,
`ACTIVE`-only, user/admin-initiated method and is intentionally left untouched.)

#### Scenario: A PENDING subscription is cancelled

- GIVEN a `Subscription` in `PENDING`
- WHEN the system cancels it
- THEN its status becomes `CANCELLED`
- AND no access was ever granted for that subscription

#### Scenario: Existing ACTIVE-only cancellation entry points are unchanged

- GIVEN a `PENDING` subscription
- WHEN a student calls `DELETE /api/v1/billing/subscriptions/me`, or an admin calls the admin
  cancellation endpoint, for that subscription
- THEN both continue to operate only on `ACTIVE` subscriptions, exactly as before this change

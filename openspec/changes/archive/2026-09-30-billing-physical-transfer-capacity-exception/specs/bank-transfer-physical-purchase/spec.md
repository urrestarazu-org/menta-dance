# Bank Transfer Physical Purchase Specification

## Purpose

Let a student without a card buy a presential course by bank transfer, through the
existing `POST /api/v1/billing/physical/purchases` endpoint routed by
`paymentMethod`. Creation reserves no capacity: unlike the Mercado Pago rail, no
`physical_capacity_hold` is ever created, and the capacity outcome (`ASSIGNED` or
`EXCEPTION`) is decided at approval time by the already-shipped no-hold branch of
`presential-purchase-fulfillment`. Proof upload, admin review, audit log, and buyer
emails reuse `billing-manual-payment-verification` unchanged.

## Requirements

### Requirement: Creating a bank-transfer physical purchase returns transfer instructions

A successful `BANK_TRANSFER` request to `POST /api/v1/billing/physical/purchases`
for a valid, unexpired, non-full `quoteId` MUST return `201` with the bank details
needed to complete the transfer: CBU, alias, account holder name, CUIT, the exact
amount, and a payment reference tying the transfer to the created `Payment`. The
request MUST create exactly one `Payment` in `AwaitingManualVerification`,
targeting that quote via `PaymentTarget.Physical`.

#### Scenario: Response carries usable bank details and creates one Payment

- GIVEN a valid, unexpired, non-full `PhysicalCourseQuote`
- WHEN a `BANK_TRANSFER` checkout request is made for it
- THEN the response is `201` with CBU, alias, holder, CUIT, amount, and a payment
  reference
- AND exactly one `Payment` exists in `AwaitingManualVerification`, targeting that
  quote

### Requirement: Creation reserves no capacity and calls no provider

Creating a `BANK_TRANSFER` physical purchase MUST NOT insert any
`physical_capacity_holds` row and MUST NOT call any payment provider. The
authoritative capacity decision happens only at approval, per
`presential-purchase-fulfillment`.

#### Scenario: No hold row and no provider call

- GIVEN a successful `BANK_TRANSFER` physical checkout request
- WHEN the request completes
- THEN zero `physical_capacity_holds` rows exist for that quote
- AND no payment-provider call was made

### Requirement: Routing preserves the existing Mercado Pago checkout unchanged

`RoutingCreatePhysicalPurchaseCheckoutUseCase` MUST dispatch a `MERCADO_PAGO`
request to the existing `CreatePhysicalPurchaseCheckoutUseCaseImpl`
byte-identically to today, including its hold creation and its guaranteed `409`.

#### Scenario: Mercado Pago checkout behaves exactly as before

- GIVEN a `MERCADO_PAGO` checkout request through the router
- WHEN it is processed
- THEN the response, the created `Payment`, and the created capacity hold match
  today's pre-router behavior exactly

### Requirement: An expired quote is rejected with 410 on the bank-transfer path

A `quoteId` whose `expiresAt` has passed MUST be rejected with `410 Gone`, exactly
as on the Mercado Pago path, and MUST NOT create a `Payment`.

#### Scenario: Expired quote is rejected, no Payment created

- GIVEN a `PhysicalCourseQuote` whose `expiresAt` is in the past
- WHEN it is used in a `BANK_TRANSFER` checkout request
- THEN the response is `410`
- AND no `Payment` row is created

### Requirement: Creation shares the daily bank-transfer creation budget

Consuming `BankTransferRateLimitPort.consumeBankTransferCreation(userId)` MUST
happen before any other creation step. It MUST count against the same per-user
daily budget as bank-transfer subscription creation (D4 — see
`bank-transfer-subscription`), not a separate physical-only counter.

#### Scenario: Prior subscription creations count toward the physical budget

- GIVEN a user who already made 10 bank-transfer creations today, whether
  subscriptions or physical purchases
- WHEN they attempt a `BANK_TRANSFER` physical purchase creation
- THEN the response is `429 application/problem+json`
- AND no `Payment` is created

### Requirement: A rejected bank-transfer physical payment releases nothing

Admin rejection of a bank-transfer physical `Payment` (mechanics defined by
`billing-manual-payment-verification`) MUST leave it `Rejected` with no `Purchase`
created or cancelled. `PaymentFulfillmentService.release(Payment)` MUST remain a
no-op for `PaymentTarget.Physical`, because nothing was ever reserved for it (D5).

#### Scenario: Rejection releases nothing because nothing was held

- GIVEN a `BANK_TRANSFER` physical `Payment` `AwaitingManualVerification` with an
  invalid proof
- WHEN an admin rejects it
- THEN the `Payment` becomes `Rejected`
- AND no `Purchase` is created or cancelled
- AND `PaymentFulfillmentService.release` performs no capacity action for it

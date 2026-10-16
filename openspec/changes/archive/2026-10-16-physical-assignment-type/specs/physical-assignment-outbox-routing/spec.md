# Physical Assignment Outbox Routing Specification (delta)

## Purpose

Behaviour of `PhysicalCapacityAssignmentOutboxEventHandler` (api:app) when
resolving `billing.PhysicalPaymentCompleted`, now that the quote determines
the assignment type before conversion.

## ADDED Requirements

### Requirement: The quote is resolved before conversion and drives the type

After resolving the Physical payment target, the handler MUST load the
`PhysicalCourseQuote` exactly once, before calling `convertAll`, and MUST
pass `AssignmentType` mapped from `quote.purchaseType` to `convertAll`. The
legacy `HoldNotFound` path MUST reuse that same loaded quote and MUST NOT
load it again.

#### Scenario: Held MONTHLY quote converts as MONTHLY

- GIVEN a completed payment with an active hold and a `MONTHLY` quote
- WHEN the event is handled
- THEN the quote is loaded before `convertAll`
- AND `convertAll` is called with `AssignmentType.MONTHLY`

#### Scenario: Held INDIVIDUAL quote converts as INDIVIDUAL

- GIVEN a completed payment with an active hold and an `INDIVIDUAL` quote
- WHEN the event is handled
- THEN `convertAll` is called with `AssignmentType.INDIVIDUAL`

#### Scenario: HoldNotFound reuses the loaded quote

- GIVEN a completed payment with no hold and an existing quote
- WHEN the event is handled
- THEN `convertAll` returns `HoldNotFound`
- AND the legacy planner and `assignAll` run using the already-loaded quote
- AND the quote repository was queried exactly once

### Requirement: A missing quote before conversion routes to QUOTE_NOT_FOUND

If no quote exists for the Physical target's `quoteId` before `convertAll`,
the handler MUST NOT call `convertAll` and MUST first call
`createPurchaseFromPaymentEvent(payload, List.of())`, building the purchase
directly at `EXCEPTION` when none exists. It MUST then publish a
payment-fulfillment failure with `Reason.QUOTE_NOT_FOUND` (not
`TARGET_NOT_SCHEDULED`) if and only if the resulting purchase is `EXCEPTION`,
whether just created or pre-existing. Held rows MUST be left unconverted and
expire through TTL. This applies equally when no hold exists.

#### Scenario: Quote missing, hold present

- GIVEN a completed payment with an active hold and no quote for its target
- WHEN the event is handled
- THEN `convertAll` is not called
- AND the purchase is created at `EXCEPTION` with an empty session list
- AND exactly one failure is published with reason `QUOTE_NOT_FOUND`, after
  the purchase was created
- AND the hold rows are neither converted nor released

#### Scenario: Quote missing, no hold

- GIVEN a completed payment with no hold and no quote
- WHEN the event is handled
- THEN the outcome is identical to the previous scenario, with reason
  `QUOTE_NOT_FOUND`

#### Scenario: Redelivery after a crash between create and publish

- GIVEN a purchase already `EXCEPTION` for a missing quote whose failure
  event was never published
- WHEN the event is delivered again
- THEN the purchase remains `EXCEPTION`
- AND the `QUOTE_NOT_FOUND` failure is published (a repeat is dropped by the
  existing duplicate-notification index)

#### Scenario: Coverage shortfall keeps its reason

- GIVEN a completed payment with no hold, an existing quote, and a coverage
  shortfall beyond the lookahead
- WHEN the event is handled
- THEN the failure reason is `TARGET_NOT_SCHEDULED`, unchanged

### Requirement: Redelivery stays idempotent

A redelivered event whose hold was already converted MUST be a no-op
(`AlreadyConverted`): no purchase transition, no assignment change. If such
a redelivery finds the quote missing, the handler MUST follow the
missing-quote route: because `createPurchaseFromPaymentEvent` returns an
existing non-`EXCEPTION` purchase untouched and the failure is published only
when the resulting purchase is `EXCEPTION`, the purchase MUST remain
`ASSIGNED`, NO `PaymentFulfillmentFailed` event and NO ops mail MUST be
emitted, no assignment row MUST change, and the handler MUST NOT throw.

#### Scenario: Redelivery of a converted hold with the quote present

- GIVEN a hold already converted and its purchase `ASSIGNED`
- WHEN the event is delivered again
- THEN `convertAll` returns `AlreadyConverted`
- AND nothing is written and no failure is published

#### Scenario: Redelivery of a converted hold with the quote missing

- GIVEN a hold already converted, its purchase `ASSIGNED`, and the quote
  now absent
- WHEN the event is delivered again
- THEN the purchase remains `ASSIGNED`
- AND no `PaymentFulfillmentFailed` event is published and no ops mail is sent
- AND no assignment row is inserted, modified, or deleted
- AND the handler completes without throwing, so the outbox row is not
  retried

### Requirement: Billing defines and documents QUOTE_NOT_FOUND

`billing`'s `Reason` enum MUST contain `QUOTE_NOT_FOUND` with javadoc
distinguishing it from `TARGET_NOT_SCHEDULED`. `docs/06-BILLING-API.md` MUST
list it among the failure reasons. The ops exception mail has no per-reason
mapping and prints the raw reason code, so it MUST render `QUOTE_NOT_FOUND`
like every other reason, and its coverage test MUST iterate all `Reason`
values.

#### Scenario: Reason exists and is documented

- GIVEN the billing domain and `docs/06-BILLING-API.md`
- WHEN the `Reason` values and the doc's reason list are compared
- THEN `QUOTE_NOT_FOUND` appears in both

#### Scenario: Exception mail handles the new reason

- GIVEN a fulfillment-failed event with reason `QUOTE_NOT_FOUND`
- WHEN the exception mail is composed
- THEN a mail is produced whose body contains the raw code `QUOTE_NOT_FOUND`,
  as for the other reasons
- AND the coverage test asserts the raw code appears for every `Reason` value

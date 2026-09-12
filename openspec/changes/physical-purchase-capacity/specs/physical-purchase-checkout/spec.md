# Physical Purchase Checkout Specification

## Purpose

Public entry point that turns a valid, unexpired `PhysicalCourseQuote` into a
`Payment` in `PENDING` state (`PaymentTarget.Physical`) bound to the
authenticated buyer, returning provider checkout data. This endpoint touches
no capacity: assignment happens only at confirmation, per
`presential-purchase-fulfillment`.

## Requirements

### Requirement: Checkout resolves the buyer from the authenticated principal

`POST /api/v1/billing/physical/purchases` MUST resolve the purchasing user id
from the authenticated principal, never from the request body. Any
user-identifying field present in the body MUST be ignored.

#### Scenario: Authenticated request buys for the token's user

- GIVEN an authenticated request with `quoteId`, `paymentMethod`, and an
  idempotency key
- WHEN the endpoint is called
- THEN the created `Payment` records the user id from the authenticated
  principal, not from any request field

### Requirement: Checkout requires quoteId, paymentMethod, and an idempotency key

The endpoint MUST require all three. A request missing any of them MUST be
rejected before any `Payment` is created.

#### Scenario: Missing idempotency key is rejected

- GIVEN a request with `quoteId` and `paymentMethod` but no idempotency key
- WHEN the endpoint is called
- THEN the response is a client error and no `Payment` row is created

### Requirement: Same idempotency key replays the same checkout

A repeated request from the same user with the same idempotency key MUST
return the same checkout response and MUST NOT create a second `Payment`.

#### Scenario: Retried request returns identical checkout data

- GIVEN a `Payment` already created for user `U` with idempotency key `K`
- WHEN `U` retries the endpoint with the same `K`
- THEN the response matches the original checkout data
- AND exactly one `Payment` row exists for that `(U, K)` pair

### Requirement: An expired quote is rejected with 410

A `quoteId` whose 1-hour `expiresAt` has passed at request time MUST be
rejected with `410 Gone` and an RFC 9457 ProblemDetail carrying a
quote-expired code, and MUST NOT create a `Payment`.

`410` follows this project's established precedent for an expired
time-limited artifact whose remedy is to obtain a fresh one:
`PasswordResetTokenExpiredException` maps to `HttpStatus.GONE` in
`PasswordResetExceptionHandler`. It also keeps this failure distinguishable
from the visibly-full rejection below, which owns `409` on the same
endpoint — two different failures on one route must not share a status.

#### Scenario: Quote past its validity window is rejected

- GIVEN a `PhysicalCourseQuote` whose `expiresAt` is in the past
- WHEN it is used as `quoteId` in a checkout request
- THEN the response is `410` with an RFC 9457 ProblemDetail carrying a
  quote-expired code
- AND no `Payment` row is created

#### Scenario: Expired and full are distinguishable

- GIVEN a `PhysicalCourseQuote` that is both past `expiresAt` and whose
  eligible sessions read full
- WHEN it is used as `quoteId` in a checkout request
- THEN the response is `410`, not `409` — validity is checked before
  availability, so the caller is told the actionable thing first (request a
  new quote) rather than a capacity reading taken from a quote that no
  longer applies

### Requirement: A visibly-full quote is rejected with 409 (best effort, no guarantee)

If, at request time, every session eligible under the quote already reads
zero available spots, the endpoint MUST respond `409` with an RFC 9457
ProblemDetail and MUST NOT create a `Payment`. This is a read-time check
only: it MUST NOT reserve or otherwise guarantee capacity survives past the
response. A request that passes it MAY still resolve to `EXCEPTION` at
confirmation (see `presential-purchase-fulfillment`) if another buyer takes
the last spot first. This is a distinct, weaker guarantee than the
hold-backed `409 CAPACITY_UNAVAILABLE` of a future capacity-hold capability.

#### Scenario: Full quote is rejected before charging

- GIVEN a quote whose eligible sessions are all read as full at request time
- WHEN the checkout endpoint is called
- THEN the response is `409` with an RFC 9457 ProblemDetail
- AND no `Payment` row is created

#### Scenario: Passing the check is not a capacity guarantee

- GIVEN a checkout request that passed the full-quote check and created a
  `Payment`
- WHEN another buyer takes the last eligible spot before this payment is
  confirmed
- THEN this outcome is not prevented by the check above
- AND the purchase MAY still resolve to `EXCEPTION` at confirmation

### Requirement: Checkout creates no capacity assignment

Creating a `Payment` via this endpoint MUST NOT insert any
`physical_capacity_assignments` row and MUST NOT create any capacity hold.

#### Scenario: Pending checkout leaves capacity untouched

- GIVEN a successful checkout response with `Payment` status `PENDING`
- WHEN the eligible sessions' assignment counts are checked immediately
  after
- THEN they are unchanged from before the request

### Requirement: Checkout never references `api:physical`

`CreatePhysicalPurchaseCheckoutUseCase` MUST depend only on `billing`-owned
models (`PhysicalCourseQuote`, `Payment`) and MUST NOT reference any
`com.menta.physical.*` type, port, or entity.

#### Scenario: ArchUnit forbids a physical dependency from checkout

- GIVEN the checkout use case in `api:billing`
- WHEN ArchUnit rules run
- THEN no dependency on any `com.menta.physical.*` type is found

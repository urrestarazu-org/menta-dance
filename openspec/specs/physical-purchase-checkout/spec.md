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

### Requirement: A visibly-full quote is rejected with 409 (real guarantee for MERCADO_PAGO, best effort for BANK_TRANSFER)

For `MERCADO_PAGO`, if the ordered set of eligible sessions under the quote cannot
be fully reserved by an atomic capacity hold, the endpoint MUST respond `409` with
an RFC 9457 ProblemDetail (`CAPACITY_UNAVAILABLE`) and MUST NOT create a
`Payment`. This is a real guarantee: a request that passes it has an active hold
covering every eligible session, so no other buyer can take that spot before
confirmation.

For `BANK_TRANSFER`, the same best-effort `CoveragePlanner` availability read runs
at creation and a visibly-full quote is still rejected with `409`, but this check
creates no hold, reserves nothing, and guarantees nothing: the authoritative
capacity decision happens only at approval (see `bank-transfer-physical-purchase`,
D7). A request that passes this check MAY still resolve to `EXCEPTION` at approval
if capacity was taken in the meantime.

(Previously: read as a hold-backed guarantee covering the whole endpoint,
regardless of `paymentMethod`. It is now scoped to `MERCADO_PAGO` only;
`BANK_TRANSFER` keeps the weaker, explicitly non-binding pre-#208 behavior.)

#### Scenario: Full quote is rejected before charging (MERCADO_PAGO)

- GIVEN a `MERCADO_PAGO` quote whose eligible sessions cannot all be held
- WHEN the checkout endpoint is called
- THEN the response is `409` with an RFC 9457 ProblemDetail
  (`CAPACITY_UNAVAILABLE`)
- AND no `Payment` row is created
- AND no capacity hold row is created

#### Scenario: Passing the check is now a real guarantee (MERCADO_PAGO)

- GIVEN a checkout request whose atomic hold succeeded and created a `Payment`
- WHEN another buyer attempts to take the same eligible spot before this payment
  is confirmed
- THEN the other buyer's attempt fails against the active hold
- AND this purchase's confirmation is no longer exposed to that race

#### Scenario: A visibly-full quote is rejected but the rejection is not binding (BANK_TRANSFER)

- GIVEN a `BANK_TRANSFER` quote whose eligible sessions read full at creation time
- WHEN the checkout endpoint is called
- THEN the response is `409` with `CAPACITY_UNAVAILABLE`
- AND no `Payment` row is created
- AND no capacity hold row is created — this was a read, not a reservation

### Requirement: Checkout creates no capacity assignment; a hold only for MERCADO_PAGO

Creating a `Payment` via this endpoint MUST NOT insert any
`physical_capacity_assignments` row, for either `paymentMethod`. For
`MERCADO_PAGO`, it MUST create a capacity hold covering every eligible session
before the `Payment` row is persisted. For `BANK_TRANSFER`, it MUST create
neither an assignment nor a hold, ever (D1) — capacity is computed fresh at
approval by `presential-purchase-fulfillment`'s no-hold path.

(Previously: method-agnostic — the endpoint always created a hold and never an
assignment. It is now conditional on `paymentMethod`: the assignment prohibition
is unchanged for both rails; the hold is created only for `MERCADO_PAGO`.)

#### Scenario: Pending MERCADO_PAGO checkout leaves assignments untouched but holds the spots

- GIVEN a successful `MERCADO_PAGO` checkout response with `Payment` status
  `PENDING`
- WHEN the eligible sessions' assignment counts are checked immediately after
- THEN they are unchanged from before the request
- AND an active hold row exists for each eligible session, correlated to this
  `Payment`

#### Scenario: Pending BANK_TRANSFER checkout leaves assignments and holds both untouched

- GIVEN a successful `BANK_TRANSFER` checkout response with `Payment` status
  `AwaitingManualVerification`
- WHEN the eligible sessions' assignment and hold counts are checked immediately
  after
- THEN both are unchanged from before the request

### Requirement: Checkout never references `api:physical`

`CreatePhysicalPurchaseCheckoutUseCase` MUST depend only on `billing`-owned
models (`PhysicalCourseQuote`, `Payment`) and MUST NOT reference any
`com.menta.physical.*` type, port, or entity.

#### Scenario: ArchUnit forbids a physical dependency from checkout

- GIVEN the checkout use case in `api:billing`
- WHEN ArchUnit rules run
- THEN no dependency on any `com.menta.physical.*` type is found

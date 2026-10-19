# Delta for Physical Purchase Checkout

## MODIFIED Requirements

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

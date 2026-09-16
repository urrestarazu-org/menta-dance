# Delta for Physical Purchase Checkout

## MODIFIED Requirements

### Requirement: A visibly-full quote is rejected with 409 (best effort, no guarantee)

If the ordered set of eligible sessions under the quote cannot be fully
reserved by an atomic capacity hold, the endpoint MUST respond `409` with
an RFC 9457 ProblemDetail (`CAPACITY_UNAVAILABLE`, same code and shape as
before) and MUST NOT create a `Payment`. This is now a real guarantee: a
request that passes it has an active hold covering every eligible session,
so no other buyer can take that spot before confirmation.

(Previously: read-time-only check with no reservation; a passing request
could still resolve to `EXCEPTION` if another buyer won the race. That race
is now closed by the hold created at checkout.)

#### Scenario: Full quote is rejected before charging

- GIVEN a quote whose eligible sessions cannot all be held
- WHEN the checkout endpoint is called
- THEN the response is `409` with an RFC 9457 ProblemDetail
  (`CAPACITY_UNAVAILABLE`)
- AND no `Payment` row is created
- AND no capacity hold row is created

#### Scenario: Passing the check is now a real guarantee

- GIVEN a checkout request whose atomic hold succeeded and created a
  `Payment`
- WHEN another buyer attempts to take the same eligible spot before this
  payment is confirmed
- THEN the other buyer's attempt fails against the active hold
- AND this purchase's confirmation is no longer exposed to that race

### Requirement: Checkout creates no capacity assignment

Creating a `Payment` via this endpoint MUST NOT insert any
`physical_capacity_assignments` row. It MUST create a capacity hold
covering every eligible session before the `Payment` row is persisted.

(Previously: checkout MUST NOT insert an assignment row AND MUST NOT create
any capacity hold. The assignment half is unchanged; the hold half is
inverted — checkout now creates the hold that assignment still defers.)

#### Scenario: Pending checkout leaves assignments untouched but holds the spots

- GIVEN a successful checkout response with `Payment` status `PENDING`
- WHEN the eligible sessions' assignment counts are checked immediately
  after
- THEN they are unchanged from before the request
- AND an active hold row exists for each eligible session, correlated to
  this `Payment`

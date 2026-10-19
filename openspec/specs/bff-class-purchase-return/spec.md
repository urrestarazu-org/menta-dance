# BFF Class Purchase Return Specification

## Purpose

Make the class purchase return page reflect the Purchase outcome, not only the
payment status. A paid purchase that ends in `EXCEPTION` MUST no longer read as
"assigning your places" forever. State comes only from the Billing API
payment status and its `fulfillment` field (#559, builds on #552).

## Requirements

### Requirement: Return page branches on payment status and fulfillment

For a `COMPLETED` class payment the BFF return page MUST choose its notice from
the `fulfillment` value of `GET /api/v1/billing/payments/{paymentId}`. The BFF
MUST read `fulfillment` as a nullable value and MUST treat any value it does
not recognize as `null`.

#### Scenario: Pending fulfillment keeps today's copy

- GIVEN a `COMPLETED` payment with `fulfillment: "PENDING_FULFILLMENT"`
- WHEN the student opens the return page
- THEN the notice is the existing "Estamos asignando tus lugares" rendering

#### Scenario: Null fulfillment renders as today

- GIVEN a `COMPLETED` payment with `fulfillment: null`
- WHEN the student opens the return page
- THEN the page renders exactly as it did before this change

#### Scenario: Unknown fulfillment renders as today

- GIVEN a `COMPLETED` payment with a `fulfillment` value the BFF does not know
- WHEN the student opens the return page
- THEN the page renders as for `null` and no error is shown

### Requirement: EXCEPTION shows the staff-review message

When the payment is `COMPLETED` and `fulfillment` is `EXCEPTION`, the page MUST
show the text "Recibimos tu pago, pero no pudimos asignarte los lugares. El
equipo lo está revisando y te va a contactar." The page MUST NOT show the
"¡Pago confirmado! Estamos asignando tus lugares." text, MUST NOT list assigned
sessions, and MUST NOT render a contact or support link.

#### Scenario: Exception copy

- GIVEN a `COMPLETED` payment with `fulfillment: "EXCEPTION"`
- WHEN the student opens the return page
- THEN the page contains the exception text above
- AND does not contain "Estamos asignando tus lugares"
- AND contains no contact or support link

### Requirement: RESOLVED shows the resolved message

When the payment is `COMPLETED` and `fulfillment` is `RESOLVED`, the page MUST
show the text "Tu compra ya fue resuelta por el equipo." The page MUST NOT
list assigned sessions, MUST NOT render a contact or support link, and MUST NOT
state refund or compensation wording nor the resolution type or note.

#### Scenario: Resolved copy

- GIVEN a `COMPLETED` payment with `fulfillment: "RESOLVED"`
- WHEN the student opens the return page
- THEN the page contains "Tu compra ya fue resuelta por el equipo."
- AND contains no contact or support link

### Requirement: Assigned sessions are listed only when meaningful

The BFF MUST call the assigned-sessions use case only when `fulfillment` is
`ASSIGNED`, `PENDING_FULFILLMENT` or `null`, and MUST list those sessions as
in #552. It MUST NOT call it when `fulfillment` is `EXCEPTION` or `RESOLVED`,
because the student's sessions endpoint returns all of their sessions, not only
those of this purchase.

#### Scenario: Assigned lists sessions

- GIVEN a `COMPLETED` payment with `fulfillment: "ASSIGNED"`
- WHEN the student opens the return page
- THEN the assigned-sessions use case is called and its sessions are listed

#### Scenario: Exception and resolved do not fetch sessions

- GIVEN a `COMPLETED` payment with `fulfillment` `EXCEPTION` or `RESOLVED`
- WHEN the student opens the return page
- THEN the assigned-sessions use case is not called
- AND no session list is rendered

### Requirement: Non-completed payments are unaffected

The rendering for payment statuses other than `COMPLETED` (pending, failed,
and the like) MUST NOT depend on `fulfillment` and MUST remain as before.

#### Scenario: Pending payment ignores fulfillment

- GIVEN a payment whose status is not `COMPLETED`
- WHEN the student opens the return page
- THEN the notice is the same as before this change regardless of `fulfillment`

### Requirement: Failure to read fulfillment degrades gracefully

The BFF payment-status adapter MUST carry `fulfillment` through the payment
progress it returns. If the field is absent from the upstream response or
cannot be mapped, the BFF MUST treat it as `null` and render today's page; it
MUST NOT show an error because of it. An upstream failure to read the payment
status as a whole keeps its existing handling.

#### Scenario: Field absent upstream

- GIVEN an upstream payment response with no `fulfillment` property
- WHEN the student opens the return page
- THEN the page renders as for `null` fulfillment with no error

#### Scenario: Adapter maps the field

- GIVEN an upstream response with `status: "COMPLETED"` and
  `fulfillment: "EXCEPTION"`
- WHEN the adapter reads the payment
- THEN the returned progress carries status `COMPLETED` and fulfillment
  `EXCEPTION`

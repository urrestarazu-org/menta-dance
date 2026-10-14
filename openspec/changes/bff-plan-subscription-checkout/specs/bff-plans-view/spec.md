# Delta for BFF Plans View

## MODIFIED Requirements

### Requirement: Anonymous-reachable plans route

The BFF MUST expose `GET /plans` without requiring authentication, rendering
every plan returned by the billing plans call. The anonymous page MUST keep
the existing shared-cache behavior; a response personalized with the
caller's subscription state MUST be sent with `Cache-Control: no-store`.

(Previously: reachable anonymously, no caching distinction.)

#### Scenario: Anonymous visitor opens the plans page

- GIVEN a visitor with no session
- WHEN they request `GET /plans`
- THEN the BFF serves the plans page directly with no redirect to `/login`
- AND the response keeps its shared-cache headers (not `no-store`)

#### Scenario: Personalized page is not cacheable

- GIVEN an authenticated STUDENT
- WHEN they request `GET /plans`
- THEN the response carries `Cache-Control: no-store`

### Requirement: Upstream failures degrade to the shared error view

A `404`, `429`, or `503` from the billing plans call MUST render the BFF's
existing shared error view, with no leaked problem-detail body and no
plans-specific error copy (D6). A failure of the current-subscription call
for an authenticated STUDENT is covered by the fail-closed requirement
below and MUST also render the shared error view.

#### Scenario: Billing downtime renders the shared error view

- GIVEN the billing plans call returns `404`, `429`, or `503`
- WHEN a visitor requests `/plans`
- THEN the BFF renders the existing shared error view
- AND the response contains no upstream problem-detail body and no
  plans-specific error message

## ADDED Requirements

### Requirement: Subscribe button depends on role

Each plan MUST render a "Suscribirme" action according to the caller:
anonymous visitors MUST see it and, when used, be sent to login and return
to `/plans` afterwards; a `STUDENT` MUST see it (subject to the subscription
state rules); any other authenticated role MUST see plans with no button.

#### Scenario: Anonymous sees button and returns after login

- GIVEN a visitor with no session
- WHEN they request `/plans`
- THEN each plan shows "Suscribirme"
- AND following it leads to `/login`, and after a successful login the user
  lands back on `/plans`

#### Scenario: Student sees the button

- GIVEN a STUDENT with no blocking subscription state
- WHEN they request `/plans`
- THEN each plan shows a "Suscribirme" form with a CSRF token and a
  render-time idempotency key

#### Scenario: Other roles see no button

- GIVEN an authenticated user with a role other than `STUDENT`
- WHEN they request `/plans`
- THEN plans render with no "Suscribirme" control

### Requirement: Current subscription is consulted only with a token

The BFF MUST call `GET /api/v1/billing/subscriptions/me` only when the
request carries an access token (authenticated STUDENT). Anonymous requests
MUST NOT trigger it.

#### Scenario: Anonymous page makes no subscription call

- GIVEN a visitor with no session
- WHEN they request `/plans`
- THEN no request to `/api/v1/billing/subscriptions/me` is made

### Requirement: Subscription state drives button and notice for a student

For an authenticated STUDENT the page MUST apply:

| `/me` result | Button | Notice |
|--------------|--------|--------|
| 200 `ACTIVE` | none | Active subscription with its end date |
| 200 `PENDING` | none (no new checkout) | Pending subscription with the full `subscriptionId`, copyable as a reference code |
| 200 `EXPIRED` | "Suscribirme" | Previous subscription already expired, with its end date |
| 404 `NO_SUBSCRIPTION` | "Suscribirme" | none |

#### Scenario: Active subscription

- GIVEN `/me` returns `ACTIVE` with an end date
- WHEN the STUDENT requests `/plans`
- THEN a notice shows the end date and no "Suscribirme" control is rendered

#### Scenario: Pending subscription

- GIVEN `/me` returns `PENDING` with `subscriptionId` `S-123`
- WHEN the STUDENT requests `/plans`
- THEN the notice shows the full `S-123` in a copyable element
- AND no "Suscribirme" control is rendered and no checkout can be started

#### Scenario: Expired subscription offers renewal

- GIVEN `/me` returns `EXPIRED` with an end date
- WHEN the STUDENT requests `/plans`
- THEN a notice states the subscription already expired and shows the end date
- AND "Suscribirme" is rendered for each plan

#### Scenario: No subscription

- GIVEN `/me` answers 404 `NO_SUBSCRIPTION`
- WHEN the STUDENT requests `/plans`
- THEN "Suscribirme" is rendered and no subscription notice is shown

### Requirement: Subscription status failure is fail-closed

For an authenticated STUDENT, any `/me` outcome other than `200` or `404
NO_SUBSCRIPTION` (including `5xx`, `429`, `401`, timeout) MUST render the
standard error view with no plans and no "Suscribirme" control, so no
checkout can start while the state cannot be verified. The response MUST
NOT leak upstream detail.

#### Scenario: Billing unavailable for a student

- GIVEN `/me` answers 503 or times out
- WHEN the STUDENT requests `/plans`
- THEN the standard error view is rendered
- AND no "Suscribirme" control appears anywhere in the response

#### Scenario: Unexpected status

- GIVEN `/me` answers an unexpected status such as 500
- WHEN the STUDENT requests `/plans`
- THEN the same fail-closed error view is rendered

# BFF Plan Subscription Checkout Specification

## Purpose

Let a STUDENT subscribe to a plan from the web BFF with online payment
(`MERCADO_PAGO`, real or local simulator) and land on an honest return page
whose state comes only from the Billing API. Mirrors the class-purchase flow
(#365) and respects ADR-0038 / ADR-0039: a `COMPLETED` payment is not an
active subscription.

## Requirements

### Requirement: Subscribe POST is STUDENT-only and CSRF-protected

The BFF MUST expose a `POST` subscribe endpoint that accepts a `planId`. It
MUST require an authenticated principal with role `STUDENT` and MUST require
a valid CSRF token. A request without a valid CSRF token MUST be rejected and
MUST NOT call the Billing API. An anonymous request MUST be redirected to
`/login`. An authenticated non-STUDENT request MUST be rejected (forbidden)
and MUST NOT call the Billing API.

#### Scenario: Missing CSRF token is rejected

- GIVEN an authenticated STUDENT
- WHEN they POST the subscribe endpoint without a CSRF token
- THEN the response is a client error (403)
- AND no `POST /api/v1/billing/subscriptions` is sent to the API

#### Scenario: Non-student role is rejected

- GIVEN an authenticated user whose role is not `STUDENT`
- WHEN they POST the subscribe endpoint with a valid CSRF token
- THEN the response is 403
- AND the Billing API receives no checkout request

#### Scenario: Anonymous POST goes to login

- GIVEN a visitor with no session
- WHEN they POST the subscribe endpoint
- THEN the BFF redirects to `/login` and calls no upstream API

### Requirement: Checkout request uses MERCADO_PAGO and a render-time idempotency key

The BFF MUST call `POST /api/v1/billing/subscriptions` with the caller's
access token, body `{planId, paymentMethod: "MERCADO_PAGO", idempotencyKey}`.
The idempotency key MUST be generated when the plans page is rendered and
carried in the form, so a double submit or browser retry of the same rendered
form replays the same checkout upstream. The BFF MUST NOT generate a new key
at POST time.

#### Scenario: Request body carries plan, method and the rendered key

- GIVEN the plans page rendered a subscribe form with idempotency key `K`
- WHEN the STUDENT submits it for plan `P`
- THEN the API receives `{planId: P, paymentMethod: "MERCADO_PAGO",
  idempotencyKey: K}` with the student's bearer token

#### Scenario: Double submit replays the same key

- GIVEN a rendered form with key `K`
- WHEN it is submitted twice
- THEN both upstream calls carry the same `K`

### Requirement: Redirect to checkout only through CheckoutUrlPolicy

On a `201` the BFF MUST validate `checkoutUrl` with the existing
`CheckoutUrlPolicy` and redirect the browser to it only when the policy
accepts it. A URL rejected by the policy MUST NOT be followed; the BFF MUST
render the standard purchase notice error view instead. Before redirecting,
the BFF MUST store `{kind: SUBSCRIPTION, paymentId}` from the response in the
server session.

#### Scenario: Accepted checkout URL redirects and stores session state

- GIVEN the API answers `201` with `paymentId` `PAY-1` and an allowed
  `checkoutUrl`
- WHEN the STUDENT submits the form
- THEN the BFF responds with a redirect to that `checkoutUrl`
- AND the session holds `{kind: SUBSCRIPTION, paymentId: PAY-1}`

#### Scenario: Disallowed checkout URL is not followed

- GIVEN the API answers `201` with a `checkoutUrl` outside the policy
- WHEN the STUDENT submits the form
- THEN there is no redirect to that URL
- AND the standard error notice view is rendered

### Requirement: Upstream checkout errors map to specific, non-leaky outcomes

The BFF MUST map checkout errors as follows, reusing `PurchaseFlowErrors`
and the `purchase-notice` view, with Spanish voseo copy and without leaking
upstream problem-detail bodies:

| Upstream | Outcome |
|----------|---------|
| 409 subscription already active | Notice that the student already has an active subscription, with its end date when provided |
| 409 `SUBSCRIPTION_CONFLICT` | Notice that a subscription is already being processed / conflicts, distinct from "already active" |
| 422 (`PLAN_NOT_AVAILABLE` or payment method not accepted) | Notice that the plan is not available for online payment |
| 400 | Notice that the request was invalid; no retry loop |
| 503 | Notice that payment is temporarily unavailable, try again later |

#### Scenario: Already active

- GIVEN the API answers 409 with the already-active problem code and
  `currentEndDate`
- WHEN the STUDENT subscribes
- THEN the notice view states the subscription is active and shows the end date

#### Scenario: Subscription conflict is distinct

- GIVEN the API answers 409 with code `SUBSCRIPTION_CONFLICT`
- WHEN the STUDENT subscribes
- THEN the notice copy differs from the already-active copy

#### Scenario: Plan not available

- GIVEN the API answers 422
- WHEN the STUDENT subscribes
- THEN the notice says the plan cannot be paid online and offers a way back to
  `/plans`

#### Scenario: Invalid request

- GIVEN the API answers 400
- WHEN the STUDENT subscribes
- THEN the notice reports an invalid request and exposes no upstream detail

#### Scenario: Payment provider unavailable

- GIVEN the API answers 503
- WHEN the STUDENT subscribes
- THEN the notice reports temporary unavailability and exposes no upstream
  detail

### Requirement: Return page state comes from the session payment and owner-only status

`/compras/retorno` MUST determine the outcome only from the session-held
`{kind, paymentId}` and `GET /api/v1/billing/payments/{paymentId}` called with
the caller's token. Query-string parameters (including `status`,
`payment_id`, `external_reference`) MUST be ignored for every decision. When
the stored kind is `SUBSCRIPTION` and the session holds no `paymentId`, or the
API answers `404` (not the owner / unknown), the BFF MUST render a
subscription-specific fallback and MUST NOT redirect to `/classes`. When the
session holds no kind at all, the route MUST behave as the class-purchase
return (redirect to `/classes`), because a stateless browser cannot be told
apart from a class buyer and the existing class-purchase behavior is pinned by
its tests.

#### Scenario: URL params never decide

- GIVEN the session holds `{SUBSCRIPTION, PAY-1}` and the API reports
  `AWAITING_PROVIDER`
- WHEN the browser returns with `?status=approved&payment_id=OTHER`
- THEN the page shows the pending copy
- AND the status request used `PAY-1`, not `OTHER`

#### Scenario: Payment owned by someone else

- GIVEN the session holds `{SUBSCRIPTION, PAY-1}` and the API answers 404
- WHEN the browser returns
- THEN the subscription fallback view is rendered
- AND the response is not a redirect to `/classes`

#### Scenario: Subscription kind without a payment id

- GIVEN a STUDENT whose session holds kind `SUBSCRIPTION` and no `paymentId`
- WHEN they open `/compras/retorno`
- THEN the subscription fallback view is rendered with a link back to `/plans`

#### Scenario: No session state

- GIVEN a STUDENT whose session holds no kind and no `paymentId`
- WHEN they open `/compras/retorno`
- THEN the response is the class-purchase behavior (redirect to `/classes`)
- AND no subscription copy is rendered

### Requirement: Subscription return copy follows PaymentProgress and never claims activation

For `kind = SUBSCRIPTION` the page MUST use subscription copy mapped from
`PaymentProgress`, never class-assignment copy ("asignando tus lugares").
Because a payment `COMPLETED` is not an active subscription (ADR-0039), the
confirmed state MUST read "tu suscripción se está activando" and MUST NOT
state that the subscription is already active.

| PaymentProgress | Required copy intent |
|-----------------|----------------------|
| confirmed (`COMPLETED`) | "Tu suscripción se está activando" |
| pending (`AWAITING_PROVIDER` and equivalents) | Payment still being confirmed |
| rejected / cancelled / expired | Payment not completed, with a path back to `/plans` |

#### Scenario: Confirmed payment

- GIVEN the session holds `{SUBSCRIPTION, PAY-1}` and the API reports
  `COMPLETED`
- WHEN the browser returns
- THEN the page contains "se está activando"
- AND does not contain "activa" as a claim of completed activation nor
  class-assignment wording

#### Scenario: Pending payment

- GIVEN the API reports `AWAITING_PROVIDER`
- WHEN the browser returns
- THEN the page shows pending copy and no activation claim

#### Scenario: Rejected payment

- GIVEN the API reports `REJECTED`
- WHEN the browser returns
- THEN the page says the payment was not completed and links to `/plans`

### Requirement: Purchase-kind discrimination keeps the class return unchanged

The session MUST record the purchase kind alongside `paymentId`, and the
shared `/compras/retorno` route MUST dispatch on it: `SUBSCRIPTION` renders
the subscription return view; the class-purchase kind MUST keep the exact
existing behavior (copy, redirects, fallbacks) and all existing class-return
tests MUST pass unchanged. A class checkout started after a subscription
checkout MUST overwrite the stored kind and payment id, and vice versa.

#### Scenario: Class purchase return is unchanged

- GIVEN a class checkout stored its payment in the session
- WHEN the browser returns to `/compras/retorno`
- THEN the response is identical to pre-change behavior, including the
  class-assignment copy

#### Scenario: Latest checkout wins

- GIVEN a subscription checkout then a class checkout in the same session
- WHEN the browser returns to `/compras/retorno`
- THEN the class-purchase view is rendered for the class payment id

### Requirement: Documented deviation from signed state (D1)

The design MUST NOT carry the payment identity in a signed `state` query
parameter. `back_url` is a single global API property
(`billing.mercadopago.checkout-back-url`), not per request, so the BFF
carries `paymentId` in the server session and verifies it through the
owner-only payment status endpoint. This deviation from issue #361's
criterion 3, identical to #365, MUST be documented in
`docs/06-BILLING-API.md`.

#### Scenario: Deviation is documented

- GIVEN the change is applied
- WHEN `docs/06-BILLING-API.md` is read
- THEN it states that the web return flow uses a session-held `paymentId`
  instead of a signed `state`, and why

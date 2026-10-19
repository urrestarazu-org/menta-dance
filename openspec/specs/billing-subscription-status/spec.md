# Billing Subscription Status Specification

## Purpose

Read-side view of a user's own subscription: current state (pending payment /
active / expiring soon / expired), computed remaining days, a dedicated
no-subscription error contract, and the full chronological history of every
subscription the user ever checked out. All responses resolve the acting user
from the authenticated principal, the same way as existing self-service
endpoints, and are never cached.

## Requirements

### Requirement: Acting-user resolution

`GET /api/v1/billing/subscriptions/me` and
`GET /api/v1/billing/subscriptions/me/history` MUST resolve the target user
from the authenticated `Authentication` principal (`actingUserId`), identical
to the existing `DELETE /subscriptions/me` resolution. No role or additional
authorization check applies beyond authentication — any authenticated user
reads only their own data.

#### Scenario: Authenticated user reads their own subscription

- GIVEN an authenticated request with no explicit user identifier in the path
- WHEN `GET /me` or `GET /me/history` is called
- THEN the acting user is derived from the `Authentication` principal, not
  from any request parameter

### Requirement: Current subscription considers only PENDING, ACTIVE, EXPIRED

`GET /me` MUST resolve the "current subscription" among subscriptions whose
status is PENDING, ACTIVE, or EXPIRED only. A CANCELLED subscription that
still retains access within its `endDate` MUST NOT be returned by this
endpoint (D1) — that case is deliberately out of scope for this capability,
not an oversight, and is left for a future extension.

#### Scenario: A cancelled-with-remaining-access subscription is not returned

- GIVEN a user's only subscription row has status CANCELLED and an `endDate`
  in the future
- WHEN the user calls `GET /me`
- THEN the response is the no-subscription error (see below), not the
  cancelled row

### Requirement: Active subscription response

For a subscription with status ACTIVE, `GET /me` MUST return `id` (UUID
string), plan, `startDate`, `endDate`, and `daysRemaining` computed at read
time from `endDate` (never stored). MUST NOT include `autoRenew` or any
recurring-billing field — none exists in this domain.

#### Scenario: Active subscription with days remaining

- GIVEN an ACTIVE subscription with `endDate` 20 days in the future
- WHEN the user calls `GET /me`
- THEN the response includes `status: "ACTIVE"` and `daysRemaining: 20`,
  computed from `endDate`, not a stored value

### Requirement: Expiring-soon flag

When an ACTIVE subscription's computed `daysRemaining` is less than or equal
to 7 (a fixed domain constant, not externally configurable), the `GET /me`
response MUST additionally include `expiringSoon: true`. Below that boundary
`expiringSoon` MUST be `false` or omitted.

#### Scenario: Subscription within the expiring-soon window

- GIVEN an ACTIVE subscription with `daysRemaining` equal to 7
- WHEN the user calls `GET /me`
- THEN the response includes `expiringSoon: true`

#### Scenario: Subscription outside the expiring-soon window

- GIVEN an ACTIVE subscription with `daysRemaining` equal to 8
- WHEN the user calls `GET /me`
- THEN the response either omits `expiringSoon` or sets it to `false`

### Requirement: Expired subscription response

For a subscription with status EXPIRED, `GET /me` MUST return `id`, plan,
`startDate`, `endDate`, and a renewal hint that references
`GET /api/v1/billing/plans`. MUST NOT include `daysRemaining` or
`expiringSoon`.

#### Scenario: Expired subscription points to the plans endpoint

- GIVEN a subscription with status EXPIRED
- WHEN the user calls `GET /me`
- THEN the response has `status: "EXPIRED"` and a reference to
  `GET /api/v1/billing/plans` for renewal

### Requirement: Pending-payment subscription response

For a subscription with status PENDING (no `startDate`/`endDate` yet),
`GET /me` MUST return `id`, plan, `status: "PENDING"`, and the existing
`Subscription.getCheckoutUrl()` value so the user can complete payment. MUST
NOT include `daysRemaining`, `expiringSoon`, `startDate`, or `endDate`.

#### Scenario: Pending subscription exposes the checkout URL

- GIVEN a subscription with status PENDING and a persisted checkout URL
- WHEN the user calls `GET /me`
- THEN the response includes that `checkoutUrl` and no `daysRemaining`

### Requirement: No-subscription error

When a user has no subscription row with status PENDING, ACTIVE, or EXPIRED,
`GET /me` MUST respond `404` with an RFC 9457 ProblemDetail carrying a
`NO_SUBSCRIPTION` error code, rendered through the existing
`SubscriptionExceptionHandler` pattern. This MUST use a distinct exception
type from `SubscriptionNotFoundException` — that exception is deliberately
ambiguous for cancellation anti-enumeration (US-BILLING-011), a concern that
does not apply when a user queries their own dashboard.

#### Scenario: User with no subscription rows

- GIVEN a user with zero persisted `billing_subscriptions` rows
- WHEN the user calls `GET /me`
- THEN the response is `404` with ProblemDetail `code: "NO_SUBSCRIPTION"`,
  distinct from `SUBSCRIPTION_NOT_FOUND`

### Requirement: Subscription history

`GET /api/v1/billing/subscriptions/me/history` MUST return every persisted
subscription row for the acting user (one immutable row per checkout, never
mutated into a different logical subscription), ordered descending by
creation date. Each entry MUST include `id` (UUID string), plan, status,
`startDate`, and `endDate`. This first version MUST NOT paginate.

#### Scenario: History lists all rows newest first

- GIVEN a user with three persisted subscription rows created at different
  times
- WHEN the user calls `GET /me/history`
- THEN the response lists all three rows ordered by creation date descending

### Requirement: Responses are never cached

Both `GET /me` and `GET /me/history` MUST NOT be served from any cache layer.
Each request MUST reflect the current persisted state, including a status
transition performed moments earlier by `SubscriptionExpiryWorker`.

#### Scenario: A fresh expiry is visible immediately

- GIVEN a subscription just transitioned from ACTIVE to EXPIRED by the
  expiry worker
- WHEN the user calls `GET /me` immediately after
- THEN the response reflects `status: "EXPIRED"`, not a stale cached value

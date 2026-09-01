# Billing Subscriptions Specification

## Purpose

Let a paying student stop their own subscription's renewal and let an admin override it with
an auditable reason, while preserving already-correct access retention, re-purchase, and
checkout-rejection behavior, and never exposing the cancellation reason to the student.

## Requirements

### Requirement: Self-service cancellation

An authenticated student MUST be able to cancel their own `ACTIVE` subscription without
changing `endDate`.

#### Scenario: Student cancels an active subscription

- GIVEN an authenticated student with an `ACTIVE` subscription
- WHEN they call `DELETE /api/v1/billing/subscriptions/me`
- THEN status becomes `CANCELLED`, `cancelledAt` is set, `endDate` is unchanged
- AND the response reports `endDate` and the plan's `cancellationPolicy` text

#### Scenario: No cancellable subscription

- GIVEN a student with no `ACTIVE` subscription
- WHEN they call `DELETE /api/v1/billing/subscriptions/me`
- THEN the system returns `404` and nothing changes

### Requirement: Access retained until endDate (regression)

A `CANCELLED` subscription MUST keep granting access exactly as `ACTIVE` does until
`endDate`, and MUST deny it after. This change MUST NOT alter this existing behavior.

#### Scenario: Access persists then ends

- GIVEN a `CANCELLED` subscription
- WHEN Virtual resolves access before vs. after `endDate`
- THEN access is granted before `endDate` and denied after, identically to `ACTIVE`

### Requirement: Re-purchase after cancellation (regression)

Checkout rejection MUST continue to trigger only on `PENDING`/`ACTIVE` subscriptions, never
on `CANCELLED`.

#### Scenario: Checkout succeeds after cancellation

- GIVEN a student whose only subscription is `CANCELLED`
- WHEN they start a checkout for any plan
- THEN checkout returns `201`, creates a new subscription row, and never reactivates the old
  one

### Requirement: Checkout overlap notice (D3)

When a new checkout's plan overlaps a still-in-force `CANCELLED` subscription for that same
plan, the response MUST carry a non-blocking `overlapNotice`; otherwise it MUST be `null`.
Overlap means: same `planId`, status `CANCELLED`, `endDate` present and strictly after now.

#### Scenario: Overlap produces a warning, never a block

- GIVEN a `CANCELLED` subscription for plan P with `endDate` in the future
- WHEN the student checks out for plan P
- THEN the response is `201` with `checkoutUrl`, and `overlapNotice = {code:
  "OVERLAPPING_PAID_PERIOD", currentAccessEndsAt: <latest matching endDate>}`

#### Scenario: No overlap reports no notice

- GIVEN the student's `CANCELLED` subscriptions for plan P are absent, for another plan,
  expired, or `endDate`-less
- WHEN they check out for plan P
- THEN `overlapNotice` is `null`

#### Scenario: Idempotent replay recomputes the notice

- GIVEN a checkout retried with the same `idempotencyKey` as an overlap-eligible purchase
- WHEN the replay branch returns its result
- THEN `overlapNotice` is present exactly as on the first attempt

### Requirement: Administrative cancellation, mandatory reason, audit, no leak

An admin MUST cancel any `ACTIVE` subscription only with a non-blank `reason`; actor and
reason MUST be persisted; `cancellationReason` MUST NEVER appear in a student-facing
response; a non-admin MUST be rejected.

#### Scenario: Admin cancels with a reason

- GIVEN an `ACTIVE` subscription
- WHEN an admin calls `DELETE /api/v1/admin/billing/subscriptions/{subscriptionId}` with a
  non-blank `reason`
- THEN status becomes `CANCELLED` with `cancelledAt`, `cancelledBy`, `cancellationReason`
  persisted
- AND the response shape carries no `cancellationReason`

#### Scenario: Missing reason is rejected

- GIVEN an `ACTIVE` subscription
- WHEN an admin calls the endpoint with a blank or absent `reason`
- THEN the system returns `400` and the subscription stays `ACTIVE`

#### Scenario: Reason never reaches the student

- GIVEN a subscription cancelled by an admin
- WHEN the student reads their own subscription or the cancellation response
- THEN no `cancellationReason` field exists in that payload, by DTO shape

#### Scenario: Non-admin cannot cancel any subscription via the admin route

- GIVEN an authenticated student without `ROLE_ADMIN`
- WHEN they call the admin cancellation endpoint for any subscription
- THEN the system returns `403` and nothing changes

# Delta for Billing Subscriptions

## ADDED Requirements

### Requirement: Admin-assigned trial subscription

An authenticated user with `ROLE_ADMIN` MUST be able to grant a target user an `ACTIVE`
`TRIAL` subscription with no associated `Payment`, for an existing, non-`INACTIVE` plan, using
the same frozen `courseIds` snapshot a paid subscription would use. The trial's duration in
days MUST be taken from the admin's request and MUST NOT be derived from the plan's own
duration. The grant MUST record actor, timestamp, a mandatory non-blank reason, and the
granted number of days. The request MUST be rejected with `400`, changing nothing, when
`reason` is blank or absent or when `days` is absent, zero, or negative. No non-admin route
MAY grant a trial, including to oneself.

#### Scenario: Admin grants a trial subscription

- GIVEN an admin, a target user with no current subscription, and an available plan
- WHEN the admin submits the trial grant for that user and plan with a non-blank `reason` and
  `days = N` in the request, where `N` is the admin's own decision and is unrelated to the
  plan's configured duration
- THEN a new subscription is created with `status = ACTIVE`, `type = TRIAL`,
  `fulfillmentStatus = ASSIGNED`, `startDate = now`, `endDate = now + N days`, the plan's
  `courseIds` snapshot, and no `Payment` row
- AND the grant's actor, timestamp, reason, and the requested `days` are persisted

#### Scenario: Missing reason is rejected

- GIVEN an admin and a target user eligible for a trial
- WHEN the admin submits the grant with a blank or absent `reason`
- THEN the system returns `400` and no subscription is created

#### Scenario: A non-positive or absent days value is rejected

- GIVEN an admin and a target user eligible for a trial
- WHEN the admin submits the grant with `days` absent, zero, or negative
- THEN the system returns `400` and no subscription is created
- AND the plan's own duration is never substituted for the missing or invalid value

#### Scenario: Non-admin cannot grant a trial to anyone, including self

- GIVEN an authenticated student without `ROLE_ADMIN`
- WHEN they attempt to call the trial-grant capability, for themselves or another user
- THEN the system returns `403` (or the route does not exist for that role) and no subscription
  is created

### Requirement: Trial access parity with a paid subscription

A `TRIAL` subscription MUST grant access to Virtual course content identically to a `PAID`
subscription with the same course snapshot, for the same time window. `SubscriptionType` MUST
NOT be used as an authorization input anywhere in the access decision.

#### Scenario: Trial and paid produce identical access decisions

- GIVEN a `TRIAL` subscription and a `PAID` subscription, both `ACTIVE`, with the same
  `courseIds` snapshot and overlapping validity windows
- WHEN Virtual resolves course access for each subscription's owner
- THEN both access decisions are identical, independent of `type`

### Requirement: Automatic subscription expiry

The system MUST transition any subscription with `status = ACTIVE` and `endDate` at or before
the current time to `status = EXPIRED` without manual intervention, regardless of `type` (`PAID`
or `TRIAL`) — a subscription expires the instant `endDate` is reached, not only once it is
strictly in the past. Expiry MUST be idempotent: a subscription not currently `ACTIVE` MUST NOT
be altered, and `endDate` MUST NOT change.

#### Scenario: A stale trial expires automatically

- GIVEN a `TRIAL` subscription with `status = ACTIVE` and `endDate` at or before now
- WHEN the expiry process runs
- THEN its `status` becomes `EXPIRED` and `endDate` is unchanged

#### Scenario: A stale paid subscription expires automatically

- GIVEN a `PAID` subscription with `status = ACTIVE` and `endDate` at or before now
- WHEN the expiry process runs
- THEN its `status` becomes `EXPIRED` and `endDate` is unchanged

#### Scenario: Non-active subscriptions are left untouched

- GIVEN a `CANCELLED` or already-`EXPIRED` subscription whose `endDate` is in the past
- WHEN the expiry process runs
- THEN its `status` and `endDate` remain unchanged

### Requirement: Reject a trial grant for a nonexistent user

The trial grant MUST validate the target `userId` before any other check. When `userId` does
not reference an existing user, the system MUST return `404 USER_NOT_FOUND` and MUST NOT
create any subscription row. This check MUST run first, ahead of the plan-availability check
(`422`) and the already-in-force check (`409`), so it wins whenever multiple problems coexist.

#### Scenario: Unknown userId is rejected before any other check

- GIVEN a `userId` that does not reference an existing user
- WHEN an admin attempts to grant a trial for that `userId`
- THEN the system returns `404 USER_NOT_FOUND` and no subscription row is created

#### Scenario: Unknown user takes precedence over an inactive plan

- GIVEN a `userId` that does not reference an existing user, and a `planId` that does not exist
  or is not `ACTIVE`
- WHEN an admin attempts to grant a trial referencing both values
- THEN the system returns `404 USER_NOT_FOUND`, not `422`, and no subscription row is created

### Requirement: Reject a trial grant when a subscription is already in force

The trial grant MUST be rejected when the target user already has a subscription with
`status` in (`ACTIVE`, `PENDING`), regardless of that subscription's `type`. This check MUST
run last, only after confirming the target user exists (`404`) and the plan is available
(`422`).

#### Scenario: Target already has an active subscription of either type

- GIVEN a target user with a current subscription whose status is `ACTIVE` or `PENDING`
  (`TRIAL` or `PAID`)
- WHEN an admin attempts to grant a trial to that user
- THEN the system returns `409 SUBSCRIPTION_ALREADY_ACTIVE` and no new subscription is created

### Requirement: Re-purchase permitted after trial ends

Once a student's `TRIAL` subscription reaches `EXPIRED` or `CANCELLED`, that student MUST be
able to start a paid checkout for any available plan, producing a new, distinguishable
subscription row without reusing or reactivating the trial row.

#### Scenario: Paid checkout succeeds after trial expiry or cancellation

- GIVEN a student whose only subscription is a `TRIAL` in status `EXPIRED` or `CANCELLED`
- WHEN they start a checkout for an available plan
- THEN a new subscription row is created with `type = PAID`, distinguishable in the student's
  subscription history from the prior trial row, and the trial row is never reactivated

### Requirement: Reject a trial grant for an unavailable plan

The trial grant MUST be rejected when the referenced `planId` does not exist or is not
`ACTIVE`. This check MUST run only after confirming the target user exists (`404`), and MUST
run before the already-in-force check (`409`).

#### Scenario: Plan does not exist or is inactive

- GIVEN a `planId` that does not exist, or exists with a non-`ACTIVE` status
- WHEN an admin attempts to grant a trial referencing that `planId`
- THEN the system returns `422 PLAN_NOT_AVAILABLE` and no subscription is created

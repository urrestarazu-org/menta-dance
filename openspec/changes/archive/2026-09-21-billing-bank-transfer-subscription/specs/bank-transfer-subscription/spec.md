# Bank Transfer Subscription Specification

## Purpose

Let a student without a card subscribe by bank transfer: create a `PENDING` subscription with
a `Payment` awaiting manual verification, submit and replace proof of the transfer, check
payment status, and reach a definite outcome — an admin's decision or an automatic 72-hour
expiry — so the payment never stays stuck.

## Requirements

### Requirement: Creating a bank-transfer subscription

A successful `BANK_TRANSFER` subscription request (see the `billing-subscriptions` capability)
MUST return the bank details needed to complete the transfer: CBU, alias, account holder name,
CUIT, the exact amount, and a payment reference tying the transfer to that `Payment`. The
system MUST reject an 11th such request from the same user within the same calendar day with
`429 application/problem+json`, creating nothing.

#### Scenario: Response carries usable bank details

- GIVEN a successful `BANK_TRANSFER` subscription request
- WHEN the response is read
- THEN it includes CBU, alias, holder, CUIT, amount, and a reference identifying the `Payment`

#### Scenario: An 11th daily bank-transfer request is rejected

- GIVEN a user who already made 10 `BANK_TRANSFER` subscription requests today
- WHEN they make an 11th
- THEN the response is `429 application/problem+json`
- AND no `Subscription` or `Payment` is created

### Requirement: Submitting, validating, and replacing a payment proof

The `Payment`'s owner MUST be able to submit a proof file via
`POST /api/v1/billing/payments/{paymentId}/proof` (multipart) while it is
`AwaitingManualVerification`. A valid submission MUST store the proof, notify operations that a
proof is waiting (D2), and return `200`. The system MUST reject a proof whose declared content
type is not PNG, JPG/JPEG, or PDF, or whose size exceeds 5 MB, with `400
application/problem+json`, storing nothing. A later valid submission for the same `Payment`
MUST replace the prior proof entirely and send a new notification; the replaced proof MUST no
longer be retrievable. A caller who is not the `Payment`'s owner MUST be rejected without
storing anything. The system MUST reject a 4th upload for the same `Payment` with `429
application/problem+json`.

#### Scenario: Valid proof is stored and operations is notified

- GIVEN an authenticated owner of a `Payment` in `AwaitingManualVerification`
- WHEN they submit a valid PNG/JPG/PDF proof under 5 MB
- THEN the response is `200`
- AND operations receives a notification that a proof is waiting

#### Scenario: Invalid format is rejected

- GIVEN the same owner and `Payment`
- WHEN they submit a file whose content type is not PNG, JPG/JPEG, or PDF
- THEN the response is `400 application/problem+json`
- AND no proof is stored and no notification is sent

#### Scenario: Oversized file is rejected

- GIVEN the same owner and `Payment`
- WHEN they submit a file larger than 5 MB
- THEN the response is `400 application/problem+json`
- AND no proof is stored and no notification is sent

#### Scenario: A second submission replaces the first

- GIVEN a `Payment` with one proof already stored
- WHEN the owner submits a second valid proof
- THEN the first proof is no longer retrievable
- AND a new operations notification is sent

#### Scenario: A non-owner cannot submit a proof

- GIVEN a `Payment` owned by user A
- WHEN user B submits a proof for it
- THEN the request is rejected and nothing is stored

#### Scenario: A 4th upload for the same payment is rejected

- GIVEN a `Payment` that already received 3 proof uploads
- WHEN a 4th is submitted
- THEN the response is `429 application/problem+json`
- AND nothing is stored and no notification is sent

### Requirement: Reading payment status

The `Payment`'s owner MUST be able to read its current status via
`GET /api/v1/billing/payments/{paymentId}`, receiving `status`, `createdAt`, and `updatedAt`
with `200`. A non-owner MUST be rejected.

#### Scenario: Owner reads payment status

- GIVEN an authenticated owner of a `Payment`
- WHEN they call `GET /api/v1/billing/payments/{paymentId}`
- THEN the response is `200` with `status`, `createdAt`, and `updatedAt`

#### Scenario: A non-owner cannot read another user's payment

- GIVEN a `Payment` owned by user A
- WHEN user B calls `GET /api/v1/billing/payments/{paymentId}` for it
- THEN the request is rejected and no payment data is returned

### Requirement: Automatic expiry of an unresolved bank-transfer payment

A `Payment` in `AwaitingManualVerification` with no proof submitted within 72 hours of creation
MUST transition to `Expired`, and its associated `PENDING` `Subscription` MUST be cancelled,
both in the same transaction.

#### Scenario: 72 hours pass with no proof

- GIVEN a `Payment` `AwaitingManualVerification`, created more than 72 hours ago, with no proof
  ever submitted
- WHEN the expiry sweep runs
- THEN the `Payment` becomes `Expired`
- AND its associated `Subscription` becomes `CANCELLED`, in the same transaction

#### Scenario: A submitted proof withholds automatic expiry

- GIVEN a `Payment` `AwaitingManualVerification`, created more than 72 hours ago, with a proof
  already submitted
- WHEN the expiry sweep runs
- THEN the `Payment` is left `AwaitingManualVerification`

### Requirement: Admin resolution of a pending proof (D1)

An authenticated `ROLE_ADMIN` principal MUST be able to resolve a `Payment` that is
`AwaitingManualVerification` with at least one submitted proof: approving it transitions the
`Payment` to `Completed` and its `Subscription` out of `PENDING`; rejecting it transitions the
`Payment` to `Rejected` and its `Subscription` to `CANCELLED`. A non-admin caller MUST be
rejected with `403` and nothing changes. Resolving a `Payment` that is no longer
`AwaitingManualVerification` MUST fail without changing anything.

#### Scenario: Admin approves a pending proof

- GIVEN an admin and a `Payment` `AwaitingManualVerification` with a submitted proof
- WHEN the admin approves it
- THEN the `Payment` becomes `Completed`
- AND its `Subscription` leaves `PENDING`

#### Scenario: Admin rejects a pending proof

- GIVEN an admin and a `Payment` `AwaitingManualVerification` with a submitted proof
- WHEN the admin rejects it
- THEN the `Payment` becomes `Rejected`
- AND its `Subscription` becomes `CANCELLED`

#### Scenario: A non-admin cannot resolve a payment

- GIVEN an authenticated user without `ROLE_ADMIN`
- WHEN they attempt to resolve any `Payment`
- THEN the response is `403` and nothing changes

#### Scenario: Resolving an already-expired payment fails

- GIVEN a `Payment` that the expiry sweep already moved to `Expired`
- WHEN an admin attempts to approve or reject it
- THEN the attempt fails and neither the `Payment` nor its `Subscription` changes

### Requirement: Proof storage is not publicly reachable

A stored proof MUST NOT be reachable by an unauthenticated request, and MUST NOT be reachable
by an authenticated caller who is neither the `Payment`'s owner nor an admin.

#### Scenario: Unauthenticated access fails

- GIVEN a stored proof's location
- WHEN an unauthenticated request attempts to fetch it directly
- THEN the request is rejected

#### Scenario: Non-owner, non-admin access fails

- GIVEN a stored proof owned by user A
- WHEN authenticated user B, who is neither the owner nor an admin, attempts to fetch it
- THEN the request is rejected

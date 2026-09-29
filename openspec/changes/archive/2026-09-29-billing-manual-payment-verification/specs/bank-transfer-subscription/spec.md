# Delta for Bank Transfer Subscription

## MODIFIED Requirements

### Requirement: Admin resolution of a pending proof (D1)

An authenticated `ROLE_ADMIN` principal MUST be able to resolve a `Payment` that is
`AwaitingManualVerification` with at least one submitted proof: approving it transitions the
`Payment` to `Completed` and its `Subscription` out of `PENDING`; rejecting it transitions the
`Payment` to `Rejected` and its `Subscription` to `CANCELLED`. A non-admin caller MUST be
rejected with `403` and nothing changes. Resolving a `Payment` that is no longer
`AwaitingManualVerification` MUST fail without changing anything. In addition, a successful
approve or reject MUST write a `billing_audit_log` entry (admin, action, reason, timestamp — see
the `billing-manual-payment-verification` capability's D4 requirement) and MUST send the buyer a
Spanish decision email through the dedicated buyer-notification port (D5); email delivery runs
outside the commit transaction and its failure never rolls back the transition (D8).

(Previously: resolution changed only `Payment`/`Subscription` state, with no audit trail and no
buyer notification.)

#### Scenario: Admin approves a pending proof

- GIVEN an admin and a `Payment` `AwaitingManualVerification` with a submitted proof
- WHEN the admin approves it
- THEN the `Payment` becomes `Completed`
- AND its `Subscription` leaves `PENDING`
- AND one `billing_audit_log` row is written
- AND the buyer receives a Spanish confirmation email

#### Scenario: Admin rejects a pending proof

- GIVEN an admin and a `Payment` `AwaitingManualVerification` with a submitted proof
- WHEN the admin rejects it
- THEN the `Payment` becomes `Rejected`
- AND its `Subscription` becomes `CANCELLED`
- AND one `billing_audit_log` row is written with the reason
- AND the buyer receives a Spanish rejection email containing that reason verbatim

#### Scenario: A non-admin cannot resolve a payment

- GIVEN an authenticated user without `ROLE_ADMIN`
- WHEN they attempt to resolve any `Payment`
- THEN the response is `403` and nothing changes

#### Scenario: Resolving an already-expired payment fails

- GIVEN a `Payment` that the expiry sweep already moved to `Expired`
- WHEN an admin attempts to approve or reject it
- THEN the attempt fails and neither the `Payment` nor its `Subscription` changes
- AND no audit row is written and no buyer email is sent

### Requirement: Proof storage is not publicly reachable

A stored proof MUST NOT be reachable by an unauthenticated request, and MUST NOT be reachable
by an authenticated caller who is neither the `Payment`'s owner nor an admin, with one sanctioned
exception: a valid, unexpired, HMAC-signed proof-access token (D1) MAY read the file through the
dedicated token-authenticated endpoint. An absent, tampered, or expired token MUST still fail.

(Previously: no exception existed — every direct fetch was rejected unconditionally.)

#### Scenario: Unauthenticated access fails

- GIVEN a stored proof's location
- WHEN an unauthenticated request attempts to fetch it directly, without a signed token
- THEN the request is rejected

#### Scenario: Non-owner, non-admin access fails

- GIVEN a stored proof owned by user A
- WHEN authenticated user B, who is neither the owner nor an admin, attempts to fetch it directly
- THEN the request is rejected

#### Scenario: A valid signed token reads the file

- GIVEN a proof-access token signed less than 15 minutes ago, unmodified
- WHEN it is presented to the token-authenticated file endpoint
- THEN the stored proof file is served

#### Scenario: An expired or tampered token still fails

- GIVEN a proof-access token that is expired or whose signature no longer verifies
- WHEN it is presented to the token-authenticated file endpoint
- THEN the request is rejected and no file is served

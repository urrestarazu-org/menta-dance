# Billing Manual Payment Verification Specification

## Purpose

Give an admin the operable surface around the existing approve/reject decision: find payments
awaiting manual verification, view proof through a time-limited signed link, resolve a stuck
`RECONCILIATION_REQUIRED` payment with an audited correction, and leave an append-only trail —
plus tell the buyer what happened.

## Requirements

### Requirement: List payments awaiting manual verification

`GET /api/v1/admin/billing/payments?status=PENDING&substatus=AWAITING_MANUAL_VERIFICATION` MUST
be available only to `ROLE_ADMIN`, returning `200` with a paginated, oldest-first list carrying
user, plan, amount/currency, status/substatus, `hasProof`, and `createdAt`. The page size MUST
NOT exceed 50; a request for a larger page MUST be rejected with `400`.

#### Scenario: Admin lists pending-verification payments

- GIVEN payments in `PENDING/AWAITING_MANUAL_VERIFICATION`
- WHEN an admin calls the list endpoint
- THEN the response is `200` with results ordered oldest-first, each carrying user, plan,
  amount/currency, status/substatus, `hasProof`, and `createdAt`

#### Scenario: Non-admin is rejected

- GIVEN an authenticated caller without `ROLE_ADMIN`
- WHEN they call the list endpoint
- THEN the response is `403`

#### Scenario: Anonymous caller is rejected

- GIVEN no authentication credential
- WHEN the list endpoint is called
- THEN the response is `401` or `403`

#### Scenario: Page size above 50 is rejected

- GIVEN a request with `pageSize=51` or higher
- WHEN an admin calls the list endpoint
- THEN the response is `400`, and no results are returned

### Requirement: Payment detail with a signed proof URL

`GET /api/v1/admin/billing/payments/{paymentId}` MUST be available only to `ROLE_ADMIN`,
returning `200` with the payment, its user, its plan, and — when a proof exists — a proof URL
signed with an embedded 15-minute expiry (D1).

#### Scenario: Admin views payment detail with a proof link

- GIVEN a payment with a submitted proof
- WHEN an admin requests its detail
- THEN the response is `200` including a signed proof URL valid for 15 minutes

#### Scenario: Non-admin is rejected

- GIVEN an authenticated caller without `ROLE_ADMIN`
- WHEN they request any payment's detail
- THEN the response is `403`

### Requirement: Token-authenticated proof file serving (D1)

The signed proof URL's endpoint MUST serve the stored file when its token is present, unexpired,
and its HMAC signature (`Mac` + `MessageDigest.isEqual`, mirroring
`HmacSha256WebhookSignatureVerifier`) verifies against the payload. It MUST reject a missing
token with `401`, and a tampered or expired token with `403`, serving nothing in either case.
This endpoint does not require `ROLE_ADMIN` — the token itself is the credential.

#### Scenario: Valid unexpired token serves the file

- GIVEN a token signed less than 15 minutes ago, unmodified
- WHEN the file endpoint is called with it
- THEN the response is `200` with the stored proof file

#### Scenario: Absent token is rejected

- GIVEN a request to the file endpoint with no token
- WHEN it is processed
- THEN the response is `401`, and no file is served

#### Scenario: Tampered token is rejected

- GIVEN a token whose payload or signature was modified after issuance
- WHEN the file endpoint is called with it
- THEN the response is `403`, and no file is served

#### Scenario: Expired token is rejected

- GIVEN a token whose embedded expiry is more than 15 minutes in the past
- WHEN the file endpoint is called with it
- THEN the response is `403`, and no file is served

### Requirement: Audited correction out of RECONCILIATION_REQUIRED (D9)

`POST /api/v1/admin/billing/payments/{paymentId}/corrections` MUST be available only to
`ROLE_ADMIN`, applying a new domain transition on a payment currently `PENDING` with substatus
`RECONCILIATION_REQUIRED` into a terminal, approve-like or reject-like state, given mandatory
`reason` and evidence. On success it MUST mark the tied `billing_reconciliation_tasks` row
`resolved` with `resolved_at`/`resolved_by`, and write a `billing_audit_log` entry. A correction
attempted on a payment not in `RECONCILIATION_REQUIRED` MUST fail with `409`, changing nothing.

#### Scenario: Admin corrects a reconciliation-required payment

- GIVEN a payment `PENDING/RECONCILIATION_REQUIRED` with a tied open reconciliation task
- WHEN an admin submits a correction with `reason` and evidence
- THEN the payment transitions to the chosen terminal state
- AND the tied reconciliation task becomes `resolved` with `resolved_at`/`resolved_by`
- AND one `billing_audit_log` entry is written

#### Scenario: Correction on a payment not requiring reconciliation is rejected

- GIVEN a payment whose substatus is not `RECONCILIATION_REQUIRED`
- WHEN an admin submits a correction for it
- THEN the response is `409`, and neither the payment nor any reconciliation task changes

#### Scenario: Non-admin is rejected

- GIVEN an authenticated caller without `ROLE_ADMIN`
- WHEN they submit a correction
- THEN the response is `403`, and nothing changes

### Requirement: Append-only billing_audit_log (D4)

The system MUST write one `billing_audit_log` row — `payment_id`, `admin_id`, `action`, `reason`
(nullable), `created_at` — for every admin decision on a payment: approve, reject, and
correction. Correction rows MUST additionally record the submitted evidence. The log MUST be
append-only; no existing row is ever updated or deleted by this change.

#### Scenario: Approve writes an audit row

- GIVEN an admin approves a payment
- WHEN the transition commits
- THEN one `billing_audit_log` row is written with `payment_id`, `admin_id`, `action=APPROVE`,
  and `created_at`

#### Scenario: Reject writes an audit row with a reason

- GIVEN an admin rejects a payment with a reason
- WHEN the transition commits
- THEN one `billing_audit_log` row is written carrying that `reason`

#### Scenario: Correction writes an audit row with evidence

- GIVEN an admin corrects a `RECONCILIATION_REQUIRED` payment
- WHEN the correction commits
- THEN one `billing_audit_log` row is written including the submitted evidence

### Requirement: Buyer-facing decision email (D5, D8)

On approve or reject, the system MUST send the payment's buyer a Spanish-language email through a
dedicated buyer-notification port, distinct from the ops-mailbox proof-submission adapter: a
confirmation template on approve, a rejection template on reject carrying the admin's `reason`
verbatim. Email delivery MUST run outside the transaction that commits the payment/subscription
change, and its failure MUST NOT roll back an otherwise-valid approve, reject, or correction.

#### Scenario: Approval sends a confirmation email

- GIVEN an admin approves a payment
- WHEN the transition commits
- THEN the buyer receives a Spanish confirmation email
- AND the ops-mailbox adapter is not invoked

#### Scenario: Rejection sends a rejection email with the reason

- GIVEN an admin rejects a payment with a reason
- WHEN the transition commits
- THEN the buyer receives a Spanish rejection email containing that reason verbatim

#### Scenario: Email failure does not roll back the decision

- GIVEN an admin approves a payment
- WHEN the buyer notification adapter fails to send
- THEN the payment's `Completed` transition and its audit row remain committed

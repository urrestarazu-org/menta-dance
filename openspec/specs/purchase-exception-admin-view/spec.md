# Purchase Exception Admin View Specification

## Purpose

Give an admin a read-only, paginated inventory of physical purchases stuck in
`FulfillmentStatus.EXCEPTION`, so "which purchases need attention right now"
has an answer beyond re-reading #209's notification emails. This capability
adds no way to resolve, transition, or reassign a purchase — it is pure
observability over the existing state machine.

## Requirements

### Requirement: List purchases in EXCEPTION status

`GET /api/v1/admin/billing/purchases?status=EXCEPTION` MUST be available only
to `ROLE_ADMIN`, returning `200` with a paginated list ordered oldest-first by
the joined `Payment.createdAt`. Each row MUST carry the buyer's `user_id`,
`createdAt`, amount, currency, the target reference (quote id), and the
covered session ids. The underlying query MUST be fixed to `EXCEPTION`; a
purchase in `PENDING_FULFILLMENT` or `ASSIGNED` MUST NOT appear. An empty
result set MUST return `200` with an empty page, never `404`.

#### Scenario: Admin lists purchases in EXCEPTION

- GIVEN physical purchases in `EXCEPTION`
- WHEN an admin calls the list endpoint
- THEN the response is `200` with results ordered oldest-first by `Payment.createdAt`
- AND each row carries `user_id`, `createdAt`, amount, currency, target reference, and session ids

#### Scenario: A non-EXCEPTION purchase does not appear

- GIVEN one purchase `EXCEPTION` and one `ASSIGNED` (or `PENDING_FULFILLMENT`)
- WHEN an admin calls the list endpoint
- THEN only the `EXCEPTION` purchase appears in the results

#### Scenario: Empty result is a 200, not a 404

- GIVEN no purchase is currently `EXCEPTION`
- WHEN an admin calls the list endpoint
- THEN the response is `200` with an empty page

### Requirement: Zero-session EXCEPTION rows are never dropped (D7)

A purchase built directly at `EXCEPTION` with an empty session list (#238's
`Purchase.exception(paymentId, sessionIds)` path, e.g. a payment whose target
never resolved to a schedulable session) MUST appear in the list with an
empty session array. It MUST NOT be filtered out and MUST NOT cause an error.

#### Scenario: Zero-session EXCEPTION row is listed with an empty array

- GIVEN a purchase in `EXCEPTION` with no covered sessions
- WHEN an admin calls the list endpoint
- THEN that purchase appears in the results with an empty session array
- AND the response is `200`

### Requirement: Page size is capped, never clamped

The page size MUST NOT exceed 50; a request for a larger page MUST be
rejected with `400` and return no results. When `size` is absent, the
endpoint MUST default to 20.

#### Scenario: Page size above 50 is rejected

- GIVEN a request with `size=51` or higher
- WHEN an admin calls the list endpoint
- THEN the response is `400`, and no results are returned

#### Scenario: Absent size defaults to 20

- GIVEN a request with no `size` parameter
- WHEN an admin calls the list endpoint
- THEN the query is evaluated with a page size of 20

### Requirement: Access is restricted to ADMIN

The endpoint MUST be available only to `ROLE_ADMIN`. An authenticated caller
without that role MUST receive `403`. An anonymous caller MUST receive `401`
or `403`, matching the convention already established by the sibling
`/api/v1/admin/billing/payments` list endpoint.

#### Scenario: Non-admin is rejected

- GIVEN an authenticated caller without `ROLE_ADMIN`
- WHEN they call the list endpoint
- THEN the response is `403`

#### Scenario: Anonymous caller is rejected

- GIVEN no authentication credential
- WHEN the list endpoint is called
- THEN the response is `401` or `403`

### Requirement: Row content excludes reason and buyer email

A row MUST NOT include a `reason` field: `purchase-exception-notification`'s
existing "Reason is payload-only, never persisted" requirement means no
`reason` column exists on `billing_purchases` to read from. A row MUST
identify the buyer only by `user_id`; resolving that id to an email address
is explicitly out of scope for this capability.

#### Scenario: A row carries no reason and no buyer email

- GIVEN a purchase in `EXCEPTION`
- WHEN an admin calls the list endpoint
- THEN the row contains no `reason` field
- AND the row identifies the buyer only by `user_id`, with no email field

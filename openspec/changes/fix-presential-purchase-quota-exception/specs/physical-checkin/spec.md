# Delta for physical-checkin

## ADDED Requirements

### Requirement: Paid presential students are eligible for the check-in gate

A student whose physical payment has reached `billing_purchases.status = ASSIGNED` for the target session MUST be served by `POST /api/v1/physical/sessions/{sessionId}/access-qr` without `403 CAPACITY_ASSIGNMENT_REQUIRED`, on equal footing with any other student who holds a confirmed `physical_capacity_assignments` row. The existing `QR Credential Issuance Gate` requirement still applies in full.

#### Scenario: Paying student passes the confirmed-assignment check

- GIVEN a `billing_purchases` row with `status = ASSIGNED` for `(sessionId, studentId)` and an active session inside its check-in window
- WHEN the student requests an access QR via `POST /api/v1/physical/sessions/{sessionId}/access-qr`
- THEN the system returns 200 with `qrCredentials`, `expiresAt`, and `refreshAfterSeconds=30`
- AND the response is indistinguishable from one served by the historical non-payment path

#### Scenario: Non-paying student without an Assignment still receives 403

- GIVEN a student who holds no `billing_purchases` row with `status = ASSIGNED` for `(sessionId, studentId)` and no `physical_capacity_assignments` row for that pair
- WHEN the student requests an access QR via `POST /api/v1/physical/sessions/{sessionId}/access-qr`
- THEN the system returns 403 `CAPACITY_ASSIGNMENT_REQUIRED` exactly as the canonical gate specifies

#### Scenario: EXCEPTION residual still produces 403 — paid student never gets a seat

- GIVEN a `billing_purchases` row with `status = EXCEPTION` for `(sessionId, studentId)` and zero `physical_capacity_assignments` rows for that pair
- WHEN the student requests an access QR via `POST /api/v1/physical/sessions/{sessionId}/access-qr`
- THEN the system returns 403 `CAPACITY_ASSIGNMENT_REQUIRED`
- AND no QR credential is issued (the `EXCEPTION` residual is preserved end-to-end)

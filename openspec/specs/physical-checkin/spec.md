# Physical Check-in Specification

## Purpose

Turn a student's ephemeral QR credential into exactly one auditable `physical_attendances` row at the door, rejecting unauthorized readers, malformed/expired/wrong-session credentials, cancelled sessions, out-of-window scans, and students without a confirmed capacity assignment — without ever creating a duplicate row on re-scan.

## Requirements

### Requirement: QR Credential Issuance Gate

The system MUST issue an ephemeral, signed QR credential via `POST /api/v1/physical/sessions/{sessionId}/access-qr` only when, checked in order: the session exists, is not `CANCELLED`, is within its check-in window (`[scheduledAt - sessionWindowBefore, scheduledAt + sessionWindowAfter]`), and the student holds a confirmed capacity assignment for it. On success the response MUST include `qrCredentials`, `expiresAt`, and `refreshAfterSeconds=30`.

| Check (in order) | Failure → Response |
|---|---|
| Session exists | 404 `SESSION_NOT_FOUND` |
| Session not `CANCELLED` | 403 `SESSION_CANCELLED` |
| Within check-in window | 403 `OUTSIDE_CHECK_IN_WINDOW` |
| Confirmed capacity assignment | 403 `CAPACITY_ASSIGNMENT_REQUIRED` |

#### Scenario: Issue a credential for an eligible student

- GIVEN an active session inside its check-in window and a confirmed assignment for the student
- WHEN the student requests an access QR
- THEN the system returns 200 with `qrCredentials`, `expiresAt`, and `refreshAfterSeconds=30`

#### Scenario: Cancelled session blocks issuance ahead of window and assignment

- GIVEN a session with status `CANCELLED`, even where the student still has a confirmed assignment on record
- WHEN the student requests an access QR
- THEN the system returns 403 `SESSION_CANCELLED` without evaluating the window or the assignment

### Requirement: Ordered, Redis-Free Check-in Rejection

The system MUST validate `POST /api/v1/physical/sessions/{sessionId}/check-ins` (QR variant) through cheap checks, strictly in this order, before any Redis call: registry device authentication → credential shape → session binding → signature → expiry → session exists/not-cancelled/in-window → confirmed assignment → existing-attendance idempotency. Only a request that clears every gate may reach a Redis lock. A failed device authentication MUST short-circuit every later gate (a bad device with a malformed credential returns the device error).
(Previously: gate 1 was a constant-time compare against one shared device token; it returned only `INVALID_DEVICE_TOKEN`.)

| Step | Failure → Response |
|---|---|
| Registry device authentication (UUID pre-check, secret proof, then status/expiry) | 401 `INVALID_DEVICE_TOKEN`, 401 `DEVICE_REVOKED`, 401 `DEVICE_EXPIRED` |
| Credential shape (`qr:` prefix, 4 segments, UUID claims, non-blank `jti`) | 400 `INVALID_QR_CREDENTIAL` |
| Embedded `sessionId` == path `sessionId` | 400 `INVALID_QR_CREDENTIAL` |
| Recomputed signature matches | 400 `INVALID_QR_CREDENTIAL` |
| Not expired | 410 `EXPIRED_QR_CREDENTIAL` |
| Session exists | 404 `SESSION_NOT_FOUND` |
| Session not `CANCELLED` | 403 `SESSION_CANCELLED` |
| Within check-in window | 403 `OUTSIDE_CHECK_IN_WINDOW` |
| Confirmed capacity assignment | 403 `CAPACITY_ASSIGNMENT_REQUIRED` |

#### Scenario: A flood of malformed or unauthorized scans never reaches Redis

- GIVEN a scan that fails any cheap check above (unknown/revoked/expired device, wrong secret, tampered credential, wrong session, expired token)
- WHEN the check-in request is processed
- THEN the system returns the matching error response and acquires zero Redis locks

#### Scenario: Cancelled session blocks check-in despite a confirmed assignment

- GIVEN a session with status `CANCELLED` and a confirmed assignment still on record for the student (cancellation does not retroactively delete assignments)
- WHEN a valid, unexpired, correctly-signed credential for that session is scanned
- THEN the system returns 403 `SESSION_CANCELLED` before checking the window or the assignment

#### Scenario: Device failure precedes credential failure

- GIVEN a wrong device secret and a malformed credential
- WHEN the check-in is processed
- THEN the system returns 401 `INVALID_DEVICE_TOKEN`, not 400 `INVALID_QR_CREDENTIAL`

### Requirement: Idempotent Redemption

When an `Attendance` already exists for `(sessionId, studentId)`, the system MUST return 200 with that existing record and MUST NOT acquire any Redis lock or attempt a second insert.

#### Scenario: Re-scan of an already-checked-in student

- GIVEN a student who already has an attendance row for this session
- WHEN a valid credential is scanned for them again
- THEN the system returns 200 with the existing attendance and makes zero Redis calls

### Requirement: Locked, Fail-Closed Insertion

After all cheap checks pass and no prior attendance exists, the system MUST acquire the lock `checkin:qr:{jti}` then `checkin:attendance:{sessionId}:{studentId}` before inserting the `Attendance` row. It MUST fail closed (reject the check-in) if Redis is unreachable during either acquisition, and MUST NOT perform lock compare-and-delete compensation — a lock left behind by a failed insert expires by TTL (accepted product decision).

#### Scenario: Concurrent scan of the same credential is rejected

- GIVEN two simultaneous check-in requests carrying the same `jti`
- WHEN the second request attempts to acquire the QR lock
- THEN the system returns 409 `ALREADY_PROCESSING` and performs no insert

#### Scenario: Redis unavailable fails closed

- GIVEN Redis is unreachable when a lock acquisition is attempted
- WHEN a check-in request reaches the locking step
- THEN the system returns 503 `CHECK_IN_DEGRADED` with header `Retry-After: 30` and performs no insert

### Requirement: Schema-Level Idempotency Guard

The `physical_attendances` table MUST enforce `UNIQUE (session_id, user_id)` as the last-resort guard against duplicate rows, independent of the application-level lock and idempotency checks above.

#### Scenario: Database rejects a duplicate despite a lock race

- GIVEN two inserts for the same `(sessionId, userId)` that both cleared their respective locks
- WHEN both `INSERT` statements execute
- THEN only one row persists and the second is rejected by the unique constraint

### Requirement: RFC 9457 Error Responses

Every rejection from either endpoint MUST return an `application/problem+json` body with the matching HTTP status and a stable, machine-readable `code` property.

#### Scenario: Any rejection carries a machine-readable error code

- GIVEN any of the failure conditions listed in the requirements above
- WHEN the endpoint rejects the request
- THEN the response body is RFC 9457 Problem Details containing the matching `code`

### Requirement: Check-in type is a two-valued discriminator

The check-in request's `type` field MUST accept exactly two literal values, `QR` and `MANUAL`,
selecting between the two check-in variants. Any other value MUST be rejected with
`400 INVALID_REQUEST`.

#### Scenario: An unrecognized type value is rejected

- GIVEN a check-in request with `type: "SOMETHING_ELSE"`
- WHEN the request is submitted
- THEN the system returns 400 `INVALID_REQUEST`

### Requirement: QR variant rejects MANUAL-only fields

A check-in request with `type: "QR"` MUST reject the presence of `studentId` (a MANUAL-only
field) with `400 INVALID_REQUEST`, evaluated before any use-case step runs — mirroring the MANUAL
variant's own rejection of QR-only fields (see the new `physical-manual-checkin` capability in
this change).

#### Scenario: QR request carrying studentId is rejected

- GIVEN a check-in request with `type: "QR"` and its QR credential fields
- WHEN the request additionally includes `studentId`
- THEN the system returns 400 `INVALID_REQUEST` before any use-case validation runs

### Requirement: Registry authentication of QR check-in

For `type: "QR"`, the request body fields `deviceId` (a registered device's UUID) and `deviceToken` (that device's raw secret, as shown once at registration or rotation) MUST authenticate the reader against the registry. The `ACTIVE`, unexpired device whose stored secret matches `deviceToken` is the authenticated device. The legacy shared secret MUST NOT authenticate any request. The `deviceId`/`deviceToken` fields stay in the JSON body; no header is introduced. Blank/absent `deviceId` or `deviceToken` keeps the existing request-validation rejection (unchanged).

#### Scenario: Registered active device checks in

- GIVEN a registered `ACTIVE` device with no expiry (or `expiresAt` in the future) and a valid credential
- WHEN the reader posts `deviceId=<its UUID>` and `deviceToken=<its secret>`
- THEN device authentication passes and the remaining gates run as before

#### Scenario: Legacy shared secret is rejected

- GIVEN a request carrying a non-registry `deviceId` (for example `reader-1`) and the former shared secret
- WHEN the check-in is processed
- THEN the system returns 401 `INVALID_DEVICE_TOKEN`

### Requirement: Indistinguishable INVALID_DEVICE_TOKEN rejections

The system MUST return 401 `INVALID_DEVICE_TOKEN` for (a) a `deviceId` that is a valid UUID with no registered device, (b) a `deviceId` that is not a UUID, and (c) a registered device with a wrong `deviceToken`. A non-UUID `deviceId` MUST be rejected before any database access and MUST NOT yield 400. Cases (a), (b) and (c) MUST be indistinguishable to the caller: same status, `code`, title and detail (no existence oracle).

#### Scenario: Unknown, malformed and wrong-secret ids look identical

- GIVEN an unregistered UUID, the id `reader-1`, and a registered device with a wrong secret
- WHEN each is posted
- THEN each response is 401 `INVALID_DEVICE_TOKEN` with an identical body (excluding correlation fields)

#### Scenario: Non-UUID id never touches the database

- GIVEN `deviceId=not-a-uuid`
- WHEN the check-in is processed
- THEN the response is 401 `INVALID_DEVICE_TOKEN` (not 400)
- AND the device repository is not queried

### Requirement: Revoked and expired devices are rejected only after the secret is proven

The system MUST return 401 `DEVICE_REVOKED` when the proven device is `REVOKED`, and 401 `DEVICE_EXPIRED` when it is `ACTIVE` and `expiresAt <= now` (inclusive; a null `expiresAt` never expires). If the device is both revoked and past expiry, `DEVICE_REVOKED` MUST win. These codes MUST NOT be returned unless `deviceToken` matched the stored secret; otherwise the response is `INVALID_DEVICE_TOKEN`. An expired device is not recoverable by rotation; it requires re-registration.

#### Scenario: Revoked device with correct secret

- GIVEN a `REVOKED` device and its correct secret
- WHEN it checks in
- THEN the system returns 401 `DEVICE_REVOKED`

#### Scenario: Expired device, inclusive boundary

- GIVEN an `ACTIVE` device with `expiresAt` equal to the current instant and its correct secret
- WHEN it checks in
- THEN the system returns 401 `DEVICE_EXPIRED`

#### Scenario: Revoked wins over expired

- GIVEN a `REVOKED` device whose `expiresAt` is in the past, and its correct secret
- WHEN it checks in
- THEN the system returns 401 `DEVICE_REVOKED`

#### Scenario: Revoked or expired device with wrong secret reveals nothing

- GIVEN a `REVOKED` (or expired) device and a wrong secret
- WHEN it checks in
- THEN the system returns 401 `INVALID_DEVICE_TOKEN`

### Requirement: DEVICE_REVOKED has context-specific statuses

The code `DEVICE_REVOKED` MUST map to 401 on `POST /api/v1/physical/sessions/{sessionId}/check-ins` and MUST keep mapping to 409 on the admin rotate-secret-after-revoke endpoint (`physical-device-management`, unchanged). Both statuses MUST be documented in the OpenAPI contract.

#### Scenario: Same code, two statuses

- GIVEN a revoked device
- WHEN it checks in, and separately an ADMIN rotates its secret
- THEN check-in returns 401 `DEVICE_REVOKED` and rotation returns 409 `DEVICE_REVOKED`

### Requirement: Attendance records the authenticated device

For a successful QR check-in, `Attendance.device_id` MUST store the authenticated device's UUID (canonical string form), not the submitted free text. Historical rows MUST keep their existing free-text values untouched. MANUAL check-in behavior (including `device_id` holding the actor's `userId`) MUST be unchanged. No schema change.

#### Scenario: UUID is persisted

- GIVEN a registered device checks in successfully
- WHEN the attendance row is inserted
- THEN `device_id` equals that device's UUID

#### Scenario: Manual check-in unaffected

- GIVEN a valid MANUAL check-in with no `deviceId`/`deviceToken`
- WHEN it is processed
- THEN no device authentication runs and `device_id` holds the actor's `userId`

### Requirement: Device rejections are observable (locked contract)

Every device-gate rejection MUST emit exactly one structured `WARN` log and increment one counter, by reason: `unknown_or_invalid` (unknown id, non-UUID id, wrong secret), `revoked`, `expired`. Successful or later-gate failures MUST emit neither. The log and counter MUST be emitted through an application out-port; Micrometer and SLF4J types appear only in infrastructure. Locked literals, enforced by a test that fails on drift:

| Element | Value |
|---|---|
| Log level / marker prefix | `WARN` / `alarm=physical_checkin_device_rejected` |
| Log fields | `reason=<reason>` `deviceId=<uuid or none>` |
| Counter name / tag key | `physical.checkin.device.rejected` / `reason` |
| Tag values | `unknown_or_invalid`, `revoked`, `expired` |

The secret MUST NEVER be logged. `deviceId` MUST be logged only when the submitted value is a valid UUID (otherwise `none`; the raw non-UUID text is never logged). `deviceId` MUST NOT be a metric tag.

#### Scenario: One log and one increment per rejection

- GIVEN a revoked device checks in with its correct secret
- WHEN the rejection occurs
- THEN exactly one WARN line with `reason=revoked` and its UUID is written
- AND counter `physical.checkin.device.rejected{reason=revoked}` increments by 1

#### Scenario: Non-UUID id and secrets stay out of the log

- GIVEN `deviceId=reader-1` and some `deviceToken`
- WHEN the rejection occurs
- THEN the line has `reason=unknown_or_invalid` and `deviceId=none`
- AND neither the raw id nor the token appears in any log output

#### Scenario: Accepted check-in emits nothing

- GIVEN a valid device check-in
- WHEN it completes
- THEN no rejection log or counter increment occurs

### Requirement: Legacy shared-token property is retired

The property `app.physical.checkin.device-token` MUST have no effect on authentication. If it is present at startup (any profile), the system MUST log one `WARN` stating it is ignored (never its value) and MUST start normally. The former fail-fast on prod/staging profiles MUST be removed, and a missing property MUST NOT warn or fail.

#### Scenario: Property present

- GIVEN `app.physical.checkin.device-token` is set, including on `prod`
- WHEN the application starts
- THEN one WARN naming the ignored property is logged and startup succeeds
- AND requests using that value as `deviceToken` get 401 `INVALID_DEVICE_TOKEN`

#### Scenario: Property absent

- GIVEN the property is not set on `prod`
- WHEN the application starts
- THEN it starts with no property-related WARN

## Out of Scope

Redis compare-and-delete lock compensation (issue #38 escenario 6) — TTL expiry only, by product decision. Real HMAC signing — the MVP uses a deterministic placeholder format, tracked as a known follow-up risk, not a defect. The `MANUAL` check-in variant (deferred to issue #45), Android-side QR refresh, and attendance query/reporting endpoints.

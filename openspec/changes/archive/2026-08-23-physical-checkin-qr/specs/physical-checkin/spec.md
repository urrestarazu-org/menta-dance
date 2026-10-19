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

The system MUST validate `POST /api/v1/physical/sessions/{sessionId}/check-ins` (QR variant) through cheap checks, strictly in this order, before any Redis call: device token → credential shape → session binding → signature → expiry → session exists/not-cancelled/in-window → confirmed assignment → existing-attendance idempotency. Only a request that clears every gate may reach a Redis lock.

| Step | Failure → Response |
|---|---|
| Device token (constant-time compare) | 401 `INVALID_DEVICE_TOKEN` |
| Credential shape (`qr:` prefix, 4 segments, UUID claims, non-blank `jti`) | 400 `INVALID_QR_CREDENTIAL` |
| Embedded `sessionId` == path `sessionId` | 400 `INVALID_QR_CREDENTIAL` |
| Recomputed signature matches | 400 `INVALID_QR_CREDENTIAL` |
| Not expired | 410 `EXPIRED_QR_CREDENTIAL` |
| Session exists | 404 `SESSION_NOT_FOUND` |
| Session not `CANCELLED` | 403 `SESSION_CANCELLED` |
| Within check-in window | 403 `OUTSIDE_CHECK_IN_WINDOW` |
| Confirmed capacity assignment | 403 `CAPACITY_ASSIGNMENT_REQUIRED` |

#### Scenario: A flood of malformed or unauthorized scans never reaches Redis

- GIVEN a scan that fails any cheap check above (wrong device token, tampered credential, wrong session, expired token)
- WHEN the check-in request is processed
- THEN the system returns the matching error response and acquires zero Redis locks

#### Scenario: Cancelled session blocks check-in despite a confirmed assignment

- GIVEN a session with status `CANCELLED` and a confirmed assignment still on record for the student (cancellation does not retroactively delete assignments)
- WHEN a valid, unexpired, correctly-signed credential for that session is scanned
- THEN the system returns 403 `SESSION_CANCELLED` before checking the window or the assignment

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

## Out of Scope

Redis compare-and-delete lock compensation (issue #38 escenario 6) — TTL expiry only, by product decision. Real HMAC signing — the MVP uses a deterministic placeholder format, tracked as a known follow-up risk, not a defect. The `MANUAL` check-in variant (deferred to issue #45), Android-side QR refresh, device registry, and attendance query/reporting endpoints.

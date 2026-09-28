# Physical Manual Check-in Specification

## Purpose

Let a `RECEPTIONIST` (or `ADMIN`) record attendance for a physical session without a QR
credential — when the reader is offline, the student's phone is dead, or the QR expired —
reusing the QR flow's capacity-assignment, idempotency, and locking guarantees, while narrowing
"session active" to "not cancelled" so a receptionist can backfill attendance for an
already-elapsed session with no time limit.

## Requirements

### Requirement: MANUAL check-in role gate

The system MUST accept a MANUAL check-in (`POST /api/v1/physical/sessions/{sessionId}/check-ins`
with `type: "MANUAL"`) only from an authenticated `RECEPTIONIST` or `ADMIN`. Every other
authenticated role, and any anonymous caller, MUST receive `403 INSUFFICIENT_ROLE`.

#### Scenario: STUDENT is rejected on a MANUAL body

- GIVEN an authenticated caller with only the `STUDENT` role
- WHEN they submit a MANUAL check-in body
- THEN the system returns 403 `INSUFFICIENT_ROLE`

#### Scenario: INSTRUCTOR is rejected on a MANUAL body

- GIVEN an authenticated caller with only the `INSTRUCTOR` role
- WHEN they submit a MANUAL check-in body
- THEN the system returns 403 `INSUFFICIENT_ROLE`

#### Scenario: Anonymous caller is rejected on a MANUAL body

- GIVEN no authentication credential
- WHEN a MANUAL check-in body is submitted
- THEN the system returns 403 `INSUFFICIENT_ROLE`

### Requirement: MANUAL request is a validated discriminated union

A request with `type: "MANUAL"` MUST reject the presence of any QR-only field
(`qrCredentials`, `deviceId`, `deviceToken`) with `400 INVALID_REQUEST`, evaluated before any
use-case step runs.

#### Scenario: MANUAL body carrying a QR-only field is rejected

- GIVEN a check-in request with `type: "MANUAL"` and `studentId`
- WHEN the request additionally includes `qrCredentials` (or `deviceId`/`deviceToken`)
- THEN the system returns 400 `INVALID_REQUEST` before any use-case validation runs

### Requirement: Ordered MANUAL validation before insertion

The system MUST validate a MANUAL check-in strictly in this order: role → `studentId` existence
→ confirmed capacity assignment → session not `CANCELLED` → existing-attendance idempotency →
single Redis lock (`checkin:attendance:{sessionId}:{studentId}`, the same acquire-or-expire
pattern the QR flow already uses) → INSERT. Only the first failing check determines the response.

| Step (in order) | Failure → Response |
|---|---|
| Role is RECEPTIONIST or ADMIN | 403 `INSUFFICIENT_ROLE` |
| `studentId` exists | 404 `STUDENT_NOT_FOUND` |
| Confirmed capacity assignment | 403 `CAPACITY_ASSIGNMENT_REQUIRED` |
| Session status not `CANCELLED` | 409 `SESSION_NOT_ACTIVE` |

#### Scenario: Missing confirmed assignment is rejected

- GIVEN a known student with no confirmed capacity assignment for the session
- WHEN a RECEPTIONIST submits a MANUAL check-in for them
- THEN the system returns 403 `CAPACITY_ASSIGNMENT_REQUIRED`

#### Scenario: Unknown student is rejected

- GIVEN a `studentId` that does not exist
- WHEN a RECEPTIONIST submits a MANUAL check-in for it
- THEN the system returns 404 `STUDENT_NOT_FOUND`

#### Scenario: Cancelled session is rejected

- GIVEN a session with status `CANCELLED`
- WHEN a RECEPTIONIST submits a MANUAL check-in for a student with a confirmed assignment
- THEN the system returns 409 `SESSION_NOT_ACTIVE`

#### Scenario: Elapsed, non-cancelled session is accepted (retroactive backfill)

- GIVEN a session that already occurred, with status other than `CANCELLED`, and a confirmed
  assignment for the student
- WHEN a RECEPTIONIST submits a MANUAL check-in for that student, with no time limit since the
  session occurred
- THEN the system returns 201 `Created` — MANUAL does not consult `hasOccurred`, only the
  cancellation status (deliberate divergence from the QR variant, D8)

#### Scenario: Unknown student on a cancelled session yields STUDENT_NOT_FOUND, not SESSION_NOT_ACTIVE

- GIVEN a `studentId` that does not exist and a session with status `CANCELLED`
- WHEN a RECEPTIONIST submits a MANUAL check-in
- THEN the system returns 404 `STUDENT_NOT_FOUND`, because student existence is validated before
  the cancellation check

#### Scenario: Missing assignment on a cancelled session yields CAPACITY_ASSIGNMENT_REQUIRED, not SESSION_NOT_ACTIVE

- GIVEN a known student with no confirmed capacity assignment and a session with status
  `CANCELLED`
- WHEN a RECEPTIONIST submits a MANUAL check-in for that student
- THEN the system returns 403 `CAPACITY_ASSIGNMENT_REQUIRED`, because the assignment check
  precedes the cancellation check

### Requirement: Idempotent redemption

When an `Attendance` already exists for `(sessionId, studentId)`, the system MUST return 200 with
that existing record and MUST NOT acquire any Redis lock or attempt a second insert.

#### Scenario: Repeated MANUAL check-in for an already-checked-in student

- GIVEN a student who already has an attendance row for this session
- WHEN a RECEPTIONIST submits a MANUAL check-in for them again
- THEN the system returns 200 with the existing attendance and inserts no new row

### Requirement: Successful MANUAL check-in persists the receptionist as actor

On a first, fully valid MANUAL check-in, the system MUST insert exactly one `Attendance` row with
`kind = MANUAL` and `device_id` holding the acting `RECEPTIONIST`'s (or `ADMIN`'s) own `userId` —
not a device identifier — reflecting `device_id`'s broadened "actor" invariant (D2).

#### Scenario: RECEPTIONIST records a valid MANUAL check-in

- GIVEN an active, non-cancelled session and a student with a confirmed assignment
- WHEN a RECEPTIONIST submits a MANUAL check-in for that student
- THEN the system returns 201 `Created` with the stored attendance

#### Scenario: ADMIN also succeeds on a MANUAL check-in

- GIVEN the same preconditions as above
- WHEN an `ADMIN` submits a MANUAL check-in for that student
- THEN the system returns 201 `Created` with the stored attendance

#### Scenario: Persisted device_id holds the receptionist's userId

- GIVEN a RECEPTIONIST with a known `userId` performs a valid MANUAL check-in
- WHEN the attendance row is inserted
- THEN `device_id` on that row equals the RECEPTIONIST's `userId`, not any device identifier

## Out of Scope

The QR flow's own eleven-step ordering, its `SESSION_CANCELLED`/`OUTSIDE_CHECK_IN_WINDOW`
statuses, and its two-lock pattern are untouched — MANUAL is a separate, shorter path (see the
`physical-checkin` delta in this change). The `PhysicalDevice` registry,
`DEVICE_EXPIRED`/`DEVICE_REVOKED` enforcement, BFF/Android front-desk UI, bulk/roster check-in,
and undoing a check-in are not covered.

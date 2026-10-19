# Physical Attendance History Specification

## Purpose

Let a student read their own assigned physical sessions and attendance for one calendar
month, and let `ADMIN`/`INSTRUCTOR` read another student's month under a scoped access rule,
without exposing a second write path or a denormalized source of truth.

## Requirements

### Requirement: Self attendance for a calendar month

An authenticated caller MUST be able to read their own attendance via
`GET /api/v1/physical/attendance/me?month=YYYY-MM&includeAbsent=true|false`. The response MUST
contain `scheduledSessionCount`, `attended`, `absent`, `attendanceRate`, and `sessions[]`.
`scheduledSessionCount` MUST equal the count of the caller's `physical_capacity_assignments`
rows whose joined session's `scheduled_at` falls in `[monthStart, monthNext)` for the requested
month. Each session row MUST include date, course, instructor, and attendance status. Ordering
MUST be `scheduled_at` ASC, `sessionId` ASC as tie-break. An anonymous request MUST return
`401`.

#### Scenario: Student reads a five-session month

- GIVEN an authenticated student with 5 assignments in `2026-09`, 4 attended and 1 absent
- WHEN they call `GET /api/v1/physical/attendance/me?month=2026-09`
- THEN the response is `200` with `scheduledSessionCount: 5`, `attended: 4`, `absent: 1`

#### Scenario: Student reads a four-session month

- GIVEN an authenticated student with 4 assignments in `2026-10`, all attended
- WHEN they call `GET /api/v1/physical/attendance/me?month=2026-10`
- THEN the response is `200` with `scheduledSessionCount: 4`, `attended: 4`, `absent: 0`

#### Scenario: Anonymous request is rejected

- GIVEN no authentication credential
- WHEN a request is made to `GET /api/v1/physical/attendance/me?month=2026-09`
- THEN the response is `401`

#### Scenario: A student cannot obtain another student's data via the self endpoint

- GIVEN two students A (authenticated caller) and B
- WHEN A calls `GET /api/v1/physical/attendance/me` with any parameter referencing B
- THEN the response only ever reflects A's own assignments, never B's

### Requirement: Denominator counts assignments, not purchase coverage windows

`scheduledSessionCount` MUST be derived purely from assignment rows whose session falls in the
requested month, never from a purchase's rolling N-session coverage window. A `MONTHLY`
purchase confirmed mid-month MUST be counted identically to an `INDIVIDUAL` purchase: only by
the calendar month of each assigned session.

#### Scenario: A mid-month MONTHLY purchase splits across two monthly views

- GIVEN a `MONTHLY` purchase confirmed on 2026-09-20 producing assignments in both September
  and October
- WHEN the student reads `2026-09` and then `2026-10`
- THEN each month's `scheduledSessionCount` reflects only that month's assigned sessions
- AND neither month's count equals the purchase's own coverage-window session count

### Requirement: includeAbsent controls the session list, never the aggregate

`includeAbsent=false` (default) MUST omit unattended assignments from `sessions[]` entirely.
`includeAbsent=true` MUST include them with `status: ABSENT`. In both cases,
`scheduledSessionCount`, `attended`, `absent`, and `attendanceRate` MUST be computed over the
full assignment set, unaffected by the flag.

#### Scenario: includeAbsent=false omits unattended rows but keeps aggregates intact

- GIVEN a month with 5 assignments, 4 attended and 1 absent
- WHEN the student calls the endpoint with `includeAbsent=false`
- THEN `sessions[]` contains only the 4 attended rows
- AND `scheduledSessionCount` is 5, `attended` is 4, `absent` is 1

#### Scenario: includeAbsent=true lists the absent row

- GIVEN the same month
- WHEN the student calls the endpoint with `includeAbsent=true`
- THEN `sessions[]` contains all 5 rows, including one with `status: ABSENT`
- AND the aggregate numbers are unchanged from the `includeAbsent=false` call

### Requirement: Status is derived live, never stored

Each session's `status` MUST be `ATTENDED` when a `physical_attendances` row exists for that
assignment, otherwise `ABSENT`. No stored status column MUST back this value. `recordedAt` MUST
be present for `ATTENDED` rows and `null` for `ABSENT` rows.

#### Scenario: An attended session reports recordedAt

- GIVEN an assignment with a matching `physical_attendances` row
- WHEN the session is read
- THEN `status` is `ATTENDED` and `recordedAt` is the check-in timestamp

#### Scenario: An absent session has a null recordedAt

- GIVEN an assignment with no matching `physical_attendances` row
- WHEN the session is read
- THEN `status` is `ABSENT` and `recordedAt` is `null`

### Requirement: Empty month returns a zeroed 200, never 404

A month with zero assignments MUST return `200` with `scheduledSessionCount: 0`, empty
`sessions[]`, and `attendanceRate: 0.00`.

#### Scenario: Student with no assignments in the month

- GIVEN an authenticated student with zero assignments in `2026-11`
- WHEN they call `GET /api/v1/physical/attendance/me?month=2026-11`
- THEN the response is `200` with `scheduledSessionCount: 0`, `sessions: []`, `attendanceRate: 0.00`

### Requirement: Attendance rate rounding

`attendanceRate` MUST equal `attended / scheduledSessionCount`, computed to scale 2 with
`HALF_UP` rounding, and MUST be `0.00` when `scheduledSessionCount` is 0.

#### Scenario: Rate rounds HALF_UP at scale 2

- GIVEN a month with `attended: 4` and `scheduledSessionCount: 5`
- WHEN the aggregate is computed
- THEN `attendanceRate` is `80.00`

#### Scenario: Rate is zero with no denominator

- GIVEN a month with `scheduledSessionCount: 0`
- WHEN the aggregate is computed
- THEN `attendanceRate` is `0.00`

### Requirement: Sessions cancelled before quoting are naturally absent

A physical session cancelled before it was ever quoted MUST NOT appear in `sessions[]` and MUST
NOT affect `scheduledSessionCount`, because no assignment row was ever created for it. No
`SessionStatus` filter MUST be applied.

#### Scenario: A pre-quote cancellation never produced an assignment

- GIVEN a session cancelled before any student quoted it
- WHEN a student's month containing that session's date is read
- THEN the cancelled session does not appear in `sessions[]`
- AND it is not counted in `scheduledSessionCount`

### Requirement: Elevated read for ADMIN and INSTRUCTOR

`ADMIN` and `INSTRUCTOR` MUST be able to read another student's attendance via
`GET /api/v1/admin/physical/attendance/{studentId}?month=YYYY-MM&includeAbsent=true|false`.
`ADMIN` MUST see the student's unrestricted month — all sessions and aggregates, identical in
shape to the self endpoint. A `STUDENT` caller MUST be rejected with `403`. An anonymous
request MUST return `401`.

#### Scenario: Admin reads any student's month unrestricted

- GIVEN an authenticated `ADMIN` and a student with 5 assignments across 3 different courses
- WHEN the admin calls `GET /api/v1/admin/physical/attendance/{studentId}?month=2026-09`
- THEN the response is `200` with all 5 sessions and aggregates covering the full set

#### Scenario: A STUDENT caller cannot reach the elevated endpoint

- GIVEN an authenticated caller with only the `STUDENT` role
- WHEN they call `GET /api/v1/admin/physical/attendance/{studentId}?month=2026-09`
- THEN the response is `403`

#### Scenario: Anonymous request to the elevated endpoint is rejected

- GIVEN no authentication credential
- WHEN a request is made to `GET /api/v1/admin/physical/attendance/{studentId}?month=2026-09`
- THEN the response is `401`

### Requirement: Instructor elevated read is scoped to owned courses

An `INSTRUCTOR` caller's elevated read MUST be filtered server-side to sessions whose course's
`professorId` equals the caller's id. `sessions[]` MUST contain only sessions from courses the
instructor teaches, and `scheduledSessionCount`, `attended`, `absent`, and `attendanceRate` MUST
be computed over that same filtered set, never the student's full cross-course history. The
filter MUST be applied at the query layer (joined against the course's `professorId`), never
post-filtered in application code.

#### Scenario: Instructor sees only their own courses' sessions and aggregates

- GIVEN an `INSTRUCTOR` teaching course A, and a student with 3 assignments in course A and 2
  in course B (taught by someone else)
- WHEN the instructor reads that student's month
- THEN `sessions[]` contains only the 3 course-A sessions
- AND `scheduledSessionCount`, `attended`, and `absent` reflect only those 3 sessions

#### Scenario: Instructor sees zero rows for a student in a course they don't teach

- GIVEN an `INSTRUCTOR` teaching no course the target student is assigned to
- WHEN the instructor reads that student's month
- THEN `sessions[]` is empty and every aggregate number is 0

### Requirement: Elevated read does not leak student existence to an instructor

A student who is not assigned to any course the instructor teaches MUST yield the same empty
`200` shape (`scheduledSessionCount: 0`, `sessions: []`, `attendanceRate: 0.00`) as a student
who has zero assignments that month at all. The instructor's elevated read MUST NOT distinguish
these two cases through status code or response shape.

#### Scenario: Non-existent overlap and genuinely empty month look identical

- GIVEN student X (not enrolled in any course the instructor teaches) and student Y (a real
  student with zero physical assignments that month)
- WHEN the instructor reads each student's month via the elevated endpoint
- THEN both responses are `200` with `scheduledSessionCount: 0`, `sessions: []`,
  `attendanceRate: 0.00`, indistinguishable from each other

### Requirement: Month boundaries resolve in the configured local zone

Month boundaries (`[monthStart, monthNext)`) MUST be derived from `YearMonth` in the zone
configured by `physical.attendance.zone-id` (default `America/Argentina/Buenos_Aires`), never
in UTC. A session scheduled late in the local evening near a month boundary MUST be attributed
to the calendar month that boundary falls in locally, not the UTC-shifted month.

#### Scenario: A late-evening local session lands in the correct local month

- GIVEN a session scheduled at `2026-09-30T23:30:00-03:00` (local time in the configured zone,
  which is `2026-10-01T02:30:00Z` in UTC)
- WHEN the student reads `2026-09`
- THEN the session is included in September's `sessions[]` and count
- AND it is excluded from October's `sessions[]` and count

### Requirement: Bounded, untruncated response for one month

Neither endpoint MUST paginate. A full calendar month's assignments MUST be returned complete
and untruncated in a single response.

#### Scenario: A full month returns without truncation

- GIVEN a student with assignments filling every course session offered in one month
- WHEN the month is read via either endpoint
- THEN `sessions[]` contains every one of those assignments in a single response with no
  page/cursor parameter and no truncation

# Delta for Physical Attendance History

## MODIFIED Requirements

### Requirement: Self attendance for a calendar month

An authenticated caller MUST be able to read their own attendance via
`GET /api/v1/physical/attendance/me?month=YYYY-MM&includeAbsent=true|false`. The response MUST
contain `scheduledSessionCount`, `attended`, `absent`, `attendanceRate`, and `sessions[]`.
`scheduledSessionCount` MUST equal the count of the caller's `physical_capacity_assignments`
rows whose joined session's `scheduled_at` falls in `[monthStart, monthNext)` for the requested
month. Each session row MUST include date, course, instructor, attendance status, and `kind`
(`QR` or `MANUAL`, the origin of the recorded attendance). `kind` MUST be present for `ATTENDED`
rows and MUST be `null` or omitted for `ABSENT` rows. Ordering MUST be `scheduled_at` ASC,
`sessionId` ASC as tie-break. An anonymous request MUST return `401`. The OpenAPI contract MUST
document `kind` as an optional nullable enum `[QR, MANUAL]`.
(Previously: session rows carried no attendance origin; `kind` did not exist in the response.)

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

#### Scenario: Attended rows expose their origin

- GIVEN a student with one attendance recorded by QR and one recorded manually in `2026-09`
- WHEN they call `GET /api/v1/physical/attendance/me?month=2026-09`
- THEN the two `sessions[]` rows carry `kind: QR` and `kind: MANUAL` respectively
- AND `scheduledSessionCount`, `attended`, `absent`, `attendanceRate` and the row ordering are unchanged

#### Scenario: Absent rows have no origin

- GIVEN a month with one absent assignment
- WHEN the student calls the endpoint with `includeAbsent=true`
- THEN the `ABSENT` row has `kind` null or omitted

### Requirement: Status is derived live, never stored

Each session's `status` MUST be `ATTENDED` when a `physical_attendances` row exists for that
assignment, otherwise `ABSENT`. No stored status column MUST back this value. `recordedAt` and
`kind` MUST be present for `ATTENDED` rows (`kind` read from the attendance row's stored kind)
and MUST be `null` or omitted for `ABSENT` rows.
(Previously: only `recordedAt` was specified as present for `ATTENDED` and `null` for `ABSENT`.)

#### Scenario: An attended session reports recordedAt

- GIVEN an assignment with a matching `physical_attendances` row
- WHEN the session is read
- THEN `status` is `ATTENDED` and `recordedAt` is the check-in timestamp

#### Scenario: An attended session reports its kind

- GIVEN an assignment with a matching `physical_attendances` row of kind `MANUAL`
- WHEN the session is read
- THEN `status` is `ATTENDED` and `kind` is `MANUAL`

#### Scenario: An absent session has a null recordedAt

- GIVEN an assignment with no matching `physical_attendances` row
- WHEN the session is read
- THEN `status` is `ABSENT`, `recordedAt` is `null` and `kind` is `null` or omitted

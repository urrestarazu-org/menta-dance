# Proposal: Physical attendance history

**Issue**: #39 (US-PHYSICAL-002, "Ver historial de asistencia") · **Input**: `exploration.md`

## Intent

A student who buys physical classes has no way to see what they were assigned or whether
they showed up. `physical_capacity_assignments` records the seat and `physical_attendances`
records the scan, but neither is readable per student: both repositories expose only
session-scoped lookups (`existsConfirmedAssignment`, `findBySessionIdAndUserId`). The
student cannot answer "how many classes did I have this month, and how many did I attend?",
and support has to answer it by hand. This change delivers the read API only.

## Scope

### In Scope

- `GET /api/v1/physical/attendance/me?month=YYYY-MM&includeAbsent=true|false` — the acting
  student's own attendance for one calendar month: per-session rows (date, course, instructor,
  attendance status) plus the aggregate counts from the issue's example response.
- `GET /api/v1/admin/physical/attendance/{studentId}?month=YYYY-MM&includeAbsent=true|false` —
  elevated read of *another* student's attendance for one month. `ADMIN` may read any student.
  `INSTRUCTOR` may read a student's attendance **only for sessions of courses that instructor
  teaches** — the response's `sessions[]` is filtered to those courses, and the aggregate counts
  (`scheduledSessionCount`, `attended`, `absent`, `attendanceRate`) are computed over that same
  filtered set, never the student's full cross-course history. See D2.
- New per-student range-query port methods on `PhysicalCapacityAssignmentRepository` and
  `AttendanceRepository`, plus their JPA adapters — parameterized by an optional course-id filter
  for the instructor-scoped case.
- New `GetPhysicalAttendanceHistoryUseCase` returning a lightweight projection, modelled on
  `GetSubscriptionHistoryUseCase`, taking an explicit `AttendanceViewer` (self / admin / instructor
  scoped to their own courses) rather than a bare user id, so the scoping rule lives in one place.
- Explicit method-scoped `.authenticated()` matcher for the self endpoint, and an
  `hasAnyRole("ADMIN","INSTRUCTOR")` matcher for the admin endpoint, in `SecurityConfig` (see
  Security).
- `V21__*.sql` adding `student_id` / `user_id` indexes to both tables.
- OpenAPI contract update (issue DoD).

### Out of Scope

- Attendance history across arbitrary date ranges or multiple months; only `?month=` exists here.
- Any change to the check-in write path, `Attendance.record(...)`, or the assignment lifecycle.
- Cancellation policy for sessions cancelled *after* quoting — explicitly deferred by the issue.
- BFF or Android surfaces.
- A denormalized monthly snapshot table (exploration approach 2, rejected — second source of truth).

## Settled product decisions

| # | Topic | Decision |
|---|---|---|
| D1 | Denominator | `scheduledSessionCount` = count of the student's `physical_capacity_assignments` rows whose joined `physical_sessions.scheduled_at` falls in `[monthStart, monthNext)`. See "Decision record". |
| D2 | Authorization | Two endpoints. Self: acting user id from the token (`actingUserId(authentication)`), mirroring `SubscriptionController.historyOwn()`. Elevated: `ADMIN` reads any student unrestricted; `INSTRUCTOR` reads a student but the result is filtered to sessions of courses where `PhysicalCourse.professorId == actingUserId` — the same ownership check `PhysicalCoursePricingController`/`PricingNotOwnedException` already establishes. See "Decision record". |
| D3 | Pagination | No pagination. A calendar month is bounded (issue's own example: 4–5 sessions). See "Decision record". |
| D4 | `includeAbsent=false` | Default. The list contains **only** sessions with a recorded check-in; unattended assignments are omitted from `sessions[]` entirely. The aggregate block is unaffected by the flag: `scheduledSessionCount`, `attended`, `absent`, and `attendanceRate` are always computed over the full assignment set. |
| D5 | Status vocabulary | Per-session `status` is `ATTENDED` or `ABSENT`, derived live from the presence of a `physical_attendances` row. No stored status column. |
| D6 | Empty month | A month with zero assignments returns `200` with `scheduledSessionCount: 0`, empty `sessions[]`, and `attendanceRate: 0.00` — never `404`. |
| D7 | Rate rounding | `attendanceRate` = `attended / scheduledSessionCount`, scale 2, `HALF_UP`, `0.00` when the denominator is zero. |
| D8 | Cancelled sessions | A session cancelled before quoting never produced an assignment row, so it is naturally absent. No `SessionStatus` filter is added. |

### Response shape (contract sketch)

```json
{
  "period": "2026-09",
  "scheduledSessionCount": 5,
  "attended": 4,
  "absent": 1,
  "attendanceRate": 80.00,
  "sessions": [
    { "sessionId": "...", "scheduledAt": "2026-09-03T19:00:00Z",
      "courseName": "Salsa Intermedio", "instructorName": "Ana Pérez",
      "status": "ATTENDED", "recordedAt": "2026-09-03T18:52:11Z" }
  ]
}
```

`recordedAt` is null for `ABSENT` rows. Ordering: `scheduled_at` ASC, `sessionId` ASC as
deterministic tie-break.

## Decision record (interpretive calls a human should be able to override)

**D1 — the denominator.** The issue says the denominator comes "del snapshot mensual o de
pagos individuales". The exploration found that this phrasing does not match the code:
`PurchaseType.MONTHLY` is *not* calendar-aligned. `CoveragePlanner` counts forward
`scheduledSessionCount` sessions from the purchase's `confirmedAt`, a rolling N-session
window; neither `PhysicalCourseQuote` nor the assignment row stores a period. Two readings
were possible:

- **(a) query-derived** — count assignment rows whose session's `scheduled_at` is in the
  requested month;
- **(b) coverage-anchored** — count the purchase's own N-session window.

We choose **(a)**. Reasoning: the endpoint's contract is `?month=YYYY-MM`, and (b) has no
natural mapping onto a calendar month at all — a purchase confirmed on 2026-09-20 with 8
sessions straddles September and October, so "the coverage window for month 2026-09" is not
a well-defined quantity. (a) is the only reading the schema can actually answer, it treats
`MONTHLY` and `INDIVIDUAL` purchases identically once the assignment row exists, and it keeps
this change purely additive inside `api/physical` with **no new dependency on Billing's
coverage internals**. The accepted consequence: the number a student sees for a month is "what
I was assigned in that month", which for a mid-month MONTHLY purchase will split across two
monthly views and will not equal the purchase's `scheduledSessionCount`. If the product owner
wants "sessions remaining on my purchase", that is a different endpoint (Billing-owned), not
this one.

**D3 — pagination.** The DoD asks for "pruebas de paginación", but at `?month=` granularity
the result set is bounded by the sessions a student can be assigned in one month — single
digits in practice, and no `me/history` endpoint in this codebase paginates. Adding page/size
parameters would be unexercised API surface. We read the DoD line as anticipating a
multi-month or open-ended history that this endpoint does not offer, and satisfy it instead
with a **bounded-result test** asserting the full month is returned in one response with no
truncation. If the product owner intends genuine paging, that implies a date-range endpoint
and should be reopened here, not silently bolted on.

**D2 — elevated access.** Included in this change, per explicit product decision. The issue's
line "profesores y administradores requieren permisos explícitos" is read as a deliverable, not
just a constraint: `ADMIN` gets unrestricted read of any student's attendance. `INSTRUCTOR` does
**not** — the exploration found no precedent for an instructor seeing a student's *entire*
cross-course history, and this codebase already has a narrower, established answer to "what can
an instructor see": `PhysicalCourse.professorId` plus `PricingNotOwnedException`
(`PhysicalCoursePricingController`) restrict an instructor to courses they actually teach. This
change reuses that exact rule — an instructor's elevated read is filtered server-side to sessions
whose course's `professorId` equals the caller's id, both in the `sessions[]` list and in the
aggregate counts, so the numbers an instructor sees never leak a student's attendance in a course
taught by someone else. A student in zero of the instructor's courses yields an empty, `200`
result (D6's empty-month shape), not a `403` — the same anti-enumeration reasoning `PaymentNotFoundException`
already establishes elsewhere in this codebase: an instructor probing a `studentId` should not
learn from the status code alone whether that student exists or merely isn't in their course.

## Capabilities

### New Capabilities

- `physical-attendance-history`: how a student reads their own assigned sessions and
  attendance for a calendar month, the denominator and rate semantics, and the access rule.

### Modified Capabilities

- None. `physical-checkin` governs the write path and is untouched by this read-only change.

## Approach

Exploration approach 1: one read-only projection joining
`physical_capacity_assignments` → `physical_sessions` (filtered by student and
`[monthStart, monthNext)`) with a LEFT JOIN on `physical_attendances`. A single query yields
both the session list and the aggregate, so the counts can never disagree with the list.

Module chain, as established in `api/physical`: out-port → `*UseCaseImpl` → `PhysicalConfiguration`
bean wiring → controller → JPA repository/adapter. Domain stays framework-free (ADR-0021,
ArchUnit-enforced); no cross-module JOIN or import of `com.menta.billing.*` is introduced, so
the existing module-boundary ArchUnit rules hold unchanged.

## Security (non-negotiable)

`SecurityConfig` is first-match-wins and line 200 (`anyRequest().access(...)`) falls through
to a grant for unmapped paths — the same trap `virtual-lesson-progress` documented. The change
MUST add an explicit method-scoped `.authenticated()` matcher for
`GET /api/v1/physical/attendance/me` **before** any broader physical block, with a slice test
asserting `401` when unauthenticated so a future matcher reorder fails the build.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/physical/.../application/port/out/PhysicalCapacityAssignmentRepository.java` | Modified | Per-student date-range query |
| `api/physical/.../application/port/out/AttendanceRepository.java` | Modified | Per-student date-range query |
| `api/physical/.../application/usecase/` | New | `GetPhysicalAttendanceHistoryUseCase` + impl |
| `api/physical/.../application/dto/` | New | Month aggregate + per-session projection |
| `api/physical/.../infrastructure/persistence/` | Modified | JPA query methods + adapters |
| `api/physical/.../infrastructure/web/controller/` | New | `PhysicalAttendanceController` (self) + `PhysicalAttendanceAdminController` (elevated) |
| `.../PhysicalConfiguration.java` | Modified | Bean wiring |
| `api/auth/.../SecurityConfig.java` | Modified | One `.authenticated()` matcher (self) + one `hasAnyRole("ADMIN","INSTRUCTOR")` matcher (admin), first-match-wins ordering |
| `api/app/.../db/migration/V21__*.sql` | New | Indexes on `physical_capacity_assignments.student_id`, `physical_attendances.user_id` |
| `bruno/API - Direct/physical/` | New | Request file |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| A mid-month MONTHLY purchase's on-screen count won't equal the purchase's own `scheduledSessionCount` (accepted D1 consequence) | Low | Explicitly stated in the decision record; not a bug, a confirmed tradeoff |
| Anonymous access via the security fall-through | High | Explicit matcher + `401` regression test |
| Full table scan without the new indexes | Med | `V21` indexes both tables on the student column |
| D3 read of the DoD is wrong and real paging is wanted | Low | Bounded-result test documents the reading; adding paging later is additive |
| Month boundary / timezone ambiguity on `scheduled_at` | Med | `[monthStart, monthNext)` half-open `Instant` bounds derived from `YearMonth`; the zone used MUST be stated in the spec, not left implicit |
| Coverage gate: physical is 95% domain+app / 90% infra | Med | Test-first per strict TDD; the rate/denominator branches need explicit cases |
| Instructor-scoping bug leaks a student's attendance in a course they don't teach | High | Filter is applied at the query layer (JOIN against `physical_courses.professor_id`), never post-filtered in application code; dedicated test asserts an instructor sees zero rows for a course they don't teach, not a filtered list |
| An instructor probing a `studentId` learns the student exists via a differently-shaped empty response | Low | Empty-course-overlap and student-not-found both return the same `200` empty shape (D6), never `403`/`404` |

## Rollback Plan

1. Revert the merge commit — the endpoint disappears; nothing else reads the new port methods.
2. `SecurityConfig` returns to its prior matcher list; the added matcher covers only the new
   path, so no existing endpoint's authorization changes.
3. Leave `V21` applied — it only adds indexes, no columns, no data movement. Drop it in a later
   deliberate migration if desired.

No write path, no state machine, and no existing row is touched, so this is a low-risk revert.

## Dependencies

- None blocking. `physical_capacity_assignments` (V7) and `physical_attendances` (V15) exist.
- No new library, external service, or configuration.

## Success Criteria

- [ ] A student reads their own month and gets `scheduledSessionCount`, `attended`, `absent`,
      `attendanceRate`, and a per-session list.
- [ ] Tests cover both a four-session and a five-session month (issue DoD) and prove the rate
      is computed from the actual assignment count, never a fixed monthly constant.
- [ ] `includeAbsent=true` lists unattended assignments as `ABSENT`; `includeAbsent=false`
      omits them while leaving all four aggregate numbers unchanged.
- [ ] An anonymous request returns `401`; an authenticated student cannot obtain another
      student's history through the self endpoint under any parameter.
- [ ] `ADMIN` reads any student's month unrestricted via the elevated endpoint.
- [ ] `INSTRUCTOR` reads a student's month filtered to only that instructor's own courses —
      both `sessions[]` and every aggregate number reflect the filtered set, never the
      student's full history; a student with zero overlapping courses yields the empty shape
      (`200`, count `0`), not an error.
- [ ] A `STUDENT` cannot reach the elevated endpoint at all (`403`).
- [ ] A month with no assignments returns `200`, count `0`, rate `0.00`.
- [ ] A bounded-result test proves one month is returned complete and untruncated (D3).
- [ ] OpenAPI contract updated; `./gradlew check` passes, including ArchUnit and the physical
      coverage gate.

## Proposal question round — resolved

The orchestrator's default interpretive calls were put to the user directly before `sdd-spec`;
all four are now confirmed product decisions, not defaults:

1. **D1** — calendar-month assignment count, confirmed. `scheduledSessionCount` is derived from
   `physical_sessions.scheduled_at`, never Billing's coverage window.
2. **D2** — elevated access is **in scope for this change** (reversed from the orchestrator's
   deferred default). `ADMIN` unrestricted; `INSTRUCTOR` scoped to `PhysicalCourse.professorId`
   ownership, reusing the existing `PricingNotOwnedException`-style rule. See Scope and the
   updated "Decision record" above.
3. **D3** — no real pagination, confirmed. Bounded single-month response with a
   no-truncation test.
4. **Timezone** — the academy's configured local zone, confirmed. `[monthStart, monthNext)`
   `Instant` bounds are derived from `YearMonth` in that zone, not UTC, so a late-evening local
   class is never misattributed to the wrong month. No zone configuration exists anywhere in
   this codebase today (checked: no `ZoneId`, no `America/Argentina/*` literal, nothing in
   `application.yml`) — this change introduces the first one, a new
   `physical.attendance.zone-id` property (default `America/Argentina/Buenos_Aires`), scoped to
   this endpoint only. `sdd-design` picks the exact bean/injection shape.

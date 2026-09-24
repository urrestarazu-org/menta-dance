# Exploration: Physical attendance history

**Change**: `physical-attendance-history`
**Issue**: [#39](https://github.com/urrestarazu-org/menta-dance/issues/39) — US-PHYSICAL-002, "Ver historial de asistencia"
**Date**: 2026-09-21
**Phase**: explore (read-only investigation; no implementation)

## Current state

### Assignment model

`physical_capacity_assignments` (`api/app/src/main/resources/db/migration/V7__physical_courses.sql`) —
`id, session_id, student_id, created_at`, `UNIQUE(session_id, student_id)`, indexed only on
`session_id`. A row *is* a confirmed assignment (no status column; cancellation deletes the row).
`PhysicalCapacityAssignmentRepository`
(`api/physical/src/main/java/com/menta/physical/application/port/out/PhysicalCapacityAssignmentRepository.java`)
today exposes only `existsConfirmedAssignment(sessionId, studentId)` — no "list by student" method
exists.

### "Snapshot mensual o pagos individuales"

This is Billing's `PurchaseType.MONTHLY`/`INDIVIDUAL` via `PhysicalCourseQuote`
(`api/billing/src/main/java/com/menta/billing/domain/model/PhysicalCourseQuote.java`).

**Critical finding: MONTHLY does not mean "sessions in a calendar month."** Per
`PhysicalCapacityAssignmentOutboxEventHandler`'s Javadoc
(`api/app/src/main/java/com/menta/app/outbox/PhysicalCapacityAssignmentOutboxEventHandler.java:80-92`),
MONTHLY counts forward from `confirmedAt` for `scheduledSessionCount` sessions via
`CoveragePlanner` — a rolling N-session window, not calendar-aligned. Neither the quote nor the
assignment row stores a "period"; the only usable date is `physical_sessions.scheduled_at`. This
is a real semantic gap the proposal must resolve explicitly: "sessions assigned in month X"
(derivable via `scheduled_at`) may diverge from "the purchase's own coverage window."

### Check-in flow

`ProcessPhysicalCheckInUseCaseImpl.checkIn()`
(`api/physical/src/main/java/com/menta/physical/application/usecase/ProcessPhysicalCheckInUseCaseImpl.java`)
— an 11-step validation ending in `Attendance.record(...)` + `attendanceRepository.save`. "Did
student X check in for session Y" = `AttendanceRepository.findBySessionIdAndUserId(sessionId,
userId)`.

### Attendance table

Exists and is queryable. `physical_attendances` (`V15__physical_attendances.sql`) — `id,
session_id, user_id, recorded_at, device_id, kind`, `UNIQUE(session_id, user_id)`, indexed only
on `session_id`. `AttendanceJpaRepository` has exactly one method today
(`findBySessionIdAndUserId`); no per-student range query exists.

### Authorization precedent

`SubscriptionController.historyOwn()`
(`api/billing/src/main/java/com/menta/billing/infrastructure/web/controller/SubscriptionController.java:106-113`)
is the exact "own data only, any authenticated role" pattern to mirror — `actingUserId(authentication)`
plus an explicit `SecurityConfig` `.authenticated()` matcher, no role check.
`PhysicalCourseAdminController`/`PhysicalSessionAdminController` show the different
`isAdmin(authentication)` + `hasAnyRole("ADMIN","INSTRUCTOR")` elevated pattern, but there is
**no existing precedent** for admin/instructor reading a *student's* attendance in Physical —
that would be new surface.

### Cancelled-before-quoting sessions

`PhysicalSession` has `SessionStatus.CANCELLED` (`cancel()` domain method). A session cancelled
before a quote ever covers it simply never enters the eligible-session set computed by
`CoveragePlanner`, so it never becomes an assignment row — consistent with the issue's explicit
scope note.

### Month-boundary semantics

No existing code reasons about calendar months for physical scheduling; `CoveragePlanner` is
N-sessions-forward, not calendar-aligned. A new query needs `[monthStart, monthNext)` `Instant`
bounds derived from the request's `YearMonth`, filtered against `physical_sessions.scheduled_at`.

### Read-endpoint precedent

`GetSubscriptionHistoryUseCase.history(UUID userId)` → `SubscriptionRepository.findHistoryByUserId(userId)`
returning a lightweight projection DTO is the shape to copy for `GetPhysicalAttendanceHistoryUseCase`.
No pagination precedent exists anywhere in this codebase for a "me/history" endpoint, despite the
issue's DoD requiring pagination tests.

## Affected areas

- `api/physical/.../application/port/out/PhysicalCapacityAssignmentRepository.java` — new range-query method
- `api/physical/.../application/port/out/AttendanceRepository.java` — new range-query method
- `api/physical/.../application/usecase/` — new `GetPhysicalAttendanceHistoryUseCase` impl
- `api/physical/.../infrastructure/persistence/adapter/` and JPA repositories — new query methods, new indexes
- `api/physical/.../infrastructure/web/controller/` — new controller, `GET /api/v1/physical/attendance/me`
- `api/auth/src/main/java/com/menta/auth/infrastructure/security/SecurityConfig.java` — new explicit `.authenticated()` matcher (first-match-wins ordering — same trap the `virtual-lesson-progress` exploration already documented)
- `api/app/src/main/resources/db/migration/V21__*.sql` — new indexes on `student_id` (next available migration number after `V20_1_5`)
- OpenAPI contract (issue DoD)

## Approaches

### 1. New read-only projection joining assignments + sessions + attendances (recommended)

One new port method joining `physical_capacity_assignments` → `physical_sessions` (filtered by
student + date range) with a left-join on `physical_attendances`.

- **Pros**: reuses existing tables, no new write path, matches the `SubscriptionController.historyOwn()` shape.
- **Cons**: needs new `student_id` indexes on both tables; needs a new Flyway migration.
- **Effort**: Medium.

### 2. Denormalized monthly snapshot table

Pre-computed per student/month.

- **Pros**: O(1) reads.
- **Cons**: no precedent in this codebase (Physical explicitly documents avoiding cached counters
  — see `PhysicalSession`'s own Javadoc); adds a second source of truth.
- **Effort**: High.

## Recommendation

Approach 1 — consistent with existing live-join conventions, purely additive.

## Risks

1. **Denominator ambiguity**: "scheduledSessionCount" in the issue example likely means
   "assignments whose session falls in the requested month" (via `scheduled_at`), not the
   purchase's own confirmation-anchored N-session window — must be resolved explicitly in the
   proposal. **This is the first thing `sdd-propose` must resolve.**
2. Missing `student_id` indexes on `physical_capacity_assignments` and `physical_attendances` —
   needed to avoid full scans.
3. No pagination precedent exists despite the DoD requiring pagination tests.
4. No existing admin/instructor read precedent for a student's own attendance — new surface if
   scope expands.
5. Cancelled-session/assignment interaction is underspecified in the domain (matches the issue's
   own deferred scope, not a gap to fix here).

## Ready for proposal

Yes — with risk #1 (denominator semantics) flagged as the first thing `sdd-propose` must resolve.

# Design: Physical attendance history (#39, US-PHYSICAL-002)

## Technical Approach

Two read-only endpoints over **one** JPQL query. The query drives from
`physical_capacity_assignments` (the denominator, D1), joins `physical_sessions` for the date
filter, joins `physical_courses` for the display fields **and** the instructor ownership
predicate, and `LEFT JOIN`s `physical_attendances` for the live `ATTENDED`/`ABSENT` derivation
(D5). Because the list and the aggregate come from the same row set, they cannot disagree, and
the instructor filter narrows both at once.

Physical's established chain, unchanged: out-port → `*UseCaseImpl` → `PhysicalConfiguration`
bean → controller → JPA repository/adapter. No transactional decorator (reads skip it; the
adapter already carries `@Transactional(REQUIRED, readOnly = true)`, as
`AttendanceRepositoryAdapter` does). Domain gains nothing — this change adds no aggregate, no
invariant and no Spring/JPA import below `infrastructure`, so ADR-0021's ArchUnit rules hold
unchanged. No `com.menta.billing.*` import; Billing's coverage window is never consulted (D1).

## Architecture Decisions

### C1 — One port method pair on `PhysicalCapacityAssignmentRepository`; `AttendanceRepository` is untouched

The proposal's Affected Areas table lists a new range query on **both** out-ports. **Corrected
here**: a second per-student query on `AttendanceRepository` would be a second source for the
same fact, and the whole point of the approach is one row set. Attendance enters through the
`LEFT JOIN`, not through its own port. `AttendanceRepository` keeps its two existing methods.

```java
// application/port/out/PhysicalCapacityAssignmentRepository.java — two methods, no null sentinel
/** Unrestricted: self (C2 Self) and ADMIN (C2 Admin). */
List<AttendanceHistoryRow> findMonthlyAttendance(UUID studentId, Instant monthStart, Instant monthNext);

/** Narrowed to sessions of courses whose physical_courses.professor_id == professorId. */
List<AttendanceHistoryRow> findMonthlyAttendanceInCoursesOwnedBy(
    UUID studentId, Instant monthStart, Instant monthNext, UUID professorId);
```

| Option | Tradeoff | Decision |
|---|---|---|
| One method, nullable `professorId` ("null = unrestricted") | The unrestricted result is one accidental `null` away; exactly the leak the High risk row names | Rejected at the port |
| Two ports/two queries | No fail-open value, but two JPQL strings to keep in sync — they would drift | Rejected |
| **Two port methods, one JPQL** | No fail-open value crosses the application boundary; the nullable parameter exists only inside the 4-line adapter, where one test pins it | **Chosen** |

The nullable-parameter idiom is already this repo's (`PhysicalCourseJpaRepository.findByStatusAfterCursor`'s
`:cursor IS NULL OR c.id > :cursor`), so it stays where that idiom belongs — in the JPA layer.

**The filter is never a post-filter.** `includeAbsent` is deliberately **absent** from both
signatures: it only hides rows from `sessions[]` and must not touch the aggregate (D4), so it is
applied in the assembler, after counting. The *course* filter is the opposite — it must change
the counts, so it is a query parameter and nothing else.

```sql
-- infrastructure/persistence/repository/PhysicalCapacityAssignmentJpaRepository.java
SELECT s.id           AS sessionId,
       s.scheduledAt  AS scheduledAt,
       c.title        AS courseName,
       c.professorName AS instructorName,
       a.recordedAt   AS recordedAt
FROM PhysicalCapacityAssignmentJpaEntity asg
JOIN PhysicalSessionJpaEntity s ON s.id = asg.sessionId
JOIN PhysicalCourseJpaEntity  c ON c.id = s.courseId
LEFT JOIN AttendanceJpaEntity a ON a.sessionId = s.id AND a.userId = asg.studentId
WHERE asg.studentId = :studentId
  AND s.scheduledAt >= :monthStart
  AND s.scheduledAt <  :monthNext
  AND (:professorId IS NULL OR c.professorId = :professorId)
ORDER BY s.scheduledAt ASC, s.id ASC
```

These entities carry plain `UUID` columns and no `@ManyToOne`, so these are Hibernate 6 explicit
entity joins (`JOIN Entity alias ON ...`), not association navigation. `physical_courses` is
joined **unconditionally** — it supplies `courseName`/`instructorName` for every caller — so the
scoped and unscoped forms differ by exactly one `AND` clause on an already-joined table, which is
what makes one query honest rather than a convenience.

Row type: Spring Data interface projection `MonthlyAttendanceRowProjection` in
`infrastructure/persistence/repository/`, mapped by the adapter to the application record
`AttendanceHistoryRow` — the same split `CourseProgressRowProjection` uses in `api/virtual`, so
the out-port never returns a persistence type.

### C2 — `AttendanceViewer` is a sealed interface carrying its own subject

```java
// application/dto/AttendanceViewer.java
public sealed interface AttendanceViewer {

    UUID studentId();

    record Self(UUID studentId) implements AttendanceViewer { }
    record Admin(UUID studentId) implements AttendanceViewer { }
    record InstructorOwnCourses(UUID studentId, UUID professorId) implements AttendanceViewer { }

    /** The self endpoint has no studentId parameter at all — subject == caller, structurally. */
    static AttendanceViewer self(UUID actingUserId) { return new Self(actingUserId); }

    /** Mirrors this repo's established (actingUserId, actingAsAdmin) controller pair. */
    static AttendanceViewer elevated(UUID studentId, UUID actingUserId, boolean actingAsAdmin) {
        return actingAsAdmin ? new Admin(studentId)
                             : new InstructorOwnCourses(studentId, actingUserId);
    }
}
```

| Option | Tradeoff | Decision |
|---|---|---|
| Bare `(UUID studentId, UUID actingUserId, boolean isAdmin)` | The scoping rule is re-derived at every call site — the thing the proposal asked to centralise | Rejected |
| `enum ViewerKind` + nullable `professorId` | "INSTRUCTOR with a null professorId" is representable, and that state is *exactly* the unrestricted query | Rejected |
| **Sealed interface + records** | The illegal state is unrepresentable; the use case's `switch` is exhaustive, so a future `Reception` variant is a **compile error**, not a silent fall into the unrestricted branch | **Chosen** |

Sealed types are already this repo's idiom for closed alternatives (`PaymentStatus` in Billing).
Carrying `studentId` on the viewer means authority and subject travel as one value and cannot be
mismatched. The use case's entire authorization logic is then three lines:

```java
List<AttendanceHistoryRow> rows = switch (viewer) {
    case AttendanceViewer.Self s  -> repo.findMonthlyAttendance(s.studentId(), start, next);
    case AttendanceViewer.Admin a -> repo.findMonthlyAttendance(a.studentId(), start, next);
    case AttendanceViewer.InstructorOwnCourses i ->
        repo.findMonthlyAttendanceInCoursesOwnedBy(i.studentId(), start, next, i.professorId());
};
```

`Self` and `Admin` resolve to the same call deliberately: they are the same *query* with
different *authority*, and collapsing them into one variant would lose the distinction the
`SecurityConfig` matchers and the audit story rely on.

### C3 — Timezone: `ZoneId` is a configured value, not a port

**Property** `physical.attendance.zone-id`, default `America/Argentina/Buenos_Aires`, parsed in
`PhysicalConfiguration` and passed as a plain `ZoneId` constructor argument:

```java
// infrastructure/config/PhysicalConfiguration.java — ZoneId.of throws at context startup on a bad id
@Bean
public GetPhysicalAttendanceHistoryUseCase getPhysicalAttendanceHistoryUseCase(
    PhysicalCapacityAssignmentRepository assignmentRepository,
    @Value("${physical.attendance.zone-id:America/Argentina/Buenos_Aires}") String zoneId
) {
    return new GetPhysicalAttendanceHistoryUseCaseImpl(assignmentRepository, ZoneId.of(zoneId));
}
```

| Option | Tradeoff | Decision |
|---|---|---|
| New `ZoneIdProvider` port | A one-method interface returning a constant — an indirection with no behavior to substitute; tests would stub a value | Rejected |
| Widen the existing `Clock` port | `Clock` is `now()` only, injected across all of Physical; every implementor and stub would carry a zone it never uses | Rejected |
| `@Value` inside `GetPhysicalAttendanceHistoryUseCaseImpl` | Application classes here carry no Spring annotations | Rejected |
| **`@Value` on the `@Bean` method parameter** | The established shape (`BillingConfiguration:222, 250-253, 457-458`); the use case stays framework-free and takes a literal `ZoneId` in tests | **Chosen** |

**The conversion, exactly:**

```java
Instant monthStart = month.atDay(1).atStartOfDay(zoneId).toInstant();
Instant monthNext  = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant();
```

`atStartOfDay(ZoneId)` — not `LocalDateTime.toInstant(ZoneOffset)` — is required: it resolves the
offset **per date**, so the September and October boundaries get their own offsets, and a DST gap
at midnight moves forward to the first valid instant instead of producing a wrong `Instant`.
Buenos Aires is UTC-3 year-round today, but Argentina has adopted and dropped DST repeatedly and
the property is configurable, so no fixed offset may be assumed. Explicitly rejected:
`toInstant(ZoneOffset.UTC)` and `ZoneOffset.of("-03:00")`.

Bounds are half-open `[monthStart, monthNext)` — `>=` / `<`, never `BETWEEN`, whose inclusive
upper bound would attribute a session at exactly 00:00 on the 1st to both months. No zone math
occurs in SQL; the query compares `Instant` to `Instant`.

**Honest deviation.** The exploration reported "no `ZoneId` anywhere"; that is not quite right —
`CreatePhysicalCourseQuoteUseCaseImpl` documents an implicit `ZoneOffset.UTC` convention shared
with Physical's session-scheduling use cases. This endpoint diverges deliberately: it is the
first whose *contract* is a user-facing calendar month, and UTC would misattribute a 23:00 local
class to the following month. The inconsistency is recorded in Open Questions, not hidden.

### C4 — Anti-enumeration is structural: there is no lookup to leak

`GetPhysicalAttendanceHistoryUseCaseImpl` **never checks whether the student exists** — no
`UserQueryPort`, no existence probe, no `404` path anywhere in the flow. The three cases converge
because the query has no branch that distinguishes them:

| Case | Why the row set is empty |
|---|---|
| Student does not exist | No `physical_capacity_assignments` row matches `student_id` |
| Student exists, instructor teaches none of their courses | `c.professorId = :professorId` excludes every row |
| Student exists, overlapping course, zero sessions that month | The `[monthStart, monthNext)` predicate excludes every row |

All three produce the byte-identical `200` body
`{"period":"…","scheduledSessionCount":0,"attended":0,"absent":0,"attendanceRate":0.00,"sessions":[]}`
(D6). This is stronger than a policy: there is no branch to implement incorrectly.

**No `AttendanceNotFoundException` is introduced, deliberately.** The `PaymentNotFoundException`
idiom exists to collapse `403` and `404` into one `404` *where a not-found case exists at all*.
Here D6 makes an empty month a legitimate `200`, so the endpoint has no not-found case — adding
an exception type would manufacture a distinguishable failure mode that currently cannot occur.
The guard is a test asserting identical bodies across all three rows above, not a new type.

A malformed `{studentId}` maps to `400` and is not an oracle: it is decidable client-side and
tells the caller nothing about existence. Response-time side channels are out of scope.

### C5 — `V21__physical_attendance_history_indexes.sql`

`V21` is free (highest applied is `V20_1_5__billing_payment_proofs.sql`). Column names re-read
from the migrations, not trusted from the proposal: `physical_capacity_assignments.student_id`
(V7:44) and `physical_attendances.user_id` (V15:17).

```sql
-- The month query drives from this table filtered on student_id; V7 indexes only session_id.
ALTER TABLE physical_capacity_assignments
    ADD KEY idx_physical_assignments_student (student_id);
```

**Correction to the proposal.** The proposal also mandates an index on
`physical_attendances.user_id`. It is **not added**, and this is a verified finding: the
`LEFT JOIN` predicate is `a.session_id = s.id AND a.user_id = :studentId`, two equality
conditions fully covered by the existing `uq_physical_attendances_session_user (session_id,
user_id)`. A standalone `user_id` index would serve no query in this change while adding write
amplification to the check-in hot path. Adding it later is a one-line additive migration if a
future per-student attendance scan ever needs it.

A composite `(student_id, session_id)` on the assignments table was also considered and rejected:
`session_id` is already indexed for the join and a student's monthly assignment count is single
digits (D3), so the covering index buys nothing measurable today.

### C6 — DTOs: one application view, one web response pair, one pure assembler

```java
// application/dto — AttendanceStatus lives here, not in domain/model: it is derived, never
// stored, and has no identity or invariant (the CourseProgressView precedent in api/virtual).
public enum AttendanceStatus { ATTENDED, ABSENT }

public record AttendanceHistoryRow(               // out-port row, one per assignment
    UUID sessionId, Instant scheduledAt, String courseName, String instructorName, Instant recordedAt
) {
    public boolean attended() { return recordedAt != null; }
}

public record AttendanceSessionView(
    UUID sessionId, Instant scheduledAt, String courseName, String instructorName,
    AttendanceStatus status, Instant recordedAt   // null for ABSENT
) { }

public record AttendanceHistoryView(
    String period, int scheduledSessionCount, int attended, int absent,
    BigDecimal attendanceRate, List<AttendanceSessionView> sessions
) { }

// application/port/in
public interface GetPhysicalAttendanceHistoryUseCase {
    AttendanceHistoryView history(AttendanceViewer viewer, YearMonth month, boolean includeAbsent);
}
```

`YearMonth` and `BigDecimal` are `java.time`/`java.math` — framework-free, so the in-port stays
clean. `MonthlyAttendanceAssembler` (`application/usecase`, pure static, mirroring
`CourseProgressAssembler`) turns rows into the view with zero mocks, which is what makes the 95%
domain+application gate reachable.

**Rate, exactly (D7):**

```java
attendanceRate = count == 0
    ? new BigDecimal("0.00")
    : BigDecimal.valueOf(attended * 100L).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
```

Multiply **before** dividing. Dividing first at scale 2 and then multiplying rounds twice: 2/3
would yield `0.67 × 100 = 67.00` instead of the correct `66.67`. This ordering is a test case,
not a comment.

**Web layer (`infrastructure/web/dto`): one shared pair,** `AttendanceHistoryResponse` +
`AttendanceSessionResponse`, with the repo's `from(AttendanceHistoryView)` static factory, used
by **both** controllers. The endpoints differ in who may call, never in what comes back — the
instructor narrowing already happened in the query (C1) — so two identical DTO sets would only
create drift, and a shape difference between them would itself be an enumeration oracle (C4).

### C7 — `SecurityConfig`: two explicit matchers, and a prefix that nothing existing covers

The elevated path is `/api/v1/physical/**admin**/attendance/{studentId}`, **not**
`/api/v1/admin/physical/...`. Verified against the current matcher list: neither
`/api/v1/admin/physical/courses/**` (line 273), nor `/api/v1/admin/**` (line 287), nor any
physical matcher covers it. Without an explicit entry it is fully unmapped and falls through to
`anyRequest().access(roleAuthorizationManager)` (line 291), which grants unmapped paths — the
exact incident this file's own Javadoc documents. Both matchers below are therefore mandatory.

Insert **immediately after line 267** (the `access-qr` matcher), inside the physical block and
**before** both the `/api/v1/admin/physical/courses/**`/`/api/v1/admin/physical/sessions/**`
matchers (lines 273-276) and the coarse `/api/v1/admin/**` → `hasRole("ADMIN")` gate (line 287):

```java
// US-PHYSICAL-002, #39: any authenticated student reads their OWN month. The student id
// comes from the JWT principal; this endpoint has no studentId parameter to abuse.
.requestMatchers(HttpMethod.GET, "/api/v1/physical/attendance/me").authenticated()
// US-PHYSICAL-002, #39: elevated read of ANOTHER student's month. Must be declared before
// the generic /api/v1/admin/** rule (line 287, hasRole("ADMIN") only) — that rule WOULD
// match this path (it's a prefix match) and would incorrectly 403 an INSTRUCTOR, who is a
// legitimate caller here. This is the opposite failure mode from an unmapped path falling
// through to a permissive grant, but the same root cause: first-match-wins means the more
// specific rule must come first. INSTRUCTOR is admitted by this matcher and narrowed to
// their own courses inside the query (C1), never by the matcher itself.
.requestMatchers(HttpMethod.GET, "/api/v1/admin/physical/attendance/*")
    .hasAnyRole("ADMIN", "INSTRUCTOR")
```

Ordering facts: the two new matchers cannot shadow each other (different prefixes); single-segment
`*` does not match `/`, so `{studentId}` stays one segment; and the elevated matcher MUST precede
line 287 (and does not conflict with lines 273-276, whose `courses`/`sessions` suffixes are
disjoint from `attendance`). A parameterized slice test pins anonymous → `401` on both paths,
`STUDENT` → `403` on the elevated one, and `INSTRUCTOR` → reaches the controller (not `403`) on
the elevated one, so a future reorder that drops back to the generic `/admin/**` rule fails the
build instead of silently locking out every instructor.

Controllers follow `PhysicalCheckInController`/`PhysicalCourseAdminController` verbatim:
`actingUserId(authentication)` = `UUID.fromString(authentication.getName())`, `isAdmin` =
`ROLE_ADMIN` in the authorities. A new `@PhysicalAttendanceEndpoint` marker plus
`PhysicalAttendanceExceptionHandler` maps `DateTimeParseException` (malformed `month`) and
`IllegalArgumentException` (malformed `studentId`) to `400 application/problem+json`, reusing
Physical's `ProblemDetails`. `month` is a **required** parameter — no "current month" default,
which would add a zone-dependent branch for no contract benefit.

## Data Flow

    GET /api/v1/physical/attendance/me?month=&includeAbsent=       [PhysicalAttendanceController]
      └─ AttendanceViewer.self(actingUserId)                       ← no studentId param exists
    GET /api/v1/admin/physical/attendance/{studentId}?month=&…     [PhysicalAttendanceAdminController]
      └─ AttendanceViewer.elevated(studentId, actingUserId, isAdmin(auth))
                                    │
                                    ▼
                 GetPhysicalAttendanceHistoryUseCaseImpl(zoneId)              (C3)
                   YearMonth ──→ [monthStart, monthNext)  atStartOfDay(zoneId)
                   switch (viewer) ──→ one of two port methods                 (C1, C2)
                                    │
                                    ▼
           PhysicalCapacityAssignmentRepositoryAdapter  @Transactional(readOnly)
             assignments ⋈ sessions ⋈ courses ⟕ attendances   ORDER BY scheduled_at, id
                                    │  List<AttendanceHistoryRow>
                                    ▼
                 MonthlyAttendanceAssembler  (pure)                            (C6)
                   counts + rate over the FULL row set  ← includeAbsent never applied here
                   sessions[] filtered by includeAbsent ← applied only here     (D4)
                                    │
                                    ▼
                   AttendanceHistoryResponse.from(view)   — identical shape for both callers

## File Changes

| File | Action | Description |
|---|---|---|
| `api/app/.../db/migration/V21__physical_attendance_history_indexes.sql` | Create | One index on `physical_capacity_assignments.student_id` (C5) |
| `api/auth/.../security/SecurityConfig.java` | Modify | Two GET matchers after line 267 (C7) |
| `physical/application/dto/AttendanceViewer.java` | Create | Sealed interface + 3 records + 2 factories (C2) |
| `physical/application/dto/{AttendanceStatus,AttendanceHistoryRow,AttendanceSessionView,AttendanceHistoryView}.java` | Create | Boundary types (C6) |
| `physical/application/port/in/GetPhysicalAttendanceHistoryUseCase.java` | Create | In-port |
| `physical/application/port/out/PhysicalCapacityAssignmentRepository.java` | Modify | +2 range methods, no nullable sentinel (C1) |
| `physical/application/port/out/AttendanceRepository.java` | **Untouched** | Attendance arrives via the `LEFT JOIN` (C1) |
| `physical/application/usecase/GetPhysicalAttendanceHistoryUseCaseImpl.java` | Create | Zone bounds + viewer `switch` |
| `physical/application/usecase/MonthlyAttendanceAssembler.java` | Create | Pure counts/rate/filter (C6) |
| `physical/infrastructure/persistence/repository/PhysicalCapacityAssignmentJpaRepository.java` | Modify | One `@Query` + `MonthlyAttendanceRowProjection` |
| `physical/infrastructure/persistence/adapter/PhysicalCapacityAssignmentRepositoryAdapter.java` | Modify | Two methods → one query; `null`/professorId mapping lives only here |
| `physical/infrastructure/web/controller/PhysicalAttendanceController.java` | Create | `GET …/attendance/me` |
| `physical/infrastructure/web/controller/PhysicalAttendanceAdminController.java` | Create | `GET …/admin/attendance/{studentId}` |
| `physical/infrastructure/web/controller/{PhysicalAttendanceEndpoint,PhysicalAttendanceExceptionHandler}.java` | Create | Marker + `400` mapping (C7) |
| `physical/infrastructure/web/dto/{AttendanceHistoryResponse,AttendanceSessionResponse}.java` | Create | One shared pair (C6) |
| `physical/infrastructure/config/PhysicalConfiguration.java` | Modify | Use-case bean + `@Value` zone id (C3) |
| `api/app/src/main/resources/application*.yml` | Modify | `physical.attendance.zone-id` |
| `bruno/API - Direct/physical/*.bru` | Create | Two requests |
| OpenAPI contract | Modify | Both endpoints (issue DoD) |

## Testing Strategy

| Layer | What to test | Approach |
|---|---|---|
| Unit (application) | `MonthlyAttendanceAssembler`: 4-session and 5-session months (DoD); rate `4/5 = 80.00`; **`2/3 = 66.67`, not `67.00`** (multiply-before-divide, C6); zero denominator → `0.00`; `includeAbsent=false` hides `ABSENT` rows while all four aggregates stay identical (D4) | `MonthlyAttendanceAssemblerTest`, pure, zero mocks |
| Unit (application) | Zone bounds (C3): `YearMonth.of(2026,9)` → `2026-09-01T03:00Z` / `2026-10-01T03:00Z` under Buenos Aires; a session at `2026-09-30T23:00-03:00` lands in **September**, and the *same* instant lands in **October** when the use case is built with `ZoneOffset.UTC` | `GetPhysicalAttendanceHistoryUseCaseImplTest` — the two-zone pair is mandatory; a single UTC-written test passes a UTC-hardcoded bug |
| Unit (application) | Viewer dispatch (C2): `Self`/`Admin` → `findMonthlyAttendance`; `InstructorOwnCourses` → `findMonthlyAttendanceInCoursesOwnedBy` with the caller's id; the scoped method is **never** reached with a `null` professor id | Mockito `verify` + `verifyNoMoreInteractions` |
| Unit (application) | Anti-enumeration (C4): student-with-no-rows, instructor-with-no-overlap and empty-month all yield an **equal** `AttendanceHistoryView`; no existence port is ever consulted | Assert object equality across the three cases |
| Unit (web) | `AttendanceViewer.self` is built from the principal and the self controller exposes no studentId input; elevated controller passes `isAdmin(authentication)`; malformed `month`/`studentId` → `400 problem+json` | MockMvc slice, mirroring `PhysicalCheckInControllerTest` |
| Infra (persistence) | The JPQL: `ORDER BY scheduled_at, id`; `ABSENT` rows survive the `LEFT JOIN` with `recordedAt == null`; half-open bounds include `monthStart` and exclude `monthNext`; the adapter's scoped method returns **zero rows** for a course owned by another professor (not a filtered list) | `@DataJpaTest` + Testcontainers MySQL 8 |
| Integration | `ADMIN` reads any student unrestricted; `INSTRUCTOR` reading a student enrolled in two courses (one theirs, one not) sees only their own sessions **and** aggregates computed over that subset only; `STUDENT` → `403` on the elevated path | Testcontainers, full filter chain |
| Integration | Bounded-result test (D3): a full month is returned complete and untruncated in one response | Testcontainers |
| Security | Anonymous → `401` on both paths; `STUDENT` → `403` on the elevated path | Parameterized `SecurityConfigTest` cases (C7) |
| Architecture | No `org.springframework`/`jakarta.persistence` under `physical/domain` or `physical/application`; no `com.menta.billing.*` import | Existing `PhysicalArchitectureTest` run |
| Coverage | physical gate: 95% domain+application, 90% infrastructure | `./gradlew :api:physical:test jacocoTestCoverageVerification` |

**TDD order (RED → GREEN per slice):** assembler → zone bounds → viewer dispatch →
anti-enumeration equality → JPQL/adapter → controllers/advice → `SecurityConfig` 401/403 →
instructor-scoping integration (last; it is the High risk row's proof).

## Threat Matrix

N/A — this change introduces no shell command, subprocess, VCS/PR automation,
executable-file classification, or process integration. HTTP route registration and Spring
Security matcher ordering are covered by the dedicated `401`/`403` regression tests in C7, and
the one real adversarial boundary (an instructor enumerating students) is covered structurally
by C4 plus its equality test.

## Migration / Rollout

`V21` is additive: one `ADD KEY`, no column, no data movement, no lock-heavy rebuild on tables of
this size. No feature flag — the endpoints do not exist until the merge lands, and reverting the
merge removes them with nothing else reading the new port methods. Leave `V21` applied on revert.
`physical.attendance.zone-id` has a working default, so no deployment-time configuration is
required; setting it wrong is caught at context startup by `ZoneId.of` (C3), not at request time.

Two independently deliverable slices, both under the 800-line review budget
(`sdd-tasks` owns the authoritative forecast):

1. **Self endpoint** — `V21`, `AttendanceViewer`, application DTOs, assembler, use case + zone,
   port/adapter/JPQL, `PhysicalAttendanceController`, advice, the `/me` `SecurityConfig` matcher.
2. **Elevated endpoint** — scoped port method + adapter wiring, `PhysicalAttendanceAdminController`,
   the `/admin/attendance/*` matcher, instructor-scoping and anti-enumeration integration tests,
   Bruno + OpenAPI.

Slice 1 is usable on its own; slice 2 is purely additive on top of it.

## Requirement → Component Map

| # | Requirement | Components | Slice |
|---|---|---|---|
| R1 | Student reads own month with aggregates and per-session list (D1, D5, D7) | Use case, assembler, port/JPQL, self controller, `V21` | **1** |
| R2 | `includeAbsent` hides rows without moving any aggregate (D4) | `MonthlyAttendanceAssembler` only | **1** |
| R3 | Empty month → `200`, count `0`, rate `0.00` (D6) | Assembler zero-denominator branch | **1** |
| R4 | Anonymous → `401`; the self endpoint cannot read another student | `SecurityConfig` matcher, `AttendanceViewer.self` | **1** |
| R5 | Calendar month resolved in the academy's configured zone | `ZoneId` bean wiring + `atStartOfDay` conversion (C3) | **1** |
| R6 | `ADMIN` reads any student unrestricted | `AttendanceViewer.Admin`, admin controller, matcher | **2** |
| R7 | `INSTRUCTOR` sees only their own courses, in list **and** aggregates | `InstructorOwnCourses` + `findMonthlyAttendanceInCoursesOwnedBy` (C1) | **2** |
| R8 | `STUDENT` cannot reach the elevated endpoint | `hasAnyRole("ADMIN","INSTRUCTOR")` matcher | **2** |
| R9 | Non-overlapping student is indistinguishable from an empty month | Structural — no existence lookup exists (C4) | **2** |
| R10 | Bounded, untruncated single-month response (D3) | No pagination surface; bounded-result test | **2** |

## Open Questions

None blocking. Three items for `sdd-tasks` to carry forward as **facts, not questions**:

- **Path convention — resolved.** The originally-drafted proposal path
  (`/api/v1/physical/admin/attendance/{studentId}`) inverted this repo's every other elevated
  surface (`/api/v1/admin/physical/courses`, `/api/v1/admin/virtual/lessons`,
  `/api/v1/admin/billing/...`). Confirmed with the product owner and corrected to
  `/api/v1/admin/physical/attendance/{studentId}`, consistent with that convention. It still
  falls under the generic `/api/v1/admin/**` → `hasRole("ADMIN")` rule at line 287, so its own
  explicit `hasAnyRole("ADMIN","INSTRUCTOR")` matcher (C7) must be declared *before* line 287 —
  same first-match-wins requirement either path would have needed.
- **`V21` omits the `physical_attendances.user_id` index** the proposal mandates, because the
  existing `uq_physical_attendances_session_user` already covers the `LEFT JOIN` (C5).
- **Zone convention divergence.** This endpoint uses a configured `ZoneId` while
  `CreatePhysicalCourseQuoteUseCaseImpl` and Physical's session-scheduling use cases use implicit
  `ZoneOffset.UTC` (C3). Unifying them is a separate, deliberate change — not smuggled in here.

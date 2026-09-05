# Design: Virtual lesson progress (#52, US-VIRTUAL-005)

## Technical Approach

One mutable upsert row per `(user_id, lesson_id)` in `virtual_lesson_progress` (V19). Reuses virtual's
chain verbatim: port → `*UseCaseImpl` → `Transactional*` decorator (writes) → `VirtualConfiguration`
bean → controller → JPA entity/mapper/adapter. Reads skip the transactional decorator, as
`GetPublicLessonUseCaseImpl` does. Billing is reached only via `VirtualCourseEntitlementPort`
(`:api:shared`). Ownership is a query predicate, never a post-load filter.

## Architecture Decisions

### Schema `virtual_lesson_progress` (V19)

```sql
CREATE TABLE virtual_lesson_progress (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    lesson_id BINARY(16) NOT NULL,
    course_id BINARY(16) NOT NULL,            -- denormalized, mirrors virtual_lessons (V6 rationale)
    position_seconds INT NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    position_updated_at DATETIME(3) NULL,     -- resume ordering key; exactly one writer (PUT .../progress)
    updated_at DATETIME(3) NOT NULL,          -- generic row audit; bumped by any write, never used for resume
    PRIMARY KEY (id),
    UNIQUE KEY uq_virtual_lesson_progress_user_lesson (user_id, lesson_id),
    KEY idx_virtual_lesson_progress_user_course (user_id, course_id),
    CONSTRAINT fk_virtual_lesson_progress_lesson FOREIGN KEY (lesson_id) REFERENCES virtual_lessons (id),
    CONSTRAINT fk_virtual_lesson_progress_course FOREIGN KEY (course_id) REFERENCES virtual_courses (id),
    CONSTRAINT chk_virtual_lesson_progress_position CHECK (position_seconds >= 0)
);
```

| Choice | Alternatives rejected | Rationale |
|---|---|---|
| Surrogate `id` + unique `(user_id, lesson_id)` | Composite PK | Matches every virtual table; unique key still gives schema-level idempotency |
| No FK on `user_id` | FK to `users` | Virtual holds no FK into auth anywhere; the token subject is authoritative |
| FKs without `ON DELETE CASCADE` (RESTRICT) | CASCADE; no FK | Matches V6/V11. Lesson deletion only happens via `DeleteVirtualCourseUseCaseImpl.java:30-48`, which requires `DRAFT` — a course students can never have progress against, so RESTRICT is unreachable today. If it ever fires it fails loud instead of silently destroying student history (closed decision 6). Deliberately **not** extending the delete chain: that is management scope |
| Denormalized `course_id` | JOIN through lessons | Course aggregate stays a flat `WHERE user_id = ? AND course_id = ?` |

### Course aggregate: two bounded queries, no N+1

`GetCourseProgressUseCaseImpl` issues exactly two reads, then derives everything in memory:

1. `long countByCourseId(UUID)` on `VirtualLessonJpaRepository` — live denominator (closed decision 2).
2. `List<CourseProgressRowProjection> findRowsForUserAndCourse(userId, courseId)` — an interface
   projection alongside `LessonAggregateProjection`/`ModuleCountProjection`, joining
   progress → lesson → module for `lessonId, positionSeconds, completed, positionUpdatedAt, lessonOrder,
   moduleOrder`, `ORDER BY p.positionUpdatedAt DESC, m.displayOrder ASC, l.displayOrder ASC,
   p.lessonId ASC`.

Resume lesson = first row. Ordering by `position_updated_at` — not `updated_at` — is what keeps
`POST /complete` from moving the resume point (decision 3 is "last *saved position*", and completing is
not saving a position; decision 7's position immobility alone does not deliver this). A row first created
by `POST /complete` on a never-played lesson leaves `position_updated_at` NULL — the honest encoding of
"never played", not a magic timestamp. MySQL 8.0 orders NULL last under `ORDER BY ... DESC`, so such a row
never wins resume selection without any special-casing in the query. If every row is NULL, the curriculum
tie-break decides, yielding the lowest `module.display_order` / `lesson.display_order`. Resume ordering
MUST stay in SQL: the assembler consumes the already-ordered list and never compares timestamps itself, so
it needs no null handling. If ordering ever moves into Java it requires `Comparator.nullsLast`. Completed count = rows with `completed = true`, including lessons now
inaccessible (decision 6). `percentage = 0` when total = 0; otherwise half-up
`round(completed * 100.0 / total)`, clamped so 100 is returned only when `completed == total`.
Rejected: per-lesson lookups (N+1) and a SQL-side percentage (untestable without a container).

### Ownership: predicate, not guard

No guard class. Every read is `findByUserIdAndLessonId(userId, lessonId)` / `...AndCourseId(...)`, so a
foreign row is never loaded and cannot leak. `actingUserId` is a mandatory use-case parameter extracted
controller-side by `UUID.fromString(authentication.getName())` (`VirtualLessonAdminController.java:55-57`
pattern), never from path or body (decision 9). Rejected: a `ProgressOwnershipGuard` mirroring
`LessonOwnershipGuard` — it must load the row before comparing, reintroducing the exposure it prevents.

### SecurityConfig

Four method-scoped matchers inserted **immediately before** the `.requestMatchers(...)` block at
`SecurityConfig.java:135-150` (first-match-wins), following the `DELETE /billing/subscriptions/me`
precedent at lines 166-170:

```java
.requestMatchers(HttpMethod.PUT,  "/api/v1/virtual/lessons/*/progress").authenticated()
.requestMatchers(HttpMethod.GET,  "/api/v1/virtual/lessons/*/progress").authenticated()
.requestMatchers(HttpMethod.POST, "/api/v1/virtual/lessons/*/complete").authenticated()
.requestMatchers(HttpMethod.GET,  "/api/v1/virtual/courses/*/progress").authenticated()
```

Regression test: one parameterized slice case per `(method, path)` pair asserting anonymous → `401`, so a
future reorder above the `permitAll` wildcard fails the build.

### Upsert concurrency

Read-then-write (`findByUserIdAndLessonId` → mutate or create → `save`) inside the `@Transactional`
decorator, one transaction per request. The loser of a concurrent first insert hits
`uq_virtual_lesson_progress_user_lesson` and its transaction rolls back, so the retry must sit **outside**
the transactional proxy: an outermost `RetryOnDuplicateKey*UseCase` decorator (shared static
`DuplicateKeyRetry.once(Supplier<T>)` in `infrastructure/transaction/`) re-invokes the delegate once in a
fresh transaction, which then finds the winner's row and updates it — last write wins, per closed
decision. Rejected: native `INSERT ... ON DUPLICATE KEY UPDATE` — it bypasses the mapper and would force
the "complete never moves position" rule to be duplicated in SQL.

### Stale position after lesson edit — confirmed

Clamp on read only: both GETs return `min(stored, durationMinutes * 60)`. The stored value is never
rewritten (student data is never destroyed). Write-side validation uses the duration at write time.

### Domain vs projection

| Type | Location | Why |
|---|---|---|
| `LessonProgress` | `domain/model` | Owns the invariants: `0 ≤ position ≤ maxSeconds` (→ `InvalidLessonPositionException`), and `markCompleted()` sets `completed`/`completedAt` **without touching `position` or `positionUpdatedAt`** (decisions 7 and 3) and is a no-op when already complete. `positionUpdatedAt` is a nullable `Instant` end-to-end (JPA entity, mapper, domain). Immutable + `with*`, mirroring `VirtualLesson` |
| `LessonProgressId` | `domain/model` | Value-object convention (`LessonId`, `ModuleId`) |
| `CourseProgressView` | `application/dto` (record) | Derived, never stored, no identity, denominator owned by the repository — an aggregate here would be fiction. Overrides the proposal's tentative `domain/model/CourseProgress` |
| `CourseProgressAssembler` | `application/usecase`, pure static | Percentage + resume selection unit-testable with zero mocks (0.95 gate) |

### Access gates and error mapping

Lesson endpoints reuse `LessonAccessPolicy.decide(...)` (free / preview / entitlement, fail-closed)
(decision 1). The course aggregate uses a new `CourseProgressAccessPolicy` calling
`VirtualCourseEntitlementPort.resolveCourseAccess` directly with the same fail-closed `catch
(RuntimeException) → deny`; no free/preview exception (decision 5). Course unknown or not `PUBLISHED` →
404 before the entitlement check (the catalog is already public, so nothing new leaks).

New marker `@VirtualStudentEndpoint` + `VirtualStudentProgressExceptionHandler` (reusing virtual's
`ProblemDetails`):

| Exception | Status | Code |
|---|---|---|
| `InvalidLessonPositionException` | 400 | `INVALID_LESSON_POSITION` |
| `ForbiddenLessonAccessException` | 403 | `LESSON_FORBIDDEN_SUBSCRIPTION_REQUIRED` |
| `ForbiddenCourseProgressException` (new) | 403 | `COURSE_PROGRESS_FORBIDDEN_SUBSCRIPTION_REQUIRED` |
| `LessonNotFoundException` / `CourseNotFoundException` | 404 | existing codes |
| `IllegalArgumentException` (malformed path id) | 404 | anti-enumeration, as the public chain |

The typed position exception is declared before the generic `IllegalArgumentException` handler — that
ordering is the whole reason `@PublicVirtualEndpoint` cannot be reused.

## Data Flow

    Controller (token subject) ──→ *UseCase port
        │                              │
        │                  RetryOnDuplicateKey (writes) ──→ Transactional* ──→ *UseCaseImpl
        │                                                                          │
        │                          LessonAccessPolicy / CourseProgressAccessPolicy ─┤
        │                                    │                                      │
        │                          VirtualCourseEntitlementPort (:api:shared)   LessonProgressRepository
        │                                                                          │
        └── @VirtualStudentEndpoint advice ── problem+json          virtual_lesson_progress (JPA adapter)

## File Changes

| File | Action | Description |
|---|---|---|
| `api/app/.../db/migration/V19__virtual_lesson_progress.sql` | Create | Table above |
| `api/auth/.../security/SecurityConfig.java` | Modify | Four `.authenticated()` matchers before line 135 |
| `api/virtual/.../domain/model/LessonProgress.java`, `LessonProgressId.java` | Create | Entity + id VO |
| `api/virtual/.../domain/exception/InvalidLessonPositionException.java`, `ForbiddenCourseProgressException.java` | Create | `extends BusinessException` |
| `api/virtual/.../application/port/in/{Save,Get}LessonProgressUseCase.java`, `CompleteLessonUseCase.java`, `GetCourseProgressUseCase.java` | Create | Four ports |
| `api/virtual/.../application/port/out/LessonProgressRepository.java` | Create | `findByUserIdAndLessonId`, `save`, `findRowsForUserAndCourse`, `countLessonsByCourseId` |
| `api/virtual/.../application/usecase/*UseCaseImpl.java`, `CourseProgressAccessPolicy.java`, `CourseProgressAssembler.java` | Create | Four impls + policy + pure assembler |
| `api/virtual/.../application/dto/` | Create | Commands + `LessonProgressView`, `CourseProgressView` |
| `api/virtual/.../infrastructure/transaction/Transactional{SaveLessonProgress,CompleteLesson}UseCase.java`, `RetryOnDuplicateKey*`, `DuplicateKeyRetry.java` | Create | Write decorators |
| `api/virtual/.../infrastructure/persistence/{entity,mapper,adapter,repository}/` | Create | Entity, mapper, adapter, JPA repo + `CourseProgressRowProjection` |
| `api/virtual/.../infrastructure/web/controller/VirtualStudentProgressController.java`, `VirtualStudentEndpoint.java`, `VirtualStudentProgressExceptionHandler.java` | Create | Routes + marker + advice |
| `api/virtual/.../infrastructure/config/VirtualConfiguration.java` | Modify | Bean wiring for the four use cases + policy |
| `bruno/API - Direct/virtual/*.bru` | Create | Four requests |

## Interfaces / Contracts

```java
public interface SaveLessonProgressUseCase {
    LessonProgressView save(String lessonId, UUID actingUserId, int positionSeconds);
}
public interface CompleteLessonUseCase {          // never moves positionSeconds
    LessonProgressView complete(String lessonId, UUID actingUserId);
}
public record CourseProgressView(
    String courseId, int completedLessons, int totalLessons, int percentage,
    ResumeLesson resumeLesson) {                  // resumeLesson == null on an empty course (decision 8)
    public record ResumeLesson(String lessonId, String moduleId, int positionSeconds, boolean completed) { }
}
```

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Domain | Position bounds, `markCompleted` idempotence + position immobility | Plain JUnit, no mocks |
| Application | Both access policies incl. fail-closed branch; assembler (0 lessons, all complete, rounding, tie-break) | Mockito on ports; assembler pure |
| Infrastructure | Mapper, adapter, projection ordering (incl. "save position in lesson 5, then complete lesson 2 → resume is still lesson 5", and a NULL `positionUpdatedAt` row losing to a non-NULL one), `DuplicateKeyRetry`, advice status/code mapping | `@DataJpaTest` + Testcontainers for the projection; plain unit tests elsewhere |
| Security | Four `(method, path)` pairs → 401 anonymous | `SecurityConfig` slice test, one case per pair |
| E2E | Save → resume → complete → aggregate | Bruno collection (slice 3) |

## Threat Matrix

N/A — no routing-authority, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. HTTP route registration and Spring Security matcher ordering are covered by
the dedicated 401 regression tests above, not by that matrix.

## Migration / Rollout

`V19` is additive: `CREATE TABLE` only, no backfill, no existing table altered. Rollback reverts the merge
and leaves `V19` applied. Three independently deliverable slices: (1) `SecurityConfig` matchers + 401 tests
+ V19; (2) domain, persistence, `PUT`/`GET` progress, `POST /complete`, advice chain; (3) course aggregate
+ Bruno.

## Open Questions

- [ ] None blocking. All nine product decisions are closed and bound above.

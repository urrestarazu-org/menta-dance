# Exploration: Virtual lesson progress

**Change**: `virtual-lesson-progress`
**Issue**: [#52](https://github.com/urrestarazu-org/menta-dance/issues/52) — US-VIRTUAL-005, "Guardar y completar progreso de lección"
**Milestone**: v0.3.0 — Suscripción y aprendizaje virtual E2E
**Date**: 2026-09-04
**Phase**: explore (read-only investigation; no implementation)

## Current state

Greenfield: `rg -il 'progress' api/virtual/src` returns zero matches. No domain model,
no table, no endpoint exists for lesson progress today.

The `api/virtual` module is otherwise mature. It has `VirtualCourse`, `VirtualModule`
and `VirtualLesson` aggregates — all modelled as mutable snapshot rows, never event
logs — plus a `LessonAccessPolicy` gating premium content behind the cross-module
`VirtualCourseEntitlementPort` (Billing).

### End-to-end write pattern to imitate

```
UpdateVirtualLessonUseCase              (application/port/in)
  → UpdateVirtualLessonUseCaseImpl      (application/usecase)
  → TransactionalUpdateVirtualLessonUseCase
                                        (infrastructure/transaction, adds @Transactional)
  → bean wiring in VirtualConfiguration.java:300-308
  → VirtualLessonAdminController.update() at VirtualLessonAdminController.java:36-53
  → VirtualLessonJpaEntity / mapper / VirtualLessonRepositoryAdapter
```

Read use cases (`GetPublicLessonUseCaseImpl`, `GetPublicLessonStreamUseCaseImpl`) skip
the transactional decorator and are wired as plain `@Component` beans.

## Critical risk: SecurityConfig would expose the new endpoints anonymously

`SecurityConfig.java:148-150` grants `permitAll()` to:

```java
"/api/v1/virtual/lessons",
"/api/v1/virtual/lessons/**"
```

This rule carries **no `HttpMethod` restriction**, unlike every other method-scoped rule
in the same file (lines 154, 160, 165, 170, 173, 176, 193 all name `HttpMethod.X`
explicitly). The planned `PUT /api/v1/virtual/lessons/{lessonId}/progress`,
`GET .../progress` and `POST .../complete` all nest under this wildcard and would be
reachable anonymously at the Spring Security layer — directly contradicting the story's
"el alumno sólo modifica/consulta su propio progreso".

`GET /api/v1/virtual/courses/{courseId}/progress` has no matcher at all and falls through
to `anyRequest().access(roleAuthorizationManager)` at `SecurityConfig.java:198-200`, whose
own comment states that "unmapped paths fall through to a grant so controller-layer checks
apply".

This exact trap has already bitten the project once and is documented in place: lines
166-170 explain that `DELETE /api/v1/billing/subscriptions/me` (#130) needed its own
explicit entry because Spring Security matches per method, or it would have fallen through
to a grant.

**Design must add explicit, method-scoped, `authenticated()` matchers ahead of the broad
`permitAll` rule** — the file uses first-match-wins ordering throughout.

## Six open questions

### 1. Does saving progress require an active subscription?

`LessonAccessPolicy.decide(lesson, module, actingUserId)` at `LessonAccessPolicy.java:37-60`
is the single existing access-decision path: free lesson → `PUBLIC_FREE`, preview module →
`PUBLIC_MODULE_PREVIEW`, otherwise a Billing entitlement check. Both public read use cases
reuse it.

Reusing the same policy for save-progress keeps "can save progress" consistent with
"can stream". Whether to gate it at all remains a product decision.

### 2. What is the denominator of the derived course percentage?

No per-lesson or per-module *publish* flag exists — only course-level `CourseStatus`
(`DRAFT`/`PUBLISHED`/`ARCHIVED`, `CourseStatus.java:4-8`). `VirtualModule.preview` and
`VirtualLesson.free` are *access* flags, not publish flags.

`VirtualCourse.moduleCount` / `lessonCount` are unfiltered `COUNT()`s over all rows
(`VirtualCourseRepositoryAdapter.java:62,92,124`); no filtered variant exists. So for a
`PUBLISHED` course, "all lessons" already equals "all published lessons".

Unresolved: an admin adding a lesson to an already-published course instantly changes every
student's denominator. No versioning or enrollment-snapshot concept exists in this module.

### 3. What is "última lección"?

`VirtualModule.order` and `VirtualLesson.order` (persisted as `display_order`, V11 migration)
provide a deterministic total order, and `findByModuleId` / `findByCourseId` return
pre-sorted ascending results. That machinery can back a curriculum-position tie-break.

"Last touched" vs "last completed" vs "furthest along" is entirely new territory — no
resume-point concept exists today.

### 4. Position unit

`VirtualLesson` stores only `durationMinutes` (whole minutes; `virtual_lessons.duration_minutes
INT`, `CHECK >= 0`, V6 migration). Two duration formatters
(`GetPublicLessonUseCaseImpl.java:222-224`, `GetPublicLessonStreamUseCaseImpl.java:180-182`)
hard-code the seconds component to `00`, with an explicit comment anticipating a
seconds-level migration that was never built.

Consequence: upper-bound validation of a seconds-based position against
`durationMinutes * 60` is only minute-granular, never exact.

### 5. Does completing a lesson imply end-of-position?

No existing constraint, since no position or completion concept exists yet. The story
explicitly separates the two triggers ("ver 90% de video no produce completitud automática"),
which argues for independent fields rather than derived state.

### 6. Lesson deleted or unpublished after progress was recorded

`virtual_lessons` / `virtual_modules` reference their parents with **no `ON DELETE CASCADE`**
(V6 migration). The only delete path, `DeleteVirtualCourseUseCaseImpl.java:30-48`, requires
the course be `DRAFT` (`CourseNotDraftException` guard) and deletes lessons → modules →
course manually.

A `PUBLISHED` course — the only kind a student could have progress against — therefore cannot
be deleted today. The real exposure is smaller than the story implies: lesson *editing* is not
restricted by course status, so a lesson's `videoId` or duration can change underneath a saved
position, but the row will not disappear.

## Identity extraction

| Surface | Method | Behaviour |
| --- | --- | --- |
| Admin / management | `VirtualLessonAdminController.actingUserId()` (line 55-57) | Hard `UUID.fromString(authentication.getName())` |
| Public | `VirtualPublicLessonController.actingUserIdOrNull()` (lines 146-160) | Null-safe: handles null, unauthenticated, blank, `anonymousUser`, malformed UUID |

Progress endpoints need mandatory auth (admin-style extraction) while sitting under a path
prefix that is currently public — see the SecurityConfig risk above.

## Other mapped conventions

- **Problem responses**: `ProblemDetails` (RFC 9457) is duplicated per module by design;
  virtual's copy is at `infrastructure/web/ProblemDetails.java:17-34`.
- **Exception handling**: two `@RestControllerAdvice` chains keyed by marker annotations —
  `@VirtualManagementEndpoint` → `VirtualCourseExceptionHandler`, and `@PublicVirtualEndpoint`
  → `VirtualPublicLessonExceptionHandler`. A new endpoint must pick or extend one, because
  `IllegalArgumentException` handling differs (the public chain collapses to an
  anti-enumeration 404).
- **Transactions**: the decorator wraps write use cases only; reads skip it.
- **ArchUnit** (`api/virtual/src/test/java/com/menta/virtual/ArchitectureTest.java`): domain is
  isolated from application, infrastructure, Spring and JPA; application is isolated from
  infrastructure; domain and application must not reach `com.menta.billing.infrastructure..`
  and must go through `VirtualCourseEntitlementPort` in `:api:shared`.
- **Coverage gate** (`api/virtual/build.gradle.kts:56-63`): domain + application BUNDLE
  ≥ 0.95 LINE; infrastructure BUNDLE ≥ 0.90 LINE. This matches `CLAUDE.md`; any older note
  citing different numbers is stale.
- **Bruno layout**: `bruno/API - Direct/virtual/*.bru` (public),
  `bruno/API - Direct/virtual/admin/*.bru` (management),
  `bruno/E2E/{scenario}/{NN-phase}/{NN request}.bru` (numbered journeys). Closest E2E
  precedent: `bruno/E2E/bunny-net/02-journey/`.
- **Migrations**: `api/app/src/main/resources/db/migration/`; highest existing is
  `V18__billing_subscription_trial.sql`, so this change would add `V19`.

## Candidate approaches

### A. Mutable upsert row per `(user_id, lesson_id)` — recommended

New `virtual_lesson_progress` table, following the same use-case → transactional decorator →
controller → JPA pattern as every other virtual write.

- **Pros**: naturally idempotent; cheap single-row reads; trivial completed-count for the
  course aggregate; matches every other aggregate's shape in this module.
- **Cons**: no position history.
- **Effort**: low–medium.

### B. Append-only event log

Mirrors `virtual_course_audit`.

- **Pros**: full history and audit trail.
- **Cons**: every read needs a "latest row per lesson" query, which is heavier for the
  course-aggregate endpoint; no precedent for that shape in this module; weaker fit for the
  literal idempotency requirement.
- **Effort**: medium–high.

## Affected areas

- `api/virtual/.../domain/model/` — new progress aggregate
- `api/virtual/.../application/` — new use cases and ports for save / get / complete
- `api/virtual/.../infrastructure/persistence/` — new entity, mapper, adapter
- `api/app/src/main/resources/db/migration/V19__*.sql` — new table
- `api/virtual/.../infrastructure/web/controller/` — new controller and exception-handler chain
- `api/auth/.../infrastructure/security/SecurityConfig.java` — explicit authenticated matchers
- `bruno/API - Direct/virtual/` and a new E2E journey under `bruno/E2E/`

## Risks carried into proposal

1. **SecurityConfig grants anonymous access** to the exact paths the new endpoints must live
   under. Must be resolved explicitly, not implicitly. Highest priority.
2. **Percentage-denominator volatility** when lessons are added post-publish — unresolved
   product policy.
3. **Position precision** is capped by whole-minute duration storage.
4. **No reusable ownership guard**: existing `CourseOwnershipGuard` / `LessonOwnershipGuard`
   guard `professorId` (admin ownership), not "this progress row belongs to this student".
   A new guard shape is required.

## Ready for proposal

Yes — with the SecurityConfig gap as an explicit, non-negotiable item.

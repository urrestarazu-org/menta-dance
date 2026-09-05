# Tasks: Virtual Lesson Progress (#52, US-VIRTUAL-005)

## Review Workload Forecast

| Field | Value |
|---|---|
| Review budget (this change) | 800 changed lines per PR |
| Estimated changed lines — Slice 1 | ~150–180 (confirmed) |
| Estimated changed lines — Slice 2 | ~605–650 (confirmed, flagged tight) |
| Estimated changed lines — Slice 3 | ~370–420 (confirmed) |
| Estimated total across the change | ~1,150–1,250 |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main (Git Flow: target is `develop`, this repo's integration branch) |

```text
Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
800-line budget risk: Low (Slice 1) / Medium (Slice 2, ~76% of budget) / Low-Medium (Slice 3)
```

`auto-chain` means the orchestrator proceeds straight to Slice 1 with the chain
strategy below — no user prompt required before `sdd-apply`. Slice 2 is the
one to watch: strict-TDD test volume against the 0.95/0.90 coverage gate can
push it toward 700+ lines. If it crosses 800, split the transaction-decorator
work (`RetryOnDuplicateKey*`, `DuplicateKeyRetry`) into its own follow-up PR
before merging the use cases — do not silently let Slice 2 exceed budget.

**Correction to the design's forecast**: no dedicated Testcontainers
migration-history test for V19 (unlike V18's
`SubscriptionTrialMigrationIntegrationTest`, ~218 lines). V18 needed one
because it altered a table with pre-existing rows (rehydration-safety). V19
is `CREATE TABLE` only, no backfill, no existing row to protect — the design's
own "Migration / Rollout" section says so. Slice 1 stays lean; V19 is proven
by the app's normal Flyway-on-context-load path.

**Correction to the design's File Changes table**: `LessonProgressRepository`
(out port) is authored incrementally, not as one interface. Slice 2 declares
`findByUserIdAndLessonId` + `save`; Slice 3 *adds* `findRowsForUserAndCourse`
+ `countLessonsByCourseId` in the same PR that implements them. This keeps
Slice 2 compiling and shippable without stub methods for a query Slice 3 owns.
Likewise `ForbiddenCourseProgressException` moves from the domain-exception
task in Slice 2 to Slice 3, next to its only caller (`CourseProgressAccessPolicy`).

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | Close the anonymous-access hole + V19 schema | PR 1 | `./gradlew :api:auth:test --tests "*SecurityConfigTest*"` | N/A — pure Spring Security slice test, no external system | Revert `SecurityConfig.java` matcher block + `V19` migration; no other surface reads the new table yet |
| 2 | Save/read/complete one lesson's progress | PR 2 | `./gradlew :api:virtual:test` | N/A for domain/application/infra unit tests; Bruno manual run against local API optional, not required to merge | Revert the `virtual_lesson_progress` write/read path; V19 table stays empty, no data loss |
| 3 | Course aggregate + resume point + Bruno | PR 3 | `./gradlew :api:virtual:test --tests "*CourseProgress*"` | Testcontainers MySQL 8.0 for the projection-ordering test (the one case the design requires it for) | Revert the course-aggregate endpoint and projection query; lesson-level progress from PR 2 is unaffected |

## Slice Ordering Rationale

**Slice 1 must land first and merge to `develop` before Slice 2 begins.**
`SecurityConfig.java:148-150` grants `permitAll()` to `/api/v1/virtual/lessons/**`
with no `HttpMethod` restriction, and `GET /courses/{id}/progress` has no
matcher at all and falls through to `anyRequest().access(...)` — a grant for
unmapped paths. If Slice 2's endpoints existed before Slice 1's matchers land,
every one of them would be anonymously reachable in `develop` for the
duration between merges. Reordering slices for convenience reopens the exact
hole the proposal calls out as the highest-priority risk. Slice 3 depends on
Slice 2's `LessonProgress` persistence and `LessonProgressRepository`, so it
must come last.

Chain strategy is **stacked-to-main** (interpreting "main" as `develop`, this
repo's integration branch per Git Flow): each slice is independently
deliverable and verifiable per the design's own "Migration / Rollout"
section, so each PR merges to `develop` in order rather than stacking on an
unmerged tracker branch.

## Slice 1 — Security foundation + schema

**Branch**: `feature/virtual-lesson-progress-security-schema` off `develop`,
merges back into `develop`.

- [x] 1.1 RED: In `api/auth/src/test/java/com/menta/auth/infrastructure/security/SecurityConfigTest.java`, add four `@Test` methods (matching the file's existing one-method-per-case style, not `@ParameterizedTest`) asserting `401` for anonymous `PUT /api/v1/virtual/lessons/{id}/progress`, `GET .../progress`, `POST .../complete`, `GET /api/v1/virtual/courses/{id}/progress`.
- [x] 1.2 Verify RED: run `./gradlew :api:auth:test --tests "*SecurityConfigTest*"` — confirm the new cases fail with `404`/`200` (falls through the `permitAll` wildcard or `anyRequest().access(...)`), not a compile error.
- [x] 1.3 GREEN: in `SecurityConfig.java`, insert the four `.requestMatchers(HttpMethod.X, "...").authenticated()` matchers immediately before the `permitAll` block at line 135, per the `DELETE /billing/subscriptions/me` precedent (lines 166-170). Add the same kind of inline comment explaining why each method-scoped entry is required.
- [x] 1.4 Verify GREEN: re-run `./gradlew :api:auth:test` — new cases pass `401`; full `SecurityConfigTest` suite and `:api:auth:test` stay green (no existing matcher regressed).
- [x] 1.5 Create `api/app/src/main/resources/db/migration/V19__virtual_lesson_progress.sql` with the `virtual_lesson_progress` table from `design.md` (surrogate `id`, unique `(user_id, lesson_id)`, FKs to `virtual_lessons`/`virtual_courses` without `ON DELETE CASCADE`, `chk_virtual_lesson_progress_position` check).
- [x] 1.6 Verify: `./gradlew :api:app:test` — existing Spring context-load integration tests apply `V19` cleanly as part of the normal migration chain (no dedicated migration test needed; see forecast correction above).
- [x] 1.7 Run `./gradlew check` to confirm the slice is green end-to-end (Checkstyle, ArchUnit unaffected, ordinary test suites) before opening PR 1.

## Slice 2 — Lesson progress core (save, read, complete)

**Branch**: `feature/virtual-lesson-progress-core` off `develop` (cut after
Slice 1 merges), merges back into `develop`.

### Domain

- [x] 2.1 RED: `api/virtual/src/test/java/com/menta/virtual/domain/model/LessonProgressTest.java` — position must satisfy `0 ≤ position ≤ maxSeconds` (throws `InvalidLessonPositionException` outside bounds); `markCompleted()` sets `completed`/`completedAt` without touching `position`/`positionUpdatedAt`; `markCompleted()` is a no-op when already complete.
- [x] 2.2 Verify RED: `./gradlew :api:virtual:test --tests "*LessonProgressTest*"` fails for the missing class/methods, not a typo.
- [x] 2.3 GREEN: create `domain/model/LessonProgress.java` (immutable + `with*`, mirrors `VirtualLesson`), `domain/model/LessonProgressId.java` (value-object convention, mirrors `LessonId`), `domain/exception/InvalidLessonPositionException.java` (`extends BusinessException`).
- [x] 2.4 Verify GREEN: re-run the domain test class — all green.

### Application: ports and DTOs

- [x] 2.5 Create `application/port/in/SaveLessonProgressUseCase.java` (`LessonProgressView save(String lessonId, UUID actingUserId, int positionSeconds)`), `GetLessonProgressUseCase.java` (`Optional<LessonProgressView> get(String lessonId, UUID actingUserId)`), `CompleteLessonUseCase.java` (`LessonProgressView complete(String lessonId, UUID actingUserId)`).
- [x] 2.6 Create `application/port/out/LessonProgressRepository.java` with `Optional<LessonProgress> findByUserIdAndLessonId(UUID, LessonId)` and `LessonProgress save(LessonProgress)` only — the two course-aggregate query methods are added in Slice 3.
- [x] 2.7 Create `application/dto/LessonProgressView.java` (record: lessonId, positionSeconds, completed, completedAt).

### Application: use cases (Mockito on ports, no Spring context)

- [x] 2.8 RED: `application/usecase/SaveLessonProgressUseCaseImplTest.java` — free lesson without entitlement saves and returns `200`-equivalent view; protected lesson without entitlement throws `ForbiddenLessonAccessException`; out-of-bounds position throws `InvalidLessonPositionException` before any repository write; repeating the identical position calls `repository.save` with unchanged persisted state (idempotent, spec scenario "Repeated identical save").
- [x] 2.9 Verify RED, then GREEN: implement `SaveLessonProgressUseCaseImpl.java` calling `LessonAccessPolicy.decide(...)`, clamping the read-time-vs-write-time duration bound at write time, `findByUserIdAndLessonId` → mutate-or-create → `save`.
- [x] 2.10 RED: `application/usecase/GetLessonProgressUseCaseImplTest.java` — no saved row returns a default view (`position = 0`, `completed = false`) once access is granted, not `Optional.empty()`; a lapsed-subscription/protected-lesson denial throws `ForbiddenLessonAccessException` (spec: "A lapsed subscriber loses read access"); unknown lesson id yields `Optional.empty()` (anti-enumeration, mirrors `GetPublicLessonUseCaseImpl`); returned `positionSeconds` is clamped to `min(stored, durationMinutes * 60)` at read time (stale-position-after-edit rule).
- [x] 2.11 Verify RED, then GREEN: implement `GetLessonProgressUseCaseImpl.java`.
- [x] 2.12 RED: `application/usecase/CompleteLessonUseCaseImplTest.java` — completing sets `completed = true` and leaves `positionSeconds`/`positionUpdatedAt` untouched (spec: "Completing a lesson does not move its saved position"); repeating is a no-op returning the same state; access cascade denies identically to save/get.
- [x] 2.13 Verify RED, then GREEN: implement `CompleteLessonUseCaseImpl.java`.

### Infrastructure: write-side concurrency and transactions

- [x] 2.14 Create `infrastructure/transaction/DuplicateKeyRetry.java` (shared static `once(Supplier<T>)` helper).
- [x] 2.15 RED: a `DuplicateKeyRetryTest.java` (or inline in the decorator tests) proving one retry on `DataIntegrityViolationException` and no retry on any other exception type.
- [x] 2.16 GREEN: implement `DuplicateKeyRetry`, then `infrastructure/transaction/TransactionalSaveLessonProgressUseCase.java`, `TransactionalCompleteLessonUseCase.java`, `RetryOnDuplicateKeySaveLessonProgressUseCase.java`, `RetryOnDuplicateKeyCompleteLessonUseCase.java` — outermost retry decorator wraps the transactional decorator, one transaction per attempt, per design's "Upsert concurrency" section.

### Infrastructure: persistence (plain unit tests, no `@DataJpaTest`)

- [x] 2.17 RED: `infrastructure/persistence/mapper/LessonProgressJpaMapperTest.java` — round-trip domain ↔ entity, including a null `positionUpdatedAt`.
- [x] 2.18 GREEN: create `infrastructure/persistence/entity/LessonProgressJpaEntity.java`, `infrastructure/persistence/mapper/LessonProgressJpaMapper.java`.
- [x] 2.19 RED: `infrastructure/persistence/adapter/LessonProgressRepositoryAdapterTest.java` — Mockito against a mocked `LessonProgressJpaRepository`, mirroring `VirtualLessonRepositoryAdapterTest`'s pattern (no Spring context).
- [x] 2.20 GREEN: create `infrastructure/persistence/repository/LessonProgressJpaRepository.java` (Spring Data interface) and `infrastructure/persistence/adapter/LessonProgressRepositoryAdapter.java`.

### Infrastructure: web

- [x] 2.21 Create `infrastructure/web/controller/VirtualStudentEndpoint.java` (marker annotation).
- [x] 2.22 RED: `infrastructure/web/controller/VirtualStudentProgressExceptionHandlerTest.java` — `InvalidLessonPositionException` → `400`/`INVALID_LESSON_POSITION`; `ForbiddenLessonAccessException` → `403`/`LESSON_FORBIDDEN_SUBSCRIPTION_REQUIRED`; `LessonNotFoundException` → `404`; `IllegalArgumentException` (malformed id) → `404` anti-enumeration; confirm the typed position exception is declared before the generic `IllegalArgumentException` handler.
- [x] 2.23 GREEN: create `infrastructure/web/controller/VirtualStudentProgressExceptionHandler.java` reusing virtual's `ProblemDetails`.
- [x] 2.24 RED: `infrastructure/web/controller/VirtualStudentProgressControllerTest.java` (`@WebMvcTest` or MockMvc-with-mocked-use-cases, no security context) — `PUT`/`GET .../progress` and `POST .../complete` wire request → command → use case → response for the success and each denial path; `actingUserId` extraction mirrors `VirtualLessonAdminController.actingUserId()`, never from path/body.
- [x] 2.25 GREEN: create `infrastructure/web/controller/VirtualStudentProgressController.java`.
- [x] 2.26 Wire the four beans (three use cases behind their decorators, plus reuse of the existing `LessonAccessPolicy` bean) in `infrastructure/config/VirtualConfiguration.java`.
- [x] 2.27 Verify: `./gradlew :api:virtual:test` and `./gradlew :api:virtual:jacocoDomainApplicationCoverageVerification :api:virtual:jacocoInfrastructureCoverageVerification` both pass at 0.95/0.90 BUNDLE.
- [x] 2.28 Run `./gradlew check` (ArchUnit: domain/application untouched by `com.menta.billing.infrastructure..`; Checkstyle 100-col) before opening PR 2.

## Slice 3 — Course aggregate, resume point, Bruno

**Branch**: `feature/virtual-lesson-progress-course-aggregate` off `develop`
(cut after Slice 2 merges), merges back into `develop`.

### Application: extend the out port, assembler, access policy

- [x] 3.1 Extend `application/port/out/LessonProgressRepository.java` with `List<CourseProgressRowProjection> findRowsForUserAndCourse(UUID userId, UUID courseId)` and `long countLessonsByCourseId(UUID courseId)`. **Deviation**: `CourseProgressRowProjection` lives in `application/port/out/` (not `infrastructure/persistence/projection/` as literally written in 3.10) — the out port cannot reference an infrastructure type under the ArchUnit-gated `application_should_not_depend_on_infrastructure` rule.
- [x] 3.2 Create `application/dto/CourseProgressView.java` (record with nested `ResumeLesson`, per `design.md`'s `Interfaces/Contracts`).
- [x] 3.3 RED: `application/usecase/CourseProgressAssemblerTest.java` — zero-lesson course → `percentage = 0`, null resume; all-complete → `percentage = 100`; half-up rounding case; resume tie-break by `module.display_order`, `lesson.display_order`, `lesson_id` when timestamps tie or are all null; a row with null `positionUpdatedAt` never wins resume selection over a non-null row.
- [x] 3.4 GREEN: implement `application/usecase/CourseProgressAssembler.java` as a pure static function consuming an already-ordered row list (no timestamp comparison in Java, per design — SQL `ORDER BY` owns that).
- [x] 3.5 Create `domain/exception/ForbiddenCourseProgressException.java` (`extends BusinessException`).
- [x] 3.6 RED: `application/usecase/CourseProgressAccessPolicyTest.java` — entitled student → allow; no entitlement (including free/preview-only progress) → deny; lapsed entitlement → deny; entitlement-port `RuntimeException` → fail-closed deny. (Unknown/non-`PUBLISHED` course → 404 is exercised at `GetCourseProgressUseCaseImplTest` level, task 3.8 — the policy itself only ever receives an already-resolved course, mirroring `LessonAccessPolicy`/`LessonAccessPolicyTest`.)
- [x] 3.7 GREEN: implement `application/usecase/CourseProgressAccessPolicy.java` calling `VirtualCourseEntitlementPort.resolveCourseAccess` directly (no free/preview exception, unlike `LessonAccessPolicy`).
- [x] 3.8 RED: `application/usecase/GetCourseProgressUseCaseImplTest.java` — two bounded reads only (count + row projection, mocked ports); zero-lesson course returns a `200`-shaped zeroed view, never an empty/not-found signal; a completed lesson that later became inaccessible still counts toward `completedLessons`.
- [x] 3.9 GREEN: create `application/port/in/GetCourseProgressUseCase.java` and implement `application/usecase/GetCourseProgressUseCaseImpl.java`. **Deviation**: the use case throws `CourseNotFoundException` directly for an unknown/malformed/non-`PUBLISHED` course id (no `Optional` wrapper) — mirrors the write-side lesson use cases' `LessonNotFoundException` pattern, not `GetLessonProgressUseCase`'s `Optional.empty()` shape, since design.md's course-progress contract has no anti-enumeration `Optional` in its signature.

### Infrastructure: projection (the one Testcontainers case)

- [x] 3.10 Create `CourseProgressRowProjection` (interface projection: `lessonId, moduleId, positionSeconds, completed, positionUpdatedAt, lessonOrder, moduleOrder`) and the backing query on `LessonProgressJpaRepository`, joining progress → lesson → module with `ORDER BY p.position_updated_at DESC, m.display_order ASC, l.display_order ASC, p.lesson_id ASC`. Done ahead-of-schedule in the 3.1 groundwork (see that task's deviation note for the `application/port/out/` placement). **Further deviation found while writing 3.11's Testcontainers test**: the query is JPQL with an ad-hoc `JOIN ... ON` between `LessonProgressJpaEntity`/`VirtualLessonJpaEntity`/`VirtualModuleJpaEntity`, NOT `nativeQuery = true` as originally written here — a native query returns raw JDBC `byte[]` for every `BINARY(16)` column, which Spring Data's interface-projection factory cannot convert to `UUID` on its own (`UnsupportedOperationException: Cannot project byte[] to java.util.UUID`). This only surfaces against a real database, never against Mockito — exactly why task 3.11 exists.
- [x] 3.11 RED: `infrastructure/persistence/adapter/LessonProgressRepositoryAdapterProjectionTest.java`, `@DataJpaTest` + `@Testcontainers` MySQL 8.0 — proves "save position in a played lesson, then complete a DIFFERENT never-played lesson (lower display_order) → resume is still the played lesson" i.e. a NULL `position_updated_at` row losing to a non-NULL one under real MySQL `NULLS LAST` semantics for `ORDER BY ... DESC`, and that timestamp ordering wins over curriculum order (not just a tie-break). This RED was "wrong symbol" only in the sense that the JPQL rewrite (3.10's deviation) did not exist yet — the first run against native SQL failed for a real behavioral reason (`UnsupportedOperationException`), which is the correct RED for an infrastructure-boundary test.
- [x] 3.12 GREEN: implement the query and `countLessonsByCourseId` on `infrastructure/persistence/repository/LessonProgressJpaRepository.java`, wire both new adapter methods. Adapter wiring done in the 3.1 groundwork; the query itself fixed to JPQL per 3.10's deviation note to pass 3.11.
- [x] 3.13 Verify: `./gradlew :api:virtual:test --tests "*CourseProgress*"` — Testcontainers case passes; confirmed it is the only container-backed (`@Testcontainers`) test added by this change (`grep -rl "@Testcontainers" api/virtual/src/test/` → exactly this one file; the repo's only other `@Testcontainers` class is the pre-existing, unrelated `api/app` `SubscriptionTrialMigrationIntegrationTest` from #131).

### Infrastructure: web and wiring

- [x] 3.14 Extend `infrastructure/web/controller/VirtualStudentProgressExceptionHandler.java` (or add a case to the same advice, still keyed on `@VirtualStudentEndpoint`) for `ForbiddenCourseProgressException` → `403`/`COURSE_PROGRESS_FORBIDDEN_SUBSCRIPTION_REQUIRED`. Also added a `CourseNotFoundException` → `404` mapping (needed for `GetCourseProgressUseCaseImpl`'s anti-enumeration path; the existing `IllegalArgumentException`/`LessonNotFoundException` handlers don't cover it) plus RED/GREEN tests in `VirtualStudentProgressExceptionHandlerTest.java`.
- [x] 3.15 RED: extend `VirtualStudentProgressControllerTest.java` with `GET /api/v1/virtual/courses/{courseId}/progress` cases: entitled success, no-entitlement 403, unknown course anti-enumeration, empty-course zeroed 200.
- [x] 3.16 GREEN: add the course-progress route to `VirtualStudentProgressController.java`. **Deviation** (documented in-flight, matches the apply-progress plan): removed the class-level `@RequestMapping("/api/v1/virtual/lessons")` and moved full paths onto every method, since the course route's base path (`/api/v1/virtual/courses/**`) differs from the lesson routes' (`/api/v1/virtual/lessons/**`) and Spring always concatenates class + method mappings.
- [x] 3.17 Wire `GetCourseProgressUseCase` and `CourseProgressAccessPolicy` beans in `VirtualConfiguration.java` (+ `VirtualConfigurationTest` coverage). **Note, not a deviation**: `CourseProgressAssembler` is NOT wired as a bean — it is a pure static utility class (private constructor, task 3.4) with no collaborators, called directly by `GetCourseProgressUseCaseImpl`; there is nothing to inject.
- [x] 3.18 Verify: `./gradlew :api:virtual:test` and both `jacocoDomainApplicationCoverageVerification`/`jacocoInfrastructureCoverageVerification` pass.

### E2E and delivery

- [x] 3.19 Create four Bruno requests under `bruno/API - Direct/virtual/` for the four endpoints (`Save Lesson Progress`, `Get Lesson Progress`, `Complete Lesson`, `Get Course Progress`; seq 8-11), following the existing collection's bearer-auth + skip-if-env-var-missing convention (mirrors `Get Entitled Lesson Stream.bru`'s `virtualEntitledLessonId` pattern; introduces `virtualEntitledCourseId` the same undeclared/manually-set way).
- [x] 3.20 Create a numbered E2E journey under `bruno/E2E/bunny-net/03-progress/{13-16}.bru` — save → resume (`GET .../courses/{id}/progress`) → complete → re-read aggregate — mirroring `bruno/E2E/bunny-net/02-journey/`'s structure and continuing its seq numbering (12 → 13-16). Reuses `plannedCourseId`/`plannedProtectedLessonId`/`authToken` already set by `02-journey` (the premium-grant scenario) — no new fixture needed since that course's entitlement is already active by step 12.
- [x] 3.21 Run `./gradlew check` (full suite, ArchUnit, Checkstyle, both coverage gates) before opening PR 3. `BUILD SUCCESSFUL in 11m 41s`, 111 actionable tasks executed, forced with `--rerun-tasks`. Checkstyle reports pre-existing `severity="warning"` violations across every module (including unrelated ones like `api/shared`, `bff`) — this module's Checkstyle task is configured to warn, not fail, matching this repo's established convention (snake_case test method names are the norm throughout, e.g. every pre-existing `*UseCaseImplTest`); none are `severity="error"` and the build exits 0.

## Key Learnings

1. `SecurityConfigTest.java` uses one plain `@Test` method per (method, path) case, not JUnit5 `@ParameterizedTest`, so Slice 1's four new regression cases should match that existing style rather than introduce a new pattern.
2. V19 needs no dedicated Testcontainers migration test unlike V18, because it is a pure additive `CREATE TABLE` with no pre-existing row to protect through an `ALTER`.
3. `VirtualLessonRepositoryAdapterTest` proves adapter/mapper tests in this module are plain Mockito unit tests with no Spring context; only the course-aggregate projection query needs real MySQL via Testcontainers, for `ORDER BY ... DESC` NULL-ordering semantics.
4. Splitting `LessonProgressRepository` across Slice 2 (save/find-by-id) and Slice 3 (course projection queries) keeps each slice independently compilable without stub methods for a query the later slice owns.

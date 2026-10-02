# Tasks: Physical Course Detail with Session Availability (#107)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~720 total: S1 ~46, S2 ~390, S3 ~285 |
| 400-line budget risk | High against 400 (total ~720); Medium against the 800 review budget (S2 is tightest) |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (S1) -> PR 2 (S2) -> PR 3 (S3) |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main (each PR targets `develop`, the integration branch, after the previous one merges) |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|----|----------------------|-----------------|-------------------|
| S1 | Half-open `[from, to)` session query + ascending-order contract | PR 1 (`Refs #107`) | `./gradlew :api:app:test --tests "*PhysicalCourseAvailabilityIntegrationTest"` | Testcontainers MySQL (same test) | Revert `PhysicalSessionJpaRepository` L32, 2 Javadocs and 2 tests; no schema change |
| S2 | Sealed detail, physical detail record, clock, window property, controller, 3 test inversions | PR 2 (`Refs #107`) | `./gradlew :api:app:test --tests "*CatalogCompositionServiceTest" --tests "*CatalogControllerTest" --tests "*CatalogIntegrationTest"` | MockMvc standalone + `CatalogIntegrationTest` (Testcontainers) | Revert S3 first, then S2; virtual path is untouched |
| S3 | Physical HTTP scenarios, OpenAPI `oneOf`, CI lint, Bruno, docs | PR 3 (`Closes #107`) | `./gradlew :api:app:test --tests "*CatalogIntegrationTest"` + `docker run --rm -v "$(pwd)/api/openapi:/spec:ro" redocly/cli:latest lint /spec/catalog-v1.yaml` | Bruno `Get Course - Physical.bru` against `:api:app:bootRun` | Docs/contract/test only; revert alone, no behavior change |

Budget flags: S2 is the tightest (~390). Heaviest tasks: 2.6 (service rewrite, ~35 deleted lines counted) and 2.3/2.4 (unit tests). Pre-agreed split point if S2 trends above ~700: move the additive controller shape pins (task 2.7) to a follow-up PR 2b. Inversions (2.2, 2.8, 2.9) must stay in S2 (design A12).

## Phase 0: Tracking (orchestrator performs; not code)

- [x] 0.1 Board item for #107 on project 1: status In progress, fields set, before S1 starts.
- [x] 0.2 Move the item to In review when PR 1, PR 2 and PR 3 are each opened.
- [x] 0.3 Ensure the final PR body contains `Closes #107` (automation sets Done on merge); PR 1 and PR 2 use `Refs #107`.

## Phase 1: S1 - Half-open bound (PR 1, branch `feature/catalog-sessions-s1-half-open-bound`)

- [x] 1.1 RED: in `api/app/src/test/java/com/menta/app/integration/physical/PhysicalCourseAvailabilityIntegrationTest.java` add `a_session_exactly_at_the_upper_bound_is_excluded_and_one_at_the_lower_bound_included` (specs: "Session exactly at from/to"); run, confirm it fails against `BETWEEN`.
- [x] 1.2 Pin: add `sessions_are_listed_in_ascending_scheduled_order` (3 sessions inserted out of order; spec "Ascending order"); expected green already, it is a characterization pin.
- [x] 1.3 GREEN: in `api/physical/src/main/java/com/menta/physical/infrastructure/persistence/repository/PhysicalSessionJpaRepository.java` change only `findScheduledWithAvailability` (L32) to `s.scheduled_at >= :from AND s.scheduled_at < :to`; leave `findManagedWithAvailability` (L58) inclusive (A10); update its Javadoc.
- [x] 1.4 Javadoc in `api/physical/src/main/java/com/menta/physical/application/port/in/PhysicalCourseAvailabilityPort.java`: `listSessions` is half-open and "ordered by `scheduledAt` ascending".
- [x] 1.5 Regression: run `./gradlew :api:billing:test :api:physical:test` and `./gradlew :api:app:test --tests "*PhysicalCourseQuoteIntegrationTest"` (seed at `periodStart` stays inclusive, A11).
- [x] 1.6 Gate: `./gradlew :api:physical:test :api:app:test` green.
- [x] 1.7 Gate: JaCoCo `./gradlew :api:physical:jacocoTestCoverageVerification :api:app:jacocoTestCoverageVerification` (physical 95%/90%, `api:app` flat floor `moduleCoverageFloor`).
- [x] 1.8 Gate: `./gradlew :api:physical:checkstyleMain :api:app:checkstyleTest`; manually verify added lines (LineLength 100, import order; snake_case MethodName warning is a known non-blocker).
- [x] 1.9 Gate: ArchUnit `./gradlew test --tests "*ArchitectureTest"`.
- [x] 1.10 Gate: size check `git diff --shortstat origin/develop...HEAD` <= 800 changed lines.
- [x] 1.11 PR 1: `gh pr list --head <branch>` first; base `develop`, Spanish body, `Refs #107`, confirm preview before push (`skills/prcreator/SKILL.md`).

## Phase 2: S2 - Detail resolution (PR 2, branch from `develop` after PR 1 merges: `feature/catalog-sessions-s2-physical-detail`)

- [x] 2.1 Scaffold types (no behavior) in `api/app/src/main/java/com/menta/app/catalog/`: create `CatalogCourseDetail.java` (sealed, permits both records, A1) and `CatalogPhysicalCourseDetailResponse.java` (nested `PhysicalDetailBlock`, `PublicSession`, String `scheduledAt`, A4); add `implements CatalogCourseDetail` to `CatalogCourseDetailResponse.java`.
- [x] 2.2 RED (service): in `api/app/src/test/java/com/menta/app/catalog/CatalogCompositionServiceTest.java` move ctor call sites to `(..., Clock.fixed(...), 30)`; invert `getCourseDetail_physical_only_id_still_throws_CourseNotFoundException` to `getCourseDetail_physical_only_id_returns_the_physical_detail`; add mapping test (course/physical fields, no internal counts, no price). Compile failure counts as RED. Delete any test of `getCourse()`.
- [x] 2.3 RED (service window): `listSessions(id, now, now+30d)` verified exactly via `Clock.fixed`; custom window 7; exactly 100 kept; 101 -> earliest 100; sold-out (`availableSpots` 0) kept; port order preserved.
- [x] 2.4 RED (service failures): sessions failure and physical-lookup failure -> `CatalogUpstreamException`; malformed id -> `CourseNotFoundException`; inactive (empty) -> 404; virtual hit never consults physical; virtual failure -> 503 without a physical call; ctor rejects 0 and 91, accepts 1 and 90.
- [x] 2.5 GREEN (config): create `api/app/src/main/java/com/menta/app/catalog/CatalogConfiguration.java` with `@Bean Clock catalogClock()` = `Clock.systemUTC()` (never named `clock`, A7); add `catalog.physical.sessions.window-days: ${CATALOG_PHYSICAL_SESSIONS_WINDOW_DAYS:30}` to `api/app/src/main/resources/application.yml`.
- [x] 2.6 GREEN (service): in `CatalogCompositionService.java` add the ctor `(physicalPort, virtualPort, Clock, @Value int)` with 1..90 validation (`MAX_SESSION_WINDOW_DAYS`), `getCourseDetail` flow per design, `MAX_DETAIL_SESSIONS = 100`; delete `getCourse()`, `LOG`/SLF4J and `VirtualCourseSummary` imports; rewrite Javadoc (A9).
- [x] 2.7 RED then GREEN (controller shape, additive): in `api/app/src/test/java/com/menta/app/catalog/CatalogControllerTest.java` pin `$.modality`, `$.physical.sessions[0]` keys exactly {sessionId, scheduledAt, capacity, availableSpots}, no `assignedSpots`/`activeCapacityHolds`/`virtual`/type property, virtual body unchanged with no `sessions`, sessions failure -> 503 + `Retry-After: 30`.
- [x] 2.8 RED then GREEN (controller inversion): invert `get_returns_404_when_physical_alone_is_resolved_for_the_id` to `get_returns_the_physical_detail_when_only_physical_resolves_the_id`; in `CatalogController.java` return `ResponseEntity<CatalogCourseDetail>` and fix Javadoc.
- [x] 2.9 RED then GREEN (integration inversion): in `api/app/src/test/java/com/menta/app/integration/catalog/CatalogIntegrationTest.java` invert `get_a_physical_only_course_answers_the_same_404_as_a_missing_one` to `get_a_physical_only_course_returns_its_physical_detail` (200, `modality`); virtual assertions use a cast or pattern match; confirm the Spring context wires `catalogClock`.
- [x] 2.10 REFACTOR: remove unused imports, tidy Javadoc, no behavior change; rerun focused tests.
- [x] 2.11 Gate: `./gradlew :api:app:test` green.
- [x] 2.12 Gate: `./gradlew :api:app:jacocoTestCoverageVerification` (flat floor 0.90 LINE; new code fully unit-covered).
- [x] 2.13 Gate: `./gradlew :api:app:checkstyleMain :api:app:checkstyleTest`; verify added lines (LineLength 100, import order).
- [x] 2.14 Gate: ArchUnit `./gradlew test --tests "*ArchitectureTest"` (no `physical.infrastructure` or `LessonAccessPolicy` edge).
- [x] 2.15 Gate: size check `git diff --shortstat origin/develop...HEAD` <= 800 (target ~390); if > ~700, apply the 2.7 split.
- [x] 2.16 PR 2: base `develop`, Spanish body, `Refs #107`, `gh pr list --head` first, confirm preview.

## Phase 3: S3 - HTTP scenarios, contract, docs (PR 3, branch `feature/catalog-sessions-s3-contract-docs`)

- [x] 3.1 Seeds in `CatalogIntegrationTest.java`: started (now-1h), in-window sold-out (capacity 2, 2 assignments), cancelled, now+31d, plus an inactive course; `cleanUp` deletes assignments, then sessions, then courses (FK `V7`); day-scale margins.
- [x] 3.2 Scenario tests (acceptance on top of S2, expected green; red means a defect): inclusion and exclusion, ascending order, exact four keys, `scheduledAt` ends in `Z`, sold-out listed with 0, inactive -> 404 `COURSE_NOT_FOUND`, empty `sessions` array when none.
- [x] 3.3 List-unchanged test: list items carry no `sessions`; `?from=&to=` on detail are ignored.
- [x] 3.4 RED (contract): add the `catalog-v1.yaml` file (as it is mounted in the redocly container, under its spec directory) to the redocly lint command in `.github/workflows/pr-develop.yml`; run it locally with the `docker run` command above and record the failures.
- [x] 3.5 GREEN (contract): in `api/openapi/catalog-v1.yaml` set 200 to `oneOf: [CatalogVirtualCourseDetail, CatalogPhysicalCourseDetail]` (no discriminator; `required` makes branches exclusive), add 3 schemas, fix any `nullable` beside `$ref` flagged by redocly; lint passes.
- [x] 3.6 Bruno: update `bruno/API - Direct/catalog/Get Course.bru`; create `bruno/API - Direct/catalog/Get Course - Physical.bru`.
- [x] 3.7 Docs: update `docs/07-CATALOG-API.md` and `docs/user-stories/US-PHYSICAL-003.md` (fields, window `catalog.physical.sessions.window-days` default 30, range 1-90, no from/to, cap 100, no internal counts or prices).
- [x] 3.8 Gate: `./gradlew :api:app:test` green and `./gradlew check` green.
- [x] 3.9 Gate: JaCoCo `./gradlew :api:app:jacocoTestCoverageVerification` (flat floor).
- [x] 3.10 Gate: Checkstyle on added Java lines (LineLength 100, import order).
- [x] 3.11 Gate: ArchUnit `./gradlew test --tests "*ArchitectureTest"`.
- [x] 3.12 Gate: size check `git diff --shortstat origin/develop...HEAD` <= 800 (target ~285).
- [x] 3.13 PR 3: base `develop`, Spanish body, `Closes #107`, `gh pr list --head` first, confirm preview.

## Phase 4: Remediation of the verify FAIL (PR 4, branch `feature/catalog-sessions-s4-scenario-pins`)

Test-only. Source: verify-report C1, W1, W2. No production change expected; a red result means a production defect and stops the work.

- [x] 4.1 C1: `PhysicalCourseAvailabilityIntegrationTest` - a session at `T`, read with `from == to == T`, then with `from` after `to`: both empty (characterization pin, expected green).
- [x] 4.2 W1: `PhysicalCourseAvailabilityIntegrationTest` - seed a hold with `converted_at` set (plus one active hold as control) and assert it is not subtracted from `availableSpots` (pins `h.converted_at IS NULL`).
- [x] 4.3 W2: `CatalogCompositionServiceTest` - listing courses never calls `physicalPort.listSessions` (`verify(..., never())`).
- [x] 4.4 Sensitivity: temporarily break each pinned clause (`< :to` to `<= :to` for 4.1 with an equal-bound seed, drop `converted_at IS NULL` for 4.2, call `listSessions` in the list path for 4.3), confirm red, restore; record in apply-progress.
- [x] 4.5 Gate: `./gradlew :api:app:test :api:app:jacocoTestReport :api:app:jacocoTestCoverageVerification :api:app:checkstyleMain :api:app:checkstyleTest` green; Checkstyle on added lines.
- [x] 4.6 Gate: ArchUnit for `api:app`; size check `git diff --shortstat origin/develop...HEAD` <= 800 (target ~80).
- [x] 4.7 PR 4: base `develop`, Spanish body, `Refs #107` (issue already closed by PR 3), `gh pr list --head` first, confirm preview.

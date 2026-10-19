# Apply Progress: catalog-physical-sessions-availability (#107)

Mode: Strict TDD. Artifact store: hybrid. Delivery: auto-chain, stacked onto develop.
Batch 1 branch: `feature/catalog-sessions-s1-half-open-bound` (from develop @ 77b5091). Batch 2 branch: `feature/catalog-sessions-s2-physical-detail` (from develop @ 2b69a9f, which contains S1 / PR #313). Batch 3 branch: `feature/catalog-sessions-s3-contract-docs` (from develop @ b7b4486, which contains S1 #313 and S2 #314). Nothing committed or staged in any batch.

## Batch 1 - Phase 1 (S1, PR 1): tasks 1.1 - 1.10 done; 1.11 (PR creation) left to the orchestrator

### Completed Tasks
- [x] 1.1 RED: `a_session_exactly_at_the_upper_bound_is_excluded_and_one_at_the_lower_bound_included`
- [x] 1.2 Pin: `sessions_are_listed_in_ascending_scheduled_order`
- [x] 1.3 GREEN: `findScheduledWithAvailability` now `>= :from AND < :to`; managed query untouched (inclusive); Javadoc on both
- [x] 1.4 Port Javadoc on `listSessions` (half-open, SCHEDULED only, ascending)
- [x] 1.5 Regression billing + physical + quote integration test
- [x] 1.6 Gate: physical + app tests
- [x] 1.7 Gate: JaCoCo verification physical + app
- [x] 1.8 Gate: Checkstyle main/test (added lines intersected, see below)
- [x] 1.9 Gate: ArchUnit
- [x] 1.10 Gate: size check (measured from the working tree, see below)
- [ ] 1.11 PR creation: orchestrator

### Files Changed
| File | Action | What was done |
|------|--------|---------------|
| `api/physical/src/main/java/com/menta/physical/infrastructure/persistence/repository/PhysicalSessionJpaRepository.java` | Modified | `findScheduledWithAvailability`: `BETWEEN` -> `>= :from AND < :to`; Javadoc on it and a note on `findManagedWithAvailability` (still inclusive) |
| `api/physical/src/main/java/com/menta/physical/application/port/in/PhysicalCourseAvailabilityPort.java` | Modified | `listSessions` Javadoc: half-open, SCHEDULED only, ascending by `scheduledAt` |
| `api/app/src/test/java/com/menta/app/integration/physical/PhysicalCourseAvailabilityIntegrationTest.java` | Modified | 2 new integration tests |
| `openspec/changes/catalog-physical-sessions-availability/tasks.md` | Modified | 1.1 - 1.10 marked `[x]` |

### TDD Cycle Evidence
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1 | `PhysicalCourseAvailabilityIntegrationTest` | Integration (Testcontainers MySQL) | 9 pre-existing tests in the class passed in the RED run (11/12 pass, 0 errors) | Written first; run failed: 12 tests, 1 failure (the new test; result contained the session at `to`) | 12/12 pass after the query change | Single test covers `from` (included), inside (included) and `to` (excluded) | None needed (one-line SQL change) |
| 1.2 | same | Integration | same run | Pin written; passed immediately (characterization, expected green per tasks.md) | 12/12 | 3 sessions inserted out of order, asserts exact order | None needed |
| 1.3 | same | Integration | n/a | Driven by 1.1 | 12/12; quote test 8/8 | Quote test seeds at `periodStart` (inclusive) and stays green | None needed |
| 1.4 | n/a | Javadoc only | n/a | n/a (documentation, no behavior) | compiles; checkstyle clean on added lines | skipped: no logic | n/a |

Test Summary: 2 tests written, 12/12 passing in the class; layer: Integration (2); approval tests: none; pure functions: none.

### Work Unit Evidence (S1)
| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :api:app:test --tests "*PhysicalCourseAvailabilityIntegrationTest" --tests "*PhysicalCourseQuoteIntegrationTest"`: exit 0; JUnit XML: availability 12 tests / 0 failures / 0 errors; quote 8 tests / 0 failures / 0 errors |
| RED run | same availability filter before the production change: exit 1; XML 12 tests, 1 failure (`a_session_exactly_at_the_upper_bound...`), 0 errors |
| Runtime harness | Testcontainers MySQL via the real Spring context (same tests); the port bean is called directly |
| Rollback boundary | Revert the `findScheduledWithAvailability` WHERE clause, the 2 Javadoc edits and the 2 new tests; no schema change |

### Verification commands (fresh, from JUnit XML)
- `./gradlew :api:billing:cleanTest :api:physical:cleanTest :api:app:cleanTest :api:billing:test :api:physical:test :api:app:test --continue`: exit 0. billing 858 tests, physical 463, app 400; 0 skipped / 0 failures / 0 errors in all three.
- `./gradlew :api:physical:jacocoTestReport :api:physical:jacocoTestCoverageVerification :api:app:jacocoTestCoverageVerification :api:physical:checkstyleMain :api:physical:checkstyleTest :api:app:checkstyleMain :api:app:checkstyleTest --continue`: exit 0.
- ArchUnit: `./gradlew :api:app:test :api:physical:test :api:billing:test :api:auth:test :api:virtual:test --tests "*ArchitectureTest" --continue`: exit 0. XML: app 5, billing 8, physical 8, virtual 7, auth 14 tests; 0 failures / 0 errors. app/billing/physical suites ran fresh in the full run above (timestamp 15:25Z); in the filtered run those tasks were up to date, the virtual suite ran fresh and the auth XML is a prior-run artifact (module untouched). Note: root `./gradlew test --tests "*ArchitectureTest"` as written in tasks.md fails at 0.6 s (the root `test` task also spans the android project), so per-module tasks were used.

### Checkstyle on added lines (added line numbers from `git diff -U0` intersected with the Checkstyle XML)
- `PhysicalSessionJpaRepository.java`: 9 added lines, 0 warnings on them (3 pre-existing warnings elsewhere in the file, lines 12 and 133 over 100 chars).
- `PhysicalCourseAvailabilityPort.java`: 9 added lines, 0 warnings on them (1 pre-existing).
- `PhysicalCourseAvailabilityIntegrationTest.java`: 35 added lines, 1 warning on them: `MethodNameCheck` at line 193 (`a_session_exactly_at_the_upper_bound...`, known snake_case non-blocker). The checker did not report `sessions_are_listed_in_ascending_scheduled_order` (line 214) although it is also snake_case; I did not investigate why. No LineLength or import-order warning on added lines. 12 other warnings in the file are on pre-existing lines.

### Size
`git diff --shortstat origin/develop...HEAD` is empty because nothing is committed (the command compares commits only). Measured from the working tree: `git diff --numstat` = 35/0 (test), 9/2 (port), 9/2 (repository) = 53 added + 4 deleted = 57 changed lines; no untracked source files. Forecast was ~46; budget 800.

### Deviations from Design
None. Beyond tasks.md: the managed query's Javadoc got a one-line note that it stays inclusive (A10); no behavior change there.

### Issues Found
- The port Javadoc already claimed `[from, to)` before this change while the query was inclusive, which is the defect the RED test exposed.
- `tasks.md` 1.9 command (`./gradlew test --tests "*ArchitectureTest"`) is not runnable as written (see above).

### Status
10/11 Phase 1 tasks complete (1.11 reserved for the orchestrator). Ready for verify of S1 / PR creation.


---

## Batch 2 - Phase 2 (S2, PR 2): tasks 2.1 - 2.15 done; 2.16 (PR creation) left to the orchestrator

Mode: Strict TDD. Branch `feature/catalog-sessions-s2-physical-detail`. Task 2.7 (additive controller pins) stayed in S2: measured size 669 < 700, so the pre-agreed 2b split was not needed. Phase 3 and tracking tasks not started.

### Completed Tasks
- [x] 2.1 Scaffold: sealed `CatalogCourseDetail`, `CatalogPhysicalCourseDetailResponse` (+ `PhysicalDetailBlock`, `PublicSession`), `implements CatalogCourseDetail` on the virtual record
- [x] 2.2 RED (service): ctor call sites moved to `(..., Clock.fixed, 30)`; inversion `getCourseDetail_physical_only_id_returns_the_physical_detail`; mapping + record-shape tests
- [x] 2.3 RED (service window): default 30d and custom 7d verified exactly with `Clock.fixed`; 100 kept; 101 -> earliest 100; sold-out kept; port order kept
- [x] 2.4 RED (service failures): sessions failure, physical-lookup failure, malformed id, inactive, virtual-first (no physical interaction), virtual failure (no physical interaction), ctor rejects -1/0/91 and accepts 1/90
- [x] 2.5 GREEN (config): `CatalogConfiguration.catalogClock()`; `catalog.physical.sessions.window-days: ${CATALOG_PHYSICAL_SESSIONS_WINDOW_DAYS:30}` in `application.yml`
- [x] 2.6 GREEN (service): new ctor with 1..90 validation, `getCourseDetail` flow, `MAX_DETAIL_SESSIONS = 100`; `getCourse()`, `LOG`, SLF4J and `VirtualCourseSummary` imports deleted; Javadocs rewritten (A9)
- [x] 2.7 Controller shape pins: exact key sets (root, `physical`, session) read from the real Spring converter output, empty `sessions` array, virtual body key set unchanged, sessions failure -> 503 + `Retry-After: 30`
- [x] 2.8 Controller inversion + `ResponseEntity<CatalogCourseDetail>`
- [x] 2.9 Integration inversion `get_a_physical_only_course_returns_its_physical_detail` (real ports, Testcontainers MySQL, Spring context wires `catalogClock`)
- [x] 2.10 REFACTOR: shorter helpers (`givenPhysicalOnlyCourse`), imports, Javadoc, stale class comments in both unit tests fixed; line-length fixes found by Checkstyle
- [x] 2.11 Gate: `:api:app:test`
- [x] 2.12 Gate: `:api:app:jacocoTestCoverageVerification`
- [x] 2.13 Gate: checkstyle main/test (added lines intersected, see below)
- [x] 2.14 Gate: ArchUnit (per module)
- [x] 2.15 Gate: size (measured from the working tree)
- [ ] 2.16 PR creation: orchestrator

### Files Changed (S2)
| File | Action | What was done |
|------|--------|---------------|
| `api/app/src/main/java/com/menta/app/catalog/CatalogCourseDetail.java` | Created | Sealed interface, permits both records, no `@JsonTypeInfo` |
| `api/app/src/main/java/com/menta/app/catalog/CatalogPhysicalCourseDetailResponse.java` | Created | Record + `from(course, sessions)` explicit mapping (drops `courseId`/`assignedSpots`/`activeCapacityHolds`), nested `PhysicalDetailBlock`, `PublicSession` |
| `api/app/src/main/java/com/menta/app/catalog/CatalogConfiguration.java` | Created | `@Bean Clock catalogClock()` = `Clock.systemUTC()` |
| `api/app/src/main/java/com/menta/app/catalog/CatalogCompositionService.java` | Modified | New ctor, virtual-first `getCourseDetail`, window and cap, dead code removed |
| `api/app/src/main/java/com/menta/app/catalog/CatalogController.java` | Modified | Returns `ResponseEntity<CatalogCourseDetail>`, comment fixed |
| `api/app/src/main/java/com/menta/app/catalog/CatalogCourseDetailResponse.java` | Modified | `implements CatalogCourseDetail`, virtual-only Javadoc |
| `api/app/src/main/resources/application.yml` | Modified | `catalog.physical.sessions.window-days` |
| `api/app/src/test/java/com/menta/app/catalog/CatalogCompositionServiceTest.java` | Modified | 7 -> 25 tests (1 inverted, 18 new invocations: 13 plain methods + 2 parameterized methods expanding to 5 cases) |
| `api/app/src/test/java/com/menta/app/catalog/CatalogControllerTest.java` | Modified | 10 -> 14 tests (1 inverted, 4 new) |
| `api/app/src/test/java/com/menta/app/integration/catalog/CatalogIntegrationTest.java` | Modified | 1 inverted test |
| `openspec/changes/catalog-physical-sessions-availability/tasks.md` | Modified | 2.1 - 2.15 marked `[x]` |

### TDD Cycle Evidence (S2)
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 2.1 | n/a (types) | Scaffold | 23/23 (7 service + 10 controller + 6 integration) passed before any edit | n/a (no behavior) | compiles with 2.6 | skipped: structural types, no logic | Javadoc rewritten |
| 2.2 | `CatalogCompositionServiceTest` | Unit (Mockito) | same baseline | Written first; `compileTestJava` exit 1: 6 errors (4-arg ctor missing, `CatalogCourseDetail` not castable to the physical record). Compile failure counts as RED per tasks.md 2.1 | 25/25 after 2.5 + 2.6 | Physical-only id (2 sessions, sold-out one) + empty list + virtual id | helpers extracted |
| 2.3 | same | Unit | same | same compile RED | 25/25 | window 30 vs 7, exact 100 vs 101, sold-out, order | none |
| 2.4 | same | Unit | same | same compile RED | 25/25 | sessions fail, physical lookup fail, inactive, malformed, virtual fail, ctor -1/0/91 reject vs 1/90 accept | none |
| 2.5 | covered through 2.9 (context wiring) | Integration | n/a | n/a (config) | Spring context starts with `catalogClock` | skipped: one bean, one property | n/a |
| 2.6 | `CatalogCompositionServiceTest` | Unit | n/a | driven by 2.2-2.4 | 25/25 | see 2.2-2.4 | Dead code deleted |
| 2.7 | `CatalogControllerTest` | MockMvc standalone | 10/10 | Behavioral RED: with the physical branch of `getCourseDetail` temporarily stubbed to empty (restored afterwards), `CatalogControllerTest` ran 14 tests, 4 failures (the 3 new shape/empty/503 tests and the inversion) | 14/14 | Physical keys, empty array, virtual keys, 503 | none |
| 2.8 | same | MockMvc standalone | 10/10 | same behavioral RED (inversion among the 4 failures) | 14/14 | n/a (single inversion) | none |
| 2.9 | `CatalogIntegrationTest` | Integration (Testcontainers MySQL) | 6/6 | same behavioral RED run: 6 tests, 1 failure (the inverted test) | 6/6 | single scenario (S3 adds the HTTP matrix) | none |

Test Summary: 25 tests written or changed in S2 (18 new service, 4 new controller, 3 inversions); net +22 against the 400 tests of S1 (`:api:app` now 422); layers: Unit (18 new + 1 inversion), MockMvc (4 new + 1 inversion), Integration (1 inversion); approval tests: 3 inversions; pure functions: `CatalogPhysicalCourseDetailResponse.from` and `PublicSession.from`.

Design risk A2 verified: `CatalogControllerTest` reads the body produced by Spring's real message converter (MockMvc standalone) and asserts exact key sets: physical root `{courseId, modality, title, level, physical}`, `physical` `{professorName, dayOfWeek, startTime, capacity, sessions}`, session `{sessionId, scheduledAt, capacity, availableSpots}`; virtual root `{courseId, title, description, thumbnailUrl, category, level, isPremium, modules, stats}`. No type property, `sessions`, `assignedSpots`, `activeCapacityHolds` or price anywhere, so the sealed interface serializes through its runtime record with no `@JsonTypeInfo`.

### Work Unit Evidence (S2)
| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :api:app:test --tests "*CatalogCompositionServiceTest" --tests "*CatalogControllerTest" --tests "*CatalogIntegrationTest"`: exit 0; JUnit XML: service 25 / controller 14 / integration 6 tests, 0 failures, 0 errors, 0 skipped |
| RED run (behavioral) | same filter (controller + integration) with the physical branch temporarily stubbed to empty: exit 1; XML controller 14 tests / 4 failures, integration 6 tests / 1 failure; source restored afterwards (backup compared) |
| RED run (compile) | `./gradlew :api:app:compileTestJava` before the production change: exit 1, 6 compile errors in `CatalogCompositionServiceTest` |
| Runtime harness | `CatalogIntegrationTest`: real Spring context + Testcontainers MySQL + real physical and virtual ports over HTTP (`TestRestTemplate`); confirms `catalogClock` and the `@Value` default wire |
| Rollback boundary | Revert S2 files listed above (S3 first, if present); virtual path untouched; no schema change |

### Verification commands (fresh)
- Safety net before edits: same focused filter: exit 0; 7 + 10 + 6 = 23 tests passing.
- Final gate: `./gradlew :api:app:cleanTest :api:app:test :api:app:jacocoTestReport :api:app:jacocoTestCoverageVerification :api:app:checkstyleMain :api:app:checkstyleTest --continue`: BUILD SUCCESSFUL (15 min wall time, machine busy). JUnit XML for `api/app`: 422 tests, 0 failures, 0 errors, 0 skipped. JaCoCo verification passed; module LINE coverage 95.7% (floor 0.90); every new/changed catalog class has 0 missed lines (`CatalogCompositionService` 35/35, `CatalogPhysicalCourseDetailResponse` 10/10 + nested, `CatalogConfiguration` 2/2).
- ArchUnit: `./gradlew :api:app:test :api:physical:test :api:billing:test :api:auth:test :api:virtual:test --tests "*ArchitectureTest" --continue`: exit 0. XML: app 5, billing 8, physical 8, virtual 7, auth 14 tests, 0 failures. Only the app suite ran fresh (15:56Z, in the clean full gate); the other modules are untouched by S2 and their XML is from earlier runs (billing 15:25Z, virtual 15:30Z, physical 15:32Z, auth 2026-09-30). The root `./gradlew test --tests "*ArchitectureTest"` is not runnable as written (spans android), so per-module tasks were used.

### Checkstyle on added lines (added line numbers from `git diff -U0` plus untracked files, intersected with `api/app/build/reports/checkstyle/{main,test}.xml`, final run)
- `CatalogCompositionService.java`: 65 added lines, 0 warnings on them.
- `CatalogPhysicalCourseDetailResponse.java`, `CatalogCourseDetail.java`, `CatalogConfiguration.java`, `CatalogController.java`, `CatalogCourseDetailResponse.java`: 0 warnings on added lines.
- `CatalogIntegrationTest.java`: 15 added lines, 1 warning: `MethodNameCheck` (line 192, snake_case test name, known non-blocker).
- `CatalogControllerTest.java`: 117 added lines, 1 warning: `MethodNameCheck` (line 276).
- `CatalogCompositionServiceTest.java`: 239 added lines, 6 warnings, all `MethodNameCheck` (lines 232, 267, 278, 315, 325, 346; snake_case names containing digits or similar, known non-blocker).
- No LineLength, import-order, Javadoc or other warnings remain on added lines. The first Checkstyle pass found LineLength, SummaryJavadoc, MissingJavadocType and a helper-name MethodName warning on added lines; all fixed in 2.10 (helper renamed `aSession` -> `sessionOf`). Pre-existing warnings elsewhere in the touched files were left alone.

### Size (S2)
`git diff --shortstat origin/develop...HEAD` is empty (nothing committed). Measured from the working tree, excluding `openspec/`: `git diff --numstat` = 454 added + 120 deleted over 7 tracked files = 574; untracked new files = 20 + 11 + 64 = 95 lines; total 669 changed lines. Forecast was ~390; the tests grew (~+250 vs ~190 forecast) mainly from the 18-test service matrix and the 4 shape pins. 669 < 700 and < 800, so no 2b split.

### Deviations from Design
- Integration inversion asserts an empty `sessions` array (no session seeded; the seeded-session matrix is S3 task 3.1/3.2) and does not add the inactive-course test, which S3 owns.
- `CatalogPhysicalCourseDetailResponse` carries a `from(course, sessions)` factory and `PublicSession.from` (the design only said "explicit mapping"); the service stays a thin orchestrator.
- Constructor failure message names the property (`catalog.physical.sessions.window-days`) and the bounds; the ctor test asserts the property name.
- Otherwise none: no `@JsonTypeInfo`, bean named `catalogClock`, 1..90 validated in the ctor, virtual-first, cap 100.

### Issues Found
- Gradle was very slow in the final gate (15 min vs 3.5 min earlier); cause not investigated (machine load).
- The Checkstyle `MethodNameCheck` flags only some snake_case test names (those with digits etc.); not investigated further, known non-blocker.
- `tasks.md` 2.14 command is not runnable as written (see above); 2.15 `git diff --shortstat origin/develop...HEAD` is empty until commits exist.

### Status
15/16 Phase 2 tasks complete (2.16 PR creation reserved for the orchestrator). Cumulative: Phase 0 0.1 only (orchestrator), Phase 1 10/11, Phase 2 15/16, Phase 3 0/13. Ready for verify of S2 / PR creation.


---

## Batch 3 - Phase 3 (S3, PR 3): tasks 3.1 - 3.12 done; 3.13 (PR creation) left to the orchestrator

Mode: Strict TDD. Branch `feature/catalog-sessions-s3-contract-docs`. Production Java untouched in S3 (tests, contract, CI, Bruno, docs only).

### Completed Tasks
- [x] 3.1 Seeds in `CatalogIntegrationTest`: started (now-1h), in-window sold-out (capacity 2, 2 assignments), cancelled, now+31d, other course's session, inactive course; `cleanUp` deletes assignments, then sessions, then courses (FK V7); day-scale margins
- [x] 3.2 Scenario tests: inclusion/exclusion + ascending order, exact four keys + `Z` instant + availability arithmetic, sold-out listed with 0, inactive -> 404 `COURSE_NOT_FOUND`; empty `sessions` array stays covered by the S2 test `get_a_physical_only_course_returns_its_physical_detail`
- [x] 3.3 List-unchanged test + `from`/`to` ignored on the detail
- [x] 3.4 Contract RED step (see deviation: lint did not go red): `catalog-v1.yaml` added to the redocly lint command
- [x] 3.5 Contract GREEN: `oneOf` of two detail schemas, 3 new schemas, lint passes
- [x] 3.6 Bruno: `Get Course.bru` updated, `Get Course - Physical.bru` created
- [x] 3.7 Docs: `docs/07-CATALOG-API.md`, `docs/user-stories/US-PHYSICAL-003.md`
- [x] 3.8 Gate: `:api:app` tests and root `check`
- [x] 3.9 Gate: JaCoCo verification
- [x] 3.10 Gate: Checkstyle on added lines
- [x] 3.11 Gate: ArchUnit (app module)
- [x] 3.12 Gate: size (measured from the working tree)
- [ ] 3.13 PR creation: orchestrator

### Files Changed (S3)
| File | Action | What was done |
|------|--------|---------------|
| `api/app/src/test/java/com/menta/app/integration/catalog/CatalogIntegrationTest.java` | Modified | +144 / -1: seeds, helpers, 6 new tests (6 -> 12), cleanUp order, class Javadoc |
| `.github/workflows/pr-develop.yml` | Modified | `/spec/catalog-v1.yaml` added to the redocly lint command |
| `api/openapi/catalog-v1.yaml` | Modified | +142 / -4: 200 = `oneOf` [`CatalogVirtualCourseDetail`, `CatalogPhysicalCourseDetail`] (no discriminator), new `CatalogPublicSession`, endpoint description (window, cap, no from/to, 503) |
| `bruno/API - Direct/catalog/Get Course.bru` | Modified | Docs no longer say physical answers 404; added a no-`sessions` assertion |
| `bruno/API - Direct/catalog/Get Course - Physical.bru` | Created | 95 lines: request on `{{physicalCourseId}}`, docs, tests (modality, key set, ascending, `Z`, cap, no internals) |
| `docs/07-CATALOG-API.md` | Modified | +68: detail section (virtual-first, fields, window property, no from/to, cap 100, no internal counts or prices, 503) |
| `docs/user-stories/US-PHYSICAL-003.md` | Modified | +41 / -10: acceptance criteria and example aligned (the old text promised `?from=&to=`, `assignedSpots`, `activeCapacityHolds` and `quoteEndpoint`) |
| `openspec/changes/catalog-physical-sessions-availability/tasks.md` | Modified | 3.1 - 3.12 marked `[x]` |

### TDD Cycle Evidence (S3)
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 3.1 | `CatalogIntegrationTest` | Integration (Testcontainers MySQL) | 6/6, exit 0 before any edit | n/a (seed helpers) | used by 3.2/3.3 | n/a | helpers `seedSession`, `seedAssignments`, `inDays`, `getDetailBody`, `sessionsOf` |
| 3.2 | same | Integration (HTTP via `TestRestTemplate`, real ports) | same | Acceptance on merged S2: expected green on first run, not a red-first test | 12/12, exit 0 on first run | 4 scenarios with different seeds (window+order, public fields, sold-out, inactive); sensitivity proof below | restructured the first test after Checkstyle `VariableDeclarationUsageDistance` |
| 3.3 | same | Integration | same | same (expected green) | 12/12 | list item (no `sessions`) and detail with `from`/`to` (two sessions kept, one beyond 30d dropped) | none |
| 3.4 | redocly lint | Contract | n/a | See deviation: lint on current `catalog-v1.yaml` was exit 0 (not red) | n/a | n/a | n/a |
| 3.5 | redocly lint | Contract | n/a | Substitute RED evidence: before the edit `rg` found 0 `oneOf` and 0 `CatalogPhysicalCourseDetail`/`CatalogVirtualCourseDetail` in the file | exit 0, same 7 warnings as before (no new warning) | n/a | n/a |

Sensitivity proof (anti-trivial GREEN): same class run with `CATALOG_PHYSICAL_SESSIONS_WINDOW_DAYS=45` and `--rerun --no-build-cache`: exit 1, 12 tests, 2 failures (`get_lists_only_scheduled_sessions_inside_the_window_in_ascending_order`, `get_ignores_from_and_to_query_parameters_on_the_detail`), i.e. the tests fail when the window is wrong. Property restored (not set) afterwards; all later runs green.

Test Summary: 6 new tests in S3 (12 in the class); layer Integration (6); approval tests: none; pure functions: none.

### Work Unit Evidence (S3)
| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :api:app:test --tests "*CatalogIntegrationTest"`: exit 0; XML 12 tests / 0 failures / 0 errors / 0 skipped (6 -> 12). Final gate run (below) re-ran it fresh |
| Contract command | `docker run --rm -v "$(pwd)/api/openapi:/spec:ro" redocly/cli:latest lint /spec/auth-v1.yaml /spec/billing-v1.yaml /spec/catalog-v1.yaml`: exit 0 before the edit (7 warnings) and after (7 warnings); the 3 warnings on `catalog-v1.yaml` are pre-existing (`info-license`, `no-server-example.com`, `operation-4xx-response` on the list operation) |
| Runtime harness | `CatalogIntegrationTest`: real Spring context + Testcontainers MySQL + real physical and virtual ports over HTTP. Bruno `Get Course - Physical.bru` was NOT run (no `bootRun` executed in this batch) |
| Rollback boundary | Revert the 8 files listed above; no production code, no behavior change; independent of S1/S2 |

### Verification commands (fresh)
- Gate: `./gradlew :api:app:test :api:app:jacocoTestReport :api:app:jacocoTestCoverageVerification :api:app:checkstyleMain :api:app:checkstyleTest --rerun-tasks`: exit 0, BUILD SUCCESSFUL in 3m 44s, 22 tasks executed. JUnit XML `api/app/build/test-results/test`: 78 suites, 428 tests, 0 failures, 0 errors, 0 skipped (422 after S2 + 6 new). `CatalogIntegrationTest`: 12/0/0 (timestamp 2026-10-02T18:12Z). JaCoCo verification passed; module LINE coverage 401/419 covered = 95.7% (floor 0.90). (An earlier run of the same command, before the Checkstyle cleanup edit, also exited 0 with 428 tests.)
- ArchUnit: `./gradlew :api:app:test --tests "*ArchitectureTest"`: exit 0; XML `com.menta.app.ArchitectureTest` 5 tests, 0 failures, fresh (18:14Z). Only the app module was run: S3 touches no main code in any module. The root `./gradlew test --tests "*ArchitectureTest"` is not runnable as written (spans android).
- `./gradlew check --continue` (root): exit 0, BUILD SUCCESSFUL in 3s. Most tasks were UP-TO-DATE or FROM-CACHE (101 of 111 up to date, 8 executed, 2 from cache; `:api:app:test` FROM-CACHE) because the app gate had just run with identical inputs and the other modules are untouched; it confirms the whole build graph (including android lint and bff) is green, it is not a fresh re-execution of every test.

### Checkstyle on added lines (added line numbers from `git diff -U0 HEAD` intersected with `api/app/build/reports/checkstyle/test.xml`, final run)
- Only one Java file changed: `CatalogIntegrationTest.java`, 144 added lines, 3 warnings on them, all `MethodNameCheck` (lines 276, 296, 311; snake_case test names, known non-blocker #298). The other 3 new snake_case names were not flagged (same unexplained behavior as S1/S2). The first pass also reported 2 `LineLength` and 3 `VariableDeclarationUsageDistance` on added lines; all fixed (helper reformatted, test statements reordered) and re-verified clean. No import-order warning on added lines.

### Size (S3)
`git diff --shortstat origin/develop...HEAD` is empty (nothing committed). Measured from the working tree, excluding `openspec/`: `git diff --numstat HEAD` = 408 added + 24 deleted over 6 tracked files = 432; untracked `Get Course - Physical.bru` = 95 lines; total 527 changed lines (forecast ~285; tests ~144 vs ~90, OpenAPI 146 vs ~195 shared with docs/Bruno). 527 < 700 and < 800.

### Deviations from Design / tasks
- Task 3.4: the redocly RED step did not fail. With `catalog-v1.yaml` added to the lint command, lint on the unchanged file exits 0 with 3 warnings only. The drift (200 documented as `CatalogCourseResponse` while the wire returns the two detail shapes) is semantic and not a rule redocly checks. RED evidence is therefore the absence of `oneOf` and the new schemas (rg count 0), not a lint failure. 3.4 is marked done on that basis; flagging it for the orchestrator.
- Design says "fix any `nullable` beside `$ref` flagged by redocly": redocly did not flag it, so the list schema's `nullable: true` beside `$ref` (`physical`/`virtual`) was left untouched.
- Design says 3 new schemas: delivered 3 named schemas (`CatalogVirtualCourseDetail` with inline modules/lessons/stats, `CatalogPhysicalCourseDetail` with an inline `physical` block, `CatalogPublicSession`).
- Not independently validated: the wire bodies against the new `oneOf` (no JSON-schema validator available locally); only redocly structural lint plus the S2/S3 key-set tests back it.
- Out of scope but stale: `docs/adr/0037-catalog-course-id-routing.md` still describes parallel resolution of both ports (already stale after #47); not edited.

### Issues Found
- `./gradlew check` at the root is runnable here (android included) and exited 0; the earlier note that root `test --tests` is unrunnable still holds for the filtered ArchUnit command.

### Status
12/13 Phase 3 tasks complete (3.13 PR creation reserved for the orchestrator). Cumulative: Phase 0 0.1 only (orchestrator), Phase 1 10/11, Phase 2 15/16, Phase 3 12/13. Ready for verify of S3 / PR creation.


---

## Batch 4 - Phase 4 (S4, PR 4): tasks 4.1 - 4.6 done; 4.7 (PR creation) left to the orchestrator

Mode: Strict TDD (characterization pins). Branch `feature/catalog-sessions-s4-scenario-pins`. Test-only: `git diff -- 'api/*/src/main'` is empty (0 bytes) after the sensitivity edits were restored. Source: verify-report C1, W1, W2.

### Completed Tasks
- [x] 4.1 C1: `a_zero_width_or_inverted_range_is_empty_even_with_a_session_on_the_bound` (`PhysicalCourseAvailabilityIntegrationTest`): session at `T`; `from == to == T` empty; `from = T+1h, to = T` empty; control range `[T, T+1s)` returns the session
- [x] 4.2 W1: `a_converted_hold_is_not_subtracted_from_availability_while_an_active_one_is` (same class): capacity 20, one active hold and one converted hold (`converted_at` set, `expires_at` in the future so only `converted_at` distinguishes them); asserts `activeCapacityHolds == 1` and `availableSpots == 19`; new helper `seedConvertedHold`
- [x] 4.3 W2: `listCourses_returns_physical_courses_without_reading_any_session_availability` (`CatalogCompositionServiceTest`): the physical port returns one course (asserted in the result, so not vacuous) and `verify(physicalPort, never()).listSessions(any(), any(), any())`
- [x] 4.4 Sensitivity proof (below)
- [x] 4.5 Gate: app tests, JaCoCo report and verification, Checkstyle main and test
- [x] 4.6 Gate: ArchUnit (app module) and size
- [ ] 4.7 PR creation: orchestrator

### Files Changed (S4)
| File | Action | What was done |
|------|--------|---------------|
| `api/app/src/test/java/com/menta/app/integration/physical/PhysicalCourseAvailabilityIntegrationTest.java` | Modified | +41 / -0: 2 tests (12 -> 14) and helper `seedConvertedHold` |
| `api/app/src/test/java/com/menta/app/catalog/CatalogCompositionServiceTest.java` | Modified | +13 / -0: 1 test (25 -> 26), static import `anyInt` |
| `openspec/changes/catalog-physical-sessions-availability/tasks.md` | Modified | 4.1 - 4.6 marked `[x]` |
| `openspec/changes/catalog-physical-sessions-availability/apply-progress.md` | Modified | this section |

### TDD Cycle Evidence (S4)
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 4.1 | `PhysicalCourseAvailabilityIntegrationTest` | Integration (Testcontainers MySQL, real port) | 12/12 baseline (verify run) | Characterization pin: green first, RED proven by sensitivity (`<= :to`) | 14/14 after adding, exit 0 | zero-width, inverted and a non-empty control range in one test | line-length fix after Checkstyle |
| 4.2 | same | Integration | same | Characterization pin: green first, RED proven by sensitivity (clause dropped) | 14/14 | active vs converted hold (both unexpired) | none |
| 4.3 | `CatalogCompositionServiceTest` | Unit (Mockito) | 25/25 baseline (verify run) | Characterization pin: green first, RED proven by sensitivity (`listSessions` in the list path) | 26/26 | non-empty list result asserted alongside `never()` | line-length fix |
| 4.4 | production files, temporary | n/a | n/a | see below | restored | n/a | n/a |

Sensitivity proof (each break applied alone, focused class run, then restored):
| Pin | Temporary production edit | Result |
|-----|---------------------------|--------|
| 4.1 | `PhysicalSessionJpaRepository.findScheduledWithAvailability`: `s.scheduled_at < :to` changed to `<= :to` | exit 1, 14 tests, 2 failed: the 4.1 test and the pre-existing `a_session_exactly_at_the_upper_bound_is_excluded_and_one_at_the_lower_bound_included` |
| 4.2 | same query: `h.converted_at IS NULL AND ` removed from the holds subquery | exit 1, 14 tests, 1 failed: the 4.2 test |
| 4.3 | `CatalogCompositionService.listCourses`: added a `physicalPort.listSessions(...)` call per physical summary | exit 1, 26 tests, 1 failed: the 4.3 test |
All three edits were reverted by hand; `git diff -- 'api/*/src/main'` is 0 bytes (and `git diff --name-only HEAD` lists only the two test files).

### Work Unit Evidence (S4)
| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :api:app:test --tests "*PhysicalCourseAvailabilityIntegrationTest" --tests "*CatalogCompositionServiceTest"`: exit 0; XML 14 tests and 26 tests, 0 failures, 0 errors, 0 skipped |
| Runtime harness | `PhysicalCourseAvailabilityIntegrationTest`: real Spring context, real port and Testcontainers MySQL |
| Rollback boundary | Revert the two test files; no production code, no schema change |

### Verification commands (fresh)
- Gate: `./gradlew :api:app:test :api:app:jacocoTestReport :api:app:jacocoTestCoverageVerification :api:app:checkstyleMain :api:app:checkstyleTest --rerun-tasks`: exit 0, BUILD SUCCESSFUL in 3m 52s, 22 of 22 tasks executed. JUnit XML `api/app/build/test-results/test`: 78 suites, 431 tests, 0 failures, 0 errors, 0 skipped (428 + 3 new). JaCoCo LINE 401 covered / 18 missed (95.7%, unchanged; floor 0.90), verification passed.
- ArchUnit: `./gradlew :api:app:test --tests "*ArchitectureTest"`: exit 0; `com.menta.app.ArchitectureTest` 5 tests, 0 failures, 0 errors.
- Checkstyle on added lines (`git diff -U0 HEAD` intersected with `build/reports/checkstyle/{test,main}.xml`): 54 added lines, 2 warnings, both `MethodNameCheck` (snake_case test names, known #298). The first pass also reported 2 `LineLength` (103 and 102 chars) on added lines; both wrapped and re-verified clean in the final run.

### Size (S4)
`git diff --shortstat HEAD`: 2 files changed, 54 insertions(+), 0 deletions (target ~80; nothing untracked outside `openspec/`).

### Deviations / Issues
- One earlier gate run hung (the Spring test JVM was stuck retrying a refused MySQL connection to a Testcontainers port that no longer existed, after ~28 minutes with no new XML). It was killed manually and produced no exit code, so it is not counted as evidence; the gate was rerun from scratch (and once more after the Checkstyle wraps) and finished in 3m 52s each, exit 0. Cause not investigated (looks like a transient container/environment issue; not related to the new tests).
- No production code was touched; no pin was red against production, so no defect was found.

### Status
6/7 Phase 4 tasks complete (4.7 PR creation reserved for the orchestrator). Cumulative: Phase 0 0.1 only (orchestrator), Phase 1 10/11, Phase 2 15/16, Phase 3 12/13, Phase 4 6/7. Ready for re-verify.

# Apply Progress: billing-real-course-catalog-adapter (#108)

Mode: Strict TDD. Artifact store: hybrid. Delivery: auto-chain, stacked onto `develop`, review budget 800 lines.

## Batch 1: Phase 1 / S1 (PR 1), branch `feature/billing-catalog-s1-batch-lookups`

Status: tasks 1.1-1.15 complete (15/16 in Phase 1). Task 1.16 (open PR) and Phase 0 tracking are left to the orchestrator. Nothing is committed, pushed or opened.

### Tasks

- [x] 1.1 to 1.10: Virtual and Physical batch lookups, unit tests and MySQL parity tests, refactor.
- [x] 1.11 to 1.15: gates (tests, JaCoCo, Checkstyle on added lines, ArchUnit, size).
- [ ] 1.16: PR 1 (orchestrator).

### TDD Cycle Evidence

| Task | Test file | Layer | Safety net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1 | `VirtualCourseCatalogPortImplTest` | Unit | 6/6 before | Written first; `compileTestJava` failed with "cannot find symbol" on `findPublishedByIds` | 14/14 pass | 8 new cases (malformed/blank/null mix, no valid id, empty collection, uppercase input key, duplicates in two forms, partial answer, null NPE, parity with single-id summary) | Unused import removed |
| 1.2 | `VirtualCourseRepositoryAdapterTest` | Unit | 15/15 before | Written first; compile failure on `findPublishedByIds` and `findByIdInAndStatus` | 18/18 pass | 3 new cases (3 constant queries with joined aggregates, aggregates skipped when nothing matches, empty ids no query) | Line wraps |
| 1.3 | (covered by 1.2) | Unit | n/a | n/a | JPA `findByIdInAndStatus`, port-out `findPublishedByIds`, adapter implementation | n/a | n/a |
| 1.4 | (covered by 1.1) | Unit | n/a | n/a | Port-in `findPublishedByIds` plus impl; 1.1 and 1.2 green together | n/a | n/a |
| 1.5 | `PhysicalCourseAvailabilityPortImplTest` | Unit | 7/7 before | Written first; compile failure on `findActiveByIds` | 15/15 pass | 8 new cases, same set as 1.1 | Line wraps |
| 1.6 | `PhysicalCourseRepositoryAdapterTest` | Unit | 11/11 before | Written first; compile failure | 14/14 pass | 3 new cases (one status-filtered query and no `findById`, none active, empty ids no query) | Line wraps |
| 1.7 | (covered by 1.5, 1.6) | Unit | n/a | n/a | JPA, port-out, adapter, port-in and impl added | n/a | n/a |
| 1.8 | `VirtualCourseCatalogIntegrationTest` | Integration (Testcontainers MySQL) | 8/8 before | See "RED for 1.8 and 1.9" | 10/10 pass | 2 new cases (PUBLISHED/DRAFT/ARCHIVED/unknown parity with summary equality and counts; malformed id skipped and uppercase key) | Line wraps |
| 1.9 | `PhysicalCourseAvailabilityIntegrationTest` | Integration (Testcontainers MySQL) | 14/14 before | See "RED for 1.8 and 1.9" | 16/16 pass | 2 new cases (ACTIVE/inactive/unknown parity; malformed id skipped and uppercase key) | Line wraps |
| 1.10 | all touched files | n/a | n/a | n/a | Full gates rerun after refactor | n/a | Removed an unused `Function` import; wrapped every added line to 100 characters or less |

RED for 1.1, 1.2, 1.5, 1.6 is a compile failure on a not-yet-declared method, per the tasks note; no stubs were added.

RED for 1.8 and 1.9: the MySQL parity tests were written after the batch methods already existed (the tasks order places them after 1.3, 1.4, 1.7), so they could not fail naturally. To prove the assertions can fail, both repository adapters were temporarily mutated (Virtual: filter `DRAFT` instead of `PUBLISHED`; Physical: `INACTIVE` instead of `ACTIVE`). With the mutation, `VirtualCourseCatalogIntegrationTest` ran 10 tests with 2 failures and `PhysicalCourseAvailabilityIntegrationTest` ran 16 tests with 2 failures (the two new tests in each). The mutation was reverted from backups, confirmed by `rg` on the main sources, and the same two classes then passed 10/10 and 16/16.

### Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :api:virtual:test --tests "*VirtualCourseCatalogPortImplTest" --tests "*VirtualCourseRepositoryAdapterTest"` exit 0 (14 and 18 tests, 0 failures); `./gradlew :api:physical:test --tests "*PhysicalCourseAvailabilityPortImplTest" --tests "*PhysicalCourseRepositoryAdapterTest"` exit 0 (15 and 14 tests, 0 failures); `./gradlew :api:app:test --tests "*VirtualCourseCatalogIntegrationTest" --tests "*PhysicalCourseAvailabilityIntegrationTest"` exit 0 (10 and 16 tests, 0 failures) |
| Runtime harness | Testcontainers MySQL in the two `api:app` integration tests above (batch vs single-id parity), exit 0 |
| Rollback boundary | Revert the added methods in `VirtualCourseCatalogPort`, `VirtualCourseCatalogPortImpl`, `VirtualCourseRepository`, `VirtualCourseRepositoryAdapter`, `VirtualCourseJpaRepository` and the Physical equivalents, plus the added tests in the six test classes. No schema change, no caller. |

### Gates (final runs, after the last code edit)

| Task | Command | Exit | Result |
|------|---------|------|--------|
| 1.11 | `./gradlew :api:virtual:test :api:physical:test :api:app:test` | 0 | JUnit XML totals: virtual 355 tests in 77 suites, physical 474 in 94, app 435 in 78; 0 failures, 0 errors, 0 skipped in all three |
| 1.12 | `./gradlew :api:virtual:jacocoTestCoverageVerification :api:physical:jacocoTestCoverageVerification :api:app:jacocoTestCoverageVerification` | 0 | Verification passed for the three modules (thresholds as configured; no numbers reported by Gradle on success) |
| 1.13 | `./gradlew :api:virtual:checkstyleMain :api:virtual:checkstyleTest :api:physical:checkstyleMain :api:physical:checkstyleTest :api:app:checkstyleTest` | 0 | Warnings only. On ADDED lines (617 added Java lines in 16 files, intersected with `build/reports/checkstyle/{main,test}.xml`): 0 LineLength, 0 import order, 9 MethodName (snake_case test names, known non-blocker #298). Main-source added lines: 0 findings. |
| 1.14 | `./gradlew :api:virtual:test --tests "*ArchitectureTest" :api:physical:test --tests "*ArchitectureTest"` | 0 | `com.menta.virtual.ArchitectureTest` 7 tests, `com.menta.physical.ArchitectureTest` 8 tests, 0 failures |
| 1.15 | `git diff --shortstat HEAD` | n/a | 16 files changed, 617 insertions(+), 0 deletions (below the 700 stop line and the 800 budget; above the ~340 target because of the test volume). No untracked Java files. |

Process notes (not evidence):
- A first run of the 1.11 gate was killed on purpose (I interrupted it to remove an unused import found during the refactor review). A second run of the same gate failed with `NoSuchFileException ... build/test-results/test/binary/in-progress-results-generic.bin` in all three modules and no failing test in the XML; the likely cause is the overlap with the killed run's leftover work in the same build directories, but that cause was not proven. The gate was rerun from scratch twice afterwards (once before and once after the Checkstyle line wraps) and both runs exited 0. Neither the killed nor the failed run is used as evidence.

### Files changed (all uncommitted, all additions)

Main (Virtual): `VirtualCourseCatalogPort` +22, `VirtualCourseCatalogPortImpl` +39, `VirtualCourseRepository` +14, `VirtualCourseRepositoryAdapter` +41, `VirtualCourseJpaRepository` +4.
Main (Physical): `PhysicalCourseAvailabilityPort` +22, `PhysicalCourseAvailabilityPortImpl` +39, `PhysicalCourseRepository` +13, `PhysicalCourseRepositoryAdapter` +13, `PhysicalCourseJpaRepository` +4.
Tests: `VirtualCourseCatalogPortImplTest` +100, `VirtualCourseRepositoryAdapterTest` +80, `PhysicalCourseAvailabilityPortImplTest` +107, `PhysicalCourseRepositoryAdapterTest` +47, `VirtualCourseCatalogIntegrationTest` +38, `PhysicalCourseAvailabilityIntegrationTest` +34.
Total tracked diff: 617 insertions, 0 deletions.

### Deviations and decisions

- The Virtual repository batch method returns `List<VirtualCourse>` as proposed in the tasks (no return-type adjustment). The port impl keys the final map by the caller's input string (A2) by parsing each input to a `CourseId` and joining on `VirtualCourse.getId()`.
- `findPublishedByIds` in the Virtual adapter repeats the in-memory aggregate join already present in `findPublished`, `findPublishedById` and `findByIdAnyStatus` instead of extracting a shared helper, to keep S1 purely additive (no existing method touched). A follow-up cleanup could extract the helper.
- Two cases beyond the task text were added to each integration test (malformed id skipped plus uppercase-key check); they cost about 20 lines per module and exercise A2 and A4 against real MySQL.
- Design unchanged otherwise (A2, A3, A4 as written; the adapter does the 3 constant queries, aggregates skipped when nothing matches).

### Remaining

- 1.16 (open PR 1, orchestrator) and Phase 0 tracking (orchestrator).
- Phases 2 and 3 not started.

## Batch 2: Phase 2 / S2 (PR 2), branch `feature/billing-catalog-s2-batch-port`

Status: tasks 2.1-2.14 complete (14/15 in Phase 2). Task 2.15 (open PR) and Phase 0 tracking are left to the orchestrator. Nothing is committed, pushed or opened. Branch created from `develop` at 28cc690 (contains merged S1, PR #319), so `HEAD` is the S2 base.

### Tasks

- [x] 2.1 to 2.4: RED tests (resolver, list use case, get use case, placeholder).
- [x] 2.5 to 2.8: GREEN (batch port, placeholder adapted, resolver, both use cases, `api:app` stub).
- [x] 2.9: REFACTOR.
- [x] 2.10 to 2.14: gates (tests, JaCoCo, Checkstyle on added lines, ArchUnit, size).
- [ ] 2.15: PR 2 (orchestrator).

### TDD Cycle Evidence

| Task | Test file | Layer | Safety net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 2.1 | `PlanCourseResolverTest` (new) | Unit (Mockito, logback `ListAppender`) | N/A (new) | Written first; `:api:billing:compileTestJava` exit 1 with 24 "cannot find symbol" errors on `courseNames`, `resolveNames`, `toResults` | 8/8 pass | 8 cases: distinct ids across plans in one call (order `a, b, c`), shared course across three plans looked up once, no plans means no call, plans without courses mean no call, port failure gives empty map plus one WARN (exact message, level, no throwable) and null names, per-plan order, unresolved id keeps stored id with null name (incl. non-UUID), plan without courses gives `[]` | Line wraps |
| 2.2 | `ListPlansUseCaseImplTest` | Unit | 6/6 before | Written first (same compile failure as 2.1) | 8/8 pass | 3 old stubs moved to `courseNames(any())`; stale comment fixed; 2 new cases (one batch call across two plans sharing a course with per-plan order, plans without courses never reach the catalog) | Unused `eq` and `Optional` imports removed |
| 2.3 | `GetPlanUseCaseImplTest` | Unit | 4/4 before | Written first (same compile failure) | 6/6 pass | Stub moved to `courseNames(any())`; 2 new cases (detail resolves `List.of(plan)` with one batch call and a partly resolved course list, plan without courses never reaches the catalog) | Line wrap |
| 2.4 | `NotImplementedCourseCatalogPortTest` | Unit | 1/1 before | Written first (same compile failure) | 1/1 pass | Single (one behavior: placeholder still throws `UnsupportedOperationException`) | None needed |
| 2.5 | (covered by 2.1-2.4) | n/a | n/a | n/a | `CourseCatalogPort.courseNames(Collection<String>)` with A1 Javadoc; `NotImplementedCourseCatalogPort` implements it and still throws | n/a | n/a |
| 2.6 | (covered by 2.1) | n/a | n/a | n/a | `resolveNames` and `toResults` in `PlanCourseResolver` | n/a | Javadoc summary line added |
| 2.7 | (covered by 2.2, 2.3) | n/a | n/a | n/a | `ListPlansUseCaseImpl` resolves all plans at once; `GetPlanUseCaseImpl` resolves `List.of(plan)` | n/a | n/a |
| 2.8 | `BillingPlansIntegrationTest` | Integration (Testcontainers MySQL) | Not run before the change (the port change breaks `api:app` test compilation, which is the RED) | Compile failure on `courseName(` once the port changed | 6/6 pass | Single (one stub moved to the batch method, no new case) | Unused `Optional` import removed |
| 2.9 | all touched files | n/a | n/a | n/a | Focused tests rerun after the refactor: 23/23 | n/a | See 2.2, 2.3, 2.6 and the Checkstyle fixes below |

RED for 2.1-2.4 is a compile failure on not-yet-declared methods, per the tasks note; no stubs were added.

Sensitivity check (temporary, restored): in `PlanCourseResolver.resolveNames` I replaced the `LinkedHashSet` with an `ArrayList` (no dedupe) and removed the empty-ids guard in the same edit. `PlanCourseResolverTest` then ran 8 tests with 5 failures: the single-call/distinct-ids test, the shared-course test, both no-call tests and the WARN test. The file was restored from a backup copy and `diff` showed it identical to the pre-mutation version; the final gate runs below were executed after the restore.

### WARN format chosen

`log.warn("Course name resolution failed; courseIds={}", courseIds)` where `courseIds` is the `LinkedHashSet` of distinct ids across all plans in encounter order, so the output is for example `Course name resolution failed; courseIds=[a, b, c]`. Level WARN, logger `com.menta.billing.application.usecase.PlanCourseResolver` (explicit `LoggerFactory.getLogger`, the style already used in the billing application layer, for example `PublishPhysicalPaymentCompletedUseCase`). No throwable, no exception message and no stack trace; the test asserts the exact formatted message, the WARN level and a null throwable proxy. It logs once per failed request, with all the ids of that request (not the ids of one plan).

### Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :api:billing:test --tests "*PlanCourseResolverTest" --tests "*ListPlansUseCaseImplTest" --tests "*GetPlanUseCaseImplTest" --tests "*NotImplementedCourseCatalogPortTest" :api:billing:checkstyleMain :api:billing:checkstyleTest` exit 0, 23 tests (8, 8, 6, 1), 0 failures; `./gradlew :api:app:test --tests "*BillingPlansIntegrationTest"` exit 0, 6 tests, 0 failures |
| Runtime harness | `BillingPlansIntegrationTest` (Testcontainers MySQL) still runs against the mocked `CourseCatalogPort`; the only change is the stub moved to `courseNames(any())`. Real wiring (placeholder bean throwing into the resolver catch-all) is covered by the unit tests only; the real adapter is S3. |
| Rollback boundary | Revert the `api/billing` changes (`CourseCatalogPort`, `NotImplementedCourseCatalogPort`, `PlanCourseResolver`, `ListPlansUseCaseImpl`, `GetPlanUseCaseImpl`), the new `PlanCourseResolverTest`, the edits in `ListPlansUseCaseImplTest`, `GetPlanUseCaseImplTest`, `NotImplementedCourseCatalogPortTest` and the one-line stub in `api/app` `BillingPlansIntegrationTest`. S1 stays valid on its own. |

### Gates (final runs, after the last code edit; sequential, none overlapped)

| Task | Command | Exit | Result |
|------|---------|------|--------|
| 2.10 | `./gradlew :api:billing:test :api:app:test` | 0 | JUnit XML totals: billing 870 tests in 140 suites, app 435 in 78; 0 failures, 0 errors, 0 skipped in both |
| 2.11 | `./gradlew :api:billing:jacocoTestCoverageVerification :api:app:jacocoTestCoverageVerification` | 0 | Verification passed for both modules (thresholds as configured; Gradle prints no numbers on success) |
| 2.12 | `./gradlew :api:billing:checkstyleMain :api:billing:checkstyleTest :api:app:checkstyleTest` | 0 | Warnings only. On ADDED lines (diff against `HEAD` plus the untracked test file, intersected with `build/reports/checkstyle/{main,test}.xml`): 0 LineLength, 0 import order, 0 other checks, 6 MethodName (snake_case test names, known non-blocker #298: 5 in `PlanCourseResolverTest`, 1 in `GetPlanUseCaseImplTest`). Main-source added lines: 0 findings. |
| 2.13 | `./gradlew :api:billing:test --tests "*ArchitectureTest"` | 0 | `com.menta.billing.ArchitectureTest` 8 tests, 0 failures, 0 errors |
| 2.14 | `git diff --shortstat HEAD` plus untracked line count | n/a | 9 tracked files changed, 173 insertions(+), 49 deletions(-), plus 1 untracked file (`PlanCourseResolverTest`, 183 lines): 405 changed lines in total (above the ~290 target because of test volume; below the 700 stop line and the 800 budget) |

Process notes (not evidence):
- A first Checkstyle run (before the final edits) reported on added lines: `SummaryJavadoc` and `LineLength` in `PlanCourseResolver`, and `VariableDeclarationUsageDistance` plus 2 `LineLength` in the tests. They were fixed (Javadoc summary, wrapped lines, assertion reorder) and the gates 2.10-2.13 were rerun from scratch afterwards; only the post-fix runs are recorded above.
- Plain `./gradlew` output was unreadable through rtk, so Gradle was run with `rtk proxy` and the counts above come from the JUnit XML, as requested.
- The native attempt authority was re-acquired with the continuation token before editing (state `proceed`); settle was not called.

### Files changed (all uncommitted)

Main (`api/billing`): `CourseCatalogPort` +19/-9, `NotImplementedCourseCatalogPort` +3/-2, `PlanCourseResolver` +50/-18, `ListPlansUseCaseImpl` +7/-4, `GetPlanUseCaseImpl` +5/-3.
Tests: `PlanCourseResolverTest` new (+183), `ListPlansUseCaseImplTest` +42/-8, `GetPlanUseCaseImplTest` +44/-2, `NotImplementedCourseCatalogPortTest` +2/-1, `api/app` `BillingPlansIntegrationTest` +1/-2.
Openspec: `tasks.md` (2.1-2.14 marked), this file.

### Deviations and decisions

- `resolveNames` takes the port as a second parameter (`resolveNames(List<Plan>, CourseCatalogPort)`) because the resolver is a static utility; the design wrote only the plan list.
- The resolver WARN is its own message (`Course name resolution failed; courseIds=...`), distinct from the adapter WARN of A6 (`Course catalog lookup failed; module=..., courseIds=...`) that S3 adds. The design fixes the A6 format for the adapter only.
- Beyond the task text: 2 extra cases each in `ListPlansUseCaseImplTest` and `GetPlanUseCaseImplTest` (batch across plans, plans without courses never reach the catalog; detail with a partly resolved list). The first test of `GetPlanUseCaseImplTest` keeps its stub (moved to `courseNames(any())` as task 2.3 says) although its plan has no courses, so the stub is now unused by that test.
- The `CourseCatalogPort` Javadoc still says the wired adapter is a placeholder and names `NotImplementedCourseCatalogPort`; true until S3. That reference is not in the task 3.11 list (which names `BillingConfiguration`, `PhysicalCourseOwnershipPort` and `PlanCourseResult`), so S3 must also clean it when it deletes the placeholder.
- `BillingConfiguration` Javadoc (~102) was not touched, as instructed; no bean change.

### Risks

- Until S3 replaces the placeholder, the placeholder throws `UnsupportedOperationException` from `courseNames`; the resolver catches it, so responses are unchanged (names null, HTTP 200), but every plans request that has at least one course now logs one WARN. This is log noise only, and disappears in S3.

### Remaining

- 2.15 (open PR 2, orchestrator) and Phase 0 tracking (orchestrator).
- Phase 3 not started.

## Batch 3: Phase 3 / S3 (PR 3, final slice), branch `feature/billing-catalog-s3-adapter`

Status: tasks 3.1-3.20 complete (20/21 in Phase 3). Task 3.21 (open PR 3) and Phase 0 tracking are left to the orchestrator. Nothing is committed, pushed or opened; the placeholder and its test are working-tree deletions and the index is clean. Branch created from `develop` at ff6cbf0 (contains merged S1 #319 and S2 #320), so `HEAD` is the S3 base. Mode: Strict TDD. The native attempt authority was re-acquired with the continuation token (state `proceed`); settle was not called.

### Tasks

- [x] 3.1 to 3.4: Lombok in `api:app`, adapter unit tests (RED), adapter (GREEN).
- [x] 3.5 to 3.7: placeholder and its test deleted, ArchUnit rules, mock removed from the shared base.
- [x] 3.8 to 3.10: integration test with real Virtual and Physical seeds.
- [x] 3.11 to 3.13: Javadoc fixes, cleanup verification, refactor.
- [x] 3.14 to 3.15: docs (`docs/06-BILLING-API.md`) and OpenAPI (`courses[].name` description), both in Spanish.
- [x] 3.16 to 3.20: gates (tests, `check`, JaCoCo, Checkstyle on added lines, ArchUnit, size).
- [ ] 3.21: PR 3 (orchestrator).

### TDD Cycle Evidence

| Task | Test file | Layer | Safety net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 3.1 | (build file) | n/a | `ArchitectureTest` 5, `BillingPlansIntegrationTest` 6, `CatalogIntegrationTest` 12, `VirtualLessonAccessIntegrationTest` 6 all green before any edit | n/a (structural) | `compileOnly` and `annotationProcessor` for Lombok; compiled together with 3.4 | Triangulation skipped: purely structural config | None needed |
| 3.2, 3.3 | `CourseCatalogPortAdapterTest` (new) | Unit (Mockito, logback `ListAppender`) | N/A (new) | Written first; `:api:app:compileTestJava` exit 1, "cannot find symbol: class CourseCatalogPortAdapter" (no stub added) | 14/14 pass | 14 cases: virtual-only answer with physical never consulted; physical gets only pending ids (`[p1, unknown]`) and virtual all ids; virtual title never overwritten by a physical answer; distinct ids in encounter order, one call per module; empty input no lookup; null elements omitted and only-null input no lookup; null collection NPE; immutable result keyed by the exact input id (uppercase UUID); virtual failure (physical asked all ids, WARN virtual); physical failure (WARN physical with only pending ids); both failing (two WARNs, empty map); `IllegalArgumentException` treated as a failure; malformed id emits no WARN; WARN carries only module and ids (exact `getArgumentArray`, null throwable, failure detail text absent) | Line wraps, `assertWarn(event, module, ids)` helper |
| 3.4 | (covered by 3.2, 3.3) | n/a | n/a | n/a | `CourseCatalogPortAdapter` with `@Slf4j`, `@Component`, `@RequiredArgsConstructor` | n/a | Removed two unused DTO imports |
| 3.5 | (deletion) | n/a | placeholder test was 1/1 in the S2 baseline | n/a | Placeholder and test deleted; `rg NotImplementedCourseCatalogPort` over `api` and `docs` returns no match (exit 1); billing test count went 870 to 869 | n/a | n/a |
| 3.6 | `ArchitectureTest` | Architecture (ArchUnit) | 5/5 before | Could not fail naturally (the adapter already existed); proven with a temporary mutation, see below | 7/7 pass | 3 changes: adapter added to the package rule; direct dependency on Virtual's and on Physical's `port.in` interface; no dependency on virtual or physical `domain`, `application.usecase`, `application.port.out` or `infrastructure` | None needed |
| 3.7 | (shared base) | n/a | see 3.8 | n/a | `CourseCatalogPort` mock and import removed from `CatalogAccessMocksIntegrationTestBase` | n/a | n/a |
| 3.8, 3.9, 3.10 | `BillingPlansIntegrationTest` | Integration (Testcontainers MySQL, real adapter, HTTP) | 6/6 before | Natural RED: tests written first while the shared base still mocked the port; `:api:app:test --tests "*BillingPlansIntegrationTest"` ran 13 tests with 5 failures, all with every title `null` where a title was expected (the null-name tests pass under the mock by construction) | 13/13 pass after the mock was removed from the base | 7 new cases plus the existing detail test moved from a stub to a real seed: mixed plan with virtual, physical and an id answerable by both (virtual wins) in plan order; DRAFT, ARCHIVED and inactive give `null` with id; unknown id, non-UUID `course-1` and a resolvable course in one plan; plan without courses gives `[]`; shared course across plans; detail equals list for the four plan kinds; exactly the keys `id` and `name` (with `name` present as `null`) in both endpoints | Cleanup in `@AfterEach` extended; `seedVirtualCourse` and `seedPhysicalCourse` made `void`; lines wrapped |
| 3.11 | Javadoc only | n/a | n/a | n/a | `CourseCatalogPort`, `BillingConfiguration`, `PhysicalCourseOwnershipPort`, `PlanCourseResult` no longer mention the placeholder | n/a | n/a |
| 3.12 | the three shared-context classes | Integration | n/a | n/a | `BillingPlansIntegrationTest` 13, `CatalogIntegrationTest` 12, `VirtualLessonAccessIntegrationTest` 6, run together in one Gradle invocation, 0 failures; the exact-id assertions of `CatalogIntegrationTest` pass after `BillingPlansIntegrationTest` has seeded and cleaned | n/a | n/a |
| 3.13 | all touched files | n/a | n/a | n/a | Full gates rerun after the refactor | n/a | See Checkstyle fixes below |

### Mutation and sensitivity proofs (all temporary, all restored)

Backups of the adapter were taken before each mutation and the file was restored with `cp`; `diff` against the backup printed identical every time, and the final file has none of the mutation text.

- Unit, mutation 1 (physical asked for all ids, physical overwrites virtual, physical always consulted): `CourseCatalogPortAdapterTest` ran 14 tests with 5 failures (virtual-only no-physical, null elements, virtual title never overwritten, physical failure pending ids, physical receives only pending ids).
- Integration, mutation A (physical lookup skipped: `if (false)`): `BillingPlansIntegrationTest` ran 13 tests with 2 failures (the mixed-plan title test and the detail-equals-list test).
- Integration, mutation B (precedence flipped: physical asked for every id and wins): 13 tests with the same 2 failures (the id answerable by both returned "Fisico C").
- ArchUnit, mutation C (a class literal of `VirtualCourseJpaRepository` added to the adapter): `ArchitectureTest` ran 7 tests with 1 failure, `course_catalog_port_adapter_reaches_virtual_and_physical_only_through_their_in_ports`.

### Adapter behavior (final)

`CourseCatalogPortAdapter.courseNames(Collection<String>)`: null collection throws `NullPointerException`; null elements are dropped; distinct ids in encounter order; empty means no lookup and `Map.of()`. Sequential and virtual-first: `findPublishedByIds(all ids)`, then `findActiveByIds(ids not answered by virtual)` only when that set is not empty; `putIfAbsent` so a virtual title is never overwritten; result `Map.copyOf`, keyed by the input string. Any `RuntimeException` from a module (including `IllegalArgumentException`) is caught per module: one WARN `Course catalog lookup failed; module={}, courseIds={}` with module `virtual` or `physical` and the set of ids that module was asked (all ids for virtual, the pending ids for physical), no throwable, no exception message, no other data; that module contributes nothing and the other module's names are kept. When virtual fails every id is pending, so physical is asked for all of them. Malformed ids are skipped inside each module and emit no WARN. No `@Transactional`. Example output: `Course catalog lookup failed; module=physical, courseIds=[p1, p2]`.

### Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :api:app:test --tests "*CourseCatalogPortAdapterTest"` exit 0, 14 tests, 0 failures; `./gradlew :api:app:test --tests "*BillingPlansIntegrationTest" --tests "*.integration.catalog.CatalogIntegrationTest" --tests "*VirtualLessonAccessIntegrationTest"` exit 0 (13, 12 and 6 tests, 0 failures); `./gradlew :api:app:test --tests "*ArchitectureTest" :api:billing:test --tests "*ArchitectureTest"` exit 0 (7 and 8 tests) |
| Runtime harness | `BillingPlansIntegrationTest` with real Virtual and Physical rows through the real adapter over HTTP (Testcontainers MySQL, no `CourseCatalogPort` mock in the base), exit 0 |
| Rollback boundary | Revert `CourseCatalogPortAdapter` and its test, the Lombok lines in `api/app/build.gradle.kts`, the ArchUnit additions, the base-class mock removal, the `BillingPlansIntegrationTest` changes, the four Javadoc edits in `api:billing`, the docs and OpenAPI edits, and restore the two deleted placeholder files. After that the S2 behavior returns (placeholder throws, resolver degrades to `null`). S1 and S2 stay valid on their own. |

### Gates (final runs, after the last code edit; sequential, none overlapped)

| Task | Command | Exit | Result |
|------|---------|------|--------|
| 3.16 | `./gradlew :api:billing:test :api:app:test` | 0 | JUnit XML totals: billing 869 tests in 139 suites, app 458 in 79; 0 failures, 0 errors, 0 skipped in both (billing was up to date from the identical earlier run) |
| 3.16 | `./gradlew :api:shared:check :api:auth:check :api:billing:check :api:virtual:check :api:physical:check :api:app:check` | 0 | BUILD SUCCESSFUL; tests of the unchanged modules and of billing were up to date (executed with identical inputs in earlier runs: virtual and physical in S1, billing and app above), JaCoCo verification and Checkstyle executed; this is `check` for every `api` module, not the root `check` (it also needs `verifyLocalInfrastructureContract` and the android module) |
| 3.17 | `./gradlew :api:billing:jacocoTestCoverageVerification :api:app:jacocoTestCoverageVerification` | 0 | Passed (verification ran in the `check` run just before; this run was up to date). From `api/app/build/reports/jacoco/test/jacocoTestReport.xml`: `CourseCatalogPortAdapter` 110/110 instructions, 8/8 branches, 6/6 methods covered, including the Lombok-generated constructor; `api:app` bundle 95.67% instructions and 88.24% branches |
| 3.18 | `./gradlew :api:app:checkstyleMain :api:app:checkstyleTest :api:billing:checkstyleMain :api:billing:checkstyleTest` | 0 | Warnings only. On ADDED lines (606 added Java lines in 8 files: `git diff -U0 HEAD` plus the 2 untracked files whole, intersected with `build/reports/checkstyle/{main,test}.xml`): 0 LineLength, 0 import order, 10 MethodName (snake_case test names, known non-blocker #298: 6 in `CourseCatalogPortAdapterTest`, 4 in `BillingPlansIntegrationTest`). Main-source added lines: 0 findings. |
| 3.19 | `./gradlew :api:app:test --tests "*ArchitectureTest" :api:billing:test --tests "*ArchitectureTest"` | 0 | `com.menta.app.ArchitectureTest` 7 tests, `com.menta.billing.ArchitectureTest` 8 tests, 0 failures |
| 3.12 | `./gradlew :api:app:test --tests "*BillingPlansIntegrationTest" --tests "*.integration.catalog.CatalogIntegrationTest" --tests "*VirtualLessonAccessIntegrationTest"` | 0 | 13, 12 and 6 tests, 0 failures, no cross-test leakage |
| 3.20 | `git diff --shortstat HEAD` plus untracked line counts | n/a | 12 tracked files changed, 271 insertions(+), 70 deletions(-) (341), plus 2 untracked Java files (adapter 86 lines, adapter test 272 lines = 358): 699 changed lines in total, of which docs plus OpenAPI (3.14, 3.15) are 25 (19 added, 6 deleted). Not counted: the openspec artifacts. Above the ~430 target because of test volume; under the 800 budget and at (not above) the ~700 split line. |

Process notes (not evidence):
- A first Checkstyle run found 18 LineLength findings on added test lines; they were fixed (wrapped lines, a shared `assertWarn(event, module, ids)` helper, a boolean `active` instead of an FQN enum argument) and every gate above was rerun after the last edit; only post-fix runs are recorded.
- A `git rm --cached` on the placeholder directory briefly staged the deletion; it was unstaged with `git reset` at once and the files were deleted from the working tree instead. The index is clean.
- Plain `./gradlew` output was unreadable through rtk, so Gradle was run with `rtk proxy` and the counts above come from the JUnit XML.
- No gate run was killed or hung.

### Files changed (all uncommitted)

Main: `api/app/build.gradle.kts` +4; `CourseCatalogPortAdapter` new (86); Javadoc in `CourseCatalogPort` +4/-2, `BillingConfiguration` +3/-4, `PhysicalCourseOwnershipPort` +2/-4, `PlanCourseResult` +4/-4; `NotImplementedCourseCatalogPort` deleted (-28).
Tests: `CourseCatalogPortAdapterTest` new (272); `ArchitectureTest` +30; `BillingPlansIntegrationTest` +205/-3 (real seeds); `CatalogAccessMocksIntegrationTestBase` -2; `NotImplementedCourseCatalogPortTest` deleted (-17).
Docs: `docs/06-BILLING-API.md` +14/-4, `api/openapi/billing-v1.yaml` +5/-2.
Openspec: `tasks.md` (3.1-3.20 marked), this file.

### Deviations and decisions

- The adapter unit test uses a logback `ListAppender` (as S2's `PlanCourseResolverTest`) instead of `OutputCaptureExtension` (tasks 3.2 text): it asserts level, exact formatted message, argument array and a null throwable, which captured console text cannot do reliably.
- Extra beyond the task text: an ArchUnit rule forbidding the adapter any dependency on virtual or physical `domain`, `application.usecase`, `application.port.out` and `infrastructure`; an integration seed of one UUID that exists in both modules (to prove virtual precedence end to end and to make the precedence mutation observable); a defensive `putIfAbsent` so a misbehaving physical answer cannot overwrite a virtual title; null elements dropped in the adapter because `Map.copyOf` based results from the modules would throw on `containsKey(null)`.
- `CourseCatalogPort` class Javadoc (not in the original 3.11 list, added by the orchestrator) now names the real adapter in `api:app`.
- Design unchanged otherwise (A5 per-module `RuntimeException`, A6 WARN format, A7 no `@Transactional`, A9 ADR-0037 not amended, A10 only the shared base lost its mock).

### Risks

- The diff is 699 changed lines, at the pre-agreed split line (docs and OpenAPI are 25 of them); no further growth is available inside this slice without crossing it.
- Context caching: `CatalogAccessMocksIntegrationTestBase` now has one mock fewer, so its three subclasses move to one new shared cache key (A10, no change in the number of contexts). The other `@MockBean CourseCatalogPort` declarations still override the real bean; all 458 `api:app` tests pass.
- Open design question and follow-up kept from the design: WARN without the exception class (A6); ADR-0037 amendment (A9).

### Remaining

- 3.21 (open PR 3, orchestrator) and Phase 0 tracking (0.1-0.4, orchestrator). All of Phases 1 to 3 implemented.

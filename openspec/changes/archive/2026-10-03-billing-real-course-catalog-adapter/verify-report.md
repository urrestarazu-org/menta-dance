```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:8197259b39a27fc4b4177bb7ad2b5422feb1ad3e0e67ce32b610b87d93093655
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 7/7
scenarios: 18/18
test_command: ./gradlew :api:virtual:test :api:virtual:jacocoTestReport :api:virtual:jacocoTestCoverageVerification :api:virtual:checkstyleMain :api:virtual:checkstyleTest :api:physical:test :api:physical:jacocoTestReport :api:physical:jacocoTestCoverageVerification :api:physical:checkstyleMain :api:physical:checkstyleTest :api:billing:test :api:billing:jacocoTestReport :api:billing:jacocoTestCoverageVerification :api:billing:checkstyleMain :api:billing:checkstyleTest :api:app:test :api:app:jacocoTestReport :api:app:jacocoTestCoverageVerification :api:app:checkstyleMain :api:app:checkstyleTest --no-build-cache --rerun-tasks
test_exit_code: 0
test_output_hash: sha256:10ee1597eb4cdaf3fb7fffa8bf3acfca267151f552759dffd467044fd25b1236
build_command: ./gradlew :api:virtual:build :api:physical:build :api:billing:build :api:app:build -x test --no-build-cache --rerun-tasks
build_exit_code: 0
build_output_hash: sha256:88ce9728c3ee98b9649ba8c372722da8bd6808669c8f6809be373c3cd8857160
```

## Verification Report

**Change**: billing-real-course-catalog-adapter (GitHub #108)
**Version**: spec `billing-plan-course-names` (single spec, no version field)
**Mode**: Strict TDD (Gradle, JUnit 5, Mockito, Testcontainers, ArchUnit, JaCoCo, Checkstyle)
**Code verified**: develop at d5a8f09b6622d067bf158e07c10e9bd499838f39 (PRs #319 S1, #320 S2, #321 S3, all merged). No source, test, doc or contract file was modified by this phase.

### Completeness

| Metric | Value |
|--------|-------|
| Requirements (counted with `rg '^### Requirement:'`) | 7 |
| Scenarios (counted with `rg '^#### Scenario:'`) | 18 |
| Tasks total (`- [x]` in tasks.md) | 56 |
| Tasks complete | 56 |
| Tasks incomplete (`- [ ]`) | 0 |
| tasks.md paths containing `../` or a backticked absolute path | 0 |

Phase 0 tasks (0.1-0.4) are orchestrator tracking items; they are marked done in tasks.md and are not verifiable from code (the board state was not inspected).

### Build and Tests Execution

Evidence stored under the scratchpad subfolder v108: test-run.out, build-run.out, redocly.out (plus their exit files). Both Gradle runs used `--no-build-cache --rerun-tasks`, were run one at a time, and were not killed or hung. Console output was captured with `rtk proxy`; test counts below come from the JUnit XML in `api/*/build/test-results/test` (the four modules' result and Checkstyle directories were deleted before the run).

| Command | Exit | Evidence |
|---|---|---|
| test_command (see envelope) | 0 | BUILD SUCCESSFUL in 4m 26s, 40 actionable tasks, 40 executed. sha256 of raw output in the envelope. |
| build_command (see envelope) | 0 | BUILD SUCCESSFUL in 38s, 47 actionable tasks, 47 executed. sha256 of raw output in the envelope. |
| redocly lint (auth, billing, catalog), run as CI does it | 0 | "Your API descriptions are valid", 7 warnings, all pre-existing and unrelated (info-license x3, no-server-example.com x3, operation-4xx-response on catalog). sha256 of raw output 2c52596cbfcb5afd61e5b5aba8bd1941c212e528200a1c5ffcbca3eea9ec8375. |

JUnit XML counts from this fresh run (tests / failures / errors / skipped):

| Module | Suites | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| api:virtual | 77 | 355 | 0 | 0 | 0 |
| api:physical | 94 | 474 | 0 | 0 | 0 |
| api:billing | 139 | 869 | 0 | 0 | 0 |
| api:app | 79 | 458 | 0 | 0 | 0 |
| Total | 389 | 2156 | 0 | 0 | 0 |

These totals equal the apply-progress totals for S3 (virtual 355, physical 474, billing 869, app 458).

Per class (all 0 failures, 0 errors, 0 skipped): CourseCatalogPortAdapterTest 14, PlanCourseResolverTest 8, ListPlansUseCaseImplTest 8, GetPlanUseCaseImplTest 6, BillingPlansIntegrationTest 13, VirtualCourseCatalogPortImplTest 14, VirtualCourseRepositoryAdapterTest 18, PhysicalCourseAvailabilityPortImplTest 15, PhysicalCourseRepositoryAdapterTest 14, VirtualCourseCatalogIntegrationTest 10, PhysicalCourseAvailabilityIntegrationTest 16, CatalogIntegrationTest (catalog package) 12, VirtualLessonAccessIntegrationTest 6.

ArchUnit (`ArchitectureTest`, fresh XML): api:virtual 7, api:physical 8, api:billing 8, api:app 7, all passing. The api:auth ArchitectureTest XML in the tree is stale (not part of this run) and is not counted.

JaCoCo gates. In the test_command run, `jacocoTestCoverageVerification` executed for all four modules (exit 0). The layered gates (`jacocoDomainApplicationCoverageVerification`, `jacocoInfrastructureCoverageVerification`) hang off `check`, so they are not in the test_command; they were executed in the build_command run (`:api:virtual:build`, `:api:physical:build`, `:api:billing:build` each list both layered tasks in the log, and `:api:app:build` runs the flat 0.90 floor). Note that the build_command run used `-x test`, so JaCoCo read the execution data produced by the test_command run immediately before it.

| Gate | Threshold (LINE) | Measured from jacocoTestReport.xml (line coverage, by package prefix) | Result |
|---|---|---|---|
| virtual domain + application | 0.95 | 97.98% (874/892) | PASS (gate task exit 0) |
| virtual infrastructure | 0.90 | 95.20% (754/792) | PASS |
| physical domain + application | 0.95 | 98.09% (821/837) | PASS |
| physical infrastructure | 0.90 | 97.53% (712/730) | PASS |
| billing domain + application | 0.85 | 96.82% (1493/1542) | PASS |
| billing infrastructure | 0.85 | 91.56% (1628/1778) | PASS |
| app flat floor (`moduleCoverageFloor`) | 0.90 | 95.94% (425/443); branch 88.24% (60/68) | PASS |

The measured percentages are my own computation from the XML and are informational; the pass/fail verdict of each gate is the Gradle task exit code.

Changed-file coverage (class level, from the same XML), every class fully covered on line, branch and method counters: CourseCatalogPortAdapter 24/24 lines, 8/8 branches, 6/6 methods (including the Lombok-generated constructor); PlanCourseResolver 17/17 lines, 6/6 branches; VirtualCourseCatalogPortImpl 105/105 lines; PhysicalCourseAvailabilityPortImpl 47/47 lines; VirtualCourseRepositoryAdapter 88/88 lines; PhysicalCourseRepositoryAdapter 30/30 lines; ListPlansUseCaseImpl 22/22 lines; GetPlanUseCaseImpl 24/24 lines. Average of the changed classes: 100%.

Checkstyle on code added by the three PRs: I intersected `git diff -U0 <commit>~1 <commit> -- '*.java'` for 28cc690, ff6cbf0 and d5a8f09 (1579 added-line positions in 32 Java files) with every `build/reports/checkstyle/{main,test}.xml`. Result: 25 findings on added lines, all of them MethodName on snake_case test names (S1 9, S2 6, S3 10), the known non-blocker #298. Zero LineLength, zero import-order, zero other checks, zero findings in main sources. (The Checkstyle XML reports present in the tree hold 5990 warnings overall, dominated by LineLength and MethodName, including stale auth and shared reports not regenerated by this run; none of them is on added lines.)

### TDD Compliance

| Check | Result | Details |
|-------|--------|---------|
| TDD evidence reported | Yes | apply-progress.md has a "TDD Cycle Evidence" table in each of the three batches. |
| All tasks have tests | Yes | Every code task maps to a named test class; build-file, deletion and Javadoc tasks are marked structural/covered. |
| RED confirmed (tests exist) | Yes, with note | All referenced test files exist and ran. RED for 1.1, 1.2, 1.5, 1.6, 2.1-2.4, 3.2-3.3 was a compile failure on an undeclared symbol; RED for 1.8, 1.9, 3.6 could not occur naturally (tests came after the code per the task order) and was proven by temporary mutations reported in apply-progress. I did not repeat those mutations. |
| GREEN confirmed (tests pass) | Yes | All listed test classes pass in my fresh run (counts above). |
| Triangulation adequate | Yes | Multiple cases per behavior (for example 8 new cases in each batch port-impl test, 14 in the adapter test, 13 in the integration test). |
| Safety net for modified files | Yes | apply-progress reports N/N before-counts for modified test files and "N/A (new)" for the new ones (PlanCourseResolverTest, CourseCatalogPortAdapterTest). |

### Test Layer Distribution (tests in the named classes, not only the new ones)

| Layer | Tests | Classes | Tool |
|-------|-------|---------|------|
| Unit (Mockito, logback ListAppender) | 97 | 8 | JUnit 5 + Mockito |
| Integration (Testcontainers MySQL, HTTP) | 39 | 3 | Testcontainers MySQL |
| Architecture | 30 | 4 | ArchUnit |

### Assertion Quality

No tautologies, no assertion without a production call, no ghost loops. Loops over collections (`listed.forEach`, `allMatch`) are guarded by prior `hasSize(3)` or by a list built non-empty. Orphan empty-result assertions have non-empty companions. Mock call-count assertions (`verify`, `verifyNoInteractions`) are used where the spec itself is about lookup calls. One stale stub is reported as a SUGGESTION below.

**Assertion quality**: 0 CRITICAL, 0 WARNING, 1 SUGGESTION.

### Spec Compliance Matrix

Layer key: U = unit, I = integration against real MySQL through HTTP, M = module-level MySQL parity. All listed tests passed in the fresh run.

| Requirement | Scenario | Covering test(s) | Result |
|---|---|---|---|
| R1 Resolvable courses show their title | Published virtual course resolves | BillingPlansIntegrationTest > resolvable_courses_show_their_title_with_virtual_first_and_the_plan_course_order (I); get_returns_the_full_detail_of_an_active_plan_including_resolved_course_names (I); CourseCatalogPortAdapterTest > virtual_titles_resolve_and_physical_is_not_consulted_when_virtual_answers_everything (U) | COMPLIANT |
| R1 | Active physical course resolves | BillingPlansIntegrationTest > resolvable_courses_show_their_title... (physical id returns "Bachata Nivel 1") (I); CourseCatalogPortAdapterTest > physical_receives_only_the_ids_virtual_did_not_answer (U) | COMPLIANT |
| R1 | Mixed plan resolves both modalities | BillingPlansIntegrationTest > resolvable_courses_show_their_title... and get_resolves_the_same_names_as_the_list_for_every_kind_of_plan (I, exact id=name list in plan order); PlanCourseResolverTest > names_are_returned_per_plan_in_that_plans_own_course_order (U) | COMPLIANT |
| R2 Unresolvable courses degrade to null | Nonexistent course | BillingPlansIntegrationTest > unknown_and_malformed_ids_have_a_null_name_without_affecting_a_resolvable_course (I); PlanCourseResolverTest > an_unresolved_course_keeps_its_stored_id_with_a_null_name (U) | COMPLIANT |
| R2 | Not publicly visible | BillingPlansIntegrationTest > courses_that_are_not_publicly_visible_keep_their_id_with_a_null_name (DRAFT, ARCHIVED, inactive physical; list and detail) (I) | COMPLIANT |
| R2 | Malformed id among valid ones | BillingPlansIntegrationTest > unknown_and_malformed_ids_have_a_null_name... ("course-1" null, valid course keeps its title) (I); VirtualCourseCatalogPortImplTest and PhysicalCourseAvailabilityPortImplTest > find_*_by_ids_skips_malformed_blank_and_null_ids_and_queries_only_the_valid_one (U); VirtualCourseCatalogIntegrationTest and PhysicalCourseAvailabilityIntegrationTest > batch_lookup_skips_a_malformed_id... (M) | COMPLIANT |
| R2 | Plan without courses | BillingPlansIntegrationTest > a_plan_without_courses_lists_an_empty_course_list (I); PlanCourseResolverTest > a_plan_without_courses_has_an_empty_result_list (U) | COMPLIANT |
| R3 Virtual takes precedence | Id answerable by both | BillingPlansIntegrationTest (id seeded in both modules returns "Virtual C", not "Fisico C") (I); CourseCatalogPortAdapterTest > a_virtual_title_is_never_overwritten_by_a_physical_answer_for_the_same_id (U) | COMPLIANT |
| R3 | Physical lookup skipped when virtual answers everything | CourseCatalogPortAdapterTest > virtual_titles_resolve_and_physical_is_not_consulted_when_virtual_answers_everything (`verifyNoInteractions(physicalCatalog)`) (U); physical_receives_only_the_ids_virtual_did_not_answer (U) | COMPLIANT (unit only) |
| R4 Batched, constant-cost | Shared course across plans | PlanCourseResolverTest > a_course_shared_by_several_plans_is_looked_up_once (U); ListPlansUseCaseImplTest > resolves_the_courses_of_all_plans_with_one_batch_call (U); BillingPlansIntegrationTest > a_course_shared_by_several_plans_has_the_same_title_in_each_of_them (I) | COMPLIANT |
| R4 | Lookup count independent of size | PlanCourseResolverTest > resolves_the_distinct_ids_of_all_plans_with_a_single_catalog_call (3 plans, `verifyNoMoreInteractions`, ids a,b,c in encounter order); CourseCatalogPortAdapterTest > each_module_is_looked_up_once_with_the_distinct_ids_in_encounter_order (U); GetPlanUseCaseImplTest > resolves_the_course_names_of_the_plan_with_one_batch_call (U) | COMPLIANT (see W1: no test scales to 20 courses / 10 plans) |
| R4 | Nothing to resolve | PlanCourseResolverTest > no_plans_means_no_catalog_call_and_no_names and plans_without_courses_mean_no_catalog_call_and_no_names; ListPlansUseCaseImplTest > plans_without_courses_never_reach_the_catalog and GetPlanUseCaseImplTest > a_plan_without_courses_never_reaches_the_catalog; CourseCatalogPortAdapterTest > an_empty_input_performs_no_lookup_and_returns_an_empty_map (all `verifyNoInteractions`) (U); BillingPlansIntegrationTest > returns_200_with_an_empty_list_when_there_are_no_active_plans (I) | COMPLIANT |
| R5 A failing module degrades only its own courses | Virtual lookup fails | CourseCatalogPortAdapterTest > a_virtual_failure_degrades_only_virtual_ids_and_physical_still_resolves (physical resolves, exactly one WARN, exact message, no throwable) (U) | COMPLIANT (unit only, by design) |
| R5 | Physical lookup fails | CourseCatalogPortAdapterTest > a_physical_failure_keeps_the_virtual_names_and_only_names_the_pending_ids (U) | COMPLIANT (unit only, by design) |
| R5 | Both modules fail | CourseCatalogPortAdapterTest > both_modules_failing_gives_an_empty_map_and_one_warn_per_module (U); PlanCourseResolverTest > a_catalog_failure_degrades_to_no_names... (null names with stored ids) (U) | COMPLIANT (unit only, by design) |
| R6 Batch matches single-id visibility | Virtual parity | VirtualCourseCatalogIntegrationTest > batch_and_single_id_lookups_return_only_the_published_course_with_the_same_title (PUBLISHED/DRAFT/ARCHIVED/unknown, summary equality) (M, real MySQL) | COMPLIANT |
| R6 | Physical parity | PhysicalCourseAvailabilityIntegrationTest > batch_and_single_id_lookups_return_only_the_active_course_with_the_same_title (ACTIVE/inactive/unknown) (M, real MySQL) | COMPLIANT |
| R7 Response contract unchanged | Shape preserved | BillingPlansIntegrationTest > every_course_item_has_exactly_the_keys_id_and_name_in_both_endpoints (`containsOnlyKeys("id","name")`, null name present) (I); OpenAPI diff in d5a8f09 changes only the description of `PlanCourseResponse.name`, `type: [string, "null"]` and `required: [id, name]` untouched; redocly exit 0 | COMPLIANT |

**Compliance summary**: 18/18 scenarios compliant (3 of them at unit level only by design), 7/7 requirements.

### Correctness (Static Evidence, develop at d5a8f09)

| Item | Status | Notes |
|------|--------|-------|
| Billing port `Map<String,String> courseNames(Collection<String>)` | Implemented | Javadoc: immutable, resolved ids only, input-keyed, empty means no lookup, null collection throws NullPointerException. `courseName(String)` is gone. |
| Virtual `findPublishedByIds` / physical `findActiveByIds` | Implemented | Return `Map.copyOf` keyed by the caller's input id; `CourseId.of` filter skips null, blank and non-UUID ids with no query and no batch failure; `Map.of()` without repository call when no valid id; duplicates deduplicated through `LinkedHashSet`; null collection throws NPE. Both reuse `toSummary`, so the summary equals the single-id one (asserted by the parity tests). |
| Repository queries | Implemented | `findByIdInAndStatus(ids, PUBLISHED)` and `(ids, ACTIVE)`; virtual then runs `countByCourseIdIn` and `aggregateByCourseIdIn` over the hits only, skipped when nothing matches; an empty id collection does no query. `@Transactional(REQUIRED, readOnly)`. |
| Visibility parity with single-id lookups | Implemented and proven | PUBLISHED/ACTIVE only, same mapping; real-MySQL parity tests pass. |
| `CourseCatalogPortAdapter` | Implemented | `@Slf4j @Component @RequiredArgsConstructor`, no `@Transactional` annotation (the word appears only in Javadoc); virtual first on all distinct ids, physical only for ids virtual did not answer and skipped when none; `putIfAbsent`; per-module `RuntimeException` caught, WARN `"Course catalog lookup failed; module={}, courseIds={}"`, no throwable, no other argument; malformed ids never reach the catch (skipped inside the modules); result `Map.copyOf`. |
| `PlanCourseResolver` | Implemented | `resolveNames` collects distinct ids across all plans in a `LinkedHashSet`, one port call, no call when empty, catch-all `RuntimeException` with one WARN and an empty map; `toResults` maps each plan's courses in their own order with `names.get(id)`. |
| Use cases | Implemented | List resolves all plans together once; detail resolves `List.of(plan)`; neither is transactional. |
| Placeholder deletion | Verified | `NotImplementedCourseCatalogPort` and its test are deleted; `rg` over api, docs, bff and the rest of the repo (excluding openspec) returns no match. It remains named only in the openspec change artifacts (design.md and similar) that describe the change. |
| Lombok in `api:app` | Verified | `compileOnly(libs.lombok)` and `annotationProcessor(libs.lombok)` at api/app/build.gradle.kts lines 53-54; used by the adapter. |
| ArchUnit | Verified | Adapter added to the `com.menta.app.billing` package rule; two new rules (direct dependency on Virtual's `VirtualCourseCatalogPort` and Physical's `PhysicalCourseAvailabilityPort`; no dependency on their domain, usecase, port.out or infrastructure packages). `app_should_not_depend_on_physical_infrastructure` still passes (7/7). |
| Shared test base | Verified | `CatalogAccessMocksIntegrationTestBase` no longer mocks `CourseCatalogPort`; the other tests that declare it still compile and pass. |
| Integration cleanup | Verified | `BillingPlansIntegrationTest` `@AfterEach` deletes plan courses, plans, virtual courses and physical courses (FK-safe order); the three shared-context classes pass together in the full run. |
| Docs and OpenAPI | Consistent | docs/06-BILLING-API.md (Spanish) describes one lookup per module per request, virtual precedence, null degradation with the stored id, 200 with the full plan, failure isolation with a WARN naming module and ids. The `courses[].name` description in billing-v1.yaml matches; schema shape unchanged (`name` stays `[string, "null"]`, `required: [id, name]`). The old "#40/#46 pending" wording is gone. |
| No cache | Verified | No Caffeine, `@Cacheable`, `CacheManager` or `EnableCaching` anywhere under api (cache deferred to #312). |

### Coherence (Design)

| Decision | Followed? | Notes |
|----------|-----------|-------|
| A1 batch port shape | Yes | |
| A2 input-keyed maps of summaries | Yes | |
| A3 constant 3 queries virtual, 1 physical | Yes in code | Pinned only at mock level, see W1. |
| A4 malformed filter inside each module | Yes | |
| A5 any RuntimeException is a failure, no IAE special case | Yes | Pinned by an_illegal_argument_from_a_module_is_a_failure_like_any_other_runtime_exception. |
| A6 WARN format, no throwable | Yes | Pinned by exact message, argument array and null throwable. |
| A7 adapter in api:app, Lombok, no `@Transactional` | Yes | |
| A8 resolver | Yes, signature deviates | `resolveNames(List<Plan>, CourseCatalogPort)` takes the port as a second parameter because the resolver is a static utility (documented in apply-progress). See W3. |
| A9 ADR-0037 not amended | Yes | The ADR still says "paralelo"; code is sequential, virtual-first. Follow-up issue is the orchestrator's. |
| A10 only the shared base loses the mock | Yes | |

### Issues Found

**CRITICAL**: None.

**WARNING**:
- W1. Requirement R4 "Batched, constant-cost resolution per request" is pinned at the level the spec states it (lookup calls per module): one port call per request with the distinct ids of all plans (resolver, list and detail use cases), one call per module with distinct ids in encounter order, no call for empty input, no physical call when virtual answers everything. The gaps: (a) no test scales the input as the scenario describes (2 courses versus 20 distinct courses across 10 plans); the largest tests use 3 plans and 3 ids, and the implementation has no loop over plans or ids, so the risk is low; (b) the repository adapters' constant query count (virtual: `findByIdInAndStatus` + `countByCourseIdIn` + `aggregateByCourseIdIn`; physical: `findByIdInAndStatus`) is pinned only by Mockito `verify` of exactly one call to each mocked JPA repository method with three requested ids, plus `never()` on the per-id `findById`; there is no real SQL statement-count assertion (for example Hibernate statistics) and no scaling case. A regression that issues per-hit queries inside the repository adapter would be caught only for the shapes mocked there.
- W2. The failure-isolation scenarios (virtual fails, physical fails, both fail) are covered at unit level only, as designed (a Spring spy would add a context cache key). No test drives the real chain adapter plus resolver to an HTTP 200 under a throwing module, and the "malformed id emits no WARN" rule is asserted at the adapter against mocked modules that never throw, so the real malformed-id-no-WARN behavior rests on the module tests (skip without failing) and on the integration test returning 200 with a null name, without log capture.
- W3. Documented design deviations, all benign: `resolveNames` takes the port as a parameter (A8); the resolver WARN has its own text `Course name resolution failed; courseIds={}` (the design fixes A6 for the adapter only); the adapter test captures logs with a logback `ListAppender` instead of `OutputCaptureExtension` (stronger: it asserts level, arguments and absence of a throwable).
- W4. ADR-0037 still describes catalog routing as "parallel" while the adapter is sequential and virtual-first (known; follow-up issue to be created by the orchestrator, not a blocker).
- W5. 25 Checkstyle MethodName warnings on added lines (snake_case test names, known non-blocker #298); nothing else.

**SUGGESTION**:
- GetPlanUseCaseImplTest > returns_the_full_detail_of_an_active_plan keeps a stub `courseNames(any())` (line 72) that is never invoked because its plan has no courses; harmless dead setup (mocks are not strict there).
- The WARN omits the exception class by design (A6, open question in the design); diagnosing a module failure needs that module's own logs.
- RED for tasks 1.8, 1.9 and 3.6 was established by temporary mutation rather than a natural failure; consider keeping such parity tests as the first step in future slices.
- Lombok-generated code in `api:app` counts toward the flat 0.90 floor (the adapter's generated constructor is covered).

### Unverified (not run fresh by this phase, not counted as passed)

- Partial-failure behavior end to end over HTTP (see W2); the physical-only and virtual-only end-to-end branches are covered only by the seeded rows of BillingPlansIntegrationTest.
- The apply phase's sensitivity and mutation proofs (batches 1-3) were not repeated.
- The orchestrator's per-PR gates on each branch before merge, the board state of Phase 0 tasks, and the repository-root `./gradlew check` (unrunnable here because of the android module) were not run by this phase.
- The build_command run used `-x test`, so its JaCoCo steps consumed the execution data of the test_command run; the test XML above comes from the test_command run only.

### Verdict

PASS WITH WARNINGS. No critical findings: all 56 tasks complete, 7/7 requirements and 18/18 scenarios have passing covering tests, 2156 tests with 0 failures, all layered and flat JaCoCo gates green, Checkstyle clean on added lines apart from the known MethodName non-blocker, redocly exit 0. The warnings are test-pinning gaps for the constant-cost claim (W1), unit-only failure isolation (W2), and documented minor deviations (W3-W5).

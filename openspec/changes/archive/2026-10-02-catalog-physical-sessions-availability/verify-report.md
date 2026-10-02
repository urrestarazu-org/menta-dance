```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:30766134d1b350a90ad049201e12d5d014f9326e65bb106bc0d97149b276361a
verdict: pass
blockers: 0
critical_findings: 0
requirements: 14/14
scenarios: 33/33
test_command: ./gradlew :api:physical:test :api:physical:jacocoTestReport :api:physical:jacocoTestCoverageVerification :api:physical:checkstyleMain :api:physical:checkstyleTest :api:app:test :api:app:jacocoTestReport :api:app:jacocoTestCoverageVerification :api:app:checkstyleMain :api:app:checkstyleTest --no-build-cache --rerun-tasks
test_exit_code: 0
test_output_hash: sha256:fe24e62e08e896df6270fd3da6f3611132deb6e16e800ae883cd68e3612265f2
build_command: ./gradlew :api:physical:build :api:app:build -x test --no-build-cache --rerun-tasks
build_exit_code: 0
build_output_hash: sha256:5334c92c8dcfa6706e6f14d9680f633be6f77deab083fb4b56ab18f6d30eaa39
```

## Verification Report (re-verification after remediation)

**Change**: catalog-physical-sessions-availability (GitHub issue #107; remediation issue #316)
**Version**: N/A (two new capability specs, no modified ones)
**Mode**: Strict TDD (Gradle runner; strict-tdd-verify.md applied)
**Code under verification**: develop @ 1fe883c (PRs #313 S1, #314 S2, #315 S3, #317 S4 test-only). `git status` shows only untracked `openspec/` artifacts; `git diff HEAD -- api bruno docs .github` is empty. Nothing was modified except this report.
**Attempt**: 2. The failed attempt 1 carried `evidence_revision` `sha256:53106d31...a161e6`; this attempt carries the value printed by `gentle-ai sdd-attempt status` (read at the start and again before writing), `sha256:30766134d1b350a90ad049201e12d5d014f9326e65bb106bc0d97149b276361a`, which differs.

### Verdict in one line

PASS: 0 CRITICAL, 0 WARNING, 11 SUGGESTION. All 33 scenarios of both specs now have a covering test that passed in a fresh run; the three attempt-1 findings (C1, W1, W2) are closed by the S4 pins, which I checked for vacuity. Everything fresh exited 0.

### Re-evaluation of the attempt-1 findings (re-derived, not copied)

| Finding | Attempt-1 status | Now | Evidence |
|---|---|---|---|
| C1 "Zero-width and inverted range" had no test | CRITICAL | Closed | `PhysicalCourseAvailabilityIntegrationTest.a_zero_width_or_inverted_range_is_empty_even_with_a_session_on_the_bound` passed in the JUnit XML of this run. It seeds a session at `T`, asserts `listSessions(from=T, to=T)` is empty, asserts `from=T+1h, to=T` is empty, then asserts a control range `[T, T+1s)` returns exactly that session. The control proves the session is visible to the read, so the two empty assertions are not vacuous. The `from == to` case is bound-sensitive (with `<= :to` the session would be returned); the inverted case is empty under any bound choice, so it is a real exercise of the code but not a discriminating one |
| W1 converted hold half of "Expired and converted holds are ignored" | WARNING | Closed | `a_converted_hold_is_not_subtracted_from_availability_while_an_active_one_is` passed. It seeds one active hold and one converted hold (`converted_at` set), both unexpired (`expires_at` in the future), so only `converted_at` tells them apart; asserts `activeCapacityHolds == 1` and `availableSpots == 19` on capacity 20. The active hold is the non-vacuous control. The expired-hold half stays covered by `computes_available_spots_as_a_live_count_of_assignments_and_active_holds` (17 = 20 - 2 - 1, one expired hold seeded) |
| W2 "List has no sessions" did not pin that the list performs no availability read | WARNING | Closed | `CatalogCompositionServiceTest.listCourses_returns_physical_courses_without_reading_any_session_availability` passed. The physical port returns one course, the result is asserted to contain `phys-1` (so the path really ran), then `verify(physicalPort, never()).listSessions(any(), any(), any())`. `listSessions` is the only availability read on that port |

I did not repeat the apply-batch-4 sensitivity proofs (temporary production edits); see "Not verified".

### Completeness

| Metric | Value |
|--------|-------|
| Tasks total (tasks.md) | 50 |
| Tasks complete `[x]` | 50 |
| Tasks incomplete `[ ]` | 0 |
| Requirements counted (`^### Requirement:`) | 14 (catalog-course-detail 9, physical-session-availability 5) |
| Scenarios counted (`^#### Scenario:`) | 33 (catalog-course-detail 21, physical-session-availability 12) |
| Requirements fully compliant | 14 / 14 |
| Scenarios compliant | 33 / 33 |

Counts come from `/opt/homebrew/bin/rg -c` over both spec files, run in this session; they equal the previous 14 and 33. tasks.md: 50 lines match `^- \[[ x]\]`, all 50 are `[x]`. `rg` for `../` or a backticked path starting with `/` returns no match in tasks.md (two lines contain a backtick followed by `/`, namely `` `LOG`/SLF4J `` and `` `assignedSpots`/`activeCapacityHolds` ``, which are not paths).

### Build & Tests Execution (fresh, `--no-build-cache --rerun-tasks`)

Gradle ran through `rtk proxy ./gradlew ...` so the console output is raw. I deleted `api/{physical,app}/build/test-results` and the checkstyle report directories before the run, so every XML file below belongs to this run. No run hung or was killed this time (the test_command finished in 4m 15s). Raw outputs are in the session scratchpad under `reverify/`.

| Command | Exit | sha256 of captured output |
|---|---|---|
| test_command (physical + app: test, jacocoTestReport, jacocoTestCoverageVerification, checkstyleMain, checkstyleTest) | 0 (BUILD SUCCESSFUL in 4m 15s, 28 actionable tasks, 28 executed) | fe24e62e08e896df6270fd3da6f3611132deb6e16e800ae883cd68e3612265f2 |
| build_command (`:api:physical:build :api:app:build -x test`) | 0 (BUILD SUCCESSFUL in 20s, 31 actionable tasks, 31 executed) | 5334c92c8dcfa6706e6f14d9680f633be6f77deab083fb4b56ab18f6d30eaa39 |
| redocly lint exactly as CI (`docker run --rm -v "$(pwd)/api/openapi:/spec:ro" redocly/cli:latest lint /spec/auth-v1.yaml /spec/billing-v1.yaml /spec/catalog-v1.yaml`) | 0 ("Your API descriptions are valid", 7 warnings) | 3e322b8d41666a37b710caacb71c25ac81ad035a5e75d9ba1e2b017ac9e82566 |

The 7 redocly warnings are `info-license` (3), `no-server-example.com` (3) and `operation-4xx-response` (1, on the catalog list operation). None concerns the new `oneOf` schemas.

**Tests (JUnit XML under `api/*/build/test-results/test`)**:

| Module | Suites | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| api:physical | 94 | 463 | 0 | 0 | 0 |
| api:app | 78 | 431 | 0 | 0 | 0 |
| Total | 172 | 894 | 0 | 0 | 0 |

Relevant classes (XML tests/failures): CatalogCompositionServiceTest 26/0, CatalogControllerTest 14/0, CatalogIntegrationTest 12/0, PhysicalCourseAvailabilityIntegrationTest 14/0, PhysicalCourseQuoteIntegrationTest 8/0, HoldCapacityAdapterIntegrationTest 12/0, PhysicalPurchaseIntegrationTest 21/0, physical PhysicalSessionTest 16/0, PhysicalCourseAvailabilityPortImplTest 7/0, app ArchitectureTest 5/0, physical ArchitectureTest 8/0. The three S4 pins each appear as `passed` (not skipped) in the XML. Delta against attempt 1: +3 tests in api:app (428 to 431), physical unchanged (463), 891 to 894.

**Coverage (JaCoCo XML, LINE, computed from `jacocoTestReport.xml` of this run)**:

| Gate | Result | Threshold | Status |
|---|---|---|---|
| physical domain + application | 98.04% (801 covered / 16 missed) | 0.95 | Above |
| physical infrastructure | 97.51% (706 / 18) | 0.90 | Above |
| api:app flat | 95.70% (401 / 18) | 0.90 | Above |

The physical layered gates (`jacocoDomainApplicationCoverageVerification`, `jacocoInfrastructureCoverageVerification`) hang off `check`, not off `jacocoTestCoverageVerification`, so the test_command does not run them. The build_command executed both (visible in its output, lines "Task :api:physical:jacocoInfrastructureCoverageVerification" and "...DomainApplication...", BUILD SUCCESSFUL). Because it ran with `-x test`, it read the `test.exec` files produced by the test_command a few minutes earlier in the same session (`api/physical/build/jacoco/test.exec` 17:49, `api/app/build/jacoco/test.exec` 17:51); that is fresh data from this session, not a stale one. S4 is test-only, so the module totals are identical to attempt 1.

**ArchUnit**: `com.menta.app.ArchitectureTest` 5/5 and `com.menta.physical.ArchitectureTest` 8/8 passed in this run. The catalog package imports only `com.menta.physical.application.dto.*` and `com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort` (both pre-existing edges) plus `com.menta.virtual.application.*` (pre-existing). The diff `77b5091..1fe883c` of `api/app/src/main` and `api/physical/src/main` adds only `PhysicalSessionAvailability`/`PhysicalCourseSummary` DTO imports and drops `VirtualCourseSummary`; no `infrastructure` import and no new module edge.

**Checkstyle on lines added by the four PRs**: I took `git diff -U0 77b5091 1fe883c -- api` (the four commits are linear: 2b69a9f, b7b4486, 6a10f81, 1fe883c on top of 77b5091), so line numbers are those of HEAD, which is what the Checkstyle XML reports use. 12 Java files, 788 added lines, intersected with `api/{app,physical}/build/reports/checkstyle/{main,test}.xml` of this run. Result: 14 warnings on added lines, all `MethodNameCheck` (snake_case test names, known and tracked as #298). Zero LineLength, import-order, Javadoc or any other check on added lines. Whole-report totals (pre-existing noise, not attributable): app main 79, app test 901, physical main 400, physical test 452. Main-code files added or edited by the change have 0 warnings on added lines.

**Coverage of the changed main files (api:app)**: CatalogCompositionService 35/35 lines, 6/6 branches; CatalogPhysicalCourseDetailResponse 17/17; CatalogConfiguration 2/2; CatalogController 5/5; CatalogCourseDetail has no executable lines; CatalogCourseDetailResponse 35/41 lines and 2/4 branches (uncovered lines are the pre-existing duration-format branches; this change only touched Javadoc and `implements`). The physical changes (`PhysicalSessionJpaRepository` SQL string, `PhysicalCourseAvailabilityPort` Javadoc) add no executable lines.

### TDD Compliance

| Check | Result | Details |
|-------|--------|---------|
| TDD Evidence reported | Yes | apply-progress.md has "TDD Cycle Evidence" tables for S1, S2, S3 and S4 |
| All tasks have tests | Yes | every behavior task maps to a test file; 1.4, 2.1, 2.5 are Javadoc, scaffold and config with an explicit reason |
| RED confirmed (tests exist) | Yes | the four touched test files exist on develop @ 1fe883c |
| GREEN confirmed (tests pass now) | Yes | fresh run: CatalogCompositionServiceTest 26, CatalogControllerTest 14, CatalogIntegrationTest 12, PhysicalCourseAvailabilityIntegrationTest 14, all with 0 failures |
| Triangulation adequate | Yes | window 30 vs 7, 100 vs 101, sold-out vs open, bounds 1/90 vs -1/0/91, virtual vs physical vs missing, active vs converted hold, empty vs non-empty control ranges |
| Safety net for modified files | Yes | baselines recorded before edits (S1 9/9, S2 23/23, S3 6/6, S4 12/12 and 25/25) |

Documented deviations (not blockers): S3 tasks 3.2/3.3 and all three S4 pins are characterization tests that were green on first run (stated in tasks.md; S4 RED was demonstrated by sensitivity edits per apply-progress, not repeated here); task 3.4 never went red (lint on the unchanged file was already exit 0) and a substitute RED was recorded; the S2 behavioral RED was obtained by temporarily stubbing the physical branch.

### Test Layer Distribution (whole files touched by the change, from XML)

| Layer | Tests | Files | Tools |
|-------|-------|-------|-------|
| Unit (Mockito, MockMvc standalone) | 40 | 2 (CatalogCompositionServiceTest 26, CatalogControllerTest 14) | JUnit 5, Mockito, AssertJ, MockMvc |
| Integration (Spring context + Testcontainers MySQL) | 26 | 2 (CatalogIntegrationTest 12, PhysicalCourseAvailabilityIntegrationTest 14) | Testcontainers, TestRestTemplate |
| E2E (live API / Bruno) | 0 | 0 | Bruno file exists, never run live |

### Assertion Quality

**Assertion quality**: 0 CRITICAL, 0 WARNING in the changed tests. No tautologies, no assertion without a production call, no ghost loops in JUnit code. Each empty-collection assertion has a non-empty companion: the zero-width pin carries its own control range; the `sessions` empty-array tests are complemented by the seeded tests `get_lists_only_scheduled_sessions_inside_the_window_in_ascending_order`, `get_exposes_exactly_the_four_public_session_fields_with_a_utc_instant`, `get_lists_a_sold_out_session_with_zero_available_spots`; `a_cancelled_session_never_appears_in_the_public_availability_read` is paired with the SCHEDULED sessions asserted elsewhere. The new list pin asserts a non-empty result before `never()`. The `from/to` test is not vacuous: honoring the parameters would drop the `now + 1d` session. Mock-heavy check: CatalogCompositionServiceTest is mock-based by design (a composition class over two ports) and mixes `verify` with value assertions; no test is mock-only.

### Spec Compliance Matrix

Legend: COMPLIANT = covering test passed in the fresh run. "(composed)" = proven by two or more tests at different layers rather than one end-to-end test.

#### Spec catalog-course-detail (9 requirements, 21 scenarios)

| # | Requirement | Scenario | Covering test(s) | Result |
|---|---|---|---|---|
| 1 | Virtual-first resolution | Virtual id answers the unchanged virtual detail | CatalogCompositionServiceTest `getCourseDetail_does_not_consult_the_physical_port_for_a_virtual_detail`, `getCourseDetail_happy_path_returns_full_detail_with_modules_and_lessons`; CatalogControllerTest `get_returns_virtual_detail_with_modules_lessons_and_premium_flag_set`, `get_does_not_ask_the_physical_port_for_a_virtual_detail`; CatalogIntegrationTest `get_resolves_a_virtual_course_with_modules_lessons_stats_and_no_video_leak` | COMPLIANT |
| 2 | Virtual-first resolution | Virtual id never receives a sessions field | CatalogControllerTest `the_virtual_detail_body_keeps_its_shape_with_no_modality_or_sessions` (exact key set of the real converter output) | COMPLIANT |
| 3 | Unknown ids 404 | Unknown id | CatalogIntegrationTest `get_an_unknown_course_id_returns_a_404_problem`; CatalogControllerTest `get_returns_404_when_neither_module_has_the_course` | COMPLIANT |
| 4 | Unknown ids 404 | Inactive physical course is not enumerable | CatalogIntegrationTest `get_an_inactive_physical_course_returns_the_same_404_as_a_missing_one` (inactive course with a scheduled session seeded); CatalogCompositionServiceTest `getCourseDetail_inactive_physical_course_is_a_404_and_no_sessions_are_read` | COMPLIANT |
| 5 | Physical detail returns course data | Physical id answers 200 with course data | CatalogControllerTest `get_returns_the_physical_detail_when_only_physical_resolves_the_id`; CatalogCompositionServiceTest `getCourseDetail_physical_only_id_returns_the_physical_detail`, `the_physical_detail_shape_has_no_internal_counts_and_no_price`; CatalogIntegrationTest `get_a_physical_only_course_returns_its_physical_detail`; CatalogControllerTest `the_physical_detail_body_has_only_the_public_keys_and_no_type_property` (top-level keys, so no price and no `quoteEndpoint`) | COMPLIANT |
| 6 | Physical detail returns course data | Physical course without upcoming sessions | CatalogControllerTest `the_physical_detail_serializes_an_empty_sessions_array_not_null`; CatalogCompositionServiceTest `getCourseDetail_answers_an_empty_sessions_list_when_none_are_scheduled`; CatalogIntegrationTest `get_a_physical_only_course_returns_its_physical_detail` (real DB, empty array) | COMPLIANT |
| 7 | Forward window | Default window of 30 days | CatalogCompositionServiceTest `getCourseDetail_asks_for_sessions_from_now_to_now_plus_the_default_window` (exact `[NOW, NOW+30d)` handed to the port); PhysicalCourseAvailabilityIntegrationTest `a_session_exactly_at_the_upper_bound_is_excluded_and_one_at_the_lower_bound_included` (DB half-open); CatalogIntegrationTest `get_lists_only_scheduled_sessions_inside_the_window_in_ascending_order` (now+20d in, now+31d out, default wired through application.yml) | COMPLIANT (composed) |
| 8 | Forward window | Configured window | CatalogCompositionServiceTest `getCourseDetail_uses_the_configured_window` (7 days), `the_constructor_accepts_the_window_bounds` (1 and 90) | COMPLIANT (unit level, see S2) |
| 9 | Forward window | Started session excluded | CatalogIntegrationTest `get_lists_only_scheduled_sessions_inside_the_window_in_ascending_order` (SCHEDULED at now-1h absent); CatalogCompositionServiceTest window test pins `from = NOW` | COMPLIANT (composed) |
| 10 | Forward window | Session exactly at now is included | CatalogCompositionServiceTest (port receives `from = NOW`) plus PhysicalCourseAvailabilityIntegrationTest bound test (session at `from` returned) | COMPLIANT (composed) |
| 11 | Forward window | from/to parameters are ignored | CatalogIntegrationTest `get_ignores_from_and_to_query_parameters_on_the_detail` | COMPLIANT |
| 12 | Forward window | Out-of-range window rejected | CatalogCompositionServiceTest `the_constructor_rejects_a_window_outside_1_to_90_days` (-1, 0, 91 throw `IllegalArgumentException` naming `window-days`) | COMPLIANT (constructor level, see S2) |
| 13 | Ascending and capped at 100 | Exactly 100 sessions | CatalogCompositionServiceTest `getCourseDetail_keeps_all_sessions_when_there_are_exactly_100` | COMPLIANT |
| 14 | Ascending and capped at 100 | 101 sessions | CatalogCompositionServiceTest `getCourseDetail_truncates_101_sessions_to_the_earliest_100`, `getCourseDetail_keeps_the_port_order_without_re_sorting`; ascending from the port: PhysicalCourseAvailabilityIntegrationTest `sessions_are_listed_in_ascending_scheduled_order` | COMPLIANT (composed) |
| 15 | Public session fields minimal | Only public fields serialized | CatalogControllerTest `the_physical_detail_body_has_only_the_public_keys_and_no_type_property` (port data carries assigned 3 and holds 2); CatalogIntegrationTest `get_exposes_exactly_the_four_public_session_fields_with_a_utc_instant` (3 assignments seeded) | COMPLIANT |
| 16 | Public session fields minimal | scheduledAt format | CatalogIntegrationTest `get_exposes_exactly_the_four_public_session_fields_with_a_utc_instant` (ends in `Z`, parses within 1 s of the seed); CatalogControllerTest `get_returns_the_physical_detail_when_only_physical_resolves_the_id` (`2026-10-03T22:00:00Z`) | COMPLIANT |
| 17 | Sold-out shown, cancelled hidden | Full session shown | CatalogIntegrationTest `get_lists_a_sold_out_session_with_zero_available_spots` (capacity 2, 2 assignments, shows 0 next to an open session); CatalogCompositionServiceTest `getCourseDetail_keeps_a_sold_out_session_listed_with_zero_spots` | COMPLIANT |
| 18 | Sold-out shown, cancelled hidden | Cancelled session hidden | CatalogIntegrationTest `get_lists_only_scheduled_sessions_inside_the_window_in_ascending_order` (CANCELLED at now+3d absent while SCHEDULED ones are listed) | COMPLIANT |
| 19 | Upstream failure degrades the whole detail | Sessions lookup fails | CatalogControllerTest `get_maps_a_sessions_failure_to_a_503_problem_without_course_data` (503, problem+json, `CATALOG_DEGRADED`, no `courseId`, `Retry-After: 30`); CatalogCompositionServiceTest `getCourseDetail_sessions_failure_degrades_the_whole_detail` | COMPLIANT |
| 20 | Upstream failure degrades the whole detail | Physical module unavailable | CatalogCompositionServiceTest `getCourseDetail_physical_lookup_failure_degrades_without_asking_for_sessions` (throws `CatalogUpstreamException`, no sessions read); the same exception type is mapped to 503 + `Retry-After: 30` by the handler, pinned by CatalogControllerTest `get_maps_a_sessions_failure...` and `get_maps_an_unexpected_port_failure_to_a_503_problem...` | COMPLIANT (composed, see S9) |
| 21 | List endpoint unchanged | List has no sessions | CatalogIntegrationTest `list_items_carry_no_sessions_even_when_the_course_has_scheduled_sessions` (item and `physical` block have no `sessions`, real DB); CatalogCompositionServiceTest `listCourses_returns_physical_courses_without_reading_any_session_availability` (`never()` on `listSessions`) | COMPLIANT |

#### Spec physical-session-availability (5 requirements, 12 scenarios)

| # | Requirement | Scenario | Covering test(s) | Result |
|---|---|---|---|---|
| 22 | Range half-open | Session exactly at from is included | PhysicalCourseAvailabilityIntegrationTest `a_session_exactly_at_the_upper_bound_is_excluded_and_one_at_the_lower_bound_included` (asserts `containsExactly(atFrom, inside)`) | COMPLIANT |
| 23 | Range half-open | Session exactly at to is excluded | same test (a session seeded at `to` is absent) | COMPLIANT |
| 24 | Range half-open | Other courses never leak in | CatalogIntegrationTest `get_lists_only_scheduled_sessions_inside_the_window_in_ascending_order` (a second active course with an in-window SCHEDULED session is seeded; the result `containsExactly` only the first course's two sessions; real port and MySQL). It is not a direct port-level test (see S10) | COMPLIANT |
| 25 | Empty or inverted range | Empty range | PhysicalCourseAvailabilityIntegrationTest `excludes_sessions_scheduled_outside_the_requested_range` | COMPLIANT |
| 26 | Empty or inverted range | Zero-width and inverted range | PhysicalCourseAvailabilityIntegrationTest `a_zero_width_or_inverted_range_is_empty_even_with_a_session_on_the_bound` (from == to == T and from after to both empty, with a non-empty control range) | COMPLIANT (was UNTESTED) |
| 27 | Only SCHEDULED, ascending | Cancelled session excluded | PhysicalCourseAvailabilityIntegrationTest `a_cancelled_session_never_appears_in_the_public_availability_read`; CatalogIntegrationTest window test (CANCELLED and SCHEDULED both in range) | COMPLIANT |
| 28 | Only SCHEDULED, ascending | Ascending order | PhysicalCourseAvailabilityIntegrationTest `sessions_are_listed_in_ascending_scheduled_order` (inserted latest, earliest, middle) | COMPLIANT |
| 29 | availableSpots formula | Assignments and active holds reduce availability | PhysicalCourseAvailabilityIntegrationTest `computes_available_spots_as_a_live_count_of_assignments_and_active_holds` (capacity 20, 2 assignments, 1 active hold: 17) | COMPLIANT |
| 30 | availableSpots formula | Expired and converted holds are ignored | expired: same test as row 29 (one expired hold seeded, 17 not 16); converted: PhysicalCourseAvailabilityIntegrationTest `a_converted_hold_is_not_subtracted_from_availability_while_an_active_one_is` (19 with one active and one converted hold) | COMPLIANT (was PARTIAL; composed from two tests, each half has a control) |
| 31 | availableSpots formula | Over-committed session floors at zero | physical PhysicalSessionTest `available_spots_never_goes_negative_even_if_oversold` (domain `Math.max(0, ...)`); PhysicalCourseAvailabilityPortImplTest `lists_sessions_with_availability_mapped_to_the_cross_module_shape` | COMPLIANT (unit level; no MySQL-level oversold seed) |
| 32 | Availability agrees with hold invariant | Reported free spot is holdable | HoldCapacityAdapterIntegrationTest `exactly_one_hold_survives_N_concurrent_claims_on_a_capacity_one_session` (one hold accepted on a capacity-one session); read side in rows 22 to 30 | COMPLIANT (composed, see S4) |
| 33 | Availability agrees with hold invariant | Reported full session rejects a hold | HoldCapacityAdapterIntegrationTest `assignment_first_then_hold_is_refused`, `hold_first_then_assignment_is_refused`; PhysicalPurchaseIntegrationTest `checkout_is_rejected_with_409_when_the_quoted_session_is_visibly_full_and_creates_no_payment` | COMPLIANT (composed, see S4) |

**Compliance summary**: 33/33 scenarios compliant, 0 PARTIAL, 0 UNTESTED, 0 FAILING. Requirements fully compliant: 14/14.

### Correctness (static evidence against the merged code, develop @ 1fe883c)

| Item | Status | Notes |
|---|---|---|
| Virtual-first, then physical `findActiveById`, then `listSessions(now, now+window)` | Implemented | `CatalogCompositionService.getCourseDetail`: virtual `lookup`, physical `lookup` (`orElseThrow(CourseNotFoundException::new)`), then `callPort("physical", () -> physicalPort.listSessions(courseId, from, to))` with `from = clock.instant()`, `to = from.plus(sessionWindow)` |
| Sealed `CatalogCourseDetail` | Implemented | `permits CatalogCourseDetailResponse, CatalogPhysicalCourseDetailResponse`; controller returns `ResponseEntity<CatalogCourseDetail>`; no `@JsonTypeInfo` |
| Cap 100, ascending | Implemented | `MAX_DETAIL_SESSIONS = 100`, `stream().limit(100)` over the port's `ORDER BY s.scheduled_at ASC` |
| Window property | Implemented | `catalog.physical.sessions.window-days`, `@Value("${catalog.physical.sessions.window-days:30}")`, yml `${CATALOG_PHYSICAL_SESSIONS_WINDOW_DAYS:30}`; constructor rejects `< 1` or `> 90` (`MAX_SESSION_WINDOW_DAYS = 90`) with `IllegalArgumentException`, so it fails at bean construction, not on first request |
| `catalogClock` bean, not `clock` | Implemented | `CatalogConfiguration.catalogClock()` returns `Clock.systemUTC()`; the Spring context starts in the integration tests (CatalogIntegrationTest, 12/12 green), so injection by type is unambiguous |
| 503 `CATALOG_DEGRADED` + Retry-After | Implemented | `CatalogExceptionHandler.upstreamDegraded` sends `Retry-After: 30` (constant `"30"`), `application/problem+json`, on every `CatalogUpstreamException`; the sessions failure and the physical lookup failure both raise it, with no partial body |
| Public session fields exactly four | Implemented | `PublicSession(sessionId, scheduledAt, capacity, availableSpots)`; explicit mapping drops `courseId`, `assignedSpots`, `activeCapacityHolds`; no price field; `scheduledAt` is the port's `Instant.toString()` (UTC, `Z`) |
| Half-open only on the public read | Implemented | `findScheduledWithAvailability`: `s.scheduled_at >= :from AND s.scheduled_at < :to`; `findManagedWithAvailability` still `BETWEEN :from AND :to` (admin read stays inclusive); its Javadoc says so |
| `availableSpots` formula and hold clause | Implemented | `PhysicalSession.getAvailableSpots()` is `Math.max(0, capacity - assignedSpots - activeCapacityHolds)`; the holds subquery is `h.converted_at IS NULL AND h.expires_at > :now`, both clauses now pinned by tests |
| Dead `getCourse()` removed | Implemented | `rg 'getCourse\('` over `api` and `bff` returns no match; no `LOG`/SLF4J in the service |
| No new cross-module edge | Implemented | see ArchUnit above |
| Virtual detail and list responses unchanged | Implemented | `CatalogCourseDetailResponse` diff is Javadoc plus `implements`; `listCourses`, `CatalogCourseResponse`, `PhysicalCatalogBlock` are unchanged; the exact virtual key-set test passes |
| `catalog-v1.yaml` `oneOf` vs records | Implemented | `CatalogPhysicalCourseDetail` = `courseId, modality (const PHYSICAL), title, level, physical{professorName, dayOfWeek, startTime, capacity, sessions[maxItems 100]}`, required `courseId, modality, title, level, physical`; `CatalogPublicSession` = `sessionId, scheduledAt (date-time), capacity, availableSpots (minimum 0)`, all four required; `CatalogVirtualCourseDetail` mirrors `CatalogCourseDetailResponse` including nested module, lesson and stats fields. The branches are mutually exclusive through `required` (virtual: `modules`, `stats`, `isPremium`; physical: `modality`, `physical`), no discriminator, as designed. The 503 response declares a `Retry-After` header |
| CI lints `catalog-v1.yaml` | Implemented | `.github/workflows/pr-develop.yml` line 170 is `lint /spec/auth-v1.yaml /spec/billing-v1.yaml /spec/catalog-v1.yaml`; the same command exits 0 here |
| Bruno files | Consistent | `Get Course.bru` gained the no-`sessions` assertion; new `Get Course - Physical.bru` documents `physical.sessions`, window, cap, 404 and 503 + `Retry-After: 30` and matches the code |
| docs/07-CATALOG-API.md, US-PHYSICAL-003 | Consistent | fields, `[ahora, ahora + W)`, 1 to 90, no from/to, cap 100, no internal counts or prices, 503 + `Retry-After: 30`, list unchanged |
| tasks.md hygiene | Clean | no `../`, no backticked absolute path |

### Coherence (design decisions A1-A12)

| Decision | Followed? | Notes |
|---|---|---|
| A1 sealed type | Yes | |
| A2 no discriminator, runtime-class serialization | Yes | pinned by exact key-set tests through the real message converter |
| A3 OpenAPI `oneOf` without discriminator | Yes | |
| A4 physical records, String `scheduledAt`, `sessions` nested in `physical` | Yes | |
| A5 virtual first, all-or-nothing | Yes | virtual failure never reaches physical (`verifyNoInteractions(physicalPort)`) |
| A6 `@Value` + yml env var, 1..90 at construction | Yes | |
| A7 `catalogClock` | Yes | |
| A8 cap 100 | Yes | |
| A9 dead code removed | Yes | |
| A10 only the public query becomes half-open | Yes | |
| A11 billing edge at `periodEndExclusive` | Yes | intended; PhysicalCourseQuoteIntegrationTest 8/8 green |
| A12 three inversions in S2 | Yes | the inverted test names exist and pass |

### Issues Found

**CRITICAL**: None.

**WARNING**: None.

**SUGGESTION (11, all non-blocking; S1 to S8 are the attempt-1 suggestions re-evaluated, S9 to S11 are new)**
- S1. Task 3.4 never went red (redocly on the unchanged `catalog-v1.yaml` already exited 0); a substitute RED was recorded in apply-progress. Documented; no action. Unchanged.
- S2. The configured-window and startup-failure scenarios (rows 8 and 12) are proven at constructor level. No committed test binds a non-default value through `application.yml` or fails a Spring context start with `window-days` 0 or 91; the apply-progress env=45 sensitivity run was manual. A constructor `IllegalArgumentException` during bean creation fails startup, which is standard Spring behavior but is not asserted by a test. Unchanged, not worse.
- S3. The spec wording says "the physical block (...) plus the detail-only `sessions` collection" without fixing where `sessions` lives. Design A4, the records, `catalog-v1.yaml`, Bruno, docs and every test nest it at `physical.sessions`. Consider making the spec say `physical.sessions` at archive time. Unchanged.
- S4. The hold-invariant scenarios (rows 32 and 33) are proven by separate tests of the read side and the write side; no single test pairs `listSessions` availability with the hold decision on one state. Unchanged.
- S5. `Get Course - Physical.bru` loops with `forEach` over `sessions`; against an environment with no upcoming sessions the order and key assertions run zero times (the Bruno request was never run live anyway). Unchanged.
- S6. `build.gradle.kts` still says `":api:app" to "0.90",    // real 97.4%`; the measured value is 95.70% (also in apply-progress). The physical layered-gate comment says real 97.8% and 97.3%; measured now 98.04% and 97.51%. Comment drift only; the thresholds themselves are healthy. Unchanged for app, slightly stale for physical.
- S7. 14 `MethodName` Checkstyle warnings on added lines (snake_case test names), known and tracked as #298. Unchanged category, no other check fires on added lines.
- S8. The exact `now` and `now + W` boundaries (rows 7, 9, 10) are proven by composition (the arguments handed to the port plus the DB half-open test), not by one HTTP test with a controllable clock. Unchanged.
- S9. (new) Row 20 "Physical module unavailable": no single test drives an HTTP 503 from a failing physical `findActiveById`. The service-level test proves the `CatalogUpstreamException`, and two controller tests prove the handler's 503 + `Retry-After: 30` for that exception type, so the chain is covered. A one-line controller test would make it direct.
- S10. (new) Row 24 "Other courses never leak in" is exercised at HTTP level in CatalogIntegrationTest through the real port and MySQL, not by a direct `listSessions` test with two courses in `PhysicalCourseAvailabilityIntegrationTest`. The SQL clause `s.course_id = :courseId` is covered, but the module that owns the read has no dedicated test for it.
- S11. (new, documentation drift) apply-progress.md still shows the PR-creation tasks (1.11, 2.16, 3.13, 4.7) as unchecked and "6/7" for Phase 4, while tasks.md, the authority, marks all 50 as `[x]` and the PRs are merged. The PR-creation boxes were deliberately left to the orchestrator during apply, so this is bookkeeping only; refresh at archive if desired.

### Not verified with a fresh command (reported as unverified, not as passed)

- Bruno `Get Course - Physical.bru` was never run against a live API (known unverified by design).
- Real response bodies were not validated against the `oneOf` with a JSON-schema validator (none installed; I did not install tooling). redocly validated the schema document (exit 0), not instances. Possible mismatch, pre-existing and not introduced by #107: the virtual schema's `description`, `thumbnailUrl` and `category` are `type: string` and not required, while Jackson would serialize a null as `null`.
- The sensitivity proofs of apply batches 3 and 4 (temporary production edits that turn the pins red) were not repeated; I only verified statically that the pins have non-empty controls and that the `from == to` and converted-hold assertions depend on the guarded SQL clauses.
- Engram `sdd/catalog-physical-sessions-availability/decisions` was not re-read; the design and spec files are the authoritative inputs used.
- ADR-0037 still describes parallel resolution (out of scope); catalog cache and rate limit are tracked in #312 and are not requirements here.

### Verdict

PASS

0 CRITICAL, 0 WARNING, 11 SUGGESTION. 33/33 scenarios and 14/14 requirements are covered by tests that passed fresh (894 tests, 0 failures; test_command, build_command and redocly all exit 0; JaCoCo physical 98.04% and 97.51%, app 95.70%; ArchUnit 5/5 and 8/8; no Checkstyle finding on added lines beyond the known MethodName warnings). Ready for archive.

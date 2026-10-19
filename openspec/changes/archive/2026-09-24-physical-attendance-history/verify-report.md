```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:c477c956028bcf55fe79e6a5c0e1e8bf8a238bf506c2fc39c183a908f32afb6a
verdict: pass
blockers: 0
critical_findings: 0
requirements: 12/12
scenarios: 21/21
test_command: ./gradlew test --rerun-tasks
test_exit_code: 0
test_output_hash: sha256:3344fd946787b1c57bc223c28dcddc1dabe8b552e604f5c3ec11781e4b3352ec
build_command: ./gradlew check
build_exit_code: 0
build_output_hash: sha256:c84c4f37ab8c85afcda260a5ea9f236092955118783e6597488f5c290e2a2556
```

## Verification Report

**Change**: physical-attendance-history (#39, US-PHYSICAL-002)
**Version**: N/A (openspec change, no semver)
**Mode**: Strict TDD

**Delivery context**: re-verification after remediation. Prior `sdd-verify` run (this file's previous content) returned FAIL with 2 CRITICAL findings — spec scenarios R2 ("A mid-month MONTHLY purchase splits across two monthly views") and R7 ("A pre-quote cancellation never produced an assignment") had zero runtime test coverage. PR #261 (commit `5038d00`, merged to `develop`) added exactly two fixture-based integration tests to `PhysicalAttendanceHistoryIntegrationTest` covering both scenarios. No production code changed in that PR — only test coverage on top of the already-merged P1 (#259, commit `4e1bca0`) and P2 (#260, commit `42345a7`) implementation. Current `develop` HEAD `5038d00` was verified directly (`git status` clean, no local artifact divergence).

**Requirement/scenario count**: confirmed against `specs/physical-attendance-history/spec.md` (`rg -c "^### Requirement:"` / `rg -c "^#### Scenario:"`) — **12 requirements / 21 scenarios**. All figures below use this verified count; the admission validator target is `--requirements 12 --scenarios 21`.

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 48 (P1: 1.1–1.28, P2: 2.1–2.17, Remediation: R.1–R.3) |
| Tasks complete | 48 (`[x]` on every line in tasks.md) |
| Tasks incomplete | 0 |

No `apply-progress` artifact exists in Engram or on disk for this change (searched both, no results). tasks.md carries inline RED/GREEN/Verify annotations per task, used as the TDD evidence source, consistent with the original verification.

### Build & Tests Execution
**Build**: ✅ Passed — `./gradlew check` (checkstyle + full test suite + layered JaCoCo coverage verification across the monorepo), exit 0, `BUILD SUCCESSFUL in 12s` (24 executed, 87 up-to-date — reusing the fresh `test` task outputs from the run immediately before it).

**Tests**: ✅ All passed — `./gradlew test --rerun-tasks` (forces full re-execution, bypasses Gradle's up-to-date cache), exit 0, `BUILD SUCCESSFUL in 15m 25s`, "65 actionable tasks: 65 executed". Module-level detail confirmed from the fresh JUnit XML results of this exact run:
- `:api:physical:test` — 293 tests, 0 failures, 0 errors, 0 skipped (unchanged from the prior verification — no production code touched by the remediation).
- `:api:auth:test` — 498 tests, 0 failures, 0 errors, 0 skipped.
- `:api:app:test` — 345 tests, 0 failures, 0 errors, 0 skipped, including the full `PhysicalAttendanceHistoryIntegrationTest` class: **14/14 green** (12 prior + the 2 new remediation tests `a_mid_month_monthly_purchase_splits_assignments_across_two_monthly_views` and `a_pre_quote_cancellation_never_produced_an_assignment`), confirmed both individually (`:api:app:test --tests "*PhysicalAttendanceHistoryIntegrationTest*" --rerun-tasks`, exit 0, `BUILD SUCCESSFUL in 53s`) and as part of the full monorepo run.
- `:api:physical:ArchitectureTest` — 7/7 green (no forbidden import introduced).

No test failure anywhere in the change's own suite or the surrounding monorepo.

**Coverage**: layered gate (`jacocoDomainApplicationCoverageVerification` 95%, `jacocoInfrastructureCoverageVerification` 90% for `:api:physical`) → ✅ Above threshold, both gates pass inside `./gradlew check`. Coverage is unchanged from the prior verification since no production code was modified by the remediation (only a test file). Per-file spot check remains valid:

| File | Line coverage |
|---|---|
| `MonthlyAttendanceAssembler` | 100% (19/19), branch 100% |
| `GetPhysicalAttendanceHistoryUseCaseImpl` | 100% (15/15), branch 100% |
| `AttendanceViewer` + variants | 100% |
| `PhysicalAttendanceController` | 100% (8/8) |
| `PhysicalAttendanceAdminController` | 100% (12/12) |
| `PhysicalCapacityAssignmentRepositoryAdapter` | 92% (11/12) |

### Spec Compliance Matrix
21/21 scenarios now directly proven by a passing runtime test. The two previously ❌ UNTESTED scenarios are now ✅ COMPLIANT.

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Self attendance for a calendar month | Student reads a five-session month | `PhysicalAttendanceHistoryIntegrationTest.a_five_session_month_with_one_absence_round_trips_through_the_full_stack` | ✅ COMPLIANT |
| Self attendance for a calendar month | Student reads a four-session month | `PhysicalAttendanceHistoryIntegrationTest.a_four_session_month_all_attended_round_trips_through_the_full_stack` | ✅ COMPLIANT |
| Self attendance for a calendar month | Anonymous request is rejected | `SecurityConfigTest.an_unauthenticated_get_of_the_self_attendance_history_route_is_rejected`, `PhysicalAttendanceHistoryIntegrationTest.an_anonymous_request_is_rejected_with_401` | ✅ COMPLIANT |
| Self attendance for a calendar month | A student cannot obtain another student's data via the self endpoint | `PhysicalAttendanceControllerTest.a_student_cannot_obtain_another_students_data_via_any_parameter` | ✅ COMPLIANT |
| Denominator counts assignments, not purchase coverage windows | Mid-month MONTHLY purchase splits across two monthly views | `PhysicalAttendanceHistoryIntegrationTest.a_mid_month_monthly_purchase_splits_assignments_across_two_monthly_views` — drives a real checkout + webhook confirmation through the full Billing stack, seeds a 3-session MONTHLY quote split 2 September / 1 October, asserts each month's `scheduledSessionCount` reflects only that month's assigned sessions and that neither month equals the purchase's own 3-session coverage-window count | ✅ COMPLIANT |
| includeAbsent controls the session list, never the aggregate | includeAbsent=false omits unattended rows but keeps aggregates intact | `MonthlyAttendanceAssemblerTest.include_absent_false_hides_absent_rows_but_keeps_aggregates_identical`, `PhysicalAttendanceHistoryIntegrationTest.include_absent_toggles_only_the_list_never_the_aggregates_against_real_rows` | ✅ COMPLIANT |
| includeAbsent controls the session list, never the aggregate | includeAbsent=true lists the absent row | `MonthlyAttendanceAssemblerTest.absent_row_maps_to_null_recorded_at_and_attended_row_carries_it`, same integration test | ✅ COMPLIANT |
| Status is derived live, never stored | An attended session reports recordedAt | `MonthlyAttendanceAssemblerTest.absent_row_maps_to_null_recorded_at_and_attended_row_carries_it` | ✅ COMPLIANT |
| Status is derived live, never stored | An absent session has a null recordedAt | same test, `PhysicalCapacityAssignmentRepositoryAdapterTest` (LEFT JOIN null survival) | ✅ COMPLIANT |
| Empty month returns a zeroed 200, never 404 | Student with no assignments in the month | `MonthlyAttendanceAssemblerTest.zero_denominator_yields_zero_rate`, `PhysicalAttendanceHistoryIntegrationTest.a_month_with_zero_assignments_returns_200_zeroed_never_404` | ✅ COMPLIANT |
| Attendance rate rounding | Rate rounds HALF_UP at scale 2 | `MonthlyAttendanceAssemblerTest.five_session_month_four_attended_one_absent_yields_eighty_percent_rate`, `.two_of_three_attended_rounds_half_up_to_66_67_not_67_00` | ✅ COMPLIANT |
| Attendance rate rounding | Rate is zero with no denominator | `MonthlyAttendanceAssemblerTest.zero_denominator_yields_zero_rate` | ✅ COMPLIANT |
| Sessions cancelled before quoting are naturally absent | A pre-quote cancellation never produced an assignment | `PhysicalAttendanceHistoryIntegrationTest.a_pre_quote_cancellation_never_produced_an_assignment` — seeds a session with status `CANCELLED` and no assignment row ever created, alongside one real attended session; asserts the response contains only the real session (`scheduledSessionCount: 1`, `sessions` has exactly the attended session) | ✅ COMPLIANT |
| Elevated read for ADMIN and INSTRUCTOR | Admin reads any student's month unrestricted | `PhysicalAttendanceHistoryIntegrationTest.admin_reads_any_students_month_unrestricted_across_courses` | ✅ COMPLIANT |
| Elevated read for ADMIN and INSTRUCTOR | A STUDENT caller cannot reach the elevated endpoint | `SecurityConfigTest.an_authenticated_student_get_of_the_elevated_attendance_history_route_is_forbidden`, `PhysicalAttendanceHistoryIntegrationTest.a_student_caller_cannot_reach_the_elevated_endpoint` | ✅ COMPLIANT |
| Elevated read for ADMIN and INSTRUCTOR | Anonymous request to the elevated endpoint is rejected | `SecurityConfigTest.an_unauthenticated_get_of_the_elevated_attendance_history_route_is_rejected`, `PhysicalAttendanceHistoryIntegrationTest.an_anonymous_request_to_the_elevated_endpoint_is_rejected_with_401` | ✅ COMPLIANT |
| Instructor elevated read is scoped to owned courses | Instructor sees only their own courses' sessions and aggregates | `GetPhysicalAttendanceHistoryUseCaseImplTest.instructor_own_courses_viewer_dispatches_to_the_scoped_method_with_the_callers_id`, `PhysicalCapacityAssignmentRepositoryAdapterTest` (course-filter fixture), `PhysicalAttendanceHistoryIntegrationTest.instructor_sees_only_their_own_courses_sessions_and_aggregates` | ✅ COMPLIANT |
| Instructor elevated read is scoped to owned courses | Instructor sees zero rows for a student in a course they don't teach | `PhysicalCapacityAssignmentRepositoryAdapterTest`, `GetPhysicalAttendanceHistoryUseCaseImplTest.an_instructor_viewer_with_zero_overlapping_courses_yields_the_same_empty_month` | ✅ COMPLIANT |
| Elevated read does not leak student existence to an instructor | Non-existent overlap and genuinely empty month look identical | `GetPhysicalAttendanceHistoryUseCaseImplTest.an_instructor_viewer_with_zero_overlapping_courses_yields_the_same_empty_month`, `PhysicalAttendanceHistoryIntegrationTest.non_overlapping_student_and_genuinely_empty_student_are_indistinguishable` | ✅ COMPLIANT |
| Month boundaries resolve in the configured local zone | A late-evening local session lands in the correct local month | `GetPhysicalAttendanceHistoryUseCaseImplTest.a_late_evening_session_lands_in_september_under_buenos_aires_but_october_under_utc` | ✅ COMPLIANT |
| Bounded, untruncated response for one month | A full month returns without truncation | `PhysicalAttendanceHistoryIntegrationTest.a_full_month_returns_every_assignment_in_a_single_response_with_no_truncation` (self), `.a_full_month_returns_every_assignment_untruncated_via_the_elevated_endpoint` (elevated) | ✅ COMPLIANT |

**Compliance summary**: 21/21 scenarios COMPLIANT, 0/21 UNTESTED, 0/21 FAILING.

### Correctness (Static Evidence)
| Requirement | Status | Notes |
|---|---|---|
| R1 (self endpoint) | ✅ Implemented | `GetPhysicalAttendanceHistoryUseCaseImpl` + `MonthlyAttendanceAssembler` match design C1/C2/C6 verbatim |
| R2 (denominator) | ✅ Implemented and proven at runtime | `a_mid_month_monthly_purchase_splits_assignments_across_two_monthly_views` drives a real MONTHLY purchase through checkout + webhook confirmation and asserts the per-calendar-month split |
| R3 (includeAbsent) | ✅ Implemented | Applied only in the assembler's session-list loop, never in the aggregate math |
| R4 (status derived live) | ✅ Implemented | `AttendanceHistoryRow.attended()` = `recordedAt != null`, no stored status column |
| R5 (empty month 200) | ✅ Implemented | Assembler's zero-denominator branch always returns `0.00`, never throws/404s |
| R6 (rate rounding) | ✅ Implemented | Multiply-before-divide confirmed in `MonthlyAttendanceAssembler.rateOf` (matches C6 exactly) |
| R7 (pre-quote cancellation) | ✅ Implemented and proven at runtime | `a_pre_quote_cancellation_never_produced_an_assignment` seeds a `CANCELLED` session with no assignment row and asserts it is absent from the response |
| R8 (elevated ADMIN/INSTRUCTOR) | ✅ Implemented | `AttendanceViewer.elevated(...)` + two `SecurityConfig` matchers, verified in order |
| R9 (instructor scoping) | ✅ Implemented | Course filter applied inside the query (`c.professorId = :professorId`), never post-filtered |
| R10 (anti-enumeration) | ✅ Implemented | No `UserQueryPort`, no existence probe anywhere in `GetPhysicalAttendanceHistoryUseCaseImpl` |
| R11 (local zone) | ✅ Implemented | `atStartOfDay(zoneId)` per-date conversion, configured via `@Value` on the bean, matches C3 |
| R12 (untruncated response) | ✅ Implemented | No pagination parameter on either controller |

### Coherence (Design)
| Decision | Followed? | Notes |
|---|---|---|
| C1 — two port methods, one JPQL, no nullable sentinel crossing the boundary | ✅ Yes | Unchanged since prior verification; `AttendanceRepository` untouched |
| C2 — sealed `AttendanceViewer` (`Self`/`Admin`/`InstructorOwnCourses`) | ✅ Yes | Verbatim match, exhaustive `switch` in the use case |
| C3 — configured `ZoneId` via `@Value` on the `@Bean` method parameter | ✅ Yes | `PhysicalConfiguration.getPhysicalAttendanceHistoryUseCase` matches exactly |
| C4 — structural anti-enumeration, no `AttendanceNotFoundException` | ✅ Yes | No existence port import anywhere in the use case; now runtime-proven for both R9 and R10's specific scenarios |
| C5 — single `V21` migration (student_id index only) | ⚠️ Deviation, documented | Renumbered to **V22** (see prior verification); unchanged by this remediation, still pinned by `PhysicalAttendanceHistoryMigrationIntegrationTest` |
| C6 — shared web DTO pair, multiply-before-divide rate | ✅ Yes | `AttendanceHistoryResponse`/`AttendanceSessionResponse` used by both controllers; rate formula matches exactly |
| C7 — two explicit `SecurityConfig` matchers, elevated matcher before line 287's generic `/admin/**` gate | ✅ Yes | Unchanged since prior verification; order pinned by parameterized `SecurityConfigTest` cases |

### TDD Compliance
| Check | Result | Details |
|---|---|---|
| TDD Evidence reported | ✅ | tasks.md carries inline RED/GREEN/Verify labels per task, including the new R.1–R.3 remediation section |
| All tasks have tests | ✅ | 48/48 tasks checked `[x]`; every code-producing task pairs with a named test class |
| RED confirmed (tests exist) | ✅ | All named test files exist on disk, including the two new test methods, independently located and read during this verification |
| GREEN confirmed (tests pass) | ✅ | Fresh `./gradlew test --rerun-tasks` exit 0 across the whole monorepo, plus a dedicated fresh run of `:api:app:test --tests "*PhysicalAttendanceHistoryIntegrationTest*" --rerun-tasks` (14/14 green) |
| Triangulation adequate | ✅ | R2 and R7 now each have one dedicated fixture-based runtime test proving their specific business scenario, closing the triangulation gap the prior verification flagged |
| Safety Net for modified files | ✅ | No production file was modified by this remediation — only the test file — so no regression risk was introduced; the full monorepo suite (`:api:physical:test` 293/293, `:api:auth:test` 498/498, `:api:app:test` 345/345) remained green throughout |

**TDD Compliance**: 6/6 process checks passed.

### Assertion Quality
Read both new test methods (`a_mid_month_monthly_purchase_splits_assignments_across_two_monthly_views`, `a_pre_quote_cancellation_never_produced_an_assignment`) in full. Both exercise real production code paths end-to-end:
- The R2 test drives a real HTTP checkout request, a real webhook confirmation callback, and a real outbox-worker processing cycle through the full Billing stack, then asserts concrete integer counts (`scheduledSessionCount` = 2 for September, 1 for October) and a `isNotEqualTo` check against the purchase's own 3-session coverage-window count — a genuine differentiator, not a tautology.
- The R7 test seeds a `CANCELLED` session with an explicit comment establishing it never produced an assignment row, then asserts the response's `sessions` list has size 1 and its single element's `sessionId` matches only the real, attended session — not merely a count check, but an identity check that rules out silent inclusion of the cancelled session.

No tautologies, ghost loops, or assertion-free tests found in either new test or the pre-existing suite re-read during this pass.

**Assertion quality**: ✅ All assertions verify real behavior, 0 CRITICAL, 0 WARNING

### Quality Metrics
**Checkstyle**: ✅ No new violations — `./gradlew check` (includes `checkstyleMain`/`checkstyleTest` for both `:api:physical` and `:api:auth`) passes, `BUILD SUCCESSFUL`.
**Type Checker**: ➖ N/A (Java, compiler is the type checker; full build compiles cleanly)

### Issues Found

**CRITICAL**: None

**WARNING**: None

**SUGGESTION**: None

### Verdict
**PASS** — All 48 tasks are complete, the full monorepo `./gradlew test --rerun-tasks` (65/65 tasks, exit 0) and `./gradlew check` (checkstyle + layered JaCoCo coverage gates, exit 0) are both green with zero regressions, and all 21/21 spec scenarios are now directly proven by a passing runtime test — closing both CRITICAL gaps (R2, R7) the prior verification identified. No production code was changed by this remediation; both fixes were coverage additions confirmed RED-then-GREEN per tasks.md R.1/R.2. This change is ready for `sdd-archive`.

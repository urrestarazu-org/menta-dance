```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:23e097d30858df29d565dd8e459e595a73a5c64b3ec64ea421aadfdd07d1839a
verdict: fail
blockers: 2
critical_findings: 2
requirements: 12/12
scenarios: 19/21
test_command: ./gradlew test --rerun-tasks
test_exit_code: 0
test_output_hash: sha256:0bb8b7ece89b6aa13ce44a3b799cfe897ac2a3e1de02687883500c8321330f04
build_command: ./gradlew check
build_exit_code: 0
build_output_hash: sha256:d4b9f95ecd6ec042e9d850bb4cd773e84bacd7dbf63c91047b358d0ebfe69ae5
```

## Verification Report

**Change**: physical-attendance-history (#39, US-PHYSICAL-002)
**Version**: N/A (openspec change, no semver)
**Mode**: Strict TDD

**Delivery context**: shipped as 2 chained PRs, stacked-to-main, both merged to `develop`: PR #259 (P1, commit `4e1bca0`, self endpoint) and PR #260 (P2, commit `42345a7`, elevated endpoint). Current `develop` HEAD `42345a7` was verified directly (`git status` clean, no local artifact divergence).

**Requirement/scenario count correction**: the verification launch brief cited 13 requirements / 24 scenarios. A fresh count against `specs/physical-attendance-history/spec.md` (`rg -c "^### Requirement:"` / `rg -c "^#### Scenario:"`) gives **12 requirements / 21 scenarios**. All figures below use the verified count, and the admission validator was run with `--requirements 12 --scenarios 21`.

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 45 (P1: 1.1–1.28, P2: 2.1–2.17) |
| Tasks complete | 45 (`[x]` on every line in tasks.md) |
| Tasks incomplete | 0 |

No `apply-progress` artifact exists in Engram or on disk for this change (searched both, no results). tasks.md itself carries inline RED/GREEN/Verify annotations per task, which is used below as the TDD evidence source in its place — consistent with this project's convention of embedding TDD evidence directly in tasks.md rather than a separate progress artifact.

### Build & Tests Execution
**Build**: ✅ Passed — `./gradlew check` (checkstyle + full test suite + layered JaCoCo coverage verification across the monorepo), exit 0, `BUILD SUCCESSFUL in 8s` (24 executed, 87 up-to-date immediately after the full `test` run below).

**Tests**: ✅ All passed — `./gradlew test --rerun-tasks` (forces full re-execution, bypasses Gradle's up-to-date cache), exit 0, `BUILD SUCCESSFUL in 15m 24s`, "65 actionable tasks: 65 executed". Independently confirmed module-level detail with separate fresh `--rerun-tasks` runs:
- `:api:physical:test` — 293 tests, 0 failures, 0 errors, 0 skipped.
- `:api:auth:test` — 498 tests, 0 failures, 0 errors, 0 skipped, including 23/23 `SecurityConfigTest` cases.
- `:api:app:test --tests "*PhysicalAttendanceHistory*"` — `PhysicalAttendanceHistoryIntegrationTest` 12/12 green, `PhysicalAttendanceHistoryMigrationIntegrationTest` 2/2 green, against real Testcontainers MySQL 8.
- `:api:physical:ArchitectureTest` — 7/7 green (no forbidden `org.springframework`/`jakarta.persistence` import under `domain`/`application`, no `com.menta.billing.*` import).

No test failure anywhere in the change's own suite or the surrounding monorepo. The FAIL verdict below is driven entirely by two spec scenarios with no covering runtime test, not by any executed test failing.

**Coverage**: layered gate (`jacocoDomainApplicationCoverageVerification` 95%, `jacocoInfrastructureCoverageVerification` 90% for `:api:physical`) → ✅ Above threshold, both gates pass inside `./gradlew check`. Per-file spot check of the new production code (`jacocoTestReport.xml`):

| File | Line coverage |
|---|---|
| `MonthlyAttendanceAssembler` | 100% (19/19), branch 100% |
| `GetPhysicalAttendanceHistoryUseCaseImpl` | 100% (15/15), branch 100% |
| `AttendanceViewer` + variants | 100% |
| `PhysicalAttendanceController` | 100% (8/8) |
| `PhysicalAttendanceAdminController` | 100% (12/12) |
| `PhysicalCapacityAssignmentRepositoryAdapter` | 92% (11/12) |

### Spec Compliance Matrix
19/21 scenarios directly proven by a passing runtime test. 2 scenarios have no dedicated runtime test at all — only structural/architectural evidence (source inspection, `ArchitectureTest`'s "no billing import" rule) — and are therefore ❌ UNTESTED per this project's compliance bar (a spec scenario is compliant only when a covering test passed at runtime).

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Self attendance for a calendar month | Student reads a five-session month | `PhysicalAttendanceHistoryIntegrationTest.a_five_session_month_with_one_absence_round_trips_through_the_full_stack` | ✅ COMPLIANT |
| Self attendance for a calendar month | Student reads a four-session month | `PhysicalAttendanceHistoryIntegrationTest.a_four_session_month_all_attended_round_trips_through_the_full_stack` | ✅ COMPLIANT |
| Self attendance for a calendar month | Anonymous request is rejected | `SecurityConfigTest.an_unauthenticated_get_of_the_self_attendance_history_route_is_rejected`, `PhysicalAttendanceHistoryIntegrationTest.an_anonymous_request_is_rejected_with_401` | ✅ COMPLIANT |
| Self attendance for a calendar month | A student cannot obtain another student's data via the self endpoint | `PhysicalAttendanceControllerTest.a_student_cannot_obtain_another_students_data_via_any_parameter` | ✅ COMPLIANT |
| Denominator counts assignments, not purchase coverage windows | Mid-month MONTHLY purchase splits across two monthly views | (none — no fixture-based test seeds a Billing `MONTHLY` purchase and asserts the per-month split) | ❌ UNTESTED |
| includeAbsent controls the session list, never the aggregate | includeAbsent=false omits unattended rows but keeps aggregates intact | `MonthlyAttendanceAssemblerTest.include_absent_false_hides_absent_rows_but_keeps_aggregates_identical`, `PhysicalAttendanceHistoryIntegrationTest.include_absent_toggles_only_the_list_never_the_aggregates_against_real_rows` | ✅ COMPLIANT |
| includeAbsent controls the session list, never the aggregate | includeAbsent=true lists the absent row | `MonthlyAttendanceAssemblerTest.absent_row_maps_to_null_recorded_at_and_attended_row_carries_it`, same integration test | ✅ COMPLIANT |
| Status is derived live, never stored | An attended session reports recordedAt | `MonthlyAttendanceAssemblerTest.absent_row_maps_to_null_recorded_at_and_attended_row_carries_it` | ✅ COMPLIANT |
| Status is derived live, never stored | An absent session has a null recordedAt | same test, `PhysicalCapacityAssignmentRepositoryAdapterTest` (LEFT JOIN null survival) | ✅ COMPLIANT |
| Empty month returns a zeroed 200, never 404 | Student with no assignments in the month | `MonthlyAttendanceAssemblerTest.zero_denominator_yields_zero_rate`, `PhysicalAttendanceHistoryIntegrationTest.a_month_with_zero_assignments_returns_200_zeroed_never_404` | ✅ COMPLIANT |
| Attendance rate rounding | Rate rounds HALF_UP at scale 2 | `MonthlyAttendanceAssemblerTest.five_session_month_four_attended_one_absent_yields_eighty_percent_rate`, `.two_of_three_attended_rounds_half_up_to_66_67_not_67_00` | ✅ COMPLIANT |
| Attendance rate rounding | Rate is zero with no denominator | `MonthlyAttendanceAssemblerTest.zero_denominator_yields_zero_rate` | ✅ COMPLIANT |
| Sessions cancelled before quoting are naturally absent | A pre-quote cancellation never produced an assignment | (none — no runtime fixture recreates a pre-quote cancellation; the `@Query` in `PhysicalCapacityAssignmentJpaRepository` has zero `SessionStatus` reference, confirmed by source inspection only) | ❌ UNTESTED |
| Elevated read for ADMIN and INSTRUCTOR | Admin reads any student's month unrestricted | `PhysicalAttendanceHistoryIntegrationTest.admin_reads_any_students_month_unrestricted_across_courses` | ✅ COMPLIANT |
| Elevated read for ADMIN and INSTRUCTOR | A STUDENT caller cannot reach the elevated endpoint | `SecurityConfigTest.an_authenticated_student_get_of_the_elevated_attendance_history_route_is_forbidden`, `PhysicalAttendanceHistoryIntegrationTest.a_student_caller_cannot_reach_the_elevated_endpoint` | ✅ COMPLIANT |
| Elevated read for ADMIN and INSTRUCTOR | Anonymous request to the elevated endpoint is rejected | `SecurityConfigTest.an_unauthenticated_get_of_the_elevated_attendance_history_route_is_rejected`, `PhysicalAttendanceHistoryIntegrationTest.an_anonymous_request_to_the_elevated_endpoint_is_rejected_with_401` | ✅ COMPLIANT |
| Instructor elevated read is scoped to owned courses | Instructor sees only their own courses' sessions and aggregates | `GetPhysicalAttendanceHistoryUseCaseImplTest.instructor_own_courses_viewer_dispatches_to_the_scoped_method_with_the_callers_id`, `PhysicalCapacityAssignmentRepositoryAdapterTest` (2.1 course-filter fixture), `PhysicalAttendanceHistoryIntegrationTest.instructor_sees_only_their_own_courses_sessions_and_aggregates` | ✅ COMPLIANT |
| Instructor elevated read is scoped to owned courses | Instructor sees zero rows for a student in a course they don't teach | `PhysicalCapacityAssignmentRepositoryAdapterTest` (2.1), `GetPhysicalAttendanceHistoryUseCaseImplTest.an_instructor_viewer_with_zero_overlapping_courses_yields_the_same_empty_month` | ✅ COMPLIANT |
| Elevated read does not leak student existence to an instructor | Non-existent overlap and genuinely empty month look identical | `GetPhysicalAttendanceHistoryUseCaseImplTest.an_instructor_viewer_with_zero_overlapping_courses_yields_the_same_empty_month`, `PhysicalAttendanceHistoryIntegrationTest.non_overlapping_student_and_genuinely_empty_student_are_indistinguishable` | ✅ COMPLIANT |
| Month boundaries resolve in the configured local zone | A late-evening local session lands in the correct local month | `GetPhysicalAttendanceHistoryUseCaseImplTest.a_late_evening_session_lands_in_september_under_buenos_aires_but_october_under_utc` | ✅ COMPLIANT |
| Bounded, untruncated response for one month | A full month returns without truncation | `PhysicalAttendanceHistoryIntegrationTest.a_full_month_returns_every_assignment_in_a_single_response_with_no_truncation` (self), `.a_full_month_returns_every_assignment_untruncated_via_the_elevated_endpoint` (elevated) | ✅ COMPLIANT |

**Compliance summary**: 19/21 scenarios COMPLIANT, 2/21 UNTESTED, 0/21 FAILING.

### Correctness (Static Evidence)
| Requirement | Status | Notes |
|---|---|---|
| R1 (self endpoint) | ✅ Implemented | `GetPhysicalAttendanceHistoryUseCaseImpl` + `MonthlyAttendanceAssembler` match design C1/C2/C6 verbatim (source-read) |
| R2 (denominator) | ⚠️ Implemented, unproven at runtime | No Billing import anywhere in `physical`; query only joins assignments/sessions/courses/attendances — see CRITICAL below |
| R3 (includeAbsent) | ✅ Implemented | Applied only in the assembler's session-list loop, never in the aggregate math (source-read) |
| R4 (status derived live) | ✅ Implemented | `AttendanceHistoryRow.attended()` = `recordedAt != null`, no stored status column |
| R5 (empty month 200) | ✅ Implemented | Assembler's zero-denominator branch always returns `0.00`, never throws/404s |
| R6 (rate rounding) | ✅ Implemented | Multiply-before-divide confirmed in `MonthlyAttendanceAssembler.rateOf` (matches C6 exactly) |
| R7 (pre-quote cancellation) | ⚠️ Implemented, unproven at runtime | No `SessionStatus` filter present anywhere in the JPQL — see CRITICAL below |
| R8 (elevated ADMIN/INSTRUCTOR) | ✅ Implemented | `AttendanceViewer.elevated(...)` + two `SecurityConfig` matchers, verified in order |
| R9 (instructor scoping) | ✅ Implemented | Course filter applied inside the query (`c.professorId = :professorId`), never post-filtered |
| R10 (anti-enumeration) | ✅ Implemented | No `UserQueryPort`, no existence probe anywhere in `GetPhysicalAttendanceHistoryUseCaseImpl` |
| R11 (local zone) | ✅ Implemented | `atStartOfDay(zoneId)` per-date conversion, configured via `@Value` on the bean, matches C3 |
| R12 (untruncated response) | ✅ Implemented | No pagination parameter on either controller |

### Coherence (Design)
| Decision | Followed? | Notes |
|---|---|---|
| C1 — two port methods, one JPQL, no nullable sentinel crossing the boundary | ✅ Yes | `PhysicalCapacityAssignmentRepository` has exactly the two methods design specifies; `AttendanceRepository` untouched since its last commit (QR check-in feature, pre-dates this change) |
| C2 — sealed `AttendanceViewer` (`Self`/`Admin`/`InstructorOwnCourses`) | ✅ Yes | Verbatim match, exhaustive `switch` in the use case (compile-time safety confirmed) |
| C3 — configured `ZoneId` via `@Value` on the `@Bean` method parameter | ✅ Yes | `PhysicalConfiguration.getPhysicalAttendanceHistoryUseCase` matches exactly |
| C4 — structural anti-enumeration, no `AttendanceNotFoundException` | ✅ Yes | No existence port import anywhere in the use case; equality tests + full-body integration assertion both present and runtime-proven |
| C5 — single `V21` migration (student_id index only) | ⚠️ Deviation, documented | Task 1.2 renumbered to **V22** after discovering `db/rollback` already owns `V21` for issue #41; a dedicated `PhysicalAttendanceHistoryMigrationIntegrationTest` (2/2 green) pins the collision-avoidance. Same class of numbering correction the precedent `billing-bank-transfer-subscription` change also made (`V20_1_5` vs. design's `V21` sketch) — documented, not silent |
| C6 — shared web DTO pair, multiply-before-divide rate | ✅ Yes | `AttendanceHistoryResponse`/`AttendanceSessionResponse` used by both controllers; rate formula matches exactly |
| C7 — two explicit `SecurityConfig` matchers, elevated matcher before line 287's generic `/admin/**` gate | ✅ Yes | Verified by direct source read: `/me` matcher, then `/admin/physical/attendance/*` (ADMIN+INSTRUCTOR), then the pre-existing `/admin/physical/courses/**`/`/admin/physical/sessions/**`, then the generic `/admin/**` gate — order is correct and pinned by parameterized `SecurityConfigTest` cases |

### TDD Compliance
| Check | Result | Details |
|---|---|---|
| TDD Evidence reported | ✅ | tasks.md carries inline RED/GREEN/Verify labels per task (no separate `apply-progress` artifact exists for this change) |
| All tasks have tests | ✅ | 45/45 tasks checked `[x]`; every code-producing task pairs with a named test class |
| RED confirmed (tests exist) | ✅ | All named test files exist on disk and were independently located and read during this verification |
| GREEN confirmed (tests pass) | ✅ | Fresh `./gradlew test --rerun-tasks` exit 0 across the whole monorepo, including every test named in tasks.md |
| Triangulation adequate | ⚠️ | Assembler has 6 distinct test methods (not 7 as tasks.md 1.6 states — see WARNING below) covering 4-session/5-session/2-of-3/zero-denominator/includeAbsent/recordedAt-mapping; zone bounds triangulated with a same-instant two-zone pair (1.8); however R2 and R7's specific business scenarios have zero triangulation (zero tests) |
| Safety Net for modified files | ✅ | `SecurityConfig.java`/`PhysicalConfiguration.java`/`PhysicalCapacityAssignmentRepositoryAdapter.java` (all modified, not new) — pre-existing tests for those classes remained green throughout (`:api:auth:test` 498/498, `:api:physical:test` 293/293) |

**TDD Compliance**: 5/6 process checks passed.

### Assertion Quality
No tautologies, ghost loops, or assertion-free tests found while reading `MonthlyAttendanceAssemblerTest`, `GetPhysicalAttendanceHistoryUseCaseImplTest`, `PhysicalAttendanceControllerTest`, `PhysicalAttendanceAdminControllerTest`, `PhysicalCapacityAssignmentRepositoryAdapterTest`, `PhysicalAttendanceHistoryIntegrationTest`, and the extended `SecurityConfigTest` cases. All assert concrete values (specific counts, specific `BigDecimal` rates, specific HTTP status codes, specific response bodies) against real production code paths, several via real HTTP + real MySQL through Testcontainers.

**Assertion quality**: ✅ All assertions verify real behavior, 0 CRITICAL, 0 WARNING

### Quality Metrics
**Checkstyle**: ✅ No new violations — `./gradlew check` (includes `checkstyleMain`/`checkstyleTest` for both `:api:physical` and `:api:auth`) passes, `BUILD SUCCESSFUL`.
**Type Checker**: ➖ N/A (Java, compiler is the type checker; full build compiles cleanly)

### Issues Found

**CRITICAL**:
1. **R2 scenario UNTESTED** — "A mid-month MONTHLY purchase splits across two monthly views" has no runtime test. The implementation is structurally correct (the query is driven only by `physical_capacity_assignments` rows, and `ArchitectureTest` confirms zero `com.menta.billing.*` coupling anywhere in `physical`), but per this project's compliance bar a spec scenario is compliant only when a covering test passed at runtime, and none exists. A regression that accidentally reintroduced a purchase-coverage-window concept into the denominator would not be caught by any test in this change.
2. **R7 scenario UNTESTED** — "A pre-quote cancellation never produced an assignment" has no runtime test. The implementation is structurally correct (no `SessionStatus` predicate exists anywhere in `PhysicalCapacityAssignmentJpaRepository`'s query), but the specific business scenario is never exercised end-to-end. A regression that added a `SessionStatus` filter to "optimize" the query would not be caught by any test in this change.

**WARNING**:
1. tasks.md task 1.6 claims `MonthlyAttendanceAssemblerTest` has "7 tests, all real assertions" — the file has 6 test methods (`rg -c "void "` confirms 6). Cosmetic documentation inaccuracy; does not affect coverage or correctness (assembler is at 100% line+branch coverage regardless).

**SUGGESTION**: None

### Verdict
**FAIL** — Both spec CRITICAL findings are scenario-coverage gaps, not implementation defects: source inspection and the green `ArchitectureTest` "no billing import" rule give strong structural confidence that R2 and R7 are correctly implemented, and all 45 tasks are complete with the full monorepo `./gradlew test --rerun-tasks` (65/65 tasks, exit 0) and `./gradlew check` (checkstyle + layered JaCoCo coverage gates, exit 0) both green with zero regressions elsewhere. However, this project's compliance bar (a spec scenario is compliant only when a covering test passed at runtime — the same bar the precedent `billing-bank-transfer-subscription` change was held to for its R9 scenarios) is not met for these two scenarios, so the verdict cannot be PASS. Recommended remediation before `sdd-archive`: add one fixture-based test each — (a) seed a real Billing `MONTHLY` purchase confirmed mid-month via the existing purchase-confirmation flow and assert the resulting assignments split correctly across the two monthly views, and (b) seed a session cancelled before any student quoted it (no assignment row ever created) and assert it is absent from both `sessions[]` and `scheduledSessionCount` for that month — then re-run `sdd-verify`.

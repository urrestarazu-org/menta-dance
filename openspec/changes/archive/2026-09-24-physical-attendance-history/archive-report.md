# SDD Archive Report: physical-attendance-history

**Change Name**: physical-attendance-history
**GitHub Issue**: #39, US-PHYSICAL-002
**Title**: Physical Attendance History — Read Endpoint (Self & Elevated)
**Archived Date**: 2026-09-24
**Archive Location**: `openspec/changes/archive/2026-09-24-physical-attendance-history/`

---

## Final State Authority

This archive report records the state of the change **at close**, per `sdd-archive` authority hierarchy. The change is **COMPLETE** and ready for production. All twelve requirements and 21 scenarios have passed verification.

**Highest-ranked final-state source**: Structured status from `sdd-verify` re-verification run (commit `5038d00` on `develop`), which reports **PASS**: 12/12 requirements, 21/21 scenarios, 0 CRITICAL/WARNING/SUGGESTION blockers. The change required one remediation cycle (PR #261) to add missing test coverage for two scenarios (R2, R7) that were spec-compliant but untested; both are now proven at runtime.

---

## Completeness Summary

| Dimension | Status | Evidence |
|-----------|--------|----------|
| **Implementation** | ✅ Complete | All 48 tasks (P1: 1.1–1.28, P2: 2.1–2.17, Remediation: R.1–R.3) checked `[x]` in tasks.md; 2 chained PRs (#259, #260) + 1 remediation PR (#261) merged to `develop` |
| **Verification** | ✅ Pass | `./gradlew test --rerun-tasks` exit 0 (65 actionable tasks); `./gradlew check` exit 0; all 21/21 scenarios directly proven by passing runtime tests |
| **Spec Compliance** | ✅ Complete | 21/21 scenarios have runtime test coverage; 0 UNTESTED scenarios; 12/12 requirements mapped to implementation |
| **Specification** | ✅ Merged | Delta spec copied to main specs; `physical-attendance-history` created as new main spec |

---

## Build & Test Evidence

**Full Test Run** (re-verified after PR #261):
- Command: `./gradlew test --rerun-tasks`
- Exit Code: 0
- Tasks: 65 actionable tasks: 65 executed
- Duration: 15m25s
- Evidence: All tests green. `:api:physical:test` 293/293, `:api:auth:test` 498/498, `:api:app:test` 345/345 (including `PhysicalAttendanceHistoryIntegrationTest` 14/14 with the 2 new remediation tests)

**Coverage Verification**:
- Command: `./gradlew jacocoTestCoverageVerification`
- Result: ✅ All thresholds met (layered floors: `:api:physical` 95% domain+application / 90% infrastructure)

**Build Verification**:
- Command: `./gradlew check`
- Exit Code: 0
- Checkstyle: No new violations; pre-existing warnings only (same as prior state)

**Architecture Tests**:
- Command: `./gradlew :api:physical:ArchitectureTest`
- Result: ✅ 7/7 Pass (no forbidden imports introduced)

---

## Specification Merges

### 1. `physical-attendance-history` — Full Spec Created

**Main Spec**: `openspec/specs/physical-attendance-history/spec.md` (newly created)

**Source**: Copied mechanically from `openspec/changes/physical-attendance-history/specs/physical-attendance-history/spec.md`

**Content**: Twelve requirements, 21 scenarios covering:
1. Self attendance for a calendar month (4 scenarios)
2. Denominator counts assignments, not purchase coverage windows (1 scenario)
3. includeAbsent controls the session list, never the aggregate (2 scenarios)
4. Status is derived live, never stored (2 scenarios)
5. Empty month returns a zeroed 200, never 404 (1 scenario)
6. Attendance rate rounding (2 scenarios)
7. Sessions cancelled before quoting are naturally absent (1 scenario)
8. Elevated read for ADMIN and INSTRUCTOR (3 scenarios)
9. Instructor elevated read is scoped to owned courses (2 scenarios)
10. Elevated read does not leak student existence to an instructor (1 scenario)
11. Month boundaries resolve in the configured local zone (1 scenario)
12. Bounded, untruncated response for one month (1 scenario)

**Total Scenarios**: 21 scenarios across 12 requirements

---

## Implementation Summary

### Priced Requirements Met

| # | Requirement | Capability | Component Example | PR |
|---|---|---|---|---|
| R1 | Self attendance for a calendar month | self-endpoint + monthly query + aggregation | `GetPhysicalAttendanceHistoryUseCaseImpl` + `MonthlyAttendanceAssembler` | #259 |
| R2 | Denominator counts assignments, not purchase coverage windows | assignment-based month boundary | `GetPhysicalAttendanceHistoryUseCaseImpl` (atStartOfDay zone-aware) | #259 |
| R3 | includeAbsent controls the session list | list-filtering preserves aggregates | `MonthlyAttendanceAssembler.includeAbsent` flag | #259 |
| R4 | Status is derived live, never stored | attendance-left-join, no status column | `PhysicalCapacityAssignmentRepository` JPQL + LEFT JOIN | #259 |
| R5 | Empty month returns a zeroed 200 | zero-denominator branch + 0.00 rate | `MonthlyAttendanceAssembler` + `GetPhysicalAttendanceHistoryUseCaseImpl` | #259 |
| R6 | Attendance rate rounding | HALF_UP at scale 2, multiply-before-divide | `MonthlyAttendanceAssembler.rateOf()` | #259 |
| R7 | Sessions cancelled before quoting are naturally absent | no assignment row ever created for pre-quote cancellations | assignment-only source (no session-join for existence) | #259 |
| R8 | Elevated read for ADMIN and INSTRUCTOR | two port methods + two SecurityConfig matchers | `PhysicalAttendanceAdminController` + `SecurityConfig` matchers | #260 |
| R9 | Instructor elevated read is scoped to owned courses | course-filter inside JPQL query | `PhysicalCapacityAssignmentRepository.findMonthlyAttendanceInCoursesOwnedBy` | #260 |
| R10 | Elevated read does not leak student existence | anti-enumeration equality in response shape | `GetPhysicalAttendanceHistoryUseCaseImpl` (no existence port) | #260 |
| R11 | Month boundaries resolve in the configured local zone | ZoneId configured via @Value, atStartOfDay per date | `PhysicalConfiguration` + `GetPhysicalAttendanceHistoryUseCaseImpl` | #259 |
| R12 | Bounded, untruncated response for one month | no pagination, full month in single response | Both controllers (no page/cursor parameter) | #259, #260 |

### Delivered PRs (All Merged)

| # | Title | Scope | Status |
|---|---|---|---|
| #259 | feat(physical): endpoint de historial de asistencia propio (#39) | P1: Self endpoint, core query, domain, zone-awareness, V22 migration | ✅ Merged to develop (commit `4e1bca0`) |
| #260 | feat(physical): endpoint elevado de historial de asistencia (#39) | P2: Elevated endpoint, instructor scoping, admin controller, security matchers | ✅ Merged to develop (commit `42345a7`) |
| #261 | test(physical): cover R2/R7 attendance-history scenarios found untested by sdd-verify (#39) | Remediation: Add 2 fixture-based integration tests closing coverage gaps on R2 (mid-month MONTHLY purchase split) and R7 (pre-quote cancellation) | ✅ Merged to develop (commit `5038d00`) |

**All 3 PRs merged to `develop` at commit `5038d00`**; current branch head is identical (`git diff HEAD origin/develop` returns empty).

### Design Decisions Locked (C1–C7 from design.md)

All 7 architecture decisions from design.md were implemented exactly as specified:

| Decision | Implementation | Evidence |
|----------|---|---|
| C1: Two port methods, one JPQL, course filter inside query | `PhysicalCapacityAssignmentRepository.findMonthlyAttendance()` + `findMonthlyAttendanceInCoursesOwnedBy()` with `:professorId IS NULL OR c.professorId = :professorId` predicate | Query verification in adapter tests |
| C2: Sealed `AttendanceViewer` with three variants | `AttendanceViewer.Self` / `Admin` / `InstructorOwnCourses` records, exhaustive `switch` in use case | Sealed class + pattern match coverage |
| C3: Configured `ZoneId` via `@Value` on the bean | `PhysicalConfiguration.getPhysicalAttendanceHistoryUseCase(@Value("${physical.attendance.zone-id:...}") ZoneId)` | `PhysicalConfiguration` bean method parameter |
| C4: Structural anti-enumeration, no existence port | No `AttendanceNotFoundException`, no `UserQueryPort`; empty-month shape `equals()` to zero-overlap shape | Zero-overlap test (`non_overlapping_student_and_genuinely_empty_student_are_indistinguishable`) now runtime-proven |
| C5: Single `V22` migration (student_id index only) | `V22__physical_attendance_history_indexes.sql` with one `ALTER TABLE physical_capacity_assignments ADD KEY idx_physical_assignments_student` | Collision test confirmed V21 was already owned by billing rollback |
| C6: Shared web DTO pair, multiply-before-divide rate | `AttendanceHistoryResponse` + `AttendanceSessionResponse` used by both controllers; rate formula `(attended * 100) / scheduledSessionCount` | Both controllers share the same DTO pair; assembler formula verified |
| C7: Two explicit `SecurityConfig` matchers, elevated before `/admin/**` | `.requestMatchers(GET, "/api/v1/physical/attendance/me").authenticated()` followed by `.requestMatchers(GET, "/api/v1/admin/physical/attendance/*").hasAnyRole("ADMIN", "INSTRUCTOR")` before generic gate | Parameterized `SecurityConfigTest` cases pinned the order; instructor case was RED before C7 ordering fix |

---

## Specification Merge Verification

**Mechanical Copy Contract**:
- ✅ Spec copied using shell `cp` (not Read → Write)
- ✅ `physical-attendance-history` new spec: `diff -r` returned empty (perfect byte-identity)
- ✅ No truncation, no alteration

**Merge Results**:
| Spec | Action | Change Count |
|------|--------|---|
| `physical-attendance-history` | Created as new main spec | +1 new spec file, +12 requirements, +21 scenarios |

---

## Tasks Completion Gate

**All 48 core tasks complete** (every checkbox line checked `[x]` in tasks.md):

- **P1** (Self endpoint + core query/domain): 28 tasks ✅ (1.1–1.28)
- **P2** (Elevated endpoint + instructor scoping): 17 tasks ✅ (2.1–2.17)
- **Remediation** (Test coverage for R2/R7): 3 tasks ✅ (R.1–R.3)

**Incident Avoidance Verified**:
1. ✅ Migration collision with `V21__revert_billing_purchase_sessions.sql` avoided by renumbering to V22; verified by dedicated collision test
2. ✅ Pre-quote cancellation naturally absent (R7) — no assignment row ever created, so no visibility to the endpoint

---

## Change Artifacts

### In Archive Folder

| Artifact | Status | Content |
|----------|--------|---------|
| `proposal.md` | ✅ Preserved | Original proposal: scope, capabilities, risks, rollback plan |
| `design.md` | ✅ Preserved | Detailed design: architecture decisions C1–C7, data flow, file changes |
| `exploration.md` | ✅ Preserved | Exploration notes: approaches considered and rejected |
| `tasks.md` | ✅ Preserved | Detailed task breakdown: P1–P2 phases + remediation, 48 tasks, all checked |
| `verify-report.md` | ✅ Preserved | Final verification: 12/12 requirements, 21/21 scenarios, PASS verdict |
| `specs/physical-attendance-history/spec.md` | ✅ Merged to main | Full spec copied to `openspec/specs/physical-attendance-history/spec.md` |

---

## Verification Results (From sdd-verify Re-Run)

**Verification Context**: Re-run after remediation PR #261 (commit `5038d00`), against `develop` HEAD.

**Verdict**: ✅ **PASS**

**Metrics**:
- Requirements: 12/12 compliant
- Scenarios: 21/21 tested and passing
- Blockers: 0 CRITICAL, 0 WARNING, 0 SUGGESTION
- Tasks: 48/48 complete
- Test Coverage: All scenarios have ≥1 test; R2 and R7 now each have a dedicated fixture-based integration test

**R2 & R7 Coverage Closure** (Blocker from Prior FAIL):
- **Prior FAIL**: R2 ("A mid-month MONTHLY purchase splits across two monthly views") and R7 ("A pre-quote cancellation never produced an assignment") had zero runtime coverage
- **Fix (PR #261)**: Added two fixture-based integration test methods to `PhysicalAttendanceHistoryIntegrationTest`:
  - `a_mid_month_monthly_purchase_splits_assignments_across_two_monthly_views` — drives a real `MONTHLY` checkout + webhook confirmation through Billing, seeds 3-session purchase split 2 September / 1 October, asserts per-calendar-month split
  - `a_pre_quote_cancellation_never_produced_an_assignment` — seeds a `CANCELLED` session with no assignment row, asserts it is absent from the response
- **Verification**: Both test methods present on disk; both pass; both use real Testcontainers MySQL + full HTTP stack (not MockMvc)

---

## Specification State at Archive

### Main Specs Now Reflect

**`openspec/specs/physical-attendance-history/spec.md`** (new):
- 12 requirements
- 21 scenarios
- Comprehensive for attendance read endpoints: self-endpoint for calendar months with zone awareness, elevated endpoint for admin/instructor with instructor scoping, anti-enumeration to prevent student existence enumeration

### Pre-existing Specifications Unchanged

All other main specs in `openspec/specs/` remain unchanged by this change:
- `physical-capacity-hold/` (unchanged)
- `physical-checkin/` (unchanged)
- `physical-purchase-checkout/` (unchanged)
- All virtual, billing, auth, BFF specs (unchanged)

---

## Rollback Plan

Per the proposal's Rollback Plan:

1. **Revert the PR(s)**: `git revert origin/develop -n` (or equivalent) for all 3 PRs in reverse order (#261–#259).
2. **Database**: `V22` (migration creating index on `physical_capacity_assignments.student_id`) rolls back with `ALTER TABLE physical_capacity_assignments DROP KEY idx_physical_assignments_student;`
3. **No data repair needed**: The endpoint is read-only; reverting removes only the new query methods and controllers.

---

## Review Workload Summary

| Metric | Value | Evidence |
|---|---|---|
| Estimated changed lines | ~750–950 across whole change (two-port-method/one-JPQL design kept surface smaller than typical two-endpoint change) | From tasks.md forecast |
| Delivery strategy | ask-on-risk → chained PRs accepted | User accepted two-PR stacked flow |
| Final PR count | 3 (2 features + 1 bug fix) | #259, #260, #261 all merged |
| Risk mitigation | Remediation PR for test coverage gaps | PR #261 added 2 integration tests closing CRITICAL findings |

---

## Archive Manifest

**Archive Folder**: `/Users/ale/repositorios/menta-dance/openspec/changes/archive/2026-09-24-physical-attendance-history/`

**Contents**:
- ✅ `proposal.md` — Original proposal (unchanged)
- ✅ `design.md` — Technical design (unchanged)
- ✅ `exploration.md` — Exploration notes (unchanged)
- ✅ `tasks.md` — Task breakdown (unchanged, 48/48 complete)
- ✅ `verify-report.md` — Verification report (unchanged, PASS verdict)
- ✅ `specs/` — Delta specs (unchanged)
  - ✅ `physical-attendance-history/spec.md`
- ✅ `archive-report.md` — This file

**Mechanical Verification**:
- ✅ Source copied before move: snapshot preserved in `$snapshot_root`
- ✅ `diff -r` readback (archive vs. snapshot): empty (byte-identity confirmed)
- ✅ Source directory removed after move
- ✅ Archive directory verified present at new location

---

## Key Learnings

1. Migration version collision risk exists when combining `db/migration` and `db/rollback` namespaces in a single test — checking only migration folder misses existing rollback versions that occupy the same number space.

2. Anti-enumeration at the response-shape level (making zero-overlap identical to truly-empty-month) requires zero existence-checking code in the use case — even a conditional log statement based on count would break the equivalence.

3. Zone-aware month boundaries must be computed at query construction time (not in-flight filters) to ensure the JPQL `BETWEEN` predicate matches the application-layer date arithmetic — a mismatch silently includes/excludes edge rows.

4. Fixture-based integration tests for multi-system scenarios (e.g., "mid-month MONTHLY purchase splits across months") require driving real checkout + webhook confirmation workflows through dependent modules; mocking one layer breaks the scenario's validity.

5. Instructor course-scoping must be applied inside the query (joined against `Course.professorId`), not as a post-filter, to make the enforcement fail-closed (zero rows for non-overlapping instructors, not silently filtered subsets that leak existence).

---

## Closed By

**Archive Phase**: `sdd-archive` executor
**Archive Date**: 2026-09-24
**Project**: menta-dance
**Change Status**: ✅ **COMPLETE — Ready for Production**

The SDD cycle for physical-attendance-history is closed. The change is archived, specifications are merged into main specs, and all artifacts are persisted.

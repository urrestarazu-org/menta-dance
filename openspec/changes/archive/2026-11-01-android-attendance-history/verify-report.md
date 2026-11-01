```yaml
schema: gentle-ai.verify-result/v1
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 11/11
scenarios: 26/34
test_command: rtk proxy ./gradlew :android:testDebugUnitTest --rerun-tasks :android:build :android:assembleDebugAndroidTest :android:lintDebug --console=plain
test_exit_code: 0
test_output_hash: sha256:0e757a6c921a976440eb2974cd2f3024aef238555e5f229cc76bea1a11250240
build_command: rtk proxy ./gradlew :api:physical:test :api:physical:checkstyleMain :api:physical:jacocoTestCoverageVerification --console=plain
build_exit_code: 0
build_output_hash: sha256:d2aa186a7747d1b75ecdfae74df19b74aede345bb05fd4ac47c7e269a6e2ed16
```

Notes on the header:

- `evidence_revision` is omitted. The reference report does not show how it was derived (the hash of its archived spec files was tried with three plausible methods and none reproduced it), and the native ledger and validator do not exist in gentle-ai 4.x, so no validator was run on this candidate.
- `scenarios: 26/34` counts only COMPLIANT scenarios. The other 8 are PARTIAL (indirect or device-only evidence for part of the THEN clause) and carried as WARNING 1. Zero scenarios are FAILING or UNTESTED. A native validator that refuses a passing verdict with incomplete counts would reject this header as written; the counts were not inflated to satisfy it.
- Both `*_output_hash` values are sha256 of the raw Gradle console logs (they include timings, so they are an identity of this run, not reproducible across runs). A third run (integration tests, see below) is recorded in the body.

## Verification Report

**Change**: android-attendance-history (issue #376, backend prerequisite #673)
**Version**: N/A (three delta specs: android-attendance-history [new], physical-attendance-history [modified], android-auth-navigation [modified])
**Mode**: Strict TDD
**Verified at**: `develop` @ `71b6876` plus this branch `feature/680-archivar-android-attendance-history` (no source changes on the branch yet; the only untracked file is the tool-state file `.gentle-ai-instance`, never touched), 2026-11-01. All code PRs are merged: #674 (artifacts), #675 (backend `kind`), #676 (adapter), #678 (ViewModel), #679 (UI).
**Source of truth**: the openspec files. The Engram copies of proposal/spec/design under `sdd/android-attendance-history/*` carry stale row-label wording; the decisions observation (#1820) supersedes them and matches the openspec files ("Ingreso por QR · HH:mm" / "Ingreso en recepción · HH:mm" / "Ingreso HH:mm").

### Completeness

| Metric | Value |
|--------|-------|
| Tasks total (checkbox) | 63 |
| Tasks ticked | 57 (includes 2.15, ticked as an explicit skip with a reason) |
| Tasks unticked | 6: 3.11, 3.12, F.1, F.2, F.3, F.4 |
| Requirements | 11 (8 + 2 + 1) |
| Scenarios | 34 (19 + 9 + 6) |

The six unticked items are bookkeeping, not missing work:

- 3.11 (commit) and 3.12 (PR) were executed: commit/PR `feat(android): agregar la pantalla Mi asistencia (#376) (#679)` is `71b6876` on `develop`. The boxes were never ticked. They must be ticked in the archive PR.
- F.1-F.4 are the verify/archive follow-up itself (this report is F.2; F.3 and F.4 are the archive and its PR). F.1's issue exists as #680, whose branch this is.

Spot-checks of ticked tasks against the tree: 1.3/1.6/1.8 (`MonthlyAttendanceAssembler.java:48-49`, `AttendanceSessionResponse.java:10,20`, `MonthlyAttendanceRowProjection.getKind`, JPQL `a.kind AS kind`, adapter `toRows`), 1.10 (`api/openapi/physical-v1.yaml:1458-1461`), 1.11 (`.bru` docs + `res.body.sessions: isArray`), 2.2/2.3 (domain models, `AttendanceWireModels.kt:10-21`, `OkHttpAttendanceHistoryRepository.kt:22-73`), 2.5 (`PhysicalModule.attendanceHistoryRepository`, `@Singleton`, `@ApiHttpClient`), 2.6, 2.9 (`AttendanceHistoryViewModel.kt:23-86`), 3.2 (`AttendanceFormatters.kt:12-29`), 3.3 (`strings.xml:30,42-48`, all eight strings exact), 3.5/3.6 (`Routes.kt:22`, `AuthenticatedScaffold.kt:30`, `MentaNavHost.kt:69-76,108`). All match. 2.15 (`bru` request) was skipped with the stated reason. 3.10: the rg check was re-run, see "Leftovers".

### Build & Tests Execution (all fresh, this run)

**Android unit tests + build + androidTest assembly + lint**: PASS.

```text
rtk proxy ./gradlew :android:testDebugUnitTest --rerun-tasks :android:build :android:assembleDebugAndroidTest :android:lintDebug --console=plain
  -> BUILD SUCCESSFUL in 1m 44s, 149 actionable tasks: 149 executed, EXIT=0
Unit tests (from build/test-results/testDebugUnitTest/*.xml): 226 tests, 0 failures, 0 errors, 0 skipped, 21 classes
Change-owned: AttendanceHistoryViewModelTest 19, OkHttpAttendanceHistoryRepositoryTest 16, AttendanceFormattersTest 6 (41 new);
  guard DomainPurityTest 2; pre-existing 185 (matches the #375 baseline)
androidTest APK produced: android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk
Lint (lintDebug): 0 errors, 12 warnings; no warning mentions any attendance file (rg 'attendance' on the lint text report is empty)
```

**Backend (physical module gate)**: PASS.

```text
rtk proxy ./gradlew :api:physical:test :api:physical:checkstyleMain :api:physical:jacocoTestCoverageVerification --console=plain
  -> BUILD SUCCESSFUL in 2m 18s, EXIT=0
  :api:physical:test executed (not cached): 575 tests, 0 failures, 0 errors, 0 skipped, 106 classes
  Change-relevant: MonthlyAttendanceAssemblerTest 9, PhysicalAttendanceControllerTest 4,
    GetPhysicalAttendanceHistoryUseCaseImplTest 6, ArchitectureTest 8
  :api:physical:checkstyleMain UP-TO-DATE (inputs unchanged since its last success; no main source changed in this verification)
  :api:physical:jacocoTestReport + jacocoTestCoverageVerification executed, exit 0 (layered gate 95 domain+application / 90 infrastructure holds)
```

**Backend (integration, Testcontainers/MySQL)**: PASS. `:api:physical:test` does not contain the full-stack scenarios; they live in `:api:app`, so they were run separately, forced past the build cache:

```text
rtk proxy ./gradlew :api:app:test --rerun --no-build-cache --tests '*PhysicalAttendanceHistoryIntegrationTest' --console=plain
  -> BUILD SUCCESSFUL in 26s, EXIT=0; PhysicalAttendanceHistoryIntegrationTest 16 tests, 0 failures, 0 errors, 0 skipped
     (sha256 of console log: 0456144cd35ca6c19882b6821fcf568e5832b4b07937494857673e691d3b5236)
```

**OpenAPI check (request item c)**: no automated OpenAPI test covers `physical-v1.yaml` in the build (`rg` over `api/**/src/test` finds only `PaymentStatusContractTest` and `TokenResponseSerializationTest`, which are unrelated; `physical-v1` is referenced only by `api/openapi/README.md` and the CI lint). The CI check is the Redocly lint, so that was run with the same image pinned by task 1.10:

```text
docker run --rm -v "$PWD/api/openapi:/spec" redocly/cli:2.59.0 lint /spec/physical-v1.yaml
  -> "Your API description is valid", 0 errors, 2 warnings (info-license, no-server-example-com; both pre-existing), exit 0
```

**Coverage**: Android has no JaCoCo (reported as not available, not a failure, same as #375). Backend: layered gate passed (no per-file figures printed).

### TDD Compliance (Strict TDD)

| Check | Result | Details |
|-------|--------|---------|
| TDD evidence reported | PASS with caveat | No formal "TDD Cycle Evidence" table. Evidence is recorded as prose per group in `tasks.md` (every RED/GREEN task carries counts) and in apply-progress #1822 (PR1 + PR2 only, no PR3 section). See WARNING 4 |
| RED tasks have tests | PASS | All RED tasks (1.1, 1.2, 1.5, 1.7, 2.1, 2.8, 2.10-2.13, 3.1) have their test file in the tree |
| RED observed (failing first) | PASS for 10/11 groups | PR1: 1.1 compile failure then 2 assertion failures; 1.5 1 failed of 4; 1.7 2 failed of 16 (with Docker). PR2: 2.1 16/16 failed against `TODO()` stubs; 2.8 4/4; 2.10 5 failed; 2.11 4 failed; 2.12 3 failed; 2.13 1 failed. PR3: 3.1 RED is a compile failure only; the two `formatSessionTime` assertions passed immediately because the function already existed (no new code) |
| GREEN confirmed now | PASS | All listed classes pass in the fresh runs above (9/9, 6/6, 4/4, 16/16, 16/16, 19/19, 6/6) |
| Triangulation adequate | PASS | Distinct expected values: QR / MANUAL / null / missing / "NFC" kinds; month boundaries `2026-10-01T01:30Z`, `2026-09-30T23:30-03:00`, `2026-10-01T03:00Z`; 24 successive "Anterior"; stale success vs stale failure vs not-yet-started; rollover before/after; 400/401/500 and malformed body/row/instant. Mutation check recorded in 2.12 (cancel-only vs guard-only) |
| Safety net for modified files | PASS with note | PR2: `PhysicalModule` is glue and the full suite was re-run. PR1: modified existing tests (`MonthlyAttendanceAssemblerTest`, `GetPhysicalAttendanceHistoryUseCaseImplTest`, controller and integration tests) ran green in the full module runs, but a pre-change baseline run is not recorded for them (SUGGESTION 5) |

Glue-only tasks (2.5, 2.6, 3.3-3.7) are GREEN-only by the declared convention (DI, composables, nav wiring, strings) and rest on the compile gate plus manual tasks 3.M1-3.M7.

### Test Layer Distribution (change-owned)

| Layer | Tests | Files | Tools |
|-------|-------|-------|-------|
| Android unit (JVM, fakes, virtual time) | 25 | 2 (`AttendanceHistoryViewModelTest` 19, `AttendanceFormattersTest` 6) | JUnit4, kotlinx-coroutines-test |
| Android adapter over HTTP | 16 | 1 (`OkHttpAttendanceHistoryRepositoryTest`) | MockWebServer |
| Android architecture guard | 2 | 1 (`DomainPurityTest`) | JUnit4 |
| Backend unit / MockMvc | 3 new assembler + 1 new controller (files run 9 + 4) | 2 | JUnit5, MockMvc |
| Backend integration (full stack) | 1 new + 1 extended (file runs 16) | 1 | Testcontainers MySQL |
| E2E / Compose UI | 0 | 0 | not installed (out of scope per proposal) |

### Assertion Quality

All reviewed assertions verify real behaviour (the three Android test files were read in full or in the relevant sections; the backend ones at the cited lines). No tautologies. Loops run over non-empty literal collections (`listOf(400, 401, 500)`, `(1..12)`, `repeat(24)`). Fakes only, no mocks. Empty-collection assertions have non-empty companions (`an_empty_sessions_array_is_loaded_with_no_entries` vs the 12-row and 4-row cases). Type-only assertions are paired with value assertions. Stale tests use the fake's `onRequest` hook so the superseded request really is in flight. **0 CRITICAL, 0 WARNING.**

### Quality Metrics

**Linter**: 0 errors / 12 warnings, none in files of this change. **Type checker**: Kotlin and Java compile clean (no `e:` lines, no `FAILED` in either log). Checkstyle on `:api:physical` passes (up-to-date result, see above). Android has no ktlint/detekt.

### Spec Compliance Matrix

Legend:

- **COMPLIANT**: an automated test that passed in this run covers the observable behaviour of the scenario.
- **COMPLIANT (manual)**: the delta spec itself tags the scenario "manual verification" and device evidence is recorded in `tasks.md` (3.M1-3.M7, 2026-10-31, API 29 and API 37 emulators). Self-reported, not re-run here (WARNING 3).
- **PARTIAL**: part of the THEN clause (composed on-screen text, a UI literal, or wiring) is covered only by device observation, by reading the source, or indirectly; no automated assertion of the composed output. Each PARTIAL names its reason. All PARTIAL rows still have positive evidence.

#### android-attendance-history (19 scenarios)

| Requirement | Scenario | Evidence (implementation; test) | Result |
|-------------|----------|---------------------------------|--------|
| Monthly attendance request | Request shape | `OkHttpAttendanceHistoryRepository.kt:28-41,70` (GET, `month` via `addQueryParameter`, no studentId/includeAbsent); `OkHttpAttendanceHistoryRepositoryTest` `the_request_is_a_get_with_the_month_query_and_the_accept_header_and_nothing_else` (L36), `the_month_query_follows_the_requested_month` (L51). Credentials come from the shared `@ApiHttpClient` stack (`PhysicalModule.attendanceHistoryRepository`), whose Bearer behaviour is covered by the existing auth-stack tests, not by this adapter test (bare `OkHttpClient`) | COMPLIANT |
| Monthly attendance request | Whole month in one response | `twelve_attended_rows_arrive_in_one_response_and_one_request` (L70): 12 entries, `requestCount == 1` | COMPLIANT |
| Month selector and default month | Default is the current month | `AttendanceHistoryViewModel.kt:85` (`YearMonth.from(clock.instant().atZone(ACADEMY_ZONE))`); `AttendanceHistoryViewModelTest` `the_default_month_is_the_current_month_in_buenos_aires` (L40); label `AttendanceFormattersTest` `month_label_is_spanish_and_lowercase` (L17) | COMPLIANT |
| Month selector and default month | Last evening of a month in Buenos Aires | `the_last_evening_of_a_month_in_buenos_aires_still_belongs_to_that_month` (L47, `2026-10-01T01:30Z` and `2026-09-30T23:30-03:00` -> 2026-09), `buenos_aires_midnight_starts_the_next_month` (L54); device 3.M5 (UTC device at 22:30 BA, default month correct on API 29 and 37) | COMPLIANT |
| Month selector and default month | Siguiente disabled at the current month | `AttendanceHistoryViewModel.kt:57-60,64`; `next_is_a_no_op_at_the_current_month_and_issues_no_request` (L162: month unchanged, `isNextEnabled == false`, 0 calls). Button binding `enabled = state.isNextEnabled` at `MyAttendanceScreen.kt:101`; device 3.M2 | COMPLIANT |
| Month selector and default month | Navigating to earlier months | `previous_has_no_lower_bound_and_next_stays_enabled_after_24_selections` (L191: 2024-09, next enabled); `previous_selects_the_month_before_...` (L175); device 3.M2 (14 taps back to mayo de 2025) | COMPLIANT |
| Attendance row content | QR attendance | Parts: parse `a_qr_row_is_parsed_into_an_entry_with_its_instants_and_origin` (L84), `check_in_time_is_rendered_in_the_given_zone` (19:02, L22), `class_time_is_cut_in_buenos_aires_time` ("jueves 3 de septiembre, 19:00", L43), `a_known_origin_maps_to_its_check_in_string` (L32). Composition `MyAttendanceScreen.kt:119-137`, strings `strings.xml:46`. The composed row text is not asserted by a test (no Compose UI tests); device 3.M1 recorded "Ingreso por QR · 16:02", "Con Demo Instructor", "martes 13 de octubre, 16:00" on API 29 and 37 | PARTIAL (composed text device-only; each part tested) |
| Attendance row content | Manual attendance | `a_manual_row_maps_to_the_manual_origin` (L107), `a_known_origin_maps_to_its_check_in_string` (L32); `strings.xml:47`; device 3.M1 "Ingreso en recepción · 16:05" | PARTIAL (same reason as QR attendance) |
| Attendance row content | Unknown or missing kind | `a_null_missing_or_unrecognised_kind_is_unknown_and_parsing_still_succeeds` (L116: null, missing, "NFC" -> UNKNOWN, parsing succeeds); `an_unknown_origin_has_no_label` (L38); fallback `labelRes() ?: R.string.my_attendance_check_in` at `MyAttendanceScreen.kt:133`, `strings.xml:48`. The rendered "Ingreso HH:mm" line was neither unit-asserted nor seen on a device (the demo data only has QR and MANUAL rows) | PARTIAL (fallback rendering by reading only) |
| Attendance row content | Server order and Buenos Aires month cut | `rows_keep_the_server_order_even_when_it_is_not_chronological` (L142, includes a `2026-10-01T02:30Z` row); `class_time_is_cut_in_buenos_aires_time` (L43, "miércoles 30 de septiembre, 23:30") and `check_in_time_follows_the_academy_zone_across_midnight` (L27). The 02:30Z row was not exercised on a device (see WARNING 3) | COMPLIANT |
| Empty month | No attendances | `an_empty_list_produces_the_empty_state` (VM L101), `an_empty_sessions_array_is_loaded_with_no_entries` (repo L176); Empty branch is a plain `Text` with no CTA (`MyAttendanceScreen.kt:71-75`); text exact in `strings.xml:44`. Text not asserted by a test; device 3.M3 shows exactly that text on API 29 and 37 with no button or link besides the selector | PARTIAL (literal text device-observed, not unit-asserted) |
| Loading and error states | Loading then content | `refresh_shows_loading_and_then_the_loaded_entries_in_order` (L85: Loading, then Loaded in server order) | COMPLIANT |
| Loading and error states | Failure and retry | `a_failure_produces_the_error_state_and_retry_requests_the_same_month_again` (L113: Error, retry -> Loading -> Loaded, `[sept, sept]`); data: `every_failing_status_is_unavailable` (400/401/500, L224), `a_malformed_body_is_unavailable` (L199), `a_row_missing_a_required_field_is_unavailable` (L206), `a_malformed_instant_is_unavailable` (L215), `an_io_failure_is_unavailable` (L233). Message `strings.xml:45`, retry button `MyAttendanceScreen.kt:76-83`; text device-observed in 3.M4 ("No pudimos cargar tu asistencia." + "Reintentar" on API 29 and 37). Text not unit-asserted | PARTIAL (literal text device-observed, not unit-asserted) |
| Loading and error states | Session expired | Adapter maps 401 to `Unavailable` (repo L224, `OkHttpAttendanceHistoryRepository.kt:40`); routing is left to the shared `TokenRefreshAuthenticator` + `SessionGate` (existing, tested in the auth tests). No test or recorded manual run proves that the list shows no error on an unrecoverable 401, and the list may show its error state for a moment before the gate navigates (same open point as #375 SUGGESTION 2) | PARTIAL (no direct proof of "no list error") |
| Month change replaces the in-flight load | Last request wins | `a_stale_success_for_the_superseded_month_is_dropped` (L235: requests `[sept, aug]`, final month August, August content); `a_month_change_before_the_request_starts_requests_only_the_last_month` (L264); impl `AttendanceHistoryViewModel.kt:62-74` (`job?.cancel()` + `it.month == month` guard); device 3.M2 (rapid taps end on the last selection) | COMPLIANT |
| Month change replaces the in-flight load | Stale failure dropped | `a_stale_failure_for_the_superseded_month_does_not_replace_the_loaded_state` (L250) | COMPLIANT |
| Reload on resume and month change | Resume refreshes | `a_resume_refresh_on_a_loaded_state_requests_the_same_month_again_and_keeps_it` (L143: content kept while refreshing, same month, 2 requests); `a_refresh_for_the_same_month_while_one_is_in_flight_is_ignored` (L130); month change reloads via `select` (L175) | COMPLIANT |
| Reload on resume and month change | Resume wiring | `LifecycleResumeEffect(viewModel) { viewModel.refresh() }` at `MyAttendanceScreen.kt:44-47`; spec tags it manual; device 3.M7 (background and return re-requests the same month, month unchanged, API 29 and 37) | COMPLIANT (manual) |
| Layering | Domain purity | `DomainPurityTest` 2/2 in this run; `rg 'import (android|androidx)\.' android/src/main/java/com/menta/android/domain` is empty; data layer uses `@ApiHttpClient` (`PhysicalModule.kt`); the existing sessions port, adapter, fake and their tests have no diff in the change range | COMPLIANT |

#### physical-attendance-history (9 scenarios)

| Requirement | Scenario | Evidence | Result |
|-------------|----------|----------|--------|
| Self attendance for a calendar month | Student reads a five-session month | `PhysicalAttendanceHistoryIntegrationTest` `a_five_session_month_with_one_absence_round_trips_through_the_full_stack` (L308: 5 / 4 / 1, rate 80.00); assembler `five_session_month_four_attended_one_absent_yields_eighty_percent_rate` (L55) | COMPLIANT |
| Self attendance for a calendar month | Student reads a four-session month | integration `a_four_session_month_all_attended_round_trips_through_the_full_stack` (L335); assembler L37 | COMPLIANT |
| Self attendance for a calendar month | Anonymous request is rejected | integration `an_anonymous_request_is_rejected_with_401` (L553) | COMPLIANT |
| Self attendance for a calendar month | A student cannot obtain another student's data | `PhysicalAttendanceControllerTest` `a_student_cannot_obtain_another_students_data_via_any_parameter` (L125); the caller is derived from the token | COMPLIANT |
| Self attendance for a calendar month | Attended rows expose their origin | integration `sessions_expose_kind_per_row_with_an_explicit_null_for_the_absent_one` (L388: QR, MANUAL; counts 3/2/1 and order unchanged); controller `sessions_render_kind_by_name_for_attended_rows_and_as_an_explicit_null_for_absent_rows` (L76); assembler `attended_rows_expose_their_origin_qr_and_manual` (L134); impl: JPQL `a.kind AS kind`, adapter `toRows` (`valueOf`), `AttendanceSessionResponse.java:20` | COMPLIANT |
| Self attendance for a calendar month | Absent rows have no origin | same integration test (L410-411: key present, null value); assembler `absent_row_has_no_origin_while_the_attended_row_next_to_it_keeps_it` (L149); curl evidence 1.M1 (8 ABSENT rows with `"kind": null`) | COMPLIANT |
| Status is derived live, never stored | An attended session reports recordedAt | assembler `absent_row_maps_to_null_recorded_at_and_attended_row_carries_it` (L121) | COMPLIANT |
| Status is derived live, never stored | An attended session reports its kind | integration `a_manual_attendance_row_renders_correctly_in_the_history_view` (L362: `kind == "MANUAL"` from the stored row through the real JPQL) | COMPLIANT |
| Status is derived live, never stored | An absent session has a null recordedAt | assembler L121 and L149; `kind_is_dropped_when_the_row_has_no_recorded_at_even_if_a_kind_slipped_in` (L163, the single enforcement point `MonthlyAttendanceAssembler.java:48-49`) | COMPLIANT |

Requirement-level sentence "The OpenAPI contract MUST document `kind` as an optional nullable enum `[QR, MANUAL]`": satisfied at `api/openapi/physical-v1.yaml:1458-1461` (`type: [string, "null"]`, `enum: [QR, MANUAL, null]`, not in `required`) and the Redocly lint passes fresh. There is no automated test of the contract itself, only the lint (SUGGESTION 3).

#### android-auth-navigation (6 scenarios)

| Requirement | Scenario | Evidence | Result |
|-------------|----------|----------|--------|
| Authenticated home and navigation (AC3) | Home contents | `StudentSection` is exactly Home, MySessions, MyAttendance in that order (`AuthenticatedScaffold.kt:27-31`); the drawer iterates `entries` plus logout. No automated test; the scenario is not tagged manual in the spec; device 3.M6 (drawer lists Inicio, Mis clases, Mi asistencia, Cerrar sesión) | PARTIAL (no automated test; by reading plus device) |
| Authenticated home and navigation (AC3) | Navigate to Mis clases | `navigateToSection` with `popUpTo(Home)` unchanged; spec tags manual; device 3.M6 (Mis clases <-> Mi asistencia works) | COMPLIANT (manual) |
| Authenticated home and navigation (AC3) | Navigate to Mi asistencia | `composable<MyAttendance>` (`MentaNavHost.kt:69-76`), route branch (L108); spec tags manual; device 3.M6 plus 3.M1 (opens on the current month, "octubre de 2026"). 3.M6 does not state explicitly that the drawer closes on selection | COMPLIANT (manual) |
| Authenticated home and navigation (AC3) | Navigate back to Home | Same wiring; spec tags manual; device 3.M6 (Back from Mi asistencia returns to Home) | COMPLIANT (manual) |
| Authenticated home and navigation (AC3) | Back from home | Existing `popUpTo`/`replaceAll` behaviour, unchanged by this change; not tagged manual and no automated test; device 3.M6 (Back on Home leaves the app, relaunch lands on Home) | PARTIAL (no automated test; device only) |
| Authenticated home and navigation (AC3) | Logout stays available | The scaffold wraps the new destination with `onLogout` (`MentaNavHost.kt:69-76`); spec tags manual; device 3.M6 ("Cerrar sesión" from Mi asistencia shows login); logic covered by existing `SessionViewModelTest` | COMPLIANT (manual) |

**Compliance summary**: 34 scenarios: 21 COMPLIANT by automated test (12 in android-attendance-history, 9 in physical-attendance-history), 5 COMPLIANT (manual) (1 in android-attendance-history, 4 in android-auth-navigation) as the spec itself prescribes, 8 PARTIAL, 0 FAILING, 0 UNTESTED. Requirements: 11/11 implemented.

### Correctness (Static Evidence)

| Requirement | Status | Notes |
|-------------|--------|-------|
| Monthly attendance request | Implemented | `OkHttpAttendanceHistoryRepository.kt`; no `studentId`, no `includeAbsent`, no pagination |
| Month selector and default month | Implemented | `AttendanceHistoryViewModel.kt:23,54-66,82-85`; selector `MyAttendanceScreen.kt:91-105` visible in every content state; label `AttendanceFormatters.kt:16-17` |
| Attendance row content | Implemented | `MyAttendanceScreen.kt:119-137`; BA zone for class date and check-in; `kind` mapped in `OkHttpAttendanceHistoryRepository.kt:63-67`; no origin string starts with "Con" |
| Empty month | Implemented | `strings.xml:44` exact; no CTA |
| Loading and error states | Implemented | `AttendanceHistoryViewModel.kt:76-80`; error text and "Reintentar" (`common_retry`) |
| Month change replaces the in-flight load | Implemented | `job?.cancel()` plus the `it.month == month` guard |
| Reload on resume and month change | Implemented | `LifecycleResumeEffect` at `MyAttendanceScreen.kt:44-47`; no load in `init` |
| Layering | Implemented | Domain free of Android imports; sessions port untouched |
| Self attendance for a calendar month (`kind`) | Implemented | Assembler -> response -> JPQL -> adapter -> OpenAPI -> Bruno |
| Status is derived live (`kind`) | Implemented | `rowAttended ? row.kind() : null` is the single enforcement point |
| Authenticated home and navigation (three entries) | Implemented | `AuthenticatedScaffold.kt:27-31`, `MentaNavHost.kt:69-76,108`, `Routes.kt:22` |

### Coherence (Design)

| Decision | Followed? | Notes |
|----------|-----------|-------|
| B1 `AttendanceKind` in row/view, `String` in projection and response | Yes | `AttendanceHistoryRow`, `AttendanceSessionView`, `MonthlyAttendanceRowProjection.getKind(): String`, `AttendanceSessionResponse.kind: String` |
| B2 emit `null` for ABSENT rows (no `@JsonInclude`) | Yes | Controller test asserts `hasKey("kind")` and null; integration asserts `containsKey` and null |
| B3 no Lombok on touched backend types | Yes | Only records and interfaces were touched |
| A1 new port and adapter, sessions port untouched | Yes | `AttendanceHistoryRepository` + `OkHttpAttendanceHistoryRepository`; no diff on the sessions port, adapter, fake or tests |
| A2 `kind` as `String?` -> `AttendanceOrigin{QR,MANUAL,UNKNOWN}` | Yes | `AttendanceWireModels.kt:20`, adapter L63-67 |
| A3 skip rows with `recordedAt == null` | Yes | `OkHttpAttendanceHistoryRepository.kt:51-52`; test L159 |
| A4 `YearMonth.from(clock.instant().atZone(ACADEMY_ZONE))` | Yes | `AttendanceHistoryViewModel.kt:85`; `ACADEMY_ZONE` is `internal` at top level (L23) |
| A5 cancel and replace, stale guard, same-month refresh ignored | Yes | `AttendanceHistoryViewModel.kt:42-43,62-74` |
| A6 `data class AttendanceHistoryUiState(month, isNextEnabled, content)` | Yes | `AttendanceHistoryUiState.kt` |
| A7 pure top-level formatters, `formatSessionTime` reused | Yes | `AttendanceFormatters.kt`; reuse at `MyAttendanceScreen.kt:121` |
| A8 no new ADR | Yes | No ADR added |
| `isNextEnabled` recomputed on refresh | Yes | `AttendanceHistoryViewModel.kt:45-50`; rollover tests L288-L316 |
| OpenAPI `kind` shape and Bruno changes | Yes | `physical-v1.yaml:1458-1461`; `.bru` docs plus `res.body.sessions: isArray` |
| DI: `@Singleton` on `@ApiHttpClient` | Yes | `PhysicalModule.kt` |
| Strings and UI structure (TextButtons, label, `ElevatedCard` + `LazyColumn(key = sessionId)`) | Yes | `MyAttendanceScreen.kt`, `strings.xml:30,42-48` |
| Deviation: fake's optional blocking gate omitted | Documented | 2.7; `onRequest` hook covers the in-flight cases |
| Deviation: PR2 split into PR2a (#676) / PR2b (#678) | Documented | 2.18; size 880 vs 800 budget after tests came out at 626 lines vs ~280 forecast |
| Deviation: #677 merged into the PR2a branch by mistake and re-applied as #678 | Documented | 2.20; the current tree contains the ViewModel, UiState, use case and fake, and the 19 ViewModel tests pass: net effect correct. Merge order held: #675 (`kind`) before #676/#678, #678 before #679 (`git log`) |

### Leftovers (task 3.10 and verification step 4)

- `rtk proxy rg -n 'Con QR|Registrada en recepción' android/ openspec/changes/android-attendance-history/` -> exactly one hit, the task text of 3.10 (`tasks.md:171`). Expected. No code or spec hit.
- `rtk proxy rg -n 'import (android|androidx)\.' android/src/main/java/com/menta/android/domain` -> empty (exit 1). `DomainPurityTest` 2/2 green.
- `.gentle-ai-instance` is untracked (`git ls-files --error-unmatch` fails; `git status` shows `??`) and is not part of any commit in the change range. It is not covered by `.gitignore` or `check-ignore` (SUGGESTION 1).
- `git status` is otherwise clean: no source, spec or task drift since `develop` @ `71b6876`.

### Manual Evidence (recorded in `tasks.md`, not re-run)

1.M1 (2026-10-30, curl against the local demo API with two SQL-inserted attendances: `QR` and `MANUAL` visible, 8 ABSENT rows with `"kind": null`, aggregates 10/2/8/20.00 unchanged, `2026-09` returned a zeroed 200). 3.M1-3.M7 (2026-10-31, API 29 and API 37 emulators, local stack): lowercase "octubre de 2026", the two check-in lines, instructor line, BA class time, month navigation (14 taps back, rapid taps end on the last one), exact empty text, error with "Reintentar" under airplane mode and recovery, UTC device at 22:30 BA on 31 October, drawer order and Back behaviour, logout from Mi asistencia, background/resume re-request. Not exercised on a device: the `2026-10-01T02:30Z` row at the BA month edge (unit-tested only), dark theme (the app never goes dark), the "Ingreso HH:mm" fallback line. The local login hit the pre-existing AUTH_DEGRADED 503 (#672), unrelated to this change and not counted.

### Issues Found

**CRITICAL (0)**: None. No failing test, no failing build or lint, no untested scenario, no incomplete core task.

**WARNING (4)**

1. Eight scenarios are PARTIAL (reasons in the matrix): QR attendance, Manual attendance, Unknown or missing kind (composed row text or fallback rendering is not asserted by a test; QR and Manual were seen on a device, the fallback was not), No attendances and Failure and retry (UI literals device-observed but not unit-asserted), Session expired (no proof that the list shows no error on an unrecoverable 401; the adapter maps it to `Unavailable`), Home contents and Back from home (not tagged manual in the spec, no automated test). Every PARTIAL has its parts tested or device evidence; none is contradicted.
2. Task bookkeeping: 3.11 and 3.12 are unticked although their work is merged (`71b6876`, #679), and F.1-F.4 are open by design. Tick 3.11, 3.12 and F.1 (issue #680) in the archive PR so the archived `tasks.md` is consistent.
3. The manual evidence (1.M1, 3.M1-3.M7) is self-reported in `tasks.md` and was not re-run by this verification. In addition, the `2026-10-01T02:30Z` Buenos Aires month-edge row is covered by unit tests only (`rows_keep_the_server_order_even_when_it_is_not_chronological`, `class_time_is_cut_in_buenos_aires_time`, `check_in_time_follows_the_academy_zone_across_midnight`) and the BA default-month edge was exercised on device only for the default month (3.M5), not for the row.
4. TDD process evidence is incomplete in structure, not in substance: apply-progress #1822 has no formal "TDD Cycle Evidence" table and no PR3 section (it stops at PR2). The per-group RED/GREEN counts do exist in `tasks.md`. For PR3 the RED of the formatter test is a compile failure only, and two of its assertions passed on first run because `formatSessionTime` already existed.

**SUGGESTION (5)**

1. `.gentle-ai-instance` is untracked and not gitignored. Stage files explicitly in the archive PR (never `git add -A`/`git add .`), or add an ignore rule in a separate change.
2. When a UI-test slice is scheduled, a Compose test for the row (three check-in variants), the empty and error texts, and the three drawer entries would turn the PARTIAL rows (QR, Manual, Unknown kind, Empty, Failure and retry, Home contents) into automated ones; a test for the unrecoverable-401 path (no list error) would close the Session expired gap.
3. The OpenAPI requirement is guarded only by the CI Redocly lint, not by a contract test. Optional, low value for a single optional property.
4. Archive from the openspec files only. The Engram copies of proposal/spec/design under `sdd/android-attendance-history/*` carry stale row-label wording; merging from them into `openspec/specs/**` would reintroduce the old wording.
5. Record a safety-net baseline run when modifying existing tests (PR1 modified four existing backend test files with no pre-change baseline recorded), and calibrate test-size forecasts (tests were 626 lines vs ~280 forecast, which forced the PR2a/PR2b split).

### Verdict

**PASS WITH WARNINGS** (0 CRITICAL, 4 WARNING, 5 SUGGESTION). All fresh gates are green: Android 226/226 unit tests, build, androidTest assembly and lint (0 errors); backend `:api:physical` 575/575 with checkstyle and the layered JaCoCo gate; the 16 full-stack integration tests; Redocly lint valid. 11/11 requirements are implemented; 26/34 scenarios are COMPLIANT (21 automated, 5 manual as the spec prescribes) and 8 are PARTIAL with the reasons above; none is failing or untested. The change is ready for archive once WARNING 2 is applied in the archive PR; archive from the openspec files, not from the Engram copies.

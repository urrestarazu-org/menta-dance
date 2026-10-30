# Design: Android "Mi asistencia" (#376)

## Technical Approach

A three-PR chain. PR1 adds an additive, nullable `kind` to the existing monthly history response. PR2 adds the Android core: a new port, adapter, use case and ViewModel. PR3 adds the screen, the formatters and the third drawer section. The Android slices follow #375 exactly: one blocking OkHttp adapter implements one domain port, domain sealed results come straight from the adapter, and every failure collapses to `Unavailable`. The proposal's decisions are final. The delta specs are the source of truth for scenarios and copy; where they differ from the Strings section, follow the spec.

## Architecture Decisions

| # | Fork | Options | Decision and rationale |
|---|------|---------|------------------------|
| B1 | Type of `kind` across backend layers | `String` end to end; domain `AttendanceKind` in row/view, `String` on the wire | **`AttendanceKind` in `AttendanceHistoryRow` and `AttendanceSessionView`, `String` in the projection and the response.** The projection mirrors the entity column (`String`, like `getAssignmentType`). `application` may depend on `domain`. The response renders the enum by name, like `status` already does. An unknown stored value fails in `AttendanceKind.valueOf`, which is the behavior `AttendanceJpaMapper.toDomain` already has. |
| B2 | `kind` on ABSENT rows | emit `null`; omit with `@JsonInclude(NON_NULL)` | **Emit `null`**, the same as `recordedAt` (no global `default-property-inclusion` exists). The assembler sets `kind = rowAttended ? row.kind() : null`. This keeps the invariant "kind present iff recordedAt present" in one place. |
| B3 | Lombok | annotate the touched types | **None.** Every touched backend type is a `record` or an interface. CLAUDE.md keeps `record` for DTOs. |
| A1 | Port | add `monthlyAttendance` to `PhysicalSessionsRepository`; new port | **New `AttendanceHistoryRepository` + `OkHttpAttendanceHistoryRepository`** (proposal). `FakePhysicalSessionsRepository` and its tests stay untouched. The cost is about 10 duplicated lines of request building. |
| A2 | `kind` decoding | kotlinx enum; `String?` + manual map | **`String?`** mapped to `AttendanceOrigin.{QR, MANUAL, UNKNOWN}`. An unknown or missing value becomes `UNKNOWN`, which hides the label. It never maps to `Unavailable`. |
| A3 | ABSENT rows on the wire | `Unavailable`; skip | **Skip** rows whose `recordedAt` is `null`. `includeAbsent` is never sent, so such rows are not expected. Skipping keeps the list valid if the server ever sends them. |
| A4 | Current month and zone | `YearMonth.now(clock.withZone(BA))`; `clock.instant().atZone(BA)` | **`YearMonth.from(clock.instant().atZone(ACADEMY_ZONE))`**. `TimeModule` provides `Clock.systemUTC()`. The test `TestClock.withZone()` returns `this` (UTC), so `withZone` would silently break boundary tests. `ACADEMY_ZONE = ZoneId.of("America/Argentina/Buenos_Aires")` is an `internal` constant in `presentation/myattendance`. |
| A5 | Concurrency | ignore while in flight (MySessions); cancel and replace + stale guard | **Cancel and replace on a month change** (last wins). `execute()` is not abortable (#375 A4), so after `withContext(io)` returns, the result is applied only if `requested == state.month`. `refresh()` for the **same** month while a load is in flight is ignored, so a resume does not restart the request. |
| A6 | UiState shape | flat sealed interface; data class + sealed content | **`data class AttendanceHistoryUiState(month, isNextEnabled, content)`** with a `sealed interface AttendanceContent { Loading, Empty, Error, Loaded(entries) }`. The selector is visible in every state, so month and content vary independently. |
| A7 | Formatting | in the composable; pure functions | **Pure top-level functions** in `AttendanceFormatters.kt`, JVM-tested (no Compose tests). The class time reuses `formatSessionTime(instant, ACADEMY_ZONE, es-AR)`. |
| A8 | ADR | new ADR for the fixed academy zone; none | **None.** It is a feature-scoped presentation rule, already captured in the spec, with no cross-module or platform impact. ADR-0047 (minSdk 29) is unaffected: `java.time` is native. |

## Backend (PR1)

```
JPQL "a.kind AS kind" → MonthlyAttendanceRowProjection.getKind(): String (null when no attendance row)
  → adapter.toRows: kind == null ? null : AttendanceKind.valueOf(kind)
  → AttendanceHistoryRow(..., recordedAt, AttendanceKind kind)
  → MonthlyAttendanceAssembler: AttendanceSessionView(..., status, recordedAt, rowAttended ? kind : null)
  → AttendanceSessionResponse(..., String kind) = view.kind() == null ? null : view.kind().name()
```

OpenAPI `AttendanceSessionResponse.kind`: `type: [string, "null"]`, `enum: [QR, MANUAL, null]`, not required, description "Cómo se registró el check-in; null cuando status es ABSENT." Bruno `Attendance History (Me).bru`: the docs mention `kind`, and the request adds `res.body.sessions: isArray`. The admin endpoint shares the DTO and gains the field harmlessly.

## Android Core (PR2)

```kotlin
enum class AttendanceOrigin { QR, MANUAL, UNKNOWN }
data class AttendanceEntry(val sessionId: String, val courseName: String, val instructorName: String,
                           val scheduledAt: Instant, val recordedAt: Instant, val origin: AttendanceOrigin)
sealed interface AttendanceHistoryResult { data class Loaded(val entries: List<AttendanceEntry>) : AttendanceHistoryResult
                                           data object Unavailable : AttendanceHistoryResult }
interface AttendanceHistoryRepository { fun monthlyAttendance(month: YearMonth): AttendanceHistoryResult } // blocking
class GetMyAttendanceUseCase @Inject constructor(repo) { operator fun invoke(month: YearMonth) = repo.monthlyAttendance(month) }
```

- **Adapter**: `GET api/v1/physical/attendance/me?month=YYYY-MM` (`addQueryParameter("month", month.toString())`, no `includeAbsent`), the same `Accept` header and `Json { ignoreUnknownKeys = true }`. Non-2xx, I/O errors, a malformed body or a malformed `Instant` map to `Unavailable`. 401 is left to the session gate.
- **DTOs** (`AttendanceWireModels.kt`, internal): `AttendanceHistoryResponseDto(sessions)` and `AttendanceSessionDto(sessionId, scheduledAt, courseName, instructorName, recordedAt: String?, kind: String? = null)`. Aggregates are ignored.
- **DI**: `PhysicalModule.attendanceHistoryRepository(@ApiHttpClient client)` is `@Singleton`.
- **ViewModel** (`@HiltViewModel`, injects `GetMyAttendanceUseCase`, `Clock`, `@IoDispatcher`):

```
init: month = current(); content = Loading                (no load in init; the screen refreshes on resume)
refresh(): if job active for state.month → return; if Error → Loading; load(state.month)
showPreviousMonth(): select(month - 1)       showNextMonth(): if month < current() → select(month + 1)
select(m): job?.cancel(); state = (m, isNextEnabled = m < current(), Loading); load(m)
load(m): job = launch { r = withContext(io){ getMyAttendance(m) }; if (state.month == m) state = state.copy(content = r.toContent()) }
```

`isNextEnabled` is recomputed on every refresh, so the button stays correct across a month rollover.

## UI and Navigation (PR3)

- `AttendanceFormatters.kt`: `formatMonthLabel(YearMonth, Locale)` with pattern `MMMM 'de' yyyy`, producing "septiembre de 2026"; `formatCheckInTime(Instant, ZoneId)` producing `HH:mm`; `AttendanceOrigin.labelRes(): Int?`, which returns `null` for `UNKNOWN`.
- `MyAttendanceScreen` (stateful: `hiltViewModel`, `LifecycleResumeEffect { refresh() }`) and `MyAttendanceContent` (stateless). Selector row: "Anterior" `TextButton`, the label, and "Siguiente" `TextButton(enabled = isNextEnabled)`. The list reuses the #375 `ElevatedCard` + `LazyColumn(key = sessionId)` style.
- `Routes.kt` gets `@Serializable data object MyAttendance`. `StudentSection.MyAttendance(R.string.menu_section_my_attendance)` is added third. `MentaNavHost` gets a `composable<MyAttendance>` with `AuthenticatedScaffold` and a `route` `when` branch.
- Strings: `menu_section_my_attendance` "Mi asistencia", `my_attendance_previous` "Anterior", `my_attendance_next` "Siguiente", `my_attendance_empty` "No tenés asistencias registradas en este mes.", `my_attendance_error` "No pudimos cargar tu asistencia.", `my_attendance_check_in_qr` "Ingreso por QR · %1$s", `my_attendance_check_in_manual` "Ingreso en recepción · %1$s", `my_attendance_check_in` "Ingreso %1$s" (unknown or missing `kind`). The origin and the check-in time share one line, so the origin never starts with "Con" and cannot be confused with the instructor line. The UI reuses `common_retry` and `my_sessions_instructor`.

## File Changes

`P` = `api/physical/src/main/java/com/menta/physical`, `A` = `android/src/main/java/com/menta/android`, `T` = `android/src/test/java/com/menta/android`.

| PR | Create | Modify |
|----|--------|--------|
| 1 | none | `P/infrastructure/persistence/repository/{PhysicalCapacityAssignmentJpaRepository,MonthlyAttendanceRowProjection}.java`, `P/infrastructure/persistence/adapter/PhysicalCapacityAssignmentRepositoryAdapter.java`, `P/application/dto/{AttendanceHistoryRow,AttendanceSessionView}.java`, `P/application/usecase/MonthlyAttendanceAssembler.java`, `P/infrastructure/web/dto/AttendanceSessionResponse.java`, `api/openapi/physical-v1.yaml`, `bruno/API - Direct/physical/Attendance History (Me).bru`; tests `MonthlyAttendanceAssemblerTest`, `GetPhysicalAttendanceHistoryUseCaseImplTest` (constructor), `PhysicalAttendanceControllerTest`, `api/app/.../PhysicalAttendanceHistoryIntegrationTest` |
| 2 | `A/domain/model/{AttendanceEntry,AttendanceOrigin,AttendanceHistoryResult}.kt`, `A/domain/repository/AttendanceHistoryRepository.kt`, `A/domain/usecase/GetMyAttendanceUseCase.kt`, `A/data/remote/AttendanceWireModels.kt`, `A/data/repository/OkHttpAttendanceHistoryRepository.kt`, `A/presentation/myattendance/{AttendanceHistoryUiState,AttendanceHistoryViewModel}.kt`, `T/data/repository/OkHttpAttendanceHistoryRepositoryTest.kt`, `T/presentation/myattendance/AttendanceHistoryViewModelTest.kt`, `T/testing/FakeAttendanceHistoryRepository.kt` | `A/di/PhysicalModule.kt` |
| 3 | `A/presentation/myattendance/{AttendanceFormatters,MyAttendanceScreen}.kt`, `T/presentation/myattendance/AttendanceFormattersTest.kt` | `A/presentation/navigation/{Routes,AuthenticatedScaffold,MentaNavHost}.kt`, `res/values/strings.xml`, delta specs |

## Testing Strategy (strict TDD: each RED test lands before its code)

| PR | Test (RED first) | Covers |
|----|------------------|--------|
| 1 | `MonthlyAttendanceAssemblerTest` | an attended row carries `QR`/`MANUAL`; an absent row has `kind == null` with `includeAbsent=true` |
| 1 | `PhysicalAttendanceControllerTest` | `$.sessions[0].kind == "QR"`, `$.sessions[1].kind` is `nullValue()` |
| 1 | `PhysicalAttendanceHistoryIntegrationTest` (Testcontainers) | the existing manual-row test asserts `kind == "MANUAL"`; a QR row asserts `"QR"`; an absent row has the key present with a null value. This is the only real coverage of the JPQL and `toRows`. |
| 2 | `OkHttpAttendanceHistoryRepositoryTest` (MockWebServer) | path, `month` query, no `includeAbsent`, `Accept`; QR/MANUAL/unknown/missing `kind`; null `recordedAt` skipped; unknown keys; 401/500/I/O/malformed body/bad instant → `Unavailable` |
| 2 | `AttendanceHistoryViewModelTest` (`MainDispatcherRule`, `Clock.fixed`, fake) | default month at `2026-09-30T23:30-03:00` is September and at `2026-10-01T03:00Z` is October; Loading → Loaded/Empty/Error; retry; next disabled at the current month; previous enables next; month change cancels (fake records the months) and a stale result is dropped (the fake's `onRequest` hook changes the month during the first call); same-month refresh in flight is ignored |
| 3 | `AttendanceFormattersTest` | "septiembre de 2026", `HH:mm` in BA, `labelRes` for all three origins |

The PR1 layered gates (physical 95/90) hold because every new branch has a test. The PR2 and PR3 gates are the existing `:android:testDebugUnitTest` checks and `DomainPurityTest`. Manual checks per PR3: drawer order, Back to Home, month paging, airplane mode → error → "Reintentar".

## Size Forecast (additions + deletions)

| PR | Prod | Tests | Docs/spec | Total | vs 800 |
|----|------|-------|-----------|-------|--------|
| 1 | ~30 | ~75 | ~55 (OpenAPI, Bruno, delta) | **~160** | Low |
| 2 | ~230 | ~280 | 0 | **~510** | Medium |
| 3 | ~230 | ~50 | ~120 (two deltas) | **~400** | Low |

## Threat Matrix

N/A: no shell, subprocess, VCS/PR automation, executable-file classification or process-integration boundary. App navigation is not request routing. The endpoint keeps deriving the student from the token, and no logging is added (ADR-0025).

## Migration / Rollout

No data migration (`physical_attendances.kind` is already `NOT NULL`). PR1 is additive. Revert PR3, PR2 and PR1 in that order. PR2 must merge after PR1 (#673).

## Open Questions

- None blocking. Confirm at apply time that the JDK `es-AR` CLDR data renders `MMMM` in lowercase ("septiembre"), like Android ICU; if they differ, assert the JVM output and verify on the emulator.

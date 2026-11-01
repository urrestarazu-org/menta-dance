# Proposal: Android "Mi asistencia" (#376)

## Intent

Students cannot review in the app which classes they attended in a month or how each attendance was recorded. The backend already serves the monthly history (`GET /api/v1/physical/attendance/me`) but omits the QR/manual origin required by #376. Success: a student opens "Mi asistencia", sees the current month's attended classes with origin and check-in time, and pages back through earlier months.

## Scope

### In Scope
- **PR1 backend (#673)**: additive `kind` (`QR`|`MANUAL`, null/omitted when `ABSENT`) on each `sessions[]` item; OpenAPI, tests.
- **PR2 Android core**: domain model + new port/use case, OkHttp adapter (`AttendanceHistory…`), DI, ViewModel (month state, load/retry/resume), tests.
- **PR3 Android UI (`Closes #376`)**: screen, month selector, formatters, drawer entry, route, strings, spec deltas.

### Out of Scope
- Monthly summary line, absences (`includeAbsent` not sent), client pagination, month picker/dropdown.
- Icons, dark theme work, iOS/web, Compose UI tests.

## Capabilities

### New Capabilities
- `android-attendance-history`: monthly attended list, month navigation, row content, empty/error/retry, zone rules.

### Modified Capabilities
- `physical-attendance-history`: session rows expose `kind` (QR|MANUAL; null for ABSENT).
- `android-auth-navigation`: drawer lists exactly three entries (Home, "Mis clases", "Mi asistencia"); was two.

## Approach

- Default month and boundaries/times in fixed `America/Argentina/Buenos_Aires`, derived from the injected `Clock` (never raw UTC); intentionally differs from "Mis clases" (device zone).
- Selector: "Anterior" / label ("septiembre de 2026") / "Siguiente"; "Siguiente" disabled at current month; no lower bound.
- Row: course, class date/time, instructor, one check-in line from `recordedAt` that carries the origin: "Ingreso por QR · HH:mm" / "Ingreso en recepción · HH:mm", or "Ingreso HH:mm" when the origin is unknown.
- Empty: "No tenés asistencias registradas en este mes."; error: Spanish message + "Reintentar".
- `kind` parsed as String with fallback; any failure collapses to `Unavailable` (401 left to session gate).
- Month change cancels/replaces the in-flight load (last wins); stale results discarded. Reload on resume and on month change.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/physical/**` attendance history query/views/DTO | Modified | carry `kind` |
| `api/openapi/physical-v1.yaml` | Modified | nullable `kind` enum |
| `android/.../domain`, `data`, `di` | New | port, use case, adapter, binding |
| `android/.../presentation/myattendance` | New | ViewModel, screen, formatters |
| `android/.../presentation/navigation`, `strings.xml` | Modified | third section |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Zone mismatch (device vs BA) near month end | Med | Fixed BA zone from `Clock`; boundary tests |
| Empty list misread as "no classes" | Med | Neutral copy |
| Rapid switching with blocking `execute()` | Med | Last-wins; discard stale results; fake-based tests |
| Drawer spec says "exactly two" | High | MODIFIED delta in PR3 |
| Unknown `kind` breaks parsing | Low | String DTO + fallback |

## Rollback Plan

Revert PR3, PR2, PR1 in reverse order. PR1 is additive (clients ignore unknown keys); no data migration.

## Dependencies

- #673 merged before PR2 consumes `kind`.

## Success Criteria

- [ ] All #376 acceptance criteria pass.
- [ ] Stacked-to-main chain under Git Flow; each PR at most 800 changed lines (~160 / ~450 / ~400).
- [ ] Month-boundary, last-wins, and unknown-`kind` cases covered by JVM tests.
- [ ] `./gradlew :api:physical:test` and `:android:testDebugUnitTest` green; coverage gates hold.

## Assumptions for user review

- "Mi asistencia" is the third drawer entry, after "Mis clases".
- No client pagination; reload on resume and month change.
- Month change cancels the in-flight load instead of the MySessions ignore-if-in-flight rule.
- New `AttendanceHistory…` port; `PhysicalSessionsRepository` untouched.

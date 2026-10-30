# Android Attendance History Specification

## Purpose

Monthly attended-classes history ("Mi asistencia") for the student Android app (#376), backed by `GET /api/v1/physical/attendance/me`. Scenarios marked "manual verification" cannot be covered by JVM unit tests (no Compose UI tests in scope). All user-facing text MUST be Spanish (es-AR).

## Requirements

### Requirement: Monthly attendance request

For the selected month the app MUST request `GET /api/v1/physical/attendance/me?month=YYYY-MM` through the authenticated API client. It MUST NOT send a `studentId` and MUST NOT send `includeAbsent`, so the list contains attended classes only. The app MUST NOT paginate: the whole month arrives in one response.

#### Scenario: Request shape
- GIVEN the selected month is 2026-09
- WHEN the history loads
- THEN exactly one request is made with the path `/api/v1/physical/attendance/me`, query `month=2026-09`, no `studentId` and no `includeAbsent`, carrying the session credentials

#### Scenario: Whole month in one response
- GIVEN the API answers 200 with 12 attended sessions
- WHEN the history loads
- THEN all 12 rows are held in one state AND no further page request is made

### Requirement: Month selector and default month

The screen MUST show a selector with an "Anterior" text button, a month label (Spanish, lowercase month name, e.g. "septiembre de 2026") and a "Siguiente" text button. The initial month MUST be the current calendar month in the fixed zone `America/Argentina/Buenos_Aires`, computed from the injected `Clock` (never from the raw UTC date and never from the device zone). "Anterior" MUST select the previous month with no lower bound. "Siguiente" MUST select the next month and MUST be disabled when the selected month is the current month, so no future month can be selected.

#### Scenario: Default is the current month
- GIVEN the clock reads 2026-09-15T15:00:00Z
- WHEN the screen state is created
- THEN the selected month is 2026-09 AND the label is "septiembre de 2026"

#### Scenario: Last evening of a month in Buenos Aires
- GIVEN the clock reads 2026-10-01T01:30:00Z (2026-09-30 22:30 in Buenos Aires)
- WHEN the screen state is created
- THEN the selected month is 2026-09, not 2026-10

#### Scenario: Siguiente disabled at the current month
- GIVEN the selected month is the current month
- WHEN the selector state is read
- THEN "Siguiente" is disabled AND selecting next is a no-op that makes no request

#### Scenario: Navigating to earlier months
- GIVEN the selected month is 2026-09
- WHEN the user selects "Anterior" 24 times
- THEN the selected month is 2024-09 AND "Anterior" is still enabled AND "Siguiente" is enabled

### Requirement: Attendance row content

Each attended session MUST be listed in the server order, showing the course name, the class date and start time, the instructor name and one check-in line that combines the origin and the check-in time. The class date and time and the check-in time MUST be rendered in `America/Argentina/Buenos_Aires` in Spanish. The check-in time MUST derive from `recordedAt`. The check-in line MUST read "Ingreso por QR · HH:mm" when `kind` is `QR`, "Ingreso en recepción · HH:mm" when `kind` is `MANUAL`, and "Ingreso HH:mm" (no origin) when `kind` is null, absent or any other value. The origin wording MUST NOT start with "Con", which is reserved for the instructor line.

#### Scenario: QR attendance
- GIVEN a row "Salsa" / "Ana", `scheduledAt` 2026-09-03T22:00:00Z, `recordedAt` 2026-09-03T22:02:00Z, `kind` QR
- WHEN the list loads
- THEN the row shows "Salsa", "jueves 3 de septiembre, 19:00", "Con Ana" and "Ingreso por QR · 19:02"

#### Scenario: Manual attendance
- GIVEN a row with `kind` MANUAL
- WHEN the list loads
- THEN the check-in line reads "Ingreso en recepción · HH:mm"

#### Scenario: Unknown or missing kind
- GIVEN rows whose `kind` is null, absent, or an unrecognised value such as "NFC"
- WHEN the response is parsed
- THEN parsing succeeds AND each row is listed with the check-in line "Ingreso HH:mm" and no origin

#### Scenario: Server order and Buenos Aires month cut
- GIVEN rows returned for 2026-09 in order [A, B], B at 2026-10-01T02:30:00Z
- WHEN the list loads
- THEN the order is [A, B] AND B shows "miércoles 30 de septiembre, 23:30"

### Requirement: Empty month

When the API answers 200 with `sessions: []` the screen MUST show the plain text "No tenés asistencias registradas en este mes." It MUST NOT claim that the student has no classes and MUST NOT offer a purchase link or call to action.

#### Scenario: No attendances
- GIVEN the API answers 200 with `sessions: []`
- WHEN the history loads
- THEN the state is Empty AND the text is exactly "No tenés asistencias registradas en este mes." AND no purchase navigation is offered

### Requirement: Loading and error states

The screen MUST show a loading state while a request runs. A network failure, non-2xx answer (other than an unrecoverable 401) or malformed body MUST produce an error state with the Spanish message "No pudimos cargar tu asistencia." and a "Reintentar" action that re-requests the selected month. A 401 unrecoverable by the shared HTTP layer MUST follow the existing session-expiry routing and MUST NOT show a list error.

#### Scenario: Loading then content
- GIVEN a pending request
- WHEN loading starts
- THEN the state is Loading, then Loaded on success

#### Scenario: Failure and retry
- GIVEN the request fails for network, 5xx, 400 or malformed-body reasons
- WHEN the failure arrives
- THEN the state is Error with "No pudimos cargar tu asistencia." AND retry issues a new request for the same month

#### Scenario: Session expired
- GIVEN the shared HTTP layer reports an unrecoverable 401
- WHEN the request completes
- THEN the app follows session-expiry routing AND no list error is shown

### Requirement: Month change replaces the in-flight load

Selecting a different month MUST cancel or supersede any in-flight load and start a load for the new month; the latest selection MUST win. The result of a superseded load MUST be discarded and MUST NOT change the state or the selected month.

#### Scenario: Last request wins
- GIVEN a load for 2026-09 is in flight
- WHEN the user selects "Anterior" (2026-08) and the 2026-09 result then arrives
- THEN the state shows the 2026-08 result AND the 2026-09 result is dropped

#### Scenario: Stale failure dropped
- GIVEN a load for 2026-09 is in flight and the user then selects 2026-08
- WHEN the 2026-09 load fails after the 2026-08 load succeeded
- THEN the state stays Loaded for 2026-08

### Requirement: Reload on resume and month change

The history for the selected month MUST be re-requested every time the screen becomes visible again and every time the selected month changes. A resume MUST keep the selected month.

#### Scenario: Resume refreshes
- GIVEN 2026-08 selected and loaded
- WHEN the screen is resumed
- THEN a new request is made for 2026-08 AND the selected month is unchanged

#### Scenario: Resume wiring
- GIVEN the screen composed
- WHEN the app returns from background
- THEN a refresh is triggered (manual verification)

### Requirement: Layering

The domain layer MUST NOT depend on Android (`DomainPurityTest`); `java.time` is allowed. The data layer MUST use the `@ApiHttpClient` stack. The existing sessions port and its tests MUST remain unchanged.

#### Scenario: Domain purity
- GIVEN the new domain classes
- WHEN `DomainPurityTest` runs
- THEN it passes

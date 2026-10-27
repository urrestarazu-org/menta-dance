# Android My Sessions Specification

## Purpose

Upcoming in-person sessions list ("Mis clases") for the student Android app (#375), backed by `GET /api/v1/physical/sessions/me`. Scenarios marked "manual verification" cannot be covered by JVM unit tests (no Compose UI tests in scope).

## Requirements

### Requirement: Upcoming sessions list

The app MUST show the sessions returned by `GET /api/v1/physical/sessions/me`, in the server order (`scheduledAt` ascending). Each item MUST show course name, instructor name and the scheduled start in the device time zone. All user-facing text MUST be Spanish.

#### Scenario: Sessions returned
- GIVEN the API answers 200 with two sessions
- WHEN the list loads
- THEN the state holds both sessions in the same order with course, instructor and start

#### Scenario: Session already started today
- GIVEN the API includes a session whose `scheduledAt` is earlier today
- WHEN the list loads
- THEN that session is listed like any other (no client-side date filtering)

### Requirement: Empty state

When the API answers 200 with `sessions: []` the screen MUST show the plain Spanish text "No tenés clases próximas con lugar confirmado". It MUST NOT show a purchase link or call to action.

#### Scenario: No sessions
- GIVEN the API answers 200 with `sessions: []`
- WHEN the list loads
- THEN the state is Empty AND the text is exactly "No tenés clases próximas con lugar confirmado" AND no purchase navigation is offered

### Requirement: Loading and error states

The screen MUST show a loading state while the request runs. A network failure or unavailable API MUST produce an error state with a Spanish message and a retry action. A 401 unrecoverable by the shared HTTP layer MUST follow the existing session-expiry routing and MUST NOT show a list error.

#### Scenario: Loading then content
- GIVEN a pending request
- WHEN the ViewModel starts loading
- THEN the state is Loading, then Content or Empty on success

#### Scenario: Network failure and retry
- GIVEN the request fails for network or 5xx reasons
- WHEN the failure arrives
- THEN the state is Error with a Spanish message (wording: suggested copy) AND retry issues a new request

### Requirement: Re-fetch on every resume

The list MUST be re-fetched every time the screen becomes visible again. `checkInOpen` is a snapshot of the query instant and MUST NOT be cached across resumes.

#### Scenario: Resume refreshes
- GIVEN the list shown with a session whose `checkInOpen` was false
- WHEN the screen is resumed and the API now answers `checkInOpen: true`
- THEN a new request is made AND the state reflects `true`

#### Scenario: Resume wiring
- GIVEN the screen composed
- WHEN the app returns from background
- THEN a refresh is triggered (manual verification)

### Requirement: Check-in affordance gated by `checkInOpen`

A "Mostrar QR" button MUST be shown for a session only when `checkInOpen == true`; tapping it MUST navigate to the `AccessQr(sessionId)` destination. For a session with `checkInOpen == false` the item MUST show neutral Spanish text (wording: suggested copy, e.g. "El QR se habilita cerca del horario de la clase") and MUST NOT hardcode the window length.

#### Scenario: Window open
- GIVEN a session with `checkInOpen: true`
- WHEN the list is presented
- THEN the item exposes the "Mostrar QR" action carrying its `sessionId`

#### Scenario: Window closed
- GIVEN a session with `checkInOpen: false`
- WHEN the list is presented
- THEN no "Mostrar QR" action exists AND the neutral text is shown without minutes or hours

### Requirement: No cancelled state

The list MUST NOT model a cancelled session state, because the server omits `CANCELLED` sessions. Unknown extra fields in the response (including `assignmentType`) MUST be tolerated.

#### Scenario: Tolerant parsing
- GIVEN a response with extra unknown fields
- WHEN it is parsed
- THEN parsing succeeds AND no cancelled state is produced

### Requirement: Layering

The domain layer MUST NOT depend on Android (`DomainPurityTest`); `java.time` is allowed. The data layer MUST use the `@ApiHttpClient` stack.

#### Scenario: Domain purity
- GIVEN the new domain classes
- WHEN `DomainPurityTest` runs
- THEN it passes

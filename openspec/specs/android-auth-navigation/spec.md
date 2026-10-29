# Android Auth Navigation Specification

## Purpose

Login, startup routing, role gate, home, logout and session-expiry routing for the student Android app (#374).

## Requirements

### Requirement: Login screen (AC1)

The login screen MUST accept email and password, show a loading state while the request runs (submit disabled), and show an error state otherwise. All user-facing text MUST be Spanish.

#### Scenario: Successful login as student
- GIVEN the login screen and valid student credentials
- WHEN the user submits
- THEN loading is shown, then the home screen

#### Scenario: Empty fields
- GIVEN an empty email or password
- WHEN the user submits
- THEN no request is made and a field error is shown

### Requirement: Failure messages (AC1)

The login failure MUST map to messages as follows; no message SHALL reveal account state beyond this table.

| API outcome | Domain failure | User message |
|---|---|---|
| 401 INVALID_CREDENTIALS | InvalidCredentials | generic invalid credentials |
| 403 ACCOUNT_NOT_ACTIVATED | AccountNotActivated (distinct) | SAME generic invalid credentials |
| 423 USER_LOCKED | AccountLocked | locked-account message, no support link |
| 429 LOGIN_RATE_LIMITED | RateLimited(Retry-After) | too many attempts, retry after N seconds |
| 503 AUTH_DEGRADED / network | Unavailable | service unavailable, try later |

#### Scenario: Not-activated account
- GIVEN a correct password on a PENDING_ACTIVATION account (403)
- WHEN login fails
- THEN the domain failure is AccountNotActivated AND the message text equals the invalid-credentials message

#### Scenario: Locked, rate limited, unavailable
- GIVEN 423, 429 with `Retry-After: 30`, or 503
- WHEN login fails
- THEN each shows its own message; the 429 message includes 30 seconds; the 423 message has no support link

#### Scenario: INACTIVE/SUSPENDED
- GIVEN a 401 for such an account
- WHEN login fails
- THEN the generic invalid-credentials message is shown

### Requirement: Cold-start session resume (AC2)

On launch with a stored refresh token the app MUST show a splash while refreshing (via `X-Refresh-Token`), then route to home on success. With no stored token it MUST route straight to login.

#### Scenario: Refresh succeeds
- GIVEN a stored refresh token the API accepts
- WHEN the app starts
- THEN splash, then home, without login input

#### Scenario: No stored token
- GIVEN no stored token
- WHEN the app starts
- THEN login is shown

### Requirement: Cold-start refresh failure (AC2)

If the API rejects the token (401, REFRESH_TOKEN_COMPROMISED, or 400 INVALID_AUTH_REQUEST for a malformed stored token) the stored token MUST be wiped and login shown with a brief notice. If the API is offline or unavailable the stored token MUST be kept, login shown with a notice.

#### Scenario: Token rejected
- GIVEN the API answers 401 to the refresh
- WHEN the app starts
- THEN the Keystore refresh is cleared AND login shows the notice

#### Scenario: Offline
- GIVEN no network or 503 on refresh
- WHEN the app starts
- THEN the stored token remains AND login shows the notice

### Requirement: Authenticated home and navigation (AC3)

The authenticated destination MUST show a greeting and a logout action. The navigation drawer MUST list exactly two entries, Home and "Mis clases", and selecting an entry MUST navigate to its destination (`onNavigate`) and close the drawer. It MUST NOT show placeholder or "coming soon" entries. Navigation MUST use Navigation Compose and be extensible with one entry per later section.

#### Scenario: Home contents
- GIVEN an authenticated student
- WHEN home is shown
- THEN the greeting and logout are present AND the drawer lists Home and "Mis clases" and no other entry

#### Scenario: Navigate to Mis clases
- GIVEN the drawer open on Home
- WHEN the user selects "Mis clases"
- THEN the My Sessions destination is shown and the drawer closes (manual verification)

#### Scenario: Navigate back to Home
- GIVEN My Sessions shown
- WHEN the user selects Home in the drawer
- THEN Home is shown (manual verification)

#### Scenario: Back from home
- GIVEN home after login
- WHEN the user presses back
- THEN the app does not return to login

#### Scenario: Logout stays available
- GIVEN any authenticated destination
- WHEN the user chooses logout
- THEN the existing logout behaviour applies (manual verification; logic covered by Logout requirement)

### Requirement: Logout (AC4)

Logout MUST revoke the session on the API and MUST delete the Keystore refresh token, then route to login. Local deletion MUST happen even if the API call fails.

#### Scenario: Logout online
- GIVEN an authenticated session
- WHEN the user logs out
- THEN the API logout is called with the Bearer and refresh token, the store is cleared, login is shown

#### Scenario: Logout offline
- GIVEN the API is unreachable
- WHEN the user logs out
- THEN the store is cleared and login is shown

### Requirement: Student role gate (AC6)

The role MUST be read from the unverified `role` claim of the access token (ADMIN, INSTRUCTOR, STUDENT, RECEPTIONIST) for UX gating only. A non-STUDENT session (login or cold-start refresh) MUST show a "this app is for students" notice and be signed out (API revoke and Keystore wipe). A missing or undecodable claim MUST be treated as non-STUDENT.

#### Scenario: Instructor logs in
- GIVEN valid credentials whose token claims `INSTRUCTOR`
- WHEN login succeeds
- THEN the notice is shown, the session is revoked, the store is cleared, and home is never shown

#### Scenario: Missing claim
- GIVEN an access token without a readable `role`
- WHEN the session is established
- THEN it is treated as non-STUDENT

### Requirement: Session expiry routing (AC5)

When the HTTP layer signals an unrecoverable session (D4), the app MUST wipe the local session and route to login with a "session expired" notice.

#### Scenario: Mid-use expiry
- GIVEN an authenticated user on home
- WHEN a request still returns 401 after the single retry
- THEN the local session is wiped and login shows "session expired"

### Requirement: Placeholder removal (AC7)

`StaticWelcomeRepository`, `WelcomeMessage`, `GetWelcomeMessageUseCase`, `WelcomeRepository` and the placeholder `MainViewModel` MUST be removed.

#### Scenario: No placeholder code
- GIVEN the change is applied
- WHEN the source tree is searched for these symbols
- THEN none is found AND the project compiles and tests pass

### Requirement: Secrecy and layering

The refresh token MUST live only in Keystore-backed storage and MUST NOT be logged or exposed to presentation. The domain layer MUST NOT depend on Android.

#### Scenario: Observable state
- GIVEN any session state exposed to UI
- WHEN inspected
- THEN it contains no token value

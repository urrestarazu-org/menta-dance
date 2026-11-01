# Android Access QR Specification

## Purpose

Issue, hold, renew and display the ephemeral access QR for an in-person session (#375, US-PHYSICAL-001), via `POST /api/v1/physical/sessions/{sessionId}/access-qr` (response: `qrCredentials`, `expiresAt`, `refreshAfterSeconds`). Scenarios marked "manual verification" cannot be covered by JVM unit tests.

## Requirements

### Requirement: QR destination

The QR MUST live on its own destination `AccessQr(sessionId)`; only the non-secret `sessionId` MAY travel as a navigation argument.

#### Scenario: Open from list
- GIVEN a session with `checkInOpen: true`
- WHEN the user taps "Mostrar QR"
- THEN `AccessQr(sessionId)` is shown and a credential request is made for that id

### Requirement: Credential custody

The credential MUST be treated as an opaque string and MUST live in memory only, in ViewModel UI state. It MUST NOT use `SavedStateHandle`, `rememberSaveable`, disk, navigation arguments or logs. `toString` of every type holding it MUST mask it. The credential MUST be dropped when the screen is hidden.

#### Scenario: Masked toString
- GIVEN an issued credential `qr:abc`
- WHEN the model or UI state is converted with `toString`
- THEN the output does not contain `qr:abc`

#### Scenario: Hidden clears state
- GIVEN a QR displayed
- WHEN `onHidden` is called
- THEN the credential is no longer in the state AND any renewal job is cancelled

### Requirement: Renewal tied to visibility

While the screen is visible the app MUST renew the QR before `exp`. After each issuance the next request MUST wait `delay = min(refreshAfterSeconds, timeToExpiry - margin)`, never less than a floor of about 5 seconds, using an injected `java.time.Clock`. The loop MUST start on visible and stop on hidden.

#### Scenario: Server hint wins
- GIVEN `refreshAfterSeconds = 30` and ample time to expiry
- WHEN virtual time advances 30s
- THEN exactly one new request is made

#### Scenario: Expiry wins
- GIVEN `expiresAt` earlier than `now + refreshAfterSeconds + margin`
- WHEN the delay is computed
- THEN the new request happens before `expiresAt`

#### Scenario: Floor
- GIVEN an `expiresAt` already past by the device clock (skew)
- WHEN the delay is computed
- THEN it is the ~5s floor, not zero (no hot loop)

#### Scenario: Stop on hide
- GIVEN the loop running
- WHEN `onHidden` is called and time advances
- THEN no further requests are made

### Requirement: Denial messages

The following outcomes MUST drop the QR immediately, stop renewal, and show a Spanish message (wording: suggested copy) with no QR. The domain MUST map them to distinct reasons.

| API outcome | Reason |
|---|---|
| 403 `OUTSIDE_CHECK_IN_WINDOW` | OutsideWindow |
| 403 `SESSION_CANCELLED` | Cancelled |
| 403 `CAPACITY_ASSIGNMENT_REQUIRED` | NoCapacity |
| 404 `SESSION_NOT_FOUND` | NotFound |

#### Scenario: Window closes while open
- GIVEN a QR displayed and the next renewal answers 403 `OUTSIDE_CHECK_IN_WINDOW`
- WHEN the response arrives
- THEN the credential is cleared, the OutsideWindow message is shown, and no retry is scheduled

#### Scenario: Other denials
- GIVEN 403 `SESSION_CANCELLED`, 403 `CAPACITY_ASSIGNMENT_REQUIRED` or 404 `SESSION_NOT_FOUND`
- WHEN issuing
- THEN each yields its own reason and message AND no QR is shown

### Requirement: Transient failure

On network failure or 5xx the app MUST keep the current QR until `expiresAt`, then clear it, show "Sin conexión", and retry with backoff while visible. A later success MUST restore the QR and clear the message. An unrecoverable 401 follows the existing session-expiry routing.

#### Scenario: Failure before expiry
- GIVEN a QR valid for 20 more seconds and a failed renewal
- WHEN 10 seconds pass
- THEN the same QR is still shown

#### Scenario: Failure past expiry
- GIVEN the same failure and the clock passes `expiresAt`
- WHEN the state is evaluated
- THEN the QR is cleared AND "Sin conexión" is shown AND retries continue with increasing delay

#### Scenario: Recovery
- GIVEN "Sin conexión" shown
- WHEN a retry succeeds
- THEN the new QR is shown and the message disappears

### Requirement: Local rendering

The QR MUST be encoded locally with ZXing core 3.5.4 into a pure matrix and drawn on a Canvas as black on white with a quiet zone, even in dark theme. No network call MAY be used to obtain the image.

#### Scenario: Round-trip
- GIVEN a credential string
- WHEN it is encoded to the matrix and decoded with the ZXing reader
- THEN the decoded text equals the credential

#### Scenario: Long credential
- GIVEN a credential of 512 characters
- WHEN encoded
- THEN encoding succeeds and round-trips

#### Scenario: Drawing and theme
- GIVEN dark theme active
- WHEN the QR is drawn
- THEN modules are black on a white background with a quiet zone (manual verification)

### Requirement: Brightness

While the QR screen is visible the window brightness MUST be raised to maximum and the previous value MUST be restored on leave.

#### Scenario: Boost and restore
- GIVEN the QR screen opened and then left
- WHEN each happens
- THEN brightness is full while visible and the previous setting is restored (manual verification)

### Requirement: Screenshot blocking

The QR screen MUST set `FLAG_SECURE` while visible and clear it on leave.

#### Scenario: Secure window
- GIVEN the QR screen visible
- WHEN a screenshot or recents thumbnail is attempted
- THEN the content is blocked (manual verification)

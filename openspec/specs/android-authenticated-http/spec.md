# Android Authenticated HTTP Specification

## Purpose

Shared HTTP behaviour for authenticated API calls (AC5). Client-agnostic: requirements describe behaviour only.

## Requirements

### Requirement: Bearer injection

Every authenticated request MUST carry `Authorization: Bearer <access token>`. Unauthenticated endpoints (login, refresh) MUST NOT require it.

#### Scenario: Authenticated call
- GIVEN a held access token
- WHEN an authenticated request is made
- THEN it carries the Bearer header

### Requirement: Single refresh-and-retry

On a 401 the client MUST refresh the access token once and retry the original request once. A non-401 response MUST NOT trigger a refresh.

#### Scenario: 401 then success
- GIVEN the first response is 401 and refresh succeeds
- WHEN the retry is sent
- THEN the caller receives the retry's response AND exactly one refresh occurred

#### Scenario: Other errors
- GIVEN a 403 or 500
- WHEN received
- THEN the response is returned with no refresh

### Requirement: Single-flight refresh

Concurrent 401s MUST NOT start concurrent refreshes (the refresh token rotates and reuse revokes all sessions). Waiters MUST reuse the outcome of the in-flight refresh and retry with the new token.

#### Scenario: Two parallel 401s
- GIVEN two requests receive 401 simultaneously
- WHEN both need a refresh
- THEN exactly one refresh call reaches the API AND both retry with the new token

#### Scenario: Token already renewed
- GIVEN a request failed with an outdated token and a newer one is already held
- WHEN it handles its 401
- THEN it retries with the newer token without refreshing

### Requirement: Persistent 401 and refresh rejection

If the retry also returns 401, or the refresh is rejected (401, REFRESH_TOKEN_COMPROMISED, or 400 INVALID_AUTH_REQUEST for a malformed stored token), the client MUST wipe the local session and emit a session-expired signal, with no further retries.

#### Scenario: Retry still 401
- GIVEN refresh succeeds but the retry returns 401
- WHEN the response arrives
- THEN the local session is wiped, a session-expired signal is emitted, and no second retry occurs

#### Scenario: Refresh rejected
- GIVEN the refresh answers REFRESH_TOKEN_COMPROMISED
- WHEN handled
- THEN the same wipe and signal occur

### Requirement: Refresh unavailable

If the refresh fails for network or 503 reasons, the client MUST NOT wipe the stored refresh token and MUST surface an unavailable error to the caller.

#### Scenario: Offline during refresh
- GIVEN a 401 and a refresh that cannot reach the API
- WHEN handled
- THEN the stored token is kept AND the caller gets an unavailable error

### Requirement: Token secrecy

Tokens MUST NOT appear in logs or error messages. The rotated refresh token MUST be persisted before the new access token is used.

#### Scenario: Storage failure after rotation
- GIVEN refresh succeeds but persisting fails
- WHEN handled
- THEN the session is cleared (fail closed) and the caller gets a failure

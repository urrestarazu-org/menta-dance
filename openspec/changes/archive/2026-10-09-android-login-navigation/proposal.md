# Proposal: Android Login and Authenticated Navigation (#374)

## Intent

The Android app has secure session custody (#82) but no UI: it still shows a static welcome placeholder. Students cannot sign in, resume a session, or sign out. This change delivers the first usable student flow and the authenticated HTTP foundation that later screens (#375+) build on.

## Scope

### In Scope
- Login screen (email/password) with Spanish messages per API code. Since #477/#478 the API reveals the account state only after the password is verified, so the client maps:
  - `INVALID_CREDENTIALS` (401): unknown email, wrong password, and correct password on an `INACTIVE`/`SUSPENDED` account — one generic message.
  - `ACCOUNT_NOT_ACTIVATED` (403, correct password, `PENDING_ACTIVATION`): the user sees the same generic invalid-credentials message, but the domain model carries a distinct failure case so the app knows which case happened (telemetry/logging without PII, and a hook for a future "resend activation" action).
  - `USER_LOCKED` (423, correct password): a dedicated "your account is locked" message, no support link.
  - `LOGIN_RATE_LIMITED` (429 + Retry-After) and `AUTH_DEGRADED`/unavailable: dedicated messages.
- Cold start (D2): splash while refreshing a stored token; success -> home, failure -> login with brief notice.
- Role gate (D1): non-`STUDENT` -> "this app is for students" notice, remote revoke + Keystore wipe.
- Home (D3): greeting + logout only; no placeholder sections.
- Authenticated HTTP client: `Authorization: Bearer`, one refresh-and-retry on 401; persistent 401 -> local wipe, login with "session expired" (D4).
- Remove `StaticWelcomeRepository`, `WelcomeMessage`, `GetWelcomeMessageUseCase`, `MainViewModel` placeholder.

### Out of Scope
- Backend changes; registration, activation, password reset UI; reader mode for non-students (#357); feature sections (#375+).

## Capabilities

### New Capabilities
- `android-auth-navigation`: login, startup routing, role gate, home, logout, session-expiry routing.
- `android-authenticated-http`: bearer injection, single refresh-retry on 401, expiry signal.

### Modified Capabilities
- None (no Android specs exist).

## Approach

- **Role source (verified)**: login/refresh body is `access_token`/`token_type`/`expires_in` only; role travels as a single `role` JWT claim (`JwtService`). Decode the payload client-side, unverified, exactly as BFF `JwtAccessTokenClaimsReader` does — UX gating only; the API enforces authorization. Extend `SessionState.Authenticated` with `role`. No backend change.
- Repository calls are blocking; ViewModels run them on an IO dispatcher.
- Single-activity Compose navigation driven by a session-state machine (Splash / Login / Home), Hilt ViewModels.
- **Open design decision (sdd-design)**: OkHttp (+ optional Retrofit) vs. keeping `HttpURLConnection`.
  - OkHttp: `Authenticator`/interceptor give the retry and bearer idioms, MockWebServer for tests; costs a dependency and migrating `HttpAuthRemoteDataSource`.
  - HttpURLConnection: zero deps, consistent with #82; requires a hand-written, concurrency-safe refresh gate (single-flight) and more test scaffolding.
- **Slicing hint (auto-chain, 800 lines)**: (1) domain role + JWT reader + HTTP client/retry; (2) navigation shell, splash, login, role gate; (3) home/logout, expiry routing, placeholder removal. Final slicing belongs to sdd-tasks.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `android/.../domain/model/SessionState.kt` | Modified | Add role |
| `android/.../data/repository/AuthSessionRepository.kt` | Modified | Role extraction, expiry signal |
| `android/.../data/remote/` | Modified/New | Authenticated client |
| `android/.../presentation/` | New/Removed | Screens, nav; drop placeholder |
| `android/.../MainActivity.kt`, `di/` | Modified | Nav host, bindings |
| `gradle/libs.versions.toml` | Modified | navigation-compose, coroutines, maybe OkHttp |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Concurrent 401s trigger parallel refreshes, burning the rotated token (reuse = compromise) | Med | Single-flight refresh in design |
| Unverified JWT decode treated as security control | Low | Document as UX-only |
| Issue #374 acceptance criteria drift during spec | Low | Orchestrator cross-checked all 7 ACs against this proposal; spec phase maps each AC to scenarios |
| No emulator: instrumentation tests unexecuted | Med | Keep logic in JVM unit tests |

## Rollback Plan

Revert the slice PRs; Android-only, no schema or API change. #82 custody remains intact.

## Dependencies

- Existing API auth endpoints (verified), including the `403 ACCOUNT_NOT_ACTIVATED` contract delivered by #477 (PR #478, merged; documented in `api/openapi/auth-v1.yaml`). New Gradle deps per design.

## Success Criteria

- [ ] Student signs in, resumes on cold start, and signs out.
- [ ] Non-student is signed out with the notice, refresh token wiped.
- [ ] Persistent 401 routes to login with "session expired".
- [ ] Placeholder removed; JVM tests cover state machine and retry.

## Proposal question round

D1–D4 were settled before this phase. Resolved afterwards:
1. **Not-activated accounts (resolved via #477/#478, merged):** the backend now returns `403 ACCOUNT_NOT_ACTIVATED` only for a correct password. The user keeps seeing a generic message (decision: generic user-facing messages, specific knowledge behind the scenes); the client distinguishes the case internally. This replaces the earlier assumption that the API could not tell the case apart, and closes the acceptance-criterion mismatch ("cuenta no activada") without leaking account state. Wrong password for a `LOCKED` account is now also a generic 401, so a lock is no longer visible without the password.
2. **Offline/unavailable on cold start with a stored token (confirmed):** go to login with a notice (D2), keep the stored refresh token, and wipe it only when the API rejects it (`401`/`REFRESH_TOKEN_COMPROMISED`).
3. **Locked account (confirmed):** message only, no support contact link.

# Tasks: Android Login and Authenticated Navigation (#374)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~2,900 total (S1 ~450, S2 ~450, S3 ~600, S4 ~700, S5 ~700) |
| 800-line budget risk | Low for S1-S3; Medium for S4 and S5 (<=100 lines headroom) |
| Chained PRs recommended | Yes |
| Suggested split | S1 -> S2 -> S3 -> S4 -> S5 (S1+S2 may merge if S1 < 350 lines) |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-develop (repo rule: every slice PR targets `develop`, merged in order; each slice branches from the merged state of develop; NO tracker branch) |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
800-line budget risk: Medium

Note: the guard literal `stacked-to-main` is the closest enum value. The actual base of every PR is `develop`, never `main`.

Interim state: between S4 and S5 the app has Login and a greeting-only Home with no logout and no cold-start resume. Acceptable because the app is V0 (not in production); S5 must follow S4 promptly.

### Strict TDD and verification conventions

- Every behaviour task is RED (failing test, committed/run first) then GREEN (minimum code). REFACTOR only inside the same slice.
- Unit command: `./gradlew :android:testDebugUnitTest`
- Full gate per slice: `./gradlew :android:testDebugUnitTest :android:lint :android:assembleDebug :android:assembleDebugAndroidTest`
- The worktree has no Android SDK: run the gates in the main checkout or CI. Also run `./gradlew checkstyleMain` equivalents already wired in CI if present.
- Dependency pins: exact latest stable compatible with Kotlin 2.4.20, AGP 9.4.1, compileSdk 37, minSdk 24 (ADR-0024). No dead code per slice: add a dependency or symbol only in the slice that uses it.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| S1 | Wire migration to OkHttp 5 + kotlinx.serialization | PR 1 (base develop) | `./gradlew :android:testDebugUnitTest --tests "*OkHttpAuthRemoteDataSourceTest"` | MockWebServer on JVM; manual: emulator login against `10.0.2.2:8081` still works (manual only) | Revert restores `HttpAuthRemoteDataSource` |
| S2 | Session model, role decode, failure mapping, refresh wipe rules | PR 2 (base develop after S1) | `./gradlew :android:testDebugUnitTest --tests "*JwtRoleReaderTest" --tests "*AuthSessionRepositoryTest" --tests "*DomainPurityTest"` | Pure JVM fakes; N/A for runtime (no UI consumer yet) | Domain/data model files only; UI untouched |
| S3 | Authenticated HTTP: single-flight, interceptor, authenticator, logout retry, sessionExpired | PR 3 (base develop after S2) | `./gradlew :android:testDebugUnitTest --tests "*AuthSessionRepositoryConcurrencyTest" --tests "*AuthenticatedClientTest"` | MockWebServer counting `Dispatcher` | Repository lock + new client classes; revert keeps S2 |
| S4 | Login flow and root NavHost with Login and minimal Home | PR 4 (base develop after S3) | `./gradlew :android:testDebugUnitTest --tests "*LoginUseCaseTest" --tests "*LoginViewModelTest"` | Manual emulator (Compose, back stack) | Presentation + use-case gate; placeholder still present |
| S5 | Session shell: splash, SessionViewModel gate, drawer, placeholder deletion | PR 5 (base develop after S4) | `./gradlew :android:testDebugUnitTest --tests "*SessionViewModelTest" --tests "*RefreshSessionUseCaseTest"` | Manual emulator checklist (see S5) | Shell files; revert restores greeting-only Home |

---

## Slice 1: Wire migration (branch `feature/android-login-navigation-s1-wire-okhttp`, ~450 lines, depends on: none)

- [x] 1.1 Edit `gradle/libs.versions.toml`, root `build.gradle.kts`, `android/build.gradle.kts`: add `okhttp` (one version.ref shared with `mockwebserver3`), `kotlinx-serialization-json`, plugin `kotlin-serialization`; pin exact latest stable. Satisfies: ADR-1. Verify: `./gradlew :android:assembleDebug`.
- [x] 1.2 RED: `T/data/remote/OkHttpAuthRemoteDataSourceTest.kt` with MockWebServer: login body/headers; refresh sends `X-Refresh-Token`; logout 204 with Bearer + refresh header; problem+json `code`+`status`; `Retry-After: 30`; `expires_in: 900.0` decoding; network error. Satisfies: Failure messages, Cold-start refresh, Logout (wire level).
- [x] 1.3 GREEN: create `M/data/remote/AuthWireModels.kt` (`@Serializable` LoginRequest, TokenResponse `expires_in: Double`, Problem) and `M/data/remote/OkHttpAuthRemoteDataSource.kt`; add `status: Int` to `Problem` in `AuthRemoteDataSource.kt`.
- [x] 1.4 Create `M/di/NetworkModule.kt` providing `@AuthHttpClient` OkHttpClient (10 s timeouts, `retryOnConnectionFailure(false)`, no logging interceptor); switch `M/di/AndroidAuthModule.kt` to the new adapter. Satisfies: Token secrecy, ADR-2 no-replay rule.
- [x] 1.5 Delete `M/data/remote/HttpAuthRemoteDataSource.kt`; fix `AuthSessionRepositoryTest` fakes for `Problem.status`. Verify: full slice gate.

## Slice 2: Session model (branch `feature/android-login-navigation-s2-session-model`, ~450 lines, depends on: S1 merged)

- [x] 2.1 RED: `T/data/remote/JwtRoleReaderTest.kt` (valid ADMIN/INSTRUCTOR/STUDENT/RECEPTIONIST, missing role, lowercase, malformed, 2 parts, bad Base64url). Satisfies: Student role gate (missing claim).
- [x] 2.2 GREEN: create `M/domain/model/Role.kt`, `M/data/remote/JwtRoleReader.kt` (stdlib `Base64.UrlSafe`, regex `[A-Z][A-Z_]*`, null on failure).
- [x] 2.3 RED: extend `T/data/repository/AuthSessionRepositoryTest.kt`: 401->InvalidCredentials, 403->AccountNotActivated, 423->AccountLocked, 429->RateLimited(30), 503/network->Unavailable, refresh 401/COMPROMISED/400 INVALID_AUTH_REQUEST wipes store + SessionRejected, refresh offline/503 keeps token, persist-before-access, persist failure fails closed, `SessionState.Authenticated.role`. Satisfies: Failure messages, Cold-start refresh failure, Token secrecy.
- [x] 2.4 GREEN: make `M/domain/model/AuthFailure.kt` sealed (design Interfaces); `SessionState.Authenticated(expiresInSeconds, role: Role?)`; update `M/data/repository/AuthSessionRepository.kt` mapping and wipe rules. Fix existing callers/tests compile (current UI uses `AuthFailure.code` if any: adapt minimally, no new behaviour).
- [x] 2.5 RED then GREEN: `T/architecture/DomainPurityTest.kt` scanning `domain/` sources for `android.`/`androidx.` imports. Satisfies: Secrecy and layering.
- [x] 2.6 Test no token in `toString()` of `SessionState`/`AuthFailure.Unexpected`. Satisfies: Observable state.
- Verify: full slice gate. Dead-code check: `Role`, `JwtRoleReader`, failures all consumed by repository.

## Slice 3: Authenticated HTTP (branch `feature/android-login-navigation-s3-authenticated-http`, ~600 lines, depends on: S2 merged)

- [x] 3.1 RED: `T/data/repository/AuthSessionRepositoryConcurrencyTest.kt` (CountDownLatch): two threads same stale token -> one remote refresh, both get new token; newer token held -> no refresh; rejected outcome shared. Satisfies: Single-flight, Token already renewed.
- [x] 3.2 GREEN: create `M/data/session/AccessTokenSource.kt` (`currentAccessToken`, `renewAfterUnauthorized`, `expireSession`, `Renewal`); add `synchronized` lock, `generation`, `lastRenewal`, `@Volatile accessToken` to `AuthSessionRepository`; login/refresh/logout/renew share the lock.
- [x] 3.3 RED: repository tests: logout 401 -> renew once -> retry logout with new pair; unconditional wipe in `finally`; `sessionExpired` emitted after second-401 or rejected renewal; not emitted on Unavailable. Satisfies: Logout, Persistent 401, Refresh unavailable.
- [x] 3.4 GREEN: logout retry; add `sessionExpired: Flow<Unit>` (`MutableSharedFlow(extraBufferCapacity=1, DROP_OLDEST)`) to `M/domain/repository/SessionRepository.kt` and the implementation.
- [x] 3.5 RED: `T/data/remote/AuthenticatedClientTest.kt` (MockWebServer): Bearer added; 401->refresh->retry once; 403/500 no refresh; persistent 401 wipes + signal, no second retry; refresh COMPROMISED wipes + signal; unavailable throws `IOException`/`RefreshUnavailableException`, token kept; two parallel 401s -> exactly one `/refresh` hit; login/refresh calls carry no Bearer. Satisfies: Bearer injection, Single refresh-and-retry, Persistent 401, Refresh unavailable.
- [x] 3.6 GREEN: create `M/data/remote/BearerTokenInterceptor.kt`, `M/data/remote/TokenRefreshAuthenticator.kt` (+ `RefreshUnavailableException`); add `@ApiHttpClient` (`newBuilder` + interceptor + authenticator) to `NetworkModule`; bind repository as `SessionRepository` and `AccessTokenSource` in `AndroidAuthModule`.
- Verify: full slice gate. Note: `@ApiHttpClient` has no app consumer until #375+; it is a Hilt `@Provides` (not flagged by lint) and is exercised by tests.

## Slice 4: Login flow (branch `feature/android-login-navigation-s4-login-flow`, ~700 lines, depends on: S3 merged)

- [x] 4.1 Edit `gradle/libs.versions.toml`/`android/build.gradle.kts`: `navigation-compose`, `hilt-lifecycle-viewmodel-compose`, `kotlinx-coroutines-android`, `kotlinx-coroutines-test` (test); exact pins.
- [x] 4.2 RED: `T/domain/usecase/LoginUseCaseTest.kt`: STUDENT -> Student; INSTRUCTOR/null role -> `logout()` called then NotStudent; failure passthrough. Satisfies: Student role gate (login), Instructor logs in, Missing claim.
- [x] 4.3 GREEN: create `M/domain/model/StudentSessionResult.kt`; modify `M/domain/usecase/LoginUseCase.kt` to return it with the gate.
- [x] 4.4 Create `M/di/DispatchersModule.kt` (`@IoDispatcher`) and `T/testing/MainDispatcherRule.kt`.
- [x] 4.5 RED: `T/presentation/login/LoginViewModelTest.kt`: blank email/password -> field error, no request; loading true while running; success emits signed-in event; each `AuthFailure` -> `LoginError` (NotActivated == InvalidCredentials; RateLimited carries seconds); non-student -> StudentsOnly stays on Login. Satisfies: Login screen, Empty fields, Failure messages, Not-activated, Locked/limited/unavailable, INACTIVE/SUSPENDED.
- [x] 4.6 GREEN: create `M/presentation/login/LoginViewModel.kt`, `LoginUiState.kt`, `LoginError.kt`; add Spanish strings (design Open Questions copy) to `android/src/main/res/values/strings.xml`.
- [x] 4.7 Create `M/presentation/login/LoginScreen.kt` (email, password, submit disabled while loading, error text); manual-only verification.
- [x] 4.8 Create `M/presentation/navigation/Routes.kt` (`@Serializable`), `M/presentation/navigation/MentaNavHost.kt` (Login -> Authenticated graph { Home }, `popUpTo{inclusive}`+`launchSingleTop`), `M/presentation/home/HomeScreen.kt` ("¡Hola!" only, no logout yet); modify `M/MainActivity.kt` to `setContent { MentaDanceTheme { MentaNavHost() } }`. Placeholder `MainScreen` stays until S5 but is no longer referenced only if still compiled; keep it compiling (no lint-flagged unused code since classes remain referenced by DI until S5).
- Verify: full slice gate + manual: login `alumno@`, wrong password, blank fields, `profesor@` notice, Back on Home exits (manual only).

## Slice 5: Session shell (branch `feature/android-login-navigation-s5-session-shell`, ~700 lines, depends on: S4 merged)

- [x] 5.1 RED: `T/domain/usecase/RefreshSessionUseCaseTest.kt`: STUDENT -> Student; non-student -> `logout()` + NotStudent; no token -> SignedOut; Rejected/Unavailable -> Failed. Satisfies: Cold-start resume, Student role gate (cold start).
- [x] 5.2 GREEN: modify `M/domain/usecase/RefreshSessionUseCase.kt`; create `M/domain/usecase/ObserveSessionExpiryUseCase.kt`.
- [x] 5.3 RED: `T/presentation/session/SessionViewModelTest.kt`: gate Splash -> SignedIn (Student) / SignedOut(null) (no token) / SignedOut(ResumeRejected) / SignedOut(ResumeUnavailable, token kept) / SignedOut(StudentsOnly); `onSignedIn()` -> SignedIn; logout online and offline -> SignedOut(null) with local wipe; `sessionExpired` -> SignedOut(SessionExpired). Satisfies: Cold-start resume and failure, Logout, Session expiry routing.
- [x] 5.4 GREEN: create `M/presentation/session/SessionGate.kt` (+ `SessionNotice`) and `SessionViewModel.kt` (activity-scoped, `@IoDispatcher`); notice strings in `strings.xml`.
- [x] 5.5 Create `M/presentation/splash/SplashScreen.kt`, `M/presentation/navigation/AuthenticatedScaffold.kt` (top bar + drawer: `StudentSection` entries + "Cerrar sesión"); wire gate into `MentaNavHost.kt` and Login notice display.
- [x] 5.6 Delete placeholders: `StaticWelcomeRepository`, `WelcomeRepository`, `WelcomeMessage`, `GetWelcomeMessageUseCase`, `MainViewModel`, `MainUiState`, `MainScreen`, `M/di/AndroidBindingsModule.kt`, `T/AndroidSmokeTest.kt` (plus their tests). Satisfies: Placeholder removal. Verify: `rg "StaticWelcomeRepository|WelcomeMessage|GetWelcomeMessageUseCase|WelcomeRepository|MainViewModel" android/` returns nothing.
- [x] 5.7 Docs: note Android auth flow in `android/README` or existing docs only if a stale placeholder reference exists.
- Verify: full slice gate + manual emulator checklist (manual only): resume after kill/relaunch; airplane-mode cold start shows notice then later relaunch resumes; logout then Bruno refresh with old token rejected; `profesor@`/`recepcion@` notice; Back on Home exits; 6+ failures -> 429 message.

---

## Traceability

| Requirement (spec) | Tasks | Verification |
|---|---|---|
| Login screen (AC1) | 4.5, 4.6, 4.7 | VM unit; screen rendering manual only |
| Failure messages (AC1) | 1.2, 2.3, 4.5, 4.6 | Unit + MockWebServer |
| Cold-start resume (AC2) | 5.1, 5.3, 5.5 | Unit; splash/relaunch manual only |
| Cold-start refresh failure (AC2) | 2.3, 5.3 | Unit; offline relaunch manual only |
| Home and navigation (AC3) | 4.8, 5.5 | Home content and Back-stack manual only |
| Logout (AC4) | 1.2, 3.3, 5.3 | Unit + wire; Keystore wipe on device manual only |
| Student role gate (AC6) | 2.1, 2.2, 4.2, 4.3, 5.1 | Unit |
| Session expiry routing (AC5) | 3.3, 3.5, 5.3 | Unit (no screen makes authenticated calls; manual not feasible) |
| Placeholder removal (AC7) | 5.6 | `rg` + compile + tests |
| Secrecy and layering | 1.4, 2.5, 2.6 | DomainPurityTest, unit |
| Bearer injection | 3.5, 3.6 | MockWebServer |
| Single refresh-and-retry | 3.5, 3.6 | MockWebServer |
| Single-flight refresh | 3.1, 3.2, 3.5 | Concurrency + MockWebServer |
| Persistent 401 and refresh rejection | 3.3, 3.5, 3.6 | Unit + MockWebServer |
| Refresh unavailable | 2.3, 3.3, 3.5 | Unit + MockWebServer |
| Token secrecy (HTTP) | 1.4, 2.3, 2.6, 3.5 | Unit |

Manual-only: Compose rendering, NavHost back stack, Keystore on emulator, cleartext debug config, Hilt graph (validated by KSP at `assembleDebug`), androidTest compiled but not run (ADR-0036).

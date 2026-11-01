# Design: Android "Mis clases" and access QR (#375)

## Technical Approach

Three Clean Architecture slices in `android/` (`domain` ← `data` ← `presentation`/`di`), the first consumer of `@ApiHttpClient`. One adapter (`OkHttpPhysicalSessionsRepository`) implements one domain port; the access-QR ViewModel owns a visibility-bound renewal loop; QR encoding is a pure JVM function and the Canvas only draws. Decisions D1-D9 are taken as given. The specs (written in parallel) are the source of truth for scenarios and exact copy. If they differ from the Strings section, follow the spec.

## Architecture Decisions

| # | Fork | Options | Decision and rationale |
|---|------|---------|------------------------|
| A1 | Data-layer shape | (a) remote data source + repository, like auth; (b) one adapter that implements the domain port | **(b)**. Auth needs a repository for locking and the token store. Physical calls are stateless. One adapter tested with MockWebServer covers parsing and error mapping. This saves about 90 lines (an interface, a class and a test) per slice. |
| A2 | Result type | reuse `AuthRemoteResult`; generic `ApiResult<T>`; domain sealed results | **Domain sealed results** returned straight from the adapter. `AuthRemoteResult.Success` carries auth fields. With A1 a generic intermediate type adds nothing. |
| A3 | problem+json `code` | duplicate the private `problem()`; extract it | **Extract** `internal fun Response.problemCode(): String?` (Content-Type check plus `ProblemBody` decoding) into `data/remote/ProblemJson.kt`. Auth's `problem()` calls it; the existing `OkHttpAuthRemoteDataSourceTest` guards the refactor. Lands in **S2** (S1 maps every failure to `Unavailable`). |
| A4 | Threading | blocking `execute()` in `withContext(io)`; `enqueue` + `suspendCancellableCoroutine` | **Blocking** to match the existing pattern (`LoginUseCase`, `LoginViewModel`). Caveat: cancellation does not abort the socket. The call runs up to the 10 s timeout and `withContext` discards the result. This is acceptable for one request per screen. |
| A5 | Time in domain | `String`; `java.time` | **`java.time.Instant`/`Duration`**. minSdk 26 (ADR-0046) makes them native, and `DomainPurityTest` only bans `android.`/`androidx.`. Parse with `Instant.parse`: the backend serializes `Instant` (`...Z`). A malformed value maps to `Unavailable`. |
| A6 | `sessionId` into the VM | `SavedStateHandle.toRoute` (Bundle, not JVM-testable); `SavedStateHandle["sessionId"]`; assisted injection | **Assisted injection**: `@HiltViewModel(assistedFactory = AccessQrViewModel.Factory::class)` + `hiltViewModel<VM, Factory>(creationCallback = { it.create(id) })`. It is type-safe and the VM is a plain constructor in tests. Hilt 2.60.1 supports it. **Fallback** if `hilt-lifecycle-viewmodel-compose:1.4.0` lacks `creationCallback` (compile check in the first S3 task): `SavedStateHandle(mapOf("sessionId" to id))`, which is JVM-constructible. |
| A7 | Renewal driver | `stateIn(WhileSubscribed)` + `flatMapLatest`; explicit `onVisible()`/`onHidden()` Job | **Explicit Job**, called from `LifecycleStartEffect` (visible while STARTED, so multi-window counts). It is simpler to reason about and to test in virtual time. |
| A8 | Where encoding runs | ViewModel (matrix in state); composable `remember(credential)` | **Composable** `remember(credential.value) { QrEncoder.encode(...) }` (about 1 ms). The VM stays free of ZXing, S2 stays independent of S3, and only one object holds the credential. |
| A9 | Domain name vs route | both `AccessQr` | Route keeps **`AccessQr(sessionId)`** (D3). The domain model is **`AccessCredential`**. The stateful composable is `AccessQrRoute` (the `LoginRoute` convention). |
| A10 | Drawer → route | route inside the enum; callback | **`onNavigate: (StudentSection) -> Unit`**, and `MentaNavHost` maps a section to its route with `when`. The scaffold stays route-agnostic. A route object inside the enum would clash with the entry names (`Home`). |
| A11 | Button before S3 | dead button; hidden | `MySessionsScreen(onShowQr: ((String) -> Unit)?)`. The button renders only when `checkInOpen && onShowQr != null`. `MentaNavHost` passes `null` until S3, so no dead button reaches the chain. |

## Domain (S1 + S2)

```kotlin
data class MySession(val sessionId: String, val courseName: String, val instructorName: String,
                     val scheduledAt: Instant, val checkInOpen: Boolean)
sealed interface MySessionsResult { data class Loaded(val sessions: List<MySession>) : MySessionsResult
                                    data object Unavailable : MySessionsResult }
data class AccessCredential(val value: String, val expiresAt: Instant, val refreshAfterSeconds: Long) {
    fun renewalDelay(now: Instant): Duration   // max(FLOOR 5s, min(refreshAfter, expiresAt-now-MARGIN 5s))
    fun isExpiredAt(now: Instant): Boolean
    override fun toString() = "AccessCredential(expiresAt=$expiresAt, refreshAfterSeconds=$refreshAfterSeconds)"
}
enum class AccessDenial { OUTSIDE_CHECK_IN_WINDOW, SESSION_CANCELLED, CAPACITY_ASSIGNMENT_REQUIRED, SESSION_NOT_FOUND, OTHER }
sealed interface AccessQrResult { Issued(credential), Denied(reason), Unavailable }
interface PhysicalSessionsRepository { fun mySessions(): MySessionsResult; fun issueAccessQr(sessionId: String): AccessQrResult } // blocking
class GetMySessionsUseCase / IssueAccessQrUseCase   // @Inject, blocking, thin (LoginUseCase pattern)
```

`assignmentType`/`courseId`/`from`/`to` are ignored (`ignoreUnknownKeys`). Clients treat `qrCredentials` as opaque and never parse it.

**Error mapping** (S2): code first, then status. A 403 with one of the three codes maps to its denial. 404 maps to `SESSION_NOT_FOUND`. Any other 4xx except 401/408/429 maps to `OTHER` (retrying cannot help). 401 (after the authenticator gives up; the session gate takes over), 408, 429, 5xx, I/O errors and malformed bodies map to `Unavailable`.

## Data Layer

| Item | Detail |
|------|--------|
| Endpoints | `GET /api/v1/physical/sessions/me`; `POST /api/v1/physical/sessions/{id}/access-qr` with an empty body. Paths are built with `HttpUrl.newBuilder().addPathSegment(id)`. `Accept: application/json, application/problem+json`. The Bearer token comes from the interceptor. |
| DTOs | `data/remote/PhysicalWireModels.kt`, internal `@Serializable`: `MySessionsResponseDto`, `MySessionDto`, `AccessQrResponseDto` (**`toString` overridden** to mask `qrCredentials`). |
| Base URL | `BuildConfig.API_BASE_URL` (Android-only rename; the BFF/infra `AUTH_API_BASE_URL` env vars are unrelated and untouched). |
| No logging | No interceptor or `Log.*` is added (ADR-0025). |

## Presentation

**MySessions** (S1): `MySessionsUiState = Loading | Empty | Error | Loaded(items)`. `MySessionsViewModel.refresh()` is invoked by `LifecycleResumeEffect` (no load in `init`, so there is no double fetch). It is ignored while a fetch is in flight. Later refreshes keep the current content until the result arrives. "Reintentar" calls `refresh()`. The time label comes from a pure `formatSessionTime(instant, ZoneId.systemDefault(), Locale.forLanguageTag("es-AR"))`.

**AccessQr** (S2 VM, S3 screen): `AccessQrUiState = Loading | Showing(credential) | Offline | Denied(reason)`. `toString` is masked through `AccessCredential`. The credential lives only in this StateFlow: no `SavedStateHandle`, no `rememberSaveable`, no nav arg.

```
onVisible ─► job = launch { loop }            onHidden ─► job.cancel(); state = Loading (credential dropped)
loop: r = withContext(io){ issue(sessionId) }
  Issued      → Showing(c); failures=0; delay(c.renewalDelay(clock.instant()))
  Denied(x)   → Denied(x); return                       (credential dropped immediately)
  Unavailable → failures++; b = min(2s·2^(failures-1), 30s)
                shown valid? delay(min(b, expiresAt-now)) : { Offline; delay(b) }
                (the next iteration retries; if the retry fails after expiry → Offline)
```

`Clock` is injected. In tests `TestClock(testScheduler, origin)` derives `instant()` from `testScheduler.currentTime`, so `delay` and the expiry checks share virtual time.

## Navigation (S1 + S3)

- `Routes.kt`: `@Serializable data object MySessions` (S1), `@Serializable data class AccessQr(val sessionId: String)` (S3).
- `StudentSection.MySessions(R.string.menu_section_my_sessions)`. `AuthenticatedScaffold(selected, onNavigate, onLogout, content)`: drawer `onClick` closes the drawer and calls `onNavigate(section)` when `section != selected`.
- Section navigation: `navigate(route) { popUpTo(Home); launchSingleTop = true }`. Home → MySessions → Back returns Home; Back on Home exits.
- `AccessQr` has its own `Scaffold`, no drawer, and a "Volver" `TextButton` (no icon dependency) that calls `popBackStack()`. Back resumes MySessions, which re-fetches. Session expiry (`replaceAll(Login)`) destroys the VM and the credential.

## QR Rendering and Window Effects (S3)

| Piece | Design |
|-------|--------|
| `QrEncoder.encode(text): QrMatrix` | `QRCodeWriter().encode(text, QR_CODE, 0, 0, {ERROR_CORRECTION=M, MARGIN=0, CHARACTER_SET=UTF-8})` returns a `BitMatrix`, mapped to `QrMatrix(size, BooleanArray)` (plain class, `get(x,y)`, `toString = "QrMatrix(size=N)"`). Capacity covers the 512-char server limit. A `WriterException` maps to the "No pudimos mostrar el QR" text. |
| `QrCode(matrix)` Canvas | Fills white, with a 4-module quiet zone and an integer `cell = floor(minPx / (n + 8))` (pure helper `qrCellSize`, centered). It draws a black `drawRect` per dark module and ignores the dark theme. |
| `SecureScreenEffect` | `DisposableEffect` on the Activity window (`LocalActivity`, from activity-compose 1.10; fallback: unwrap `ContextWrapper`). Adds `FLAG_SECURE` on enter and clears it on dispose only if it was not set before. Scope: the whole `AccessQr` destination. |
| `BrightnessBoostEffect` | Saves `attributes.screenBrightness`, sets `BRIGHTNESS_OVERRIDE_FULL` and adds `FLAG_KEEP_SCREEN_ON`. On dispose it restores the saved value and clears the flag. Window-level, so no permission is needed. Active only while `Showing`. |

## DI

| Module | Provides |
|--------|----------|
| `di/PhysicalModule.kt` (S1) | `PhysicalSessionsRepository` = `OkHttpPhysicalSessionsRepository(@ApiHttpClient client, BuildConfig.API_BASE_URL)`, `@Singleton` |
| `di/TimeModule.kt` (S2) | `Clock = Clock.systemUTC()` |
| `AndroidAuthModule` (S1) | `BuildConfig.AUTH_API_BASE_URL` → `API_BASE_URL` |

## Strings (voseo, `res/values/strings.xml`)

S1: `menu_section_my_sessions` "Mis clases", `my_sessions_title` "Mis clases", `my_sessions_empty` "No tenés clases próximas con lugar confirmado", `my_sessions_error` "No pudimos cargar tus clases.", `common_retry` "Reintentar", `my_sessions_show_qr` "Mostrar QR", `my_sessions_check_in_closed` "El QR se habilita cuando abre el check-in.", `my_sessions_instructor` "Con %1$s".
S3: `access_qr_title` "Tu QR de acceso", `access_qr_hint` "Mostrá este código en la entrada.", `access_qr_loading` "Generando tu QR…", `access_qr_offline` "Sin conexión. Reintentamos automáticamente.", `access_qr_denied_outside_window` "El check-in de esta clase no está abierto.", `access_qr_denied_cancelled` "Esta clase fue cancelada.", `access_qr_denied_no_capacity` "No tenés lugar confirmado en esta clase.", `access_qr_denied_not_found` "No encontramos esta clase.", `access_qr_error` "No pudimos mostrar el QR.", `access_qr_back` "Volver".

## Testing Strategy (JUnit4, fakes, no mockk/turbine/UI tests)

| Slice | JVM test (new file) | Covers |
|-------|---------------------|--------|
| S1 | `OkHttpPhysicalSessionsRepositoryTest` (MockWebServer) | GET path/Accept, list parse, `Instant`, empty list, unknown fields, malformed body / 500 / I/O → `Unavailable` |
| S1 | `MySessionsViewModelTest` (`FakePhysicalSessionsRepository`) | Loading → Loaded/Empty/Error, retry, every `refresh()` refetches (count), in-flight dedupe, `checkInOpen` exposed per item |
| S1 | `SessionTimeFormatterTest` | es-AR format in a fixed zone |
| S2 | `OkHttpPhysicalSessionsRepositoryTest` (+) | POST path/empty body, parse, each 403 code, 404, 400 → OTHER, 401/429/5xx/I/O → Unavailable, non-problem Content-Type → status fallback |
| S2 | `AccessCredentialTest` | `renewalDelay` (normal 30 s, near-expiry margin, skew → 5 s floor), `isExpiredAt`, **`toString` masks the value** (also `AccessQrUiState.Showing`) |
| S2 | `AccessQrViewModelTest` (virtual time + `TestClock`) | issues on `onVisible`; renews at 30 s; `onHidden` cancels (no calls after `advanceTimeBy`) and drops the credential; re-`onVisible` restarts; Denied stops the loop and drops the QR; transient failure keeps the QR until `expiresAt`, then Offline; backoff 2/4/8/16/30/30 s; recovery after Offline |
| S3 | `QrEncoderTest` | **Round trip**: encode → render `QrMatrix` + quiet zone into `RGBLuminanceSource` (4 px/module) → `HybridBinarizer` → `QRCodeReader().decode().text == input` for the placeholder format and a 512-char token; square size; `QrMatrix.toString` masked; `qrCellSize` |
| all | existing `DomainPurityTest`, `OkHttpAuthRemoteDataSourceTest` | domain purity; A3 refactor guard |

**Manual per PR** (no UI tests): S1 drawer navigation and back stack, refetch on resume, empty/error states, app on an API 26 emulator. S3: a real scanner reads the QR in dark theme; brightness rises and is restored on Back; a screenshot is blocked and the Recents thumbnail is blank; the QR renews without a visible flash; airplane mode shows "Sin conexión" after expiry.

## File Changes (`A` = `android/src/main/java/com/menta/android`, `T` = `android/src/test/java/com/menta/android`)

| Slice | Create | Modify |
|-------|--------|--------|
| S1 | `A/domain/model/MySession.kt`, `A/domain/model/MySessionsResult.kt`, `A/domain/repository/PhysicalSessionsRepository.kt`, `A/domain/usecase/GetMySessionsUseCase.kt`, `A/data/remote/PhysicalWireModels.kt`, `A/data/repository/OkHttpPhysicalSessionsRepository.kt`, `A/di/PhysicalModule.kt`, `A/presentation/mysessions/{MySessionsUiState,MySessionsViewModel,MySessionsScreen,SessionTimeFormatter}.kt`, `T/.../OkHttpPhysicalSessionsRepositoryTest.kt`, `T/.../MySessionsViewModelTest.kt`, `T/.../SessionTimeFormatterTest.kt`, `T/testing/FakePhysicalSessionsRepository.kt`; `docs/adr/0046-android-min-sdk-26.md` | `android/build.gradle.kts` (minSdk 26, `API_BASE_URL`), `A/di/AndroidAuthModule.kt`, `A/presentation/navigation/{Routes,MentaNavHost,AuthenticatedScaffold}.kt`, `res/values/strings.xml`, `docs/adr/README.md`, `openspec/config.yaml` |
| S2 | `A/domain/model/{AccessCredential,AccessDenial,AccessQrResult}.kt`, `A/domain/usecase/IssueAccessQrUseCase.kt`, `A/data/remote/ProblemJson.kt`, `A/di/TimeModule.kt`, `A/presentation/accessqr/{AccessQrUiState,AccessQrViewModel}.kt`, `T/.../AccessCredentialTest.kt`, `T/.../AccessQrViewModelTest.kt`, `T/testing/TestClock.kt`; optional `bruno/API - Direct/physical/check-ins/Issue Access QR.bru` | `PhysicalSessionsRepository.kt`, `OkHttpPhysicalSessionsRepository.kt`, `PhysicalWireModels.kt`, `OkHttpAuthRemoteDataSource.kt` (uses `problemCode`), `FakePhysicalSessionsRepository.kt`, repository test |
| S3 | `A/presentation/accessqr/{QrMatrix,QrEncoder,QrCode,WindowEffects,AccessQrScreen}.kt`, `T/.../QrEncoderTest.kt` | `gradle/libs.versions.toml` + `android/build.gradle.kts` (`zxing-core` 3.5.4), `Routes.kt`, `MentaNavHost.kt` (`AccessQr` destination, `onShowQr`), `strings.xml` |

**Size forecast** (additions + deletions, tests included):

| Slice | Prod | Tests | Docs | Total | vs 800 |
|-------|------|-------|------|-------|--------|
| S1 | ~420 | ~250 | ~120 (ADR 115 + README + config) | **~760-800** | High |
| S2 | ~260 | ~350 | ~20 (Bruno) | ~600-650 | Medium |
| S3 | ~290 | ~90 | 0 | ~350-400 | Low |

**S1 split rule**: if the measured S1 diff exceeds 800 lines, ship the platform floor (ADR-0046 + README row, minSdk 26, `API_BASE_URL` rename, config.yaml) as its own first PR, ahead of the list. This needs no redesign.

## Threat Matrix

N/A: no shell, subprocess, VCS/PR automation, executable-file classification or process-integration boundary. App navigation is not request routing. Security controls: memory-only credential, masked `toString` (credential, DTO, UiState, matrix), no logging, `FLAG_SECURE`, `allowBackup=false` (already set).

## Migration / Rollout

No data migration. minSdk 24 → 26 drops API 24-25 installs (ADR-0046, V0, not in production). Revert slices in reverse order. Reverting S1 restores minSdk 24 and `AUTH_API_BASE_URL`.

## Open Questions

- None blocking. To verify at apply time: the `creationCallback` overload in `hilt-lifecycle-viewmodel-compose:1.4.0` (A6 fallback ready) and `LocalActivity` in activity-compose 1.10.1 (fallback ready).

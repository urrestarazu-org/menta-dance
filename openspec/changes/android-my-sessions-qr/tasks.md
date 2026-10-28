# Tasks: Android "Mis clases" and access QR (#375)

Conventions: `A` = `android/src/main/java/com/menta/android`, `T` = `android/src/test/java/com/menta/android`, `R` = `android/src/main/res`.
Strict TDD: every RED task is a failing JVM test written first (a compile failure counts as RED; add only the signature stubs needed to compile, bodies `TODO()`), every GREEN task is the minimum code to pass. Runner: `./gradlew :android:testDebugUnitTest [--tests "<Class>"]`. Fakes only (no mockk/turbine/Compose UI tests). Domain tasks are guarded by `DomainPurityTest` (the Android analogue of the ArchUnit rule; ArchUnit proper covers `api:*` only). Glue with no JVM-testable logic (DI, composables, nav wiring, strings) is GREEN-only and covered by the manual tasks of its PR.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~2,400-2,700 total: PR-A ~670 (openspec artifacts only), PR0 ~230, PR1 ~650-700, PR2 ~600-650, PR3 ~350-400 |
| 800-line budget risk | High as one PR; Low-Medium per PR (artifacts shipped on their own in PR-A, precedent #521 / PR #582) |
| Chained PRs recommended | Yes |
| Suggested split | PR-A artifacts (docs only) -> PR0 platform floor -> PR1 Mis clases -> PR2 access-qr -> PR3 QR render |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main (Git Flow: PR-A and PR0 -> `develop`, independent of each other; PR1 stacked on PR0; PRn stacked on PR(n-1) branch, retargeted to `develop` when its parent merges) |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

Budget for this repo is 800 changed lines per PR (`review_budget_lines`); the 400-line guard line above is the skill's literal contract and is exceeded by every PR, which is accepted by the 800 override. Strategy is resolved (auto-chain + stacked-to-main), so no decision gates apply.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| A | Change artifacts (proposal, design, specs, tasks); no code | PR-A `feature/375-pr-a-sdd-artifacts` (base `develop`) | N/A (markdown only) | `git diff --stat origin/develop...HEAD` touches only `openspec/changes/android-my-sessions-qr/` | revert PR-A (docs only) |
| 0 | minSdk 26, `API_BASE_URL`, ADR-0046 | PR0 `feature/375-pr0-android-platform-floor` (base `develop`) | `./gradlew :android:testDebugUnitTest --tests "*NetworkModuleTest"` | `./gradlew build :android:assembleDebugAndroidTest`; `rg AUTH_API_BASE_URL android/` is empty | revert PR0 alone (minSdk 24, old BuildConfig name; no code depends on it yet) |
| 1 | "Mis clases" list, drawer navigation, no QR button | PR1 `feature/375-pr1-mis-clases` (base PR0 branch) | `./gradlew :android:testDebugUnitTest --tests "*PhysicalSessionsRepositoryTest" --tests "*MySessionsViewModelTest" --tests "*SessionTimeFormatterTest" --tests "*DomainPurityTest"` | emulator API 26 against local stack `10.0.2.2:8081` | revert PR1 (list, drawer entry, DI); PR0 stays |
| 2 | Access-QR use case, renewal loop, denial/offline mapping (no UI) | PR2 `feature/375-pr2-access-qr` (base PR1 branch) | `./gradlew :android:testDebugUnitTest --tests "*AccessCredentialTest" --tests "*AccessQrViewModelTest" --tests "*PhysicalSessionsRepositoryTest" --tests "*OkHttpAuthRemoteDataSourceTest"` | N/A (no UI yet; optional Bruno request against local stack proves the wire contract) | revert PR2 (adds code nothing reaches yet; restores auth `problem()`) |
| 3 | ZXing render, brightness, FLAG_SECURE, `AccessQr` destination, `onShowQr` wiring | PR3 `feature/375-pr3-qr-render` (base PR2 branch) | `./gradlew :android:testDebugUnitTest --tests "*QrEncoderTest"` | emulator + real scanner (see 3.M) | revert PR3 (button disappears again via `onShowQr = null` path) |

Per-PR branch protocol (all PRs): open the PR with base shown below; while the parent is open the child base is the parent branch so the diff shows only the current unit. When the parent merges to `develop`, retarget the child (`gh pr edit <n> --base develop`); if the parent was squash-merged, first `git rebase --onto origin/develop <old-parent-tip> <child-branch>` and push with `--force-with-lease` only after explicit user approval. Every merge must leave `develop` green. Each PR body carries the Chain Context diagram with the current PR marked, per the `chained-pr` skill, and `Closes`/`Refs #375` per the board rule (only PR3 uses `Closes #375`; PR0-PR2 use `Refs #375`).

---

## PR0 - Platform floor  (`feature/375-pr0-android-platform-floor`, base `develop`)

Scope: ADR-0046, `minSdk = 26`, `API_BASE_URL` rename. Out of scope: any feature code; the change artifacts (they ship in PR-A, see Phase 0.2). Start: `develop`. End: Android builds on minSdk 26 and the old name is gone.
Specs: `android-platform-floor` (minSdk 26; Shared API base URL).

### Phase 0.1 Floor and rename

- [ ] 0.1 Check: `docs/adr/0046-android-min-sdk-26.md` and the `docs/adr/README.md` row 0046 are present and consistent (already written); fix wording only if wrong.
- [ ] 0.2 RED (config check): confirm `rg 'minSdk = 24|AUTH_API_BASE_URL' android/` matches in `android/build.gradle.kts` and `A/di/AndroidAuthModule.kt` (Scenario "Rename complete" fails today).
- [ ] 0.3 GREEN `android/build.gradle.kts`: `minSdk = 26`; rename both `buildConfigField` entries to `API_BASE_URL` (debug `http://10.0.2.2:8081`, release `https://api.mentadance.com`); leave `targetSdk = 36`; add no desugaring option/dependency (Scenario "Build configuration").
- [ ] 0.4 GREEN `A/di/AndroidAuthModule.kt`: `BuildConfig.AUTH_API_BASE_URL` -> `BuildConfig.API_BASE_URL`. Do not touch BFF/infra `AUTH_API_BASE_URL` env vars (unrelated).
- [ ] 0.5 `openspec/config.yaml` line 15: change `minSdk 24` to `minSdk 26 (ADR-0046)` (spec: "`openspec/config.yaml` MUST mention the floor").
- [ ] 0.6 Verify (Scenario "Rename complete"): `rg AUTH_API_BASE_URL android/` is empty; `rg 'desugar' android/` is empty.

### Phase 0.2 Change artifacts (PR-A, `feature/375-pr-a-sdd-artifacts`, base `develop`)

- [ ] 0.7 PR-A: commit `openspec/changes/android-my-sessions-qr/{proposal.md,design.md,tasks.md,specs/**}` alone, `Refs #375`, so no code PR carries ~670 lines of markdown. It is independent of PR0 (no shared files); tick-offs in later PRs touch only `tasks.md`.

### Phase 0.3 Gate and size

- [ ] 0.8 Gate: `./gradlew build :android:assembleDebugAndroidTest` and `./gradlew :android:lintDebug` green (Scenario "Build stays green").
- [ ] 0.9 Manual: install the debug build on an API 26 emulator, log in as `alumno@menta.dance` against `10.0.2.2:8081` (proves the rename kept auth working).
- [ ] 0.10 Size check (measured): `git diff --shortstat origin/develop...HEAD`. Forecast ~230 (ADR 115 + README 1 + `config.yaml` ~48 + code ~10 + diff context). Well under 800; no exception needed.

---

## PR1 - "Mis clases" list  (`feature/375-pr1-mis-clases`, base PR0 branch -> `develop` after PR0 merges)

Scope: domain/data/DI/ViewModel/screen, drawer `onNavigate`, strings; `MySessionsScreen(onShowQr = null)` so no dead button ships (A11). Start: PR0 applied. End: a student opens "Mis clases" from the drawer and sees list/empty/error; no QR button exists.
Specs: `android-my-sessions`, `android-auth-navigation` (delta).

### Phase 1.1 Data and domain (list)

- [x] 1.1 RED `T/data/repository/OkHttpPhysicalSessionsRepositoryTest.kt` (MockWebServer): GET `/api/v1/physical/sessions/me` path + `Accept: application/json, application/problem+json`; two sessions parsed in server order with `Instant` (Scenario "Sessions returned"); an already-started-today session is kept (Scenario "Session already started today"); `sessions: []` -> `Loaded(empty)`; extra fields incl. `assignmentType` tolerated (Scenario "Tolerant parsing"); malformed body / 500 / I/O failure / bad `Instant` -> `Unavailable`.
- [x] 1.2 GREEN create `A/domain/model/MySession.kt`, `A/domain/model/MySessionsResult.kt`, `A/domain/repository/PhysicalSessionsRepository.kt` (only `mySessions()` in this PR), `A/data/remote/PhysicalWireModels.kt` (`MySessionsResponseDto`, `MySessionDto`, internal `@Serializable`), `A/data/repository/OkHttpPhysicalSessionsRepository.kt` (blocking `execute()`, `HttpUrl` builder, no logging).
- [x] 1.3 Run `*DomainPurityTest` (Scenario "Domain purity"): `java.time` allowed, no `android.`/`androidx.` in domain.
- [x] 1.4 GREEN `A/di/PhysicalModule.kt`: `@Singleton PhysicalSessionsRepository = OkHttpPhysicalSessionsRepository(@ApiHttpClient client, BuildConfig.API_BASE_URL)` (glue; compile gate + manual).

### Phase 1.2 Presentation logic

- [x] 1.5 RED `T/presentation/mysessions/SessionTimeFormatterTest.kt`: `formatSessionTime(instant, fixed ZoneId, Locale es-AR)` yields the expected Spanish label (device-zone scenario in "Upcoming sessions list").
- [x] 1.6 GREEN `A/presentation/mysessions/SessionTimeFormatter.kt` (pure function).
- [x] 1.7 Create `T/testing/FakePhysicalSessionsRepository.kt` (scripted results, call counter, optional gate for in-flight tests); reuse `MainDispatcherRule`.
- [x] 1.8 RED `T/presentation/mysessions/MySessionsViewModelTest.kt`: Loading -> Loaded (Scenario "Loading then content"); Empty on empty list (Scenario "No sessions"); Error then retry issues a new request (Scenario "Network failure and retry"); each `refresh()` refetches and reflects `checkInOpen` false -> true (Scenario "Resume refreshes"); `refresh()` ignored while in flight; per-item `checkInOpen` exposed (Scenarios "Window open"/"Window closed", state level only); later refreshes keep content until the result arrives.
- [x] 1.9 GREEN `A/domain/usecase/GetMySessionsUseCase.kt` (thin, `@Inject`), `A/presentation/mysessions/MySessionsUiState.kt` (`Loading | Empty | Error | Loaded`), `A/presentation/mysessions/MySessionsViewModel.kt` (`refresh()`, no load in `init`).

### Phase 1.3 Screen and navigation (GREEN-only glue)

- [x] 1.10 `R/values/strings.xml`: add S1 strings (`menu_section_my_sessions` "Mis clases", `my_sessions_title`, `my_sessions_empty` "No tenés clases próximas con lugar confirmado", `my_sessions_error`, `common_retry`, `my_sessions_show_qr`, `my_sessions_check_in_closed`, `my_sessions_instructor`). Empty state has no purchase link.
- [x] 1.11 `A/presentation/mysessions/MySessionsScreen.kt`: stateful route (`LifecycleResumeEffect { refresh() }`) + stateless content; `MySessionsScreen(onShowQr: ((String) -> Unit)?)` shows "Mostrar QR" only when `checkInOpen && onShowQr != null`, else neutral text without minutes/hours.
- [x] 1.12 `A/presentation/navigation/Routes.kt`: `@Serializable data object MySessions`. `A/presentation/navigation/AuthenticatedScaffold.kt`: `StudentSection.MySessions`, param `onNavigate: (StudentSection) -> Unit`; drawer click closes drawer and calls `onNavigate` when `section != selected`; drawer lists exactly Home and "Mis clases".
- [x] 1.13 `A/presentation/navigation/MentaNavHost.kt`: map section -> route with `when`; `navigate(route) { popUpTo(Home); launchSingleTop = true }`; register the `MySessions` destination passing `onShowQr = null` (A11).

### Phase 1.4 Manual verification (run on API 37, the only installed image; API 26 not exercised; local stack `10.0.2.2:8081`, `alumno@menta.dance` / a student with confirmed capacity)

- [x] 1.M1 Drawer navigation: Home -> "Mis clases" shows the list and closes the drawer; "Mis clases" -> Home works; drawer lists exactly those two entries (Scenarios "Navigate to Mis clases", "Navigate back to Home", "Home contents").
- [x] 1.M2 Back stack: Home -> Mis clases -> Back returns to Home; Back on Home exits and never returns to login (Scenario "Back from home").
- [x] 1.M3 Refetch on resume (verified on API 37 emulator; the empty-state text was NOT seen on device because the seed student has 15 sessions, it is covered by `MySessionsViewModelTest`): send the app to background and back, confirm a new request (Scenario "Resume wiring"); empty state text exact; stop the API for the error state + "Reintentar".
- [x] 1.M4 Logout from "Mis clases" behaves as on Home (Scenario "Logout stays available"); no "Mostrar QR" button appears even for `checkInOpen: true` sessions.

### Phase 1.5 Gate and size

- [x] 1.14 Gate: focused command above, then `./gradlew build :android:assembleDebugAndroidTest` and `./gradlew :android:lintDebug`.
- [x] 1.15 Size check (measured): `git diff --shortstat origin/develop...HEAD` (PR0 is already merged): see PR body. Forecast ~650-700 vs 800. If over, the screen and nav glue (1.10-1.13) cannot be split off without shipping a dead screen; trim test duplication or ask for `size:exception`.

---

## PR2 - Access QR use case and renewal  (`feature/375-pr2-access-qr`, base PR1 branch -> `develop` after PR1 merges)

Scope: domain access models, POST adapter, ProblemJson extraction, `Clock`, `AccessQrViewModel`. No UI, no dependency changes. Start: PR1 applied. End: ViewModel fully tested; nothing user-visible changes.
Specs: `android-access-qr` (Credential custody, Renewal tied to visibility, Denial messages, Transient failure).

### Phase 2.1 Domain model

- [x] 2.1 RED `T/domain/model/AccessCredentialTest.kt`: `renewalDelay` normal 30 s (Scenario "Server hint wins"), near expiry uses `expiresAt - now - 5 s` (Scenario "Expiry wins"), already-past expiry -> 5 s floor (Scenario "Floor"); `isExpiredAt`; `toString` omits the value (Scenario "Masked toString").
- [x] 2.2 GREEN `A/domain/model/AccessCredential.kt` (masked `toString`), `A/domain/model/AccessDenial.kt` (`OUTSIDE_CHECK_IN_WINDOW, SESSION_CANCELLED, CAPACITY_ASSIGNMENT_REQUIRED, SESSION_NOT_FOUND, OTHER`), `A/domain/model/AccessQrResult.kt` (`Issued | Denied | Unavailable`). Run `*DomainPurityTest`.

### Phase 2.2 Data (access-qr endpoint)

- [x] 2.3 Guard: run `*OkHttpAuthRemoteDataSourceTest` green before the refactor (A3).
- [x] 2.4 RED extend `OkHttpPhysicalSessionsRepositoryTest`: POST `/api/v1/physical/sessions/{id}/access-qr` empty body + path segment encoding; 200 parsed to `Issued`; each 403 code -> its denial; 404 -> `SESSION_NOT_FOUND`; 400 -> `OTHER`; 401 / 408 / 429 / 5xx / I/O / malformed body -> `Unavailable`; non-problem Content-Type falls back to status mapping (Scenarios "Window closes while open", "Other denials").
- [x] 2.5 GREEN `A/data/remote/ProblemJson.kt` (`internal fun Response.problemCode(): String?`); `A/data/remote/OkHttpAuthRemoteDataSource.kt` `problem()` delegates to it (auth test stays green); `PhysicalWireModels.kt` adds `AccessQrResponseDto` with masked `toString`; `PhysicalSessionsRepository.kt` + `OkHttpPhysicalSessionsRepository.kt` add `issueAccessQr(sessionId)`; `FakePhysicalSessionsRepository.kt` gains scripted access results.
- [x] 2.6 GREEN `A/domain/usecase/IssueAccessQrUseCase.kt` (thin; covered by the ViewModel tests); `A/di/TimeModule.kt` providing `Clock.systemUTC()`.

### Phase 2.3 ViewModel (virtual time)

- [x] 2.7 Create `T/testing/TestClock.kt` (`instant()` derived from `testScheduler.currentTime` plus an origin).
- [x] 2.8 RED `T/presentation/accessqr/AccessQrViewModelTest.kt` part A (issue/renew/hide): issues on `onVisible` (Scenario "Open from list", VM level); renews exactly once at 30 s; `onHidden` cancels (no calls after `advanceTimeBy`) and drops the credential (Scenarios "Stop on hide", "Hidden clears state"); re-`onVisible` restarts; `Showing.toString` masks the value.
- [x] 2.9 GREEN `A/presentation/accessqr/AccessQrUiState.kt` (`Loading | Showing | Offline | Denied`), `A/presentation/accessqr/AccessQrViewModel.kt` (`@HiltViewModel(assistedFactory = AccessQrViewModel.Factory::class)`, constructor `(sessionId, useCase, clock, io)`, explicit `onVisible()/onHidden()` Job).
- [x] 2.10 RED part B (denials): each of the four denials and `OTHER` shows `Denied(reason)`, drops the QR immediately, schedules no retry (Scenarios "Window closes while open", "Other denials"). GREEN: denial branch in the loop.
- [x] 2.11 RED part C (transient): failure with 20 s of validity keeps the same QR after 10 s (Scenario "Failure before expiry"); after `expiresAt` -> `Offline` with retries at 2/4/8/16/30/30 s (Scenario "Failure past expiry"); success restores `Showing` (Scenario "Recovery"). GREEN: failure counter, backoff `min(2s*2^(n-1), 30s)`, `delay(min(b, expiresAt-now))` while valid.

### Phase 2.4 Optional / docs

- [x] 2.12 Optional: skipped. The request was ~24 lines (over the ~20-line cap) and the local stack was down, so it could not be run with `bru`; the wire contract is covered by `OkHttpPhysicalSessionsRepositoryTest` (MockWebServer).
- [x] 2.13 `docs/05-PHYSICAL-API.md`: checked, no contradiction, no change.

### Phase 2.5 Gate and size

- [x] 2.14 Gate: focused command above, then `./gradlew build :android:assembleDebugAndroidTest` and `./gradlew :android:lintDebug`.
- [x] 2.15 Size check (measured): `git diff --shortstat <PR1-branch>...HEAD`. Forecast ~600-650 vs 800.

---

## PR3 - QR rendering and window effects  (`feature/375-pr3-qr-render`, base PR2 branch -> `develop` after PR2 merges)

Scope: ZXing, Canvas, brightness, FLAG_SECURE, `AccessQr` destination, `onShowQr` wiring. Start: PR2 applied. End: the feature works end to end. Out of scope: Compose UI tests, offline QR, scanner side.
Specs: `android-access-qr` (QR destination, Local rendering, Brightness, Screenshot blocking).

### Phase 3.1 Library verification (do first, compile-only)

- [x] 3.1 Verify `hiltViewModel<AccessQrViewModel, AccessQrViewModel.Factory>(creationCallback = { it.create(id) })` compiles with `hilt-lifecycle-viewmodel-compose:1.4.0`. Fallback if the overload is missing: constructor takes `SavedStateHandle`, tests build `SavedStateHandle(mapOf("sessionId" to id))` (adjust PR2's VM signature in a follow-up commit here). Result: compiles as-is on `hilt-lifecycle-viewmodel-compose:1.4.0` (no fallback needed).
- [x] 3.2 Verify `androidx.activity.compose.LocalActivity` exists in activity-compose 1.10.1. Fallback: unwrap `LocalContext.current` through `ContextWrapper` to the `Activity`. Result: `androidx.activity.compose.LocalActivity` exists in 1.10.1 (no fallback needed).

### Phase 3.2 Encoding (pure JVM)

- [x] 3.3 `gradle/libs.versions.toml` + `android/build.gradle.kts`: add `com.google.zxing:core:3.5.4` as `implementation`.
- [x] 3.4 RED `T/presentation/accessqr/QrEncoderTest.kt`: encode -> render `QrMatrix` + 4-module quiet zone into `RGBLuminanceSource` (4 px/module) -> `HybridBinarizer` -> `QRCodeReader().decode().text == input` for the placeholder format (Scenario "Round-trip") and a 512-char token (Scenario "Long credential"); square size; `QrMatrix.toString` is `QrMatrix(size=N)`; `qrCellSize(minPx, n)` = `floor(minPx / (n + 8))` and centering offset.
- [x] 3.5 GREEN `A/presentation/accessqr/QrMatrix.kt`, `A/presentation/accessqr/QrEncoder.kt` (`QRCodeWriter`, ECC M, margin 0, UTF-8; `WriterException` surfaced as an error value), `qrCellSize` helper.

### Phase 3.3 Screen glue (GREEN-only, manual verification)

- [x] 3.6 `A/presentation/accessqr/QrCode.kt`: Canvas, white background, 4-module quiet zone, black modules, ignores dark theme (Scenario "Drawing and theme").
- [x] 3.7 `A/presentation/accessqr/WindowEffects.kt`: `SecureScreenEffect` (add `FLAG_SECURE`, clear on dispose only if it was not set before) and `BrightnessBoostEffect` (save `screenBrightness`, set `BRIGHTNESS_OVERRIDE_FULL`, `FLAG_KEEP_SCREEN_ON`; restore on dispose; active only while `Showing`).
- [x] 3.8 `R/values/strings.xml`: add S3 strings (`access_qr_title`, `access_qr_hint`, `access_qr_loading`, `access_qr_offline` "Sin conexión. Reintentamos automáticamente.", the four `access_qr_denied_*`, `access_qr_error`, `access_qr_back`).
- [x] 3.9 `A/presentation/accessqr/AccessQrScreen.kt`: `AccessQrRoute` (`hiltViewModel` with `creationCallback`, `LifecycleStartEffect` -> `onVisible`/`onHidden`), `remember(credential.value) { QrEncoder.encode(...) }`, own `Scaffold` with "Volver" `TextButton` -> `popBackStack()`; both effects scoped to the whole destination.
- [x] 3.10 `A/presentation/navigation/Routes.kt`: `@Serializable data class AccessQr(val sessionId: String)`; `MentaNavHost.kt`: register the destination and pass `onShowQr = { navController.navigate(AccessQr(it)) }` to `MySessionsScreen` (replaces `null`).

### Phase 3.4 Manual verification (emulator API 26 + one newer device/emulator, local stack `10.0.2.2:8081`, student with confirmed capacity and an open check-in window)

- [ ] 3.M1 Open from list: tap "Mostrar QR" on a `checkInOpen` session shows the QR (Scenario "Open from list"); a real scanner reads it, in dark theme too (Scenario "Drawing and theme").
- [ ] 3.M2 Brightness rises to max while visible and is restored on Back (Scenario "Boost and restore").
- [ ] 3.M3 Screenshot is blocked and the Recents thumbnail is blank (Scenario "Secure window"); `FLAG_SECURE` is cleared after leaving the screen.
- [ ] 3.M4 Renewal shows no visible flash; airplane mode shows "Sin conexión" only after expiry and the QR returns on reconnect (Scenarios "Failure past expiry", "Recovery"); backgrounding the app clears the QR.
- [ ] 3.M5 Close the window server-side (or use a cancelled session) and confirm the denial message with no QR; logout from the list still works.

### Phase 3.5 Gate, docs and size

- [x] 3.11 Gate: `./gradlew build :android:assembleDebugAndroidTest` and `./gradlew :android:lintDebug`. Result: `./gradlew :android:build :android:assembleDebugAndroidTest :android:lintDebug` BUILD SUCCESSFUL (185 unit tests, 0 failures; scoped to `:android` because root `build` fails on backend Testcontainers when Docker is down).
- [x] 3.12 Size check (measured): `git diff --shortstat <PR2-branch>...HEAD`. Forecast ~350-400 vs 800. Result: `git diff --shortstat origin/develop` (untracked included): 12 files, +375/-15 = 390 vs 800.
- [x] 3.13 Final pass: tick all tasks, confirm `docs/ADR.md` index and `docs/adr/README.md` list ADR-0046, and that `rg AUTH_API_BASE_URL android/` is empty. Result: `rg AUTH_API_BASE_URL android/` is empty; ADR-0046 is in `docs/adr/README.md` (row 0046); `docs/ADR.md` does not exist, `docs/10-ADRS.md` only points to the README index.

---

## Optional follow-ups (non-blocking, not in the chain)

- [ ] F.1 `api/openapi/physical-v1.yaml`: `assignmentType` is documented as "pending" although the schema has it; align the description.
- [ ] F.2 `docs/16-CICD-PIPELINE.md`: says "5 tests" for Android; update to the real count after PR3 (or drop the number).

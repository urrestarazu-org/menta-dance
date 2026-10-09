```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:b96853d002ba3ef9ddd754e2a2dfc25a3df2f47d15d310d69df52501420d5765
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 16/16
scenarios: 27/27
test_command: "./gradlew :android:testDebugUnitTest --rerun --no-build-cache :android:lint :android:assembleDebug :android:assembleDebugAndroidTest"
test_exit_code: 0
test_output_hash: sha256:e4eceb665d8a9aabfa3fbb5db3bcdac3a5479440ccff8f2a5b42fe0a37df6a9c
build_command: "./gradlew :android:testDebugUnitTest --rerun --no-build-cache :android:lint :android:assembleDebug :android:assembleDebugAndroidTest"
build_exit_code: 0
build_output_hash: sha256:23b762b578a20475e8787b85661a16577ac1ec5c9622fcb7db45d8e59e65da49
```

# Verification Report: android-login-navigation (#374)

Mode: hybrid (Engram + openspec). Strict TDD: active. Base: origin/develop 733c14d (S1-S5 merged).

## Completeness
Tasks: 5 slices, all checked (S1 1.1-1.5, S2 2.1-2.6, S3 3.1-3.6, S4 4.1-4.8, S5 5.1-5.7); code state matches. 0 incomplete.

## Execution evidence
- Gate exit 0 (BUILD SUCCESSFUL). Unit tests: 125 run, 0 failed, 0 errors, 0 skipped across 12 classes (JUnit XML mtime 13:02:27, i.e. rerun in this session).
- Lint: 0 errors, 14 warnings (GradleDependency 5, NewerVersionAvailable 4, UseKtx 2, OldTargetApi, AndroidGradlePluginVersion, MissingApplicationIcon). Apply-progress said 6; the report file mtime (12:43) predates this run, so lint outputs were served from Gradle up-to-date state.
- assembleDebug and assembleDebugAndroidTest: APKs present (debug 13.1M, androidTest 386.5K). Most tasks were UP-TO-DATE ("2 executed, 96 up-to-date").
- No Android coverage tool (JaCoCo) configured for :android: coverage skipped.
- androidTest is compiled only, not run (ADR-0036). No emulator run.

## Spec compliance (16 requirements, 27 scenarios)
Legend: T = covered by a passing test; M = manual-only (not executed).

| Requirement / scenario | Evidence | Status |
|---|---|---|
| Login screen: success as student | LoginViewModelTest student_login_emits_the_signed_in_event, loading_is_true_while_the_request_runs; screen M | T (VM) + M (render) |
| Login screen: empty fields | LoginViewModelTest blank_email/blank_password (no request) | T |
| Failure msgs: not-activated | AuthSessionRepositoryTest login_403_*; LoginViewModelTest each_failure_maps_to_its_login_error (NotActivated == InvalidCredentials) | T |
| Failure msgs: locked / 429 / 503 | AuthSessionRepositoryTest login_423/429(+Retry-After)/503/network; VM mapping; Spanish copy in strings.xml (plural with %d, no support link) not asserted by a test | T (mapping) + M (copy) |
| Failure msgs: INACTIVE/SUSPENDED 401 | login_401_is_invalid_credentials + VM mapping | T |
| Cold-start resume: refresh succeeds | RefreshSessionUseCaseTest student_session_is_resumed; SessionViewModelTest student_session_resumes_to_signed_in, gate_starts_on_splash; splash render M | T + M |
| Cold-start resume: no token | refresh_without_a_stored_token_is_signed_out; SessionViewModelTest no_stored_token_* | T |
| Refresh failure: token rejected | refresh_401/COMPROMISED/400_invalid_auth_request wipe; SessionViewModelTest rejected_token_* | T |
| Refresh failure: offline | refresh_offline/503 keep token; SessionViewModelTest unavailable_api_*keeps token | T (real relaunch M) |
| Home contents | HomeScreen/AuthenticatedScaffold inspected | M (see W1) |
| Back from home | MentaNavHost replaceAll popUpTo inclusive + launchSingleTop | M |
| Logout online | AuthSessionRepositoryLogoutAndExpiryTest (Bearer+refresh, 401 retry), SessionViewModelTest logout_revokes_* | T |
| Logout offline | logout_clears_local_tokens_when_remote_revocation_throws; SessionViewModelTest logout_while_offline_* | T |
| Role gate: instructor | LoginUseCaseTest instructor_login_revokes_*; RefreshSessionUseCaseTest non_student_*; SessionViewModelTest non_student_* | T |
| Role gate: missing claim | JwtRoleReaderTest (8), LoginUseCaseTest/RefreshSessionUseCaseTest missing_role_claim_* | T |
| Session expiry: mid-use | AuthenticatedClientTest persistent_401_*; Logout/ExpiryTest; SessionViewModelTest session_expiry_routes_* | T (no screen issues authenticated calls) |
| Placeholder removal | rg for 5 symbols plus MainScreen/MainUiState/AndroidSmokeTest/TODO: 0 matches; compile+tests pass | T/static |
| Secrecy: observable state | AuthSessionRepositoryTest session_state_and_unexpected_failure_never_render_a_token; renewal toString; LoginUiState.toString; no Log/println/logging interceptor in src/main; NetworkModuleTest no interceptors on auth client | T |
| Layering | DomainPurityTest 2/2 pass (non-vacuous guard + no android imports in domain) | T |
| HTTP: Bearer injection | AuthenticatedClientTest authenticated_requests_carry_the_bearer_token; login/refresh no Bearer (2 tests) | T |
| HTTP: 401 then success | a_401_refreshes_once_and_retries_once | T |
| HTTP: other errors | a_403/a_500 without refresh | T |
| HTTP: two parallel 401s | two_parallel_401s_make_exactly_one_refresh (+ concurrency tests with 2 and 8 threads) | T |
| HTTP: token already renewed | a_newer_token_already_held_is_reused_without_a_remote_refresh | T |
| HTTP: retry still 401 | a_persistent_401_after_a_successful_refresh_wipes_signals_and_does_not_retry_again | T |
| HTTP: refresh rejected | a_compromised_refresh_token_wipes_signals | T |
| HTTP: offline during refresh | an_unavailable_refresh_throws_an_io_exception_keeps_the_token | T |
| HTTP: storage failure after rotation | refresh_clears_local_state_when_rotated_refresh_cannot_be_persisted; persist_failure_fails_closed | T |

All 27 scenarios have either a passing test or an explicit manual-only classification agreed in tasks.md. No UNTESTED or FAILING scenario outside the manual-only set.

## Issue #374 ACs
1 Login screen: VM unit-tested, Compose render manual. 2 Auto-resume + fallback to login: unit. 3 Navigation Compose + home + menu: structure present, render manual. 4 Logout: unit. 5 Shared HTTP client with one retry (OkHttp, justified in design ADR-1): MockWebServer. 6 Non-student notice: unit. 7 Placeholder removed: verified by search. Checkboxes in the issue body are still unticked (tracker bookkeeping).

## Decisions D1-D4
D1 non-student: notice + revoke + wipe, home never shown (gateStudent calls logout, SessionGate.SignedOut(StudentsOnly)): OK. D2 cold start splash; offline keeps token with notice: OK (unit). D3 menu lists only existing sections (StudentSection has only Home) + logout: OK, but see W1. D4 persistent 401 after one retry wipes and emits sessionExpired, routed to SignedOut(SessionExpired): OK (unit).

## TDD compliance
TDD Cycle Evidence table: absent. The apply-progress artifact is a prose summary of S5 only (upserted per slice, 5 revisions). Test files exist for every behaviour task (tasks 1.2, 2.1, 2.3, 2.5, 3.1, 3.3, 3.5, 4.2, 4.5, 5.1, 5.3) and pass now; RED-first order cannot be verified after squash merges. Assertion audit (sampled names and key assertions): no tautologies or ghost loops spotted; DomainPurityTest has an anti-vacuous guard. Mock-ratio and per-assertion audit not done exhaustively.

## Issues
CRITICAL: none.
WARNING:
- W1 Spec "Home contents" says greeting + logout and no other section entry; the drawer also lists an "Inicio" entry (StudentSection.Home). It matches design D3 but is literally one more entry than the spec text. Reconcile spec or design.
- W2 Strict TDD evidence table missing in apply-progress (see above); the strict module would call it CRITICAL, downgraded because runtime evidence for every scenario exists and the artifact is a single upserted summary. Orchestrator may overrule.
- W3 Spanish copy (429 with N seconds, 423 without support link, notices) is only verified by reading strings.xml; the LoginError-to-string mapping in LoginScreen is not unit-tested.
- W4 Lint: 14 warnings vs 6 reported in apply-progress; lint ran mostly up-to-date, so the count is from a 12:43 report.
SUGGESTION:
- S1 Tick the 7 AC checkboxes in #374 when closing.
- S2 tasks.md has the guard literal "stacked-to-main" while the real base was develop (already noted in the file).
- S3 OldTargetApi warning: targetSdk 36 while compileSdk is 37.

## Honest limits
No emulator: Compose screens, splash, drawer, Back stack, relaunch and offline relaunch, real Keystore wipe are unverified. androidTest not run. Hilt graph validated only by KSP at assembleDebug. Assemble/lint were largely up-to-date. Unverified: RED-before-GREEN ordering, exhaustive assertion-quality audit, real API behaviour (Bruno/stack).

## Verdict: PASS WITH WARNINGS

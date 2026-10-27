# Proposal: Android "Mis clases" and access QR (#375)

## Intent

Students cannot see their upcoming in-person sessions or present an access QR from the app; check-in (US-PHYSICAL-001) requires a credential the backend already issues. Success: a student with confirmed capacity opens "Mis clases", taps "Mostrar QR" while check-in is open, and shows a locally drawn, self-renewing QR that never touches disk or logs.

## Scope

### In Scope
- **S1** "Mis clases" list (`GET /sessions/me`): domain/data/DI/ViewModel/screen, drawer `onNavigate` in `AuthenticatedScaffold`, Spanish strings, re-fetch on every resume; `minSdk = 26` (ADR-0046) plus `openspec/config.yaml` mention; rename `AUTH_API_BASE_URL` to `API_BASE_URL`.
- **S2** Access QR (`POST /sessions/{id}/access-qr`): use case, renewal loop, mapping of 403 `OUTSIDE_CHECK_IN_WINDOW` / `SESSION_CANCELLED` / `CAPACITY_ASSIGNMENT_REQUIRED` and 404 `SESSION_NOT_FOUND`; optional Bruno `Issue Access QR.bru` if cheap.
- **S3** ZXing core 3.5.4 encoding to `QrMatrix`, Compose Canvas drawing, brightness boost, `FLAG_SECURE`, `AccessQr(sessionId)` destination glue.

### Out of Scope
- Backend/OpenAPI changes (none needed).
- Compose UI tests, coverage gate, offline QR, scanner side.
- Hardcoded window-length copy.

## Capabilities

### New Capabilities
- `android-my-sessions`: upcoming sessions list, empty/error states, check-in affordance gated by `checkInOpen`.
- `android-access-qr`: credential issuance, in-memory custody, renewal before `exp`, error messages, local rendering, brightness, screenshot blocking.
- `android-platform-floor`: minSdk 26 and shared `API_BASE_URL`.

### Modified Capabilities
- `android-auth-navigation`: drawer lists Home and "Mis clases" and navigates (was Home only).

## Approach

Clean Architecture slices (`presentation/domain/data/di`), first consumer of `@ApiHttpClient`. Renewal: ViewModel loop tied to visibility (`LifecycleStartEffect`), `delay = min(refreshAfterSeconds, timeToExp - margin)` with ~5s floor, injected `java.time.Clock`. Credential stays in `UiState` only (no `SavedStateHandle`/`rememberSaveable`), masked in `toString`. Encoding is pure and JVM round-trip tested; Canvas only draws. Strict TDD with JVM unit tests and fakes.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Device clock skew vs `expiresAt` | Med | Delay floor; server is truth; re-request on expiry |
| QR leak via screenshots/recents | Med | `FLAG_SECURE`; memory-only, masked `toString` |
| Shared scaffold change untested (no UI tests) | Med | Minimal `onNavigate` param; manual check per PR |
| Process death loses credential | Low | Intended; re-request on return |
| Window closes while screen open | Med | Server 403 mapping; re-fetch on resume |

## Rollback Plan

Each slice merges as its own PR; revert in reverse order (S3, S2, S1). Reverting S1 restores minSdk 24 and the old BuildConfig name. No data or backend state involved.

## Dependencies

- `com.google.zxing:core:3.5.4`; ADR-0046 (uncommitted); both backend endpoints already implemented (`api/openapi/physical-v1.yaml`).

## Success Criteria

- [ ] All five #375 acceptance criteria pass.
- [ ] Credential never persisted or logged (`toString` test).
- [ ] Renewal timing covered with virtual-time tests.
- [ ] Each PR at most 800 changed lines (forecast: S1 ~550-700, S2 ~500-600, S3 ~250-350; total ~1,300-1,650).
- [ ] `./gradlew build :android:assembleDebugAndroidTest` green.

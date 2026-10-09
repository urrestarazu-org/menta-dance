# Archive Report: android-login-navigation (#374)

**Archived to**: `openspec/changes/archive/2026-10-09-android-login-navigation/`
**Date**: 2026-10-09 | **Issue**: #374 | **Mode**: hybrid (OpenSpec + Engram)
**Outcome**: intentional archive with warnings (0 CRITICAL).

## Verification

Verdict **PASS WITH WARNINGS** (0 CRITICAL, 4 WARNING), per `verify-report.md` (Engram #1749) at verification time: 125 unit tests, 0 failures; lint 0 errors. 16 requirements / 27 scenarios: each has a passing test or an explicit manual-only classification. `tasks.md`: 32/32 `[x]`, 0 unchecked.

No `reviewGate` was supplied in the launch context; archive proceeded under ordinary repository policy.

## Specs synced

Both domains had no main spec, so each delta was copied mechanically as a full spec (`diff` empty).

| Domain | Action | Details |
|---|---|---|
| `android-auth-navigation` | Created | 10 requirements: login screen, failure messages, cold-start resume and refresh failure, authenticated home and navigation, logout, student role gate, session expiry routing, placeholder removal, secrecy and layering. |
| `android-authenticated-http` | Created | 6 requirements: Bearer injection, single refresh-and-retry, single-flight refresh, persistent 401 and refresh rejection, refresh unavailable, token secrecy. |

## Verify warnings (final state)

- **W1 Resolved before archive.** The "Home contents" scenario said greeting + logout with no other section entry, while the drawer lists the Home entry (design D3). Per the launch prompt, the spec scenario was reconciled with D3 ("the menu lists only the Home entry"); the archived spec text confirms it. Verify-report still shows the pre-reconciliation wording as history.
- **W2 Open (accepted).** The Strict TDD evidence table is missing in apply-progress; RED-before-GREEN ordering cannot be verified after squash merges. Downgraded from CRITICAL because runtime evidence exists for every scenario. Follow-up: keep a per-slice TDD table in future changes.
- **W3 Open (accepted).** Spanish copy (429 with N seconds, 423 without support link, notices) was only verified by reading `strings.xml`; the LoginError-to-string mapping in `LoginScreen` has no unit test.
- **W4 Open (accepted).** Lint reported 14 warnings versus 6 in apply-progress; lint ran mostly up-to-date, so the count comes from an earlier report.

Suggestions: S1 tick the 7 AC checkboxes in #374 on close; S2 the `stacked-to-main` guard literal in `tasks.md` (real base was `develop`); S3 OldTargetApi warning (targetSdk 36, compileSdk 37).

## Honest limits (not verified)

- No emulator run. Compose screens, splash, drawer, Back stack, relaunch, offline relaunch and a real Keystore wipe on device are unverified. `androidTest` was not run.
- Manual-only scenarios not executed: "Home contents" and "Back from home" (plus the Compose render of login/splash).
- Hilt graph validated only by KSP at `assembleDebug`; assemble/lint were largely up-to-date.
- Not verified: RED-before-GREEN ordering, exhaustive assertion-quality audit, real API behavior (Bruno/stack).

## Mechanical archive evidence

- Spec copies: `diff` of each delta vs `openspec/specs/{domain}/spec.md` returned "Files are identical".
- Folder move: `git mv` (fell back to `mv` if untracked) then `diff -r` of a pre-move snapshot vs the archived folder: empty output. Source folder no longer exists.

## Engram lineage

proposal #1742, spec #1744, design #1745, tasks #1747, apply-progress #1748, verify-report #1749, decisions #1741, design-decisions #1746, archive-report saved under `sdd/android-login-navigation/archive-report`.

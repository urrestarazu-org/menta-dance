# Archive Report: android-my-sessions-qr

**Change**: android-my-sessions-qr (issue #375; follow-up work tracked in #670)
**Archived on**: 2026-10-29
**Archived to**: `openspec/changes/archive/2026-10-29-android-my-sessions-qr/`
**Artifact store**: hybrid (OpenSpec files + Engram topic `sdd/android-my-sessions-qr/archive-report`)
**Final state basis**: the state at close, per the Final-State Authority hierarchy. Native review authority: none applicable (`reviewGate` absent in `gentle-ai sdd-status`; no review discovered for this candidate). Task artifact: 62/62 checked. Launch-prompt final-state facts: rank 3. Intermediate snapshots (`verify-report`, `apply-progress`): rank 4, cited with attribution only.

## Gates at Archive Time

| Gate | Result | Evidence |
|------|--------|----------|
| Native review receipt | Not applicable | `gentle-ai sdd-status ... --json` has no `reviewGate` key; no `reviewOffer` either. Proceeds under ordinary repository policy. |
| Task completion | PASS | `tasks.md`: 0 unchecked `- [ ]`, 62 checked `- [x]`. No stale-checkbox reconciliation was needed or performed. |
| CRITICAL findings | PASS | Latest `verify-report` (Engram #1813, `evidence_revision` sha256:0d0efa04...): `verdict: pass_with_warnings`, `blockers: 0`, `critical_findings: 0`. |
| Requirements / scenarios | 18/18, 36/36 | Per `verify-report` #1813, at verification time. |
| Gate status | `archive: ready` | `gentle-ai sdd-status android-my-sessions-qr --cwd . --json`. |

Note on verification history: the first verify run returned FAIL (one CRITICAL: no "TDD Cycle Evidence" table in `apply-progress`). That table was appended to Engram `apply-progress` (#1811), and verify was re-run to PASS WITH WARNINGS. The CRITICAL is resolved; the final verdict has 0 CRITICAL.

## Observation IDs (traceability)

Read in full with `mem_get_observation` for this archive:

| Artifact | Engram ID | Topic key | Used for |
|----------|-----------|-----------|----------|
| proposal | #1807 | `sdd/android-my-sessions-qr/proposal` | Context and rollback plan |
| spec | #1808 | `sdd/android-my-sessions-qr/spec` | Condensed mirror; authoritative delta text was taken from the on-disk delta files |
| design | #1809 | `sdd/android-my-sessions-qr/design` | Context (D1-D9, A1-A11) |
| tasks | #1810 | `sdd/android-my-sessions-qr/tasks` | Task structure; completion taken from on-disk `tasks.md` |
| verify-report | #1813 | `sdd/android-my-sessions-qr/verify-report` | Verdict, warnings, compliance matrix (intermediate snapshot) |

Referenced but not read by this phase: `apply-progress` #1811 (cited through #1813 only).

Filesystem sources (authoritative for content): `proposal.md`, `design.md`, `tasks.md`, `specs/*/spec.md` (four deltas), `verify-report.md` (untracked before archive; its `evidence_revision` and verdict match Engram #1813).

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| `android-my-sessions` | Created | Full spec copied mechanically (7 requirements per the file; the condensed Engram mirror #1808 says 6, the file is authoritative and matches the 18-requirement total in #1813). |
| `android-access-qr` | Created | Full spec copied mechanically (8 requirements). |
| `android-platform-floor` | Created | Full spec copied mechanically (2 requirements). Declares `minSdk 29`, consistent with ADR-0047. |
| `android-auth-navigation` | Updated | 1 requirement MODIFIED: "Authenticated home and navigation (AC3)". 0 added, 0 removed. The other 9 requirements are preserved unchanged (10 requirements before and after). |

MODIFIED merge detail: the delta block replaced the old requirement block (old lines 78-90) with its 28 lines. The line `(Previously: home showed a greeting and logout only, ...)` from the delta was NOT carried into the main spec. Main specs carry no such history annotations; this is the only deviation from verbatim delta text. Recorded as a deliberate choice, not a silent omission.

Mechanical readbacks (all empty = pass):

```
diff -r openspec/changes/android-my-sessions-qr/specs/android-my-sessions/spec.md openspec/specs/android-my-sessions/spec.md          -> (empty)
diff -r openspec/changes/android-my-sessions-qr/specs/android-access-qr/spec.md openspec/specs/android-access-qr/spec.md          -> (empty)
diff -r openspec/changes/android-my-sessions-qr/specs/android-platform-floor/spec.md openspec/specs/android-platform-floor/spec.md  -> (empty)
head -n 77 (old main) vs head -n 77 (merged)                  -> (empty)
tail -n +91 (old main) vs tail -n +106 (merged)                -> (empty)
merged lines 78-105 vs delta lines 5-7 + 9-33                  -> (empty)
```

## Archive Move and Readback

- Snapshot taken before the move (outside the repository, in the session scratch directory).
- Tracked artifacts moved with `git mv`: `proposal.md`, `design.md`, `tasks.md`, `specs/`.
- Untracked `verify-report.md` moved with plain `mv`.
- `.gentle-ai-instance` intentionally NOT moved (tool-state file, never committed). It remains in `openspec/changes/android-my-sessions-qr/`.
- Readback: `diff -r -x .gentle-ai-instance <snapshot> openspec/changes/archive/2026-10-29-android-my-sessions-qr` returned empty output. The unfiltered diff differed only by `Only in <snapshot>: .gentle-ai-instance`, which is the intentional exclusion.

## Final-State Facts (launch prompt, rank 3)

Corroborated where noted with read-only `gh` and `git` checks at archive time.

| Fact | Status |
|------|--------|
| PR #663 (artifacts) merged 2026-10-27 | Corroborated by `gh` (base develop). |
| PR #664 (PR0, platform floor) merged 2026-10-27 | Corroborated by `gh`; commit `3073f10` in history. |
| PR #665 (PR1, Mis clases) merged 2026-10-28 | Corroborated by `gh`. |
| PR #666 (PR2, access QR use case) merged 2026-10-28 | Corroborated by `gh`. |
| PR #667 (PR3, QR render) merged 2026-10-28; closes #375 | Corroborated by `gh` (`closingIssuesReferences` = 375). |
| PR #669 merged 2026-10-29 (commit `9b061f4`); closes issue #668 | Corroborated by `gh` (#668 is an issue, not a PR; the launch prompt's "#668/#669" pairing is the issue and its fix PR). |
| minSdk is 29, not 26; ADR-0047 replaces ADR-0046 | Corroborated: `android/build.gradle.kts:16` `minSdk = 29`; `docs/adr/0047-android-min-sdk-29.md` present; `docs/adr/0046-android-min-sdk-26.md` present (superseded). |
| All 62 tasks ticked; F.1/F.2 deferred without checkbox | Corroborated: 0 unchecked, 62 checked in `tasks.md`. |
| Task 0.9 (API 26 run) not run; replaced by API 29 emulator run 2026-10-29 | Launch prompt (rank 3). Verify #1813 records the same, as a warning. |
| Manual 3.M1-3.M5 and 1.M1-1.M4 passed 2026-10-29 (API 37; API 29 for login, list, QR scan, SECURE flag, brightness) | Launch prompt (rank 3). Verify #1813 records these as self-reported manual evidence it did not re-run. |
| 185/185 Android unit tests; `:android:build :android:assembleDebugAndroidTest :android:lintDebug` green on 9b061f4 | Launch prompt (rank 3), consistent with verify #1813 (fresh run at verify time). Not re-run by archive. |
| CI for #669: 7/7 checks passed | Launch prompt (rank 3). Not re-checked by archive. |
| Edit authority for root `/` granted 2026-10-29; dies with archive | Launch prompt. `gentle-ai sdd-status` still lists `/` in `allowedEditRoots`; all archive operations were confined to the repository root. |

## Contradictions and Their Resolution

- `proposal.md` and `design.md` say `minSdk 26` / ADR-0046. Both are history at proposal and design time and are intentionally not rewritten. The main spec `android-platform-floor` says 29 and cites ADR-0047. Resolved in favor of the final state (launch prompt plus build file). Not a live contradiction.
- The verify report's WARNING 3 and the "API 26 not run" note describe the state at verification time. The final state is API 29 (ADR-0047, #669). Reported as superseded, with attribution to #1813 and the launch prompt.
- No unrankable contradictions remain.

## Carried Non-Blocking Limitations (at verification time, per #1813)

These are reported as documented limitations at close, not as pending work. Attribution: `verify-report` #1813, verified 2026-10-29.

1. Dark theme never exercised; the app never goes dark (`MentaDanceTheme` ignores `isSystemInDarkTheme()`, pre-existing, palette #509).
2. API 26 never run; the floor moved to 29 (ADR-0047), and an API 29 emulator run was done instead.
3. TDD RED observed and logged for only 1 of 7 test groups (`QrEncoderTest`); the apply-progress TDD table is labeled RECONSTRUCTED.
4. Three scenarios counted compliant with indirect evidence: "No sessions" (exact text not asserted by a test), "Window closed" (text verified by reading `strings.xml`), "Drawing and theme" (dark-theme limitation above).
5. Unrecoverable 401 maps to `Unavailable`, so the list may flash its error state before the login redirect (SUGGESTION in #1813; no test covers this).
6. Brightness boost applies only in the `Showing` state; the spec wording says "while the QR screen is visible" (documented design deviation).
7. Follow-ups F.1 (`physical-v1.yaml` `assignmentType` description) and F.2 (`docs/16-CICD-PIPELINE.md` Android test count) are deferred and carry no checkbox.
8. Build-green scenario was verified scoped to `:android` and by CI on #669; root `./gradlew build` cannot pass locally without Docker (backend Testcontainers).

## Repository References to the Old Path

Search: `git grep -n -F 'openspec/changes/android-my-sessions-qr'` and `git grep -n -F 'android-my-sessions-qr'` (tracked files; build directories are untracked and excluded).

- Only remaining references are inside this archived change, as historical records:
  - `tasks.md` line 28: PR-A row names the old folder as its diff scope.
  - `tasks.md` line 54: task 0.7 describes committing the old folder for PR-A.
  - `verify-report.md` line 19: plain change name, not a path.
- No link was fixed. Archived artifacts are an audit trail and are not edited; these lines describe what was true when PR-A was made.
- No references were found in `docs/`, ADRs, `README`, or other `openspec/` files. No docs or ADR were touched.

## Files

Created:
- `openspec/specs/android-my-sessions/spec.md`
- `openspec/specs/android-access-qr/spec.md`
- `openspec/specs/android-platform-floor/spec.md`
- `openspec/changes/archive/2026-10-29-android-my-sessions-qr/archive-report.md` (this file)

Modified:
- `openspec/specs/android-auth-navigation/spec.md` (1 requirement MODIFIED)

Moved (`git mv` or `mv`):
- `openspec/changes/android-my-sessions-qr/{proposal.md, design.md, tasks.md, specs/}` to `openspec/changes/archive/2026-10-29-android-my-sessions-qr/` (tracked, `git mv`)
- `openspec/changes/android-my-sessions-qr/verify-report.md` to the same archive folder (untracked, `mv`)

Left in place:
- `openspec/changes/android-my-sessions-qr/.gentle-ai-instance` (tool-state file, untracked, never staged or committed). The directory `openspec/changes/android-my-sessions-qr/` remains only because of this file.

Staged (`git add`, not committed): the archive folder, the four main-spec locations.

## Outcome

Change archived. Main specs reflect the final behavior. Nothing committed or pushed.

# Archive Report: android-attendance-history

**Change**: android-attendance-history (issue #376; backend prerequisite #673; follow-up tracked in #680)
**Archived on**: 2026-11-01
**Archived to**: `openspec/changes/archive/2026-11-01-android-attendance-history/`
**Artifact store**: hybrid (OpenSpec files + Engram topic `sdd/android-attendance-history/archive-report`)
**Final state basis**: the state at close, per the Final-State Authority hierarchy. Native review authority: none applicable. The native SDD ledger does not exist in gentle-ai 4.x (no `reviewGate`, no `sdd-status`); the change is archived by hand under ordinary repository policy. No `gentle-ai sdd-*` command was run. Task artifact: on-disk `tasks.md` (rank 2), 63/63 checked after this archive. Launch-prompt final-state facts: rank 3. Intermediate snapshots (`verify-report`, `apply-progress`, and the Engram copies of tasks/verify): rank 4, cited with attribution only.

## Gates at Archive Time

| Gate | Result | Evidence |
|------|--------|----------|
| Native review receipt | Not applicable | No native ledger in gentle-ai 4.x. Archived under ordinary repository policy. |
| Task completion | PASS | On-disk `tasks.md` before this archive: 61 checked, 2 unchecked (F.3, F.4). Both are the archive's own tasks, completed by this phase (F.3 with the archive paths, F.4 by this archive PR). No implementation task unchecked, no stale-checkbox reconciliation needed. |
| CRITICAL findings | PASS | `verify-report.md` (on disk; Engram #1827 mirrors it): `verdict: pass_with_warnings`, `blockers: 0`, `critical_findings: 0`. |
| Requirements / scenarios | 11/11, 26/34 COMPLIANT + 8 PARTIAL, 0 failing, 0 untested | `verify-report.md` at verification time (2026-11-01). |
| Gate status | Archive proceeds | Launch-prompt authorization; user authorized edits under `openspec/`. |

## Observation IDs (traceability)

Read with `mem_search` + `mem_get_observation` for this archive. Engram copies carry stale wording (see "Final-State Corrections"); they were used for traceability only, never as content source.

| Artifact | Engram ID | Topic key | Used for |
|----------|-----------|-----------|----------|
| proposal | #1817 | `sdd/android-attendance-history/proposal` | Context (scope, PR chain, rollback). Not a content source. |
| spec | #1818 | `sdd/android-attendance-history/spec` | Condensed mirror only. Its counts (8 requirements, 17 scenarios) and row wording are stale; the on-disk delta files are authoritative. |
| design | #1819 | `sdd/android-attendance-history/design` | Context (B1-B3, A1-A8, rollout). |
| tasks | #1821 | `sdd/android-attendance-history/tasks` | Structure and PR chain. Its progress note predates PR2 and PR3; completion taken from on-disk `tasks.md`. |
| verify-report | #1827 | `sdd/android-attendance-history/verify-report` | Verdict and warnings (intermediate snapshot). Its "3.11/3.12 unticked" note is stale; on disk they are ticked. |

Referenced, not read: `apply-progress` #1822 (cited through #1827 only).

Filesystem sources (authoritative for content): `proposal.md`, `design.md`, `tasks.md`, `specs/*/spec.md` (three deltas), `verify-report.md`.

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| `android-attendance-history` | Created | Full delta copied mechanically with `cp`. 8 requirements, 19 scenarios, 134 lines. |
| `physical-attendance-history` | Updated | 2 requirements MODIFIED in place: "Self attendance for a calendar month" and "Status is derived live, never stored". 0 added, 0 removed. 10 other requirements byte-identical. Requirements: 12 before, 12 after. Scenarios: 21 before, 24 after (+3: two new in Self, one new in Status). Lines: 230 before, 252 after. |
| `android-auth-navigation` | Updated | 1 requirement MODIFIED: "Authenticated home and navigation (AC3)" (drawer lists exactly three entries). 0 added, 0 removed. 9 other requirements byte-identical. Requirements: 10 before, 10 after. Scenarios: 21 before, 22 after (+1, "Navigate to Mi asistencia"). Lines: 160 before, 165 after. |

Scenario reconciliation: 9 (physical delta) + 6 (auth delta) + 19 (new domain) = 34, matching the 34 scenarios in `verify-report.md`.

### MODIFIED merge detail and deviation

- The delta block replaced the main-spec block in place. Requirement positions in the main files are unchanged: physical "Self attendance" at line 11, "Status is derived live" at line 97; auth "Authenticated home and navigation (AC3)" at line 78.
- The splice was done by a byte-level script (`python3 -I`, scratchpad, outside the repository) that slices lines by requirement heading and writes the bytes back. No model Read/Write touched the content. The script asserts that every untouched requirement is byte-identical before writing.
- **Deviation (intentional)**: each delta block carries a `(Previously: ...)` annotation line. These 3 lines (2 in physical, 1 in auth) were NOT carried into the main specs, consistent with #375. Main specs carry no history annotations. `grep -c Previously` over the three main specs returns 0.
- Engram proposal #1817 and spec #1818 were not used for merging; the on-disk delta text is the content source.

### Mechanical readbacks

Raw `diff` output, obtained through `rtk proxy diff` (the default `diff` alias is rewritten by the rtk hook into a summary and was not used as evidence).

A. New domain spec, source vs destination (`cp` copy), empty = pass:

```
diff -r openspec/changes/android-attendance-history/specs/android-attendance-history/spec.md openspec/specs/android-attendance-history/spec.md
(no output, exit 0)
```

B. Replaced requirement blocks, filtered delta block vs block now in the main spec, empty = pass:

```
diff -r readback/self-attendance-for-a-calendar-month.delta-filtered.md readback/self-attendance-for-a-calendar-month.main-new.md
(no output, exit 0)
diff -r readback/status-is-derived-live--never-stored.delta-filtered.md readback/status-is-derived-live--never-stored.main-new.md
(no output, exit 0)
diff -r readback/authenticated-home-and-navigation--ac3.delta-filtered.md readback/authenticated-home-and-navigation--ac3.main-new.md
(no output, exit 0)
```

C. Old main vs new main (hunk ranges only; all changes fall inside the replaced blocks):

- `physical-attendance-history`: hunks at old lines 18-20, 45 (insert after), 85-86, 93 (insert after), 98. Replaced blocks span old lines 11-45 and 82-98.
- `android-auth-navigation`: hunks at old lines 80, 85, 91 (insert after), 93, 103. Replaced block spans old lines 78-106.

D. `Previously` leakage into main specs: 0 matches in `physical-attendance-history`, `android-auth-navigation`, `android-attendance-history`.

## Archive Move and Readback

- Snapshot taken before the move: recursive `cp -R` of `openspec/changes/android-attendance-history` into the session scratchpad.
- Tracked artifacts moved with `git mv`: `proposal.md`, `design.md`, `tasks.md`, `specs/`.
- Untracked `verify-report.md` and untracked tool-state file `.gentle-ai-instance` moved with plain `mv`. `.gentle-ai-instance` was never staged or added to git.
- The source directory `openspec/changes/android-attendance-history/` was removed (`rmdir`, empty). Check: it no longer exists.

Readback 1, snapshot vs archive immediately after the move (empty = pass):

```
diff -r <snapshot>/ openspec/changes/archive/2026-11-01-android-attendance-history
(no output, exit 0)
```

Readback 2, after the F.3/F.4 tick in the archived `tasks.md` (the one intentional content change, made by this phase). Only those two lines differ:

```
diff -r <snapshot>/tasks.md openspec/changes/archive/2026-11-01-android-attendance-history/tasks.md
183,184c183,184
< - [ ] F.3 ...  (unticked)
< - [ ] F.4 ...  (unticked)
---
> - [x] F.3 ...  (ticked, evidence: archive done, paths)
> - [x] F.4 ...  (ticked, evidence: this archive PR)
```

Readback 3, after this report was written: only the additive `archive-report.md` is reported, as expected.

## Archived Contents

- `proposal.md`, `design.md`, `tasks.md` (63/63 `[x]`, 0 `[ ]`), `specs/` (three deltas), `verify-report.md`, `.gentle-ai-instance` (untracked), `archive-report.md` (this file).

## Repository References to the Old Path

Search (after the move, excluding the archive): `rtk proxy rg -n 'changes/android-attendance-history' --glob '!openspec/changes/archive/**' .` returned no matches (exit 1). `rtk proxy rg -n 'android-attendance-history' docs` returned no matches. No link needed fixing.

## Final-State Facts (launch prompt, rank 3)

Corroborated where noted; the rest is attributed to the launch prompt.

| Fact | Status |
|------|--------|
| PR #674 (artifacts, PR-A) merged | Corroborated by tasks.md A.x evidence and commit `26669ed` in history. |
| PR #675 (PR1, backend `kind`, closes #673) merged | Corroborated: commit `85f2afa`; tasks.md 1.14/1.15. |
| PR #676 (PR2a, adapter and data) merged | Corroborated: commit `498ddc4`; tasks.md 2.20. |
| PR #677 merged into the PR2a branch by mistake; re-applied on develop as #678 by cherry-pick | Launch prompt (rank 3); corroborated by tasks.md 2.20 evidence. |
| PR #678 (PR2b, use case and ViewModel) merged | Corroborated: commit `86e85df`. |
| PR #679 (PR3, screen, closes #376) merged | Corroborated: commit `71b6876`. |
| PR2 split into PR2a and PR2b because it exceeded the 800-line budget (~880 measured, 912 with ticks) | Corroborated: tasks.md 2.18 (measured, forecast ~510). |
| `kind` added to the backend in #675 | Corroborated: tasks.md 1.14. |
| Manual runs on API 29 and API 37 on 2026-10-31 (3.M1-3.M7) | Corroborated: tasks.md 3.M1-3.M7 evidence lines. Self-reported; not re-run by the archive or verify. |
| Attendance row wording changed (see Final-State Corrections) | Corroborated: on-disk delta spec and `strings.xml` lines 46-48. |
| Issue #376 is closed | Corroborated: `gh issue view 376` returned `CLOSED`. |
| Issue #680 (follow-up, archive) is open | Corroborated: `gh issue view 680` returned `OPEN`. The archive PR is opened from this branch per F.4. |
| Local AUTH_DEGRADED 503 (#672) found while testing; unrelated | Corroborated: `gh issue view 672` returned `OPEN`, title "[auth] El login devuelve 503 AUTH_DEGRADED por eventos de outbox sin handler". Not fixed here; out of scope for this archive. |

## Final-State Corrections (on-disk artifacts outrank Engram copies)

- **Attendance row wording.** Engram spec #1818 and design #1819 describe the origin as "Con QR" / "Registrada en recepcion" (`Con` followed by the origin). The on-disk delta spec states that the origin wording MUST NOT start with "Con", which is reserved for the instructor line. The shipped `strings.xml` uses: "Ingreso por QR · HH:mm" (QR), "Ingreso en recepción · HH:mm" (MANUAL), and "Ingreso HH:mm" (null, absent or unknown). The main spec carries the on-disk wording. Final state: the on-disk wording, shipped in #679.
- **Scenario count.** Engram #1818 says 17 scenarios for the new domain. The on-disk file has 19. Total across the three deltas is 34, matching `verify-report.md`.
- **Task ticks 3.11 and 3.12.** Engram #1827 records them as unticked. On disk they are ticked (`tasks.md` lines 172-173). Verify WARNING 2 is therefore resolved in the persisted artifact. The Engram copies are snapshots.
- **Tasks progress counts.** Engram #1827 says "57 ticked, 6 unticked". On disk, at archive time, 61 were ticked and 2 (F.3, F.4) were open; both are closed by this archive.

## Verify-Report Warnings: Final State

Attribution: `verify-report.md` (rank 4) at verification time, 2026-11-01. Final state reported per the hierarchy.

1. **8 PARTIAL scenarios (carried, not resolved).** QR attendance, Manual attendance, Unknown or missing kind (composed row text device-observed or fallback read only), No attendances, Failure and retry (literal text device-observed, not unit-asserted), Session expired (no proof of "no list error"), Home contents and Back from home (no automated test). Positive evidence exists for each; none is failing. Carried as documented limitations, not pending work.
2. **Task bookkeeping (resolved).** 3.11 and 3.12 are ticked on disk; F.1 is ticked (issue #680 on board). F.3 and F.4 are ticked by this archive.
3. **Manual evidence self-reported (carried).** 1.M1 and 3.M1-3.M7 are self-reported and were not re-run by verify. The `2026-10-01T02:30Z` month-edge row (appears under September as "30 de septiembre, 23:30") is covered by unit tests only. Carried.
4. **TDD evidence structure (carried).** `apply-progress` #1822 has no formal "TDD Cycle Evidence" table and no PR3 section. Per-group RED/GREEN counts exist in `tasks.md`. Carried as a process caveat.

Suggestions carried, not actioned: Compose UI test for the row, empty, error and drawer entries (would convert the PARTIAL rows); OpenAPI contract test (only CI Redocly lint guards it); recording a baseline before modifying existing tests; calibrating test-size forecasts (tests 626 lines vs ~280 forecast).

## Contradictions and Their Resolution

- Engram spec #1818 and design #1819 (row wording "Con QR", 17 scenarios) versus the on-disk spec and shipped `strings.xml`. Resolved in favour of the on-disk artifacts and the shipped code (ranks 2 and 3). Not a live contradiction: the Engram copies are the stale mirror.
- Engram verify #1827 (3.11/3.12 unticked; 57/63 ticked) versus on-disk `tasks.md` (ticked, 61/63 before this archive). Resolved in favour of the on-disk file (rank 2). Snapshot claim, attributed.
- No unrankable contradictions remain.

## Repository and Process Notes

- Nothing was committed or pushed. No PR was opened. No code was edited. No other archived change was touched.
- `.gentle-ai-instance` is untracked and not gitignored. Not staged; moved with `mv` into the archive folder.

## Files

Created:
- `openspec/specs/android-attendance-history/spec.md`
- `openspec/changes/archive/2026-11-01-android-attendance-history/archive-report.md` (this file)

Modified:
- `openspec/specs/physical-attendance-history/spec.md` (2 requirements MODIFIED)
- `openspec/specs/android-auth-navigation/spec.md` (1 requirement MODIFIED)
- `openspec/changes/archive/2026-11-01-android-attendance-history/tasks.md` (F.3 and F.4 ticked, archived copy)

Moved (`git mv` for tracked, `mv` for untracked):
- `openspec/changes/android-attendance-history/{proposal.md, design.md, tasks.md, specs/}` to `openspec/changes/archive/2026-11-01-android-attendance-history/` (`git mv`)
- `openspec/changes/android-attendance-history/verify-report.md` to the same archive folder (`mv`, untracked)
- `openspec/changes/android-attendance-history/.gentle-ai-instance` to the same archive folder (`mv`, untracked, never staged)

Removed:
- `openspec/changes/android-attendance-history/` (empty after the moves)

## Deviations From the #375 Precedent

- The #375 precedent left `.gentle-ai-instance` in place and kept the source folder. This archive moves it (per the launch prompt) and removes the source folder, so the active `openspec/changes/` no longer lists the change.
- The `Previously` annotation lines were not carried into main specs (same as #375, now recorded explicitly).
- The F.3/F.4 ticks were applied to the archived copy after the move; the readback shows exactly those two lines differ.

## Outcome

Change archived. Main specs reflect the final behaviour: the drawer shows exactly three entries, the physical self endpoint exposes `kind`, and the new Android domain spec is in place. Nothing committed or pushed.

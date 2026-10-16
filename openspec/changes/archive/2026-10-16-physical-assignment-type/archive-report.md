# Archive Report: physical-assignment-type (#521)

**Closed**: 2026-10-16
**Artifact store**: hybrid (Engram `sdd/physical-assignment-type/archive-report` + this file)
**Archive status**: closed with warnings (verify `pass_with_warnings`, 0 CRITICAL; no stale-checkbox reconciliation; no intentional partial archive)
**Archived to**: `openspec/changes/archive/2026-10-16-physical-assignment-type/`
**Delivery**: 3 PRs, all merged per the orchestrator launch facts (not re-verified against GitHub in this phase): #583 (`QUOTE_NOT_FOUND` handling), #584 (write path + V26 migration), #585 (read side: my-sessions projection, OpenAPI/Bruno/docs).

## Artifacts Read (traceability)

| Artifact | Engram topic_key | Observation ID |
|----------|------------------|----------------|
| proposal | `sdd/physical-assignment-type/proposal` | #1782 |
| spec (three delta specs) | `sdd/physical-assignment-type/spec` | #1783 |
| design | `sdd/physical-assignment-type/design` | #1784 |
| tasks | `sdd/physical-assignment-type/tasks` | #1785 |
| verify-report (final verify) | `sdd/physical-assignment-type/verify-report` | #1787 |

The file copies in this folder were treated as authoritative for content (Engram observations are summaries pointing to them). The verify report file is `verify-report.md` in this folder.

## Native Review Receipt Gate

Not evaluated. The orchestrator launch prohibited `gentle-ai review` commands in this phase, so no structured status (`reviewGate`) was requested and no `review/*` topics were read. Archive proceeded under the launch instruction. This is a recorded gap in the gate evidence, not a verified "absent" state.

## Task Completion Gate

Tasks artifact checked before any sync or move (`tasks.md`):
- All implementation tasks are checked `[x]`, including 7.5 (reworded in the working tree to cite PR #585 and 223 changed lines).
- Two items remain unchecked under `## Non-blocking follow-ups (out of scope)`: F.1 (bff consumes `assignmentType`, separate issue) is a note without a checkbox; F.2 is an unchecked `- [ ]` follow-up. Neither is an implementation task, so the gate passes without reconciliation.

Uncommitted state at archive time: the 7.5 wording edit and the untracked `verify-report.md` were present in the worktree. They are carried into the archive as-is and still need to be committed by the author.

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| `physical-assignment-type` | Created | New capability, copied byte-identical from the delta (7 requirements, ADDED). |
| `physical-assignment-outbox-routing` | Created | New capability, copied byte-identical from the delta (4 requirements, ADDED). |
| `physical-capacity-hold` | Updated | 1 MODIFIED (`Conversion uses exactly the held session set and is idempotent`), 0 ADDED, 0 REMOVED. Requirements 7 before, 7 after. The other 6 requirements are unchanged (byte-identical to the pre-merge snapshot for lines 1-94). |

Merge method for the MODIFIED block: a deterministic splice script (`python3 -I`, scratchpad, no model-generated content) replaced the requirement block with the delta block of the same name. The pre-merge main spec was snapshotted first.

## Verification Evidence (verify-report, intermediate snapshot)

Per `verify-report` #1787 (written 2026-10-16 11:40, HEAD `c33e0dc`), this is the state at verification time, not necessarily at close:
- Verdict: `pass_with_warnings`. 12 requirements / 29 scenarios compliant. CRITICAL 0.
- Gate (cached, all tasks UP-TO-DATE): shared 70, physical 571, billing 971, app 533 tests; 0 fail/err/skip; checkstyle and jacoco ok.
- WARNING 1: Bruno `My Sessions.bru` documents `assignmentType`, but its assert block only checks status 200. No executable Bruno assertion exists for the field. Still open at close: no later evidence in the launch facts addresses it.
- WARNING 2: gate results were cached, not re-executed during verify. Test counts above are verify-time numbers; no later count was supplied.
- SUGGESTION items from verify (add Bruno assertion; reword task 7.5; optional ArchUnit physical-to-billing rule): the 7.5 reword is reflected in the archived `tasks.md`. The Bruno assertion and the optional ArchUnit rule are not carried as follow-ups in this report beyond F.2.

## Final-State Reconciliation

Ranked sources: repository and tasks artifact first, then the orchestrator launch facts, then intermediate snapshots (`verify-report`).

1. **Task 7.5 wording** (verify SUGGESTION, #1787): the tasks artifact now cites #585 and 223 lines. Final state per the tasks artifact.
2. **Bruno assertion and gate caching** (verify WARNING 1 and 2, #1787): no later evidence closes them. Recorded as warnings, not as resolved.
3. **Delivery**: three PRs #583, #584, #585 per launch facts. The verify report predates the read-side PR's final merge state; no contradiction was found between the sources.

No unrankable contradictions were found.

## Follow-ups (not fixed in this change)

- **F.1**: `bff` consumes `assignmentType`. Separate issue; not opened in this phase.
- **F.2**: Manual validation of the my-sessions response against a running API after merge (Bruno). Unchecked `- [ ]` in `tasks.md` under non-blocking follow-ups.

## Verbatim Readback Output

Copy of the new capability specs (`cp` then `diff -r` against the delta):
```
$ diff -r openspec/changes/physical-assignment-type/specs/physical-assignment-type/spec.md openspec/specs/physical-assignment-type/spec.md
(no output, exit 0)
$ diff -r openspec/changes/physical-assignment-type/specs/physical-assignment-outbox-routing/spec.md openspec/specs/physical-assignment-outbox-routing/spec.md
(no output, exit 0)
```

MODIFIED merge readback (delta block vs merged main block, extracted by requirement name):
```
$ diff -r rb_delta_block.md rb_merged_block.md
(no output, exit 0)
```
Both blocks are 46 lines. Untouched content: lines 1-94 of the pre-merge snapshot and the merged `physical-capacity-hold` spec are identical (`diff` on those lines: no output, exit 0).

Move readback. An earlier run reported one stray empty file, `specs/physical-capacity-hold/spec.md.blk`, which was created by a no-op line in my readback script inside the change's delta folder before the move. It was removed from the archive, and the final readback below is the one that counts:
```
$ diff -r "<pre-move snapshot>/source" openspec/changes/archive/2026-10-16-physical-assignment-type
(no output, exit 0)
```

## Files

- Created: `openspec/specs/physical-assignment-type/spec.md`, `openspec/specs/physical-assignment-outbox-routing/spec.md`, `openspec/changes/archive/2026-10-16-physical-assignment-type/archive-report.md`
- Modified: `openspec/specs/physical-capacity-hold/spec.md` (1 MODIFIED requirement)
- Moved (plain `mv`, not staged): `openspec/changes/physical-assignment-type/` -> `openspec/changes/archive/2026-10-16-physical-assignment-type/`, including the untracked `verify-report.md`, the modified `tasks.md`, and the delta specs.
- Git state: nothing committed, pushed, or staged. The `git mv` used by the skill was replaced with `mv` because the launch prohibited git write commands. The index still shows the old tracked paths; the author should stage the move and the new specs.

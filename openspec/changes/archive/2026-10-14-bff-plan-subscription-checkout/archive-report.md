# Archive Report: bff-plan-subscription-checkout (#361)

**Closed**: 2026-10-14
**Artifact store**: hybrid (Engram `sdd/bff-plan-subscription-checkout/archive-report` + this file)
**Archive status**: intentional-with-warnings (one stale-checkbox reconciliation, see Task Completion Gate; follow-ups listed below)
**Archived to**: `openspec/changes/archive/2026-10-14-bff-plan-subscription-checkout/`
**Delivery**: 6 stacked PRs #570-#575 (merged against each other; only the first reached `develop` directly), consolidated by PR #576 (`feature/bff-plan-subscription-checkout-s2c-docs` -> `develop`, squash `1252223`, merged 2026-10-14T18:20:16Z). Issue #361 closed 2026-10-14T18:20:17Z and set to Done on the project board.

## Artifacts Read (traceability)

| Artifact | Engram topic_key | Observation ID |
|----------|------------------|----------------|
| explore | `sdd/bff-plan-subscription-checkout/explore` | #1755 |
| proposal | `sdd/bff-plan-subscription-checkout/proposal` | #1758 |
| proposal decisions (user resolution round) | `sdd/bff-plan-subscription-checkout/proposal-round-resolution` | #1760 |
| spec (delta) | `sdd/bff-plan-subscription-checkout/spec` | #1761 |
| design | `sdd/bff-plan-subscription-checkout/design` | #1762 |
| tasks | `sdd/bff-plan-subscription-checkout/tasks` | #1764 |
| apply-progress (intermediate snapshot) | `sdd/bff-plan-subscription-checkout/apply-progress` | #1765 |
| verify-report PR1 (intermediate snapshot) | `sdd/bff-plan-subscription-checkout/verify-report-pr1` | #1766 |
| verify-report PR2 (intermediate snapshot) | `sdd/bff-plan-subscription-checkout/verify-report-pr2` | #1767 |

No `sdd/bff-plan-subscription-checkout/verify-report` (non-suffixed) topic exists; the PR1 and PR2 reports are the verify artifacts. The openspec files in the change folder were treated as authoritative for content.

## Native Review Receipt Gate

Not applicable. `gentle-ai review mode status` reports receipt-driven development `off` (decided by global; clone-local unset). `reviewGate` is structurally absent for this candidate, so no `review/*` topics were read and no receipt was required.

## Task Completion Gate

Tasks artifact checked before any sync or move: 2 unchecked implementation tasks found at archive start, both stale: `4.3` (open PR1) and `8.3` (open PR2; update project board fields). Their only outstanding action was "open the PR", which had happened.

**Reconciliation (exceptional, orchestrator-instructed)**. Reason recorded verbatim: "Explicit instruction from the orchestrator (2026-10-14): tasks 4.3 and 8.3 are stale checkboxes. Their only outstanding action was 'open the PR', which has happened (PRs #570-#575 merged; consolidated in #576, squash 1252223 on develop; #361 closed and Done on the board). Gate green locally (PR1 761 tests, PR2 806 tests). Both verify reports are pass_with_warnings with 0 CRITICAL."

Evidence checked by the archive phase (read-only `gh` and git):
- `gh pr view 576`: state MERGED, base `develop`, merge commit `1252223620687656366169d6a8c2955071d162e1`.
- `gh pr view 570` through `575`: all MERGED.
- `gh issue view 361`: state CLOSED.
- `git show --numstat 1252223`: 34 files, 2,324 insertions, 101 deletions.
- Archived `tasks.md`: `rg -F '[ ]'` returns 0 matches.

Edits to `tasks.md` (only these two lines changed; `[ ]` became `[x]` with an annotation in the parenthetical): line 40 (4.3) and line 53 (8.3). No other unchecked implementation task existed.

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| `bff-plan-subscription-checkout` | Created | New capability, copied byte-identical from the delta (6 requirements) |
| `bff-plans-view` | Updated | 2 MODIFIED (`Anonymous-reachable plans route`, `Upstream failures degrade to the shared error view`), 4 ADDED (`Subscribe button depends on role`, `Current subscription is consulted only with a token`, `Subscription state drives button and notice for a student`, `Subscription status failure is fail-closed`), 0 REMOVED. Requirements 5 -> 9. Untouched: `Plans-list route is permitted without widening access elsewhere`, `Each plan renders name, description, price with currency and duration`, `Featured plans show a badge, list order is unchanged`. |

Merge method: a deterministic splice script (`python3 -I`, scratchpad, no model-generated content) replaced each MODIFIED block by the delta block of the same name and appended the ADDED blocks in delta order. The pre-merge main spec was snapshotted first.

## Verbatim Readback Output

Copy of the new capability spec (`cp -R` then `diff -r` against the delta):
```
$ diff -r openspec/changes/bff-plan-subscription-checkout/specs/bff-plan-subscription-checkout/spec.md openspec/specs/bff-plan-subscription-checkout/spec.md
(no output, exit 0)
```

Merged `bff-plans-view` spec, MODIFIED and ADDED blocks extracted from the delta and from the merged file, then compared:
```
$ diff -r rb/delta rb/merged
(no output, exit 0)
```

Merged `bff-plans-view` spec, untouched main blocks extracted from the pre-merge snapshot and from the merged file, then compared:
```
$ diff -r rb2/main rb2/merged
(no output, exit 0)
```

Move readback, pre-move recursive snapshot of the change folder against the archived folder (taken after the two `tasks.md` edits):
```
$ diff -r "$snapshot_root/source" "openspec/changes/archive/2026-10-14-bff-plan-subscription-checkout"
(no output, exit 0)
```

Requirement count: merged `bff-plans-view` = 9 (`### Requirement:` headings), pre-merge = 5, delta = 6.

## Final-State Reconciliation

Ranked sources: repository and native evidence first, then the orchestrator launch facts, then intermediate snapshots (apply-progress, verify-report-pr1, verify-report-pr2). Snapshot claims below are attributed to their source and time.

1. **PR2 verify W1 (spec not amended; no-kind session redirect is an accepted deviation)**, per verify-report-pr2 #1767 at 2026-10-14 15:02. Superseded: the spec was amended. Scenario `No session state` now states the class-purchase redirect to `/classes`, and new scenario `Subscription kind without a payment id` was added (spec.md lines 164-175, verified). Fix commit `27f20a8` exists locally.
2. **PR2 verify W2 (docs/06 step 5 overstates the fallback)**, per #1767. Superseded by the fix commit `1a61da8` (exists locally; the reworded text was not re-read in this phase).
3. **PR1 verify W1 (size 1,161 lines over budget)**, per verify-report-pr1 #1766 at 2026-10-14 14:21. Superseded: the change was cut into slices and all code landed together in #576. The cross-slice orphan button warning is moot for the same reason.
4. **PR1 verify W3 (form posts to `/suscripciones`, which exists only in PR2)**, per #1766. Moot: both slices are in #576.
5. **Open-PR statements** ("PR2 tasks 5.1-8.3 and 4.3 (open PR) pending", "PR not opened"), per #1766 and apply-progress #1765 at 14:12-14:21. Superseded by the merged state above and by the Task Completion Gate reconciliation.
6. **Size figures, unreconciled**. The repository total for the squash commit is 2,425 changed lines (2,324 insertions + 101 deletions, 34 files), verified with `git show --numstat`. The per-slice figures in the launch facts (s0 648, s1a 687, s1b 476, s2a 344, s2b 797, s2c ~126) sum to 3,078, which does not match 2,425. The slice numbers are per-slice review counts whose counting method is not recorded, so this contradiction is recorded here and not resolved. Earlier snapshots give other figures (apply-progress #1765: PR2 at 1,247 changed lines before the split; design #1762: forecast ~1,550). These are historical and are not used as final numbers.
7. **Test counts**: PR1 761 tests, PR2 806 tests (0 failures, 2 pre-existing skips), per verify-report-pr1 #1766 and verify-report-pr2 #1767 and the launch facts. No later count was supplied.
8. **Rejected payment slot release**: a REJECTED payment cancels the subscription and frees the slot (`PaymentVerificationService.applyOutcome` -> `PaymentFulfillmentService.release` -> `Subscription.cancelled`), confirmed in apply-progress #1765 and verify-report-pr2 #1767 (test `a_rejected_payment_cancels_the_pending_subscription`). The design's open question (#1762) is closed by this finding.

No CRITICAL findings in either verify report (verify-report-pr1: 0; verify-report-pr2: 0). Both verdicts were `pass_with_warnings`, blockers 0.

## Decisions Preserved

- D1: `paymentId` lives in the server session, not a signed `state`, because `back_url` is one global API property (same constraint as #365).
- D2: PENDING notice shows the full `subscriptionId` with no checkout button.
- D3: STUDENT-only button; anonymous visitors go to login and return to `/plans`; other roles see no button.
- `/me` is fail-closed: anything other than 200 or 404 `NO_SUBSCRIPTION` renders the standard error view with no plans and no button.
- EXPIRED shows the button plus an "already expired" notice with the end date.
- A new `SubscriptionApiClient` port was added instead of extending `BillingApiClient`.
- Session attribute `com.menta.bff.PENDING_PURCHASE_KIND` discriminates class and subscription returns.

## Follow-ups (not fixed in this change)

- `docs/26` paymentId lookup has not been executed against a live database.
- Manual end-to-end walkthrough with the local simulator (approved webhook unlocking a restricted lesson) is pending.
- The `accessibility` CI check was still pending when #576 merged.
- Rejected and pending payment outcomes are covered only by WireMock tests.
- PR2 verify W3 (6.1/6.2 have no separate RED commit) and W5 (s2b 797 of 800 lines) are informational.
- SDD runtime ledger left `decision_required: reset` for accumulated budget (informational).

## Files

- Created: `openspec/specs/bff-plan-subscription-checkout/spec.md`, `openspec/changes/archive/2026-10-14-bff-plan-subscription-checkout/archive-report.md`
- Modified: `openspec/specs/bff-plans-view/spec.md`, `openspec/changes/bff-plan-subscription-checkout/tasks.md` (before move; now archived copy)
- Moved (git mv, staged as renames): `openspec/changes/bff-plan-subscription-checkout/` -> `openspec/changes/archive/2026-10-14-bff-plan-subscription-checkout/`, including the untracked `verify-report-pr1.md` and `verify-report-pr2.md`
- Git state: nothing committed or pushed. Renames are staged; `tasks.md` and `openspec/specs/bff-plans-view/spec.md` carry unstaged changes; the new spec folder is untracked.

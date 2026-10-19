# Archive Report: bff-plans-view

**Change**: `bff-plans-view` (GitHub issue #177, US-BILLING-013)
**Archived**: 2026-09-11
**Mode**: hybrid (Engram + openspec filesystem)
**Final verdict**: PASS — 0 CRITICAL, 0 WARNING, 1 SUGGESTION (non-blocking)

## Source Artifacts Read (traceability)

| Artifact | Engram observation | Path |
|---|---|---|
| proposal | #1288 `sdd/bff-plans-view/proposal` | `openspec/changes/archive/2026-09-11-bff-plans-view/proposal.md` |
| spec (deltas) | #1289 `sdd/bff-plans-view/spec` | `openspec/changes/archive/2026-09-11-bff-plans-view/specs/` |
| design | #1290 `sdd/bff-plans-view/design` | `openspec/changes/archive/2026-09-11-bff-plans-view/design.md` |
| tasks | #1291 `sdd/bff-plans-view/tasks` | `openspec/changes/archive/2026-09-11-bff-plans-view/tasks.md` |
| verify-report | #1293 `sdd/bff-plans-view/verify-report` | `openspec/changes/archive/2026-09-11-bff-plans-view/verify-report.md` |

No native review (RDD) artifacts exist for this candidate: `reviewGate` is structurally
absent (receipt-driven development is off / never started for this change), so archive
proceeds under ordinary repository policy. No `sdd/bff-plans-view/review/*` topics were
read because none exist.

## Task Completion Gate

`openspec/changes/bff-plans-view/tasks.md` (pre-move) contained 37/37 checked tasks
(`- [x]`), 0 unchecked. Verified directly via `rg -c` before any spec sync or move.
Gate passed with no reconciliation needed.

## Final State (authoritative — supersedes intermediate snapshots)

All work shipped across 5 chained PRs, all merged to `develop`, auto-closing issue #177:

| PR | Scope |
|---|---|
| #196 | Slice 1: port/DTO/properties |
| #197 | Slice 2: adapter fetch + status mapping |
| #198 | Slice 3: single-entry TTL cache with single-flight, strict no-stale-on-failure |
| #199 | Slice 4: use case/controller/template, exact-path `permitAll` |
| #200 | Slice 5: security regression + rendering integration tests + #170 lesson-sample CTA cutover (auto-closed #177 on merge) |

**Final `develop` HEAD**: `9b87286`

No PR in this chain required a `size:exception` — all 5 landed under their forecast
ledger caps, continuing the discipline established since the prior
`bff-continue-course`/`bff-virtual-learning-view` changes of grounding forecasts in
measured repo precedents (per `sdd/bff-plans-view/tasks` #1291: ~1450-1550 total lines
forecast, High 400-line-budget risk, chained PRs mandated and followed).

`sdd-verify` (#1293) independently confirmed on `develop@9b87286`: full forced
`./gradlew :bff:test check --rerun-tasks` (not cached) — 2069 tests, 0 failures,
0 errors, 2 pre-existing skips, BUILD SUCCESSFUL. All 10 requirements / 16 scenarios
across the `bff-plans-view`, `billing-api-integration`, and `virtual-lesson-view`
(MODIFIED delta) specs are COMPLIANT with passing covering tests.

The verify report's single SUGGESTION is a non-blocking note about the machine-readable
envelope's `evidence_revision` being a synthetic placeholder (this hybrid project has no
single canonical evidence-bundle file to hash as a whole) — the actual SHA-256 test/build
output hashes recorded in the report are real and authoritative, not synthetic.

## Corrected Design Decision — Cache No-Stale-on-Failure (D6)

The single most load-bearing design decision this change shipped was the corrected
"no stale-on-failure" cache rule. The design's first draft served an expired cache entry
when a refresh failed, reasoning that stale data beats an error page. During orchestration
this was caught as silently overriding the user's locked D6 decision (billing downtime
renders the shared error view) for the common case of a warm production process, with no
upper bound on staleness during a long outage. The user was asked directly and chose
strict D6: an upstream failure always propagates, cache cold or expired, no exceptions.

`sdd-verify` (#1293) independently confirmed this holds in actual code, not just in a test
name, by reading `BillingApiAdapter.getPlans()`'s control flow directly:
`cache.set(...)` is only reachable after a successful upstream response, and every
exception path in the refresh leaves the existing cache entry untouched and propagates.
Single-flight double-checked locking with `AtomicReference` + `ReentrantLock` was also
confirmed real by direct read, not inferred.

## Integration-Test Wiring Fixes (PR 5)

PR 5's apply agent found and fixed two real integration-test wiring bugs during its own
TDD cycle (test configuration only, not production defects):

1. `menta.billing.base-url` pointed at the wrong host instead of the test WireMock server.
2. Cross-test cache pollution from the shared 5-minute TTL cache bean bleeding state
   between integration tests.

Both confirmed genuinely fixed by `sdd-verify` reading `AbstractTestcontainersConfig.java`
directly: the `menta.billing.base-url` dynamic property and `menta.billing.cache-ttl=PT0S`
are both present.

## Funnel Closure with #170

This change closes the funnel `bff-virtual-learning-view` (#170) opened and deliberately
left incomplete: #170's lesson-sample view showed a subscription call-to-action with a
dead-end message ("no plans page exists yet"), explicitly because inventing a placeholder
link would have repeated a documented product debt pattern (#56). `bff-plans-view` (#177)
is that promised follow-up. PR 5 cuts the CTA over from the dead-end message to a real
`/plans` link. `LessonView.Sample` still deliberately carries no `plansUrl` field — the
link lives in the template only, per #170's original locked design, unchanged here.
Confirmed by `sdd-verify` reading `templates/lesson.html` and `LessonView.java` directly.

This is reflected in the spec merge below: `virtual-lesson-view`'s "BFF-assembled sample
view on subscription denial" requirement was MODIFIED, replacing its "no navigable link"
clause with "MUST link to the BFF's `/plans` route", carrying a `(Previously: ...)` note
per the delta spec's own convention.

## Out of Scope (tracked separately)

Issue #195 (billing's rate-limiter fingerprint ignoring `X-Forwarded-For`) is the
underlying reason this change needed a cache at all — the cache works around the symptom
for this specific caller, not the root cause. Explicitly out of scope for `bff-plans-view`
and tracked independently.

## Specs Synced

| Domain | Action | Details |
|---|---|---|
| `bff-plans-view` | Created (mechanical copy, no prior main spec) | New capability spec |
| `billing-api-integration` | Created (mechanical copy, no prior main spec) | New capability spec |
| `virtual-lesson-view` | Modified (merged into existing main spec) | 1 requirement MODIFIED: "BFF-assembled sample view on subscription denial" — CTA now links to `/plans`; added 1 new scenario "Subscription CTA links to the plans page" |

### Mechanical Copy / Move Verification (diff -r, verbatim)

```
=== diff (temp vs source) for bff-plans-view ===
(empty — no differences)
=== diff (temp vs source) for billing-api-integration ===
(empty — no differences)
=== diff -r snapshot vs archived change folder ===
(empty — no differences, exit status 0)
```

## Archive Contents

- `proposal.md` ✅
- `specs/` (3 delta specs) ✅
- `design.md` ✅
- `tasks.md` ✅ (37/37 tasks complete)
- `verify-report.md` ✅

## Source of Truth Updated

- `openspec/specs/bff-plans-view/spec.md` (new)
- `openspec/specs/billing-api-integration/spec.md` (new)
- `openspec/specs/virtual-lesson-view/spec.md` (updated — CTA link requirement)

## SDD Cycle Complete

The change has been fully planned, implemented, verified, and archived. Ready for the
next change.

# Archive Report: demo-seed-data (#379)

**Archived to**: `openspec/changes/archive/2026-10-08-demo-seed-data/`
**Date**: 2026-10-08 | **Issue**: #379 (closed) | **Mode**: hybrid (OpenSpec + Engram)

## Verification

Verdict **PASS WITH WARNINGS** (0 CRITICAL, 4 WARNING). See `verify-report.md`. 14/14 requirements and 32/32 scenarios covered. `tasks.md`: 40/40 `[x]`.

## Specs synced

| Domain | Action | Details |
|---|---|---|
| `demo-seed-data` | Created | New spec, 13 requirements: guard, demo-only config, users, catalog, sessions, seat, QR reader, billing, trial, boundaries. |
| `local-bunny-net` | Modified | The adapter-selection requirement now also selects the local adapter under `demo`, with two new scenarios and an updated production-guard scenario. |

## Delivery

Merged in 5 PRs against `develop`: #464 (slice 1), #465 (slice 2), #469 (slices 3 and 4, recovery PR), #468 (slice 5), #470 (slice 6). Slices 3 and 4 had first landed in intermediate stacked branches (#466, #467) and were recovered through #469.

## Follow-ups (non-blocking)

- W1: add a context-level test of `DemoProfileGuard` with `demo` + `prod`.
- W2: add a Testcontainers idempotency test.
- W3: keep a per-slice TDD table in apply-progress in future changes.
- W4: slice 3 exceeded the 400-line budget (432); split rolling-session logic next time.

## Not verified

QR secret rotation at runtime, rolling-session replace path, free-lesson streaming over HTTP, exact 90-day trial expiry, and the full build including Android. Verify these before relying on them.

## Engram lineage

proposal #1734, spec #1735, design #1736, tasks #1737, apply-progress #1738, verify-report #1739, archive-report #1740.

# Archive Report: catalog-physical-sessions-availability

**Change**: catalog-physical-sessions-availability
**Issues**: #107 (public physical course detail with live session availability), #316 (tests missing after the first verify FAIL)
**Project**: menta-dance
**Archive date**: 2026-10-02
**Archive path**: `openspec/changes/archive/2026-10-02-catalog-physical-sessions-availability/`
**Artifact store**: hybrid (openspec + Engram)

## Outcome

`GET /api/v1/catalog/courses/{courseId}` answers 200 for a PHYSICAL id with the course data and its upcoming scheduled sessions with live availability (it answered 404 since #47). The virtual detail and the list endpoint are unchanged. The half-open `[from, to)` range bug in the public session query was fixed first, because billing's documented contract relies on it.

## Delivery

Four PRs merged into `develop`:

| PR | Slice | Commit | Lines | Content |
|----|-------|--------|-------|---------|
| #313 | S1 | `2b69a9f` | +53/-4 | `findScheduledWithAvailability` uses `>= :from AND < :to`; integration tests for the bound and the order |
| #314 | S2 | `b7b4486` | +549/-116 | sealed `CatalogCourseDetail`, physical detail with `sessions`, window property, `catalogClock`, 503 `CATALOG_DEGRADED`, three 404-pinning tests inverted, `getCourse()` removed |
| #315 | S3 | `6a10f81` | +503/-24 | HTTP scenarios with seeded sessions, `catalog-v1.yaml` `oneOf`, `catalog-v1.yaml` added to the redocly lint in `pr-develop.yml`, Bruno, `docs/07-CATALOG-API.md`, `US-PHYSICAL-003`; `Closes #107` |
| #317 | S4 | `1fe883c` | +54/-0 | test-only remediation of the first verify FAIL; `Closes #316` |

Issues #107 and #316 are closed and Done on the board. Follow-up: #312 (cache and rate limit of the public catalog endpoints; Backlog, Baja).

## Specs promoted

Both capabilities are new; no existing main spec covered them. The delta specs were copied verbatim to the main specs (`diff` between the archived delta and the main spec reports the files identical):

- `openspec/specs/catalog-course-detail/spec.md`: 9 requirements, 21 scenarios.
- `openspec/specs/physical-session-availability/spec.md`: 5 requirements, 12 scenarios.

## Verification

Two verify attempts.

**Attempt 1: FAIL** on `develop` @ `6a10f81`. evidence_revision `sha256:53106d3197d121f67bfa993ca1c7a87e5a2e6a2e595f63d0553c0aea30a161e6`, report sha256 `f251ab25137d1b4305a0eca34e1f6600dafa66f033c1068a7f062f89611bb210`. 1 CRITICAL: the scenario "Zero-width and inverted range" had no test. 2 WARNING: the converted-hold half of "Expired and converted holds are ignored" was untested, and "List has no sessions" did not pin that the list performs no availability read. That report was superseded and is not in this folder; its evidence lives in the native ledger.

**Attempt 2: PASS** on `develop` @ `1fe883c`. evidence_revision `sha256:30766134d1b350a90ad049201e12d5d014f9326e65bb106bc0d97149b276361a`, report sha256 `022448670e2978157902b75e228dbf0e471a38d32a9796af92bf59db65508eb6` (confirmed unchanged after the move). 0 critical, 0 warning, 11 SUGGESTION; 14/14 requirements and 33/33 scenarios; `sdd-verify-validate --requirements 14 --scenarios 33` valid with verdict pass.

PR #317 closed the first findings with three tests: `PhysicalCourseAvailabilityIntegrationTest.a_zero_width_or_inverted_range_is_empty_even_with_a_session_on_the_bound` and `a_converted_hold_is_not_subtracted_from_availability_while_an_active_one_is`, and `CatalogCompositionServiceTest.listCourses_returns_physical_courses_without_reading_any_session_availability`.

Results reported by the verify agent from a fresh `--no-build-cache --rerun-tasks` run (test command and build command both exit 0; redocly lint as CI exit 0 with 7 pre-existing warnings): physical 463 tests and app 431 tests, 894 in total, 0 failures, errors or skipped; JaCoCo physical domain+application 98.04%, infrastructure 97.51%, `api:app` 95.70% (floor 0.90); ArchUnit physical 8/8 and app 5/5; Checkstyle on added lines: 14 `MethodName` (tracked in #298) and nothing else. The orchestrator re-checked the report hash, the validator result, the spec counts and the JUnit XML counts (physical 94 suites/463 tests, app 78 suites/431 tests, 0 failures) but did not re-run the gate; the gate, JaCoCo, ArchUnit and Checkstyle figures are the verify agent's.

## Native SDD ledger

Attempt 1 (work unit S1-S3) was settled `failed` with the attempt-1 evidence_revision after the planning artifacts were declared as intended untracked. The ledger charged 1801 changed lines against the 800-line budget (bases merged during the attempt are charged to it) and set `maintainer_decision`. The maintainer explicitly authorized an audited `sdd-attempt reset` (request id `reset-107-remediation-01`, actor `ale`; reason: remediation of the verify FAIL, because the merged PRs had consumed the 800-line budget of the original objective) on 2026-10-02. Attempt 2 (work unit S4) was acquired with `--remediates-evidence-revision` of the failed evidence and settled `passed` with the attempt-2 evidence_revision; settle returned `complete` with no `maintainer_decision`. Native status then reported tasks 50/50 and nextRecommended `archive`. A reset did happen in this change.

## Decisions (D1-D8, final)

1. Virtual-first resolution: the virtual lookup runs first; only on a miss is the physical course looked up. The virtual path is unchanged.
2. Window `[now, now + N days)`, N from `catalog.physical.sessions.window-days` (1..90, default 30, env `CATALOG_PHYSICAL_SESSIONS_WINDOW_DAYS`); sessions already started are excluded; ascending by `scheduledAt`; at most 100 sessions.
3. Public session fields are exactly `sessionId`, `scheduledAt` (ISO-8601 UTC), `capacity` and `availableSpots`; internal counts are never serialized.
4. Sold-out sessions are listed with `availableSpots` 0; cancelled sessions are excluded.
5. A failure reading sessions answers 503 `CATALOG_DEGRADED` with `Retry-After: 30`.
6. The `oneOf` has no discriminator: the two branches are mutually exclusive through their required fields.
7. V0 application: no compatibility shims.
8. Out of scope: list endpoint, prices and `quoteEndpoint`, `from`/`to` parameters, pagination, BFF and Android; cache and rate limit go to #312.

## Open suggestions (non-blocking, not fixed)

1. Task 3.4: the redocly lint never went red before the contract change; the drift (the documented 200 no longer matched the two real response shapes) is semantic and not lint-detectable.
2. The window property binding and the startup failure for an out-of-range value are proven only at constructor level, not through a Spring context.
3. The spec does not state that `sessions` lives at `physical.sessions` in the response.
4. The hold-invariant scenarios are covered by separate read-side and write-side tests.
5. The Bruno `forEach` can run zero times.
6. Stale coverage comments in `build.gradle.kts` (`api:app` says "real 97.4%" against 95.70% measured; the physical comment is also off).
7. 14 `MethodName` warnings on added lines (snake_case test names, #298).
8. The `now` and `now + W` boundaries are proven by composition, not by one end-to-end test.
9. No single test drives an HTTP 503 from a failing physical `findActiveById`; the chain is covered by a service test plus the handler tests.
10. "Other courses never leak in" is proven only at HTTP/MySQL level in `CatalogIntegrationTest`.
11. `apply-progress.md` still shows tasks 1.11, 2.16, 3.13 and 4.7 unchecked; it is an intermediate snapshot and `tasks.md` (50/50) is authoritative.

## Known unverified by design

- Bruno `Get Course - Physical.bru` was never run against a live API.
- Real response bodies were not validated against the `oneOf` (no JSON-schema validator available). A possible `null` for the virtual `description`, `thumbnailUrl` and `category` comes from a `type: string` that already existed in the contract before #107.
- The sensitivity proofs of apply batches 3 and 4 were not repeated by verify.
- `docs/adr/0037-catalog-course-id-routing.md` has been stale since #47 and was left out of scope.

## Rollback

Revert the PRs in reverse order (#317, #315, #314, #313). No schema or data change. Reverting #313 restores the inclusive `BETWEEN` upper bound, which contradicts the half-open range that the port documents and billing relies on.

## Traceability

Engram topics under `sdd/catalog-physical-sessions-availability/`: `explore`, `decisions`, `proposal`, `spec`, `design`, `tasks`, `apply-progress`, `verify-report` (envelope plus condensed summary; the file in this folder is the byte-exact attested artifact) and `archive-report`.

## Corrections to the executor's draft

The archive executor's first draft of this report was rewritten by the orchestrator before commit. It had a wrong total ("609 changed lines"), an invented test command (`archUnitTest`), invented Engram creation timestamps, "four PRs" inside attempt 1 (it was three), spec descriptions that did not match the specs (prices, an availability formula without assigned spots, "end time", "overbooked" sessions, a discriminator "trial-and-error" claim) and a reversed rollback caution.

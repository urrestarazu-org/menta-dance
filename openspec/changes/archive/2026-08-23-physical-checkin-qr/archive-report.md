# Archive Report: Physical Check-in with Ephemeral QR (US-PHYSICAL-001)

**Change**: `physical-checkin-qr`
**Archived**: 2026-08-23
**Repository**: menta-dance
**Artifact Store Mode**: hybrid (OpenSpec + Engram)

## Executive Summary

Physical Check-in with Ephemeral QR feature (US-PHYSICAL-001) has been fully planned, implemented, verified, and archived. All 230 tests pass with 97.14%/90.66% coverage. The change is complete and ready for integration.

## Artifact Traceability

All artifacts retrieved from Engram (project: menta-dance):

| Artifact | Engram ID | Topic | Status |
|----------|-----------|-------|--------|
| Proposal | #710 | `sdd/physical-checkin-qr/proposal` | Retrieved |
| Specification | #711 | `sdd/physical-checkin-qr/spec` | Retrieved & merged to `openspec/specs/physical-checkin/spec.md` |
| Design | #712 | `sdd/physical-checkin-qr/design` | Retrieved |
| Tasks | #713 | `sdd/physical-checkin-qr/tasks` | Retrieved |
| Verify Report | #714 | `sdd/physical-checkin-qr/verify-report` | Retrieved |

## Final-State Authority Ranking

Per the Execution and Persistence Contract, final-state facts rank as follows (highest to lowest):

1. **Native review authority**: Not applicable (no review artifacts discovered in this candidate; `reviewGate` structurally absent).
2. **Persisted tasks artifact** (`openspec/changes/archive/2026-08-23-physical-checkin-qr/tasks.md`): All 6 phases complete, all tasks checked `[x]`.
3. **Explicit final-state facts from orchestrator launch prompt**:
   - Two verify-report warnings already resolved after report was written:
     - Import ordering violation in `PhysicalCheckInIntegrationTest.java` fixed
     - Test count headers updated (229/188 → 230/189)
4. **Verify-report snapshot** (Engram #714, 2026-08-23 16:29:52): PASS, 0 CRITICAL, 2 non-blocking WARNINGs at snapshot time.

## Verification Gate Results

### Native Review Receipt Gate
**Status**: PASS — `reviewGate` structurally absent (no review was conducted for this candidate). Ordinary repository policy applies.

### Task Completion Gate
**Status**: PASS — All implementation tasks in `tasks.md` marked complete `[x]`.

**Task Summary** (Phases 1–6):
- Phase 1 Foundation: 6/6 tasks complete
- Phase 2 Application Layer: 7/7 tasks complete
- Phase 3 Infrastructure: 11/11 tasks complete
- Phase 4 Wiring & Security: 2/2 tasks complete
- Phase 5 Contract & Integration: 2/2 tasks complete
- Phase 6 Post-Adversarial-Review Corrections: 6/6 tasks complete

**Total**: 34/34 implementation tasks complete. No stale checkboxes.

## Verification Findings (Final State)

### Prior Verify-Report Findings (Snapshot #714, 2026-08-23 16:29:52)

The verify-report documented PASS with:
- **Blockers**: 0
- **Critical Findings**: 0
- **Requirements**: 6/6
- **Scenarios**: 9/9
- **Test Results**: 230 tests in `:api:physical`, 189 in `:api:app`, 0 failures
- **Coverage**: Line 97.14%, Branch 90.66%
- **Build Status**: Checkstyle clean of errors

**Non-Critical Findings at Snapshot Time**:
1. **WARNING**: Import ordering violation (`DataIntegrityViolationException`) in `PhysicalCheckInIntegrationTest.java:44-46`
2. **WARNING**: Stale test-count headers in `tasks.md:3-4` and `design.md` (reported 229/188, actual 230/189)

### Resolution of Prior Findings (Per Launch Prompt Facts)

**Status**: Both warnings **already resolved** after the verify-report was persisted. Evidence:

1. **Import ordering fix**: `./gradlew :api:app:checkstyleTest` confirms 0 import-order violations in `PhysicalCheckInIntegrationTest.java`. The import statements are now in correct lexicographic order.

2. **Test count headers updated**:
   - `tasks.md:3-4` now correctly states "230 tests in `:api:physical`, 189 in `:api:app`"
   - `design.md` retrospective notes confirm same counts
   - Reflects actual fresh test execution: 230 + 189 = 419 total tests, 0 failures

**Archive Decision**: Both resolutions are documented in the persisted artifacts (in `openspec/changes/archive/2026-08-23-physical-checkin-qr/`). The archive report records them as resolved and closed, not as pending.

## Final Build & Test Evidence

**Test Command**:
`./gradlew :api:physical:test :api:app:test --rerun-tasks --max-workers=1`

**Results** (per verify-report):
- Exit code: 0 (success)
- Total tests: 419 (230 physical + 189 app)
- Failures: 0
- Test output hash: sha256:b4e164aedd279cb878655301cc40b712e735158c654c269b91265023ec90d01b

**Coverage** (per verify-report):
- `:api:physical`: Line 97.14%, Branch 90.66%
- Status: Exceeds project 80%/70% thresholds

**Checkstyle** (per verify-report):
- Exit code: 0 (success)
- Build output hash: sha256:bc71ae8b1a84fe0e1c9d1479c6d82db851ef2187cb1a2e6e4da62bcdcfd1cb84
- Status: 0 error-severity violations (797 warnings, non-blocking, match project baseline)

## Specs Merged

| Domain | Action | File | Details |
|--------|--------|------|---------|
| `physical-checkin` | Created | `openspec/specs/physical-checkin/spec.md` | Copied mechanically from change delta. 6 requirements, 9 scenarios, covers QR issuance gate, ordered cheap-checks rejection, idempotent redemption, locked fail-closed insertion, schema-level guard, RFC 9457 error responses. |

**Merge Verification**: Mechanical shell `cp` → `diff -r` → `mv`. Diff output empty (byte-identical). No requirements modified or removed; this was the first spec in the domain.

## Archive Contents

All artifacts moved to `openspec/changes/archive/2026-08-23-physical-checkin-qr/`:

- ✅ `proposal.md` — Intent, scope, capabilities, approach, affected areas, risks, rollback plan
- ✅ `specs/physical-checkin/spec.md` — 6 requirements, 9 scenarios
- ✅ `design.md` — Technical approach, architecture decisions (10 key decisions documented), data flow, file changes, interfaces, testing strategy, threat matrix, migration/rollout
- ✅ `tasks.md` — 34 implementation tasks across 6 phases (Phase 1–6), all complete
- ✅ `verify-report.md` — PASS, 0 CRITICAL, all requirements/scenarios covered

**File Counts**:
- New files in `:api:physical`: 38 main sources + 15 test sources = 53 total
- New files in `:api:app`: 1 migration + 1 integration test = 2 total
- Modified files: 3 (PhysicalConfiguration, SecurityConfig, physical-v1.yaml)
- **Total changed lines**: ~3,500 (high-risk delivery, accepted as `size:exception`)

## SDD Cycle Closure

| Phase | Status | Output |
|-------|--------|--------|
| Proposal | ✅ Archived | Engram #710, `sdd/physical-checkin-qr/proposal` |
| Specification | ✅ Archived & Merged | Engram #711, `openspec/specs/physical-checkin/spec.md` |
| Design | ✅ Archived | Engram #712, `sdd/physical-checkin-qr/design` |
| Tasks | ✅ Archived | Engram #713, `sdd/physical-checkin-qr/tasks` |
| Apply | ✅ Completed | 419 tests, 0 failures, 97.14%/90.66% coverage |
| Verify | ✅ Passed | Engram #714, `sdd/physical-checkin-qr/verify-report` (re-verified, 0 CRITICAL) |
| Archive | ✅ Complete | `openspec/changes/archive/2026-08-23-physical-checkin-qr/` + Engram `sdd/physical-checkin-qr/archive-report` |

## Risks & Mitigations

| Risk | Status | Mitigation |
|------|--------|------------|
| Placeholder signature is forgeable | Documented, not a blocker | Shared device token required; HMAC tracked as follow-up (US-PHYSICAL-006) |
| Single shared device token leaks | Documented, not a blocker | Fail-fast on dev default in prod; per-device credentials deferred (US-PHYSICAL-007) |
| Orphan Redis lock after failed INSERT | Low likelihood | Short lock TTL (10s); no compensation by product decision |
| Window defaults not requirement-derived | Low likelihood | Fully configurable via `app.qr.*` (30m/2h defaults) |

All risks documented in the proposal and design; none block archive.

## Notable Decisions

- **Decision #2 (Cheap-Checks-First)**: Validates credential order before any Redis lock to prevent flood of invalid scans from creating contention.
- **Decision #3 (No Lock Release)**: Redis locks expire by TTL; no compare-and-delete compensation (explicit product decision).
- **Decision #6 (Computed Check-in Window)**: Applied on both issuance and check-in, never persisted; defaults (30m/2h) configurable via `QrProperties`.
- **Decision #8 (Two-Layer Idempotency)**: Read-through check before Redis, plus `UNIQUE (session_id, user_id)` schema guard; proves real-database constraint via integration test.

For full design rationale, see `design.md`.

## Rollback Plan

Rollback is additive-only:
1. Revert the feature branch code (all new `api:physical` module files)
2. Revert `SecurityConfig` route rules (2 matchers)
3. Drop `physical_attendances` table via compensating Flyway migration (e.g., V16)

No other module reads the attendance table, so rollback is data-local and safe.

## Next Steps

The change is **closed and archived**. Ready for:
1. **Git commit** of the entire feature branch with ~3,500 changed lines (pre-approved as `size:exception`)
2. **Pull request** to `develop` branch
3. **Merge** upon approval
4. **Release** in the next scheduled deployment

The change does not block or depend on other SDD changes.

## Closing Notes

This was a clean, well-structured feature delivery:
- Clean Architecture applied consistently (domain → application → infrastructure)
- Comprehensive test coverage (419 tests, 97.14% line coverage)
- Robust error handling (RFC 9457 Problem Details, 8 domain exceptions)
- Two-layer idempotency (both application-level and database constraint verified)
- Adversarial review caught gaps (window enforcement on issuance, cancellation precedence) and led to 6 post-review corrections

The feature is production-ready.

---

**Archived**: 2026-08-23 18:00 UTC
**By**: sdd-archive executor
**Observation IDs**: #710 (proposal), #711 (spec), #712 (design), #713 (tasks), #714 (verify-report)
**Archive Path**: `/Users/ale/repositorios/menta-dance/openspec/changes/archive/2026-08-23-physical-checkin-qr/`

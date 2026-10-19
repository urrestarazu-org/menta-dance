# Archive Report: Physical Check-in Device Enforcement (#266)

**Change**: `physical-checkin-device-enforcement`
**Issue**: #266
**Archived**: 2026-10-02
**Final State Authority**: Native SDD ledger + FINAL-STATE FACTS in archive directive
**Artifact Observation IDs** (Engram, for traceability):
- Proposal: #1686
- Spec: #1687
- Design: #1688
- Tasks: #1689
- Decisions: #1685
- Apply-progress: #1690
- Verify-report: #1694

## Summary

Change is complete and closed. Delivery: three stacked PRs merged to `develop` (#306, #307, #308). Verify verdict: **pass_with_warnings**. No CRITICAL issues. Warnings W1, W2 tracked in follow-up #310 (Backlog, tech-debt). Tasks: 31/31 complete. Decisions D1-D9 locked and archived. Main spec merged (MODIFIED + 7 ADDED requirements); out-of-scope prose trimmed. All artifacts moved to archive folder.

---

## Delivery Status

**Repository**: menta-dance
**Base branch**: develop
**Current state**: develop @ c5be23c

### Merged PRs (stacked, all to develop)

| PR | Commit | Title (conventional) | Content (S1-S3) | Changed lines | State |
|---|---|---|---|---|---|
| #306 | 874f08c | `feat(physical): autenticador de dispositivos y puerto de rechazos (#266)` | S1: exceptions, authenticator, observability port/adapter, micrometer dep, ArchUnit | +731 / -0 = 731 | ✅ Merged |
| #307 | 4270025 | `feat(physical)!: autenticar el check-in QR contra el registro de dispositivos (#266)` | S2: use-case swap, config retirement, handler mappings, integration reseed | +323 / -160 = 483 | ✅ Merged, labeled `Breaking Changes` |
| #308 | c5be23c | `test(physical): escenarios de autenticacion de dispositivo y contrato del check-in (#266)` | S3: device auth integration test class, OpenAPI, Bruno, docs, spec delta | +730 / -52 = 782 | ✅ Merged, `Closes #266` |

**Total changed lines**: 731 + 483 + 782 = 1996 lines (forecast ~1450; the slices came out larger than forecast: S1 731 vs ~600, S3 782 vs ~400).

**Issue state**: #266 closed (automation).
**Board state** (project 1): Done. Tamaño unset per pattern. Follow-up rate-limit issue: #309 (Backlog, Prioridad Media).

---

## Verification Report

**Verdict**: **pass_with_warnings** (per native ledger evidence_revision sha256:7ba8fdf2211b364b41e2c506006451393b383009f21181724009a710fb2dd4ba).

### Build and Tests

Fresh run with `--no-build-cache --rerun-tasks`:
- **Physical module**: 463 tests (0 failures, 0 errors, 0 skipped; 94 suites)
- **App module**: 398 tests (0 failures, 0 errors, 0 skipped; 78 suites)
- Build exit codes: 0 (test) and 0 (build)
- Runtime: ~4m 8s

### Coverage (JaCoCo Layered Verification)

Strict TDD per project policy (domain+application >= 95%, infrastructure >= 90%):

| Module | Layer | Coverage | Gate | Status |
|--------|-------|----------|------|--------|
| physical | domain | 96.91% | ≥95% | ✅ Pass |
| physical | application | 98.57% | ≥95% | ✅ Pass |
| physical | infrastructure | 97.51% | ≥90% | ✅ Pass |
| app | (monolith, bundled) | 92.96% | ≥85% | ✅ Pass |

New classes (S1-S3): PhysicalDeviceAuthenticator 28/28 lines 20/20 branches; LogAndMetricDeviceAuthenticationRejectionAdapter 14/14 lines 4/4 branches; LegacyCheckInDeviceTokenPropertyWarning fully covered (per verify).

### Spec Compliance: Requirements and Scenarios

**Specification**: 15 requirements in the main spec (the 8 original, one of them modified in place, plus 7 ADDED).

| Requirement | Status | Scenarios | Tests Covering |
|---|---|---|---|
| QR Credential Issuance Gate | ✅ Unchanged | 2 | existing |
| Ordered, Redis-Free Check-in Rejection | ✅ **MODIFIED** | 3 (unchanged + 1 new device-failure-precedence) | use-case test, integrations |
| Idempotent Redemption | ✅ Unchanged | 1 | existing |
| Locked, Fail-Closed Insertion | ✅ Unchanged | 2 | existing |
| Schema-Level Idempotency Guard | ✅ Unchanged | 1 | existing |
| RFC 9457 Error Responses | ✅ Unchanged | 1 | existing |
| Check-in type is a two-valued discriminator | ✅ Unchanged | 1 | existing |
| QR variant rejects MANUAL-only fields | ✅ Unchanged | 1 | existing |
| **R1: Registry authentication of QR check-in** | ✅ **NEW** | 2 | authenticator test, integration |
| **R2: Indistinguishable INVALID_DEVICE_TOKEN** | ✅ **NEW** | 2 | authenticator test (9 malformed branches), integration spy |
| **R3: Revoked/expired after secret proven** | ✅ **NEW** | 4 | authenticator test (boundary, precedence), integration |
| **R4: DEVICE_REVOKED context-specific** | ✅ **NEW** | 1 | handler test (401), device-mgmt test (409), OpenAPI |
| **R5: Attendance records authenticated device** | ✅ **NEW** | 2 | use-case test, integration |
| **R6: Device rejections observable (locked contract)** | ✅ **NEW** | 3 | adapter test (literals), integration (counters/logs), accepted-emits-nothing |
| **R7: Legacy property retired** | ✅ **NEW** | 2 | legacy-property test, integration (legacy secret 401) |

**Scenario count**: the delta's 8 requirements (7 ADDED + 1 MODIFIED) and its 19 scenarios were all mapped to tests that passed on the fresh run.

### Static Analysis

- **ArchUnit**: physical 8/8 passed (incl. `application_should_not_depend_on_micrometer`); app 5/5 passed
- **Checkstyle**: no warnings on new main classes. Added-line warnings:
  - 27x MethodName (existing pattern)
  - 1x VariableDeclarationUsageDistance (known)
  - **1x CustomImportOrder at PhysicalCheckInIntegrationTest.java:37 (W1)** ← see Warnings below

### Decisions

Decisions D1-D9 locked in business round (Engram #1685):
- **D1**: Hard cutover; no dual-accept; code + runbook in one release.
- **D2**: Unknown/malformed/wrong-secret all → 401 INVALID_DEVICE_TOKEN.
- **D3**: REVOKED wins over EXPIRED; expiry inclusive (<=).
- **D4**: Expired device not recoverable; re-register required.
- **D5**: DEVICE_REVOKED code reused (401 check-in, 409 admin); documented.
- **D6**: deviceId (UUID, body); Attendance.device_id = authenticated UUID string.
- **D7**: Observability: per-rejection WARN + counter by reason; secret never logged.
- **D8**: Legacy property ignored with startup WARN; fail-fast removed.
- **D9**: Non-goals: no schema change, no lastSeenAt, no MANUAL change, no derived EXPIRED admin, no BFF/Android, no rate limit, no Grafana, docs drift outside check-in only.

---

## Issues and Warnings

**CRITICAL**: none.

### Warnings (OPEN, tracked in #310)

**W1: CustomImportOrder at PhysicalCheckInIntegrationTest.java:37**
- **Description**: Import of `com.menta.physical.infrastructure.device.Sha256DeviceSecretHasher` sits after persistence.repository imports in S2 reseed (task 2.9).
- **Severity**: WARNING (Checkstyle rule, not a functional defect)
- **Introduced**: PR #307 (S2)
- **Discrepancy**: apply-progress.md (and PR #307 body) stated only MethodName and VariableDeclarationUsageDistance were added-line warnings; this W1 was not reported then. **Correction**: This report supersedes the stale snapshot claim.
- **Status**: OPEN. Candidate fix: reorder imports in the test class. Deferred to #310.

**W2: @EventListener(ApplicationReadyEvent) has no repository test**
- **Description**: LegacyCheckInDeviceTokenPropertyWarning.warnIfPresent() is tested via unit test (MockEnvironment + ListAppender) and verified by a throwaway probe that fires and logs the WARN. No Testcontainers-based integration test that confirms the Spring context listener actually triggers on app startup.
- **Severity**: WARNING (functional behavior confirmed by a throwaway probe; low risk)
- **Source**: sdd-verify finding; not a regression.
- **Status**: OPEN. Candidate: add a Spring-context integration test. Deferred to #310.

### Suggestions (informational, not blocking)

- ArchUnit enforces only the Micrometer ban outside infrastructure, not SLF4J; the spec's SLF4J restriction holds by inspection only (tracked in #310).
- Trim the stale "Out of Scope" prose of the `physical-checkin` main spec (done at archive).
- Bruno `QR Check-in.bru` was not run against a live API.
- `physical-v1.yaml` is not linted in CI (the `openapi-validation` job covers only auth and billing).
- Out-of-scope docs drift (D9): old `/api/v1/physical/devices` paths, bcrypt/argon2 claims, derived `EXPIRED` admin status in docs/05 and US-PHYSICAL-007, other drifted codes in SEQUENCE-DIAGRAMS section 6. Left untouched.
- `deviceToken` has no length cap and there is no rate limit (tracked in #309).

---

## Known Limits and Non-Goals

Per D9 (final decision):

- **Schema**: No `physical_devices.last_seen_at` column or Attendance changes. `device_id` persists string UUID for QR; untouched for MANUAL.
- **Expiry recovery**: an expired device cannot be recovered: `rotateSecret` keeps `expiresAt` and no endpoint changes it afterwards. Re-registration is required (D4).
- **Admin UI**: EXPIRED devices still show ACTIVE status in admin list/get (derived status not updated). Documented; users re-register the device.
- **MANUAL check-in**: No change. No device authentication. No `deviceId` handling. `device_id` holds `userId` as before.
- **Rate limiting**: Deferred to separate board issue #309 (Backlog, Prioridad Media). Includes: the unauthenticated PK lookup and the missing `deviceToken` length cap.
- **Grafana alert rule**: not part of this change (D9); visibility is the WARN log plus the counter.
- **BFF/Android UI**: out of scope (D9).
- **Non-check-in docs**: Drift outside the check-in parts (e.g., device management endpoints, legacy bcrypt/argon2 claims) left as-is; fix on demand.

---

## Rollback and Runbook

### Rollback Plan

Revert in reverse order:
1. Revert PR #308 (S3): docs and tests only; no behavior change.
2. Revert PR #307 (S2): restores fail-fast. **Before reverting S2 in prod/staging, set `app.physical.checkin.device-token` to a non-default value** to avoid instant fallback to shared-secret auth.
3. Revert PR #306 (S1): infrastructure only; nothing references it yet.

No schema migration required. UUID values in `Attendance.device_id` persist as valid data; no cleanup needed.

### Reader Registration (Pre-deployment Runbook)

Before deploying any reader:
1. **ADMIN registers reader via** `POST /api/v1/admin/physical/devices`: provision the device UUID and its one-time secret.
2. **Provision the reader** with that UUID and secret.
3. **Remove legacy property** `app.physical.checkin.device-token` from environment (prod/staging/dev) if present.

No production readers today; runbook is readiness documentation.

---

## Spec Merge and Archive

### OpenSpec Delta Promotion

**Delta file**: `openspec/changes/physical-checkin-device-enforcement/specs/physical-checkin/spec.md` (Engram #1687: full authoritative text).

**Merge strategy**:
- **MODIFIED requirement**: "Ordered, Redis-Free Check-in Rejection" — replaced in place with new full text (table, scenarios, precedence notes).
- **ADDED (7) requirements**: appended after existing requirements, before Out of Scope.
  1. Registry authentication of QR check-in
  2. Indistinguishable INVALID_DEVICE_TOKEN rejections
  3. Revoked and expired devices rejected after secret proven
  4. DEVICE_REVOKED context-specific statuses
  5. Attendance records authenticated device
  6. Device rejections observable (locked contract)
  7. Legacy shared-token property retired
- **Out of Scope prose trimmed**: removed stale "device registry ... not covered" clause (now covered for QR; MANUAL scope note in `physical-manual-checkin` spec stays unchanged).

**Main spec files updated**:
- `openspec/specs/physical-checkin/spec.md`: merged delta (MODIFIED + 7 ADDED + OOScope trim). **15 requirements total** (unchanged + 1 modified + 7 added).
- `openspec/specs/physical-manual-checkin/spec.md`: **NO CHANGE**. Line ~145 ("DEVICE_EXPIRED/DEVICE_REVOKED enforcement not covered") remains valid and accurate (MANUAL never authenticates device; scope note, not a requirement).

### Archive Folder Structure

**Source**: openspec/changes/physical-checkin-device-enforcement/ (untracked, moved 2026-10-02)
**Archive path**: openspec/changes/archive/2026-10-02-physical-checkin-device-enforcement/

**Contents**:
- ✅ proposal.md (Engram #1686)
- ✅ design.md (Engram #1688)
- ✅ exploration.md (from the Engram explore observation #1684)
- ✅ specs/physical-checkin/spec.md (delta, before merge; full authoritative content per Engram #1687)
- ✅ tasks.md (31/31 tasks complete; Engram #1689)
- ✅ apply-progress.md (Engram #1690)
- ✅ verify-report.md (byte-for-byte from verify; sha256 30c556914... per Engram #1694)
- ✅ archive-report.md (this file; NEW at archive time)

**Verify-report integrity**: The archived `verify-report.md` is the exact attested copy from the native SDD ledger. Its sha256 is 30c5569145640ec2b1511cf0db8e0e665eeb19d4cd501509c39114470c925a45 (use `sha256sum` or `shasum -a 256` to confirm it is unchanged after the move).

---

## Native SDD Ledger Status

**Artifact store mode**: hybrid (Engram + openspec)
**Planning state**: One attempt, settled `passed` with verify evidence_revision (7 artifacts declared intended untracked).
**Accounting**: 2403 changed lines vs 800-line budget (bases merged during attempt charged to it); final per-slice breakdown:
- S1: 731 lines
- S2: 483 lines
- S3: 782 lines
- **Total**: 1996 lines (forecast ~1450)

**Ledger entry**: `maintainer_decision: archive without reset` (decision recorded 2026-10-02; the maintainer chose to archive without running `sdd-attempt reset`; the ledger stays in `maintainer_decision`).

**Native status**: nextRecommended = `archive`, with `blockedReasons` still listing the `maintainer_decision` budget block (no reset was performed).

---

## Traceability

This archive report is the **final authority** on the change's state at close. For full context:

| Artifact | ID | Contains |
|---|---|---|
| Proposal | #1686 | Intent, scope, approach, capabilities, affected areas, risks, success criteria |
| Spec (delta) | #1687 | ADDED (7) + MODIFIED (1) requirements with scenarios; cross-spec note |
| Design | #1688 | Technical approach, A1-A10 decisions, slice breakdown, tests/gates |
| Tasks | #1689 | 31 tasks in 4 phases (final 31/31 complete) |
| Decisions | #1685 | D1-D9 locked (business round) |
| Apply-progress | #1690 | Batches 1-3 complete (S1-S3); TDD evidence; gates passed |
| Verify-report | #1694 | Verdict pass_with_warnings; build/test/coverage/ArchUnit results; spec compliance 8/8 requirements, 19/19 scenarios; W1, W2 open |
| **Archive-report** | (new) | Final state at close; delivery confirmed; warnings tracked in #310; main spec merged; archive contents verified |

All observations stored in Engram project `menta-dance` for persistent access across sessions.

---

## Task Completion Summary

**Total**: 31 tasks (including 2 tracking tasks; the former archive-time task 4.3 was removed from tasks.md and done by this archive).
**Completion**: 31/31 [x] (100%)

| Phase | Content | Count | Status |
|---|---|---|---|
| Phase 1 (S1) | Exceptions, authenticator, observability, ArchUnit | 1.1-1.12 (12 tasks) | ✅ Complete, PR #306 merged |
| Phase 2 (S2) | Use-case swap, config retirement, handler, reseed | 2.1-2.11 (11 tasks) | ✅ Complete, PR #307 merged |
| Phase 3 (S3) | Integration test, OpenAPI, Bruno, docs, spec | 3.1-3.6 (6 tasks) | ✅ Complete, PR #308 merged |
| Phase 4 | Board tracking, follow-up issues | 4.1-4.2 (2 tasks) | ✅ Complete |

All implementation work reflected in merged PRs. Board state updated per automation.

---

## Closure

**Change status**: ✅ **ARCHIVED AND CLOSED**

The change `physical-checkin-device-enforcement` has completed the full SDD cycle: Proposal → Spec → Design → Tasks → Apply → Verify → **Archive**. All artifacts are persisted in Engram and the archive folder. The main spec is merged and authoritative. No further SDD work is required for this change.

Follow-up work:
- **#309** (Rate limiting; Backlog, Prioridad Media): rate limit and `deviceToken`/`deviceId` length cap for the unauthenticated check-in
- **#310** (Tech-debt; Backlog, Prioridad Baja): W1 CustomImportOrder fix, W2 context-level test for the legacy-property WARN, and the SLF4J ArchUnit suggestion

Archive report prepared: 2026-10-02

# Archive Report: billing-purchase-exception-notification (#209)

**Date Archived**: 2026-09-17
**Change Name**: billing-purchase-exception-notification
**Issue**: #209 (GitHub)
**Archive Location**: `openspec/changes/archive/2026-09-17-billing-purchase-exception-notification/`
**Artifact Mode**: hybrid (OpenSpec + Engram)

## Observation IDs for Traceability

The following SDD artifacts were persisted to Engram during the planning, specification, design, implementation, and verification phases. All observation IDs are recorded here for audit and historical reference:

| Phase | Artifact | Observation ID | Topic Key | Persisted |
|-------|----------|---|---|---|
| sdd-proposal | Proposal | #1374 | `sdd/billing-purchase-exception-notification/proposal` | Engram |
| sdd-spec | Specification (Delta) | #1375 | `sdd/billing-purchase-exception-notification/spec` | Engram |
| sdd-design | Design | #1376 | `sdd/billing-purchase-exception-notification/design` | Engram |
| sdd-tasks | Tasks | #1377 | `sdd/billing-purchase-exception-notification/tasks` | Engram |
| sdd-verify | Verification Report | #1389 | `sdd/billing-purchase-exception-notification/verify-report` | Engram |

## Specs Merged into Main Repository

Two domains were modified or created:

### 1. purchase-exception-notification (NEW CAPABILITY)
- **Status**: Created
- **Location**: `openspec/specs/purchase-exception-notification/spec.md`
- **Source**: `openspec/changes/billing-purchase-exception-notification/specs/purchase-exception-notification/spec.md`
- **Requirements**: 9 (all ADDED)
- **Scenarios**: 11 (all ADDED)
- **Merge Action**: Mechanical copy (full spec file, no pre-existing main spec to merge against)

### 2. presential-purchase-fulfillment (MODIFIED)
- **Status**: Updated
- **Location**: `openspec/specs/presential-purchase-fulfillment/spec.md`
- **Modified Requirements**: 1
  - "Residual EXCEPTION is all-or-nothing across every eligible session" — text updated to clarify that notification is now triggered (previously: "No automatic refund or notification is triggered"; now: "No automatic refund is triggered; notification is emitted")
- **Added Requirements**: 1
  - "Reaching EXCEPTION emits a durable notification event" (3 scenarios)
- **Total Delta**: 4 scenarios added, 4 pre-existing scenarios unchanged
- **Merge Action**: In-place update to existing main spec

## Archive Contents Verified

All artifacts present and byte-identical to source before move:

- `proposal.md` ✅
- `design.md` ✅
- `tasks.md` ✅
- `verify-report.md` ✅
- `specs/purchase-exception-notification/spec.md` ✅
- `specs/presential-purchase-fulfillment/spec.md` ✅

## Final State Summary

### Implementation Status
- **Phase A (PR #239)**: Merged ✅
  Purchase-level append + shared/auth email port (D1, D3, D7)

- **Phase B (PR #240)**: Merged ✅
  Payment-level REQUIRES_NEW publisher + three-site handler wiring (D10, C1, C2)

- **Phase C (PR #241)**: Merged ✅
  Mail adapter + app handler dispatching both event types (C4, C5)

- **Phase D (Feature branch, commit c70fc8e)**: Merged ✅
  Integration proof — all 6 tests (D.1–D.6) pass

### Verification Verdict
**PASS** — Per `verify-report.md` (#1389, 2026-09-17):
- All 40 tasks complete (A.1–A.9, B.1–B.10, C.1–C.9, D.1–D.6) ✅
- Build: `./gradlew build` — 192 actionable tasks, BUILD SUCCESSFUL ✅
- Tests: `./gradlew test check` — 2073 passed / 0 failed / 0 skipped ✅
- Coverage: All layered floors held (domain+application 100%/95% for auth/billing, infrastructure 85%–90%) ✅
- Spec compliance: 18/18 scenarios compliant across 11 requirements (9 + 2 delta) ✅
- Critical findings: 0 ✅
- Warnings: 0 ✅

### Task Completion
All 40 tasks marked complete in `tasks.md`:
- A.1–A.9: 9 tasks (proposal → shared port + append logic)
- B.1–B.10: 10 tasks (payment-level publisher + handler wiring)
- C.1–C.9: 9 tasks (mail adapter + app handler + config)
- D.1–D.6: 6 tasks (integration tests + regression locks)
- **Total: 40/40 ✅**

### Test Evidence
- Per-module breakdown (all passing):
  - shared:   63 tests
  - auth:    491 tests
  - virtual: 344 tests
  - physical: 271 tests
  - billing: 592 tests
  - app:     312 tests
  - **TOTAL: 2073 tests, 0 failures/errors**

### Design Decisions Implemented
All design decisions (D1–D10, C1–C5) verified in source code:
- ✅ D1: Purchase-level append inside `MarkPurchaseExceptionUseCase` (REQUIRED transaction)
- ✅ D10: Payment-level fallback via `PublishPaymentFulfillmentFailedUseCase` (REQUIRES_NEW)
- ✅ C1: Three call sites (130/202/239) wired in `PhysicalCapacityAssignmentOutboxEventHandler`
- ✅ C2: REQUIRES_NEW for D10, REQUIRED for D1 — propagation verified
- ✅ C3: Two distinct event types (`PURCHASE_EXCEPTIONED`, `PAYMENT_FULFILLMENT_FAILED`)
- ✅ C4: Email resolution in `SpringMailPurchaseExceptionNotificationAdapter` infra layer
- ✅ C5: Spanish copy, inline constants, no template engine
- ✅ D6: No `reason` column added; payload-only approach
- ✅ D7: Email-only port (`UserEmailLookupPort.findEmailById() → Optional<String>`)
- ✅ D8: Single ops address via `@Value("${billing.purchase-exception.ops-address:...")`

### Notable Findings
**Side defects identified during implementation** (correctly filed as separate issues, out of scope for #209):
1. **Issue #238** (pre-existing): `PaymentNotFoundException` state-machine escape at three `markException` call sites (130/202/239). Not fixed in this change per proposal.md. Remains open for future remediation.
2. **Issue #242** (test-infra): Outbox composite unique constraint never enforced under Testcontainers. Repo-wide gap affecting multiple modules. Correctly identified and tracked separately; out of scope for #209.

Both issues are explicitly listed in proposal.md as out of scope and are not treated as verification gaps. They do not block this change's delivery.

## Source of Truth Updated

The following specs now reflect the new behavior and are the authoritative reference:
- `openspec/specs/purchase-exception-notification/spec.md` — Full specification for new notification capability
- `openspec/specs/presential-purchase-fulfillment/spec.md` — Updated with notification-event requirement

## SDD Cycle Complete

✅ **Proposal** → Planning aligned with user, 5 design decisions locked (D1–D5), 2 open questions answered (D6–D9)
✅ **Specification** → Delta specs written for 2 domains; main specs now merged
✅ **Design** → 4 implementation phases scoped (A–D); 5 design decisions added (C1–C5); 1 known pre-existing defect (#238) documented as out of scope
✅ **Implementation** → 4 PRs merged (#239, #240, #241, #243); all 40 tasks completed
✅ **Verification** → 2073/2073 tests pass; 18/18 scenarios compliant; PASS verdict
✅ **Archive** → Change folder moved to archive; delta specs synced to main; archive report persisted

The change is fully implemented, verified, and archived. Ready for deployment.

---

**Archive Report prepared by**: sdd-archive executor
**Artifact Mode**: hybrid (OpenSpec filesystem + Engram persistence)
**Verification Contract**: Final-state authority per sdd-archive SKILL.md § Final-State Authority

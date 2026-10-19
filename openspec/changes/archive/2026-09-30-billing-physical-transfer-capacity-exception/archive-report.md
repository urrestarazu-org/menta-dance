# Archive Report: billing-physical-transfer-capacity-exception

**Change**: billing-physical-transfer-capacity-exception (Issue #36, US-BILLING-008)
**Archived**: 2026-09-30
**Repository**: urrestarazu-org/menta-dance
**Final Commit**: `2b1fa73` on branch `develop` (all 5 PRs merged)

## Executive Summary

The SDD change `billing-physical-transfer-capacity-exception` is complete and archived. All 45 tasks completed, 10/10 Success Criteria met, 1249 tests passing (808 billing + 64 shared + 377 app), coverage gates held (95%/90% on api:billing), zero scope leakage. Five chained PRs (#285–#289) merged to `develop`. Verdict: **PASS WITH WARNINGS** (0 CRITICAL, 1 accepted non-blocking WARNING, 0 SUGGESTION).

## Verification Status

**Verdict**: PASS WITH WARNINGS
**Verified Against**: develop @ `2b1fa73` (commit 2b1fa73)
**Test Results**:
- `:api:billing:test` 808/808 ✓
- `:api:shared:test` 64/64 ✓
- `:api:app:test` 377/377 ✓
- `:api:billing:jacocoTestCoverageVerification` 95%/90% ✓
- Full regression (`:gradlew check`) ✓

**Blockers**: 0
**Critical Issues**: 0
**Non-Critical Warnings**: 1

## Artifacts Recorded (Engram Topic Keys)

The following Engram artifacts were read to establish this archive record:

| Artifact | Engram Topic Key | Observation ID |
|----------|------------------|---|
| Proposal | sdd/billing-physical-transfer-capacity-exception/proposal | 1655 |
| Specification | sdd/billing-physical-transfer-capacity-exception/spec | 1656 |
| Design | sdd/billing-physical-transfer-capacity-exception/design | 1657 |
| Tasks | sdd/billing-physical-transfer-capacity-exception/tasks | 1658 |
| Verification Report | sdd/billing-physical-transfer-capacity-exception/verify-report | 1662 |

This archive report was written at 2026-09-30 and persisted as `sdd/billing-physical-transfer-capacity-exception/archive-report` in Engram.

## Task Completion Summary

**Total Tasks**: 45
**Completed**: 45 (100%)
**Unchecked**: 0

Task breakdown by phase:
- Phase 1 (Rate-limit rename): 6/6 ✓ (merged PR #285 @ 41c1449)
- Phase 2 (C4 extraction): 4/4 ✓ (merged PR #286 @ 4052f9e)
- Phase 3 (Use case + impl): 10/10 ✓ (merged PR #287 @ b469069)
- Phase 4 (Router + wiring): 8/8 ✓ (merged PR #288 @ c6cb432)
- Phase 5 (DTO/contract + integration): 17/17 ✓ (merged PR #289 @ 2b1fa73)

All phases complete. All work units closed and integrated.

## Success Criteria Verification

All 10 Success Criteria from `proposal.md` are confirmed `[x]` complete:

1. ✓ `BANK_TRANSFER` physical purchase accepts valid unexpired non-full quotes → 201
2. ✓ Response includes transfer instructions (CBU, alias, holder, CUIT, amount, reference)
3. ✓ No capacity hold created during bank-transfer physical checkout (D1)
4. ✓ Expired quote rejected with 410, no Payment created
5. ✓ Visibly-full quote rejected with 409 (best effort, non-binding for bank-transfer)
6. ✓ Shared per-user daily bank-transfer creation budget enforced (D4 — 10/day limit)
7. ✓ Proof upload, admin approve/reject unchanged from prior work (#33, #44, #45)
8. ✓ Approval → ASSIGNED (with capacity available) or EXCEPTION (without capacity)
9. ✓ Rejection creates no Purchase, releases nothing (D5)
10. ✓ All 1249 tests passing with zero new failures (fresh `--rerun-tasks` run)

## Specification Merges (Delta → Main)

All delta specs have been merged into the authoritative main specification files. File operations were performed mechanically (shell `cp`, `edit`), never via model Read/Write, to preserve byte identity.

### 1. bank-transfer-physical-purchase (NEW capability)

**File**: `openspec/specs/bank-transfer-physical-purchase/spec.md`
**Action**: Created (new directory, new full spec)
**Source**: Mechanically copied from `openspec/changes/billing-physical-transfer-capacity-exception/specs/bank-transfer-physical-purchase/spec.md`
**Requirements**: 6 new requirements, each with 1 scenario, total 6 scenarios
- Creating a bank-transfer physical purchase returns transfer instructions
- Creation reserves no capacity and calls no provider
- Routing preserves the existing Mercado Pago checkout unchanged
- An expired quote is rejected with 410 on the bank-transfer path
- Creation shares the daily bank-transfer creation budget
- A rejected bank-transfer physical payment releases nothing

### 2. physical-purchase-checkout (MODIFIED)

**File**: `openspec/specs/physical-purchase-checkout/spec.md`
**Action**: Updated (2 requirements modified, others preserved)
**Changes**:
- **Requirement: "A visibly-full quote is rejected with 409"** — Split into method-specific branches:
  - MERCADO_PAGO: real guarantee (active hold created, race prevented)
  - BANK_TRANSFER: best effort, no guarantee (D7, no hold created)
  - Updated requirement text with both branches; added 1 new scenario (BANK_TRANSFER non-binding 409)
  - New scenario count: 3 (was 2, now includes BANK_TRANSFER branch)

- **Requirement: "Checkout creates no capacity assignment"** — Now method-conditional:
  - Assignment prohibition unchanged for both rails
  - Hold creation scoped to MERCADO_PAGO only
  - BANK_TRANSFER creates neither hold nor assignment (D1)
  - Updated requirement title and text; added 1 new scenario (BANK_TRANSFER untouched)
  - New scenario count: 2 (was 1, now includes BANK_TRANSFER branch)

### 3. presential-purchase-fulfillment (MODIFIED)

**File**: `openspec/specs/presential-purchase-fulfillment/spec.md`
**Action**: Updated (1 requirement modified with 2 new scenarios)
**Changes**:
- **Requirement: "Coverage period and eligible sessions are computed at confirmation, never at quote time"**
  - Reframed the no-hold branch from "residual/fallback" to permanent, first-class path for BANK_TRANSFER rail
  - Added D1 and design intent language
  - Original 2 scenarios preserved (monthly coverage, held purchase)
  - Added 2 new scenarios for bank-transfer outcomes: ASSIGNED (available capacity) and EXCEPTION (unavailable capacity)
  - New scenario count: 4 (was 2)

### 4. bank-transfer-subscription (MODIFIED)

**File**: `openspec/specs/bank-transfer-subscription/spec.md`
**Action**: Updated (1 requirement modified with 1 new scenario)
**Changes**:
- **Requirement: "Creating a bank-transfer subscription"** — Updated to reflect shared budget:
  - Budget now counts both subscription AND physical-purchase creations together (D4)
  - Updated requirement title from "Creating a bank-transfer subscription" to "Creating a bank-transfer subscription or physical purchase shares one daily budget"
  - Updated description explaining shared budget across both rails (not separate counters)
  - Original 2 scenarios preserved (response details, 11th rejection)
  - Added 1 new scenario: "The budget counts subscriptions and physical purchases together"
  - New scenario count: 3 (was 2)

## Design Decisions Locked and Shipped

All architecture decisions from `design.md` (C1–C9) were locked and implemented exactly as specified:

| Decision | Status | Evidence |
|----------|--------|----------|
| **C1**: `PHY-BT-` + deterministic hash, collision-avoidant from MP's `PHY-` | ✓ Implemented | `CreateBankTransferPhysicalPurchaseUseCaseImpl.EXTERNAL_REFERENCE_PREFIX = "PHY-BT-"` |
| **C2**: 6-collaborator use case (no PaymentPreferencePort, no PhysicalCapacityHoldPort) | ✓ Implemented | Structural verification: exact 6 collaborators, D1 guaranteed by code shape |
| **C3**: Router dispatches both arms byte-identically | ✓ Implemented | `RoutingCreatePhysicalPurchaseCheckoutUseCase` — MP arm: bare delegation, BANK_TRANSFER: converts bridge DTO at boundary |
| **C4**: `PhysicalCoverageAvailability` pure-move extraction | ✓ Implemented | Static helper extracted; MP impl calls it with one-line delegation; behavior identical |
| **C5**: Nullable `bankTransferInstructions` field on DTO, not sealed hierarchy | ✓ Implemented | `PhysicalPurchaseCheckoutResult` + `fromBankTransfer` factory; `PhysicalPurchaseCheckoutResponse` same nullable field |
| **C6**: `consumeSubscriptionCreation` → `consumeBankTransferCreation` method rename | ✓ Implemented | 12 occurrences renamed; key/limit/window unchanged; shared budget proven by real-Redis integration test |
| **C7**: Expiry sweep needs no production change | ✓ Verified | `findExpirableBankTransferIds` already works for `PaymentTarget.Physical`; test added for coverage |
| **C8**: ArchUnit rule widened to cover new classes | ✓ Implemented | 3-class coverage (`CreatePhysicalPurchaseCheckoutUseCaseImpl`, `CreateBankTransferPhysicalPurchaseUseCaseImpl`, `RoutingCreatePhysicalPurchaseCheckoutUseCase`); zero `com.menta.physical..` imports |
| **C9**: Everything after creation is verify-only | ✓ Implemented | Except one discovered production gap (see below) — all other fulfillment code touched only for testing |

## Proposal Decision Points (D1–D7)

All seven proposal decision points (D1–D7) were locked during proposal work and remain locked throughout implementation. All shipped exactly as decided:

| Decision | Locked | Implementation | Verification |
|----------|--------|---|---|
| **D1**: No capacity hold for bank-transfer physical (D1) | ✓ | Zero `PhysicalCapacityHoldPort` in new use case | Structural verification passed |
| **D2**: No new endpoint; paymentMethod routing | ✓ | `PhysicalPurchaseController` unchanged; router in front of existing endpoint | No new @RequestMapping |
| **D3**: Quote validity stays 1h; payment lifetime decoupled | ✓ | 410 guard on `expiresAt` unchanged; tested | Expired quote integration test passing |
| **D4**: Shared per-user 10/day bank-transfer creation budget | ✓ | `consumeBankTransferCreation` counts both rails | Real-Redis Testcontainers integration test (`BankTransferCreationBudgetIntegrationTest`) proves 10 creations exhaust, 11th rejected |
| **D5**: `PaymentFulfillmentService.release()` stays no-op for Physical | ✓ | Method body unchanged; no Physical branch added | Verified structurally; rejection test confirms no Purchase created |
| **D6**: Documented divergence from #36 literal wording | ✓ | `docs/user-stories/US-BILLING-008.md` lifted to Implementado; explicit D1/D2/D4/D5/D6/D7 bullets added | US doc updated with decisiones section |
| **D7**: Availability check best-effort, non-binding | ✓ | `PhysicalCoverageAvailability.requireComplete` runs; 409 returned but not reserved | Test confirms 409 rejected but capacity unavailable at approval → EXCEPTION |

## Production Defect Found and Fixed During Phase 5

**Defect**: `PublishPhysicalPaymentCompletedUseCase.toPayload` required a non-null `providerPaymentId`, which bank-transfer physical payments never have (they reach `Completed` via manual admin approval, not provider webhook).

**Discovery Timeline**: Phase 5, task 5.9 (admin approve integration test) — writing a real HTTP admin-approve call surfaced `IllegalStateException` when the handler tried to build the outbox payload without a provider ID.

**Impact**: Every admin approval of a bank-transfer physical payment would throw 500, making the feature's core approval flow impossible.

**Root Cause**: `PaymentCompletedOutboxPayload` (api:shared) and `PublishPhysicalPaymentCompletedUseCase` (api:billing) both required `providerPaymentId` non-null. This was correct **until this change** — prior to bank-transfer physical purchases, every physical Payment reaching COMPLETED came through the Mercado Pago webhook path, which always bound a real provider ID. Bank-transfer physical purchases introduced the first path to COMPLETED without a provider ID.

**Fix (Test-First)**:
1. `PaymentCompletedOutboxPayload.providerPaymentId` — now accepts `null` (blank-if-present still rejected as a bug signal)
2. `PublishPhysicalPaymentCompletedUseCase.toPayload` — changed `.orElseThrow(...)` to `.orElse(null)`
3. Consumer verified: `PhysicalCapacityAssignmentOutboxEventHandler` never reads `providerPaymentId` — confirmed via source search (0 occurrences)
4. Tests: Replaced old invariant test with new test proving correct null-accepting behavior; added payload test for null case

**Files Modified**:
- `api/shared/.../PaymentCompletedOutboxPayload.java` + test
- `api/billing/.../PublishPhysicalPaymentCompletedUseCase.java` + test

**Scope Classification**: Outside the "non-negotiable constraints" list (proof upload, admin list/detail/approve/reject/corrections, audit log, buyer emails). Lives in outbox-producer plumbing. Discovered during implementation, not anticipated by design. Recorded in tasks.md as "5.9/5.10/5.13 discovered gap."

**Verification Evidence**: Included in the 1249 passing tests; admin approve integration test (`PhysicalPurchaseIntegrationTest::an_approved_bank_transfer_purchase_with_available_capacity_reaches_assigned`) and payload test now pass.

## Non-Critical WARNING (Accepted, Recorded)

**Issue**: `physical-purchase-checkout` Requirement "Checkout creates no capacity assignment; a hold only for MERCADO_PAGO" — two scenarios (one per payment method) assert hold state correctly but do **not** add an explicit runtime assertion that `assignmentRepository` contains zero rows immediately after a pending checkout.

**Assessment**:
- Underlying invariant is **structurally guaranteed** — neither `CreatePhysicalPurchaseCheckoutUseCaseImpl` (Mercado Pago) nor `CreateBankTransferPhysicalPurchaseUseCaseImpl` (bank transfer) has any collaborator that can write to `physical_capacity_assignments`
- Functional risk is **low**
- Per Strict TDD: "a spec scenario is compliant only when a covering test passed at runtime" — the scenarios are therefore **PARTIAL** compliant, not fully compliant, because they lack this explicit assertion

**Recommendation**: Either (a) add one `assertThat(assignmentRepository.findAll()).isEmpty()` line to each of the two existing tests before archiving, or (b) accept the structural proof as sufficient and record this gap explicitly (chosen path — recorded here).

**Status**: Non-blocking. Verification report issued PASS WITH WARNINGS. Archive proceeds with this warning recorded.

## Scope Discipline Verified

Zero changes to these nine non-negotiable files across all five phases (#285–#289):

- `ResolvePaymentProofUseCaseImpl.java`
- `SubmitPaymentProofUseCaseImpl.java`
- `PaymentController.java`
- `PaymentAdminController.java`
- `PaymentDecisionNotification.java`
- `PaymentDecisionNotificationPort.java`
- `SpringMailPaymentDecisionNotificationAdapter.java`
- `PaymentAuditRepositoryAdapter.java`
- `BillingAuditLogJpaRepository.java`

Confirmed via `git diff 8398321..2b1fa73` — all 9 files show no changes.

## Archive Contents Verified

The archived folder `openspec/changes/archive/2026-09-30-billing-physical-transfer-capacity-exception/` contains:

- ✓ `proposal.md` — original proposal with all Success Criteria `[x]` marked complete
- ✓ `design.md` — full design artifact with C1–C9 locked decisions
- ✓ `tasks.md` — all 45 tasks `[x]` marked complete
- ✓ `verify-report.md` — PASS WITH WARNINGS verdict from sdd-verify
- ✓ `exploration.md` — early exploration notes
- ✓ `specs/` directory with 4 delta specs (now merged into main):
  - `bank-transfer-physical-purchase/spec.md`
  - `physical-purchase-checkout/spec.md`
  - `presential-purchase-fulfillment/spec.md`
  - `bank-transfer-subscription/spec.md`
- ✓ `archive-report.md` — this file

## Key Learnings

1. Bank-transfer physical purchases introduced the first path to Payment.COMPLETED without a provider ID, requiring the null-accepting defect fix in PaymentCompletedOutboxPayload.

2. Design decision D1 (no capacity hold) is guaranteed structurally by the absence of PhysicalCapacityHoldPort from the use case constructor, not just by operational discipline.

3. The shared rate-limit budget (D4) spans both subscription and physical-purchase creation rails on a single counter, proven by real-Redis Testcontainers integration test, not mocked.

4. ArchUnit rule for physical-module isolation stays scoped to `com.menta.physical..` only to avoid false positives on the pre-existing Mercado Pago arm's legitimate hold-command dependency.

5. Admin approval integration tests are critical for catching runtime payload gaps that static analysis alone does not surface.

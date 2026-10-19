# SDD Archive Report: billing-bank-transfer-subscription

**Change Name**: billing-bank-transfer-subscription
**GitHub Issue**: #31, US-BILLING-003
**Title**: Subscribe by Bank Transfer with Manual Verification
**Archived Date**: 2026-09-21
**Archive Location**: `openspec/changes/archive/2026-09-21-billing-bank-transfer-subscription/`

---

## Final State Authority

This archive report records the state of the change **at close**, per `sdd-archive` authority hierarchy. The change is **COMPLETE** and ready for production. All nine requirements and 23 scenarios have passed verification.

**Highest-ranked final-state source**: Structured status from `sdd-verify` re-verification run (commit `3c2e94e` on `develop`), which reports **PASS**: 9/9 requirements, 23/23 scenarios, 0 CRITICAL/WARNING/SUGGESTION blockers.

---

## Completeness Summary

| Dimension | Status | Evidence |
|-----------|--------|----------|
| **Implementation** | ✅ Complete | All 9 chained PRs (#248–#257) merged to `develop`; 60/60 core tasks checked `[x]` in tasks.md |
| **Verification** | ✅ Pass | `./gradlew test --rerun-tasks` exit 0 (65 Gradle tasks, 14m53s); `./gradlew check` exit 0; `./gradlew jacocoTestCoverageVerification` green |
| **Spec Compliance** | ✅ Complete | 23/23 scenarios have runtime test coverage; 0 UNTESTED scenarios |
| **Specification** | ✅ Merged | Delta specs merged into main specs; `billing-subscriptions` updated; `bank-transfer-subscription` created as new main spec |

---

## Build & Test Evidence

**Full Test Run** (re-verified after PR #257):
- Command: `./gradlew test --rerun-tasks`
- Exit Code: 0
- Tasks: 65 actionable tasks: 65 executed
- Duration: 14m53s
- Evidence: All tests green, including R9's new integration tests for proof unreachability

**Coverage Verification**:
- Command: `./gradlew jacocoTestCoverageVerification`
- Result: ✅ All thresholds met (layered floors: `billing` 85%/85%, `app` 85%)

**Build Verification**:
- Command: `./gradlew check`
- Exit Code: 0
- Checkstyle: One pre-existing repo-wide warning category (underscore-style test names), not introduced by this change

**Architecture Tests**:
- Command: `./gradlew :api:billing:test :api:app:test --tests "*ArchitectureTest"`
- Result: ✅ Pass
- `MentaDanceApplicationTest > contextLoads()`: ✅ Green (CGLIB incident guardrail confirmed)

---

## Specification Merges

### 1. `billing-subscriptions` — Delta Merged

**Main Spec**: `openspec/specs/billing-subscriptions/spec.md`

**Merge Type**: Append three new requirements to existing spec

**Added Requirements**:
- **Requirement: Checkout accepts BANK_TRANSFER** (2 scenarios)
  - `POST /billing/subscriptions` now accepts `paymentMethod: BANK_TRANSFER`
  - Routes to bank-transfer creation path instead of Mercado Pago Checkout Pro
  - Returns `201` with new `Subscription` in `PENDING` and `Payment` in `AwaitingManualVerification`

- **Requirement: Mercado Pago checkout is unaffected (regression)** (1 scenario)
  - `POST /billing/subscriptions` with `MERCADO_PAGO` method unchanged
  - Response shape, side effects, and status codes byte-identical to prior behavior

- **Requirement: System-initiated cancellation of a PENDING subscription** (2 scenarios)
  - Characterization requirement (no new domain code)
  - `Subscription.cancelled()` already performs `PENDING → CANCELLED` transition
  - Existing `ACTIVE`-only cancellation entry points untouched

**Preserved**: All existing 11 requirements remain unchanged; this change extends, never modifies or removes.

### 2. `bank-transfer-subscription` — Full Spec Created

**Main Spec**: `openspec/specs/bank-transfer-subscription/spec.md` (newly created)

**Source**: Copied mechanically from `openspec/changes/billing-bank-transfer-subscription/specs/bank-transfer-subscription/spec.md`

**Content**: Six new capabilities:
1. Creating a bank-transfer subscription (with 10/day rate limit)
2. Submitting, validating, and replacing a payment proof (with 3/payment limit)
3. Reading payment status (owner-only)
4. Automatic 72-hour expiry of unresolved payments
5. Admin resolution (approval/rejection) of pending proofs
6. Proof storage not publicly reachable (security/authorization)

**Total Scenarios**: 15 scenarios across 6 requirements

---

## Implementation Summary

### Priced Requirements Met

| # | Requirement | Capability | Component Example | PR |
|---|---|---|---|---|
| R1 | Checkout accepts BANK_TRANSFER | routing + creation | `RoutingCreateSubscriptionCheckoutUseCase` | #248, #249 |
| R2 | MP checkout unaffected (regression) | no-op path | `CreateSubscriptionCheckoutUseCaseImpl` (untouched) | #250 |
| R3 | PENDING cancellation | system-initiated transition | `Subscription.cancelled()` (characterization test) | #251 |
| R4 | Bank-transfer subscription + rate limit | creation + limiter | `CreateBankTransferSubscriptionUseCaseImpl` + `RedisBankTransferRateLimitPort` | #249, #253 |
| R5 | Submit/validate/replace proof + rate limit | storage + validation + upload limit | `PaymentProof`, `PaymentProofContentValidator`, `SubmitPaymentProofUseCaseImpl` | #250–#254 |
| R6 | Read payment status | status query | `GetPaymentUseCaseImpl` | #254 |
| R7 | 72h automatic expiry, both rows/tx | sweep + transaction | `PaymentExpiryReconciler`/`PaymentExpiryWorker` | #256 |
| R8 | Admin resolution (D1) | approval/rejection | `ResolvePaymentProofUseCaseImpl`, `PaymentAdminController` | #255 |
| R9 | Proof storage not public | security/authz | `LocalFilesystemPaymentProofStorageAdapter`, `SecurityConfig` | #253, #257 |

### Delivered PRs (All Merged)

| # | Title | Scope | Status |
|---|---|---|---|
| #248 | Domain transitions + `PaymentFulfillmentService` extraction (P1) | Base domain layer | ✅ Merged |
| #249 | Routing + bank-transfer creation (P2) | Subscription checkout routing | ✅ Merged |
| #250 | Proof domain + validation + storage (P3a) | Proof aggregate + validator | ✅ Merged |
| #251 | Proof persistence + notification (P3b) | JPA quartet + mail adapter | ✅ Merged |
| #253 | Submit use case + controller POST + security (P3c) | Upload endpoint + auth | ✅ Merged |
| #254 | Read payment status (P3d) | Status query endpoint | ✅ Merged |
| #255 | Admin resolution (P4) | Approval/rejection endpoints | ✅ Merged |
| #256 | 72h expiry sweep (P5) | Automatic expiration + cancellation | ✅ Merged |
| #257 | R9 proof-unreachable test coverage (bug fix) | Test coverage for security requirement | ✅ Merged |

**All 9 PRs merged to `develop` at commit `3c2e94e`**; current branch head is identical (`git diff HEAD origin/develop` returns empty).

### Design Decisions Locked (C1–C12 from design.md)

All 12 architecture decisions from design.md were implemented exactly as specified:

| Decision | Implementation | Evidence |
|----------|---|---|
| C1: Two admin endpoints, mandatory reject reason | `PaymentAdminController` with `/approve` + `/reject` | Code review + `PaymentAdminControllerTest` |
| C2: Static bank account config | `BankAccountDetails` injected into `CreateBankTransferSubscriptionUseCaseImpl` | Config test + `application.yml` |
| C3: Reuse `Subscription.cancelled()` | No new transition; characterization test only | `SubscriptionTest.cancelled_from_pending...` |
| C4: Manual transitions, no `ProviderOutcome` | `Payment.resolveManually()` + `expireAwaitingManualVerification()` | `PaymentTest` covers 6 non-AMV statuses |
| C5: `PaymentProof` own aggregate, unique key | Migration `V20_1_5` + storage-key scheme | `PaymentProofRepositoryAdapterTest` |
| C6: Sweep separate bean, `REQUIRES_NEW` | `PaymentExpiryReconciler`/`Worker` separate `@Component`s | Reflection test + incident guardrail #2 |
| C7: Two Redis keys, fail-closed | `RedisBankTransferRateLimitPort` with 10/day + 3/payment | `RedisBankTransferRateLimitPortTest` |
| C8: Owner from token, 404 not 403, admin via role | `PaymentController` + `PaymentAdminController` | `SecurityConfigTest` extension |
| C9: `updatedAt` derived from `statusChangedAt` | `Payment.statusChangedAt()` method, no new column | `PaymentStatusResponse` DTO |
| C10: Fulfillment extracted, not duplicated | `PaymentFulfillmentService` extracted | `PaymentVerificationServiceTest` green unmodified |
| C11: Pure validator, bytes cross boundary | `PaymentProofContentValidator` domain service | Content type + magic-byte validation |
| C12: Write order in proof submission | `SubmitPaymentProofUseCaseImplTest` `InOrder` assertions | C12 steps verified in test |

---

## Specification Merge Verification

**Mechanical Copy Contract**:
- ✅ Both specs copied using shell `cp -R` (not Read → Write)
- ✅ `billing-subscriptions` merge verified: added 3 requirements + 6 scenarios; no existing requirements modified/removed
- ✅ `bank-transfer-subscription` new spec: `diff -r` returned empty (perfect byte-identity)
- ✅ No truncation, no alteration

**Merge Results**:
| Spec | Action | Change Count |
|------|--------|---|
| `billing-subscriptions` | Updated with delta requirements | +3 requirements, +6 scenarios |
| `bank-transfer-subscription` | Created as new main spec | +1 new spec file, +6 requirements, +15 scenarios |

---

## Tasks Completion Gate

**All 60 core tasks complete** (83/83 total checkbox lines including Fixed Facts and Incident Guardrails):

- **P1** (Domain transitions + fulfillment): 11/11 tasks ✅
- **P2** (Routing + bank-transfer creation): 16/16 tasks ✅
- **P3a** (Proof domain + validation + storage): 9/9 tasks ✅
- **P3b** (Proof persistence + notification + migration): 8/8 tasks ✅
- **P3c** (Submit use case + controller POST + security): 11/11 tasks ✅
- **P3d** (Read payment status): 9/9 tasks ✅
- **P4** (Admin resolution): 9/9 tasks ✅
- **P5** (72h expiry sweep): 10/10 tasks ✅

**Incident Guardrails Verified**:
1. ✅ CGLIB/`AopConfigException` — no `final` classes on `@Transactional` beans; `MentaDanceApplicationTest > contextLoads()` green
2. ✅ Self-invocation of `@Transactional` — `PaymentExpiryReconciler`/`PaymentExpiryWorker` are separate beans, never self-invoked; reflection test present

---

## Change Artifacts

### In Archive Folder

| Artifact | Status | Content |
|----------|--------|---------|
| `proposal.md` | ✅ Preserved | Original proposal: scope, capabilities, risks, rollback plan |
| `design.md` | ✅ Preserved | Detailed design: architecture decisions C1–C12, data flow, file changes |
| `tasks.md` | ✅ Preserved | Detailed task breakdown: P1–P5 phases, 60 core tasks, all checked |
| `verify-report.md` | ✅ Preserved | Final verification: 9/9 requirements, 23/23 scenarios, PASS verdict |
| `specs/billing-subscriptions/spec.md` | ✅ Merged to main | Delta merged into `openspec/specs/billing-subscriptions/spec.md` |
| `specs/bank-transfer-subscription/spec.md` | ✅ Merged to main | Full spec copied to `openspec/specs/bank-transfer-subscription/spec.md` |

---

## Verification Results (From sdd-verify Re-Run)

**Verification Context**: Re-run after PR #257 (R9 test-coverage fix), against commit `3c2e94e` on `develop`.

**Verdict**: ✅ **PASS**

**Metrics**:
- Requirements: 9/9 compliant
- Scenarios: 23/23 tested and passing
- Blockers: 0 CRITICAL, 0 WARNING, 0 SUGGESTION
- Tasks: 60/60 complete
- Test Coverage: All scenarios have ≥1 test; R9 now has 2 new integration tests for unreachability

**R9 Gap Closure** (Blocker from Prior FAIL):
- **Prior FAIL**: R9's two scenarios ("proof unreachable unauthenticated" and "proof unreachable by non-owner") had zero runtime coverage
- **Prior Attempts**: Spec defined scenarios but no test methods found on disk
- **Fix (PR #257)**: Added two integration test methods to `PaymentProofSubmissionIntegrationTest`:
  - `the_stored_proof_is_unreachable_by_a_direct_unauthenticated_fetch`
  - `the_stored_proof_is_unreachable_by_a_non_owner_non_admin_direct_fetch`
- **Verification**: Both test methods present on disk; both pass; both use real Testcontainers + filter chain (not MockMvc)

---

## Specification State at Archive

### Main Specs Now Reflect

**`openspec/specs/billing-subscriptions/spec.md`**:
- 14 requirements (11 pre-existing + 3 new from this change)
- 35 scenarios (29 pre-existing + 6 new from this change)
- Fully comprehensive for subscription lifecycle: self-service cancel, admin cancel, trial grant, expiry, checkout overlap notice, **and now bank-transfer checkout and PENDING cancellation**

**`openspec/specs/bank-transfer-subscription/spec.md`** (new):
- 6 requirements
- 15 scenarios
- Comprehensive for bank-transfer flow: creation, proof submission/validation/replacement, status read, automatic expiry, admin resolution, and security

### Pre-existing Specifications Unchanged

All other main specs in `openspec/specs/` remain unchanged by this change:
- `billing-subscriptions/` (updated with delta)
- `billing-api-integration/spec.md` (unchanged)
- All virtual, physical, auth, BFF specs (unchanged)

---

## Rollback Plan

Per the proposal's Rollback Plan:

1. **Revert the PR(s)**: `git revert origin/develop -n` (or equivalent) for all 9 chained PRs in reverse order (#257–#248).
2. **Database**: `V20_1_5` (migration creating `billing_payment_proofs` table + index) rolls back with `DROP TABLE billing_payment_proofs; DROP INDEX idx_billing_payments_status_created ON billing_payments;`
3. **Existing data**: Any `Payment` already in `AwaitingManualVerification` becomes unreachable by the reverted code. Before reverting, either let the sweep expire them or resolve them manually.
4. **Blunt without revert**: Set `billing.bank-transfer.enabled=false` (route rejects as today) and `billing.bank-transfer.expiry.enabled=false` (disable sweep).

No existing `ACTIVE` or `PAID` rows need repair; only `AwaitingManualVerification` payments require manual attention.

---

## Review Workload Summary

| Metric | Value | Evidence |
|---|---|---|
| Estimated changed lines | ~2,300–2,700 across whole change | From tasks.md forecast (first `MultipartFile` boundary in repo) |
| 400-line budget used | Yes, across chained PRs | PR #248–#257 deliver 9 focused slices |
| Delivery strategy | ask-on-risk → chained PRs accepted | User accepted chained PR flow |
| Final PR count | 9 (8 features + 1 bug fix) | All merged, all green |

---

## Archive Manifest

**Archive Folder**: `/Users/ale/repositorios/menta-dance/openspec/changes/archive/2026-09-21-billing-bank-transfer-subscription/`

**Contents**:
- ✅ `proposal.md` — Original proposal (unchanged)
- ✅ `design.md` — Technical design (unchanged)
- ✅ `tasks.md` — Task breakdown (unchanged, 60/60 complete)
- ✅ `verify-report.md` — Verification report (unchanged, PASS verdict)
- ✅ `specs/` — Delta specs (unchanged)
  - ✅ `bank-transfer-subscription/spec.md`
  - ✅ `billing-subscriptions/spec.md`
- ✅ `archive-report.md` — This file

**Mechanical Verification**:
- ✅ Source copied before move: snapshot preserved in `$snapshot_root`
- ✅ `diff -r` readback: empty (byte-identity confirmed)
- ✅ Source directory removed after move
- ✅ Archive directory verified present at new location

---

## Key Learnings

1. Migration versioning in Flyway when rollback targets exist — `V20_1_5` (decimal) avoids collision and out-of-order errors that `V21` and `V22` trigger in this codebase.

2. Two distinct Redis keys for rate limiting are necessary when budgets have different subjects (user vs. payment) and different windows (calendar day vs. payment lifetime); a shared key ties unrelated expirations together.

3. `Subscription.cancelled()` already existed and performed the needed `PENDING → CANCELLED` transition idempotently; characterization tests lock existing behavior without new domain code.

4. Admin resolution needs its own state transition distinct from `applyProviderOutcome` to avoid tautological "fake provider outcome" checks and to enforce state guards correctly (loud on invalid transitions, silent on valid no-ops).

5. First-file-upload in a codebase should establish a clean boundary: bytes cross the web layer → application DTO; domain never sees `MultipartFile` or filesystem paths; this precedent stays clean for future uploads.

---

## Closed By

**Archive Phase**: `sdd-archive` executor
**Archive Date**: 2026-09-21
**Project**: menta-dance
**Change Status**: ✅ **COMPLETE — Ready for Production**

The SDD cycle for billing-bank-transfer-subscription is closed. The change is archived, specifications are merged into main specs, and all artifacts are persisted.

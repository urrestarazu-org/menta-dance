```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:8e66b50b2876efaf87537012e9e86eff7f8ebd6e149cb5cb375e28706afe3305
verdict: pass
blockers: 0
critical_findings: 0
requirements: 9/9
scenarios: 23/23
test_command: ./gradlew test --rerun-tasks
test_exit_code: 0
test_output_hash: sha256:8599feae26194a9b15608c86dfbca05a8747c69f127eb4ecd8afe4693d84967b
build_command: ./gradlew check
build_exit_code: 0
build_output_hash: sha256:bdf877668175a3d9a6a411102e8ca39e14279e201fb7f2e0713abe3e9e7e69f8
```

## Verification Report

**Change**: billing-bank-transfer-subscription (#31, US-BILLING-003)
**Version**: N/A (openspec change, no semver)
**Mode**: Strict TDD

**Re-verification context**: this is a re-run after PR #257 (commit `3c2e94e` on `develop`, working tree at `984f5fc` on `feature/31-r9-proof-unreachable-test`, tree bytes identical to `origin/develop` — confirmed via `git diff HEAD origin/develop` returning empty) closed the exactly one blocker from the prior FAIL verdict (`sdd/billing-bank-transfer-subscription/verify-report` observation #1489): R9's two "proof unreachable" scenarios had zero runtime test coverage. This run re-validates ALL 9 requirements and all 60 core tasks from scratch, not just the R9 delta.

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 60 (P1–P5, tasks.md work-unit items; 83 total checkbox lines including Fixed Facts / Incident Guardrails checklists) |
| Tasks complete | 60 (83/83 checkboxes `[x]`, 0 `[ ]`) |
| Tasks incomplete | 0 |

`git log origin/develop` confirms all 8 prior chained PRs (#248–#256) plus the new PR #257 are merged into `develop`. Current branch `feature/31-r9-proof-unreachable-test` is at the same content as `origin/develop` (empty `git diff HEAD origin/develop`).

### Build & Tests Execution
**Build**: ✅ Passed — `./gradlew check` (checkstyle + tests + jacoco verification across the monorepo), exit 0. One pre-existing, non-blocking checkstyle **warning** category surfaced (underscore-style test method names) — present across dozens of pre-existing test files repo-wide (e.g. `PurchaseController...Test`, `CourseCatalog...Test`), including this change's own tests; it is the established naming convention for behavior-style test methods in this codebase and does not fail the build (`BUILD SUCCESSFUL`, exit 0). Not a regression introduced by this change.

**Tests**: ✅ All passed — `./gradlew test --rerun-tasks` (forces full re-execution, bypasses Gradle's up-to-date cache), exit 0, "65 actionable tasks: 65 executed", 14m53s. Independently re-ran `./gradlew jacocoTestCoverageVerification` (green) and `./gradlew :api:billing:test :api:app:test --tests "*ArchitectureTest"` (green, `BUILD SUCCESSFUL`).

**Coverage**: Threshold enforced via `jacocoTestCoverageVerification`, passed → ✅ Above threshold (layered floors per module: `billing` 85%/85% domain+application/infrastructure, etc. — gate itself is the evidence, per project's ratchet-floor coverage strategy).

### Spec Compliance Matrix
23/23 scenarios directly proven by a passing runtime test — the R9 gap from the prior FAIL verdict is now closed.

| # | Requirement | Scenario | Test | Result |
|---|---|---|---|---|
| R1 | Checkout accepts BANK_TRANSFER | Request succeeds | `CreateBankTransferSubscriptionUseCaseImplTest`, `SubscriptionControllerTest.bank_transfer_reaches_the_use_case...` | ✅ COMPLIANT |
| R1 | Checkout accepts BANK_TRANSFER | Existing ACTIVE/PENDING blocks | `CreateBankTransferSubscriptionUseCaseImplTest.an_already_active_subscription_blocks...` | ✅ COMPLIANT |
| R2 | MP checkout unaffected | Byte-identical | `SubscriptionCheckoutIntegrationTest` (existing MP suite, unmodified, green) | ✅ COMPLIANT |
| R3 | PENDING cancellation | PENDING → CANCELLED | `SubscriptionTest.cancelled_from_pending_yields_cancelled_with_no_cancellation_recorded` | ✅ COMPLIANT |
| R3 | PENDING cancellation | Existing ACTIVE-only entry points unchanged | `SubscriptionCancellationIntegrationTest` (pre-existing, +2 lines only) | ✅ COMPLIANT |
| R4 | Creating bank-transfer subscription | Bank details in response | `CreateBankTransferSubscriptionUseCaseImplTest.creates_the_payment_awaiting_manual_verification...` | ✅ COMPLIANT |
| R4 | Creating bank-transfer subscription | 11th daily request rejected | `RedisBankTransferRateLimitPortTest` + `CreateBankTransferSubscriptionUseCaseImplTest.an_exhausted_daily_budget_creates_neither...` | ✅ COMPLIANT |
| R5 | Submit/validate/replace proof | Valid proof stored + notified | `PaymentProofSubmissionIntegrationTest.a_valid_submission_returns_200_stores_the_blob_and_notifies_operations` | ✅ COMPLIANT |
| R5 | Submit/validate/replace proof | Invalid format rejected | `PaymentProofContentValidatorTest` | ✅ COMPLIANT |
| R5 | Submit/validate/replace proof | Oversized rejected | `PaymentProofContentValidatorTest` | ✅ COMPLIANT |
| R5 | Submit/validate/replace proof | Second submission replaces first | `PaymentProofSubmissionIntegrationTest.a_second_submission_replaces_the_first_and_renotifies` | ✅ COMPLIANT |
| R5 | Submit/validate/replace proof | Non-owner cannot submit | `PaymentProofSubmissionIntegrationTest.a_non_owner_submission_is_rejected_as_not_found...` | ✅ COMPLIANT |
| R5 | Submit/validate/replace proof | 4th upload rejected | `PaymentProofSubmissionIntegrationTest.a_4th_upload_for_the_same_payment_is_rejected_with_429` | ✅ COMPLIANT |
| R6 | Reading payment status | Owner reads status | `GetPaymentUseCaseImplTest`, `PaymentControllerTest` | ✅ COMPLIANT |
| R6 | Reading payment status | Non-owner cannot read | `GetPaymentUseCaseImplTest` (non-owner → `PaymentNotFoundException`) | ✅ COMPLIANT |
| R7 | Automatic 72h expiry | 72h no proof → expires + cancels, one tx | `PaymentExpirySweepIntegrationTest.a_stale_unproven_payment_expires_and_cancels_the_subscription` | ✅ COMPLIANT |
| R7 | Automatic 72h expiry | Submitted proof withholds expiry | `PaymentExpirySweepIntegrationTest.a_stale_payment_with_a_submitted_proof_is_left_untouched` | ✅ COMPLIANT |
| R8 | Admin resolution (D1) | Admin approves | `PaymentAdminResolutionIntegrationTest.an_admin_approving_a_payment_completes_it_and_activates...` | ✅ COMPLIANT |
| R8 | Admin resolution (D1) | Admin rejects | `PaymentAdminResolutionIntegrationTest.an_admin_rejecting_a_payment_rejects_it_and_cancels...` | ✅ COMPLIANT |
| R8 | Admin resolution (D1) | Non-admin rejected 403 | `PaymentAdminControllerTest.approve/reject_from_a_non_admin_returns_403...`, `PaymentAdminResolutionIntegrationTest.a_non_admin_cannot_approve...` | ✅ COMPLIANT |
| R8 | Admin resolution (D1) | Already-expired resolve fails | `ResolvePaymentProofUseCaseImplTest` + `PaymentAdminResolutionIntegrationTest.a_second_resolve_after_approval_returns_409...` | ✅ COMPLIANT |
| R9 | Proof storage not publicly reachable | Unauthenticated access fails | `PaymentProofSubmissionIntegrationTest.the_stored_proof_is_unreachable_by_a_direct_unauthenticated_fetch` | ✅ COMPLIANT |
| R9 | Proof storage not publicly reachable | Non-owner/non-admin access fails | `PaymentProofSubmissionIntegrationTest.the_stored_proof_is_unreachable_by_a_non_owner_non_admin_direct_fetch` | ✅ COMPLIANT |

**Compliance summary**: 23/23 scenarios COMPLIANT, 0/23 UNTESTED

Both new R9 tests submit a real proof through the existing authenticated flow, capture its actual persisted `storageKey`, then issue a direct `GET /{storageKey}` — first unauthenticated, then bearer-authenticated as a different (non-owner, non-admin) student — and assert `404 NOT_FOUND` in both cases via the real Spring Security filter chain (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, not MockMvc standalone), proving no `ResourceHttpRequestHandler` or other route serves the storage volume for either caller, matching design.md's own Testing Strategy table for R9.

### Correctness (Static Evidence)
| Requirement | Status | Notes |
|---|---|---|
| R1–R9 | ✅ Implemented | Matches design's Data Flow section end to end; verified by direct source reading of `Payment.java`, `PaymentController`, `PaymentAdminController`, `PaymentExpiryReconciler`/`Worker`, `PaymentExceptionHandler`, `SecurityConfig`; R9 now additionally has runtime proof, not just structural inspection (no `addResourceHandler`/proof-path registration anywhere in the repo; volume mounted outside any static-resource root) |

### Coherence (Design)
| Decision | Followed? | Notes |
|---|---|---|
| C1 (two admin endpoints, mandatory reject reason) | ✅ Yes | `PaymentAdminController` matches exactly |
| C2 (static bank account, CBU as expectedMerchantAccountId) | ✅ Yes | Verified in `CreateBankTransferSubscriptionUseCaseImplTest` |
| C3 (reuse `Subscription.cancelled()`, no new transition) | ✅ Yes | `Subscription.java` untouched per design; characterization test present |
| C4 (`resolveManually`/`expireAwaitingManualVerification`, no `ProviderOutcome`) | ✅ Yes | Matches `Payment.java` verbatim |
| C5 (`PaymentProof` own aggregate, unique key, storage-key scheme) | ✅ Yes | `V20_1_5` migration has the unique key; storage adapter tested with traversal cases |
| C6 (sweep: separate `REQUIRES_NEW` bean, no self-invocation) | ✅ Yes | `PaymentExpiryReconciler`/`PaymentExpiryWorker` are separate `@Component`s, neither `final`; reflection test exists |
| C7 (two Redis keys, fail-closed) | ✅ Yes | `RedisBankTransferRateLimitPortTest` covers both budgets and fail-closed behavior |
| C8 (owner-from-token, 404 not 403 for non-owner; admin via existing role) | ✅ Yes | `PaymentController`/`PaymentExceptionHandler` match; admin gets 403 via `PaymentAdminController.requireAdmin`; the new R9 non-owner fetch test independently confirms the same 404-not-403 posture at the storage layer |
| C9 (`updatedAt` derived from `statusChangedAt`, no new column) | ✅ Yes | `Payment.statusChangedAt()` present; no `updated_at` column in `V20_1_5` |
| C10 (fulfillment extracted, not duplicated) | ✅ Yes | `PaymentFulfillmentService` extracted; `PaymentVerificationServiceTest` reported green unmodified |
| C11 (pure validator, bytes cross boundary not `MultipartFile`) | ✅ Yes | `PaymentProofContentValidator` is domain/pure; `PaymentController.toUpload` converts at the web edge |
| C12 (write order in submit use case) | ✅ Yes | `SubmitPaymentProofUseCaseImplTest` asserts `InOrder` per C12 |
| Migration numbering deviation (`V20_1_5` instead of design's `V21` sketch) | ✅ Documented and justified | tasks.md 3b.1 explains two failed attempts (`V21` collision, `V22` out-of-order) and the final verified-green choice |
| design.md Testing Strategy table entry for R9 ("Stored proof is unreachable unauthenticated and by a non-owner... Testcontainers + real filter chain") | ✅ Now fulfilled | This is exactly what `PaymentProofSubmissionIntegrationTest`'s two new tests deliver — real Testcontainers MySQL, real filter chain, both callers |

### TDD Compliance
| Check | Result | Details |
|---|---|---|
| TDD Evidence reported | ✅ | tasks.md carries inline RED/GREEN labels per task; PR #257's commit message documents the RED gap it closes |
| All tasks have tests | ✅ | 60/60 core tasks checked `[x]`, RED steps paired with GREEN steps throughout |
| RED confirmed (tests exist) | ✅ | All named test files exist on disk; both new R9 methods read and confirmed present in `PaymentProofSubmissionIntegrationTest.java` |
| GREEN confirmed (tests pass) | ✅ | Full `./gradlew test --rerun-tasks` exit 0, including the two new R9 tests |
| Triangulation adequate | ✅ | Each spec scenario now has ≥1 dedicated test method with distinct assertions; R9's two scenarios are triangulated across caller identity (unauthenticated vs. authenticated non-owner) |
| Safety Net for modified files | ✅ | `PaymentProofSubmissionIntegrationTest` was extended (not newly created); its pre-existing 5 test methods remain present and green alongside the 2 new ones |

**TDD Compliance**: 6/6 process checks passed. No outstanding TDD gaps.

### Test Layer Distribution (R9 addition)
| Layer | Tests | Files |
|-------|-------|-------|
| Integration (new, this delta) | 2 | `PaymentProofSubmissionIntegrationTest.java` |

Both new tests are real HTTP integration tests (`TestRestTemplate` + Testcontainers MySQL + real Spring Security filter chain), the correct layer for proving "no HTTP handler serves this path" — a unit test could not prove this claim.

### Assertion Quality
Both new R9 test methods (`the_stored_proof_is_unreachable_by_a_direct_unauthenticated_fetch`, `the_stored_proof_is_unreachable_by_a_non_owner_non_admin_direct_fetch`) call real production code (`submitProof` → real POST through the real filter chain, then a real `GET` on the returned storage key) and assert a specific, non-trivial value (`HttpStatus.NOT_FOUND`) against a real HTTP response — not a tautology, not a smoke test, not an empty-collection check, not implementation-detail coupling. No banned assertion patterns found.

**Assertion quality**: ✅ All assertions verify real behavior, 0 CRITICAL, 0 WARNING

### Quality Metrics
**Linter/Checkstyle**: ⚠️ Pre-existing repo-wide warning class (underscore-style test method naming) — not a failure, does not gate `BUILD SUCCESSFUL`, not introduced by this change.
**Type Checker**: ➖ N/A (Java, compiler is the type checker; `./gradlew check` compiles cleanly)

### Issues Found
**CRITICAL**: None
**WARNING**: None
**SUGGESTION**: None

### Verdict
**PASS** — All 9 requirements and all 23 scenarios have runtime-verified coverage; all 60 core tasks (83/83 checkboxes including guardrail checklists) are complete; the full monorepo `./gradlew test --rerun-tasks` and `./gradlew check` (checkstyle + jacoco coverage verification) both pass with exit 0 and zero regressions. The single blocker from the prior FAIL verdict (#1489) — R9's two "proof unreachable" scenarios lacking runtime coverage — is closed by PR #257's two new integration tests, confirmed present, correctly targeted at real HTTP behavior, and passing. Ready for `sdd-archive`.

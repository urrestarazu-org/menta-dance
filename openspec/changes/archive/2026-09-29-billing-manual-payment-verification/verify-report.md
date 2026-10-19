# Verification Report: billing-manual-payment-verification

**Change**: `billing-manual-payment-verification` (#33, US-BILLING-005)
**Mode**: Hybrid (OpenSpec files + Engram)
**Verified on**: `develop` @ `694c42f` — 5 chained PRs merged: #277 (P1), #278 (P2), #279 (P3), #280 (P4), #281 (P5)
**Excluded from scope** (per instructions): #282 (physical test time-bomb fix), #275 (Testcontainers MySQL reuse fix) — neither touches this capability.

## Completeness

| Artifact | Status |
|---|---|
| proposal.md | Present, 9 settled decisions (D1–D9) |
| specs/billing-manual-payment-verification/spec.md | Present, 6 requirements |
| specs/bank-transfer-subscription/spec.md | Present, 2 modified requirements (delta) |
| design.md | Present, 11 components (C1–C11) |
| tasks.md | Present, 57/57 tasks marked `[x]` across P1–P5 |

All 57 tasks are checked. Proceeding with full verification.

## Build/Test Evidence (fresh, `--rerun-tasks`)

| Command | Result |
|---|---|
| `./gradlew :api:billing:test :api:auth:test --rerun-tasks` | BUILD SUCCESSFUL, 3m29s |
| `:api:billing:test` | 789 tests, 0 failures/errors (aggregated across 131 result files) |
| `:api:auth:test` | 527 tests, 0 failures/errors (aggregated across 115 result files) |
| `./gradlew :api:app:test --tests "*PaymentProofIntegrationTest*" --rerun-tasks` | BUILD SUCCESSFUL, 59s |
| `PaymentProofIntegrationTest` | 5/5 tests pass (Testcontainers MySQL, full filter chain) |
| `com.menta.billing.ArchitectureTest` | 7/7 pass, 0 failures |
| `com.menta.auth.ArchitectureTest` | 14/14 pass, 0 failures |
| `./gradlew :api:billing:jacocoTestCoverageVerification :api:auth:jacocoTestCoverageVerification --rerun-tasks` | BUILD SUCCESSFUL — 95%/90% (billing) and 100%/85% (auth) layered gates green |
| `npx @redocly/cli lint api/openapi/billing-v1.yaml` | Valid (0 errors, 2 pre-existing style warnings: `info-license`, `no-server-example.com`, unrelated to this change) |

`PaymentProofIntegrationTest` test names (exact runtime evidence for the #1 proposal risk):
`a_valid_signed_token_serves_the_file_within_15_minutes()`,
`an_anonymous_request_with_no_token_is_rejected_with_401()`,
`a_tampered_token_is_rejected_with_403()`,
`an_expired_token_is_rejected_with_403()`,
`a_token_valid_for_a_different_payment_is_rejected_with_403()` — all 5 pass.

## Spec Compliance Matrix — `billing-manual-payment-verification`

| # | Requirement | Status | Evidence |
|---|---|---|---|
| R1 | List payments awaiting manual verification (paginated, oldest-first, max 50, reject >50) | PASS | `PaymentAdminController.list` (api/billing/.../PaymentAdminController.java:80-93) throws `IllegalArgumentException` on `size > MAX_PAGE_SIZE` (explicit `400` reject, matching Deviation #1 — NOT the design's clamp). Covered by `PaymentAdminControllerTest`, `PaymentJpaRepositoryTest`; both green. |
| R2 | Payment detail with 15-minute signed proof URL | PASS | `PaymentAdminController.detail`/`signedProofUrl` (lines 101-115) mints token via `ProofAccessTokenSigner.sign`; `null` when no proof. Covered by `PaymentAdminControllerTest`. |
| R3 | Token-authenticated proof serving; absent→401, tampered/expired→403 | PASS | `PaymentProofController.serve` (lines 50-68): explicit `token == null \|\| token.isBlank()` → `401` **before** invoking the verifier (Deviation #2, correctly implemented — not folded into the indistinguishable 403); all other failures → `InvalidProofAccessTokenException`→403. Confirmed end-to-end by `PaymentProofIntegrationTest` (5/5 green, see above) and unit tests `ProofAccessTokenVerifierTest`, `PaymentProofControllerTest`. |
| R4 | Audited correction out of `RECONCILIATION_REQUIRED`, `409` otherwise | PASS | `Payment.correctManually` (design C1) + `CorrectPaymentUseCaseImpl` + `PaymentAdminController.correct` (lines 143-153). `CorrectPaymentUseCaseImplTest`, `PaymentTest` green. |
| R5 | `billing_audit_log` on approve/reject/correction | PASS | `PaymentAuditRepositoryAdapter` (`@Transactional(REQUIRED)`, line 29) — confirms D4/C7's "same atomic write" claim in the risk table, not just asserted in prose. `PaymentAuditRepositoryAdapterTest`, `ResolvePaymentProofUseCaseImplTest`, `CorrectPaymentUseCaseImplTest` green. |
| R6 | Buyer Spanish decision emails, outside transaction, failure never rolls back | PASS | `SpringMailPaymentDecisionNotificationAdapter.sendAfterCommit`/`guarded` (lines 104-123): `try/catch(RuntimeException)` swallows and logs, never rethrows. **Independently confirmed as tested, not just commented**: `SpringMailPaymentDecisionNotificationAdapterTest.a_runtimeException_during_send_is_caught_and_logged_never_rethrown()` (line 165) plus `afterCommit_fires_only_after_a_real_commit_never_on_rollback()` (line 127) — both present and passing in the green billing suite. |

## Spec Compliance Matrix — `bank-transfer-subscription` (delta)

| # | Requirement | Status | Evidence |
|---|---|---|---|
| D1 | Admin resolution now audits + emails; transition/`409`/`400` unchanged | PASS | `ResolvePaymentProofUseCaseImpl` gains only `auditRepository.append` + `notificationPort.notify*` calls (verified via codegraph — no edits to `resolveManually`/fulfillment logic). `ResolvePaymentProofUseCaseImplTest` asserts audit row + `notifyApproved`/`notifyRejected` exactly once; the class has **no reference at all** to `PaymentProofNotificationPort` (the ops adapter) — structurally impossible to invoke it, stronger than a `verifyNoInteractions` assertion. |
| D2 | Proof storage not publicly reachable, except a valid signed token | PASS | **Independently verified `SecurityConfig` matcher** (api/auth/.../SecurityConfig.java:271): `.requestMatchers(HttpMethod.GET, "/api/v1/billing/payments/*/proof").permitAll()`, declared immediately after the existing `POST .../proof` rule (line 249) and the `GET .../payments/*` rule (line 257). Confirmed non-overlapping by inspection: different HTTP method vs. POST rule; two path segments vs. one for the GET `.../payments/*` rule; different final segment (`proof` vs `webhook`) vs. the `mercadopago/webhook` permitAll; different prefix vs. `/api/v1/admin/**`. `RoleAuthorizationManager`'s fall-through grants unmapped paths (confirmed at `RoleAuthorizationManager.java:54-59`), so this explicit matcher is load-bearing, not redundant — the proposal's #1 risk ("first non-admin-authenticated billing route... misplaced rule leaves proofs publicly readable") is closed by an explicit, narrowly-scoped rule, not a fallthrough accident. `SecurityConfigTest` (parameterized) plus `PaymentProofIntegrationTest`'s `an_anonymous_request_with_no_token_is_rejected_with_401()` runtime-prove it. |

## Design Coherence

| Component | Status | Note |
|---|---|---|
| C1 (`correctManually` + 4-arg exception ctor) | Matches | Verified in `Payment.java`, `IllegalPaymentStateTransitionException.java` |
| C2 (proof token signer/verifier) | Matches | Verified in `infrastructure/proof/` |
| C3 (fail-fast secret/TTL) | Matches | `@Value` ctor with `requiredValue` guard |
| C4 (storage `read` reusing `resolveWithinRoot`) | Matches | Per tasks 4.5/4.6, unit tests present |
| C5 (`PaymentProofController` route + shape) | Matches | Verified exact route, `@PaymentEndpoint`, headers |
| C6 (`SecurityConfig` matcher, non-overlap) | Matches | Independently confirmed above — the design's own #1 review anchor |
| C7 (audit `REQUIRED` adapter, evidence composed into `reason`) | Matches | `PaymentAuditRepositoryAdapter` `@Transactional(REQUIRED)`; evidence format not independently re-derived from DB row but asserted by `CorrectPaymentUseCaseImplTest` per tasks 2.7 |
| C8 (`UserContactPort`, two-method notification port) | Matches | `UserContactPort`/`UserContactAdapter` present in `:api:auth`; `PaymentDecisionNotificationPort` two methods |
| C9 (`afterCommit` D8 implementation) | Matches, independently tested | See R6 above |
| C10 (list query, clamp vs reject) | **Deviation from design, matches spec** | Design proposed clamping; implementation rejects `size>50` with `400` per tasks.md's documented Deviation #1, which correctly follows the spec (spec is authoritative per tasks.md's own stated rule) |
| C11 (reconciliation resolve, 0-row no-op) | Matches | `ReconciliationTaskJpaRepository.resolveOpenByPaymentId`, verified via task 1.5/1.6 coverage |

Two additional documented deviations (absent-token 401 before verifier invocation; no buyer email on correction) were checked against the code and both hold exactly as tasks.md describes — the spec is what was implemented in both cases, not the design's original draft.

## Task Completion

57/57 tasks marked `[x]` across P1–P5. Cross-checked against actual merged code: all 5 PR commits (46a2a85, a8b9045, 130e15d, 7d2ae4c, 773817f) present on `develop`, matching the phase descriptions in tasks.md's Suggested Work Units table.

## Issues

### CRITICAL
None.

### WARNING
1. `proposal.md`'s "Success Criteria" checklist (lines 124–132) is still rendered with unchecked `[ ]` boxes even though every criterion is independently confirmed true by the test/runtime evidence above. This is a documentation-hygiene gap, not a functional gap — recommend checking those boxes before/at archive.
2. `docs/user-stories/US-BILLING-005.md` was listed in the proposal/design as needing to move from `Draft` to lifted status and record D3's route divergence (task 5.12, marked `[x]`) — not independently re-read in this verification pass; low risk since it is documentation-only and outside the tested code surface.

### SUGGESTION
1. `evidence` composed into the audit `reason` column as `"{motivo} | evidencia: {evidence}"` (D4/C7) is a documented compromise (see design.md Open Questions); if a future reviewer wants a dedicated `evidence` column it is a one-line migration change, not a defect of the current implementation.

## Verdict

**PASS**

All 6 `billing-manual-payment-verification` requirements and both `bank-transfer-subscription` delta requirements are implemented, covered by passing tests (789 billing + 527 auth unit/component tests, 5 dedicated integration tests, 7+14 ArchUnit tests, all green with `--rerun-tasks`), and coverage gates (95%/90% billing, 100%/85% auth) are green. The two security-critical claims singled out for independent verification — the `SecurityConfig` proof-endpoint matcher scoping and the D8 email-failure-never-rolls-back guarantee — are both confirmed by direct source inspection plus dedicated, currently-passing tests, not merely by design-doc prose. The two WARNING items are documentation-hygiene only and do not block archive.

```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:7605107cc9b1520a430131ee3715bc896a3fccf8dc4c85e5782b7e5599bfedcf
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 10/10
scenarios: 18/18
test_command: ./gradlew :api:billing:test :api:shared:test :api:app:test --rerun-tasks
test_exit_code: 0
test_output_hash: sha256:99b1592fc8e1857a304b9428ee63ca6eb641318e846079492de1c95c40a1f65e
build_command: ./gradlew :api:billing:jacocoTestCoverageVerification --rerun-tasks
build_exit_code: 0
build_output_hash: sha256:4063f19d39697d5028ebe8025ccd66bb0d60c1688231d753f8eeeefd1f73f2ee
```

## Verification Report

**Change**: billing-physical-transfer-capacity-exception (#36, US-BILLING-008)
**Version**: develop @ `2b1fa73` (5/5 PRs merged: #285, #286, #287, #288, #289)
**Mode**: Strict TDD

### Completeness

| Metric | Value |
|--------|-------|
| Tasks total | 45 |
| Tasks complete | 45 |
| Tasks incomplete | 0 |
| Success Criteria total (proposal.md) | 10 |
| Success Criteria complete | 10 |

### Build & Tests Execution

**Build**: PASS

```text
./gradlew :api:billing:jacocoTestCoverageVerification --rerun-tasks
BUILD SUCCESSFUL in 1m 7s (10 actionable tasks: 10 executed)
95% domain+application / 90% infrastructure gate held on :api:billing.
```

**Tests**: PASS — 1249 passed / 0 failed / 0 skipped (fresh run, `--rerun-tasks`, JUnit XML aggregated, not console tail)

```text
./gradlew :api:billing:test :api:shared:test :api:app:test --rerun-tasks
BUILD SUCCESSFUL in 4m (22 actionable tasks: 22 executed)

api:billing  — 808 tests, 0 failures, 0 errors, 0 skipped
api:shared   —  64 tests, 0 failures, 0 errors, 0 skipped
api:app      — 377 tests, 0 failures, 0 errors, 0 skipped
TOTAL        — 1249 tests, 0 failures, 0 errors, 0 skipped
```

Note on flakiness observed during verification: an earlier, separate fresh `--rerun-tasks` run of
`:api:app:test` alone produced 11 failures, all in `SubscriptionCheckoutIntegrationTest`
(`UnknownContentTypeException` on `TestRestTemplate.exchange`, `text/plain` response instead of
JSON). That test class is untouched by this change (confirmed via `git log` — last modified at
`694c42f`, before this change existed). Re-running that exact class in isolation passed 12/12
cleanly, and a second full fresh `:api:app:test --rerun-tasks` run passed 377/377 cleanly, and the
final combined run recorded above (billing+shared+app in one invocation) also passed 377/377 for
`api:app`. This is transient local resource contention under back-to-back heavy Testcontainers
Gradle invocations, not a regression introduced by this change — it does not touch any file this
change modified, and is non-reproducible on repeat. Reported here as observed evidence per the
verification contract's evidence-preservation requirement, not treated as a blocking finding.

**Coverage**: `:api:billing` 95% domain+application / 90% infrastructure gate → PASS (fresh
`jacocoTestCoverageVerification` run, not cached)

### Spec Compliance Matrix

#### Capability: `bank-transfer-physical-purchase`

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Creating a bank-transfer physical purchase returns transfer instructions | Response carries usable bank details and creates one Payment | `PhysicalPurchaseIntegrationTest > bank_transfer_checkout_returns_transfer_instructions_with_no_hold_and_no_provider_call` + `CreateBankTransferPhysicalPurchaseUseCaseImplTest` (task 3.1/3.6) | COMPLIANT |
| Creation reserves no capacity and calls no provider | No hold row and no provider call | same integration test (`holdRepository.findAll()).isEmpty()`, `verifyNoInteractions(paymentPreferencePort)`) + unit 3.6 (structural: no `PhysicalCapacityHoldPort`/`PaymentPreferencePort` collaborator exists at all) | COMPLIANT |
| Routing preserves the existing Mercado Pago checkout unchanged | Mercado Pago checkout behaves exactly as before | `PhysicalPurchaseIntegrationTest > mercado_pago_checkout_through_the_router_still_creates_a_hold_exactly_as_before` + `RoutingCreatePhysicalPurchaseCheckoutUseCaseTest` + full pre-existing MP suite unmodified and green | COMPLIANT |
| An expired quote is rejected with 410 on the bank-transfer path | Expired quote is rejected, no Payment created | `PhysicalPurchaseIntegrationTest > bank_transfer_checkout_is_rejected_with_410_for_an_expired_quote_and_creates_no_payment` + unit 3.3 | COMPLIANT |
| Creation shares the daily bank-transfer creation budget | Prior subscription creations count toward the physical budget | `BankTransferCreationBudgetIntegrationTest` (real Testcontainers Redis) — `ten_physical_purchases_then_one_subscription_also_trips_the_shared_budget`, `the_eleventh_bank_transfer_creation_in_one_day_is_rejected_whether_mixed_subscriptions_or_purchases` | COMPLIANT |
| A rejected bank-transfer physical payment releases nothing | Rejection releases nothing because nothing was held | `PhysicalPurchaseIntegrationTest > rejecting_a_bank_transfer_physical_payment_creates_no_purchase_and_releases_nothing` | COMPLIANT |

#### Capability: `physical-purchase-checkout` (delta)

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| A visibly-full quote is rejected with 409 (real guarantee MP / best effort BANK_TRANSFER) | Full quote is rejected before charging (MERCADO_PAGO) | `PhysicalPurchaseIntegrationTest > checkout_is_rejected_with_409_when_the_quoted_session_is_visibly_full_...` (pre-existing, unmodified, green) | COMPLIANT |
| same | Passing the check is now a real guarantee (MERCADO_PAGO) | `PhysicalPurchaseIntegrationTest > two_concurrent_checkouts_for_the_last_spot_guarantee_one_201_and_zero_pa...` (pre-existing, unmodified, green) | COMPLIANT |
| same | A visibly-full quote is rejected but the rejection is not binding (BANK_TRANSFER) | `PhysicalPurchaseIntegrationTest > bank_transfer_checkout_is_rejected_with_409_when_the_quoted_session_is_visibly_full_and_creates_no_payment` (409, zero Payment rows, zero hold rows) | COMPLIANT |
| Checkout creates no capacity assignment; a hold only for MERCADO_PAGO | Pending MERCADO_PAGO checkout leaves assignments untouched but holds the spots | `mercado_pago_checkout_through_the_router_still_creates_a_hold_exactly_as_before` asserts the hold exists but does **not** assert `assignmentRepository` is unaffected | PARTIAL |
| same | Pending BANK_TRANSFER checkout leaves assignments and holds both untouched | `bank_transfer_checkout_returns_transfer_instructions_with_no_hold_and_no_provider_call` asserts `holdRepository.findAll()).isEmpty()` but does **not** assert `assignmentRepository` is unaffected | PARTIAL |

#### Capability: `presential-purchase-fulfillment` (delta)

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Coverage period and eligible sessions are computed at confirmation, never at quote time | Monthly coverage counts forward from confirmation (no hold) | `PhysicalCapacityAssignmentOutboxEventHandlerTest > no_hold_for_the_payment_still_resolves_coverage_and_calls_assignAll` (pre-existing) + `PhysicalPurchaseIntegrationTest > an_approved_bank_transfer_purchase_with_available_capacity_reaches_assigned` (new, MONTHLY quote, no hold) | COMPLIANT |
| same | Held purchase uses the hold's session set, not a recomputed one | `PhysicalCapacityAssignmentOutboxEventHandlerTest > converted_hold_creates_purchase_and_marks_assigned_without_recomputation` (pre-existing, unmodified, green) | COMPLIANT |
| same | Approved bank-transfer purchase with available capacity reaches ASSIGNED | `PhysicalPurchaseIntegrationTest > an_approved_bank_transfer_purchase_with_available_capacity_reaches_assigned` | COMPLIANT |
| same | Approved bank-transfer purchase with unavailable capacity reaches EXCEPTION | `PhysicalPurchaseIntegrationTest > an_approved_bank_transfer_purchase_with_unavailable_capacity_reaches_exception` (all-or-nothing, `status_type` COMPLETED, #209 `PurchaseExceptioned` outbox row + 2-recipient email asserted) | COMPLIANT |

#### Capability: `bank-transfer-subscription` (delta)

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Creating a bank-transfer subscription or physical purchase shares one daily budget | Response carries usable bank details | `CreateBankTransferSubscriptionUseCaseImplTest > creates_the_payment_awaiting_manual_verification_and_the_pending_subscription` (pre-existing, renamed call site only, green) | COMPLIANT |
| same | An 11th daily bank-transfer request is rejected | `CreateBankTransferSubscriptionUseCaseImplTest > an_exhausted_daily_budget_creates_neither_payment_nor_subscription` (pre-existing, renamed call site only, green) | COMPLIANT |
| same | The budget counts subscriptions and physical purchases together | `BankTransferCreationBudgetIntegrationTest` (real Redis, mixed-rail scenarios) | COMPLIANT |

**Compliance summary**: 16/18 scenarios fully COMPLIANT, 2/18 PARTIAL (both in `physical-purchase-checkout` Requirement 2 — the "no new assignment during a pending checkout" half of each scenario is structurally true, since neither `CreatePhysicalPurchaseCheckoutUseCaseImpl` nor `CreateBankTransferPhysicalPurchaseUseCaseImpl` has any collaborator that can write a `physical_capacity_assignments` row, confirmed by source inspection — but no runtime test asserts this fact for either `paymentMethod` at the pending-checkout point in time).

### Correctness (Static Evidence)

| Requirement / Claim | Status | Notes |
|---|---|---|
| D1 — no capacity hold in the new use case | Confirmed | `CreateBankTransferPhysicalPurchaseUseCaseImpl` has exactly 6 collaborators (`PhysicalCourseQuoteRepository`, `PaymentRepository`, `PhysicalCourseAvailabilityPort`, `BankTransferRateLimitPort`, `Clock`, `BankAccountDetails`) — no `PhysicalCapacityHoldPort` field, import, or call anywhere in the class |
| D4 — one shared rate-limit budget | Confirmed | `rg` across `api/billing/src/main` + `api/app/src/main` finds exactly 2 production call sites of `consumeBankTransferCreation` (`CreateBankTransferSubscriptionUseCaseImpl:68`, `CreateBankTransferPhysicalPurchaseUseCaseImpl:80`); zero remaining occurrences of `consumeSubscriptionCreation`; `BankTransferCreationBudgetIntegrationTest` proves the shared counter against a real Testcontainers Redis (`GenericContainer("redis:7-alpine")`, real `LettuceConnectionFactory`, real `RedisBankTransferRateLimitPort`), never a mock |
| D5 — `release()` no-op for Physical, unchanged | Confirmed | `PaymentFulfillmentService.release(Payment)` still guards only on `instanceof PaymentTarget.Virtual`; no `Physical` branch was added |
| C1 — `PHY-BT-` vs `PHY-` collision avoidance | Confirmed | `CreateBankTransferPhysicalPurchaseUseCaseImpl.EXTERNAL_REFERENCE_PREFIX = "PHY-BT-"`; MP arm (`CreatePhysicalPurchaseCheckoutUseCaseImpl`) uses `"PHY-"`; `CreateBankTransferPhysicalPurchaseUseCaseImplTest > the_bank_transfer_reference_is_provably_different_from_the_mercado_pago_reference` asserts the two differ for the same `(userId, idempotencyKey)` |
| C3 — router delegates the MP arm byte-identically | Confirmed | `RoutingCreatePhysicalPurchaseCheckoutUseCase.create`: `case MERCADO_PAGO -> mercadoPagoUseCase.create(command);` — a bare delegation, no added precondition. (The `BANK_TRANSFER` arm additionally converts a bridge DTO at the router boundary — a documented Phase 4 deviation that does not touch the MP arm.) |
| ArchUnit widening (C8) | Confirmed | `checkout_use_case_should_not_depend_on_physical_module` covers all 3 classes (`CreatePhysicalPurchaseCheckoutUseCaseImpl`, `CreateBankTransferPhysicalPurchaseUseCaseImpl`, `RoutingCreatePhysicalPurchaseCheckoutUseCase`); fresh run — PASSED (7/7 ArchUnit tests green, including this one) |
| No new `com.menta.physical..` dependency | Confirmed | `rg "com\.menta\.physical"` across every new/modified main file in this change (use case, router, helper, port, DTOs, config, MP impl) — 0 matches |
| Phase 5 bugfix (`providerPaymentId` nullable) | Confirmed | `PaymentCompletedOutboxPayload.providerPaymentId` validated by `requireBoundedIfPresent` (null legitimate, blank-if-present rejected); `PhysicalCapacityAssignmentOutboxEventHandler` (the consumer) — `rg "providerPaymentId"` — 0 matches; independently re-confirmed by reading the handler's full field usage (`payload.paymentId()`, `payload.confirmedAt()` only) |
| OpenAPI contract | Confirmed | `billing-v1.yaml` documents `BANK_TRANSFER` on `CreatePhysicalPurchaseRequest.paymentMethod` and `bankTransferInstructions` on the physical checkout response; `npx @redocly/cli lint` → valid, exactly the 2 pre-existing warnings (`info-license`, `no-server-example.com`), 0 new |
| Scope discipline | Confirmed | `git diff --stat 8398321..2b1fa73` for `ResolvePaymentProofUseCaseImpl.java`, `SubmitPaymentProofUseCaseImpl.java`, `PaymentController.java`, `PaymentAdminController.java`, `PaymentDecisionNotification.java`, `PaymentDecisionNotificationPort.java`, `SpringMailPaymentDecisionNotificationAdapter.java`, `PaymentAuditRepositoryAdapter.java`, `BillingAuditLogJpaRepository.java` — zero changes across all 9 files |
| US doc divergence documentation | Confirmed | `docs/user-stories/US-BILLING-008.md` — `Estado: Implementado (#36)`; explicit `D1` and `D6` bullets in a "Decisiones de implementación" section |
| Tasks / Success Criteria completeness | Confirmed | `tasks.md` — 45/45 `[x]`, 0 unchecked; `proposal.md` — 10/10 Success Criteria `[x]`, 0 unchecked |

### Coherence (Design)

| Decision | Followed? | Notes |
|---|---|---|
| D1 (no hold) | Yes | Verified structurally, see Correctness table |
| D2 (routing, not a new endpoint) | Yes | `PhysicalPurchaseController` unchanged; router wired in `BillingConfiguration` |
| D3 (quote validity unchanged) | Yes | 410 guard on `expiresAt` unchanged, tested |
| D4 (shared budget) | Yes | Verified structurally + via real-Redis integration test |
| D5 (release no-op) | Yes | Verified structurally |
| D6 (documented divergence) | Yes | US doc + tasks.md both document it |
| D7 (best-effort availability check) | Yes | `PhysicalCoverageAvailability.requireComplete` runs and its plan is discarded; tested |
| C1 (`PHY-BT-` prefix) | Yes | Verified + tested |
| C4 (pure-move extraction, zero constructor churn) | Yes | `CreatePhysicalPurchaseCheckoutUseCaseImplTest` passes unmodified after the extraction |
| C5 (nullable-field DTO, not sealed hierarchy) | Yes | `PhysicalPurchaseCheckoutResult` gained a nullable `bankTransferInstructions` field, pulled forward to Phase 4 (documented deviation) |
| C8 (ArchUnit widened, MP arm's `com.menta.shared.physical..` dependency left untouched) | Yes | Confirmed — the rule stays scoped to `com.menta.physical..` only, deliberately not widened to `com.menta.shared.physical..`, to avoid a false positive on the MP arm's legitimate hold-command dependency (documented Phase 4 deviation, verified correct by a real RED test run per apply-progress) |
| C9 (everything after creation is verify-only) | Yes | Except the one genuinely discovered defect (`providerPaymentId`), which lived in a producer class outside the "non-negotiable constraints" list and was fixed test-first, minimally, and documented in three places |

### Issues Found

**CRITICAL**: None

**WARNING**:
1. `physical-purchase-checkout` Requirement "Checkout creates no capacity assignment; a hold only for MERCADO_PAGO" has two scenarios ("Pending MERCADO_PAGO ... leaves assignments untouched" and "Pending BANK_TRANSFER ... leaves assignments and holds both untouched") that are only **PARTIALLY** tested: the existing tests (`mercado_pago_checkout_through_the_router_still_creates_a_hold_exactly_as_before`, `bank_transfer_checkout_returns_transfer_instructions_with_no_hold_and_no_provider_call`) assert the hold state correctly for each rail but never assert `assignmentRepository` row counts immediately after a pending checkout, for either `paymentMethod`. The underlying invariant is structurally guaranteed (no assignment-writing collaborator exists in either checkout use case), so functional risk is low, but per Strict TDD's "a spec scenario is compliant only when a covering test passed at runtime" rule this is not yet a fully proven scenario. Recommend adding one `assertThat(assignmentRepository.findAll()).isEmpty()` line to each of the two named tests before archiving, or explicitly accepting the structural-proof as sufficient evidence in the archive record.

**SUGGESTION**: None

### Test Layer Distribution (informational)

| Layer | Representative files |
|---|---|
| Unit (application) | `CreateBankTransferPhysicalPurchaseUseCaseImplTest`, `RoutingCreatePhysicalPurchaseCheckoutUseCaseTest`, `PhysicalPurchaseCheckoutResultTest`, `PaymentCompletedOutboxPayloadTest`, `PublishPhysicalPaymentCompletedUseCaseTest` |
| Unit (controller, MockMvc) | `PhysicalPurchaseControllerTest` |
| Integration (Testcontainers MySQL, real HTTP) | `PhysicalPurchaseIntegrationTest`, `PaymentExpirySweepIntegrationTest` |
| Integration (real Testcontainers Redis) | `BankTransferCreationBudgetIntegrationTest` |
| Architecture | `ArchitectureTest` |

### Verdict

**PASS WITH WARNINGS**

45/45 tasks complete, 10/10 Success Criteria complete, 1249/1249 tests passing on a fresh
`--rerun-tasks` run, coverage gate held, ArchUnit held, OpenAPI valid, zero scope leakage into the
9 non-negotiable files, and every D1/D4/D5/C1/C3/C8/Phase-5-bugfix claim independently re-verified
against source rather than trusted from apply-progress. One non-blocking WARNING: two
`physical-purchase-checkout` scenarios about assignment-row non-interference during a pending
checkout are structurally true but not runtime-asserted by name. No CRITICAL findings; ready for
`sdd-archive` at the orchestrator's discretion, with the WARNING recorded for the archive record or
addressed first.

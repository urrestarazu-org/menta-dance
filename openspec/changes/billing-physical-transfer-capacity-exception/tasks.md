# Tasks: Bank-transfer physical purchase and capacity exception (#36, US-BILLING-008)

## Fixed Facts (do not reopen)

D1 (no hold, capacity computed at approval by the already-shipped `HoldNotFound` branch) —
D2 (no new endpoint; `paymentMethod` routing) — D3 (quote validity stays 1h, payment lifetime
decoupled) — D4 (one shared bank-transfer creation budget, `consumeBankTransferCreation`) — D5
(`release` stays a no-op for `Physical`) — D6 (documented divergence from #36's literal "hold
expired" wording; inherits the shipped `/api/v1/admin/billing/payments/...` prefix) — D7
(creation-time availability check is best-effort, non-binding) are resolved with the user. C1
(`PHY-BT-` + deterministic `uuid5(userId:idempotencyKey)`, distinct from the MP arm's `PHY-` to
prevent cross-rail collision) — C2 (six-collaborator impl, no `PaymentPreferencePort`, no
`PhysicalCapacityHoldPort`, budget→replay→quote→availability→save order) — C3
(`RoutingCreatePhysicalPurchaseCheckoutUseCase`, byte-identical shape to
`RoutingCreateSubscriptionCheckoutUseCase`) — C4 (`resolveCoveragePlan`'s body extracted verbatim
to package-private static `PhysicalCoverageAvailability.requireComplete`, zero constructor/bean
churn) — C5 (`PhysicalPurchaseCheckoutResult` nullable-field + factory, not a sealed hierarchy) —
C6 (`consumeSubscriptionCreation` → `consumeBankTransferCreation`, 12 total occurrences, key/limit/
window untouched) — C7 (expiry sweep needs **no** production change, verified against
`PaymentJpaRepository:28-31`, `PaymentExpiryWorker:48-57`, `PaymentFulfillmentService:52-58` — the
work is a regression test) — C8 (`ArchitectureTest`'s name-scoped rule widened to the three new
classes) — C9 (everything after creation is verify-only, zero production edits) are locked from
design. Two facts refine the proposal's "Unchanged" framing, carried here as facts, not questions:
**`CreatePhysicalPurchaseCheckoutUseCaseImpl` IS modified** (one method body → one delegation
line) and **`ArchitectureTest` IS modified** (widened predicate).

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~1,300–1,650 total (1 renamed method across 5 files, 1 extracted static helper, 1 new port + impl + 7-scenario unit suite, 1 router + wiring + ArchUnit widening, 1 DTO arm + 8 integration scenarios + OpenAPI/Bruno/US doc) |
| Per-phase estimate | P1 ~30–50; P2 ~60–90; P3 ~340–400; P4 ~140–180; P5 ~400–480 |
| 400-line budget risk | High — P3 (new use case + its 7-scenario RED suite) and P5 (DTO arm + 8 integration scenarios + contract/doc updates) each approach or exceed 400 lines |
| Chained PRs recommended | Yes |
| Suggested split | 5 PRs, P1 → P2 → P3 → P4 → P5. Design's own slice 2 (port + impl + router + C4 extraction + wiring + ArchUnit) is split into P2/P3/P4 per design's explicit suggestion to isolate the C4 pure-move as its own commit — carried one step further by also isolating router+wiring+ArchUnit, since the new-use-case unit suite alone (P3) already approaches budget |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main (session default; matches #45 and #33) |

```text
Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High
```

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| P1 | Rate-limit rename (C6), zero behavior change | PR 1 | `:api:billing:test --tests "*RedisBankTransferRateLimitPortTest*" --tests "*CreateBankTransferSubscriptionUseCaseImplTest*"` | N/A — mechanical rename, no runtime scenario needed | Revert 3 production files + 2 test files; `consumeSubscriptionCreation` restored, same Redis key |
| P2 | C4 pure-move extraction — `PhysicalCoverageAvailability` | PR 2 | `:api:billing:test --tests "*CreatePhysicalPurchaseCheckoutUseCaseImplTest*"` (unmodified, must stay green) | N/A — behavior-preserving static move | Revert 2 files; `resolveCoveragePlan`'s body returns inline, MP path unaffected |
| P3 | `CreateBankTransferPhysicalPurchaseUseCase` port + impl | PR 3 | `:api:billing:test --tests "*CreateBankTransferPhysicalPurchaseUseCaseImplTest*"` | N/A — Mockito unit only, unreachable until PR 4 wires the router | Revert 2 new files; nothing calls them yet, zero blast radius |
| P4 | Router + `BillingConfiguration` wiring + ArchUnit widening | PR 4 | `:api:billing:test --tests "*RoutingCreatePhysicalPurchaseCheckoutUseCaseTest*" --tests "*BillingConfigurationTest*" --tests "*ArchitectureTest*"` | N/A at unit level; `BANK_TRANSFER` becomes reachable through the real endpoint | Revert router + config + ArchUnit files; endpoint returns to the pre-router MP-only guard |
| P5 | DTO/contract arm + full integration coverage + OpenAPI/Bruno/docs | PR 5 | `:api:billing:test --tests "*PhysicalPurchaseCheckoutResultTest*"` + `:api:app:test --tests "*PhysicalPurchaseIntegrationTest*" --tests "*PaymentExpirySweepIntegrationTest*"` | Testcontainers MySQL + Redis, full app context | Revert P5 files only; creation still works end-to-end, response just loses the instructions field and untested edges |

## Phase 1: Rate-limit rename (C6) — mechanical, behavior-preserving

- [x] 1.1 RED: rename all 6 call-sites in `RedisBankTransferRateLimitPortTest.java` (lines 57, 67, 93, 101, 112, 118) from `consumeSubscriptionCreation` to `consumeBankTransferCreation` — fails to compile until 1.3.
- [x] 1.2 RED: rename all 3 call-sites in `CreateBankTransferSubscriptionUseCaseImplTest.java` (lines 73, 157, 165) to `consumeBankTransferCreation` — fails to compile until 1.4.
- [x] 1.3 GREEN: rename `BankTransferRateLimitPort.consumeSubscriptionCreation` → `consumeBankTransferCreation` in `BankTransferRateLimitPort.java:14` (+ javadoc `:13`).
- [x] 1.4 GREEN: rename the `@Override` implementation in `RedisBankTransferRateLimitPort.java:73` — `SUBSCRIPTION_CREATION_KEY_PREFIX`, the limit, and the window stay untouched.
- [x] 1.5 GREEN: rename the only production call site in `CreateBankTransferSubscriptionUseCaseImpl.java:68`.
- [x] 1.6 Verify: `./gradlew :api:billing:test --tests "*RedisBankTransferRateLimitPortTest*" --tests "*CreateBankTransferSubscriptionUseCaseImplTest*"` green, zero behavior change (same key/limit/window). P1 ready for PR.

## Phase 2: C4 pure-move extraction (needs Phase 1 merged)

- [x] 2.1 Baseline: run `./gradlew :api:billing:test --tests "*CreatePhysicalPurchaseCheckoutUseCaseImplTest*"` before touching any file — records the pre-move 409/410 assertions as the safety net (design risk #4).
- [x] 2.2 GREEN: create `billing/application/usecase/PhysicalCoverageAvailability.java` — package-private static `requireComplete(PhysicalCourseAvailabilityPort, PhysicalCourseQuote, Instant): CoveragePlanner.Plan.Complete`, verbatim body of `resolveCoveragePlan` (`CreatePhysicalPurchaseCheckoutUseCaseImpl.java:147-165`).
- [x] 2.3 GREEN: modify `CreatePhysicalPurchaseCheckoutUseCaseImpl.java` — `resolveCoveragePlan`'s body becomes a one-line delegation to `PhysicalCoverageAvailability.requireComplete(...)`; constructor, guard, and every other line unchanged.
- [x] 2.4 Verify: re-run `CreatePhysicalPurchaseCheckoutUseCaseImplTest` unmodified — same 409/410 assertions pass byte-identically, proving the extraction is behavior-preserving. P2 ready for PR.

## Phase 3: `CreateBankTransferPhysicalPurchaseUseCase` port + impl (needs Phase 2 merged)

- [x] 3.1 RED: `CreateBankTransferPhysicalPurchaseUseCaseImplTest` (new) — Mockito `InOrder` asserts `rateLimitPort.consumeBankTransferCreation` → `paymentRepository.findByExternalReference` (replay) → `quoteRepository.findById` → `PhysicalCoverageAvailability.requireComplete` → `paymentRepository.save` (C2).
- [x] 3.2 RED: same class — rate-limited user throws `BankTransferRateLimitedException` (429) with zero other port interactions.
- [x] 3.3 RED: same class — expired or unknown `quoteId` throws `PhysicalCourseQuoteExpiredException` (410) with zero writes.
- [x] 3.4 RED: same class — visibly-full quote throws `PhysicalCapacityUnavailableException` (409) with zero writes; the returned plan is discarded, nothing reserved (D1, D7).
- [x] 3.5 RED: same class — replaying `(userId, idempotencyKey)` returns the same result, writes no second `Payment`, but still consumes the budget (C1 idempotency consequence).
- [x] 3.6 RED: same class — the created `Payment` is `AwaitingManualVerification` with the CBU as `expectedMerchantAccountId`, `PaymentTarget.Physical(quoteId)`, quote's amount; the impl has no `PaymentPreferencePort`/`PhysicalCapacityHoldPort` collaborator to interact with at all (D1) — structural proof, stronger than a per-test `verifyNoInteractions` on a mock this class cannot reach.
- [x] 3.7 RED: same class — a `MERCADO_PAGO` reference and a `BANK_TRANSFER` reference for the same `(userId, idempotencyKey)` differ (`PHY-BT-` vs `PHY-`, C1 collision-avoidance), computed through each use case's own production code, not a re-implemented formula.
- [x] 3.8 GREEN: `billing/application/port/in/CreateBankTransferPhysicalPurchaseUseCase.java` (new) — `create(CreatePhysicalPurchaseCheckoutCommand): PhysicalPurchaseBankTransferCheckoutResult`. Deviation from this task's original sketch: the return type is a new phase-scoped DTO, not `PhysicalPurchaseCheckoutResult` — see Phase 3 apply-progress note below for why.
- [x] 3.9 GREEN: `billing/application/usecase/CreateBankTransferPhysicalPurchaseUseCaseImpl.java` (new) — six collaborators (`PhysicalCourseQuoteRepository`, `PaymentRepository`, `PhysicalCourseAvailabilityPort`, `BankTransferRateLimitPort`, `Clock`, `BankAccountDetails`); `externalReferenceFor` with `EXTERNAL_REFERENCE_PREFIX = "PHY-BT-"` + `UUID.nameUUIDFromBytes`.
- [x] 3.10 Verify: `./gradlew :api:billing:test --tests "*CreateBankTransferPhysicalPurchaseUseCaseImplTest*"` green (9/9); `:api:billing:jacocoTestCoverageVerification` (95%/90%) green. P3 ready for PR.

**Deviation note (task 3.8)**: `CreateBankTransferPhysicalPurchaseUseCase.create()` returns the new
`com.menta.billing.application.dto.PhysicalPurchaseBankTransferCheckoutResult` record
(`paymentId, quoteId, status, externalReference, bankTransferInstructions`), not
`PhysicalPurchaseCheckoutResult`. Reason: `PhysicalPurchaseCheckoutResult` has no
`bankTransferInstructions` field yet — adding it is explicitly Phase 5's job (design C5,
`fromBankTransfer` factory mirroring `SubscriptionCheckoutResult`). Phase 3's scope is
`BillingConfiguration`-free and `PhysicalPurchaseCheckoutResult`-free by explicit instruction, so a
temporary bridge DTO was created instead of reaching into either. Phase 4's
`RoutingCreatePhysicalPurchaseCheckoutUseCase` will need to reconcile the two return types when it
wires both arms behind one `CreatePhysicalPurchaseCheckoutUseCase`-shaped router — most likely by
Phase 5 adding `PhysicalPurchaseCheckoutResult.fromBankTransfer(...)` first and this phase-scoped
DTO being retired/converted at that point, OR Phase 4 converting inline. Flagged here for Phase 4/5
to resolve explicitly, not silently.

## Phase 4: Router + wiring + ArchUnit widening (needs Phase 3 merged)

- [ ] 4.1 RED: `RoutingCreatePhysicalPurchaseCheckoutUseCaseTest` (new, mirrors `RoutingCreateSubscriptionCheckoutUseCaseTest`) — `MERCADO_PAGO` dispatches to the MP delegate, `verifyNoInteractions` on the bank-transfer delegate.
- [ ] 4.2 RED: same class — `BANK_TRANSFER` dispatches to the bank-transfer delegate, `verifyNoInteractions` on the MP delegate; the router adds no precondition.
- [ ] 4.3 GREEN: `billing/application/usecase/RoutingCreatePhysicalPurchaseCheckoutUseCase.java` (new) — two-arm exhaustive `switch` on `paymentMethod`, byte-identical shape to `RoutingCreateSubscriptionCheckoutUseCase.java:19-38`.
- [ ] 4.4 GREEN: `BillingConfiguration.java` — new `createBankTransferPhysicalPurchaseUseCase` bean mirroring `createBankTransferSubscriptionUseCase` (`:251-265`), reusing the four `billing.bank-transfer.account.*` `@Value`s and `new BankAccountDetails(cbu, alias, holder, cuit)`.
- [ ] 4.5 GREEN: `BillingConfiguration.java` — modify `createPhysicalPurchaseCheckoutUseCase` (`:479-491`): add the new bean as a parameter, wrap `new TransactionalCreatePhysicalPurchaseCheckoutUseCase(new RoutingCreatePhysicalPurchaseCheckoutUseCase(mercadoPagoUseCase, createBankTransferPhysicalPurchaseUseCase))` — transactional decorator moves outside the router; `mercadoPagoUseCase` construction unchanged.
- [ ] 4.6 RED: extend `ArchitectureTest.checkout_use_case_should_not_depend_on_physical_module` (`:80-86`) — widen `haveSimpleName` to also match `CreateBankTransferPhysicalPurchaseUseCaseImpl` and `RoutingCreatePhysicalPurchaseCheckoutUseCase`; assert none references `com.menta.physical..` or `com.menta.shared.physical..` (C8).
- [ ] 4.7 GREEN: confirm 4.6 passes with no production import added (C8 — the new impl's collaborators live entirely in `com.menta.billing.application.{dto,port,usecase}`).
- [ ] 4.8 Verify: `./gradlew :api:billing:test --tests "*RoutingCreatePhysicalPurchaseCheckoutUseCaseTest*" --tests "*BillingConfigurationTest*" --tests "*ArchitectureTest*"` and `:api:billing:jacocoTestCoverageVerification` green. P4 ready for PR.

## Phase 5: DTO/contract arm + integration coverage (needs Phase 4 merged)

- [ ] 5.1 RED: extend `PhysicalPurchaseCheckoutResultTest` — `fromBankTransfer` leaves `providerPreferenceId`/`checkoutUrl` null and populates `bankTransferInstructions`; `from` leaves `bankTransferInstructions` null.
- [ ] 5.2 GREEN: `PhysicalPurchaseCheckoutResult.java` — add nullable `bankTransferInstructions` field + `fromBankTransfer` factory + private `build`, mirroring `SubscriptionCheckoutResult` (`:22-58`).
- [ ] 5.3 GREEN: `PhysicalPurchaseCheckoutResponse.java` (web DTO) — same nullable field, passed through, mirroring `SubscriptionCheckoutResponse:28,34`.
- [ ] 5.4 RED: integration test (Testcontainers MySQL, `:api:app:test`) — `201` with CBU/alias/holder/CUIT/amount/reference; zero `physical_capacity_holds` rows; zero provider-port calls (spec `bank-transfer-physical-purchase`, Scenarios "Response carries usable bank details..." and "No hold row and no provider call").
- [ ] 5.5 RED: integration test — same request with `paymentMethod: MERCADO_PAGO` through the router is byte-identical to today, hold included (spec `bank-transfer-physical-purchase`, Scenario "Mercado Pago checkout behaves exactly as before"; MP regression).
- [ ] 5.6 RED: integration test — expired quote on the bank-transfer path → `410`, no `Payment` row (spec `bank-transfer-physical-purchase`, Scenario "Expired quote is rejected, no Payment created").
- [ ] 5.7 RED: integration test — a visibly-full quote on the bank-transfer path → `409 CAPACITY_UNAVAILABLE`, zero `Payment` rows, zero hold rows — the check is a read, not a reservation (spec `physical-purchase-checkout`, Scenario "A visibly-full quote is rejected but the rejection is not binding (BANK_TRANSFER)").
- [ ] 5.8 RED: integration test (Testcontainers Redis) — 11th bank-transfer creation in one day → `429`, whether the prior ten were subscriptions, physical purchases, or a mix (spec `bank-transfer-subscription`, Scenario "The budget counts subscriptions and physical purchases together"; D4).
- [ ] 5.9 RED: integration test — proof upload → admin approve → `ASSIGNED` with one `physical_capacity_assignments` row per eligible session computed from `confirmedAt`, through the unchanged `HoldNotFound` branch (spec `presential-purchase-fulfillment`, Scenario "Approved bank-transfer purchase with available capacity reaches ASSIGNED").
- [ ] 5.10 RED: integration test — capacity unavailable at approval → `EXCEPTION`, zero partial assignments, all-or-nothing, `status_type` stays `COMPLETED` (spec `presential-purchase-fulfillment`, Scenario "...reaches EXCEPTION").
- [ ] 5.11 RED: integration test — admin reject → no `Purchase`, no assignment, `PaymentFulfillmentService.release` no-op for `PaymentTarget.Physical` (spec `bank-transfer-physical-purchase`, Scenario "Rejection releases nothing because nothing was held"; D5).
- [ ] 5.12 RED: extend `PaymentExpirySweepIntegrationTest` — a physical `AwaitingManualVerification` payment older than 72h with no proof expires and leaves zero `billing_purchases` rows (C7 — characterization test confirming the verified-not-assumed finding; no production change expected).
- [ ] 5.13 GREEN: confirmation-only pass across 5.4–5.12, since everything after creation is verify-only (C9) — fix only if a wiring gap surfaces.
- [ ] 5.14 GREEN: `api/openapi/billing-v1.yaml` — add `BANK_TRANSFER` + `bankTransferInstructions` on the physical checkout contract.
- [ ] 5.15 GREEN: `bruno/API - Direct/billing/` — new/updated request(s) for the physical `BANK_TRANSFER` checkout.
- [ ] 5.16 GREEN: `docs/user-stories/US-BILLING-008.md` — lift from Draft; record D1's and D6's divergences from the issue's literal wording, plus the one-line note that the shared daily budget (D4) now spans both product lines.
- [ ] 5.17 Verify: `./gradlew :api:billing:test :api:app:test` green; `:api:billing:jacocoTestCoverageVerification` (95%/90%) green; `./gradlew check` full regression; confirm every Success Criterion in `proposal.md`. P5 ready for PR — after merge, run `sdd-verify` against all requirements in `bank-transfer-physical-purchase`, `physical-purchase-checkout` delta, `presential-purchase-fulfillment` delta, and `bank-transfer-subscription` delta, then `sdd-archive`.

## Requirement → Task Coverage

| # | Requirement | Covered by |
|---|---|---|
| R1 | `BANK_TRANSFER` creation returns instructions | 3.1–3.9, 5.1–5.4 |
| R2 | No hold, no assignment, no provider call at creation (D1) | 3.6, 5.4 |
| R3 | Expired quote → 410, no `Payment` (D3) | 3.3, 5.6 |
| R4 | Visibly-full quote → 409, best effort, non-binding (D7) | 3.4, 5.7 |
| R5 | Shared per-user daily bank-transfer creation budget (D4) | 1.1–1.6, 5.8 |
| R6 | Approval → `ASSIGNED`/`EXCEPTION`, no new fulfillment code (D1, D6) | 5.9–5.10 |
| R7 | Rejection leaves no `Purchase`, releases nothing (D5) | 5.11 |
| R8 | 72h expiry leaves no `Purchase` | 5.12 |
| R9 | New use case references no `com.menta.physical..` (D1) | 4.6–4.7 |
| — | Mercado Pago path byte-identical | 2.1–2.4, 4.1–4.2, 5.5 |

## Next Steps

After **P5** merges and its integration tests are green: run `sdd-verify` against the
`bank-transfer-physical-purchase` requirements plus the `physical-purchase-checkout`,
`presential-purchase-fulfillment`, and `bank-transfer-subscription` delta requirements, then
`sdd-archive`.

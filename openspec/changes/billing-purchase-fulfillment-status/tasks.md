# Tasks: billing-purchase-fulfillment-status (#559)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~570 total (S0 ~300 artifacts, S1 ~340, S2 ~230); code slices well under the 800 budget |
| 400-line budget risk | Low (800 budget, each code slice < 400) |
| Chained PRs recommended | Yes |
| Suggested split | S0 artifacts -> S1 API -> S2 BFF |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 0 | OpenSpec artifacts | PR 0 (base develop) | N/A (docs) | N/A: no code | `openspec/changes/billing-purchase-fulfillment-status/` |
| 1 | API `fulfillment` + OpenAPI + docs | PR 1 (base develop, or PR 0 branch) | `./gradlew :api:billing:test :api:app:test` | Integration test (real bean, outbox path) | API files; BFF ignores extra field |
| 2 | BFF branching + copy | PR 2 (base PR 1 branch, retarget to develop on merge) | `./gradlew :bff:test` | Manual follow-up F.1 | BFF files only; revert first |

**Chain reminder (lesson #361):** each stacked PR MUST be retargeted to `develop`, or its head branch deleted on merge, before the next PR is merged; otherwise the child merges into a dead branch.

## Slice 0: Artifacts

- [x] 0.1 Commit `openspec/changes/billing-purchase-fulfillment-status/{proposal,design,tasks}.md` + `specs/*`. Covers: all. Done: `git status` clean, PR 0 opened.

## Slice 1: API (api:billing, api:app)

Base: `api/billing/src/{main,test}/java/com/menta/billing/...`

- [x] 1.1 RED `GetPaymentUseCaseImplTest`: four statuses mapped, no row -> null, non-owner -> 404 with `verifyNoInteractions(purchaseRepository)`. Covers: Nullable fulfillment; Null rule; Ownership. Done: fails (no field/ctor arg).
- [x] 1.2 GREEN `PaymentStatusResult` (+`FulfillmentStatus fulfillment`), `GetPaymentUseCaseImpl` (`@RequiredArgsConstructor`, `findByPaymentId` after owner check). Done: 1.1 passes.
- [x] 1.3 RED `PaymentControllerTest`: JSON has `fulfillment` (null serialized), no `reason`/`resolution*` keys. Covers: Internal detail never exposed. Done: fails.
- [x] 1.4 GREEN `PaymentStatusResponse` (`String fulfillment`, no `NON_NULL`) + mapping. Done: 1.3 passes.
- [x] 1.5 RED `BillingConfigurationTest`: get-payment bean `isInstanceOf(TransactionalGetPaymentUseCase)`. Covers: Read path in tx. Done: fails or documents missing ctor arg.
- [x] 1.6 GREEN `BillingConfiguration:330` inject `PurchaseRepository`. Done: 1.5 + billing context load pass.
- [x] 1.7 RED `PresentialPurchaseExceptionPathIntegrationTest` (`api:app`): real `GetPaymentUseCase` returns null after webhook, `EXCEPTION` after outbox; no MANDATORY tx error. Covers: Wiring resolves lookup in tx. Done: fails before 1.2/1.6, passes after.
- [x] 1.8 RED `PaymentStatusContractTest` (billing tests, SnakeYAML on `api/openapi/billing-v1.yaml`): path get 200/404, properties == record components, enum == `FulfillmentStatus.values()` + null, no reason/resolution/note. Covers: OpenAPI; drift fails. Done: fails (path absent).
- [x] 1.9 GREEN `api/openapi/billing-v1.yaml`: path, `PaymentStatusResponse` schema (`fulfillment: [string, "null"]`), 200/404. Done: 1.8 passes.
- [x] 1.10 `docs/06-BILLING-API.md`: field, enum, null rules, no-leak note. Done: reviewed vs spec.
- [x] 1.11 Gate: ArchUnit passes (no billing->physical); `./gradlew :api:billing:test :api:app:test`; `./gradlew :api:billing:jacocoTestCoverageVerification` (domain+application 85%, infra 85%) and `:api:app` floor >= 85%. Covers: Architecture rules. Done: green, ~340 lines.

## Slice 2: BFF (depends on Slice 1 contract)

Base: `bff/src/{main,test}/java/...`

- [x] 2.1 RED `PurchaseFulfillmentTest`: `fromUpstream` null/absent/unknown -> null, four known values. Covers: unknown treated as null. Done: fails.
- [x] 2.2 GREEN `PurchaseFulfillment` enum, `PaymentStatusView(PaymentProgress, PurchaseFulfillment)`. Done: 2.1 passes.
- [x] 2.3 RED `PhysicalPurchaseApiAdapterTest` (HTTP stub): maps COMPLETED+EXCEPTION; absent and unknown -> null. Covers: Adapter maps; field absent. Done: fails.
- [x] 2.4 GREEN `PhysicalPurchaseApiAdapter` (`PaymentWire(status, fulfillment)`), `PhysicalPurchaseApiClient`, `GetPaymentProgressUseCase(Impl)` return `PaymentStatusView`. Done: 2.3 passes.
- [x] 2.5 Ripple: `SubscriptionReturnControllerTest`, `QuotePhysicalPurchaseUseCaseImplTest` stubs -> `new PaymentStatusView(progress, null)`; assertions unchanged. Covers: Non-completed unaffected. Done: compile + pass.
- [x] 2.6 RED `PhysicalPurchaseControllerTest`: EXCEPTION (title "Pago recibido", info alert, approved body), RESOLVED (title "Compra resuelta", info alert, body), `verifyNoInteractions(getAssignedSessionsUseCase)`, no contact link, no "Estamos asignando"; ASSIGNED/PENDING/null/unknown call sessions; non-COMPLETED ignores fulfillment. Covers: EXCEPTION, RESOLVED, Assigned sessions, Non-completed. Done: fails.
- [x] 2.7 GREEN `PhysicalPurchaseController.classNotice` branching (`case null`), copy, `subscriptionNotice(view.progress())`. Done: 2.6 passes, `purchase-notice.html` untouched unless needed.
- [x] 2.8 Docs: BFF return section (states table, no sessions on EXCEPTION/RESOLVED). Done: reviewed.
- [x] 2.9 Gate: `./gradlew :bff:test` + `:bff:checkstyleMain :bff:checkstyleTest`; `:bff:jacocoTestCoverageVerification` (floor >= 85%). Done: green, ~230 lines.

## Follow-up (non-blocking)

- [ ] F.1 Manual validation on local stack (API 8081, BFF 8080): drive a purchase to EXCEPTION, open return page; confirm title, body, no sessions, no contact link; then RESOLVED. Record result in PR 2.

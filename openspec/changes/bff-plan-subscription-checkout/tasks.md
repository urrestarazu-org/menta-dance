# Tasks: BFF Plan Subscription Checkout (#361)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1,550 total: PR1 ~720, PR2 ~830 (incl. tests, docs) |
| 400-line budget risk | High (each PR under the 800 review budget) |
| Chained PRs recommended | Yes |
| Suggested split | PR1 (offer + login return) -> PR2 (checkout POST + return + docs) |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

Branches: existing `feature/bff-plan-subscription-checkout` is renamed (`git branch -m`) to `feature/bff-plan-subscription-checkout-s1-plans-offer` (PR1, base `develop`). PR2 branch `feature/bff-plan-subscription-checkout-s2-checkout-return` (base = S1 branch; retarget to `develop` after S1 merges). PR2 pushes only if PR2 is under 800 lines; PR2 diff must not show S1 changes.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | `/plans` offer by viewer/state, no-store, login return | PR 1 | `./gradlew :bff:test --tests "*Plans*" --tests "*SubscriptionApiAdapter*"` | Local BFF: anonymous -> Suscribirme -> login -> `/plans` | Revert S1; `/plans` back to catalog only |
| 2 | Checkout POST, return discriminator, docs | PR 2 | `./gradlew :bff:test --tests "*Subscription*" --tests "*PhysicalPurchase*"` | Simulator checkout + approved webhook | Revert S2; offer view stays, no POST route |

Gate for every commit batch: `./gradlew :bff:test :bff:checkstyleMain :bff:checkstyleTest` (includes ArchUnit). Paths relative to `bff/src/main/java/com/menta/bff` (main) and `bff/src/test/java/com/menta/bff` (test). Commits: conventional, Spanish, no AI attribution, one work unit each.

## PR1 - Offer view and login return (spec: bff-plans-view)

- [x] 1.1 RED: `SubscriptionApiAdapterTest` (WireMock) `/me` 200 ACTIVE/PENDING/EXPIRED, 404 NO_SUBSCRIPTION -> None, 404 other/503/timeout/401 -> ServiceUnavailable, token never logged. Covers: fail-closed, unexpected status.
- [x] 1.2 GREEN: `application/port/out/SubscriptionApiClient.java`, `application/dto/{CurrentSubscription}.java` (`SubscriptionCheckout` deferred to 5.1, unused in PR1), `infrastructure/adapter/SubscriptionApiAdapter.java` (`/me` only). Commit: `feat(bff): consultar la suscripcion actual del alumno (#361)`.
- [x] 2.1 RED: `GetPlansViewUseCaseImplTest` per viewer x state (ACTIVE/PENDING/EXPIRED/none/ANONYMOUS/OTHER); `/me` not called for non-STUDENT; one key per plan. Covers: button depends on role, token-only call, state table.
- [x] 2.2 GREEN: `application/dto/{PlansViewer,PlansOffer,PlansView}.java`, modify `GetPlansViewUseCase{,Impl}.java`, `UseCaseConfig.java` (key `Supplier<String>`). Commit: `feat(bff): componer la oferta de suscripcion en la vista de planes (#361)`.
- [x] 3.1 RED: `PlansControllerTest` viewer detection (ROLE_STUDENT + token; missing token fail-closed), `Cache-Control: no-store` non-anonymous, anonymous keeps shared cache, shared error view with no "Suscribirme". Covers: personalized not cacheable, billing unavailable.
- [x] 3.2 GREEN: `PlansController.java`, `templates/plans.html` (notices, `<code>` select-all, voseo copy, CSRF form with key, anonymous link to `/plans/suscribirme`). Commit: `feat(bff): mostrar estado de suscripcion y accion Suscribirme en planes (#361)`.
- [x] 4.1 RED: integration (`BaseIntegrationTest`) anonymous -> `/plans/suscribirme` -> login -> `/plans`; direct login -> `/dashboard`; no open redirect. Covers: anonymous returns after login.
- [x] 4.2 GREEN: `SubscriptionController` (`GET /plans/suscribirme` -> `redirect:/plans`), `BffSecurityConfig.java` (scoped `HttpSessionRequestCache`, `defaultSuccessUrl("/dashboard")` without alwaysUse). Commit: `feat(bff): volver a planes tras iniciar sesion (#361)`.
- [ ] 4.3 (gate run green locally; PR not opened yet) Run gate; open PR1 (base `develop`, `Closes #361` only on PR2; PR1 uses `Refs #361`).

## PR2 - Checkout, return, docs (spec: bff-plan-subscription-checkout)

- [ ] 5.1 RED: adapter POST tests: 201, incomplete body, 409 active (date)/conflict, 422, 400, 503; body `{planId, MERCADO_PAGO, key}`. GREEN: add `createSubscriptionCheckout` + exceptions to port/adapter. Commit: `feat(bff): crear checkout de suscripcion contra la API (#361)`.
- [ ] 5.2 RED then GREEN: `SubscribeToPlanUseCaseImplTest` / `SubscribeToPlanUseCase{,Impl}.java`. Covers: rendered key used, double submit same key.
- [ ] 6.1 RED: `SubscriptionControllerTest` + integration: CSRF-less 403 no upstream call, INSTRUCTOR 403, anonymous -> `/login`, policy-accepted redirect with session `{PAYMENT_ID, KIND=SUBSCRIPTION}`, rejected URL -> notice, error mapping 409x2/422/400/503 non-leaky.
- [ ] 6.2 GREEN: `POST /suscripciones` in `SubscriptionController.java`, `web/dto/SubscriptionForm.java`, `PurchaseFlowErrors.java` branches, `BffSecurityConfig` `hasRole("STUDENT")`. Commit: `feat(bff): suscribirse a un plan con pago en linea (#361)`.
- [ ] 7.1 RED: return tests: URL params ignored, 404 / no session -> subscription fallback (no `/classes`), COMPLETED "se esta activando", PENDING, REJECTED/CANCELLED/EXPIRED link `/plans`, latest checkout wins.
- [ ] 7.2 GREEN: `PhysicalPurchaseController.java` (`PENDING_PURCHASE_KIND`, `pay()` clears kind, skip assigned sessions), `templates/purchase-notice.html` (`breadcrumbHref`/`breadcrumbLabel`, default `/classes`). Commit: `feat(bff): retorno de pago con copy de suscripcion (#361)`.
- [ ] 7.3 Regression: run unchanged `PhysicalPurchaseIntegrationTest` + `PhysicalPurchaseControllerTest` (class return, kind absent); no edits to existing assertions.
- [ ] 8.1 Docs: `docs/06-BILLING-API.md` D1 deviation (session `paymentId`, no signed `state`; why); `docs/26-LOCAL-DEV-SETUP-HOWTO.md` paymentId lookup (DB/Bruno) for simulator webhook. Commit: `docs(bff): documentar retorno de suscripcion y desviacion D1 (#361)`.
- [ ] 8.2 VERIFY in API (read-only): a REJECTED payment releases the slot (SubscriptionStatus.CANCELLED, US-BILLING-010 escenario 6). Record finding in design.md Open Questions and PR2 body; if not released, add notice caveat task.
- [ ] 8.3 Run gate; open PR2 (base S1 branch, `Closes #361`); update project board fields.

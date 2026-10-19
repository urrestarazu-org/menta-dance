# Proposal: bff-plan-subscription-checkout

**Issue**: #361 · **ADRs**: ADR-0038, ADR-0039 · **Module**: `bff` only

## Intent

`/plans` lists plans but nobody can buy one from the web. Students need a
"Suscribirme" path that pays online (Mercado Pago or the local simulator)
and an honest return page driven by the Billing API, never by URL params.

## Scope

### In Scope
- "Suscribirme" per plan: STUDENT only; anonymous gets login-then-`/plans`; other roles get no button (D3).
- `/me` pre-check: ACTIVE shows notice with end date; PENDING shows notice with copyable full `subscriptionId`, no button (D2); EXPIRED shows button plus an "already expired" notice with the end date; none (404) shows button; any other `/me` failure is fail-closed (error view, no button).
- CSRF-protected POST creating the checkout (`MERCADO_PAGO`, render-time idempotency key) and redirect via `CheckoutUrlPolicy`.
- Error mapping: 409 already active, 409 conflict, 422, 400, 503.
- Return via session-held `paymentId` + `GET /payments/{id}` (D1), with a purchase-kind discriminator on the shared `/compras/retorno`.
- Doc of the D1 deviation in `docs/06-BILLING-API.md`.

### Out of Scope
Bank transfer (#362), subscription dashboard/cancel (#363), plan change while ACTIVE, API changes, Android, new simulator scenarios.

## Capabilities

### New Capabilities
- `bff-plan-subscription-checkout`: subscribe POST, error mapping, redirect, subscription return page, purchase-kind discrimination on the shared return route.

### Modified Capabilities
- `bff-plans-view`: role- and subscription-state-dependent button/notice; `/me` pre-check, EXPIRED notice, and fail-closed handling when `/me` cannot be verified.

## Approach

Mirror #365: new `BillingApiClient` methods (`createSubscriptionCheckout`, `getCurrentSubscription`) in the adapter's wire/handle/mapError style; use cases in `application`; controller reuses `PurchaseFlowErrors`, `PaymentProgress`. Session stores `{kind, paymentId}` so the return page picks subscription copy ("tu suscripción se está activando") and never class-assignment copy (ADR-0039: payment COMPLETED is not activation).

## Affected Areas

| Area | Impact |
|------|--------|
| `bff/.../port/out/BillingApiClient.java`, `adapter/BillingApiAdapter.java` | Modified |
| `bff/.../application/usecase/*Subscription*` | New |
| `bff/.../controller/PlansController.java`, new `SubscriptionController` | Modified/New |
| `bff/.../controller/PhysicalPurchaseController.java` (return route) | Modified |
| `bff/src/main/resources/templates/plans.html` | Modified |
| `docs/06-BILLING-API.md` | Modified |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Subscription return shows class copy / mistaken paymentId | Med | Kind discriminator + regression test on class return |
| `/plans` stays public but becomes personalized | Med | `/me` only with a token; `no-store` when personalized |
| Rejected/pending not reachable with simulator | High | WireMock coverage; demo covers approved only |
| Size near 800 lines | Med | Single PR; `sdd-tasks` splits only if forecast exceeds 800 |

## Rollback Plan

`git revert` of the squash commit. No schema, API, or config change; the class-purchase return route returns to its current behavior.

## Success Criteria

- [ ] A student subscribes end to end with the local simulator.
- [ ] Return status comes only from the API; URL params are ignored.
- [ ] ACTIVE/PENDING students get no second checkout.
- [ ] Class-purchase return behavior and tests unchanged.

## Proposal question round (resolved by the user, 2026-10-14)

1. D3 (confirmed): anonymous visitors see the button, log in, and land back on `/plans` (requires saved-request redirect support in login; verify in design).
2. `/me` failure is fail-closed (user decision): for an authenticated STUDENT, any `/me` outcome other than 200 or 404 `NO_SUBSCRIPTION` (e.g. 503, timeout) shows the standard error view and offers no way to continue. The student cannot subscribe until the status can be verified. 404 means "no subscription" and offers the button.
3. EXPIRED (user decision): the plans offer "Suscribirme" again (renewal = new checkout) AND a notice that the previous subscription has already expired, with its end date.

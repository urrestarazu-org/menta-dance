# Design: BFF Plan Subscription Checkout (#361)

## Technical Approach

Mirror the #365 class-purchase flow inside `bff` only. A new token-bearing port
`SubscriptionApiClient` (kept apart from `BillingApiClient`, exactly as
`PhysicalPurchaseApiClient` already is) wraps `GET /subscriptions/me` and
`POST /subscriptions`. `GetPlansViewUseCase` composes the cached, token-less
catalog with a per-request viewer offer. A new `SubscriptionController` posts the
checkout; the shared `/compras/retorno` gains a purchase-kind discriminator.

## Architecture Decisions

| Decision | Options / tradeoff | Choice |
|---|---|---|
| Port location | Add to `BillingApiClient` (proposal wording) breaks its documented "no token parameter" promise / new port | **New `SubscriptionApiClient`**; reuses `BillingApiClient.NotFoundException`/`ServiceUnavailableException` like `PhysicalPurchaseApiClient` (deviation from proposal wording, same intent) |
| Adapter plumbing | Extract shared helper from `PhysicalPurchaseApiAdapter` (touches #365 code) / duplicate `execute/handle/mapError` (~40 lines) | **Duplicate** in `SubscriptionApiAdapter`; refactor is out of scope |
| `/me` mapping | Flat record / sealed type | **Sealed `CurrentSubscription`** (`Active(endDate)`, `Pending(subscriptionId)`, `Expired(endDate)`, `None`); use case maps it with an exhaustive `switch` into a flat `PlansOffer` the template reads |
| Fail-closed | Catch and show softer notice / propagate | **Propagate** `ServiceUnavailableException` (`@ResponseStatus 503` → `error.html`), as `PlansController` already does |
| Session shape | One `Serializable` record / two `String` attributes | **Two strings**: existing `com.menta.bff.PENDING_PAYMENT_ID` unchanged + new `com.menta.bff.PENDING_PURCHASE_KIND` (`SUBSCRIPTION`; absent = class). Redis uses JDK serialization; no new class enters the session and pre-deploy sessions stay readable |
| Login return | `?next=` param (open-redirect risk) / global saved request / scoped saved request | **Scoped**: anonymous button links to authenticated `GET /plans/suscribirme` (→ `redirect:/plans`); `HttpSessionRequestCache` matcher limited to that path; `defaultSuccessUrl("/dashboard")` without `alwaysUse` |
| Error mapping | New `SubscriptionFlowErrors` / extend `PurchaseFlowErrors.apply` | **Extend** with subscription branches; reuse `notice()` and `purchase-notice` view |

### Login-return verification

`BffSecurityConfig` today has `.defaultSuccessUrl("/dashboard", true)`: the saved
request is **ignored**, and `/plans` is `permitAll`, so it is never saved. Change:
`.requestCache(c -> c.requestCache(cache))` where the cache matches only
`GET /plans/suscribirme` (`PathPatternRequestMatcher`, Spring Security 6.5), and
`alwaysUse=false`. Every other login still lands on `/dashboard`. No user-supplied
target exists, so there is no open redirect; Spring appends `?continue`, ignored.
`changeSessionId()` keeps the saved request across fixation protection.

## Data Flow

    Browser        PlansController / SubscriptionController     Billing API
    GET /plans ─→  getPlans() [shared cache, no token]       ─→ GET /plans
       (STUDENT) ─→ getCurrentSubscription(token)            ─→ GET /subscriptions/me
                   200→offer · 404 NO_SUBSCRIPTION→None · else→503 error.html
    POST /suscripciones {planId, idempotencyKey, _csrf}
                 ─→ createSubscriptionCheckout(token, ...)   ─→ POST /subscriptions
                   CheckoutUrlPolicy ok → session{PAYMENT_ID, KIND=SUBSCRIPTION}
                 ←─ 302 checkoutUrl (MP / simulator) ─→ back_url
    GET /compras/retorno ─→ getPaymentProgress(paymentId)    ─→ GET /payments/{id}
                   copy chosen by KIND; URL params ignored

## Interfaces / Contracts

```java
public interface SubscriptionApiClient {
    CurrentSubscription getCurrentSubscription(String accessToken);          // 404 NO_SUBSCRIPTION -> None
    SubscriptionCheckout createSubscriptionCheckout(String accessToken, String planId, String idempotencyKey);
    // SubscriptionAlreadyActiveException(Instant currentEndDate /*nullable*/) 409 SUBSCRIPTION_ALREADY_ACTIVE
    // SubscriptionConflictException 409 other · PlanNotAvailableException 422 · InvalidRequestException 400
    // 404/429/5xx/timeout/unknown/incomplete body -> BillingApiClient.ServiceUnavailableException
}
public enum PlansViewer { ANONYMOUS, STUDENT, OTHER }
public record PlansOffer(SubscribeAction action, OfferNotice notice, String endDateLabel,
                         String subscriptionId, Map<String, String> idempotencyKeyByPlanId) {}
```

`GetPlansViewUseCase.execute(PlansViewer, String accessToken)` returns
`PlansView(List<PlanSummary>, PlansOffer)`; `/me` is called only for `STUDENT`.
Keys come from the injected `Supplier<String>` (pattern of `QuotePhysicalPurchaseUseCaseImpl`),
one per plan per render. Dates use `ClassesProperties.displayZone`, `dd/MM/yyyy`.
`/me` 404 with a code other than `NO_SUBSCRIPTION` is fail-closed.

## Controllers and Security

- `PlansController.plans(Authentication, HttpServletRequest, HttpServletResponse, Model)`:
  `STUDENT` = authority `ROLE_STUDENT` **and** token attribute present (missing
  token → fail-closed); other authenticated → `OTHER`; null → `ANONYMOUS`.
  Sets `Cache-Control: no-store` for any non-anonymous viewer. The adapter cache
  keeps only `List<PlanSummary>`; `getPlans()` signature unchanged.
- `SubscriptionController`: `POST /suscripciones` (`@Valid SubscriptionForm`, Lombok
  `@Data`, `@NotBlank planId`, `idempotencyKey` 1–128), `MERCADO_PAGO` fixed in the
  adapter; `GET /plans/suscribirme` → `redirect:/plans`.
- `BffSecurityConfig`: `POST /suscripciones` → `hasRole("STUDENT")`; everything
  else new stays under `anyRequest().authenticated()`; only `GET /plans` is public.
- `PhysicalPurchaseController`: `pay()` removes the kind attribute; `checkoutReturn`
  reads it; fallback/no-session target `/plans` for subscriptions, `/classes` otherwise;
  `COMPLETED` for a subscription skips `getAssignedSessionsUseCase`.

## Copy (Spanish, voseo)

| State | Text |
|---|---|
| ACTIVE | "Ya tenés una suscripción activa hasta el {fecha}." (no button) |
| PENDING | "Tenés una suscripción con el pago pendiente. Si necesitás ayuda, compartí este código: {subscriptionId}" (`<code>` + select-all, no button) |
| EXPIRED | "Tu suscripción anterior venció el {fecha}." + "Suscribirme" |
| 409 active | "Ya tenés una suscripción vigente." (+ " hasta el {fecha}" when present) |
| 409 conflict | "La suscripción cambió mientras la procesábamos. Volvé a Planes e intentá de nuevo." |
| 422 | "Este plan no está disponible para suscribirse en este momento." |
| Return COMPLETED | "¡Pago confirmado! Tu suscripción se está activando." |
| Return PENDING | "Estamos confirmando tu pago, actualizá en unos minutos." (link "Actualizar") |
| Return REJECTED / CANCELLED / EXPIRED | "El pago fue rechazado. No se te cobró nada." / "El pago fue cancelado." / "El pago venció antes de completarse." (link "Ver planes") |

400/503 reuse `AuthFlowErrors`. `purchase-notice.html` breadcrumb becomes
`${breadcrumbHref}`/`${breadcrumbLabel}` defaulting to `/classes`.

## File Changes

| File (`bff/src/main/...`) | Action |
|---|---|
| `application/port/out/SubscriptionApiClient.java` | Create |
| `application/dto/{CurrentSubscription,SubscriptionCheckout,PlansViewer,PlansOffer,PlansView}.java` | Create |
| `application/usecase/{SubscribeToPlanUseCase,SubscribeToPlanUseCaseImpl}.java` | Create |
| `application/usecase/{GetPlansViewUseCase,GetPlansViewUseCaseImpl}.java` | Modify |
| `infrastructure/adapter/SubscriptionApiAdapter.java` | Create |
| `infrastructure/config/{UseCaseConfig,BffSecurityConfig}.java` | Modify |
| `infrastructure/web/controller/{PlansController,PhysicalPurchaseController,PurchaseFlowErrors}.java` | Modify |
| `infrastructure/web/controller/SubscriptionController.java`, `web/dto/SubscriptionForm.java` | Create |
| `resources/templates/{plans,purchase-notice}.html` | Modify |
| `docs/06-BILLING-API.md`, `docs/26-LOCAL-DEV-SETUP-HOWTO.md` | Modify |

## Testing Strategy (Strict TDD, RED first)

| Layer | What | Approach |
|---|---|---|
| Unit | `GetPlansViewUseCaseImpl` per viewer × `/me` outcome; `SubscribeToPlanUseCaseImpl` | Mockito |
| Adapter | `/me` 200 ×3, 404 `NO_SUBSCRIPTION`, 404 other, 503, timeout; POST 201, incomplete body, 409×2, 422, 400, 503; token never logged | WireMock (`PhysicalPurchaseApiAdapterTest` pattern) |
| Controller | `no-store`, viewer detection, redirect policy, session writes, error statuses | `PlansControllerTest`, new `SubscriptionControllerTest` |
| Integration | Anonymous→`/plans/suscribirme`→login→`/plans`; direct login→`/dashboard`; CSRF-less POST 403; INSTRUCTOR POST 403; subscription return copy; **class return unchanged** (kind absent) | `BaseIntegrationTest` + Redis session assertions (`PhysicalPurchaseIntegrationTest`) |

Simulator covers only approved (and inconsistent → pending); rejected/cancelled/expired are WireMock-only.

## Threat Matrix

N/A — no shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary (HTTP routes are covered by Security tests above).

## Migration / Rollout

No migration. Docs: D1 deviation (session `paymentId` instead of signed `state`) and the
subscription return in `06-BILLING-API.md`; simulator demo for subscriptions in
`26-LOCAL-DEV-SETUP-HOWTO.md`. No Bruno/OpenAPI impact: BFF HTML routes are not in either.

## Size Forecast

Production + templates ≈ 700, docs ≈ 60, tests ≈ 800: **≈ 1,550 lines > 800**. Suggested
slices: (1) port, adapter, `/plans` offer, login return; (2) POST, return discriminator, docs.

## Open Questions

- [x] After a REJECTED payment, does the subscription leave PENDING? **Yes (verified read-only, #361 PR2).**
  `PaymentVerificationService.applyOutcome` calls `PaymentFulfillmentService.release` for any terminal
  non-settled payment (REJECTED/CANCELLED/EXPIRED); `release` saves `Subscription.cancelled()`
  (`SubscriptionStatus.CANCELLED`, `occupiesUserSlot() == false`) for virtual targets, and
  `PaymentExpiryWorker` does the same on expiry. `GET /subscriptions/me` resolves only
  PENDING/ACTIVE/EXPIRED, so a CANCELLED row reads as `NO_SUBSCRIPTION` and `/plans` offers
  "Suscribirme" again. Tests: `PaymentVerificationServiceTest.a_rejected_payment_cancels_the_pending_subscription`,
  `PaymentFulfillmentServiceTest` (release cancels, already-cancelled writes nothing). No copy caveat
  needed; the return copy for REJECTED stays "No se te cobró nada" with a link to `/plans`.
- [x] Local demo needs the `paymentId` for the approved webhook; the BFF never shows it. Document the lookup (DB/Bruno) in `26-…`.

## Deviations recorded during PR2 apply

- No-session / 404 fallback: `/compras/retorno` keeps redirecting to `/classes` when the session holds no
  kind (existing class tests `returnWithoutPayment` / 404 must stay unchanged); the subscription fallback
  notice (link `/plans`) renders only when the stored kind is `SUBSCRIPTION`. A session with neither
  attribute is indistinguishable from a class buyer.
- `SubscriptionController` takes `ClassesProperties` to format the 409 end date in the display zone;
  `PurchaseFlowErrors.apply` gained an overload carrying the formatted date.
- `pay()` clearing the kind landed in the 6.2 commit (needed for correctness of that work unit); its
  test is in `SubscriptionReturnControllerTest`.

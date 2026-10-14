# Design: billing-purchase-fulfillment-status (#559)

## Technical Approach

Extend the owner-checked student read `GET /api/v1/billing/payments/{paymentId}` with a nullable `fulfillment` (Purchase status enum only). The BFF reads it through the same single call and branches the class return page on it. No new endpoint, no schema change, two slices (API, then BFF).

## Architecture Decisions

| Decision | Choice | Rejected | Rationale |
|---|---|---|---|
| Lookup location | `GetPaymentUseCaseImpl` (application) gains `PurchaseRepository`; calls `findByPaymentId` **after** the `payment.userId` check | New use case / endpoint | One round trip; ownership stays on `Payment` (Purchase has no userId); spec forbids reading Purchase before ownership |
| Transaction | Unchanged bean: `new TransactionalGetPaymentUseCase(new GetPaymentUseCaseImpl(paymentRepository, purchaseRepository))` in `BillingConfiguration:330`; `REQUIRED, readOnly` satisfies adapter's `MANDATORY, readOnly` | Change adapter propagation | Decorator already exists; MANDATORY is the deliberate #245 guard |
| Payment kind filter | Always look up; subscription payments simply have no row → `null` | Branch on payment purpose | Unique-key lookup is cheap; avoids coupling to purpose |
| Application DTO | `PaymentStatusResult` gains `FulfillmentStatus fulfillment` (nullable, domain enum) | String | Type safety; application→domain is allowed |
| Wire DTO | `PaymentStatusResponse` gains `String fulfillment` = `name()` or `null` (always serialized, no `NON_NULL`) | Expose `Purchase` / reason | Spec: enum only; never `reason`, resolution type or note |
| Degradation (API) | Purchase read failure propagates (same DB/tx as payment) | Swallow to `null` | A silent `null` would hide real failures behind optimistic copy |
| BFF model | New record `PaymentStatusView(PaymentProgress progress, PurchaseFulfillment fulfillment)`; port/use case keep their names, return type changes once | Second port method / second HTTP call; add field to the enum | One call; enum `PaymentProgress` stays a pure payment state |
| BFF unknown values | `PurchaseFulfillment.fromUpstream` returns `null` for null/absent/unknown | `UNKNOWN` constant | Spec: "treat any value it does not recognize as `null`"; Java 21 `case null` in switch |
| Lombok | `@RequiredArgsConstructor` on `GetPaymentUseCaseImpl` (constructor is rewritten anyway); records elsewhere | Hand-written constructor | CLAUDE.md rule for new code |

ArchUnit: no new package; billing touches only billing types (`FulfillmentStatus`, `PurchaseRepository`), never `com.menta.physical`.

## Data Flow

    BFF checkoutReturn ──GET /billing/payments/{id}──→ PaymentController
         │                                               │
         │                         TransactionalGetPaymentUseCase (REQUIRED, ro)
         │                                               │
         │                  GetPaymentUseCaseImpl: findById → owner check (404)
         │                                   → PurchaseRepository.findByPaymentId (MANDATORY)
         │←── {status, createdAt, updatedAt, fulfillment|null} ──┘
    PaymentWire(status, fulfillment) → PaymentStatusView → classNotice

BFF `classNotice` (non-COMPLETED branches unchanged, ignore `fulfillment`):

| COMPLETED + fulfillment | `GetAssignedSessionsUseCase` | Copy |
|---|---|---|
| `ASSIGNED`, `PENDING_FULFILLMENT`, `null`/unknown | called | today's #552 logic (unchanged) |
| `EXCEPTION` | **not** called | "Recibimos tu pago, pero no pudimos asignarte los lugares. El equipo lo está revisando y te va a contactar." |
| `RESOLVED` | **not** called | "Tu compra ya fue resuelta por el equipo." |

No contact link; link stays "Volver a clases". `purchase-notice.html` needs no change (session list renders only when `assignedSessions` is set). `subscriptionNotice` receives `view.progress()`; its behavior is untouched. `isFinal()`/session clearing is unchanged.

## File Changes

| File | Action |
|---|---|
| `api/billing/.../application/usecase/GetPaymentUseCaseImpl.java` | Modify: purchase lookup, Lombok ctor |
| `api/billing/.../application/dto/PaymentStatusResult.java` | Modify: nullable `fulfillment` |
| `api/billing/.../infrastructure/web/dto/PaymentStatusResponse.java` | Modify: `String fulfillment` |
| `api/billing/.../infrastructure/config/BillingConfiguration.java` | Modify: inject `PurchaseRepository` |
| `api/openapi/billing-v1.yaml` | Modify: new path `get`, `PaymentStatusResponse` schema (`fulfillment: type: [string, "null"]`, enum + null), 200/404 |
| `docs/06-BILLING-API.md` | Modify: field + null rules |
| `bff/.../application/dto/PurchaseFulfillment.java`, `PaymentStatusView.java` | Create |
| `bff/.../port/out/PhysicalPurchaseApiClient.java`, `usecase/GetPaymentProgressUseCase(Impl).java` | Modify: return `PaymentStatusView` |
| `bff/.../infrastructure/adapter/PhysicalPurchaseApiAdapter.java` | Modify: `PaymentWire(status, fulfillment)` |
| `bff/.../web/controller/PhysicalPurchaseController.java` | Modify: branching |

## Testing Strategy (Strict TDD, RED first)

| Layer | Test |
|---|---|
| Unit | `GetPaymentUseCaseImplTest`: four statuses, no row → null, non-owner → 404 with `verifyNoInteractions(purchaseRepository)` |
| Controller (API) | `PaymentControllerTest`: JSON has `fulfillment`, `null` serialized, no `reason`/`resolution*` keys |
| Contract | New `PaymentStatusContractTest` (billing tests): loads `../openapi/billing-v1.yaml` via SnakeYAML; asserts path/200/404, schema properties == `PaymentStatusResponse` record components, enum == `FulfillmentStatus.values()` + null, no reason/resolution/note |
| Wiring | `BillingConfigurationTest`: bean `isInstanceOf(TransactionalGetPaymentUseCase)` |
| Integration (tx) | `PresentialPurchaseExceptionPathIntegrationTest`: call the real `GetPaymentUseCase` bean → `null` after webhook (no row yet), `EXCEPTION` after outbox; proves MANDATORY is satisfied |
| BFF adapter | `PhysicalPurchaseApiAdapterTest` (existing HTTP stub): maps fulfillment, absent/unknown → null |
| BFF controller | `PhysicalPurchaseControllerTest`: EXCEPTION/RESOLVED copy, `verifyNoInteractions(getAssignedSessionsUseCase)`, no contact link; ASSIGNED/PENDING/null call sessions; non-COMPLETED ignores fulfillment |
| Ripple | `SubscriptionReturnControllerTest`, `QuotePhysicalPurchaseUseCaseImplTest`: stubs wrap in `new PaymentStatusView(progress, null)`; assertions unchanged |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary.

## Migration / Rollout

No migration. Slice 1 (API + OpenAPI + docs + tests) ~340 lines; slice 2 (BFF) ~230 lines; total ~570 of 800. The BFF ignores the extra field until slice 2 ships, so each slice reverts independently.

## Open Questions

- [x] Notice titles for EXCEPTION/RESOLVED (approved 2026-10-14): EXCEPTION title "Pago recibido", RESOLVED title "Compra resuelta"; both shown as an info alert (not error), with the approved copy bodies.

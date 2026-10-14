# Proposal: billing-purchase-fulfillment-status

**Issue**: #559 · **Modules**: `api:billing`, `bff` · **Builds on**: #552

## Intent

A paid class purchase that ends in EXCEPTION still reads "¡Pago confirmado! Estamos asignando tus lugares." forever. The student cannot tell that staff must act. The return page must reflect the Purchase outcome, not only the payment.

## Scope

### In Scope
- Nullable `fulfillment` (`PENDING_FULFILLMENT|ASSIGNED|EXCEPTION|RESOLVED`) on the owner-checked `GET /api/v1/billing/payments/{paymentId}`; status enum only.
- `null` for non-purchase payments and for COMPLETED payments without a Purchase row yet.
- Document the student endpoint (path, schema, 404) in `api/openapi/billing-v1.yaml` for the first time; update `docs/06-BILLING-API.md`.
- BFF class return copy: EXCEPTION "Recibimos tu pago, pero no pudimos asignarte los lugares. El equipo lo está revisando y te va a contactar."; RESOLVED "Tu compra ya fue resuelta por el equipo."; ASSIGNED keeps listing sessions (#552); null/unknown renders as today.

### Out of Scope
`reason`, resolution type or note; refund/compensation wording; a new endpoint; subscription return page; Android; notifications.

## Capabilities

### New Capabilities
- `billing-purchase-fulfillment-status`: `fulfillment` field on the student payment status, its null rules, and the OpenAPI contract.
- `bff-class-purchase-return`: class return page rendering per payment status and fulfillment.

### Modified Capabilities
None.

## Approach

`GetPaymentUseCaseImpl` keeps its `payment.userId` ownership check and adds `PurchaseRepository.findByPaymentId` (MANDATORY tx, satisfied by `TransactionalGetPaymentUseCase`). The BFF `PaymentWire` gains `fulfillment`; `GetPaymentProgressUseCase` returns status plus fulfillment; `classNotice` branches on both. Lombok in new code; billing never imports physical; no compat shims (V0).

## Affected Areas

| Area | Impact |
|------|--------|
| `api/billing/.../usecase/GetPaymentUseCaseImpl.java`, `PaymentStatusResponse` | Modified |
| `api/billing/.../config/BillingConfiguration.java` | Modified |
| `api/openapi/billing-v1.yaml`, `docs/06-BILLING-API.md` | Modified |
| `bff/.../adapter/PhysicalPurchaseApiAdapter.java`, `dto/PaymentProgress` | Modified |
| `bff/.../controller/PhysicalPurchaseController.java` | Modified |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Endpoint absent from OpenAPI; contract drift | Med | Add as new; contract test |
| Outbox is async: COMPLETED with no Purchase | High | `null` maps to today's copy |
| MANDATORY repo called outside the Transactional decorator | Med | Wiring test via the decorator |
| Port return-type change ripples through BFF tests | Med | Change port once in slice 2 |

## Rollback Plan

Revert slice 2 first: the BFF ignores the extra field. Then revert slice 1. No schema or migration.

## Success Criteria

- [ ] EXCEPTION and RESOLVED show their copy; ASSIGNED keeps sessions.
- [ ] Subscriptions and no-row payments return `fulfillment: null`.
- [ ] No `reason` in any response; ArchUnit passes.

## Slices

1. API + OpenAPI + docs. 2. BFF. Estimate ~550–680 lines (800 budget).

## Decisions (resolved by the user, 2026-10-14)

- (a) `PENDING_FULFILLMENT` is exposed as its own value; the BFF maps it to "Estamos asignando tus lugares".
- (b) No contact/support link on EXCEPTION/RESOLVED.
- (c) `GetAssignedSessionsUseCase` is NOT called on EXCEPTION/RESOLVED; it is called only for ASSIGNED, PENDING_FULFILLMENT or null (`sessions/me` returns all of the student's sessions, not only this purchase's).

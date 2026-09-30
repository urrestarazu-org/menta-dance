# Exploration: Bank-transfer physical purchase and capacity exception

**Change**: `billing-physical-transfer-capacity-exception`
**Issue**: [#36](https://github.com/urrestarazu-org/menta-dance/issues/36) — US-BILLING-008, "Transferencia presencial y excepción de capacidad"
**Date**: 2026-09-30
**Phase**: explore (read-only investigation; no implementation)

## Current state

### The `Purchase` state machine already exists

`PENDING_FULFILLMENT` / `ASSIGNED` / `EXCEPTION` are not new — built by #41
(`physical-purchase-capacity`, archived) and #115
(`fix-presential-purchase-quota-exception`). `Purchase.java` and
`FulfillmentStatus.java` (`api/billing/src/main/java/com/menta/billing/domain/model/`)
already model exactly what scenarios 1 and 2 of #36 describe. #36 does not
introduce these states.

### Fulfillment routing is already payment-method-agnostic

`PaymentFulfillmentService.ensure(Payment)`
(`api/billing/.../application/usecase/PaymentFulfillmentService.java:44-49`)
switches on `PaymentTarget`, never on `PaymentMethod`:

```java
public void ensure(Payment payment) {
    switch (payment.getTarget()) {
        case PaymentTarget.Physical ignored -> publishPhysicalPaymentCompletedUseCase.handle(payment);
        case PaymentTarget.Virtual virtual -> ensureSubscription(payment, virtual);
    }
}
```

`ResolvePaymentProofUseCaseImpl.resolve()` (#31/#33) already calls
`paymentFulfillmentService.ensure(resolved)` on approval and `.release(resolved)`
on rejection. For a `PaymentTarget.Physical` payment, `ensure` already reaches
`publishPhysicalPaymentCompletedUseCase.handle(payment)` → the
`billing.PhysicalPaymentCompleted` outbox event →
`PhysicalCapacityAssignmentOutboxEventHandler`
(`api/app/.../outbox/PhysicalCapacityAssignmentOutboxEventHandler.java`), whose
`HoldNotFound` fallback branch (lines 228-289) already computes eligible
sessions fresh from `confirmedAt` via `CoveragePlanner` and calls
`AssignCapacityUseCase` / `MarkPurchaseAssignedUseCase` /
`MarkPurchaseExceptionUseCase`. All of it already implemented and tested.

**Verified**: `release(Payment)` (lines 52-58) only handles
`PaymentTarget.Virtual` — for `Physical` it is a no-op today.

### Proof upload and admin approve/reject are already generic — confirmed against source

- `SubmitPaymentProofUseCaseImpl` / `PaymentController`
  (`POST /api/v1/billing/payments/{paymentId}/proof`,
  `GET /api/v1/billing/payments/{paymentId}`) operate only on `PaymentId` /
  owning-user / status; no `PaymentTarget` branch.
- `PaymentAdminController` (`@RequestMapping("/api/v1/admin/billing/payments")`,
  verified directly) exposes `GET` (list), `GET /{paymentId}` (detail),
  `POST /{paymentId}/approve`, `POST /{paymentId}/reject`,
  `POST /{paymentId}/corrections` — **not** the issue's literal
  `/api/v1/billing/admin/payments/...`. #33's own **D3** already diverged from
  that identical wrong prefix for the same route-consistency reason; #36
  inherits the same route, not the issue's literal wording.
- Both delegate straight into `ResolvePaymentProofUseCaseImpl`, itself fully
  generic over `PaymentTarget` (confirmed above).

**Conclusion**: none of the three named endpoints in the issue's "Notas
técnicas" need new code. The only missing piece is upstream: nothing can
create a `Payment` for a bank-transfer physical purchase today.

### Real gap: purchase creation hard-rejects `BANK_TRANSFER`

`CreatePhysicalPurchaseCheckoutUseCaseImpl.create()` — verified directly:

```java
if (command.paymentMethod() != PaymentMethod.MERCADO_PAGO) {
    // owns MERCADO_PAGO only; bank transfer is a separate, unbuilt flow here.
    throw new IllegalArgumentException("Checkout Pro requires MERCADO_PAGO");
}
```

#31's own archived proposal names this explicitly as future scope: *"#36 —
presential/in-person transfer... this change deliberately leaves that one in
place."* The sibling to mirror is `CreateBankTransferSubscriptionUseCaseImpl`,
which creates `Payment.awaitingManualVerification(...)` directly for
`PaymentTarget.Virtual` — no provider call, no hold.

### The hold-TTL conflict — verified against #208's locked D6

Issue #36's scenario 2 says **"el hold expiró"**; its DoD names **"pruebas de
hold vencido."** No hold mechanism exists today for bank-transfer physical
purchases, and #208's archived proposal
(`openspec/changes/archive/2026-09-16-physical-capacity-hold/proposal.md`)
locks this directly, verified verbatim:

> **D6 — TTL is bounded to instant payment, not async cash/transfer**
> The hold's lifetime is short — on the order of 30-60 minutes, sized to cover
> Checkout Pro's card flow... Rejected: a multi-hour or multi-day window sized
> for cash/bank-transfer settlement. That trades a real, ongoing cost... for a
> payment method this change does not build. Async cash/transfer capacity
> handling is explicitly deferred.

This is a direct conflict between the issue's literal wording and an already
locked, already shipped architectural decision. It is the single biggest fork
`sdd-propose` must resolve explicitly, not paper over.

### `PhysicalCourseQuote` friction

Valid for exactly 1 hour from creation, never reserves capacity
(`PhysicalCourseQuote.java:12-26`). A manual bank-transfer verification cycle
can take days — a real friction point if the purchase-creation flow tries to
reuse the quote's validity window as its own commitment lifetime, the same way
`CreateBankTransferSubscriptionUseCaseImpl` already decouples from any
Mercado Pago preference lifetime for the virtual case.

### Rate limiting

`BankTransferRateLimitPort.consumeSubscriptionCreation` /
`consumeProofUpload` exist but are subscription-creation-named, keyed
generically on `userId`/`paymentId`. A physical purchase creation path needs
an equivalent call — reusing the existing budget vs. a new counter is open.

## Scope note — explicitly excluded

Two board issues are follow-ups of #209 (which introduced `EXCEPTION` for
physical purchases) and are **not** part of #36:

- **#236** — extend `EXCEPTION` notification to virtual subscriptions
  (separate, synchronous, no outbox, single cause).
- **#237** — admin list/view of purchases in `EXCEPTION` (no such `GET`
  endpoint exists in billing today).

## Approaches

### 1. No-hold, approval-time capacity computation (recommended)

New `CreateBankTransferPhysicalPurchaseUseCase*` mirrors
`CreateBankTransferSubscriptionUseCaseImpl`: creates
`Payment.awaitingManualVerification(..., PaymentTarget.Physical(quoteId), ...)`,
no hold call. Reuses proof upload, admin approve/reject, and the outbox
handler's existing `HoldNotFound` branch completely unchanged.

- **Pros**: maximum reuse of already-tested code; consistent with locked D6;
  smallest, most reviewable diff; matches the issue's own "capacidad...
  calculado desde la confirmación" wording almost verbatim.
- **Cons**: requires an explicit, documented divergence from the issue's
  literal "hold" wording (same class of divergence as prior D3/D6/D8); the
  capacity window between proof upload and admin approval is unbounded —
  mitigated by `EXCEPTION` being an explicitly accepted, never-auto-resolved
  terminal state per the issue's own NFR.
- **Effort**: Low-Medium.

### 2. New long-TTL hold for bank-transfer physical purchases

Extend `PhysicalCapacityHoldPort`/hold use cases with a bank-transfer-specific
TTL (e.g. matching the 72h expiry sweep), created at purchase creation,
released on reject/expiry, converted on approve like the Mercado Pago flow.

- **Pros**: matches the issue's literal wording and DoD exactly; guarantees a
  seat during verification.
- **Cons**: directly reopens the locked D6 decision; holds a seat for days
  against a possibly-abandoned/rejected transfer, a real cost to other
  buyers; new migration, TTL config, and a second concurrency-critical
  hold-creation trigger.
- **Effort**: High.

### 3. Hybrid — hold only once proof is submitted

Same mechanism as #2, triggered by proof upload rather than payment creation,
bounding the reservation cost to buyers who showed real intent.

- **Pros**: less exposure than #2; reuses the same hold/conversion code.
- **Cons**: still partially reopens D6; adds a second, distinct hold-creation
  trigger; a payment that is never proofed gets no protection either way.
- **Effort**: Medium-High.

## Recommendation

Approach 1. It satisfies all three BDD scenarios almost entirely through
already-built, already-tested, target-agnostic code
(`PaymentFulfillmentService`, `ResolvePaymentProofUseCaseImpl`,
`SubmitPaymentProofUseCaseImpl`, the outbox handler's `HoldNotFound` branch,
`MarkPurchaseAssignedUseCase`, `MarkPurchaseExceptionUseCase`). Net-new
surface is essentially one creation use case plus its DTO/port/controller/
OpenAPI wiring — consistent with this project's established pattern of
mirroring a sibling flow and explicitly documenting divergence from an
issue's literal wording when it conflicts with a locked precedent
(D3/D6/D8 across #31/#33/#208).

## Open questions for `sdd-propose`

1. **Hold vs. no-hold** — Approach 1 vs. 2 vs. 3; must be resolved with the
   user, not silently assumed, given the direct conflict with #208's D6 and
   the issue's own literal wording/DoD.
2. **Quote-validity vs. payment-lifetime decoupling** — does the new creation
   use case need its own commitment window independent of the quote's fixed
   1-hour validity?
3. **Shape and name of the new creation endpoint**, and whether it shares
   `BankTransferRateLimitPort`'s existing budget or needs its own counter.
4. **`PaymentFulfillmentService.release()` for `Physical`** — confirm it stays
   a no-op under Approach 1 (nothing to release) rather than leaving that
   implicit.

## Key Learnings

1. `PaymentFulfillmentService.ensure()` routes on `PaymentTarget`, not
   `PaymentMethod`, so physical purchase fulfillment already works for any
   payment rail once a `Payment` reaches `COMPLETED`.
2. The admin approve/reject endpoints at
   `/api/v1/admin/billing/payments/{paymentId}/approve` already existed from
   issue #33 and need zero new code for issue #36's approval flow.
3. Issue #208's locked decision D6 explicitly rejected a multi-day capacity
   hold for async payment methods, directly conflicting with issue #36's
   literal "hold expired" wording.
4. `SubmitPaymentProofUseCaseImpl` and `PaymentController` are fully
   target-agnostic, so the existing proof-upload endpoint needs no changes
   for physical purchases.
5. `PhysicalCourseQuote` objects are valid for exactly one hour and never
   reserve capacity, creating friction against a multi-day bank-transfer
   verification window.

## Ready for proposal

Yes. No blocking unknowns in the domain/reuse story — the hardest parts
(fulfillment routing, admin resolution, proof upload) are already correct and
complete. `sdd-propose` must explicitly resolve the four open questions above.

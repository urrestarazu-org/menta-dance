# Exploration: Admin view for purchases in EXCEPTION

**Change**: `billing-exception-admin-view`
**Issue**: [#237](https://github.com/urrestarazu-org/menta-dance/issues/237) — "Vista administrativa de compras en EXCEPTION" (follow-up de #209)
**Date**: 2026-09-30
**Phase**: explore (read-only investigation; no implementation)

## Current state

### What #209 already shipped (notification only, physical-purchase-scoped)

`MarkPurchaseExceptionUseCase` (`api/billing/.../application/usecase/MarkPurchaseExceptionUseCase.java:53-118`)
guards `Purchase` → `FulfillmentStatus.EXCEPTION`: `PENDING_FULFILLMENT`→`EXCEPTION` accepted and
appends `BillingOutboxEventTypes.PURCHASE_EXCEPTIONED` in the same transaction; `EXCEPTION`→`EXCEPTION`
is an idempotent no-op; `ASSIGNED`→`EXCEPTION` is refused (ADR-0028 — no path back once `ASSIGNED`).

That outbox event feeds `PurchaseExceptionNotificationOutboxEventHandler` →
`PurchaseExceptionNotificationPort.notify(...)` (confirmed: `notify(PurchaseExceptionNotification)`,
javadoc requires send failures propagate unchanged so the outbox worker retries) →
`SpringMailPurchaseExceptionNotificationAdapter`. It notifies **two** recipients: the buyer
(best-effort) and a fixed ops address. **This is Purchase-only.**

**`Subscription.exception()` is already reached in production today** — confirmed directly:
`PaymentFulfillmentService.ensureSubscription` (`:85-89`) calls `existing.get().exception()` when
the referenced Plan was deleted at settlement time. That path appends **no outbox event and
triggers no notification at all** — a strictly worse, currently-silent gap than the physical one
#209 fixed, correctly split into the separate #236.

### `Purchase` domain/persistence shape — confirmed minimal

`Purchase.java`: `id`, `paymentId`, `physicalSessionIds`, `status` only. `billing_purchases`
(`V8__billing_payments.sql:25-34`, confirmed verbatim):
```sql
CREATE TABLE billing_purchases (
    id BINARY(16) NOT NULL,
    payment_id BINARY(16) NOT NULL,
    physical_session_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_billing_purchases_payment_id (payment_id),
    CONSTRAINT fk_billing_purchases_payment FOREIGN KEY (payment_id) REFERENCES billing_payments (id)
);
```
No `created_at`, no reason column. Everything admin-useful (buyer `user_id`, `created_at`,
`target_modality`, `target_reference`/quote id, amount/currency) lives on the joined `Payment` /
`billing_payments` row via the FK-enforced 1:1 `payment_id`. An INNER join is safe — every
persisted `Purchase` has exactly one `Payment`.

### Confirmed: no existing GET/list endpoint over `billing_purchases` anywhere

`PurchaseRepository` (`application/port/out/PurchaseRepository.java`) exposes only `save`,
`saveIsolated`, `findByPaymentId`, `findByPaymentIdForUpdate` — read in full, confirmed no list
method exists. No `@RestController` in the repo exposes a purchases-by-status list today.

### Precedent to mirror — #33's pending-verification list

`PaymentAdminController.list()` (`GET /api/v1/admin/billing/payments`) →
`PaymentRepository.findAwaitingManualVerification(page, size)` → `PendingVerificationPage`/`Item`.
Backing query is a JPQL projection-constructor query with a separate `countQuery`,
`Page<Row>`/`Pageable`, `MAX_PAGE_SIZE=50` explicitly rejected (not clamped) with a `400`, default
page size 20. This is the exact shape to mirror, joining `PurchaseJpaEntity` + `PaymentJpaEntity`.

### Role gating — confirmed ADMIN-only, no RECEPTIONIST on billing routes

`SecurityConfig.roleAuthorizationManager()` registers `/api/v1/admin/**` → `ADMIN` only (and
`/api/v1/instructor/**` → `INSTRUCTOR`, unrelated). No `RECEPTIONIST` role exists on any billing
admin route — the #45 manual-check-in precedent does not carry over here. The new endpoint should
be `ADMIN`-only with the same local `requireAdmin` defense-in-depth check `PaymentAdminController`
already uses.

### Resolution action confirmed absent — this is pure observability

`MarkPurchaseAssignedUseCase` is called only from the automated
`PhysicalCapacityAssignmentOutboxEventHandler` — grepped every referencing file, zero admin
controllers call it. **No in-app way exists today to move a `Purchase` out of `EXCEPTION`** — the
only fix today is an out-of-band DB edit or manually reprocessing the outbox event. #237 is
therefore a pure read model: list what's stuck, nothing more, at least in this issue's scope.

## The real scope question

`FulfillmentStatus` is shared between `Purchase` (physical) and `Subscription` (virtual), and
`Subscription.exception()` is confirmed reachable in production. But the two aggregates have
genuinely incompatible shapes for a unified list row (sessions vs. plan/course snapshot) — forcing
one would need a type discriminator and nullable per-type fields, the same unrepresentable-merge
problem this codebase has already rejected elsewhere for `FulfillmentStatus`. #209 itself
deliberately split "does this apply to virtual too?" into the separate #236, which remains open.

## Approaches

1. **Physical-only v1, mirror #33 exactly** — new port method + JPQL join + DTOs + new admin
   controller, scoped strictly to `Purchase`.
   - Pros: smallest scope; reuses an already-reviewed pattern almost verbatim; preserves the
     deliberate #237/#236 split; single PR, well under the 400-line budget.
   - Cons: `Subscription` `EXCEPTION` (proven reachable, currently silent) stays invisible longer.
   - Effort: Low.
2. **Unified list across `Purchase` + `Subscription`** in this same issue.
   - Pros: one admin view for the whole shared concept.
   - Cons: incompatible row shapes force an unrepresentable-merge discriminator type; re-opens the
     scope split #209 deliberately made; #236 isn't even designed yet.
   - Effort: Medium.
3. **Physical-only v1 (identical code to option 1), explicitly documenting the Subscription-EXCEPTION
   silence as evidence feeding #236's priority**, rather than silently deferring it.
   - Same code as option 1; differs only in what the proposal records.
   - Effort: Low.

## Recommendation

Approach 3. Scope v1 to physical `Purchase` only — smallest correct slice, mirrors an
already-reviewed pattern almost line-for-line, preserves the deliberate #237/#236 split. Record, as
evidence for #236, that `Subscription.exception()` is already reached in production today with
**zero** observability (no notification, no admin view) — worse than the physical gap #237 closes.

## Size assessment

Much smaller than #36 — every downstream primitive already exists; this is a pure read-model
addition (one port method, one JPQL query, DTOs, one controller), no state-machine change, no new
outbox event, no cross-module port. Estimated ~150-250 changed lines including tests, well under
the 400-line review budget. **Single PR, no chained/stacked delivery needed.**

## Risks

- `billing_purchases` has no timestamp of its own — ordering must come from the joined
  `Payment.createdAt`; the INNER join is safe given the FK-enforced 1:1 relationship.
- No resolution action exists yet — reviewers may expect a bundled "reassign capacity"/"cancel"
  action; must explicitly scope that out to avoid #36-sized creep.
- A future unified `Purchase`+`Subscription` list (approach 2) may need to extend or replace this
  query/DTOs later — acceptable given the deliberate #236 split; worth a one-line future-work note.

## Ready for proposal

Yes. Scope is clear, every open question from #209's "Alcance a definir" is answered with code
evidence, and this is sized for a single PR with no chaining decision needed.

## Key Learnings

1. Purchase exceptions notify the buyer and ops via mail today, but Subscription exceptions
   trigger zero notification despite being reachable in production.
2. The `billing_purchases` table stores no `created_at` or reason column, requiring a join to
   `billing_payments` for any admin-useful context.
3. No use case exists today that transitions a `Purchase` out of `EXCEPTION`, making any admin view
   purely read-only observability, not a resolution workflow.
4. `SecurityConfig` gates every `/api/v1/admin/**` route to `ADMIN` only, with no `RECEPTIONIST`
   role registered anywhere in billing.
5. `PaymentAdminController`'s pending-verification list is the exact JPQL projection-query pattern
   to mirror for this issue's admin list.

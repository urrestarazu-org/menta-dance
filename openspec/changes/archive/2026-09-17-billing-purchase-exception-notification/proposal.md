# Proposal: Notify Student and Operations on Purchase EXCEPTION (Issue #209)

## Intent

A physical purchase that lands in `FulfillmentStatus.EXCEPTION` is a silent failure: the student
paid, got no seat, and nobody is told. Today the only trace is a `log.warn` inside the outbox worker
(`PhysicalCapacityAssignmentOutboxEventHandler`) plus a `Reason` that is **never persisted**
(`MarkPurchaseExceptionUseCase` javadoc states this explicitly). Support learns about it when the
student complains. This change makes EXCEPTION observable to the two people who need it: the buyer
and operations.

## Scope

### In Scope

- New outbox event `billing.PurchaseExceptioned`, appended inside `MarkPurchaseExceptionUseCase`
  in the same transaction as `purchaseRepository.save(purchase.exception())` — via the existing
  `BillingOutboxAppenderPort` (`Propagation.REQUIRED`), exactly as
  `PublishPhysicalPaymentCompletedUseCase` appends `billing.PhysicalPaymentCompleted`.
- New constant in `BillingOutboxEventTypes` + a payload record in `com.menta.shared.billing`.
- New `api:app` outbox handler dispatching to a billing notification port.
- Two emails per event: buyer (resolved `userId → email`) and a configured operations address.
- Cross-module email lookup extending the **existing** `shared/auth` bridge
  (`UserExistencePort` / `UserExistenceAdapter`, established by #131) — `billing → shared ← auth`,
  no `api:billing → api:auth` Gradle edge, no HTTP.
- Spanish user-facing copy (project convention); English code/JavaDoc.

### Out of Scope

- Virtual subscription EXCEPTION → **deferred to #236**. Virtual reaches EXCEPTION through a
  synchronous, outbox-free path (`PaymentVerificationService.ensureSubscription`, single trigger:
  plan deleted at confirmation). No shared mechanism, so no shared slice.
- Admin listing/query endpoint for EXCEPTION purchases → **deferred to #237**.
- Refunds, retries, auto-remediation, or any change to the EXCEPTION state machine itself
  (`ASSIGNED → EXCEPTION` stays refused; `EXCEPTION → EXCEPTION` stays an idempotent no-op).
- Push/SMS/in-app channels; BFF or Android surfaces.
- Changing who may reach EXCEPTION, or the five `Reason` values.

## Capabilities

### New Capabilities

- `purchase-exception-notification`: who is notified when a physical purchase reaches EXCEPTION,
  through which channel, with what content, and with what delivery guarantees.

### Modified Capabilities

- `presential-purchase-fulfillment`: reaching `EXCEPTION` now additionally emits a durable
  notification event in the same transaction; the transition rules themselves are unchanged.

## Approach

| # | Decision | Choice | Rationale |
|---|---|---|---|
| D1 | Trigger point | Append the event **inside** `MarkPurchaseExceptionUseCase`, not in the `api:app` handler | It is the single chokepoint for the physical EXCEPTION transition, and it is `@Transactional(REQUIRED)`, so event and state commit atomically. The handler has **four** `markException` call sites — hooking there would mean four hooks and one missable path. |
| D2 | Delivery | Outbox + `api:app` handler, mirroring `ActivationOutboxEventHandler` → `ActivationNotificationPort` | Reuses the proven async path; SMTP failure never rolls back the EXCEPTION transition, and the reconciler retries. |
| D3 | Identity lookup | Extend the existing `shared/auth` package with an email-returning port | The bridge already exists (#131). Note this **widens** what `billing` can see about a user from a boolean to contact data — a deliberate, documented precedent change (see Q2). |
| D4 | Operations recipient | Configured address, `@Value`-injected | No ops-recipient concept exists in the repo. Ops is not a user account, so a static configured address is the smallest correct model (see Q3). |
| D5 | Idempotency | The existing outbox unique index on (`aggregate_id`, `event_type`) | A redelivered event short-circuits on the `EXCEPTION → EXCEPTION` no-op **before** the append, so no duplicate is attempted; the index is the backstop. |

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `billing/application/usecase/MarkPurchaseExceptionUseCase.java` | Modified | Appends the event after `save`; gains the appender dependency |
| `billing/application/contract/BillingOutboxEventTypes.java` | Modified | `PURCHASE_EXCEPTIONED = "billing.PurchaseExceptioned"` |
| `shared/billing/PurchaseExceptionedOutboxPayload.java` | New | `paymentId`, `purchaseId`, `userId`, `reason?`, `occurredAt` |
| `shared/auth/…Port.java` + `auth/…Adapter.java` | New | Email lookup, sibling of `UserExistencePort`/`UserExistenceAdapter` |
| `billing/application/port/out/…NotificationPort.java` + mail adapter | New | Modeled on `ActivationNotificationPort` / `SpringMailActivationNotificationAdapter` |
| `app/outbox/PurchaseExceptionOutboxEventHandler.java` | New | `supports(...)` + dispatch, like `ActivationOutboxEventHandler` |
| `billing/infrastructure/config/BillingConfiguration.java` | Modified | Rewire the `markPurchaseExceptionUseCase` bean |
| `api/app/src/main/resources/db/migration/V21__*.sql` | New **(only if Q1 = persist)** | `reason` column on `billing_purchases` |
| `application-*.yml` | Modified | Ops address + from-address properties |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Appending inside the use case poisons the outbox-worker transaction | Med | The appender is `REQUIRED` and never throws on the happy path; the existing `noRollbackFor = IllegalPurchaseStateTransitionException` contract is untouched, and append happens only on the real `PENDING_FULFILLMENT → EXCEPTION` edge |
| The `TARGET_NOT_SCHEDULED` path with **no Purchase row** throws `PaymentNotFoundException` before any append — student never notified | Med | Real gap, reachable at handler line 130. Spec must state expected behaviour explicitly; a payment-level fallback notification may be needed |
| Email to a student for a failure they cannot act on increases support load | Med | Copy must state the concrete next step; ops receives the same event and can act first |
| D3 widens `billing`'s view of identity beyond a boolean | Med | Return **only** an email, never a `User`; JavaDoc the constraint like `UserExistencePort` does; Gradle still forbids `com.menta.auth.*` imports in `billing` |
| SMTP outage silently drops notifications | Med | Outbox retry/blacklist machinery already exists; do not swallow send failures in the handler |
| Notification content leaks internal `Reason` enum wording to students | Low | Ops message may carry the technical reason; student message uses neutral Spanish copy |
| Coverage gate: billing is 100% domain+application / 85% infrastructure | Low | New use-case logic needs full unit coverage in the same slice |

## Rollback Plan

Revert the PR. The change is additive: pending `billing.PurchaseExceptioned` rows are left
undispatched (no handler `supports` them, so the worker skips them; purge them if desired). Nothing
in the EXCEPTION state machine changes, so no purchase row needs repair. If Q1 lands a `reason`
column, its migration reverts with a compensating `DROP COLUMN` — the column is nullable and
read-only for notification, so no data rewrite is required. The feature can also be blunted without
a revert by pointing the ops address at a sink and disabling the mail sender.

## Dependencies

- None blocking. #131 (`shared/auth` bridge) and #115 (`fix-presential-purchase-quota-exception`,
  which created the EXCEPTION mechanics) are merged.
- Requires SMTP configuration in the target environment (already needed by account activation).

## Success Criteria

- [ ] Every `PENDING_FULFILLMENT → EXCEPTION` transition for a physical purchase produces exactly one
      `billing.PurchaseExceptioned` outbox row, committed atomically with the status change.
- [ ] A redelivered `billing.PhysicalPaymentCompleted` event produces **no** second notification.
- [ ] Both the buyer's email and the configured ops address receive a message; an integration test
      asserts both recipients from one EXCEPTION.
- [ ] A rolled-back EXCEPTION transition leaves **no** outbox row.
- [ ] SMTP failure leaves the purchase in `EXCEPTION` and the event retryable — never lost, never
      reverting the state change.
- [ ] Student-facing copy is Spanish; no `com.menta.auth.*` import exists under `api/billing/`.
- [ ] Virtual EXCEPTION behaviour is byte-identical to today (regression lock for the #236 boundary).

## Locked decisions (confirmed with the product owner — not open questions)

Recipients (student + ops), physical-only scope, and no admin endpoint were already locked before
this proposal was drafted. The five items below were genuinely open when this proposal was first
written; each is now resolved and must not be reopened by `sdd-spec`/`sdd-design`.

- **D6 — `Reason` is payload-only, not persisted.** Carried in the `billing.PurchaseExceptioned`
  outbox payload (`reason?`), never added as a `billing_purchases` column in this change. Ops can
  read *why* from the notification itself; the "why" is not queryable after the row dispatches.
  A `reason` column (V21) is the natural follow-up if/when #237 (admin listing) needs it queryable —
  explicitly not built here, to keep this slice additive-only with no migration.
- **D7 — Email-port shape is email-only.** `UserContactPort`-style lookup returns just an
  `Optional<String>` email, never a name or any other `User` field — matching the minimal-contract
  discipline `UserExistencePort`'s own JavaDoc already argues for. The Spanish copy does not address
  the student by name.
- **D8 — Ops recipient is one configured address.** A single `@Value`-injected
  `billing.purchase-exception.ops-address`, pointed at a distribution list managed outside the app —
  not a comma-separated list or a per-notification override.
- **D9 — Student copy promises a human follow-up, never a refund.** The message states operations
  will contact the student; it never promises or implies a refund, because no refund path exists in
  the code (#41 excluded automatic refund policy as a deliberate business decision, not an oversight
  this change should quietly work around).
- **D10 — The `TARGET_NOT_SCHEDULED` / missing-Purchase-row gap (handler line 130) is in scope.**
  This is precisely the "paid and nobody noticed" case #209 describes, and it is the worst instance
  of it: `markException` throws `PaymentNotFoundException` before any append, so the outbox-only
  design above does not cover it on its own. `sdd-design` must add a payment-level fallback path
  (the state machine has no `Purchase` row to hang an outbox append on at this call site, so the
  notification trigger there cannot reuse `MarkPurchaseExceptionUseCase` — a companion mechanism
  keyed on `PaymentId` alone is needed) so a buyer whose payment target never resolved to a real
  course/session is still notified. This is real added scope versus the original "hook one
  use case" framing in D1 — `sdd-design` should size it explicitly rather than let it fall out of
  the happy-path design.

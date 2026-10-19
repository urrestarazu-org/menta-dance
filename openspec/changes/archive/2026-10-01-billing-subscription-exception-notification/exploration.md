# Exploration: Notify EXCEPTION for virtual subscriptions

**Change**: `billing-subscription-exception-notification`
**Issue**: #236 ("Notificar EXCEPTION también para suscripciones virtuales", follow-up of #209)
**Phase**: explore (read-only) · **Date**: 2026-10-01
**Canonical copy**: Engram `sdd/billing-subscription-exception-notification/explore` (obs #1673). This file mirrors it (hybrid store).

## Current State

### 1. How #209 works (physical purchase EXCEPTION notification)

Source: `openspec/changes/archive/2026-09-17-billing-purchase-exception-notification/` and code.

- **Purchase-level producer (D1)**: `MarkPurchaseExceptionUseCase` — `@Transactional(REQUIRED, noRollbackFor=IllegalPurchaseStateTransitionException)`. Only on the real `PENDING_FULFILLMENT -> EXCEPTION` edge: `save(purchase.exception())` then `outboxAppender.append(PURCHASE_EXCEPTIONED, paymentId, json)` in the same transaction. `EXCEPTION -> EXCEPTION` returns early (no append); `ASSIGNED -> EXCEPTION` throws.
- **Payment-level producer (D10)**: `PublishPaymentFulfillmentFailedUseCase`, `REQUIRES_NEW` append of `billing.PaymentFulfillmentFailed` keyed on `paymentId` (the caller transaction is doomed at 3 pre-Purchase sites; defect #238 still open).
- **Event constants**: `application/contract/BillingOutboxEventTypes` (`PURCHASE_EXCEPTIONED`, `PAYMENT_FULFILLMENT_FAILED`, `PHYSICAL_PAYMENT_COMPLETED`).
- **Payloads**: `api/shared/.../shared/billing/PurchaseExceptionedOutboxPayload`, `PaymentFulfillmentFailedOutboxPayload` (records shared by producer and consumer). `reason` = `Reason` enum name, payload-only (D6, never persisted).
- **Consumer**: `api/app/.../outbox/PurchaseExceptionNotificationOutboxEventHandler` (`OutboxEventHandler`; supports exactly the two types; maps to the `PurchaseExceptionNotification` DTO; calls the port; send failures propagate so the worker keeps `FAILED`/backoff).
- **Port**: `PurchaseExceptionNotificationPort.notify(...)` (must propagate `MailException`). The DTO is purchase-flavoured (`paymentId`, `purchaseId?`, `userId?`, `reason`, `occurredAt`, `eventType`).
- **Adapter**: `infrastructure/notification/SpringMailPurchaseExceptionNotificationAdapter`: buyer email via `UserEmailLookupPort.findEmailById`; a missing email logs and skips the buyer but never the ops mail; sends buyer then ops; at-least-once. Buyer copy is Spanish, no name, no `Reason`, no refund promise, and says "No pudimos confirmar tu lugar" (seat wording is physical-specific).
- **Config**: `application.yml:137-139` `billing.purchase-exception.{ops-address,from-address}`.
- **Wiring**: `BillingConfiguration.markPurchaseExceptionUseCase` (~L540).
- **Idempotency**: (a) state edge guard; (b) V2 unique index `uk_common_outbox_aggregate_event_type (aggregate_id, event_type)` as backstop. Lesson #242: a unique violation inside a `REQUIRED` transaction marks the caller rollback-only, so producers on a shared transaction MUST pre-check `BillingOutboxAppenderPort.existsForAggregateAndEventType` (as `PublishPhysicalPaymentCompletedUseCase` does).
- **Locked #209 decisions to reuse**: D6 reason payload-only; D7 email-only lookup, no name; D8 single ops address; D9 buyer copy promises human follow-up, never a refund. Revert note: `OutboxReconciliationWorker.resolveHandler` throws for an unregistered type, so reverting a new event type requires purging pending rows.
- **Spec conflict**: `openspec/specs/purchase-exception-notification/spec.md` requirement "Only physical purchases are in scope" (scenario "Virtual EXCEPTION emits no notification event") is contradicted by #236 and must become a MODIFIED delta. A unit regression lock also exists in `PaymentVerificationServiceTest` (~L288-297, "#209 D.4 regression lock (the #236 boundary)").

### 2. How a virtual subscription reaches EXCEPTION

- State = `Subscription.fulfillmentStatus == EXCEPTION` (an axis independent of `SubscriptionStatus`). `Subscription.exception()` leaves `status` at `PENDING`. No reason or timestamp is persisted.
- The only production caller of `Subscription.exception()` is `PaymentFulfillmentService.ensureSubscription` (`api/billing/.../application/usecase/PaymentFulfillmentService.java:70-95`): subscription exists, not activated, `planRepository.findById` (any status) empty -> `save(existing.exception())` and return.
- `PaymentVerificationService` only delegates to `PaymentFulfillmentService.ensure`. The same chokepoint is also reached by `ResolvePaymentProofUseCaseImpl` (admin approves a bank-transfer proof) and `CorrectPaymentUseCaseImpl` (admin correction). Hooking `PaymentFulfillmentService.ensureSubscription` covers webhook + bank-transfer approval + correction; hooking `PaymentVerificationService` would miss two rails.
- Transaction context: `WebhookVerificationWorker.process` is `@Transactional(REQUIRES_NEW)` and catches `RuntimeException` from verify into retry bookkeeping. `ResolvePaymentProof`/`CorrectPayment` are wrapped by transactional decorators in `BillingConfiguration` (exact wrapper to confirm in design).
- **Replay semantics**: after EXCEPTION the subscription stays `PENDING`, so every later `ensure()` (webhook replay, duplicate webhook on a Completed payment) re-enters the plan lookup. If the plan appears -> `ASSIGNED` (test `retries_a_subscription_left_exceptional_by_the_first_fulfillment_attempt`); if still missing -> `exception()` + save again, no change. A naive hook at `plan.isEmpty()` would **re-notify on every replay**. An edge guard is required: notify only if the persisted `fulfillmentStatus != EXCEPTION` before the save.
- `PaymentFulfillmentService` is a plain class (not final, not annotated), constructed with `new` in 5 places (`PaymentVerificationService` legacy constructor, `BillingConfiguration` x2, `PaymentFulfillmentServiceTest`, `PaymentVerificationServiceTest`). A constructor change touches all five.

### Reachability finding (contradicts the #209/#237 docs claiming "reachable in production today")

Verified by the orchestrator against the repository before relaying:

- `V14__billing_checkout.sql:116-117`: `fk_billing_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES billing_plans (id)` with no `ON DELETE` clause (default `RESTRICT`). A plan row cannot be deleted while a subscription references it, and the pending subscription exists before the payment settles.
- No application code deletes plans: the `PlanRepository` port has no delete, `api/billing/src/main` has no plan delete path, and there is no admin plan CRUD. Plans only go `INACTIVE`, and `findById` deliberately returns inactive plans (scenario 2b), so deactivation does not trigger EXCEPTION.
- An empty `findById` therefore needs manual DB surgery with FK checks off, a dropped FK, or mocks. #236 is defensive observability for a state that requires data corruption. The earlier "reachable" claim came from code reading only.

## Affected Areas

- **Reusable**: `BillingOutboxAppenderPort` (`append`, `existsForAggregateAndEventType`), `Clock`, the `ObjectWriter` pattern, the `OutboxEventHandler` SPI + `OutboxReconciliationWorker`, `UserEmailLookupPort` (shared/auth; `UserContactPort.emailOf` from #33 is an equivalent duplicate, do not add a third), `billing.purchase-exception.*` properties (or sibling keys).
- **Not reusable as-is**: `Reason` enum (physical-only values), the `PurchaseExceptionNotification` DTO (`purchaseId`), the buyer copy (seat wording).
- **New/modified for the recommended approach**: `BillingOutboxEventTypes` (+`SUBSCRIPTION_EXCEPTIONED = "billing.SubscriptionExceptioned"`); `api/shared` `SubscriptionExceptionedOutboxPayload` (`subscriptionId`, `paymentId`, `userId`, `planId`, `reason`, `occurredAt`); `PaymentFulfillmentService` (edge-guarded hook) + 5 construction sites; new `PublishSubscriptionExceptionedUseCase` (`REQUIRED` append, exists pre-check, `ObjectWriter`; precedent `PublishPhysicalPaymentCompletedUseCase`); new subscription notification port + DTO; new Spanish SpringMail subscription adapter in `infrastructure/notification`; new sibling handler in `api/app/.../outbox`; `BillingConfiguration` wiring; `application.yml` only if new keys; delta spec MODIFIED "Only physical purchases are in scope"; update the `PaymentVerificationServiceTest` regression lock.
- **Architecture**: ArchUnit `api/billing/src/test/.../ArchitectureTest` (domain not -> application/infrastructure, application not -> infrastructure, layered rule, no `com.menta.auth.*` in billing). Keeping the hook + JSON in application and mail + email lookup in infrastructure touches no rule.

## Transaction mechanism comparison

- **A. In-transaction outbox append (`REQUIRED`) + `api:app` handler**: atomic with the subscription save, durable, SMTP failure never touches the verification transaction, retry/backoff for free, consistent with #209. Cons: new event/payload/port/adapter/handler, mandatory exists pre-check (#242), purge-on-revert.
- **B. After-commit send** (precedent `SpringMailPaymentDecisionNotificationAdapter`, `TransactionSynchronization.afterCommit` + guarded swallow): smallest, no new event/handler. Cons: non-durable; an SMTP failure drops the only signal, which defeats "paid and nobody told".
- **C. Synchronous in-transaction send**: rejected (an SMTP failure inside the webhook transaction causes a retry loop and duplicate mails, adds latency inside a DB transaction, may notify rolled-back state).
- **D. Reuse `PaymentFulfillmentFailed` event/handler/adapter**: needs a new `Reason` value (widens a locked enum), has no target discriminator so the copy would say "lugar", and the event is designed for a `REQUIRES_NEW` doomed transaction; muddles capabilities.

For A, `REQUIRED` is correct (the caller transaction is not doomed here). Idempotency is two-layered: edge guard (`fulfillmentStatus != EXCEPTION`) + `existsForAggregateAndEventType` pre-check, with the V2 unique index as a race backstop.

## Approaches

| # | Approach | Effort | Notes |
|---|---|---|---|
| 1 | Dedicated outbox event + sibling notification path (recommended if built) | Medium | Durable, atomic, mirrors #209, physical code untouched; ~8-10 new files |
| 2 | Generalize the #209 pipeline (discriminator / third type in shared payload, DTO, port, handler, adapter) | Medium-High | Modifies proven archived code; nullable-union smell |
| 3 | After-commit mail send | Low-Medium (~200-300 lines) | Non-durable |
| 4 | Do not build; close/defer as won't-fix citing the FK evidence | Low | Leaves a silent state if the FK is ever dropped |
| 5 | Hybrid: edge-guarded `log.error` (+ metric) now, notification deferred | Low | Does not satisfy the issue title |

## Recommendation

Raise the reachability finding with the user first (Q1). If #236 is still wanted as titled, build Approach 1 (event `billing.SubscriptionExceptioned`, edge-guarded in-transaction append from `PaymentFulfillmentService`, sibling handler/port/adapter, reuse `UserEmailLookupPort` and #209 D6-D9). Approach 3 is the fallback if the user prefers minimal scope over durability; 4/5 are legitimate if the state is deemed unreachable.

## Tests and coverage gates

Billing gates (`api/billing/build.gradle.kts`): domain+application 0.85 LINE (real ~98%), infrastructure 0.85 (real ~92.9%). New application logic needs full unit coverage in the same slice.

- Extend: `PaymentFulfillmentServiceTest` (`ensure_degrades_to_exception_when_the_plan_no_longer_exists`: one publish on first EXCEPTION, zero when already EXCEPTION, zero on happy/already-activated), `PaymentVerificationServiceTest` (regression lock L288-297; retry/recovery path publishes nothing; constructor change), `ResolvePaymentProof`/`CorrectPayment` tests (rails reach the hook).
- New: publisher unit test (mirror `PublishPaymentFulfillmentFailedUseCaseTest`), handler test (mirror `PurchaseExceptionNotificationOutboxEventHandlerTest`), adapter test (mirror `SpringMailPurchaseExceptionNotificationAdapterTest`), `api:app` Testcontainers integration (force `findById` empty; assert exactly one outbox row, both recipients, zero on replay; do NOT rely on the DB unique backstop under Testcontainers, #242).

## Size (800-line review budget)

Approach 1 ≈ 450-600 changed lines including tests/spec (publisher + payload + type ~90; hook + wiring + 5 sites ~60; port/DTO + adapter ~130; handler ~60; unit tests ~250; integration ~100; spec ~60). Above the repo default of 400 but under 800; optionally 2 slices (A: producer + hook; B: handler + port + adapter + integration). Approach 3 ≈ 200-300; approaches 4/5 < 100.

## Open Questions (product decisions)

1. **(Blocking)** Is #236 still wanted given the FK reachability finding? Build anyway / log alarm only / close as won't-fix.
2. Durability vs simplicity: outbox (A) vs after-commit (B).
3. Recipients: buyer + ops, or ops only? Same ops mailbox (`billing.purchase-exception.ops-address`) or a sibling key `billing.subscription-exception.*`?
4. Buyer copy: carry D9 (no refund/activation promise); plan name not used (D7; the lookup is what failed).
5. Scope: include the sibling silent case `existing.isEmpty()` (Completed payment with no subscription row) and `ReconciliationRequired`? Recommended non-goal: no.
6. Confirm non-goals: no admin list for subscription EXCEPTION (#237 extension), no persisted reason, no refund/auto-remediation, no Subscription state machine change, no `Reason` enum change, no BFF/Android.
7. Accept the auto-recovery race (a later retry flips to `ASSIGNED` after the buyer was told "needs attention"; same accepted risk as #209 C3)?
8. Event aggregate id: `subscriptionId` vs `paymentId` (design-level).

## Risks

Reachability (FK + no delete path); re-notify on replay without the edge guard; a unique violation in a `REQUIRED` append rolls back the webhook transaction (#242), so the pre-check is mandatory; purge-on-revert for a new event type; the constructor change touches 5 sites and two #209/#236 regression locks; the spec conflict must be a MODIFIED requirement; #238 is unrelated but the same theme (out of scope); duplicate email ports (`UserEmailLookupPort` / `UserContactPort`).

## Ready for Proposal

Yes, conditionally: answer Q1 first and record it as a locked decision in the proposal (as #209 did for D6-D9).

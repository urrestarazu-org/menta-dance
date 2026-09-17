# Design: Notify Student and Operations on Purchase EXCEPTION

## Technical Approach

Two producers, one consumer, one delivery adapter.

The **purchase-level** producer is D1: `MarkPurchaseExceptionUseCase` appends
`billing.PurchaseExceptioned` immediately after `purchaseRepository.save(purchase.exception())`,
in the same `REQUIRED` transaction, exactly as `PublishPhysicalPaymentCompletedUseCase` appends
`billing.PhysicalPaymentCompleted`.

The **payment-level** producer is D10: a second event `billing.PaymentFulfillmentFailed`, keyed on
`PaymentId` alone, appended from `api:app` at the handler sites where no `Purchase` row exists.

Both land in one new `api:app` handler dispatching to one billing out-port, whose Spring Mail
adapter resolves the buyer's email through a new email-only `shared/auth` port (D3/D7) and a
`@Value` ops address (D4/D8). Nothing in the EXCEPTION state machine changes.

## Architecture Decisions

### C1 — D10 is three call sites, not one, and the current code already fails there

The proposal and issue name "handler line 130". Reading
`PhysicalCapacityAssignmentOutboxEventHandler.handle` shows **three** `markException` calls that
run *before* `purchaseCreationFromEventPort.createPurchaseFromPaymentEvent`:

| Line | Condition | Reason | `Purchase` row | `userId` available |
|---|---|---|---|---|
| 130 | payment absent or non-Physical target | `TARGET_NOT_SCHEDULED` | no | **only if `payment != null`** |
| 202 | `PhysicalCourseQuote` not found | `TARGET_NOT_SCHEDULED` | no | yes |
| 239 | coverage shortfall (`Plan` not `Complete`) | `TARGET_NOT_SCHEDULED` | no | yes |

The other two sites (164, 267) call `createPurchaseFromPaymentEvent` first, so D1 covers them.

At all three, `markException` hits `purchaseRepository.findByPaymentId(...).isEmpty()` and throws
`PaymentNotFoundException`, which is **not** in that method's `noRollbackFor`. It propagates out of
`handle`, the worker marks the row FAILED and retries to blacklist, and the `Purchase` never reaches
EXCEPTION at all. This is pre-existing and already documented in the handler's own comment at lines
155–158. **This change does not fix it** — repairing the state machine is out of scope per the
proposal — but the design must survive it: a notification appended in that doomed transaction would
roll back with it.

### C2 — The payment-level append is `REQUIRES_NEW`; the purchase-level append is `REQUIRED`

| | D1 `PurchaseExceptioned` | D10 `PaymentFulfillmentFailed` |
|---|---|---|
| Producer | `MarkPurchaseExceptionUseCase` (billing application) | `PublishPaymentFulfillmentFailedUseCase` (billing application), called from `api:app` |
| Propagation | `REQUIRED` — atomic with `purchase.exception()` | `REQUIRES_NEW` — **must** commit even though the caller's transaction is about to die (C1) |
| Duplicate on redelivery | impossible: `EXCEPTION → EXCEPTION` returns before the append (D5) | certain: the worker retries the same doomed path — absorbed by the unique index |

`REQUIRED` for D10 would be wrong, not merely suboptimal: it would guarantee the notification is
never delivered on exactly the paths D10 exists to cover.

`REQUIRES_NEW` suspends the outer transaction, so the inner `DataIntegrityViolationException` from
`idx_common_outbox_aggregate_event_type` on the second delivery never marks the worker's transaction
rollback-only. The handler catches it and continues — "already notified", the same
"let the database unique constraint decide" discipline `PublishPhysicalPaymentCompletedUseCase`
already documents.

### C3 — Two event types, not one nullable-`purchaseId` event

A single shared event type would dedupe both producers against each other on
(`aggregate_id`, `event_type`) — superficially attractive. It is rejected because the D1 append is
`REQUIRED`: a collision there throws `DataIntegrityViolationException` inside the purchase
transaction and **rolls back the EXCEPTION transition itself**. That is the proposal's first risk row,
and a single event type would manufacture it. Two types cannot collide.

Cost, stated rather than hidden: a payment that hits site 239 on delivery 1 (event appended) and
then succeeds far enough on a later delivery to reach site 267 produces **two** notifications. Both
describe the same payment, both carry `paymentId`, ops correlates. Accepted; see Risks.

### C4 — Email resolution lives in the mail adapter, and a missing email never suppresses ops

`shared/auth` gains `UserEmailLookupPort.findEmailById(UUID) → Optional<String>` — email and nothing
else (D7), JavaDoc'd with the same minimal-contract argument `UserExistencePort` already carries.
`auth` implements it as a sibling of `UserExistenceAdapter` over
`UserRepository.findById(UserId).map(u -> u.getEmail().getValue())`.

Resolution happens in billing's **infrastructure** mail adapter, not in the handler or use case, so
`application` stays free of contact data. Delivery order and failure rules:

1. Resolve the buyer email. `Optional.empty()` (or a null `userId`, site 130 with `payment == null`)
   → log, skip the student message, **continue**.
2. Send the student message (Spanish, D9).
3. Send the ops message.

Only a thrown `MailException` escapes, so the worker retries the row — never lost (proposal success
criterion 5). Delivery is at-least-once, identical to the activation email; a retry after a partial
send may duplicate one message.

### C5 — Spanish copy shape (structure only; wording is an implementation detail)

Inline constants in the adapter, matching `SpringMailActivationNotificationAdapter` — no template
engine exists in the repo and this change does not introduce one.

**Student** — no name (D7), no `Reason` wording, no refund (D9):

```
Subject: Tu compra en Menta Dance necesita atención
Body:    Referencia de pago: {paymentId}
         Fecha: {occurredAt}
         No pudimos confirmar tu lugar. Nuestro equipo se comunicará contigo.
         (never: importe, reintento automático, reembolso)
```

**Ops** — carries the technical reason:

```
Subject: [Menta Dance] Compra en EXCEPTION — pago {paymentId}
Body:    paymentId={paymentId} purchaseId={purchaseId|—} userId={userId|no resuelto}
         reason={reason} occurredAt={occurredAt} evento={eventType}
```

## Data Flow

    MarkPurchaseExceptionUseCase.markException(paymentId, reason)      [billing, REQUIRED]
      ├─ no Purchase row      ─→ PaymentNotFoundException   (C1 — D10 territory, no append)
      ├─ status == ASSIGNED   ─→ IllegalPurchaseStateTransitionException  (no append)
      ├─ status == EXCEPTION  ─→ return                      (D5 — no append, no duplicate)
      └─ PENDING_FULFILLMENT  ─→ save(purchase.exception())
                                 └─ append(billing.PurchaseExceptioned, paymentId, json)

    PhysicalCapacityAssignmentOutboxEventHandler               [api:app, worker tx]
      sites 130 / 202 / 239 (no Purchase row):
        publishPaymentFulfillmentFailed(paymentId, userId?, reason)   REQUIRES_NEW → commits
          └─ DataIntegrityViolationException ─→ caught, "already notified"
        markException(...)  ← unchanged; still throws PaymentNotFoundException (C1)
      sites 164 / 267 (Purchase row exists): unchanged — D1 covers them

    OutboxReconciliationWorker
      └─ PurchaseExceptionNotificationOutboxEventHandler   (supports BOTH types)
            └─ PurchaseExceptionNotificationPort.notify(PurchaseExceptionNotification)
                  └─ SpringMail adapter: UserEmailLookupPort → student mail → ops mail

## File Changes

| File | Action | Description |
|---|---|---|
| `shared/.../auth/UserEmailLookupPort.java` | Create | `Optional<String> findEmailById(UUID)` — email only (C4, D7) |
| `auth/.../persistence/adapter/UserEmailLookupAdapter.java` | Create | `@Component` sibling of `UserExistenceAdapter` |
| `shared/.../billing/PurchaseExceptionedOutboxPayload.java` | Create | `paymentId, purchaseId, userId, reason, occurredAt` |
| `shared/.../billing/PaymentFulfillmentFailedOutboxPayload.java` | Create | `paymentId, userId?, reason, occurredAt` (no `purchaseId`) |
| `billing/.../application/contract/BillingOutboxEventTypes.java` | Modify | `PURCHASE_EXCEPTIONED`, `PAYMENT_FULFILLMENT_FAILED` |
| `billing/.../application/usecase/MarkPurchaseExceptionUseCase.java` | Modify | `+BillingOutboxAppenderPort`, `+Clock`, `ObjectWriter`; append after `save` (D1) |
| `billing/.../application/usecase/PublishPaymentFulfillmentFailedUseCase.java` | Create | `REQUIRES_NEW` append keyed on `PaymentId` (C2) |
| `billing/.../application/dto/PurchaseExceptionNotification.java` | Create | `paymentId, purchaseId?, userId?, reason, occurredAt, eventType` |
| `billing/.../application/port/out/PurchaseExceptionNotificationPort.java` | Create | `void notify(PurchaseExceptionNotification)` |
| `billing/.../infrastructure/notification/SpringMailPurchaseExceptionNotificationAdapter.java` | Create | Two messages, `@Value` from/ops addresses, copy constants (C4, C5) |
| `billing/.../infrastructure/config/BillingConfiguration.java` | Modify | Rewire `markPurchaseExceptionUseCase`; add `publishPaymentFulfillmentFailedUseCase` bean |
| `app/.../billing/PublishPaymentFulfillmentFailedAdapter.java` | Create | Typed `api:app` callable, like `MarkPurchaseExceptionAdapter` |
| `app/.../outbox/PurchaseExceptionNotificationOutboxEventHandler.java` | Create | `supports` both types; maps both payloads to one notification |
| `app/.../outbox/PhysicalCapacityAssignmentOutboxEventHandler.java` | Modify | One private helper called before the three pre-Purchase `markException` sites (C1) |
| `api/app/src/main/resources/application*.yml` | Modify | `billing.purchase-exception.{ops-address,from-address}` |

No migration. D6 keeps `Reason` payload-only; no `billing_purchases` column, no V21.

## Interfaces / Contracts

```java
// api/shared/src/main/java/com/menta/shared/auth/UserEmailLookupPort.java
public interface UserEmailLookupPort {
    /** Email only: never a User, a name, a role, or a status (D7). */
    Optional<String> findEmailById(UUID userId);
}

// api/billing/.../application/port/in — called from api:app, REQUIRES_NEW (C2)
public interface PublishPaymentFulfillmentFailedPort {
    /** @param userId may be null when the Payment row itself is absent (site 130). */
    void publish(PaymentId paymentId, UUID userId, Reason reason);
}
```

## Testing Strategy

| Layer | What to test | Approach |
|---|---|---|
| Billing application (unit) | `markException` appends **exactly once** on `PENDING_FULFILLMENT → EXCEPTION`; appends **zero** times on the `EXCEPTION` no-op, the `ASSIGNED` refusal, and the `PaymentNotFoundException` path; payload carries `reason` and a fixed-`Clock` `occurredAt` | Extend `MarkPurchaseExceptionUseCaseTest`, Mockito on the appender — carries billing's 100% floor |
| Billing application (unit) | `PublishPaymentFulfillmentFailedUseCase` serializes a null `userId`; JSON failure throws `IllegalStateException` without appending | Mirror `PublishPhysicalPaymentCompletedUseCaseTest` |
| Billing infra (unit) | Adapter sends two messages; unresolved email ⇒ **ops still sent**, student skipped; student body contains no `Reason` token and no refund wording; `MailException` propagates | Mockito on `JavaMailSender` + `UserEmailLookupPort`, asserting `SimpleMailMessage` recipients |
| Auth infra (unit) | `UserEmailLookupAdapter` maps `User.getEmail().getValue()`; unknown id ⇒ `Optional.empty()` | Mockito on `UserRepository` |
| App (unit) | New handler `supports` both types and **only** those two; each payload maps to the right notification; send failure propagates so the worker marks the row FAILED | Mirror `ActivationOutboxEventHandlerTest` |
| App (unit) | `PhysicalCapacityAssignmentOutboxEventHandlerTest`: the publish helper fires at sites 130/202/239 and **not** at 164/267; `DataIntegrityViolationException` from the publish is swallowed and `markException` is still attempted | Extend the existing 5-call-site test class |
| App (integration) | One real `PENDING_FULFILLMENT → EXCEPTION` ⇒ exactly one outbox row and **two** recipients (success criterion 3); redelivery ⇒ still one row, no second pair; rolled-back transition ⇒ zero rows | Testcontainers MySQL 8 + `GreenMail`/mock sender, extending `PhysicalPurchaseIntegrationTest` |
| App (integration) | **C2 proof**: force site 239, let `PaymentNotFoundException` fail the worker row, assert the `billing.PaymentFulfillmentFailed` row **survives** the rollback and a retry adds no second row | Testcontainers — this is the whole point of `REQUIRES_NEW` |
| Regression lock | Virtual EXCEPTION via `PaymentVerificationService.ensureSubscription` emits **zero** rows and sends nothing (#236 boundary, success criterion 7) | Existing virtual suite + one added assertion |
| Architecture | `BillingArchitectureTest`: no `com.menta.auth..` import under `api/billing`; contact data stays out of `billing.application` | Existing ArchUnit run |

TDD order: RED billing-application append → RED `REQUIRES_NEW` publisher → RED mail adapter
(two recipients / missing email) → RED handler wiring → RED integration C2 proof.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. No new HTTP endpoint; the only new egress is SMTP through the
`JavaMailSender` already configured for account activation.

## Migration / Rollout

No schema migration (D6). Rollout is config-first: deploy with
`billing.purchase-exception.ops-address` pointed at a real distribution list; pointing it at a sink
blunts the feature without a revert.

**Correction to the proposal's rollback plan**: `OutboxReconciliationWorker.resolveHandler` throws
`IllegalStateException("No handler registered for event type: …")` — it does **not** skip unknown
types. Reverting the PR therefore requires deleting any pending `billing.PurchaseExceptioned` /
`billing.PaymentFulfillmentFailed` rows, or the worker loop fails on them. Purging is mandatory,
not optional.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Two notifications for one payment when a retry advances past the pre-Purchase site (C3) | Low | Both carry `paymentId`; ops correlates. Accepted over rolling back an EXCEPTION transition |
| Student notified, then a later retry succeeds and the purchase lands `ASSIGNED` | Low | Copy promises only that ops will make contact (D9) — it never asserts the purchase failed permanently |
| Site 130 with `payment == null` cannot resolve a buyer | Med | Ops is still notified with `userId=no resuelto`; ops has the `paymentId` and the provider record |
| Partial send duplicates one message on retry | Med | At-least-once, same as the activation email; ops-facing duplication is cheap |
| The C1 state-machine bug remains: those purchases still never reach `EXCEPTION` | High | Explicitly out of scope; notification is decoupled from it by C2. Should be filed as a follow-up alongside #237 |

## Open Questions

None blocking. D1–D10 are locked and applied; C1–C5 resolve every implementation decision they left
open. One item for `sdd-tasks` to surface, not a design question: the C1 state-machine defect should
become its own issue rather than a silent TODO.

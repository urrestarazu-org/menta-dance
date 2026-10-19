# Purchase Exception Notification Specification

## Purpose

When a physical purchase reaches `FulfillmentStatus.EXCEPTION`, notify the buyer and operations so the "paid, got no seat, nobody told" failure is observable instead of silent. Covers recipients, channel, content, and delivery guarantees for both the normal outbox-driven path and the payment-level fallback where no `Purchase` row ever existed.

## Requirements

### Requirement: One EXCEPTION event notifies exactly two recipients

When a `billing.PurchaseExceptioned` outbox row is dispatched, the system MUST send exactly two emails: one to the buyer's address (resolved `userId → email`) and one to the configured operations address (D8). Both MUST originate from the same event.

#### Scenario: One EXCEPTION event, two recipients notified

- GIVEN a dispatched `billing.PurchaseExceptioned` row for `userId = U`
- WHEN the handler processes it
- THEN one email is sent to `U`'s resolved address
- AND one email is sent to the configured `billing.purchase-exception.ops-address`

### Requirement: Notification dispatch happens at most once per EXCEPTION transition

Redelivery of the event that caused the transition MUST NOT produce a second notification. This relies on the existing `EXCEPTION → EXCEPTION` no-op in the state machine, which short-circuits before any event append, plus the outbox unique index on `(aggregate_id, event_type)` as backstop (D5).

#### Scenario: Redelivered completion event produces no second notification

- GIVEN a purchase already `EXCEPTION`, with its `billing.PurchaseExceptioned` row already dispatched
- WHEN the reconciler redelivers the triggering `billing.PhysicalPaymentCompleted` event
- THEN no additional `billing.PurchaseExceptioned` row is appended
- AND no additional email is sent

### Requirement: Delivery failure never reverts state and is always retryable

An SMTP failure while sending either email MUST NOT roll back the `EXCEPTION` transition or the outbox row's dispatch state. The row MUST remain retryable through the existing outbox reconciliation/retry machinery, mirroring `ActivationOutboxEventHandler`.

#### Scenario: SMTP failure leaves the purchase EXCEPTION and the event retryable

- GIVEN a `billing.PurchaseExceptioned` row ready for dispatch
- WHEN the mail adapter throws on send
- THEN the `billing_purchases` row remains `EXCEPTION`
- AND the outbox row is not marked `COMPLETED`
- AND a later retry re-attempts dispatch

### Requirement: Buyer copy is neutral Spanish, promises follow-up, never a refund

The buyer-facing message MUST be in Spanish, MUST NOT address the buyer by name (D7 — the port returns email only), MUST state that operations will contact them, and MUST NOT promise or imply a refund (D9).

#### Scenario: Buyer email states human follow-up, no refund

- GIVEN a dispatched `billing.PurchaseExceptioned` event
- WHEN the buyer email is composed
- THEN its body is Spanish
- AND it states operations will follow up
- AND it contains no refund promise or refund-implying wording

### Requirement: Ops copy may carry the technical reason

The operations message MAY include the `Reason` enum value carried in the event payload; the buyer message MUST NOT.

#### Scenario: Ops email carries the reason, buyer email does not

- GIVEN a `billing.PurchaseExceptioned` event with `reason = CAPACITY_BELOW_ASSIGNED`
- WHEN both emails are composed
- THEN the ops email body includes the reason
- AND the buyer email body does not mention `Reason` or its values

### Requirement: Email lookup returns only an email, never a name or other user field

The `shared/auth` port used for recipient resolution MUST return `Optional<String>` (an email) and nothing else. `api:billing` MUST NOT import `com.menta.auth.*`.

#### Scenario: ArchUnit forbids a direct auth dependency from billing

- GIVEN the notification adapter in `api:billing`
- WHEN ArchUnit rules run
- THEN no dependency on any `com.menta.auth.*` type is found

#### Scenario: Lookup miss still lets the ops email send

- GIVEN a `userId` with no resolvable email
- WHEN the handler dispatches the event
- THEN the ops email is still sent
- AND the buyer email is skipped without failing the handler

### Requirement: Reason is payload-only, never persisted

`Reason` MUST be carried only in the `billing.PurchaseExceptioned` outbox payload. This change MUST NOT add a `reason` column to `billing_purchases` (D6).

#### Scenario: No schema change persists the reason

- GIVEN this change is applied
- WHEN `billing_purchases`'s schema is inspected
- THEN no `reason` column exists

### Requirement: A payment with no Purchase row still notifies the buyer

When the EXCEPTION trigger point has no `Purchase` row to transition — the payment's target never resolved to a schedulable course/session (`Reason.TARGET_NOT_SCHEDULED`, e.g. a missing/non-Physical payment target) — the normal `MarkPurchaseExceptionUseCase` append cannot run, because it requires an existing row and throws `PaymentNotFoundException` otherwise. A companion mechanism keyed on `PaymentId` alone MUST still notify the buyer and ops in this case (D10).

#### Scenario: Missing Purchase row still produces a notification

- GIVEN a physical payment whose target never resolved to a schedulable course/session, so no `billing_purchases` row was ever created for it
- WHEN the fulfillment path routes this payment to EXCEPTION
- THEN the buyer (resolved from the payment's `userId`) and ops both receive a notification
- AND no `PaymentNotFoundException` escapes to the outbox worker as an unhandled failure

#### Scenario: Payment-level fallback is also redelivery-safe

- GIVEN a payment-level EXCEPTION notification already sent for a `paymentId` with no `Purchase` row
- WHEN the same trigger is redelivered
- THEN no second buyer or ops email is sent for that `paymentId`

### Requirement: Only physical purchases are in scope

Virtual subscription EXCEPTION behavior MUST remain byte-identical: no `billing.PurchaseExceptioned` event, no notification, for the virtual EXCEPTION path.

#### Scenario: Virtual EXCEPTION emits no notification event

- GIVEN a virtual subscription reaches EXCEPTION via `PaymentVerificationService.ensureSubscription`
- WHEN that transition completes
- THEN no `billing.PurchaseExceptioned` outbox row is appended
- AND no buyer or ops email is sent

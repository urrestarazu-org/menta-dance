# Delta for Purchase Exception Notification

## MODIFIED Requirements

### Requirement: Only physical purchases are in scope

Virtual subscription EXCEPTION behavior MUST keep its state transitions and emitted events unchanged: no `billing.PurchaseExceptioned` event, no notification, for the virtual EXCEPTION path. An operational alarm (structured log + metric, see `subscription-fulfillment-alarm`) is added and is not a notification event: it MUST NOT create an outbox row or send any email.

(Previously: "Virtual subscription EXCEPTION behavior MUST remain byte-identical" — wording only; the virtual path now also emits an operational alarm, so byte-identity no longer holds, while transitions and emitted events stay unchanged.)

#### Scenario: Virtual EXCEPTION emits no notification event

- GIVEN a virtual subscription reaches EXCEPTION via `PaymentVerificationService.ensureSubscription`
- WHEN that transition completes
- THEN no `billing.PurchaseExceptioned` outbox row is appended
- AND no buyer or ops email is sent

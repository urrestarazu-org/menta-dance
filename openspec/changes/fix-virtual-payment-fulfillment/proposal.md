# Proposal: fix-virtual-payment-fulfillment

> Close issue #114 by proving, end to end, that an approved virtual payment
> produces Billing's local `Subscription(ASSIGNED)` entitlement without a push
> grant into Virtual.

**Change ID**: `fix-virtual-payment-fulfillment`
**Milestone**: v0.3.0 — Suscripción y aprendizaje virtual E2E
**Bug track**: issue #114
**Related ADRs**: ADR-0021 (Clean Architecture), ADR-0038 (payment webhook
> verification), ADR-0039 (post-payment fulfillment boundaries)
**Related user stories**: US-BILLING-010 scenario 2; US-VIRTUAL-007
**Dependency**: #56 consumes the Billing entitlement but is deliberately not
> part of this change.

---

## 1. Intent

Issue #114 documents a production-breaking path in which a verified virtual
payment reached `Payment.COMPLETED` but every subscription fell into
`FulfillmentStatus.EXCEPTION`. The historical root cause was Billing's
`NotImplementedVirtualAccessGrantPort`: it threw on every push grant and the
verification service correctly preserved financial settlement while degrading
fulfillment.

The repository baseline has since incorporated the architectural correction in
commit `0bd2182` (`fix(billing): reorientar fulfillment post-pago (#123)`):
`VirtualAccessGrantPort` and its placeholder are absent, ADR-0039 establishes a
Billing-owned pull entitlement, and `PaymentVerificationService` activates the
subscription snapshot then calls `assigned()` locally. This SDD therefore
closes the remaining Definition-of-Done gap with a regression-level integration
test of the real webhook worker flow. It must also confirm the current code
continues to reserve `EXCEPTION` for genuine local fulfillment failures, not
for the normal virtual-payment path.

This is intentionally a narrow Billing delivery. It does **not** implement
Virtual's entitlement consumer, caching, or premium-content authorization;
those remain issue #56.

## 2. Scope

### In scope

- Add an application integration test that follows a pre-existing virtual
  checkout/payment through signed webhook receipt, inbox processing, provider
  verification, and subscription persistence.
- Assert that an approved matching provider outcome results in:
  - `PaymentStatus.COMPLETED`;
  - `SubscriptionStatus.ACTIVE`;
  - `FulfillmentStatus.ASSIGNED`; and
  - the plan's course IDs frozen as the subscription snapshot.
- Assert that the normal virtual path performs no cross-module push grant and
  emits no physical-payment outbox event.
- Keep the verified payment financially settled if a genuine local fulfillment
  step cannot be completed; only the subscription may become `EXCEPTION`,
  preserving an auditable recovery state.
- Add or tighten unit coverage only if the integration test exposes a gap in
  `PaymentVerificationService`'s idempotent recovery behavior.

### Out of scope

- Any write, HTTP call, messaging call, or other push mutation from Billing to
  Virtual.
- Virtual's read-side use of `VirtualCourseEntitlementPort`, premium endpoint
  authorization, caching, streaming, and UI changes (issue #56).
- Physical-payment orchestration, capacity assignment, or outbox handler
  changes (delivered separately by #115).
- Database-schema changes: `billing_subscriptions` already persists the
  lifecycle, fulfillment status, and course snapshot required here.

## 3. Architectural Approach

1. The signed webhook remains an untrusted notification only. The existing
   inbox worker reads the provider payment and matches the expected amount,
   currency, external reference, and merchant account before settlement
   (ADR-0038).
2. For `PaymentTarget.Virtual`, Billing finds the checkout-created subscription,
   activates it using the provider-confirmed time, freezes the plan course IDs,
   and persists `assigned()` in its own module transaction.
3. Virtual remains a pull consumer of the shared entitlement contract. It is
   not notified and no adapter may be introduced to grant state externally.
4. A duplicate or late confirmation must be idempotent: it may repair an
   already active subscription that lacks `ASSIGNED`, but it must not alter its
   original dates or course snapshot.
5. A genuine inability to complete the local subscription fulfillment retains
   `Payment.COMPLETED` and records `Subscription.EXCEPTION`; it must never
   masquerade as a successful entitlement.

This preserves the module direction `infrastructure -> application -> domain`.
Billing owns `billing_*` persistence; no cross-module table, repository, SQL
join, or JPA entity may be used. The existing shared contract is read-only from
Virtual's perspective and requires no new cross-module write port.

## 4. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | Given a pending virtual checkout and a valid signed webhook whose provider result is `approved` and matches the expected payment fields, when the webhook worker processes it, then the persisted payment is `COMPLETED` and its subscription is `ACTIVE` + `ASSIGNED`. |
| AC-2 | The assigned subscription contains the course snapshot of the plan at confirmation time. |
| AC-3 | The successful virtual path neither calls a Virtual push-grant adapter nor creates `billing.PhysicalPaymentCompleted` outbox work. |
| AC-4 | Reprocessing the same completed payment is idempotent: it preserves activation dates and snapshot while repairing an active-but-unassigned historical subscription. |
| AC-5 | A genuine local fulfillment failure leaves the payment `COMPLETED` and makes only the subscription `EXCEPTION`; the happy path MUST NOT enter `EXCEPTION`. |
| AC-6 | Billing domain/application architecture tests remain compliant: framework-free domain/application and no foreign-module infrastructure access. |

## 5. Verification

- Targeted red/green test: `./gradlew :api:app:test --tests com.menta.app.integration.billing.PaymentWebhookIntegrationTest --no-daemon`
- Billing behavior/architecture suite: `./gradlew :api:billing:test --no-daemon`
- Final project gate: `./gradlew check --no-daemon`
- Confirm JaCoCo against the currently approved 90% Billing domain/application
  threshold; issue #138 owns the separate policy review and future increase.

No API route, request, response, authentication rule, rate limit, OpenAPI
contract, or Bruno request changes are expected: the existing webhook contract
is exercised unchanged. Therefore no OpenAPI/Bruno update is required for this
change.

## 6. Risks and Rollback

The primary risk is confusing the established pull model with a new push
integration while attempting to satisfy the old issue text. The regression test
must bind the behavior to the ADR-0039 boundary instead: Billing records the
entitlement, and #56 consumes it.

No migration or external provider behavior changes are planned. If the test
exposes a production regression after an implementation adjustment, revert the
single Billing code change while retaining the failing regression test, then
investigate the transactional boundary. Do not compensate with a Virtual write
or by marking the payment failed: financial settlement and fulfillment are
separate state axes.

## 7. Next SDD Phase

Create `specs/billing/spec.md` with RFC 2119 requirements and Given/When/Then
scenarios for AC-1 through AC-6, then produce the design and topologically
ordered, strict-TDD tasks before implementation.

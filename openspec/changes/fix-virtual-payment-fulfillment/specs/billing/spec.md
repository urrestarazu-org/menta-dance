# Billing Delta Specification: Virtual Payment Fulfillment

## Purpose

Close issue #114 with an end-to-end regression proof that Billing records a
locally owned virtual-course entitlement after an approved, verified payment.
The production architecture was corrected in `0bd2182`; this delta specifies
the observable webhook-worker behavior that guards it from regression.

## Requirements

### Requirement: Approved Virtual Payment Completes Local Fulfillment

After the existing signed-webhook inbox worker verifies an approved provider
outcome against the expected merchant account, external reference, amount, and
currency, Billing MUST complete the matching virtual payment and fulfill its
checkout-created subscription locally. The resulting subscription MUST be
`ACTIVE` and have `FulfillmentStatus.ASSIGNED`.

#### Scenario: Verified approved webhook assigns a virtual subscription

- **GIVEN** a pending virtual checkout with its matching payment and
  checkout-created subscription
- **AND** a valid signed webhook whose provider result is `approved` and whose
  authoritative payment details match the expected merchant account, external
  reference, amount, and currency
- **WHEN** the webhook inbox worker processes the notification
- **THEN** Billing persists `PaymentStatus.COMPLETED`
- **AND** persists the matching subscription as `SubscriptionStatus.ACTIVE`
- **AND** persists its fulfillment status as `FulfillmentStatus.ASSIGNED`
- **AND** the normal path does not persist `FulfillmentStatus.EXCEPTION`

### Requirement: Assignment Freezes the Purchased Course Snapshot

On successful virtual fulfillment, Billing MUST preserve the plan's course IDs
as the subscription's purchased snapshot. Later plan edits MUST NOT be required
for, or alter, the outcome being asserted for that completed payment.

#### Scenario: Assignment retains the plan courses at confirmation

- **GIVEN** a virtual plan containing a known set of course IDs and a pending
  checkout-created subscription for that plan
- **WHEN** a matching approved provider payment is processed successfully
- **THEN** the assigned subscription contains that known set as its course
  snapshot

### Requirement: Virtual Fulfillment Is Billing-Owned and Pull-Based

The successful virtual-payment path MUST NOT write to, invoke an adapter for,
or otherwise push a grant into Virtual. It MUST NOT create physical-payment
outbox work such as `billing.PhysicalPaymentCompleted`. Virtual's read-side
consumption of the Billing entitlement remains outside this change and is owned
by issue #56.

#### Scenario: Successful virtual fulfillment has no cross-module push side effect

- **GIVEN** a pending virtual payment that receives a matching approved
  provider outcome
- **WHEN** the webhook inbox worker completes fulfillment
- **THEN** the only entitlement transition asserted is the Billing subscription
  becoming `ACTIVE` and `ASSIGNED`
- **AND** no Virtual push-grant adapter is invoked
- **AND** no physical-payment completion outbox event is persisted

### Requirement: Reprocessing Is Idempotent and Repairs Historical Fulfillment

Processing a duplicate or late approved confirmation for an already completed
virtual payment MUST be idempotent. When an active historical subscription is
not yet assigned, processing MAY repair it to `ASSIGNED`; it MUST preserve the
subscription's original activation dates and frozen course snapshot.

#### Scenario: Duplicate confirmation repairs an active unassigned subscription

- **GIVEN** a completed virtual payment with an `ACTIVE` subscription whose
  fulfillment status is not `ASSIGNED`
- **AND** that subscription already has activation dates and a course snapshot
- **WHEN** the same approved provider confirmation is processed again
- **THEN** the subscription becomes `ASSIGNED`
- **AND** its original activation dates are unchanged
- **AND** its original course snapshot is unchanged
- **AND** no additional payment or subscription is created

### Requirement: Genuine Local Fulfillment Failure Is Auditable

If Billing genuinely cannot persist or assign the local subscription after the
provider outcome has been verified, it MUST retain `PaymentStatus.COMPLETED`
and transition only the affected subscription to `FulfillmentStatus.EXCEPTION`.
It MUST NOT report the financial settlement as failed, and it MUST NOT use
`EXCEPTION` on the normal successful path.

#### Scenario: Local assignment failure preserves settled payment

- **GIVEN** a matching approved provider outcome for a pending virtual payment
- **AND** a genuine local failure while persisting or assigning its subscription
- **WHEN** the worker handles the fulfillment failure
- **THEN** the payment remains `COMPLETED`
- **AND** the subscription is recorded as `EXCEPTION`
- **AND** the failure remains recoverable by the established idempotent path

### Requirement: Billing Boundaries Remain Enforced

The regression coverage and any supporting changes MUST preserve Billing's
Clean Architecture boundaries: `domain` and `application` remain framework
free, and Billing MUST NOT access another module's infrastructure, repository,
entity, SQL schema, or write port.

#### Scenario: Billing architecture suite remains green

- **GIVEN** the virtual-payment fulfillment regression coverage
- **WHEN** the Billing test suite executes
- **THEN** the Billing architecture tests pass
- **AND** no new foreign-module infrastructure dependency is introduced

## Out of Scope

Virtual entitlement consumption and premium-content authorization (issue #56),
all Virtual writes or notifications, physical payment/capacity orchestration,
database migrations, and changes to the existing webhook API contract. No
OpenAPI or Bruno update is required because the existing webhook contract is
exercised unchanged.

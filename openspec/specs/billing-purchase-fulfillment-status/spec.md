# Billing Purchase Fulfillment Status Specification

## Purpose

Let the owner of a payment learn the outcome of the class Purchase created from
it, through a nullable `fulfillment` field on the existing student payment
status endpoint `GET /api/v1/billing/payments/{paymentId}`. The field exposes
the Purchase status enum only, so a student can tell that staff must act
(#559, builds on #552).

## Requirements

### Requirement: Payment status exposes a nullable fulfillment

The response of `GET /api/v1/billing/payments/{paymentId}` MUST include a
`fulfillment` field. When the payment has an associated Purchase row, the
field MUST be the Purchase status, one of `PENDING_FULFILLMENT`, `ASSIGNED`,
`EXCEPTION` or `RESOLVED`. Existing fields of the response MUST keep their
current names, types and values.

#### Scenario: Purchase pending fulfillment

- GIVEN a payment owned by the caller with a Purchase in `PENDING_FULFILLMENT`
- WHEN the caller requests the payment status
- THEN the response has `fulfillment: "PENDING_FULFILLMENT"`

#### Scenario: Purchase assigned

- GIVEN a payment owned by the caller with a Purchase in `ASSIGNED`
- WHEN the caller requests the payment status
- THEN the response has `fulfillment: "ASSIGNED"`

#### Scenario: Purchase in exception

- GIVEN a payment owned by the caller with a Purchase in `EXCEPTION`
- WHEN the caller requests the payment status
- THEN the response has `fulfillment: "EXCEPTION"`

#### Scenario: Purchase resolved

- GIVEN a payment owned by the caller with a Purchase in `RESOLVED`
- WHEN the caller requests the payment status
- THEN the response has `fulfillment: "RESOLVED"`

### Requirement: Fulfillment is null when no Purchase applies

`fulfillment` MUST be `null` (present, not omitted) when the payment is not a
class purchase payment (for example a subscription payment) and when no
Purchase row exists yet for the payment (for example a `COMPLETED` payment
whose asynchronous outbox processing has not created the Purchase). Absence of
a Purchase MUST NOT cause an error response.

#### Scenario: Subscription payment

- GIVEN a payment owned by the caller that belongs to a subscription
- WHEN the caller requests the payment status
- THEN the response is 200 with `fulfillment: null`

#### Scenario: Completed payment without a Purchase row yet

- GIVEN a `COMPLETED` class payment owned by the caller with no Purchase row
- WHEN the caller requests the payment status
- THEN the response is 200 with `fulfillment: null`
- AND the payment `status` is `COMPLETED`

### Requirement: Ownership check is unchanged

The endpoint MUST continue to return `404` when the payment does not exist or
is not owned by the authenticated caller, and the ownership check MUST be
performed on the payment's user. The Purchase MUST NOT be read or disclosed
before ownership is established.

#### Scenario: Another user's payment

- GIVEN a payment owned by user A with a Purchase in `EXCEPTION`
- WHEN user B requests that payment status
- THEN the response is 404
- AND the body contains no `fulfillment` value

#### Scenario: Unknown payment

- GIVEN no payment with the requested id
- WHEN an authenticated user requests it
- THEN the response is 404

### Requirement: Internal Purchase detail is never exposed

The response MUST expose the Purchase status enum only. It MUST NOT contain the
exception reason, the resolution type, the resolution note, or any other
Purchase field, for any payment and any fulfillment value.

#### Scenario: Exception purchase leaks nothing

- GIVEN a payment owned by the caller with a Purchase in `EXCEPTION` that has
  a reason, and a `RESOLVED` Purchase that has a resolution type and note
- WHEN the caller requests each payment status
- THEN the serialized responses contain no `reason`, resolution type or note
  field or value

### Requirement: Read path runs inside the read-only transaction

The Purchase lookup MUST be performed through the transactional decorator of
the get-payment use case so the mandatory-transaction repository call is
satisfied. The module MUST keep its dependency rules: billing MUST NOT import
the physical module and architecture tests MUST pass.

#### Scenario: Wiring resolves the lookup in a transaction

- GIVEN the billing configuration wiring of the get-payment use case
- WHEN the status is requested through the exposed bean
- THEN the Purchase lookup executes without a missing-transaction failure

#### Scenario: Architecture rules hold

- GIVEN the billing module after the change
- WHEN the ArchUnit tests run
- THEN they pass with no billing to physical dependency

### Requirement: OpenAPI documents the student payment status endpoint

`api/openapi/billing-v1.yaml` MUST document `GET /api/v1/billing/payments/{paymentId}`
for the student for the first time: the path parameter, the 200 response schema
(including `fulfillment` as a nullable enum of `PENDING_FULFILLMENT`,
`ASSIGNED`, `EXCEPTION`, `RESOLVED`) and the 404 response. `docs/06-BILLING-API.md`
MUST describe the field and its null rules. A contract test MUST detect drift
between the implementation and the document.

#### Scenario: Endpoint present in the contract

- GIVEN `api/openapi/billing-v1.yaml`
- WHEN the paths are inspected
- THEN `/api/v1/billing/payments/{paymentId}` has a `get` operation with
  responses `200` and `404`
- AND the `200` schema declares nullable `fulfillment` with the four enum values
- AND the schema declares no `reason`, resolution or note property

#### Scenario: Contract drift fails the build

- GIVEN the response DTO gains or loses a field relative to the document
- WHEN the contract test runs
- THEN it fails

# Local Mercado Pago Simulator Specification

## Requirement: Guarded deterministic provider adapters

Billing MUST select local implementations of both `PaymentPreferencePort` and
`PaymentProviderPort` only when the explicit local Mercado Pago E2E profile is
active. It MUST select neither local adapter in prod, production, or staging,
and MUST NOT permit local and real implementations for the same port at once.

#### Scenario: Local checkout avoids the provider network

- GIVEN the local Mercado Pago E2E profile is active
- WHEN a client creates a subscription checkout
- THEN the normal checkout use case MUST persist its normal records
- AND the local preference adapter MUST return deterministic opaque values
- AND no request MUST be sent to Mercado Pago

#### Scenario: Production fails closed

- GIVEN a prod, production, or staging profile is active with the local profile
- WHEN Billing configures provider adapters
- THEN startup MUST fail before accepting checkout or webhook traffic

## Requirement: Real webhook security and processing path

The simulator MUST prepare a current HMAC-signed notification compatible with
the existing Mercado Pago webhook contract. The request MUST pass through the
existing controller, signature verifier, durable inbox and asynchronous worker.
It MUST NOT provide an endpoint or fixture that writes a payment status,
subscription state or inbox status directly.

#### Scenario: Approved notification activates entitlement

- GIVEN a checkout exists and the local simulator prepares a matching approved
  provider result
- WHEN a correctly signed, current webhook is delivered to the real endpoint
- THEN the inbox MUST contain one received row
- AND the existing worker MUST verify it through `PaymentProviderPort`
- AND the normal Billing fulfillment MUST activate the subscription

#### Scenario: Duplicate notification is idempotent

- GIVEN the same provider payment id and request id were accepted once
- WHEN the signed notification is sent again
- THEN the inbox MUST retain one logical row
- AND fulfillment MUST NOT be applied twice

## Requirement: Deterministic outcomes preserve reconciliation behavior

The simulator MUST make approved, pending, rejected and inconsistent provider
results available deterministically. Pending/rejected/inconsistent results MUST
be interpreted only by existing Billing verification/state-machine code.

#### Scenario: Inconsistent provider result is not fulfilled

- GIVEN the provider result mismatches the expected amount, currency, external
  reference or merchant
- WHEN the worker verifies the inbox row
- THEN it MUST NOT activate the subscription
- AND it MUST follow the existing reconciliation outcome

# Proposal: local-mercadopago-simulator

**Change ID:** `local-mercadopago-simulator`
**Feature track:** issue #128 — v0.3.0

## Intent

Provide a deterministic Mercado Pago substitute exclusively for the local E2E
profile. It must exercise the production checkout, signed webhook, durable
inbox, verification worker and fulfillment paths without calling Mercado Pago
or weakening production signature/correlation rules.

## Scope

- Profile-scoped implementations of `PaymentPreferencePort` and
  `PaymentProviderPort` that retain local preference/payment state and return
  deterministic approved, pending, rejected and deliberately inconsistent
  results.
- A local-only mechanism to prepare a signed webhook against the real public
  webhook controller. The controller, `ReceiveWebhookUseCase`, HMAC verifier,
  inbox and worker remain production code paths.
- An E2E Bruno journey for checkout → signed notification → active
  subscription, including duplicate notification and error/reconciliation
  paths.
- Fail-closed profile wiring: local adapters never coexist with production
  Mercado Pago adapters and cannot activate under prod/production/staging.

## Out of scope

- A mock HTTP server, real Mercado Pago credentials/network calls, weakening
  HMAC/timestamp checks, schema changes, payment-domain state-machine changes,
  BFF/browser flows, Bunny.net (#129), or physical fulfilment changes.

## Acceptance criteria

1. The local profile creates a deterministic checkout preference and the real
   Billing checkout persists its normal pending records.
2. A valid, current HMAC-signed local webhook reaches the existing controller;
   the real inbox and worker activate the matching virtual subscription.
3. The simulator can return approved, pending, rejected and mismatched payment
   results; duplicate webhooks are idempotent and mismatches follow the current
   reconciliation path.
4. No local adapter or secret can be selected in prod, production or staging;
   non-local profiles retain the real adapters.
5. Bruno proves the successful path and principal failure/idempotency paths
   without calls to `api.mercadopago.com`.

## Risks and rollback

The primary risk is accidentally changing payment security while making local
tests convenient. The design keeps the real signed-webhook boundary intact and
uses a local deterministic test key only in the guarded profile. Rollback is a
revert of profile wiring and local infrastructure classes; no migration or
persisted production data format is introduced.

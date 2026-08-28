# Tasks: local-mercadopago-simulator

## 1. Profile boundary and deterministic adapter core

### 1.1 Define failing profile-selection tests
- **Owns:** Billing configuration and adapter tests.
- Prove local and real provider adapters are mutually exclusive; production /
  staging plus local profile fails closed.
- **Architecture:** Billing infrastructure only; preserves ADR-0021.

### 1.2 Implement the guarded local provider store and two port adapters
- **Owns:** `api/billing/.../infrastructure/provider/local/` and Billing config.
- Use deterministic opaque identifiers and profile-only in-memory state.
- **Commit:** `feat(billing): add guarded local Mercado Pago adapters`

## 2. Secure local webhook preparation

### 2.1 Specify failing tests for outcome preparation and signatures
- **Owns:** Billing infrastructure tests.
- Prove generated manifests are accepted by the real HMAC verifier, are current,
  and do not change verification semantics.

### 2.2 Add a profile-only preparation boundary
- **Owns:** Billing infrastructure/web configuration and tests.
- Prepare approved/pending/rejected/inconsistent outcomes without direct domain
  or persistence mutation; redact key material from diagnostics.
- **Commit:** `feat(billing): prepare deterministic local payment webhooks`

## 3. HTTP acceptance and documentation

### 3.1 Add Bruno payment journey
- **Owns:** `bruno/E2E/` and `scripts/e2e/`.
- Prove checkout, signed webhook, worker fulfillment, duplicate idempotency and
  an error/reconciliation case using only local endpoints.
- **Commit:** `test(e2e): cover local Mercado Pago checkout journey`

### 3.2 Document and verify
- **Owns:** local E2E/Bruno docs and verification records.
- Run focused Billing tests, Bruno, Compose validation and `./gradlew check`;
  record environmental blockers without weakening security assertions.
- **Commit:** `docs(e2e): document local Mercado Pago simulator`

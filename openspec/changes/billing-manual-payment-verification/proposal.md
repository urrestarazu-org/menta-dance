# Proposal: Admin verification of manual (bank-transfer) payments

**Issue**: #33 (US-BILLING-005, "Verificación de pagos manuales (Admin)") · **Input**: Engram `sdd/billing-manual-payment-verification/explore`

## Intent

An administrator can already resolve a single bank-transfer payment — `POST /api/v1/admin/billing/payments/{paymentId}/approve|reject` shipped with #31 (US-BILLING-003), including subscription activation/cancellation and the `409` on an already-processed payment. What is missing is everything **around** that decision, and without it the flow is not operable:

- there is **no way to find** the payments awaiting verification — no list query, no admin inbox; an admin must already know a `paymentId`;
- there is **no way to look at the proof** — `PaymentProof` records `storageKey`/`contentType`, but the file lives on a local Docker volume with `store`/`delete` only. No read path, no signed URL, no HTTP serving exists **anywhere in the repo**;
- the buyer is **never told** what happened. `SpringMailPaymentProofNotificationAdapter` deliberately emails only a fixed ops mailbox on proof *submission* (D2 of the bank-transfer change); no buyer-facing decision email exists;
- there is **no audit trail**. `billing_audit_log` does not exist, so "quién aprobó qué y por qué" is unanswerable today;
- a payment stuck in `PENDING/RECONCILIATION_REQUIRED` (parked there by `WebhookVerificationWorker` from #30) has **no exit**. `billing_reconciliation_tasks` is an append-only log with no resolution concept, and `Payment` has no transition out of `ReconciliationRequired`.

This change delivers the admin inbox, the proof viewer, the audited exceptional correction, the buyer emails, and the audit log — the operable surface around a decision transition that already works.

## Scope

### In Scope

- **Already done, not re-proposed** (from #31, listed so the boundary is explicit): `Payment`/`PaymentStatus` sealed hierarchy; `Payment.resolveManually(ManualVerificationDecision, Instant)`; the approve/reject endpoints and their subscription activation (`PaymentFulfillmentService.ensure`, `endDate` computation) and cancellation (`.release`); the `409` on an already-processed payment (Escenario 5) and the `400` on a reject without `reason` (Escenario 6); the `/api/v1/admin/**` ADMIN authorization rule. **None of this is re-implemented.**
- **List pending verification** (Escenario 1): new `PaymentRepository` out-port query + `GET /api/v1/admin/billing/payments?status=PENDING&substatus=AWAITING_MANUAL_VERIFICATION`, paginated with **max 50 per page** (issue NFR), ordered oldest-first, carrying user, plan, amount/currency, status/substatus, `hasProof`, `createdAt`.
- **Payment detail with a signed proof URL** (Escenario 2): `GET /api/v1/admin/billing/payments/{paymentId}` returning payment + user + plan + a **15-minute** signed proof URL, plus the new token-authenticated endpoint that actually serves the stored file (D1).
- **Signed-token mechanism** (D1): HMAC-signed token with embedded expiry, deterministic signing + timing-safe comparison, over the **existing local filesystem storage**. Extends `PaymentProofStoragePort` (or a sibling port) with a read path.
- **Audited exceptional correction** (Escenario 7): `POST /api/v1/admin/billing/payments/{paymentId}/corrections` for a payment in `PENDING/RECONCILIATION_REQUIRED`, requiring `reason` + evidence, applying a **new domain transition out of `ReconciliationRequired`**, resolving the tied reconciliation task (D2), and writing an append-only audit entry.
- **`billing_audit_log`** (D4): new JPA entity/repository + adapter mirroring `PhysicalDeviceAuditRepositoryAdapter`. Written on approve, reject, and correction: `payment_id`, `admin_id`, `action`, `reason`, `timestamp`.
- **Buyer-facing decision emails** (Escenarios 3 and 4): new notification port + Spring Mail adapter mirroring `SpringMailActivationNotificationAdapter`, with **distinct approval and rejection templates** (rejection carries the motivo), in Spanish (D5).
- **Migration V24** adding `billing_audit_log` and the `resolved`/`resolved_at`/`resolved_by` columns on `billing_reconciliation_tasks` (D2).
- OpenAPI contract update and Bruno requests for the three new endpoints (issue DoD).

### Out of Scope

- **Re-implementing approve/reject.** Their transition, fulfillment, cancellation, `409` and `400` behaviour are unchanged; this change only *adds* the audit write and the buyer email to their existing use case.
- **Migrating proof storage to S3/MinIO** and native presigned URLs (D1 rejects it for this change). The local volume stays authoritative.
- **Changing `SpringMailPaymentProofNotificationAdapter`.** The ops-mailbox notification on proof submission stays a separate port with its existing behaviour; D2 of the bank-transfer change is preserved, not folded into the new buyer port.
- **Reworking `WebhookVerificationWorker` / #30's task-creation logic.** This change adds a resolution column and reads it; it does not change when or why a task is created.
- Bulk approve/reject, an admin search/filter beyond `status`+`substatus`, proof re-download audit, rate limiting (issue explicitly asks for none for admins), retention/expiry of stored proofs.
- BFF or Android admin surfaces; any student-facing endpoint change.

## Settled decisions

| # | Topic | Decision |
|---|---|---|
| D1 | Signed proof URL mechanism | A **local signed-token endpoint**: an HMAC-signed token with an embedded expiry (15 min), serving the file from the **existing local filesystem storage**. Deterministic signing plus timing-safe comparison, mirroring the physical module's `QrCredentialParser` / `FormatQrCredentialSignatureService` split (parser validates shape, signer recomputes) — with the real HMAC/timing-safe mechanics taken from `:api:billing`'s own `HmacSha256WebhookSignatureVerifier` (`Mac` + `MessageDigest.isEqual`), since the QR signer itself is still a documented non-HMAC placeholder. **NOT migrating to S3/MinIO.** **Resolved with the user — not to be re-litigated.** |
| D2 | Reconciliation task lifecycle | Add a `resolved` / `resolved_at` / `resolved_by` column set to `billing_reconciliation_tasks` via a **new Flyway migration (V24**, next available after V23`__physical_devices`**)**. The corrections endpoint **explicitly resolves** the reconciliation task tied to the payment; the table stops being purely append-only and gains a queryable open/resolved state. **Resolved with the user — not to be re-litigated.** |
| D3 | Admin route naming (**diverges from the issue's literal text**) | All new endpoints use **`/api/v1/admin/billing/payments/...`**, matching the already-shipped approve/reject prefix — NOT the issue's literal `/api/v1/billing/admin/payments`. Reason: route consistency across the whole admin API surface (`/api/v1/admin/billing/subscriptions`, `/api/v1/admin/physical/devices`), and the existing `/api/v1/admin/**` ADMIN rule covers it with no new matcher. This is a **deliberate, documented divergence** — same handling as #44's D1 (SHA-256 vs. the issue's literal bcrypt) and #45's D8 (retroactive backfill vs. the literal Escenario 5): call it out in the PR body and the US doc, never silently absorbed. **Resolved with the user — not to be re-litigated.** |
| D4 | Audit log shape | One table **`billing_audit_log`** (the issue's own name), append-only, columns `id`, `payment_id`, `admin_id`, `action`, `reason` (nullable), `created_at`. Written through a thin `@Component` adapter with an `append(...)` method, mirroring `PhysicalDeviceAuditRepositoryAdapter` / `VirtualCourseAuditRepositoryAdapter` — deliberately **not** the physical/virtual `previousValue`/`newValue` diff shape, because the audited facts here are a decision plus its motivo, not a field mutation. Corrections additionally record the submitted evidence. |
| D5 | Email content language | Buyer-facing approval/rejection emails are **in Spanish**, per CLAUDE.md (code in English, UI/user-facing messages in Spanish) and consistent with the existing activation email. Two distinct templates; the rejection template includes the admin's `reason` verbatim. Class, method, and property names stay English. |
| D6 | Signing secret configuration | The proof-token secret is a **configuration property** (`@Value`-injected, same shape as the webhook secret and the mail `from-address`), not a hardcoded constant and not a new secrets backend. An absent/blank secret must fail fast at startup rather than sign with an empty key. |
| D7 | Lombok in new code | New mutable/constructor-injected classes in this change (use cases, adapters, JPA entities) use Lombok (`@RequiredArgsConstructor`, `@Getter`/`@Setter`) per CLAUDE.md's Boilerplate rule (effective 2026-09-27). Immutable DTOs/value objects keep `record`. Existing billing classes are **not** retrofitted. |
| D8 | Email-failure transaction boundary | The buyer decision email is sent **outside** the transaction that commits the payment/subscription state change, and its failure **never** rolls back an approval or rejection. Approve/reject/correction stand as soon as their domain transition and audit write commit; email delivery is best-effort (log-and-continue on failure, no automatic retry queue in this change). **Resolved with the user — not to be re-litigated.** |
| D9 | Correction semantics (Escenario 7) | A correction is a **status transition**, not a data edit: the admin resolves a `PENDING/RECONCILIATION_REQUIRED` payment into a terminal state (`COMPLETED`-like or `REJECTED`-like, chosen by the admin) with mandatory `reason` + evidence — the same shape as `resolveManually`, applied to this payment's different starting state. Adjusting payment *data* (amount, provider reference) without a status transition is explicitly **out of scope**. **Resolved with the user — not to be re-litigated.** |

## Capabilities

### New Capabilities

- `billing-manual-payment-verification`: the admin verification surface — paginated listing of payments awaiting manual verification, payment detail with a time-limited signed proof URL and the token-authenticated file endpoint, the audited exceptional correction out of `RECONCILIATION_REQUIRED` with its reconciliation-task resolution, and the append-only `billing_audit_log`.

### Modified Capabilities

- `bank-transfer-subscription`: two requirement-level changes. (1) *Admin resolution of a pending proof (D1)* — approving or rejecting now MUST additionally write an audit entry (admin, action, motivo, timestamp) and MUST send the buyer a decision email; the transition, `409`, and `400` behaviour are unchanged. (2) *Proof storage is not publicly reachable* — the "no public reachability" requirement gains its one sanctioned exception: a valid, unexpired, HMAC-signed token. An absent, tampered, or expired token still fails.

## Approach

Extend `PaymentAdminController` with the three new endpoints rather than introducing a second admin controller, since the prefix, the security rule, and the exception handler already exist there. Each endpoint gets its own use case behind the existing port structure; `ResolvePaymentProofUseCaseImpl` gains only two outbound calls (audit append, buyer notification), leaving its transition and fulfillment logic untouched.

The corrections flow is a **net-new domain transition** on `Payment` out of `ReconciliationRequired`, shaped like `resolveManually` (same exception type for a wrong current state → `409` via the existing `PaymentExceptionHandler`), so no new error-mapping infrastructure is needed.

Signed proof access splits in two, mirroring the QR precedent: a signer that produces `token = payload + HMAC(payload)` with an embedded expiry, and a parser/verifier that validates shape, recomputes with `Mac`, compares with `MessageDigest.isEqual`, and then checks expiry. The serving endpoint reads the file through the storage port; the domain layer never sees a URL, a secret, or a filesystem path (ADR-0021, ArchUnit-enforced).

Per strict TDD, every rejection branch (expired token, tampered token, wrong payment state, non-admin, empty page) is written as a failing test first. Test-first discipline is not optional here: `:api:billing` gates at **95% domain+application / 90% infrastructure**.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/billing/.../domain/model/Payment.java` | Modified | New correction transition out of `ReconciliationRequired` (shape of `resolveManually`) |
| `api/billing/.../domain/model/PaymentStatus.java` | Modified | Correction transition target states; Javadoc note that #33's deferred scope is now delivered |
| `api/billing/.../infrastructure/web/controller/PaymentAdminController.java` | Modified | List, detail, corrections endpoints (D3 prefix); approve/reject untouched |
| `api/billing/.../application/usecase/ResolvePaymentProofUseCaseImpl.java` | Modified | Audit append + buyer notification only; transition/fulfillment unchanged |
| `api/billing/.../application/port/out/PaymentRepository.java` + `PaymentJpaRepository`/adapter | Modified | Paginated pending-verification query (max 50/page) |
| `api/billing/.../application/port/out/PaymentProofStoragePort.java` + `LocalFilesystemPaymentProofStorageAdapter.java` | Modified | Read path for serving the stored file |
| `api/billing/.../infrastructure/proof/` (new) | New | Proof-token signer + parser/verifier (D1, D6) and the token-authenticated serving endpoint |
| `api/billing/.../application/port/out/` + `.../infrastructure/notification/` | New | Buyer decision-notification port + Spring Mail adapter, two Spanish templates (D5) |
| `api/billing/.../infrastructure/persistence/` (audit) | New | `billing_audit_log` entity/repository/adapter (D4) |
| `api/billing/.../infrastructure/persistence/entity/ReconciliationTaskJpaEntity.java` + repository | Modified | `resolved`/`resolvedAt`/`resolvedBy` + resolve/query support (D2) |
| `api/app/src/main/resources/db/migration/V24__*.sql` | New | `billing_audit_log` + reconciliation-task resolution columns |
| `api/auth/.../infrastructure/security/SecurityConfig.java` | Unchanged/Verify | `/api/v1/admin/**` already covers the three new endpoints; the **token-served proof endpoint is not under `/admin/**`** and needs its own explicit rule — verify, do not assume |
| `api/openapi/billing-v1.yaml`, `bruno/API - Direct/billing/` | Modified | Contract + requests for the three endpoints (issue DoD) |
| `docs/user-stories/US-BILLING-005.md` | Modified | Lift from Draft; record D3's divergence from its literal route wording |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| The proof-serving endpoint is the first non-admin-authenticated billing route; a misplaced security rule leaves proofs publicly readable | High | Explicit `SecurityConfig` matcher for exactly that path; integration tests assert anonymous-without-token → `401/403`, tampered token → `403`, expired token → `403`; the `bank-transfer-subscription` delta spec states the exception precisely |
| Signed-token mechanism has **zero precedent for file access** in this repo | High | D1 fixes the mechanism; `HmacSha256WebhookSignatureVerifier` supplies proven HMAC + timing-safe mechanics; signer/verifier are unit-tested independently of HTTP |
| Adding a resolution column changes `billing_reconciliation_tasks`' meaning for #30's worker | Med | Columns are nullable/defaulted so existing rows and the worker's inserts stay valid; the worker's code is explicitly out of scope; a backfill is not required (pre-existing rows read as unresolved) |
| Coverage gate is stricter here: 95% domain+application / 90% infrastructure | Med | Test-first per strict TDD; every rejection branch gets an explicit case; the mail adapter and file-serving adapter are the likeliest infrastructure gaps and need deliberate tests |
| Audit write or email failure rolls back an otherwise-valid approval | Med | D8: the audit append is in-transaction (`REQUIRED`, like the physical/virtual audit adapters); email delivery runs outside that transaction and its failure never undoes a completed payment |
| A reviewer expects the issue's literal `/api/v1/billing/admin/payments` | Med | D3 records the divergence with rationale here, in the US doc, and in the PR body — same handling as #44's D1 and #45's D8 |
| Buyer email accidentally routed through the ops-mailbox adapter, violating the bank-transfer change's D2 | Med | Separate port, separate adapter, separate templates; a test asserts the ops adapter is not invoked on approve/reject |
| 400-line review budget | High | `sdd-tasks` forecasts; likely chained slices (audit log + migration → list → detail + signed token → corrections → emails/contract) |

## Rollback Plan

1. Revert the merge commit. The three new endpoints disappear; approve/reject — never structurally altered (Approach) — returns to its pre-change behaviour (no audit write, no buyer email).
2. Migration V24 is **additive only** (a new table plus nullable columns), so a revert of application code leaves a schema that is forward-compatible and harmless. Dropping it is optional and should be a separate, deliberate step; `billing_reconciliation_tasks` keeps working with its resolution columns simply unread.
3. Audit rows already written survive the revert and remain readable — they are the record of decisions that genuinely happened and must not be discarded.
4. Payments already moved out of `RECONCILIATION_REQUIRED` by a correction stay in their new terminal state; the transition is not reversed by a code revert, and their audit entries explain them.
5. Signed proof tokens issued before the revert become unservable once the endpoint is gone — acceptable, since they expire in 15 minutes anyway.
6. The proof-token secret property becomes unused; leave it configured (an unused property is inert) rather than removing it from environments during a rollback.

## Dependencies

- None blocking. No new library, external service, or module edge — `JavaMailSender`, `Mac`/`MessageDigest`, Flyway, and Spring Data pagination are all already in use in `:api:billing` or `:api:auth`.
- One **new configuration property** (the proof-token signing secret, D6) must be present in every environment before deploy; startup fails fast if it is absent.
- Builds on #31 (approve/reject, archived) and #30 (`billing_reconciliation_tasks`, whose table this change alters); neither is reopened.
- `docs/user-stories/US-BILLING-005.md` is still `Estado: Draft` and states the literal route D3 diverges from; it should be updated in this change rather than left contradicting the code.

## Success Criteria

- [ ] `GET /api/v1/admin/billing/payments?status=PENDING&substatus=AWAITING_MANUAL_VERIFICATION` returns `200` with oldest-first, paginated results (user, plan, amount, status/substatus, `hasProof`, `createdAt`) and rejects or clamps a page size above 50.
- [ ] `GET /api/v1/admin/billing/payments/{paymentId}` returns `200` with payment, user, plan, and a signed proof URL that serves the stored file.
- [ ] That signed URL works within 15 minutes, and is rejected after expiry, when tampered, and when absent — asserted by test on all three.
- [ ] A non-admin receives `403` on all three new endpoints; an anonymous caller receives `401/403`.
- [ ] Approving sends the buyer a Spanish confirmation email; rejecting sends a distinct rejection email carrying the motivo; the ops-mailbox adapter is not invoked by either.
- [ ] Approve, reject, and correction each write one `billing_audit_log` row with `payment_id`, `admin_id`, `action`, timestamp, and `reason` where applicable.
- [ ] `POST .../{paymentId}/corrections` on a `PENDING/RECONCILIATION_REQUIRED` payment with reason + evidence applies the authorized correction, marks the tied reconciliation task resolved with `resolved_by`/`resolved_at`, and audits it; on any other payment state it returns `409`.
- [ ] Approve/reject transition, subscription activation/cancellation, the `409` on an already-processed payment, and the `400` on a missing reject reason are unchanged, with their existing test assertions untouched.
- [ ] OpenAPI contract and Bruno requests updated; `./gradlew check` passes, including ArchUnit and the billing coverage gate (95% domain+application / 90% infrastructure).

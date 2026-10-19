# Proposal: Subscribe by Bank Transfer with Manual Verification (Issue #31, US-BILLING-003)

## Intent

The domain was pre-wired for this flow and then deliberately sealed off. `PaymentMethod.BANK_TRANSFER`
exists (`PaymentMethod.java:16`) and `PaymentStatus.AwaitingManualVerification` exists
(`PaymentStatus.java:45`, already persisted as `AWAITING_MANUAL_VERIFICATION` in
`billing_payments.status_type VARCHAR(30)`), but every entry point rejects the method:
`CreateSubscriptionCheckoutUseCaseImpl.java:75-79` throws
`IllegalArgumentException("Checkout Pro requires MERCADO_PAGO")` with the comment *"Bank transfer is a
separate flow (US-BILLING-003) and must never accidentally open an MP preference"*.

Consequence today: a student without a card — the ordinary case for bank transfer in Argentina — cannot
subscribe at all, and **no code path can ever produce a payment in `AwaitingManualVerification`**. This
change opens that flow end to end: create the subscription, hand back bank details, receive a proof,
notify operations, let an admin resolve it, and expire it if nobody does.

## Scope

### In Scope

Seven BDD scenarios — the six from issue #31 plus D1:

1. `POST /api/v1/billing/subscriptions` with `paymentMethod: BANK_TRANSFER` → `201`, `Subscription`
   `PENDING` + `Payment` `AwaitingManualVerification`, response carries bank details (CBU, alias,
   holder, CUIT, amount, reference).
2. `POST /api/v1/billing/payments/{paymentId}/proof` (multipart) → stores the file outside any public
   path, notifies operations, `200`.
3. Invalid proof (not PNG/JPG/JPEG/PDF, or > 5 MB) → `400` `application/problem+json`.
4. Re-upload for the same payment replaces the previous proof and re-notifies → `200`.
5. `GET /api/v1/billing/payments/{paymentId}` → status, `createdAt`, `updatedAt`, `200`.
6. Sweep: `AwaitingManualVerification` with no proof for > 72 h → `Payment` `Expired` **and** the
   associated `PENDING` `Subscription` cancelled, in the same transaction.
7. **(D1)** An admin approves or rejects an uploaded proof, moving the payment to `Completed` or
   `Rejected` and the subscription accordingly.

Plus the supporting machinery: new `billing_payment_proofs` table (V21 — latest migration on disk is
`V20_1`), a storage port/adapter over a Docker volume, a new Redis rate-limit port (10 bank-transfer
payments/user/day, 3 uploads/payment), owner-only authorization, and new `SecurityConfig` matchers.

### Out of Scope

- **One-year retention purge of proofs.** It is a data-lifecycle job with its own operational and legal
  shape (what "post-verification" means, what is deleted vs. anonymized); bolting it onto the first
  slice would double the sweep surface for zero user-visible value on day one.
- **Any other payment method**, including in-person/presential transfer (#36).
- **Admin listing/queue of pending verifications** (#33) — D1 adds the *resolution* endpoint only, not
  a browsing surface.
- Refunds, partial payments, and any change to the Mercado Pago Checkout Pro path.

## Capabilities

### New Capabilities

- `bank-transfer-subscription`: creating a subscription paid by bank transfer, submitting and replacing
  a payment proof, reading payment status, admin approval/rejection, and 72 h expiry.

### Modified Capabilities

- `billing-subscriptions`: `POST /billing/subscriptions` no longer rejects `BANK_TRANSFER`; a `PENDING`
  subscription gains a system-initiated cancellation path it does not have today.

## Locked decisions (confirmed with the product owner — do not reopen)

- **D1 — Admin approval/rejection is in scope, as a 7th scenario.** Issue #31 as written stops at
  "notify the admin". Without a resolution endpoint, `AwaitingManualVerification` has no exit except
  the 72 h expiry, so a legitimate transfer could only be confirmed by editing the database by hand.
  Verified in code: `PaymentStatus.Pending.resolve(...)` (`PaymentStatus.java:30`) only accepts a
  `ProviderOutcome` — there is no manual-resolution transition at all today. The concrete endpoint
  contract is an **open design question** (see below), not a locked decision.
- **D2 — Notification identity and authorization identity are two different mechanisms.**
  *Notification* ("a proof is waiting") follows the #209 precedent: one fixed, configured operations
  mailbox, exactly as `SpringMailPurchaseExceptionNotificationAdapter.java:49` injects
  `${billing.purchase-exception.ops-address:ops@menta.local}`. *Authorization* for the D1 endpoint uses
  the existing `ROLE_ADMIN` mechanism — `SubscriptionAdminController.isAdmin(Authentication)`
  (lines 85-89) as the application-boundary check, plus `SecurityConfig`'s generic
  `/api/v1/admin/**` → `hasRole("ADMIN")` rule (line 258). No new role. Access to the ops mailbox
  grants nothing: only an authenticated `ROLE_ADMIN` principal may approve or reject.
- **D3 — A dedicated use case, not a branch inside `CreateSubscriptionCheckoutUseCaseImpl`.** The
  transfer flow opens no Mercado Pago preference, needs no `PaymentPreferencePort`, and returns a
  different payload (bank details instead of a checkout URL). Folding it into a method that is
  currently linear and already test-locked would make both paths harder to read. The existing test
  `bank_transfer_is_rejected_before_it_can_be_sent_to_checkout_pro`
  (`CreateSubscriptionCheckoutUseCaseImplTest`) must be updated to assert the *routing* boundary
  rather than a flat rejection; the identical test in `CreatePhysicalPurchaseCheckoutUseCaseImplTest`
  stays untouched (physical transfer is #36).

## Approach

| Layer | Move |
|---|---|
| Domain | Add the missing transitions only. `Subscription.cancel(...)` (line 246) **hard-requires `ACTIVE`** and throws `IllegalStateException("cannot cancel a subscription that is not ACTIVE")` — verified — so scenario 6 cannot reuse it. A `PENDING`-specific transition (or a widened `cancel`) is required. `Payment` needs a manual approve/reject transition that does not go through `ProviderOutcome`. |
| Application | New `CreateBankTransferSubscriptionUseCase` (D3), `SubmitPaymentProofUseCase`, `GetPaymentUseCase`, `ResolvePaymentProofUseCase` (D1), plus out-ports for proof storage, ops notification, and rate limiting. |
| Infrastructure | New `PaymentController` (`/api/v1/billing/payments/...`, owner-scoped) and an admin route under the existing `/api/v1/admin/billing/...` prefix; filesystem storage adapter over a Docker volume; `PaymentExpiryReconciler`/`PaymentExpiryWorker` modeled on `SubscriptionExpiryReconciler` (`@ConditionalOnProperty` on the class, `@Scheduled(fixedRateString=...)`, dispatch to a separate `REQUIRES_NEW` worker bean — never a self-invoked `@Transactional` method). |
| Persistence | `V21__billing_payment_proofs.sql`. `billing_payments` and `billing_subscriptions` already exist (`V8`, `V14`) and need no change. |

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `billing/domain/model/Subscription.java` | Modified | System-initiated cancellation of a `PENDING` subscription (today `cancel` refuses anything not `ACTIVE`) |
| `billing/domain/model/Payment.java` | Modified | Manual approve/reject transition out of `AwaitingManualVerification` |
| `billing/domain/model/PaymentProof.java` | New | Proof aggregate/value object (filename, content type, size, storage key, uploadedAt) |
| `billing/application/usecase/CreateBankTransferSubscriptionUseCase*.java` | New | D3 |
| `billing/application/usecase/{SubmitPaymentProof,GetPayment,ResolvePaymentProof}UseCase*.java` | New | Scenarios 2-5, 7 |
| `billing/application/usecase/CreateSubscriptionCheckoutUseCaseImpl.java` | Modified | Lines 75-79: reject → route |
| `billing/application/port/out/{PaymentProofStoragePort,PaymentProofNotificationPort,BankTransferRateLimitPort}.java` | New | Storage, ops mail, Redis limits |
| `billing/infrastructure/web/controller/PaymentController.java` | New | Owner-scoped via `UUID.fromString(authentication.getName())`, never a client-supplied id |
| `billing/infrastructure/web/controller/PaymentAdminController.java` | New | D1 endpoint under `/api/v1/admin/billing/...` |
| `billing/infrastructure/web/controller/PaymentExceptionHandler.java` | New | `application/problem+json`, sibling of `SubscriptionExceptionHandler` |
| `billing/infrastructure/storage/LocalFilesystemPaymentProofAdapter.java` | New | Docker volume; no new Gradle dependency (`spring-boot-starter-web` already provides `MultipartFile`) |
| `billing/infrastructure/notification/SpringMail…ProofNotificationAdapter.java` | New | D2, mirrors `SpringMailPurchaseExceptionNotificationAdapter` |
| `billing/infrastructure/security/RedisBankTransferRateLimitPort.java` | New | Lua `INCR`+`EXPIRE`, fail-closed, idiom of `RedisBillingPlansRateLimitPort` |
| `billing/infrastructure/scheduling/PaymentExpiry{Reconciler,Worker}.java` | New | Scenario 6 |
| `auth/infrastructure/security/SecurityConfig.java` | Modified | Matchers for `POST/GET /api/v1/billing/payments/**` (authenticated); the admin route is already covered by `/api/v1/admin/**` |
| `api/app/src/main/resources/db/migration/V21__billing_payment_proofs.sql` | New | Only new table |
| `docker-compose.yml` / `infra/docker/**`, `application-*.yml` | Modified | Proof volume, bank details, ops address, sweep + rate-limit properties |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Uploaded proof reachable over HTTP, or a path-traversal filename escaping the volume | Med | Storage key generated server-side from `PaymentId`; original filename never used as a path; volume mounted outside any static-resource root; spec must assert an unauthenticated fetch attempt fails |
| Scenario 6 quietly skipped because `Subscription.cancel` throws on `PENDING` | High | Verified real today. Treated as explicit domain work, not a sweep detail; an open design question owns the shape |
| Content type trusted from the client `Content-Type` header only | Med | Validate declared type **and** magic bytes; reject on mismatch |
| Sweep and admin approval race on the same payment at the 72 h boundary | Med | Worker runs `REQUIRES_NEW` and re-reads state inside the transaction; approval of an already-`Expired` payment must fail loudly, not silently resurrect a subscription |
| Rate limits fail-open if Redis is down, allowing unbounded uploads | Med | Fail-closed like `RedisBillingPlansRateLimitPort` (`BillingDegradedException`) |
| Disk fills with proofs (no retention job in this slice) | Med | Accepted for the MVP; 5 MB cap × 3 uploads/payment × 10 payments/day/user bounds worst-case growth; retention deferred by scope |
| Coverage gate: billing is 85 % domain+application / 85 % infrastructure | Low | New use cases and the storage adapter need coverage in the same slice |
| Widest change in this proposal touches `SecurityConfig`, owned by `api:auth` | Low | Additive matchers only, before the generic `/api/v1/admin/**` rule; no ArchUnit boundary crossed (`billing` never imports `com.menta.auth.*`) |

## Rollback Plan

Revert the PR. The change is additive at the schema level: `V21` drops with a compensating
`DROP TABLE billing_payment_proofs`, and `billing_payments`/`billing_subscriptions` are untouched, so
no existing row needs repair. Rows already written in `AwaitingManualVerification` become unreachable
by the reverted code — before reverting, either let the sweep expire them or resolve them manually,
otherwise they linger as `PENDING` subscriptions with no exit. The feature can also be blunted without
a revert: set `billing.bank-transfer.enabled=false` (route rejects as it does today) and disable the
sweep via its `@ConditionalOnProperty` switch, mirroring `billing.subscription.expiry.enabled`.

## Dependencies

- None blocking. #131/#167 (expiry sweep precedent) and #209 (ops-mailbox notification precedent) are
  merged.
- Requires a writable Docker volume for proofs and SMTP configuration in the target environment (SMTP
  is already required by account activation and #209).

## Success Criteria

- [ ] A `BANK_TRANSFER` subscription request returns `201` with usable bank details and leaves exactly
      one `Payment` in `AwaitingManualVerification` plus one `PENDING` `Subscription`.
- [ ] A stored proof is unreachable without authentication, and unreachable by a non-owner.
- [ ] A second upload for the same payment replaces the first and re-notifies; a fourth is rejected.
- [ ] An 11th bank-transfer payment for the same user on the same day is rejected.
- [ ] An admin approval moves the payment to `Completed` and the subscription out of `PENDING`; a
      rejection is equally terminal and observable through `GET /payments/{paymentId}`.
- [ ] After 72 h with no proof, the payment is `Expired` **and** its subscription is cancelled — proven
      by an integration test asserting both rows in one sweep tick.
- [ ] A non-admin principal cannot reach the D1 endpoint, and access to the ops mailbox grants no
      approval capability (D2).
- [ ] The Mercado Pago Checkout Pro path is behaviourally byte-identical to today.

## Open design questions (for `sdd-design` — deliberately unresolved here)

1. **Shape of the D1 endpoint.** One endpoint carrying the action in the body
   (`POST /api/v1/admin/billing/payments/{paymentId}/verification` with `{ "decision": "APPROVED" | "REJECTED", "reason": ... }`),
   or two endpoints (`.../approve` and `.../reject`)? The repo has no precedent either way;
   `SubscriptionAdminController` uses verb-per-resource (`DELETE`, `POST /trial`). A rejection reason is
   probably mandatory by analogy with admin cancellation — confirm.
2. **Origin of the bank details.** One static configured account for the whole academy (mirroring the
   `merchantAccountId` `@Value` pattern), or per-plan? Issue #31 does not say and no value object or
   config exists today. Static is the smaller move; per-plan is harder to undo later.
3. **`PENDING` cancellation in the domain.** Widen `Subscription.cancel(...)` to accept `PENDING`, or
   add a distinct transition (e.g. `abandon`/`expirePending`) so the `ACTIVE`-only invariant that
   US-BILLING-011/012 rely on stays intact? Related: what actor is recorded in `Cancellation` when the
   canceller is the sweep and not a human.
4. **Whether admin resolution reuses `Payment.applyProviderOutcome` or gets its own transition.** The
   sealed `Pending.resolve(ProviderOutcome, Instant)` shape assumes a provider; a manual decision has
   no provider payload.
5. **Final class, property, and table-column names**, including the storage-key scheme and whether
   proof metadata lives in its own aggregate or hangs off `Payment`.

## Related issues (not absorbed by this change)

- **#33 — admin verification of manual payments.** D1 adds only the single-payment resolution
  endpoint this flow needs to be usable at all. #33 is the operator-facing surface (listing, filtering,
  queue, audit view) and remains a separate backlog story with its own scope.
- **#36 — presential/in-person transfer.** A different purchase target (physical purchases) whose
  checkout use case has its own `BANK_TRANSFER` rejection and its own test; this change deliberately
  leaves that one in place.

Both are tracked independently on the board; absorbing them here would turn one reviewable slice into
three unrelated ones.

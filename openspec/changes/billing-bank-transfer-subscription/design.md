# Design: Subscribe by Bank Transfer with Manual Verification

## Technical Approach

Four use cases behind one router, one new aggregate, one new table, and one sweep pair —
no new module, no new Gradle dependency, no change to the Mercado Pago code path.

`POST /billing/subscriptions` keeps its single in-port. A new
`RoutingCreateSubscriptionCheckoutUseCase` implements `CreateSubscriptionCheckoutUseCase` and
dispatches on `paymentMethod`, so `CreateSubscriptionCheckoutUseCaseImpl` is reached byte-identically
for `MERCADO_PAGO` and keeps its own `IllegalArgumentException` guard as an internal invariant (D3).

`Payment` gains two transitions and `PaymentProof` becomes its own aggregate. The admin resolution and
the 72 h sweep converge on the fulfillment logic that already exists inside `PaymentVerificationService`,
extracted into a shared collaborator so an approved transfer activates a subscription through exactly
the same code an approved card payment does.

Three verified findings reshape the proposal's plan and are recorded as C3, C9 and C10 below.

## Architecture Decisions

### C1 — Two admin endpoints (`/approve`, `/reject`), rejection reason mandatory

`POST /api/v1/admin/billing/payments/{paymentId}/approve` (no body) and
`POST /api/v1/admin/billing/payments/{paymentId}/reject` (`{"reason": "..."}`, `@NotBlank`).

| Option | Tradeoff | Decision |
|---|---|---|
| One endpoint, `decision` in body | Needs a conditionally-mandatory `reason` (required only when `REJECTED`) — the one shape bean validation handles worst, pushing the check into the use case | Rejected |
| Two endpoints | Each has a fixed, statically-validated body; the audit surface is one path per outcome | **Chosen** |

The decisive precedent is not `SubscriptionAdminController`'s verb-per-resource style alone — it is that
the repo already faced this exact conditional-reason problem and answered it by splitting paths:
`DELETE /subscriptions/me` takes no body, `DELETE /admin/billing/subscriptions/{id}` takes a `@Valid`
mandatory `reason` (`SubscriptionAdminController:52-63`). Both drive the same `Subscription.cancel`,
whose `reason` is mandatory precisely when the actor is not the owner. An admin resolving another
user's money is that same case, so **reason is mandatory on reject and absent on approve**.

### C2 — One statically configured bank account for the whole academy

`billing.bank-transfer.account.{cbu,alias,holder,cuit}` injected as a `BankAccountDetails` record into
`CreateBankTransferSubscriptionUseCaseImpl`'s constructor — the same `@Value`-into-constructor shape
`merchantAccountId` already uses (`CreateSubscriptionCheckoutUseCaseImpl:63`).

Per-plan accounts are rejected: neither issue #31 nor the proposal states a business reason for more
than one account, and a per-plan account needs a column on `billing_plans`, an admin editing surface,
and a migration — none of which can be removed later without touching data. Static config is the
smaller move and promoting it to per-plan later costs one column plus a fallback, with no client
contract in the way.

Two derived fields, both reusing existing correlation machinery:

- **reference** = `SUB-{paymentId}`, the same `EXTERNAL_REFERENCE_PREFIX` value
  `CreateSubscriptionCheckoutUseCaseImpl:51` already generates, already carrying
  `uq_billing_payments_external_reference`. The student quotes it on the transfer; ops finds the row.
- **`expectedMerchantAccountId`** (`NOT NULL` since V8) = the configured **CBU**, not the MP merchant id.
  This is a safety property, not a filler: if an MP webhook ever reached this payment,
  `Payment.matchesExpected` compares merchant ids, a CBU never equals an MP merchant id, and the
  payment routes to `ReconciliationRequired` instead of being silently completed by the wrong rail.

### C3 — No new `Subscription` transition is needed: `cancelled()` already is one

The proposal and the `billing-subscriptions` delta both assume `Subscription` can only be cancelled
from `ACTIVE`. Verified against the whole file: **false**.

```java
// Subscription.java:218 — already exists, already used by PaymentVerificationService
public Subscription cancelled() {
    if (!status.occupiesUserSlot()) { return this; }   // occupiesUserSlot() == PENDING || ACTIVE
    return copy(SubscriptionStatus.CANCELLED, ...);     // cancellation left untouched
}
```

`SubscriptionStatus.occupiesUserSlot()` (line 47) is `PENDING || ACTIVE`, so `cancelled()` already
performs `PENDING → CANCELLED`, already idempotent, already distinct from the `ACTIVE`-only
`cancel(by, reason, at)`. It is the transition escenario 6 and the reject path need, and
`PaymentVerificationService.releaseFulfillment` already calls it for exactly this situation
(a terminal non-settled payment releasing a `PENDING` subscription).

| Option | Decision |
|---|---|
| Widen `cancel(...)` to accept `PENDING` | Rejected — would weaken the `ACTIVE`-only invariant US-BILLING-011/012 depend on, for a capability that already exists |
| Add `abandon()` / `expirePending()` | Rejected — a third synonym for a transition `cancelled()` already performs; two methods reaching `CANCELLED` from `PENDING` is a future bug |
| **Reuse `cancelled()`** | **Chosen** — zero domain change |

**Actor recorded for the sweep: none, deliberately.** `cancelled()` leaves `cancellation` null.
`Cancellation(at, by, reason)` types `by` as a `UUID` user id with no system sentinel, and minting a
fake "system user" UUID would put a non-existent principal in an audit trail whose entire purpose is
naming a human. The absence of a `Cancellation` is itself the signal that no human decided. Who
cancelled and why is recoverable from the `Payment`: `Expired` for the sweep, `Rejected` for an admin.

*Spec correction for `sdd-tasks`*: the `billing-subscriptions` delta's parenthetical ("no path existed
to cancel a non-`ACTIVE` subscription") is inaccurate. The requirement itself stands and is satisfied;
it becomes a **characterization test** locking `PENDING → CANCELLED`, not production work.

### C4 — Manual resolution gets its own transition; it never fabricates a `ProviderOutcome`

`PaymentStatus.Pending.resolve(ProviderOutcome, Instant)` is a `default` method on the sealed `Pending`
interface that switches on `outcome.providerStatus()`. Reusing it would require synthesizing a
`ProviderOutcome` from the payment's *own* expected fields so that `matchesExpected` passes — a
tautological check that would put a fake provider status into the row that US-BILLING-002's matching
discipline exists to keep honest. Rejected outright.

Two new instance methods on `Payment`, following the `Purchase.assigned()`/`Purchase.exception()`
idiom already used in this module — instance method with a state guard, not a static factory (static
factories in this codebase mean *birth*, like `Payment.awaitingProvider`):

```java
// Manual admin decision (D1). Loud on any non-AwaitingManualVerification status.
public Payment resolveManually(ManualVerificationDecision decision, Instant at) {
    if (!(status instanceof PaymentStatus.AwaitingManualVerification)) {
        throw new IllegalPaymentStateTransitionException(id, status, decision);
    }
    return withStatus(decision == ManualVerificationDecision.APPROVED
        ? new PaymentStatus.Completed(at) : new PaymentStatus.Rejected(at));
}

// 72 h sweep. Silent no-op on anything else — a concurrent tick must not throw.
public Payment expireAwaitingManualVerification(Instant at) {
    return status instanceof PaymentStatus.AwaitingManualVerification
        ? withStatus(new PaymentStatus.Expired(at)) : this;
}
```

The asymmetry is the point and it is the answer to the proposal's sweep/approval race row: a duplicate
sweep tick is an expected condition (no-op, like `Subscription.expire`), a second admin click on an
already-`Expired` payment is a human acting on stale information and **must** surface as `409`, never
resurrect a subscription. That is exactly what the spec's "resolving an already-expired payment fails"
scenario demands, and it is why `applyProviderOutcome`'s monotonic no-op cannot be reused here.

No new `PaymentStatus` record: `Completed`, `Rejected` and `Expired` already exist and already persist
through `PaymentJpaMapper`. `ManualVerificationDecision {APPROVED, REJECTED}` is a new domain enum.
A new `Payment.awaitingManualVerification(...)` static factory mirrors `awaitingProvider` for birth.

### C5 — `PaymentProof` is its own aggregate, keyed unique on `payment_id`

`billing_payment_proofs` carries `UNIQUE KEY uq_billing_payment_proofs_payment_id (payment_id)`.
Replacement is therefore an overwrite of one row, and "the replaced proof is no longer retrievable"
is a structural property of the schema rather than a cleanup step someone must remember.

Hanging the five proof columns off `Payment` is rejected: `Payment` is shared by Checkout Pro and
physical purchases, which can never have a proof, so every one of those rows — and every branch of
`PaymentJpaMapper` — would carry five permanently-null columns to serve one flow.

**Storage key scheme — no client byte ever reaches a path:**

```
{paymentId}/{proofId}.{ext}      e.g.  9f3c…/1a7e….pdf
```

- `proofId` is a server-generated `UUID` (`PaymentProofId.generate()`).
- `ext` comes from a closed map over the **validated** content type
  (`image/png→png`, `image/jpeg→jpg`, `application/pdf→pdf`) — never from the uploaded filename's suffix.
- The original filename is persisted as `original_filename` **metadata only**, for the ops email, and
  is never concatenated into a path.

Path traversal is impossible by construction because the key contains only two UUIDs and a
whitelisted literal. The filesystem adapter still asserts
`root.resolve(key).normalize().startsWith(root)` as defense in depth, and the volume is mounted
outside any static-resource root, so no `ResourceHttpRequestHandler` can serve it.

### C6 — Sweep: separate bean, `REQUIRES_NEW`, both writes inside it

Exact mirror of `SubscriptionExpiryReconciler`/`SubscriptionExpiryWorker`:

| Bean | Annotations | Responsibility |
|---|---|---|
| `PaymentExpiryReconciler` | `@Component`, `@ConditionalOnProperty` **on the class**, `@Scheduled(fixedRateString=...)`, **no** `@Transactional` | Query ids, dispatch one by one, log-and-skip failures |
| `PaymentExpiryWorker` | `@Component`, `@Transactional(propagation = REQUIRES_NEW)` | Re-read `Payment` **and** `Subscription` inside the transaction, apply both writes, commit together |

A private `@Transactional` method self-invoked from the reconciler is explicitly rejected: Spring's AOP
proxy never intercepts a self-invoked call, so it would run with **no transaction at all**, and
`SubscriptionRepositoryAdapter.save` is `Propagation.MANDATORY` — it would fail loudly rather than
silently, but the spec's "both in the same transaction" would be unimplementable either way. This is
the lesson already burned in by #245 and documented verbatim in `SubscriptionExpiryWorker`'s Javadoc.

`@ConditionalOnProperty` sits on the **class**, not on `tick()` — Spring only evaluates it on a
component/configuration class; on a plain method it is inert and the job would run unconditionally.

Everything else keeps the repo's decorator discipline: application use cases carry no Spring
annotations, and `infrastructure/transaction/Transactional*UseCase` decorators supply the `REQUIRED`
boundary, exactly as `TransactionalCancelSubscriptionUseCase` does.

### C7 — Two Redis keys, one Lua script, one port

```
rate:billing-bank-transfer:user:{userId}:{yyyy-MM-dd}   limit 10, TTL 26 h
rate:billing-proof-upload:payment:{paymentId}           limit 3,  TTL 72 h
```

One shared structure is rejected: the two budgets have different subjects (user vs. payment),
different windows (calendar day vs. the payment's own 72 h lifetime) and different meanings. A shared
hash would tie the upload counter's expiry to the user's daily bucket, so a proof budget could reset
mid-payment or expire while the payment is still open.

`RedisBankTransferRateLimitPort` reuses `RedisBillingPlansRateLimitPort`'s `CONSUME_SCRIPT` verbatim
(`INCR`, `EXPIRE` on first hit, `TTL` read, single round trip) and fails **closed** with
`BillingDegradedException` → `503` + `Retry-After`.

**One deliberate deviation from the plans limiter**: the upload budget is consumed *after* validation
passes, not on every request. `RedisBillingPlansRateLimitPort`'s "every request counts" rule exists
because a public scraping surface has no successful outcome to exempt. Here, charging rejected uploads
would let three malformed files lock a legitimate owner out of their own payment for 72 h with no
proof and no recourse. Disk growth stays bounded either way, because a rejected file is never written.
The daily payment-creation budget is consumed *before* any write, since creation itself is the cost.

### C8 — Authorization: owner from the token, admin from the existing role

Owner identity is `UUID.fromString(authentication.getName())` in `PaymentController`, passed in the
command and compared in the use case against `payment.getUserId()` — the same `actingUserId` pattern
as `SubscriptionController:115`. A client-supplied id is never read for authorization.

A non-owner gets **`404`**, not `403`, via `PaymentNotFoundException` — the same anti-enumeration
reasoning `SubscriptionExceptionHandler` documents for `SubscriptionNotFoundException` ("never a 403,
so the response cannot be used to probe which is true"). The spec says only "rejected".

The D1 endpoints need **no new `SecurityConfig` rule**: `/api/v1/admin/billing/payments/**` already
falls under the generic `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")` (line 258), which is
what produces the spec's `403`. `PaymentAdminController` adds the same defense-in-depth
`isAdmin(Authentication)` boundary check `SubscriptionAdminController:85-89` uses.

Two owner matchers are added, method-and-path scoped rather than a broad `payments/**`:

```java
.requestMatchers(HttpMethod.POST, "/api/v1/billing/payments/*/proof").authenticated()
.requestMatchers(HttpMethod.GET,  "/api/v1/billing/payments/*").authenticated()
```

Breadth matters here: `/api/v1/billing/payments/mercadopago/webhook` is `permitAll` and must stay so.
It is declared earlier and first-match-wins protects it, but a method-scoped matcher removes the
question entirely — the webhook is a `POST` at a deeper path neither rule can shadow.

### C9 — `updatedAt` needs no new column: `status_changed_at` already carries it

`billing_payments` (V8 + V14) has **no** `updated_at` column, and `Payment` has no such field — yet
the spec requires `GET /payments/{paymentId}` to return one. It also has
`status_changed_at DATETIME(3) NULL`, which `PaymentJpaMapper:83-87` already writes from the terminal
status record (`Completed.confirmedAt`, `Rejected.rejectedAt`, `Cancelled.cancelledAt`,
`Expired.expiredAt`) and reads back at line 68-71.

So `updatedAt = statusChangedAt.orElse(createdAt)`, exposed through a new derived accessor
`Payment.statusChangedAt()` that switches on the sealed status. Adding an `updated_at` column is
rejected: it would be a second source of truth for the same fact, requiring every existing write path
— including Mercado Pago's — to keep it in sync, to serve one read endpoint.

Stated honestly: `updatedAt` means *when the status last changed*, so a proof upload does not move it.
The spec requires the field to be present and truthful, not to track every mutation.

### C10 — Fulfillment is extracted, not duplicated

Approve must do exactly what an approved card payment does: `Subscription.activate(confirmedAt,
plan.durationDays, plan.courseIds)` followed by `assigned()`, with the plan read by id regardless of
status. Reject and expire must do exactly what a rejected card payment does: `cancelled()`.

All of that already exists — as **private** methods of `PaymentVerificationService`
(`ensureFulfillment`, `releaseFulfillment`, `ensureSubscription`, lines 174-224). Reimplementing it in
`ResolvePaymentProofUseCaseImpl` would fork the course-snapshot freeze (escenario 2b) into two places.

Extract them, unchanged, into `PaymentFulfillmentService` (`billing.application.usecase`) exposing
`ensure(Payment)` and `release(Payment)`; `PaymentVerificationService` delegates to it, keeping its own
existing tests green as the refactor's proof. `ResolvePaymentProofUseCaseImpl` and `PaymentExpiryWorker`
then call the same collaborator. The `PaymentTarget.Physical` branch comes along for free and is never
reached by this flow, since `AwaitingManualVerification` is only ever born `Virtual` here.

### C11 — Content validation is a pure domain service, and bytes cross the boundary, not `MultipartFile`

This is the repository's **first** file upload — `MultipartFile` appears nowhere in `api/` today, so
there is no precedent to follow and the boundary is set here.

`PaymentController` converts `MultipartFile` into `PaymentProofUpload(declaredContentType,
originalFilename, sizeBytes, byte[] content)`, an `application/dto` record. Nothing below the web
layer ever sees a Spring or servlet type, and nothing below infrastructure ever sees a filesystem path
— the domain holds only the opaque `storageKey` string.

`PaymentProofContentValidator` (`billing.domain.service`) is pure, I/O-free and Spring-free:

| Check | Rule |
|---|---|
| Declared type | ∈ `{image/png, image/jpeg, application/pdf}` |
| Size | ≤ `5 * 1024 * 1024` bytes |
| Magic bytes | `89 50 4E 47 0D 0A 1A 0A` / `FF D8 FF` / `%PDF-` |
| Agreement | sniffed type **must** equal declared type — a PDF declared as PNG is rejected |

`spring.servlet.multipart.max-file-size` is set to **6 MB**, deliberately above the 5 MB business rule,
so the domain rule produces the spec's `400 application/problem+json` and the container cap is only a
DoS backstop. If the container cap were 5 MB it would fire first with
`MaxUploadSizeExceededException`, and the business rule would be dead code. That exception is still
mapped to `400` in `PaymentExceptionHandler` for the genuinely oversized case.

### C12 — Write order inside `SubmitPaymentProofUseCaseImpl`

1. `consumeProofUpload(paymentId)` — *after* validation (C7), before any write
2. load `Payment`; owner check; assert `AwaitingManualVerification`
3. validate content (C11)
4. `storagePort.store(newKey, bytes)`
5. read the existing proof's key, then `proofRepository.save(newProof)` — overwrites on the unique key
6. `notificationPort.notifyProofSubmitted(...)`
7. `oldKey.ifPresent(storagePort::delete)` — the last mutating step before commit

Rationale: a failure anywhere before step 5 leaves at most one orphan blob that no row references —
unreachable, and bounded to 3 × 5 MB per payment by the limiter. Deleting the previous blob *last*
means the only step that can still roll back after that delete is the commit itself. Notification is
synchronous rather than routed through the outbox (as #209 did): an outbox event type, an `api:app`
handler and a second dispatcher are real surface, and this notification has no cross-transaction
requirement — #209 needed `REQUIRES_NEW` precisely because its producing transaction was doomed, which
is not the case here. A `MailException` fails the request and the owner retries within their budget.

## Data Flow

    POST /billing/subscriptions {paymentMethod}         [SubscriptionController — unchanged]
      └─ RoutingCreateSubscriptionCheckoutUseCase                              (C10 of the proposal: D3)
           ├─ MERCADO_PAGO   → CreateSubscriptionCheckoutUseCaseImpl   (byte-identical, untouched)
           └─ BANK_TRANSFER  → CreateBankTransferSubscriptionUseCaseImpl
                                rate limit 10/user/day (C7, before any write)
                                Payment.awaitingManualVerification(... CBU as merchantAccountId, C2)
                                Subscription.pendingCheckout(...)      [no provider call at all]
                                → 201 + BankTransferInstructions{cbu, alias, holder, cuit, amount, SUB-{id}}

    POST /billing/payments/{id}/proof  (multipart)      [PaymentController, owner from token — C8]
      └─ SubmitPaymentProofUseCaseImpl                                            steps 1-7 of C12
           PaymentProofContentValidator → store(blob) → save(row) → notify(ops) → delete(old blob)

    GET  /billing/payments/{id}                         [owner only]
      └─ GetPaymentUseCaseImpl → {status, createdAt, updatedAt = statusChangedAt ?? createdAt}   (C9)

    POST /admin/billing/payments/{id}/approve|reject    [ROLE_ADMIN — C1, C8]
      └─ ResolvePaymentProofUseCaseImpl
           payment.resolveManually(APPROVED|REJECTED, now)   ← throws on any other status  (C4)
           ├─ APPROVED → PaymentFulfillmentService.ensure(payment)  → activate() + assigned()   (C10)
           └─ REJECTED → PaymentFulfillmentService.release(payment) → cancelled()

    PaymentExpiryReconciler  @Scheduled, no @Transactional                        (C6)
      └─ PaymentExpiryWorker.expireOne(paymentId)      @Transactional(REQUIRES_NEW)
           payment.expireAwaitingManualVerification(now)   ← no-op if already resolved
           PaymentFulfillmentService.release(payment)      → subscription.cancelled()
           ────────────────── one commit, both rows ──────────────────

## File Changes

| File | Action | Description |
|---|---|---|
| `billing/domain/model/Payment.java` | Modify | `+awaitingManualVerification`, `+resolveManually`, `+expireAwaitingManualVerification`, `+statusChangedAt()` (C4, C9) |
| `billing/domain/model/ManualVerificationDecision.java` | Create | `APPROVED, REJECTED` |
| `billing/domain/model/PaymentProof.java` | Create | `id, paymentId, storageKey, originalFilename, contentType, sizeBytes, uploadedAt` (C5) |
| `billing/domain/model/PaymentProofId.java` | Create | VO cloned from `PaymentId` |
| `billing/domain/service/PaymentProofContentValidator.java` | Create | Pure format/size/magic-byte rules (C11) |
| `billing/domain/exception/IllegalPaymentStateTransitionException.java` | Create | `→ 409` |
| `billing/domain/exception/PaymentProofRejectedException.java` | Create | `→ 400` |
| `billing/domain/model/Subscription.java` | **Untouched** | `cancelled()` already performs `PENDING → CANCELLED` (C3) |
| `billing/application/usecase/RoutingCreateSubscriptionCheckoutUseCase.java` | Create | Dispatch on `paymentMethod` |
| `billing/application/usecase/CreateBankTransferSubscriptionUseCaseImpl.java` | Create | Scenario 1 (D3) |
| `billing/application/usecase/{SubmitPaymentProof,GetPayment,ResolvePaymentProof}UseCaseImpl.java` | Create | Scenarios 2-5, 7 |
| `billing/application/usecase/PaymentFulfillmentService.java` | Create | Extracted from `PaymentVerificationService` (C10) |
| `billing/application/usecase/PaymentVerificationService.java` | Modify | Delegates to the extracted collaborator; behavior unchanged |
| `billing/application/usecase/CreateSubscriptionCheckoutUseCaseImpl.java` | **Untouched** | Its non-MP guard stays as an internal invariant; its existing test stays green |
| `billing/application/port/in/{CreateBankTransferSubscription,SubmitPaymentProof,GetPayment,ResolvePaymentProof}UseCase.java` | Create | In-ports |
| `billing/application/port/out/PaymentProofStoragePort.java` | Create | `store(String key, byte[])`, `delete(String key)` — no path ever crosses (C5) |
| `billing/application/port/out/PaymentProofRepository.java` | Create | `save`, `findByPaymentId` |
| `billing/application/port/out/PaymentProofNotificationPort.java` | Create | `notifyProofSubmitted(PaymentProofNotification)` |
| `billing/application/port/out/BankTransferRateLimitPort.java` | Create | `consumeSubscriptionCreation(UUID)`, `consumeProofUpload(PaymentId)` (C7) |
| `billing/application/port/out/PaymentRepository.java` | Modify | `+findExpirableBankTransferIds(Instant createdBefore, int batchSize)` |
| `billing/application/dto/SubscriptionCheckoutResult.java` | Modify | `+BankTransferInstructions` (nullable, like the existing `OverlapNotice`) |
| `billing/application/dto/{BankAccountDetails,BankTransferInstructions,PaymentProofUpload,SubmitPaymentProofCommand,ResolvePaymentProofCommand,PaymentStatusResult,PaymentProofNotification}.java` | Create | Boundary records (C11) |
| `billing/infrastructure/web/controller/PaymentController.java` | Create | `POST /{id}/proof`, `GET /{id}` — owner from token (C8) |
| `billing/infrastructure/web/controller/PaymentAdminController.java` | Create | `POST /{id}/approve`, `POST /{id}/reject` (C1) |
| `billing/infrastructure/web/controller/{PaymentEndpoint,PaymentExceptionHandler}.java` | Create | `@RestControllerAdvice(annotations = PaymentEndpoint.class)`, sibling of `SubscriptionExceptionHandler` |
| `billing/infrastructure/web/dto/{PaymentStatusResponse,RejectPaymentRequest,BankTransferInstructionsResponse}.java` | Create | Web DTOs |
| `billing/infrastructure/transaction/Transactional{SubmitPaymentProof,ResolvePaymentProof,GetPayment}UseCase.java` | Create | `REQUIRED` decorators; `GetPayment` is `readOnly = true` |
| `billing/infrastructure/transaction/TransactionalCreateSubscriptionCheckoutUseCase.java` | Modify | Now wraps the router, not the MP impl |
| `billing/infrastructure/storage/LocalFilesystemPaymentProofStorageAdapter.java` | Create | Docker volume; `normalize().startsWith(root)` assertion (C5) |
| `billing/infrastructure/notification/SpringMailPaymentProofNotificationAdapter.java` | Create | `@Value` ops address, mirrors `SpringMailPurchaseExceptionNotificationAdapter` (D2) |
| `billing/infrastructure/security/RedisBankTransferRateLimitPort.java` | Create | Two keys, one Lua script, fail-closed (C7) |
| `billing/infrastructure/scheduling/PaymentExpiry{Reconciler,Worker}.java` | Create | Scenario 6 (C6) |
| `billing/infrastructure/persistence/{entity/PaymentProofJpaEntity,mapper/PaymentProofJpaMapper,repository/PaymentProofJpaRepository,adapter/PaymentProofRepositoryAdapter}.java` | Create | Proof persistence |
| `billing/infrastructure/persistence/repository/PaymentJpaRepository.java` | Modify | `NOT EXISTS` sweep query |
| `billing/infrastructure/config/BillingConfiguration.java` | Modify | Wire router, four use cases, three ports, config values |
| `auth/infrastructure/security/SecurityConfig.java` | Modify | Two method-scoped owner matchers (C8) |
| `api/app/src/main/resources/db/migration/V21__billing_payment_proofs.sql` | Create | New table + one index on `billing_payments` (see Migration) |
| `api/app/src/main/resources/application*.yml` | Modify | Bank account, ops address, sweep, rate limits, multipart cap |
| `docker-compose.yml` / `infra/docker/**` | Modify | Proof volume outside any static-resource root |

## Interfaces / Contracts

```java
// billing/application/port/out — the domain never learns a filesystem path (C5)
public interface PaymentProofStoragePort {
    /** @param storageKey server-generated, opaque; never derived from a client filename. */
    void store(String storageKey, byte[] content);
    void delete(String storageKey);
}

public interface BankTransferRateLimitPort {
    RateLimitDecision consumeSubscriptionCreation(UUID userId);   // 10/day  — before any write
    RateLimitDecision consumeProofUpload(PaymentId paymentId);    // 3/payment — after validation (C7)
}
```

```sql
-- V21__billing_payment_proofs.sql
CREATE TABLE billing_payment_proofs (
    id                BINARY(16)   NOT NULL,
    payment_id        BINARY(16)   NOT NULL,
    storage_key       VARCHAR(160) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(64)  NOT NULL,
    size_bytes        BIGINT UNSIGNED NOT NULL,
    uploaded_at       DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_billing_payment_proofs_payment_id (payment_id),   -- replacement is an overwrite (C5)
    CONSTRAINT fk_billing_payment_proofs_payment
        FOREIGN KEY (payment_id) REFERENCES billing_payments (id)
);

-- Sweep support: the existing idx_billing_payments_status_type does not cover created_at.
ALTER TABLE billing_payments
    ADD KEY idx_billing_payments_status_created (status_type, created_at);
```

## Testing Strategy

| Layer | What to test | Approach |
|---|---|---|
| Domain (unit) | `resolveManually` approves/rejects from `AwaitingManualVerification` and **throws** from `AwaitingProvider`, `ReconciliationRequired`, `Completed`, `Rejected`, `Cancelled`, `Expired`; `expireAwaitingManualVerification` no-ops on all six (C4) | Extend `PaymentTest` — the asymmetry is the spec |
| Domain (unit) | **Characterization**: `Subscription.cancelled()` on `PENDING` yields `CANCELLED` with `cancellation` absent (C3) | Extend `SubscriptionTest` — locks a behavior that already exists |
| Domain (unit) | Validator: each accepted type; oversized; declared≠sniffed; empty file; PDF renamed `.png` (C11) | New `PaymentProofContentValidatorTest`, table-driven |
| Domain (unit) | Storage key contains only UUIDs + whitelisted ext for `../../etc/passwd`, `a\0.png`, a 300-char name, and a non-ASCII name | New test on the key factory — the path-traversal risk row |
| Application (unit) | Router sends `MERCADO_PAGO` to the MP impl and `BANK_TRANSFER` to the transfer impl, and nothing else | New `RoutingCreateSubscriptionCheckoutUseCaseTest` |
| Application (unit) | Creation: limiter consulted **before** any repository write; `429` creates neither row; `expectedMerchantAccountId == CBU` (C2) | Mockito, `InOrder` on limiter vs. repositories |
| Application (unit) | Proof submit: exact C12 order; non-owner → `PaymentNotFoundException` with zero writes; replacement deletes the old key after the new row is saved; invalid content never touches the limiter or storage | Mockito `InOrder` |
| Application (unit) | Resolve: approve → `Completed` + `activate`+`assigned`; reject → `Rejected` + `cancelled()`; already-`Expired` → throws, zero writes | Mockito on `PaymentFulfillmentService` |
| Application (unit) | `PaymentVerificationService` delegates to the extracted collaborator with **no behavior change** | Existing `PaymentVerificationServiceTest` green, unmodified — the refactor's proof (C10) |
| Infra (unit) | Filesystem adapter writes under the root; a hand-crafted `../` key is refused; `delete` of a missing key is silent | JUnit `@TempDir` |
| Infra (unit) | Redis port: two distinct keys, correct TTLs, fail-closed `BillingDegradedException` | Mirror `RedisBillingPlansRateLimitPortTest` |
| Infra (unit) | `PaymentExpiryWorker.expireOne` annotated `REQUIRES_NEW`; reconciler `tick()` carries **no** `@Transactional` and delegates to a separate bean (C6) | Reflection assertion, mirroring `SubscriptionExpiryWorkerTest` and `PublishPaymentFulfillmentFailedUseCaseTest` |
| Infra (web) | `PaymentController` reads the owner from `Authentication`, never the body; every error is `application/problem+json`; `PaymentAdminController` rejects a blank reject reason with `400` before any call | MockMvc, mirroring `SubscriptionControllerTest` |
| Integration | Scenario 6 end to end: after 72 h one sweep tick leaves `Payment` `Expired` **and** `Subscription` `CANCELLED`, asserted on both rows | Testcontainers MySQL 8 |
| Integration | Approve activates with a frozen course snapshot identical to the MP path; reject cancels; a second resolve after expiry returns `409` and mutates nothing | Testcontainers |
| Integration | Stored proof is unreachable unauthenticated and by a non-owner; the volume path is served by no handler | Testcontainers + real filter chain |
| Integration | `SecurityConfigTest`: the two new matchers gate correctly and `/payments/mercadopago/webhook` stays `permitAll` (C8) | Extend the existing filter-chain test |
| Regression | `SubscriptionCheckoutIntegrationTest` MP scenarios byte-identical; `CreatePhysicalPurchaseCheckoutUseCaseImplTest`'s `BANK_TRANSFER` rejection untouched (#36) | Existing suites, unmodified |
| Architecture | `BillingArchitectureTest`: no `java.nio.file` or `org.springframework.web.multipart` under `domain`/`application` | Existing ArchUnit run |

TDD order: RED domain transitions → RED validator/key → RED router → RED use cases → RED adapters →
RED sweep → RED integration (scenario 6 both-rows assertion last, it is the success criterion).

## Threat Matrix

`references/threat-matrix.md` — **N/A for every row**: this change introduces no routing of shell
commands, no subprocess, no VCS or PR automation, no executable-file classification, and no process
integration. Documentation-like paths: N/A, no file is interpreted or executed. Git selection, commit
state, push state, PR commands: N/A, no VCS surface exists in this change.

The one real adversarial boundary is the upload, covered by C5/C11 and carried into RED tests above:

| Adversarial case | Expected behavior |
|---|---|
| Filename `../../etc/passwd.png` | Stored under `{paymentId}/{proofId}.png`; filename kept as metadata only; nothing escapes the volume |
| PDF bytes declared `image/png` | `400 application/problem+json`, nothing written |
| 6 MB valid PNG | `400` from the domain rule (container cap is 6 MB, C11), nothing written |
| Direct HTTP fetch of the storage path | No handler serves the volume; unauthenticated request rejected |
| Non-owner `GET`/`POST` for another user's payment | `404`, never `403` (C8) |
| Redis unavailable | `503` + `Retry-After`; never an unbounded upload (fail-closed, C7) |

## Migration / Rollout

`V21` is confirmed free — the migration directory's highest is `V20_1__physical_capacity_hold_correlation.sql`,
re-read directly rather than trusted from the proposal.

`V21` creates `billing_payment_proofs` and adds **one index** to `billing_payments`. That index is a
correction to the proposal's "`billing_payments` needs no change": the sweep filters on
`status_type` **and** `created_at`, and `idx_billing_payments_status_type` alone would make it a
range scan over every terminal payment. No column is added (C9 makes `updated_at` unnecessary).

Rollout is config-first and every switch is independent:
`billing.bank-transfer.enabled=false` returns the route to today's rejection,
`billing.bank-transfer.expiry.enabled=false` stops the sweep via `@ConditionalOnProperty`,
and the ops address can be pointed at a sink. Reverting the PR drops one table and one index;
`billing_payments` and `billing_subscriptions` keep their shape, so no existing row needs repair.
Before reverting, resolve or expire any live `AwaitingManualVerification` rows — the reverted code
cannot reach them and they would linger as `PENDING` subscriptions with no exit.

## Requirement → Component Map

| # | Requirement (capability) | Components | Suggested slice |
|---|---|---|---|
| R1 | Checkout accepts `BANK_TRANSFER` (`billing-subscriptions`) | `RoutingCreateSubscriptionCheckoutUseCase`, `TransactionalCreateSubscriptionCheckoutUseCase` | **P2** |
| R2 | MP checkout unaffected — regression (`billing-subscriptions`) | No production change; router test + existing suites | **P2** |
| R3 | System-initiated cancellation of a `PENDING` subscription | **None** — `Subscription.cancelled()` already exists (C3); characterization test only | **P1** |
| R4 | Creating a bank-transfer subscription + 10/day limit | `CreateBankTransferSubscriptionUseCaseImpl`, `BankAccountDetails`, `BankTransferInstructions`, `SubscriptionCheckoutResult`, `Payment.awaitingManualVerification`, `BankTransferRateLimitPort`, `RedisBankTransferRateLimitPort` | **P2** |
| R5 | Submitting, validating, replacing a proof + 3/payment limit | `PaymentProof`, `PaymentProofId`, `PaymentProofContentValidator`, `PaymentProofUpload`, `SubmitPaymentProofUseCaseImpl`, `PaymentProofStoragePort`, `LocalFilesystemPaymentProofStorageAdapter`, `PaymentProofRepository` + JPA quartet, `PaymentProofNotificationPort`, `SpringMailPaymentProofNotificationAdapter`, `PaymentController`, `V21` | **P3** |
| R6 | Reading payment status | `GetPaymentUseCaseImpl`, `Payment.statusChangedAt()`, `PaymentStatusResult`, `PaymentStatusResponse`, `PaymentController` | **P3** |
| R7 | Automatic 72 h expiry, both rows one transaction | `Payment.expireAwaitingManualVerification`, `PaymentRepository.findExpirableBankTransferIds`, `PaymentExpiryReconciler`, `PaymentExpiryWorker`, `PaymentFulfillmentService` | **P5** |
| R8 | Admin resolution of a pending proof (D1) | `ManualVerificationDecision`, `Payment.resolveManually`, `IllegalPaymentStateTransitionException`, `ResolvePaymentProofUseCaseImpl`, `PaymentAdminController`, `RejectPaymentRequest`, `PaymentFulfillmentService` | **P4** |
| R9 | Proof storage is not publicly reachable | `LocalFilesystemPaymentProofStorageAdapter` key scheme, `SecurityConfig` matchers, `docker-compose` volume placement | **P3** |
| — | Cross-cutting: transitions, fulfillment extraction, error mapping | `Payment` transitions, `PaymentFulfillmentService` (+ `PaymentVerificationService` delegation), `PaymentEndpoint`, `PaymentExceptionHandler`, `BillingConfiguration` | **P1** |

Suggested slice order for `sdd-tasks`: **P1** domain + fulfillment extraction + error mapping →
**P2** routing + creation → **P3** proof upload + storage + status read → **P4** admin resolution →
**P5** expiry sweep. Each slice is independently deliverable, testable and revertible; P1 through P4
leave the flow usable-but-unexpiring, and P5 closes it. The 400-line review budget will not survive
a single PR — `sdd-tasks` owns the forecast, but this decomposition is the intended shape.

## Open Questions

None blocking. All five open design questions from the proposal are resolved: C1 (endpoint shape and
mandatory reject reason), C2 (static bank account), C3 (`PENDING` cancellation and the absent actor),
C4 (dedicated manual transition), C5/C9/C11 (names, storage-key scheme, own aggregate, no new column).

Two items for `sdd-tasks` to carry forward as facts, not questions:

- The `billing-subscriptions` delta's parenthetical about `cancel(...)` is inaccurate (C3). The
  requirement stands and needs a test, not production code.
- `V21` adds an index to `billing_payments`, contradicting the proposal's "needs no change" (Migration).

# Design: Admin verification of manual (bank-transfer) payments (#33, US-BILLING-005)

## Technical Approach

Three new endpoints on the existing `PaymentAdminController`, one new public token-authenticated
endpoint on a new controller, one new domain transition, two new out-ports (audit, buyer
notification), one new read path on `PaymentProofStoragePort`, one migration. The already-shipped
approve/reject transition, fulfillment, `409` and `400` are not restructured — they gain exactly
two outbound calls.

The organising principle is that **nothing in this change may widen an authorization surface by
omission**. `SecurityConfig` ends in `.anyRequest().access(roleAuthorizationManager)`, and
`RoleAuthorizationManager` maps only `/api/v1/admin/**` and `/api/v1/instructor/**` — every other
path falls through to a **grant**. A new endpoint without an explicit matcher is therefore public,
not denied. C6 makes that matcher concrete and proves the non-overlap; it is the review anchor for
the proposal's #1 risk.

Domain stays framework-free (ADR-0021, ArchUnit): the signer, the secret, the URL and the
filesystem path all live in `infrastructure`; `application` sees only a `storageKey`, a `PaymentId`
and a `UUID adminId`.

## Architecture Decisions

### C1 — `Payment.correctManually`: reuses `IllegalPaymentStateTransitionException`, which needs one new constructor

D9 makes a correction a status transition with the shape of `resolveManually`, only from a
different starting state. Verified against the source: `PaymentExceptionHandler` maps
`IllegalPaymentStateTransitionException` → `409` and nothing else does, so reusing that exception
type is what keeps "no new error-mapping infrastructure" true (proposal, Approach).

```java
/**
 * Audited exceptional correction out of {@link PaymentStatus.ReconciliationRequired}
 * (#33, US-BILLING-005 escenario 7, D9). A status transition, never a data edit: amount,
 * provider reference and external reference are untouched.
 *
 * @throws IllegalPaymentStateTransitionException if this payment is not currently
 *     {@link PaymentStatus.ReconciliationRequired}
 */
public Payment correctManually(ManualVerificationDecision decision, Instant at) {
    if (!(status instanceof PaymentStatus.ReconciliationRequired)) {
        throw new IllegalPaymentStateTransitionException(
            id, status, decision, "ReconciliationRequired");
    }
    return withStatus(decision == ManualVerificationDecision.APPROVED
        ? new PaymentStatus.Completed(at) : new PaymentStatus.Rejected(at));
}
```

The existing exception hardcodes `", expected AwaitingManualVerification"` in its message, so a
four-arg constructor is added and the existing three-arg one delegates with
`"AwaitingManualVerification"`. `ERROR_CODE`, the three getters and every existing call site are
byte-unchanged; `resolveManually` is not edited at all.

| Option | Tradeoff | Decision |
|---|---|---|
| A new `IllegalPaymentCorrectionException` | Needs a second `@ExceptionHandler`, a second error code, and a second `409` contract for the same semantic fact | Rejected |
| Widen `resolveManually` to accept both starting states | The method the approve/reject path depends on gains a branch; a correction would become indistinguishable from a normal resolution at the call site | Rejected |
| **New method + 4-arg exception constructor** | One added constructor, one added method, zero edits to the shipped transition | **Chosen** |

`PaymentExceptionHandler.illegalStateTransition`'s Spanish detail
(`"El pago ya no está a la espera de verificación manual."`) becomes false for a correction. One
handler method is kept and the detail is generalized to
`"El pago no está en un estado que admita esta operación."`; `PaymentExceptionHandlerTest`'s single
assertion on that string is updated. This is a deliberate, visible one-line copy change, not a
regression.

### C2 — Proof access token: `b64url(payload).b64url(mac)`, signature checked before expiry

The signer/parser split mirrors `FormatQrCredentialSignatureService`/`QrCredentialParser`
structurally; the mechanics are lifted from `HmacSha256WebhookSignatureVerifier` (`Mac` +
`MessageDigest.isEqual`), never from the QR module's non-HMAC placeholder (D1).

```java
// infrastructure/proof/ProofAccessTokenSigner.java  (@Component, D7 @RequiredArgsConstructor n/a — @Value ctor)
final class ProofAccessTokenSigner {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String VERSION = "1";

    /** payload = "1:{paymentId}:{expiresAtEpochSecond}" */
    String sign(PaymentId paymentId, Instant now) {
        String payload = VERSION + ":" + paymentId.getValue() + ":" + now.plus(ttl).getEpochSecond();
        return encode(payload.getBytes(UTF_8)) + "." + encode(hmac(payload));
    }

    private static String encode(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
```

`Base64.getUrlEncoder().withoutPadding()` is deliberate: the token travels as a query parameter, so
`+`, `/` and `=` must never appear — no percent-encoding, no double-decode ambiguity, no truncation
at a stray `=`.

```java
// infrastructure/proof/ProofAccessTokenVerifier.java
PaymentId verify(String token, Instant now) {           // throws InvalidProofAccessTokenException
    // 1. shape: exactly one '.', both halves decodable as URL-safe Base64
    // 2. signature: MessageDigest.isEqual(hmac(payload), providedMac)   ← BEFORE expiry
    // 3. payload: version == "1", 3 colon-separated fields, parseable UUID + epoch second
    // 4. expiry:  expiresAt > now
}
```

Signature is validated **before** expiry, and every failure — absent, malformed, wrong version,
tampered, expired, bad UUID — throws the same `InvalidProofAccessTokenException` with the same
message. A forged token therefore reveals nothing about expiry windows, and the response cannot
distinguish "expired" from "never valid". `MessageDigest.isEqual` compares the raw MAC bytes, not
the Base64 strings; `Base64.getUrlDecoder()`'s `IllegalArgumentException` is caught and converted,
never allowed to reach the `IllegalArgumentException` → `400` handler (which would leak a
distinguishable status).

### C3 — Secret and TTL: fail-fast `@Value`, no usable default (D6)

```java
ProofAccessTokenSigner(
    @Value("${billing.bank-transfer.proof.token-secret}") String secret,
    @Value("${billing.bank-transfer.proof.token-ttl-seconds:900}") long ttlSeconds
) {
    this.secret = requiredValue(secret, "payment proof token secret");  // blank → IllegalArgumentException
    this.ttl = Duration.ofSeconds(ttlSeconds);
}
```

No default on the secret (absent property → unresolvable placeholder → startup failure) **and** a
blank guard in the same shape `SpringMailActivationNotificationAdapter.requiredValue` already uses
(absent and blank are different failures, both fatal). The verifier takes the same property, so a
mismatch is impossible. TTL defaults to `900` = the 15 minutes D1 fixes.

### C4 — Storage read path: `read` on the existing port, not a new port

```java
public interface PaymentProofStoragePort {
    void store(String storageKey, byte[] content);
    void delete(String storageKey);
    /** @throws ProofContentUnavailableException if the key has no backing blob */
    byte[] read(String storageKey);          // NEW
}
```

`LocalFilesystemPaymentProofStorageAdapter.read` reuses the existing private `resolveWithinRoot`
verbatim — the same `root.resolve(key).normalize().startsWith(root)` traversal guard already
protecting `store`/`delete` — then `Files.readAllBytes`. A sibling read-only port would duplicate
that guard, which is precisely the thing that must have one implementation.

### C5 — Serving endpoint: `GET /api/v1/billing/payments/{paymentId}/proof?token=…`

A new `PaymentProofController` (`@PaymentEndpoint`, so it inherits `PaymentExceptionHandler`),
deliberately **not** on `PaymentAdminController`: the caller is a browser following a signed URL
with no `Authorization` header, so the path must not sit under `/api/v1/admin/**`.

```java
@GetMapping("/api/v1/billing/payments/{paymentId}/proof")
ResponseEntity<byte[]> serve(@PathVariable String paymentId, @RequestParam("token") String token) {
    PaymentId signed = tokenVerifier.verify(token, clock.now());
    if (!signed.equals(PaymentId.of(paymentId))) {      // token bound to THIS payment
        throw new InvalidProofAccessTokenException();
    }
    PaymentProof proof = proofRepository.findByPaymentId(signed)
        .orElseThrow(InvalidProofAccessTokenException::new);   // same 403, never a 404 oracle
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(proof.getContentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(storagePort.read(proof.getStorageKey()));
}
```

The path `{paymentId}` is cross-checked against the token's payload, so a valid token for payment A
cannot serve payment B's proof. `no-store` keeps the blob out of shared caches once the 15 minutes
lapse. `InvalidProofAccessTokenException extends BusinessException` with code
`INVALID_PROOF_TOKEN` → `403` via one new `@ExceptionHandler` on `PaymentExceptionHandler`.

### C6 — The `SecurityConfig` matcher — the proposal's #1 risk, resolved concretely

**Verified from source, not assumed.** Today `/api/v1/billing/payments/{id}/proof` has one rule —
`POST … .authenticated()` (line 249). Spring Security matches per method, so **`GET` on that exact
path is unmapped**, and unmapped paths reach `.anyRequest().access(roleAuthorizationManager)` whose
map contains only the two admin/instructor prefixes, granting everything else. Without this change
the endpoint would be readable by anyone — the risk is real, not hypothetical.

One line, declared immediately after the existing POST proof rule:

```java
// #33, US-BILLING-005 (design C5/C6): the signed-token proof viewer. permitAll is the
// credential decision, not an absence of one — the HMAC token in ?token= IS the credential
// and PaymentProofController rejects an absent/tampered/expired one with 403. The browser
// follows a signed URL with no Authorization header, so an authenticated() rule would break
// the flow, and this path is NOT under /api/v1/admin/**. Without this explicit matcher it
// falls through to anyRequest()'s permissive grant — which is the same outcome by accident
// instead of by decision, and untestable as intent.
.requestMatchers(HttpMethod.GET, "/api/v1/billing/payments/*/proof").permitAll()
```

Non-overlap, checked against every neighbouring rule:

| Existing rule | Overlaps? | Why |
|---|---|---|
| `POST /api/v1/billing/payments/*/proof` → authenticated | No | different method |
| `GET /api/v1/billing/payments/*` → authenticated | No | `*` matches one segment; this path has two after `payments` |
| `/api/v1/billing/payments/mercadopago/webhook` → permitAll | No | `mercadopago` matches `*`, but `webhook` ≠ `proof` |
| `/api/v1/admin/**` → hasRole("ADMIN") | No | different prefix |

Declaration order is therefore immaterial for correctness, which is exactly what a
`SecurityConfigTest` case must pin.

The three **admin** endpoints need **no** matcher: `/api/v1/admin/billing/**` is matched by
`/api/v1/admin/**` → `hasRole("ADMIN")` (line 317), and no earlier, more specific admin matcher
(`…/physical/attendance/*`, `…/physical/courses/**`, `…/physical/sessions/**`,
`…/physical/devices/**`, `…/virtual/*`) can match a `billing` path. D3 verified.

### C7 — Audit: one out-port, one thin `@Transactional(REQUIRED)` adapter (D4)

```java
// application/port/out/PaymentAuditRepository.java
public interface PaymentAuditRepository {
    void append(PaymentId paymentId, UUID adminId, PaymentAuditAction action, String reason);
}
// domain/model/PaymentAuditAction.java — APPROVE, REJECT, CORRECTION
```

`PaymentAuditRepositoryAdapter` is `@Component` + `@Transactional(propagation = REQUIRED)` +
Lombok `@RequiredArgsConstructor` (D7), structurally identical to
`PhysicalDeviceAuditRepositoryAdapter`. `REQUIRED` — not `REQUIRES_NEW` — is deliberate: the audit
row and the status transition must be one atomic fact (proposal risk row: "audit write … rolls back
an otherwise-valid approval" is answered by making them the *same* write, so neither can exist
without the other).

D4 fixes the column set at `id, payment_id, admin_id, action, reason, created_at`, and also
requires corrections to record evidence. The correction use case therefore composes
`reason + " | evidencia: " + evidence` into the single `reason TEXT` column, in that exact
documented format. Declared as a compromise, not an oversight — see Open Questions.

`admin_id` comes from `UUID.fromString(authentication.getName())` in the controller (the JWT
principal is the user UUID, the same source `PhysicalCheckInController` uses); `ResolvePaymentProofCommand`
gains an `adminId` component.

### C8 — Buyer notification: two methods, and a new narrow contact port

Two methods rather than one taking a decision, so D5's "two distinct templates" is a compile-time
property and the ops adapter (`PaymentProofNotificationPort`, unchanged) can never be the one
invoked:

```java
// application/port/out/PaymentDecisionNotificationPort.java
public interface PaymentDecisionNotificationPort {
    void notifyApproved(PaymentDecisionNotification notification);
    void notifyRejected(PaymentDecisionNotification notification);   // carries the motivo
}
// application/dto/PaymentDecisionNotification.java (record, D7)
//   (UUID paymentId, UUID buyerUserId, String targetReference,
//    BigDecimal amount, String currency, String reason)
```

**The buyer's email address is not reachable today.** `UserExistencePort` is the only
`billing → shared ← auth` port and its javadoc states it deliberately never returns an email, "a
projection would leak identity data into billing". So a new sibling is required:

```java
// api/shared/src/main/java/com/menta/shared/auth/UserContactPort.java
public interface UserContactPort { Optional<String> emailOf(UUID userId); }
```

implemented in `:api:auth` next to `UserExistenceAdapter`, and consumed **only** by the billing
*infrastructure* mail adapter. Billing's application layer still passes a bare `UUID`; the address
never crosses into `application` or `domain`, which preserves the spirit of `UserExistencePort`'s
rule while making the email deliverable.

| Option | Tradeoff | Decision |
|---|---|---|
| Put the email on `PaymentDecisionNotification` | Identity data enters `application` — the exact leak `UserExistencePort` refuses | Rejected |
| Widen `UserExistencePort` with `emailOf` | Turns the deliberately minimal existence contract into a user projection; its own javadoc forbids this | Rejected |
| Send from `:api:app` after the use case | The assembler grows business behaviour; D8's boundary becomes invisible from billing | Rejected |
| **New `UserContactPort`, consumed only by the billing mail adapter** | One new tiny shared port + one auth adapter; the boundary stays where ADR-0021 puts it | **Chosen** |

`SpringMailPaymentDecisionNotificationAdapter` mirrors `SpringMailActivationNotificationAdapter`:
`JavaMailSender` + `SimpleMailMessage` + a `@Value` from-address. Two Spanish bodies; the rejection
body interpolates `notification.reason()` verbatim (D5).

### C9 — D8 in code: `TransactionSynchronization.afterCommit`, inside the adapter

There is **no** existing `afterCommit` / `@TransactionalEventListener` anywhere in `api/` (grep:
zero matches), so this is a new mechanism and has to be justified rather than assumed.

| Option | Tradeoff | Decision |
|---|---|---|
| Outer non-transactional decorator `NotifyingResolvePaymentProofUseCase` | Matches the repo's existing decorator idiom, but forces `ResolvePaymentProofUseCase.resolve` to stop returning `void` so the decorator knows what to send — a signature change on a shipped in-port, and it contradicts the proposal's "impl gains two outbound calls" | Rejected |
| Send inline inside the transaction and swallow failures | SMTP latency inside a DB transaction; and a *successful* email followed by a later rollback tells the buyer something untrue | Rejected |
| A retry queue / outbox row | Explicitly out of scope (D8: "no automatic retry queue in this change") | Rejected |
| **`afterCommit` registered by the mail adapter** | The application layer calls the port unconditionally (proposal's Approach, verbatim); the deferral is an infrastructure concern in an infrastructure class | **Chosen** |

```java
private void sendAfterCommit(Runnable send) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        guarded(send);                                     // no tx (tests, correction retries)
        return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { guarded(send); }
    });
}

private void guarded(Runnable send) {
    try { send.run(); }
    catch (RuntimeException failure) {                     // D8: never rolls back the decision
        log.warn("Buyer decision email failed paymentId={} — payment stands", paymentId, failure);
    }
}
```

The `try/catch` is load-bearing, not defensive noise: Spring propagates an exception thrown from
`afterCommit` back to the caller of `commit()`, so without it a dead SMTP host would surface as a
`500` on an approval that already committed — the exact outcome D8 forbids. On rollback
`afterCommit` never fires, so no email is sent for a decision that did not stand.

### C10 — Pending list: an id-free constructor projection, `Page` never crosses into `application`

`PaymentRepository` follows its own precedent (`findExpirableBankTransferIds(Instant, int limit)`
takes primitives and the adapter builds the `PageRequest`), so no Spring type reaches
`application`:

```java
// application/port/out/PaymentRepository.java  (added)
PendingVerificationPage findAwaitingManualVerification(int page, int size);

// application/dto/ (records, D7)
record PendingVerificationPage(List<PendingVerificationItem> items, int page, int size,
                               long totalElements, int totalPages) {}
record PendingVerificationItem(UUID paymentId, UUID userId, String targetModality,
                               String targetReference, BigDecimal amount, String currency,
                               String statusType, boolean hasProof, Instant createdAt) {}
```

```java
// PaymentJpaRepository (added)
@Query(value = """
    SELECT new com.menta.billing.infrastructure.persistence.projection.PendingVerificationRow(
        p.id, p.userId, p.targetModality, p.targetReference, p.expectedAmount, p.expectedCurrency,
        p.statusType, p.createdAt,
        (SELECT COUNT(pr) FROM PaymentProofJpaEntity pr WHERE pr.paymentId = p.id))
      FROM PaymentJpaEntity p
     WHERE p.statusType = :statusType
     ORDER BY p.createdAt ASC
    """,
    countQuery = "SELECT COUNT(p) FROM PaymentJpaEntity p WHERE p.statusType = :statusType")
Page<PendingVerificationRow> findByStatusTypeOrderByCreatedAt(
    @Param("statusType") String statusType, Pageable pageable);
```

An explicit `countQuery` is mandatory here — Spring Data cannot derive a count from a constructor
expression. `hasProof` is a correlated `COUNT` mapped to `count > 0`, which avoids a join that would
multiply rows. The existing `idx_billing_payments_status_created (status_type, created_at)`
(V20_1_5) serves both the predicate and the `ORDER BY`; **no new index**.

`status=PENDING&substatus=AWAITING_MANUAL_VERIFICATION` maps cleanly onto the sealed hierarchy:
`PENDING` is `PaymentStatus.Pending`, `substatus` is the concrete record, and `status_type` already
stores `AWAITING_MANUAL_VERIFICATION` as a plain string (proven by `findExpirableBankTransferIds`).
Page size is **clamped**, not rejected: `size = Math.min(Math.max(size, 1), 50)`, default `20`. A
reject would need a new `400` branch for a limit the issue states as a cap, and a clamp cannot be
used to force an unbounded scan.

### C11 — Reconciliation-task resolution (D2): a conditional bulk update, zero rows is not an error

```java
// ReconciliationTaskJpaRepository (added)
@Modifying(clearAutomatically = true)
@Query("""
    UPDATE ReconciliationTaskJpaEntity t
       SET t.resolved = true, t.resolvedAt = :at, t.resolvedBy = :adminId
     WHERE t.paymentId = :paymentId AND t.resolved = false
    """)
int resolveOpenByPaymentId(@Param("paymentId") UUID paymentId,
                           @Param("at") Instant at, @Param("adminId") UUID adminId);
```

`AND t.resolved = false` makes a re-run idempotent. A return of `0` is **not** an error and must not
throw: `WebhookVerificationWorker.createReconciliationTask` writes rows with a **null** `paymentId`
when no local payment matched, so a payment can legitimately be `ReconciliationRequired` with no
task keyed to it. The correction stands either way; the count is logged.

`ReconciliationTaskJpaEntity` is existing code and is **not** retrofitted to Lombok (D7): three
fields plus hand-written accessors in the file's current style. `V24` columns are
`resolved BOOLEAN NOT NULL DEFAULT FALSE`, `resolved_at DATETIME(6) NULL`,
`resolved_by BINARY(16) NULL` — all additive, so the worker's existing five-arg insert stays valid
and pre-existing rows read as unresolved with no backfill.

## Data Flow

    GET  /api/v1/admin/billing/payments?status=PENDING&substatus=AWAITING_MANUAL_VERIFICATION
      │  size clamped to [1,50] (C10) → PaymentRepository.findAwaitingManualVerification(page,size)
      └─→ 200 PendingVerificationPage (oldest first, hasProof, no proof URL here)

    GET  /api/v1/admin/billing/payments/{paymentId}
      │  payment + buyer + plan + proofUrl = base + "/proof?token=" + signer.sign(id, now)  (C2)
      └─→ 200                                        token valid for 900s, never persisted

    GET  /api/v1/billing/payments/{paymentId}/proof?token=…        [permitAll — C6]
      │  verify shape → HMAC (MessageDigest.isEqual) → payload → expiry     (C2, this order)
      │  any failure ─────────────────────────────────→ 403 INVALID_PROOF_TOKEN (indistinguishable)
      │  token.paymentId != {paymentId} ──────────────→ 403 INVALID_PROOF_TOKEN
      └─→ 200 bytes  (storagePort.read via resolveWithinRoot, Content-Disposition: inline, no-store)

    POST /api/v1/admin/billing/payments/{paymentId}/{approve|reject}
      ┌── TransactionalResolvePaymentProofUseCase  @Transactional(REQUIRED) ────────────┐
      │  resolveManually  →  save  →  fulfillment ensure/release        (UNCHANGED)     │
      │  auditRepository.append(paymentId, adminId, APPROVE|REJECT, reason)   (C7)      │
      │  notificationPort.notifyApproved|notifyRejected(...)  → registers afterCommit   │
      └───────────────────────── COMMIT ───────────────────────────────────────────────┘
                                    └─→ afterCommit: SMTP send, failure logged only  (C9, D8)

    POST /api/v1/admin/billing/payments/{paymentId}/corrections   {decision, reason, evidence}
      ┌── @Transactional(REQUIRED) ────────────────────────────────────────────────────┐
      │  correctManually(decision, now)  ─┤ not ReconciliationRequired → 409           │
      │  save → fulfillment ensure/release → resolveOpenByPaymentId (0 rows ok, C11)   │
      │  audit CORRECTION, reason = "{motivo} | evidencia: {evidence}"        (C7)     │
      └───────────────────────── COMMIT ──────────────────────────────→ afterCommit email

## File Changes

| File | Action | Description |
|---|---|---|
| `billing/domain/model/Payment.java` | Modify | `+correctManually(decision, at)`; `resolveManually` untouched (C1) |
| `billing/domain/model/PaymentStatus.java` | Modify | Javadoc only — the `Pending` note now points at a delivered corrections endpoint (C1) |
| `billing/domain/exception/IllegalPaymentStateTransitionException.java` | Modify | 4-arg ctor carrying the expected state; 3-arg delegates. Code/getters unchanged (C1) |
| `billing/domain/exception/InvalidProofAccessTokenException.java` | Create | `INVALID_PROOF_TOKEN` → 403, no detail variance (C2, C5) |
| `billing/domain/model/PaymentAuditAction.java` | Create | `APPROVE`, `REJECT`, `CORRECTION` (C7) |
| `billing/infrastructure/proof/ProofAccessTokenSigner.java` | Create | HMAC-SHA256 sign, URL-safe Base64, `@Value` secret/TTL (C2, C3) |
| `billing/infrastructure/proof/ProofAccessTokenVerifier.java` | Create | Shape → signature → payload → expiry, one exception (C2) |
| `billing/infrastructure/web/controller/PaymentProofController.java` | Create | `GET /api/v1/billing/payments/{id}/proof`, `@PaymentEndpoint` (C5) |
| `billing/infrastructure/web/controller/PaymentAdminController.java` | Modify | List, detail, corrections; approve/reject bodies gain only the `adminId` argument (C7, C10) |
| `billing/infrastructure/web/controller/PaymentExceptionHandler.java` | Modify | `+INVALID_PROOF_TOKEN` → 403; `illegalStateTransition` detail generalized (C1, C5) |
| `billing/application/port/out/PaymentProofStoragePort.java` + `LocalFilesystem…Adapter` | Modify | `+read(storageKey)` reusing `resolveWithinRoot` (C4) |
| `billing/application/port/out/PaymentRepository.java` + `…Adapter` + `PaymentJpaRepository` | Modify | `+findAwaitingManualVerification(page,size)` + projection query (C10) |
| `billing/application/port/out/PaymentAuditRepository.java` | Create | `append(paymentId, adminId, action, reason)` (C7) |
| `billing/application/port/out/PaymentDecisionNotificationPort.java` | Create | `notifyApproved` / `notifyRejected` (C8) |
| `billing/application/usecase/ResolvePaymentProofUseCaseImpl.java` | Modify | Exactly two added calls: audit append, buyer notification (C7, C8) |
| `billing/application/usecase/CorrectPaymentUseCaseImpl.java` + in-port | Create | D9 correction: transition, fulfillment, task resolve, audit, email |
| `billing/application/dto/ResolvePaymentProofCommand.java` | Modify | `+adminId` (C7) |
| `billing/infrastructure/persistence/{entity,repository,adapter}` (audit) | Create | `BillingAuditLogJpaEntity` (Lombok, D7) + repo + `@Transactional(REQUIRED)` adapter (C7) |
| `billing/infrastructure/persistence/entity/ReconciliationTaskJpaEntity.java` + repo | Modify | `+resolved/resolvedAt/resolvedBy`, `resolveOpenByPaymentId` (C11) |
| `billing/infrastructure/notification/SpringMailPaymentDecisionNotificationAdapter.java` | Create | Two Spanish templates, `afterCommit` deferral, `UserContactPort` (C8, C9) |
| `api/shared/.../auth/UserContactPort.java` + `:api:auth` adapter | Create | `emailOf(UUID)` — **not in the proposal's Affected Areas table** (C8) |
| `billing/infrastructure/config/BillingConfiguration.java` | Modify | Wire the correction use case and the two new ports |
| `api/app/.../db/migration/V24__billing_audit_log_and_reconciliation_resolution.sql` | Create | New table + three additive columns (D2, D4) |
| `api/auth/.../security/SecurityConfig.java` | **Modify** | **One line**: `GET /api/v1/billing/payments/*/proof` → `permitAll` (C6) |
| `api/openapi/billing-v1.yaml`, `bruno/API - Direct/billing/` | Modify | Four endpoints, the `403` token contract, D3's route divergence in prose |
| `docs/user-stories/US-BILLING-005.md` | Modify | Draft → active; record D3's divergence from the literal route |

## Interfaces / Contracts

```
GET  /api/v1/admin/billing/payments?status=PENDING&substatus=AWAITING_MANUAL_VERIFICATION&page=0&size=20
  200 { items:[{paymentId,userId,userEmail,plan,amount,currency,status,substatus,hasProof,createdAt}],
        page, size, totalElements, totalPages }          size clamped to 50
GET  /api/v1/admin/billing/payments/{paymentId}
  200 { payment{…}, buyer{…}, plan{…}, proof:{filename,contentType,sizeBytes,url,expiresAt} | null }
GET  /api/v1/billing/payments/{paymentId}/proof?token=<b64url>.<b64url>
  200 <bytes>      403 INVALID_PROOF_TOKEN (absent | malformed | tampered | expired | wrong payment)
POST /api/v1/admin/billing/payments/{paymentId}/corrections
  { "decision": "APPROVED|REJECTED", "reason": "<required, non-blank>", "evidence": "<required, non-blank>" }
  200 · 400 INVALID_REQUEST (blank reason/evidence) · 409 ILLEGAL_PAYMENT_STATE_TRANSITION · 403 non-admin
```

`reason` and `evidence` are `@NotBlank` on the request record, landing on the existing
`MethodArgumentNotValidException` → `400` path — the same mechanism that already produces the
`400` on a reject without `reason`.

## Testing Strategy

| Layer | What to test | Approach |
|---|---|---|
| Unit (domain) | `correctManually` from `ReconciliationRequired` → `Completed`/`Rejected`; from each of the other six statuses → `IllegalPaymentStateTransitionException` whose message names `ReconciliationRequired`; `resolveManually`'s existing cases unchanged | Parameterized over `PaymentStatus` |
| Unit (proof token) | Round-trip sign→verify; tampered payload, tampered MAC, swapped halves, missing `.`, extra `.`, non-Base64, wrong version, non-UUID id, expired by 1s, valid at expiry−1s — **all one exception type, one message**; a token for payment A rejected for payment B | Fixed `Clock` + fixed secret; no HTTP |
| Unit (proof token) | Signature is checked before expiry: an expired *and* tampered token is indistinguishable from a merely tampered one | Same exception/message assertion |
| Unit (config) | Absent secret → context fails; blank secret → `IllegalArgumentException` at construction (D6) | Constructor test + `ApplicationContextRunner` |
| Unit (storage) | `read` returns stored bytes; `read` of a traversal key throws; `read` of a missing key throws, never returns empty | Extends `LocalFilesystemPaymentProofStorageAdapterTest` |
| Unit (application) | Approve/reject write exactly one audit row with the right action/adminId/reason, and call the buyer port exactly once — and **never** `PaymentProofNotificationPort` | `verify` + `verifyNoInteractions(opsAdapter)` |
| Unit (application) | Correction: transition, fulfillment, task resolve, audit, email — in that order; `resolveOpenByPaymentId` returning `0` still succeeds | `InOrder`, Mockito |
| Unit (application) | List clamps `size` 0→1, 200→50; orders oldest-first; `hasProof` true/false | Mocked port + captor |
| Unit (notification) | `afterCommit` only fires on commit; an SMTP `RuntimeException` inside it is swallowed and logged; no active tx → inline send, same swallow | `TransactionSynchronizationManager` initialized manually |
| Unit (web) | Absent `?token=`, blank token → `403`, never `400`; content type/disposition/`no-store` headers | MockMvc standalone |
| Integration | Signed URL serves bytes within 15 min; after expiry `403`; tampered `403`; **anonymous with no token `403`, and the response proves the path is matched, not granted by fallthrough** | Testcontainers MySQL, full filter chain |
| Integration | Non-admin JWT and anonymous on all three admin endpoints → `403`/`401` | Existing admin integration test shape |
| Integration | Approve writes one `billing_audit_log` row and one buyer mail; correction marks `resolved/resolved_at/resolved_by`; a correction on an `AwaitingManualVerification` payment → `409` | Testcontainers + `GreenMail`-style `JavaMailSender` stub |
| Security | `SecurityConfigTest`: the four rules on `/api/v1/billing/payments/**` stay mutually exclusive in any declaration order; the webhook path is not shadowed; `/api/v1/admin/billing/**` still resolves to `hasRole("ADMIN")` | Parameterized matcher test |
| Architecture | No Spring/JPA in `billing/domain` or `billing/application`; `infrastructure/proof` is not imported by `application` | Existing `ArchitectureTest` |
| Coverage | 95% domain+application, 90% infrastructure | `./gradlew :api:billing:test jacocoTestCoverageVerification` |

**TDD order (RED → GREEN per slice):** `correctManually` + exception ctor → audit port/adapter +
V24 → token signer/verifier → storage `read` → serving controller + `SecurityConfig` matcher →
list query → detail + URL minting → corrections use case → notification port/adapter + `afterCommit`
→ OpenAPI/Bruno/US doc.

## Threat Matrix

N/A — no shell command, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. The change's two adversarial surfaces are covered structurally
instead: unauthenticated proof access is closed by an explicit `permitAll` matcher whose four
non-overlap cases are asserted (C6) plus a token whose every failure mode is one indistinguishable
`403` (C2, C5), and path traversal is closed by reusing the single existing `resolveWithinRoot`
guard rather than writing a second one (C4).

## Migration / Rollout

`V24__billing_audit_log_and_reconciliation_resolution.sql`, next after `V23__physical_devices.sql`
(verified as the current head). Purely additive: one new table, three nullable/defaulted columns.
`WebhookVerificationWorker`'s existing five-argument insert stays valid, pre-existing rows read as
unresolved, and no backfill is required.

One new required configuration property — `billing.bank-transfer.proof.token-secret` — must exist
in every environment **before** deploy; startup fails otherwise (C3, D6). Rotating it invalidates
outstanding tokens, which expire in 15 minutes anyway.

No feature flag. The endpoints are unreachable until this merge lands, and rollback follows the
proposal's plan unchanged.

Five independently deliverable slices (`sdd-tasks` owns the authoritative 400-line guard):

1. **Audit + migration** — V24, audit entity/repo/adapter, `PaymentAuditAction`, the
   reconciliation-resolution columns and query. Inert.
2. **Domain + corrections** — `correctManually`, the exception constructor, the handler detail, the
   corrections use case and endpoint.
3. **List** — port query, projection, JPA query, endpoint, clamping.
4. **Proof access** — signer/verifier, `read`, serving controller, **the `SecurityConfig` matcher**,
   detail endpoint. Review this one alone.
5. **Notifications + contract** — `UserContactPort`, mail adapter, `afterCommit`, OpenAPI, Bruno,
   US doc.

## Requirement → Component Map

| # | Requirement | Components | Slice |
|---|---|---|---|
| R1 | Paginated pending-verification list, oldest-first, max 50 | C10 | **3** |
| R2 | Payment detail with a 15-minute signed proof URL | C2, C3, C5 | **4** |
| R3 | Token-authenticated proof serving; absent/tampered/expired rejected | C2, C4, C5, **C6** | **4** |
| R4 | Audited exceptional correction out of `ReconciliationRequired` | C1, C7, C11 | **2**/**1** |
| R5 | `billing_audit_log` on approve, reject, correction | C7 | **1** |
| R6 | Buyer Spanish decision emails, outside the transaction | C8, C9 | **5** |
| — | `bank-transfer-subscription` delta: no public reachability, one signed exception | C6, C2 | **4** |
| — | Approve/reject transition, `409`, `400` unchanged | C1 (no edit), regression assertions | all |

## Open Questions

None blocking. Four items for `sdd-tasks` to carry as **facts, not questions**:

- **`SecurityConfig` IS modified** (one line), contradicting the proposal's "Unchanged/Verify"
  entry. Verified from source: `GET /api/v1/billing/payments/*/proof` is unmapped today and
  `.anyRequest().access(roleAuthorizationManager)` **grants** unmapped paths, so without the new
  matcher the proof endpoint would be public by accident (C6).
- **A new `:api:shared` port (`UserContactPort`) is required** and is absent from the proposal's
  Affected Areas table. `UserExistencePort` deliberately refuses to expose an email, so D5's buyer
  email is otherwise undeliverable (C8).
- **`IllegalPaymentStateTransitionException` gains a constructor and `PaymentExceptionHandler`'s
  `409` detail string changes**, updating one existing test assertion. The error code, HTTP status
  and all existing call sites are unchanged (C1).
- **Correction evidence is stored inside the `reason` column** as `"{motivo} | evidencia: {evidence}"`,
  because D4 fixes the column set at six columns while also requiring evidence to be recorded. If a
  reviewer prefers a dedicated nullable `evidence TEXT` column, that is a one-line change to V24 and
  the adapter — worth raising in the PR, but D4 as written is honoured by the composed string (C7).

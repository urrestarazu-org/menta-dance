# Design: Admin view for physical purchases in EXCEPTION (#237)

## Technical Approach

A purely additive read model. One port method, one JPQL projection query, one non-locking batch
read for sessions, three DTO records, one new controller. No domain change, no migration, no
`SecurityConfig` edit, no new exception handler.

The organising principle is **mirror `#33`'s shipped pending-verification list exactly, except
where the `Purchase` aggregate genuinely differs**. Two differences are real and are the whole
technical content of this change:

1. `billing_purchases` carries only `id`, `payment_id`, `status` (V8), so every admin-useful field
   comes from an INNER join to `billing_payments` — and `PurchaseJpaEntity` has **no JPA
   association** to `PaymentJpaEntity`, so the join must be an explicit entity join with `ON`.
2. Every existing read path on `PurchaseRepositoryAdapter` / `PurchaseSessionJpaRepository` is
   `Propagation.MANDATORY` and/or `FOR UPDATE`, because they all run inside a fulfillment
   transaction. A controller-driven read can reuse **neither**. C4 and C3 are that fix.

Design rules applied: ADR-0021 (`domain ← application ← infrastructure`, ArchUnit-enforced);
CLAUDE.md Boilerplate (2026-09-27): `record` for DTOs, `@RequiredArgsConstructor` for the new
constructor-injected controller.

## Components

### C1 — Port: `PurchaseRepository.findInException(int, int)`

```java
// application/port/out/PurchaseRepository.java
ExceptionPurchasePage findInException(int page, int size);
```

Primitives in, application DTO out — byte-identical in shape to the confirmed
`PaymentRepository.findAwaitingManualVerification(int page, int size)` (`PaymentRepository.java:43`).
Javadoc repeats its two load-bearing notes: *"Takes primitives, not a Spring `Pageable`"* and
*"The caller is responsible for rejecting `size > 50`; this port does not clamp."*

| Option | Tradeoff | Decision |
|---|---|---|
| `Page<...> findInException(Pageable)` | `org.springframework.data` types cross into `application`; breaks ArchUnit | Rejected |
| Return `List<Purchase>` + separate count | Loses the joined payment context the row needs; forces N+1 | Rejected |
| **Primitives in, `ExceptionPurchasePage` out** | One more DTO pair to maintain | **Chosen (D3)** |

### C2 — JPQL projection query on `PurchaseJpaRepository`

Confirmed shape to mirror (`PaymentJpaRepository.findByStatusTypeOrderByCreatedAt`, lines 40–51):
`@Query(value = ..., countQuery = ...)` returning `Page<Row>` with a trailing `Pageable`. The
explicit `countQuery` is **mandatory** — Spring Data cannot derive a count from a constructor
expression.

```java
@Query(value = """
    SELECT new com.menta.billing.infrastructure.persistence.projection.ExceptionPurchaseRow(
        pu.id, pu.paymentId, pay.userId, pay.targetModality, pay.targetReference,
        pay.expectedAmount, pay.expectedCurrency, pay.createdAt)
      FROM PurchaseJpaEntity pu
      JOIN PaymentJpaEntity pay ON pu.paymentId = pay.id
     WHERE pu.status = :status
     ORDER BY pay.createdAt ASC
    """,
    countQuery = "SELECT COUNT(pu) FROM PurchaseJpaEntity pu "
        + "JOIN PaymentJpaEntity pay ON pu.paymentId = pay.id WHERE pu.status = :status")
Page<ExceptionPurchaseRow> findByStatusOrderByPaymentCreatedAt(
    @Param("status") String status, Pageable pageable);
```

`JOIN ... ON` (not `pu.payment`) because `PurchaseJpaEntity` maps `payment_id` as a bare `UUID`
column with no `@ManyToOne` — deliberate, and not changed here. The join cannot drop a row:
`payment_id` is `NOT NULL` with `fk_billing_purchases_payment` and
`uq_billing_purchases_payment_id` (V8), a real 1:1.

**Accepted tradeoff (no migration, per proposal):** `billing_purchases` has no index on `status`,
so this is a scan of `billing_purchases` filtered on `status`, PK-joined to `billing_payments`,
then sorted. Acceptable at the expected volume (EXCEPTION rows are rare by construction). Revisit
trigger: if `billing_purchases` exceeds ~10⁵ rows, add `idx_billing_purchases_status`.

### C3 — Sessions: a **new, non-locking** batch read (D6)

The existing `PurchaseSessionJpaRepository.findByPurchaseIdOrderByPositionAsc` is a **native
`... FOR UPDATE` locking query** (lines 36–41), deliberately so for the fulfillment writers. It
must not be reused here: a read-only admin list has no business taking row locks, and it is
per-purchase, i.e. N+1 across a page.

```java
List<PurchaseSessionJpaEntity> findByPurchaseIdInOrderByPurchaseIdAscPositionAsc(
    Collection<UUID> purchaseIds);
```

A derived (non-native, non-locking) query. The adapter issues it **once per page** with the page's
purchase ids, then groups in memory (`Collectors.groupingBy(...::getPurchaseId)`) and maps each
group in `position` order. Served by `uq_billing_purchase_sessions_purchase_position
(purchase_id, position)` (V20) — no new index.

**Empty page short-circuit:** if the projection page is empty the adapter skips this query
entirely and never issues an `IN ()`.

### C4 — Adapter propagation: the trap, and the exact fix

Confirmed on `PurchaseRepositoryAdapter`: `save` → `MANDATORY`, `findByPaymentId` →
`MANDATORY, readOnly`, `findByPaymentIdForUpdate` → `MANDATORY`. Copying any of those onto the new
method makes **every** controller call fail with `IllegalTransactionStateException`, since the
request runs outside any ambient transaction.

The convention to follow is the one `PaymentRepositoryAdapter` already uses for its own
controller-driven read (`PaymentRepositoryAdapter.java:63`) — written explicitly, not bare:

```java
@Override
@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
public ExceptionPurchasePage findInException(int page, int size) { ... }
```

`REQUIRED` starts a transaction when none exists; `readOnly` matches the intent and lets the
sessions read and the projection read share one consistent snapshot.

### C5 — DTOs

| File | Layer | Shape |
|---|---|---|
| `application/dto/ExceptionPurchaseItem.java` | application | `record(UUID purchaseId, UUID paymentId, UUID userId, String targetModality, String targetReference, BigDecimal amount, String currency, Instant createdAt, List<String> physicalSessionIds)` |
| `application/dto/ExceptionPurchasePage.java` | application | `record(List<ExceptionPurchaseItem> items, int page, int size, long totalElements, int totalPages)` — field-for-field `PendingVerificationPage` |
| `infrastructure/persistence/projection/ExceptionPurchaseRow.java` | infrastructure | New. No existing projection carries this column set; mirrors `PendingVerificationRow` |
| `infrastructure/web/dto/ExceptionPurchasePageResponse.java` | infrastructure | `record` + `static from(ExceptionPurchasePage)`, mirroring `PendingVerificationPageResponse.from` |

`physicalSessionIds` is `List<String>` and is **`[]`, never `null`**, for a zero-session row (C7).

### C6 — `PurchaseAdminController`

```java
@RestController
@RequestMapping("/api/v1/admin/billing/purchases")
@PaymentEndpoint
@RequiredArgsConstructor
public class PurchaseAdminController { ... }
```

`@PaymentEndpoint` is the real annotation (`infrastructure/web/controller/PaymentEndpoint.java`,
`@Retention(RUNTIME) @Target(TYPE)`); `PaymentExceptionHandler` is
`@RestControllerAdvice(annotations = PaymentEndpoint.class)` and its `malformedRequest` maps
`IllegalArgumentException → 400` with `code = "INVALID_REQUEST"` (`PaymentExceptionHandler.java:118`).
Reused rather than adding a new marker + advice: that one mapping is all this controller needs.

The handler method copies `PaymentAdminController.list` exactly: `requireAdmin(authentication)`
first, then `if (size > MAX_PAGE_SIZE) throw new IllegalArgumentException(...)`, with
`MAX_PAGE_SIZE = 50` / `DEFAULT_PAGE_SIZE = 20` as private constants and
`@RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size`. `status` is a required
`@RequestParam` in the URL contract; the query is fixed to `EXCEPTION` (D10).

`requireAdmin` is copied verbatim (private static; `ROLE_ADMIN` in authorities, else
`ResponseStatusException(HttpStatus.FORBIDDEN)`).

### C7 — Zero-session EXCEPTION rows (D7) — confirmed factual basis

`Purchase.java:29` — `if (physicalSessionIds.isEmpty() && status != FulfillmentStatus.EXCEPTION)
throw new IllegalArgumentException(...)`. An empty list at `EXCEPTION` is therefore **legal by
construction**, and `Purchase.exception(PaymentId, List<String>)` (`Purchase.java:49`, #238) exists
with exactly the proposal's signature. This read path never rehydrates a `Purchase`, so no
invariant is even exercised — but the row mapper must emit `[]`, not drop or null the field.

### C8 — Security: verified, unchanged

`SecurityConfig` line 331 — `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`. No
earlier-declared matcher covers `/api/v1/admin/billing/**` (the earlier `/admin/*` matchers are
`physical/attendance|courses|sessions|devices` and `virtual/courses|modules|lessons`), so
first-match-wins lands on that generic rule. **No matcher is added.**

Real filter-chain behaviour, from `.exceptionHandling(...)` (lines 337–340) and
`ProblemJsonSecurityHandlers`:

| Caller | Status | `code` | Source |
|---|---|---|---|
| Anonymous | `401` | `AUTHENTICATION_REQUIRED` | `authenticationEntryPoint` |
| Authenticated non-`ADMIN` | `403` | `ACCESS_DENIED` | `accessDeniedHandler` |
| Authenticated non-`ADMIN`, controller reached directly (standalone MockMvc) | `403` | — | `requireAdmin` defense-in-depth |

`Authentication` is never `null` at the controller because the generic rule already rejects
anonymous callers; `requireAdmin` is the second, independent check, as `PaymentAdminController`'s
javadoc documents.

### C9 — Bean wiring: nothing to add

`BillingConfiguration`'s javadoc (line 101) states adapters are `@Component`-scanned; it declares
use-case beans only. `@RestController` and `@Component` are picked up by the existing scan — the
proposal's "Verify" row resolves to **no edit**.

## Data Flow

```
GET /api/v1/admin/billing/purchases?status=EXCEPTION&page=0&size=20
        │
        ▼  SecurityConfig  /api/v1/admin/** → hasRole(ADMIN)   401 anon · 403 non-admin
  PurchaseAdminController.list
        │  requireAdmin → 403 ; size > 50 → IllegalArgumentException
        │                                    └→ PaymentExceptionHandler → 400 INVALID_REQUEST
        ▼
  PurchaseRepository.findInException(page, size)          [application port, primitives only]
        ▼
  PurchaseRepositoryAdapter  @Transactional(REQUIRED, readOnly)
        ├─(1)→ PurchaseJpaRepository  Page<ExceptionPurchaseRow>  purchases ⋈ payments, ORDER BY pay.created_at
        └─(2)→ PurchaseSessionJpaRepository  WHERE purchase_id IN (page ids)   [skipped if page empty]
        ▼  group (2) by purchaseId, join in memory, empty group → []
  ExceptionPurchasePage  →  ExceptionPurchasePageResponse.from(...)  →  200
```

## Testing Strategy

Strict TDD: every row below is a failing test before its production code.

| Layer | What to test | Approach |
|---|---|---|
| Unit — controller | `size=51` → `400`; non-admin → `403` and `verifyNoInteractions(purchaseRepository)`; defaults `page=0,size=20`; body field mapping | `MockMvcBuilders.standaloneSetup(new PurchaseAdminController(mock))` `.setControllerAdvice(new PaymentExceptionHandler())` — the exact `PaymentAdminControllerTest` setup |
| Unit — DTO | `ExceptionPurchasePageResponse.from` maps every field, `[]` sessions survive | Plain JUnit |
| Integration — adapter | oldest-first across two purchases with different `payment.created_at`; `ASSIGNED`/`PENDING_FULFILLMENT` absent; zero-session row present with `[]`; sessions in `position` order; empty result → empty page, `totalElements = 0`; **one** sessions query for an N-row page | Testcontainers MySQL, `:api:billing` adapter test alongside `PurchaseRepositoryAdapterTest` |
| Integration — transaction | the endpoint succeeds **outside** any ambient transaction (the `MANDATORY` trap, C4) | `:api:app` integration test hitting the route, not an adapter-only call |
| Architecture | no `org.springframework.data` type in `com.menta.billing.application` | `./gradlew test --tests "*ArchitectureTest"` |

Gate: `./gradlew check` — ArchUnit + billing coverage (85% domain+application / 85% infrastructure).

## Threat Matrix

N/A — no routing beyond one additive `@GetMapping` under an existing authorization rule, and no
shell, subprocess, VCS/PR automation, executable-file classification, or process-integration
boundary.

## Migration / Rollout

No migration. No schema change, no index, no write path, no new configuration property. Revert of
the merge commit removes the endpoint and nothing else.

## Open Questions

None. D1–D10 are settled in the proposal and each was re-confirmed against source here.

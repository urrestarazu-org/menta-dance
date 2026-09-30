# Design: Bank-transfer physical purchase and capacity exception (#36, US-BILLING-008)

## Technical Approach

The bank-transfer physical rail is the subscription bank-transfer rail *one target down*.
`api:billing` gains one entry port, one use case, one router, one shared availability
helper, one nullable DTO arm and one port-method rename. Nothing downstream of creation
changes: `PaymentFulfillmentService`, the proof/admin path and
`PhysicalCapacityAssignmentOutboxEventHandler`'s `HoldNotFound` branch are reached
byte-identically by an approved physical bank transfer, because they already switch on
`PaymentTarget`, never on `PaymentMethod`.

The only genuinely new decisions are the four the proposal deferred (C1, C4, C5, C7
below). D1–D7 are locked and are not re-opened here.

## Architecture Decisions

### C1 — External reference: `PHY-BT-` + the deterministic `(userId, idempotencyKey)` digest

Closes **Open Question 1**.

The proposal frames the trade-off as *buyer-typable* (`SUB-{paymentId}`) versus
*opaque-but-replayable* (`PHY-` + digest). Read against the source, that framing does not
survive: `PaymentId.toString()` returns `value.toString()` of a `java.util.UUID`
(`PaymentId.java:59-61`), so `CreateBankTransferSubscriptionUseCaseImpl`'s reference
(`:44`, `:100-102`) is `SUB-` + a 36-character random UUID, while the Mercado Pago physical
reference (`CreatePhysicalPurchaseCheckoutUseCaseImpl.java:218-222`) is `PHY-` + a
36-character name-based UUID. **Both are 36-character UUIDs.** Typability is identical;
only replayability differs. With the stated tie-breaker gone, the deterministic scheme wins
on the only axis that still separates them.

| Option | Reference | Replay by `(userId, idempotencyKey)` | Decision |
|---|---|---|---|
| Subscription precedent | `PHY-{paymentId}` | Impossible — random per call | Rejected |
| Physical MP precedent | `PHY-BT-{uuid5(userId:idempotencyKey)}` | `findByExternalReference` | **Chosen** |

```java
private static final String EXTERNAL_REFERENCE_PREFIX = "PHY-BT-";

private static String externalReferenceFor(UUID userId, String idempotencyKey) {
    String seed = userId + ":" + idempotencyKey;
    return EXTERNAL_REFERENCE_PREFIX + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
}
```

**The prefix must differ from the Mercado Pago arm's `PHY-`.** Both physical arms would
otherwise derive the *same* reference from the same `(userId, idempotencyKey)` pair, and a
`BANK_TRANSFER` request replaying a key first used for `MERCADO_PAGO` would resolve an
`AwaitingProvider` payment whose `expectedMerchantAccountId` is the MP merchant id, not the
CBU — then hand the buyer transfer instructions for it. `Payment.matchesExpected`
(`Payment.java:99-103`) would never reconcile that row. Distinct prefixes make the collision
structurally impossible; `uq_billing_payments_external_reference` remains the backstop.
Both stay inside the `PHY-` prefix search ops already uses.

**Idempotency consequence**: creation is idempotent exactly the way the Mercado Pago arm is
— `paymentRepository.findByExternalReference(...)` doubles as the replay lookup, no second
`Payment` row, no new column, no new query. Unlike the subscription rail, there is no
`Subscription` slot-uniqueness backstop to lean on (`saveNewCheckout`'s
`DataIntegrityViolationException`, `SubscriptionRepositoryAdapter.java:62-72`) and no
"one active per user" invariant for purchases, so the deterministic reference is the *only*
available mechanism. A replay is answered after the budget is consumed (C2), so a replay
still costs one unit of the daily budget — deliberate, matching C7's "every request counts"
discipline from #31.

### C2 — `CreateBankTransferPhysicalPurchaseUseCase` port and impl

Exact placement, mirroring `CreateBankTransferSubscriptionUseCase` (`port/in`, no
`Transactional*` decorator of its own — the router's wrapper covers it):

```java
// api/billing/src/main/java/com/menta/billing/application/port/in/
//   CreateBankTransferPhysicalPurchaseUseCase.java
package com.menta.billing.application.port.in;

public interface CreateBankTransferPhysicalPurchaseUseCase {
    PhysicalPurchaseCheckoutResult create(CreatePhysicalPurchaseCheckoutCommand command);
}
```

```java
// api/billing/src/main/java/com/menta/billing/application/usecase/
//   CreateBankTransferPhysicalPurchaseUseCaseImpl.java
package com.menta.billing.application.usecase;

public class CreateBankTransferPhysicalPurchaseUseCaseImpl
        implements CreateBankTransferPhysicalPurchaseUseCase {

    public CreateBankTransferPhysicalPurchaseUseCaseImpl(
        PhysicalCourseQuoteRepository quoteRepository, PaymentRepository paymentRepository,
        PhysicalCourseAvailabilityPort availabilityPort, BankTransferRateLimitPort rateLimitPort,
        Clock clock, BankAccountDetails bankAccountDetails
    ) { … }
}
```

Six collaborators, the same count as its subscription twin. It takes **no**
`PaymentPreferencePort` (no provider call, D1), **no** `PhysicalCapacityHoldPort` (no hold,
D1) and **no** `merchantAccountId` (the CBU replaces it).

Order of operations — budget first, then quote, then availability, then the single write:

1. `rateLimitPort.consumeBankTransferCreation(command.userId())` → `BankTransferRateLimitedException` (429).
2. `findByExternalReference(externalReferenceFor(...))` present → return the same result (C1).
3. `quoteRepository.findById(...).filter(expiresAt > now)` → `PhysicalCourseQuoteExpiredException` (410, D3).
4. `PhysicalCoverageAvailability.requireComplete(...)` → `PhysicalCapacityUnavailableException` (409, non-binding, D7). The returned plan is **discarded**: nothing is reserved and nothing is persisted from it, because the authoritative eligible-session set is recomputed from `confirmedAt` at approval (D1).
5. `Payment.awaitingManualVerification(PaymentId.generate(), userId, quote.getAmount(), externalReference, bankAccountDetails.cbu(), new PaymentTarget.Physical(quote.getId().toString()), now)` → `paymentRepository.save`.
6. `PhysicalPurchaseCheckoutResult.fromBankTransfer(payment, BankTransferInstructions.of(bankAccountDetails, quote.getAmount(), externalReference))`.

Step 3 before step 4 preserves `CreatePhysicalPurchaseCheckoutUseCaseImpl`'s A7 ordering
(`:118-119`): an expired quote is never described by a capacity reading that no longer
applies. Step 1 before step 3 is the subscription rail's own discipline (`:36-39`).

### C3 — `RoutingCreatePhysicalPurchaseCheckoutUseCase` and the `BillingConfiguration` bean

Shape-identical to `RoutingCreateSubscriptionCheckoutUseCase.java:19-38`, same package:

```java
package com.menta.billing.application.usecase;

public class RoutingCreatePhysicalPurchaseCheckoutUseCase
        implements CreatePhysicalPurchaseCheckoutUseCase {

    public RoutingCreatePhysicalPurchaseCheckoutUseCase(
        CreatePhysicalPurchaseCheckoutUseCase mercadoPagoUseCase,
        CreateBankTransferPhysicalPurchaseUseCase bankTransferUseCase
    ) { … }

    @Override
    public PhysicalPurchaseCheckoutResult create(CreatePhysicalPurchaseCheckoutCommand command) {
        return switch (command.paymentMethod()) {
            case MERCADO_PAGO -> mercadoPagoUseCase.create(command);
            case BANK_TRANSFER -> bankTransferUseCase.create(command);
        };
    }
}
```

`BillingConfiguration` changes in exactly two places, mirroring `:224-265`:

- **`createPhysicalPurchaseCheckoutUseCase` (`:479-491`) — modified.** It gains one
  parameter, `CreateBankTransferPhysicalPurchaseUseCase createBankTransferPhysicalPurchaseUseCase`,
  and its return expression becomes
  `new TransactionalCreatePhysicalPurchaseCheckoutUseCase(new RoutingCreatePhysicalPurchaseCheckoutUseCase(mercadoPagoUseCase, createBankTransferPhysicalPurchaseUseCase))`,
  where `mercadoPagoUseCase` is the existing `new CreatePhysicalPurchaseCheckoutUseCaseImpl(...)`
  expression, unchanged argument-for-argument. The transactional decorator moves *outside*
  the router, exactly as `createSubscriptionCheckoutUseCase` does at `:235-237`: one
  all-or-nothing boundary regardless of the delegate picked.
- **`createBankTransferPhysicalPurchaseUseCase` — new bean**, mirroring
  `createBankTransferSubscriptionUseCase` (`:251-265`) including its four
  `billing.bank-transfer.account.*` `@Value`s and `new BankAccountDetails(cbu, alias, holder, cuit)`.
  No own decorator — same rationale recorded at `:240-246`.

`PhysicalPurchaseController` (`:32-36`) binds to `CreatePhysicalPurchaseCheckoutUseCase` and
does not change (D2).

### C4 — The D7 availability check is extracted, as a static helper with no constructor churn

Closes **Open Question 3**.

Today the read is `CreatePhysicalPurchaseCheckoutUseCaseImpl.resolveCoveragePlan`
(`:147-165`): it reads only `availabilityPort`, `quote` and `now`, and returns
`CoveragePlanner.Plan.Complete` or throws `PhysicalCapacityUnavailableException`. It is
already a pure function of its inputs plus one port.

| Option | Cost | Decision |
|---|---|---|
| Duplicate inline in the new impl | Two copies of #208's `INDIVIDUAL`/`MONTHLY` `periodStart` split and `requireAvailable=true`; silent drift between rails | Rejected |
| Extract as an injected collaborator | Changes the MP impl's constructor, its test `setUp`, and its bean — churn on a path the proposal wants byte-identical | Rejected |
| **Extract as a static helper taking the port as a parameter** | The MP impl's private method body becomes a one-line delegation; no constructor, bean, or test-double change anywhere | **Chosen** |

```java
// api/billing/src/main/java/com/menta/billing/application/usecase/PhysicalCoverageAvailability.java
final class PhysicalCoverageAvailability {
    static CoveragePlanner.Plan.Complete requireComplete(
        PhysicalCourseAvailabilityPort availabilityPort, PhysicalCourseQuote quote, Instant now
    ) { /* verbatim body of resolveCoveragePlan */ }
}
```

Package-private, next to `CoveragePlanner` (itself a pure static planner in the same
package). `CoveragePlanner` stays port-free; the port dependency lives in this new class
only. The move is behaviour-preserving by construction, so
`CreatePhysicalPurchaseCheckoutUseCaseImplTest`'s existing 409/410 assertions pass
untouched.

D7's asymmetry is **not** in this helper — it is in what each caller does next. The MP arm
follows it with `physicalCapacityHoldPort.hold(...)`, which makes its 409 a guarantee
(`:167-181`); the bank-transfer arm follows it with nothing, which makes its 409 a courtesy.
Same read, different bindingness, one implementation.

### C5 — `PhysicalPurchaseCheckoutResult` gains a nullable bank-transfer arm

Closes **Open Question 2**. Follow the `SubscriptionCheckoutResult` precedent
(`:22-58`) — a nullable field plus a `fromBankTransfer` factory, not a sealed hierarchy.

Verified: the physical-specific fields do **not** make it awkward. `paymentId`, `quoteId`,
`status` and `externalReference` are produced identically on both rails — `quoteId` comes
from `PaymentTarget.Physical` (`:23-27`), which both arms set. The *only* rail-specific
fields are `providerPreferenceId`/`checkoutUrl` versus `bankTransferInstructions`. There is
no `holdExpiresAt` field to accommodate: the hold deadline is threaded into the provider
preference and never returned (`:189-191`), so no physical-only field forces a split.

```java
public record PhysicalPurchaseCheckoutResult(
    String paymentId, String quoteId, String status, String providerPreferenceId, String checkoutUrl,
    String externalReference, BankTransferInstructions bankTransferInstructions
) {
    public static PhysicalPurchaseCheckoutResult from(Payment payment, PaymentPreferenceResult preference) { … }
    public static PhysicalPurchaseCheckoutResult fromBankTransfer(
        Payment payment, BankTransferInstructions bankTransferInstructions) { … }
    private static PhysicalPurchaseCheckoutResult build(…) { … }
}
```

`statusLabel` already maps `AwaitingManualVerification → "PENDING"` (`:33`) — no change.
`PhysicalPurchaseCheckoutResponse` gains the same nullable field and passes it through,
mirroring `SubscriptionCheckoutResponse:28,34`. A sealed result would force the controller
off its single `ResponseEntity<PhysicalPurchaseCheckoutResponse>` shape and force a
`oneOf` into `billing-v1.yaml`, diverging from the subscription contract for no gain.

### C6 — `consumeSubscriptionCreation` → `consumeBankTransferCreation`: the complete call-site list (D4)

A **method-name-only** rename. The Redis key literal
(`SUBSCRIPTION_CREATION_KEY_PREFIX = "rate:billing-bank-transfer:user:"`,
`RedisBankTransferRateLimitPort.java:31`), the
`billing.bank-transfer.rate-limit.subscription-creation.*` property keys
(`BillingConfiguration.java:276-279`), the limit and the window are **untouched** — renaming
a property key would be a deployment-config break, and renaming the Redis key would reset
live counters. Only the Java method name changes, so the proposal's rollback claim ("live
counters are unaffected in either direction") holds.

Every occurrence, verified exhaustively (`rg consumeSubscriptionCreation`):

| File | Line(s) | Kind |
|---|---|---|
| `billing/application/port/out/BankTransferRateLimitPort.java` | `14` (+ javadoc `13`) | Declaration |
| `billing/infrastructure/security/RedisBankTransferRateLimitPort.java` | `73` | Only implementation |
| `billing/application/usecase/CreateBankTransferSubscriptionUseCaseImpl.java` | `68` | Only production call site |
| `billing/src/test/.../RedisBankTransferRateLimitPortTest.java` | `57, 67, 93, 101, 112, 118` | Test |
| `billing/src/test/.../CreateBankTransferSubscriptionUseCaseImplTest.java` | `73, 157, 165` | Test |

3 production occurrences, 9 test occurrences, 12 total. `RedisBankTransferRateLimitPort` is
the **only** implementor (no test double implements the port; `BillingConfigurationTest` and
the `api:app` integration tests reference the bean/type, never the method).
`consumeProofUpload` and `SubmitPaymentProofUseCaseImpl` are untouched. This slices cleanly
as an isolated first PR with zero behaviour change.

### C7 — Payment expiry sweep: verified, no gap (Open Question 4)

Closes **Open Question 4**. Verified against source, not assumed.

- **The sweep's candidate query does not filter by target.**
  `PaymentJpaRepository.findExpirableBankTransferIds` (`:28-31`) is
  `WHERE p.statusType = 'AWAITING_MANUAL_VERIFICATION' AND p.createdAt < :createdBefore AND
  NOT EXISTS (SELECT 1 FROM PaymentProofJpaEntity pr WHERE pr.paymentId = p.id)`. There is
  no `targetModality` predicate, so a `PaymentTarget.Physical` row is picked up **identically**
  to a `Virtual` one. The method name says "BankTransfer" but the real discriminator is the
  status, which only the manual-verification rail ever produces — Mercado Pago payments are
  born `AwaitingProvider` (`Payment.java:69-72`) and are excluded by construction.
- **The 72h window applies unchanged.** `PaymentExpiryReconciler:49,60` —
  `billing.bank-transfer.expiry.window-hours:72`, `@ConditionalOnProperty` on the class
  (`:32-35`).
- **An expired physical payment leaves no `Purchase`.** `PaymentExpiryWorker.expireOne`
  (`:48-57`) calls `expireAwaitingManualVerification` → `save` → `paymentFulfillmentService.release(expired)`.
  It never calls `ensure`. Since `ensure` (`PaymentFulfillmentService.java:44-49`) is the
  *only* path to `publishPhysicalPaymentCompletedUseCase.handle(payment)`, no
  `billing.PhysicalPaymentCompleted` outbox event is emitted,
  `CreatePurchaseFromPaymentEventUseCase` never runs, and **no `Purchase` row is ever created**.
- **D5 composes correctly.** `release` (`:52-58`) guards on
  `payment.getTarget() instanceof PaymentTarget.Virtual`, so it is a no-op for `Physical`.
  Under D1 nothing was reserved, so there is nothing to release — correct as designed, not a
  gap. The two facts compose: nothing was created, so nothing must be undone.
- **`Payment.expireAwaitingManualVerification`** (`:213-216`) silently no-ops on every other
  status, so a sweep racing an admin decision cannot throw.

**Conclusion: no production change is required in the sweep.** The work is a regression test
asserting a physical `AwaitingManualVerification` payment older than 72h with no proof
expires and leaves zero `billing_purchases` rows. One cosmetic note for the US doc, not a
change: `findExpirableBankTransferIds` and the `billing.bank-transfer.expiry.*` keys now
govern both product lines; renaming them is out of scope.

### C8 — ArchUnit: the rule is name-scoped and must be widened

`ArchitectureTest.checkout_use_case_should_not_depend_on_physical_module` (`:80-86`) is
scoped by `haveSimpleName("CreatePhysicalPurchaseCheckoutUseCaseImpl")`. The requirement
(`physical-purchase-checkout` spec.md:131) is unchanged in intent, but as written the rule
would **not** cover the new classes. Widen the predicate explicitly, keeping the existing
name-scoped style:

```java
noClasses()
    .that().haveSimpleName("CreatePhysicalPurchaseCheckoutUseCaseImpl")
    .or().haveSimpleName("CreateBankTransferPhysicalPurchaseUseCaseImpl")
    .or().haveSimpleName("RoutingCreatePhysicalPurchaseCheckoutUseCase")
    .should().dependOnClassesThat().resideInAPackage("com.menta.physical..")
```

**Confirmed and stated explicitly (D1):** `CreateBankTransferPhysicalPurchaseUseCaseImpl`
imports nothing from `com.menta.physical..` **and nothing from `com.menta.shared.physical..`
either**. The Mercado Pago arm needs `MultiSessionCapacityHoldCommand` / `SessionClaim`
(`CreatePhysicalPurchaseCheckoutUseCaseImpl.java:24-25`) only because it creates a hold; with
no hold there is no command to build, no port to call and no cross-module edge of any kind.
Its entire collaborator set lives in `com.menta.billing.application.{dto,port,usecase}`.

### C9 — Everything after creation is verify-only

`PaymentFulfillmentService.ensure` already routes `Physical`
(`:46`), `ResolvePaymentProofUseCaseImpl`, `SubmitPaymentProofUseCaseImpl`,
`PaymentController` and `PaymentAdminController` are target-agnostic, and
`PhysicalCapacityAssignmentOutboxEventHandler`'s `HoldNotFound` branch already recomputes
eligible sessions from `confirmedAt`. **Zero production edits** in these classes; the work is
integration coverage proving the physical bank-transfer path through them (`ASSIGNED`,
`EXCEPTION`, rejection).

## Data Flow

    POST /api/v1/billing/physical/purchases   { quoteId, paymentMethod, idempotencyKey }
      ┌── TransactionalCreatePhysicalPurchaseCheckoutUseCase  @Transactional ──────────────┐
      │  RoutingCreatePhysicalPurchaseCheckoutUseCase.switch(paymentMethod)          (C3)  │
      │    MERCADO_PAGO ─→ CreatePhysicalPurchaseCheckoutUseCaseImpl  (byte-identical)     │
      │                     replay → quote → PhysicalCoverageAvailability → HOLD → prefer. │
      │    BANK_TRANSFER ─→ CreateBankTransferPhysicalPurchaseUseCaseImpl            (C2)  │
      │                     consumeBankTransferCreation ──── limited ──→ 429         (C6)  │
      │                     findByExternalReference(PHY-BT-uuid5) present → same result(C1)│
      │                     quote expired ──────────────────────────────→ 410        (D3)  │
      │                     PhysicalCoverageAvailability.requireComplete → 409  (C4, D7)   │
      │                       └─ plan DISCARDED · no hold · no provider call         (D1)  │
      │                     Payment.awaitingManualVerification(target=Physical(quoteId))   │
      └──────────────────────── COMMIT ──→ 201 { …, bankTransferInstructions }       (C5)  │

    buyer transfers ─→ POST /payments/{id}/proof  (unchanged)  ─→ admin inbox (unchanged)

    approve ─→ PaymentFulfillmentService.ensure → PaymentTarget.Physical
                 → billing.PhysicalPaymentCompleted → outbox handler → HoldNotFound branch
                 → CoveragePlanner(confirmedAt, requireAvailable=false)
                     ├─ Complete  → AssignCapacityUseCase → MarkPurchaseAssignedUseCase  → ASSIGNED
                     └─ Partial   → MarkPurchaseExceptionUseCase (+ #209 notice)        → EXCEPTION

    reject  ─→ PaymentFulfillmentService.release → Virtual-only guard → NO-OP        (D5)
                 → ensure never runs → no event → no Purchase, no assignment
    72h, no proof ─→ PaymentExpiryReconciler → PaymentExpiryWorker.expireOne → EXPIRED
                 → release NO-OP → ensure never runs → no Purchase                   (C7)

## File Changes

| File | Action | Description |
|---|---|---|
| `billing/application/port/in/CreateBankTransferPhysicalPurchaseUseCase.java` | Create | Entry port, sibling of `CreateBankTransferSubscriptionUseCase` (C2) |
| `billing/application/usecase/CreateBankTransferPhysicalPurchaseUseCaseImpl.java` | Create | Budget → replay → quote → availability → `Payment` (C1, C2) |
| `billing/application/usecase/RoutingCreatePhysicalPurchaseCheckoutUseCase.java` | Create | Two-arm `paymentMethod` dispatch (C3, D2) |
| `billing/application/usecase/PhysicalCoverageAvailability.java` | Create | Static helper; verbatim body of `resolveCoveragePlan` (C4) |
| `billing/application/usecase/CreatePhysicalPurchaseCheckoutUseCaseImpl.java` | Modify | **Only** `resolveCoveragePlan`'s body → one delegation line; guard, constructor and every other line unchanged (C4) |
| `billing/application/dto/PhysicalPurchaseCheckoutResult.java` | Modify | `+bankTransferInstructions` (nullable) + `fromBankTransfer` (C5) |
| `billing/infrastructure/web/dto/PhysicalPurchaseCheckoutResponse.java` | Modify | Same nullable field, passed through (C5) |
| `billing/application/port/out/BankTransferRateLimitPort.java` | Modify | `consumeSubscriptionCreation` → `consumeBankTransferCreation` + javadoc (C6) |
| `billing/infrastructure/security/RedisBankTransferRateLimitPort.java` | Modify | `@Override` rename only; key literal, limit, window untouched (C6) |
| `billing/application/usecase/CreateBankTransferSubscriptionUseCaseImpl.java` | Modify | Call-site rename, line 68 (C6) |
| `billing/infrastructure/config/BillingConfiguration.java` | Modify | New bank-transfer bean; `createPhysicalPurchaseCheckoutUseCase` wraps the router (C3) |
| `billing/src/test/.../ArchitectureTest.java` | Modify | Widen the name-scoped physical-module rule to the three classes (C8) |
| `billing/infrastructure/web/controller/PhysicalPurchaseController.java` | **Unchanged** | Binds to the interface; the router is transparent (D2) |
| `billing/application/usecase/PaymentFulfillmentService.java` | **Unchanged** | `ensure` routes `Physical`; `release` no-op verified (C7, D5) |
| `billing/infrastructure/scheduling/PaymentExpiry{Reconciler,Worker}.java` | **Unchanged** | Target-agnostic; verified, no gap (C7) |
| `api/app/.../PhysicalCapacityAssignmentOutboxEventHandler.java` | **Unchanged** | `HoldNotFound` branch is now the intended path (C9, D1) |
| `billing`/`app` `src/test/**` | Create/Modify | Creation, routing, rename, DTO, sweep, and `ASSIGNED`/`EXCEPTION` integration coverage |
| `api/openapi/billing-v1.yaml`, `bruno/API - Direct/billing/` | Modify | `BANK_TRANSFER` + `bankTransferInstructions` on the physical checkout contract |
| `docs/user-stories/US-BILLING-008.md` | Modify | Record D1's and D6's divergences from the issue's literal wording |

## Interfaces / Contracts

```
POST /api/v1/billing/physical/purchases
  { "quoteId": "<uuid>", "paymentMethod": "BANK_TRANSFER", "idempotencyKey": "<non-blank>" }
  201 { paymentId, quoteId, status: "PENDING", providerPreferenceId: null, checkoutUrl: null,
        externalReference: "PHY-BT-<uuid>",
        bankTransferInstructions: { cbu, alias, holder, cuit, amount{…}, reference } }
  410 PHYSICAL_COURSE_QUOTE_EXPIRED   · quote expired or unknown, no Payment written
  409 PHYSICAL_CAPACITY_UNAVAILABLE   · best effort, reserves nothing, guarantees nothing (D7)
  429 BANK_TRANSFER_RATE_LIMITED      · shared 10/user/day budget with the subscription rail (D4)
  paymentMethod: MERCADO_PAGO → byte-identical to today (hold, preference, checkoutUrl)
```

No new endpoint, no new security matcher, no new exception handler, no new property, no
Flyway migration.

## Testing Strategy

| Layer | What to test | Approach |
|---|---|---|
| Unit (application) | Creation writes exactly one `Payment` with `AwaitingManualVerification`, the CBU as `expectedMerchantAccountId`, `PaymentTarget.Physical(quoteId)` and the quote's amount; **no** provider and **no** hold port interaction | Mockito + `verifyNoInteractions(paymentPreferencePort, physicalCapacityHoldPort)` |
| Unit (application) | Strict order: budget → replay lookup → quote → availability → save | `InOrder` |
| Unit (application) | 429 before any read; 410 on expired/unknown quote with zero writes; 409 on a visibly-full quote with zero writes | One RED test per branch |
| Unit (application) | Replay of the same `(userId, idempotencyKey)` returns the same result and writes no second `Payment`; the budget is still consumed | Stubbed `findByExternalReference` |
| Unit (application) | A `MERCADO_PAGO` reference and a `BANK_TRANSFER` reference for the **same** `(userId, idempotencyKey)` differ (C1 prefix collision) | Direct assertion on both derivations |
| Unit (routing) | `MERCADO_PAGO` → MP delegate, `BANK_TRANSFER` → bank-transfer delegate; the router adds no precondition; `verifyNoInteractions` on the other arm | Mirror of `RoutingCreateSubscriptionCheckoutUseCaseTest` |
| Unit (regression) | `CreatePhysicalPurchaseCheckoutUseCaseImplTest` passes **untouched** after the C4 extraction and the C6 rename | Existing suite, no edits beyond the method name |
| Unit (rate limit) | `RedisBankTransferRateLimitPortTest` passes with only the method name changed — same key, same limit, same window | Existing suite |
| Unit (DTO) | `fromBankTransfer` leaves `providerPreferenceId`/`checkoutUrl` null and populates instructions; `from` leaves instructions null | Record assertions |
| Integration | `201` with CBU/alias/holder/CUIT/amount/reference; **zero** `physical_capacity_holds` rows; zero provider calls | Testcontainers MySQL + stub preference port |
| Integration | Proof upload → admin approve → `ASSIGNED` with one `physical_capacity_assignments` row per eligible session computed from `confirmedAt` (scenario 1) | Full app context |
| Integration | Capacity unavailable at approval → `EXCEPTION`, all-or-nothing, zero partial assignments, #209 event emitted (scenario 2) | Full app context |
| Integration | Admin reject → no `Purchase`, no assignment, `release` no-op (scenario 3, D5) | Full app context |
| Integration | 11th bank-transfer creation in one day → `429` whether the prior ten were subscriptions, physical purchases, or a mix (D4) | Testcontainers Redis |
| Integration | A physical `AwaitingManualVerification` payment older than 72h with no proof → `EXPIRED`, **zero** `billing_purchases` rows (C7) | Extends `PaymentExpirySweepIntegrationTest` |
| Architecture | The widened rule covers all three classes; no `com.menta.physical..` **or** `com.menta.shared.physical..` reference from the new impl (C8) | `ArchitectureTest` |
| Coverage | 95% domain+application, 90% infrastructure on `:api:billing` | `./gradlew :api:billing:test jacocoTestCoverageVerification` |

**TDD order (RED → GREEN per slice):** rename → router + port + impl → C4 extraction → DTO
arm → sweep regression → integration `ASSIGNED`/`EXCEPTION`/reject → OpenAPI/Bruno/US doc.

## Threat Matrix

N/A — no shell command, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. `RoutingCreatePhysicalPurchaseCheckoutUseCase` is in-process
dispatch over a closed two-value `PaymentMethod` enum with an exhaustive `switch`, not
external routing. The change's two adversarial surfaces are closed structurally instead:
creation abuse by the shared per-user daily budget consumed before any read (C2, C6), and
cross-rail reference confusion by the distinct `PHY-BT-` prefix plus the existing
`uq_billing_payments_external_reference` (C1).

## Migration / Rollout

**No migration.** No Flyway script, no column, no table, no index, no new configuration
property — the bank-transfer bean reuses the four `billing.bank-transfer.account.*` values
already required by `createBankTransferSubscriptionUseCase`. No feature flag: the
`BANK_TRANSFER` arm is unreachable until this merge lands, and rollback follows the
proposal's plan unchanged (the MP-only `IllegalArgumentException` guard is never removed).

Three independently deliverable slices (`sdd-tasks` owns the authoritative 400-line guard):

1. **Rate-limit rename (C6)** — port, Redis adapter, one production call site, two test
   classes. Twelve mechanical occurrences, zero behaviour change. Inert on its own.
2. **Use case + router + wiring (C1, C2, C3, C4, C8)** — port, impl, router, the C4
   extraction, `BillingConfiguration`, the ArchUnit widening, and their unit tests.
3. **DTO/contract + integration coverage (C5, C7, C9)** — result/response arm, OpenAPI,
   Bruno, the US doc, and the `ASSIGNED`/`EXCEPTION`/reject/sweep integration tests.

## Requirement → Component Map

| # | Requirement | Components | Slice |
|---|---|---|---|
| R1 | `BANK_TRANSFER` creation on the existing endpoint returns instructions | C1, C2, C3, C5 | **2**/**3** |
| R2 | No hold, no assignment, no provider call at creation (D1) | C2, C4 | **2** |
| R3 | Expired quote → 410 with no `Payment` (D3) | C2 | **2** |
| R4 | Visibly-full quote → 409, best effort, non-binding (D7) | C4 | **2** |
| R5 | Shared per-user daily bank-transfer creation budget (D4) | C6 | **1** |
| R6 | Approval → `ASSIGNED` or `EXCEPTION`, no new fulfillment code (D1, D6) | C9 | **3** |
| R7 | Rejection leaves no `Purchase` and releases nothing (D5) | C7, C9 | **3** |
| R8 | 72h expiry leaves no `Purchase` | C7 | **3** |
| R9 | The new use case references no `com.menta.physical..` (D1) | C8 | **2** |
| — | Mercado Pago path byte-identical | C3, C4 (pure move) | **2** |

## Open Questions

None blocking. Four facts for `sdd-tasks` to carry as **facts, not questions**:

- **`CreatePhysicalPurchaseCheckoutUseCaseImpl` IS modified** (one method body → one
  delegation line, C4), refining the proposal's "Unchanged". Its constructor, its non-MP
  guard and its behaviour are untouched.
- **`ArchitectureTest` IS modified** (C8) — the existing rule is name-scoped and would not
  otherwise cover the new classes. The spec requirement itself is unchanged.
- **The bank-transfer external-reference prefix is `PHY-BT-`, not `PHY-`** (C1), and that
  difference is load-bearing, not cosmetic.
- **The expiry sweep needs no production change** (C7) — verified against
  `PaymentJpaRepository:28-31`, `PaymentExpiryWorker:48-57` and
  `PaymentFulfillmentService:52-58`. The task is a regression test, not a fix.

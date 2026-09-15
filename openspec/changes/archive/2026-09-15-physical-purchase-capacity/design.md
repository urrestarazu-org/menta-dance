# Design: Physical Purchase and Automatic Capacity Assignment

## Technical Approach

Three additive pieces on top of #115's proven fulfillment path, plus one
structural change to `Purchase`.

1. **`Purchase` becomes 1:N** through a child table (A1). Nothing else about the
   aggregate moves: `pendingFulfillment / assigned / exception`, the
   `uq_billing_purchases_payment_id` idempotency key and
   `CreatePurchaseFromPaymentEventUseCase` keep their exact shapes.
2. **Coverage becomes a pure function** in Billing's `application` layer (A5),
   fed by the out-port Billing *already owns* — `PhysicalCourseAvailabilityPort`,
   wired in `api:app` by `PhysicalCourseAvailabilityAdapter`. The same function
   answers the checkout `409` (A6) and the confirmation-time session set; they
   differ only in the instant passed in, which is precisely D5's honesty caveat
   expressed as code rather than prose.
3. **`AssignCapacityUseCase` grows an ordered, all-or-nothing multi-session
   entry point** (A3) with one `REQUIRES_NEW` transaction for the whole set, and
   the existing single-session `assign` becomes a delegation to it.

`shared` grows exactly one type. Physical still never names `Payment` or
`Purchase`; `api:app` remains the only place both module graphs meet.

## Architecture Decisions

### A1 — `Purchase` 1:N via a child table, ordered by an explicit `position`

`billing_purchase_sessions (purchase_id, position, physical_session_id)`, with
`Purchase` holding an ordered `List<String> physicalSessionIds` and the singular
`physicalSessionId` field deleted.

| Option | Tradeoff | Verdict |
|---|---|---|
| Child table + ordered list on the aggregate | Queryable by session (needed by any future reconciliation and by the migration-parity test), FK-enforceable, and `position` makes the claim order (A2) durable and auditable rather than reconstructed | **Chosen** |
| JSON / CSV column on `billing_purchases` | No DDL churn, but unqueryable, unconstrained, and the first place in the schema where a list is opaque to SQL — no precedent in this codebase | Rejected |
| N `billing_purchases` rows per payment | Directly contradicts `uq_billing_purchases_payment_id`, which is the entire idempotency mechanism of #115 | Rejected |

No per-session status column: all-or-nothing (A3) makes the purchase's single
`FulfillmentStatus` complete information. Adding a per-row status would invent a
partial state the NFR forbids from existing.

**Forward migration (V20)**: create the child table; `INSERT ... SELECT id, 0,
physical_session_id FROM billing_purchases`; then drop
`billing_purchases.physical_session_id`. Every pre-existing row is single-session
by construction, so the backfill is a total, lossless projection. Same
one-migration-per-logical-change discipline as V18.

**Reverse migration (V21-style compensating)**: re-add
`physical_session_id VARCHAR(64) NULL`, backfill from `position = 0`, set NOT
NULL, drop the child table. It is lossy for any purchase with more than one
session, so it must **refuse** rather than silently truncate. MySQL 8 has no
`SIGNAL` outside a compound statement, so the guard is a single-row insert into a
`CHECK`-constrained scratch table:

```sql
CREATE TABLE _rollback_guard (multi_session_purchases INT NOT NULL,
    CONSTRAINT chk_no_multi_session CHECK (multi_session_purchases = 0));
INSERT INTO _rollback_guard
SELECT COUNT(*) FROM (SELECT purchase_id FROM billing_purchase_sessions
    GROUP BY purchase_id HAVING COUNT(*) > 1) multi;
```

A non-zero count aborts the migration with a constraint violation. Rollback is
therefore safe exactly while no `MONTHLY` purchase has been fulfilled — stated as
an operational precondition, not assumed. The parity test the proposal demands
asserts `COUNT(billing_purchases) == COUNT(DISTINCT purchase_id)` and
`COUNT(billing_purchase_sessions) == COUNT(billing_purchases)` across V20, and
byte-equal `physical_session_id` values after the reverse pass.

### A2 — Claim order is `(scheduledAt ASC, sessionId ASC)`, enforced at the `shared` boundary

This is a correctness decision, not a convention.

InnoDB acquires locks on the `uq_physical_assignment_session_student` index entry
at INSERT time, and on the existing entry when a duplicate key is hit. Two
overlapping `MONTHLY` purchases for the same course claim overlapping — often
identical — session sets. If writer T1 claims `S1, S2` while T2 claims `S2, S1`,
T1 can hold `S1` while waiting on `S2` and T2 the mirror image: a lock cycle,
which InnoDB resolves by killing a victim with `CannotAcquireLockException`. That
victim is a buyer who genuinely had room, converted into a spurious `EXCEPTION`
on a settled payment — a wrong outcome produced purely by iteration order. Under
one **total order** applied by every writer, lock acquisition forms a chain, never
a cycle, so this class of deadlock is impossible by construction rather than
merely rare.

Why `scheduledAt` first, `sessionId` as tiebreaker:

- `scheduledAt` is already the order D4's coverage is *defined* in ("the next N
  scheduled sessions"). The claim order is then a free by-product of computing
  coverage — there is no second sort that can drift out of sync with the first.
- Session UUIDs are v4/random: a valid total order, but arbitrary, undebuggable,
  and it would force a second, independent sort whose only job is locking.
- Two sessions of one course can in principle share a `scheduledAt`, so
  `scheduledAt` alone is not total. `sessionId` ASC closes it.

Enforcement lives in the neutral contract, not in a comment: the new `shared`
command carries `(sessionId, scheduledAt)` pairs and its compact constructor
rejects a non-ascending, empty, or duplicate-bearing list. Any future writer —
including #208's hold path — either sorts correctly or fails loudly at
construction. That is what makes the ordering hold "across every concurrent
writer" instead of by convention.

### A3 — One `REQUIRES_NEW` transaction for the whole ordered set

`PhysicalCapacityAssignmentPort` gains
`CapacityAssignments assignAll(MultiSessionCapacityAssignmentCommand)`.
`assign(CapacityAssignmentCommand)` is **kept** and reimplemented as a delegation
to `assignAll` with a singleton list, so there is exactly one implementation of
the capacity invariant and the N=1 path cannot drift.

The tension the proposal flags is real but resolves cleanly once the *reason* for
`REQUIRES_NEW` is separated from its *granularity*. #115 chose it so that a
capacity failure rolls back only the assignment attempt, leaving `api:app`'s
transaction usable to persist the `EXCEPTION` residual. That reason is unchanged
at N. What must change is where the boundary sits: N invocations of today's
method would be N independent transactions — exactly the partial-assignment
hazard. `assignAll` puts the single `REQUIRES_NEW` boundary *around the whole
loop*:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public CapacityAssignments assignAll(MultiSessionCapacityAssignmentCommand cmd) {
    for (SessionClaim claim : cmd.claims()) {   // already ordered (A2)
        claimOne(claim.sessionId(), cmd.studentId());  // private: read invariant, insert, flush
    }
    return new CapacityAssignments(cmd.sessionIds());
}
```

`claimOne` is a private method with **no** propagation annotation — a
self-invocation would not be proxied anyway, and annotating it would only mislead.
Each iteration keeps the established idiom verbatim: read the live invariant via
`findByIdWithAvailability`, insert, and `flush()` **per insert** (not once at the
end) so the unique-index decision is made and attributed before moving to session
k+1, matching `SubscriptionRepositoryAdapter.saveNewCheckout:62-72`.

No partial assignment can survive: a `CapacityBelowAssignedException` on session k
propagates out of the transactional proxy, marking the transaction rollback-only,
so inserts `1..k-1` are discarded by InnoDB. Nothing compensates by hand; the
guarantee is the transaction, and the integration test asserts it as a literal
`COUNT(*) = 0` over `physical_capacity_assignments` for that student.

### A4 — Coverage extends forward to satisfy the count; it never truncates

D4 says a full monthly price buys a full count of classes. The count is the
promise; the calendar is only the means of delivering it. Truncating to whatever
happens to remain would deliver fewer classes for the same money and — worse,
under A3 — would turn an ordinary calendar edit into an `EXCEPTION` on an
already-settled payment. So coverage is:

> the first `scheduledSessionCount` sessions of the course that are `SCHEDULED`
> with `scheduledAt >= confirmedAt`, ordered by `(scheduledAt, sessionId)`,
> searched forward within `COVERAGE_LOOKAHEAD = 120 days` of `confirmedAt`.

The horizon exists so the search terminates and so an abandoned course cannot
silently sell classes years out. 120 days comfortably clears any monthly cadence
(a weekly 4–8 session month fits in ~60 days) with slack for holiday gaps. If
fewer than `scheduledSessionCount` sessions exist inside the horizon, that is a
genuine impossibility, not a partial delivery: `EXCEPTION` with
`Reason.TARGET_NOT_SCHEDULED`, `Payment` stays `COMPLETED`. This reuses the
existing residual path verbatim.

`INDIVIDUAL` does not go through the planner's window at all: N=1 and the session
is the quote's own `selectedSessionId`, which the buyer picked. It still travels
the same `assignAll` path as a one-element ordered list.

### A5 — Coverage lives in Billing's `application`; `shared` grows one type

| Concern | Owner | Why |
|---|---|---|
| What sessions exist and when | `api:physical` | Owns the calendar; already exposed via its `PhysicalCourseAvailabilityPort` in-port |
| Which of them this price bought | `api:billing` application | It is Billing's promise about its own product; Purchase is Billing's aggregate |
| Wiring the two | `api:app` | `PhysicalCourseAvailabilityAdapter` already does exactly this |

So **no new cross-module bridge is needed for the read**. Billing's existing out
port `PhysicalCourseAvailabilityPort.findScheduledSessions(courseId, periodStart,
periodEndExclusive)` returns `ScheduledSessionSnapshot(sessionId, scheduledAt,
availableSpots)` — every field A4 and A6 need. A new `CoveragePlanner` in
`billing/application` is a pure function over that list plus an `Instant`: no
Spring, no I/O, trivially unit-testable against billing's 100 % domain+application
floor.

`shared` grows exactly one type, in `com.menta.shared.physical`, in the identical
style to `CapacityAssignmentCommand` (plain record, `requireNonNull`, zero
annotations, javadoc naming producer and consumer):

```java
public record MultiSessionCapacityAssignmentCommand(
    List<SessionClaim> claims, UUID studentId, UUID paymentId) {

    public record SessionClaim(UUID sessionId, Instant scheduledAt) { }

    public MultiSessionCapacityAssignmentCommand {
        // non-null; non-empty; no duplicate sessionId;
        // strictly ascending by (scheduledAt, sessionId) — A2's total order,
        // enforced here so no writer can deadlock by iterating differently.
        claims = List.copyOf(claims);
    }
}
```

The result stays in Physical's application layer, following `AssignmentOutcome`'s
existing placement rather than promoting it to `shared`:
`record CapacityAssignments(List<UUID> assignedSessionIds)`.

**`PaymentTarget.Physical`'s reference becomes the `quoteId`, not a sessionId.**
Under D2 the quote is the durable snapshot that carries `purchaseType`,
`courseId`, `scheduledSessionCount` and `selectedSessionId` — everything
confirmation needs — while a single session id cannot represent a `MONTHLY`
purchase at all. No DDL and no data migration: nothing in the codebase has ever
constructed a `PaymentTarget.Physical` outside test fixtures (the same
empty-by-construction argument V14 made for `billing_payments`), the column stays
`VARCHAR`, and only the meaning of newly written values changes. A reference that
resolves to no quote routes to the already-specified `EXCEPTION /
TARGET_NOT_SCHEDULED` — no compatibility branch is invented for rows that do not
exist.

### A6 — The `409` reuses the planner, from Billing, through the port it already owns

`CreatePhysicalPurchaseCheckoutUseCase` calls the *same* `CoveragePlanner` with
`clock.now()` instead of `confirmedAt`. If it cannot fill `scheduledSessionCount`
sessions with `availableSpots > 0` inside the horizon, it throws
`PhysicalCapacityUnavailableException` → `409 CAPACITY_UNAVAILABLE` and no
`Payment` is created.

This creates **no** new Billing→Physical dependency: Billing calls its own out
port, and `api:app` supplies the implementation — literally the mechanism that
already serves the quote's informative `QuoteAvailability`. Sharing one planner
between checkout and confirmation is what makes the two answers agree by
construction; they can only disagree because time passed, which is exactly and
only what D5 promises. Nothing here reserves anything.

## Data Flow

    POST /billing/physical/purchases {quoteId}
        │ CreatePhysicalPurchaseCheckoutUseCase
        ├─ quote expired / consumed ─────────────→ 410 QUOTE_EXPIRED  (A7)
        ├─ CoveragePlanner.plan(sessions, now) ── cannot fill N ─→ 409 CAPACITY_UNAVAILABLE (A6)
        └─ Payment.awaitingProvider(PaymentTarget.Physical(quoteId)) → MP preference → 201

### A7 — expired quote is `410`, not `409`

Validity is checked **before** availability, and the two failures get
different statuses on purpose.

`410 Gone` follows the precedent already set in this codebase:
`PasswordResetExceptionHandler` maps `PasswordResetTokenExpiredException` to
`HttpStatus.GONE` for exactly this shape — a time-limited artifact that
expired, whose remedy is to obtain a fresh one. A quote is the same shape.

`409` is not available here anyway: A6 owns it on this same route. Two
distinct failures sharing one status on one endpoint forces the caller to
parse the ProblemDetail code to tell "ask for a new quote" apart from "this
class is full" — two completely different next actions for the user.

Ordering matters for the same reason: an expired quote's availability
reading describes a course state the caller is no longer entitled to act
on, so reporting `409` for a stale quote would be actively misleading.

    provider webhook → PaymentVerificationService → COMPLETED (commit)
        └─ app_outbox: billing.PhysicalPaymentCompleted        [unchanged]

    OutboxWorker → PhysicalCapacityAssignmentOutboxEventHandler
        ├─ load Payment → studentId, quoteId
        ├─ load quote → courseId, purchaseType, scheduledSessionCount, selectedSessionId
        ├─ MONTHLY : CoveragePlanner.plan(findScheduledSessions(...), confirmedAt)  (A4)
        │  INDIVIDUAL: [selectedSessionId]
        ├─ shortfall inside horizon ──→ markException(TARGET_NOT_SCHEDULED)
        ├─ createPurchaseFromPaymentEvent(payload)   [idempotent on payment_id, unchanged]
        └─ assignAll(ordered claims)  ── ok ──→ purchase.assigned()
                    │  one REQUIRES_NEW tx (A3)
                    └─ CapacityBelowAssignedException ─→ markException(CAPACITY_BELOW_ASSIGNED)
                       (every insert in the set rolled back — zero rows)

## File Changes

| File | Action | Description |
|---|---|---|
| `billing/domain/model/Purchase.java` | Modify | `String physicalSessionId` → ordered `List<String> physicalSessionIds`; factories/`assigned()`/`exception()` carry the list (A1) |
| `billing/infrastructure/persistence/entity/PurchaseJpaEntity.java` | Modify | Drop the column field; `@ElementCollection`/`@OneToMany` ordered by `position` |
| `billing/infrastructure/persistence/entity/PurchaseSessionJpaEntity.java` | Create | Child row `(purchase_id, position, physical_session_id)` |
| `billing/infrastructure/persistence/mapper/PurchaseJpaMapper.java` | Modify | List mapping, order-preserving |
| `api/app/src/main/resources/db/migration/V20__billing_purchase_sessions.sql` | Create | Child table + backfill + drop singular column (A1) |
| `.../V21__revert_billing_purchase_sessions.sql` | Create | Guarded reverse migration; not applied in normal rollout (A1) |
| `billing/application/usecase/CoveragePlanner.java` | Create | Pure `plan(List<ScheduledSessionSnapshot>, Instant, int, boolean requireAvailable)` (A4, A5, A6) |
| `billing/application/usecase/CreatePhysicalPurchaseCheckoutUseCaseImpl.java` + in-port + DTOs | Create | Quote→user→`Payment` PENDING, MP preference, `409` (A6) |
| `billing/domain/exception/PhysicalCapacityUnavailableException.java` | Create | `BusinessException`, code `CAPACITY_UNAVAILABLE` |
| `billing/infrastructure/web/controller/PhysicalPurchaseController.java` + handler + DTOs | Create | `POST /api/v1/billing/physical/purchases` |
| `billing/domain/model/PaymentTarget.java` | Modify | `Physical`'s reference is the quoteId (A5) |
| `billing/application/usecase/CreatePurchaseFromPaymentEventUseCase.java` | Modify | Accept the resolved session list; `payment_id` idempotency untouched |
| `billing/infrastructure/config/BillingConfiguration.java` | Modify | New beans |
| `shared/.../physical/MultiSessionCapacityAssignmentCommand.java` | Create | Ordered neutral contract with the A2 invariant |
| `physical/application/port/in/PhysicalCapacityAssignmentPort.java` | Modify | `+ assignAll(...)` |
| `physical/application/usecase/AssignCapacityUseCase.java` | Modify | Single `REQUIRES_NEW` around the ordered loop; `assign` delegates (A3) |
| `physical/application/usecase/CapacityAssignments.java` | Create | Typed result |
| `api/app/.../billing/PhysicalCapacityAssignmentAdapter.java` | Modify | `+ assignAll` delegation |
| `api/app/.../outbox/PhysicalCapacityAssignmentOutboxEventHandler.java` | Modify | Resolve quote, plan coverage, issue one N-session command |
| `api/openapi/billing-v1.yaml` | Modify | New endpoint, `409 CAPACITY_UNAVAILABLE` |

## Testing Strategy

| Layer | What to test | Approach |
|---|---|---|
| Domain (`PurchaseTest`) | Ordered list preserved through `assigned()`/`exception()`; empty list rejected; `grantsAttendance` unchanged | Plain JUnit |
| Domain (`MultiSessionCapacityAssignmentCommandTest`, in `shared`) | Rejects empty, duplicate, and non-ascending claim lists; accepts a correctly ordered one; defensive copy | Plain JUnit — this is A2's enforcement point |
| Application (`CoveragePlannerTest`) | Exactly N picked; forward extension past a calendar gap (A4); shortfall inside the horizon returns "cannot fill"; horizon boundary inclusive/exclusive; `requireAvailable` filters `availableSpots = 0` (A6); order is `(scheduledAt, sessionId)` with a same-instant tiebreak | Pure function, no mocks — carries the 100 % billing floor |
| Application (`CreatePhysicalPurchaseCheckoutUseCaseImplTest`) | Expired/consumed quote; visibly-full → `PhysicalCapacityUnavailableException` and **no** `paymentRepository.save`; happy path writes `Payment` before calling the provider | Mockito + fixed `Clock`, mirroring `CreateSubscriptionCheckoutUseCaseImplTest` |
| Application (`AssignCapacityUseCaseTest`) | N=1 delegation equals old behaviour; failure at session k throws and attempts no k+1 insert; ordered iteration | Mockito on the writer, verifying call order |
| Adapter (`AssignCapacityAdapterIntegrationTest`, extend) | All-or-nothing: session 3 of 3 full ⇒ **zero** `physical_capacity_assignments` rows for that student; success ⇒ exactly N | Testcontainers, existing class |
| Migration (`PurchaseSessionsMigrationIntegrationTest`) | Row-count parity across V20; value equality after the guarded reverse pass; the guard aborts when a multi-session purchase exists | Mirrors `SubscriptionTrialMigrationIntegrationTest` |
| Integration — concurrency | **Clone `PhysicalSessionManagementIntegrationTest.concurrent_payments_for_same_session_capacity_one_resolves_one_is_exception:381`** — it already drives the real webhook→outbox→assign path on two threads and asserts one assignment row plus one `EXCEPTION`. Extend it to two overlapping `MONTHLY` purchases over the same session set, and borrow the `CountDownLatch` starting gun from `SubscriptionCheckoutIntegrationTest:384` (the only latch precedent in the repo) so both writers truly interleave. Assert: no deadlock exception surfaces, one purchase `ASSIGNED` with N rows, the other `EXCEPTION` with zero rows | Testcontainers |
| Integration — end to end | Scenarios 1, 2, 5, 6: monthly buy→confirm→N `ASSIGNED`; individual N=1; duplicate webhook consumes zero extra spots; unfillable coverage ⇒ `Payment COMPLETED` + `Purchase EXCEPTION`; `409` on a visibly-full quote | New `PhysicalPurchaseIntegrationTest`, structured like `PresentialPurchaseExceptionPathIntegrationTest` |
| Architecture | `PhysicalArchitectureTest`: no billing type reachable from `api:physical`; `BillingArchitectureTest`: `CoveragePlanner` imports no Spring/JPA | Existing ArchUnit runs |

TDD order per unit: RED `shared` command invariant → RED planner → RED
`assignAll` → RED integration, each before its implementation.

## Threat Matrix

Routing is the only listed boundary touched: one new authenticated `POST` whose
only input is a `quoteId` the acting user must own, with the subject taken from
the token (never a request field) — covered by the 401 and cross-user-quote
tests. No shell, subprocess, VCS/PR automation, executable-file classification or
process integration. Remaining rows: N/A.

## Migration / Rollout

V20 is a create + total backfill + drop, safe on a live table because every
existing row is single-session. V21 exists but is **not** part of normal rollout:
it is the guarded escape hatch, and it is only applicable while no `MONTHLY`
purchase has been fulfilled — after that, rolling the schema back requires a
product decision about those purchases, not a migration. The endpoint and the
`assignAll` path are independently revertible (proposal rollback steps 1 and 2);
`assign`'s retained N=1 signature is what makes step 2 a pure revert.

## Dependency Order (for `sdd-tasks`, not a PR split)

1. `shared` `MultiSessionCapacityAssignmentCommand` + its invariant tests — the
   ordering contract everything downstream depends on.
2. `api:physical`: `CapacityAssignments`, port method, `assignAll` + `assign`
   delegation.
3. `api:billing` schema: V20/V21, entity, mapper, `Purchase` list, repository.
4. `api:billing` `CoveragePlanner` (independent of 2 and 3; can run in parallel).
5. `PaymentTarget.Physical` reference change + `CreatePurchaseFromPaymentEventUseCase`.
6. `api:app`: handler rewiring + adapter `assignAll` — needs 1, 2, 3, 4, 5.
7. `api:billing` checkout use case + controller + `409` — needs 4 and 5 only.
8. OpenAPI, then the integration and concurrency suites.

Steps 1–4 are the risky half and are independent of the endpoint; 7 is the
natural cut line, since checkout can be built and reviewed against a planner that
already exists.

## Open Questions

None. The proposal's three open questions are answered above: A1 (child table,
with a guarded reverse migration), A2 (`scheduledAt, sessionId`, enforced in the
`shared` compact constructor), A4 (extend forward within a 120-day horizon; a
shortfall is `EXCEPTION`, never a partial assignment).

# Design: Technical Capacity Hold for Asynchronous Physical Payments

## Technical Approach

The hold is the *same invariant* as an assignment, decided by the *same three
locking reads*, written to a different table. Nothing structurally new is
introduced: `api:physical` gains a write path that mirrors
`AssignCapacityUseCase.assignAll` (one `REQUIRES_NEW` transaction around an
ordered loop), `api:billing` gains one out port, `api:app` gains one adapter of
the exact shape `PhysicalCourseAvailabilityAdapter` already has (D4).

The one genuinely new idea is that the capacity invariant becomes
`assigned + activeHolds + 1 > capacity` and is enforced on **both** write
paths. Everything else is composition of proven parts.

## Architecture Decisions

### B1 — The correlation is two columns on `physical_capacity_holds`, not a child table

Closes Open Question 1 (D1 fixed *that* it exists, not its shape).

V20 needed `billing_purchase_sessions` because `billing_purchases` is **one row
per purchase** and a row cannot hold N sessions. `physical_capacity_holds` is
already **one row per session** — its N-ness is modelled by N rows. A child
table here would carry exactly one parent hold per row and add no information.
The V20 precedent, applied rather than imitated, says: no child table.

**V21** adds to `physical_capacity_holds`:

| Column / key | Shape | Why |
|---|---|---|
| `payment_id` | `BINARY(16) NOT NULL` | The opaque correlation of D1, same discipline as `uq_billing_purchases_payment_id` |
| `converted_at` | `DATETIME(3) NULL` | Conversion marker — see B3 |
| `uq_physical_holds_payment_session` | `UNIQUE (payment_id, session_id)` | Makes hold creation idempotent per claim and surfaces a double-claim at `flush()`, exactly like `uq_physical_assignment_session_student` |
| `idx_physical_holds_payment` | `KEY (payment_id)` | Conversion and release both address holds by payment |

`payment_id` is **NOT NULL**, refining the proposal's rollback prose ("a
nullable reference"). The table has zero production rows, so NOT NULL costs
nothing on V21; nullable would permit, by construction, a hold that can never
be converted or released — an orphan the schema itself invites. The rollback
argument is unaffected: a reverse migration drops both columns, and there is no
data to preserve.

`activeCapacityHolds` is now `converted_at IS NULL AND expires_at > :now`. The
three native queries in `PhysicalSessionJpaRepository`
(`findScheduledWithAvailability`, `findManagedWithAvailability`,
`findByIdWithAvailability`) each gain that predicate.

### B2 — A sibling command without `studentId`, sharing one ordering invariant

Closes Open Question 2.

`studentId` is dropped. A hold reserves a **spot**, not a seat for a person
(D5): the student identity is only resolvable from a settled `Payment`, and
persisting it would (a) add a column nothing reads, (b) imply the
student-visible reservation D5 forbids, and (c) invite a
`(session_id, student_id)` uniqueness that would wrongly reject a legitimate
re-purchase after a released hold.

The ordering invariant must not be copy-pasted, or A2's deadlock guarantee
stops being singular. `SessionClaim` is promoted from a nested record of
`MultiSessionCapacityAssignmentCommand` to a top-level
`com.menta.shared.physical.SessionClaim`, and the total-order check moves into
`SessionClaimOrdering.requireTotalOrder(List<SessionClaim>)` in the same
package. Both compact constructors call it.

```java
public record MultiSessionCapacityHoldCommand(List<SessionClaim> claims, UUID paymentId) {
    public MultiSessionCapacityHoldCommand {
        Objects.requireNonNull(paymentId);
        claims = SessionClaimOrdering.requireTotalOrder(claims); // non-empty, no dup, (scheduledAt, sessionId) ASC
    }
}
```

### B3 — Conversion marks the hold, then reuses `assertAssignment` unchanged

Closes Open Question 3. **One transaction, no intermediate window.**

`ConvertCapacityHoldUseCase.convertAll(UUID paymentId, UUID studentId)` in
`api:physical`, a structural twin of `assignAll`: a single `REQUIRES_NEW`
around an ordered loop. Per session, in `(scheduledAt ASC, sessionId ASC)`
order — the same order every other writer uses, so the lock chain never cycles:

1. set `converted_at = now` on this payment's hold row for the session;
2. call `assertAssignment(sessionId, studentId)` **unchanged**.

Because step 1 has already excluded that row from the locking hold count that
step 2 takes, the arithmetic balances exactly: one hold leaves, one assignment
arrives, and conversion needs **zero new invariant logic**. Neither write is
visible outside the transaction, so no reader ever observes a transient free
spot. Rejected: delete-then-assign in two transactions (the transient over-read
the proposal names) and a bespoke conversion invariant (a second copy of the
rule that #216 proved hard to get right).

**Idempotency** is why the row is marked rather than deleted: a deleted row
cannot answer "which sessions did this hold reserve" on redelivery, and D7
makes that set authoritative. Redelivery finds every row already
`converted_at IS NOT NULL` and returns `AlreadyConverted` without writing. The
`uq_physical_assignment_session_student` collision remains the backstop.

Zero rows for `paymentId` means **no hold exists** — the rollback / pre-change
path. The outbox handler then keeps today's
`CoveragePlanner(confirmedAt, requireAvailable=false)` + `assignAll` behaviour
verbatim (proposal Approach 6). A held purchase never recomputes (D7).

### B4 — The invariant counts holds on both write paths (the proposal's High risk)

Non-negotiable. New locking read on `PhysicalCapacityHoldJpaRepository`:

```java
@Query(value = "SELECT COUNT(*) FROM physical_capacity_holds h "
    + "WHERE h.session_id = :sessionId AND h.converted_at IS NULL "
    + "AND h.expires_at > :now FOR UPDATE", nativeQuery = true)
long countActiveBySessionIdForUpdate(@Param("sessionId") UUID sessionId, @Param("now") Instant now);
```

Served by the existing `idx_physical_holds_session_expires`, so the gap lock is
scoped to one session's range.

Both writers execute the **same three statements in the same order** — the lock
order is what makes them mutually safe:

| Step | Statement | Locks |
|---|---|---|
| 1 | `PhysicalSessionJpaRepository.lockCapacityForUpdate(sessionId)` | the session row every claimant queues on |
| 2 | `PhysicalCapacityAssignmentJpaRepository.countBySessionIdForUpdate(sessionId)` | assignment index range |
| 3 | `PhysicalCapacityHoldJpaRepository.countActiveBySessionIdForUpdate(sessionId, now)` | hold index range |

Decision: `assigned + activeHolds + 1 > capacity` → refuse before writing
anything. `JpaPhysicalCapacityAssignmentAdapter.assertAssignment` gains step 3
and the new term; `JpaPhysicalCapacityHoldAdapter.assertHold(sessionId,
paymentId, expiresAt)` is the new sibling that runs the identical three reads
and inserts into `physical_capacity_holds` with an explicit `flush()`. No plain
read ever precedes step 1 (D3).

### B5 — Configuration names confirmed as proposed

`physical.capacity.hold.ttl-ms: 1800000` (30 min, the low end of D6) and
`physical.capacity.hold.expiry.{enabled: true, rate-ms: 60000, batch-size: 100}`
in `api/app/src/main/resources/application.yml`, mirroring
`billing.subscription.expiry.*` exactly. The split is correct and must stay: TTL
is a *correctness* property read by `CreateCapacityHoldUseCase`; the sweep rate
is *housekeeping* cadence — an expired hold already stops counting with no sweep
run, because every availability read filters `expires_at > :now`.

TTL is injected as a `Duration` constructor parameter from
`PhysicalConfiguration`'s bean method (`@Value` stays in `infrastructure`, never
in `application` — ArchUnit).

`HoldExpiryReconciler` / `HoldExpiryWorker` in
`com.menta.physical.infrastructure.scheduling`: `@ConditionalOnProperty` on the
**class** (#172), `@Scheduled` `tick()` dispatching each id to the separate
`REQUIRES_NEW` worker bean. It GCs rows that are expired-and-unconverted, and
converted rows older than the TTL.

### B6 — The provider deadline field is provider-neutral; MP's wire shape is unverified

`PaymentPreferenceRequest` gains `Instant expiresAt` (nullable), threaded
through `PaymentPreferencePort` to both adapters.
`MercadoPagoPaymentPreferenceAdapter.toBody` maps it into the preference body;
the local adapter stores it.

**Risk, stated rather than invented**: a repository-wide search found *no*
expiration/deadline field in `PaymentPreferencePort`, `PaymentPreferenceRequest`,
either adapter, or any doc/ADR — there is no in-repo evidence of Mercado Pago's
preference-expiry contract. The JSON field names (candidates such as `expires` /
`expiration_date_to`) and any provider-imposed minimum window MUST be confirmed
against Mercado Pago's live preference contract during implementation, before
the adapter test is written. If MP rejects a 30-minute window, the fallback is
to send no expiration: the hold still guarantees the capacity, and the
preference merely outlives it — today's behaviour, not a regression.

## Data Flow

    POST /billing/physical/purchases {quoteId}      [api:billing]
      ├─ method check → external-reference replay → quote validity (410)
      ├─ CoveragePlanner.plan(now, requireAvailable=true) → Plan.Complete(ordered sessions)
      ├─ PaymentId.generate()                      ← moved above the hold call
      ├─ PhysicalCapacityHoldPort.hold(cmd, ttl)   [billing out-port]
      │     └─ api:app PhysicalCapacityHoldAdapter → physical in-port
      │           └─ CreateCapacityHoldUseCase.holdAll   one REQUIRES_NEW
      │                 for each claim, in (scheduledAt, sessionId) order:
      │                   lockCapacityForUpdate → countAssignedForUpdate
      │                   → countActiveHoldsForUpdate → insert + flush
      │           ── any failure ─→ zero hold rows (transaction rollback)
      ├─ failure ─→ PhysicalCapacityUnavailableException → 409 (D2)
      │              no billing_payments row, no provider call
      └─ Payment.awaitingProvider(...) → preference(expiresAt ≤ hold.expires_at) → 201
            └─ provider/preference failure ─→ release(paymentId), then rethrow

    webhook → COMPLETED → outbox → PhysicalCapacityAssignmentOutboxEventHandler [api:app]
      ├─ ConvertCapacityHoldUseCase.convertAll(paymentId, studentId)   one REQUIRES_NEW
      │     ├─ HoldNotFound  ─→ legacy path: CoveragePlanner(confirmedAt) + assignAll
      │     ├─ AlreadyConverted ─→ no-op (redelivery)
      │     └─ Converted(sessionIds) ─→ purchase.assigned(sessionIds)      (D7)
      └─ CapacityBelowAssignedException ─→ markException(CAPACITY_BELOW_ASSIGNED)

## File Changes

| File | Action | Description |
|---|---|---|
| `api/app/.../db/migration/V21__physical_capacity_hold_correlation.sql` | Create | `payment_id`, `converted_at`, unique + index (B1) |
| `api/app/.../db/migration/V22__revert_physical_capacity_hold_correlation.sql` | Create | Guarded reverse migration, not part of normal rollout |
| `shared/.../physical/SessionClaim.java` | Create | Promoted from a nested record (B2) |
| `shared/.../physical/SessionClaimOrdering.java` | Create | The single total-order enforcement point |
| `shared/.../physical/MultiSessionCapacityAssignmentCommand.java` | Modify | Delegates to `SessionClaimOrdering`; nested `SessionClaim` removed |
| `shared/.../physical/MultiSessionCapacityHoldCommand.java` | Create | Ordered claims + `paymentId`, no `studentId` (B2) |
| `physical/.../persistence/entity/PhysicalCapacityHoldJpaEntity.java` | Modify | `paymentId`, `convertedAt`; javadoc no longer says "read-only" |
| `physical/.../persistence/repository/PhysicalCapacityHoldJpaRepository.java` | Modify | `countActiveBySessionIdForUpdate`, `findByPaymentIdOrdered`, expiry sweep queries (B4) |
| `physical/.../persistence/repository/PhysicalSessionJpaRepository.java` | Modify | Three availability queries gain `converted_at IS NULL` (B1) |
| `physical/.../persistence/adapter/JpaPhysicalCapacityAssignmentAdapter.java` | Modify | Step 3 + `assigned + activeHolds + 1 > capacity` (B4) |
| `physical/.../application/port/out/PhysicalCapacityHoldWriter.java` | Create | `assertHold`, `markConverted`, `release` |
| `physical/.../persistence/adapter/JpaPhysicalCapacityHoldAdapter.java` | Create | Three-locking-read hold writer (B4) |
| `physical/.../application/port/in/PhysicalCapacityHoldPort.java` | Create | Physical's in-port: `holdAll` / `release` / `convertAll` |
| `physical/.../application/usecase/{CreateCapacityHold,ReleaseCapacityHold,ConvertCapacityHold}UseCase.java` | Create | One `REQUIRES_NEW` each (B3) |
| `physical/.../infrastructure/scheduling/HoldExpiry{Reconciler,Worker}.java` | Create | `@ConditionalOnProperty` on the class (B5) |
| `physical/.../infrastructure/config/PhysicalConfiguration.java` | Modify | TTL `@Value` → `Duration` constructor arg (B5) |
| `billing/.../application/port/out/PhysicalCapacityHoldPort.java` | Create | Billing's out-port (D4) |
| `billing/.../application/usecase/CreatePhysicalPurchaseCheckoutUseCaseImpl.java` | Modify | `PaymentId.generate()` moved up; hold before `Payment`; release on later failure; `409` |
| `billing/.../application/dto/PaymentPreferenceRequest.java`, `port/out/PaymentPreferencePort.java` | Modify | `Instant expiresAt` (B6) |
| `billing/.../provider/mercadopago/MercadoPagoPaymentPreferenceAdapter.java` + local adapter | Modify | Send the deadline (B6 — wire shape to confirm) |
| `billing/.../infrastructure/config/BillingConfiguration.java` | Modify | Wire the new port into the checkout bean |
| `api/app/.../billing/PhysicalCapacityHoldAdapter.java` | Create | Cross-module bridge, plain Java call (D4) |
| `api/app/.../outbox/PhysicalCapacityAssignmentOutboxEventHandler.java` | Modify | Convert first; legacy plan only on `HoldNotFound` (B3) |
| `api/app/src/main/resources/application.yml` | Modify | `physical.capacity.hold.*` (B5) |
| `api/openapi/billing-v1.yaml` | Modify | `409` description becomes a guarantee |

## Testing Strategy

| Layer | What to test | Approach |
|---|---|---|
| Shared (unit) | `SessionClaimOrdering` rejects empty/duplicate/non-ascending over the **whole** set, not adjacent pairs; `MultiSessionCapacityHoldCommand` requires `paymentId`; the assignment command still behaves identically after the extraction | Plain JUnit — B2's single enforcement point |
| Physical application (unit) | `holdAll` iterates in order and stops at the first failure; `convertAll` returns `HoldNotFound` / `AlreadyConverted` / `Converted`; `release` is a no-op on an unknown payment | Mockito on the writer, verifying call order |
| Physical adapter (unit) | `assertHold` and `assertAssignment` each issue the three locking reads **in order** and consult no plain count — extend `JpaPhysicalCapacityAssignmentAdapterTest`'s existing `the_plain_non_locking_count_is_never_consulted` | Mockito on repositories |
| Physical adapter (integration) | **Hold concurrency**: clone `AssignCapacityAdapterIntegrationTest` + #217's iterative shape with a real `CountDownLatch` starting gun and ≥10 iterations — capacity 1, N threads claiming a hold, assert exactly one hold row every run. A probabilistic bug (#216 measured 7/11) is invisible to a single-shot test | Testcontainers MySQL 8 |
| Physical adapter (integration) | **Cross-path oversell**: one thread claims a hold, another calls `assertAssignment` on the same capacity-1 session; exactly one wins, both orders tested. This is the proposal's High risk, asserted directly | Testcontainers, latch |
| Physical adapter (integration) | Conversion never dips: a reader polling `findByIdWithAvailability` during `convertAll` never observes `availableSpots` above the held value | Testcontainers |
| Migration (integration) | V21 on a seeded table; `converted_at IS NULL` predicate keeps pre-existing availability readings byte-identical; the reverse migration guard | Mirrors `SubscriptionTrialMigrationIntegrationTest` |
| Billing application (unit) | Hold failure ⇒ `PhysicalCapacityUnavailableException`, **zero** `paymentRepository.save`, **zero** `preferencePort.createPreference`; preference failure ⇒ `release(paymentId)` called; `expiresAt` passed to the preference never exceeds the hold's | Mockito + fixed `Clock` — carries billing's 100% floor |
| App (integration) | **Idempotent conversion**: deliver the webhook twice and five times; assert identical `physical_capacity_assignments` count, `converted_at` unchanged after the first, `Purchase ASSIGNED` once | Extend `PhysicalPurchaseIntegrationTest` |
| App (integration) | Guaranteed `409`: two concurrent checkouts for the last spot ⇒ one `201`, one `409 CAPACITY_UNAVAILABLE` with zero `billing_payments` rows; partially-satisfiable `MONTHLY` ⇒ zero hold rows; expired hold stops counting with no sweep run | Testcontainers, latch |
| Architecture | `BillingArchitectureTest`: checkout still references no `com.menta.physical..`; `PhysicalArchitectureTest`: no Spring/JPA in the hold use cases | Existing ArchUnit runs |

TDD order per unit: RED shared ordering → RED adapter invariant (three reads,
holds counted) → RED concurrency integration → RED conversion idempotency, each
before its implementation.

## Threat Matrix

N/A — no shell, subprocess, VCS/PR automation, executable-file classification,
or process-integration boundary. Routing is unchanged: no new endpoint (D5), and
the one touched route (`POST /api/v1/billing/physical/purchases`) keeps its
authentication, its request shape, and its `409` status and ProblemDetail code
(D2); only the truthfulness of that `409` changes.

## Migration / Rollout

V21 is purely additive on a table with zero production rows, so `NOT NULL`
needs no backfill and the availability predicate change is a no-op against
existing data. V22 (reverse) is not part of normal rollout. The three rollback
layers of the proposal stay independently revertible; B3's `HoldNotFound`
branch is what makes layer 1 a pure revert — with the hold call removed,
confirmation falls back to today's `CoveragePlanner(confirmedAt)` path without
any code change on the confirmation side.

## Dependency Order (for `sdd-tasks`, not a PR split)

1. **V21 migration** + entity fields + reverse migration — everything else
   addresses the new columns.
2. **`shared`**: `SessionClaim` promotion, `SessionClaimOrdering`,
   `MultiSessionCapacityHoldCommand` (independent of 1; can run in parallel).
3. **`api:physical` hold write**: hold repository queries,
   `JpaPhysicalCapacityHoldAdapter`, `CreateCapacityHoldUseCase`,
   `ReleaseCapacityHoldUseCase`, in-port — needs 1 and 2.
4. **Corrected invariant** in `assertAssignment` + the availability
   `converted_at` predicate — needs 1; is the riskiest unit and is independently
   testable before any billing code exists.
5. **Cross-module bridge**: billing out-port + `api:app` adapter — needs 3.
6. **Checkout wiring**: `PaymentId.generate()` move, hold call, `409`, release
   on failure, `BillingConfiguration` — needs 5.
7. **Provider deadline**: `PaymentPreferenceRequest` / port / both adapters —
   needs 6, and needs B6's MP contract confirmed first.
8. **Conversion**: `ConvertCapacityHoldUseCase` + outbox handler rewiring —
   needs 3 and 4.
9. **Expiry sweep**: reconciler/worker + `application.yml` — needs 1 only;
   schedulable anywhere after it.
10. **OpenAPI**, then the concurrency and idempotency integration suites.

Steps 1–4 are the risky half and are entirely inside `api:physical`; step 5 is
the natural cut line, since billing can be built against a hold port that
already exists and is already proven under concurrency.

## Open Questions

- [ ] **Mercado Pago preference-expiry wire contract (B6)** — the only
      unresolved item. No in-repo evidence exists; the field names and any
      minimum-window constraint must be confirmed against the provider before
      step 7, not guessed. The design is arranged so a negative answer costs
      only the deadline field, never the guarantee.

Proposal Open Questions 1–4 are answered above: B1 (two columns, no child
table), B2 (sibling command without `studentId`, one shared ordering
invariant), B3 (one transaction, mark-then-assert, no intermediate window),
B5 (names confirmed as proposed).

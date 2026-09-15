# Proposal: Technical Capacity Hold for Asynchronous Physical Payments

## Intent

`POST /api/v1/billing/physical/purchases` is live (#41). Its capacity check is
`CreatePhysicalPurchaseCheckoutUseCaseImpl.ensureCoverageIsAvailable:131` — a
`CoveragePlanner` call with `clock.now()` and `requireAvailable = true` that
reads availability and **reserves nothing**. Between that read and the webhook
confirmation, another buyer can take the last spot. The first buyer has already
paid; the purchase lands in `EXCEPTION`, and there is no refund or notification
policy (deferred to #209). The window is not a bug in #41 — it was accepted
there as D1 and split into this issue.

The read half of the fix already exists and is unused:
`physical_capacity_holds` has existed since `V7__physical_courses.sql:55-64`,
`PhysicalCapacityHoldJpaEntity` maps it, and
`PhysicalSession.getAvailableSpots()` already subtracts `activeCapacityHolds`.
There is **zero production write path** — nothing ever inserts a row.

`PhysicalCapacityUnavailableException:15-18` names this change explicitly: the
409 "intentionally shares its error code with that future capability so the
response shape does not change when the guarantee arrives, **only its
truthfulness does**." This change makes it truthful.

Scope: scenarios **3 and 4** of #208 (US-PHYSICAL-004b).

## Scope

### In Scope

- **Scenario 3 — hold before payment.** `api:physical` creates a technical,
  atomic, all-or-nothing hold over the quote's eligible sessions **before**
  billing persists `Payment.awaitingProvider(...)` or calls the provider. The
  hold is invisible to the student: not viewable, renewable, or cancellable by
  them.
- **Provider deadline bounded by hold expiry.** A new expiration field on
  `PaymentPreferenceRequest` / `PaymentPreferencePort`, threaded to Mercado
  Pago's preference body. Verified absent today in the port, the DTO,
  `MercadoPagoPaymentPreferenceAdapter.toBody`, and the local adapter — this is
  net-new, not a wiring change.
- **Idempotent conversion at confirmation.** On the confirmed movement, the
  hold identified by the new correlation reference converts to real
  `physical_capacity_assignments` rows against **exactly the session set the
  hold already reserved** — no recomputation via `CoveragePlanner` for a held
  purchase (D7). Redelivery converts once.
- **Release on failure and on expiry.** Explicit release when checkout fails
  after the hold, plus TTL-driven invisibility and a housekeeping sweep.
- **New Flyway migration (V21)** adding the correlation reference to
  `physical_capacity_holds` (see D1).
- **Scenario 4 — guaranteed 409.** If the atomic hold cannot be created,
  checkout responds `409 CAPACITY_UNAVAILABLE` — the **same** error code and
  response shape — with **no `Payment` row and no provider call**.
- OpenAPI: behavioral description update on the existing `409` response.

### Out of Scope

- **No new endpoint.** The guarantee is delivered through the existing
  `POST /api/v1/billing/physical/purchases`.
- **No `CoveragePlanner` change.** It already normalizes `MONTHLY` and
  `INDIVIDUAL` into one ordered `List<EligibleSession>`; both call sites stay.
- **No student-facing hold concept.** No endpoint, DTO, BFF page, or Android
  screen exposes, extends, or cancels a hold.
- **No refund, credit, or notification policy** for purchases that still reach
  `EXCEPTION` (#209).
- **No change** to quote creation, quote validity (`410` still precedes `409`),
  `PhysicalCoursePricing`, or check-in eligibility.
- **No retrofit** of holds onto virtual subscriptions.

## Capabilities

### New Capabilities

- `physical-capacity-hold`: the hold lifecycle owned by `api:physical` —
  atomic all-or-nothing creation over an ordered session set, counting against
  availability, TTL expiry, explicit release, idempotent conversion to
  assignments, and invisibility to the student.

### Modified Capabilities

- `physical-purchase-checkout`: two published requirements change.
  1. *"A visibly-full quote is rejected with 409 (best effort, no guarantee)"*
     (`spec.md:81`) — the "MUST NOT reserve", "MAY still resolve to
     `EXCEPTION`", and "distinct, weaker guarantee" clauses are replaced by a
     hold-backed guarantee. The status code and ProblemDetail shape do not
     change.
  2. *"Checkout creates no capacity assignment"* (`spec.md:108`) — currently
     reads "MUST NOT insert any `physical_capacity_assignments` row **and MUST
     NOT create any capacity hold**". The assignment half stands; the hold half
     inverts.
  The ArchUnit requirement (`spec.md:120`, checkout MUST NOT reference
  `com.menta.physical.*`) is **unchanged** and constrains the whole approach.
- `presential-purchase-fulfillment`: confirmation-time assignment becomes
  conversion of an existing hold rather than a claim from scratch, still
  all-or-nothing and still idempotent on redelivery.

## Approach

Reuse three proven patterns; introduce nothing new structurally.

1. **Checkout (`api:billing`)** — `create()` keeps its current order: method
   check → external-reference replay → quote validity (`410`) → coverage plan.
   `ensureCoverageIsAvailable` stops being a discard: its `Plan.Complete`
   session list (already ordered `scheduledAt ASC, sessionId ASC`) feeds a new
   **out** port `PhysicalCapacityHoldPort`
   (`com.menta.billing.application.port.out`), called with the ordered set plus
   the correlation reference. `PaymentId.generate()` moves above the hold call
   so the reference exists before `Payment` is persisted.
2. **Bridge (`api:app`)** — a new `com.menta.app.billing.PhysicalCapacityHoldAdapter`
   implements billing's out port with a plain Java call into a new
   `api:physical` **in** port. This mirrors `PhysicalCourseAvailabilityAdapter`
   exactly — never HTTP, never a shared schema (ADR-0037) — and is the only
   shape that satisfies the existing ArchUnit rule.
3. **Hold write (`api:physical`)** — a sibling of `AssignCapacityUseCase.assignAll`:
   one `REQUIRES_NEW` transaction wrapping an ordered loop of per-session
   claims, each deciding the invariant from **two sequential locking reads**
   (`lockCapacityForUpdate` on `physical_sessions`, then a `FOR UPDATE` count),
   never a plain read first, then insert + explicit `flush()`.
4. **Success** → normal flow resumes: `Payment.awaitingProvider(...)`, then a
   preference whose deadline is bounded by the hold's `expires_at`.
5. **Failure** → `PhysicalCapacityUnavailableException` (`409`), before any
   `Payment` row or provider call.
6. **Confirmation (`api:app`)** — `PhysicalCapacityAssignmentOutboxEventHandler`
   locates the hold by its correlation reference and converts it to
   assignments over the exact session set it already reserved (D7 — no
   `CoveragePlanner` recomputation for a held purchase); a hold that failed or
   expired is released.

Physical still never sees `Payment` or `Purchase` — only neutral `api:shared`
commands and opaque IDs.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/app/src/main/resources/db/migration/V21__*.sql` | New | Correlation reference on `physical_capacity_holds` |
| `api/billing/.../application/port/out/PhysicalCapacityHoldPort.java` | New | Billing's out port for the hold |
| `api/billing/.../usecase/CreatePhysicalPurchaseCheckoutUseCaseImpl.java` | Modified | Hold claim before `Payment`; `409` on failure |
| `api/billing/.../application/dto/PaymentPreferenceRequest.java` + `port/out/PaymentPreferencePort.java` | Modified | New expiration field |
| `api/billing/.../MercadoPagoPaymentPreferenceAdapter.java` + local adapter | Modified | Send the deadline in the preference body |
| `api/billing/.../infrastructure/config/BillingConfiguration.java` | Modified | Wire the new port into the checkout bean |
| `api/app/.../billing/PhysicalCapacityHoldAdapter.java` | New | Cross-module bridge (plain Java call) |
| `api/physical/.../application/port/in/` + `usecase/` | New | Hold in-port + create/release/convert use cases |
| `api/physical/.../persistence/adapter/` + hold JPA repository | New/Modified | Two-locking-read hold writer |
| `api/physical/.../infrastructure/scheduling/` | New | Hold-expiry reconciler + `REQUIRES_NEW` worker |
| `api/shared/.../physical/` | New | Ordered multi-session hold command (see Q2) |
| `api/app/.../outbox/PhysicalCapacityAssignmentOutboxEventHandler.java` | Modified | Convert/release instead of claim-from-scratch |
| `api/app/src/main/resources/application.yml` | Modified | `physical.capacity.hold.*` properties |
| `api/openapi/*.yaml` | Modified | `409` description now a guarantee |
| `api/{billing,physical,app}/src/test/**` | New/Modified | Concurrency, TTL, release, conversion, `409` |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| **Holds are invisible to the assignment write invariant.** `JpaPhysicalCapacityAssignmentAdapter.assertAssignment:95` checks `assigned + 1 > capacity` and never counts holds. A non-held assignment path can therefore oversell into held spots — the mirror of #216 | High | The invariant must become `assigned + activeHolds + 1 > capacity` on **both** write paths, with the hold count taken as a locking read. Non-negotiable; a concurrency test must cover it |
| **Double-counting during conversion.** If the hold row survives while its assignment row is inserted, the session transiently reads one spot short | Med | Conversion must delete the hold and insert the assignment in one transaction (Q3); a test asserts `availableSpots` never dips below the true value |
| **Deadlock under multi-session holds.** Two writers claiming overlapping session sets in different orders | Med | Same total order `(scheduledAt ASC, sessionId ASC)` already enforced by `MultiSessionCapacityAssignmentCommand`'s compact constructor, applied to the hold command too |
| **Stale concurrency note in #208's body.** It cites "unique index + `flush()`, no `FOR UPDATE`" — the pattern #41's proposal locked as D3 and #216 proved wrong | Med | Corrected in the issue's own comment and superseded here by D3. `sdd-design`/`sdd-tasks` MUST NOT read #41's archived D3 or its "No `SELECT … FOR UPDATE`" success criterion as current |
| **Provider deadline mismatch.** Mercado Pago's expiration semantics may not map cleanly to the hold TTL, or may be rejected for short windows | Med | Verify against MP's preference contract in `sdd-design`; the hold TTL must be chosen so the provider accepts it, not the reverse |
| **Orphan holds from abandoned checkouts** grow the table | Low | TTL already makes them invisible at read time (`expires_at > :now`); the sweep is GC, not correctness |
| Coverage floors (`billing` 100%/85%, `physical` 95%/90%) | Low | Hold decision logic stays POJO-testable; ArchUnit keeps the boundary honest |

## Rollback Plan

Three independently revertible layers:

1. **Checkout hold call** — revert `CreatePhysicalPurchaseCheckoutUseCaseImpl`
   and the `api:app` adapter. `ensureCoverageIsAvailable` returns to its
   best-effort form; the `409` reverts to a courtesy rejection with the same
   code and shape, so **no API consumer sees a contract change**.
2. **Provider deadline field** — additive and optional; unset restores today's
   preference body byte for byte.
3. **Schema** — V21 is additive (a nullable reference on a table with no
   production rows today). A reverse migration drops it. Because nothing writes
   holds today, there is no existing data to preserve or reconstruct.

Reverting leaves the `EXCEPTION` residual path (#115) as the safety net, which
is exactly today's behavior. No financial state unwinds.

## Dependencies

- **#41 / `2026-09-15-physical-purchase-capacity`** — delivered and archived
  (checkout endpoint, `Purchase` 1:N, `CoveragePlanner`, best-effort `409`).
  This change is its declared continuation.
- **#217** (fix for the #216 oversell) — supplies the concurrency pattern this
  change must clone.
- **#172's lesson** — `@ConditionalOnProperty` belongs on the scheduler
  **class**, not on the `@Scheduled` method.
- Mercado Pago preference expiration support (to be confirmed in design).
- **#209** (EXCEPTION notification/refund) remains open and unaffected.

## Success Criteria

- [ ] A successful checkout leaves a hold covering every eligible session, and
      `availableSpots` drops immediately for all of them.
- [ ] Concurrent checkouts for the last spot: exactly one gets a hold, the
      other gets `409 CAPACITY_UNAVAILABLE` — same error code as today.
- [ ] A `409` from a failed hold leaves zero `billing_payments` rows and makes
      zero provider calls.
- [ ] A partially-satisfiable `MONTHLY` set creates **no** hold rows at all.
- [ ] An expired hold stops counting against availability with no sweep run.
- [ ] Redelivered confirmation converts the hold exactly once; spot count is
      identical after one and after five deliveries.
- [ ] A checkout that fails after the hold releases it.
- [ ] The provider preference deadline never exceeds the hold's `expires_at`.
- [ ] No assignment path can oversell into a held spot.
- [ ] ArchUnit passes: checkout still references no `com.menta.physical.*`.
- [ ] Coverage floors for `api:billing` and `api:physical` still pass.

## Locked Decisions

### D1 — `physical_capacity_holds` gains a correlation reference

Closed with the product owner before this proposal. The table today has only
`id, session_id, expires_at, created_at` — **no way to identify which purchase
a hold belongs to**. Without it, "idempotent conversion" (a DoD requirement of
#208) is not verifiable: a redelivered webhook cannot be told apart from a new
one. The reference is opaque (payment-id-shaped), following the same discipline
`Purchase` already uses with `uq_billing_purchases_payment_id` (#115).

Rejected alternative: fungible, anonymous holds ("delete any one live hold for
this session, insert the assignment"). It satisfies the count invariant but
gives no redelivery detection and no observability.

### D2 — The guaranteed `409` reuses the existing error code

Not a new code, not a new endpoint. `PhysicalCapacityUnavailableException`'s
javadoc states this is the intended design; API consumers see no change.

### D3 — Concurrency is two sequential locking reads, superseding #41's D3

#41's archived proposal locked "unique index + `flush()`, no
`SELECT … FOR UPDATE`". #216 proved that insufficient for a
sum-against-variable-threshold invariant (MySQL 8 `CHECK` cannot reference
aggregates or other tables), and #217 replaced it with
`lockCapacityForUpdate` + a `FOR UPDATE` count, deliberately with **no plain
read beforehand** (a non-locking read fixes the MVCC snapshot before the lock
is granted — measured 7/11 oversells with it, 0/5 without). The hold has the
same invariant shape and inherits the same pattern.

### D4 — The cross-module call goes through a billing out-port + `api:app` adapter

The only shape compatible with the published ArchUnit requirement that checkout
never reference `com.menta.physical.*`, and structurally guaranteed by
`api:billing` having no Gradle dependency on `api:physical`. Precedent:
`PhysicalCourseAvailabilityPort` / `PhysicalCourseAvailabilityAdapter`.

### D5 — The hold is technical, never a product feature

The student cannot see, extend, or cancel it. Displayed availability in a quote
remains a **non-binding projection**; the hold is the only real temporal
guarantee, and it belongs to the checkout transaction, not to the buyer.

### D6 — TTL is bounded to instant payment, not async cash/transfer

The hold's lifetime is short — on the order of 30-60 minutes, sized to cover
Checkout Pro's card flow, which is the only `PaymentMethod` #41 supports
today (`MERCADO_PAGO`). A payment that takes longer than that already falls
through to the existing `EXCEPTION` residual (#115) — the same outcome as
today, not a regression.

Rejected: a multi-hour or multi-day window sized for cash/bank-transfer
settlement. That trades a real, ongoing cost (a capacity-1 or capacity-2
session reads sold-out to every other buyer for the duration) for a payment
method this change does not build. Async cash/transfer capacity handling is
explicitly deferred — it is not scenario 3/4 of #208, and #41's own checkout
only accepts `MERCADO_PAGO`.

### D7 — The hold is authoritative at confirmation; no recomputation

Confirmation converts **exactly** the sessions the hold reserved at checkout
time. `CoveragePlanner` is not re-run against `confirmedAt` for a held
purchase — the hold's own session set, fixed atomically before `Payment` was
persisted, is the source of truth for what gets assigned.

This supersedes the issue's literal wording ("converts... to the set
computed with the confirmed timestamp") and the design that #41 already
built for the *unheld* path (`PhysicalCapacityAssignmentOutboxEventHandler`
still recomputes via `CoveragePlanner(confirmedAt)` for any purchase that
somehow reaches confirmation without a hold — see rollback path). Recomputing
for a *held* purchase would reintroduce exactly the window this change
exists to close: if the recomputed set asks for a session nobody reserved,
the same oversell risk from #216 returns, just moved one step later.

A session that vanished from the calendar between hold and confirmation
(course cancelled, rescheduled) is rare and falls to the existing all-or-
nothing `EXCEPTION` residual — not a partial conversion, not a silent
substitution.

## Open Design Questions

Non-blocking, for `sdd-design`:

1. **Correlation shape** — a column on `physical_capacity_holds`, or a child
   table analogous to `billing_purchase_sessions` (V20), given that one
   `MONTHLY` hold spans N sessions? D1 fixes *that* it exists, not its shape.
2. **Hold command type** — `MultiSessionCapacityAssignmentCommand` enforces the
   right total order but carries `studentId`, which a hold does not have. A
   sibling type in `api:shared`, a shared ordered supertype, or an optional
   `studentId`?
3. **Conversion mechanism** — D7 fixes *which* sessions convert (the held
   set, unconditionally); this only decides *how*: one atomic transaction
   moving rows hold → assignment, or release-then-assign in two steps with an
   intermediate window? The second risks a transient over-read and a lost
   spot under concurrency.
4. **Configuration names** — proposed `physical.capacity.hold.ttl-ms` for the
   hold's lifetime (D6: ~30-60 min) and
   `physical.capacity.hold.expiry.{enabled,rate-ms,batch-size}` for the
   sweep, mirroring `billing.subscription.expiry.*`. Confirm the split
   between TTL and sweep cadence.

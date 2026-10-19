# Proposal: Physical Purchase and Automatic Capacity Assignment

## Intent

A student can already ask what a physical course costs — `POST
/api/v1/billing/physical/quotes` is live and returns a `PhysicalCourseQuote`
(`MONTHLY` / `INDIVIDUAL`, 1-hour validity, price snapshot). What they cannot do
is buy it. There is no `POST /api/v1/billing/physical/purchases`, no
`CreatePhysicalPurchaseCheckoutUseCase`, and no `PurchaseController` anywhere in
the codebase. The quote's own javadoc says linking a quote to a user is "a later
checkout story" — this is that story (#41, US-PHYSICAL-004).

The fulfillment half already exists and is proven: #115 built the outbox-driven
path where a confirmed physical `Payment` becomes a `Purchase`
(`PENDING_FULFILLMENT` → `ASSIGNED` / `EXCEPTION`), idempotent on `payment_id`,
with `api:app` orchestrating neutral `shared` contracts and `api:physical`
assigning capacity behind a UNIQUE index. It handles exactly **one** session per
purchase: `Purchase.physicalSessionId` is a single `requireNonNull` `String`
(`Purchase.java:16`). A `MONTHLY` purchase spanning several sessions has no way
to be represented. Closing that structural gap, and putting a real entry point in
front of it, is this change.

## Scope

### In Scope

Scenarios **1, 2, 5 and 6** of US-PHYSICAL-004:

- `POST /api/v1/billing/physical/purchases` — takes a valid, unexpired `quoteId`,
  binds it to the acting user, creates a `Payment` in `PENDING` against
  `PaymentTarget.Physical`, and returns the provider checkout data. Modeled on
  the existing virtual `CreateSubscriptionCheckoutUseCase` (Mercado Pago
  preference + `idempotencyKey`).
- **`Purchase` 1:N redesign**: one purchase covering N physical sessions, plus
  the migration of existing singular `physical_session_id` rows. This is the core
  of the change and its largest architectural risk.
- **Coverage period + eligible sessions**, computed at *confirmation* time from
  the confirmed timestamp — never at quote time. The quote contributes only its
  price snapshot.
- **All-or-nothing multi-session assignment**: extend `AssignCapacityUseCase`
  (today single-session) to N sessions claimed in a stable order. `Purchase`
  reaches `ASSIGNED` only when every assignment succeeds.
- Scenario 2 (`INDIVIDUAL`) as the degenerate N=1 case of the same path.
- Scenario 5: replay of an already-processed `paymentId` / `quoteId` yields the
  same assignments and consumes no additional spots.
- Scenario 6: when the computed sessions cannot all be assigned, `Payment` stays
  `COMPLETED` and `Purchase` becomes `EXCEPTION`, with no automatic refund
  policy.
- **Best-effort `409` on a visibly-full quote at checkout** (D5). This is a
  read-time courtesy rejection, *not* the hold-backed guarantee of scenario 4 —
  see the Out of Scope note below for why the two must not be conflated.
- Updated versioned OpenAPI contracts (`api/openapi/`).

### Out of Scope

- **Scenarios 3 and 4 — capacity holds and the *guaranteed*
  `409 CAPACITY_UNAVAILABLE` — split to issue #208.**
  `PhysicalCapacityHoldJpaEntity` exists but has zero production write path (its
  own javadoc declares the write/reservation path a separate future issue); it is
  the largest net-new piece, and the other four scenarios ship without it.
  Scenario 4 depends entirely on scenario 3.

  **Do not conflate this with D5's `409`.** They differ in what they promise:

  | | D5 (this change) | Scenario 4 (#208) |
  |---|---|---|
  | Trigger | Availability *read* says full | Atomic hold *creation* failed |
  | Promise | None — a spot can vanish a second later | Real: a successful checkout holds capacity |
  | If it passes | Buyer may still land in `EXCEPTION` | Buyer's spot is reserved for the payment window |

  D5 exists to avoid charging for something already visibly sold out, and to
  establish the response shape #208 will back with a real guarantee.
- Any automatic refund, credit, or compensation policy for `EXCEPTION` purchases.
  Notifying anyone that a purchase landed in `EXCEPTION` is likewise out of scope
  and tracked in #209 — today that state is recorded correctly and silently.
- Any BFF page or Android screen — API-only, following the #29 → #177 precedent.
- Changes to check-in (`ProcessPhysicalCheckInUseCaseImpl` already gates on a
  `physical_capacity_assignments` row, not on `Purchase` status), to quote
  creation, or to `PhysicalCoursePricing`.

## Capabilities

### New Capabilities

- `physical-purchase-checkout`: the public purchase entry point — quote-to-user
  binding, quote-validity enforcement, `Payment` creation in `PENDING`, checkout
  response contract, and idempotency at the endpoint layer.

### Modified Capabilities

- `presential-purchase-fulfillment`: today's requirements assume one session per
  purchase. They change to N sessions: coverage-period derivation at confirmation
  time, eligible-session computation, all-or-nothing assignment in stable order,
  `ASSIGNED` only on full success, and `EXCEPTION` on partial failure. **This
  spec lives in `openspec/changes/fix-presential-purchase-quota-exception/specs/`
  and was never published to `openspec/specs/` — see Risks.**

## Approach

Keep every established boundary; extend, do not redesign.

1. **Checkout (billing)** — `CreatePhysicalPurchaseCheckoutUseCase` loads the
   quote, rejects it if expired or already consumed, and creates a `Payment` via
   the existing `Payment.awaitingProvider(...)` / `PaymentTarget.Physical` path.
   Nothing about capacity happens here; without #208's hold there is nothing to
   reserve yet.
2. **Confirmation (unchanged)** — `PaymentVerificationService` already publishes
   `billing.PhysicalPaymentCompleted` after commit. No change.
3. **Orchestration (`api:app`)** — `PhysicalCapacityAssignmentOutboxEventHandler`
   computes the coverage period from the confirmed timestamp, resolves the
   eligible sessions, and issues **N** `CapacityAssignmentCommand`s instead of
   one. `shared` grows a multi-session command/result shape; `api:physical`
   returns typed `CapacityAssignments`.
4. **Assignment (`api:physical`)** — `AssignCapacityUseCase` takes an ordered
   session list and claims each spot with this project's established concurrency
   idiom: INSERT + explicit `flush()` against the V7
   `UNIQUE(session_id, student_id)` index, `DataIntegrityViolationException` →
   `CapacityBelowAssignedException`. **No `SELECT … FOR UPDATE`** — that would
   introduce a second, competing concurrency style
   (cf. `SubscriptionRepositoryAdapter.saveNewCheckout:62-72`). Any partial
   failure aborts the whole set.
5. **Residual** — the existing `MarkPurchaseExceptionUseCase` path is reused
   verbatim for scenario 6.

Physical still knows nothing about `Payment` or `Purchase`: it receives IDs and
neutral commands from `shared` and enforces uniqueness itself.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/billing/.../domain/model/Purchase.java` | Modified | Single `physicalSessionId` → N sessions |
| `api/billing/.../infrastructure/persistence/**` + Flyway migration | Modified/New | 1:N schema + backfill of existing singular rows |
| `api/billing/.../application/usecase/CreatePhysicalPurchaseCheckoutUseCase.java` | New | Quote → user → `Payment` PENDING |
| `api/billing/.../web/controller/*PurchaseController.java` + DTOs | New | `POST /api/v1/billing/physical/purchases` |
| `api/billing/.../usecase/CreatePurchaseFromPaymentEventUseCase.java` | Modified | Persist N sessions, keep `payment_id` idempotency |
| `api/shared/.../physical/CapacityAssignmentCommand.java` | Modified | Multi-session neutral contract + typed result |
| `api/app/.../outbox/PhysicalCapacityAssignmentOutboxEventHandler.java` | Modified | Coverage period, eligible sessions, N assignments |
| `api/physical/.../usecase/AssignCapacityUseCase.java` | Modified | Ordered, all-or-nothing multi-session claim |
| `api/openapi/*.yaml` | Modified | Versioned contract for the new endpoint |
| `api/{billing,physical,app}/src/test/**` | New/Modified | Unit + Testcontainers coverage for scenarios 1, 2, 5, 6 |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| **No capacity guarantee without #208**: between checkout and webhook confirmation the buyer can lose the last spot and land in `EXCEPTION` | High | Accepted deliberately. The `EXCEPTION` path is built and tested (#115), so the system degrades safely rather than breaking. The guarantee arrives with #208 — a named future dependency, not an accidental gap |
| `Purchase` 1:N migration corrupts or drops existing singular rows | Med | Backfill every existing `physical_session_id` into exactly one child row; assert row-count parity before/after in a migration test |
| Multi-session claim deadlocks under concurrency | Med | Stable global ordering of sessions (see Open Design Questions) so every transaction claims in the same sequence; no `FOR UPDATE` |
| Partial assignment leaves orphan `physical_capacity_assignments` rows | Med | All-or-nothing within one transaction boundary; abort rolls back every insert in the set |
| **Unarchived spec debt**: 7 changes sit unarchived in `openspec/changes/`, including this change's direct predecessor #115, whose spec deltas were never published to `openspec/specs/`. Canonical specs do not reflect what is actually built in physical | High | `sdd-spec` MUST read `openspec/changes/fix-presential-purchase-quota-exception/specs/` as current truth, not only `openspec/specs/`. Archiving is out of scope here — flagged, not fixed |
| `MONTHLY` coverage semantics chosen wrong → wrong sessions charged | Med | Locked in `sdd-design` before implementation (Open Design Questions) |
| Coverage floors (`billing` 100%/85%, `physical` 95%/90%) | Low | Domain/application logic stays POJO-testable; ArchUnit keeps layering honest |

## Rollback Plan

The endpoint, the multi-session command shape, and the schema change roll back
separately:

1. **Endpoint** — purely additive; delete the controller and use case. No
   existing caller depends on it.
2. **Orchestration/assignment** — the N-session path degenerates to N=1, which is
   exactly today's behavior; reverting the `api:app` and `api:physical` commits
   restores #115's proven flow.
3. **Schema** — the 1:N migration is the only non-trivial revert. It must be
   written so the child table can be dropped and `physical_session_id`
   reconstructed for every single-session row (all rows, pre-`MONTHLY`). A
   reverse migration and its test are part of the work, not an afterthought.

Nothing here changes payment liquidation, so no financial state needs unwinding.

## Dependencies

- **#115 / `fix-presential-purchase-quota-exception`** — merged and in
  production; this change extends it. Its spec deltas are unpublished.
- **#208 (capacity holds)** — the *future* dependency that closes the
  checkout-to-confirmation capacity window (scenarios 3 and 4). This change ships
  without it by design.
- `POST /api/v1/billing/physical/quotes` (live) — the required predecessor call.
- Mercado Pago checkout integration, already used by the virtual subscription
  flow.

## Success Criteria

- [ ] A `MONTHLY` quote purchased and confirmed produces one `Purchase` covering
      every eligible session in the coverage period, all `ASSIGNED`.
- [ ] An `INDIVIDUAL` quote produces exactly one assignment and `ASSIGNED`.
- [ ] A duplicate webhook or retry for a processed `paymentId`/`quoteId` yields
      identical assignments and consumes zero additional spots.
- [ ] When the eligible sessions cannot all be assigned, `Payment` is `COMPLETED`
      and `Purchase` is `EXCEPTION`, with zero partial assignment rows.
- [ ] Existing single-session purchases survive the migration with row-count
      parity and unchanged check-in behavior.
- [ ] No `SELECT … FOR UPDATE` is introduced; concurrency is enforced by unique
      index + `flush()`.
- [ ] ArchUnit passes: `api:physical` still references no billing type.
- [ ] Coverage floors for `api:billing` and `api:physical` still pass.
- [ ] `api/openapi/` documents the new endpoint.

## Locked Decisions

### D1 — Holds (scenarios 3 and 4) are out, in #208

Decided with the product owner before this proposal. The hold is the largest
net-new component and gates only scenario 4; the remaining four scenarios deliver
value without it. Accepting the capacity window is a conscious tradeoff, not an
oversight.

### D2 — Coverage is computed at confirmation, never at quote time

A quote is a 1-hour price snapshot, nothing more. The coverage period and the
eligible session set derive from the confirmed payment timestamp, so a slow
provider confirmation cannot silently sell a stale calendar.

### D3 — Concurrency stays unique-index + `flush()`

Matching `AssignCapacityUseCase` and `SubscriptionRepositoryAdapter`. Introducing
pessimistic locking for this one path would fork the project's concurrency model.

### D4 — Monthly coverage is the next `scheduledSessionCount` sessions from confirmation

A `MONTHLY` quote already charges `pricing.getMonthlyPrice()` verbatim, with no
proration — `PhysicalCourseQuote.monthly()` settles that, and it is shipped. Full
price therefore buys a full count of classes, not a calendar window that happens
to be short: coverage is the next `scheduledSessionCount` scheduled sessions of
the course, counted forward from the confirmed payment timestamp.

This is also why that field is snapshotted on the quote at all. For `INDIVIDUAL`
it divides the price; for `MONTHLY` nothing consumed it until now. It records how
many classes the buyer is owed.

Rejected alternatives: a rolling calendar window (a course with a holiday week
would silently deliver fewer classes for the same price) and a next-cycle
boundary (a buyer paying on the 20th would wait up to 11 days to attend
anything they already paid for).

### D5 — Checkout rejects a visibly-full quote with `409`

If, at request time, the eligible sessions for the quote are already full, the
checkout endpoint refuses instead of creating a `Payment`.

This is explicitly **not** a capacity guarantee — without #208's hold, another
buyer can take the last spot a second later, and that case still resolves to
`EXCEPTION` at confirmation. What it buys is honesty in the obvious case: a
buyer is not charged for something the system can already see is sold out. It
also establishes the `409` shape that #208 will formalize into a real,
hold-backed guarantee.

## Open Design Questions

Non-blocking, for `sdd-design`:

1. **`Purchase` 1:N shape** — child table `purchase_sessions`, or a list of IDs
   inside the aggregate? And how are existing rows with a singular
   `physical_session_id` migrated (and reverse-migrated)?
2. **Stable ordering** — what exactly defines the claim order the NFR demands
   (session start time? session UUID?), and how is it guaranteed across every
   concurrent writer, including any future #208 hold path?
3. **`scheduledSessionCount` vs. reality at confirmation** — D4 fixes coverage at
   the count snapshotted on the quote, but the course calendar can change between
   quote and confirmation. If fewer than `scheduledSessionCount` sessions remain
   schedulable, is that a partial assignment (and therefore `EXCEPTION` under the
   all-or-nothing NFR), or does coverage extend further forward until the count is
   satisfied?

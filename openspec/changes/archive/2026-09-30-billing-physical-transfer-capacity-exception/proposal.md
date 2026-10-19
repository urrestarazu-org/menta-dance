# Proposal: Bank-transfer physical purchase and capacity exception

**Issue**: #36 (US-BILLING-008, "Transferencia presencial y excepción de capacidad") · **Input**: `openspec/changes/billing-physical-transfer-capacity-exception/exploration.md` (mirrored at Engram `sdd/billing-physical-transfer-capacity-exception/explore`)

## Intent

A student who wants a presential course and does not have a card has **no way in**. Every other piece of the flow already exists and is already tested:

- `PaymentFulfillmentService.ensure(Payment)` switches on `PaymentTarget`, never on `PaymentMethod`, and already routes `Physical` → `publishPhysicalPaymentCompletedUseCase.handle(payment)`;
- `ResolvePaymentProofUseCaseImpl` (#31/#33) already calls `ensure`/`release` generically on approve/reject;
- `SubmitPaymentProofUseCaseImpl` and `PaymentController` (`POST /api/v1/billing/payments/{paymentId}/proof`, `GET /api/v1/billing/payments/{paymentId}`) branch on nothing but `PaymentId`, owner, and status;
- `PaymentAdminController` (`/api/v1/admin/billing/payments` — list, detail, `approve`, `reject`, `corrections`) is fully target-agnostic;
- `PhysicalCapacityAssignmentOutboxEventHandler`'s `HoldNotFound` branch already computes eligible sessions fresh from `confirmedAt` via `CoveragePlanner` and drives `AssignCapacityUseCase` / `MarkPurchaseAssignedUseCase` / `MarkPurchaseExceptionUseCase`;
- `Purchase` / `FulfillmentStatus` already model `PENDING_FULFILLMENT` / `ASSIGNED` / `EXCEPTION` (#41, #115).

The single blocking gap is at the front door. `CreatePhysicalPurchaseCheckoutUseCaseImpl.create()` hard-rejects anything but Mercado Pago:

```java
if (command.paymentMethod() != PaymentMethod.MERCADO_PAGO) {
    throw new IllegalArgumentException("Checkout Pro requires MERCADO_PAGO");
}
```

#31's archived proposal named this exclusion and deferred it to **this** issue. This change removes it, the way the virtual side already did, and lets both the `ASSIGNED` and `EXCEPTION` outcomes fall out of code that is already shipped.

Scope: scenarios **1, 2 and 3** of #36.

## Scope

### In Scope

- **`BANK_TRANSFER` physical purchase creation** — a new `CreateBankTransferPhysicalPurchaseUseCase*`, a direct sibling of `CreateBankTransferSubscriptionUseCaseImpl`: consume the creation budget, resolve and validate the quote (`410` on expiry), snapshot its price and eligible sessions, create `Payment.awaitingManualVerification(..., new PaymentTarget.Physical(quoteId), now)`, and return the bank-transfer instructions. No provider call, **no hold** (D1).
- **Routing, not a new endpoint** (D2) — a `RoutingCreatePhysicalPurchaseCheckoutUseCase` in front of the existing `POST /api/v1/billing/physical/purchases`, mirroring `RoutingCreateSubscriptionCheckoutUseCase` byte-for-byte in shape. `PhysicalPurchaseController` is untouched.
- **Bank-transfer instructions on the physical checkout response** — CBU, alias, holder, CUIT, exact amount, and the payment reference, reusing `BankAccountDetails` / `BankTransferInstructions`.
- **Best-effort, explicitly non-binding availability check at creation** (D7): a visibly-full quote is still rejected up front; no capacity is reserved and no guarantee is implied.
- **Rate-limit port generalization** (D4) — `BankTransferRateLimitPort.consumeSubscriptionCreation` becomes `consumeBankTransferCreation`, one shared per-user daily budget across both bank-transfer creation paths. `consumeProofUpload` is unchanged.
- **Approval-time capacity outcome** — approval reaches the existing `HoldNotFound` branch unchanged, landing the purchase in `ASSIGNED` (scenario 1) or `EXCEPTION` (scenario 2) with zero new fulfillment code.
- OpenAPI contract, Bruno requests, and `docs/user-stories/US-BILLING-008.md` (record D1's and D6's divergences).

### Out of Scope

- **#236** — extending the `EXCEPTION` notification to virtual subscriptions. A separate, already-filed follow-up of #209.
- **#237** — admin listing/viewing of purchases in `EXCEPTION`. Also a separate #209 follow-up; no such `GET` exists in billing today and this change does not add one.
- **No new hold mechanism, no hold TTL change, no second hold-creation trigger** (D1). `physical-capacity-hold` is not reopened.
- **No change to proof upload, proof serving, admin list/detail/approve/reject/corrections, the audit log, or the buyer decision emails** (#31/#33). They already work for `PaymentTarget.Physical`.
- **No change to the Mercado Pago physical path** — its hold, its guaranteed `409`, and its hold-authoritative conversion (#208 D7) stay exactly as shipped.
- **No change to quote creation, the 1-hour quote validity, `PhysicalCoursePricing`, `CoveragePlanner`, or check-in eligibility.**
- No refund, credit, auto-retry, or auto-resolution for a purchase that reaches `EXCEPTION`; it stays terminal and human-resolved, per the issue's own NFR.

## Settled decisions

| # | Topic | Decision |
|---|---|---|
| D1 | **Capacity approach: no hold** | Bank-transfer physical purchases create **no `CapacityHold`**. Capacity is computed **at approval time**, fresh from `confirmedAt`, by the already-built `HoldNotFound` branch of `PhysicalCapacityAssignmentOutboxEventHandler` (lines 228-289); unavailable capacity lands the `Purchase` in `EXCEPTION` — exactly scenario 2. Rationale: #208's locked **D6** states verbatim that the hold's TTL is *"short — on the order of 30-60 minutes, sized to cover Checkout Pro's card flow"* and explicitly **rejects** *"a multi-hour or multi-day window sized for cash/bank-transfer settlement. That trades a real, ongoing cost … for a payment method this change does not build."* A hold here would reopen that decision and charge every other buyer for a possibly-abandoned transfer. Approaches 2 (new long-TTL hold) and 3 (hold on proof upload) were considered and rejected for the same reason. **Resolved with the user — not to be re-litigated.** |
| D2 | **No new endpoint; `paymentMethod` routing** | Creation stays on the existing buyer-facing `POST /api/v1/billing/physical/purchases`. A `RoutingCreatePhysicalPurchaseCheckoutUseCase` dispatches on `command.paymentMethod()` — `MERCADO_PAGO` → `CreatePhysicalPurchaseCheckoutUseCaseImpl` byte-identically, `BANK_TRANSFER` → the new use case. This is the exact precedent already shipped for the virtual side (`RoutingCreateSubscriptionCheckoutUseCase`, design D3 of #31), so no new prefix family, no new security matcher, and no new exception-handler wiring. The existing non-MP `IllegalArgumentException` guard stays in `CreatePhysicalPurchaseCheckoutUseCaseImpl` as defense-in-depth, unreachable through the router — same as its virtual twin. |
| D3 | **Quote validity stays 1 hour; payment lifetime is decoupled** | `PhysicalCourseQuote`'s 1-hour `expiresAt` is **not** extended and is consulted exactly once, at creation, to snapshot price and the eligible-session basis — identical to today's Mercado Pago physical checkout. Once the `Payment` exists it has its own lifetime, bounded by the payment expiry sweep and the admin decision, not by the quote. This mirrors how `CreateBankTransferSubscriptionUseCaseImpl` already decouples from any provider-preference lifetime. A quote that expires while the transfer is being verified does **not** invalidate the payment. |
| D4 | **One shared bank-transfer creation budget** | `BankTransferRateLimitPort.consumeSubscriptionCreation(UUID userId)` is renamed to `consumeBankTransferCreation(UUID userId)` and reused by the physical path. The existing name is subscription-specific naming leaking into a physical caller, not a separate concept; the key is already `userId`-scoped and method-agnostic. The 10/user/day budget and the Redis key stay as they are, now counting both rails. `consumeProofUpload` (3 per `paymentId` per 72h) is untouched and already covers physical proofs. |
| D5 | **`PaymentFulfillmentService.release()` stays a no-op for `Physical`** | Verified: `release(Payment)` handles only `PaymentTarget.Virtual` today. Under D1 nothing is ever reserved for a bank-transfer physical purchase, so a rejection has nothing to release and the no-op is **correct as designed**, not a gap. Stated explicitly so `sdd-design` and `sdd-verify` do not flag it. A rejected payment simply never produces a `billing.PhysicalPaymentCompleted` event and therefore never produces a `Purchase`. |
| D6 | **Documented divergence from the issue's literal wording** | #36 scenario 2 says *"el hold expiró"* and its DoD names *"pruebas de hold vencido."* Under D1 there is no hold, so those tests do not exist; the equivalent, tested condition is **"capacity is unavailable at approval time."** This is a deliberate, documented divergence — same handling as #33's D3 (admin route prefix), #44's D1, #45's D8, and #208's D7. It must be called out in the PR body and in the US doc, never silently absorbed. Likewise, this change **inherits** the already-shipped `/api/v1/admin/billing/payments/...` prefix rather than the issue's literal `/api/v1/billing/admin/payments`. |
| D7 | **Creation-time availability check is best-effort and non-binding** | The new use case still runs the `CoveragePlanner` availability read and rejects a visibly-full quote at creation. This is the pre-#208 behaviour for the physical endpoint and costs nothing, but it reserves **nothing** and guarantees **nothing**: the authoritative capacity decision happens at approval (D1). The `409` on this path is therefore a courtesy rejection, not the hold-backed guarantee the Mercado Pago path now carries. The spec must state that asymmetry rather than let `physical-purchase-checkout`'s current guarantee wording read as covering both rails. |

## Capabilities

### New Capabilities

- `bank-transfer-physical-purchase`: the `BANK_TRANSFER` rail for presential purchases — creation on the existing checkout endpoint via `paymentMethod` routing, bank-transfer instructions in the response, the shared daily creation budget, the non-binding creation-time availability check, and the approval-time `ASSIGNED` / `EXCEPTION` outcome with no capacity reservation at any point.

### Modified Capabilities

- `physical-purchase-checkout`: two requirement-level changes. (1) *"A visibly-full quote is rejected with 409 (best effort, no guarantee)"* (`spec.md:81`) currently reads as a hold-backed guarantee for the whole endpoint; it must be scoped to `MERCADO_PAGO`, with `BANK_TRANSFER` keeping the weaker, explicitly non-binding rejection (D7). (2) *"Checkout creates no capacity assignment"* (`spec.md:112`) currently asserts the endpoint **MUST** create a capacity hold; that becomes method-conditional — `BANK_TRANSFER` MUST create neither an assignment **nor** a hold. The `ArchUnit` requirement (`spec.md:131`) is unchanged and still constrains the new use case.
- `presential-purchase-fulfillment`: the no-hold branch of *"Coverage period and eligible sessions are computed at confirmation, never at quote time"* (`spec.md:37`) is currently framed as a residual left over from before holds existed. It becomes a **first-class, intended path** — the permanent behaviour for every bank-transfer physical purchase. No handler code changes; the requirement's framing and a scenario do.
- `bank-transfer-subscription`: *"The system MUST reject an 11th such request from the same user within the same calendar day"* (`spec.md:15-18`) now counts bank-transfer **subscription and physical-purchase** creations against one shared per-user daily budget (D4). The limit, the status code, and the response shape are unchanged.

## Approach

Clone the virtual bank-transfer flow one level down, and change nothing downstream.

1. **New use case (`api:billing`)** — `CreateBankTransferPhysicalPurchaseUseCaseImpl` mirrors `CreateBankTransferSubscriptionUseCaseImpl`'s exact order: consume the creation budget first (attempting a checkout is itself the cost the budget bounds), then resolve the quote with its `410` guard, then the best-effort availability read (D7), then `PaymentId.generate()`, then `Payment.awaitingManualVerification(...)` with the configured CBU as `expectedMerchantAccountId` and `new PaymentTarget.Physical(quoteId)` as the target, then return `BankTransferInstructions`.
2. **Router (`api:billing`)** — `RoutingCreatePhysicalPurchaseCheckoutUseCase implements CreatePhysicalPurchaseCheckoutUseCase`, a two-arm `switch` on `paymentMethod`, wired in `BillingConfiguration` exactly as its subscription twin is. The controller binds to the interface and does not change.
3. **Result DTO** — `PhysicalPurchaseCheckoutResult` gains the bank-transfer arm (instructions instead of a provider checkout URL / hold expiry), mirroring `SubscriptionCheckoutResult.fromBankTransfer`.
4. **Everything after creation is already built.** Proof upload, admin inbox, proof viewing, approve/reject, audit log, buyer emails, `PaymentFulfillmentService.ensure`, the outbox event, the `HoldNotFound` capacity computation, `MarkPurchaseAssignedUseCase`, `MarkPurchaseExceptionUseCase` and the `EXCEPTION` notification (#209) all run unchanged. The work in those areas is **test coverage proving the physical path through them**, not new production code.
5. **No cross-module edge is added.** The new use case never touches `com.menta.physical.*`; the existing ArchUnit rule holds without a new adapter, because no hold is created (D1).

Per strict TDD, each rejection branch (non-eligible payment method through the guard, expired quote, rate-limited 11th creation, visibly-full quote, unavailable capacity at approval) is written failing first. `:api:billing` gates at **95% domain+application / 90% infrastructure**.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/billing/.../application/usecase/CreateBankTransferPhysicalPurchaseUseCaseImpl.java` | New | The one real gap: creation for `BANK_TRANSFER` (D1, D3, D7) |
| `api/billing/.../application/port/in/CreateBankTransferPhysicalPurchaseUseCase.java` | New | Entry port, sibling of `CreateBankTransferSubscriptionUseCase` |
| `api/billing/.../application/usecase/RoutingCreatePhysicalPurchaseCheckoutUseCase.java` | New | `paymentMethod` dispatch (D2) |
| `api/billing/.../application/usecase/CreatePhysicalPurchaseCheckoutUseCaseImpl.java` | Unchanged | Its non-MP guard stays as defense-in-depth behind the router |
| `api/billing/.../application/dto/PhysicalPurchaseCheckoutResult.java` + web response DTO | Modified | Bank-transfer arm carrying `BankTransferInstructions` |
| `api/billing/.../application/port/out/BankTransferRateLimitPort.java` + `RedisBankTransferRateLimitPort.java` | Modified | Rename `consumeSubscriptionCreation` → `consumeBankTransferCreation` (D4); key, limit, window unchanged |
| `api/billing/.../application/usecase/CreateBankTransferSubscriptionUseCaseImpl.java` | Modified | Call-site rename only (D4) |
| `api/billing/.../infrastructure/config/BillingConfiguration.java` | Modified | Wire the new use case and the router bean |
| `api/billing/.../infrastructure/web/controller/PhysicalPurchaseController.java` | Unchanged | Binds to the interface; the router is transparent to it (D2) |
| `api/billing/.../application/usecase/PaymentFulfillmentService.java` | Unchanged/Verify | `ensure` already routes `Physical`; `release` stays a no-op for `Physical` (D5) |
| `api/app/.../outbox/PhysicalCapacityAssignmentOutboxEventHandler.java` | Unchanged/Verify | `HoldNotFound` branch is the intended path now (D1) — behaviour verified by test, not modified |
| `api/{billing,app}/src/test/**` | New/Modified | Creation, routing, rate limit, quote expiry, and end-to-end `ASSIGNED` / `EXCEPTION` integration coverage |
| `api/openapi/billing-v1.yaml`, `bruno/API - Direct/billing/` | Modified | `BANK_TRANSFER` on the physical checkout contract (issue DoD) |
| `docs/user-stories/US-BILLING-008.md` | Modified | Record D1 and D6's divergences from the issue's literal wording |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| A reviewer reads the issue's DoD (*"pruebas de hold vencido"*) and reports the change as incomplete | High | D6 records the divergence here, in the US doc, and in the PR body, citing #208's D6 verbatim — same handling as #33's D3 |
| Unbounded window between proof upload and admin approval means the buyer can pay and still land in `EXCEPTION` | Med | Accepted and intended (D1). `EXCEPTION` is an explicitly non-auto-resolved terminal state per the issue's own NFR, and #209's notification already fires. Support resolution is human, and out of scope by design |
| Renaming a port method touches a shipped subscription path | Med | Mechanical rename with no behavioural change (D4); `CreateBankTransferSubscriptionUseCaseImplTest` and `RedisBankTransferRateLimitPortTest` must pass untouched apart from the name |
| Sharing one daily budget lets physical creations exhaust a user's subscription budget (and vice versa) | Med | Deliberate (D4): the budget bounds abuse of manual-verification creation per user, not per product line. If the product later needs separate ceilings, the key prefix already makes a split trivial |
| The router silently changes Mercado Pago behaviour | Med | The MP arm delegates byte-identically with no added precondition, exactly like `RoutingCreateSubscriptionCheckoutUseCase`; existing `CreatePhysicalPurchaseCheckoutUseCaseImplTest` assertions stay untouched |
| `physical-purchase-checkout`'s hold guarantee wording is left covering both rails | Med | D7 makes the asymmetry an explicit spec change, not an implicit one; the delta spec must scope the guarantee to `MERCADO_PAGO` |
| Coverage gate (95% domain+application / 90% infrastructure on `:api:billing`) | Med | Test-first per strict TDD; the new use case is plain application logic and fully unit-testable |
| 400-line review budget | Med | `sdd-tasks` forecasts; a likely split is (rate-limit rename + port) → (use case + router + wiring) → (DTO/contract + integration coverage) |

## Rollback Plan

1. **Revert the merge commit.** The router disappears; `CreatePhysicalPurchaseCheckoutUseCaseImpl`'s existing `IllegalArgumentException` guard — never removed (D2) — again rejects `BANK_TRANSFER` at the front door. The endpoint returns to Mercado-Pago-only exactly as before.
2. **No schema change.** This change adds no Flyway migration, no column, and no table. There is nothing to un-migrate.
3. **Payments already created** on the bank-transfer physical rail remain valid rows and keep flowing through the (unchanged) proof/admin/fulfillment path, because none of that code is modified by this change. A revert does not strand them mid-flow.
4. **The rate-limit rename** is a compile-time-only concern; reverting restores `consumeSubscriptionCreation` with the same Redis key, so live counters are unaffected in either direction.
5. **No financial state unwinds** and no capacity is released, because none was ever reserved (D1, D5).

## Dependencies

- **#31 / `bank-transfer-subscription`** (archived) — supplies `Payment.awaitingManualVerification`, `BankAccountDetails`, `BankTransferInstructions`, `BankTransferRateLimitPort`, and the `RoutingCreateSubscriptionCheckoutUseCase` pattern this change clones. Not reopened.
- **#33 / `billing-manual-payment-verification`** (archived) — supplies the admin inbox, proof viewing, approve/reject with audit and buyer emails. Target-agnostic already; not reopened.
- **#41 / `physical-purchase-capacity`** and **#115** — supply `Purchase`, `FulfillmentStatus`, `CoveragePlanner`, and the confirmation-time assignment path. Not reopened.
- **#208 / `physical-capacity-hold`** (archived) — its **D6** is the binding precedent for D1. Explicitly **not** reopened.
- **#209** — the `EXCEPTION` notification already fires for physical purchases and needs no change here. **#236** and **#237** remain open and out of scope.
- No new library, external service, configuration property, or module edge.

## Success Criteria

- [x] `POST /api/v1/billing/physical/purchases` with `paymentMethod: BANK_TRANSFER` returns `201` with CBU, alias, holder, CUIT, exact amount, and a payment reference, and creates exactly one `Payment` in `PENDING/AWAITING_MANUAL_VERIFICATION` targeting the quote.
- [x] That request creates **zero** `physical_capacity_holds` rows and makes **zero** provider calls.
- [x] The same request with `paymentMethod: MERCADO_PAGO` behaves byte-identically to today, hold included, with its existing test assertions untouched.
- [x] An expired quote still returns `410` on the bank-transfer path, creating no `Payment`.
- [x] An 11th bank-transfer creation by the same user in one calendar day returns `429`, whether the prior ten were subscriptions, physical purchases, or a mix.
- [x] Uploading a proof and having an admin approve it moves the purchase to `ASSIGNED` with one `physical_capacity_assignments` row per eligible session computed from `confirmedAt` (scenario 1) — through the existing handler, with no new fulfillment code.
- [x] When capacity is unavailable at approval time, the purchase lands in `EXCEPTION`, all-or-nothing, with zero partial assignments, and the #209 notification event is emitted (scenario 2).
- [x] An admin rejection leaves no `Purchase`, no assignment, and releases nothing — `PaymentFulfillmentService.release` remains a verified no-op for `PaymentTarget.Physical` (D5, scenario 3).
- [x] Proof upload, admin list/detail/approve/reject/corrections, the audit log, and the buyer emails work for a physical payment with **no production-code change** in those classes.
- [x] OpenAPI contract and Bruno requests updated; `./gradlew check` passes, including ArchUnit (the new use case references no `com.menta.physical.*`) and the billing coverage gate.

## Open Design Questions

Non-blocking, for `sdd-design`:

1. **External-reference scheme.** The Mercado Pago physical path derives it deterministically from `(userId, idempotencyKey)` to make replay findable; `CreateBankTransferSubscriptionUseCaseImpl` uses `SUB-{paymentId}` instead, because the reference is also the string the buyer types into their transfer. The physical bank-transfer path needs one of the two — a `PUR-`/`PHY-`-prefixed payment-id reference (buyer-friendly, loses idempotent replay) or the deterministic hash (keeps replay, opaque to the buyer). Pick one and state the idempotency consequence.
2. **Result DTO shape.** A sealed/`Optional`-bearing `PhysicalPurchaseCheckoutResult` carrying either a checkout URL + hold expiry or bank-transfer instructions, versus two sibling result types. `SubscriptionCheckoutResult` already chose one of these; follow it unless the physical fields make it awkward.
3. **Where the availability check lives** (D7). Duplicated inside the new use case, or lifted into a shared collaborator both creation use cases call. D7 fixes *that* it runs and that it is non-binding, not where the code sits.
4. **Payment expiry sweep interaction.** Confirm that the existing bank-transfer payment expiry sweep treats a `PaymentTarget.Physical` payment identically, and that an expired-unproofed physical payment leaves no `Purchase` — verify, do not assume.

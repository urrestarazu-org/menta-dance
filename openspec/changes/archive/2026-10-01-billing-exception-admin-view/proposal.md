# Proposal: Admin view for physical purchases in EXCEPTION

**Issue**: #237 ("Vista administrativa de compras en EXCEPTION", follow-up de #209) · **Input**: `openspec/changes/billing-exception-admin-view/exploration.md` / Engram `sdd/billing-exception-admin-view/explore`

## Intent

#209 made a physical purchase's `FulfillmentStatus.EXCEPTION` **audible** — the buyer and a fixed ops address get one email per transition, via `PURCHASE_EXCEPTIONED` on the outbox. It did not make it **visible**: once that email is read, archived, or missed, there is no way to answer "which purchases are stuck right now?"

Confirmed against source, nothing exists today:

- `PurchaseRepository` exposes only `save`, `saveIsolated`, `findByPaymentId`, `findByPaymentIdForUpdate` — **no list query, by status or otherwise**;
- no `@RestController` in the repo lists `billing_purchases` at all, so an admin must already hold a `paymentId` to see anything;
- `billing_purchases` itself carries **no buyer, no timestamp, no reason** (`id`, `payment_id`, `status` only), so even a direct DB query answers nothing operationally useful without joining `billing_payments`.

The result is an ops process whose only inventory of stuck purchases is an inbox. This change delivers the missing read model: a paginated, `ADMIN`-only list of purchases in `EXCEPTION` with the buyer/payment context needed to act on them out of band.

## Scope

### In Scope

- **Paginated `EXCEPTION` list query**: one new `PurchaseRepository` out-port method (`findInException(int page, int size)`) plus its JPA adapter, returning buyer, timestamp, amount/currency, target reference, and covered sessions per row — oldest-first.
- **New `PurchaseAdminController`**: `GET /api/v1/admin/billing/purchases?status=EXCEPTION`, paginated, `ADMIN`-only, `400` on `size > 50`.
- **Application + web DTOs** mirroring #33's pair: `ExceptionPurchasePage`/`ExceptionPurchaseItem` (application) and `ExceptionPurchasePageResponse` (web).
- OpenAPI contract entry and a Bruno request for the new endpoint (issue DoD).

### Out of Scope

- **Any resolution action.** Confirmed: no use case exists today to move a `Purchase` out of `EXCEPTION` — `MarkPurchaseAssignedUseCase` is called only by the automated `PhysicalCapacityAssignmentOutboxEventHandler`, from zero admin controllers. A reassign / cancel / retry / refund endpoint is **deliberately not** in this change (D2); #237 asks for a view. This is **not an oversight** — it is the scope boundary.
- **Virtual `Subscription` in `EXCEPTION`** (D1). `Subscription.exception()` is reached in production today — `PaymentFulfillmentService.ensureSubscription:85-89`, the plan-deleted-at-settlement case — with **no outbox event, no notification, and no admin view**: strictly worse than the physical gap #209/#237 close. Recorded here as evidence for the priority of the separate, still-open **#236**; not designed or fixed here, preserving #209's own deliberate split.
- Changing `MarkPurchaseExceptionUseCase`, `PurchaseExceptionNotificationPort`, the outbox event, or the state machine (ADR-0028). Untouched.
- Any schema change, migration, `reason` column (the `purchase-exception-notification` spec forbids it), free-text search, filter beyond `status`, BFF or Android surface.

## Settled decisions

| # | Topic | Decision |
|---|---|---|
| D1 | Scope: physical only, v1 | Physical `Purchase` only. A unified `Purchase`+`Subscription` row needs a type discriminator plus nullable per-type fields (sessions vs. plan/course snapshot) — the unrepresentable merge this codebase already rejected for `FulfillmentStatus` elsewhere. **Resolved via the exploration's recommendation — not to be re-litigated.** |
| D2 | Read-only, no resolution action | Pure observability. No transition out of `EXCEPTION` is added or exposed. Stated in Out of Scope so a reviewer reads it as a boundary, not a gap. **Resolved — not to be re-litigated.** |
| D3 | Port signature discipline | The port takes **primitive `int page, int size`** and returns an application DTO page, exactly like `PaymentRepository.findAwaitingManualVerification(int, int)` — **no Spring `Page`/`Pageable` crosses into `application`** (ADR-0021, ArchUnit-enforced). `Pageable`/`Page<Row>` stay inside the JPA repository in `infrastructure`. |
| D4 | Page size | `MAX_PAGE_SIZE = 50` **rejected with `400`, never clamped**; default 20 — verbatim the convention `PaymentAdminController` already establishes (and the `#33` spec scenario). |
| D5 | Row context via INNER join | `billing_purchases` has no buyer and no timestamp, so every admin-useful field (`user_id`, `created_at`, `target_modality`, `target_reference`, amount/currency) comes from an INNER join to `billing_payments` on the FK-enforced, `uq_billing_purchases_payment_id`-unique 1:1 `payment_id`. Ordering is `Payment.createdAt` ascending. The INNER join cannot drop a row: every persisted `Purchase` has exactly one `Payment`. |
| D6 | Sessions come from a second flat query | Covered sessions live in the **`billing_purchase_sessions` child table (V20)**, one ordered row per session — not a column on `billing_purchases` (the exploration quoted V8's original single-column shape, superseded). Read them with a second flat query joined in memory, never a JPA `@OneToMany` — the existing `PurchaseRepositoryAdapter` / `PlanRepositoryAdapter` discipline. |
| D7 | Zero-session EXCEPTION rows are legal | `Purchase.exception(paymentId, sessionIds)` (#238) builds a row **directly at `EXCEPTION` with an empty session list** when a payment never resolved to any schedulable session — legal per `Purchase`'s relaxed invariant. Such a row MUST appear in the list with an empty session array; it must not be dropped, and must not throw. These are precisely the rows an admin most needs to see. |
| D8 | New controller, existing security rule | A **new `PurchaseAdminController`**, not a method on `PaymentAdminController` — different aggregate, different resource path. It sits under the existing `/api/v1/admin/**` → `ADMIN` rule in `SecurityConfig.roleAuthorizationManager()`, so **no new matcher is needed**; it repeats `PaymentAdminController`'s local `requireAdmin` → `403` defense-in-depth check. `RECEPTIONIST` does not exist on any billing route; #45's manual-check-in precedent does not carry over. |
| D9 | Lombok / `record` | New constructor-injected classes (controller, adapter) use `@RequiredArgsConstructor`; DTOs stay `record` — CLAUDE.md's Boilerplate rule (2026-09-27). Existing billing classes are not retrofitted. |
| D10 | Route naming | `/api/v1/admin/billing/purchases`, consistent with `/api/v1/admin/billing/payments` (#33's D3) and `/api/v1/admin/physical/devices`. `status=EXCEPTION` is part of the URL contract; the underlying query is **fixed** to `EXCEPTION`, same shape as #33's fixed `AwaitingManualVerification` query behind its `status`/`substatus` params. |

## Capabilities

### New Capabilities

- `purchase-exception-admin-view`: the `ADMIN`-only, read-only, paginated list of physical purchases in `FulfillmentStatus.EXCEPTION` — row content and its `billing_payments` join, oldest-first ordering, page-size cap behaviour, empty-page behaviour, zero-session rows (D7), and role rejection.

### Modified Capabilities

- **None.** `purchase-exception-notification`'s requirements are unchanged — including "Only physical purchases are in scope" and "Reason is payload-only, never persisted", both of which this change deliberately preserves. No existing spec's behaviour changes; this is a purely additive read model.

## Approach

Mirror `PaymentAdminController`'s pending-verification list almost line-for-line — it is a reviewed, shipped, tested pattern for the identical problem shape.

1. **Port** (`application/port/out/PurchaseRepository.java`): add `ExceptionPurchasePage findInException(int page, int size)`, javadoc'd like `findAwaitingManualVerification` including the "caller rejects `size > 50`; this port does not clamp" note (D3, D4).
2. **Adapter** (`PurchaseRepositoryAdapter`): a JPQL projection-constructor query over `PurchaseJpaEntity` + `PaymentJpaEntity` with a separate `countQuery` and `Pageable`, then one flat `billing_purchase_sessions` read for the page's purchase ids, joined in memory (D5, D6). `@Transactional(readOnly = true)` — note that the existing read methods here are `Propagation.MANDATORY` because they run inside fulfillment transactions; a controller-driven read must **not** be, so this method declares its own default propagation.
3. **DTOs**: `ExceptionPurchasePage`/`ExceptionPurchaseItem` records in `application/dto`, `ExceptionPurchasePageResponse` with a `from(...)` factory in `infrastructure/web/dto` — the `PendingVerificationPage`/`PendingVerificationPageResponse` pair, duplicated in shape not in code.
4. **Controller**: `PurchaseAdminController` with one `@GetMapping`, `requireAdmin`, the `size > MAX_PAGE_SIZE` → `IllegalArgumentException` → existing `400` handler path, and `@PaymentEndpoint`-equivalent wiring so `PaymentExceptionHandler`'s mapping applies.

Per strict TDD each rejection and edge branch is a failing test first: `size = 51` → `400`, non-admin → `403`, empty result → `200` with an empty page, a zero-session `EXCEPTION` row → present with `[]` (D7), and an `ASSIGNED`/`PENDING_FULFILLMENT` purchase → absent.

No domain change, no migration, no new outbox event, no cross-module port, no `SecurityConfig` edit. Estimated **~150–250 changed lines including tests — a single PR, no chaining**.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/billing/.../application/port/out/PurchaseRepository.java` | Modified | One new paginated, `EXCEPTION`-fixed query method (D3) |
| `api/billing/.../infrastructure/persistence/adapter/PurchaseRepositoryAdapter.java` | Modified | JPQL projection query + `countQuery` + sessions read (D5, D6); own propagation, not `MANDATORY` |
| `api/billing/.../infrastructure/persistence/repository/PurchaseJpaRepository.java` | Modified | The `Pageable` projection query; `Page`/`Pageable` confined here (D3) |
| `api/billing/.../application/dto/ExceptionPurchasePage.java`, `ExceptionPurchaseItem.java` | New | Application-layer page + row records |
| `api/billing/.../infrastructure/web/dto/ExceptionPurchasePageResponse.java` | New | Web response with `from(...)`, mirroring `PendingVerificationPageResponse` |
| `api/billing/.../infrastructure/web/controller/PurchaseAdminController.java` | New | `GET /api/v1/admin/billing/purchases` (D8, D10) |
| `api/billing/.../infrastructure/config/BillingConfiguration.java` | Verify | Wire the controller/adapter if this module wires beans explicitly there |
| `api/auth/.../infrastructure/security/SecurityConfig.java` | Unchanged | `/api/v1/admin/**` → `ADMIN` already covers the route; **verify, do not edit** |
| `api/billing/.../domain/`, `MarkPurchaseExceptionUseCase`, `PurchaseExceptionNotificationPort` | Unchanged | Explicitly untouched (ADR-0028 state machine, #209's notification path) |
| `api/app/src/main/resources/db/migration/` | Unchanged | **No migration** — read-only addition |
| `api/openapi/billing-v1.yaml`, `bruno/API - Direct/billing/` | Modified | Contract + request for the new endpoint (issue DoD) |
| `docs/user-stories/` (#237's story doc, if present) | Modified | Record the D1/D2 boundaries and the #236 evidence |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| A reviewer reads the missing resolution action as an incomplete feature | High | D2 states it as an explicit boundary in Out of Scope, with the grep evidence (`MarkPurchaseAssignedUseCase` has zero admin callers); repeated in the PR body |
| `billing_purchases` has no timestamp, so ordering silently depends on the join | Med | D5 fixes ordering on `Payment.createdAt`; a test asserts oldest-first across two purchases with different payment timestamps |
| Zero-session `EXCEPTION` rows (#238) break the row mapper or get filtered out | Med | D7; a dedicated test asserts such a row is listed with an empty session array |
| Copying `PurchaseRepositoryAdapter`'s existing `Propagation.MANDATORY` onto the new read method would make every controller call fail | Med | Called out in Approach step 2; a controller-level (not adapter-unit-only) test exercises the endpoint outside any ambient transaction |
| Spring `Page`/`Pageable` leaks into `application`, breaking ArchUnit | Med | D3 mirrors the existing primitive-argument port precedent; `./gradlew test --tests "*ArchitectureTest"` gates it |
| A future unified `Purchase`+`Subscription` list (#236) may need to extend or replace this query/DTOs | Low | Accepted; the deliberate #236 split makes a later extension cheaper than a premature merge now |
| Billing coverage gate (85% domain+application / 85% infrastructure) | Low | Test-first per strict TDD; the adapter query and the controller are the two infrastructure surfaces needing deliberate tests |

## Rollback Plan

1. Revert the merge commit. The endpoint disappears; nothing else changes.
2. **No migration, no schema change, no data written** — there is nothing to un-migrate, backfill, or reconcile. This change only reads.
3. `MarkPurchaseExceptionUseCase`, the `PURCHASE_EXCEPTIONED` outbox event, and #209's notification path are never structurally altered, so a revert cannot regress them.
4. Purchases in `EXCEPTION` stay in `EXCEPTION` — the change never transitions anything (D2). Ops falls back to the #209 emails, exactly the pre-change situation.
5. The OpenAPI entry and Bruno request revert with the code; no environment, property, or secret is introduced to clean up.

## Dependencies

- **None blocking.** No new library, no new module edge, no new configuration property, no external service. Spring Data pagination, JPQL projection queries, and the `/api/v1/admin/**` rule are all already in use in `:api:billing`.
- Builds on **#209** (`purchase-exception-notification`, archived — not reopened) and reuses **#33**'s list pattern (`billing-manual-payment-verification`, archived — not modified).
- **#236** (extend `EXCEPTION` observability to virtual subscriptions) remains open and is **not** blocked, unblocked, or designed by this change.

## Success Criteria

- [ ] `GET /api/v1/admin/billing/purchases?status=EXCEPTION` returns `200` with oldest-first, paginated rows carrying buyer, `createdAt`, amount/currency, target reference, and covered sessions.
- [ ] Only purchases in `EXCEPTION` appear; `PENDING_FULFILLMENT` and `ASSIGNED` purchases are absent — asserted by test.
- [ ] A purchase in `EXCEPTION` with **zero** sessions (#238 path) is listed with an empty session array, neither dropped nor erroring (D7).
- [ ] `size = 51` returns `400` (rejected, not clamped); absent `size` defaults to 20; an empty result returns `200` with an empty page, not `404`.
- [ ] A non-`ADMIN` authenticated caller receives `403`; an anonymous caller receives `401/403`.
- [ ] No Flyway migration, no domain change, no outbox event, and no `SecurityConfig` matcher is added — verified by diff.
- [ ] `MarkPurchaseExceptionUseCase` and the #209 notification path keep their existing tests untouched and passing.
- [ ] OpenAPI contract and Bruno request updated; `./gradlew check` passes, including ArchUnit and the billing coverage gate (85% domain+application / 85% infrastructure).

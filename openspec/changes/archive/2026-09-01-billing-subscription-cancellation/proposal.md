# Proposal: Subscription Cancellation (US-BILLING-011, Issue #130)

> **Amended 2026-08-31** — product decisions D1–D3 resolved by the product owner after the
> first draft. D3 changes scope: checkout is no longer untouched. See *Amendments*.

## Intent

A student with a paid subscription cannot stop it renewing; every cancellation is manual support
work, and nobody can see until when access is kept. `SubscriptionStatus.CANCELLED` and
`VirtualCourseEntitlementService.grantsCurrentAccess` were already built for this story — only the
decision path is missing.

## Scope

### In Scope

- `DELETE /api/v1/billing/subscriptions/me` — student cancels own `ACTIVE` subscription; response states `endDate` and the informative `Plan.cancellationPolicy`.
- `DELETE /api/v1/admin/billing/subscriptions/{subscriptionId}` — admin cancels any subscription. **`reason` is mandatory (D1)**: blank/absent → `400`, before any state change.
- `Subscription.cancel(actor, reason, at)` guarded on `ACTIVE` only, plus `cancelledAt`/`cancelledBy`/`cancellationReason` fields, mapping and migration.
- `CancelSubscriptionUseCase` (+ impl) taking `actingUserId` + `isAdmin`; authorization decided in the use case.
- `SubscriptionNotFoundException` → 404 in the existing `SubscriptionExceptionHandler`.
- **Overlap notice on checkout (D3)** — advisory, non-blocking; contract below.

### Out of Scope

- Automatic refunds; `cancellationPolicy` stays informative text.
- `SubscriptionStatusChanged` event / Caffeine invalidation — **superseded**: no event bus or cache exists anywhere in `api/`, and archived change `2026-08-31-virtual-subscription-access` declared this an explicit no-goal.
- Automatic `CANCELLED → EXPIRED` sweep (US-BILLING-004).
- Any change to `SubscriptionStatus`, `grantsCurrentAccess`, or `Subscription.cancelled()`.
- Any admin listing/report of cancellations; the persisted `cancellationReason` column is the whole audit surface delivered here.
- Any notification on cancellation (no notification mechanism exists in the module).
- Raising billing's 90% coverage gate to the documented 100% (backlog #138).

## Capabilities

### New Capabilities
- `billing-subscriptions`: cancellation lifecycle, access retention until `endDate`, re-purchase after cancellation *including the overlap notice*, cancellation authorization and audit. Checkout is not otherwise retro-documented here.

### Modified Capabilities
- None.

## Approach

| Decision | Choice | Rationale |
|---|---|---|
| Admin route | Separate `/api/v1/admin/billing/subscriptions/{id}` | Matches the repo-wide `/api/v1/admin/{module}/**` convention; the `ROLE_ADMIN` gate stays in `SecurityConfig`, so no `isAdmin` branching on a student path and no id-enumeration oracle on `/me`. |
| Audit | Three nullable columns on `Subscription` | One-shot terminal transition, not a repeated action; an append-only audit table would add port + adapter + entity + migration for a single action type. |
| Domain method | New `cancel(...)`, not `cancelled()` | `cancelled()` guards on `occupiesUserSlot()`, so it would accept `PENDING` (escenario 4 requires 404), and it stamps no metadata. |
| **D1 — admin reason** | **Mandatory** | Every admin-initiated termination of a paid service must be explainable after the fact. Enforced twice: `@NotBlank` on `CancelSubscriptionRequest` (→ `400`) and a domain guard in `cancel(...)` when `actor != owner`. Self-cancellation via `/me` sends no reason and stores `null`. |
| **D2 — reason visibility** | **Internal only** | `cancellationReason` is persisted but MUST NOT appear in any student-facing response (`DELETE /me`, or any read of one's own subscription). An operational reason ("fraude", "chargeback") is not customer copy. Enforced by DTO shape — the student-facing records simply have no such component. |
| **D3 — re-purchase** | **Warn, never block** | The student keeps the right to buy; they must not be surprised into paying twice. |
| Events/cache | None | See Out of Scope. |

Both routes converge on one use case: `/me` resolves by token subject, the admin route by id.
Cancellation never touches `endDate`, so escenario 2 falls out of existing code.

### D3 — Overlap notice contract

**Overlapping paid period** — at checkout time the user has a subscription that is
`status = CANCELLED`, for the **same `planId`** now being purchased, whose `endDate` is present and
**strictly after `clock.now()`**. Different plan, expired `endDate`, or absent `endDate` (a
`PENDING` checkout that was cancelled) is **not** an overlap.

**Non-blocking** — no exception, no branch, no changed status code. Checkout still returns `201`
with its `checkoutUrl`. The notice is additive information, never a precondition.

**Wire shape** — one new optional component on the existing records:

- `SubscriptionCheckoutResult` gains `OverlapNotice overlapNotice` (nullable).
- `SubscriptionCheckoutResponse` gains `OverlapNotice overlapNotice`; `null` when there is no overlap.
- `record OverlapNotice(String code, Instant currentAccessEndsAt)`, with `code` fixed at
  `"OVERLAPPING_PAID_PERIOD"` and `currentAccessEndsAt` = the cancelled subscription's `endDate`.

The API returns a **code plus a date, never Spanish prose**, matching the existing
`SUBSCRIPTION_ALREADY_ACTIVE` error-code style; the BFF owns the user-facing wording and decides
whether to show a confirm step before redirecting to `checkoutUrl`. When several cancelled
subscriptions for the plan still have remaining access, the notice reports the **latest**
`endDate`. The notice is derived from current state on every response — including the idempotent
replay branch — so a retry of the same `idempotencyKey` never silently drops the warning.

**Detection query** — new read on `SubscriptionRepository`:
`Optional<Subscription> findLatestCancelledWithRemainingAccess(UUID userId, PlanId planId, Instant at)`.
Placed after the existing `findCurrentByUserId` slot check, which a `CANCELLED` row does not trip
(`occupiesUserSlot()` is false for it) — that is exactly why the silent double-pay is reachable today.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `domain/model/Subscription.java` | Modified | `cancel()` + 3 fields |
| `domain/exception/SubscriptionNotFoundException.java` | New | 404 |
| `application/port/in/CancelSubscriptionUseCase.java` + impl | New | Authorization + transition |
| `application/port/out/SubscriptionRepository.java` | Modified | `findActiveByUserId`, `findById`, `findLatestCancelledWithRemainingAccess` |
| `infrastructure/web/dto/CancelSubscriptionRequest.java` | New | `@NotBlank reason` (admin route only) |
| `infrastructure/web/controller/SubscriptionController.java` | Modified | `DELETE /me` |
| `.../SubscriptionAdminController.java` | New | Admin route |
| `.../SubscriptionExceptionHandler.java` | Modified | 404 mapping |
| `infrastructure/persistence/` + migration | Modified | 3 nullable columns + overlap query |
| `application/usecase/CreateSubscriptionCheckoutUseCaseImpl.java` | **Modified (D3)** | Computes the overlap notice; behavior otherwise unchanged |
| `application/dto/SubscriptionCheckoutResult.java` | **Modified (D3)** | `overlapNotice` component |
| `infrastructure/web/dto/SubscriptionCheckoutResponse.java` | **Modified (D3)** | `overlapNotice` component |
| `application/dto/OverlapNotice.java` | **New (D3)** | `code` + `currentAccessEndsAt` |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `cancelled()` reused by mistake, cancelling `PENDING` | Med | Separate method; test asserts `PENDING` → 404 |
| Two routes drift in behavior | Med | One shared use case; parity test |
| Admin cancels without traceability | Low | `cancelledBy` non-null always; `reason` non-blank on the admin path |
| D2 leak — `cancellationReason` reaches the student | Med | No such component on student-facing DTOs; controller test asserts the JSON key is absent |
| D3 turns into a block | Med | Test asserts `201` + `checkoutUrl` present *while* `overlapNotice` is non-null |
| D3 extra query on every checkout | Low | Indexed on `(user_id, plan_id, status)`; one `Optional` read on an already-transactional path |
| Mandatory reason blocks bulk operational cleanup | Low | Accepted by the product owner; a generic reason string is valid |
| Coverage gate is 90%, docs say 100% | Low | Target 100% for new code; do not assume enforcement |

## Rollback Plan

Revert the PR and apply a compensating migration dropping the three columns. Already-cancelled
rows keep `status = CANCELLED`; access remains correct because it derives from `endDate` only.
The D3 slice rolls back independently: `overlapNotice` is an additive optional field, so removing
it cannot break a client that never required it.

## Dependencies

- None. US-BILLING-010 (checkout) is already merged.

## Success Criteria

- [ ] All 5 issue scenarios pass as integration tests.
- [ ] Cancelled subscription keeps virtual access until `endDate` (no `grantsCurrentAccess` change).
- [ ] Re-purchase after cancellation creates a new row; the old one is never reactivated.
- [ ] Non-`ACTIVE` and non-owned targets return 404, never 403 or 200.
- [ ] Admin cancellation without a non-blank `reason` returns `400` and changes nothing (D1).
- [ ] No student-facing response ever contains `cancellationReason` (D2).
- [ ] Re-purchasing a plan whose cancelled subscription is still in force returns `201` **and** a non-null `overlapNotice` carrying that `endDate`; every other checkout returns `overlapNotice: null` (D3).

## Amendments

**D3 contradicts the exploration.** The exploration listed
`CreateSubscriptionCheckoutUseCaseImpl` under "no changes needed", and that conclusion **no longer
applies**. This is not an exploration error: the exploration correctly established that
cancellation alone does not require touching checkout, since a `CANCELLED` subscription frees the
user slot and re-purchase already works. The product owner then decided that *working silently* is
not the desired outcome — the student must be warned about the overlapping paid period. That is a
post-proposal product decision widening the scope, not a defect in the prior analysis.

## Proposal question round — resolved

1. ~~Admin cancellation reason mandatory?~~ **Resolved (D1): mandatory.**
2. ~~Reason visible to the student?~~ **Resolved (D2): internal only.**
3. ~~Cancel-and-re-purchase accepted silently, or warn?~~ **Resolved (D3): warn, without blocking.**

Accepted assumptions (no longer open):

- No cancellation listing/report in this change — endpoint only.
- No notification on cancellation — no notification mechanism exists in the module.

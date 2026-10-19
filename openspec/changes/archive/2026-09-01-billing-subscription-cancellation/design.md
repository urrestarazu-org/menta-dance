# Design: Subscription Cancellation (US-BILLING-011, Issue #130)

## Technical Approach

One `CancelSubscriptionUseCase` behind two HTTP adapters, following billing's existing
`actingUserId` + `isAdmin` shape (`PhysicalCoursePricingController`). The aggregate gains a
single nullable `Cancellation` value object instead of three loose scalars. D3's overlap notice
is computed inside `CreateSubscriptionCheckoutUseCaseImpl.toResult(...)`, the one funnel both the
new-checkout and the idempotent-replay branches already pass through.

## Architecture Decisions

| # | Decision | Alternatives rejected | Rationale |
|---|---|---|---|
| A1 | Audit as one nullable VO `Cancellation(Instant at, UUID by, String reason)` on `Subscription` | 3 parallel nullable scalars; separate audit table | The three values are written together, once, never independently. Keeps the canonical constructor at 14 args instead of 16, and `Optional<Cancellation>` states "cancelled or not" without inspecting three fields. Audit table rejected in the proposal. |
| A2 | New `cancel(UUID by, String reason, Instant at)`; `cancelled()` untouched | Reuse/reshape `cancelled()` | `cancelled()` guards on `occupiesUserSlot()`, so it accepts `PENDING` (escenario 4 needs 404) and stamps nothing. |
| A3 | `cancel(...)` throws `IllegalStateException` when `status != ACTIVE` | Idempotent no-op like `activate()`/`cancelled()` | Webhook replays are expected, so those are no-ops; a *user-initiated* cancel of a non-`ACTIVE` row is a caller bug. Unreachable over HTTP — both lookups filter to `ACTIVE` — so it is a domain-test-only invariant. |
| A4 | One use case, target as sealed `CancellationTarget` (`Own` \| `ById(UUID)`) | Nullable `subscriptionId`; two use cases | Mirrors the repo's existing sealed `PaymentTarget.Virtual`. Avoids a nullable id and keeps one authorization/transition path, so the two routes cannot drift (named risk). |
| A5 | `SubscriptionNotFoundException` → 404 for *absent*, *not-`ACTIVE`* and *not-owned* alike | 403 for not-owned | Same anti-oracle reasoning already written on `PlanNotAvailableException` in `SubscriptionExceptionHandler`. Satisfies "never 403 or 200". |
| A6 | Admin controller reuses the existing `@SubscriptionEndpoint` marker | New marker + new advice | `SubscriptionExceptionHandler` is `@RestControllerAdvice(annotations = SubscriptionEndpoint.class)`; reuse yields the 404/400/`INVALID_REQUEST` mappings for free. |
| A7 | **`SecurityConfig` needs a new rule for `DELETE /me`** | Assume the existing entry covers it | Line 156 is `HttpMethod.POST`-scoped and `anyRequest().access(roleAuthorizationManager)` **falls through to a grant** for unmapped paths (its own comment at line 149 says so). Add `.requestMatchers(HttpMethod.DELETE, "/api/v1/billing/subscriptions/me").authenticated()` before the admin gate. Admin path needs nothing: line 182 `/api/v1/admin/**` → `hasRole("ADMIN")` already covers it. |
| A8 | No new index for the D3 query | `(user_id, plan_id, status)` as the proposal sketched | Existing `idx_billing_subscriptions_user_status (user_id, status)` already keys it; rows per user are a handful, so `plan_id`/`end_date` stay cheap residuals. A 4th index taxes every `saveNewCheckout` insert on the hot checkout path. |
| A9 | Overlap notice computed in `toResult(...)`, made an instance method | Compute at each return site | Both the replay branch (line 86) and the new-checkout return (line 116) already call `toResult`; centralizing makes "replay silently drops the notice" *structurally* impossible instead of test-enforced. `subscription.getPlanId()` is correct on both paths. |
| A10 | `DELETE` returns `200` with a body, not `204` | `204 No Content` | The issue requires the response to state `endDate` and the informative `Plan.cancellationPolicy`. |

## Data Flow

    DELETE /me ─────┐                            ┌─→ findActiveByUserId(actor)
                    ├─→ CancelSubscriptionUseCase┤
    DELETE /admin/…/{id} ─┘  (authz + reason rule)└─→ findById(id) + isAdmin

           └─→ Subscription.cancel(by, reason, now) ─→ repo.save ─→ mapper
                        (endDate untouched)              (active_user_id → NULL)

    POST /subscriptions ─→ …existing… ─→ toResult(sub)
                                              └─→ findLatestCancelledWithRemainingAccess
                                                      └─→ OverlapNotice | null

`active_user_id` needs no code change: `SubscriptionJpaMapper` derives it from
`occupiesUserSlot()`, which is `false` for `CANCELLED`, so the slot frees itself and re-purchase
works (escenario 3). Cover it with a test, do not assume it.

## File Changes

| File (under `api/billing/src/main/java/com/menta/billing/`) | Action |
|---|---|
| `domain/model/Cancellation.java` | Create — `record Cancellation(Instant at, UUID by, String reason)` |
| `domain/model/Subscription.java` | Modify — field + `cancel(...)` + `getCancellation()`; 14-arg constructor, update `pendingCheckout`/`copy` |
| `domain/exception/SubscriptionNotFoundException.java` | Create — `SUBSCRIPTION_NOT_FOUND` |
| `application/port/in/CancelSubscriptionUseCase.java` | Create |
| `application/dto/CancelSubscriptionCommand.java`, `CancellationTarget.java`, `CancellationResult.java`, `OverlapNotice.java` | Create |
| `application/usecase/CancelSubscriptionUseCaseImpl.java` | Create |
| `application/usecase/CreateSubscriptionCheckoutUseCaseImpl.java` | Modify (D3) — `toResult` instance method |
| `application/dto/SubscriptionCheckoutResult.java` | Modify (D3) — `overlapNotice` component |
| `application/port/out/SubscriptionRepository.java` | Modify — 3 new reads |
| `infrastructure/persistence/{entity,mapper,adapter,repository}/…Subscription…` | Modify — 3 columns + 2 queries |
| `infrastructure/transaction/TransactionalCancelSubscriptionUseCase.java` | Create |
| `infrastructure/config/BillingConfiguration.java` | Modify — bean wiring |
| `infrastructure/web/controller/SubscriptionController.java` | Modify — `DELETE /me` |
| `infrastructure/web/controller/SubscriptionAdminController.java` | Create — `@SubscriptionEndpoint`, `/api/v1/admin/billing/subscriptions` |
| `infrastructure/web/controller/SubscriptionExceptionHandler.java` | Modify — 404 mapping |
| `infrastructure/web/dto/CancelSubscriptionRequest.java`, `CancelSubscriptionResponse.java` | Create |
| `infrastructure/web/dto/SubscriptionCheckoutResponse.java` | Modify (D3) |
| `api/auth/.../security/SecurityConfig.java` | Modify — A7 |
| `api/app/src/main/resources/db/migration/V17__billing_subscription_cancellation.sql` | Create |
| `api/openapi/billing-v1.yaml`, `bruno/API - Direct/billing/*.bru` | Modify/Create |

## Interfaces / Contracts

```java
// domain — reason is mandatory only when the actor is not the owner (D1, second gate)
public Subscription cancel(UUID by, String reason, Instant at);

// application
public sealed interface CancellationTarget {
    record Own() implements CancellationTarget {}
    record ById(UUID subscriptionId) implements CancellationTarget {}
}
public record CancelSubscriptionCommand(
    CancellationTarget target, UUID actingUserId, boolean isAdmin, String reason) {}
public record CancellationResult(
    String subscriptionId, SubscriptionStatus status, Instant accessEndsAt, String cancellationPolicy) {}
public record OverlapNotice(String code, Instant currentAccessEndsAt) {} // code = "OVERLAPPING_PAID_PERIOD"

// port out — findCurrentByUserId also matches PENDING, so it cannot serve escenario 4
Optional<Subscription> findActiveByUserId(UUID userId);
Optional<Subscription> findById(UUID subscriptionId);
Optional<Subscription> findLatestCancelledWithRemainingAccess(UUID userId, PlanId planId, Instant at);
```

`cancellationPolicy` comes from the existing `PlanRepository.findById(PlanId)` (not
`findActiveById` — a deactivated plan must still cancel). `CancelSubscriptionResponse` has **no**
`cancellationReason` component at all: D2's absence is structural, not a serializer setting.
JPA derived queries: `findByUserIdAndStatus`,
`findFirstByUserIdAndPlanIdAndStatusAndEndDateAfterOrderByEndDateDesc`.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Domain (target 100%) | `cancel` from `ACTIVE` only; `IllegalStateException` from `PENDING`/`CANCELLED`/`EXPIRED`; `endDate` never moved; `by` always stamped incl. self-cancel; blank reason from a non-owner rejected; `cancelled()` unchanged | JUnit 5, no mocks |
| Application | `Own` vs `ById` resolution; non-admin `ById` → 404; missing/blank reason → rejected before any `save`; policy text read from `findById`; **D3: notice present on the replay branch**; different plan / expired / null `endDate` ⇒ no notice; latest `endDate` wins | Mockito, verify `save` never called on the reject paths |
| Infrastructure | mapper round-trips `Cancellation` incl. all-null legacy rows; `active_user_id` becomes NULL on cancel; the two new queries | `SubscriptionRepositoryAdapterTest` + Testcontainers |
| Controller | `DELETE /me` 200 body shape; **JSON key `cancellationReason` absent, not null** (D2); admin blank reason → 400 with no state change; 404 problem shape | MockMvc + `SubscriptionExceptionHandler` |
| Integration | The 5 issue escenarios; access retained until `endDate` via `VirtualCourseEntitlementService`; re-purchase creates a new row and returns `201` **with** a non-null `overlapNotice` | `api/app` `@SpringBootTest` |

Billing's real gate is **0.90** domain+application (`api/billing/build.gradle.kts`), not the 100%
`CLAUDE.md` documents (backlog #138). Author to 100%; do not assume enforcement.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. The one security-relevant surface (A7, the `DELETE /me` fall-through
grant) is handled as an explicit design decision with a controller test.

## Migration / Rollout

`V17` adds `cancelled_at DATETIME(3) NULL`, `cancelled_by BINARY(16) NULL`,
`cancellation_reason VARCHAR(500) NULL`. No backfill and no index (A8): pre-existing `CANCELLED`
rows come from the payment-failure path and truthfully have no actor or reason, mapping back to
`Optional.empty()`. Rollback = revert + a compensating `DROP COLUMN`; access stays correct because
it derives from `endDate` only.

**PR slicing** — forecast ≈1500 changed lines, over the 800-line session budget, so chain three
slices (each self-contained, tested, revertible; PR *n* targets PR *n−1*'s branch):

1. **Domain + persistence** (~450) — `Cancellation`, `cancel(...)`, 3 port reads, entity/mapper/adapter, `V17`. No HTTP surface, no client-visible change.
2. **Use case + both endpoints** (~700) — use case, exception + mapping, both controllers, `SecurityConfig`, wiring, OpenAPI `DELETE`s, Bruno, escenarios 1–5.
3. **D3 overlap notice** (~300) — `OverlapNotice`, both DTOs, `toResult` centralization, OpenAPI field. Additive and independently revertible.

`api/openapi/README.md` requires OpenAPI **and** Bruno to move in the same change as any route
change, so Bruno coverage is mandatory here even though issue #130's Definition of Done omits it.

## Open Questions

- [ ] None blocking. Slice count (3) is a recommendation for `sdd-tasks`, not a locked contract.

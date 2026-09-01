# Exploration: US-BILLING-012 — Admin-assigned trial subscription (Issue #131)

## Current State

`billing-subscription-cancellation` (US-BILLING-011, issue #130) is fully merged, not just designed — `CancelSubscriptionUseCase`/`CancelSubscriptionUseCaseImpl`, `SubscriptionAdminController` (`DELETE /api/v1/admin/billing/subscriptions/{id}`), `SubscriptionController` (`DELETE /me`), `Cancellation` VO, `SubscriptionNotFoundException`, `SubscriptionRepository.findActiveByUserId/findById/findLatestCancelledWithRemainingAccess` all exist on disk exactly as `sdd/billing-subscription-cancellation/design` (Engram #866) specified. `Subscription`'s canonical constructor is now 14 args, last one `Cancellation cancellation` (nullable VO: `at`, `by`, `reason`).

**`SubscriptionStatus`** (`domain/model/SubscriptionStatus.java`) — 4 values: `PENDING`, `ACTIVE`, `EXPIRED`, `CANCELLED`. `occupiesUserSlot()` → true only for `PENDING`/`ACTIVE`. Javadoc already anticipated this story: *"Neither the cancellation endpoint nor the automatic expiry sweep is built here."* **Reconfirmed via grep**: `SubscriptionStatus.EXPIRED` has zero production usages anywhere in `api/` — only 2 hits, both in test files (`SubscriptionTest.java`, `VirtualCourseEntitlementServiceTest.java`). Nothing has EVER transitioned a subscription to `EXPIRED`. This issue is the first to build that sweep.

**Access cascade already type-agnostic — confirmed, no change needed.** `VirtualCourseEntitlementService.grantsCurrentAccess` checks only `status` (`ACTIVE`/`CANCELLED` + unexpired `endDate`) and the frozen `courseIds` snapshot. A `TRIAL` subscription that reuses `SubscriptionStatus.ACTIVE` gets identical treatment automatically, satisfying escenario 2 with zero changes to Virtual's read path.

**Escenario 4 rejection — confirmed reusable as-is.** `findCurrentByUserId` (used by `CreateSubscriptionCheckoutUseCaseImpl.create()`) already matches `PENDING`+`ACTIVE` regardless of any type/origin field, because origin is orthogonal to `SubscriptionStatus`. `SubscriptionAlreadyActiveException(current.getEndDate().orElse(null))` already carries the vigencia info the trial-assignment 409 needs to report. Same precedent already validated for checkout (US-BILLING-010) and cancellation (US-BILLING-011) — a trial-assignment use case throwing the same exception from the same query needs no new port method.

**Escenario 6 (`PLAN_NOT_AVAILABLE`) — exact existing precedent found.** `CreateSubscriptionCheckoutUseCaseImpl.create()`: `planRepository.findActiveById(PlanId.of(...)).orElseThrow(PlanNotAvailableException::new)`. `PlanNotAvailableException extends BusinessException`, `ERROR_CODE = "PLAN_NOT_AVAILABLE"`, already mapped to 422 in `SubscriptionExceptionHandler` (per cancellation exploration/design). Reuse verbatim for trial assignment — deliberately one exception for "does not exist" and "exists but INACTIVE" (anti-enumeration, same discipline as the checkout path).

**Admin authorization convention — confirmed, extend existing controller.** `SubscriptionAdminController` (`@RequestMapping("/api/v1/admin/billing/subscriptions")`, `@SubscriptionEndpoint`) already exists with the exact `actingUserId(authentication)`/`isAdmin(authentication)` idiom this story needs. `SecurityConfig`'s generic `/api/v1/admin/**` → `hasRole("ADMIN")` rule already covers any new route added under this controller — **no `SecurityConfig` change needed** (unlike cancellation's A7, which needed a new rule for `DELETE /me`; this story has no self-service route at all).

## Central Blocking Constraint — AC1 "SIN Payment asociado" vs. the aggregate's hard invariant

This is the one real architectural fork in this issue, and it must be resolved explicitly by `sdd-propose`/`sdd-design`, not silently.

`Subscription`'s constructor: `this.paymentId = Objects.requireNonNull(paymentId, "paymentId cannot be null")`. The DB schema (`V14__billing_checkout.sql`) backs this with:
```sql
payment_id BINARY(16) NOT NULL,
UNIQUE KEY uq_billing_subscriptions_payment_id (payment_id),
CONSTRAINT fk_billing_subscriptions_payment FOREIGN KEY (payment_id) REFERENCES billing_payments (id)
```
A trial subscription with **zero** `billing_payments` rows (AC1's literal requirement) cannot satisfy this NOT NULL FK column today. Verified `Payment.java` has no representable "no real transaction" state either: every field (`expectedAmount: Money`, `expectedExternalReference`, `expectedMerchantAccountId`, `PaymentStatus` sealed to `AwaitingProvider/Pending/Completed/Rejected/Cancelled/Expired`) models a real Mercado Pago transaction end-to-end — there is no sentinel/no-op status in that enum.

Two approaches, both real and neither silently pickable:

1. **Make `payment_id` nullable** (`Subscription.paymentId` → `Optional<PaymentId>`, `getPaymentId()` return type changes, `Subscription.trial(...)` static factory passes null). Migration: `ALTER TABLE billing_subscriptions MODIFY COLUMN payment_id BINARY(16) NULL` — MySQL's unique index already tolerates multiple NULLs (V14's own comment documents this verified behavior for `active_user_id`), and a NULL FK value is never checked by MySQL, so no FK relaxation is needed beyond the column nullability itself.
   - Pros: AC1 literally true (zero `billing_payments` rows for trials); `Payment` keeps its pure "real provider transaction" meaning; small, well-scoped migration.
   - Cons: `getPaymentId()`'s return type change ripples through every existing caller (`CreateSubscriptionCheckoutUseCaseImpl.toResult`/`externalReferenceFor`, `SubscriptionJpaMapper`, `SubscriptionCheckoutResult.from`) — bounded (a handful of call sites, `copy()` itself is unaffected since it doesn't take `paymentId` as a parameter) but a real, must-audit ripple, same shape as the cancellation change's constructor growth.
   - Effort: Medium.
2. **Synthesize a fake $0 `Payment` row** for every trial (e.g., `PaymentStatus.Completed` at zero amount, a sentinel `expectedMerchantAccountId`/`PaymentMethod`).
   - Pros: zero schema change, zero ripple to `getPaymentId()`/constructor beyond a new origin field.
   - Cons: **directly contradicts AC1's literal text**; corrupts `Payment`'s domain meaning for every future reader (finance reporting, reconciliation, `PaymentVerificationService`) who must now learn to filter out fake rows that were never a real transaction.
   - Effort: Low, but architecturally the wrong tradeoff.

**Recommendation**: Approach 1 (nullable `payment_id`). AC1 is explicit and testable ("SIN Payment asociado"); faking a Payment row is the kind of hack this codebase's own conventions (structurally-impossible-states-over-nullable-flags, e.g. D2 in the cancellation design omitting `cancellationReason` from JSON rather than serializing null) consistently reject elsewhere.

## Scheduled Sweep — exact precedent found, single-instance, no distributed lock

Two `@Scheduled` reconcilers exist, confirmed via `grep -rln "@Scheduled" api/`:
- `WebhookInboxReconciler` (`api/billing/.../infrastructure/webhook/`) — lives **inside `api:billing`** because it never composes ports across modules. `@Scheduled(fixedRateString = "${billing.webhook.reconcile-rate-ms:5000}")`, paginated batch (`PageRequest.of(0, batchSize)`, configurable `@Value` batch size), no distributed lock, no ShedLock, single-instance assumption implicit.
- `OutboxBlacklistReconciler` (`api/app/.../outbox/`) — lives in `api:app` specifically because it composes `TokenBlacklistPort` (Redis) across the `auth` module at the composition root. Also has a second daily `@Scheduled(cron = ...)` cleanup job as a precedent for a retention/cleanup task, and writes a Redis heartbeat key for health signaling — not applicable here.
- `@EnableScheduling` lives on `api/auth`'s `SecurityConfig` (already active monorepo-wide) — no new enabling annotation needed anywhere.

**No expiry sweep exists today for ANY subscription — PAID or TRIAL.** The new sweeper must cover both uniformly (`status == ACTIVE && endDate.isBefore(now) → EXPIRED`), since expiry is a time-based transition orthogonal to origin, exactly like the access cascade. Given it only touches `SubscriptionRepository` (no cross-module composition), the `WebhookInboxReconciler` precedent (live in `api:billing`, not `api:app`) is the correct model, not `OutboxBlacklistReconciler`'s.

Needs: a new `Subscription.expire(Instant at)` domain method (monotonic guard like `cancelled()`, transitions `ACTIVE`→`EXPIRED` only, `endDate` untouched, no actor/reason — automatic, not admin-attributed) and a new repository read (`findAllByStatusAndEndDateBefore(ACTIVE, now, pageSize)` or similar, paginated the same way `WebhookInboxJpaRepository.findEligibleForProcessing` is).

## Resolved Contradiction — `SubscriptionStatusChanged` event / cache (reconfirmed)

Re-ran `grep -r "SubscriptionStatusChanged\|Caffeine\|CacheManager\|@Cacheable\|@CacheEvict\|ApplicationEventPublisher\|@EventListener\|@TransactionalEventListener" api/` → **zero matches**, identical to the cancellation exploration's finding. No event-publishing or caching mechanism exists anywhere in the monorepo. The issue's technical note about the expiry sweep emitting `SubscriptionStatusChanged` is the same stale/superseded requirement already documented against the `virtual-subscription-access` archived design (issue #56) and the cancellation exploration (#863). Must be recorded again as an explicit "out of scope / superseded" decision, not silently dropped.

## Coverage Gate Discrepancy (reconfirmed)

`api/billing/build.gradle.kts` still gates domain+application at **0.90 LINE (BUNDLE)** with the inline comment *"Temporary policy; raise in a dedicated task"* — unchanged since the cancellation exploration. `CLAUDE.md`'s test-strategy table documents billing at **100%**. Same known discrepancy, tracked as backlog issue #138. Author new domain/application code toward 100% but do not assume the build enforces it.

## Affected Areas

- `api/billing/src/main/java/com/menta/billing/domain/model/Subscription.java` — new `SubscriptionOrigin` (or similar) enum field (simple field, not a VO — matches `FulfillmentStatus`'s shape); new `Subscription.trial(...)` static factory (admin-supplied `endDate` from days, not `Plan.durationDays`); constructor grows to 15 args; `paymentId` nullability decision (see above) if Approach 1 is chosen; new `expire(Instant at)` method; new nullable audit VO for the grant (`at`, `by`, `reason`, `days` — same shape family as `Cancellation`).
- `api/billing/src/main/java/com/menta/billing/domain/model/PaymentId.java` / `getPaymentId()` — return-type ripple if Approach 1 is chosen.
- `api/app/src/main/resources/db/migration/` — new `V18` (or next number) for the `payment_id` nullability change (if Approach 1) and any new `origin`/audit columns.
- `api/billing/src/main/java/com/menta/billing/infrastructure/persistence/` (entity/mapper/adapter/repository) — persist new field(s); new paginated expiry-eligible query.
- `api/billing/src/main/java/com/menta/billing/application/port/in/` — new `AssignTrialSubscriptionUseCase` (or similarly named) port + impl, following `CancelSubscriptionUseCaseImpl`'s exact `actingUserId`/`isAdmin`-independent-of-`SecurityConfig` shape (this route is admin-only end to end, so no `Own` vs `ById` split is needed — simpler than cancellation's two-target design).
- `api/billing/src/main/java/com/menta/billing/infrastructure/web/controller/SubscriptionAdminController.java` — new endpoint (e.g. `POST /api/v1/admin/billing/subscriptions/trial`), `@SubscriptionEndpoint` already gives 404/422/400 mappings for free; no `SecurityConfig` change needed.
- `api/billing/src/main/java/com/menta/billing/infrastructure/webhook/` sibling package (e.g. `infrastructure/scheduling/`) — new `SubscriptionExpiryReconciler`, modeled on `WebhookInboxReconciler`, living in `api:billing` (not `api:app`).
- `api/billing/src/main/java/com/menta/billing/domain/exception/PlanNotAvailableException.java` — reused verbatim, zero change.
- `api/billing/src/main/java/com/menta/billing/application/usecase/VirtualCourseEntitlementService.java` — confirmed NOT affected.
- `api/billing/build.gradle.kts` — no change proposed here; discrepancy flagged only.
- Outside billing: `api/openapi/billing-v1.yaml` and `bruno/API - Direct/billing/*.bru` — new route needs both per `api/openapi/README.md`'s convention (same rule the cancellation change followed).

## Approaches (for the `payment_id` fork — the only real design fork in this issue)

1. **Nullable `payment_id`, `Optional<PaymentId>` on the aggregate** — see above.
   - Pros: AC1 literally satisfied; `Payment` stays semantically pure.
   - Cons: getter-return-type ripple across ~4-6 call sites; needs a migration touching an existing NOT NULL column.
   - Effort: Medium.
2. **Synthetic zero-amount `Payment` row per trial** — see above.
   - Pros: no schema change, smallest diff.
   - Cons: contradicts AC1's literal text; corrupts `Payment`'s domain meaning for every future reader.
   - Effort: Low.

## Recommendation

Approach 1 (nullable `payment_id`) for the Payment fork. Reuse, unchanged: `PlanNotAvailableException` (escenario 6), `findCurrentByUserId`+`SubscriptionAlreadyActiveException` (escenario 4), `VirtualCourseEntitlementService.grantsCurrentAccess` (escenario 2), `SubscriptionAdminController`'s `actingUserId`/`isAdmin` idiom and its existing `SecurityConfig` coverage. Model the expiry sweep on `WebhookInboxReconciler` (single-instance, no distributed lock, lives in `api:billing`), extended to cover PAID and TRIAL uniformly since none exists today for either. Record the `SubscriptionStatusChanged`/cache note as explicitly superseded again, same as issue #130. Flag but do not fix the billing coverage-gate discrepancy (#138).

## Risks

- The `payment_id` nullability decision is a genuine fork with real ripple either way — `sdd-propose`/`sdd-design` must decide explicitly before locking the aggregate's new constructor shape; picking Approach 2 silently would violate AC1's literal wording.
- The expiry sweep is genuinely new infrastructure (first-ever `EXPIRED` transition in the codebase) covering both TRIAL and PAID subscriptions — scope creep risk if `sdd-tasks` narrows it to TRIAL-only, since the issue's own wording ("vencimiento automático... el proyecto ya tiene planificación de tareas en producción") implies reusing one general mechanism, not a trial-specific one.
- No distributed lock exists on either scheduled-task precedent; if the sweep needs multi-instance safety, that is new ground, not something to copy blindly from the existing single-instance reconcilers without confirming deployment topology.
- Coverage gate discrepancy (90% real vs. 100% documented) is pre-existing and out of this change's scope, per issue #138 — do not let it block or expand this change's own target.
- The trial-assignment response DTO shape is undecided: reusing `SubscriptionCheckoutResult` (which carries meaningless `checkoutUrl`/`providerPreferenceId` fields for a trial) vs. a new lighter DTO is an open question for `sdd-propose`, following this codebase's own preference for structurally-impossible-states over nullable-and-ignored fields (D2 precedent from the cancellation design).

## Ready for Proposal

Yes. The two things `sdd-propose` must decide explicitly are: (1) the `payment_id` nullability approach (Approach 1 recommended) and the resulting `Subscription.trial(...)` factory/constructor shape, and (2) the exact shape of the trial-assignment response DTO. Everything else (access cascade, escenario-4 rejection, escenario-6 plan validation, admin authorization, `SecurityConfig` coverage, event/cache non-requirement) is confirmed reusable as-is with no blocking unknowns.

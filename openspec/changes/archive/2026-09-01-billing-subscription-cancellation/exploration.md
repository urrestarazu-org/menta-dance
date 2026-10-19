# Exploration: US-BILLING-011 — Subscription cancellation (Issue #130)

## Current State

**`SubscriptionStatus`** (`api/billing/src/main/java/com/menta/billing/domain/model/SubscriptionStatus.java`) already declares four distinct values — `PENDING`, `ACTIVE`, `EXPIRED`, `CANCELLED` — and its Javadoc explicitly anticipates this story: *"CANCELLED and EXPIRED are separate on purpose (US-BILLING-011): cancellation is a decision, expiry is the passage of time... Neither the cancellation endpoint nor the automatic expiry sweep is built here — this enum only makes them representable."* `occupiesUserSlot()` returns `true` only for `PENDING`/`ACTIVE`; `CANCELLED` and `EXPIRED` already free the slot. **No code change needed here.**

**`Subscription`** (`domain/model/Subscription.java`) has a `cancelled()` method, but it is the US-BILLING-010 escenario-6 terminal transition for a checkout whose payment never settled (guarded by `occupiesUserSlot()`, invoked from the payment-verification path, not from a student/admin action). It does **not** record a cancellation timestamp, actor, or reason, and does not distinguish "cancelled by whom/why" — none of that state exists on the aggregate today. A new user-initiated cancellation needs either: (a) a new `cancel(Instant at)`-style method (or reuse `cancelled()` if the state transition is provably identical) that only applies to `ACTIVE`, plus new fields (`cancelledAt`, `cancelledBy`, `cancellationReason`) if audit is tracked on the aggregate itself, or (b) a separate audit-trail row (see "Audit precedent" below) while `Subscription` itself only gains a `cancelledAt`. Reusing the existing `cancelled()` verbatim is unsafe — it does not stamp any cancellation metadata and its guard (`occupiesUserSlot`) would also silently accept a `PENDING` subscription, which escenario 4 says must 404 instead.

No cancellation endpoint, use case, or port exists in `api/billing` at all — `grep -ri cancel api/billing` only turns up: the `Subscription.cancelled()` domain method, `SubscriptionAlreadyActiveException` (unrelated — it's the checkout-conflict exception), and the E2E Mercado Pago simulator fixture. Confirms escenario coverage starts from zero.

**Access calculation already supports escenario 2** — `VirtualCourseEntitlementService.grantsCurrentAccess` (application/usecase, line 45-51):
```java
private static boolean grantsCurrentAccess(Subscription subscription, String courseId, Instant now) {
    return subscription.getFulfillmentStatus() == FulfillmentStatus.ASSIGNED
        && (subscription.getStatus() == SubscriptionStatus.ACTIVE
            || subscription.getStatus() == SubscriptionStatus.CANCELLED)
        && subscription.getEndDate().filter(end -> end.isAfter(now)).isPresent()
        && subscription.getCourseIds().contains(courseId);
}
```
This already treats `CANCELLED` + unexpired `endDate` as access-granting, exactly matching "cancelar = no renovar, no perder lo pagado." **No change needed** once `Subscription` correctly transitions ACTIVE→CANCELLED without touching `endDate`.

**`Plan.cancellationPolicy`** is a plain immutable `String` field (constructor-validated non-null, getter `getCancellationPolicy()`), already exposed through `PlanDetailResult`/the plan-detail read path. It is informative text only — no behavior hangs off it, matching the issue's NFR ("informativa al confirmar la baja, no altera el cálculo de vigencia"). No domain change needed; the cancellation response/confirmation step should surface it as read-only text sourced from the existing plan.

**Rejection-by-ACTIVE-only (escenario 3) already correct.** `CreateSubscriptionCheckoutUseCaseImpl.create()` calls `subscriptionRepository.findCurrentByUserId(command.userId())` and throws `SubscriptionAlreadyActiveException` only if present. The port contract (`SubscriptionRepository.findCurrentByUserId` Javadoc) is explicit: *"At most one result may exist: it is either PENDING... or ACTIVE... A cancelled or expired subscription does not occupy the slot and must not be returned."* Once cancellation correctly writes `CANCELLED`, this query — and therefore escenario 3 — needs zero changes. A new subscription checkout after cancellation creates a brand-new row via `saveNewCheckout`, never reactivates the old one — consistent with the issue's requirement.

**Admin-vs-self authorization precedent — found INSIDE `billing` itself**, better than the cross-module `PhysicalCourseAdminController` example: `PhysicalCoursePricingController` (`infrastructure/web/controller/PhysicalCoursePricingController.java`) already implements the exact pattern needed for escenario 5:
```java
PhysicalCoursePricingResult result = updatePhysicalCoursePricingUseCase.update(
    courseId, command, actingUserId(authentication), isAdmin(authentication)
);
private static UUID actingUserId(Authentication authentication) {
    return UUID.fromString(authentication.getName());
}
private static boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
}
```
The controller never enforces the role itself for the coarse gate (`SecurityConfig` does that); it passes `actingUserId` + `isAdmin` down and the use case makes the per-resource authorization decision (self-ownership vs admin override). `SubscriptionController` (existing, `POST /api/v1/billing/subscriptions`) already uses the same `actingUserId` idiom (no admin branch yet, since checkout has no admin path). The new cancellation use case should follow this exact shape: `cancel(subscriptionIdOrSelf, actingUserId, isAdmin, reasonOrNull)`.

**Open design question**: the issue only specifies `DELETE /api/v1/billing/subscriptions/me` for the student flow (escenario 1). Escenario 5 ("cancelación por un administrador desde el panel") implies a second route (e.g. `DELETE /api/v1/admin/billing/subscriptions/{id}` or a `{id}` variant of the same path) — not specified in the issue. This is a real open question for `sdd-propose`, not something to silently resolve here.

**Audit precedent** — `VirtualCourseAuditRepository`/`VirtualCourseAuditJpaEntity` (`api/virtual/.../application/port/out/VirtualCourseAuditRepository.java`, `.../infrastructure/persistence/entity/VirtualCourseAuditJpaEntity.java`) is the established append-only structured-audit shape: `append(entityId, actorId, action, previousValue, newValue)`, one row per operation, never updated. It exists because US-VIRTUAL-006 needed a queryable, structured before/after log for *repeated* admin operations on courses/modules/lessons — a genuinely different shape from this story, where cancellation is a single terminal one-time transition per subscription. Two reasonable approaches for escenario 5's "auditoría de actor y motivo": (1) add `cancelledAt`, `cancelledBy`, `cancellationReason` columns directly onto `Subscription`/its JPA entity (simplest, matches the one-shot nature of cancellation, no new table); (2) introduce a `SubscriptionAudit`-style append-only table mirroring `VirtualCourseAuditRepository` (more consistent with the existing cross-module audit convention, but adds a new port/adapter/entity/migration for a single action type). Recommend (1) as default unless `sdd-propose` anticipates more auditable subscription actions soon.

**Exception + HTTP-mapping convention confirmed.** Every billing failure path is a `BusinessException` subclass (e.g. `PlanNotFoundException`, `SubscriptionAlreadyActiveException`) paired with a `@RestControllerAdvice`-annotated `*ExceptionHandler` scoped by a marker annotation (`@PublicBillingEndpoint`, `@SubscriptionEndpoint`, etc.) that maps it to an RFC 9457 `ProblemDetail` via `ProblemDetails.response(...)`. Escenario 4 (404 when no ACTIVE subscription) needs a new `SubscriptionNotFoundException extends BusinessException` plus a handler entry (either in a new `SubscriptionExceptionHandler` or added to the existing `@SubscriptionEndpoint`-scoped handler, if one already exists for the checkout controller — needs to be confirmed by `sdd-propose`/`sdd-design`, not found in this exploration pass).

## Resolved Contradiction — `SubscriptionStatusChanged` event / Caffeine cache

**Confirmed via `grep -r "SubscriptionStatusChanged\|Caffeine\|CacheManager\|@Cacheable\|@CacheEvict" api/` → zero matches anywhere in the monorepo.** Also confirmed zero usages of `ApplicationEventPublisher`, `@EventListener`, or `@TransactionalEventListener` anywhere in `api/` — there is no domain-event publishing mechanism in this codebase at all, in any module, not just billing.

This matches and reinforces what the already-archived `virtual-subscription-access` change (issue #56, `openspec/changes/archive/2026-08-31-virtual-subscription-access/`) declared as an explicit no-goal: *"No Caffeine cache, `SubscriptionStatusChanged` event, listener, TTL, or invalidation test is implemented. Direct Billing reads meet next-request freshness."* `VirtualCourseEntitlementService.resolveCourseAccess` reads `SubscriptionRepository`/`PlanRepository` directly and synchronously on every call — there is no cached snapshot to invalidate.

**Recommendation for `sdd-propose`**: treat issue #130's "emite `SubscriptionStatusChanged` para invalidación de caché" note as **stale/superseded**, not applicable. It predates the architectural decision recorded in the `virtual-subscription-access` archive. Do not implement any event publication or cache invalidation for this change. This must be recorded as an explicit decision in the proposal (e.g. an "Explicitly out of scope" or "Superseded requirement" note citing the archived change), not silently dropped.

## Coverage Gate Discrepancy

`CLAUDE.md`'s test-strategy table states billing's domain+application floor is **100%**. The actual `api/billing/build.gradle.kts` (line 53-58) currently gates domain+application at **0.90 LINE (BUNDLE)**, with an inline comment: *"Temporary policy; raise in a dedicated task."* Infrastructure is 0.85, matching the documented value. **This is a real discrepancy** — `CLAUDE.md` is ahead of the actual ratchet (matches the pending Backlog item #138, "[TECH-DEBT] Revisar y elevar umbrales de cobertura JaCoCo"). `sdd-tasks`/`sdd-apply` for this change should target the CLAUDE.md-documented 100% for any new domain/application code it authors, but must not assume the build will enforce it — the actual gate script enforces 90% today. Flag this to the user/proposal as a known pre-existing gap, not something this change is responsible for closing.

## Affected Areas

- `api/billing/src/main/java/com/menta/billing/domain/model/Subscription.java` — needs a cancellation-capable transition (new method or reworked `cancelled()`), plus new fields for `cancelledAt`/actor/reason if audited on the aggregate.
- `api/billing/src/main/java/com/menta/billing/domain/model/SubscriptionStatus.java` — no change; `CANCELLED`/`EXPIRED` already distinct and already documented as built for this story.
- `api/billing/src/main/java/com/menta/billing/application/port/out/SubscriptionRepository.java` — likely needs a `findActiveByUserId`/`findById` style lookup for the cancellation use case (escenario 4's 404 needs "no ACTIVE subscription for this user/id" lookup distinct from `findCurrentByUserId`, which also matches `PENDING`).
- `api/billing/src/main/java/com/menta/billing/application/usecase/` — new `CancelSubscriptionUseCase` (port) + impl, following the `UpdatePhysicalCoursePricingUseCase` shape (`actingUserId`, `isAdmin`, ownership check inside the use case).
- `api/billing/src/main/java/com/menta/billing/infrastructure/web/controller/SubscriptionController.java` — add `DELETE` mapping(s); needs the admin-route design question resolved first.
- `api/billing/src/main/java/com/menta/billing/domain/exception/` — new `SubscriptionNotFoundException` (escenario 4).
- `api/billing/src/main/java/com/menta/billing/infrastructure/persistence/` (mapper/entity/adapter) — persist new cancellation fields.
- `api/billing/src/main/java/com/menta/billing/application/usecase/VirtualCourseEntitlementService.java` — confirmed NOT affected; `grantsCurrentAccess` already correct for CANCELLED + unexpired endDate.
- `api/billing/build.gradle.kts` — coverage gate currently 90% domain+application, not 100% as CLAUDE.md states; flag, don't silently assume either value.

## Approaches

1. **Cancellation metadata directly on `Subscription`/JPA entity** — add `cancelledAt`, `cancelledBy`, `cancellationReason` as nullable columns on the existing subscription table/aggregate.
   - Pros: no new table/port/adapter; matches the one-shot nature of cancellation (not a repeated-action audit); smallest migration; keeps `findCurrentByUserId`/`findAllByUserId` as the only read paths needed.
   - Cons: `Subscription` aggregate grows by three fields it only ever needs once in its lifecycle; slightly muddies the "commercial lifecycle vs fulfillment" two-axis design if not documented carefully.
   - Effort: Low

2. **Separate `SubscriptionAudit` append-only table** mirroring `VirtualCourseAuditRepository`/`VirtualCourseAuditJpaEntity`.
   - Pros: consistent with the existing cross-module structured-audit convention; extensible if billing ever needs to audit other subscription actions later; keeps `Subscription` aggregate lean.
   - Cons: new port + adapter + JPA entity + repository + migration for a single action type; over-engineered for "cancel happens once, ever, per subscription."
   - Effort: Medium

## Recommendation

Approach 1 (fields on `Subscription`) for the cancellation metadata, combined with the confirmed-safe reuse of `VirtualCourseEntitlementService.grantsCurrentAccess` as-is. Explicitly drop the `SubscriptionStatusChanged`/cache-invalidation requirement as superseded by the `virtual-subscription-access` archived design, documenting this as a visible decision rather than a silent omission. `sdd-propose` must resolve the open admin-route question (single `DELETE /subscriptions/{id}` with `isAdmin` branching vs. a separate `/admin/...` path) before `sdd-design`.

## Risks

- Reusing `Subscription.cancelled()` verbatim would incorrectly allow cancelling a `PENDING` subscription (escenario 4 requires 404, not cancellation) and would not stamp any cancellation metadata — must not be reused without a guard change.
- The admin cancellation route/path is unspecified in issue #130 and needs an explicit decision before `sdd-design` can lock the API contract.
- The real coverage gate for billing domain+application is 90%, not the 100% CLAUDE.md documents — do not let `sdd-tasks` silently assume either number is the enforced truth without flagging it.
- No existing `SubscriptionExceptionHandler`/`@SubscriptionEndpoint`-scoped handler was located during this pass for the checkout controller's own errors — needs confirmation before assuming where the new 404 mapping belongs.

## Ready for Proposal

Yes — investigation found no blocking unknowns in the domain model itself (the hardest parts, `SubscriptionStatus` and `grantsCurrentAccess`, are already correct/complete). The two things `sdd-propose` must decide explicitly are: (1) the admin cancellation route shape, and (2) formally recording the `SubscriptionStatusChanged`/cache requirement as superseded rather than silently dropped.

# Proposal: Admin-assigned Trial Subscription (US-BILLING-012, Issue #131)

## Intent

An admin cannot let a student evaluate the full catalog without a real Mercado Pago charge. Today
every subscription is born from a payment, and nothing in the codebase has ever expired one:
`SubscriptionStatus.EXPIRED` has zero production usages. This change adds the admin grant path and
builds the first expiry sweep the platform has.

## Scope

### In Scope

- `POST /api/v1/admin/billing/subscriptions/trial` — body `userId`, `planId`, `days`, `reason`; `201`.
- `SubscriptionType { PAID, TRIAL }` on `Subscription`, plus `Subscription.trial(...)` creating an
  `ACTIVE` + `ASSIGNED` row with the same frozen `courseIds` snapshot as a paid subscription,
  `startDate = now`, `endDate = now + days`, and **no** `Payment` row.
- Nullable `payment_id` (D1) and the `TrialGrant` audit VO (D5), with migration `V18`.
- `SubscriptionExpiryReconciler` + `Subscription.expire(at)` — the first `ACTIVE → EXPIRED`
  transition, covering **PAID and TRIAL alike** (D2).
- **Cross-module existence check (D8)**: new `shared/auth/UserExistencePort`, its `auth` adapter, and a
  `UserNotFoundException` → `404` so a trial can never be granted to a `userId` that does not exist.
- Reused unchanged: `findCurrentByUserId` + `SubscriptionAlreadyActiveException` (409, AC4),
  `PlanNotAvailableException` (422, AC6), `VirtualCourseEntitlementService` (AC2). AC4 and AC6 keep
  their status codes and their relative order; D8 only inserts a `404` **ahead** of both.

### Out of Scope

- Any user data crossing into `billing` beyond a boolean (D8): no email, name, role, or status.
- Any direct `api:billing → api:auth` Gradle dependency; the edge stays `billing → shared ← auth`.
- Retro-validating the `userId` of subscriptions that already exist, and any FK on
  `billing_subscriptions.user_id` — the check is application-level only.
- `SubscriptionStatusChanged` event / cache invalidation — **superseded** (D4).
- Any `SecurityConfig` change: `/api/v1/admin/**` → `hasRole("ADMIN")` already covers the route.
- Any limit on trials per student, any cap on `days`, any student-facing or self-service trial route.
- Trial reporting/listing, notifications, refunds; raising billing's 90% coverage gate (backlog #138).
- Distributed locking for the sweep — both existing reconcilers are single-instance.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `billing-subscriptions`: trial assignment (admin-only, audited, no payment, **granted only to a
  user that exists** — D8), subscription type never entering authorization, and automatic expiry of
  any subscription past its `endDate`.

## Approach

| # | Decision | Choice | Rationale |
|---|---|---|---|
| D1 | `Payment` for trials | **Nullable `payment_id`**; `getPaymentId()` → `Optional<PaymentId>` | AC1 says "SIN Payment asociado" literally. The repo already refuses to invent a Payment: `ReconciliationTaskJpaEntity.payment_id` is nullable with exactly that Javadoc. A $0 fake row would corrupt `Payment` for reconciliation and finance readers forever. `V18` does `MODIFY payment_id BINARY(16) NULL`; the UNIQUE index admits many NULLs (documented for `active_user_id` in `V14`) and MySQL never checks an FK on NULL. Ripple is bounded: `SubscriptionJpaMapper`, `CreateSubscriptionCheckoutUseCaseImpl`, `SubscriptionCheckoutResult.from`, `PaymentVerificationService`. |
| D2 | Expiry sweep | One reconciler for **PAID + TRIAL**, in `api:billing` | Expiry is time-based and orthogonal to origin, like the access cascade. Modeled on `WebhookInboxReconciler` (`@Scheduled(fixedRateString)`, paginated batch, no lock) — not `OutboxBlacklistReconciler`, which lives in `api:app` only because it composes cross-module ports. `@EnableScheduling` is already active. A TRIAL-only sweep would be scope creep in reverse: it would leave paid subscriptions unexpired forever. |
| D3 | Response DTO | **New** `TrialAssignmentResult` + `TrialSubscriptionResponse`; do **not** reuse `SubscriptionCheckoutResult` | `checkoutUrl`, `providerPreferenceId` and `overlapNotice` are meaningless for a trial. Same discipline as D2 of #130: structurally impossible states over nullable-and-ignored fields. Carries `subscriptionId`, `userId`, `planId`, `type`, `status`, `startDate`, `endDate`. |
| D4 | Events / cache | None | No event bus or cache exists anywhere in `api/` (re-verified). Already declared superseded in #130 and in archived `virtual-subscription-access`. Recorded, not silently dropped. |
| D5 | Grant audit | `TrialGrant(Instant at, UUID by, String reason, int days)`, nullable VO | Same shape family and mapping idiom as `Cancellation` (4 nullable columns, `toTrialGrant` null-guarded on `granted_at`). `reason` is mandatory (`@NotBlank` → `400`), mirroring D1 of #130: free access granted by a human must be explainable afterwards. `days` is stored because it is the admin's decision, not derivable from `Plan.durationDays`. |
| D6 | Type in authorization | `SubscriptionType` is descriptive only | No branch on it in access, expiry, or the AC4 slot check; AC2 then falls out of existing code. |
| D7 | `idempotencyKey` | Server-generated `"trial:" + subscriptionId` | The aggregate invariant (non-blank) and the `(user_id, idempotency_key)` unique index both stay intact without a client-supplied key. |
| D8 | Unknown `userId` | New `shared/auth/UserExistencePort` — `boolean existsById(UUID)` — implemented in `auth`, consumed by `AssignTrialSubscriptionUseCaseImpl`; absent user → `UserNotFoundException` → `404` | Gap A7 from design: `billing_subscriptions.user_id` has **no FK** and `billing` has no way to ask `auth` anything, so today an admin typo would return `201` and leave an orphan trial row that no student can ever use and no query can explain. The product owner chose to close it now rather than discover it in support. The contract is deliberately the smallest one that answers the question — a boolean, never a `User` — so `billing` gains no view of identity data. `shared/auth/` is a **new package**; `shared/billing/` (`VirtualCourseEntitlementPort`, ADR-0039) is the shape precedent. `api:billing` and `api:auth` already depend on `api:shared`, so no build file changes: the edge is `billing → shared ← auth`, never `billing → auth`. The `auth` side reuses the existing `UserRepository`/`UserRepositoryAdapter`, adding `existsById(UserId)` next to today's `existsByEmail` so existence never materialises the aggregate. |

`ACTIVE` + `ASSIGNED` at creation is required for `grantsAccess()`; the slot projection
`active_user_id` then blocks a second subscription (AC4) and frees itself on expiry (AC5).

**Validation order in `AssignTrialSubscriptionUseCaseImpl` (D8) — explicit, not incidental.**
With several problems at once, the first failure is deterministic:

1. **User exists** → `404` (`UserNotFoundException`).
2. **Plan active** → `422` (`PlanNotAvailableException`).
3. **No subscription in force** → `409` (`SubscriptionAlreadyActiveException`).

Existence comes first because step 3 *queries by `userId`*: run against an unknown user it returns
"no current subscription" and passes silently, so the conflict rule is only meaningful once the
subject is known to exist. Steps 2 and 3 keep the exact relative order of the paid path in
`CreateSubscriptionCheckoutUseCaseImpl` (plan before conflict), so a reader learns one rule, not two.
No `Subscription` is constructed and nothing is written until all three pass. Unlike the anti-oracle
choices in `SubscriptionExceptionHandler` (which collapse causes on purpose), a distinct `404` here
is safe: the route is behind `hasRole("ADMIN")`, so the caller is trusted with knowing whether a
user id exists — and a mistyped id is precisely what the admin needs told apart from a plan problem.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `domain/model/Subscription.java` | Modified | `Optional<PaymentId>`, `SubscriptionType`, `TrialGrant`, `trial(...)`, `expire(...)` |
| `domain/model/SubscriptionType.java`, `TrialGrant.java` | New | Enum + audit VO |
| `application/port/in/AssignTrialSubscriptionUseCase` + impl | New | Admin-only, `actingUserId`/`isAdmin` |
| `application/port/out/SubscriptionRepository.java` | Modified | Paginated expiry-eligible read |
| `application/dto/TrialAssignmentResult.java` | New | D3 |
| `infrastructure/web/controller/SubscriptionAdminController.java` | Modified | New `POST /trial` |
| `infrastructure/web/dto/` | New | Request (`@NotBlank reason`, `@Positive days`) + response |
| `infrastructure/scheduling/SubscriptionExpiryReconciler.java` | New | D2 |
| `infrastructure/persistence/` (entity, mapper, adapter, repo) | Modified | Nullable `payment_id`, type, grant columns, sweep query |
| `db/migration/V18__billing_subscription_trial.sql` | New | Nullability + 5 columns |
| `api/shared/.../shared/auth/UserExistencePort.java` | New | D8 — new package; boolean-only contract |
| `api/auth/.../infrastructure/persistence/adapter/UserExistenceAdapter.java` | New | D8 — `@Component`, matching `UserRepositoryAdapter`'s location and naming; delegates to `UserRepository` |
| `api/auth/.../domain/repository/UserRepository.java` + `UserRepositoryAdapter.java` | Modified | D8 — add `existsById(UserId)` mirroring the existing `existsByEmail` |
| `domain/exception/UserNotFoundException.java` (billing) | New | D8 — extends `BusinessException`, code `USER_NOT_FOUND` |
| `infrastructure/web/controller/SubscriptionExceptionHandler.java` | Modified | D8 — `UserNotFoundException` → `404`, same shape as `subscriptionNotFound` |
| `api/openapi/billing-v1.yaml`, `bruno/API - Direct/billing/` | Modified | Route contract (incl. the D8 `404`) + manual collection |
| `VirtualCourseEntitlementService`, `SecurityConfig`, `build.gradle.kts` | **Unchanged** | Confirmed by exploration; D8 needs no build change — both modules already depend on `api:shared` |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `Optional<PaymentId>` ripple breaks a paid-path call site | Med | Compiler finds all of them; regression test on checkout + webhook fulfillment |
| Sweep expires a paid subscription wrongly | Med | Guard is `status == ACTIVE && endDate < now`; `expire()` is monotonic and never touches `endDate` |
| Multi-instance deployment double-runs the sweep | Low | `expire()` is idempotent (non-`ACTIVE` is a no-op); topology matches existing single-instance reconcilers |
| A future reader treats `TRIAL` as an authorization input | Low | D6 asserted by test: identical access assertions for PAID and TRIAL |
| Migration on an existing NOT NULL column | Low | Relaxing NOT NULL is backward-compatible; no data rewrite |
| Coverage gate is 90%, docs say 100% | Low | Target 100% for new domain/application code; do not assume enforcement (#138) |
| **D8 sets a precedent**: first cross-module port in the `billing → auth` direction | Med | Deliberate and documented: `shared/auth/` mirrors `shared/billing/` (ADR-0039), the contract is a single boolean, and the module graph is unchanged (`billing → shared ← auth`). Record it as an ADR amendment so the next `billing → auth` need extends this port instead of inventing a second path or a direct dependency |
| A later contributor widens `UserExistencePort` into a user-data read for `billing` | Med | Javadoc states the boolean-only rule and its reason. The hard guard is Gradle, not ArchUnit: `api/billing/build.gradle.kts` declares no `project(":api:auth")`, so `billing` cannot import `com.menta.auth.*` at all — any widening must be argued as a change to `shared`. Billing's `ArchitectureTest` only enforces layer rules today; adding a cross-module rule there is optional follow-up, not a prerequisite |
| D8 makes the trial path depend on the `auth` bean being present | Low | Wiring is a single constructor injection resolved at startup — a missing bean fails the context, not a request; the existing `BillingConfiguration` bean tests are the precedent |
| Existence check and creation are not atomic (user deleted in between) | Low | Accepted: no user-deletion path exists today (`deleteById` has no production caller), and the outcome would be a trial no one can use — the same state the check already prevents in every realistic case |

## Rollback Plan

Revert the PR and apply a compensating migration dropping the type/grant columns and restoring
`payment_id NOT NULL` — **only after** deleting or back-filling trial rows, which are the sole rows
with a NULL `payment_id`. Expired rows need no compensation: `EXPIRED` is terminal and access
already derives from `endDate`. The sweep can be disabled alone by setting its rate property.

D8 reverts independently and needs no migration: dropping the `UserExistencePort` call restores the
prior (permissive) behaviour, and the port plus its `auth` adapter are additive — nothing else reads
them, and no existing row was validated or rewritten.

## Dependencies

- None. US-BILLING-010 (checkout) and US-BILLING-011 (cancellation, #130) are merged.

## Success Criteria

- [ ] All 6 issue scenarios pass as integration tests, plus **AC7 (new, D8)**: assigning a trial to a
      `userId` that does not exist returns `404` (`USER_NOT_FOUND`) and writes **no** row.
- [ ] The order is asserted, not assumed: an unknown `userId` **with** an inactive plan returns `404`,
      not `422`; a known `userId` with an inactive plan returns `422` (AC6); a known `userId` with an
      active plan and a subscription in force returns `409` (AC4).
- [ ] `billing` never receives a `User`: `UserExistencePort` returns only `boolean`, and no
      `com.menta.auth.*` import exists anywhere under `api/billing/`.
- [ ] A trial row exists with `payment_id IS NULL` and **zero** `billing_payments` rows created.
- [ ] A TRIAL and a PAID subscription produce byte-identical virtual access assertions.
- [ ] The sweep moves both a stale PAID and a stale TRIAL to `EXPIRED` with no manual action.
- [ ] Assignment without a non-blank `reason` or with `days <= 0` returns `400` and changes nothing.
- [ ] After expiry or cancellation, buying a paid plan creates a new row; the trial is never reused.

## Amendments

**A1 — D8 added after design (owner-approved).** `sdd-design` reported gap A7: `billing` had no way
to verify that an admin-supplied `userId` exists, and `billing_subscriptions.user_id` carries no FK,
so a typo would have returned `201` and created an orphan trial. The product owner chose to widen
this change's scope rather than defer it. D8 records that decision; D1–D7 are unaffected — none of
them constrains validation, module topology, or error mapping, so nothing above is reverted.

## Proposal question round (auto mode — assumptions pending review)

1. Should `days` have an upper bound (e.g. 90)? **Assumed: no cap** — admin discretion, per "días fijados por el admin".
2. Is the grant `reason` internal-only, like `cancellationReason` (D2 of #130)? **Assumed: yes** — it never reaches a student-facing response.
3. Should the sweep's first run be rate-limited given it may find a backlog of never-expired paid rows? **Assumed: the existing paginated batch shape is sufficient.**
4. Does an admin need to end a trial early? **Assumed: no new path** — the existing admin cancellation (#130) already covers it.
5. (D8) Should a trial also be refused to a user that exists but is `INACTIVE`/not activated? **Assumed: no** — D8 checks existence only. Blocking on status is a separate policy the owner has not asked for, and it would make the port leak lifecycle state into `billing`.
6. (D8) Should the `404` distinguish "user does not exist" from "plan does not exist"? **Assumed: yes, distinct codes** — `USER_NOT_FOUND` (404) vs `PLAN_NOT_AVAILABLE` (422); the route is admin-only, so the anti-oracle collapse used on student-facing routes does not apply.

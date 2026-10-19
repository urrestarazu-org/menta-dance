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
- Nullable `payment_id` (D1) and the `TrialGrant` audit VO (D5), with migration `V18` in
  `api/app/src/main/resources/db/migration/` — the repository's single Flyway location.
- The aggregate invariants that replace the dropped `requireNonNull` (design A17): `PAID` implies a
  payment and no grant, `TRIAL` implies a grant and no payment, enforced in the canonical constructor.
- A new repository write, `saveNewSubscription` (design A12): a trial is born `ACTIVE` with its course
  snapshot already known, so the slot claim **and** the snapshot must commit together. Reusing the
  checkout write would have produced a `201` with zero enabled courses.
- `SubscriptionExpiryReconciler` + a separate `SubscriptionExpiryWorker` bean + `Subscription.expire(at)`
  — the first `ACTIVE → EXPIRED` transition, covering **PAID and TRIAL alike** (D2). The split is
  required, not cosmetic (design A6): Spring's proxy does not intercept self-invocation, and the
  repository's `save` is `Propagation.MANDATORY`. `expire(at)` carries a temporal guard (design A13):
  a non-`ACTIVE` row is a silent no-op, a not-yet-due `endDate` throws.
- A real off switch for the sweep (design A16): `billing.subscription.expiry.enabled`, evaluated by
  `@ConditionalOnProperty` on the reconciler class. The pre-existing rate property cannot disable a
  `@Scheduled` job at any value.
- Optimistic locking on `billing_subscriptions` (design A14): `@Version` plus a
  `ObjectOptimisticLockingFailureException → 409 SUBSCRIPTION_CONFLICT` mapping, so the sweep and a
  concurrent cancellation cannot silently overwrite each other. **This widens an existing contract**:
  the handler is a `@RestControllerAdvice(annotations = SubscriptionEndpoint.class)`, so the `409`
  becomes reachable on the cancellation routes delivered in #130 and on the checkout route, not only
  on `/trial`. It is a `500 → 409` narrowing — no previously successful call starts failing — and it
  is documented on all four routes in OpenAPI and Bruno.
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
| `domain/model/Subscription.java` | Modified | `Optional<PaymentId>`, `SubscriptionType`, `TrialGrant`, `trial(...)`, `expire(...)` with its temporal guard (A13), the type/payment/grant invariants (A17), and the `version` the aggregate now carries (A14) |
| `domain/model/SubscriptionType.java`, `TrialGrant.java` | New | Enum + audit VO |
| `application/port/in/AssignTrialSubscriptionUseCase` + impl | New | Admin-only, `actingUserId`/`isAdmin` |
| `application/port/out/SubscriptionRepository.java` | Modified | Two methods: the id-projection expiry read (A2) and `saveNewSubscription` — slot claim plus course snapshot in one transaction (A12) |
| `application/dto/TrialAssignmentResult.java` | New | D3 |
| `infrastructure/web/controller/SubscriptionAdminController.java` | Modified | New `POST /trial` |
| `infrastructure/web/dto/` | New | Request (`@NotBlank reason`, `@Positive days`) + response |
| `infrastructure/scheduling/SubscriptionExpiryReconciler.java` | New | D2 — `@Component`, `@ConditionalOnProperty` on the class (A16), `tick()` not transactional |
| `infrastructure/scheduling/SubscriptionExpiryWorker.java` | New | A6 — separate `@Component` with `@Transactional(REQUIRES_NEW)`; the self-invocation fix, not a refactor. Registered by `@Component` only, like the webhook pair — **no `@Bean` in `BillingConfiguration`** (A6b) |
| `infrastructure/config/BillingConfiguration.java` | Modified | One new `@Bean`: the trial use case with its `UserExistencePort` |
| `infrastructure/persistence/` (entity, mapper, adapter, repo) | Modified | Nullable `payment_id`, type, grant columns, `@Version`, sweep query, `saveNewSubscription` |
| `api/app/src/main/resources/db/migration/V18__billing_subscription_trial.sql` | New | Nullability + 5 columns + `version` + sweep index. Flyway scripts live only under `api/app/` (verified against V14/V15/V17), never under a feature module |
| `api/shared/.../shared/auth/UserExistencePort.java` | New | D8 — new package; boolean-only contract |
| `api/auth/.../infrastructure/persistence/adapter/UserExistenceAdapter.java` | New | D8 — `@Component`, matching `UserRepositoryAdapter`'s location and naming; delegates to `UserRepository` |
| `api/auth/.../domain/repository/UserRepository.java` + `UserRepositoryAdapter.java` | Modified | D8 — add `existsById(UserId)` mirroring the existing `existsByEmail` |
| `domain/exception/UserNotFoundException.java` (billing) | New | D8 — extends `BusinessException`, code `USER_NOT_FOUND` |
| `infrastructure/web/controller/SubscriptionExceptionHandler.java` | Modified | D8 — `UserNotFoundException` → `404`, same shape as `subscriptionNotFound`; A14 — `ObjectOptimisticLockingFailureException` → `409 SUBSCRIPTION_CONFLICT`, which applies to **every** `@SubscriptionEndpoint` controller |
| `api/v1/subscriptions`, `/subscriptions/me`, `/admin/billing/subscriptions/{id}` | **Contract widened** | A14 — already-merged routes (#130 cancellation, checkout) gain a reachable `409`. No code change in those controllers; the change is in the shared advice and must be documented and reviewed as a contract change |
| `api/openapi/billing-v1.yaml`, `bruno/API - Direct/billing/` | Modified | `/trial` route contract (incl. the D8 `404`) + manual collection, **plus the A14 `409` on the three pre-existing subscription routes** |
| `VirtualCourseEntitlementService`, `SecurityConfig`, `build.gradle.kts` | **Unchanged** | Confirmed by exploration; D8 needs no build change — both modules already depend on `api:shared` |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `Optional<PaymentId>` ripple breaks a paid-path call site | Med | Compiler finds all of them; regression test on checkout + webhook fulfillment |
| Sweep expires a paid subscription wrongly | Med | Guard is `status == ACTIVE && endDate <= now` in **both** the SQL predicate and the domain guard (A13), so the sweep can never select a row the aggregate would refuse; `expire()` is monotonic and never touches `endDate`, and a not-yet-due row throws loudly instead of expiring |
| A trial is created with an empty course snapshot | **Was High, now closed** | Design A12 found that reusing `saveNewCheckout` would have returned `201` with zero enabled courses and no exception anywhere. Closed by the dedicated `saveNewSubscription` write plus a regression lock: one test asserts the trial write persists every course id, a twin test asserts the checkout write still persists none |
| Sweep and a concurrent cancellation overwrite each other (lost update, incl. erasing a cancellation audit trail) | Med | Design A14: `@Version` on the entity and the version carried through the aggregate; the loser gets `ObjectOptimisticLockingFailureException`, which the sweep logs and retries next tick and the HTTP layer maps to `409` instead of `500`. Verified by an integration test that commits a cancellation between the sweep's read and its write |
| **A14 changes the observable contract of endpoints already in production** | Med | The `409` mapping lives in a `@RestControllerAdvice(annotations = SubscriptionEndpoint.class)` shared by `SubscriptionController` and `SubscriptionAdminController`, so it reaches the #130 cancellation routes and the checkout route. It is a `500 → 409` narrowing, so nothing that succeeded before now fails; it is documented on all four routes in OpenAPI and Bruno and flagged in the PR description rather than shipped implicitly |
| The sweep is registered twice and two ticks run concurrently | Med | A6b fixes a single registration path: `@Component` on both classes, no `@Bean` in `BillingConfiguration` — matching `WebhookInboxReconciler`/`WebhookVerificationWorker`, which are scanned only. Asserted on the running context: exactly one bean of each type |
| The documented sweep off switch does not actually switch anything | Med | A16: `billing.subscription.expiry.rate-ms` can only change the interval, never disable a `@Scheduled` job. The switch is `billing.subscription.expiry.enabled` via `@ConditionalOnProperty` **on the reconciler class** — Spring ignores that annotation on a plain method — and a test asserts the bean is absent when it is `false` |
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
already derives from `endDate`. The sweep can be disabled alone by setting
`billing.subscription.expiry.enabled=false` — **not** by the rate property, which only changes the
interval and can never stop a `@Scheduled` job (design A16). The A14 `409` mapping reverts with the
sweep slice, restoring the previous behaviour on the pre-existing subscription routes.

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
- [ ] Assignment without a non-blank `reason` or with `days` absent, zero or negative returns `400` and
      changes nothing (normative scenario in `spec.md`, not just a criterion here).
- [ ] `endDate` is `now + days` where `days` comes from the admin's request; a plan whose
      `durationDays` differs proves it was never derived from the plan.
- [ ] `V18` applies cleanly to the existing schema under Testcontainers **in the same slice that
      introduces it**, leaving pre-existing rows at `type = 'PAID'`, `version = 0` and their
      `payment_id` intact.
- [ ] After expiry or cancellation, buying a paid plan creates a new row; the trial is never reused.

## Amendments

**A1 — D8 added after design (owner-approved).** `sdd-design` reported gap A7: `billing` had no way
to verify that an admin-supplied `userId` exists, and `billing_subscriptions.user_id` carries no FK,
so a typo would have returned `201` and created an orphan trial. The product owner chose to widen
this change's scope rather than defer it. D8 records that decision; D1–D7 are unaffected — none of
them constrains validation, module topology, or error mapping, so nothing above is reverted.

**A2 — scope and risk description brought up to date after adversarial design review.** Two review
rounds over `design.md` surfaced implementation-level facts this proposal had not described: the
born-`ACTIVE` write needs its own repository method or the trial ships with no courses (A12); the
sweep must be two beans because Spring cannot proxy self-invocation and the repository save is
`MANDATORY` (A6), each registered exactly once via `@Component` (A6b); `expire(at)` needs a temporal
guard that fails loudly on a not-yet-due row (A13); the sweep races cancellation, so the row needs
`@Version` and a `409` mapping that — importantly — widens the contract of endpoints already merged
in #130 (A14); the documented sweep off switch did not exist and now does (A16); and dropping
`requireNonNull` on `paymentId` requires stating the type/payment/grant invariants explicitly (A17).
The In Scope, Affected Areas, Risks and Rollback sections above now reflect all of them. **D1–D8 are
unchanged**: every item here is a consequence of how those decisions are implemented, not a revision
of what was decided.

## Proposal question round (auto mode — assumptions pending review)

1. Should `days` have an upper bound (e.g. 90)? **Assumed: no cap** — admin discretion, per "días fijados por el admin".
2. Is the grant `reason` internal-only, like `cancellationReason` (D2 of #130)? **Assumed: yes** — it never reaches a student-facing response.
3. Should the sweep's first run be rate-limited given it may find a backlog of never-expired paid rows? **Assumed: the existing paginated batch shape is sufficient.**
4. Does an admin need to end a trial early? **Assumed: no new path** — the existing admin cancellation (#130) already covers it.
5. (D8) Should a trial also be refused to a user that exists but is `INACTIVE`/not activated? **Assumed: no** — D8 checks existence only. Blocking on status is a separate policy the owner has not asked for, and it would make the port leak lifecycle state into `billing`.
6. (D8) Should the `404` distinguish "user does not exist" from "plan does not exist"? **Assumed: yes, distinct codes** — `USER_NOT_FOUND` (404) vs `PLAN_NOT_AVAILABLE` (422); the route is admin-only, so the anti-oracle collapse used on student-facing routes does not apply.

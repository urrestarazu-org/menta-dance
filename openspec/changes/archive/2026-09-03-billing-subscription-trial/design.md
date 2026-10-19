# Design: Admin-assigned Trial Subscription (US-BILLING-012, #131)

## Technical Approach

Extend the `Subscription` aggregate with two descriptive fields (`SubscriptionType`, `TrialGrant`), relax `paymentId` to nullable, add two transitions (`trial(...)`, `expire(at)`), and reach them through one admin-only use case plus one scheduled sweep split into a reconciler and a per-row transactional worker. Because a trial is born `ACTIVE` with its course snapshot already known, it needs a repository write the paid path never needed: one that claims the slot **and** persists the snapshot in the same transaction (A12). D8 adds the platform's first `billing → shared ← auth` port so an unknown `userId` is rejected before anything is written. No new module, no new bounded context, no event bus; every authorization and access read is existing code reused verbatim (proposal D6).

## Architecture Decisions

| # | Decision | Choice | Rejected | Rationale |
|---|---|---|---|---|
| A1 | `paymentId` shape | Constructor keeps `PaymentId paymentId` but drops `requireNonNull`; `getPaymentId()` → `Optional<PaymentId>` | `Optional` as a constructor parameter | Exact `startDate`/`endDate`/`cancellation` idiom already in this class. Keeps the public arity at **16** (14 + `type` + `trialGrant`); a second 17-arg **rehydration** constructor carries `version` (A14) so no existing call site changes arity twice. |
| A2 | Sweep query + index | `findExpirableIds(Instant now, int limit)` → `List<UUID>`, predicate `status = 'ACTIVE' AND end_date <= :now`; new `KEY idx_billing_subscriptions_status_end_date (status, end_date)` in V18 | Returning `List<Subscription>`; reusing `idx_billing_subscriptions_user_status` | `toDomainWithCourses` runs one extra course query **per row**, for a snapshot the reconciler never reads — an id projection avoids an N+1 on every tick and keeps the read-to-write window short for A14. That index leads on `user_id`; the sweep filters `status + end_date` with no user, so it would full-scan. `<=` mirrors the domain guard exactly (A13). |
| A3 | Type/grant columns | Six nullable columns on `billing_subscriptions`, `type` `NOT NULL DEFAULT 'PAID'`, plus `version` (A14) | Separate `billing_subscription_trials` table | Written once, together, never queried independently — V17's `Cancellation` reasoning. `DEFAULT 'PAID'` back-fills without a data pass. `type` is a non-reserved word in MySQL 8.0, so the column name can match the field name. |
| A4 | Use-case authorization | Single path, `isAdmin` guard in the impl before any lookup | Reuse `CancellationTarget` sealed shape | No self-service route exists. Defense-in-depth mirrors `CancelSubscriptionUseCaseImpl.resolveById`. |
| A5 | Validation order | `reason`/`days` (400) → admin guard → **user exists (404)** → plan (422) → slot (409) | Validate after reading the subscription | D1 of #130: never partially validated after a read. The 404 leads the business checks (A8). |
| A6 | Sweep transaction | `SubscriptionExpiryReconciler.tick()` carries **no** `@Transactional` and delegates each row to a **separate bean**, `SubscriptionExpiryWorker.expireOne(UUID)` (`REQUIRES_NEW`); `tick()` wraps each call in `try/catch (RuntimeException)` and continues the batch | A `@Transactional` method on the reconciler itself; a batch-wide transaction | **Verified precedent**: `WebhookInboxReconciler.tick()` is not transactional and calls the injected `WebhookVerificationWorker.process(row)` for exactly this reason — Spring's AOP proxy does not intercept self-invocation, so `this.expireOne(...)` inside the same class would run with **no transaction at all**, and `SubscriptionRepositoryAdapter.save` is `Propagation.MANDATORY`, so it would fail outright. `REQUIRES_NEW` (not `REQUIRED`) matches the worker precedent and keeps the guarantee if a future caller — a test, or an admin "run now" route — ever wraps `tick()` in a transaction. One bad row logs and is skipped; `expire()` is idempotent, so the next tick retries. |
| A6b | Sweep bean registration | **`@Component` on both classes and NO `@Bean` in `BillingConfiguration`** — exactly one registration path per class | A `@Bean` method for each; `@Component` **and** a `@Bean` | **Verified precedent**: `WebhookInboxReconciler` and `WebhookVerificationWorker` appear nowhere in `BillingConfiguration` — they are `@Component`-scanned only, and A6 explicitly copies that pair. Declaring both would register **two** instances of the same scheduler, i.e. two concurrent `tick()`s racing over the same `findExpirableIds` batch — the exact lost-update pressure A14 exists to contain. `BillingConfiguration` keeps `@Bean`s only for what it actually composes (the use case with its ports), so the scanned/composed split stays the one a reader already knows. Consequence for tests: a plain `BillingConfigurationTest` can never observe these beans, so the "two distinct beans" assertion moves to the `@SpringBootTest` context (Testing Strategy). |
| A7 | ~~User existence not validated~~ | **Superseded by D8** — closed by A8/A9 below | — | The gap this row recorded (orphan trial + no 404) is now in scope. |
| A8 | Existence contract | New `com.menta.shared.auth.UserExistencePort` — `boolean existsById(UUID userId)` and nothing else | `UserLookupPort` returning a user projection; an FK on `billing_subscriptions.user_id` | The smallest contract that answers the question. A projection would leak identity data into `billing` and invite reuse creep; an FK would couple two modules' schemas and require retro-validating existing rows (proposal Out of Scope). `shared/auth/` is new but `shared/billing/` (ADR-0039) is the shape precedent — the direction is simply inverted. |
| A9 | `auth`-side implementation | `UserRepository.existsById(UserId)` + `UserRepositoryAdapter` delegating to `JpaRepository.existsById(UUID)`; `UserExistenceAdapter` (`@Component`) implements the shared port on top of the domain port | Adapter calling `UserJpaRepository` directly; adapter calling `findById(...).isPresent()` | The port belongs to `auth`'s domain, so the cross-module adapter must go through it, not around it. `findById` would materialise the aggregate to answer a boolean. **No new method on `UserJpaRepository`**: `existsById(UUID)` is inherited from `CrudRepository`. |
| A10 | Cross-module wiring | `UserExistenceAdapter` is `@Component`-scanned in `auth`; `BillingConfiguration` injects the `UserExistencePort` **interface** into the `assignTrialSubscriptionUseCase` `@Bean` method | A `@Bean` in `AuthConfiguration`; a manual bridge bean in `api:app` | Exactly inverts the ADR-0039 precedent. `@SpringBootApplication(scanBasePackages = "com.menta")` picks the component up; a missing bean fails the context at startup, never a request. **No build-file change**: `billing`, `auth` and `app` each already declare `implementation(project(":api:shared"))` (verified). |
| A11 | Module guard | Rely on Gradle; no new ArchUnit rule | New `billing`-side ArchUnit rule banning `com.menta.auth..` | `api/billing/build.gradle.kts` declares no `project(":api:auth")`, so a `com.menta.auth.*` import in `billing` cannot compile — a stronger and earlier guard than a test. |
| A12 | Persisting a born-`ACTIVE` subscription | New port method `saveNewSubscription(Subscription)`: same `save` + `flush()` + `DataIntegrityViolationException → SubscriptionAlreadyActiveException` slot claim as `saveNewCheckout`, **plus** `replaceCourses(subscription)` in the same transaction | Reusing `saveNewCheckout`; widening `saveNewCheckout` to always call `replaceCourses`; naming it `saveNewTrial` | **Verified bug, otherwise silent**: `saveNewCheckout` returns `SubscriptionJpaMapper.toDomain(saved, List.of())` and never calls `replaceCourses` — correct for a checkout, whose snapshot is written *later* by the `subscriptionRepository.save(...)` at `CreateSubscriptionCheckoutUseCaseImpl:117` once the payment settles. A trial has no later step, so reusing that method would return `201` with a trial that has **zero** enabled courses, breaking the spec's "Admin grants a trial subscription" and "Trial and paid produce identical access decisions" scenarios with no exception anywhere. Widening `saveNewCheckout` would add a pointless `DELETE` + `flush` to the paid path and contradict its Javadoc contract. `saveNewTrial` would put the type in the persistence contract and invite a type branch there, against D6 — the method is named for the *shape* of the write (slot claim + snapshot), not the flow. |
| A13 | `expire(at)` guards | Two guards with **different** failure modes: `status != ACTIVE` → silent no-op returning `this`; `endDate` not `<= at` → `IllegalStateException` | Both as no-ops; both as throws; strict `isBefore` | Split by whether the situation is a legitimate race or a caller bug, which is exactly how this aggregate already reads: `cancelled()`/`activate()` no-op on a wrong status because a concurrent transaction can legitimately move it, while `cancel()` **throws** on a non-`ACTIVE` status because its callers filter to `ACTIVE` first, so reaching it is a bug. Time only moves forward, so a row that was expiry-eligible stays eligible — a non-past `endDate` can only mean a broken eligibility predicate, never a race, and must be loud. The boundary is `!endDate.isAfter(at)` (i.e. `endDate <= at`) to match the platform's existing access semantics: `findLatestCancelledWithRemainingAccess` treats access as remaining only while `endDate > at`. A2's SQL predicate is the same expression, so the sweep can never select a row the domain refuses. |
| A14 | Expiry/cancellation lost update | `@Version` **primitive** `long version` on `SubscriptionJpaEntity`, `version BIGINT NOT NULL DEFAULT 0` in V18, and the version carried through `Subscription` (rehydration constructor + `copy(...)`, mapped both ways) | A wrapper `Long version`; `@Version` on the entity alone; a raw conditional `UPDATE ... WHERE status = 'ACTIVE'` for the sweep; a separate V19 | **Verified**: the entity has no `@Version` today, so a sweep and a cancellation can both read an `ACTIVE` row and the later commit silently overwrites the other — including erasing a cancellation audit trail. Two verified constraints force the exact shape: (1) `SubscriptionJpaMapper.toEntity` builds a **brand-new detached entity** on every write, so if it did not carry the real version, Hibernate's merge would compare `0` against the stored version and fail *every* update after the first — the version must travel with the aggregate; (2) a **wrapper** `Long` would make Spring Data's `isNew()` inspect the version instead of the id and call `persist()` on updates, while a **primitive** keeps the id-based check and therefore the exact merge behaviour in production today. A raw conditional `UPDATE` was rejected because `SubscriptionJpaMapper` is the documented **sole writer** of the `active_user_id` slot projection, and expiry must free that slot; SQL would become a second writer of that invariant. V18 already alters this table three ways for one logical change, and the column ships in the same slice, so a separate V19 would only add a second DDL pass. |
| A15 | Role of the target user | **Not validated.** `UserExistencePort` stays existence-only; choosing a real student is the admin's operational responsibility | Widening the port to expose `Role`; a second `UserRoleQueryPort` | A15 is a decision, not an omission. A8/D8 deliberately fixed the contract at a boolean so `billing` gains no view of identity data; a role would be exactly the leak that Javadoc forbids, and it would put an `auth` concept inside a `billing` branch. The route is already behind `hasRole("ADMIN")`, and granting a trial to an instructor produces a harmless unused row, not a security or data-integrity failure. YAGNI until the owner asks for a policy. The spec accordingly says "target **user**", never "target student", for the grant's subject. |
| A17 | Type/payment/grant invariants | Enforced **in the canonical constructor**, not in the factories: `type == PAID ⇒ paymentId != null && trialGrant == null`; `type == TRIAL ⇒ paymentId == null && trialGrant != null`. Violation → `IllegalArgumentException` | Enforcing only in `pendingCheckout(...)`/`trial(...)`; enforcing nowhere and relying on tests | A1 removes the `requireNonNull` that made "a subscription always has a payment" structurally true, so the pairing must be re-stated somewhere or it is simply gone. The constructor is the **only** choke point every path funnels through — both factories, every transition via `copy(...)`, and `SubscriptionJpaMapper.toDomain`. Factory-only checks would let a future `copy(...)` (or a careless `withCheckout`/`assigned` edit) mint a `TRIAL` with a `paymentId`, which is exactly the drift the invariant exists to stop, and it would leave the public 16-arg constructor — already used directly by tests and the mapper — unguarded. **Rehydration is safe by construction**: `payment_id` was `NOT NULL` for every row written before V18, and V18 back-fills `type = 'PAID'` with all four grant columns `NULL`, so no existing row can violate the rule; the trial path is the first writer of a `NULL` `payment_id` and it always writes `type = 'TRIAL'` with a grant. The residual failure mode — a row edited out of band into an inconsistent pair — fails **loudly at load** instead of silently propagating a half-typed aggregate, which is the same posture as the `requireNonNull` guards already in this constructor. |

## Data Flow

    POST /admin/.../trial ──→ SubscriptionAdminController ──→ AssignTrialSubscriptionUseCase (@Transactional)
                                                                   │
                                    userExistencePort.existsById ──┤ 404 USER_NOT_FOUND   ← D8
                                    planRepository.findActiveById ─┤ 422 PLAN_NOT_AVAILABLE
                                    findCurrentByUserId ───────────┤ 409 SUBSCRIPTION_ALREADY_ACTIVE
                                                                   ↓
                     Subscription.trial(...) ACTIVE + ASSIGNED + courseIds snapshot
                                                                   ↓
                     saveNewSubscription  ──→ INSERT billing_subscriptions (slot claim, flush)
                              (A12)       └─→ INSERT billing_subscription_courses (same tx)  ──→ 201

    billing ──→ com.menta.shared.auth.UserExistencePort ←── auth (UserExistenceAdapter → UserRepository → JPA)
       (no  com.menta.auth.*  import exists, or can exist, under api/billing/)

    @Scheduled tick()  ── no transaction, batch of ids, try/catch per row ──┐   (A6)
                                                                           ↓
       SubscriptionExpiryWorker.expireOne(id)   @Transactional(REQUIRES_NEW)
             findById(id) ──→ expire(now) ──→ save ──→ EXPIRED, slot freed, version++

## File Changes

| File | Action | Description |
|---|---|---|
| `billing/domain/model/SubscriptionType.java` | Create | `PAID`, `TRIAL`. Plain enum, `FulfillmentStatus` shape. |
| `billing/domain/model/TrialGrant.java` | Create | `record TrialGrant(Instant at, UUID by, String reason, int days)`; `requireNonNull` on `at`/`by`, non-blank `reason`, `days > 0`. |
| `billing/domain/model/Subscription.java` | Modify | 16-arg constructor + 17-arg rehydration constructor (`version`, A14), both enforcing the **A17 type/payment/grant invariants**; `trial(...)`; `expire(Instant)` with both A13 guards; `Optional<PaymentId> getPaymentId()`; `getType()`, `getTrialGrant()`, `getVersion()`; `copy(...)` carries type, grant and version. |
| `billing/application/port/out/SubscriptionRepository.java` | Modify | **Two** new methods: `Subscription saveNewSubscription(Subscription)` (A12) and `List<UUID> findExpirableIds(Instant now, int limit)` (A2). |
| `billing/infrastructure/persistence/adapter/SubscriptionRepositoryAdapter.java` | Modify | `saveNewSubscription` = `saveNewCheckout`'s slot claim + `replaceCourses(subscription)` (`MANDATORY`); `findExpirableIds` (`REQUIRED`, `readOnly`). |
| `billing/application/port/in/AssignTrialSubscriptionUseCase` + `usecase/…Impl` | Create | A4/A5; injects `UserExistencePort`; writes via `saveNewSubscription`. |
| `billing/application/dto/AssignTrialCommand.java`, `TrialAssignmentResult.java` | Create | D3; result carries `type`, matching the spec's vocabulary. |
| `billing/infrastructure/transaction/TransactionalAssignTrialSubscriptionUseCase.java` | Create | Mirrors the cancellation decorator. |
| `billing/infrastructure/scheduling/SubscriptionExpiryReconciler.java` | Create | New package, sibling of `webhook/`. `@Scheduled` `tick()`, **no** `@Transactional`, per-row `try/catch` + `log.warn`. |
| `billing/infrastructure/scheduling/SubscriptionExpiryWorker.java` | **Create** | **A6** — separate `@Component`; `@Transactional(REQUIRES_NEW) expireOne(UUID)`. The self-invocation fix, not an optional refactor. |
| `billing/infrastructure/web/controller/SubscriptionAdminController.java` | Modify | `POST /trial` → 201. |
| `billing/infrastructure/web/dto/AssignTrialRequest/Response.java` | Create | `@NotBlank userId/planId/reason`, `@Positive days`; response exposes `type`. |
| `billing/infrastructure/persistence/entity/SubscriptionJpaEntity.java` | Modify | 5 type/grant columns + `@Version private long version` (A14) + getter; `payment_id` nullable. |
| `billing/infrastructure/persistence/mapper/SubscriptionJpaMapper.java` | Modify | `type`, `toTrialGrant` null-guarded on `granted_at`, null-guarded `PaymentId`, and **`version` in both directions** (A14). |
| `billing/infrastructure/persistence/repository/SubscriptionJpaRepository.java` | Modify | Id-projection expiry query (A2). |
| `billing/infrastructure/config/BillingConfiguration.java` | Modify | **One** new `@Bean`: the trial use case with its injected `UserExistencePort`. **No `@Bean` for the reconciler or the worker** — both are `@Component`-scanned (A6b). |
| `api/app/src/main/resources/db/migration/V18__billing_subscription_trial.sql` | Create | `MODIFY payment_id … NULL`, `type`, 4 grant columns, `version BIGINT NOT NULL DEFAULT 0`, sweep index. **Every Flyway script in this repo lives under `api/app/`, never under a feature module** (verified: `V14`, `V15`, `V17`). Last existing is **V17** (confirmed). |
| `shared/src/main/java/com/menta/shared/auth/UserExistencePort.java` | Create | **D8** — new package; single `boolean existsById(UUID)`; Javadoc states the boolean-only rule (A15). |
| `auth/domain/repository/UserRepository.java` | Modify | **D8** — `boolean existsById(UserId id);` beside today's `existsByEmail(Email)`. |
| `auth/infrastructure/persistence/adapter/UserRepositoryAdapter.java` | Modify | **D8** — `return jpaRepository.existsById(id.getValue());` (A9). |
| `auth/infrastructure/persistence/adapter/UserExistenceAdapter.java` | Create | **D8** — `@Component`, implements `UserExistencePort`. |
| `billing/domain/exception/UserNotFoundException.java` | Create | **D8** — extends `BusinessException`, code `USER_NOT_FOUND`. |
| `billing/infrastructure/web/controller/SubscriptionExceptionHandler.java` | Modify | **D8** — `UserNotFoundException` → `404`; **A14** — `ObjectOptimisticLockingFailureException` → `409 SUBSCRIPTION_CONFLICT`. |
| `api/openapi/billing-v1.yaml` | Modify | `/trial` route contract incl. the D8 `404`, **plus a new `409 SUBSCRIPTION_CONFLICT` on every pre-existing `@SubscriptionEndpoint` route** — `POST /subscriptions`, `DELETE /subscriptions/me`, `DELETE /admin/billing/subscriptions/{subscriptionId}` (A14 contract widening, see below). |
| `bruno/API - Direct/billing/Assign Trial Subscription.bru` | Create | Manual collection entry for the new route. |
| `bruno/API - Direct/billing/Cancel Own Subscription.bru`, `Cancel Subscription (Admin).bru`, `Create Subscription Checkout.bru` | Modify | Docs block only: record the newly reachable `409 SUBSCRIPTION_CONFLICT` (A14). No request change. |
| `*/build.gradle.kts` | **Unchanged** | Verified: `billing`, `auth` and `app` already depend on `:api:shared`; `billing` does not depend on `:api:auth`. |

## Interfaces / Contracts

```java
// api/shared — the whole contract. Widening this is the failure mode A8/A15 guard against.
public interface UserExistencePort {
    /** Existence only: never a User, an email, a role, or a status. */
    boolean existsById(UUID userId);
}

// api/billing — validation order is asserted, not incidental (A5).
public TrialAssignmentResult assign(AssignTrialCommand command) {
    requireAdmin(command);                                                                     // 404-shaped, A4
    if (!userExistencePort.existsById(command.userId())) throw new UserNotFoundException();     // 404
    Plan plan = planRepository.findActiveById(...).orElseThrow(PlanNotAvailableException::new); // 422
    subscriptionRepository.findCurrentByUserId(command.userId())
        .ifPresent(current -> { throw new SubscriptionAlreadyActiveException(...); });          // 409

    Instant now = clock.now();
    // A12: slot claim AND course snapshot in one transaction. saveNewCheckout would silently
    // persist zero courses, because it maps with List.of() and never calls replaceCourses.
    TrialGrant grant = new TrialGrant(now, command.actingUserId(), command.reason(), command.days());
    return TrialAssignmentResult.from(subscriptionRepository.saveNewSubscription(Subscription.trial(
        UUID.randomUUID(), command.userId(), plan.getId(), now, plan.courseIds(), grant
    )));
}

/**
 * ACTIVE → EXPIRED. endDate is never touched.
 * Non-ACTIVE returns {@code this} (idempotent — a concurrent cancellation is legitimate, A13).
 * @throws IllegalStateException if endDate has not passed — the caller's eligibility rule is broken (A13)
 */
public Subscription expire(Instant at);   // guard: !endDate.isAfter(at)

// api/billing — the sweep (A6). Registration is @Component only: NO @Bean anywhere (A6b).
// A16: @ConditionalOnProperty sits on the CLASS. Spring evaluates that condition on a
// @Configuration/@Component class or on a @Bean method — on a plain method it is inert,
// so annotating tick() would leave the job running unconditionally.
@Component
@ConditionalOnProperty(name = "billing.subscription.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionExpiryReconciler {

@Scheduled(fixedRateString = "${billing.subscription.expiry.rate-ms:60000}")
public void tick() {                                // deliberately NOT @Transactional
    for (UUID id : subscriptionRepository.findExpirableIds(clock.now(), batchSize)) {
        try {
            worker.expireOne(id);                       // separate bean — self-invocation would bypass the proxy
        } catch (RuntimeException failed) {             // one bad row never aborts the batch
            log.warn("Subscription expiry failed subscriptionId={} cause={}", id, failed.getMessage());
        }
    }
}

}   // end SubscriptionExpiryReconciler

@Component                                          // A6b: the only registration path, like WebhookVerificationWorker
public class SubscriptionExpiryWorker {

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void expireOne(UUID subscriptionId) {
    subscriptionRepository.findById(subscriptionId).ifPresent(current -> {   // re-read INSIDE the tx: fresh @Version,
        Subscription expired = current.expire(clock.now());                  // and findById hydrates courseIds —
        if (expired != current) {                                            // save() rewrites the snapshot from the
            subscriptionRepository.save(expired);                            // aggregate, so a courseless read would
        }                                                                    // DELETE it (A12's replaceCourses).
    });
}
```

Existence leads because step 3 queries **by** `userId`: against an unknown user it reports "no current subscription" and passes silently. Steps 2 and 3 keep the paid path's relative order (`CreateSubscriptionCheckoutUseCaseImpl`). A distinct `404` is safe here — this route is behind `hasRole("ADMIN")`, and a mistyped id is exactly what the admin needs told apart from a plan problem.

`POST /api/v1/admin/billing/subscriptions/trial` → `201 { subscriptionId, userId, planId, type, status, startDate, endDate, days }`; `400 INVALID_REQUEST`, `404 USER_NOT_FOUND`, `409 SUBSCRIPTION_ALREADY_ACTIVE`, `422 PLAN_NOT_AVAILABLE`.

## `getPaymentId()` Ripple — complete call-site audit

| Call site | Adjustment |
|---|---|
| `SubscriptionJpaMapper:47` `toEntity` | `subscription.getPaymentId().map(PaymentId::getValue).orElse(null)` |
| `SubscriptionJpaMapper:27` `toDomain` | Null-guard: `entity.getPaymentId() == null ? null : PaymentId.of(...)` |
| `SubscriptionCheckoutResult:25` `from` | `.map(Object::toString).orElse(null)` |
| `CreateSubscriptionCheckoutUseCaseImpl:139` `toResult` | `externalReferenceFor(subscription.getPaymentId().orElseThrow())` — a checkout row without a payment is a real invariant break |
| `CreateSubscriptionCheckoutUseCaseImpl:150` `externalReferenceFor` | Unchanged (takes `PaymentId`) |
| `PaymentVerificationService` | **Unchanged** — zero `subscription.getPaymentId()` calls; it uses `findByPaymentId`. |
| Tests: `SubscriptionTest:142`, `SubscriptionJpaMapperTest:36`, `SubscriptionRepositoryAdapterTest:98,103`, `CreateSubscriptionCheckoutUseCaseImplTest:277,279` | Unwrap the `Optional` |

## `@Version` Blast Radius (A14)

| Path | Exposed to `ObjectOptimisticLockingFailureException`? | Handling |
|---|---|---|
| `SubscriptionExpiryWorker.expireOne` | Yes — the sweep is one side of the race | Caught by `tick()`'s per-row `catch` (A6), logged, row skipped. Self-healing: `expire()` is idempotent and `findExpirableIds` re-evaluates next tick; if the racer cancelled the row it is no longer eligible at all. **No compensating action.** |
| `CancelSubscriptionUseCase` (self-service + admin, #130) | Yes — the other side of the race | New handler maps it to `409 SUBSCRIPTION_CONFLICT` so a knowingly-introduced race can never surface as a `500`. The window is bounded to rows already past `endDate`; a retry reads the terminal state and answers deterministically. |
| `CreateSubscriptionCheckoutUseCaseImpl` | Effectively no | Its two writes are `saveNewSubscription`/`saveNewCheckout` (insert, version `0`) and one `save` of the row it just inserted inside the same transaction. Nothing else can hold a stale copy of a row that did not exist a moment ago. Covered by the same `409` handler if it ever happens. |
| `PaymentVerificationService` / `WebhookVerificationWorker` | Yes, in principle | Already inside `REQUIRES_NEW` with retry + reconciliation-task fallback; an optimistic failure enters that existing retry path unchanged. **No new code.** |

**A14 widens the HTTP contract of endpoints that are already in production — this is not a trial-only change.**
`SubscriptionExceptionHandler` is a `@RestControllerAdvice(annotations = SubscriptionEndpoint.class)`, and
`@SubscriptionEndpoint` is carried today by **`SubscriptionController`** (`POST /api/v1/subscriptions`,
`DELETE /api/v1/subscriptions/me`) and **`SubscriptionAdminController`**
(`DELETE /api/v1/admin/billing/subscriptions/{subscriptionId}`) — both verified. Adding the
`ObjectOptimisticLockingFailureException → 409 SUBSCRIPTION_CONFLICT` mapping therefore makes `409` a newly
reachable response on the **cancellation routes delivered in #130 and on the checkout route**, not only on
`POST /trial`. Two consequences the reviewer must see: (1) the `409` is documented on all four routes in
`api/openapi/billing-v1.yaml` and in the existing Bruno requests, not just the new one; (2) the change is
strictly a `500 → 409` narrowing — without it the race A14 knowingly introduces would surface as a `500` —
so no previously successful call becomes a failure, and no existing client contract is invalidated.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit (billing domain, 100%) | `trial(...)` yields ACTIVE+ASSIGNED+TRIAL, null payment, `grantsAccess()` true, `endDate = now + grant.days()` (no separate `days` parameter exists to diverge from it), snapshot non-empty; `expire()` no-ops from every non-`ACTIVE` status; **`expire()` throws `IllegalStateException` when `endDate` is in the future, and expires at `endDate == at`** (A13); `version` survives `copy(...)`; `TrialGrant` invariants | `SubscriptionTest`, `TrialGrantTest` |
| **Unit (billing domain, A17)** | The four illegal pairs all throw `IllegalArgumentException` from the constructor: `PAID` + null `paymentId`, `PAID` + non-null `trialGrant`, `TRIAL` + non-null `paymentId`, `TRIAL` + null `trialGrant`; and both legal pairs survive a full `copy(...)` transition chain (`trial → expire`, `pendingCheckout → activate → cancel`) | `SubscriptionTest` |
| **Unit (billing infra, A12)** | **`saveNewSubscription` persists the course rows** — regression lock on the real bug: assert `SubscriptionCourseJpaRepository.saveAll` receives every `courseId`, and that a slot violation still maps to `SubscriptionAlreadyActiveException`. A twin test asserts `saveNewCheckout` still persists **no** courses, so the paid contract is not silently widened | `SubscriptionRepositoryAdapterTest` (existing Mockito class) |
| **Unit (sweep, A6)** | `tick()` delegates to the **injected worker** (mocked), once per id; a worker throwing on row 1 still processes rows 2..n; `expireOne` skips the `save` when `expire` returned the same instance | `SubscriptionExpiryReconcilerTest`, `SubscriptionExpiryWorkerTest` |
| Unit (auth adapter) | `existsById_delegates_to_the_jpa_repository` — mocked `UserJpaRepository`, both `true` and `false` | `UserRepositoryAdapterTest` (existing) |
| Unit (auth cross-module adapter) | `UserExistenceAdapter` maps `UUID → UserId` and returns the verdict unchanged | New `UserExistenceAdapterTest`, mocked `UserRepository` |
| Unit (billing application) | **AC7**: absent user → `UserNotFoundException`, `verifyNoInteractions` on plan/subscription repositories; order asserted (unknown user + inactive plan → 404, not 422); the use case calls `saveNewSubscription`, never `saveNewCheckout` | `AssignTrialSubscriptionUseCaseImplTest` |
| Unit (billing infra) | Mapper round-trip with/without payment/grant **and version preserved both ways**; controller 201; `UserNotFoundException` → 404; `ObjectOptimisticLockingFailureException` → 409 | `SubscriptionJpaMapperTest`, `SubscriptionAdminControllerTest`, `SubscriptionExceptionHandlerTest` |
| **Integration (cross-module, real)** | **No mock of `UserExistencePort`.** Seed a real user via `auth`'s `UserRepository`, assign a trial → 201; then `POST` with a random UUID → 404 and no row | Testcontainers MySQL + `@SpringBootTest`, new `api/app/src/test/.../integration/billing/SubscriptionTrialIntegrationTest.java` |
| **Integration (trial snapshot, A12)** | After a trial grant, `billing_subscription_courses` holds **exactly** the plan's course ids — the assertion the current `saveNewCheckout` reuse would have failed; `payment_id IS NULL` with zero `billing_payments` rows; TRIAL and PAID produce identical `VirtualCourseEntitlementService` assertions; second assignment → 409 | Same class / `api:app` |
| **Integration (sweep, A6)** | **Real** ACTIVE→EXPIRED against MySQL for one stale PAID and one stale TRIAL via `tick()`; non-stale and non-`ACTIVE` rows untouched; **a row that throws does not stop the batch**; the expired row keeps its course snapshot (the `save` → `replaceCourses` trap) | Testcontainers, not mock-only |
| **Integration (locking, A14)** | Load the same subscription twice, save one copy, then save the stale copy → `ObjectOptimisticLockingFailureException`; and a cancellation committed between the sweep's read and its write leaves the cancellation audit intact | Testcontainers, `api:app` |
| **Integration (V18, slice 1)** | **V18 applies cleanly to the existing schema in the same PR that introduces it**: Flyway reaches `V18`, the five type/grant columns and `version` exist, `payment_id` is nullable, `idx_billing_subscriptions_status_end_date` exists, and a row inserted before the migration survives with `type = 'PAID'`, `version = 0` and its `payment_id` intact | Testcontainers MySQL, new `api/app/src/test/.../integration/billing/SubscriptionTrialMigrationIntegrationTest.java`, following the existing `api/app/src/test/java/com/menta/app/integration/` convention |
| Wiring (config) | `BillingConfigurationTest` asserts the trial use-case bean is built with the injected `UserExistencePort` | Existing config-test convention |
| **Wiring (scan, A6b)** | The **running context** exposes **exactly one** `SubscriptionExpiryReconciler` bean and **exactly one** `SubscriptionExpiryWorker` bean — a double registration would mean two concurrent ticks; and with `billing.subscription.expiry.enabled=false` the reconciler bean is **absent** (A16's off switch is real). A plain `BillingConfigurationTest` cannot see `@Component`-scanned beans, so this assertion belongs to the `@SpringBootTest` | `SubscriptionExpirySweepIntegrationTest` (+ a `@TestPropertySource` nested/disabled variant) |

## Threat Matrix

N/A — no shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary. The one new HTTP route is covered by `SecurityConfig`'s existing `/api/v1/admin/**` → `hasRole("ADMIN")` plus the A4 in-use-case `isAdmin` guard. D8 adds no new boundary: it is an in-process Java call, and the module edge is enforced by the Gradle graph (A11).

## Migration / Rollout

V18 lives in `api/app/src/main/resources/db/migration/` — the single Flyway location in this repo — and is additive plus one relaxation (`MODIFY payment_id BINARY(16) NULL`) and one new `version BIGINT NOT NULL DEFAULT 0` (A14); no data rewrite, no back-fill — existing rows start at version `0`, which is exactly what a freshly mapped entity carries. That claim is not deferred: slice 1 ships a Testcontainers test asserting V18 applies to the real schema and leaves existing rows intact. D8 needs **no** migration and validates no existing row. **A16 — the sweep needs a real off switch.** `billing.subscription.expiry.rate-ms` only changes the *interval*; a `@Scheduled(fixedRateString)` job cannot be disabled by any value of that property, so the deploy note below would otherwise describe a control that does not exist. `SubscriptionExpiryReconciler` therefore carries `@ConditionalOnProperty(name = "billing.subscription.expiry.enabled", havingValue = "true", matchIfMissing = true)` **on the class** — the only position Spring evaluates for a scanned component, since the condition is inert on a plain method such as `tick()`: on by default (so no environment silently loses expiry), off by an explicit `false`. Rejected: injecting a boolean and returning early from `tick()` — the bean and its schedule would still exist, which is the kind of half-off state that makes an incident harder to reason about. **Deploy note:** the first tick expires every already-past-`endDate` row that has never been expired — verify that backlog's size before rollout, and set `billing.subscription.expiry.enabled=false` if it must be drained deliberately rather than in one tick.

## PR Slicing (budget 800 lines/PR)

D8 gets its own slice, placed **before** the use case: the port is additive and harmless on its own, and this way the use case is born with the check.

| # | Slice | Est. lines | Autonomous? |
|---|---|---|---|
| 1 | Domain (`SubscriptionType`, `TrialGrant`, both constructors + A17 invariants, `trial`, `expire` + A13 guards, `version`), V18 **under `api/app/src/main/resources/db/migration/`** + its Testcontainers migration test, persistence + mapper + full `getPaymentId()` ripple, `saveNewSubscription` (A12) + `findExpirableIds` (A2) on port and adapter | ~720 | Yes — compiles green, no new behavior exposed, **and its own migration is proven against a real MySQL inside this slice** rather than deferred to PR 4/5 |
| 2 | **D8 cross-module port**: `shared/auth/UserExistencePort`, `auth` `existsById`, `UserExistenceAdapter`, billing `UserNotFoundException` + handler `404`, unit tests | ~200 | Yes — additive; nothing consumes the port yet; reverts alone with no migration |
| 3 | `AssignTrialSubscriptionUseCase` + DTOs + transactional decorator + `BillingConfiguration` wiring, AC7 unit tests | ~380 | Yes — unit-tested, not yet routable |
| 4 | `POST /trial` controller + web DTOs + OpenAPI + Bruno + endpoint, snapshot and cross-module Testcontainers tests | ~460 | Yes — closes escenarios 1–4, 6 and AC7 |
| 5 | `SubscriptionExpiryReconciler` + `SubscriptionExpiryWorker` (both `@Component`, no `@Bean` — A6b) + `application.yml` defaults + `409` optimistic-lock mapping + **the OpenAPI/Bruno `409` widening on the pre-existing cancellation and checkout routes (A14)** + sweep, bean-scan and locking integration tests | ~400 | Yes — closes escenario 5 |

Chain: `1 → 2 → 3 → 4 → 5`, each targeting the previous slice's branch. Total ~2160 lines (up ~380 from the pre-correction estimate: A12's repository method, A6's worker bean, A14's version plumbing and A13's guard, A17's invariants and slice 1's migration test, plus their tests). Every slice is under the 800-line budget. Slice 1 is the largest and is deliberately not split further: `Subscription`, `SubscriptionJpaMapper` and V18 are edited once for the type, the nullable payment and the version together — splitting them would mean two migrations on the same table and a mid-chain commit where the mapper reads a column the aggregate cannot carry.

## Open Questions

- [ ] Optional follow-up (A11): add a `billing` ArchUnit rule banning `com.menta.auth..` imports as documentation of intent, even though Gradle already makes it uncompilable?
- [ ] Record D8 as an ADR-0039 amendment (bidirectional `shared` ports) so the next `billing → auth` need extends `UserExistencePort` instead of inventing a second path?
- [ ] Should the sweep's first production run be throttled given the never-expired paid backlog (proposal question 3, assumed "no")?

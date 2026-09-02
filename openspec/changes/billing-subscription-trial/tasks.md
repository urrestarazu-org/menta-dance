# Tasks: Admin-assigned Trial Subscription (US-BILLING-012, #131)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~2160 total (720 + 200 + 380 + 460 + 400), per design's own PR Slicing table |
| Governing budget (session) | 800 lines/PR (`review_budget_lines`) |
| 400-line budget risk | Low — against the 800-line budget actually configured for this session; against the skill's generic 400-line default, slices 1 (~720) and 4 (~460) would read High/Medium |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 → PR 2 → PR 3 → PR 4 → PR 5, each targeting the previous PR's branch |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main (base = `develop`; each PR targets the immediately preceding PR's branch) |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Base branch | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|---|
| 1 | Domain (`SubscriptionType`, `TrialGrant`, both ctors + A17 invariants, `trial`, `expire`+A13, `version`/A14) + V18 (`api/app/src/main/resources/db/migration/`) **+ its own migration test** + persistence/mapper/adapter (`saveNewSubscription`/A12, `findExpirableIds`/A2) + `getPaymentId()` ripple | `develop` | PR 1 | `./gradlew :api:billing:test --tests "*.SubscriptionTest" --tests "*.TrialGrantTest" --tests "*.SubscriptionRepositoryAdapterTest" --tests "*.SubscriptionJpaMapperTest" --tests "*.CreateSubscriptionCheckoutUseCaseImplTest" :api:app:test --tests "*.SubscriptionTrialMigrationIntegrationTest"` | Mockito for the domain/adapter tests **plus Testcontainers MySQL for `SubscriptionTrialMigrationIntegrationTest`** — a slice that ships a migration proves that migration itself; it is not deferred to PR 4/5 | Additive schema + unconsumed methods; revert leaves no trial rows to compensate (route not yet exposed) |
| 2 | D8 cross-module port: `shared/auth/UserExistencePort`, `auth.existsById`, `UserExistenceAdapter`, `UserNotFoundException`+404 handler | PR 1 branch | PR 2 | `./gradlew :api:auth:test --tests "*.UserRepositoryAdapterTest" --tests "*.UserExistenceAdapterTest" :api:billing:test --tests "*.SubscriptionExceptionHandlerTest"` | N/A — Mockito-only, additive port nothing consumes yet | Additive, no migration; revert alone per design "reverts alone with no migration" |
| 3 | `AssignTrialSubscriptionUseCase`+Impl, DTOs, transactional decorator, `BillingConfiguration` wiring | PR 2 branch | PR 3 | `./gradlew :api:billing:test --tests "*.AssignTrialSubscriptionUseCaseImplTest" --tests "*.BillingConfigurationTest"` | N/A — unit-tested with mocked ports, "not yet routable" per design | Beans unwired on revert; no persisted state depends on them |
| 4 | `POST /trial` controller + web DTOs + OpenAPI + Bruno + cross-module/snapshot integration tests | PR 3 branch | PR 4 | `./gradlew :api:billing:test --tests "*.SubscriptionAdminControllerTest" --tests "*.AssignTrialRequestTest" :api:app:test --tests "*.SubscriptionTrialIntegrationTest"` | Testcontainers MySQL + `@SpringBootTest` — real `POST /trial` against ephemeral DB, seeded real `auth` user | Removing the route stops new grants; existing trial rows keep working (access is status/endDate-driven, D6) |
| 5 | `SubscriptionExpiryReconciler`+`Worker` (both `@Component`, no `@Bean` — A6b), config flag (A16), `409` optimistic-lock mapping **+ its OpenAPI/Bruno widening on pre-existing routes** | PR 4 branch | PR 5 | `./gradlew :api:billing:test --tests "*.SubscriptionExpiryReconcilerTest" --tests "*.SubscriptionExpiryWorkerTest" --tests "*.SubscriptionExceptionHandlerTest" :api:app:test --tests "*.SubscriptionExpirySweepIntegrationTest" --tests "*.SubscriptionOptimisticLockingIntegrationTest"` | Testcontainers MySQL — real `tick()` against seeded stale PAID/TRIAL rows, plus a bean-scan assertion on the running context | Sweep-only revert (or `billing.subscription.expiry.enabled=false`, A16); rows stay `ACTIVE` past `endDate`, no data corruption. The `409` mapping reverts with it, restoring the previous `500` on the (still theoretical) race |

## Phase 1: Domain & Persistence Foundation (PR 1)

- [x] 1.1 RED `TrialGrantTest`: reject null `at`/`by`, blank `reason`, `days <= 0` — `TrialGrant.java` invariants [S1, S2, S3]
- [x] 1.2 GREEN `billing/domain/model/TrialGrant.java`: `record TrialGrant(Instant at, UUID by, String reason, int days)` with guards
- [x] 1.3 GREEN `billing/domain/model/SubscriptionType.java`: `PAID`, `TRIAL` enum
- [x] 1.4 RED `SubscriptionTest#trial`: `trial(...)` yields `ACTIVE`+`ASSIGNED`+`TRIAL`, null payment, `grantsAccess()` true, `endDate = now + days` where `days` is the caller-supplied value, never `Plan.durationDays`, non-empty course snapshot [S1, S5]
- [x] 1.5 RED `SubscriptionTest#expire`: no-op (returns `this`) from every non-`ACTIVE` status [S8]
- [x] 1.6 RED `SubscriptionTest#expire`: throws `IllegalStateException` when `endDate` is future; transitions to `EXPIRED` when `endDate == at`, `endDate` unchanged (A13 boundary `!endDate.isAfter(at)`) [S6, S7]
- [x] 1.7 RED `SubscriptionTest#typeInvariants` (A17): all four illegal pairs throw `IllegalArgumentException` from the canonical constructor — `PAID` with null `paymentId`, `PAID` with a non-null `trialGrant`, `TRIAL` with a non-null `paymentId`, `TRIAL` with a null `trialGrant`; and both legal pairs survive a full transition chain (`trial → expire`, `pendingCheckout → activate → cancel`), proving `copy(...)` cannot drift out of the invariant [S1]
- [x] 1.8 RED `SubscriptionTest#copy`: `version` survives `copy(...)` (A14)
- [x] 1.9 GREEN `billing/domain/model/Subscription.java`: 16-arg ctor (drop `requireNonNull` on `paymentId`) + 17-arg rehydration ctor (`version`), **both enforcing the A17 invariants in the canonical constructor** — the single choke point shared by the factories, `copy(...)` and `SubscriptionJpaMapper.toDomain`; `Optional<PaymentId> getPaymentId()`; `getType()`, `getTrialGrant()`, `getVersion()`; `trial(...)` factory; `expire(Instant)` with both A13 guards; `copy(...)` carries type/grant/version
- [x] 1.10 GREEN `getPaymentId()` ripple: unwrap `Optional` in `SubscriptionJpaMapper.toEntity`, `SubscriptionCheckoutResult.from`, `CreateSubscriptionCheckoutUseCaseImpl.toResult`; update tests `SubscriptionTest:142`, `SubscriptionJpaMapperTest:36`, `SubscriptionRepositoryAdapterTest:98,103`, `CreateSubscriptionCheckoutUseCaseImplTest:277,279`
- [x] 1.11 RED (integration) `SubscriptionTrialMigrationIntegrationTest`: Testcontainers MySQL + Flyway on the real script set — the migration ships in this slice, so this slice proves it. Assert Flyway reaches `V18` with no failure; `billing_subscriptions` exposes `type`, the four grant columns and `version`; `payment_id` is now nullable (`IS_NULLABLE = 'YES'` in `information_schema.columns`); `idx_billing_subscriptions_status_end_date` exists over `(status, end_date)`; and a subscription row seeded **before** V18 survives with `type = 'PAID'`, `version = 0`, its `payment_id` intact and no grant columns populated (A17's rehydration-safety claim). Follow the existing `api/app/src/test/java/com/menta/app/integration/` convention [S1, S6, S7]
- [x] 1.12 GREEN `api/app/src/main/resources/db/migration/V18__billing_subscription_trial.sql` (**not** under `api/billing/` — every Flyway script in this repo lives in `api/app`, verified against V14/V15/V17): `MODIFY payment_id BINARY(16) NULL`, `type NOT NULL DEFAULT 'PAID'`, 4 grant columns, `version BIGINT NOT NULL DEFAULT 0`, `idx_billing_subscriptions_status_end_date (status, end_date)`
- [x] 1.13 RED `SubscriptionJpaMapperTest`: round-trip with/without payment/grant, `version` preserved both ways [S1, S6, S7]
- [x] 1.14 GREEN `SubscriptionJpaEntity.java`: 5 type/grant columns + `@Version private long version` + getter, nullable `payment_id`; `SubscriptionJpaMapper.java`: `type`, null-guarded `toTrialGrant`/`PaymentId`, `version` both directions
- [x] 1.15 RED `SubscriptionRepositoryAdapterTest#saveNewSubscription`: persists every `courseId` via `SubscriptionCourseJpaRepository.saveAll`; slot violation still maps to `SubscriptionAlreadyActiveException` [S1] (A12 regression lock)
- [x] 1.16 RED `SubscriptionRepositoryAdapterTest#saveNewCheckout`: twin test asserting it still persists **zero** courses — guards A12, protects the unwidened paid-path contract (no spec.md scenario — regression guard)
- [x] 1.17 GREEN `SubscriptionRepository.java` (+2 methods): `saveNewSubscription(Subscription)`, `findExpirableIds(Instant now, int limit)`; `SubscriptionRepositoryAdapter.java`: `saveNewSubscription` = `saveNewCheckout`'s slot claim + `replaceCourses(subscription)` (`MANDATORY`); `findExpirableIds` (`REQUIRED`, `readOnly`)
- [x] 1.18 GREEN `SubscriptionJpaRepository.java`: id-projection expiry query (A2)

## Phase 2: D8 Cross-module User-existence Port (PR 2)

- [ ] 2.1 RED `UserRepositoryAdapterTest#existsById_delegates_to_the_jpa_repository`: true/false cases [supports S9]
- [ ] 2.2 GREEN `auth/domain/repository/UserRepository.java`: `boolean existsById(UserId id)`; `UserRepositoryAdapter.java`: `jpaRepository.existsById(id.getValue())` (A9)
- [ ] 2.3 RED `UserExistenceAdapterTest`: maps `UUID → UserId`, returns verdict unchanged, mocked `UserRepository` [supports S9]
- [ ] 2.4 GREEN `shared/src/.../shared/auth/UserExistencePort.java`: `boolean existsById(UUID userId)`, Javadoc states boolean-only rule (A15); `auth/infrastructure/persistence/adapter/UserExistenceAdapter.java` (`@Component`)
- [ ] 2.5 GREEN `billing/domain/exception/UserNotFoundException.java`: extends `BusinessException`, code `USER_NOT_FOUND`
- [ ] 2.6 RED `SubscriptionExceptionHandlerTest`: `UserNotFoundException` → `404` [S9]
- [ ] 2.7 GREEN `SubscriptionExceptionHandler.java`: map `UserNotFoundException` → `404`

## Phase 3: Assign-Trial Use Case (PR 3)

- [ ] 3.1 RED `AssignTrialSubscriptionUseCaseImplTest#unknownUser`: absent user → `UserNotFoundException`, `verifyNoInteractions` on plan/subscription repositories [S9]
- [ ] 3.2 RED `AssignTrialSubscriptionUseCaseImplTest#unknownUserBeatsInactivePlan`: unknown `userId` + inactive `planId` → `404`, not `422` [S10]
- [ ] 3.3 RED `AssignTrialSubscriptionUseCaseImplTest#planNotAvailable`: known user, inactive/missing plan → `PlanNotAvailableException` (422) [S13]
- [ ] 3.4 RED `AssignTrialSubscriptionUseCaseImplTest#slotOccupied`: known user, active plan, subscription in force (`ACTIVE`/`PENDING`, either type) → `SubscriptionAlreadyActiveException` (409) [S11]
- [ ] 3.5 RED `AssignTrialSubscriptionUseCaseImplTest#happyPath`: calls `saveNewSubscription`, never `saveNewCheckout`; the `days` used for `endDate` is the command's, asserted against a plan whose `durationDays` differs; validation order `reason`/`days`(400) → admin guard → user(404) → plan(422) → slot(409) [S1]
- [ ] 3.6 GREEN `billing/application/dto/AssignTrialCommand.java`, `TrialAssignmentResult.java` (D3, carries `type`)
- [ ] 3.7 GREEN `billing/application/port/in/AssignTrialSubscriptionUseCase` + `AssignTrialSubscriptionUseCaseImpl`: A4 admin guard, A5 order, injects `UserExistencePort`
- [ ] 3.8 GREEN `billing/infrastructure/transaction/TransactionalAssignTrialSubscriptionUseCase.java`: decorator mirroring the cancellation precedent
- [ ] 3.9 RED `BillingConfigurationTest#trialUseCaseBean`: trial use-case bean is built with the injected `UserExistencePort`
- [ ] 3.10 GREEN `BillingConfiguration.java`: new `@Bean` for `assignTrialSubscriptionUseCase`

## Phase 4: Admin Route, Web DTOs & Cross-module Verification (PR 4)

- [ ] 4.1 RED `AssignTrialRequestTest`/`SubscriptionAdminControllerTest#blankReason`: blank/absent `reason` → `400` [S2]
- [ ] 4.2 RED `SubscriptionAdminControllerTest#nonPositiveDays`: `days` absent, zero or negative → `400` (bean validation `@Positive`), and no subscription is created [S3]
- [ ] 4.3 RED `SubscriptionAdminControllerTest#created`: valid request → `201` with `TrialSubscriptionResponse` body [S1]
- [ ] 4.4 GREEN `billing/infrastructure/web/dto/AssignTrialRequest.java`/`Response.java`: `@NotBlank userId/planId/reason`, `@Positive days`, response exposes `type`
- [ ] 4.5 GREEN `SubscriptionAdminController.java`: `POST /api/v1/admin/billing/subscriptions/trial` → `201`
- [ ] 4.6 RED `SubscriptionAdminControllerTest#nonAdmin`/security test: non-`ROLE_ADMIN` caller → `403`, no subscription created [S4]
- [ ] 4.7 RED (integration) `SubscriptionTrialIntegrationTest#unknownUserId`: seed real `auth` user, `POST /trial` with random `UUID` → `404`, zero rows written; no mock of `UserExistencePort` [S9]
- [ ] 4.8 RED (integration) `SubscriptionTrialIntegrationTest#courseSnapshot`: after grant, `billing_subscription_courses` holds **exactly** the plan's `courseIds` (A12 regression the current `saveNewCheckout` reuse would fail); `payment_id IS NULL`, zero `billing_payments` rows [S1]
- [ ] 4.9 RED (integration) `SubscriptionTrialIntegrationTest#accessParity`: TRIAL and PAID subscriptions produce byte-identical `VirtualCourseEntitlementService` assertions [S5]
- [ ] 4.10 RED (integration) `SubscriptionTrialIntegrationTest#slotConflict`: second assignment to the same user → `409` [S11]
- [ ] 4.11 RED (integration) `SubscriptionTrialIntegrationTest#repurchaseAfterTrial`: student whose only subscription is `TRIAL` in `EXPIRED`/`CANCELLED` starts a paid checkout → new distinguishable `PAID` row, trial row never reactivated [S12]
- [ ] 4.12 GREEN `api/app/src/test/.../integration/billing/SubscriptionTrialIntegrationTest.java`: Testcontainers MySQL + `@SpringBootTest` implementing 4.7–4.11
- [ ] 4.13 Update `api/openapi/billing-v1.yaml` and create `bruno/API - Direct/billing/Assign Trial Subscription.bru` with the `/trial` route contract, including the `400` (blank `reason`, non-positive `days`) and the D8 `404`

## Phase 5: Automatic Expiry Sweep (PR 5)

- [ ] 5.1 RED `SubscriptionExpiryReconcilerTest#delegatesPerId`: `tick()` calls the injected `SubscriptionExpiryWorker.expireOne` once per id returned by `findExpirableIds` [S6, S7]
- [ ] 5.2 RED `SubscriptionExpiryReconcilerTest#batchSurvivesFailure`: a worker throwing on row 1 still processes rows 2..n (per-row `try/catch`, A6) [S6, S7]
- [ ] 5.3 GREEN `billing/infrastructure/scheduling/SubscriptionExpiryReconciler.java`: `@Component` **on the class** plus `@ConditionalOnProperty(name = "billing.subscription.expiry.enabled", havingValue = "true", matchIfMissing = true)` **on the class, not on `tick()`** — Spring evaluates that condition only on a component/configuration class or a `@Bean` method, so on a plain method it is inert and the job would run unconditionally (A16); `@Scheduled(fixedRateString)` on `tick()`, **no** `@Transactional`, per-row `try/catch` + `log.warn`
- [ ] 5.4 RED `SubscriptionExpiryWorkerTest#skipsSaveOnNoop`: `expireOne` does not call `save` when `expire()` returns the same instance [S8]
- [ ] 5.5 GREEN `billing/infrastructure/scheduling/SubscriptionExpiryWorker.java`: separate class registered by `@Component` **only**, `@Transactional(propagation = REQUIRES_NEW) expireOne(UUID)` (A6/A6b)
- [ ] 5.6 RED `SubscriptionExceptionHandlerTest#optimisticLock`: `ObjectOptimisticLockingFailureException` → `409 SUBSCRIPTION_CONFLICT` (A14)
- [ ] 5.7 GREEN `SubscriptionExceptionHandler.java`: map `ObjectOptimisticLockingFailureException` → `409`
- [ ] 5.8 RED (integration) `SubscriptionExpirySweepIntegrationTest#sweepBeansAreScannedOnce` (A6b): the **running** context exposes exactly one `SubscriptionExpiryReconciler` bean and exactly one `SubscriptionExpiryWorker` bean — a duplicate registration would mean two concurrent `tick()`s over the same batch. A `@TestPropertySource(billing.subscription.expiry.enabled=false)` variant asserts the reconciler bean is **absent**, proving A16's off switch is real. This lives in the `@SpringBootTest`, not in `BillingConfigurationTest`, because a plain unit test of the config class cannot observe `@Component`-scanned beans
- [ ] 5.9 GREEN `application.yml`: defaults for `billing.subscription.expiry.enabled`, `rate-ms` and the batch size. **No `@Bean` is added to `BillingConfiguration` for the reconciler or the worker** — `@Component` is their single registration path, matching `WebhookInboxReconciler`/`WebhookVerificationWorker`, which appear nowhere in that class (verified)
- [ ] 5.10 RED (integration) `SubscriptionExpirySweepIntegrationTest#stalePaidAndTrial`: real `ACTIVE → EXPIRED` for one stale `PAID` and one stale `TRIAL` via `tick()` against Testcontainers MySQL [S6, S7]
- [ ] 5.11 RED (integration) `SubscriptionExpirySweepIntegrationTest#untouched`: non-stale and non-`ACTIVE` (`CANCELLED`/already-`EXPIRED`) rows keep `status`/`endDate` unchanged [S8]
- [ ] 5.12 RED (integration) `SubscriptionExpirySweepIntegrationTest#batchResilience`: a row that throws does not stop the batch; expired row keeps its course snapshot (`save` → `replaceCourses` trap)
- [ ] 5.13 GREEN `api/app/src/test/.../integration/billing/SubscriptionExpirySweepIntegrationTest.java` implementing 5.8, 5.10–5.12
- [ ] 5.14 RED (integration) `SubscriptionOptimisticLockingIntegrationTest`: load same subscription twice, save one copy, save the stale copy → `ObjectOptimisticLockingFailureException`; a cancellation committed between the sweep's read/write leaves the cancellation audit intact (A14)
- [ ] 5.15 GREEN `api/app/src/test/.../integration/billing/SubscriptionOptimisticLockingIntegrationTest.java` implementing 5.14
- [ ] 5.16 GREEN **A14 contract widening on already-delivered endpoints** — `SubscriptionExceptionHandler` is a `@RestControllerAdvice(annotations = SubscriptionEndpoint.class)`, and `@SubscriptionEndpoint` is carried by `SubscriptionController` and `SubscriptionAdminController` (verified), so the new `409 SUBSCRIPTION_CONFLICT` becomes a reachable response on routes shipped before this change. Document it in `api/openapi/billing-v1.yaml` for `POST /api/v1/subscriptions`, `DELETE /api/v1/subscriptions/me` and `DELETE /api/v1/admin/billing/subscriptions/{subscriptionId}` (the #130 cancellation routes) as well as `POST .../trial`, and note it in the docs block of `bruno/API - Direct/billing/Cancel Own Subscription.bru`, `Cancel Subscription (Admin).bru` and `Create Subscription Checkout.bru`. Call it out in the PR description: this is an observable contract change to already-merged functionality, not a trial-only addition

## Scenario → Task Coverage

| # | Scenario (spec.md) | Covering task(s) |
|---|---|---|
| S1 | Admin grants a trial subscription | 1.1, 1.4, 1.7, 1.10, 1.11, 1.13, 1.15, 3.5, 4.3, 4.8 |
| S2 | Missing reason is rejected | 1.1, 4.1 |
| S3 | A non-positive or absent days value is rejected | 1.1, 4.2 |
| S4 | Non-admin cannot grant a trial to anyone, including self | 4.6 |
| S5 | Trial and paid produce identical access decisions | 1.4, 4.9 |
| S6 | A stale trial expires automatically | 1.6, 1.11, 1.13, 5.1, 5.2, 5.10 |
| S7 | A stale paid subscription expires automatically | 1.6, 1.11, 1.13, 5.1, 5.2, 5.10 |
| S8 | Non-active subscriptions are left untouched | 1.5, 5.4, 5.11 |
| S9 | Unknown userId is rejected before any other check | 2.1, 2.3, 2.6, 3.1, 4.7 |
| S10 | Unknown user takes precedence over an inactive plan | 3.2 |
| S11 | Target already has an active subscription of either type | 3.4, 4.10 |
| S12 | Paid checkout succeeds after trial expiry or cancellation | 4.11 |
| S13 | Plan does not exist or is inactive | 3.3 |

All **13** scenarios in `specs/billing-subscriptions/spec.md` (7 requirements) are covered; **none is orphaned, and no task is orphaned either**. The previously flagged gap is closed: `days <= 0 → 400` (task 4.2) now traces to the normative scenario S3, added to the spec in this round instead of being carried as a known gap. Scenario ids were renumbered when S3 was inserted — the ids below follow document order in `spec.md`, so S3–S13 shift by one from the earlier revision of this file.

Tasks that intentionally trace to no scenario, and why:

| Task | Justification |
|---|---|
| 1.16 | Regression guard on `saveNewCheckout`'s **unchanged** contract (A12). It protects existing behaviour the spec never restates. |
| 5.8 | Wiring invariant (A6b/A16): exactly one bean per sweep class, and a real off switch. Structural, not behavioural. |
| 5.12 | Batch resilience (A6). An operational property of the sweep, not a user-visible outcome. |
| 5.14, 5.15 | Concurrency safety (A14). The spec describes outcomes; the lost-update guard is the mechanism that keeps them true. |
| 5.16 | Contract documentation for the `409` A14 makes reachable on pre-existing routes. |

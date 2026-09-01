# Tasks: Admin-assigned Trial Subscription (US-BILLING-012, #131)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~2020 total (620 + 200 + 380 + 460 + 360), per design's own PR Slicing table |
| Governing budget (session) | 800 lines/PR (`review_budget_lines`) |
| 400-line budget risk | Low — against the 800-line budget actually configured for this session; against the skill's generic 400-line default, slices 1 (~620) and 4 (~460) would read High/Medium |
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
| 1 | Domain (`SubscriptionType`, `TrialGrant`, both ctors, `trial`, `expire`+A13, `version`/A14) + V18 + persistence/mapper/adapter (`saveNewSubscription`/A12, `findExpirableIds`/A2) + `getPaymentId()` ripple | `develop` | PR 1 | `./gradlew :api:billing:test --tests "*.SubscriptionTest" --tests "*.TrialGrantTest" --tests "*.SubscriptionRepositoryAdapterTest" --tests "*.SubscriptionJpaMapperTest" --tests "*.CreateSubscriptionCheckoutUseCaseImplTest"` | N/A — Mockito-only per design's Testing Strategy; V18 applies cleanly since it only relaxes/adds columns, verified live by PR 4/5 Testcontainers | Additive schema + unconsumed methods; revert leaves no trial rows to compensate (route not yet exposed) |
| 2 | D8 cross-module port: `shared/auth/UserExistencePort`, `auth.existsById`, `UserExistenceAdapter`, `UserNotFoundException`+404 handler | PR 1 branch | PR 2 | `./gradlew :api:auth:test --tests "*.UserRepositoryAdapterTest" --tests "*.UserExistenceAdapterTest" :api:billing:test --tests "*.SubscriptionExceptionHandlerTest"` | N/A — Mockito-only, additive port nothing consumes yet | Additive, no migration; revert alone per design "reverts alone with no migration" |
| 3 | `AssignTrialSubscriptionUseCase`+Impl, DTOs, transactional decorator, `BillingConfiguration` wiring | PR 2 branch | PR 3 | `./gradlew :api:billing:test --tests "*.AssignTrialSubscriptionUseCaseImplTest" --tests "*.BillingConfigurationTest"` | N/A — unit-tested with mocked ports, "not yet routable" per design | Beans unwired on revert; no persisted state depends on them |
| 4 | `POST /trial` controller + web DTOs + OpenAPI + Bruno + cross-module/snapshot integration tests | PR 3 branch | PR 4 | `./gradlew :api:billing:test --tests "*.SubscriptionAdminControllerTest" --tests "*.AssignTrialRequestTest" :api:app:test --tests "*.SubscriptionTrialIntegrationTest"` | Testcontainers MySQL + `@SpringBootTest` — real `POST /trial` against ephemeral DB, seeded real `auth` user | Removing the route stops new grants; existing trial rows keep working (access is status/endDate-driven, D6) |
| 5 | `SubscriptionExpiryReconciler`+`Worker`, config flag (A16), `409` optimistic-lock mapping | PR 4 branch | PR 5 | `./gradlew :api:billing:test --tests "*.SubscriptionExpiryReconcilerTest" --tests "*.SubscriptionExpiryWorkerTest" :api:app:test --tests "*.SubscriptionExpirySweepIntegrationTest" --tests "*.SubscriptionOptimisticLockingIntegrationTest"` | Testcontainers MySQL — real `tick()` against seeded stale PAID/TRIAL rows | Sweep-only revert (or `billing.subscription.expiry.enabled=false`, A16); rows stay `ACTIVE` past `endDate`, no data corruption |

## Phase 1: Domain & Persistence Foundation (PR 1)

- [ ] 1.1 RED `TrialGrantTest`: reject null `at`/`by`, blank `reason`, `days <= 0` — `TrialGrant.java` invariants [supports S1/S2]
- [ ] 1.2 GREEN `billing/domain/model/TrialGrant.java`: `record TrialGrant(Instant at, UUID by, String reason, int days)` with guards
- [ ] 1.3 GREEN `billing/domain/model/SubscriptionType.java`: `PAID`, `TRIAL` enum
- [ ] 1.4 RED `SubscriptionTest#trial`: `trial(...)` yields `ACTIVE`+`ASSIGNED`+`TRIAL`, null payment, `grantsAccess()` true, `endDate = now + days`, non-empty course snapshot [S1, S4]
- [ ] 1.5 RED `SubscriptionTest#expire`: no-op (returns `this`) from every non-`ACTIVE` status [S7]
- [ ] 1.6 RED `SubscriptionTest#expire`: throws `IllegalStateException` when `endDate` is future; transitions to `EXPIRED` when `endDate == at`, `endDate` unchanged (A13 boundary `!endDate.isAfter(at)`) [S5, S6]
- [ ] 1.7 RED `SubscriptionTest#copy`: `version` survives `copy(...)` (A14)
- [ ] 1.8 GREEN `billing/domain/model/Subscription.java`: 16-arg ctor (drop `requireNonNull` on `paymentId`) + 17-arg rehydration ctor (`version`); `Optional<PaymentId> getPaymentId()`; `getType()`, `getTrialGrant()`, `getVersion()`; `trial(...)` factory; `expire(Instant)` with both A13 guards; `copy(...)` carries type/grant/version
- [ ] 1.9 GREEN `getPaymentId()` ripple: unwrap `Optional` in `SubscriptionJpaMapper.toEntity`, `SubscriptionCheckoutResult.from`, `CreateSubscriptionCheckoutUseCaseImpl.toResult`; update tests `SubscriptionTest:142`, `SubscriptionJpaMapperTest:36`, `SubscriptionRepositoryAdapterTest:98,103`, `CreateSubscriptionCheckoutUseCaseImplTest:277,279`
- [ ] 1.10 GREEN `billing/db/migration/V18__billing_subscription_trial.sql`: `MODIFY payment_id BINARY(16) NULL`, `type NOT NULL DEFAULT 'PAID'`, 4 grant columns, `version BIGINT NOT NULL DEFAULT 0`, `idx_billing_subscriptions_status_end_date (status, end_date)`
- [ ] 1.11 RED `SubscriptionJpaMapperTest`: round-trip with/without payment/grant, `version` preserved both ways [S1, S5, S6]
- [ ] 1.12 GREEN `SubscriptionJpaEntity.java`: 5 type/grant columns + `@Version private long version` + getter, nullable `payment_id`; `SubscriptionJpaMapper.java`: `type`, null-guarded `toTrialGrant`/`PaymentId`, `version` both directions
- [ ] 1.13 RED `SubscriptionRepositoryAdapterTest#saveNewSubscription`: persists every `courseId` via `SubscriptionCourseJpaRepository.saveAll`; slot violation still maps to `SubscriptionAlreadyActiveException` [S1] (A12 regression lock)
- [ ] 1.14 RED `SubscriptionRepositoryAdapterTest#saveNewCheckout`: twin test asserting it still persists **zero** courses — guards A12, protects the unwidened paid-path contract (no spec.md scenario — regression guard)
- [ ] 1.15 GREEN `SubscriptionRepository.java` (+2 methods): `saveNewSubscription(Subscription)`, `findExpirableIds(Instant now, int limit)`; `SubscriptionRepositoryAdapter.java`: `saveNewSubscription` = `saveNewCheckout`'s slot claim + `replaceCourses(subscription)` (`MANDATORY`); `findExpirableIds` (`REQUIRED`, `readOnly`)
- [ ] 1.16 GREEN `SubscriptionJpaRepository.java`: id-projection expiry query (A2)

## Phase 2: D8 Cross-module User-existence Port (PR 2)

- [ ] 2.1 RED `UserRepositoryAdapterTest#existsById_delegates_to_the_jpa_repository`: true/false cases [supports S8]
- [ ] 2.2 GREEN `auth/domain/repository/UserRepository.java`: `boolean existsById(UserId id)`; `UserRepositoryAdapter.java`: `jpaRepository.existsById(id.getValue())` (A9)
- [ ] 2.3 RED `UserExistenceAdapterTest`: maps `UUID → UserId`, returns verdict unchanged, mocked `UserRepository` [supports S8]
- [ ] 2.4 GREEN `shared/src/.../shared/auth/UserExistencePort.java`: `boolean existsById(UUID userId)`, Javadoc states boolean-only rule (A15); `auth/infrastructure/persistence/adapter/UserExistenceAdapter.java` (`@Component`)
- [ ] 2.5 GREEN `billing/domain/exception/UserNotFoundException.java`: extends `BusinessException`, code `USER_NOT_FOUND`
- [ ] 2.6 RED `SubscriptionExceptionHandlerTest`: `UserNotFoundException` → `404` [S8]
- [ ] 2.7 GREEN `SubscriptionExceptionHandler.java`: map `UserNotFoundException` → `404`

## Phase 3: Assign-Trial Use Case (PR 3)

- [ ] 3.1 RED `AssignTrialSubscriptionUseCaseImplTest#unknownUser`: absent user → `UserNotFoundException`, `verifyNoInteractions` on plan/subscription repositories [S8]
- [ ] 3.2 RED `AssignTrialSubscriptionUseCaseImplTest#unknownUserBeatsInactivePlan`: unknown `userId` + inactive `planId` → `404`, not `422` [S9]
- [ ] 3.3 RED `AssignTrialSubscriptionUseCaseImplTest#planNotAvailable`: known user, inactive/missing plan → `PlanNotAvailableException` (422) [S12]
- [ ] 3.4 RED `AssignTrialSubscriptionUseCaseImplTest#slotOccupied`: known user, active plan, subscription in force (`ACTIVE`/`PENDING`, either type) → `SubscriptionAlreadyActiveException` (409) [S10]
- [ ] 3.5 RED `AssignTrialSubscriptionUseCaseImplTest#happyPath`: calls `saveNewSubscription`, never `saveNewCheckout`; validation order `reason`/`days`(400) → admin guard → user(404) → plan(422) → slot(409) [S1]
- [ ] 3.6 GREEN `billing/application/dto/AssignTrialCommand.java`, `TrialAssignmentResult.java` (D3, carries `type`)
- [ ] 3.7 GREEN `billing/application/port/in/AssignTrialSubscriptionUseCase` + `AssignTrialSubscriptionUseCaseImpl`: A4 admin guard, A5 order, injects `UserExistencePort`
- [ ] 3.8 GREEN `billing/infrastructure/transaction/TransactionalAssignTrialSubscriptionUseCase.java`: decorator mirroring the cancellation precedent
- [ ] 3.9 RED `BillingConfigurationTest#trialUseCaseBean`: trial use-case bean is built with the injected `UserExistencePort`
- [ ] 3.10 GREEN `BillingConfiguration.java`: new `@Bean` for `assignTrialSubscriptionUseCase`

## Phase 4: Admin Route, Web DTOs & Cross-module Verification (PR 4)

- [ ] 4.1 RED `AssignTrialRequestTest`/`SubscriptionAdminControllerTest#blankReason`: blank/absent `reason` → `400` [S2]
- [ ] 4.2 RED `SubscriptionAdminControllerTest#nonPositiveDays`: `days <= 0` → `400` (bean validation `@Positive`) — proposal Success Criteria, no dedicated spec.md scenario; document as gap
- [ ] 4.3 RED `SubscriptionAdminControllerTest#created`: valid request → `201` with `TrialSubscriptionResponse` body [S1]
- [ ] 4.4 GREEN `billing/infrastructure/web/dto/AssignTrialRequest.java`/`Response.java`: `@NotBlank userId/planId/reason`, `@Positive days`, response exposes `type`
- [ ] 4.5 GREEN `SubscriptionAdminController.java`: `POST /api/v1/admin/billing/subscriptions/trial` → `201`
- [ ] 4.6 RED `SubscriptionAdminControllerTest#nonAdmin`/security test: non-`ROLE_ADMIN` caller → `403`, no subscription created [S3]
- [ ] 4.7 RED (integration) `SubscriptionTrialIntegrationTest#unknownUserId`: seed real `auth` user, `POST /trial` with random `UUID` → `404`, zero rows written; no mock of `UserExistencePort` [S8]
- [ ] 4.8 RED (integration) `SubscriptionTrialIntegrationTest#courseSnapshot`: after grant, `billing_subscription_courses` holds **exactly** the plan's `courseIds` (A12 regression the current `saveNewCheckout` reuse would fail); `payment_id IS NULL`, zero `billing_payments` rows [S1]
- [ ] 4.9 RED (integration) `SubscriptionTrialIntegrationTest#accessParity`: TRIAL and PAID subscriptions produce byte-identical `VirtualCourseEntitlementService` assertions [S4]
- [ ] 4.10 RED (integration) `SubscriptionTrialIntegrationTest#slotConflict`: second assignment to the same user → `409` [S10]
- [ ] 4.11 RED (integration) `SubscriptionTrialIntegrationTest#repurchaseAfterTrial`: student whose only subscription is `TRIAL` in `EXPIRED`/`CANCELLED` starts a paid checkout → new distinguishable `PAID` row, trial row never reactivated [S11]
- [ ] 4.12 GREEN `api/app/src/test/.../integration/billing/SubscriptionTrialIntegrationTest.java`: Testcontainers MySQL + `@SpringBootTest` implementing 4.7–4.11
- [ ] 4.13 Update `api/openapi/billing-v1.yaml` and `bruno/API - Direct/billing/Assign Trial Subscription.bru` with the route contract, including the `404`

## Phase 5: Automatic Expiry Sweep (PR 5)

- [ ] 5.1 RED `SubscriptionExpiryReconcilerTest#delegatesPerId`: `tick()` calls the injected `SubscriptionExpiryWorker.expireOne` once per id returned by `findExpirableIds` [S5, S6]
- [ ] 5.2 RED `SubscriptionExpiryReconcilerTest#batchSurvivesFailure`: a worker throwing on row 1 still processes rows 2..n (per-row `try/catch`, A6) [S5, S6]
- [ ] 5.3 GREEN `billing/infrastructure/scheduling/SubscriptionExpiryReconciler.java`: `@ConditionalOnProperty(billing.subscription.expiry.enabled, matchIfMissing=true)` (A16), `@Scheduled(fixedRateString)`, **no** `@Transactional`, per-row `try/catch` + `log.warn`
- [ ] 5.4 RED `SubscriptionExpiryWorkerTest#skipsSaveOnNoop`: `expireOne` does not call `save` when `expire()` returns the same instance [S7]
- [ ] 5.5 GREEN `billing/infrastructure/scheduling/SubscriptionExpiryWorker.java`: separate `@Component`, `@Transactional(REQUIRES_NEW) expireOne(UUID)`
- [ ] 5.6 RED `SubscriptionExceptionHandlerTest#optimisticLock`: `ObjectOptimisticLockingFailureException` → `409 SUBSCRIPTION_CONFLICT` (A14)
- [ ] 5.7 GREEN `SubscriptionExceptionHandler.java`: map `ObjectOptimisticLockingFailureException` → `409`
- [ ] 5.8 RED `BillingConfigurationTest#sweepBeans`: reconciler and worker are two distinct beans
- [ ] 5.9 GREEN `BillingConfiguration.java`: `@Bean`s for reconciler + worker; `application.yml` property defaults
- [ ] 5.10 RED (integration) `SubscriptionExpirySweepIntegrationTest#stalePaidAndTrial`: real `ACTIVE → EXPIRED` for one stale `PAID` and one stale `TRIAL` via `tick()` against Testcontainers MySQL [S5, S6]
- [ ] 5.11 RED (integration) `SubscriptionExpirySweepIntegrationTest#untouched`: non-stale and non-`ACTIVE` (`CANCELLED`/already-`EXPIRED`) rows keep `status`/`endDate` unchanged [S7]
- [ ] 5.12 RED (integration) `SubscriptionExpirySweepIntegrationTest#batchResilience`: a row that throws does not stop the batch; expired row keeps its course snapshot (`save` → `replaceCourses` trap)
- [ ] 5.13 GREEN `api/app/src/test/.../integration/billing/SubscriptionExpirySweepIntegrationTest.java` implementing 5.10–5.12
- [ ] 5.14 RED (integration) `SubscriptionOptimisticLockingIntegrationTest`: load same subscription twice, save one copy, save the stale copy → `ObjectOptimisticLockingFailureException`; a cancellation committed between the sweep's read/write leaves the cancellation audit intact (A14)
- [ ] 5.15 GREEN `api/app/src/test/.../integration/billing/SubscriptionOptimisticLockingIntegrationTest.java` implementing 5.14

## Scenario → Task Coverage

| # | Scenario (spec.md) | Covering task(s) |
|---|---|---|
| S1 | Admin grants a trial subscription | 1.4, 1.9, 1.13, 3.5, 4.3, 4.8 |
| S2 | Missing reason is rejected | 1.1, 4.1 |
| S3 | Non-admin cannot grant a trial to anyone, including self | 4.6 |
| S4 | Trial and paid produce identical access decisions | 1.4, 4.9 |
| S5 | A stale trial expires automatically | 1.6, 5.1, 5.2, 5.10 |
| S6 | A stale paid subscription expires automatically | 1.6, 5.1, 5.2, 5.10 |
| S7 | Non-active subscriptions are left untouched | 1.5, 5.4, 5.11 |
| S8 | Unknown userId is rejected before any other check | 2.1, 2.3, 2.6, 3.1, 4.7 |
| S9 | Unknown user takes precedence over an inactive plan | 3.2 |
| S10 | Target already has an active subscription of either type | 3.4, 4.10 |
| S11 | Paid checkout succeeds after trial expiry or cancellation | 4.11 |
| S12 | Plan does not exist or is inactive | 3.3 |

All 12 scenarios in `specs/billing-subscriptions/spec.md` are covered; none is orphaned. **Note**: the user's brief referenced "15 escenarios" — the spec file as read contains 7 requirements and exactly 12 `#### Scenario:` blocks; this table maps all 12. Task 4.2 (`days <= 0` → 400) traces to the proposal's Success Criteria, not a numbered spec.md scenario — flagged, not force-mapped.

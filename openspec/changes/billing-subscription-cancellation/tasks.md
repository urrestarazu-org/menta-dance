# Tasks: Subscription Cancellation (US-BILLING-011, Issue #130)

Scenario legend (spec `billing-subscriptions`): S1 self-service cancel · S2 no cancellable subscription (404) · S3 access persists then ends · S4 re-purchase after cancellation · S5 overlap warns · S6 no overlap → null · S7 idempotent replay keeps notice · S8 admin cancels with reason · S9 missing reason → 400 · S10 reason never reaches student · S11 non-admin → 403.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1500 total (450 / 700 / 300) |
| Session review budget | 800 lines/PR |
| 400-line budget risk | Medium (Slice 2 ~700 approaches budget) |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (domain+persistence) → PR 2 (use cases+endpoints) → PR 3 (D3 notice) |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|----|-----------------------|-----------------|--------------------|
| 1 | `Cancellation` VO, `cancel()`, migration, mapper/adapter | PR 1, base `develop` | `./gradlew :api:billing:test --tests "*.Subscription*Test"` | N/A — no HTTP surface yet | Revert PR 1; compensating `DROP COLUMN` on V17 |
| 2 | `CancelSubscriptionUseCase`, `/me` + admin controllers, A7 fix, OpenAPI, Bruno | PR 2, base PR 1 branch | `./gradlew :api:billing:test --tests "*.Cancel*" --tests "*.SecurityConfigTest"` | `api/app` `@SpringBootTest` integration (S1,S2,S8,S9,S10,S11) | Revert PR 2 only; PR 1 columns stay, endpoints disappear |
| 3 | `findLatestCancelledWithRemainingAccess`, `OverlapNotice`, `toResult` centralization | PR 3, base PR 2 branch | `./gradlew :api:billing:test --tests "*.CreateSubscriptionCheckoutUseCaseImplTest"` | `api/app` `@SpringBootTest` (S4,S5,S6,S7) | Revert PR 3; `overlapNotice` additive, no schema change |

## Phase 1: Domain — `Cancellation` + `Subscription.cancel()` (PR 1)

- [x] 1.1 RED `SubscriptionTest`: `cancel(by, reason, now)` from `ACTIVE` sets status/`cancelledAt`/`cancelledBy`/`cancellationReason`, `endDate` unchanged (A2)
- [x] 1.2 RED `SubscriptionTest`: `cancel()` from `PENDING`/`CANCELLED`/`EXPIRED` throws `IllegalStateException` (A3)
- [x] 1.3 RED `SubscriptionTest`: self-cancel stamps `cancelledBy` non-null even when `by == owner`; blank `reason` from non-owner rejected (D1)
- [x] 1.4 RED `SubscriptionTest.rejects_null_or_blank_required_fields`: update 13-arg raw `new Subscription(...)` call (lines 168-172) to 14 args
- [x] 1.5 RED `SubscriptionTest`: `cancelled()`/`activate()` behavior unchanged (regression, A2)
- [x] 1.6 GREEN create `domain/model/Cancellation.java`: `record Cancellation(Instant at, UUID by, String reason)` (A1)
- [x] 1.7 GREEN `Subscription.java`: add `cancellation` field, 14-arg constructor, `cancel(UUID by, String reason, Instant at)`, `getCancellation()`; update `copy()` (7→8 params) and `pendingCheckout` to pass `null`
- [x] 1.8 REFACTOR: confirm domain package still 100% covered (`jacocoTestReport` for `api:billing`)

## Phase 2: Persistence (PR 1)

- [x] 2.1 RED `SubscriptionJpaMapperTest`: `toDomain`/`toEntity` round-trip `Cancellation` incl. all-null legacy rows
- [x] 2.2 RED `SubscriptionRepositoryAdapterTest`: `findActiveByUserId`, `findById` return mapped `Subscription`
- [x] 2.3 GREEN create `api/app/src/main/resources/db/migration/V17__billing_subscription_cancellation.sql`: `cancelled_at DATETIME(3) NULL`, `cancelled_by BINARY(16) NULL`, `cancellation_reason VARCHAR(500) NULL`, no new index (A8)
- [x] 2.4 GREEN `SubscriptionJpaEntity`: add 3 nullable columns + getters
- [x] 2.5 GREEN `SubscriptionJpaMapper`: map `Cancellation` in `toDomain`/`toEntity`
- [x] 2.6 GREEN `SubscriptionRepository` port: add `findActiveByUserId(UUID)`, `findById(UUID)`
- [x] 2.7 GREEN `SubscriptionJpaRepository`: add `findByUserIdAndStatus` derived query for strictly-active-by-user, reuse `CrudRepository#findById`
- [x] 2.8 GREEN `SubscriptionRepositoryAdapter`: implement `findActiveByUserId`, `findById` with course mapping
- [x] 2.9 Run `./gradlew :api:billing:test :api:billing:jacocoTestCoverageVerification` — confirm PR 1 green, no HTTP change

## Phase 3: Use Case (PR 2)

- [x] 3.1 RED `CancelSubscriptionUseCaseImplTest`: `Own` resolves by `actingUserId`; `ById` by non-admin → `SubscriptionNotFoundException` (A4, A5)
- [x] 3.2 RED `CancelSubscriptionUseCaseImplTest`: blank/absent `reason` on admin path rejected before any `save` (D1)
- [x] 3.3 RED `CancelSubscriptionUseCaseImplTest`: `cancellationPolicy` read via `PlanRepository.findById` (not `findActiveById`)
- [x] 3.4 GREEN create `application/dto/CancellationTarget.java`: `sealed interface CancellationTarget { record Own() {} record ById(UUID subscriptionId) {} }` (A4)
- [x] 3.5 GREEN create `application/dto/CancelSubscriptionCommand.java`, `application/dto/CancellationResult.java`
- [x] 3.6 GREEN create `domain/exception/SubscriptionNotFoundException.java` (`SUBSCRIPTION_NOT_FOUND`, A5)
- [x] 3.7 GREEN create `application/port/in/CancelSubscriptionUseCase.java`
- [x] 3.8 GREEN create `application/usecase/CancelSubscriptionUseCaseImpl.java`: authorization + `Subscription.cancel(...)` + `save`
- [x] 3.9 GREEN create `infrastructure/transaction/TransactionalCancelSubscriptionUseCase.java`
- [x] 3.10 GREEN `infrastructure/config/BillingConfiguration.java`: wire new beans

## Phase 4: HTTP + Security Fix (PR 2)

- [x] 4.1 RED security test: unauthenticated `DELETE /api/v1/billing/subscriptions/me` currently falls through to permissive `anyRequest()` — assert `401` fails today (A7)
- [x] 4.2 GREEN `SecurityConfig.java`: add `.requestMatchers(HttpMethod.DELETE, "/api/v1/billing/subscriptions/me").authenticated()` before `anyRequest()` (A7, mandatory)
- [x] 4.3 RED `SubscriptionControllerTest`: `DELETE /me` → 200, body has `endDate`+`cancellationPolicy`; JSON key `cancellationReason` absent (S1, D2)
- [x] 4.4 RED `SubscriptionControllerTest`: `DELETE /me` with no `ACTIVE` subscription → 404, no state change (S2)
- [x] 4.5 GREEN create `infrastructure/web/dto/CancelSubscriptionResponse.java` (no `cancellationReason` component, D2)
- [x] 4.6 GREEN `SubscriptionController.java`: add `DELETE /me` mapping using `CancellationTarget.Own`
- [x] 4.7 RED `SubscriptionAdminControllerTest`: valid reason → 200, `cancelledBy`+`cancellationReason` persisted (S8) — controller-level defense-in-depth test; true 403 for S11 is `SecurityConfigTest` (standalone MockMvc has no security filter chain)
- [x] 4.8 RED `SubscriptionAdminControllerTest`: blank/absent reason → 400, no state change (S9)
- [x] 4.9 RED `SubscriptionAdminControllerTest` + `SecurityConfigTest`: non-admin caller → 403 (S11) — regression-tested at the real `/api/v1/admin/**` matcher, confirmed no new SecurityConfig entry needed
- [x] 4.10 GREEN create `infrastructure/web/dto/CancelSubscriptionRequest.java` (`@NotBlank reason`)
- [x] 4.11 GREEN create `infrastructure/web/controller/SubscriptionAdminController.java`: `@SubscriptionEndpoint`, `DELETE /api/v1/admin/billing/subscriptions/{id}` (A6)
- [x] 4.12 GREEN `SubscriptionExceptionHandler.java`: map `SubscriptionNotFoundException` → 404
- [x] 4.13 GREEN `api/openapi/billing-v1.yaml`: add both `DELETE` paths + response schemas
- [x] 4.14 GREEN `bruno/API - Direct/billing/*.bru`: add `/me` and `/admin/{id}` DELETE requests (repo convention, `api/openapi/README.md`)
- [x] 4.15 Integration test (`api/app` `@SpringBootTest`): S1, S2, S8, S9, S11 end-to-end (`SubscriptionCancellationIntegrationTest`); S3 access-retention regression already covered by Slice 1's `VirtualLessonAccessIntegrationTest` (re-verified green this batch); S10 covered structurally at controller-test level (JSON-key-absent assertions), not duplicated at integration level
- [x] 4.16 Run `./gradlew :api:billing:test :api:app:test :api:billing:jacocoTestCoverageVerification`

## Phase 5: D3 Overlap Notice (PR 3)

- [x] 5.1 RED port/adapter test: `findLatestCancelledWithRemainingAccess` returns latest matching row (same `planId`, `CANCELLED`, `endDate` > now); empty for different plan / expired / null `endDate`
- [x] 5.2 GREEN `SubscriptionRepository` port: add `findLatestCancelledWithRemainingAccess(UUID, PlanId, Instant)`
- [x] 5.3 GREEN `SubscriptionJpaRepository`: derived query `findFirstByUserIdAndPlanIdAndStatusAndEndDateAfterOrderByEndDateDesc`
- [x] 5.4 GREEN `SubscriptionRepositoryAdapter`: implement 5.2 (A8: no new index)
- [x] 5.5 RED `CreateSubscriptionCheckoutUseCaseImplTest`: new checkout with overlap → `201` + `overlapNotice` (S5); no overlap → `null` (S6); replay branch (line 86) also returns notice (S7, A9)
- [x] 5.6 GREEN create `application/dto/OverlapNotice.java`: `record OverlapNotice(String code, Instant currentAccessEndsAt)`, `code = "OVERLAPPING_PAID_PERIOD"`
- [x] 5.7 GREEN `SubscriptionCheckoutResult.java`: add nullable `overlapNotice` component
- [x] 5.8 GREEN `CreateSubscriptionCheckoutUseCaseImpl.java`: convert `toResult` (line 131) from `static` to instance method computing overlap via 5.2; apply at both call sites (lines 86, 116) (A9)
- [x] 5.9 GREEN `infrastructure/web/dto/SubscriptionCheckoutResponse.java`: add `overlapNotice` field
- [x] 5.10 GREEN `api/openapi/billing-v1.yaml`: add `overlapNotice` + `OverlapNotice` schema to checkout response (validated with `redocly lint`, only the 2 pre-existing accepted warnings)
- [x] 5.11 GREEN `bruno/API - Direct/billing/*.bru`: update checkout response example/assertions (`Create Subscription Checkout.bru`)
- [x] 5.12 Integration test: re-purchase after cancellation with remaining access → `201` + non-null `overlapNotice`; old subscription never reactivated (S4); plus a no-prior-cancellation → `null` companion (S6) — added to `SubscriptionCancellationIntegrationTest` (reuses its existing checkout+activate+cancel fixtures)
- [x] 5.13 Run full regression `./gradlew :api:billing:test :api:app:test` + `jacocoTestCoverageVerification`

# Tasks: Billing Subscription Status (#32, US-BILLING-004)

## Verified Facts Driving This Split

- `findCurrentByUserId` (`application/port/out/SubscriptionRepository.java:99`,
  implemented in `SubscriptionRepositoryAdapter.java:111`) resolves through the
  `active_user_id` column (`SubscriptionJpaEntity.java:69`), which is released
  the moment a row leaves PENDING/ACTIVE. It structurally cannot return
  EXPIRED. `design.md` (A1) names the new port method
  **`findLatestExpiredByUserId`** — used verbatim below, never a paraphrase.
- `SecurityConfig.java` (`api/auth/.../infrastructure/security/`) has no
  explicit matcher for `/api/v1/billing/subscriptions/me` today — not for the
  existing `DELETE`, not for either new `GET`. All three fall through to
  `anyRequest().authenticated()` (line 229 area, after the explicit matchers).
  **No security logic changes** for the two new GET routes; the only required
  change is extending the class javadoc (lines 44-89) with two entries in the
  same style as the existing `DELETE /api/v1/billing/subscriptions/me →
  authenticated` entry (line 64), documenting that both new GETs share that
  same fallback. This is scoped as a small doc-only task inside PR 4, not its
  own PR.

## Review Workload Forecast

Grounded in this module's own measured precedents (`wc -n` equivalent via
line count on the actual files, not remembered numbers):

| Precedent file | Lines | What it tells us |
|---|---|---|
| `Subscription.java` (domain) | 359 | Adding `EXPIRING_SOON_THRESHOLD_DAYS` + `daysRemaining(Instant)` + `isExpiringSoon(Instant)` with javadoc → +~35-45 lines |
| `SubscriptionNotFoundException.java` | 20 | `NoSubscriptionException` mirrors this shape byte-for-byte (A5) → ~20 lines |
| `PlanNotFoundExceptionTest.java` | 10 | `NoSubscriptionExceptionTest` mirrors this → ~10-15 lines |
| `SubscriptionTest.java` | 350 | New cases for ceiling/absent-while-PENDING/0-after-expiry/7-inclusive/8-false → +~90-120 lines |
| `SubscriptionRepository.java` (port) | 124 | Two new method signatures + javadoc, matching existing per-method density (~15-20 lines each) → +~30-40 |
| `SubscriptionJpaRepository.java` | 40 | Two new derived-query methods → +~10-14 |
| `SubscriptionRepositoryAdapter.java` | 149 | Two new `@Transactional(readOnly = true)` methods → +~30-40 |
| `SubscriptionRepositoryAdapterTest.java` | 240 | Two new methods, dense per-method case coverage matching this file's own average → +~90-120 |
| `SubscriptionJpaRepositoryTest.java` | 85 | One derived-query addition → +~25-35 |
| `CancelSubscriptionUseCaseImpl.java` | 72 | `GetCurrentSubscriptionUseCaseImpl` has one more branch (slot-first, then EXPIRED fallback, then throw) plus `Clock` injection → ~50-60 |
| `AssignTrialSubscriptionUseCaseImplTest.java` / `CancelSubscriptionUseCaseImplTest.java` | 178 / 166 | `GetCurrentSubscriptionUseCaseImplTest` has 5 branch scenarios (slot-first-ACTIVE, PENDING, EXPIRED-fallback, none→exception, CANCELLED-not-returned) → ~150-180 |
| `BillingConfiguration.java` / `BillingConfigurationTest.java` | 257 / 140 | Two more unwrapped read-only beans, mirroring `listPlansUseCase` → +~20 / +~20 |
| `SubscriptionController.java` | 70 | Two new `@GetMapping` methods + javadoc → +~40-50 |
| `SubscriptionControllerTest.java` | 255 | Two new methods, each with 4 sealed-state mapping cases + token-source assertion → +~90-130 |
| `SubscriptionExceptionHandler.java` / `SubscriptionExceptionHandlerTest.java` | 121 / 32 | One new branch (`NoSubscriptionException → 404` + `plansUrl`), mirrors `subscriptionNotFound` → +~15-20 / +~10-15 |
| `SubscriptionCancellationIntegrationTest.java` (`api:app`) | 331 | New `SubscriptionStatusIntegrationTest` covers 6 issue scenarios + 401 + immediate post-sweep reflection, one more scenario axis than the cancellation test → ~380-430 |

**Estimate**: ~1350-1600 changed lines (production + tests) across the whole
change — comparable in magnitude to the prior BFF plans-view change, driven
mostly by the application-layer use case (5 branch scenarios, 100% coverage
floor) and the end-to-end integration test (6 scenarios).

```text
Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked (feature/* off develop, each cut after its predecessor merges)
400-line budget risk: Medium-High (PR 3 and PR 5 are the ones to watch)
```

Each PR gets its own `feature/*` branch cut from an up-to-date `develop`,
per this repository's Git Flow, as explicitly requested for this change.

### Suggested Work Units

The split follows Clean Architecture layering exactly, mirroring
`design.md`'s own "Dependency Order" section: persistence and domain are
independent of each other (steps 1-2), everything from application onward is
strictly sequential (steps 3-7). Five PRs, one per architectural seam.

| Unit | Goal | PR | Focused test command | Rollback boundary |
|---|---|---|---|---|
| 1 | Domain: `daysRemaining`/`isExpiringSoon`/threshold on `Subscription`; `NoSubscriptionException` | PR 1 (~155-195) | `./gradlew :api:billing:test --tests "*SubscriptionTest*" --tests "*NoSubscriptionExceptionTest*"` | Revert 2 new/modified files; nothing references them yet |
| 2 | Persistence: `SubscriptionJpaRepository` queries, port additions, `SubscriptionRepositoryAdapter` impl, `SubscriptionHistoryEntry` projection | PR 2 (~215-260) | `./gradlew :api:billing:test --tests "*SubscriptionJpaRepositoryTest*" --tests "*SubscriptionRepositoryAdapterTest*"` | Revert 4 new/modified files; port methods stay unused |
| 3 | Application: `CurrentSubscriptionResult`, the two in-ports, the two use-case impls, `BillingConfiguration` wiring | PR 3 (~330-400) | `./gradlew :api:billing:test --tests "*GetCurrentSubscriptionUseCase*" --tests "*GetSubscriptionHistoryUseCase*" --tests "*BillingConfigurationTest*"` | Revert 7 new/modified files; beans become unused, nothing wired to the controller yet |
| 4 | Web: two response DTOs, two controller methods, exception handler branch, `SecurityConfig` javadoc | PR 4 (~255-300) | `./gradlew :api:billing:test --tests "*SubscriptionControllerTest*" --tests "*SubscriptionExceptionHandlerTest*"` | Revert 6 new/modified files; `/me` and `/me/history` become unreachable again |
| 5 | Integration tests for the 6 issue scenarios + 401 + immediate post-sweep reflection | PR 5 (~385-435) | `./gradlew :api:app:test --tests "*SubscriptionStatusIntegrationTest*"` | Revert the new integration test class; nothing in production code depends on it |

## PR 1 — Domain: computed status fields, no-subscription exception

**Branch**: `feature/billing-subscription-status-domain` off `develop`.

- [x] 1.1 RED: extend `domain/model/SubscriptionTest.java` — `daysRemaining(Instant)`:
      ceils partial days (18h remaining → `1`, never `0`, while access still
      holds), absent (`OptionalLong.empty()`) while status is PENDING (no
      `endDate` yet), `0` once `endDate` has passed but status has not yet
      flipped (sweep-lag window). `isExpiringSoon(Instant)`: `true` at exactly
      7 days remaining (inclusive `<=`, mirrors `expire()`'s own boundary
      style), `false` at 8, always `false` for EXPIRED and PENDING regardless
      of computed days.
- [x] 1.2 Verify RED: `./gradlew :api:billing:test --tests "*SubscriptionTest*"`
      fails on the missing `daysRemaining`/`isExpiringSoon` methods, not a typo.
- [x] 1.3 GREEN: modify `domain/model/Subscription.java` — add
      `private static final int EXPIRING_SOON_THRESHOLD_DAYS = 7;`,
      `public OptionalLong daysRemaining(Instant at)` (ceil, floored at 0,
      absent while PENDING), `public boolean isExpiringSoon(Instant at)`
      (`status == ACTIVE && daysRemaining(at) <= 7`). Javadoc documents the
      sweep-lag `0` case explicitly (design.md A2).
- [x] 1.4 Verify GREEN: `SubscriptionTest` suite green.
- [x] 1.5 RED: create `domain/exception/NoSubscriptionExceptionTest.java`
      (mirrors `PlanNotFoundExceptionTest.java`'s shape): carries error code
      `NO_SUBSCRIPTION`, distinct from `SubscriptionNotFoundException`'s
      `SUBSCRIPTION_NOT_FOUND`.
- [x] 1.6 Verify RED: fails on the missing `NoSubscriptionException` class.
- [x] 1.7 GREEN: create `domain/exception/NoSubscriptionException.java` —
      `extends BusinessException`, `ERROR_CODE = "NO_SUBSCRIPTION"`, no-arg
      constructor, English message, byte-for-byte the
      `SubscriptionNotFoundException` shape (A5). Javadoc states the inverse
      anti-enumeration note: the caller reads their own dashboard here, so
      ambiguity buys nothing (D2).
- [x] 1.8 Verify GREEN: `NoSubscriptionExceptionTest` green.
- [x] 1.9 Run `./gradlew :api:billing:test :api:billing:jacocoTestCoverageVerification`
      before opening PR 1 — confirms domain+application stays at 100%.

## PR 2 — Persistence: current-status and history queries

**Branch**: `feature/billing-subscription-status-persistence` off `develop`
(cut after PR 1 merges; independent of PR 1's content per design.md's
dependency order, but sequenced second here so both land before application
work needs them).

- [x] 2.1 RED: extend `infrastructure/persistence/repository/SubscriptionJpaRepositoryTest.java`
      — `findFirstByUserIdAndStatusOrderByEndDateDesc(userId, EXPIRED)` returns
      the latest EXPIRED row by `endDate` when several exist, empty when none
      do; `findAllByUserIdOrderByCreatedAtDesc(userId)` returns every row for
      the user ordered newest-created-first, `[]` for a user with none.
- [x] 2.2 Verify RED: `./gradlew :api:billing:test --tests "*SubscriptionJpaRepositoryTest*"`
      fails on the missing derived-query methods, not a typo.
- [x] 2.3 GREEN: modify `infrastructure/persistence/repository/SubscriptionJpaRepository.java`
      — add both derived-query methods (Spring Data method-name derivation,
      no `@Query`, matching the file's existing convention).
- [x] 2.4 Verify GREEN: `SubscriptionJpaRepositoryTest` suite green.
- [x] 2.5 Create `application/dto/SubscriptionHistoryEntry.java` — projection
      record: `id`, `planId`, `status`, `startDate`, `endDate`, `createdAt`
      (A3; entity → record mapped directly in the adapter, never a rehydrated
      `Subscription` with a falsified empty course snapshot).
- [x] 2.6 Modify `application/port/out/SubscriptionRepository.java` — add
      `Optional<Subscription> findLatestExpiredByUserId(UUID userId)` (exact
      name from design.md A1, javadoc contrasting it with
      `findCurrentByUserId`'s slot-only scope — see "Verified Facts" above)
      and `List<SubscriptionHistoryEntry> findHistoryByUserId(UUID userId)`.
- [x] 2.7 RED: extend `infrastructure/persistence/adapter/SubscriptionRepositoryAdapterTest.java`
      — `findLatestExpiredByUserId` picks the latest EXPIRED row only, empty
      when none exists, does not resolve through `active_user_id` (unlike
      `findCurrentByUserId`, per the file's own existing doc-comment
      convention at line 189); `findHistoryByUserId` returns rows ordered
      `created_at DESC` and issues no per-row `subscription_courses` query
      (A3 — assert query count, not just result shape, mirroring this test
      file's existing style).
- [x] 2.8 Verify RED: fails on the missing adapter methods, not a typo.
- [x] 2.9 GREEN: modify `infrastructure/persistence/adapter/SubscriptionRepositoryAdapter.java`
      — implement both methods, `@Transactional(propagation = REQUIRED,
      readOnly = true)` on each, matching every other query method in this
      adapter; `findHistoryByUserId` maps `SubscriptionJpaEntity` → record
      directly, no `toDomainWithCourses` call.
- [x] 2.10 Verify GREEN: `SubscriptionRepositoryAdapterTest` suite green,
      including all pre-existing cases (no regression on `findCurrentByUserId`
      or `findAllByUserId`).
- [x] 2.11 Run `./gradlew :api:billing:test :api:billing:jacocoTestCoverageVerification`
      before opening PR 2 — confirms infrastructure stays ≥85%.

## PR 3 — Application: use cases and wiring

**Branch**: `feature/billing-subscription-status-application` off `develop`
(cut after PR 2 merges; depends on both PR 1's domain methods and PR 2's
repository methods).

- [ ] 3.1 Create `application/dto/CurrentSubscriptionResult.java` — sealed
      interface (A4): `Active(String subscriptionId, String planId, Instant
      startDate, Instant endDate, long daysRemaining, boolean expiringSoon)`,
      `Expired(String subscriptionId, String planId, Instant startDate,
      Instant endDate)`, `PendingPayment(String subscriptionId, String planId,
      String checkoutUrl)`. No test file needed on its own — a sealed
      interface of records has no behavior; exhaustiveness is enforced by the
      compiler and exercised through the use-case tests below.
- [ ] 3.2 Create `application/port/in/GetCurrentSubscriptionUseCase.java` —
      `CurrentSubscriptionResult current(UUID userId)`.
- [ ] 3.3 Create `application/port/in/GetSubscriptionHistoryUseCase.java` —
      `List<SubscriptionHistoryEntry> history(UUID userId)`.
- [ ] 3.4 RED: create `application/usecase/GetCurrentSubscriptionUseCaseImplTest.java`
      (Mockito on `SubscriptionRepository` + a fixed injected `Clock`, mirrors
      `CancelSubscriptionUseCaseImplTest`'s harness style): slot-first
      precedence — an ACTIVE row from `findCurrentByUserId` beats an older
      EXPIRED row, never even calling `findLatestExpiredByUserId`; a PENDING
      row from `findCurrentByUserId` maps to `PendingPayment` with
      `checkoutUrl` and no dates; when `findCurrentByUserId` is empty, falls
      back to `findLatestExpiredByUserId` → `Expired`; when both are empty,
      throws `NoSubscriptionException`; a CANCELLED-with-remaining-access row
      is never returned by either lookup path exercised here (D1 — regression
      guard, not a new lookup).
- [ ] 3.5 Verify RED: `./gradlew :api:billing:test --tests "*GetCurrentSubscriptionUseCaseImplTest*"`
      fails on the missing `GetCurrentSubscriptionUseCaseImpl` class.
- [ ] 3.6 GREEN: create `application/usecase/GetCurrentSubscriptionUseCaseImpl.java`
      — injects `SubscriptionRepository` + `Clock`; resolution order:
      `findCurrentByUserId` (PENDING → `PendingPayment`, ACTIVE → `Active`
      with `daysRemaining`/`isExpiringSoon` computed from the injected
      `Clock`), else `findLatestExpiredByUserId` → `Expired`, else throw
      `NoSubscriptionException`. No `@Transactional*` decorator (matches
      `ListPlansUseCaseImpl`/`GetPlanUseCaseImpl` — the adapter's own
      per-method `readOnly = true` already covers it).
- [ ] 3.7 Verify GREEN: `GetCurrentSubscriptionUseCaseImplTest` suite green.
- [ ] 3.8 RED: create `application/usecase/GetSubscriptionHistoryUseCaseImplTest.java`
      (Mockito): delegates to `subscriptionRepository.findHistoryByUserId`
      and returns its result unchanged, preserving repository order; an empty
      list from the repository stays an empty list, no exception thrown.
- [ ] 3.9 Verify RED: fails on the missing `GetSubscriptionHistoryUseCaseImpl` class.
- [ ] 3.10 GREEN: create `application/usecase/GetSubscriptionHistoryUseCaseImpl.java`
      — pass-through, no branching.
- [ ] 3.11 Verify GREEN: `GetSubscriptionHistoryUseCaseImplTest` suite green.
- [ ] 3.12 Modify `infrastructure/config/BillingConfiguration.java` — add both
      new use-case beans, unwrapped (no `Transactional*` decorator), mirroring
      how `listPlansUseCase` is wired. Update
      `infrastructure/config/BillingConfigurationTest.java` to assert both
      beans are present and correctly typed.
- [ ] 3.13 Run `./gradlew :api:billing:test :api:billing:jacocoTestCoverageVerification`
      before opening PR 3 — confirms domain+application stays at 100%.

## PR 4 — Web: response DTOs, controller, exception handler, SecurityConfig doc

**Branch**: `feature/billing-subscription-status-web` off `develop` (cut
after PR 3 merges).

- [ ] 4.1 Create `infrastructure/web/dto/CurrentSubscriptionResponse.java` —
      flat record discriminated by `status`, all four issue states share one
      shape with a bounded, documented nullable set (`endDate`,
      `daysRemaining`, `expiringSoon`, `checkoutUrl`; nulls serialize as
      `null`, no `@JsonInclude`, matching `SubscriptionCheckoutResponse`'s
      precedent). Static `from(CurrentSubscriptionResult)` — exhaustive
      `switch` over the sealed type, `EXPIRED` branch also sets `plansUrl`.
- [ ] 4.2 Create `infrastructure/web/dto/SubscriptionHistoryItemResponse.java`
      — flat record: `id`, `planId`, `status`, `startDate`, `endDate`. Static
      `from(SubscriptionHistoryEntry)`.
- [ ] 4.3 RED: extend `infrastructure/web/controller/SubscriptionControllerTest.java`
      (standalone mocked-use-case unit test, mirrors this file's existing
      pattern): `GET /me` — `actingUserId` is read from the token, never a
      request parameter (same assertion style as the existing `DELETE /me`
      case); sealed → flat DTO mapping for all four states (ACTIVE with
      `expiringSoon` true/false, EXPIRED with `plansUrl`, PENDING with
      `checkoutUrl` and null dates); a thrown `NoSubscriptionException`
      propagates untranslated (no try/catch in the controller). `GET
      /me/history` — returns the mapped list in the use case's own order,
      `[]` stays `[]`.
- [ ] 4.4 Verify RED: `./gradlew :api:billing:test --tests "*SubscriptionControllerTest*"`
      fails on the two missing `@GetMapping` methods, not a typo.
- [ ] 4.5 GREEN: modify `infrastructure/web/controller/SubscriptionController.java`
      — add `@GetMapping("/me")` (`currentOwn`) and `@GetMapping("/me/history")`
      (`historyOwn`), reusing `actingUserId(Authentication)` verbatim (no new
      resolution logic); inject `GetCurrentSubscriptionUseCase` and
      `GetSubscriptionHistoryUseCase` via the constructor alongside the
      existing two use cases. Javadoc documents both routes fall under the
      class's inherited `@SubscriptionEndpoint` advice.
- [ ] 4.6 Verify GREEN: `SubscriptionControllerTest` suite green, including
      all pre-existing `POST`/`DELETE /me` cases (no regression).
- [ ] 4.7 RED: extend `infrastructure/web/controller/SubscriptionExceptionHandlerTest.java`
      — `NoSubscriptionException` maps to `404`, `application/problem+json`,
      `errorCode: "NO_SUBSCRIPTION"`, and a `plansUrl` property pointing at
      `/api/v1/billing/plans`.
- [ ] 4.8 Verify RED: fails on the missing handler branch, not a typo.
- [ ] 4.9 GREEN: modify `infrastructure/web/controller/SubscriptionExceptionHandler.java`
      — add `@ExceptionHandler(NoSubscriptionException.class)` mirroring
      `subscriptionNotFound`'s shape (`ProblemDetails.body(...)` + one
      `setProperty("plansUrl", ...)`, `404`), sourcing the `/api/v1/billing/plans`
      constant from the web layer (A4 — infrastructure is the only layer that
      legitimately knows its own routes).
- [ ] 4.10 Verify GREEN: `SubscriptionExceptionHandlerTest` suite green.
- [ ] 4.11 Modify `api/auth/.../infrastructure/security/SecurityConfig.java` —
      documentation-only change. Add two entries to the class javadoc (same
      block that documents the existing `DELETE /api/v1/billing/subscriptions/me
      → authenticated` rule at line 64), in the same prose style, stating that
      `GET /api/v1/billing/subscriptions/me` and `GET
      /api/v1/billing/subscriptions/me/history` (#32, US-BILLING-004) have no
      dedicated matcher and fall through to `anyRequest().authenticated()`
      alongside the existing `DELETE /me` — same reasoning already documented
      for that rule, not a new security decision. No change to the filter
      chain itself; `SecurityConfigTest` (if one exists) needs no new
      assertion since no matcher was added — this step is prose-only.
- [ ] 4.12 Run `./gradlew :api:billing:test :api:auth:test :api:billing:jacocoTestCoverageVerification`
      before opening PR 4 — confirms `api:billing` infrastructure stays ≥85%
      and `api:auth` still compiles/passes with the javadoc-only change.

## PR 5 — Integration tests for the 6 issue scenarios

**Branch**: `feature/billing-subscription-status-integration` off `develop`
(cut after PR 4 merges).

- [ ] 5.1 RED: create `api/app/src/test/java/com/menta/app/integration/billing/SubscriptionStatusIntegrationTest.java`
      (Testcontainers, mirrors `SubscriptionCancellationIntegrationTest`'s
      harness): the 6 issue scenarios end-to-end —
      - active subscription → `200`, `status: "ACTIVE"`, correct `daysRemaining`;
      - expiring-soon subscription (`daysRemaining <= 7`) → `expiringSoon: true`;
      - expired subscription → `status: "EXPIRED"`, `plansUrl` present, no
        `daysRemaining`;
      - no subscription at all → `404`, `errorCode: "NO_SUBSCRIPTION"`,
        distinct from a cancellation's `SUBSCRIPTION_NOT_FOUND`;
      - history → all persisted rows for the user, newest first;
      - pending payment → `status: "PENDING"`, `checkoutUrl` present, no dates;

      plus the two non-issue-listed but spec-mandated regressions: unauthenticated
      `GET /me` and `GET /me/history` → `401`; and a subscription that
      `SubscriptionExpiryWorker` flips from ACTIVE to EXPIRED moments earlier
      is reflected immediately on the next `GET /me` (no caching — spec
      "Responses are never cached").
- [ ] 5.2 Verify RED: `./gradlew :api:app:test --tests "*SubscriptionStatusIntegrationTest*"`
      fails for the intended reason (routes not yet exercised in this
      Testcontainers context / assertions against not-yet-present response
      shape), not a typo. Both endpoints should already be fully wired by
      PR 4 — this step confirms the new test file drives verification, not
      that it drives new production code.
- [ ] 5.3 GREEN: fix any wiring gap surfaced by 5.2 (expected to be none,
      since PR 4 already wires both routes end to end through
      `SecurityConfig`'s existing fallback and `BillingConfiguration`'s
      beans); otherwise this step is a no-op verification.
- [ ] 5.4 Verify GREEN: full `SubscriptionStatusIntegrationTest` suite green.
- [ ] 5.5 Run `./gradlew test check` (full monorepo build) before opening
      PR 5 — confirms `BillingArchitectureTest` still passes (domain gained
      no Spring/JPA import from `daysRemaining`/`isExpiringSoon`) and every
      module's JaCoCo layer threshold holds, including `api:billing`'s
      100% domain+application / 85% infrastructure floors.

## Out of Scope (confirmed in proposal.md)

- BFF "mi suscripción" page and Android screen — API-only, per the #29 → #177
  split precedent cited in the proposal.
- Wiring `findLatestCancelledWithRemainingAccess` into `/me` (D1) — deliberately
  deferred to a future story.
- Any change to `SubscriptionExpiryWorker`, cancellation, or checkout behavior.
- Any caching layer for the two new endpoints — the spec requires the opposite.

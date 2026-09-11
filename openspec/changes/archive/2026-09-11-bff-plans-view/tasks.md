# Tasks: BFF Plans View (#177, US-BILLING-013)

## Review Workload Forecast

Grounded in this module's own measured precedents (checked against current file
sizes, not remembered numbers):

| Precedent file | Lines | What it tells us |
|---|---|---|
| `VirtualApiClient.java` | 158 (4 methods, 3 nested exceptions) | `BillingApiClient` has 1 method + 2 exceptions (no `ForbiddenException` needed) → ~90-100 lines |
| `VirtualApiAdapter.java` | 365 (4 methods + shared mapping) | `BillingApiAdapter` has 1 method but adds a cache core absent from any existing adapter → ~140 lines pre-cache, +~50 for the cache |
| `VirtualApiAdapterTest.java` | 612 (4 methods, ~5-6 cases/method average) | Plans has 1 method but ~11-12 cases (mapping ×4, no-auth-header, TTL fresh/expired, expired-not-served, cold-failure, single-flight) — more cases per method than any existing adapter test |
| `VirtualApiProperties.java` | 35 | `BillingApiProperties` adds one more field (`cacheTtl`) → ~40 |
| `WebClientConfig.java` | 40 | 3rd qualified bean, mirrors the 2nd exactly → +15 |
| `GetCourseDetailViewUseCaseImpl.java` / test | 106 / 189 | `GetPlansViewUseCaseImpl` is pass-through (no branching, no cross-reference) → smaller, ~25 / ~90 |
| `CourseDetailController.java` / `course-detail.html` | 63 / 42 | New controller+template pair, similar shape → ~35 / ~55 |
| `BffSecurityConfig.java` | 117 | One more method-scoped `permitAll` entry → +8 |
| `VirtualLearningSecurityIntegrationTest.java` | 185 | Reused pattern for the both-directions regression test → ~160 new |
| `VirtualLearningViewIntegrationTest.java` | 522 | One existing case updated (CTA href), not rebuilt → ~20-30 diff |

**Estimate**: ~1450-1550 changed lines (production + tests) across the whole
change. This is a genuinely new upstream integration (billing has never
talked to the BFF before) plus the BFF's first stateful component (the
cache, including a single-flight concurrency test with no precedent in this
module) plus a new controller/template/security-route trio — heavier than
either prior BFF change despite being "one endpoint."

```text
Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High
```

`auto-chain` means the orchestrator proceeds straight to PR1 with the chain
strategy below — no user prompt required before `sdd-apply`. `stacked-to-main`
per this repo's Git Flow: each slice branches off `develop` after its
predecessor merges, and merges back into `develop` in order.

### Suggested Work Units

The cache is inseparable from `BillingApiAdapter` (design: hand-rolled, adapter-
scoped, not a separate class), so the seam falls between "adapter answers
correctly" (mapping only, no statefulness) and "adapter answers efficiently"
(the cache wrapped around that same call) — each independently testable and
independently revertible. The use case is thin enough to fold into the
controller/template slice rather than stand alone.

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | `BillingApiClient` port, `PlanSummary` DTO, `BillingApiProperties`, `billingApiWebClient` bean, `application.yml` block | PR 1 (~185) | `./gradlew :bff:compileJava` | N/A — declarations only, nothing calls them yet | Revert 4 new files + 1 config edit; nothing references them |
| 2 | `BillingApiAdapter`: fetch + status mapping (404/429/503/timeout), no cache yet | PR 2 (~380-400) | `./gradlew :bff:test --tests "*BillingApiAdapterTest*"` | `@WireMockTest`, no Spring context | Revert the new adapter + its test; port from PR 1 stays unused |
| 3 | Add the single-entry TTL + single-flight cache to `BillingApiAdapter` | PR 3 (~220-250) | `./gradlew :bff:test --tests "*BillingApiAdapterTest*"` | `@WireMockTest`, no Spring context | Revert this PR's commit restores the stateless PR-2 adapter atomically |
| 4 | `GetPlansViewUseCase`(+Impl), `PlansController`, `plans.html`, `UseCaseConfig`/`BffSecurityConfig` wiring, unit tests | PR 4 (~300-330) | `./gradlew :bff:test --tests "*Plans*"` | Mockito (use case) + standalone MockMvc (controller), no Spring context | Revert the 6 new/modified files; `/plans` becomes unreachable again |
| 5 | Integration tests (security regression + rendering) + `lesson.html` CTA cutover + existing test update | PR 5 (~350-390) | `./gradlew :bff:test --tests "*Plans*Integration*" --tests "*VirtualLearningViewIntegrationTest*"` | `BaseIntegrationTest` (Testcontainers + WireMock) | Revert this PR's commit restores the dead-end CTA and drops the two new integration test classes atomically |

## PR 1 — Port, DTO, properties, WebClient bean

**Branch**: `feature/bff-plans-view-port` off `develop`.

- [x] 1.1 Create `application/port/out/BillingApiClient.java`: `List<PlanSummary>
      getPlans()` (no token parameter — billing-api-integration spec: "the
      plans call never carries an Authorization header"), nested
      `NotFoundException` (`@ResponseStatus(404)`) and
      `ServiceUnavailableException` (`@ResponseStatus(503)`, optional
      `retryAfterSeconds`), mirroring `VirtualApiClient`'s shapes exactly. No
      `ForbiddenException` — the endpoint is public.
- [x] 1.2 Create `application/dto/PlanSummary.java`: record `(String id, String
      name, String description, BigDecimal price, String currency, int
      durationDays, boolean featured)`. Javadoc explicitly states `courses` is
      dropped and why (design: `LessonStream`'s trim-and-document convention,
      not `CourseDetail`'s byte-for-byte mirror — nothing on this page renders
      it).
- [x] 1.3 Create `infrastructure/config/BillingApiProperties.java`: `@Data
      @Component @Validated @ConfigurationProperties(prefix = "menta.billing")`
      — `baseUrl` (`@NotBlank`), `timeout` default `Duration.ofSeconds(5)`,
      `cacheTtl` default `Duration.ofMinutes(5)` (D4), mirroring
      `VirtualApiProperties`.
- [x] 1.4 Modify `infrastructure/config/WebClientConfig.java`: add
      `billingApiWebClient()` bean bound to `BillingApiProperties.getBaseUrl()`,
      javadoc citing the same qualifier reasoning as `virtualApiWebClient()`.
- [x] 1.5 Modify `bff/src/main/resources/application.yml`: add
      `menta.billing.{base-url,timeout,cache-ttl}`, mirroring the existing
      `menta.api`/`menta.auth` blocks.
- [x] 1.6 Run `./gradlew :bff:compileJava` — confirms the new types compile;
      no behavior to RED/GREEN yet (declarations only, nothing calls them).

## PR 2 — Adapter: fetch + status mapping, no cache

**Branch**: `feature/bff-plans-view-adapter` off `develop` (cut after PR 1 merges).

- [x] 2.1 RED: create `infrastructure/adapter/BillingApiAdapterTest.java`
      (`@WireMockTest`, plain constructor, `Duration.ofMinutes(5)` cacheTtl for
      now — cache behavior is out of scope for this PR, tests here only prove
      a single call each): 200 → mapped `List<PlanSummary>`, unknown `courses`
      field silently ignored (no error); no `Authorization` header is ever
      sent (`verify(getRequestedFor(...).withoutHeader("Authorization"))`);
      404 → `NotFoundException`; 429 → `ServiceUnavailableException` with
      `retryAfterSeconds` captured from `Retry-After`; 503 → same, no
      `Retry-After` present; timeout → `ServiceUnavailableException`.
- [x] 2.2 Verify RED: run `./gradlew :bff:test --tests "*BillingApiAdapterTest*"`
      — fails on the missing `BillingApiAdapter` class, not a typo.
- [x] 2.3 GREEN: create `infrastructure/adapter/BillingApiAdapter.java` —
      explicit constructor taking `@Qualifier("billingApiWebClient") WebClient`
      + `BillingApiProperties` (Lombok cannot carry the qualifier); private
      `PlanListWireResponse(List<PlanWire> plans)` record for the `{"plans":
      [...]}` envelope; no `Authorization` header set under any circumstance;
      status mapping: `404 → NotFoundException`; `429 || 5xx →
      ServiceUnavailableException` (parse `Retry-After`); anything else →
      generic `RuntimeException`, mirroring `mapErrorStatus`/`mapHttpException`
      in `VirtualApiAdapter`. No caching yet — every call hits upstream.
- [x] 2.4 Verify GREEN: full `BillingApiAdapterTest` suite green.
- [x] 2.5 Run `./gradlew :bff:test check` before opening PR 2.

## PR 3 — Cache: single-entry TTL, single-flight, no stale-on-failure

**Branch**: `feature/bff-plans-view-cache` off `develop` (cut after PR 2 merges).

- [x] 3.1 RED: extend `BillingApiAdapterTest.java` with a `-- caching --`
      section, constructing the adapter with an explicit `cacheTtl` per case
      (property-driven, no `Clock` injection, per design):
      - `cacheTtl = Duration.ofMinutes(5)`, two calls in a row →
        `verify(1, getRequestedFor(...))`, both calls return the same data
        (billing-api-integration spec: "repeated requests within the TTL
        trigger exactly one upstream call");
      - `cacheTtl = Duration.ZERO`, two calls in a row →
        `verify(2, getRequestedFor(...))` (expiry forces a fresh call each
        time);
      - **expired entry is never served on failure**: `cacheTtl =
        Duration.ZERO`, first call stubbed `200`, re-stub to `503` before the
        second call → the second call throws `ServiceUnavailableException`,
        it does NOT return the first payload (spec: "an expired entry is not
        served when the refresh fails");
      - **cold-cache failure throws**: empty cache, first call stubbed `503`
        → throws, cache stays empty (spec: "a failed upstream call does not
        populate the cache");
      - **single-flight**: `ExecutorService` + `CountDownLatch`, N threads
        call `getPlans()` concurrently against a cold cache with a WireMock
        fixed delay → `verify(1, getRequestedFor(...))`, all N threads
        receive the same result.
- [x] 3.2 Verify RED: run `./gradlew :bff:test --tests "*BillingApiAdapterTest*"`
      — the new cases fail because every call still reaches WireMock (no
      cache exists yet), not because of a typo.
- [x] 3.3 GREEN: add the cache core to `BillingApiAdapter.java` — private
      `record Entry(List<PlanSummary> plans, Instant expiresAt)`, `final
      AtomicReference<Entry> cache`, `final ReentrantLock refreshLock`.
      `getPlans()`: read `cache.get()`; if non-null and unexpired, return it;
      else `refreshLock.lock()`, double-check inside the lock, call upstream
      **while holding the lock**, on success `cache.set(new Entry(fresh,
      Instant.now().plus(properties.getCacheTtl())))` and return; a thrown
      exception propagates without touching the existing cache value (D6,
      strict — no stale-on-failure, per design's corrected reasoning).
      `finally { refreshLock.unlock(); }`.
- [x] 3.4 Verify GREEN: full `BillingApiAdapterTest` suite green, including
      all PR 2 mapping cases (no regression).
- [x] 3.5 Run `./gradlew :bff:test check` before opening PR 3.

## PR 4 — Use case, controller, template, wiring

**Branch**: `feature/bff-plans-view-usecase-controller` off `develop` (cut after PR 3 merges).

- [x] 4.1 Create `application/usecase/GetPlansViewUseCase.java`:
      `List<PlanSummary> execute()`.
- [x] 4.2 RED: `application/usecase/GetPlansViewUseCaseImplTest.java`
      (Mockito on `BillingApiClient`): delegates to `billingApiClient.getPlans()`
      and returns its result unchanged; a thrown `NotFoundException`/
      `ServiceUnavailableException` propagates untranslated (no catch).
- [x] 4.3 Verify RED: run `./gradlew :bff:test --tests "*GetPlansViewUseCase*"`
      — fails on the missing `GetPlansViewUseCaseImpl` class.
- [x] 4.4 GREEN: create `GetPlansViewUseCaseImpl.java` — pass-through, no
      try/catch.
- [x] 4.5 Modify `infrastructure/config/UseCaseConfig.java`: add the
      `GetPlansViewUseCase` bean backed by `GetPlansViewUseCaseImpl`.
- [x] 4.6 RED: create `infrastructure/web/controller/PlansControllerTest.java`
      (standalone MockMvc, mocked use case): `GET /plans` → model attribute
      `plans` equals the use case's returned list, view name `"plans"`.
- [x] 4.7 Verify RED: fails on the missing `PlansController` class.
- [x] 4.8 GREEN: create `PlansController.java` — flat `@Controller`,
      `@GetMapping("/plans")`, injects `GetPlansViewUseCase`, adds `plans` to
      the model, returns view name `"plans"`; no try/catch — uncaught
      `@ResponseStatus` exceptions fall through to `/error` → `error.html`
      (D6, mirrors `CourseDetailController`).
- [x] 4.9 Modify `infrastructure/config/BffSecurityConfig.java`: add
      `.requestMatchers(HttpMethod.GET, "/plans").permitAll()` — exact path,
      no wildcard (bff-plans-view spec: "no wildcard, since the route takes
      no path variable"), placed after the existing course/lesson entries and
      before `anyRequest().authenticated()`.
- [x] 4.10 Create `bff/src/main/resources/templates/plans.html`: flat
      `lang="es"` list, no fragments. For each plan: name, description, price
      formatted with `#numbers.formatDecimal` + currency, `'cada ' +
      durationDays + (durationDays == 1 ? ' día' : ' días')`, and a
      `th:if="${plan.featured()}"` "Destacado" badge — list order follows the
      upstream response order unchanged (D5, no reordering).
- [x] 4.11 Verify GREEN: `PlansControllerTest` and
      `GetPlansViewUseCaseImplTest` both green.
- [x] 4.12 Run `./gradlew :bff:test check` before opening PR 4.

## PR 5 — Integration tests, security regression, lesson CTA cutover

**Branch**: `feature/bff-plans-view-integration` off `develop` (cut after PR 4 merges).

- [x] 5.1 RED: create
      `infrastructure/integration/BillingPlansSecurityIntegrationTest.java`,
      extending the `VirtualLearningSecurityIntegrationTest` pattern
      (Testcontainers + WireMock): anonymous `GET /plans` → `200`, no redirect
      to `/login`; anonymous `GET /dashboard` and one other unmapped protected
      path still redirect to `/login`; anonymous `GET /plans/x` (extra path
      segment) still redirects — the exact-path matcher does not widen to a
      prefix; anonymous `POST /plans` → not permitted (`403` or redirect, not
      `200`).

      Deviation (orchestrator-directed): the both-directions plans coverage
      was added as new `@Test` methods directly on the existing
      `VirtualLearningSecurityIntegrationTest` class, not a separate
      `BillingPlansSecurityIntegrationTest` class — the established
      convention in this module is one security-regression class extended
      per feature (#170's own class). The `/dashboard` and unmapped-path
      redirect regressions were already present and green in that class; no
      duplicate assertions were added for them.
- [x] 5.2 RED: create
      `infrastructure/integration/BillingPlansViewIntegrationTest.java`:
      stubbed billing plans response → rendered HTML contains each plan's
      name, description, price+currency, and duration text; a plan with
      `featured: true` not first in response order → "Destacado" badge
      present and rendered list order matches upstream response order exactly
      (bff-plans-view spec: "list order matches the upstream response order
      exactly, unchanged by which plan is featured"); a plan with `featured:
      false` → no badge; stubbed `503` from billing → renders the shared
      error view with no leaked problem-detail body and no plans-specific
      copy.
- [x] 5.3 Verify RED: run
      `./gradlew :bff:test --tests "*BillingPlansSecurityIntegrationTest*" --tests "*BillingPlansViewIntegrationTest*"`
      — fails for the intended reason (route not yet reachable in this test's
      WireMock/security wiring context, or assertions against not-yet-present
      markup), not a typo. (Both classes should already pass once PR 4's
      wiring is present — this step confirms these NEW test files, not
      already-covered production code, drive the fix if anything is missing.)

      Confirmed a real wiring gap: `AbstractTestcontainersConfig` registered
      dynamic `menta.auth.base-url`/`menta.api.base-url` overrides but never
      `menta.billing.base-url`, so `BillingPlansViewIntegrationTest` would
      have hit `application.yml`'s `http://localhost:8081` default instead
      of WireMock. The non-featured-badge case also failed once the base-url
      fix was in — the shared plans-cache bean (5-minute TTL) was leaking
      the previous test's stubbed response across test methods on the same
      Spring context.
- [x] 5.4 GREEN: fix any wiring gap surfaced by 5.3 (expected to be none, since
      PR 4 already wires `/plans` end to end); otherwise this step is a no-op
      verification.

      Added `menta.billing.base-url` to `AbstractTestcontainersConfig`'s
      `@DynamicPropertySource`, pointing at the singleton WireMock server.
      Also added `menta.billing.cache-ttl` = `PT0S` there, mirroring
      `BillingApiAdapterTest`'s own `Duration.ZERO` convention for
      deterministically disabling the cache, so each integration test's
      WireMock stub stays authoritative for its own request instead of being
      shadowed by a prior test's cached response.
- [x] 5.5 RED: modify `templates/lesson.html`'s sample-branch CTA — currently
      a dead-end message — asserting via `VirtualLearningViewIntegrationTest`
      (existing lesson-sample test) that the CTA now links to `/plans`
      instead of the old dead-end copy. Update that existing assertion first
      so it fails against the current `lesson.html`.
- [x] 5.6 Verify RED: run
      `./gradlew :bff:test --tests "*VirtualLearningViewIntegrationTest*"` —
      the updated assertion fails against the still-dead-end CTA.
- [x] 5.7 GREEN: modify `templates/lesson.html` — replace the dead-end message
      with `th:href="@{/plans}"` (D2); `LessonView.java`'s `Sample` javadoc
      updated to note #177 is resolved via the template route, the field
      itself still deliberately absent.
- [x] 5.8 Verify GREEN: full `VirtualLearningViewIntegrationTest`,
      `BillingPlansSecurityIntegrationTest`, `BillingPlansViewIntegrationTest`
      suites green.
- [x] 5.9 Run `./gradlew test check` (full monorepo build) — confirms
      `BffArchitectureTest` still passes (cache lives in `infrastructure`, no
      new `application → infrastructure` import) and JaCoCo layer thresholds
      hold before opening PR 5.

```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:9b87286a1000000000000000000000000000000000000000000000000000
verdict: pass
blockers: 0
critical_findings: 0
requirements: 10/10
scenarios: 16/16
test_command: ./gradlew :bff:test check --rerun-tasks
test_exit_code: 0
test_output_hash: sha256:2b10074f9ca79536aa51220c8a6f194d3f41c12bc289fa7bc3c8531a9fcdd2a2
build_command: ./gradlew :bff:test check --rerun-tasks
build_exit_code: 0
build_output_hash: sha256:2b10074f9ca79536aa51220c8a6f194d3f41c12bc289fa7bc3c8531a9fcdd2a2
```

## Verification Report

**Change**: bff-plans-view (#177, US-BILLING-013)
**Version**: N/A
**Mode**: Standard (strict_tdd config present, but this is final verify, not TDD-loop verify)
**HEAD**: `develop` @ 9b87286 (merge of PR #200)

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 37 |
| Tasks complete | 37 |
| Tasks incomplete | 0 |

### Build & Tests Execution
**Build**: PASSED
```text
$ ./gradlew :bff:test check --rerun-tasks
BUILD SUCCESSFUL in 1h 7m 5s
111 actionable tasks: 111 executed
```
(Checkstyle violation reports are generated but non-blocking per project config; build did not fail on them.)

**Tests**: 2069 passed / 0 failed / 2 skipped (pre-existing, unrelated to this change), counted from actual JUnit XML `<testsuite>` attributes under each module's `build/test-results/test/*.xml`, not from console text:

| Module | tests | failures | errors | skipped |
|---|---|---|---|---|
| api:shared | 35 | 0 | 0 | 0 |
| api:auth | 486 | 0 | 0 | 0 |
| api:virtual | 344 | 0 | 0 | 0 |
| api:physical | 237 | 0 | 0 | 0 |
| api:billing | 488 | 0 | 0 | 0 |
| api:app | 247 | 0 | 0 | 0 |
| bff | 232 | 0 | 0 | 2 |
| **TOTAL** | **2069** | **0** | **0** | **2** |

Plans-specific test classes (all green):
- `BillingApiAdapterTest` — 11 tests
- `GetPlansViewUseCaseImplTest` — 3 tests
- `PlansControllerTest` — 3 tests
- `BillingPlansViewIntegrationTest` — 4 tests
- `VirtualLearningSecurityIntegrationTest` — 11 tests (includes 3 new `/plans` both-directions cases)
- `VirtualLearningViewIntegrationTest` — 12 tests (includes the updated CTA-href assertion)
- `BffArchitectureTest` — 3 tests (ArchUnit, unaffected)

**Coverage**: JaCoCo layer-verification tasks are part of `check` and the overall build succeeded, so per-module thresholds (auth/billing 100/85, virtual/physical 95/90, shared/app/bff floor ≥85%) all held. No threshold regression.

### Spec Compliance Matrix

**`bff-plans-view`**
| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Anonymous-reachable plans route | Anonymous visitor opens the plans page | `VirtualLearningSecurityIntegrationTest > anonymousPlansRequest_shouldReturn200_withNoLoginRedirect` | ✅ COMPLIANT |
| Plans-list route permitted w/o widening | Only the plans route is newly permitted | `VirtualLearningSecurityIntegrationTest > pathResemblingPlansMatcher_withExtraSegment_shouldRemainAuthenticatedOnly`, `postToPlansRoute_shouldRemainAuthenticatedOnly` | ✅ COMPLIANT |
| Plans-list route permitted w/o widening | Anonymous access to the plans route specifically | `VirtualLearningSecurityIntegrationTest > anonymousPlansRequest_shouldReturn200_withNoLoginRedirect` | ✅ COMPLIANT |
| Each plan renders name/desc/price+currency/duration | A plan renders its full price context | `BillingPlansViewIntegrationTest > plansRequest_shouldRenderNameDescriptionPriceCurrencyAndDuration` | ✅ COMPLIANT |
| Featured plans show badge, order unchanged | Featured plan shows badge without reordering | `BillingPlansViewIntegrationTest > featuredPlanNotFirst_shouldShowBadgeWithoutReordering` | ✅ COMPLIANT |
| Featured plans show badge, order unchanged | Non-featured plan shows no badge | `BillingPlansViewIntegrationTest > nonFeaturedPlan_shouldShowNoBadge` | ✅ COMPLIANT |
| Upstream failures degrade to shared error view | Billing downtime renders shared error view | `BillingPlansViewIntegrationTest > billingUnavailable_shouldRenderErrorViewWithoutLeakingUpstreamDetails` | ✅ COMPLIANT |

**`billing-api-integration`**
| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Dedicated outbound port/adapter | Adapter wired to its own qualified WebClient bean | `WebClientConfig.billingApiWebClient()` + `BillingApiAdapter` explicit constructor (compiles + boots in every integration test's Spring context) | ✅ COMPLIANT |
| Plans call never carries Authorization header | Authenticated visitor's plans call is still unauthenticated | `BillingApiAdapterTest > "should never send an Authorization header on the plans call"` | ✅ COMPLIANT |
| Upstream statuses map to typed exceptions, incl. 429 | Adapter maps 404/429/503 to typed exceptions | `BillingApiAdapterTest > 404/429/503/timeout cases` (4 dedicated tests) | ✅ COMPLIANT |
| Plans response cached for 5 minutes | Repeated requests within TTL → 1 upstream call | `BillingApiAdapterTest > "should serve repeated requests within the TTL from cache..."` | ✅ COMPLIANT |
| Plans response cached for 5 minutes | Request after TTL expires triggers fresh call | `BillingApiAdapterTest > "should trigger a fresh upstream call on every request when the cache..."` | ✅ COMPLIANT |
| Plans response cached for 5 minutes | Failed upstream call does not populate the cache | `BillingApiAdapterTest > "should throw and leave the cache empty when the upstream call fails on a cold cache"` | ✅ COMPLIANT |
| Plans response cached for 5 minutes | Expired entry is not served when refresh fails | `BillingApiAdapterTest > "should NOT serve an expired entry when the refresh fails — it must throw instead"` | ✅ COMPLIANT |

**`virtual-lesson-view` (MODIFIED)**
| Requirement | Scenario | Test | Result |
|---|---|---|---|
| BFF-assembled sample view on subscription denial | Non-entitled visitor sees sample view, no stream leak | `VirtualLearningViewIntegrationTest > nonEntitledPremiumLessonRequest_shouldRenderSampleWithNoStreamLeak` | ✅ COMPLIANT |
| BFF-assembled sample view on subscription denial | Subscription CTA links to the plans page | same test, updated to assert `href="/plans"` | ✅ COMPLIANT |

**Compliance summary**: 16/16 scenarios compliant (10/10 requirements).

### Correctness (Static Evidence — source-read, not inferred from test names)

| Requirement | Status | Notes |
|---|---|---|
| No-stale-on-failure cache | ✅ Implemented | Read `BillingApiAdapter.getPlans()`/`fetchPlans()` directly: `cache.set(...)` (line 81) is reached only after `fetchPlans()` (line 80) returns normally; `fetchPlans()`'s catch blocks (lines 110-118) all `throw`, never touch `cache`; the existing `Entry` is left completely untouched on any exception path. |
| Single-flight double-checked locking | ✅ Implemented | `AtomicReference<Entry> cache` read outside the lock (line 66); `ReentrantLock refreshLock.lock()` (line 71); re-check `cache.get()` inside the lock (line 75); upstream call (`fetchPlans()`, line 80) happens while holding the lock; `finally { refreshLock.unlock(); }` (line 83-85). Matches design's interface exactly. |
| No Authorization header ever sent | ✅ Implemented | `BillingApiClient.getPlans()` has no token parameter; `BillingApiAdapter` has zero occurrences of `header(` or `Authorization` in the whole file (grep-confirmed) — structurally impossible to send one. |
| 429/5xx → single `ServiceUnavailableException`, no dedicated 429 type | ✅ Implemented | `mapErrorStatus`/`mapHttpException` both branch `status.value() == 429 \|\| status.is5xxServerError()` into the same `ServiceUnavailableException`; `BillingApiClient` declares only `NotFoundException` and `ServiceUnavailableException`, no `PlanRateLimitedException` exists anywhere in the module. |
| Exact-path security matcher | ✅ Implemented | `BffSecurityConfig`: `.requestMatchers(HttpMethod.GET, "/plans").permitAll()` — no `*`, placed after the existing course/lesson entries and before `anyRequest().authenticated()`. Cross-checked against `VirtualLearningSecurityIntegrationTest`'s 3 plans cases (200 for exact path, still-authenticated for `/plans/x` and `POST /plans`). |
| Featured badge without reordering | ✅ Implemented | `plans.html` uses a single `th:each="plan : ${plans}"` over the model attribute with no `th:each` comparator, no separate featured/non-featured loop, no sort — structurally nothing exists in the template that could reorder the list; the badge is a sibling `th:if` inside the same iteration. Reinforced (not solely proven) by `featuredPlanNotFirst_shouldShowBadgeWithoutReordering`. |
| #170 CTA cutover | ✅ Implemented | `lesson.html`'s `${sample}` branch: `<a th:href="@{/plans}">Ver planes disponibles</a>` — a real Thymeleaf link expression, not a hardcoded string. `LessonView.Sample` record (application/usecase/LessonView.java) still has exactly the same 3 fields (`course`, `lesson`, `nav`) as #170 — no `plansUrl` field added; javadoc explicitly documents the link lives in the template only. |
| Two wiring bugs from PR 5 apply notes | ✅ Fixed | `AbstractTestcontainersConfig.testProperties()`: `registry.add("menta.billing.base-url", ...)` now present (was missing, would have hit the real `localhost:8081` default); `registry.add("menta.billing.cache-ttl", () -> "PT0S")` now present (prevents the 5-minute-TTL bean from leaking one integration test's stub into the next test method on the same cached Spring context). |
| PlanSummary DTO trims `courses` | ✅ Implemented | `PlanSummary` record has exactly 7 fields (`id, name, description, price, currency, durationDays, featured`), javadoc cites the trim rationale; adapter's private `PlanWire` record likewise omits `courses`, so it is dropped via Jackson's default ignore-unknown-properties behavior. |

### Coherence (Design)
| Decision | Followed? | Notes |
|---|---|---|
| Cache lives in the adapter, hand-rolled, single-entry (`AtomicReference` + `ReentrantLock`) | ✅ Yes | Matches design's interface listing exactly, including field names (`cache`, `refreshLock`) and the `Entry` record shape. |
| No stale-on-failure, strict D6 | ✅ Yes | Corrected design decision genuinely implemented, not left at the earlier "stale-if-error" draft. |
| 429 reuses `ServiceUnavailableException`, no new type | ✅ Yes | No `PlanRateLimitedException` type exists anywhere. |
| DTO trims `courses`, `LessonStream`-style javadoc convention | ✅ Yes | Javadoc present and matches the cited convention. |
| `GET /plans`, no wildcard | ✅ Yes | Exact matcher confirmed in `BffSecurityConfig`. |
| `GetPlansViewUseCase` thin pass-through, no try/catch | ✅ Yes | `GetPlansViewUseCaseImpl` delegates directly; `GetPlansViewUseCaseImplTest` asserts exception propagation untranslated. |
| `PlansController` mirrors `CourseDetailController`, uncaught `@ResponseStatus` exceptions | ✅ Yes | No try/catch in `PlansController`; both port exceptions carry `@ResponseStatus`. |

### Issues Found

**CRITICAL**: None

**WARNING**: None

**SUGGESTION**:
- None of substance. `evidence_revision` in the machine-readable envelope above is a synthetic placeholder digest (not a real SHA-256 of a specific artifact) since no single canonical "evidence bundle" file exists for this hybrid Engram+OpenSpec-file project; `test_output_hash`/`build_output_hash` are the real SHA-256 of the full captured `./gradlew :bff:test check --rerun-tasks` console log and are authoritative.

### Verdict
**PASS**

All 37 tasks complete and code-verified (not just tasks.md-trusted); all 10 spec requirements / 16 scenarios across the 3 specs are implemented and covered by passing tests; the single most load-bearing behavior (no-stale-on-failure cache) is proven correct by direct control-flow inspection, not just by the test's name; both real wiring bugs reported during PR 5 apply are genuinely fixed in test configuration; a forced full-monorepo `./gradlew :bff:test check --rerun-tasks` run shows 2069 tests, 0 failures, 0 errors (2 pre-existing unrelated skips), BUILD SUCCESSFUL. No gaps or undocumented deviations found against proposal/design/specs.

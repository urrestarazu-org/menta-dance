# Design: BFF Plans View

## Technical Approach

A third BFF outbound integration cloned from `VirtualApiClient`/`VirtualApiAdapter`
(D3): `BillingApiClient` port with interface-scoped exceptions, `BillingApiAdapter`
holding a single-entry TTL cache in front of `GET /api/v1/billing/plans`,
`BillingApiProperties` (`menta.billing.*`), and a third qualified
`billingApiWebClient` bean. `GetPlansViewUseCase` is a thin pass-through;
`PlansController` mirrors `CourseDetailController` (flat `@Controller`, uncaught
`@ResponseStatus` exceptions falling through to `/error` → `error.html`, D6).
`lesson.html`'s sample branch gets a `th:href="@{/plans}"` literal (D2).

## Architecture Decisions

### Decision: cache lives in the adapter, hand-rolled, single-entry

| Option | Tradeoff | Verdict |
|---|---|---|
| Hand-rolled `AtomicReference<Entry>` + `ReentrantLock` in `BillingApiAdapter` | ~30 lines, no new dependency, constructible in a plain WireMock test | **Chosen** |
| `@Cacheable` + `CacheManager` | Needs `@EnableCaching` (BFF-global surface); Spring's default map cache has **no TTL**; proxy-based, so the plain-constructor adapter test convention breaks | Rejected |
| Caffeine | New dependency whose value (keyed eviction, size bounds) is unused — the cache holds exactly one unkeyed entry; stale-on-failure still needs hand-written plumbing | Rejected |
| Use-case level | Puts a stateful, time-aware component in `application`, which `BffArchitectureTest` keeps framework-free and where every class today is stateless | Rejected |

The cache exists to protect an HTTP concern (billing's `remoteAddr`-keyed limiter,
#195), so it belongs on the infrastructure side of the port. The port contract stays
"give me the plans".

**Thundering herd**: single-flight. Read the `AtomicReference`; if fresh, return.
Otherwise take the lock, re-check (double-checked), then call upstream **while
holding the lock**. Concurrent cold-cache visitors block for at most the 5s
`timeout` instead of each firing an upstream call — which is precisely the failure
mode D1 exists to prevent.

**No stale-on-failure — an expired entry is never served.** An entry is only
ever replaced by a `200`, and an upstream failure always propagates, whether the
cache is cold or holds an expired entry. Billing downtime therefore always
renders the shared error view, exactly as D6 states.

The first draft of this design served a stale entry on upstream failure, on the
argument that five-minute-old prices beat an error page. The orchestrator put
that back to the user, because it changed D6's answer for the *common* case: a
production process is warm almost always, so "serve stale" would have been the
rule and "error view" the exception — the reverse of what was decided. The
deciding problem was that the fallback had **no upper bound on staleness**: with
billing down for hours, the page would keep serving hours-old prices as current,
silently, until the process restarted. The user chose strict D6.

The cost is accepted knowingly: a 30-second billing blip takes down the plans
page even when a minute-old valid response is sitting in memory. The gain is a
page that is either correct or honestly broken, never quietly wrong about
prices.

If a future slice wants resilience here, the shape to reach for is a *bounded*
stale window (serve stale up to some multiple of the TTL, then fall through to
the error view) — not the unbounded version rejected here.

### Decision: 429 reuses the `ServiceUnavailableException` shape, no new type

`BillingApiClient.ServiceUnavailableException` (`@ResponseStatus(503)`, optional
`retryAfterSeconds`, mirroring `VirtualApiClient`'s) covers 429 and 5xx alike.
Rationale: the visitor outcome is identical (D6); 429 here means "upstream refused
*the BFF*", not "this visitor asked too often", so surfacing 429 to the browser
would blame the wrong party — 503 is the honest status; and with D1's cache a 429
is nearly unreachable, and unreachable-in-practice states earn less machinery, not
more. `Retry-After` is parsed and kept internal, never rendered. A dedicated
`PlanRateLimitedException` was rejected: no consumer would branch on it.
Exceptions stay **interface-scoped** per house convention — `VirtualApiClient`'s
are not reused across ports.

### Decision: the BFF DTO trims `courses`

`PlanSummary` binds `id, name, description, price, currency, durationDays,
featured` only. `courses` is dropped with an explicit javadoc citing the
`LessonStream` precedent (trim + say why), not the `CourseDetail` mirror-byte-for-byte
one: nothing on this page renders it, and a plan-detail page (out of scope) would
need its own `PlanDetail` anyway. Jackson ignores unknown properties by default,
so no annotation is needed.

Price formatting stays in the template — `application` holds no presentation
strings today, and a preformatted string would bake a locale into the DTO.

### Decision: `GET /plans`, no wildcard

`.requestMatchers(HttpMethod.GET, "/plans").permitAll()`, placed after the
`/courses/*/lessons/*` entry and immediately before `anyRequest().authenticated()`.
No `*` at all: a future `/plans/{id}` stays authenticated by default, so this entry
widens access to exactly one route and one verb.

## Data Flow

    Visitor ─GET /plans─→ PlansController ──→ GetPlansViewUseCase ──→ BillingApiClient
                                │                                          │
                                │                              BillingApiAdapter
                                │                                    │
                                │                          fresh? ───┴─── yes ─→ cached List<PlanSummary>
                                │                            no
                                │                             ↓ (lock, double-check)
                                │                     GET /api/v1/billing/plans  ── 200 ─→ store + return
                                │                             │
                                │                    404/429/5xx/timeout
                                │                             ↓
                                ↓                    NotFound/ServiceUnavailable
                          plans.html                         ↓ (uncaught, @ResponseStatus)
                                                       /error → error.html
                                             (always — an expired entry is never served)

## File Changes

| File | Action | Description |
|---|---|---|
| `bff/.../application/port/out/BillingApiClient.java` | Create | `List<PlanSummary> getPlans()` + nested `NotFoundException` (404), `ServiceUnavailableException` (503, `retryAfterSeconds`) |
| `bff/.../application/dto/PlanSummary.java` | Create | Trimmed plan record (no `courses`) |
| `bff/.../application/usecase/GetPlansViewUseCase{,Impl}.java` | Create | Pass-through returning `List<PlanSummary>` |
| `bff/.../infrastructure/adapter/BillingApiAdapter.java` | Create | WebClient call, status mapping, single-entry TTL cache, private `PlanListWireResponse` |
| `bff/.../infrastructure/config/BillingApiProperties.java` | Create | `menta.billing`: `baseUrl` (`@NotBlank`), `timeout` = 5s, `cacheTtl` = 5m (D4) |
| `bff/.../infrastructure/config/WebClientConfig.java` | Modify | 3rd bean `billingApiWebClient()` |
| `bff/.../infrastructure/config/UseCaseConfig.java` | Modify | `getPlansViewUseCase(BillingApiClient)` bean |
| `bff/.../infrastructure/config/BffSecurityConfig.java` | Modify | `permitAll` `GET /plans` |
| `bff/.../infrastructure/web/controller/PlansController.java` | Create | `@GetMapping("/plans")` → model `plans` → `"plans"` |
| `bff/src/main/resources/templates/plans.html` | Create | Flat `lang="es"` list |
| `bff/src/main/resources/templates/lesson.html` | Modify | Sample CTA → `th:href="@{/plans}"` |
| `bff/src/main/resources/application.yml` | Modify | `menta.billing.{base-url,timeout,cache-ttl}` |
| `bff/.../application/usecase/LessonView.java` | Modify | `Sample` javadoc: #177 resolved via template route, field still absent (D2) |

## Interfaces / Contracts

```java
// BillingApiAdapter — cache core (TTL from properties, no Clock injection)
private record Entry(List<PlanSummary> plans, Instant expiresAt) {}
private final AtomicReference<Entry> cache = new AtomicReference<>();
private final ReentrantLock refreshLock = new ReentrantLock();

public List<PlanSummary> getPlans() {
    Entry current = cache.get();
    if (current != null && Instant.now().isBefore(current.expiresAt())) return current.plans();
    refreshLock.lock();                        // single-flight: collapses the herd
    try {
        Entry latest = cache.get();
        if (latest != null && Instant.now().isBefore(latest.expiresAt())) return latest.plans();
        // An upstream failure propagates whether the cache is cold or expired:
        // an expired entry is never served (D6, strict). The entry is replaced
        // only by a 200, so a failure also leaves any old value untouched
        // rather than caching the failure.
        List<PlanSummary> fresh = fetchPlans();
        cache.set(new Entry(fresh, Instant.now().plus(properties.getCacheTtl())));
        return fresh;
    } finally { refreshLock.unlock(); }
}
```

Wire envelope: `{"plans":[{...}]}` → private `record PlanListWireResponse(List<PlanWire> plans)`.
Status mapping: `404 → NotFoundException`; `429 || 5xx → ServiceUnavailableException`
(parsing `Retry-After`); anything else → generic `RuntimeException`, as both
existing adapters do.

Template copy (Spanish, no fragments):

```html
<span th:text="${#numbers.formatDecimal(plan.price(), 1, 'POINT', 2, 'COMMA')} + ' ' + ${plan.currency()}">0,00 ARS</span>
<span th:text="'cada ' + ${plan.durationDays()} + (${plan.durationDays()} == 1 ? ' día' : ' días')">cada 30 días</span>
<span th:if="${plan.featured()}">Destacado</span>   <!-- badge only, no reordering (D5) -->
```

## Testing Strategy

Cache determinism comes from `cacheTtl` being a property, not from a clock or a
sleep: tests construct the adapter directly (the `VirtualApiAdapterTest` pattern)
with `Duration.ofMinutes(5)` to prove caching and `Duration.ZERO` to force the
expired branch on every call. A `Clock` parameter was considered and rejected —
it adds a constructor arg and a bean for a branch `Duration.ZERO` already reaches.

| Layer | What | Approach |
|---|---|---|
| Adapter (`BillingApiAdapterTest`, `@WireMockTest`) | 200 → mapped list, `courses` ignored without error | `stubFor` + `assertThat` |
| Adapter | **No `Authorization` header ever sent** (endpoint is public) | `verify(getRequestedFor(...).withoutHeader("Authorization"))` |
| Adapter | 404 → `NotFoundException`; 429 → `ServiceUnavailableException` (+`Retry-After` captured); 503 → same; timeout → same | `assertThatThrownBy` |
| Adapter | **TTL=5m: two calls → `verify(1, getRequestedFor(...))`** | WireMock request counting |
| Adapter | **TTL=0: two calls → `verify(2, ...)`** (expiry refreshes) | idem |
| Adapter | **Expired entry is never served**: 200 then 503 with TTL=0 → the second call throws, it does NOT return the first payload | re-stub mid-test |
| Adapter | **Cold-cache failure throws** (D6 path) | 503 as first call |
| Adapter | Single-flight: N concurrent cold calls → `verify(1, ...)` | `ExecutorService` + `CountDownLatch`, WireMock fixed delay |
| Use case (`GetPlansViewUseCaseImplTest`) | Delegates; exceptions propagate untranslated | Mockito |
| Controller (`PlansControllerTest`) | Model attribute `plans`, view name `plans` | plain unit test, mocked use case |
| Integration (`BillingPlansSecurityIntegrationTest`) | Anonymous `GET /plans` → 200; `GET /plans/x` → login redirect; `POST /plans` → not permitted; `/dashboard` still redirects (no widening) | extends `VirtualLearningSecurityIntegrationTest` pattern (Testcontainers + WireMock) |
| Integration (`BillingPlansViewIntegrationTest`) | Rendered HTML contains name/price/currency/duration + "Destacado" only on featured; 503 upstream → error view with no problem-detail leak | idem |
| Integration (existing lesson tests) | Sample branch now asserts `href="/plans"` instead of the dead-end copy | update `VirtualLearningViewIntegrationTest` |
| Architecture | `BffArchitectureTest` unchanged and still green (cache lives in `infrastructure`) | existing ArchUnit run |

## Threat Matrix

Routing is the only listed boundary touched: one new GET route with no path
variable, no shell/subprocess/VCS/executable-classification/process integration.
Covered by the both-directions security test above (permit exactly `GET /plans`,
widen nothing else). Remaining rows: N/A.

## Migration / Rollout

No migration. The cache is process-local and empty on boot; a deploy is a full
invalidation. `menta.billing.cache-ttl` is the runtime lever if staleness or
rate-limit pressure needs adjusting without a code change.

## Open Questions

None. The one that was open — whether an upstream failure may serve an expired
cache entry — was put to the user and answered: it may not. See "No
stale-on-failure" above for the full reasoning and for the bounded-window shape
a future slice should reach for if it wants resilience here.

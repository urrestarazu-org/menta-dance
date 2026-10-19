# Design: BFF Virtual Learning View

## Technical Approach

Mirror `AuthApiClient`/`AuthApiAdapter` exactly: one outbound port `VirtualApiClient`
(`application/port/out`), one `VirtualApiAdapter` (`infrastructure/adapter`) using the
existing blocking `WebClient.exchange().block()` style, annotation-free records in
`application/dto`, two `@Controller`s, two flat Thymeleaf templates. No new dependency,
no new layer, no new pattern. Realizes the three specs in `specs/`.

## Architecture Decisions

| # | Choice | Rejected | Rationale |
|---|---|---|---|
| A | One `VirtualApiClient` with 3 methods (course detail, lesson, stream) | Two ports split by upstream module | Catalog and virtual live in the same `api:app` process behind one base URL; splitting doubles config and test surface for zero isolation gain. Issue asks for one port. |
| B | Two-level denial model: adapter throws `ForbiddenException`; `GetLessonViewUseCase` converts it to sealed `LessonView` (`Playable` \| `Sample`) | (a) exception all the way to the controller; (b) sealed result at the port | (a) forces `try/catch` around an *expected* outcome — one forgotten catch leaks a stacktrace instead of the gate. (b) contradicts the written `virtual-api-integration` spec and the `AuthApiClient` precedent. The split keeps the adapter a faithful transport translator while the application says "denial is renderable". Java 21 pattern matching makes the controller total: the compiler, not review, guarantees the `Sample` branch exists. |
| C | Second **qualified** bean `virtualApiWebClient` + `VirtualApiProperties` (`menta.api`: `base-url`, `timeout` = 5s), mirroring `AuthProperties` | Reuse the single `webClient` bean | `menta.auth.base-url` is env-overridable (`AUTH_API_BASE_URL`); sharing it would silently retarget catalog calls in any environment that splits the two. **Gotcha**: `WebClientConfig` exposes one *unqualified* `WebClient`; a second bean makes the type ambiguous. `AuthApiAdapter` keeps resolving by parameter-name match (`webClient`), so it is untouched, but `VirtualApiAdapter` MUST declare an explicit constructor with `@Qualifier("virtualApiWebClient")` — Lombok `@RequiredArgsConstructor` cannot carry the qualifier. |
| D | Explicit nullable `String accessToken` parameter, read by the controller from `request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE)` | `RequestContextHolder` lookup inside the adapter | Controllers are infrastructure, so importing the filter constant is legal; the adapter stays a plain constructible object for `@WireMockTest`. A hidden thread-local would make "token present/absent" untestable without a servlet context. Precedent: `LoginCommand.clientAddress` already passes request-derived state explicitly. |
| E | Sequential, catalog first, fail fast | Parallel calls | Catalog failure makes the page unrenderable either way, so parallelism buys nothing and would need reactive composition foreign to this blocking codebase. |
| F | Keep templates flat — no fragments, no layout dialect | Extract shared chrome now | Six templates, no CSS/static assets; the shared part is ~8 lines of `<head>`. A new dependency plus a refactor of 4 working templates inflates the diff with no behavior change. Revisit at first shared nav/styling or 8 templates. |
| G | `requestMatchers(HttpMethod.GET, "/courses/*", "/courses/*/lessons/*")` | `/courses/**`, method-agnostic matcher | Single `*` does not cross `/`, so future subpaths stay closed by default; GET-only keeps mutating verbs authenticated. |

## Data Flow

Lesson request (`GET /courses/{c}/lessons/{l}`):

    Browser ──> LessonViewController ──(accessToken?)──> GetLessonViewUseCase
                                                              │
                                    1. getCourseDetail(c)  ───┤ (never Authorization)
                                       404/503 ──> ForbiddenOf/NotFound ──> error view
                                                              │
                                    2. getLesson(l, token) ───┤ (Bearer iff token != null)
                                       403 ──────> LessonView.Sample  (title/duration from step 1,
                                       404/503 ──> error view          nav derived from module order,
                                                              │        BFF-owned plans CTA)
                                       200 (free OR entitled)
                                                      └─> 3. getStream(l, token) ──> Playable
                                                            503 ──> error view

`Sample` has no `videoId`/`streamUrl` field at all — the no-leak guarantee is structural,
not a `th:if`.

### A granted lesson always needs the stream call, free ones included

A `200` from the lesson endpoint means access was granted, not that a playable URL came
back with it. The detail response never carries one, and `videoId: null` on a free lesson
does not mean "no stream exists" — it means the detail endpoint withholds the raw Bunny
id. `GetPublicLessonStreamUseCaseImpl` is explicit about this: it is "Authorized for any
caller (free OR premium-with-entitlement)" (line 37), and its *Free lesson policy* note
(lines 43-46) states that an anonymous visitor is allowed a stream for a free lesson
precisely because they have no entitlement to deny.

So step 3 runs for both granted shapes. Branching it on `videoId` would leave `Playable`
with an empty `streamUrl` for every free lesson — a broken player on exactly the content
the funnel leads with. The only branch that skips the stream call is the `403`, which
produces `Sample`.

For an anonymous caller on a free lesson the token is `null`, so the stream call goes out
with no `Authorization` header and is still authorized upstream.

## Interfaces / Contracts

```java
public interface VirtualApiClient {
    CourseDetail getCourseDetail(String courseId);                // D2: never authenticated
    LessonDetail getLesson(String lessonId, String accessToken);  // accessToken nullable
    LessonStream getStream(String lessonId, String accessToken);  // accessToken nullable

    class NotFoundException extends RuntimeException { /* 404 */ }
    class ForbiddenException extends RuntimeException { /* bare 403, body never parsed */ }
    class ServiceUnavailableException extends RuntimeException { /* 5xx, timeout */ }
}

public sealed interface LessonView {
    record Playable(CourseDetail course, LessonDetail lesson, String streamUrl, Nav nav)
            implements LessonView {}
    // No plansUrl component: the CTA is a message, not a link, until #177 lands.
    record Sample(CourseDetail course, LessonSummary lesson, Nav nav)
            implements LessonView {}
}
```

DTO records carry no Jackson annotations (component names match the JSON), keeping
`application` framework-light per ADR-0021 and `BffArchitectureTest`.

## File Changes

| File | Action | Description |
|---|---|---|
| `bff/.../application/port/out/VirtualApiClient.java` | Create | Port + 3 nested exceptions |
| `bff/.../application/dto/{CourseDetail,LessonDetail,LessonStream,LessonSummary,Nav}.java` | Create | Annotation-free response records |
| `bff/.../application/usecase/{GetCourseDetailUseCase,GetLessonViewUseCase}(+Impl).java` | Create | Orchestrate the 1–3 call flow; own `LessonView` |
| `bff/.../application/usecase/LessonView.java` | Create | Sealed result |
| `bff/.../infrastructure/adapter/VirtualApiAdapter.java` | Create | WebClient, `@Qualifier`, conditional Bearer, status mapping |
| `bff/.../infrastructure/config/VirtualApiProperties.java` | Create | `menta.api` binding, mirrors `AuthProperties` |
| `bff/.../infrastructure/config/WebClientConfig.java` | Modify | Add `virtualApiWebClient` bean |
| `bff/.../infrastructure/config/UseCaseConfig.java` | Modify | Wire the two use-case beans |
| `bff/.../infrastructure/config/BffSecurityConfig.java` | Modify | GET `permitAll` for the two routes (D-G) |
| `bff/.../infrastructure/web/controller/{CourseDetailController,LessonViewController}.java` | Create | Routes, attribute read, `LessonView` switch |
| `bff/src/main/resources/templates/{course-detail,lesson}.html` | Create | Flat, `lang="es"`, matching `dashboard.html` |
| `bff/src/main/resources/application.yml` | Modify | Add `menta.api.timeout`; `base-url` already present |

## Testing Strategy

| Layer | What to test | Approach |
|---|---|---|
| Unit — adapter | 200/403/404/503 mapping per method; **Bearer present when token non-null; header entirely absent when null**; catalog call never sends `Authorization` | `@WireMockTest`, plain constructor, `verify(getRequestedFor(...).withoutHeader("Authorization"))` — mirrors `AuthApiAdapterTest` |
| Unit — use case | 403 → `Sample` with course-derived title/duration/nav and **no stream call**; **both granted shapes — free (`videoId: null`) and entitled — call the stream**, so a free lesson never yields an empty `streamUrl`; catalog failure short-circuits | Mockito on `VirtualApiClient` |
| Unit — controller | Attribute present/absent → token forwarded/omitted; each `LessonView` variant → correct template | Standalone MockMvc |
| Integration — **security regression** | Anonymous GET on both new routes returns 200, **no redirect to `/login`**; `/dashboard` and an unmapped path still redirect; POST to a new route is not permitted | `BaseIntegrationTest` + WireMock stubs on the singleton server (add `menta.api.base-url` to `AbstractTestcontainersConfig.testProperties`) |
| Integration — full stack | Anonymous free lesson renders player; anonymous premium renders sample with **no `videoId`/stream URL in the response body**; 404/503 render the error view | Same, asserting on rendered content |
| Architecture | Layering unchanged | `BffArchitectureTest` (existing) |

## Threat Matrix

| Boundary | Applicability | Note |
|---|---|---|
| Documentation-like paths | N/A | No file classification or execution |
| Git repository selection | N/A | No VCS automation |
| Commit state | N/A | No VCS automation |
| Push state | N/A | No VCS automation |
| PR commands | N/A | No PR automation |

No shell, subprocess, or VCS boundary exists. The real boundary here is HTTP
authorization, covered by the named security-regression tests above; those rows carry
into `tasks.md` as RED tests unchanged.

## Migration / Rollout

No migration. Purely additive; revert by removing the new files and the two
`BffSecurityConfig`/`WebClientConfig` edits.

## Resolved Questions

### The subscription CTA carries no link — issue #177 owns the destination

Verified: `bff/src/main` contains no reference to plans, billing, or subscription at all.
#29 (US-BILLING-001) shipped only the API. So the CTA has no page to point at.

The `Sample` view renders the subscription message **without a navigable button**, and
`Sample` therefore carries no `plansUrl` component. Issue
[#177](https://github.com/urrestarazu-org/menta-dance/issues/177) (US-BILLING-013, BFF
plans page) was opened to own that destination and will wire the CTA when it lands.

A placeholder constant was rejected. A dead link on the one screen whose job is to convert
is worse than an honest message, and it would repeat precisely the debt pattern this
change exists to correct: #56 was closed asserting a BFF capability that did not exist,
which is why #170 had to be filed. Hiding the same gap inside a `PLANS_URL` constant would
recreate it one layer down.

### The misleading anonymous filter test is fixed here, in scope

`TokenRefreshFilterTest.shouldSkipFilterForAnonymousAuthentication` builds a mock
`Authentication` stubbed `isAuthenticated() → false`. A real `AnonymousAuthenticationToken`
returns `true`, so that test exercises a state anonymous traffic never produces and proves
nothing about the case its name claims.

What actually protects anonymous requests is filter ordering: `BffSecurityConfig:102`
registers `TokenRefreshFilter` via `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`,
ahead of `AnonymousAuthenticationFilter`, so `SecurityContextHolder`'s authentication is
still `null` at line 74 and the guard's first clause short-circuits.

This is normally a harmless latency, and the design first listed it as a follow-up. It is
brought in scope because **this change is the first to route anonymous traffic through that
filter on a real page** — until now no BFF route was anonymous-reachable. If the ordering
is ever changed, every visitor opening a course lands on `/login?sessionExpired=true`: the
funnel this change exists to build, broken, with a green test suite reporting otherwise.
The cost is a corrected unit test using a real `AnonymousAuthenticationToken` alongside the
integration regression tier, which is small next to shipping a feature whose central
guarantee is watched by a test that cannot fail.

# Tasks: BFF Virtual Learning View (#170, US-VIRTUAL-009)

## Review Workload Forecast

Grounded in this repo's own closest precedents, not abstract guesses:

| Precedent file | Lines | What it tells us |
|---|---|---|
| `AuthApiClient.java` (port, 3 methods + 3 exceptions) | 98 | `VirtualApiClient` (3 methods + 3 exceptions) is comparable, ~110 |
| `AuthApiAdapter.java` (3 methods, WebClient, status mapping) | 270 | `VirtualApiAdapter` has the same method count plus a conditional-Bearer branch per call |
| `AuthApiAdapterTest.java` (WireMock, 20 cases) | 510 | The closest adapter-test precedent in this repo already exceeds 400 alone |
| `AuthProperties.java` | 35 | `VirtualApiProperties` is a straight mirror |
| `CatalogCourseDetailResponse.java` (nested module/lesson/stats records) | 156 | `CourseDetail` DTO (mirrors this exact wire shape) is comparable |
| `dashboard.html` (minimal, no loop) | 18 | `course-detail.html`/`lesson.html` render module/lesson loops + player/gate branches — expect 3-5x |
| Archived `virtual-lesson-progress` Slice 2 | forecast 605–650, **actual 1904 (2.4x over)** | Strict-TDD volume across 4 layers is chronically underestimated here — every number below already carries a safety margin, do not shave it |

**400-line budget risk: High.** Full-change estimate (production + tests, all layers): **~2,600–3,300 lines**. One PR is not viable. The natural capability seam
(`virtual-api-integration` vs. the two view capabilities) is itself too large for
a single PR once `AuthApiAdapterTest`'s 510-line precedent is accounted for, so
`virtual-api-integration` splits by upstream call (catalog vs. lesson/stream),
and `virtual-lesson-view` splits by layer (use case vs. web), mirroring how the
archived change's own Slice 3 was split into 3a/3b mid-flight — this time the
split is planned upfront instead of discovered under budget pressure.

```text
Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High
```

`auto-chain` means the orchestrator proceeds straight to PR1a with the chain
strategy below — no user prompt required before `sdd-apply`. `stacked-to-main`
is interpreted per this repo's Git Flow as: each slice branches off `develop`
after its predecessor has merged, and merges back into `develop` in order —
never an unmerged tracker branch.

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1a | `VirtualApiClient` port + `CourseDetail` DTO + `VirtualApiProperties` + WebClient bean + adapter's catalog call only (lesson/stream methods stubbed) | PR 1a (~450–550) | `./gradlew :bff:test --tests "*VirtualApiAdapterTest*"` | `@WireMockTest`, no Spring context | Revert the new port/adapter/config files; nothing else references them yet |
| 1b | Adapter's lesson + stream calls, conditional Bearer, `LessonDetail`/`LessonStream` DTOs | PR 1b (~480–560) | `./gradlew :bff:test --tests "*VirtualApiAdapterTest*"` | `@WireMockTest`, no Spring context | Revert the two method bodies back to the 1a stub; catalog path unaffected |
| 2 | `virtual-course-detail-view`: use case, controller, template, `permitAll` for `/courses/*`, `TokenRefreshFilterTest` fix, security regression (both halves) | PR 2 (~550–700) | `./gradlew :bff:test --tests "*CourseDetail*" --tests "*TokenRefreshFilterTest*"` | `BaseIntegrationTest` (Testcontainers Redis + WireMock) | Revert controller/template/matcher; filter-test fix is independently correct and safe to keep even alone |
| 3a | `virtual-lesson-view` core: `LessonSummary`/`Nav`/`LessonView`, `GetLessonViewUseCase`(+Impl), its unit tests | PR 3a (~380–460) | `./gradlew :bff:test --tests "*GetLessonViewUseCase*"` | Mockito only, no Spring context | Revert the use case; nothing calls it yet |
| 3b | `LessonViewController`, template, `permitAll` for the lesson route, security-regression extension, full-stack rendering tests | PR 3b (~550–680) | `./gradlew :bff:test --tests "*LessonView*"` | `BaseIntegrationTest` (Testcontainers Redis + WireMock) | Revert controller/template/matcher; PR 3a's use case is unaffected |

## Slice Ordering Rationale

1a → 1b → 2 → 3a → 3b is a hard dependency chain: the adapter must exist before
any use case calls it (1a before 1b, both before 2/3a); the course-detail use
case's `CourseDetail`-derived lesson summaries feed the lesson view's sample
gate, so 2 must precede 3a; 3b depends on 3a's `LessonView`/`GetLessonViewUseCase`.
Unlike the archived change, there is no anonymous-access ordering hazard here:
each `permitAll` matcher lands in the *same* PR as the controller it protects
(PR 2, PR 3b), so no route is ever anonymously reachable before it is real, and
no route is ever real before its matcher — there is no window to close.

Branches (each cut from `develop` after its predecessor merges):
`feature/bff-virtual-learning-view-api-catalog` (1a) →
`feature/bff-virtual-learning-view-api-lesson-stream` (1b) →
`feature/bff-virtual-learning-view-course-detail` (2) →
`feature/bff-virtual-learning-view-lesson-core` (3a) →
`feature/bff-virtual-learning-view-lesson-web` (3b).

## PR 1a — API integration: catalog call

**Branch**: `feature/bff-virtual-learning-view-api-catalog` off `develop`.

- [x] 1a.1 Create `application/port/out/VirtualApiClient.java`: interface with all 3 methods (`getCourseDetail`, `getLesson`, `getStream`) and 3 nested exceptions (`NotFoundException`, `ForbiddenException`, `ServiceUnavailableException`), mirroring `AuthApiClient`.
- [x] 1a.2 Create `application/dto/CourseDetail.java` (record, nested `Module`/`Lesson`/`Stats`) mirroring `CatalogCourseDetailResponse`'s exact JSON shape (`courseId, title, description, thumbnailUrl, category, level, isPremium, modules[], stats`).
- [x] 1a.3 Create `infrastructure/config/VirtualApiProperties.java` (`@ConfigurationProperties(prefix = "menta.api")`, `baseUrl` + `timeout` default 5s), mirroring `AuthProperties`. Note: `menta.api.base-url` already exists in `application.yml`; add `menta.api.timeout: 5s`.
- [x] 1a.4 Modify `infrastructure/config/WebClientConfig.java`: add qualified `@Bean @Qualifier("virtualApiWebClient") WebClient virtualApiWebClient(VirtualApiProperties)`. Existing unqualified `webClient()` bean and `AuthApiAdapter`'s parameter-name resolution stay untouched (design decision C).
- [x] 1a.5 RED: `infrastructure/adapter/VirtualApiAdapterTest.java` (`@WireMockTest`, plain constructor, mirrors `AuthApiAdapterTest`) — `getCourseDetail`: 200 maps to `CourseDetail`; 404 → `NotFoundException`; 503 → `ServiceUnavailableException` preserving `Retry-After`; the outbound request **never** carries `Authorization`, verified via `withoutHeader("Authorization")` even when a token is passed to the adapter's constructor context (there is none — the method takes no token, proving it structurally).
- [x] 1a.6 Verify RED: `./gradlew :bff:test --tests "*VirtualApiAdapterTest*"` fails on missing `VirtualApiAdapter` class, not a typo.
- [x] 1a.7 GREEN: create `infrastructure/adapter/VirtualApiAdapter.java` with an explicit constructor (`@Qualifier("virtualApiWebClient")`, no Lombok — design decision C) implementing `getCourseDetail` fully; `getLesson`/`getStream` throw `UnsupportedOperationException("implemented in PR 1b")` as an explicit, temporary, compiling placeholder — nothing calls them yet.
- [x] 1a.8 Verify GREEN: re-run `*VirtualApiAdapterTest*` — all catalog cases pass.
- [x] 1a.9 Run `./gradlew :bff:test check` to confirm the slice is green (ArchUnit, Checkstyle) before opening PR 1a.

## PR 1b — API integration: lesson + stream calls, conditional Bearer

**Branch**: `feature/bff-virtual-learning-view-api-lesson-stream` off `develop` (cut after PR 1a merges).

- [ ] 1b.1 Create `application/dto/LessonDetail.java` (record with nested `CourseRef`/`ModuleRef`, nullable `videoId`, `Nav navigation`) mirroring `PublicLessonFreeResponse`/`PublicLessonPremiumAccessibleResponse`'s `lesson`+`navigation` shape.
- [ ] 1b.2 Create `application/dto/Nav.java` (record `previousLesson`/`nextLesson`, each a small `lessonId`/`title` ref) mirroring `PublicLessonNavigationDto`.
- [ ] 1b.3 Create `application/dto/LessonStream.java` (record: `url`, `expiresAt`) mirroring `PublicLessonStreamResponse.StreamBlock`, trimmed to what the player needs.
- [ ] 1b.4 RED: extend `VirtualApiAdapterTest.java` — `getLesson`: 200 (free, `videoId: null`) and 200 (premium, `videoId` present) both map correctly; 403 → `ForbiddenException` **without attempting to parse a body**; 404/503 map as in 1a.5; `Authorization: Bearer <token>` present iff a non-null token is passed, absent (not blank) when `null`. `getStream`: identical status/Bearer matrix, **including a 200 case with a `null` token** (free-lesson anonymous stream, per design's "granted lesson always needs the stream call" — the test must NOT skip this case just because the lesson is free).
- [ ] 1b.5 Verify RED: run `*VirtualApiAdapterTest*` — new cases fail against the 1a `UnsupportedOperationException` stub, not a typo.
- [ ] 1b.6 GREEN: implement `getLesson`/`getStream` in `VirtualApiAdapter.java`, replacing the stubs; conditional header via `headers -> { if (token != null) headers.setBearerAuth(token); }`.
- [ ] 1b.7 Verify GREEN: full `*VirtualApiAdapterTest*` suite green, catalog cases from 1a still pass (no `Authorization` leak regression).
- [ ] 1b.8 Run `./gradlew :bff:test check` before opening PR 1b.

## PR 2 — `virtual-course-detail-view`

**Branch**: `feature/bff-virtual-learning-view-course-detail` off `develop` (cut after PR 1b merges).

- [ ] 2.1 RED: `application/usecase/GetCourseDetailUseCaseImplTest.java` (Mockito on `VirtualApiClient`) — success returns the `CourseDetail`; `NotFoundException`/`ServiceUnavailableException` propagate untranslated (the controller decides the view).
- [ ] 2.2 GREEN: create `application/usecase/GetCourseDetailUseCase.java` + `GetCourseDetailUseCaseImpl.java` (thin pass-through, no branching — course detail is access-agnostic, D2).
- [ ] 2.3 RED: `infrastructure/web/controller/CourseDetailControllerTest.java` (standalone MockMvc, no security context) — `GET /courses/{courseId}` renders `course-detail` view with the module/lesson tree and free/premium markers; lesson links target `/courses/{courseId}/lessons/{lessonId}` (D1); `NotFoundException`/`ServiceUnavailableException` propagate to Spring Boot's default error handling (no upstream body/status leaked — verified via `@ResponseStatus` on both exception types, added in this task).
- [ ] 2.4 GREEN: create `infrastructure/web/controller/CourseDetailController.java`; annotate `VirtualApiClient.NotFoundException`/`ServiceUnavailableException` with `@ResponseStatus(NOT_FOUND)`/`@ResponseStatus(SERVICE_UNAVAILABLE)` so they resolve through the existing `/error` → `error.html` path with no new exception-handler class.
- [ ] 2.5 Create `templates/course-detail.html` (flat, `lang="es"`, matches `dashboard.html`'s style): title/description, module list, per-lesson free/restricted marker, aggregate stats, lesson links carrying `courseId`+`lessonId`.
- [ ] 2.6 Wire `GetCourseDetailUseCase` bean in `infrastructure/config/UseCaseConfig.java`.
- [ ] 2.7 Modify `infrastructure/config/BffSecurityConfig.java`: add `.requestMatchers(HttpMethod.GET, "/courses/*").permitAll()` immediately before the `anyRequest().authenticated()` line (design decision G — single `*`, GET-only).
- [ ] 2.8 RED: fix `TokenRefreshFilterTest.shouldSkipFilterForAnonymousAuthentication` — replace the mocked `Authentication` stubbed `isAuthenticated() → false` with a real `AnonymousAuthenticationToken("key", "anonymousUser", authorities)`, which returns `true`. Confirm this new version fails against the current filter logic's naive `!authentication.isAuthenticated()` check reasoning stated in the test name (it must fail for the right reason: the filter's first clause alone does not explain why anonymous traffic is skipped — filter *ordering* does).
- [ ] 2.9 Verify RED, then GREEN: no production change is needed — `BffSecurityConfig:102`'s `addFilterBefore(tokenRefreshFilter, UsernamePasswordAuthenticationFilter.class)` already keeps `SecurityContextHolder`'s authentication `null` ahead of `AnonymousAuthenticationFilter`, so the filter's existing `authentication == null` branch (not `!isAuthenticated()`) is what actually protects anonymous requests. Add an assertion/comment in the test making this explicit so a future reordering breaks it loudly.
- [ ] 2.10 Create `infrastructure/integration/VirtualLearningSecurityIntegrationTest.java` (extends `BaseIntegrationTest`) — RED, then GREEN via 2.7:
  - anonymous `GET /courses/{courseId}` returns 200, no redirect to `/login`;
  - `GET /dashboard` (and one unmapped path) still redirect anonymous callers to `/login`, exactly as before this change;
  - a path that merely resembles `/courses/*` (e.g. `/courses/1/extra`) is NOT permitted (still authenticated-only).
- [ ] 2.11 Modify `infrastructure/integration/AbstractTestcontainersConfig.java`: register `registry.add("menta.api.base-url", ...)` pointing at the singleton `WIRE_MOCK_SERVER`, alongside the existing `menta.auth.base-url` registration.
- [ ] 2.12 RED, then GREEN: `infrastructure/integration/VirtualLearningViewIntegrationTest.java` — anonymous course-detail request against a WireMock-stubbed catalog response renders free/premium markers correctly; catalog 404/503 render the error view with no problem-detail body in the response.
- [ ] 2.13 Run `./gradlew :bff:test check` before opening PR 2.

## PR 3a — `virtual-lesson-view` core (use case)

**Branch**: `feature/bff-virtual-learning-view-lesson-core` off `develop` (cut after PR 2 merges).

- [ ] 3a.1 Create `application/dto/LessonSummary.java` (record: `lessonId, title, duration, isFree, order`), derived from a `CourseDetail.Lesson` entry.
- [ ] 3a.2 Create `application/usecase/LessonView.java`: sealed interface, `Playable(CourseDetail, LessonDetail, String streamUrl, Nav)` and `Sample(CourseDetail, LessonSummary, Nav)` — `Sample` carries **no `plansUrl` field of any kind** (the CTA is a message, not a link, until #177).
- [ ] 3a.3 RED: `application/usecase/GetLessonViewUseCaseImplTest.java` (Mockito on `VirtualApiClient`) covering, in this order:
  - catalog failure (`NotFoundException`/`ServiceUnavailableException`) short-circuits before any lesson/stream call;
  - lesson `403` → `Sample` built from the course-detail lesson summary, nav derived from course-detail module/lesson order, **zero calls to `getStream`**;
  - lesson `200` with `isFree: true` and `videoId: null` (free, granted) → `Playable`, **`getStream` IS called** with a `null` token, `streamUrl` is non-empty (this is the corrected case — a free lesson must never end up with an empty `streamUrl`);
  - lesson `200` with a `videoId` (entitled) → `Playable`, `getStream` called with the caller's token;
  - lesson `200` → nav comes from the lesson endpoint's own navigation block, not course order;
  - `getStream` `503` after a granted lesson → exception propagates (controller renders the error view, no partial player).
- [ ] 3a.4 Verify RED: run `./gradlew :bff:test --tests "*GetLessonViewUseCase*"` — fails on the missing class, not a typo.
- [ ] 3a.5 GREEN: create `application/usecase/GetLessonViewUseCase.java` + `GetLessonViewUseCaseImpl.java` implementing the 1→2→(3) flow from design's Data Flow section.
- [ ] 3a.6 Verify GREEN: full `GetLessonViewUseCaseImplTest` suite green.
- [ ] 3a.7 Run `./gradlew :bff:test check` before opening PR 3a.

## PR 3b — `virtual-lesson-view` web + security

**Branch**: `feature/bff-virtual-learning-view-lesson-web` off `develop` (cut after PR 3a merges).

- [ ] 3b.1 RED: `infrastructure/web/controller/LessonViewControllerTest.java` (standalone MockMvc) — attribute present on the request → token forwarded to the use case; absent → `null` forwarded; a Java 21 `switch` on `LessonView` renders `lesson` for `Playable` and `lesson-sample` (or a `th:if` branch in one template, per decision F) for `Sample`, exhaustively (compiler-enforced, no `default` branch needed).
- [ ] 3b.2 GREEN: create `infrastructure/web/controller/LessonViewController.java` reading `TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE` from the request.
- [ ] 3b.3 Create `templates/lesson.html` (flat, `lang="es"`): player branch (video/stream URL) and sample-gate branch (title, duration, subscription message with **no navigable link**) in the same file via `th:if`/`th:unless` on view type (decision F — no fragments yet); prev/next links from `Nav` in both branches.
- [ ] 3b.4 Wire `GetLessonViewUseCase` bean in `infrastructure/config/UseCaseConfig.java`.
- [ ] 3b.5 Modify `infrastructure/config/BffSecurityConfig.java`: add `.requestMatchers(HttpMethod.GET, "/courses/*/lessons/*").permitAll()` next to the PR 2 matcher (decision G).
- [ ] 3b.6 RED, then GREEN: extend `VirtualLearningSecurityIntegrationTest.java` — anonymous `GET /courses/{c}/lessons/{l}` returns 200, no redirect; `POST /courses/{c}/lessons/{l}` is NOT permitted (method-scoped matcher, decision G); a path resembling the lesson route but with an extra segment is still authenticated-only.
- [ ] 3b.7 RED, then GREEN: extend `VirtualLearningViewIntegrationTest.java`:
  - anonymous free lesson renders a player with a non-empty stream URL;
  - anonymous/non-entitled premium lesson renders the sample view with **no `videoId` and no stream URL anywhere in the response body**, and WireMock verifies **zero requests** to the stream endpoint for this case;
  - entitled student navigates prev/next via the upstream `navigation` block;
  - a `503` from `/stream` after a granted `200` lesson renders the error view, never a partial player.
- [ ] 3b.8 Run `./gradlew :bff:test check` (full suite, ArchUnit, Checkstyle, `BffArchitectureTest`) before opening PR 3b.

## Key Learnings

1. `AuthApiAdapterTest` (510 lines, 3 methods) is this repo's only precedent for a WebClient adapter test file, and it already exceeds the 400-line budget alone — any new 3-method adapter test should be forecast against it, not against the smaller port/DTO files.
2. A `200` from the lesson endpoint never implies a playable URL: `videoId: null` on a free lesson means the detail endpoint withholds the raw id, not that no stream exists, so `getStream` must be called for every granted lesson, free included — this must be an explicit RED case, not inferred from `videoId`.
3. `BffSecurityConfig:102`'s filter ordering (`TokenRefreshFilter` before `UsernamePasswordAuthenticationFilter`, hence before `AnonymousAuthenticationFilter`) is what actually protects anonymous requests, not the filter's `isAuthenticated()` check — a test stubbing `isAuthenticated() → false` on a mock never exercises the real `AnonymousAuthenticationToken`, which returns `true`.
4. Security-regression coverage needs both directions verified in the same integration test class: new routes reachable anonymously, AND every previously-protected route still redirects — a matcher that is only tested positively can fail open silently.
5. Splitting a large adapter across two PRs by upstream call (catalog vs. lesson/stream), with the untested methods stubbed via an explicit `UnsupportedOperationException`, keeps each slice's production code fully tested without shipping an incomplete interface implementation.

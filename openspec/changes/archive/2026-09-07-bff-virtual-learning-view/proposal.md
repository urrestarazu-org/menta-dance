# Proposal: BFF Virtual Learning View (Course Detail + Lesson Player)

## Intent

The BFF has zero integration with `api:virtual`/catalog today, so visitors and students cannot browse a course or watch a lesson through the web app — the commercial funnel from catalog to subscription cannot exist yet. This change gives the BFF a course-detail page and a lesson-player page, reachable anonymously, that reuse the API's already-public catalog/lesson endpoints. It unblocks the funnel (browse → free preview → subscription CTA) and lays the port/adapter groundwork #58 (course progress / "Continuar") needs next.

## Scope

### In Scope
- `VirtualApiClient` outbound port + `VirtualApiAdapter` (WebClient), mirroring `AuthApiClient`/`AuthApiAdapter`, hitting `api:app`'s `/api/v1/catalog/courses/{courseId}` and `api:virtual`'s `/api/v1/virtual/lessons/{lessonId}` on the same host (`menta.api.base-url`).
- Conditional Bearer-token propagation: read `TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE` when present, omit `Authorization` for anonymous requests.
- Course-detail view (modules, lessons, free/premium markers) and lesson-player view (video for entitled/free content).
- Subscription-gated "sample" lesson view assembled from course-detail data already held by the BFF (see Approach) — never from the lesson API's 403.
- Previous/next lesson navigation using the lesson endpoint's `navigation` block.
- Graceful degradation to the existing error view on upstream 404/403/503.
- `BffSecurityConfig` `permitAll` additions for the new course/lesson routes.

### Out of Scope
- Lesson progress tracking and the "Continuar"/"Comenzar" affordance (#52, #58).
- Any change to `api:virtual`'s dead 403 path (`PublicLessonRequiresSubscriptionResponse` never constructed) — treated as a fixed constraint, not fixed here.
- Stream endpoint quality-selection UI beyond wiring the returned HLS URL into a player.

## Capabilities

### New Capabilities
- `virtual-course-detail-view`: anonymous-accessible BFF page rendering a course's modules/lessons/stats from the catalog API.
- `virtual-lesson-view`: BFF lesson page with player for free/entitled content, BFF-assembled subscription gate for restricted content, and prev/next navigation.
- `virtual-api-integration`: outbound port/adapter + conditional token propagation for calling the catalog and lesson upstream endpoints.

### Modified Capabilities
- None (BFF has no prior virtual-facing capability).

## Approach

Mirror the Auth adapter pattern. Both routes are nested under the course (D1), so every request
carries the `courseId` and the BFF never depends on cached state or prior navigation.

On a course-detail request, `VirtualApiAdapter` calls the public catalog endpoint and the view
renders the module/lesson tree with free/premium markers.

On a lesson request, the BFF fetches the same course detail (for the lesson summary and ordering)
and calls the lesson endpoint. A 200 renders the player — free content without a `videoId`,
entitled content with one. A 403 renders the subscription gate built from the course-detail lesson
summary plus a BFF-owned plans link; the denial body is never parsed, because there is none.

Both routes go into `permitAll`. `TokenRefreshFilter`'s request attribute conditionally adds
`Authorization` on lesson and stream calls only (D2).

## Affected Areas

| Area | Impact | Description |
|------|--------|--------------|
| `bff/.../application/port/out/VirtualApiClient.java` | New | Outbound port |
| `bff/.../infrastructure/adapter/VirtualApiAdapter.java` | New | WebClient adapter, conditional Bearer header |
| `bff/.../infrastructure/config/WebClientConfig.java` | Modified | New/shared bean bound to `menta.api.base-url` |
| `bff/.../infrastructure/config/BffSecurityConfig.java` | Modified | `permitAll` for course/lesson routes |
| `bff/.../infrastructure/web/controller/*` | New | Course-detail + lesson controllers |
| `bff/src/main/resources/templates/*.html` | New | Minimal Thymeleaf views, no fragments |
| `bff/src/test/**` | New | WireMock adapter tests, Testcontainers integration tests |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| New anonymous routes widen attack surface | Low | Upstream endpoints are already public; no new data exposed |
| Token-propagation logic is first-of-its-kind, untested territory | Med | Dedicated adapter tests for present/absent token cases |
| Deep-linked lesson with no cached course-detail context | Resolved | Nested route carries `courseId` (D1); no cached state needed |
| Extra catalog fetch on every lesson view | Low | Public, uncached-but-cheap read; revisit only if measured |
| Design forecloses #58's progress/"Continuar" needs | Low | Keep course-detail controller extensible for a future progress call |

## Rollback Plan

All changes are additive (new routes, new port/adapter, new templates). Revert by removing the new controllers/templates/adapter and the `permitAll` entries; no schema or migration involved.

## Dependencies

- #47 (course detail), #56/#48 (lesson detail), US-VIRTUAL-004 (stream) — all already merged and public.

## Success Criteria

- [ ] Anonymous visitor can open a course-detail page and see free vs. premium lessons.
- [ ] Anonymous/authenticated visitor can play a free lesson.
- [ ] Non-entitled user hitting a premium lesson sees a BFF-assembled sample view with subscription CTA, no video/stream data leaked.
- [ ] Entitled user can play a premium lesson and navigate prev/next.
- [ ] Upstream 404/403/503 degrade to the existing error view.

## Resolved questions

The proposal's three open questions were closed by the orchestrator against verified source, not left as assumptions.

### D1 — Routes are nested: `/courses/{courseId}` and `/courses/{courseId}/lessons/{lessonId}`

This resolves the deep-link problem structurally rather than with a fallback.

`PublicLessonDetailDto` does carry a `course` block (`PublicCourseDto`), so a lesson response
identifies its own course — **but only on a 200**. The subscription denial is a bare `403`
`application/problem+json` with no body (`GetPublicLessonUseCaseImpl.java:145-147` throws before
any response is assembled). So on a flat `/lessons/{lessonId}` route, a visitor who deep-links a
premium lesson gives the BFF a 403 and nothing else: no title, no duration, and no `courseId` with
which to go look them up. The sample gate would be unrenderable in exactly the case it exists for.

Carrying `courseId` in the path removes the dependency on any cached state or prior navigation:
the BFF always fetches the public, unauthenticated catalog course detail, finds the lesson summary
in its module tree (`{lessonId, title, duration, isFree, order}`), and renders the gate from that
whatever the lesson endpoint answers. It also supplies module/lesson ordering for prev/next when
the lesson endpoint's own `navigation` block is unavailable on a denial.

Paths stay English (matching `/dashboard`, `/login`); UI copy stays Spanish.

### D2 — The Bearer token is sent on lesson and stream calls only, never on the catalog call

Catalog course detail is access-agnostic: `isPremium` is a property of the course, not of the
caller, and `CatalogController` is annotated `@PublicCatalogEndpoint` with no `Authentication`
parameter — a token would change nothing in the response and would widen token exposure for no
gain. Access is decided only on the lesson and stream endpoints, so only those carry the header,
and only when `TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE` is present.

### D3 — No existing capability spec is modified

`openspec/specs/` already holds six capabilities (`virtual`, `virtual-lesson-progress`,
`billing-subscriptions`, `physical-checkin`, `local-bunny-net`, `local-mercadopago`). The relevant
one, `virtual/spec.md`, declares BFF UI explicitly out of its own scope (line 192) while stating a
constraint this change must honor rather than change (line 19: "The BFF and every browser client
MUST NOT make this authorization decision"). The three capabilities introduced here are therefore
genuinely new, and the BFF's role stays that of a renderer of the API's decisions.

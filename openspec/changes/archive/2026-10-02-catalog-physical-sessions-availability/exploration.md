# Exploration: catalog-physical-sessions-availability

Issue #107 (follow-up of #95). Source: Engram `sdd/catalog-physical-sessions-availability/explore` (obs #1697).

## Current State

- Public endpoints (`api/app/.../catalog`): `GET /api/v1/catalog/courses` (list) and `GET /api/v1/catalog/courses/{courseId}` (detail). `SecurityConfig` permits both. No rate limit, no Cache-Control/ETag/`@Cacheable` anywhere in `api/app` (US-VIRTUAL-002 asks 60 rpm/IP + 5 min cache; never implemented).
- Errors via `@RestControllerAdvice(annotations = PublicCatalogEndpoint)` `CatalogExceptionHandler`: `CourseNotFoundException` -> 404 `COURSE_NOT_FOUND` (problem+json); `CatalogUpstreamException` -> 503 `CATALOG_DEGRADED` + `Retry-After: 30`.
- LIST: `CatalogCompositionService.listCourses` calls `physicalPort.listCourses(null, 500)` then `virtualPort.listPublished(null, 500)` via `callPort()`; any `RuntimeException` from either module -> `CatalogUpstreamException` -> whole response 503 (no partial degradation). Items are `CatalogCourseResponse(courseId, modality, title, level, physical|virtual)`. `PhysicalCatalogBlock = (professorName, dayOfWeek, startTime, capacity)`. No global Jackson `NON_NULL`: nulls serialize (physical items carry `"virtual": null`).
- **Key finding: the detail is virtual-only today.** Since #47, `CatalogController.get` -> `compositionService.getCourseDetail` -> `virtualPort.findPublishedDetailById` only, returning `CatalogCourseDetailResponse` (virtual shape: description, thumbnailUrl, category, isPremium, modules, stats; no `modality` field). A physical `courseId` answers 404 `COURSE_NOT_FOUND` by explicit #47 trade-off, pinned by tests. `CatalogCompositionService.getCourse` (modality-agnostic, both ports, tie-break physical) is dead code (no production caller, no test; a test comment claiming otherwise is stale). Issue #107 therefore cannot be "add sessions to the existing physical detail": the physical detail must be created.
- OpenAPI drift: `api/openapi/catalog-v1.yaml` still declares detail 200 as `CatalogCourseResponse` and has no schema for the virtual detail (#47 never updated it). `physical-v1.yaml` already references "listSessions composed in GET /api/v1/catalog/courses/{courseId}" as the public read.

## Physical Port

- `PhysicalCourseAvailabilityPort`: `listCourses(afterCursor, pageSize)`, `findActiveById(courseId) -> Optional<PhysicalCourseSummary>` (empty if missing OR inactive, non-enumeration), `listSessions(String courseId, Instant from, Instant to) -> List<PhysicalSessionAvailability(sessionId, courseId, scheduledAt, capacity, assignedSpots, activeCapacityHolds, availableSpots)>`. Javadoc says `[from, to)` half-open.
- Implementation -> `PhysicalSessionRepository.findScheduled` -> native SQL `findScheduledWithAvailability`: `SCHEDULED` only (cancelled never), `ORDER BY scheduled_at ASC`, correlated `COUNT(*)` subqueries: `assignedSpots` = rows in `physical_capacity_assignments`; `activeCapacityHolds` = holds with `converted_at IS NULL AND expires_at > now`. `availableSpots = max(0, capacity - assigned - activeHolds)`. Single query (no N+1); indexes exist (`course_id, scheduled_at`), assignments(`session_id`), holds(`session_id, expires_at`). No row limit, no max range, no `scheduled_at >= now` filter.
- **Pre-existing bug / contract mismatch**: SQL uses `scheduled_at BETWEEN :from AND :to` (inclusive both ends), while the port javadoc and the billing adapter assume an exclusive `to`. A session exactly at `to` is returned. Not covered by any test (`PhysicalSessionRepositoryAdapterTest` mocks the JPA repository).
- Consistency with booking: hold invariant in `PhysicalCapacityHoldWriter.assertHold` is `assigned + activeHolds + 1 > capacity`, the same arithmetic as `availableSpots`, so catalog availability agrees with what a checkout can claim. `CoveragePlanner` (billing) only plans sessions with `scheduledAt >= reference instant`, so "bookable" = not yet started.
- Billing's `api/app` `PhysicalCourseAvailabilityAdapter` maps `listSessions` to `ScheduledSessionSnapshot(sessionId, scheduledAt, availableSpots)` and relies on SCHEDULED-only filtering. Tests: `PhysicalCourseAvailabilityIntegrationTest` (MySQL/Testcontainers: live count with 2 assignments + 1 active + 1 expired hold = 17; empty range; cancelled excluded), `PhysicalCourseAvailabilityPortImplTest`, `PhysicalSessionRepositoryAdapterTest`, `PhysicalCourseQuoteIntegrationTest`.
- Time: session instants are created with `ZoneOffset.UTC` from course `startTime` (create/batch use cases), while attendance history uses `physical.attendance.zone-id` (default `America/Argentina/Buenos_Aires`). Integration tests seed a 19:00 course at 22:00Z. Possible pre-existing UTC-vs-local semantic gap; a window defined as instants (`from = now`) avoids DST issues. No `java.time.Clock` bean in `api/app`; each module has its own clock port, so the composition service needs an injected clock for deterministic tests.

## Consumers and Contracts

- The BFF consumes `GET /api/v1/catalog/courses/{id}` only for the VIRTUAL detail (`VirtualApiAdapter`, record `CourseDetail` mirrors `CatalogCourseDetailResponse`; default Spring codecs ignore unknown properties). Android has no catalog usage. Nobody deserializes `PhysicalCatalogBlock`/`CatalogCourseResponse`. Risk: a physical id returning 200 with a non-virtual body would break the BFF course page instead of 404.
- Files to keep in sync: `api/openapi/catalog-v1.yaml`; `bruno/API - Direct/catalog/{Get Course, List Courses, Get Course - Not Found}.bru` ("physical returns 404" today); `docs/07-CATALOG-API.md` (already promises sessions/capacity for physical); `docs/user-stories/US-PHYSICAL-003.md` (specifies `?from=&to=` and exposing `assignedSpots` + `activeCapacityHolds`, with professor/recurrence/`quoteEndpoint`); ADR-0037 (says parallel lookup, code is sequential); `api/openapi/physical-v1.yaml`. No openspec capability for the catalog exists.
- Tests pinning the opposite behavior (must be inverted): `CatalogControllerTest.get_returns_404_when_physical_alone_is_resolved_for_the_id`, `CatalogCompositionServiceTest.getCourseDetail_physical_only_id_still_throws_CourseNotFoundException`, `CatalogIntegrationTest.get_a_physical_only_course_answers_the_same_404_as_a_missing_one`. `get_does_not_ask_the_physical_port_for_a_virtual_detail` stays valid only with virtual-first resolution. `CatalogAccessMocksIntegrationTestBase` (MySQL, mocked Redis ports) is reusable.
- Coverage: `api:app` flat LINE floor 0.90 (real 97.4%). Physical layer gates (95/90) only matter if physical module code changes.

## Approaches

| # | Approach | Pros | Cons | Effort |
|---|----------|------|------|--------|
| A (recommended) | Polymorphic detail: virtual-first, then physical via `findActiveById` + `listSessions`; new physical detail record with a detail-only block carrying `sessions` | Matches docs/US intent; list unchanged; additive for virtual; no `sessions: null` noise | Polymorphic controller return type (sealed interface / `oneOf`); BFF edge case | Medium (~550-650 lines) |
| B | Nullable `sessions` on existing `PhysicalCatalogBlock`, revive `getCourse` | Smallest (~400 lines) | List serializes `sessions: null` or tempts N queries; mixes list/detail concerns | Low-Medium |
| C | Separate `GET /catalog/courses/{id}/sessions` | No polymorphism, no BFF risk | Physical base detail still 404; two calls; diverges from docs/07 and US-PHYSICAL-003 | Medium (~500) |
| D (rejected) | Sessions in the LIST | - | One `listSessions` per physical course (up to 500) on a public endpoint | - |

## Design Points (recommendations)

- Window: `from = now` (not started, aligned with booking), `to = from + 30 days`, length as a property. Instants avoid TZ/DST issues.
- Started sessions excluded by `from = now`; cancelled already excluded by SQL; sold-out kept with `availableSpots = 0`.
- Hard cap (e.g. 100 sessions, ascending) enforced in composition, since the port has no limit.
- Publish `sessionId`, `scheduledAt`, `capacity`, `availableSpots`; do not publish `assignedSpots` / `activeCapacityHolds`.
- `listSessions` failure -> 503 `CATALOG_DEGRADED` (all-or-nothing, consistent with current convention).
- Caching / rate limit out of scope.
- Fix `BETWEEN` -> half-open `>= :from AND < :to` at the source with an integration test, in its own small slice.
- Dead `getCourse()` to be removed.

## Risks

- BFF edge case for physical ids; inclusive `BETWEEN` bound; stale OpenAPI; 404-pinning tests must be inverted deliberately; US-PHYSICAL-003 conflicts with not exposing internal counts; pre-existing UTC-vs-academy-zone semantics; unbounded session count without a cap; no rate limit on a public live-availability endpoint (two correlated COUNTs per session row, indexed).

## Resolution

Product questions were answered in the business round and locked as D1-D8 in Engram `sdd/catalog-physical-sessions-availability/decisions` (obs #1698).

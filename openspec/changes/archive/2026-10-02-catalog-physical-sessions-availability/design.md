# Design: Physical Course Detail with Session Availability (#107)

## Technical Approach

Exploration approach A, decisions D1-D8 (final). `CatalogCompositionService.getCourseDetail` returns a sealed `CatalogCourseDetail`. It tries the virtual lookup first, which keeps the virtual path byte-identical. On a miss it calls `findActiveById` on the physical port, then `listSessions(id, now, now + window)` through `callPort()`, and truncates the result to 100 sessions. The change adds no cross-module edge: `api:app` keeps using only Physical's existing `application.port.in.PhysicalCourseAvailabilityPort` and its `application.dto` records. Separately, the public session query moves from `BETWEEN` to half-open `[from, to)`.

```
GET /catalog/courses/{id} -> CatalogController.get -> getCourseDetail(id)
  1 lookup("virtual",  findPublishedDetailById) present -> CatalogCourseDetailResponse (200)
       IAE -> empty | other RuntimeException -> 503 (physical never consulted)
  2 lookup("physical", findActiveById)          empty/IAE -> 404 COURSE_NOT_FOUND
       other RuntimeException -> 503
  3 callPort("physical", listSessions(id, now, now+windowDays)) any RuntimeException -> 503
       -> limit(100) -> CatalogPhysicalCourseDetailResponse (200)
```

## Architecture Decisions

| # | Topic | Choice | Rejected | Rationale |
|---|---|---|---|---|
| A1 | Detail type | `public sealed interface CatalogCourseDetail permits CatalogCourseDetailResponse, CatalogPhysicalCourseDetailResponse` (no members). The controller returns `ResponseEntity<CatalogCourseDetail>` | `ResponseEntity<?>`/`Object`; reviving `CatalogCourseResponse` (approach B) | The type stays exhaustive and compile-checked. The virtual record only gains `implements` and keeps its name, so the diff stays small |
| A2 | Serialization | No `@JsonTypeInfo`, no discriminator. Spring's Jackson converter calls `forType` only for container/`Optional` types, so a plain interface serializes through its runtime record class. A controller test pins both shapes and the absence of any type property | A `@JsonTypeInfo` property; adding `modality` to the virtual body | The virtual body must not change (success criterion). The physical body already carries `modality: "PHYSICAL"`, like list items |
| A3 | OpenAPI | 200 = `oneOf: [CatalogVirtualCourseDetail, CatalogPhysicalCourseDetail]`, no `discriminator`. The two branches are mutually exclusive through `required` (virtual: `modules`, `stats`, `isPremium`; physical: `modality` with `const: PHYSICAL`, `physical`) | `anyOf`; a discriminator | This mirrors the wire exactly. It also fixes the #47 drift: no virtual-detail schema exists today |
| A4 | Physical records | One file `CatalogPhysicalCourseDetailResponse(String courseId, CourseModality modality, String title, String level, PhysicalDetailBlock physical)` with nested `PhysicalDetailBlock(String professorName, String dayOfWeek, String startTime, int capacity, List<PublicSession> sessions)` and `PublicSession(String sessionId, String scheduledAt, int capacity, int availableSpots)`. `scheduledAt` passes through the port's `Instant.toString()` (ISO-8601, `Z`). The mapping is explicit, so `courseId`/`assignedSpots`/`activeCapacityHolds` of `PhysicalSessionAvailability` are dropped. There is no price field anywhere | Adding `sessions` to `PhysicalCatalogBlock`; an `Instant`-typed field | The list block stays untouched and keeps no `sessions: null` noise. A `String` passthrough does not depend on the `ObjectMapper` date settings and matches the existing `startTime` strings. Nested records follow the `CatalogCourseDetailResponse` precedent |
| A5 | Resolution / status | Virtual first through `lookup`, then physical `lookup`, then `callPort` for sessions (flow above). Malformed ids still answer 404 on both branches. An INACTIVE physical course is empty and answers 404 | Physical first; partial degradation (`sessions: null`) | Existing virtual tests and failure semantics are unchanged (a virtual failure is still 503 without a physical call). D5 requires all-or-nothing |
| A6 | Window property | `catalog.physical.sessions.window-days`, `int`, default 30 (yml `${CATALOG_PHYSICAL_SESSIONS_WINDOW_DAYS:30}` plus an identical `@Value` default on the service constructor). The constructor rejects `< 1` or `> 90` (`MAX_SESSION_WINDOW_DAYS`) with `IllegalArgumentException`, which fails startup | A `@ConfigurationProperties` class (`api:app` has no `@ConfigurationPropertiesScan`); a `Duration` property (format ambiguity) | Follows the `api:app`/billing `@Value` + yml env-var pattern (`auth.outbox.retention-days`). The 90-day ceiling bounds the DB cost on a public endpoint |
| A7 | Clock | A new `CatalogConfiguration` (`@Configuration`, `com.menta.app.catalog`) declares `@Bean java.time.Clock catalogClock()` returning `Clock.systemUTC()`, injected by type. Tests use `Clock.fixed` | Physical's `application.port.out.Clock` (another module's internal out-port); inline `Instant.now()`; test-only constructor overloads | It is the first `java.time.Clock` bean in the context (`rg`-verified), so injection by type is unambiguous. It **must not** be named `clock`: `VirtualConfiguration.clock()` already owns that bean name and Boot forbids overriding (same reason as `physicalClock`) |
| A8 | Cap | `private static final int MAX_DETAIL_SESSIONS = 100` next to `LIST_PAGE_SIZE`; `stream().limit(100)` over the port's ascending result | Configurable; sorting in composition | D7 fixes the value. S1 makes "ascending" part of the port contract and pins it, so truncation keeps the earliest sessions |
| A9 | Dead code | Delete `getCourse()`, `LOG`/SLF4J imports, and the `VirtualCourseSummary` import (`Optional` stays because `lookup` uses it). Rewrite the virtual-only Javadocs on the service, the controller and `CatalogCourseDetailResponse` | Keep `getCourse` | It has no caller. Virtual-first means a cross-modality id collision resolves to virtual silently, which is acceptable because ids are random UUIDs |
| A10 | Bound fix | Only `findScheduledWithAvailability` (line 32): `s.scheduled_at >= :from AND s.scheduled_at < :to`. `findManagedWithAvailability` (line 58) stays inclusive | Fix both | D6 targets the public read. The managed query's only caller is `ListManagedPhysicalSessionsUseCaseImpl` (admin, ±3650-day defaults), and `physical-v1.yaml` documents no half-open contract for it |
| A11 | Billing at `to` | It gets the documented behavior: a session exactly at `periodEndExclusive` (the monthly quote's next-month start, the broad INDIVIDUAL window end, the `CoveragePlanner` lookahead end) is no longer returned. Before the fix it was counted in two adjacent months | Compensating in billing | `PhysicalCourseQuoteIntegrationTest` seeds at `2026-09-01T00:00Z` = `periodStart` (inclusive), so it is unaffected |
| A12 | Test inversion placement | All 3 inversions go in **S2**, including `CatalogIntegrationTest` | Integration inversion in S3 (proposal) | After S2, the real ports resolve the seeded ACTIVE course, so `get_a_physical_only_course_answers_the_same_404_as_a_missing_one` would go red in S2. The proposal's placement is invalid |

## Interfaces / Contracts

```java
public sealed interface CatalogCourseDetail permits CatalogCourseDetailResponse, CatalogPhysicalCourseDetailResponse {}
public CatalogCompositionService(PhysicalCourseAvailabilityPort physicalPort, VirtualCourseCatalogPort virtualPort,
    Clock clock, @Value("${catalog.physical.sessions.window-days:30}") int sessionWindowDays)
public CatalogCourseDetail getCourseDetail(String courseId)
```

Port Javadoc (`listSessions`, S1): "ordered by `scheduledAt` ascending".

## File Changes

| Slice | File | Action |
|---|---|---|
| S1 | `api/physical/.../persistence/repository/PhysicalSessionJpaRepository.java` | Modify: L32 half-open, Javadoc |
| S1 | `api/physical/.../application/port/in/PhysicalCourseAvailabilityPort.java` | Modify: Javadoc ascending order |
| S1 | `api/app/src/test/.../integration/physical/PhysicalCourseAvailabilityIntegrationTest.java` | Modify: +2 tests |
| S2 | `api/app/.../catalog/CatalogCourseDetail.java`, `CatalogPhysicalCourseDetailResponse.java`, `CatalogConfiguration.java` | Create |
| S2 | `CatalogCompositionService.java`, `CatalogController.java`, `CatalogCourseDetailResponse.java` | Modify |
| S2 | `api/app/src/main/resources/application.yml` | Modify: `catalog:` section |
| S2 | `CatalogCompositionServiceTest.java`, `CatalogControllerTest.java`, `integration/catalog/CatalogIntegrationTest.java` | Modify |
| S3 | `CatalogIntegrationTest.java` | Modify: physical HTTP scenarios |
| S3 | `api/openapi/catalog-v1.yaml` | Modify: `oneOf`, 3 new schemas, 3.1-valid nullables |
| S3 | `.github/workflows/pr-develop.yml` | Modify: add `/spec/catalog-v1.yaml` to the redocly lint |
| S3 | `bruno/API - Direct/catalog/Get Course.bru`, new `Get Course - Physical.bru` | Modify/Create |
| S3 | `docs/07-CATALOG-API.md`, `docs/user-stories/US-PHYSICAL-003.md` | Modify: fields, window, no from/to, no internal counts or prices |

## Testing Strategy (Strict TDD, RED first)

| Slice | Test | Notes |
|---|---|---|
| S1 | `a_session_exactly_at_the_upper_bound_is_excluded_and_one_at_the_lower_bound_included` (RED against `BETWEEN`); `sessions_are_listed_in_ascending_scheduled_order` | MySQL Testcontainers. No physical unit-test change: an SQL string edit adds no JaCoCo lines, so the physical 95/90 gates are unaffected |
| S2 unit (composition) | Physical detail mapping (no internal counts); `listSessions(id, now, now+30d)` verified exactly with `Clock.fixed`; custom window; cap 100 from 101; sold-out kept; sessions failure, physical-lookup failure → `CatalogUpstreamException`; malformed id → 404; inactive → 404; virtual failure never consults physical; constructor rejects 0 and 91 | Mockito |
| S2 unit (controller) | Physical JSON shape: `$.modality`, `$.physical.sessions[0]` keys exactly {sessionId, scheduledAt, capacity, availableSpots}; no `assignedSpots`, `activeCapacityHolds`, `virtual` or type property; virtual shape unchanged; sessions failure → 503 + `Retry-After: 30` | MockMvc standalone |
| S2 inversions | `CatalogControllerTest.get_returns_404_when_physical_alone_is_resolved_for_the_id` → `get_returns_the_physical_detail_when_only_physical_resolves_the_id`; `CatalogCompositionServiceTest.getCourseDetail_physical_only_id_still_throws_CourseNotFoundException` → `getCourseDetail_physical_only_id_returns_the_physical_detail` (it stays green by accident today, because the physical mock defaults to empty; invert it deliberately); `CatalogIntegrationTest.get_a_physical_only_course_answers_the_same_404_as_a_missing_one` → `get_a_physical_only_course_returns_its_physical_detail` (status 200, `modality`) | Existing constructor call sites gain `Clock.fixed(...)`, 30. Assertions on the virtual return type use a cast or a pattern match |
| S3 integration | Seeds: started (now-1h), in-window sold-out (capacity 2, 2 assignments → 0 shown), cancelled, now+31d. Assert inclusion, ascending order, exact keys, `scheduledAt` ends in `Z`. Inactive physical course → 404. `cleanUp` deletes assignments → sessions before courses (FK `V7`) | Relative instants with day-scale margins, so the tests stay deterministic without a clock override |
| Gates | `:api:app` flat 0.90 LINE (real 97.4%): new code is fully unit-covered. ArchUnit unchanged and green (no `physical.infrastructure` or `LessonAccessPolicy` dependency) | `./gradlew check` per slice |

## Threat Matrix

N/A: the change adds no routing, shell, subprocess, VCS/PR automation, executable-file classification or process-integration boundary. Public-data exposure is covered by A4 (no internal counts or prices) and pinned by tests.

## Migration / Rollout

No schema or data migration (V0). Revert S3, S2, S1 in that order. Each slice compiles and is green on its own.

## Review Workload Forecast

| Slice | Main | Tests | Contracts/docs | Total |
|---|---|---|---|---|
| S1 | ~6 | ~40 | — | ~46 |
| S2 | ~195 (incl. ~35 deleted) | ~190 | ~5 | ~390 |
| S3 | — | ~90 | ~195 | ~285 |

Total ~720 of the 800 budget. Decision needed before apply: No. Chained PRs recommended: Yes. 400-line budget risk: Medium (S2 sits close to 400).

## Open Questions

- None blocking.
- `findManagedWithAvailability` remains inclusive (A10). This is a candidate follow-up if admin listings must also be half-open.
- If redocly flags pre-existing 3.1 issues in `catalog-v1.yaml` (`nullable` beside `$ref` in the list schema), S3 fixes them in the same file.

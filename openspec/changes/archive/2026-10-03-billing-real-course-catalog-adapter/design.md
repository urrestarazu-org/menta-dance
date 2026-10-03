# Design: Real Course Catalog Adapter for Billing Plans (#108)

## Technical Approach

The single-id `CourseCatalogPort.courseName` is replaced by a batch port that is resolved once per request. Virtual and Physical each gain a batch lookup with the same visibility as their single-id lookups. A new `api:app` adapter composes the two lookups: Virtual first, then Physical only for the ids Virtual did not answer, and a failure in one module degrades only that module's ids. The work ships as three stacked slices (S1 additive modules, S2 billing, S3 wiring). It implements spec `billing-plan-course-names` and decisions D1-D4.

## Architecture Decisions

| # | Decision | Rejected alternatives | Rationale |
|---|---|---|---|
| A1 | Billing port `Map<String,String> courseNames(Collection<String> courseIds)`. The result is immutable and contains only resolved ids, keyed by the exact input string. Empty input returns an empty map with no lookup. Duplicate ids are looked up once. Null, blank and malformed elements are omitted. A null collection throws `NullPointerException`. `courseName(String)` is removed. | Keep the single method beside the batch method; `List<PlanCourseName>` | V0 allows the breaking change. A map makes it O(1) for the resolver to look up a name by its stored id. |
| A2 | `VirtualCourseCatalogPort.findPublishedByIds(Collection<String>)` returns `Map<String, VirtualCourseSummary>`. `PhysicalCourseAvailabilityPort.findActiveByIds(Collection<String>)` returns `Map<String, PhysicalCourseSummary>`. Both maps are keyed by the caller's input id and contain only visible courses (PUBLISHED, or ACTIVE). | A `List` of summaries keyed by `courseId()`; a title-only DTO | `UUID.fromString` accepts non-canonical forms such as uppercase, but `summary.courseId()` is the canonical form. Keying by the input keeps parity with the single-id lookup, which resolves any form it parses. Reusing the summaries means the batch path calls the same `toSummary`, so the mapping matches the single-id path exactly. |
| A3 | Virtual uses a constant 3 queries: `findByIdInAndStatus(ids, PUBLISHED)`, then the existing `countByCourseIdIn` and `aggregateByCourseIdIn` over the hits. The aggregate queries are skipped when nothing matches, as in `findPublished`. All three run in one `@Transactional(readOnly)` repository method. Physical uses 1 query: `findByIdInAndStatus(ids, ACTIVE)`. | A title-only JPQL projection (1 query); looping `findPublishedById` (3N queries) | The aggregates cost 2 constant indexed `IN` queries, which is cheap at catalog scale. A title-only path would add a second visibility code path that could drift from the single-id one. Revisit together with #312 if profiling shows a need. |
| A4 | Malformed-id filtering lives inside each module's batch implementation. Each id goes through `CourseId.of`; an `IllegalArgumentException` (null, blank or non-UUID) skips that id. If no valid id remains, the method returns `Map.of()` without a query, which also avoids an empty `IN ()`. | Filter in the adapter with `UUID.fromString`; let the module throw and catch it per batch | The id format belongs to the module (precedent: `findByIdForAdmin`). One malformed id never fails the batch. The adapter does not need to know the id format. |
| A5 | The adapter treats any `RuntimeException` from a module the same way: it logs one WARN and that module's ids stay unresolved. There is no special case for `IllegalArgumentException`. | Mirror `CatalogCompositionService.lookup()`, where IAE means silent empty | Under A4, a batch call never throws for malformed input, so an IAE is a contract breach that should be visible. The spec's rule "a malformed id is not a module failure" still holds. |
| A6 | WARN format: `log.warn("Course catalog lookup failed; module={}, courseIds={}", module, ids)`. No throwable is logged. | Logging the stack trace | The spec says the WARN carries the module and course ids only. Diagnosability cost is listed under Open Questions. |
| A7 | Add `CourseCatalogPortAdapter` in `com.menta.app.billing` with `@Component`, `@RequiredArgsConstructor` and `@Slf4j`. It injects Virtual's and Physical's `port.in` interfaces. No FQN is needed because no simple names collide in this file. The class is not `@Transactional`. | A hand-written constructor (the pattern in existing `api:app` adapters); placing the adapter in `api:billing` | D4 decided this. Composition belongs in `api:app` (ADR-0037). |
| A8 | `PlanCourseResolver` has two methods. `resolveNames(List<Plan>)` collects distinct ids across all plans with a `LinkedHashSet`, makes one port call, and catches any `RuntimeException`: it logs one WARN and returns an empty map. `toResults(List<PlanCourse>, Map)` maps each course in its plan's own order and uses `names.get(id)`, which gives null when the id is unresolved. | Per-plan resolution (one call per plan); memoizing inside the port | Constant cost per request, as the spec requires. The safety net keeps HTTP 200 when an adapter breaks its contract. |
| A9 | Do not amend the ADR-0037 text. The adapter Javadoc states that it is sequential and virtual-first, like #107. | Rewrite the ADR to say "sequential" | ADR-0037 governs catalog-detail routing, and its divergence from the #107 code predates this change. Rewriting it would widen scope; it is recorded as a follow-up. |
| A10 | Remove the mock from `CatalogAccessMocksIntegrationTestBase` and leave the other inert `@MockBean CourseCatalogPort` declarations, which still override the real bean. | Create a new base for the plans test | The three subclasses of this base move to one new shared cache key. The total number of contexts stays the same, so the 64-context limit is not at risk. |

## Data Flow

    ListPlans/GetPlan (rate limit -> repo) --plans--> PlanCourseResolver.resolveNames
        distinct ids (LinkedHashSet) --empty--> Map.of()  [no call]
        CourseCatalogPort.courseNames(ids) == CourseCatalogPortAdapter
            virtual.findPublishedByIds(all)          --fail--> WARN virtual, {}
            pending = ids - virtualHits; if empty skip
            physical.findActiveByIds(pending)        --fail--> WARN physical, {}
            merge (virtual wins) -> Map.copyOf
        toResults(plan.courses, names) per plan (order preserved)

Transaction caveat: Virtual's and Physical's repository methods are `@Transactional(REQUIRED, readOnly)`. If a caller ever wrapped the plan use cases or the adapter in a transaction, a module exception caught by the adapter would leave the outer transaction marked rollback-only, and the commit would then throw `UnexpectedRollbackException`. The plan use cases have no `Transactional*` decorator (`BillingConfiguration` ~417) and must keep it that way. This is documented in the adapter and resolver Javadoc.

## File Changes (by slice)

**S1: additive Virtual and Physical batch lookups (~340 lines).** Nothing calls the new methods yet, so every module stays green. There are no other implementations or fakes of these interfaces, so adding methods does not break any build.
- Modify Virtual: `port/in/VirtualCourseCatalogPort`, `usecase/VirtualCourseCatalogPortImpl`, `port/out/VirtualCourseRepository` (`findPublishedByIds(Collection<CourseId>)`), `persistence/adapter/VirtualCourseRepositoryAdapter`, `persistence/repository/VirtualCourseJpaRepository`.
- Modify Physical: `port/in/PhysicalCourseAvailabilityPort`, `usecase/PhysicalCourseAvailabilityPortImpl`, `port/out/PhysicalCourseRepository` (`findActiveByIds`), `persistence/adapter/PhysicalCourseRepositoryAdapter`, `persistence/repository/PhysicalCourseJpaRepository`.
- Tests: `VirtualCourseCatalogPortImplTest`, `VirtualCourseRepositoryAdapterTest`, `PhysicalCourseAvailabilityPortImplTest`, `PhysicalCourseRepositoryAdapterTest`. Parity checks go into the existing `api:app` `VirtualCourseCatalogIntegrationTest` and `PhysicalCourseAvailabilityIntegrationTest`, so no new context is created.

**S2: billing batch port and resolver (~290 lines).**
- Modify: `CourseCatalogPort` (A1 and its Javadoc), `PlanCourseResolver` (A8), `ListPlansUseCaseImpl`, `GetPlanUseCaseImpl`.
- `NotImplementedCourseCatalogPort` now implements `courseNames` and still throws `UnsupportedOperationException`. The resolver's safety net still degrades to null, so behavior does not change.
- Tests: create `PlanCourseResolverTest`. Modify `ListPlansUseCaseImplTest` (stubs at lines 86, 102 and 117; stale comment at line 111), `GetPlanUseCaseImplTest` (line 62) and `NotImplementedCourseCatalogPortTest` (line 13).
- `api/app` `BillingPlansIntegrationTest:97` is the only `api:app` stub of `courseName(`. S2 must change it to `when(courseCatalogPort.courseNames(any())).thenReturn(Map.of("course-1","Tango Basico"))`, otherwise `api:app` test compilation breaks. The other ~25 `@MockBean` declarations stub nothing and keep compiling.

**S3: adapter and wiring (~430 lines).**
- Create: `api/app/src/main/java/com/menta/app/billing/CourseCatalogPortAdapter.java` and `CourseCatalogPortAdapterTest.java`.
- Delete: `api/billing/.../infrastructure/catalog/NotImplementedCourseCatalogPort.java` and its test. Both are deleted in the same slice that adds the adapter, so there is never a window with two beans (`NoUniqueBeanDefinitionException`).
- Modify: `api/app/build.gradle.kts` (`compileOnly(libs.lombok)` and `annotationProcessor(libs.lombok)`).
- Modify Javadoc that references the placeholder: `BillingConfiguration:102`, `PhysicalCourseOwnershipPort:16`, `PlanCourseResult:8`.
- Modify tests: `CatalogAccessMocksIntegrationTestBase` (drop the mock), `BillingPlansIntegrationTest` (real seeds), `ArchitectureTest` (add the adapter to the package rule, and assert direct dependencies on Virtual's and Physical's `port.in`).
- Modify docs: `docs/06-BILLING-API.md:52-56` and `api/openapi/billing-v1.yaml:1258-1265` (Spanish).
- `E2eBunnyNetBillingFixture` needs no change; its canonical id will now resolve.

## Interfaces / Contracts

```java
// billing port.out
Map<String, String> courseNames(Collection<String> courseIds);
// virtual port.in
Map<String, VirtualCourseSummary> findPublishedByIds(Collection<String> courseIds);
// physical port.in
Map<String, PhysicalCourseSummary> findActiveByIds(Collection<String> courseIds);
// JPA (both modules)
List<...JpaEntity> findByIdInAndStatus(Collection<UUID> ids, CourseStatus status);
```

## Testing Strategy (Strict TDD)

| Slice | RED first | Then |
|---|---|---|
| S1 | Port-impl unit tests: a malformed id is skipped without a query; input with no valid ids returns `{}` without a query; result keyed by the input string (an uppercase UUID); dedupe | Repository adapter: a single `IN` query and 3 constant queries for Virtual. MySQL parity checks: PUBLISHED/DRAFT/ARCHIVED/unknown and ACTIVE/inactive/unknown give the same answers in batch and single-id calls. |
| S2 | `PlanCourseResolverTest`: one call with the distinct ids from all plans; empty input means `verifyNoInteractions`; plan order preserved; a thrown exception degrades every name to null | Use-case tests updated to the batch stubs |
| S3 | `CourseCatalogPortAdapterTest` (Mockito plus `OutputCaptureExtension`): virtual wins; physical receives only the ids virtual did not answer; physical is skipped when nothing is pending; a failure in either module, or both, logs a WARN with exactly the module and ids | `BillingPlansIntegrationTest` with real seeds: a published virtual course, an active physical course, DRAFT, ARCHIVED, inactive, unknown, and a `"course-1"` id that is not a UUID. Seeded rows are cleaned up in `@AfterEach` because the context is shared with `CatalogIntegrationTest`. The partial-failure case stays at unit level, because a spy would add a context key. |

JaCoCo: each new method is covered by unit tests, so the gates (virtual 95/90, physical 95/90, billing 85/85, `api:app` 0.90) are not at risk. The Lombok-generated constructor runs during the tests. ArchUnit: `app_should_not_depend_on_physical_infrastructure` still holds because the adapter uses only `port.in`.

## Threat Matrix

N/A: this change has no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary.

## Migration / Rollout

No migration is required: there are no schema or data changes. Rollback is to revert S3, then S2, then S1.

## Open Questions

- [ ] Should the spec allow the WARN to include the exception class name? Without it, diagnosing a module failure needs the module's own logs (A6). This does not block the design.
- [ ] Follow-up issue: amend ADR-0037 to describe the sequential virtual-first behavior (A9).

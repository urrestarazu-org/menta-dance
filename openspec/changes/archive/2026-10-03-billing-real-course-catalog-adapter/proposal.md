# Proposal: Real Course Catalog Adapter for Billing Plans

## Intent

`GET /api/v1/billing/plans[/{id}]` always returns `courses[].name: null`: `CourseCatalogPort`'s only implementation is the throwing `NotImplementedCourseCatalogPort`, and `PlanCourseResolver` swallows the failure without logging (#108). Resolve real names from Virtual and Physical without the N+1 the single-id port implies (one lookup per course per plan per request).

## Scope

### In Scope (decisions D1-D4)
- Batch lookups: `VirtualCourseCatalogPort.findPublishedByIds`, `PhysicalCourseAvailabilityPort.findActiveByIds` + repository `IN` queries with the same visibility as `findPublishedById` / `findActiveById`.
- `CourseCatalogPort` batch method replaces `courseName(String)` (breaking, V0; documented).
- `PlanCourseResolver`, `ListPlansUseCaseImpl`, `GetPlanUseCaseImpl`: distinct ids across ALL plans resolved once; constant lookup count per request.
- `CourseCatalogPortAdapter` (`api:app`, `com.menta.app.billing`, `@RequiredArgsConstructor`); Lombok added to `api/app/build.gradle.kts`.
- Unresolved (missing, DRAFT/ARCHIVED, inactive, malformed id, failed module) -> `name: null`, HTTP 200, full plan.
- Delete `NotImplementedCourseCatalogPort` + test; drop the `@MockBean CourseCatalogPort` from `CatalogAccessMocksIntegrationTestBase`; `BillingPlansIntegrationTest` seeds real courses.
- Sync `docs/06-BILLING-API.md`, `billing-v1.yaml` `name` description; Javadoc fixes.

### Out of Scope
- Cache/Caffeine and rate limit (#312); other `NotImplemented*` placeholders (#106); BFF; nullable contract change.
- Migrating existing `api:app` adapters to Lombok; removing the ~25 other inert `@MockBean CourseCatalogPort` declarations.

## Capabilities

### New Capabilities
- `billing-plan-course-names`: plan course-name resolution (virtual precedence, visibility, null degradation, per-module failure isolation, constant lookups, batch lookup visibility parity).

### Modified Capabilities
- None (`catalog-course-detail` single-id resolution unchanged).

## Approach

- `Map<String, String> courseNames(Collection<String> courseIds)`: only resolved ids present; empty input -> empty map, no query.
- Adapter: Virtual batch over all ids; Physical batch only over ids Virtual did not answer (skipped if none); Virtual wins.
- Failure policy (decided: degrade per module, not abort): a module's non-`IllegalArgumentException` failure logs WARN (module + affected courseIds only); its ids degrade to null, the other module's answers still resolve. Resolver keeps a catch-all safety net.
- Malformed ids never fail a whole batch: omitted from the result.
- Resolver dedupes preserving first-seen order; output order follows each plan's `PlanCourse` list.

**Design points for sdd-design**: where malformed-id filtering lives; constant-query batch on Virtual (single lookup does up to 3 SELECTs on a hit); WARN shape.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/virtual/.../port/in`, `usecase`, repository + JPA | Modified | `findPublishedByIds` |
| `api/physical/.../port/in`, `usecase`, repository + JPA | Modified | `findActiveByIds` |
| `api/billing/.../port/out/CourseCatalogPort.java`, `usecase/*Plan*` | Modified | Batch port, resolver, use cases |
| `api/billing/.../infrastructure/catalog/NotImplementedCourseCatalogPort*` | Removed | Placeholder + test |
| `api/app/.../billing/CourseCatalogPortAdapter.java`, `build.gradle.kts` | New/Modified | Adapter, Lombok |
| `api/app/src/test/.../CatalogAccessMocksIntegrationTestBase.java`, `BillingPlansIntegrationTest.java` | Modified | Real adapter |
| `docs/06-BILLING-API.md`, `api/openapi/billing-v1.yaml` | Modified | Docs |

ArchUnit: `app_adapters_follow_cross_module_pattern`, `app_should_not_depend_on_physical_infrastructure`, `physical_application_port_in_is_the_only_bridge`, `layered_architecture_should_be_respected`.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Batch visibility drifts from single lookups | Med | Parity integration tests (DRAFT/ARCHIVED/inactive/missing) |
| Four-module change | Med | Stacked slices, S1 additive |
| Large `IN` list | Low | Bounded by catalog size; no chunking now |
| Base mock removal changes Spring context cache key | Low | Only `BillingPlansIntegrationTest` stubs the port |
| Lombok in `api:app` build | Low | Same declaration as other modules |
| Swallowed failure inside Virtual's `@Transactional(readOnly)` if plan use cases turn transactional | Low | Documented; use cases stay non-transactional |
| ADR-0037 says parallel; #107 shipped sequential | Low | Note divergence |

## Rollback Plan

Revert slice PRs in reverse order (S3, S2, S1). No schema or data change.

## Dependencies

- None. #312 is follow-up.

## Delivery Outline (stacked onto `develop`, budget 800, unmeasured)

| Slice | Content | Forecast |
|-------|---------|----------|
| S1 | Virtual + Physical batch lookups, repository queries, unit + parity integration tests | ~300 |
| S2 | Billing batch port, resolver/use-case rework, placeholder adapted, unit tests | ~300 |
| S3 | Adapter + test, Lombok, placeholder deletion, integration base + plans IT, docs/OpenAPI | ~400 |

## Success Criteria

- [ ] Published virtual / active physical names appear; DRAFT/ARCHIVED/inactive/unknown -> null, HTTP 200.
- [ ] Plans list uses at most one batch call per module regardless of plan/course count.
- [ ] One failed module logs WARN; the other module's names still resolve.
- [ ] `NotImplementedCourseCatalogPort` gone; `BillingPlansIntegrationTest` uses the real adapter.
- [ ] `./gradlew check` green; docs and OpenAPI aligned.

## Proposal Question Round

Covered by D1-D4 (Engram `sdd/billing-real-course-catalog-adapter/decisions`).

# Exploration: billing-real-course-catalog-adapter (#108)

Condensed from the sdd-explore report (Engram `sdd/billing-real-course-catalog-adapter/explore`, observation 1706). The orchestrator re-checked these facts with commands: `api:app` declares no Lombok; `PlanCourseResolver` catches `RuntimeException` and returns null without logging; `NotImplementedCourseCatalogPort` is a component-scanned `@Component`; `CatalogAccessMocksIntegrationTestBase` declares `@MockBean CourseCatalogPort`; `billing-v1.yaml` `PlanCourseResponse.name` is `[string, "null"]`.

## Current State

- `CourseCatalogPort` (`api/billing/.../application/port/out/`): `Optional<String> courseName(String courseId)`; empty = unknown, throws only on infrastructure failure.
- Consumers: `ListPlansUseCaseImpl` and `GetPlanUseCaseImpl`, through the package-private `PlanCourseResolver` (one call per `PlanCourse`, no cache, no dedupe, no logging; catches `RuntimeException` and returns null).
- Wire field: `courses[].name` (`PlanCourseResponse(id, name)`); internal DTO field `PlanCourseResult.courseName`.
- `NotImplementedCourseCatalogPort` is a `@Component` in `api/billing/.../infrastructure/catalog/` (no `@Bean`, no `@ConditionalOnMissingBean`). A second implementation next to it would fail with `NoUniqueBeanDefinitionException`, so deleting it (and its test) is mandatory.
- Lookup ports: `VirtualCourseCatalogPort.findPublishedById` (empty if missing or not PUBLISHED; up to 3 SELECTs on a hit) and `PhysicalCourseAvailabilityPort.findActiveById` (empty if missing or not ACTIVE; 1 PK SELECT). Both return a summary with `title` and throw `IllegalArgumentException` on a non-UUID id. `billing_plan_courses.course_id` is `VARCHAR(64)` and `PlanCourse` accepts any string.
- `CatalogCompositionService` (#107) resolves virtual first, short-circuits, maps `IllegalArgumentException` to empty and any other `RuntimeException` to `CatalogUpstreamException`.
- Existing billing adapters in `api:app` (`com.menta.app.billing`): `@Component`, hand-written constructor, fully qualified names where billing and the other module share a port name, Javadoc citing ADR-0037, Mockito unit test. `api:app` does not declare Lombok.
- About 28 `api:app` integration tests mock `CourseCatalogPort` with `@MockBean`; `BillingPlansIntegrationTest` extends `CatalogAccessMocksIntegrationTestBase`, so it never exercises a real adapter until that mock is removed.
- ADR-0037 says "query both in parallel, log on collision"; the shipped #107 is sequential, virtual first.
- No billing-plans spec exists under `openspec/specs/`.

## Approaches

1. **Sequential adapter in `api:app`, Virtual first, short-circuit (recommended).** Per-module `IllegalArgumentException` becomes empty; found: return the title; both empty without failure: `Optional.empty()`; no answer but a module failed: WARN log (module and courseId only) and throw, so `PlanCourseResolver` keeps degrading to null. Low effort, about 400-500 changed lines.
2. Approach 1 plus per-request memoization or a batch lookup. Removes the N+1 but changes the billing application layer and the port contract. Defer as a follow-up.
3. Parallel query of both ports as the ADR text says. Executor and transaction-context complexity, two queries always paid, and #107 already diverged. Reject.

## Affected Areas

- New: `api/app/.../billing/CourseCatalogPortAdapter.java` and its unit test.
- Deleted: `NotImplementedCourseCatalogPort` and `NotImplementedCourseCatalogPortTest`.
- Javadoc: `CourseCatalogPort`, `PlanCourseResult`, `PlanCourseResolver`, `PhysicalCourseOwnershipPort`, `BillingConfiguration`, a comment in `ListPlansUseCaseImplTest`.
- Integration: drop the `CourseCatalogPort` mock from `CatalogAccessMocksIntegrationTestBase`; `BillingPlansIntegrationTest` seeds real physical and virtual courses.
- Docs: `docs/06-BILLING-API.md` (still says the adapter does not exist), `api/openapi/billing-v1.yaml` `PlanCourseResponse.name` description. `US-BILLING-001`, the Bruno plan requests and the BFF need no change.

## Risks

- Silent degradation today (no log): the adapter should log a WARN before throwing.
- N+1: one lookup per course per plan per request, no cache; the endpoint is public and rate-limited. Flag only; follow-up.
- Transaction caveat: the plan use cases have no outer `@Transactional`; if they ever become transactional, a swallowed exception from Virtual's inner `@Transactional(readOnly)` could cause `UnexpectedRollbackException`.
- Lombok: `api:app` has none, which contradicts the project-wide note in CLAUDE.md; either hand-written constructor (as every adapter there) or add Lombok to `api:app`.

## Questions for the user (most important first)

1. A plan course that is DRAFT or ARCHIVED (virtual) or inactive (physical): `name` null (recommended) or its real title?
2. Keep `name: null` for unresolved courses (current nullable contract)?
3. Accept the N+1 for now with a follow-up issue?
4. Hand-written constructor (as the existing adapters) or add Lombok to `api:app`?

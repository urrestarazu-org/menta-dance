# Tasks: Real Course Catalog Adapter for Billing Plans (#108)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1060 total: S1 ~340, S2 ~290, S3 ~430 |
| 400-line budget risk | High against 400 (total ~1060, S3 ~430 alone); Medium against the 800 review budget (S3 is the tightest) |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (S1) -> PR 2 (S2) -> PR 3 (S3) |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main (each PR targets `develop`, the integration branch, after the previous one merges) |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|----|----------------------|-----------------|-------------------|
| S1 | Additive batch lookups and queries in Virtual and Physical (nothing calls them yet) | PR 1 (`Refs #108`) | `./gradlew :api:virtual:test :api:physical:test :api:app:test --tests "*VirtualCourseCatalogIntegrationTest" --tests "*PhysicalCourseAvailabilityIntegrationTest"` | Testcontainers MySQL in the two `api:app` integration tests (parity checks) | Revert the new methods and tests in both modules; no schema change, no caller |
| S2 | Billing batch port, `PlanCourseResolver.resolveNames`, both plan use cases, placeholder adapted to the batch method | PR 2 (`Refs #108`) | `./gradlew :api:billing:test :api:app:test --tests "*BillingPlansIntegrationTest"` | `BillingPlansIntegrationTest` (Testcontainers) still runs against the mocked port | Revert S3 first, then S2; S1 stays valid on its own |
| S3 | `CourseCatalogPortAdapter`, placeholder deletion, Lombok in `api:app`, real-seed integration test, ArchUnit, docs and OpenAPI | PR 3 (`Closes #108`) | `./gradlew :api:app:test --tests "*CourseCatalogPortAdapterTest" --tests "*BillingPlansIntegrationTest" --tests "*ArchitectureTest"` | `BillingPlansIntegrationTest` with real seeds (Testcontainers MySQL) | Revert S3 alone; names return to null via the placeholder from S2 |

Budget flags: S3 (~430) is the largest; S2 and S1 are well under 800. Heaviest S3 tasks: 3.2-3.4 (adapter test and class, ~200), 3.8-3.10 (`BillingPlansIntegrationTest` real seeds, ~120) and 3.14-3.15 (docs plus OpenAPI). Pre-agreed split point if the measured S3 diff trends above ~700: move docs plus OpenAPI (3.14, 3.15) to a docs-only PR 4 (`Refs #108`) and keep `Closes #108` on the last PR. S1 must not absorb S2 work and vice versa: the `courseName(` stub updates (2.2-2.4) stay in S2 so every slice compiles on its own.

## Phase 0: Tracking (orchestrator performs; not code)

- [x] 0.1 Board item for #108 on project 1: status In progress, fields set, before S1 starts (already done by the orchestrator).
- [x] 0.2 Move the item to In review when PR 1, PR 2 and PR 3 are each opened.
- [x] 0.3 Ensure the final PR body contains `Closes #108` (automation sets Done on merge); PR 1 and PR 2 use `Refs #108`.
- [x] 0.4 At PR 3 review time, confirm that task 3.12 holds: `BillingPlansIntegrationTest` seeded rows are deleted in `@AfterEach` (database shared with `CatalogIntegrationTest` and `VirtualLessonAccessIntegrationTest`).

## Phase 1: S1 - Virtual and Physical batch lookups (PR 1, branch `feature/billing-catalog-s1-batch-lookups`)

Spec: "Batch lookups match single-id visibility" and the module half of "Batched, constant-cost resolution". Nothing calls the new methods yet.

- [x] 1.1 RED (Virtual port impl): in `api/virtual/src/test/java/com/menta/virtual/application/usecase/VirtualCourseCatalogPortImplTest.java` add tests for `findPublishedByIds`: malformed/blank/null id skipped without a query (A4); no valid id returns `Map.of()` without calling the repository; result keyed by the input string (uppercase UUID, A2); duplicates looked up once; null collection throws `NullPointerException`; hit mapped to the same summary as `findPublishedById`. Compile failure counts as RED.
- [x] 1.2 RED (Virtual repository adapter): in `api/virtual/src/test/java/com/menta/virtual/infrastructure/persistence/adapter/VirtualCourseRepositoryAdapterTest.java` add tests: `findByIdInAndStatus(ids, PUBLISHED)` then `countByCourseIdIn` and `aggregateByCourseIdIn` over the hits (3 constant queries, A3); aggregates skipped when nothing matches; empty id list does no query.
- [x] 1.3 GREEN (Virtual persistence): add `List<VirtualCourseJpaEntity> findByIdInAndStatus(Collection<UUID> ids, CourseStatus status)` to `api/virtual/src/main/java/com/menta/virtual/infrastructure/persistence/repository/VirtualCourseJpaRepository.java`; add `findPublishedByIds(Collection<CourseId>)` to `api/virtual/src/main/java/com/menta/virtual/application/port/out/VirtualCourseRepository.java` and implement it in `api/virtual/src/main/java/com/menta/virtual/infrastructure/persistence/adapter/VirtualCourseRepositoryAdapter.java` (`@Transactional(REQUIRED, readOnly)`, same `toDomain` mapping as the single-id path).
- [x] 1.4 GREEN (Virtual port): add `Map<String, VirtualCourseSummary> findPublishedByIds(Collection<String>)` with Javadoc to `api/virtual/src/main/java/com/menta/virtual/application/port/in/VirtualCourseCatalogPort.java` and implement it in `api/virtual/src/main/java/com/menta/virtual/application/usecase/VirtualCourseCatalogPortImpl.java` (`CourseId.of` filter per id, `LinkedHashSet` dedupe, key by input id, reuse `toSummary`). Run 1.1 and 1.2 green.
- [x] 1.5 RED (Physical port impl): in `api/physical/src/test/java/com/menta/physical/application/usecase/PhysicalCourseAvailabilityPortImplTest.java` add the same cases for `findActiveByIds` (malformed skipped, no valid id means no query, input-keyed, dedupe, null collection NPE, summary equals the single-id path).
- [x] 1.6 RED (Physical repository adapter): in `api/physical/src/test/java/com/menta/physical/infrastructure/persistence/adapter/PhysicalCourseRepositoryAdapterTest.java` add a test for a single `findByIdInAndStatus(ids, ACTIVE)` query and no query for empty input.
- [x] 1.7 GREEN (Physical): add `findByIdInAndStatus(Collection<UUID>, CourseStatus)` to `api/physical/src/main/java/com/menta/physical/infrastructure/persistence/repository/PhysicalCourseJpaRepository.java`; `findActiveByIds(Collection<CourseId>)` to `api/physical/src/main/java/com/menta/physical/application/port/out/PhysicalCourseRepository.java` and `api/physical/src/main/java/com/menta/physical/infrastructure/persistence/adapter/PhysicalCourseRepositoryAdapter.java`; `Map<String, PhysicalCourseSummary> findActiveByIds(Collection<String>)` to `api/physical/src/main/java/com/menta/physical/application/port/in/PhysicalCourseAvailabilityPort.java` and `api/physical/src/main/java/com/menta/physical/application/usecase/PhysicalCourseAvailabilityPortImpl.java`. Run 1.5 and 1.6 green.
- [x] 1.8 RED then GREEN (Virtual MySQL parity): in `api/app/src/test/java/com/menta/app/integration/virtual/VirtualCourseCatalogIntegrationTest.java` seed PUBLISHED, DRAFT and ARCHIVED courses plus an unknown id; assert batch and single-id lookups both return only the PUBLISHED course with the same title (spec "Virtual parity"). Reuse the existing context and clean up seeds.
- [x] 1.9 RED then GREEN (Physical MySQL parity): in `api/app/src/test/java/com/menta/app/integration/physical/PhysicalCourseAvailabilityIntegrationTest.java` seed an ACTIVE and an inactive course plus an unknown id; batch and single-id both return only the ACTIVE one with the same title (spec "Physical parity").
- [x] 1.10 REFACTOR: remove unused imports, tidy Javadoc, no behavior change; rerun the S1 focused tests.
- [x] 1.11 Gate: `./gradlew :api:virtual:test :api:physical:test :api:app:test` green.
- [x] 1.12 Gate: JaCoCo `./gradlew :api:virtual:jacocoTestCoverageVerification :api:physical:jacocoTestCoverageVerification` (virtual 95%/90%, physical 95%/90%); also `:api:app:jacocoTestCoverageVerification` (flat floor `moduleCoverageFloor` in the root `build.gradle.kts`).
- [x] 1.13 Gate: `./gradlew :api:virtual:checkstyleMain :api:virtual:checkstyleTest :api:physical:checkstyleMain :api:physical:checkstyleTest :api:app:checkstyleTest`; verify added lines (LineLength 100, import order; snake_case `MethodName` on test names is a known non-blocker, #298).
- [x] 1.14 Gate: ArchUnit per module, `./gradlew :api:virtual:test --tests "*ArchitectureTest" :api:physical:test --tests "*ArchitectureTest"` (root `./gradlew test --tests "*ArchitectureTest"` is unrunnable because of the android module).
- [x] 1.15 Gate: size check `git diff --shortstat origin/develop...HEAD` <= 800 changed lines (target ~340).
- [x] 1.16 PR 1: `gh pr list --head <branch>` first; base `develop`, Spanish body, `Refs #108`, confirm preview before push (`skills/prcreator/SKILL.md`).

## Phase 2: S2 - Billing batch port and resolver (PR 2, branch `feature/billing-catalog-s2-batch-port` from `develop` after PR 1 merges)

Spec: "Batched, constant-cost resolution per request", "Unresolvable courses degrade to a null name", "Response contract is unchanged". The old `courseName(` stubs all move here so the slice compiles on its own.

- [x] 2.1 RED (resolver): create `api/billing/src/test/java/com/menta/billing/application/usecase/PlanCourseResolverTest.java`: one `courseNames` call with the distinct ids of all plans (spec "Shared course across plans"); empty input means `verifyNoInteractions` (spec "Nothing to resolve"); plan course order preserved; unresolved id gives `name` null with stored id; a thrown `RuntimeException` degrades every name to null and logs one WARN; plan without courses gives `[]`. Compile failure counts as RED.
- [x] 2.2 RED (list use case): in `api/billing/src/test/java/com/menta/billing/application/usecase/ListPlansUseCaseImplTest.java` replace the `courseName(` stubs (lines ~86, ~102, ~117) with `courseNames(any())` returning a `Map`; fix the stale comment at line ~111; assert a single batch call across several plans.
- [x] 2.3 RED (get use case): in `api/billing/src/test/java/com/menta/billing/application/usecase/GetPlanUseCaseImplTest.java` update the stub at line ~62 to the batch method.
- [x] 2.4 RED (placeholder): in `api/billing/src/test/java/com/menta/billing/infrastructure/catalog/NotImplementedCourseCatalogPortTest.java` change line ~13 to call `courseNames(Collection)` and still expect `UnsupportedOperationException`.
- [x] 2.5 GREEN (port): in `api/billing/src/main/java/com/menta/billing/application/port/out/CourseCatalogPort.java` replace `courseName(String)` with `Map<String, String> courseNames(Collection<String>)` and the A1 Javadoc (immutable, resolved ids only, input-keyed, empty means no lookup, null collection NPE); adapt `api/billing/src/main/java/com/menta/billing/infrastructure/catalog/NotImplementedCourseCatalogPort.java` to implement `courseNames` and still throw `UnsupportedOperationException`.
- [x] 2.6 GREEN (resolver): in `api/billing/src/main/java/com/menta/billing/application/usecase/PlanCourseResolver.java` implement `resolveNames(List<Plan>)` (`LinkedHashSet` of distinct ids, one port call, catch `RuntimeException`, log WARN, return empty map) and `toResults(List<PlanCourse>, Map<String, String>)` using `names.get(id)`; document the no-transaction caveat in Javadoc (A8).
- [x] 2.7 GREEN (use cases): in `api/billing/src/main/java/com/menta/billing/application/usecase/ListPlansUseCaseImpl.java` and `api/billing/src/main/java/com/menta/billing/application/usecase/GetPlanUseCaseImpl.java` call `resolveNames` once per request, then `toResults` per plan. Run 2.1-2.4 green.
- [x] 2.8 GREEN (api:app compile): in `api/app/src/test/java/com/menta/app/integration/billing/BillingPlansIntegrationTest.java` line ~97 change the stub to `when(courseCatalogPort.courseNames(any())).thenReturn(Map.of("course-1", "Tango Basico"))` so `api:app` test compilation does not break; run the test green.
- [x] 2.9 REFACTOR: remove unused imports and tidy Javadoc; rerun the S2 focused tests.
- [x] 2.10 Gate: `./gradlew :api:billing:test :api:app:test` green.
- [x] 2.11 Gate: JaCoCo `./gradlew :api:billing:jacocoTestCoverageVerification :api:app:jacocoTestCoverageVerification` (billing 85%/85%, `api:app` flat floor).
- [x] 2.12 Gate: `./gradlew :api:billing:checkstyleMain :api:billing:checkstyleTest :api:app:checkstyleTest`; verify added lines (LineLength 100, import order; snake_case `MethodName` is a known non-blocker, #298).
- [x] 2.13 Gate: ArchUnit `./gradlew :api:billing:test --tests "*ArchitectureTest"`.
- [x] 2.14 Gate: size check `git diff --shortstat origin/develop...HEAD` <= 800 (target ~290).
- [x] 2.15 PR 2: `gh pr list --head <branch>` first; base `develop`, Spanish body, `Refs #108`, confirm preview.

## Phase 3: S3 - Adapter, wiring, contract and docs (PR 3, branch `feature/billing-catalog-s3-adapter` from `develop` after PR 2 merges)

Spec: "Virtual takes precedence over physical", "A failing module degrades only its own courses", plus end-to-end checks of every other requirement. Placeholder deletion and adapter creation land together (no window with two beans).

- [x] 3.1 Build: in `api/app/build.gradle.kts` add `compileOnly(libs.lombok)` and `annotationProcessor(libs.lombok)`. Note: `api:app` has no `lombok.config`, so Lombok-generated code counts toward its JaCoCo floor.
- [x] 3.2 RED (adapter unit): create `api/app/src/test/java/com/menta/app/billing/CourseCatalogPortAdapterTest.java` (Mockito plus `OutputCaptureExtension`): virtual wins for an id both can answer (spec "Id answerable by both"); physical receives only the ids virtual did not answer; physical skipped when virtual answers everything (spec "Physical lookup skipped..."); empty input does no lookup; result is immutable and input-keyed. Compile failure counts as RED.
- [x] 3.3 RED (adapter failures): in the same test cover virtual failure (physical still resolves, one WARN `Course catalog lookup failed; module=virtual, courseIds=[...]`), physical failure (virtual names kept, WARN with module physical and only the ids physical was asked), both failing (empty map, two WARNs), a malformed id causing no WARN, and no other data in the WARN (A5, A6).
- [x] 3.4 GREEN (adapter): create `api/app/src/main/java/com/menta/app/billing/CourseCatalogPortAdapter.java` (`@Component`, `@RequiredArgsConstructor`, `@Slf4j`; injects Virtual and Physical `port.in` interfaces; virtual first then physical for pending ids; per-module `RuntimeException` caught, WARN, module ids unresolved; `Map.copyOf` merge). Javadoc states sequential and virtual-first, no `@Transactional` (A7, A9). Run 3.2 and 3.3 green.
- [x] 3.5 GREEN (delete placeholder): delete `api/billing/src/main/java/com/menta/billing/infrastructure/catalog/NotImplementedCourseCatalogPort.java` and `api/billing/src/test/java/com/menta/billing/infrastructure/catalog/NotImplementedCourseCatalogPortTest.java` in this same commit; confirm no remaining reference (`rg NotImplementedCourseCatalogPort`).
- [x] 3.6 RED then GREEN (ArchUnit): in `api/app/src/test/java/com/menta/app/ArchitectureTest.java` add the adapter to the package rule and assert direct dependencies only on Virtual's and Physical's `port.in`; `app_should_not_depend_on_physical_infrastructure` must still hold.
- [x] 3.7 GREEN (shared base): in `api/app/src/test/java/com/menta/app/integration/support/CatalogAccessMocksIntegrationTestBase.java` remove the `CourseCatalogPort` mock (A10); leave the other inert `@MockBean CourseCatalogPort` declarations.
- [x] 3.8 RED (integration seeds): in `api/app/src/test/java/com/menta/app/integration/billing/BillingPlansIntegrationTest.java` drop the `courseCatalogPort` stub and seed a PUBLISHED virtual course, an ACTIVE physical course, a virtual DRAFT, a virtual ARCHIVED, an inactive physical course, an unknown id and a non-UUID `"course-1"` id across plans.
- [x] 3.9 GREEN (integration assertions): assert HTTP 200, titles for the resolvable virtual and physical courses, `name` null with stored id for every unresolvable one, course order and count unchanged, and exactly `id` and `name` keys (specs "Mixed plan", "Not publicly visible", "Malformed id", "Nonexistent course", "Shape preserved") for both the list and `GET /plans/{id}`.
- [x] 3.10 RED then GREEN (integration edge): same test, cover a plan without courses (`courses` = `[]`) and the shared-course-across-plans case (same title on all plans). The partial-failure case stays at unit level (3.3), because a spy would add a context key.
- [x] 3.11 Javadoc fixes: remove placeholder references in `api/billing/src/main/java/com/menta/billing/infrastructure/config/BillingConfiguration.java` (~102), `api/billing/src/main/java/com/menta/billing/application/port/out/PhysicalCourseOwnershipPort.java` (~16) `api/billing/src/main/java/com/menta/billing/application/dto/PlanCourseResult.java` (~8) and `api/billing/src/main/java/com/menta/billing/application/port/out/CourseCatalogPort.java` (class Javadoc: it still says the wired adapter is the placeholder, left true by S2 until S3 deletes it).
- [x] 3.12 Verify cleanup: `BillingPlansIntegrationTest` deletes every seeded row (courses and plans) in `@AfterEach`, in FK-safe order, because the context and database are shared with `CatalogIntegrationTest` and `VirtualLessonAccessIntegrationTest`; run those three classes together and confirm no cross-test leakage.
- [x] 3.13 REFACTOR: remove unused imports, tidy Javadoc, no behavior change; rerun the S3 focused tests.
- [x] 3.14 Docs (Spanish): update `docs/06-BILLING-API.md` (lines ~52-56) to describe real name resolution, null degradation, virtual-first precedence and failure isolation.
- [x] 3.15 Contract (Spanish): update `api/openapi/billing-v1.yaml` (lines ~1258-1265) `courses[].name` description (resolved title; null when unresolvable); schema shape unchanged, name stays nullable.
- [x] 3.16 Gate: `./gradlew :api:billing:test :api:app:test` green and `./gradlew check` green for the `api` modules.
- [x] 3.17 Gate: JaCoCo `./gradlew :api:billing:jacocoTestCoverageVerification :api:app:jacocoTestCoverageVerification` (billing 85%/85%; `api:app` flat floor `moduleCoverageFloor`, Lombok-generated code counted).
- [x] 3.18 Gate: `./gradlew :api:app:checkstyleMain :api:app:checkstyleTest :api:billing:checkstyleMain`; verify added lines (LineLength 100, import order; snake_case `MethodName` is a known non-blocker, #298).
- [x] 3.19 Gate: ArchUnit per module, `./gradlew :api:app:test --tests "*ArchitectureTest" :api:billing:test --tests "*ArchitectureTest"`.
- [x] 3.20 Gate: size check `git diff --shortstat origin/develop...HEAD` <= 800 (target ~430); if above ~700, apply the pre-agreed split (3.14, 3.15 to a docs-only PR).
- [x] 3.21 PR 3: `gh pr list --head <branch>` first; base `develop`, Spanish body, `Closes #108`, confirm preview.

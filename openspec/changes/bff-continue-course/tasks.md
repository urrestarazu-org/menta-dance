# Tasks: BFF "Continuar Curso" CTA on Course Detail (#58, US-VIRTUAL-008)

## Review Workload Forecast

Grounded in this module's own measured precedents (checked against current
file sizes, not the numbers quoted in the ask):

| Precedent file | Lines | What it tells us |
|---|---|---|
| `GetLessonViewUseCaseImpl.java` / `...Test.java` | 105 / 172 (6 cases) | Closest shape match for `GetCourseDetailViewUseCaseImpl` (two port calls, one cross-reference, sealed result) — new impl ~100, new test ~170-190 |
| `LessonView.java` (sealed, 2 records) | 60 | `CourseDetailView` has 3 records (`Plain`/`Resumable`/`Resume`) — expect ~85-95 |
| `VirtualApiAdapterTest.java` | 442, 17 cases / 3 methods (~5-6 each) | The prompt's "13-17 per method" is not what's actually in the file — real average is ~5-6; the new `getCourseProgress` method needs ~8 (mandatory-Bearer guard + explicit unmapped-401 case add two beyond the norm) |
| `VirtualApiClient.java` / `VirtualApiAdapter.java` | 134 / 311 | Adding one method reusing existing exceptions: ~25 / ~45 lines |
| `GetCourseDetailUseCase(+Impl+Test)` (deleted) | 29 + 34 + 77 = 140 | Full deletion cost, counted as diff |
| `CourseDetailController.java` / `course-detail.html` | 41 / 32 | Modify, not create — token read + exhaustive switch (~20-25); one `th:if` CTA block (~12-15) |
| `VirtualLearningViewIntegrationTest.java` login→cookie flow | already exists (lines 234-257) | Reused, not built — lowers integration cost vs. #170's first build; 5 new cases (Resumable, zero-progress, 403, anonymous-zero-calls, progress-503) ~180-220 |

**Estimate**: ~950-1150 changed lines (production + tests, additions +
deletions) across the whole change. Smaller than `bff-virtual-learning-view`
(no new route, no security-config change, no new template file), but still
well above the 400-line budget for one PR.

```text
Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High
```

`auto-chain` means the orchestrator proceeds straight to PR1 with the chain
strategy below — no user prompt required before `sdd-apply`. `stacked-to-main`
per this repo's Git Flow: each slice branches off `develop` after its
predecessor merges, and merges back into `develop` in order.

### Suggested Work Units

The natural seam mirrors the prior change's split: adapter/DTO/wire-mapping
first, then the use-case layer, then the controller/template cutover. Unlike
PR 1a/1b in the prior change (split by upstream call), this adapter gets only
one new method, so it stays a single PR; the seam instead falls between
"new code, not yet wired" (PR 2) and "the atomic swap that deletes the old
use case" (PR 3) — deleting `GetCourseDetailUseCase` while `CourseDetailController`
still depends on it would break the build, so that deletion cannot land
before the controller cutover.

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | `VirtualApiClient`/`VirtualApiAdapter`: add `getCourseProgress`; `CourseProgress` wire DTO | PR 1 (~250-300) | `./gradlew :bff:test --tests "*VirtualApiAdapterTest*"` | `@WireMockTest`, no Spring context | Revert the new method + DTO; nothing calls it yet |
| 2 | `CourseDetailView` + `GetCourseDetailViewUseCase`(+Impl) + unit test — new files only, old use case untouched and still wired | PR 2 (~350-420) | `./gradlew :bff:test --tests "*GetCourseDetailViewUseCase*"` | Mockito only, no Spring context | Revert the 3 new files; old `GetCourseDetailUseCase` bean is unaffected |
| 3 | Controller cutover: wire new use case, exhaustive `switch`, template CTA, delete old use case (+impl+test), integration tests | PR 3 (~400-480) | `./gradlew :bff:test --tests "*CourseDetail*"` | `BaseIntegrationTest` (Testcontainers Redis + WireMock) for integration; standalone MockMvc for controller test | Revert this PR's single commit restores old controller/config/template and the deleted files atomically |

## PR 1 — API integration: `getCourseProgress`

**Branch**: `feature/bff-continue-course-api-progress` off `develop`.

- [x] 1.1 Create `application/dto/CourseProgress.java`: record mirroring
      `api:virtual`'s `CourseProgressResponse` byte-for-byte —
      `(String courseId, int completedLessons, int totalLessons, int percentage, ResumeLesson resumeLesson)`
      with nested `ResumeLesson(String lessonId, String moduleId, int positionSeconds, boolean completed)`.
- [x] 1.2 Modify `application/port/out/VirtualApiClient.java`: add
      `CourseProgress getCourseProgress(String courseId, String accessToken)`,
      javadoc noting the call is Bearer-required (no anonymous form) and that
      `403`/unmapped `401` both reuse the existing `ForbiddenException`/generic
      `RuntimeException` paths — no new exception type.
- [x] 1.3 RED: extend `VirtualApiAdapterTest.java` with a `-- getCourseProgress --`
      section: 200 with `resumeLesson` present maps fields correctly; 200 with
      `resumeLesson` null maps to a null `resumeLesson()`; 404 →
      `NotFoundException`; 403 → `ForbiddenException` without parsing the body;
      503 → `ServiceUnavailableException` preserving `Retry-After`; **401 → falls
      through to the generic `RuntimeException`** (explicit case — the shared
      mappers are deliberately not touched, design D3); `Authorization: Bearer
      <token>` always present; passing a `null` token throws `NullPointerException`
      from `requireNonNull` **before any WireMock request is recorded**
      (`verify(0, ...)`).
- [x] 1.4 Verify RED: run `*VirtualApiAdapterTest*` — new cases fail on the
      missing `getCourseProgress` method, not a typo.
- [x] 1.5 GREEN: implement `getCourseProgress` in `VirtualApiAdapter.java` —
      endpoint constant `/api/v1/virtual/courses/{courseId}/progress`,
      `Objects.requireNonNull(accessToken, "accessToken cannot be null")`
      **before** the `try` block (mirrors the id guards), unconditional
      `headers.setBearerAuth(accessToken)` (token is guaranteed non-null, unlike
      `getLesson`/`getStream`'s conditional header), reuse `mapErrorStatus`/
      `mapHttpException` with label `"Course progress"` — no new branch added to
      either shared method.
- [x] 1.6 Verify GREEN: full `VirtualApiAdapterTest` suite green, including
      pre-existing catalog/lesson/stream cases (no regression).
- [x] 1.7 Run `./gradlew :bff:test check` before opening PR 1.

## PR 2 — Use case + view model

**Branch**: `feature/bff-continue-course-usecase` off `develop` (cut after PR 1 merges).

- [x] 2.1 Create `application/usecase/CourseDetailView.java`: sealed interface
      `Plain(CourseDetail course)` / `Resumable(CourseDetail course, Resume resume)`,
      nested `Resume(String lessonId, String title, int percentage, int
      completedLessons, int totalLessons)` — exact shape from design D1;
      javadoc states explicitly that `positionSeconds` is read from the wire
      DTO but never carried into `Resume` (locked decision — no seek link).
- [x] 2.2 Create `application/usecase/GetCourseDetailViewUseCase.java`:
      `CourseDetailView execute(String courseId, String accessToken)`.
- [x] 2.3 RED: `application/usecase/GetCourseDetailViewUseCaseImplTest.java`
      (Mockito on `VirtualApiClient`), covering exactly:
      - `accessToken == null` → `Plain`, and `verify(virtualApiClient,
        never()).getCourseProgress(any(), any())` — the call itself never
        fires, not merely an omitted header;
      - `accessToken` present → `getCourseProgress` invoked with that exact
        token, never `null`;
      - `resumeLesson` non-null with a `lessonId` present in
        `CourseDetail.modules[].lessons[]` → `Resumable`, title resolved from
        that cross-reference;
      - `resumeLesson` non-null with a `lessonId` **absent** from the
        projection (catalog drift) → `Plain`, **no exception thrown** —
        unlike `GetLessonViewUseCaseImpl`'s `indexOfLesson`, this lookup must
        degrade safely;
      - `resumeLesson == null` → `Plain`;
      - a parameterized case covering `403`/`404`/unmapped `401`/`503` from
        `getCourseProgress` — every one collapses to `Plain`;
      - a catalog-call (`getCourseDetail`) exception is **not** caught by the
        same guard — it propagates untranslated, exactly as in #170.
- [x] 2.4 Verify RED: run `./gradlew :bff:test --tests "*GetCourseDetailViewUseCase*"`
      — fails on the missing class, not a typo.
- [x] 2.5 GREEN: create `GetCourseDetailViewUseCaseImpl.java` — fetch catalog
      first (untranslated propagation); if `accessToken == null` return
      `Plain`; else call `getCourseProgress` in a `try`/`catch
      (RuntimeException)` that logs at `WARN` and returns `Plain`; if
      `resumeLesson == null` return `Plain`; otherwise look up `lessonId`
      against the flattened lesson list (reuse the flatten pattern from
      `GetLessonViewUseCaseImpl`, but a null-safe lookup, not
      `indexOfLesson`'s throwing one) — not found → `Plain`; found → build
      `Resumable` with the resolved title.
- [x] 2.6 Verify GREEN: full `GetCourseDetailViewUseCaseImplTest` suite green.
- [x] 2.7 Run `./gradlew :bff:test check` before opening PR 2. Note: the old
      `GetCourseDetailUseCase`/`Impl` and their `UseCaseConfig` bean stay
      untouched and still wired into `CourseDetailController` — this PR ships
      new, tested, currently-unused code; the cutover happens in PR 3.

## PR 3 — Controller cutover, template, cleanup

**Branch**: `feature/bff-continue-course-controller` off `develop` (cut after PR 2 merges).

- [ ] 3.1 RED: extend `CourseDetailControllerTest.java` (standalone MockMvc) —
      no token attribute on the request → use case invoked with a `null`
      `accessToken`, model carries no `resume` attribute; token attribute
      present + a stubbed `Resumable` result → model contains `resume` with
      its exact fields; token attribute present + a stubbed `Plain` result →
      no `resume` attribute. The `switch` is exhaustive (compiler-enforced, no
      `default` branch).
- [ ] 3.2 Verify RED: fails against the old controller (wrong use case
      type, no token read), not a typo.
- [ ] 3.3 GREEN: modify `CourseDetailController.java` — inject
      `GetCourseDetailViewUseCase`, read
      `TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE` from `HttpServletRequest`
      (mirrors `LessonViewController`), exhaustive `switch` over
      `CourseDetailView`: `Plain` adds only `course`; `Resumable` additionally
      adds `resume`.
- [ ] 3.4 Modify `infrastructure/config/UseCaseConfig.java`: replace the
      `GetCourseDetailUseCase` bean with a `GetCourseDetailViewUseCase` bean
      backed by `GetCourseDetailViewUseCaseImpl`.
- [ ] 3.5 Delete `application/usecase/GetCourseDetailUseCase.java`,
      `GetCourseDetailUseCaseImpl.java`, and
      `GetCourseDetailUseCaseImplTest.java` — superseded, nothing references
      them after 3.3-3.4 (folded into `GetCourseDetailViewUseCaseImplTest`
      from PR 2).
- [ ] 3.6 Modify `templates/course-detail.html`: add a `th:if="${resume}"`
      block rendering "Continuar" with `resume.percentage()`,
      `resume.completedLessons()`/`resume.totalLessons()`, and a link built
      as `/courses/{courseId}/lessons/{lessonId}` (no query string, no
      fragment); the existing "Comenzar" rendering is the implicit
      `th:unless`/default and stays byte-identical for `Plain`.
- [ ] 3.7 Run `./gradlew :bff:test check` — full build green,
      `BffArchitectureTest` passes (no new `application → infrastructure`
      import), Checkstyle clean.
- [ ] 3.8 RED, then GREEN: extend `VirtualLearningViewIntegrationTest.java`,
      reusing the existing login→cookie helper flow (lines 234-257) for every
      authenticated case:
      - entitled + progress → response contains "Continuar", the exact
        `completedLessons`/`totalLessons`/`percentage`, and the link matches
        `/courses/{courseId}/lessons/{lessonId}` exactly (assert no query
        string or fragment);
      - entitled + zero progress (`resumeLesson` null) → "Comenzar", never
        "Continuar 0%";
      - `403` on the progress call → response body byte-equivalent to the
        anonymous rendering;
      - anonymous request → `WIRE_MOCK_SERVER.verify(0,
        getRequestedFor(urlEqualTo(progressUrl)))` — zero progress calls;
      - progress `503` → page still `200` with catalog content, no upstream
        problem-detail body and no generic error view.
- [ ] 3.9 Run `./gradlew :bff:test check` before opening PR 3.

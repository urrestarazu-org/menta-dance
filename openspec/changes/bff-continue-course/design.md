# Design: BFF "Continuar Curso" CTA on Course Detail

## Technical Approach

`GET /courses/{courseId}` stays the only route and stays `permitAll`. The
controller reads `TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE` exactly like
`LessonViewController` and forwards it to a use case that returns a sealed
view. Catalog is fetched first and unauthenticated; progress is fetched only
when a token exists and can never fail the page.

## Architecture Decisions

### D1 — Sealed `CourseDetailView`, not a nullable field

| Option | Tradeoff | Decision |
|---|---|---|
| Sealed `Plain`/`Resumable` | +1 type; exhaustive `switch` mirrors `LessonViewController` | **Chosen** |
| Nullable `progress` field on a combined record | Fewer types, but one field guarding five values | Rejected |
| Field on `CourseDetail` | That record mirrors upstream JSON byte-for-byte | Rejected |

`LessonView.Sample` exists so a denied lesson *cannot* carry stream data. The
equivalent guarantee here is the locked rule that a `403` visitor renders
identically to an anonymous one: `Plain` has no progress components, so the
denial path cannot carry personalization even by accident. The nullable
alternative also cannot express "percentage, counts and a *resolved* lesson
title are all present or none are" — `Resumable` makes that atomic.

### D2 — Logic lives in a use case, not the controller

`GetCourseDetailUseCase`/`Impl` are replaced by `GetCourseDetailViewUseCase`
/`Impl` (`execute(courseId, accessToken) → CourseDetailView`), the exact shape
of `GetLessonViewUseCase`. Rejected: controller orchestrating two ports (no
precedent, puts the collapse rule in infrastructure); keeping the old
pass-through and composing it (imports a use-case-calls-use-case pattern this
module does not have, and leaves a dead 34-line class). The rename is kept
because the return type is no longer `CourseDetail`.

### D3 — Adapter contract: token mandatory, guarded

```java
CourseProgress getCourseProgress(String courseId, String accessToken);
```

`getCourseDetail` enforces "never authenticated" structurally by having no
token parameter. The symmetric "always authenticated" rule is unexpressible in
the signature, so the adapter asserts it: `Objects.requireNonNull(accessToken,
...)` before the `try` block, matching the id guards already on all three
methods. `403`/`404`/`5xx` reuse `mapErrorStatus`/`mapHttpException` with
label `"Course progress"`. **No `401` branch is added** to those shared
mappers — that would change lesson/stream behaviour outside this scope; a
`401` keeps falling through to the generic `RuntimeException`, which D4
swallows.

### D4 — Asymmetry: catalog failures fail the page, progress failures never do

`CourseDetail` *is* the page. Progress is supplementary personalization whose
absence yields the anonymous rendering — an already-shipped, valid page.
So catalog exceptions propagate untranslated (#170 unchanged), and **every**
`RuntimeException` from the progress call collapses to `Plain`, logged at
`WARN`: `403`, `404`, the unmapped `401`, and explicitly
`ServiceUnavailableException` too. Rendering `error.html` because
personalization was briefly unavailable would be strictly worse than showing
the working catalog page. Title resolution is inside the same guard and
returns `Plain` when the resume `lessonId` is absent from the projection
(catalog drift), rather than throwing like `indexOfLesson` does.

## Data Flow

    Controller ──token?──no──→ getCourseDetail ─────────────────→ Plain
         │
        yes → getCourseDetail → getCourseProgress ─┬─ resumeLesson != null → resolve title → Resumable
                                                   ├─ resumeLesson == null ─────────────────→ Plain
                                                   └─ any RuntimeException (403/404/401/503) → Plain

## File Changes

| File | Action | Description |
|---|---|---|
| `application/dto/CourseProgress.java` | Create | Wire record + nested `ResumeLesson(lessonId, moduleId, positionSeconds, completed)` |
| `application/usecase/CourseDetailView.java` | Create | Sealed `Plain(course)` / `Resumable(course, Resume)` |
| `application/usecase/GetCourseDetailViewUseCase{,Impl}.java` | Create | Replaces `GetCourseDetailUseCase{,Impl}` (deleted) |
| `application/port/out/VirtualApiClient.java` | Modify | Add `getCourseProgress` |
| `infrastructure/adapter/VirtualApiAdapter.java` | Modify | `/api/v1/virtual/courses/{courseId}/progress`, mandatory Bearer |
| `infrastructure/web/controller/CourseDetailController.java` | Modify | Read token attribute, exhaustive `switch` |
| `infrastructure/config/UseCaseConfig.java` | Modify | Swap the bean |
| `templates/course-detail.html` | Modify | `th:if="${resume}"` → "Continuar"; else "Comenzar" |

## Interfaces / Contracts

```java
public sealed interface CourseDetailView {
    record Plain(CourseDetail course) implements CourseDetailView {}
    record Resumable(CourseDetail course, Resume resume) implements CourseDetailView {}
    record Resume(String lessonId, String title, int percentage,
                  int completedLessons, int totalLessons) {}
}
```

`positionSeconds` is read from the wire DTO but deliberately not carried into
`Resume`: the player cannot seek, so the link is
`/courses/{courseId}/lessons/{lessonId}` only.

## Testing Strategy

| Layer | What | Where |
|---|---|---|
| Unit | `403`/`404`/`5xx`/`401` mapping; Bearer always present; `requireNonNull` on null token | `VirtualApiAdapterTest` |
| Unit | `Resumable` + title resolution; `resumeLesson == null` → `Plain`; each exception → `Plain`; null token → zero progress calls; unresolvable lessonId → `Plain` | `GetCourseDetailViewUseCaseImplTest` |
| Unit | `switch` adds `resume` only for `Resumable` | `CourseDetailControllerTest` |
| Integration | entitled+progress → "Continuar" + correct link; entitled+zero → "Comenzar"; `403` → byte-equivalent to anonymous CTA; anonymous → `verify(0, getRequestedFor(progressUrl))`; progress `503` → page still `200` with catalog data | `VirtualLearningViewIntegrationTest` (login→cookie pattern) |

Layer floors (`bff` flat 85%) are unaffected; `BffArchitectureTest` stays
green — no new `application → infrastructure` import.

## Threat Matrix

N/A — no routing change, shell, subprocess, VCS/PR automation,
executable-file classification, or process-integration boundary.

## Migration / Rollout

No migration. BFF-only; revert the PR to restore #170 behaviour.

## Open Questions

None.

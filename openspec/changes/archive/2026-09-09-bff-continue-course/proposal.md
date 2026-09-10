# Proposal: BFF "Continuar Curso" CTA on Course Detail

## Intent

Issue #58 (US-VIRTUAL-008, v0.3.0): course detail (#170) renders identically
for every visitor and ignores saved progress (#52's aggregate). Returning
entitled students cannot see where they left off. This change fetches
progress for authenticated visitors and shows "Continuar" (percentage,
counts, resume link) when progress exists, else "Comenzar".

## Scope

### In Scope
- `VirtualApiClient.getCourseProgress` for `GET .../courses/{courseId}/progress` (Bearer-required, called only when a token exists).
- `CourseDetailController` conditionally fetches progress via `TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE`, mirroring the lesson-call pattern.
- Resolve `resumeLesson.lessonId` against `CourseDetail.modules[].lessons[]` for title + link (reuse `flattenLessons`/`indexOfLesson`).
- `course-detail.html`: "Continuar" when `resumeLesson != null`; else unchanged ("Comenzar").
- Adapter maps `403`/`401` on the progress call to "no progress", never a distinct error state.
- Tests extend `VirtualLearningViewIntegrationTest`'s login→cookie pattern.

### Out of Scope
- `api:virtual`'s progress contract/policy/assembler (#52 shipped, frozen).
- Lesson-player view or progress-writing (read-only consumer here).
- A distinct "subscription lapsed" UI state (rejected below).

## Locked Product Decisions (do not re-open)

**Three outcomes collapse to two UI states.** The aggregate has three
outcomes; the issue names only two. `403` (no entitlement) is neither "has
progress" nor "zero progress yet" — mapping it to "Comenzar" would hide a
lapsed subscriber's history. **User-confirmed**: treat `403` exactly like
anonymous — no progress call without a token, and a mid-flow `403` degrades
like "no token" (#170's anonymous CTA). No third state. Two states only:
"Continuar" (`resumeLesson != null`) vs. a shared "nothing to show"
(zero-lesson, zero-progress, non-entitled all collapse here).

**"Continuar" links to the lesson, not a timestamp.** Verified in source:
`lesson.html`/`LessonViewController`/`GetLessonViewUseCaseImpl` have no
mechanism to start playback at a given second, and the BFF never calls
`PUT .../lessons/{id}/progress` — nothing writes `positionSeconds` from this
codebase today. So "Continuar" cannot honestly promise seek-to-position; it
can only promise returning to the right lesson. **User-confirmed**:
`resumeLesson.lessonId` drives the link target only; `positionSeconds` is
read for percentage math (if needed) but not passed to the player. Seek-to-
position is out of scope, deferred to a future change that also teaches the
player to consume it and the BFF to write it during playback.

## Capabilities

**New**: None.
**Modified**:
- `virtual-course-detail-view`: no longer identical for anonymous/authenticated visitors.
- `virtual-api-integration`: adds a fourth outbound call (progress) with its own Bearer-conditional and 403/401 mapping.

## Approach

Reuse #170 patterns: conditional-Bearer via token attribute, lessonId
cross-referencing against `CourseDetail`, sealed/nullable view-model split
like `LessonView.Playable/Sample` (shape left to design). Catalog fetch stays
unauthenticated and first; progress only fetches with a token; any progress
failure degrades to "no progress" without failing the page.

## Affected Areas

| Area | Impact |
|------|--------|
| `VirtualApiClient`/`VirtualApiAdapter` | Add `getCourseProgress`; map 403/401 → no-progress |
| `CourseDetailController` | Conditional progress fetch |
| Course-detail use case | Resolve `resumeLesson` → title/link |
| `course-detail.html` | "Continuar" vs "Comenzar" |
| `VirtualLearningViewIntegrationTest` | New progress-state cases |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| 403-conflation surprises reviewers | Low | Locked decision documented in spec/design |
| Unmapped 401 leaks an error page | Medium | Explicit test: 401 degrades like "no token" |
| Progress call adds latency | Low | Only fires with a token |

## Rollback Plan

BFF-only, no schema/upstream change, no new routes. Revert the PR: controller
stops calling progress; template and adapter revert with it.

## Dependencies

- #52 (progress aggregate) — shipped, read-only consumer.
- #170 (course-detail + lesson-sample) — shipped, extended here.

## Success Criteria

- [ ] Entitled student with progress sees "Continuar" with correct data and a working link.
- [ ] Entitled student with zero progress sees "Comenzar", not "Continuar 0%".
- [ ] Non-entitled (403) student sees the anonymous rendering.
- [ ] Anonymous visitor triggers no progress call.
- [ ] 401 has an explicit, tested graceful-degradation branch.

## Resolved Questions

1. **Resume-link target**: resolved above — lesson-level link only, no seek
   (verified: no existing mechanism to build one).
2. **Zero-lesson course**: same "Comenzar"/no-CTA treatment as any
   zero-progress course — no template in this codebase distinguishes them,
   and the aggregate itself produces the identical `resumeLesson: null`
   shape for both, so there is nothing to tell them apart on.
3. **Percentage display**: whole number. Checked every existing template
   (`course-detail.html`, `lesson.html`) — none render a percentage today,
   so there is no prior convention to break; `CourseProgressView.percentage`
   is already an `int` upstream, so whole-number display requires no
   formatting decision at all.

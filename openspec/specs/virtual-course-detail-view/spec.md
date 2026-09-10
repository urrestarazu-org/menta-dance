# Virtual Course Detail View Specification

## Purpose

Give the BFF a course-detail page, reachable without authentication, that renders a
course's modules and lessons — with a free-vs-restricted marker per lesson — from
`api:app`'s public catalog endpoint. This is the entry point of the browse-to-subscribe
funnel and the source of lesson-summary metadata that the lesson view (see
`virtual-lesson-view`) needs to render a sample gate on denial.

## Requirements

### Requirement: Anonymous course detail access

The BFF MUST expose `GET /courses/{courseId}` without requiring authentication.
Anonymous visitors, and authenticated visitors with no current course
entitlement or no resumable progress, MUST see the same catalog rendering:
course title, description, and module/lesson tree, with no percentage,
completed/total counts, or resume link. Personalization for an authenticated,
entitled visitor with resumable progress is defined by "Progress-aware
personalization for entitled visitors with resumable progress" below.

(Previously: this requirement additionally asserted the route renders
identically for anonymous and authenticated visitors in every case, with no
personalization ever occurring on this page. This change introduces
conditional personalization for an authenticated, entitled visitor whose
progress aggregate reports a resumable lesson; the identical-rendering
guarantee now holds only for anonymous visitors and for authenticated
visitors who are not entitled or have no resumable progress.)

#### Scenario: Anonymous visitor views course detail

- GIVEN an anonymous visitor with no session
- WHEN they request `GET /courses/{courseId}` for a published course
- THEN the BFF renders the course title, description, and module/lesson tree
- AND no authentication redirect occurs

#### Scenario: Authenticated visitor without a resumable progress state sees the anonymous rendering

- GIVEN an authenticated visitor with a valid session and a valid access token
- AND either they have no current entitlement for the course, or the course-
  progress aggregate reports no resumable lesson (zero progress or a zero-
  lesson course)
- WHEN they request the same course detail page
- THEN the BFF renders the identical module/lesson tree and markers as the
  anonymous case
- AND the page shows no percentage, no completed/total counts, and no resume
  link

### Requirement: Progress-aware personalization for entitled visitors with resumable progress

When the incoming request carries `TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE`
and the upstream course-progress aggregate (`GET
/api/v1/virtual/courses/{courseId}/progress`) returns `200` with
`resumeLesson` non-null, the BFF MUST render a "Continuar" call to action on
`GET /courses/{courseId}` showing:

- the course's `completedLessons` and `totalLessons` counts;
- `percentage` as a plain whole number, with no additional rounding or
  formatting;
- a link to `/courses/{courseId}/lessons/{resumeLesson.lessonId}` — the
  existing lesson route — labeled with that lesson's title, resolved by
  cross-referencing `resumeLesson.lessonId` against the already-fetched
  `CourseDetail.modules[].lessons[]` (the course-progress aggregate does not
  carry a lesson title itself).

#### Scenario: Entitled student mid-course sees Continuar with accurate data and a working link

- GIVEN an authenticated, entitled visitor whose course-progress aggregate
  returns `resumeLesson` with a `lessonId` present in the course's module tree
- WHEN they request the course detail page
- THEN the BFF renders "Continuar" with the aggregate's `completedLessons`,
  `totalLessons`, and `percentage` as a whole number
- AND the "Continuar" link targets `/courses/{courseId}/lessons/{lessonId}`
  for that `resumeLesson.lessonId`
- AND the link is labeled with the resolved lesson's title from the course's
  module tree

### Requirement: Resume link carries no seek or position hint

The "Continuar" link MUST be built from `resumeLesson.lessonId` only. It MUST
NOT include `positionSeconds`, a query string, a URL fragment, or any other
parameter that hints at a playback position. Resuming lands the visitor on
the lesson page exactly as `/courses/{courseId}/lessons/{lessonId}` renders
it for any other visitor; seeking to a saved position is out of scope for
this change.

#### Scenario: Continuar link omits position information

- GIVEN a rendered "Continuar" call to action for an entitled visitor with
  resumable progress
- WHEN the link's target URL is inspected
- THEN it matches `/courses/{courseId}/lessons/{lessonId}` exactly
- AND it contains no query string, fragment, or path segment derived from
  `positionSeconds`

**Note on `resumeLesson.lessonId` always resolving**: `api:virtual` has no
lesson-deletion use case and no lesson-archival/unpublish state today (a
lesson, once created, is permanent and always present in its course's
catalog tree; verified against `api:virtual`'s use-case inventory). A saved
`resumeLesson.lessonId` therefore cannot go stale relative to
`CourseDetail.modules[].lessons[]` under the system's current capabilities —
this change does not need a fallback for an unresolvable resume-lesson id,
and design should not add one speculatively. If a future change introduces
lesson deletion or archival, this note becomes stale and the gap must be
revisited then.

### Requirement: Non-entitled and zero-progress states render identically to anonymous

Course detail MUST render exactly as it does for an anonymous visitor — no
percentage, no completed/total counts, no resume link — in every one of the
following cases:

1. no access token is present on the incoming request;
2. the course-progress aggregate returns `200` with `resumeLesson` null,
   whether because the visitor has zero progress in a non-empty course or
   because the course itself has zero lessons (the aggregate produces the
   identical shape for both, and this page MUST NOT attempt to distinguish
   them);
3. the course-progress aggregate returns `403` (the authenticated visitor
   holds no current entitlement for the course).

There MUST be no third, distinct rendering for a non-entitled authenticated
visitor (e.g. no "your subscription lapsed" messaging on this page) — case 3
is observably identical to case 1.

#### Scenario: Entitled student with zero progress sees Comenzar, not Continuar at zero percent

- GIVEN an authenticated, entitled visitor whose course-progress aggregate
  returns `200` with `resumeLesson` null
- WHEN they request the course detail page
- THEN the BFF renders the anonymous-equivalent view
- AND it does not render "Continuar", a percentage, or completed/total counts

#### Scenario: Zero-lesson course renders the same as zero progress

- GIVEN an authenticated, entitled visitor for a course with no lessons
- WHEN they request the course detail page
- THEN the BFF renders the anonymous-equivalent view, indistinguishable from
  the zero-progress case

#### Scenario: Non-entitled authenticated visitor sees the anonymous rendering

- GIVEN an authenticated visitor with a valid access token but no current
  entitlement for the course (the course-progress aggregate returns `403`)
- WHEN they request the course detail page
- THEN the BFF renders exactly the same page an anonymous visitor would see
- AND no distinct "no longer subscribed" or similar messaging appears

### Requirement: Progress-call failure never breaks course detail rendering

Course detail's own content (title, description, module/lesson tree) MUST
always be sourced and rendered from the catalog call alone. A failure on the
supplementary course-progress call — any `5xx`, timeout, or an unexpected
`401` — MUST degrade to the same rendering as "no progress" (identical to the
anonymous case) and MUST NOT prevent the page from rendering, MUST NOT
surface the upstream `application/problem+json` body, and MUST NOT fall
through to the BFF's generic error view. This is a stronger guarantee than
the existing catalog-failure handling: catalog failures MAY still render the
generic error view (unchanged), but progress failures never do, because
progress is supplementary data, not the page's own content.

#### Scenario: Progress upstream 5xx does not break course detail

- GIVEN an authenticated, entitled visitor
- AND the course-progress aggregate returns `503`
- WHEN they request the course detail page
- THEN the BFF still renders the course title, description, and module/lesson
  tree from the catalog call
- AND the page shows no percentage, counts, or resume link
- AND no upstream problem-detail body or generic error view is shown in place
  of the course content

#### Scenario: Unexpected 401 on the progress call degrades quietly

- GIVEN an authenticated visitor whose access token unexpectedly fails
  validation on the progress call alone (`401`)
- WHEN they request the course detail page
- THEN the BFF renders course detail exactly as it would for a visitor with
  no resumable progress
- AND no error is surfaced to the browser

### Requirement: Module and lesson tree with free/premium markers

The BFF MUST render every module's lessons in catalog order, marking each lesson as
"free" or "restricted" from its `isFree` flag, and MUST show the course's aggregate
stats (module count, lesson count, total duration) from the catalog response.

#### Scenario: Free and premium lessons are distinctly marked

- GIVEN a course whose modules contain a mix of `isFree: true` and `isFree: false` lessons
- WHEN the course detail page renders
- THEN each lesson shows a marker matching its `isFree` value
- AND the page shows the course's module count, lesson count, and total duration

### Requirement: Lesson links carry the course id

Every lesson link the course detail page renders MUST point to the nested route
`/courses/{courseId}/lessons/{lessonId}` (D1), never a flat `/lessons/{lessonId}` link,
so navigating from course detail already establishes the deep-link contract the lesson
view depends on.

#### Scenario: Lesson link navigation

- GIVEN a rendered course detail page
- WHEN a visitor inspects or follows a lesson link
- THEN the link target includes both the current `courseId` and the lesson's `lessonId`

### Requirement: Graceful degradation on upstream failure

When the catalog call returns `404` (course not found) or `503` (upstream unavailable,
with `Retry-After`), the BFF MUST render its existing generic error view and MUST NOT
leak the upstream `application/problem+json` body or status detail to the browser.

#### Scenario: Course not found

- GIVEN a `courseId` with no matching published course
- WHEN a visitor requests its course detail page
- THEN the BFF renders the existing error view
- AND no catalog problem-detail body reaches the response

#### Scenario: Catalog upstream unavailable

- GIVEN the catalog endpoint returns `503` with `Retry-After: 30`
- WHEN a visitor requests any course detail page
- THEN the BFF renders the existing error view
- AND does not expose the upstream retry header or problem detail to the browser

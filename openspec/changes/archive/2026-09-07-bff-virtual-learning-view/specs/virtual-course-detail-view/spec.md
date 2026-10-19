# Virtual Course Detail View Specification

## Purpose

Give the BFF a course-detail page, reachable without authentication, that renders a
course's modules and lessons — with a free-vs-restricted marker per lesson — from
`api:app`'s public catalog endpoint. This is the entry point of the browse-to-subscribe
funnel and the source of lesson-summary metadata that the lesson view (see
`virtual-lesson-view`) needs to render a sample gate on denial.

## Requirements

### Requirement: Anonymous course detail access

The BFF MUST expose `GET /courses/{courseId}` without requiring authentication. The
route MUST render identically for anonymous and authenticated visitors — course detail
is access-agnostic (D2), so no personalization or entitlement check happens on this page.

#### Scenario: Anonymous visitor views course detail

- GIVEN an anonymous visitor with no session
- WHEN they request `GET /courses/{courseId}` for a published course
- THEN the BFF renders the course title, description, and module/lesson tree
- AND no authentication redirect occurs

#### Scenario: Authenticated visitor sees the same rendering

- GIVEN an authenticated visitor with a valid session
- WHEN they request the same course detail page
- THEN the BFF renders the identical module/lesson tree and markers as the anonymous case

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

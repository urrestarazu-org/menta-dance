# Virtual Lesson View Specification

## Purpose

Give the BFF a lesson-player page at the nested, deep-linkable route
`/courses/{courseId}/lessons/{lessonId}` (D1) that renders the API's already-made
access decision: a player for free or entitled content, or a BFF-assembled sample view
with a subscription call-to-action for everyone else. The BFF renders this decision; it
never evaluates entitlement itself (`virtual/spec.md` line 19 — an existing constraint
this change honors, not modifies).

## Requirements

### Requirement: Deep-linkable nested route with course context

The BFF MUST expose `GET /courses/{courseId}/lessons/{lessonId}` without requiring
authentication, and MUST fetch the course detail for `courseId` on every request to
resolve the target lesson's summary (title, duration, `isFree`, order) before rendering
anything else. This resolution MUST NOT depend on cached state or prior navigation
within the session (D1).

#### Scenario: Deep link to a premium lesson without prior navigation

- GIVEN a visitor with no prior session activity opens `/courses/{courseId}/lessons/{lessonId}` directly
- WHEN the lesson is premium and the visitor is not entitled
- THEN the BFF still renders a complete sample view, using course detail fetched for this request

#### Scenario: Unknown course or lesson id

- GIVEN a `courseId` or `lessonId` with no matching resource
- WHEN a visitor requests the lesson page
- THEN the BFF renders the existing error view without leaking upstream detail

### Requirement: Playback is always obtained from the stream endpoint

A `200` from the lesson endpoint — free/preview (`access.preview: true`) or entitled
premium (`access.requiresSubscription: false` with a `videoId`) — means access was
granted, not that a playable URL was supplied. The lesson detail response never carries
one: a free lesson reports `videoId: null` because the detail endpoint withholds the raw
video id, not because no stream exists. The BFF MUST therefore call `/stream` on every
granted lesson, of either kind, to obtain the signed playback URL, propagating the
caller's Bearer token when one is present (see `virtual-api-integration`).

The stream endpoint authorizes any caller for a free lesson, including an anonymous one,
so a free lesson MUST NOT require authentication at any point in this flow.

#### Scenario: Anonymous visitor plays a free lesson

- GIVEN an anonymous visitor requests a lesson with `isFree: true`
- WHEN the lesson endpoint returns `200` with `videoId: null`
- THEN the BFF calls the stream endpoint with no `Authorization` header
- AND renders a player with the signed URL that call returns
- AND no authentication is required at any step

#### Scenario: Entitled student plays a premium lesson

- GIVEN an authenticated, entitled student requests a premium lesson
- WHEN the lesson endpoint returns `200` with a `videoId`
- THEN the BFF calls the stream endpoint with the caller's Bearer token
- AND renders a player with the signed URL that call returns

### Requirement: BFF-assembled sample view on subscription denial

When the lesson endpoint returns a bare `403` (`LESSON_FORBIDDEN_SUBSCRIPTION_REQUIRED`),
the BFF MUST NOT attempt to parse lesson metadata from that response body — it has none.
The BFF MUST instead render a sample view built from the course-detail lesson summary
already fetched for this request (title, duration) plus a BFF-owned subscription
call-to-action. This view MUST NOT include a `videoId`, a stream URL, or any call to the
`/stream` endpoint.

Until issue #177 delivers a BFF plans page, that call-to-action MUST be a message with no
navigable link. No BFF plans page exists today, and a placeholder link would repeat the
debt pattern this change exists to correct.

#### Scenario: Non-entitled visitor sees the sample view, no stream leak

- GIVEN a non-entitled visitor (anonymous or authenticated without entitlement) requests a premium lesson
- WHEN the lesson endpoint returns bare `403`
- THEN the BFF renders the lesson title and duration from course detail plus a subscription CTA
- AND the response contains no `videoId` and no stream URL
- AND the BFF makes no request to the `/stream` endpoint

### Requirement: Previous/next lesson navigation

When the lesson endpoint returns `200`, the BFF MUST render previous/next links from its
`navigation` block. When the lesson endpoint denies with `403` (which carries no
navigation block), the BFF MUST derive previous/next from the course-detail module and
lesson ordering instead, so navigation remains available on the sample view too.

#### Scenario: Navigation from the upstream block when access is granted

- GIVEN a granted lesson request returns a `navigation` block with `previousLesson`/`nextLesson`
- WHEN the page renders
- THEN prev/next links reflect that block

#### Scenario: Navigation derived from course order when denied

- GIVEN a denied lesson request (bare `403`, no navigation block)
- WHEN the page renders the sample view
- THEN prev/next links are derived from the lesson's order in the course-detail module tree

### Requirement: Graceful degradation on upstream failure

A `404` (missing/malformed lesson id) or `503` (upstream unavailable) from either the
catalog or the lesson/stream calls MUST render the BFF's existing error view without
leaking upstream problem detail, and MUST NOT render a partial player.

#### Scenario: Stream call fails after a granted lesson response

- GIVEN a `200` lesson response granting premium access
- WHEN the subsequent `/stream` call returns `503`
- THEN the BFF renders the existing error view instead of a partial or broken player

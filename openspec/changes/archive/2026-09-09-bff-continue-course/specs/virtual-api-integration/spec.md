# Delta for Virtual API Integration

## MODIFIED Requirements

### Requirement: Outbound port and adapter for catalog and lesson calls

The BFF MUST define an outbound port covering course-detail retrieval
(`GET /api/v1/catalog/courses/{courseId}`), lesson-detail retrieval
(`GET /api/v1/virtual/lessons/{lessonId}`), lesson-stream retrieval
(`GET /api/v1/virtual/lessons/{lessonId}/stream`), and course-progress
retrieval (`GET /api/v1/virtual/courses/{courseId}/progress`), implemented by
a single WebClient adapter bound to `menta.api.base-url`. The adapter MUST
map `404` to a not-found exception, `503` to an upstream-unavailable
exception preserving the `Retry-After` value, and `403` to a forbidden
exception without attempting to parse a response body, for every one of the
four calls.

(Previously: this requirement covered only the course-detail, lesson-detail,
and lesson-stream calls. It now also covers the course-progress call
introduced by this change, applying the same typed-exception mapping for
`404`, `503`, and `403`.)

#### Scenario: Adapter maps upstream statuses to typed exceptions

- GIVEN the upstream returns `404`, `503`, or `403` for any of the four calls
- WHEN the adapter processes the response
- THEN it raises the matching typed exception (not-found, upstream-
  unavailable with retry hint, or forbidden) without exposing the raw
  problem-detail body to callers

## ADDED Requirements

### Requirement: Course-progress call is Bearer-required and never attempted without a token

The BFF MUST define an outbound method for `GET
/api/v1/virtual/courses/{courseId}/progress` that always includes
`Authorization: Bearer <token>`. Unlike the lesson-detail and lesson-stream
calls, which have both an authenticated and an anonymous request shape, the
course-progress call has no anonymous form at all: the BFF MUST NOT invoke
this call when `TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE` is absent from the
incoming request. This is stronger than omitting the `Authorization` header —
the outbound call itself must never be made.

#### Scenario: Anonymous request triggers zero progress calls

- GIVEN an incoming request carries no `TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE`
- WHEN the BFF renders course detail for that request
- THEN it makes no outbound call to the course-progress endpoint at all

#### Scenario: Authenticated request includes the Bearer token on the progress call

- GIVEN an incoming request carries `TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE`
- WHEN the BFF calls the course-progress endpoint
- THEN the outbound request includes `Authorization: Bearer <token>` with
  that exact token

### Requirement: Progress-call 403 and unexpected 401 map to the same single no-progress outcome

The adapter MUST map both `403` (the authenticated caller lacks a current
entitlement) and `401` (unexpected, since the call is only ever made with a
token) on the course-progress call to the same outcome consumed by the
caller: "no progress data available." The caller MUST NOT implement distinct
branches for these two statuses — both collapse into one no-progress case, so
that a lapsed or never-subscribed visitor and an unexpected token failure are
handled by identical code, never by a status-specific special case.

#### Scenario: 403 on the progress call yields the no-progress outcome

- GIVEN the course-progress call returns `403`
- WHEN the adapter processes the response
- THEN it yields the same no-progress outcome the caller uses for "no
  resumable progress"

#### Scenario: Unexpected 401 on the progress call yields the identical outcome as 403

- GIVEN the course-progress call unexpectedly returns `401`
- WHEN the adapter processes the response
- THEN it yields the identical no-progress outcome as the `403` case, not a
  distinct error path

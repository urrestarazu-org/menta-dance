# Virtual API Integration Specification

## Purpose

Give the BFF an outbound port and WebClient adapter for the upstream catalog and lesson
endpoints, mirroring the existing `AuthApiClient`/`AuthApiAdapter` pattern, with
conditional Bearer-token propagation and the `permitAll` wiring anonymous browsing
requires.

## Requirements

### Requirement: Outbound port and adapter for catalog and lesson calls

The BFF MUST define an outbound port covering course-detail retrieval
(`GET /api/v1/catalog/courses/{courseId}`), lesson-detail retrieval
(`GET /api/v1/virtual/lessons/{lessonId}`), and lesson-stream retrieval
(`GET /api/v1/virtual/lessons/{lessonId}/stream`), implemented by a single WebClient
adapter bound to `menta.api.base-url`. The adapter MUST map `404` to a not-found
exception, `503` to an upstream-unavailable exception preserving the `Retry-After`
value, and `403` to a forbidden exception without attempting to parse a response body.

#### Scenario: Adapter maps upstream statuses to typed exceptions

- GIVEN the upstream returns `404`, `503`, or `403` for any of the three calls
- WHEN the adapter processes the response
- THEN it raises the matching typed exception (not-found, upstream-unavailable with
  retry hint, or forbidden) without exposing the raw problem-detail body to callers

### Requirement: Catalog call never carries an Authorization header

The course-detail call MUST NOT include an `Authorization` header under any
circumstance, authenticated or anonymous, because catalog access is caller-agnostic (D2).

#### Scenario: Authenticated caller's catalog call is unauthenticated

- GIVEN an authenticated visitor with a valid access token
- WHEN the BFF calls `GET /api/v1/catalog/courses/{courseId}` on their behalf
- THEN the outbound request carries no `Authorization` header

### Requirement: Conditional Bearer propagation on lesson and stream calls only

The lesson-detail and lesson-stream calls MUST include `Authorization: Bearer <token>`
only when `TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE` is present on the incoming
request. When that attribute is absent (anonymous request), the calls MUST omit the
`Authorization` header entirely — never send it blank or empty (D2).

#### Scenario: Authenticated request forwards the token to the lesson call

- GIVEN an incoming request carries `TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE`
- WHEN the BFF calls the lesson-detail or lesson-stream endpoint
- THEN the outbound request includes `Authorization: Bearer <token>` with that exact token

#### Scenario: Anonymous request omits the Authorization header

- GIVEN an incoming request carries no `TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE`
- WHEN the BFF calls the lesson-detail or lesson-stream endpoint
- THEN the outbound request has no `Authorization` header at all

### Requirement: Anonymous-reachable course and lesson routes

`BffSecurityConfig` MUST permit unauthenticated access to `/courses/{courseId}` and
`/courses/{courseId}/lessons/{lessonId}`, so anonymous requests reach the controllers
(which then call the adapter with no token) instead of being redirected to `/login`.

#### Scenario: Anonymous request is not redirected to login

- GIVEN an anonymous visitor with no session
- WHEN they request either the course-detail or the lesson route
- THEN the BFF serves the page directly with no redirect to `/login`

### Requirement: The new permitAll entries widen access to nothing else

The `permitAll` additions MUST match only the two new course and lesson routes. Every
route that required authentication before this change MUST still require it afterwards.
This constraint MUST be covered by its own regression tests rather than inferred from the
positive anonymous-access cases above: an over-broad matcher fails open silently, serving
protected pages to anonymous callers with no error to notice.

#### Scenario: Previously protected routes still redirect anonymous callers

- GIVEN an anonymous visitor with no session
- WHEN they request `/dashboard`, or any route other than `/login`, `/error`,
  `/actuator/health`, and the two new course/lesson routes
- THEN the BFF still redirects them to `/login`, exactly as before this change

#### Scenario: The lesson matcher does not shadow unrelated paths

- GIVEN an anonymous visitor requests a path that merely resembles the new routes but is
  not one of them
- WHEN the security chain evaluates it
- THEN it is treated as authenticated-only, not permitted

# Virtual Subscription Access Specification

## Purpose

Make Virtual authorize access to premium lessons, streams, and downloadable materials from Billing's authoritative subscription snapshot, while preserving the public preview cascade defined in US-VIRTUAL-003 and US-VIRTUAL-007. A streaming or download URL is an authorization capability and must never be created before that decision succeeds.

## Requirements

### Requirement: Ordered public-access cascade

Before consulting Billing, Virtual MUST evaluate the requested lesson in this order. The
first matching rule MUST grant access without authentication:

1. `lesson.isFree == true`;
2. the lesson's module has `isPreview == true`.

When neither rule grants access — including when the lesson's course belongs to no plan —
Virtual MUST require an authenticated student and resolve entitlement through the shared
Billing port; absence of a current entitlement MUST deny with `403`. The BFF and every
browser client MUST NOT make this authorization decision.

(Previously: a third rule granted public access whenever the lesson's course belonged to no
plan. D7 removes it — an unplanned course now falls through to the same entitlement check as
a planned course and denies by default, instead of being treated as a permanent public grant.)

#### Scenario: Free lesson is public

- GIVEN an anonymous visitor requests a lesson with `isFree = true`
- WHEN Virtual resolves its access
- THEN it grants the request without consulting Billing
- AND lesson detail returns `200 OK`
- AND the stream endpoint may issue a fresh signed URL

#### Scenario: Preview module is public even when its lesson is not free

- GIVEN an anonymous visitor requests a lesson with `isFree = false` in a module with
  `isPreview = true`
- WHEN Virtual resolves its access
- THEN it grants the request without authentication or Billing entitlement
- AND the response identifies the lesson as preview content

#### Scenario: A course absent from every plan denies protected lessons for an anonymous visitor

- GIVEN a non-free lesson in a non-preview module whose course belongs to no billing plan
- WHEN an anonymous visitor requests lesson detail or its `/stream` resource
- THEN Virtual returns `403 application/problem+json` for both requests
- AND lesson detail exposes no video identifier
- AND the stream endpoint issues no signed URL

#### Scenario: A course absent from every plan denies protected lessons for an authenticated student without entitlement

- GIVEN the same non-free, non-preview lesson whose course belongs to no billing plan
- WHEN an authenticated student without a current Billing entitlement requests lesson detail
  or its `/stream` resource
- THEN Virtual returns `403 application/problem+json` with subscription-required guidance
- AND no video identifier or signed URL is exposed
- AND the response is identical in outcome to a planned course denial, so callers cannot
  distinguish "unplanned" from "planned without entitlement"

### Requirement: Premium entitlement from Billing snapshot

For a lesson not granted by the public cascade, Virtual MUST allow access only when the authenticated student has at least one Billing subscription whose authoritative snapshot includes the course and whose access is current at the injected clock. Virtual MUST query this through `VirtualCourseEntitlementPort`; it MUST NOT read Billing tables, repositories, JPA entities, SQL, HTTP endpoints, or messaging infrastructure.

Billing's frozen course snapshot at subscription activation is the entitlement source. A live-plan deactivation or removal of the course MUST NOT revoke a previously paid student's access before the snapshot's access period ends.

#### Scenario: Active current snapshot grants premium access

- GIVEN an authenticated student has an `ACTIVE`, current subscription snapshot containing the requested premium course
- WHEN the student requests lesson detail or its stream
- THEN detail returns `200 OK` without a streaming URL
- AND the stream endpoint returns a newly signed, temporary stream URL

#### Scenario: Live plan changes do not revoke the snapshot

- GIVEN an authenticated student has a current entitlement snapshot containing a premium course
- AND an administrator deactivates the originating plan or removes that course from its live definition
- WHEN the student requests the lesson or stream before the snapshot end date
- THEN Virtual grants access from the frozen snapshot

#### Scenario: One of several current subscriptions is sufficient

- GIVEN an authenticated student has multiple subscriptions
- AND at least one current entitlement snapshot contains the requested premium course
- WHEN the student requests the premium lesson
- THEN Virtual grants access without recording which plan was used

### Requirement: Protected-content denial and capability non-disclosure

Virtual MUST deny a protected lesson, stream, or material request with `403 Forbidden` and `application/problem+json` when the caller is anonymous or lacks a current Billing entitlement. The response MUST include a stable machine-readable reason suitable for subscription recovery.

On denial, Virtual MUST NOT generate, persist, cache, or expose a Bunny video identifier, signed stream URL, signed material-download URL, or any equivalent media capability. For lesson detail only, Virtual MAY return the safe preview metadata required by US-VIRTUAL-003: thumbnail, duration, description, and subscription-required guidance.

#### Scenario: Anonymous visitor is denied premium streaming

- GIVEN a premium lesson that is not free, not in a preview module, and belongs to a plan
- WHEN an anonymous visitor requests its stream
- THEN Virtual returns `403 application/problem+json`
- AND generates no signed URL

#### Scenario: Authenticated student without entitlement gets safe preview metadata only

- GIVEN an authenticated student without a current entitlement for a protected lesson
- WHEN the student requests lesson detail
- THEN Virtual returns `403 application/problem+json` with subscription-required guidance and safe preview metadata
- AND it exposes neither a video identifier nor a stream or download URL

#### Scenario: A protected material is denied with its lesson

- GIVEN a material attached to a protected lesson and a caller without entitlement
- WHEN the caller requests the material download endpoint
- THEN Virtual returns `403 application/problem+json`
- AND generates no signed material URL

### Requirement: Cancellation preserves paid access; expiry revokes it

The entitlement decision MUST follow the product semantics in US-BILLING-011. A `CANCELLED` subscription whose `endDate` has not passed MUST continue granting access to courses in its frozen snapshot: cancellation stops renewal and does not forfeit the paid period. At `endDate`, the subscription becomes `EXPIRED` and access MUST cease on the next authorization decision.

This requirement deliberately resolves the conflicting shorthand in US-VIRTUAL-007's test list in favor of US-BILLING-011's explicit cancellation scenario. Any policy that revokes access immediately on cancellation requires a separate change to US-BILLING-011 and the subscription model; it MUST NOT be introduced by this change.

#### Scenario: Cancellation preserves access through end date

- GIVEN a student has a `CANCELLED` subscription with a future `endDate` and a snapshot containing the course
- WHEN the student next requests the premium lesson
- THEN Virtual reads the current Billing snapshot
- AND grants access until `endDate`

#### Scenario: Expiry denies immediately on the next request

- GIVEN a student entitlement has reached its `endDate` or has status `EXPIRED`
- WHEN the student next requests a protected lesson, stream, or material
- THEN Virtual returns `403 application/problem+json`
- AND generates no media capability

#### Scenario: Unavailable Billing fails closed for protected content

- GIVEN no public-access rule applies
- AND Billing cannot answer the shared port call
- WHEN the student requests protected content
- THEN Virtual denies the request without issuing any media capability

### Requirement: Detail, stream, and material endpoints share one decision

Lesson detail, `GET /api/v1/virtual/lessons/{lessonId}/stream`, and the lesson-progress
and course-progress endpoints (`PUT`/`GET .../lessons/{lessonId}/progress`,
`POST .../lessons/{lessonId}/complete`, `GET .../courses/{courseId}/progress`) MUST
apply the same access cascade and entitlement semantics. Lesson detail MUST include
explicit metadata that distinguishes public/preview access from `requiresSubscription`;
it MUST never embed a streaming URL. A material endpoint does not yet exist and is out
of scope.

The OpenAPI contract and versioned Bruno collection MUST document and assert the
protected-path `403 application/problem+json` contract for every one of these
endpoints, including that denial yields no stream URL and no progress data.

(Previously: this requirement covered only lesson detail and the stream endpoint. It now
also extends the shared access cascade and denial semantics to the four lesson-progress
and course-progress endpoints introduced by `virtual-lesson-progress`.)

#### Scenario: An entitled student receives an URL only from the stream endpoint

- GIVEN an entitled student accesses a protected lesson
- WHEN the student requests lesson detail
- THEN the response identifies access as allowed but contains no video URL
- WHEN the student requests `/stream`
- THEN and only then Virtual returns a fresh signed URL

#### Scenario: A progress endpoint applies the identical denial as detail and stream

- GIVEN a protected lesson and a caller who is denied by the access cascade for lesson
  detail and `/stream`
- WHEN the same caller requests `PUT .../progress`, `GET .../progress`, or
  `POST .../complete` for that lesson
- THEN Virtual returns `403 application/problem+json` for each, matching the detail and
  stream denial exactly

#### Scenario: An entitled student's progress request succeeds under the same cascade that grants detail and stream

- GIVEN an entitled student who is granted access to a protected lesson's detail and
  stream
- WHEN the same student requests `PUT .../progress` or `POST .../complete` for that
  lesson
- THEN Virtual accepts the request under the identical access decision

### Requirement: Architectural boundaries and verification

Virtual domain and application code MUST remain framework-free. The cross-module entitlement port and status-event contract MUST be shared Java contracts; Billing owns subscription state and publication, Virtual owns its access decision and cache listener, and `api:app` only wires adapters. No foreign-module infrastructure dependency may be introduced.

The change MUST include unit, controller/integration, and architecture regressions for every public branch, active/current snapshot access, expired denial, cancellation-before-end-date access, frozen-snapshot behavior, denied capability non-disclosure, and per-user invalidation.

## Out of Scope

Changing subscription cancellation APIs, renewal, expiration scheduling, or the product semantics of US-BILLING-011; changing Bunny signing parameters, player controls, Android/BFF UI, course administration, or learning progress; and introducing Redis, HTTP, RabbitMQ, cross-module persistence access, or a distributed cache for entitlement authorization.

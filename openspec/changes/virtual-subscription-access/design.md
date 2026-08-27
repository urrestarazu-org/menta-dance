# Design: Virtual Subscription Access

**Change**: `virtual-subscription-access`
**Issue**: #56
**Status**: Ready for task decomposition
**Governing stories**: US-VIRTUAL-003, US-VIRTUAL-004, US-VIRTUAL-007, US-BILLING-011
**Related ADRs**: ADR-0021, ADR-0026, ADR-0039

## 1. Decision

Virtual owns the authorization decision for its content. It resolves the lesson
and its local public rules first, then asks Billing through one minimal shared
Java read contract for the remaining commercial facts. Billing remains the
source of truth for subscriptions and frozen course snapshots.

The decision order is fixed:

1. `VirtualLesson.isFree()` grants public access;
2. `VirtualModule.isPreview()` grants public access;
3. a course that belongs to no Billing plan grants public access;
4. otherwise, an authenticated user needs a current, `ASSIGNED` subscription
   snapshot containing the course.

The first three branches never query a user entitlement. No controller, BFF,
or browser decides this policy. A signed stream or material URL is issued only
after the shared decision has granted access.

### Authoritative subscription semantics

US-BILLING-011 is the authority where its wording conflicts with the older
short-form test list in US-VIRTUAL-007:

- `ACTIVE + ASSIGNED + endDate > now` grants access.
- `CANCELLED + ASSIGNED + endDate > now` **also grants access**. Cancellation
  stops renewal; it does not take away the paid period.
- `EXPIRED`, `PENDING`, `EXCEPTION`, an absent snapshot, or `endDate <= now`
  deny access.

This requires Billing's shared read implementation to stop treating
`SubscriptionStatus.ACTIVE` as the whole authorization rule. The entitlement
method must express “current paid snapshot”, not “currently renewing”.

## 2. Why this shape

The current implementation has two independently duplicated checks: detail
only understands `lesson.isFree`, and stream understands `lesson.isFree` plus a
boolean entitlement query. It has no module preview model, no course-plan
membership query, and the Billing implementation grants only `ACTIVE` records.
That cannot faithfully implement US-VIRTUAL-007 or US-BILLING-011.

A single application-level `LessonAccessPolicy` (or equivalently named use-case
collaborator) is introduced in Virtual and called by detail, stream, and the
future material endpoint. It receives local lesson/module/course data and an
optional caller identity. It returns an explicit application DTO such as:

```text
PUBLIC_FREE | PUBLIC_MODULE_PREVIEW | PUBLIC_UNPLANNED_COURSE |
SUBSCRIPTION_GRANTED | SUBSCRIPTION_REQUIRED
```

The DTO contains no `videoId`, signing input, signed URL, or download URL. That
keeps policy reusable without turning it into a capability carrier.

## 3. Cross-module contract

`api:shared` exposes the small Billing-owned Java contract; `api:virtual`
depends only on that contract and never on Billing persistence or application
classes. `api:app` supplies composition only.

Replace the ambiguous boolean-only interaction with a read model that can
answer both remaining commercial questions in one call, for example:

```java
CourseAccessSnapshot resolveCourseAccess(UUID userIdOrNull, String courseId);
```

where the shared value is limited to:

```text
courseInAnyPlan: boolean
currentEntitlement: boolean
```

Billing computes `currentEntitlement` from its persisted subscription snapshot
and injected Clock. For a null user it returns no entitlement without querying
subscriptions. It computes `courseInAnyPlan` from Billing's plan-course
repository by value, without exposing JPA entities or live plan course lists.
Virtual interprets those facts only after its local `isFree` and `isPreview`
checks.

This one contract avoids a Virtual-to-Billing HTTP call, foreign SQL join,
foreign repository injection, or a second query whose answers can observe
different subscription states. It remains a synchronous in-process Java
interface, as required by ADR-0039.

## 4. Data model and migration

`VirtualModule` currently has no `isPreview` field and `virtual_modules` has no
corresponding column. Add one Flyway migration in `api:app`:

```sql
ALTER TABLE virtual_modules
    ADD COLUMN is_preview BOOLEAN NOT NULL DEFAULT FALSE AFTER title;
```

Map it through Virtual's domain model, JPA entity/mapper, repository, management
commands/responses, and existing course-management tests. `false` preserves the
current access posture for existing modules.

No entitlement table, per-user course-access flag, cross-module foreign key, or
Virtual persistence is added. Billing's existing frozen
`billing_subscription_courses` snapshot remains the entitlement source.

## 5. Endpoint and capability boundary

| Endpoint | Allowed result | Protected result |
|---|---|---|
| `GET /api/v1/virtual/lessons/{lessonId}` | `200`; detail never contains a signed URL | `403 application/problem+json`, safe preview metadata only, no `videoId` |
| `GET /api/v1/virtual/lessons/{lessonId}/stream` | `200`; create a fresh Bunny URL after allow | `403 application/problem+json`; do not call signer |
| Material endpoint, when it exists | `200`; create a fresh download URL after allow | `403 application/problem+json`; do not call download signer |

The existing public-detail `200` “requires subscription” body must be changed
to the governing `403 application/problem+json` contract. The Problem Detail
uses a stable subscription-required code and recovery link/guidance; it may
include only the safe preview fields allowed by US-VIRTUAL-003. It never
includes identifiers or values that enable media retrieval.

`GetPublicLessonUseCaseImpl` and `GetPublicLessonStreamUseCaseImpl` delegate to
the shared Virtual policy rather than copying the cascade. The material endpoint
is not currently implemented, so no endpoint is invented in this change; its
future use case must depend on this same policy before it obtains any signer
input.

## 6. Freshness and invalidation

**No entitlement cache is introduced in #56.** ADR-0026 permits Caffeine only
for reconstructible data and says it is never the authority for security. More
importantly, direct Billing reads make expiry effective on the very next request
and make a future cancellation immediately reflect the source-of-truth result.
They satisfy the user-story invalidation intent without a stale-positive window.

US-BILLING-011's eventual cancellation implementation must publish the shared
`SubscriptionStatusChanged(userId, subscriptionId, status, occurredAt)` event
only after its transaction commits. Virtual may later add an infrastructure
listener that evicts *only* that user's Caffeine keys. That listener cannot
change the access decision:

- it evicts after cancellation, then the next query still grants through
  `endDate` because that is the paid-period policy;
- it evicts after expiry, then the next query denies;
- it must never globally clear entries, cache media capabilities, or make an
  allow decision when Billing is unavailable.

If a performance measurement later justifies a cache, its positive entries must
expire no later than `min(configuredTtl, subscription.endDate - now)` and its
keys must include user and course. That is a follow-up design, not implicit
scope here.

## 7. Failure, security, and observability

For protected content, an unavailable or malformed Billing answer fails closed:
return the subscription-required Problem Detail and do not invoke Bunny signing.
Public branches remain independent of Billing availability.

Log only the access-decision classification, lesson/course id, correlation id,
and authenticated user id where present. Do not log signed URLs, signer secrets,
or subscription snapshots. Existing JSON logging and OpenTelemetry correlation
rules remain applicable.

## 8. Test strategy (strict TDD)

### Domain/application tests

1. Each public branch grants for an anonymous caller and does not invoke the
   Billing contract: free lesson, preview module, unplanned course.
2. A planned premium course grants for a current `ACTIVE/ASSIGNED` snapshot
   containing its frozen course id.
3. A `CANCELLED/ASSIGNED` snapshot before `endDate` grants; at `endDate` it
   denies. `EXPIRED`, pending, exceptional, absent, and non-containing
   snapshots deny.
4. Removing a course from the live originating plan after activation does not
   affect the already-frozen snapshot entitlement.
5. Detail, stream, and material-policy callers obtain the same decision for
   the same input.

### Infrastructure/controller tests

1. The module-preview migration/entity/mapper round-trips `true` and defaults
   legacy rows to `false`.
2. Detail and stream return public `200` for all three cascade branches.
3. A protected anonymous or non-entitled request returns RFC 9457 `403
   application/problem+json`; assert no `videoId`, stream URL, or material URL
   appears.
4. Spy/mock the Bunny signer and prove denial does not call it; an entitled
   stream calls it exactly once and detail never does.
5. Billing contract tests prove current snapshot semantics, including cancelled
   before end date and expiry at the injected Clock boundary.
6. Existing ArchUnit tests remain green and gain a regression that Virtual has
   no dependency on Billing infrastructure/persistence.

### Contract tests

Update the OpenAPI paths/schemas/responses for detail and stream to document
`403 application/problem+json` and the absence of media capabilities. Update
versioned Bruno requests and assertions for anonymous public access, entitled
stream access, and protected denial. There is no material request to update
until that endpoint is introduced.

## 9. Implementation ordering

1. Add the module preview migration and propagate `isPreview` through Virtual's
   model and management persistence surface with tests.
2. Extend the shared Billing access read contract and its Billing implementation
   for plan membership plus frozen-snapshot current-access semantics; cover
   cancellation-through-end-date and expiry with a fixed Clock.
3. Build the Virtual `LessonAccessPolicy` against that contract and prove the
   full cascade in isolated tests.
4. Refactor detail and stream use cases to consume the policy, fail closed, and
   prevent signing before authorization.
5. Change controller exception/result mapping to a common RFC 9457 `403` and
   add no-leak integration coverage.
6. Update OpenAPI, Bruno, ArchUnit, and run module/integration/full quality
   gates.

## 10. Files expected to change

- `api/app/src/main/resources/db/migration/V*__virtual_module_preview.sql`
- `api/virtual/src/main/java/.../domain/model/VirtualModule.java`
- Virtual module persistence entity, mapper, repository adapters, management
  DTOs/controllers/configuration, and their tests
- `api/shared/src/main/java/com/menta/shared/billing/VirtualCourseEntitlementPort.java`
  plus its small shared access result
- `api/billing/src/main/java/.../VirtualCourseEntitlementService.java`,
  subscription/plan-course query ports/adapters as needed, and tests
- Virtual access-policy DTO/port/use-case collaborator; public detail/stream
  use cases, web DTO/controller/advice, configuration, and tests
- OpenAPI and `bruno/` requests/assertions for the existing lesson endpoints
- `openspec/changes/virtual-subscription-access/tasks.md`

No Android, BFF, Bunny signing-parameter, cancellation-endpoint, status-event,
or material-endpoint implementation is part of this change.

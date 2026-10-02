# Catalog Course Detail Specification

## Purpose

Public (unauthenticated) detail of a single catalog course, `GET /api/v1/catalog/courses/{courseId}`. A VIRTUAL id keeps its current detail; a PHYSICAL id, which answered 404 since #47, now answers 200 with the course data plus the upcoming scheduled sessions and their live availability, so prospects can see classes and free spots (#107, decisions D1-D8). The list endpoint, prices/`quoteEndpoint`, `from`/`to` parameters, pagination, BFF and Android are out of scope. Catalog cache and rate limit are tracked in #312 and are NOT requirements of this capability. Holds stay invisible (see `physical-capacity-hold`): only `availableSpots` is published.

Configuration contract (locked): property `catalog.physical.sessions.window-days`, integer number of days, default `30`, valid range 1 to 90 (environment variable `CATALOG_PHYSICAL_SESSIONS_WINDOW_DAYS`).

## Requirements

### Requirement: Virtual-first resolution of the detail

The system MUST resolve `{courseId}` against the virtual catalog first. If a published virtual course matches, the system MUST answer with the existing virtual detail, MUST NOT consult the physical module, and the virtual response body MUST remain byte-for-byte unchanged in shape. Only when the virtual lookup finds nothing MUST the system look for an active physical course.

#### Scenario: Virtual id answers the unchanged virtual detail

- GIVEN a published virtual course with id `V`
- WHEN a client requests `GET /api/v1/catalog/courses/V`
- THEN the response is 200 with the same virtual detail shape as before this change
- AND the physical module is not asked for the course or for sessions

#### Scenario: Virtual id never receives a sessions field

- GIVEN a published virtual course with id `V`
- WHEN a client requests its detail
- THEN the body contains no `sessions` property

### Requirement: Unknown ids answer an indistinguishable 404

When neither a published virtual course nor an active physical course matches `{courseId}`, the system MUST answer 404 `application/problem+json` with code `COURSE_NOT_FOUND`. An unknown id, an inactive physical course and any other missing case MUST be indistinguishable in status and body shape.

#### Scenario: Unknown id

- GIVEN no virtual or physical course with id `X`
- WHEN a client requests `GET /api/v1/catalog/courses/X`
- THEN the response is 404 with code `COURSE_NOT_FOUND`

#### Scenario: Inactive physical course is not enumerable

- GIVEN a physical course `P` that exists but is inactive
- WHEN a client requests its detail
- THEN the response is identical in status and body shape to the unknown-id 404

### Requirement: Physical detail returns course data

When a PHYSICAL id resolves to an active physical course, the system MUST answer 200 with `courseId`, `modality` = `PHYSICAL`, `title`, `level`, and the physical block (`professorName`, `dayOfWeek`, `startTime`, `capacity`), plus the detail-only `sessions` collection. The response MUST NOT contain prices or `quoteEndpoint`.

#### Scenario: Physical id answers 200 with course data

- GIVEN an active physical course `P` with upcoming scheduled sessions
- WHEN a client requests `GET /api/v1/catalog/courses/P`
- THEN the response is 200 with `modality` = `PHYSICAL` and the course and physical-block fields populated
- AND `sessions` is present

#### Scenario: Physical course without upcoming sessions

- GIVEN an active physical course with no scheduled session in the window
- WHEN a client requests its detail
- THEN the response is 200 with `sessions` equal to an empty array (not null, not absent)

### Requirement: Sessions are limited to a configurable forward window

The `sessions` collection MUST contain only SCHEDULED sessions whose `scheduledAt` lies in the half-open window `[now, now + W)`, where `now` is the request instant and `W` is the number of days set by `catalog.physical.sessions.window-days` (default 30). A session whose `scheduledAt` is strictly before `now` (already started) MUST be excluded. The endpoint MUST NOT accept `from`/`to` query parameters to alter the window. A non-positive `W` MUST be rejected at application startup.

#### Scenario: Default window of 30 days

- GIVEN the property is unset and sessions at `now + 1h`, `now + 29d` and `now + 30d` exactly
- WHEN a client requests the physical detail
- THEN `sessions` contains the first two and omits the one at `now + 30d`

#### Scenario: Configured window

- GIVEN `catalog.physical.sessions.window-days` = 7 and sessions at `now + 6d` and `now + 8d`
- WHEN a client requests the physical detail
- THEN `sessions` contains only the session at `now + 6d`

#### Scenario: Started session excluded

- GIVEN a SCHEDULED session whose `scheduledAt` is one minute before `now` and another one minute after
- WHEN a client requests the physical detail
- THEN only the later session is listed

#### Scenario: Session exactly at now is included

- GIVEN a SCHEDULED session with `scheduledAt` equal to `now`
- WHEN a client requests the physical detail
- THEN that session is listed

#### Scenario: from/to parameters are ignored

- GIVEN a request with `?from=...&to=...` for a physical id
- WHEN the detail is served
- THEN the window is still `[now, now + W)`

#### Scenario: Out-of-range window rejected

- GIVEN `catalog.physical.sessions.window-days` = 0, or a value above 90 (for example 91)
- WHEN the application starts
- THEN startup fails with a configuration error

### Requirement: Sessions are ascending and hard-capped at 100

`sessions` MUST be ordered ascending by `scheduledAt` and MUST contain at most 100 items. When more than 100 sessions qualify, the system MUST return the 100 earliest and MUST NOT signal truncation or paginate.

#### Scenario: Exactly 100 sessions

- GIVEN 100 qualifying sessions
- WHEN a client requests the physical detail
- THEN all 100 are returned in ascending `scheduledAt` order

#### Scenario: 101 sessions

- GIVEN 101 qualifying sessions
- WHEN a client requests the physical detail
- THEN exactly the 100 earliest are returned and the latest is omitted

### Requirement: Public session fields are minimal

Each session MUST expose exactly `sessionId`, `scheduledAt` (ISO-8601 UTC instant), `capacity` and `availableSpots`. `assignedSpots` and `activeCapacityHolds` MUST NOT be serialized anywhere in the response.

#### Scenario: Only public fields serialized

- GIVEN a session with assigned spots and an active hold
- WHEN the detail is serialized
- THEN the session object has exactly the four public keys
- AND neither `assignedSpots` nor `activeCapacityHolds` appears in the body

#### Scenario: scheduledAt format

- GIVEN a session scheduled at 22:00 UTC
- WHEN the detail is serialized
- THEN `scheduledAt` is an ISO-8601 UTC instant (e.g. `...T22:00:00Z`)

### Requirement: Sold-out shown, cancelled hidden

A SCHEDULED session with `availableSpots` = 0 MUST be listed with that value. A CANCELLED session MUST NOT be listed.

#### Scenario: Full session shown

- GIVEN a SCHEDULED session whose capacity is fully assigned
- WHEN a client requests the physical detail
- THEN the session is listed with `availableSpots` = 0

#### Scenario: Cancelled session hidden

- GIVEN a CANCELLED session inside the window
- WHEN a client requests the physical detail
- THEN it is not listed

### Requirement: Upstream failure degrades the whole detail

If the physical course lookup or the sessions lookup fails, the system MUST answer 503 `application/problem+json` with code `CATALOG_DEGRADED` and header `Retry-After: 30`, and MUST NOT return a partial detail (no course data with missing or null `sessions`).

#### Scenario: Sessions lookup fails

- GIVEN an active physical course and a failing sessions lookup
- WHEN a client requests its detail
- THEN the response is 503 `CATALOG_DEGRADED` with `Retry-After: 30`
- AND no course data is returned

#### Scenario: Physical module unavailable

- GIVEN the virtual lookup finds nothing and the physical course lookup fails
- WHEN a client requests the detail
- THEN the response is 503 `CATALOG_DEGRADED` with `Retry-After: 30`

### Requirement: List endpoint unchanged

`GET /api/v1/catalog/courses` MUST remain unchanged: physical items MUST NOT carry `sessions` and the list MUST NOT query session availability.

#### Scenario: List has no sessions

- GIVEN active physical courses with scheduled sessions
- WHEN a client requests the catalog list
- THEN no item contains `sessions`
- AND no session availability read is performed

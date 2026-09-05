# Virtual Lesson Progress Specification

## Purpose

Let an authenticated student save and resume lesson playback position, mark a lesson
complete, and see a derived per-course progress aggregate. This spec covers the API
contract only; consuming UI is out of scope.

## Requirements

### Requirement: Authentication is mandatory on every progress endpoint

All four progress endpoints (`PUT`/`GET .../lessons/{lessonId}/progress`,
`POST .../lessons/{lessonId}/complete`, `GET .../courses/{courseId}/progress`) MUST
require an authenticated caller. An anonymous request MUST receive `401` before any
access-cascade or ownership check runs.

#### Scenario: Anonymous save is rejected

- GIVEN an anonymous caller with no bearer token
- WHEN the caller sends `PUT /api/v1/virtual/lessons/{lessonId}/progress`
- THEN Virtual returns `401`
- AND no row is created or modified

#### Scenario: Anonymous read is rejected on every progress endpoint

- GIVEN an anonymous caller
- WHEN the caller requests any of `GET .../progress`, `POST .../complete`, or
  `GET .../courses/{courseId}/progress`
- THEN Virtual returns `401` for each

### Requirement: Progress reads and writes reuse the lesson access cascade

Saving position, reading position, and completing a lesson MUST evaluate the same
access decision as lesson detail and stream: free lesson, preview module, or a current
Billing entitlement. Denial MUST return `403 application/problem+json`, consistent with
non-disclosure semantics — no lesson metadata beyond what an equivalent denied detail
request would expose.

A lapsed subscription denies both new writes and reads of that lesson's progress; the
row is retained but not returned. This applies uniformly regardless of whether progress
already exists for that lesson.

#### Scenario: Save is allowed on a free lesson without entitlement

- GIVEN an authenticated student with no Billing entitlement
- AND the lesson has `isFree = true`
- WHEN the student sends `PUT .../progress` with a valid position
- THEN Virtual returns `200` and persists the position

#### Scenario: Save is denied on a protected lesson without entitlement

- GIVEN an authenticated student with no current Billing entitlement
- AND the lesson is not free and its module is not preview
- WHEN the student sends `PUT .../progress`
- THEN Virtual returns `403 application/problem+json`
- AND no position is persisted

#### Scenario: A lapsed subscriber loses read access to previously accessible progress

- GIVEN a student's subscription snapshot has expired
- AND a saved position exists for a lesson that requires entitlement
- WHEN the student sends `GET .../progress` for that lesson
- THEN Virtual returns `403 application/problem+json`

### Requirement: Position is validated integer seconds

Saved position MUST be a non-negative integer number of seconds MUST NOT exceed
`durationMinutes * 60` for the lesson. This bound is minute-granular by design and
intentionally tolerates up to 59 seconds of slack past the lesson's true duration; this
is accepted behavior, not a defect.

#### Scenario: Position within bounds is accepted

- GIVEN a lesson with `durationMinutes = 10`
- WHEN the student sends `PUT .../progress` with `position = 600`
- THEN Virtual returns `200` and persists `position = 600`

#### Scenario: Negative position is rejected

- GIVEN any lesson
- WHEN the student sends `PUT .../progress` with `position = -1`
- THEN Virtual returns `400 application/problem+json`
- AND no position is persisted

#### Scenario: Position exceeding the duration bound is rejected

- GIVEN a lesson with `durationMinutes = 10`
- WHEN the student sends `PUT .../progress` with `position = 601`
- THEN Virtual returns `400 application/problem+json`

### Requirement: Saving position is idempotent

Repeating `PUT .../progress` with the same position for the same `(student, lesson)`
pair MUST leave the persisted state unchanged and MUST return the same successful
status.

#### Scenario: Repeated identical save changes nothing observable

- GIVEN a student already saved `position = 300` for a lesson
- WHEN the student sends `PUT .../progress` with `position = 300` again
- THEN Virtual returns `200`
- AND a subsequent `GET .../progress` still returns `position = 300`

### Requirement: Completion is an independent, idempotent action

A lesson reaches `completed = true` only through `POST .../complete`. Reaching or
saving any position value, including the lesson's full duration, MUST NOT mark a lesson
complete. `POST .../complete` MUST NOT modify the saved playback position — the two
fields are fully independent. Repeating `POST .../complete` MUST leave state unchanged
and return the same successful status.

#### Scenario: Saving the final position does not complete the lesson

- GIVEN a lesson with `durationMinutes = 10`
- WHEN the student sends `PUT .../progress` with `position = 600`
- THEN a subsequent `GET .../progress` shows `completed = false`

#### Scenario: Completing a lesson does not move its saved position

- GIVEN a student saved `position = 120` for a lesson
- WHEN the student sends `POST .../complete`
- THEN Virtual returns `200`
- AND a subsequent `GET .../progress` still returns `position = 120`
- AND `completed = true`

#### Scenario: Repeated completion changes nothing observable

- GIVEN a student already completed a lesson
- WHEN the student sends `POST .../complete` again
- THEN Virtual returns `200`
- AND the lesson's completion state and saved position are unchanged

### Requirement: Progress rows are private to their owning student

`userId` MUST always be resolved from the authenticated token subject, never from the
request path or body. A student MUST NOT be able to read or write another student's
progress through any endpoint.

#### Scenario: A student cannot read another student's lesson progress

- GIVEN student A has saved progress for a lesson
- WHEN student B requests `GET .../lessons/{lessonId}/progress` for the same lesson
- THEN Virtual returns student B's own progress state (absent, distinct from A's), never A's data

#### Scenario: A student cannot read another student's course aggregate

- GIVEN student A has completed lessons in a course
- WHEN student B requests `GET .../courses/{courseId}/progress`
- THEN Virtual returns an aggregate computed only from student B's own progress

### Requirement: Course aggregate percentage uses a live lesson count

The per-course aggregate MUST report `completedCount`, `totalCount`, and `percentage`
computed from a live count of the course's current lessons at request time, not a
snapshot taken at enrollment or at any earlier point. Adding a lesson to an already
published course MUST lower every affected student's percentage on the next read; this
is intended behavior.

A lesson completion recorded while that lesson was accessible MUST continue to count
toward `completedCount` even if that lesson later becomes inaccessible to the student
(for example, after a subscription lapses reduces access to a subset of lessons while
the aggregate itself remains readable). Progress MUST NOT be recalculated downward
purely because of an entitlement change.

#### Scenario: Adding a lesson lowers the percentage

- GIVEN a published course with 4 lessons and a student who completed 2
- WHEN an administrator adds a 5th lesson to the course
- AND the student requests `GET .../courses/{courseId}/progress`
- THEN Virtual returns `totalCount = 5`, `completedCount = 2`

#### Scenario: A historical completion on a now-inaccessible lesson still counts

- GIVEN a student completed a lesson while entitled
- AND the student's entitlement subsequently lapses, making that lesson inaccessible
- WHEN the student requests `GET .../courses/{courseId}/progress`
- THEN the completion still contributes to `completedCount`

### Requirement: Course aggregate requires a current Billing entitlement with no exception

`GET .../courses/{courseId}/progress` MUST deny with `403 application/problem+json` any
authenticated student who lacks a current Billing entitlement for that course, with no
exception. This applies even when the student has saved progress or completions
recorded exclusively on free or preview-module lessons within that course.

#### Scenario: A student with only free-lesson progress is still denied the aggregate

- GIVEN an authenticated student with no Billing entitlement for the course
- AND the student has saved progress and a completion on a free lesson in that course
- WHEN the student requests `GET .../courses/{courseId}/progress`
- THEN Virtual returns `403 application/problem+json`

#### Scenario: A lapsed subscriber is denied the aggregate

- GIVEN a student's entitlement for the course has expired
- WHEN the student requests `GET .../courses/{courseId}/progress`
- THEN Virtual returns `403 application/problem+json`

#### Scenario: An entitled student receives the aggregate

- GIVEN a student with a current Billing entitlement for the course
- WHEN the student requests `GET .../courses/{courseId}/progress`
- THEN Virtual returns `200` with `completedCount`, `totalCount`, `percentage`, and the
  resume lesson

### Requirement: Empty course aggregate never 404s

Requesting the aggregate for a course that exists but currently has zero lessons MUST
return `200` with `percentage: 0`, `totalCount: 0`, `completedCount: 0`, and a null
resume lesson. A course with no lessons is a valid state, not a not-found condition.

#### Scenario: Course with zero lessons returns a zeroed aggregate

- GIVEN an entitled student and a published course with zero lessons
- WHEN the student requests `GET .../courses/{courseId}/progress`
- THEN Virtual returns `200`
- AND `percentage = 0`
- AND the resume lesson is null

### Requirement: Resume point is the most recently touched lesson

The course aggregate's resume lesson MUST be the lesson with the most recent
saved-position timestamp among the student's progress rows for that course's current
lessons. When two or more rows share the same timestamp, or when no progress exists
yet, Virtual MUST break the tie by ascending curriculum order:
`module.display_order`, then `lesson.display_order`, then `lesson_id`.

#### Scenario: Resume point is the last-touched lesson

- GIVEN a student saved position on lesson A at 10:00 and lesson B at 10:05
- WHEN the student requests the course aggregate
- THEN the resume lesson is B

#### Scenario: Tie-break falls back to curriculum order

- GIVEN a student has no saved-position timestamp difference between two lessons
- WHEN the student requests the course aggregate
- THEN the resume lesson is the one earlier in `module.display_order`, then
  `lesson.display_order`, then `lesson_id`

### Requirement: Unknown lesson or course is denied consistently

A progress request against a lesson or course identifier that does not exist MUST
return a response consistent with the module's non-disclosure convention. It MUST NOT
leak whether the identifier is malformed, absent, or exists-but-denied through a
distinguishable status or payload.

#### Scenario: Unknown lesson id on progress read

- GIVEN a `lessonId` that does not correspond to any lesson
- WHEN an authenticated student requests `GET .../lessons/{lessonId}/progress`
- THEN Virtual returns a not-found or denial response without exposing internal state

#### Scenario: Unknown course id on aggregate read

- GIVEN a `courseId` that does not correspond to any course
- WHEN an authenticated student requests `GET .../courses/{courseId}/progress`
- THEN Virtual returns a not-found or denial response without exposing internal state

## Out of Scope

BFF consumption of these endpoints; position history or audit log; seconds-precision
duration migration; progress analytics; auto-completion derived from watch percentage.

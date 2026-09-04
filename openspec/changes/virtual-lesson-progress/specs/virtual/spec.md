# Delta for Virtual

## MODIFIED Requirements

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

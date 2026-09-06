# Proposal: Virtual lesson progress

**Issue**: #52 (US-VIRTUAL-005) · **Milestone**: v0.3.0 · **Input**: `exploration.md`

## Intent

A student can watch a virtual lesson but cannot leave and come back. Nothing records
where playback stopped, nothing marks a lesson finished, and no course-level progress
exists. Without it the v0.3.0 "learning E2E" journey has no resume point and no visible
advancement. This change delivers the API contract only; the BFF consumes it later.

## Scope

### In Scope

- `PUT /api/v1/virtual/lessons/{lessonId}/progress` — idempotent upsert of playback position.
- `GET /api/v1/virtual/lessons/{lessonId}/progress` — resume point for one lesson.
- `POST /api/v1/virtual/lessons/{lessonId}/complete` — the only path to completion; idempotent.
- `GET /api/v1/virtual/courses/{courseId}/progress` — derived aggregate + last-touched lesson.
- Explicit method-scoped `authenticated()` matchers in `SecurityConfig` (see Security).
- `V19__virtual_lesson_progress.sql`; errors as `application/problem+json`.

### Out of Scope

- BFF "Continuar" view (#58) and BFF learning scaffold (#170).
- Position history / audit log; seconds-precision `duration` migration; progress analytics.
- Auto-completion from watch percentage — explicitly rejected by the story.

## Settled product decisions

| Topic | Decision |
|---|---|
| Access gate | Reuse `LessonAccessPolicy`. Denial → `403 problem+json` per `specs/virtual`. |
| Lapsed subscription | Rows retained, writes denied. The course aggregate returns `403` as well — there is no read exception for lapsed or free-only students. |
| Denominator | Live `COUNT` of the course's current lessons. Accepted consequence: adding a lesson lowers every student's percentage. No snapshot, no versioning. |
| Resume point | Most recent saved-position timestamp. Deterministic tie-break: `module.display_order` ASC, `lesson.display_order` ASC, `lesson_id` ASC. |
| Position unit | Integer seconds, validated `0 ≤ position ≤ durationMinutes * 60`. Minute-granular bound tolerates ≤59s of slack; accepted. |
| Completion | Independent boolean field, not derived from position. |
| Historical completions | Completions recorded on lessons that later become inaccessible still count toward the percentage. Progress is never recalculated downward by an entitlement change. |
| Complete vs position | `POST /complete` MUST NOT move the saved playback position. The two triggers stay fully independent. |
| Empty course | `GET /courses/{id}/progress` on an existing course with zero lessons returns `200` with `percentage: 0` and a null resume lesson. Never `404` — the course exists. |
| Free-tier aggregate | An authenticated student with no entitlement receives `403` from the course aggregate even when they have saved progress on free or preview lessons. Accepted consequence: the `Continuar` affordance in #58 does not exist for the free tier, and #170 renders no progress until the student subscribes. |

## Security (non-negotiable)

`SecurityConfig.java:148-150` grants `permitAll()` to `/api/v1/virtual/lessons` and
`/api/v1/virtual/lessons/**` with **no `HttpMethod`**, and line 200
(`anyRequest().access(...)`) falls through to a grant for unmapped paths. All four
endpoints would be anonymously reachable.

The change MUST add four method-scoped `.authenticated()` matchers **before** the broad
`permitAll` block (first-match-wins). Precedent: lines 166-170, `DELETE
/api/v1/billing/subscriptions/me` (#130) needed its own entry for this exact reason.

Regression test: a `SecurityConfig` slice test asserting each of the four
(method, path) pairs returns `401` when unauthenticated — one case per endpoint, so a
future matcher reorder fails the build.

Ownership is enforced in the use case from the token subject; `userId` is never read from
path or body. No reusable guard exists (`CourseOwnershipGuard`/`LessonOwnershipGuard` guard
`professorId`, a different concern).

## Capabilities

### New Capabilities

- `virtual-lesson-progress`: saving, reading, completing lesson progress and deriving the
  per-course aggregate and resume point.

### Modified Capabilities

- `virtual`: requirement "Detail, stream, and material endpoints share one decision" extends
  to the progress endpoints — they apply the same access cascade and denial semantics.

## Approach

Mutable upsert row per `(user_id, lesson_id)` in `virtual_lesson_progress`
(unique key on the pair, giving idempotency at the schema level). Confirms the
exploration's recommendation; the append-only alternative adds "latest row per lesson"
complexity to both reads with no precedent in this module.

Follows the module's established chain: port → `*UseCaseImpl` → `Transactional*UseCase`
decorator (writes only) → `VirtualConfiguration` bean wiring → controller → JPA
entity/mapper/adapter. Domain stays framework-free; the Billing check goes through
`VirtualCourseEntitlementPort` in `:api:shared` (ArchUnit: domain/application must not
reach `com.menta.billing.infrastructure..`).

**Exception handling**: introduce a third marker annotation `@VirtualStudentEndpoint` with
its own `@RestControllerAdvice`. Neither existing chain fits: `@VirtualManagementEndpoint`
carries admin semantics, and `@PublicVirtualEndpoint` collapses `IllegalArgumentException`
into an anti-enumeration `404`, which would mask an out-of-range position that must be a
`400`. The new advice reuses virtual's `ProblemDetails` and preserves `403` non-disclosure
for access denial.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/virtual/.../domain/model/` | New | `LessonProgress` (owns the position/completion invariants) |
| `api/virtual/.../application/` | New | 4 use cases + in/out ports + DTOs, including `CourseProgressView` — the course aggregate is a derived read model with no identity, so `design.md` places it here rather than in `domain/model` |
| `api/virtual/.../infrastructure/persistence/` | New | Entity, mapper, adapter |
| `api/virtual/.../infrastructure/web/` | New | Controller, `@VirtualStudentEndpoint`, advice |
| `api/virtual/.../infrastructure/transaction/` | New | Decorators for the two writes |
| `.../VirtualConfiguration.java` | Modified | Bean wiring |
| `api/app/.../db/migration/V19__*.sql` | New | `virtual_lesson_progress` |
| `api/auth/.../SecurityConfig.java` | Modified | 4 authenticated matchers + comment |
| `bruno/API - Direct/virtual/` | New | 4 request files |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Anonymous access via the wildcard `permitAll` | High | Explicit matchers + per-endpoint `401` regression tests |
| A future matcher reorder silently re-opens the hole | Med | Same regression tests, one case per endpoint |
| Percentage drops when a lesson is added post-publish | Med | Accepted product consequence; documented in the spec |
| Position bound is minute-granular (≤59s slack) | Low | Accepted; duration-precision migration deferred |
| Lesson edited under a saved position (`videoId`/duration changes) | Low | Clamp the returned position to the current duration on read |
| Concurrent saves for the same `(user, lesson)` | Low | DB unique key + upsert; last write wins |
| Coverage gate (0.95 domain+app / 0.90 infra) | Med | Test-first per strict TDD; branch tests on the access gate |

## Rollback Plan

Schema-touching and security-touching, so per `rules.proposal`:

1. Revert the merge commit — the four endpoints disappear; no other surface reads
   `virtual_lesson_progress`.
2. `SecurityConfig` returns to its prior matcher list; no existing endpoint's
   authorization changes, since the added matchers cover only new paths.
3. Leave `V19` applied (additive `CREATE TABLE`, no FK from existing tables, no data
   backfill). Drop it only in a later deliberate migration.

## Dependencies

- `LessonAccessPolicy` and `VirtualCourseEntitlementPort` (`:api:shared`) — both exist.
- No new external service, library, or config.

## Delivery forecast

The full change (production + tests meeting the 0.95/0.90 gate + Bruno) will not fit the
800-line review budget. Proposed slices, each independently deliverable and verifiable:

1. **Security + schema foundation** (~150) — `SecurityConfig` matchers, `401` regression
   tests, `V19` migration.
2. **Lesson progress** (~600) — domain, persistence, `PUT`/`GET` progress, `POST` complete,
   advice chain.
3. **Course aggregate** (~350) — percentage derivation, resume point, Bruno collection.

`sdd-tasks` owns the authoritative forecast and guard lines.

## Success Criteria

- [ ] All four endpoints require authentication; anonymous requests get `401`.
- [ ] A student can only read/write their own progress; `userId` comes from the token.
- [ ] Repeating `PUT` with the same position, and repeating `POST /complete`, change nothing.
- [ ] A lesson reaches `completed` only through `POST /complete`, never from position.
- [ ] The course aggregate returns completed count, total, percentage, and resume lesson.
- [ ] Denied access returns `403 application/problem+json`; invalid position returns `400`.
- [ ] `./gradlew check` passes, including ArchUnit and the virtual coverage gate.

## Proposal question round — closed

The question round is complete. Every row in "Settled product decisions" above is now
closed; nothing in this proposal is left to `sdd-spec` as an assumption.

Resolution record:

| Question | Resolution | Decided by |
|---|---|---|
| Access gate on save-progress | Reuse `LessonAccessPolicy` | User, pre-proposal round |
| Percentage denominator | Live `COUNT`, no snapshot | User, pre-proposal round |
| Resume point semantics | Last *touched* lesson | User, pre-proposal round |
| Position unit and bound | Integer seconds, `≤ durationMinutes * 60` | User, pre-proposal round |
| Course aggregate when entitlement is absent | `403`, no exception | User, post-proposal round |
| Historical completions after access loss | Still count | User, post-proposal round |
| `POST /complete` and saved position | Does not move it | User, post-proposal round |
| Empty course response | `200`, `percentage: 0`, null resume | Orchestrator default, user-visible |
| Resume tie-break direction | Ascending curriculum order | Orchestrator default, user-visible |

The free-tier consequence of the `403` aggregate decision was raised explicitly with the
user, including its effect on #58 and #170, and confirmed. It is recorded in the settled
table above so downstream phases do not re-open it.

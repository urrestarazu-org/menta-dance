# Apply Progress: Virtual Lesson Progress (#52, US-VIRTUAL-005)

**Mode**: Strict TDD
**Scope this batch**: Slice 1 — Security foundation + schema (interactive execution mode; STOP after Slice 1)
**Branch**: `feature/virtual-lesson-progress-security-schema` (off `develop`)
**Commit**: `0c3fe67 feat(virtual): cerrar acceso anónimo a progreso de lección y agregar V19 (#52)`

## Slice 1 — Security foundation + schema — COMPLETE

- [x] 1.1 RED: four `@Test` methods added to `SecurityConfigTest.java`, matching the file's existing one-method-per-case style.
- [x] 1.2 Verify RED: `./gradlew :api:auth:test --tests "*SecurityConfigTest*"` — all 4 new cases failed with `AssertionError: Status expected:<401> but was:<404>` (fell through the `permitAll` wildcard / `anyRequest().access(...)` grant), not a compile error.
- [x] 1.3 GREEN: four `.requestMatchers(HttpMethod.X, "...").authenticated()` entries inserted in `SecurityConfig.java` immediately before the `permitAll` block (now at line ~166, shifted by the new Javadoc), with inline comments in the `DELETE /billing/subscriptions/me` (#130) register. Class Javadoc's path-policy list also updated.
- [x] 1.4 Verify GREEN: `./gradlew :api:auth:test --rerun` — 13/13 `SecurityConfigTest` cases pass, full `:api:auth:test` suite green.
- [x] 1.5 `api/app/src/main/resources/db/migration/V19__virtual_lesson_progress.sql` created — table exactly per `design.md`'s DDL (surrogate `id`, unique `(user_id, lesson_id)`, denormalized `course_id`, `position_updated_at DATETIME(3) NULL` with no epoch sentinel, RESTRICT FKs to `virtual_lessons`/`virtual_courses`, `chk_virtual_lesson_progress_position` check).
- [x] 1.6 Verify: `./gradlew :api:app:test` — `BUILD SUCCESSFUL` (10m25s). V19 applies cleanly through the normal Flyway-on-context-load path; `VirtualLessonAccessIntegrationTest` and `VirtualCourseManagementIntegrationTest` (both hit `/api/v1/virtual/lessons/**`) still pass — no regression from the new matchers.
- [x] 1.7 `./gradlew check` — `BUILD SUCCESSFUL` (10m28s) across the whole monorepo. 0 test failures. Checkstyle reports pre-existing warnings only (none on any line touched by this slice — verified by diffing added lines against the checkstyle report's line numbers). ArchUnit unaffected.

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1–1.4 | `SecurityConfigTest.java` | Integration (Spring Security filter-chain slice, no controllers) | ✅ 9/9 pre-existing cases passing before edit | ✅ Written (4 cases, one per (method,path) pair) | ✅ Passed (13/13 after fix) | ✅ 4 cases (one per method/path pair — the spec's own enumerated scenario set, no further triangulation needed per method) | ➖ None needed — matchers match existing file's shape exactly |
| 1.5–1.6 | N/A — pure additive DDL, no dedicated migration test (forecast correction: V19 is `CREATE TABLE` only, no backfill, unlike V18's `ALTER`) | Migration (proven via Spring context-load in `:api:app:test`) | ✅ `:api:app:test` green pre-migration (context loads on V1–V18) | ➖ N/A (schema-only, no production Java code) | ✅ `:api:app:test` `BUILD SUCCESSFUL` with V19 in the chain | ➖ N/A | ➖ None needed |

### Test Summary
- **Total tests written**: 4 (`SecurityConfigTest` new cases)
- **Total tests passing**: 4/4 new + 9/9 pre-existing = 13/13 in `SecurityConfigTest`; 0 regressions in `:api:auth:test` or `:api:app:test`
- **Layers used**: Integration (4 — Spring Security filter-chain slice)
- **Approval tests** (refactoring): None — no refactoring tasks in this slice
- **Pure functions created**: 0 (schema + Spring Security config only)

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :api:auth:test --tests "*SecurityConfigTest*"` → RED: 4 failed (`Status expected:<401> but was:<404>`); after GREEN, `./gradlew :api:auth:test --rerun` → `BUILD SUCCESSFUL`, 13/13 in `SecurityConfigTest` |
| Runtime harness command/scenario and exact result | `./gradlew :api:app:test` → `BUILD SUCCESSFUL` in 10m25s; V19 applied via the app's normal Flyway-on-context-load path (no dedicated Testcontainers migration test needed per forecast correction) |
| Rollback boundary | Revert commit `0c3fe67` — reverts the four `SecurityConfig.java` matchers + Javadoc entries, the four `SecurityConfigTest.java` cases, and drops `V19__virtual_lesson_progress.sql`. No other surface reads/writes `virtual_lesson_progress` yet (Slice 2 owns that), so rollback is total and isolated. |

## Files Changed

| File | Action | What Was Done |
|------|--------|----------------|
| `api/auth/src/test/java/com/menta/auth/infrastructure/security/SecurityConfigTest.java` | Modified | Added 4 `@Test` methods (401 regression for the four progress endpoints) + `get`/`put` static imports |
| `api/auth/src/main/java/com/menta/auth/infrastructure/security/SecurityConfig.java` | Modified | Added 4 method-scoped `.authenticated()` matchers before the `permitAll` block; extended class Javadoc path-policy list; wrapped the 4 new matcher calls across lines to respect Checkstyle's 100-char `LineLength` |
| `api/app/src/main/resources/db/migration/V19__virtual_lesson_progress.sql` | Created | `virtual_lesson_progress` table per `design.md`'s DDL |
| `openspec/changes/virtual-lesson-progress/tasks.md` | Modified | Marked tasks 1.1–1.7 complete |

**Changed-line count** (authored, additions + deletions, excluding `tasks.md` bookkeeping): 33 (`SecurityConfig.java`) + 54 (`SecurityConfigTest.java`) + 47 (new `V19` file) = **134 lines**. Well within the forecast's ~150–180 estimate and the 800-line PR budget.

## Deviations from Design

None — implementation matches `design.md`'s SecurityConfig snippet and DDL exactly, including the corrected `position_updated_at DATETIME(3) NULL` (no epoch sentinel).

One clarification: the launch prompt referenced inserting matchers "before the `permitAll()` block at lines 148-150," but the actual file has that entire `permitAll()` call spanning lines 135-150 as a single `.requestMatchers(...)` invocation with a comma-separated path list. Followed `design.md`'s own statement ("immediately before the `.requestMatchers(...)` block at `SecurityConfig.java:135-150`") and inserted before line 135, i.e., as the first entries inside `authorizeHttpRequests`.

## Issues Found

None.

## Remaining Tasks (Slice 2 and 3 — NOT started, per interactive execution mode)

- [ ] 2.1–2.28 — Lesson progress core (domain, ports, use cases, transaction decorators, persistence, controller). Blocked on Slice 1 merging to `develop` first (Slice Ordering Rationale in `tasks.md`).
- [ ] 3.1–3.21 — Course aggregate, resume point, Bruno collection.

## Workload / PR Boundary

- Mode: chained PR slice (`auto-chain`, `stacked-to-main` interpreted as `develop`)
- Current work unit: Unit 1 — "Close the anonymous-access hole + V19 schema" (PR 1)
- Boundary: starts from `develop` HEAD (`638987a`), ends at commit `0c3fe67` on `feature/virtual-lesson-progress-security-schema`. Self-contained: no other code reads/writes the new table or the four new routes yet.
- Estimated review budget impact: 134 authored changed lines against an 800-line-per-PR budget — Low risk, matches the tasks.md forecast (~150–180 estimated).

## Status

7/7 Slice 1 tasks complete. Ready for `sdd-verify` on Slice 1, or for the orchestrator to proceed to Slice 2 in a subsequent `sdd-apply` batch (interactive mode stops here per instructions).

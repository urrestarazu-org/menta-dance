# Tasks: Physical manual check-in by receptionist (#45, US-PHYSICAL-008)

## Fixed Facts (do not reopen)

D1 (reuse the QR flow's acquire-or-expire lock, no compare-and-delete) — D8
(only `CANCELLED` gates MANUAL; `hasOccurred` is never consulted) are
resolved with the user. D2 (`device_id` broadens to "actor", no migration),
D3 (one discriminated endpoint), D4 (`Role.RECEPTIONIST`, English
identifier), D5 (`SecurityConfig` untouched, `permitAll()` stays; the role
gate lives in the use case), D6 (`SESSION_NOT_ACTIVE`/`INSUFFICIENT_ROLE`
are genuinely new codes), D7 (asserted order: role → student exists →
confirmed assignment → not-cancelled → idempotent read → single lock →
INSERT) are locked. C1–C10 (design) are locked: `@AssertTrue` predicates
close the union (C1); `CheckInCommand.qr(...)`/`.manual(...)` factories
replace direct construction (C2); `CheckInActor(userId, roleNames)` crosses
the `:api:auth`/`:api:physical` boundary as plain strings, never `Role`
(C3); `checkInManually` is a second in-port method — `checkIn`'s body stays
byte-unchanged (C4); the MANUAL path order is M1–M7 verbatim (C5); the lock
key is `checkin:attendance:{sessionId}:{studentId}`, shared with QR (C6);
three new exceptions incl. `StudentNotFoundException` — not in the
proposal's table but required by Escenario 6 (C7); `Role.RECEPTIONIST` is
appended last, blast radius verified concretely — no exhaustive `switch`,
`VARCHAR(20)` fits, public registration cannot self-assign it (C8);
`Attendance` gets a doc-comment-only change (C9); `PhysicalConfiguration`
gains one `UserExistencePort` bean parameter, no build file change — both
`:api:physical` and `:api:billing` already declare `:api:shared` (verified
against `billing/build.gradle.kts`) (C10).

## Deviations from design's suggested slice split (flagged explicitly)

Design's "Migration / Rollout" section names 3 slices and explicitly
defers the final call to this phase ("sdd-tasks owns the authoritative
400-line guard"). This task list uses **4** chained PRs, not 3:

1. **`CheckInActor` and `CheckInCommand`'s reshape move from design's
   Slice 2 ("Application") into P1 ("Domain + role"),** matching this
   task-breakdown's own request. Reason: `CheckInCommand`'s new `actor`
   field is typed `CheckInActor`, so the reshape cannot compile unless the
   type already exists — the two cannot be split across slices in the
   order design's prose implies. Consequence: P1 is not fully inert the
   way design's Slice 1 description promised ("nothing calls any of it
   yet") — it also touches `PhysicalCheckInController.java` (one line) and
   4 test arrange-lines. Nothing new is *reachable*: `CheckInCommand.manual(...)`
   is an unused factory until P2 wires `checkInManually`.
2. **Design's Slice 3 ("Web + contract") splits into two chained PRs, P3
   (web: request DTO, dispatch, handlers) and P4 (contract: OpenAPI,
   Bruno, docs, integration/security tests).** A single "web + contract"
   PR is estimated at ~700–850 changed lines once its tests are counted —
   over the 400-line budget on its own. This also aligns with the
   proposal's own Risks-table hint ("chained slices ... use case +
   controller → contract/Bruno").

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~1,550–1,900 total (1 enum constant, 3 exceptions, 1 new DTO, 1 reshaped command + 5 call sites, 1 reshaped wire DTO, 1 in-port method, 1 use-case method, 1 config wiring, 3 handler mappings, 1 controller dispatch, 1 OpenAPI schema, 4-5 Bruno requests, 1 doc update, plus tests for all of it) |
| Per-slice estimate | P1 ~300–350; P2 ~400–460; P3 ~395–450; P4 ~400–500 |
| 400-line budget risk | High — P2 and P4 individually approach or exceed 400 lines once tests are counted |
| Chained PRs recommended | Yes |
| Suggested split | 4 PRs, P1 → P2 → P3 → P4 (design's 3-slice split, refined per the Deviations section above) |
| Delivery strategy | ask-on-risk (no delivery strategy was supplied to this phase; defaulting to the skill's default) |
| Chain strategy | **Recommended: stacked-to-main** (proven on #44's device-management: P1/P2/P3 stacked to `develop` via PRs #263/#264/#265) — pending user confirmation before `sdd-apply` starts |

```text
Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High
```

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| P1 | `Role.RECEPTIONIST`, 3 domain exceptions, `Attendance` javadoc, `CheckInActor`, `CheckInCommand` reshape + 5 call-site updates | PR 1 | `./gradlew :api:auth:test --tests "*RoleTest*" :api:physical:test --tests "*SessionNotActiveExceptionTest*" --tests "*InsufficientRoleExceptionTest*" --tests "*StudentNotFoundExceptionTest*" --tests "*CheckInActorTest*" --tests "*ProcessPhysicalCheckInUseCaseImplTest*" --tests "*PhysicalCheckInControllerTest*"` | None needed — pure unit tests, no Testcontainers | Revert all P1 files; `checkInManually` doesn't exist yet, so nothing is reachable |
| P2 | `checkInManually` in-port + impl (D7 order), `UserExistencePort` wiring | PR 2 | `./gradlew :api:physical:test --tests "*ProcessPhysicalCheckInUseCaseImplTest*" --tests "*PhysicalConfigurationTest*"` | Mockito unit tests only — no controller dispatch yet, so no MockMvc/Testcontainers | Revert P2 files only; `checkInManually` becomes unreachable again, `checkIn` untouched |
| P3 | `CheckInRequest` union, controller dispatch + anonymous handling, 3 handler mappings | PR 3 | `./gradlew :api:physical:test --tests "*CheckInRequestTest*" --tests "*PhysicalCheckInControllerTest*" --tests "*PhysicalCheckInExceptionHandlerTest*"` | MockMvc/direct controller invocation, no Testcontainers | Revert P3 files only; `type: "MANUAL"` bodies return `400` again (pre-change `@Pattern` behavior restored) |
| P4 | OpenAPI, Bruno, US doc, integration + attendance-history tests | PR 4 | `./gradlew :api:app:test --tests "*PhysicalCheckInIntegrationTest*" --tests "*PhysicalAttendanceHistoryIntegrationTest*"` | Testcontainers MySQL 8 + Redis, full filter chain | Revert P4 files only; contract docs and Bruno requests disappear, P1–P3 stay fully functional |

## Phase P1: Domain + role + actor + command (inert until P2)

- [x] 1.1 RED: `RoleTest` (new) — `api/auth/src/test/java/com/menta/auth/domain/model/RoleTest.java`. Assert `Role.values()` contains `RECEPTIONIST` immediately after `STUDENT` (ordinal continuity, C8) and `Role.valueOf("RECEPTIONIST") == Role.RECEPTIONIST`.
- [x] 1.2 GREEN: `api/auth/src/main/java/com/menta/auth/domain/model/Role.java` — append `RECEPTIONIST` after `STUDENT` with a `#45, US-PHYSICAL-008` doc comment (C8). No migration — `users.role` is `VARCHAR(20)`.
- [x] 1.3 RED: `SessionNotActiveExceptionTest`, `InsufficientRoleExceptionTest`, `StudentNotFoundExceptionTest` (new, one class each) — `api/physical/src/test/java/com/menta/physical/domain/exception/`, mirroring `SessionCancelledExceptionTest`: each asserts `getErrorCode()` equals `"SESSION_NOT_ACTIVE"` / `"INSUFFICIENT_ROLE"` / `"STUDENT_NOT_FOUND"`.
- [x] 1.4 GREEN: `SessionNotActiveException.java`, `InsufficientRoleException.java`, `StudentNotFoundException.java` (new) — `api/physical/src/main/java/com/menta/physical/domain/exception/`, each extends `BusinessException`, no-arg constructor, exact messages from design C7. `StudentNotFoundException` is required by spec Requirement "Ordered MANUAL validation before insertion" (Scenario "Unknown student is rejected").
- [x] 1.5 GREEN: `api/physical/src/main/java/com/menta/physical/domain/model/Attendance.java` — javadoc-only edit replacing the `deviceId`/`kind` bullets with C9's exact text (broadened "actor" invariant; read-`kind`-before-`deviceId` rule). No constructor/getter/factory change; `deviceId` stays required non-blank (D2, C9).
- [x] 1.6 RED: `CheckInActorTest` (new) — `api/physical/src/test/java/com/menta/physical/application/dto/CheckInActorTest.java`. Cases: `anonymous()` has `null` `userId` and empty `roleNames`; a non-empty `roleNames` with `null` `userId` throws `IllegalArgumentException`; `hasAnyRoleOf(Set.of("RECEPTIONIST"))` is `true`/`false` correctly; `roleNames` is defensively copied.
- [x] 1.7 GREEN: `CheckInActor.java` (new) — `api/physical/src/main/java/com/menta/physical/application/dto/CheckInActor.java`, verbatim from design C3 (`record`, `ANONYMOUS` constant, canonical-constructor guard, `hasAnyRoleOf`).
- [x] 1.8 RED: update the 4 existing `new CheckInCommand(...)` construction sites to `CheckInCommand.qr(...)` — `ProcessPhysicalCheckInUseCaseImplTest.java:102,158,169` and `PhysicalCheckInControllerTest.java:87`. Compile-breaking arrange-line edits only; **zero assertion changes** in either file (design's regression discipline).
- [x] 1.9 GREEN: `CheckInCommand.java` reshape — `api/physical/src/main/java/com/menta/physical/application/dto/CheckInCommand.java`. Add `AttendanceKind type`; rename `studentIdFromQr` → `studentId`; add `CheckInActor actor`; add static factories `qr(SessionId, String, String, String)` (`type = QR`, `studentId = null`, `actor = CheckInActor.anonymous()`) and `manual(SessionId, UUID, CheckInActor)` (`type = MANUAL`, QR fields `null`) — per design C2. `manual(...)` is unused until P2.
- [x] 1.10 GREEN: `PhysicalCheckInController.java:68` — replace `new CheckInCommand(...)` with `CheckInCommand.qr(SessionId.of(sessionId), request.qrCredentials(), request.deviceId(), request.deviceToken())`. One line; the method signature and everything else stay untouched until P3.
- [x] 1.11 Verify: `./gradlew :api:auth:test --tests "*RoleTest*"` and `:api:physical:test --tests "*SessionNotActiveExceptionTest*" --tests "*InsufficientRoleExceptionTest*" --tests "*StudentNotFoundExceptionTest*" --tests "*CheckInActorTest*" --tests "*ProcessPhysicalCheckInUseCaseImplTest*" --tests "*PhysicalCheckInControllerTest*"` green; `git diff` over `ProcessPhysicalCheckInUseCaseImpl.checkIn`'s body is empty; both `:api:physical` coverage gates green. P1 ready for PR.

## Phase P2: Application — `checkInManually` (D7 order)

- [x] 2.1 GREEN: `api/physical/src/main/java/com/menta/physical/application/port/in/ProcessPhysicalCheckInUseCase.java` — add `CheckInResult checkInManually(CheckInCommand command);` alongside `checkIn`, with a `#45, US-PHYSICAL-008` doc comment (C4).
- [x] 2.2 RED: extend `ProcessPhysicalCheckInUseCaseImplTest` — new nested class for the role gate: `RECEPTIONIST` and `ADMIN` both proceed past M1; `STUDENT`, `INSTRUCTOR`, an empty-role actor, and `CheckInActor.anonymous()` all throw `InsufficientRoleException` with `verifyNoInteractions(userExistencePort)` (Requirement "MANUAL check-in role gate"; Scenarios "STUDENT is rejected on a MANUAL body", "INSTRUCTOR is rejected on a MANUAL body", "Anonymous caller is rejected on a MANUAL body").
- [x] 2.3 RED: extend the same class — unknown `studentId` → `StudentNotFoundException`, `verifyNoInteractions(assignmentRepository, sessionRepository, lockPort)` (Scenario "Unknown student is rejected").
- [x] 2.4 RED: missing confirmed assignment → `CapacityAssignmentRequiredException`, `verifyNoInteractions(sessionRepository, lockPort)` (Scenario "Missing confirmed assignment is rejected").
- [x] 2.5 RED: cancelled session → `SessionNotActiveException`; parameterized case over a far-past `scheduledAt` on a non-cancelled session → proceeds to `201`, with `hasOccurred`/`sessionWindowBefore`/`sessionWindowAfter` never consulted (Scenarios "Cancelled session is rejected", "Elapsed, non-cancelled session is accepted (retroactive backfill)"; D8).
- [x] 2.6 RED: multi-violation precedence, one case per design C5's table — unknown `sessionId` (assignment mocked `false`, `findById` empty) → `CapacityAssignmentRequiredException`, not `SessionNotFoundException`; unknown student + cancelled session → `StudentNotFoundException`; missing assignment + cancelled session → `CapacityAssignmentRequiredException` (Scenarios "Unknown student on a cancelled session yields STUDENT_NOT_FOUND...", "Missing assignment on a cancelled session yields CAPACITY_ASSIGNMENT_REQUIRED..."; D7).
- [x] 2.7 RED: idempotent replay — an existing `Attendance` → `newlyRecorded == false` with the existing view, `verifyNoInteractions(lockPort)`, `verify(attendanceRepository, never()).save(any())` (Scenario "Repeated MANUAL check-in for an already-checked-in student").
- [x] 2.8 RED: lock + persistence — captured lock key is exactly `checkin:attendance:{sessionId}:{studentId}` with the injected TTL, exactly one `acquireIfAbsent` call (no `checkin:qr:` key); captured `Attendance` has `kind == MANUAL` and `deviceId == actor.userId().toString()`; RECEPTIONIST and ADMIN both reach `201` (Scenarios "RECEPTIONIST records a valid MANUAL check-in", "ADMIN also succeeds on a MANUAL check-in", "Persisted device_id holds the receptionist's userId").
- [x] 2.9 GREEN: `api/physical/src/main/java/com/menta/physical/application/usecase/ProcessPhysicalCheckInUseCaseImpl.java` — add `UserExistencePort userExistencePort` constructor parameter/field (no `build.gradle.kts` change — `:api:shared` already declared), `MANUAL_CHECK_IN_ROLES = Set.of("RECEPTIONIST", "ADMIN")`, and `checkInManually(CheckInCommand)` implementing M1–M7 verbatim from design C5. **`checkIn`'s body stays byte-unchanged.**
- [x] 2.10 RED→GREEN: extend `PhysicalConfigurationTest`, then wire `PhysicalConfiguration.processPhysicalCheckInUseCase(...)` with a new `UserExistencePort userExistencePort` bean-method parameter passed to the impl's constructor (C10, mirrors `BillingConfiguration`'s `AssignTrialSubscriptionUseCaseImpl` wiring).
- [x] 2.11 Verify: `./gradlew :api:physical:test --tests "*ProcessPhysicalCheckInUseCaseImplTest*" --tests "*PhysicalConfigurationTest*"` green; coverage gates green (95%/90%); confirm `git diff` shows zero changed lines inside `checkIn` and zero edits to existing QR assertions in `ProcessPhysicalCheckInUseCaseImplTest` beyond 1.8's arrange swap. **Highest-risk slice — review alone (design's own note).** P2 ready for PR.

## Phase P3: Web — request union + dispatch + handlers

- [x] 3.1 RED: `CheckInRequestTest` (new) — `api/physical/src/test/java/com/menta/physical/infrastructure/web/dto/CheckInRequestTest.java`. Via `Validator` from `Validation.buildDefaultValidatorFactory()`, parameterized: MANUAL + each of `qrCredentials`/`deviceId`/`deviceToken` (populated and empty-string) → invalid; MANUAL without `studentId` → invalid; QR + `studentId` → invalid; QR missing any of the triple → invalid; `type` absent/`"qr"`/`"OTHER"` → invalid; both well-formed variants → zero violations (Requirement "MANUAL request is a validated discriminated union"; delta Requirements "Check-in type is a two-valued discriminator", "QR variant rejects MANUAL-only fields"; Scenarios "MANUAL body carrying a QR-only field is rejected", "An unrecognized type value is rejected", "QR request carrying studentId is rejected").
- [x] 3.2 GREEN: `api/physical/src/main/java/com/menta/physical/infrastructure/web/dto/CheckInRequest.java` — rewrite per design C1: `@Pattern(regexp = "QR|MANUAL")`, add `studentId`, remove per-field `@NotBlank`, add `isWellFormedQrVariant()`/`isWellFormedManualVariant()` `@AssertTrue` predicates and the private `present(String)` helper, verbatim from design.
- [x] 3.3 RED: extend `PhysicalCheckInControllerTest` — `type: "MANUAL"` dispatches to `checkInUseCase.checkInManually(...)`, never `checkIn(...)`; `type: "QR"` still dispatches to `checkIn(...)`; anonymous (`null` **and** `AnonymousAuthenticationToken`) MANUAL request builds `CheckInActor.anonymous()`; an authenticated actor's `ROLE_`-prefixed authorities are stripped before reaching `CheckInActor.roleNames()`.
- [x] 3.4 GREEN: `PhysicalCheckInController.java` — add `Authentication authentication` parameter to `checkIn(...)`; add the private `checkInActor(Authentication)` helper (verbatim from design C3); build the command via a `command(SessionId, CheckInRequest, Authentication)` helper; dispatch with an exhaustive `switch (command.type())` (no `default`) over `AttendanceKind` calling `checkIn`/`checkInManually` (C4). Correct the class javadoc's now-false "never touches Authentication" claim for this endpoint.
- [x] 3.5 RED: extend `PhysicalCheckInExceptionHandlerTest` — three new cases: `InsufficientRoleException` → `403 INSUFFICIENT_ROLE`; `StudentNotFoundException` → `404 STUDENT_NOT_FOUND`; `SessionNotActiveException` → `409 SESSION_NOT_ACTIVE`; the nine existing mapping assertions stay untouched.
- [x] 3.6 GREEN: `PhysicalCheckInExceptionHandler.java` — append three `@ExceptionHandler` methods, each a `ProblemDetails.response(...)` call with the exact messages from design C7.
- [x] 3.7 Verify: `./gradlew :api:physical:test --tests "*CheckInRequestTest*" --tests "*PhysicalCheckInControllerTest*" --tests "*PhysicalCheckInExceptionHandlerTest*" --tests "*ArchitectureTest*"` green; coverage gates green. P3 ready for PR.

## Phase P4: Contract — OpenAPI, Bruno, docs, integration/security

- [x] 4.1 RED: extend `api/app/src/test/java/com/menta/app/integration/physical/PhysicalCheckInIntegrationTest.java` (Testcontainers MySQL 8 + Redis, full filter chain) — RECEPTIONIST → `201`; repeat → `200`, unchanged row count; `device_id` equals the receptionist's `userId`, `kind = 'MANUAL'` in MySQL; ADMIN → `201`; STUDENT/INSTRUCTOR/anonymous → `403`; cancelled session → `409`; elapsed non-cancelled session → `201`; unknown student → `404`; a QR scan for an already MANUAL-checked-in student → `200` (shared-key/shared-row proof, D1/C6). Covers every remaining spec Scenario end to end.
- [x] 4.2 GREEN: no production code expected — confirmation-only unless 4.1 surfaces a wiring gap not caught by P1–P3's unit tests.
- [x] 4.3 RED/GREEN: extend `api/app/src/test/java/com/menta/app/integration/physical/PhysicalAttendanceHistoryIntegrationTest.java` with one MANUAL row, asserting it renders correctly (D2 consumer risk, design C9).
- [x] 4.4 Regression: confirm via `git diff --stat` against the P3 base that `ProcessPhysicalCheckInUseCaseImpl.java`'s `checkIn` body, `Attendance.java`'s non-javadoc content, and `app.physical.checkin.device-token` are unaffected by the full 4-slice change.
- [x] 4.5 GREEN: `api/openapi/physical-v1.yaml` — `CheckInRequest` → `oneOf: [CheckInQrRequest, CheckInManualRequest]` with `discriminator.propertyName: type`; add `403 INSUFFICIENT_ROLE`, `404 STUDENT_NOT_FOUND`, `409 SESSION_NOT_ACTIVE` responses; document in prose (a) D8's retroactive-backfill divergence and (b) the unknown-`sessionId`-on-MANUAL quirk (`403 CAPACITY_ASSIGNMENT_REQUIRED`, not `404`, per D7's ordering).
- [x] 4.6 GREEN: `bruno/API - Direct/physical/check-ins/` (new folder — no check-in request exists in the collection today) — `QR Check-in.bru`, `Manual Check-in.bru`, `Manual Check-in - No Assignment.bru`, `Manual Check-in - Wrong Role.bru`.
- [x] 4.7 GREEN: `docs/user-stories/US-PHYSICAL-008.md` — lift `Estado: Draft`; record D1 (lock strategy supersedes the doc's compare-and-delete/Lua-nonce request) and D7/D8 (validation order + the narrowed "only cancelled, not elapsed" rule, diverging from the doc's literal "sesión pasada o cancelada") as resolved, not open. **Final task of this change.**
- [x] 4.8 Verify: `./gradlew check` (full monorepo regression, given the shared `Role.java`/`CheckInCommand`/`CheckInRequest` touches); confirm every Success Criterion in `proposal.md` is met. P4 ready for PR — after merge, run `sdd-verify` against `physical-manual-checkin`'s 5 requirements and the `physical-checkin` delta's 2, then `sdd-archive`.

## Requirement → Task Coverage (cross-check against spec scenarios)

| # | Requirement / Scenario | Covered by |
|---|---|---|
| R1 | MANUAL check-in role gate (STUDENT/INSTRUCTOR/Anonymous → 403) | 1.6–1.7, 2.2, 3.3–3.4, 4.1 |
| R2 | MANUAL request is a validated discriminated union (QR-only field → 400) | 3.1–3.2, 4.5 |
| R3 | Ordered MANUAL validation (role → student → assignment → cancellation) | 2.2–2.6, 4.1 |
| R4 | Idempotent redemption (200, no lock, no insert) | 2.7, 4.1 |
| R5 | Success persists the actor (`kind = MANUAL`, `device_id = userId`) | 2.8–2.9, 1.5, 4.1 |
| Delta R1 | `type` is a two-valued discriminator | 3.1–3.2 |
| Delta R2 | QR variant rejects `studentId` | 3.1–3.2, 4.5 |

## Out of Scope (confirmed in proposal.md)

The QR flow's eleven-step ordering, its own exceptions/statuses, the
`PhysicalDevice` registry, `DEVICE_EXPIRED`/`DEVICE_REVOKED` enforcement, a
new migration, and BFF/Android front-desk surfaces are unaffected by this
change.

## Next Steps

After **P4** merges and its integration tests are green: run `sdd-verify`
against all 5 `physical-manual-checkin` requirements plus the 2
`physical-checkin` delta requirements, then `sdd-archive`.

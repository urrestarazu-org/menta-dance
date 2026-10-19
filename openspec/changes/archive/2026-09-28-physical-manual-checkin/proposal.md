# Proposal: Physical manual check-in by receptionist

**Issue**: #45 (US-PHYSICAL-008, "Check-in manual por recepcionista") · **Input**: Engram `sdd/explore/physical-manual-checkin`

## Intent

Today the only way to record attendance at a physical session is the QR reader flow shipped in
#38 (`ProcessPhysicalCheckInUseCaseImpl`). When the reader is offline, the student's phone is
dead, the QR is expired, or the tablet is simply not where the student is standing, the front
desk has no recourse: there is no operator-initiated path to record attendance. The contract was
deliberately pre-built for this exact ticket — `AttendanceKind.MANUAL` already exists with a doc
comment naming issue #45, `CheckInRequest.type` is pinned to `@Pattern(regexp = "QR")` with a
doc comment naming a future MANUAL variant, and `physical_attendances.kind` already stores a
type discriminator. What is missing is the variant itself, plus the actor that performs it:
`Role` has only `ADMIN`, `INSTRUCTOR`, `STUDENT` — **RECEPCIONISTA does not exist in code
today**, even though `docs/03-AUTH-API.md:145` and `docs/22-DATA-MODEL.md:13` both list it as an
initial role. This change delivers the MANUAL variant end to end and introduces the role that
owns it.

## Scope

### In Scope

- MANUAL variant of `POST /api/v1/physical/sessions/{sessionId}/check-ins`, body
  `{"type": "MANUAL", "studentId": "<uuid>"}` → `201 Created` for a RECEPTIONIST (or ADMIN)
  when the student holds a confirmed capacity assignment (Escenario 1).
- `403 CAPACITY_ASSIGNMENT_REQUIRED` when there is no confirmed assignment (Escenario 2) —
  reusing the existing `CapacityAssignmentRequiredException` and
  `PhysicalCapacityAssignmentRepository.existsConfirmedAssignment`, unchanged.
- Idempotent retry: an existing attendance for that student+session returns `200 OK` with the
  stored record and inserts nothing (Escenario 3) — reusing
  `AttendanceRepository.findBySessionIdAndUserId` plus the `UNIQUE (session_id, user_id)`
  constraint, unchanged.
- **New** `Role.RECEPTIONIST` in `:api:auth`, plus `403 INSUFFICIENT_ROLE` for any other
  authenticated caller and for an anonymous one on a MANUAL body (Escenario 4).
- **New** `409 SESSION_NOT_ACTIVE` for a **cancelled** session (Escenario 5, narrowed — see D8).
  An already-elapsed but non-cancelled session is deliberately **not** rejected: MANUAL supports
  retroactive backfill for a session that already occurred, with no time limit.
- `404 STUDENT_NOT_FOUND` for an unknown `studentId` (Escenario 6), via the existing
  `:api:shared` `UserExistencePort.existsById` — the same cross-module reuse billing already
  does in `AssignTrialSubscriptionUseCaseImpl`, so no new plumbing (ArchUnit
  module-boundary rule holds: no `com.menta.auth.*` import from `:api:physical`).
- **Reshaping `CheckInRequest` into a real discriminated union.** This is a substantive task,
  not a footnote: every field is `@NotBlank` today and `type` accepts one literal value. It
  must become cross-field validated so a MANUAL request **rejects QR-only fields**
  (`qrCredentials`, `deviceId`, `deviceToken`, …) and a QR request rejects `studentId`, with a
  `400 INVALID_REQUEST` before any use-case validation runs.
- `CheckInCommand` shape change (type + acting user), controller wiring reading the nullable
  `Authentication`, new domain exceptions and their `PhysicalCheckInExceptionHandler` mappings.
- `api/openapi/physical-v1.yaml` contract update (issue DoD) and Bruno requests — there is no
  check-in request in `bruno/` today at all.

### Out of Scope

- **Any change to the QR flow's eleven-step ordering**, which is explicitly load-bearing
  (device token → QR parse → session binding → signature → expiry → session existence/cancelled
  → check-in window → capacity assignment → idempotent read → two Redis locks → INSERT).
  MANUAL is a parallel, shorter ordering; it does not reorder, reuse conditionally, or reinterpret
  any QR step.
- The QR flow's own exceptions and statuses. `SESSION_CANCELLED` (403) and
  `OUTSIDE_CHECK_IN_WINDOW` (403) stay **QR-only** and are not consolidated into the new 409.
  The two variants deliberately answer differently for an inactive session.
- The `PhysicalDevice` registry (#44, archived). No device, device token, or QR credential is in
  play for a receptionist-initiated check-in; the contract forbids those fields on MANUAL.
- #266's deferred `DEVICE_EXPIRED`/`DEVICE_REVOKED` check-in enforcement — unrelated to this change.
- New database migration (see D2). V23 stays the head.
- BFF or Android front-desk surfaces; bulk/roster check-in; undoing a check-in; assigning the
  RECEPTIONIST role through an admin UI.

## Settled decisions

| # | Topic | Decision |
|---|---|---|
| D1 | Redis lock strategy (**D-LOCK**) | MANUAL **reuses the QR flow's existing acquire-or-expire lock pattern** on `checkin:attendance:{sessionId}:{studentId}` — **not** compare-and-delete-with-nonce. The pre-existing Draft product doc `docs/user-stories/US-PHYSICAL-008.md` asks for compare-and-delete with a Lua nonce, but that directly contradicts the QR flow's explicit, already-shipped architecture decision rejecting that same two-phase-commit pattern for this exact domain ("not worth it for a hallway scan", archived `design.md` decision #3). Consistency with the shipped decision wins; that Draft request is **superseded, not followed**. **Resolved with the user — not to be re-litigated.** |
| D2 | `device_id` reuse (**D-DEVICE-ID**) | `physical_attendances.device_id` (`NOT NULL` in DB, required non-blank in the `Attendance` domain model) is **reused** to carry the receptionist's own `userId` for MANUAL rows, rather than adding a nullable column via a fresh migration. `device_id`'s meaning therefore **broadens from "device that scanned this" to "actor (device or person) who recorded this check-in"**. This broadened invariant MUST be documented explicitly in `Attendance`'s own doc comment as part of this change. **Resolved with the user — not to be re-litigated.** |
| D3 | One discriminated endpoint | Extend the existing endpoint/use case with a `type` discriminator instead of adding `.../check-ins/manual`. `CheckInRequest`, `CheckInCommand`, and `AttendanceKind.MANUAL` were pre-built for exactly this and name #45 in their own comments; `docs/05-PHYSICAL-API.md:50-61` and the US doc both describe one endpoint with two variants. |
| D4 | Role identifier | `Role.RECEPTIONIST` in code, per CLAUDE.md's English-identifier convention — mirroring `STUDENT`/`INSTRUCTOR` already being English for ALUMNO/PROFESOR. "Recepcionista" stays in user-facing messages and docs only. |
| D5 | Where authorization lives | The endpoint stays `permitAll()` (`SecurityConfig.java:275`) because the QR device flow authenticates with a device token, not a user session. `RoleAuthorizationManager` gates only by URL path prefix and cannot discriminate on a request body, so MANUAL's role check happens **inside the use case/controller** — the same precedent this endpoint already set, applied to a new role, not a new architectural pattern. |
| D6 | New error codes | `SESSION_NOT_ACTIVE` (409) and `INSUFFICIENT_ROLE` (403) exist nowhere in the codebase (grep-confirmed) and are genuinely new. They do not replace or subsume any QR code. |
| D7 | MANUAL validation order is asserted | role → `studentId` existence → confirmed capacity assignment → not-cancelled check (D8) → idempotent read-through → single Redis lock → INSERT. Ordering is **asserted by test**, not incidental, because it decides which error a multi-violation request receives (same discipline as `AssignTrialSubscriptionUseCaseImpl`). Note this resolves the order divergence between `docs/05-PHYSICAL-API.md`'s prose and the issue's scenario numbering. |
| D8 | Retroactive backfill (diverges from issue Escenario 5) | **Deliberate divergence, resolved with the user, not to be re-litigated.** The issue's Escenario 5 literally says "sesión pasada o cancelada" → `409`. This change narrows that: only a **cancelled** session returns `409 SESSION_NOT_ACTIVE`. An already-elapsed, non-cancelled session remains checkinable **with no time limit** — the front desk can backfill attendance for a session that already happened (e.g. forgot to record it during the session). `PhysicalSession.hasOccurred(now)` is therefore **not** consulted by the MANUAL path at all; only `PhysicalSession.status == CANCELLED` gates it. This is a genuine acceptance-criterion divergence, not an implementation detail — call it out explicitly in the PR body and the US doc update, mirroring how #44's D1 (SHA-256 vs. the issue's literal bcrypt/argon2 request) was handled. |

## Capabilities

### New Capabilities

- `physical-manual-checkin`: the operator-initiated MANUAL variant — RECEPTIONIST/ADMIN
  authorization, student existence, confirmed-assignment requirement, session-active rule,
  idempotent retry, single-lock insertion, and the `Role.RECEPTIONIST` addition that enables it.

### Modified Capabilities

- `physical-checkin`: the wire contract becomes a **true discriminated union**. Requirement
  changes: `type` is no longer a single-valued pattern, and a QR request MUST now reject
  MANUAL-only fields (mirror of the MANUAL rejection rule). The QR variant's own ordering,
  statuses, and error codes are unchanged.

## Approach

Exploration approach 1 (extend the same contract). `CheckInRequest` becomes a validated
discriminated union; the controller maps it to a `CheckInCommand` carrying the variant and the
acting user, and `ProcessPhysicalCheckInUseCaseImpl` dispatches to a **dedicated, clearly
separated MANUAL path** — a dedicated method or an extracted shared write step — never
interleaved conditionals inside the QR method body. The two paths share only what is genuinely
common: `AttendanceRepository`, the idempotent read-through, the INSERT, and the
exception-handling infrastructure. Domain stays framework-free (ADR-0021, ArchUnit-enforced).
Per strict TDD, MANUAL arrives as **new test classes/methods added to**
`ProcessPhysicalCheckInUseCaseImplTest`, written before implementation, with **zero edits to
existing QR assertions** — a diff over the existing QR test bodies is itself a regression signal.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/physical/.../application/usecase/ProcessPhysicalCheckInUseCaseImpl.java` | Modified | MANUAL dispatch + separated MANUAL path (D7 order); QR's 11 steps untouched |
| `api/physical/.../infrastructure/web/dto/CheckInRequest.java` | Modified | Discriminated union with cross-field validation (non-trivial) |
| `api/physical/.../application/dto/CheckInCommand.java` | Modified | Variant + acting-user shape |
| `api/physical/.../domain/model/Attendance.java` | Modified | `deviceId` invariant broadened to "actor" per D2 (doc comment is in scope) |
| `api/physical/.../domain/exception/` | New | `SessionNotActiveException` (409), `InsufficientRoleException` (403) |
| `api/physical/.../infrastructure/web/controller/PhysicalCheckInController.java` + `PhysicalCheckInExceptionHandler.java` | Modified | Nullable `Authentication` read; two new mappings |
| `api/auth/.../domain/model/Role.java` | Modified | Add `RECEPTIONIST` (D4) |
| `api/physical/.../application/port/out/AttendanceRepository.java`, `PhysicalCapacityAssignmentRepository.java` | Unchanged | Reused as-is (idempotency, capacity) |
| `api/shared/.../auth/UserExistencePort.java` | Unchanged | Reused as-is; `:api:physical` declares the dependency the way billing does |
| `api/app/.../db/migration/` | Unchanged | **No migration** — `kind` and `device_id` already exist (D2) |
| `api/openapi/physical-v1.yaml` | Modified | Discriminated request body + new statuses |
| `bruno/API - Direct/physical/` | New | First check-in requests in the collection |
| `docs/user-stories/US-PHYSICAL-008.md` | Modified | Lift from Draft; record D1 superseding its compare-and-delete request |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Regressing the QR path's load-bearing 11-step ordering | High | Separated MANUAL path, never interleaved conditionals (Approach); existing QR tests are additively surrounded, not edited; a diff in QR test bodies fails review |
| Discriminated-union validation leaks a mixed request into the use case | Med | Cross-field validation rejects mixed bodies with `400` before any use-case step; explicit tests per forbidden field on both variants |
| `Role` enum addition breaks a consumer (295 `Role` references in `:api:auth`) | Med | No exhaustive `switch` over `Role` found by grep, but this MUST be verified concretely at implementation, not assumed; `./gradlew build` + full test run is the gate |
| D2 broadens `device_id` semantics for `AttendanceView`/reporting consumers | Med | Invariant documented in `Attendance`'s javadoc (in scope); attendance-history reads asserted to render MANUAL rows correctly |
| A reviewer expects the Draft doc's compare-and-delete lock | Med | D1 records the supersession with rationale here, in the US doc, and in the PR body |
| 409 `SESSION_NOT_ACTIVE` read as inconsistent with QR's 403s | Low | Deliberate per-variant divergence, stated in Out of Scope and asserted by test on both variants |
| A reviewer expects the issue's literal Escenario 5 (past session also rejected) | High | D8 records the divergence with rationale here, in the US doc, and in the PR body — mirrors how #44's D1 handled a literal-issue-text divergence |
| Unbounded retroactive backfill (no time limit) lets a receptionist record attendance for a session from months ago | Med | Deliberate per D8; capacity-assignment and idempotency checks still apply unchanged, so this only affects *when*, not *whether*, a legitimate assignment can be recorded |
| Coverage gate: physical is 95% domain+application / 90% infrastructure | Med | Test-first per strict TDD; every new rejection branch needs an explicit case |
| 400-line review budget | High | `sdd-tasks` forecasts; likely chained slices (role + exceptions → DTO union → use case + controller → contract/Bruno) |

## Rollback Plan

1. Revert the merge commit. The MANUAL variant disappears; `CheckInRequest` returns to
   `@Pattern(regexp = "QR")`, so MANUAL bodies get `400` again and the QR path — never
   structurally altered (Approach) — is byte-identical to its pre-change form.
2. No migration to undo. V23 remains the head, and no column was added or altered (D2).
3. Already-inserted `kind = 'MANUAL'` rows survive the revert and remain readable:
   `AttendanceKind.MANUAL` predates this change, and their `device_id` holds a user id, which
   the column has always stored as an opaque string. They stay visible in attendance history.
4. `Role.RECEPTIONIST` removal is the one non-trivial step: if any user was already assigned
   that role, reverting the enum leaves an unmappable persisted/JWT-claim value. Reassign those
   users before reverting, or revert the physical module only and leave the enum constant in
   place (it is inert without the MANUAL path).
5. `SecurityConfig` is not modified by this change, so no endpoint's authorization changes in
   either direction.

## Dependencies

- None blocking. No new library, external service, configuration property, or migration.
- `:api:physical` must declare its `:api:shared` `UserExistencePort` dependency the way
  `:api:billing` already does — existing pattern, no new module edge.
- `docs/user-stories/US-PHYSICAL-008.md` is still `Estado: Draft`; D1 and D7 resolve its two
  open points, so it should be updated in this change rather than left contradicting the code.

## Success Criteria

- [ ] A RECEPTIONIST posts `{"type": "MANUAL", "studentId": "<uuid>"}` to an active session for a
      student with a confirmed assignment and receives `201 Created` with the stored attendance.
- [ ] No confirmed capacity assignment → `403 CAPACITY_ASSIGNMENT_REQUIRED`.
- [ ] A repeated MANUAL check-in for the same student+session returns `200 OK` with the existing
      record, and the attendance row count is unchanged.
- [ ] A STUDENT, an INSTRUCTOR, and an anonymous caller each receive `403 INSUFFICIENT_ROLE`;
      ADMIN succeeds.
- [ ] A cancelled session returns `409 SESSION_NOT_ACTIVE`. An already-elapsed, non-cancelled
      session does **not** — MANUAL accepts a retroactive check-in for it (D8, deliberate
      divergence from the issue's literal Escenario 5).
- [ ] An unknown `studentId` returns `404 STUDENT_NOT_FOUND`.
- [ ] A MANUAL body carrying any QR-only field, and a QR body carrying `studentId`, are both
      rejected with `400 INVALID_REQUEST` before any use-case validation runs.
- [ ] The MANUAL rejection order of D7 is asserted by a test, not incidental.
- [ ] MANUAL rows persist `kind = MANUAL` and `device_id = <receptionist userId>`, and
      `Attendance`'s javadoc documents the broadened actor invariant.
- [ ] The QR path's eleven-step ordering, its exceptions, and its existing test assertions are
      unchanged.
- [ ] OpenAPI contract and Bruno requests updated; `./gradlew check` passes, including ArchUnit
      and the physical coverage gate.

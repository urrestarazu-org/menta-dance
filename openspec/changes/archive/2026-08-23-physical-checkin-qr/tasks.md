# Tasks: Physical Check-in with Ephemeral QR (US-PHYSICAL-001)

> **Retroactive record.** Implementation is complete and verified (230 tests in
> `:api:physical`, 189 in `:api:app`, 0 failures, 97.14%/90.66% coverage,
> checkstyle clean of errors). Every task below documents work already done,
> in the order it was built. Nothing here is prospective for `sdd-apply`;
> `sdd-verify` uses this record to confirm delivered code matches `spec.md`
> and `design.md`.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~3,500 (additions), measured from working-tree file line counts (git diff not run — nothing is committed yet) |
| 800-line budget risk | High — actual size is ~4.4x the user's 800-line review budget |
| Chained PRs recommended | Informational only — code already exists as one built unit; no re-slicing performed |
| Suggested split | See "Retroactive PR Seams" below (informational) |
| Delivery strategy | Resolved: single PR with documented `size:exception` (user decision after seeing the ~3,500-line forecast) |
| Chain strategy | N/A — user declined chaining; code stays as one reviewed unit |

Decision needed before apply: No — this change is already implemented; there is no `sdd-apply` step left to gate.
Chained PRs recommended: Informational, declined by the user
Chain strategy: N/A (single PR)
800-line budget risk: High, accepted as `size:exception`

### Measurement basis

Signals gathered without `git diff --stat` (nothing committed): per-file line
counts via `rg -c '^'` on every new/modified file identified from the design's
File Changes table.

| Group | Files | Lines |
|-------|-------|-------|
| New `api/physical` main sources (domain+application+infrastructure, checkin/QR/attendance-scoped) | 38 | 1,538 |
| New `api/physical` test sources (same scope) | 15 | 1,156 |
| New `PhysicalConfigurationTest.java` (wiring/fail-fast tests) | 1 | 187 |
| New `PhysicalCheckInIntegrationTest.java` (`:api:app`) | 1 | 406 |
| New `V15__physical_attendances.sql` | 1 | 21 |
| Modified `PhysicalConfiguration.java`, `SecurityConfig.java`, `api/openapi/physical-v1.yaml` | 3 | partial diffs only (files are pre-existing; full-file counts of 210/~200/760 lines overstate the true added delta) |

Total new-file lines alone: **3,308**. Adding the modified files' actual
diff hunks (2 new beans + fail-fast in `PhysicalConfiguration`, 2 route
matchers in `SecurityConfig`, 2 paths + 3 schemas in the OpenAPI contract)
puts the realistic total around **~3,500 changed lines** — well past both
the skill's 400-line default and the user's 800-line budget. This is purely
informational: the code was authored and delivered as a single reviewed unit
already, so no re-planning of the cut is proposed here.

### Retroactive PR Seams (informational, not executed)

| Seam | Scope | Focused test command |
|------|-------|----------------------|
| A | Migration + domain (`Attendance`, `AttendanceId`, `AttendanceKind`, 8 exceptions) | `./gradlew :api:physical:test --tests "com.menta.physical.domain.*"` |
| B | Application layer (ports, DTOs, both use cases, `QrCredentialParser`, `AttendanceViewMapper`) + unit tests | `./gradlew :api:physical:test --tests "com.menta.physical.application.*"` |
| C | Infrastructure (JPA, Redis, QR signer, web controllers/DTOs/handler) + unit tests | `./gradlew :api:physical:test --tests "com.menta.physical.infrastructure.*"` |
| D | Wiring, `SecurityConfig`, OpenAPI contract, `:api:app` integration test | `./gradlew :api:app:test --tests "com.menta.app.integration.physical.PhysicalCheckInIntegrationTest"` |

## Phase 1: Foundation

- [x] 1.1 `api/app/src/main/resources/db/migration/V15__physical_attendances.sql` — create `physical_attendances` table with `UNIQUE (session_id, user_id)` and FK constraints (Schema-Level Idempotency Guard requirement).
- [x] 1.2 `domain/model/AttendanceKind.java` — enum with `QR` and reserved `MANUAL` value.
- [x] 1.3 `domain/model/AttendanceId.java` — identity value object.
- [x] 1.4 `domain/model/Attendance.java` — framework-free aggregate.
- [x] 1.5 8 domain exceptions in `domain/exception/`: `InvalidQrCredentialException`, `ExpiredQrCredentialException`, `OutsideCheckInWindowException`, `CheckInAlreadyProcessingException`, `CapacityAssignmentRequiredException`, `InvalidDeviceTokenException`, `CheckInDegradedException`, `SessionCancelledException` — each with a stable error code (RFC 9457 requirement).
- [x] 1.6 Unit tests for the 3 exceptions carrying extra behavior: `CheckInDegradedExceptionTest`, `InvalidDeviceTokenExceptionTest`, `SessionCancelledExceptionTest`.

## Phase 2: Application Layer

- [x] 2.1 DTOs in `application/dto/`: `AccessQrView`, `AttendanceView`, `CheckInCommand`, `CheckInResult`.
- [x] 2.2 Ports out in `application/port/out/`: `AttendanceRepository`, `Clock`, `PhysicalCapacityAssignmentRepository`, `QrCredentialSignatureService`, `RedisLockPort`.
- [x] 2.3 Ports in in `application/port/in/`: `IssuePhysicalAccessQrUseCase`, `ProcessPhysicalCheckInUseCase`.
- [x] 2.4 `application/usecase/QrCredentialParser.java` (package-private) + `QrCredentialParserTest` — validates credential shape (`qr:` prefix, 4 segments, UUID claims, non-blank `jti`).
- [x] 2.5 `application/usecase/AttendanceViewMapper.java` — domain → view mapping.
- [x] 2.6 `application/usecase/IssuePhysicalAccessQrUseCaseImpl.java` + `IssuePhysicalAccessQrUseCaseImplTest` — session exists → not cancelled → check-in window → confirmed assignment (Requirement: QR Credential Issuance Gate).
- [x] 2.7 `application/usecase/ProcessPhysicalCheckInUseCaseImpl.java` + `ProcessPhysicalCheckInUseCaseImplTest` — full ordered gate chain: device token → shape → session binding → signature → expiry → session/cancel/window → assignment → idempotency → locks → insert (Requirements: Ordered Redis-Free Rejection, Idempotent Redemption, Locked Fail-Closed Insertion).

## Phase 3: Infrastructure

- [x] 3.1 `infrastructure/qr/QrProperties.java` + `QrPropertiesTest` — `app.qr.*` window/TTL config (field defaults 30m/2h).
- [x] 3.2 `infrastructure/qr/FormatQrCredentialSignatureService.java` + `FormatQrCredentialSignatureServiceTest` — placeholder signer, `MessageDigest.isEqual` constant-time compare.
- [x] 3.3 `infrastructure/persistence/entity/AttendanceJpaEntity.java` + `AttendanceJpaEntityTest`.
- [x] 3.4 `infrastructure/persistence/repository/AttendanceJpaRepository.java`.
- [x] 3.5 `infrastructure/persistence/mapper/AttendanceJpaMapper.java` + `AttendanceJpaMapperTest`.
- [x] 3.6 `infrastructure/persistence/adapter/AttendanceRepositoryAdapter.java` + `AttendanceRepositoryAdapterTest`.
- [x] 3.7 `infrastructure/persistence/adapter/PhysicalCapacityAssignmentRepositoryAdapter.java` + `PhysicalCapacityAssignmentRepositoryAdapterTest`, plus new query method on the existing `PhysicalCapacityAssignmentJpaRepository`.
- [x] 3.8 `infrastructure/redis/RedisCheckInLockPort.java` + `RedisCheckInLockPortTest` — `SET NX EX`, any `RuntimeException` fails closed.
- [x] 3.9 `infrastructure/web/dto/{CheckInRequest,AccessQrResponse,CheckInResponse}.java`.
- [x] 3.10 `infrastructure/web/controller/PhysicalCheckInEndpoint.java` (interface) + `PhysicalCheckInController.java` + `PhysicalCheckInControllerTest`.
- [x] 3.11 `infrastructure/web/controller/PhysicalCheckInExceptionHandler.java` + `PhysicalCheckInExceptionHandlerTest` — RFC 9457 Problem Details mapping per exception.

## Phase 4: Wiring & Security

- [x] 4.1 `infrastructure/config/PhysicalConfiguration.java` (modified) + `PhysicalConfigurationTest` — `physicalClock()` bean (renamed from `clock()` to avoid `BeanDefinitionOverrideException` with `VirtualConfiguration.clock()`), `@Value` device-token + `@PostConstruct` fail-fast on `prod|production|staging`, both use-case beans.
- [x] 4.2 `api/auth/.../security/SecurityConfig.java` (modified) — `POST /api/v1/physical/sessions/*/check-ins` → `permitAll()`; `POST /api/v1/physical/sessions/*/access-qr` → `authenticated()`.

## Phase 5: Contract & Integration Verification

- [x] 5.1 `api/openapi/physical-v1.yaml` (modified) — 2 new paths, 3 new schemas, 403 notes updated after the post-review fix.
- [x] 5.2 `api/app/src/test/java/com/menta/app/integration/physical/PhysicalCheckInIntegrationTest.java` — 11 HTTP end-to-end scenarios through the real `SecurityConfig` filter chain covering both endpoints, both status codes, and replay.

## Phase 6: Post-Adversarial-Review Corrections

- [x] 6.1 Added `verifyWithinCheckInWindow` call to `IssuePhysicalAccessQrUseCaseImpl` (new constructor parameter, wiring updated in `PhysicalConfiguration`) — closes a gap where issuance did not enforce the check-in window (Scenario: "Issue a credential for an eligible student" now requires window as precondition).
- [x] 6.2 Added `SessionCancelledException` + cancellation check in both use cases, positioned before the assignment check (Requirement: Cancelled session blocks issuance/check-in ahead of assignment).
- [x] 6.3 Added 3 new integration scenarios to `PhysicalCheckInIntegrationTest` covering the cancellation fix.
- [x] 6.4 Added 2 new unit tests covering the window/cancellation fixes across `IssuePhysicalAccessQrUseCaseImplTest` and `ProcessPhysicalCheckInUseCaseImplTest`.
- [x] 6.5 Updated `api/openapi/physical-v1.yaml` 403 response notes to document `SESSION_CANCELLED` ahead of `OUTSIDE_CHECK_IN_WINDOW`/`CAPACITY_ASSIGNMENT_REQUIRED`.
- [x] 6.6 Added `the_database_rejects_a_second_attendance_row_for_the_same_session_and_student` to `PhysicalCheckInIntegrationTest` — proves the `UNIQUE (session_id, user_id)` constraint against real MySQL via `saveAndFlush`/`DataIntegrityViolationException` (Requirement: Schema-Level Idempotency Guard scenario had zero runtime coverage, flagged by `sdd-verify`).

## Deferred / Out of Scope

- Redis compare-and-delete lock compensation (issue #38 escenario 6) — TTL expiry accepted instead (Design Decision #3).
- Real HMAC signing — placeholder format ships; `FormatQrCredentialSignatureService` isolates the future swap.
- `MANUAL` check-in variant — enum value reserved, behavior deferred to issue #45.
- Android-side visual QR refresh — separate repository/PR.
- Device registry, per-device credentials (US-PHYSICAL-007), attendance query/reporting endpoints.
- Bruno collection for the two new endpoints.

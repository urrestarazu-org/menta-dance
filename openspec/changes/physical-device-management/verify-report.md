```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:3644d91a410b0120d17b858b1df4df5e1fb377c46ee75ce0d36c0ff14a50e49a
verdict: fail
blockers: 1
critical_findings: 1
requirements: 6/7
scenarios: 16/17
test_command: "./gradlew :api:physical:test --tests \"*Device*\" --rerun-tasks && ./gradlew :api:auth:test --tests \"*SecurityConfigTest*\" --rerun-tasks && ./gradlew :api:app:test --tests \"*PhysicalDevice*\" --rerun-tasks && ./gradlew :api:physical:jacocoDomainApplicationCoverageVerification :api:physical:jacocoInfrastructureCoverageVerification --rerun-tasks"
test_exit_code: 0
test_output_hash: sha256:7566a52feb4b729379aef09a2a49b86913293f07ea0dae56eda073164562e049
build_command: "./gradlew :api:physical:checkstyleMain :api:physical:checkstyleTest :api:auth:checkstyleMain :api:auth:checkstyleTest --rerun-tasks"
build_exit_code: 0
build_output_hash: sha256:36dd20ba8c4516b24b251a7f6dd0e28eba709bc2005a5893c80f2a510ae67b19
```

## Verification Report

**Change**: physical-device-management (#44, US-PHYSICAL-007, "Gestión de dispositivos QR")
**Version**: v0.4.0
**Mode**: Strict TDD

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 52 |
| Tasks complete | 52 |
| Tasks incomplete | 0 |

All 52 tasks in `tasks.md` are marked `[x]`, matching the actual file tree (verified, not
self-reported: every named production file and test file exists on disk).

### Build & Tests Execution

Full monorepo `./gradlew check` was deliberately NOT run in this session, per explicit
instruction to avoid the exact hang the prior verify attempt suffered (killed after 10 minutes
with no progress). Targeted, `--rerun-tasks`-forced (cache-bypassing, genuinely re-executed)
commands were run instead, each independently confirmed BUILD SUCCESSFUL with real evidence
captured to files (not self-report):

**Tests**: ✅ all 4 targeted suites passed (0 failures — Gradle fails the build on any test
failure, so BUILD SUCCESSFUL is proof of a clean run)
```text
$ ./gradlew :api:physical:test --tests "*Device*" --rerun-tasks
BUILD SUCCESSFUL in 11m 27s, then again in 1m 41s (two independent runs, both green)
8 actionable tasks: 8 executed
→ covers: DeviceIdTest, PhysicalDeviceTest, SecureRandomDeviceSecretGeneratorTest,
  Sha256DeviceSecretHasherTest, PhysicalDeviceRepositoryAdapterTest (DataJpaTest+Testcontainers),
  PhysicalDeviceAuditRepositoryAdapterTest (DataJpaTest+Testcontainers),
  Register/Get/Rotate/Revoke/ListPhysicalDeviceUseCaseImplTest, PhysicalDeviceViewLeakGuardTest,
  Transactional{Register,Rotate,Revoke}PhysicalDeviceUseCaseTest, PhysicalDeviceAdminControllerTest

$ ./gradlew :api:auth:test --tests "*SecurityConfigTest*" --rerun-tasks
BUILD SUCCESSFUL in 9s
8 actionable tasks: 8 executed
→ covers: the parameterized 5-path × {anonymous 401, STUDENT 403, INSTRUCTOR 403, ADMIN passes}
  device-endpoint matrix (R6), plus all pre-existing SecurityConfig cases (regression)

$ ./gradlew :api:app:test --tests "*PhysicalDevice*" --rerun-tasks
BUILD SUCCESSFUL in 1m 3s
18 actionable tasks: 18 executed
→ covers: PhysicalDeviceManagementIntegrationTest (rollback proof C5, full lifecycle,
  STUDENT/INSTRUCTOR/anonymous through the real filter chain) and
  PhysicalDeviceManagementMigrationIntegrationTest (V23 free/disjoint)

$ ./gradlew :api:physical:jacocoDomainApplicationCoverageVerification \
    :api:physical:jacocoInfrastructureCoverageVerification --rerun-tasks
BUILD SUCCESSFUL in 2m 28s
10 actionable tasks: 10 executed
→ this depends on the FULL unfiltered :api:physical:test (364 tests incl. ArchitectureTest,
  PhysicalConfigurationTest), so its green result is also full-module regression evidence.
  Both the 95% domain+application and 90% infrastructure floors held (the verification task
  fails the build below floor; it did not fail).
```

**Build (Checkstyle)**: ✅ passed
```text
$ ./gradlew :api:physical:checkstyleMain :api:physical:checkstyleTest \
    :api:auth:checkstyleMain :api:auth:checkstyleTest --rerun-tasks
BUILD SUCCESSFUL in 10s
12 actionable tasks: 12 executed
386 pre-existing WARN-severity violations reported, all in unrelated api/auth test files
(PasswordResetTokenTest, UserTest, RefreshTokenTest, UserIdTest, ActivationTokenTest,
PasswordTest, WeakPasswordExceptionTest — snake_case test method names, a repo-wide
pre-existing convention gap, not introduced by this change). Zero violations in any
physical-device-management file or in SecurityConfigTest.
```

**Coverage**: 95% domain+application / 90% infrastructure thresholds → ✅ gate passed
(exact fresh percentage not printed by the non-verbose task; the verification task itself
fails the build below floor, and it did not fail)

**Not run** (disclosed, not silently assumed): the full monorepo `./gradlew check`. Per
apply-progress, that full run in the apply session hit one pre-existing, unrelated
`VirtualCourseManagementIntegrationTest` H2 failure, reproduced there via `git stash` to confirm
it predates this change. This verify session did not independently re-confirm that specific
claim, to avoid re-triggering the exact hang this task explicitly warned about.

### Spec Compliance Matrix
| Requirement | Scenario | Test | Result |
|-------------|----------|------|--------|
| R1 Device registration, one-time secret reveal | Admin registers and receives raw secret once | `RegisterPhysicalDeviceUseCaseImplTest.hashes_before_persisting_and_never_persists_the_raw_value` / `.returned_raw_secret_equals_the_generators_output`; `PhysicalDeviceAdminControllerTest.register_returns_201_with_the_raw_secret`; `PhysicalDeviceManagementIntegrationTest.register_get_rotate_revoke_second_revoke_and_rotate_after_revoke_round_trip_through_the_full_stack` | ✅ COMPLIANT |
| R1 | Registration accepts optional expiresAt as inert metadata | `PhysicalDeviceTest.register_creates_an_active_device_with_all_fields_set` (domain-level echo only) + `PhysicalDeviceRepositoryAdapterTest.round_trips_a_non_null_expires_at` (persistence-level only) | ❌ UNTESTED at the scenario's actual boundary — no `PhysicalDeviceAdminControllerTest`/integration test sends a future `expiresAt` and asserts the `201` response echoes it. Confirmed by exhaustive grep: zero matches for `expiresAt` in `PhysicalDeviceAdminControllerTest.java` or `PhysicalDeviceManagementIntegrationTest.java`, and no `RegisterPhysicalDeviceRequest`-specific test file exists at all |
| R2 Get never exposes secret/hash | Get returns metadata without any secret field | `GetPhysicalDeviceUseCaseImplTest.returns_the_view_for_a_known_id`; `PhysicalDeviceViewLeakGuardTest` (type-level, both DTOs); `PhysicalDeviceAdminControllerTest.get_returns_200_with_no_secret_field`; integration test get step | ✅ COMPLIANT |
| R2 | Unknown device id returns 404 | `GetPhysicalDeviceUseCaseImplTest.unknown_id_throws_device_not_found`; `PhysicalDeviceAdminControllerTest.get_unknown_device_returns_404` | ✅ COMPLIANT |
| R3 Rotation invalidates previous hash | Rotation issues new secret, invalidates old hash | `PhysicalDeviceTest.rotate_secret_on_active_returns_a_new_instance_with_the_new_hash_receiver_unchanged`; `RotatePhysicalDeviceSecretUseCaseImplTest.replaces_the_stored_hash_with_a_different_value_on_an_active_device`; `PhysicalDeviceRepositoryAdapterTest.unique_secret_hash_rejects_a_duplicate`; integration test rotate step | ✅ COMPLIANT |
| R4 Revocation is terminal | Revoking active device succeeds | `PhysicalDeviceTest.revoke_on_active_transitions_to_revoked`; `RevokePhysicalDeviceUseCaseImplTest.active_device_transitions_to_revoked_with_one_audit_row`; `PhysicalDeviceAdminControllerTest.revoke_returns_200_with_the_revoked_status`; integration revoke step | ✅ COMPLIANT |
| R4 | Re-revoking rejected, not a no-op | `PhysicalDeviceTest.revoke_on_already_revoked_throws_device_already_revoked_exception`; `RevokePhysicalDeviceUseCaseImplTest.already_revoked_device_throws_device_already_revoked_and_appends_no_audit_row`; `PhysicalDeviceAdminControllerTest.revoke_on_an_already_revoked_device_returns_409_with_the_already_revoked_code`; integration `secondRevokeResponse` 409 | ✅ COMPLIANT |
| R4 | Rotating a revoked device rejected | `PhysicalDeviceTest.rotate_secret_on_revoked_throws_device_revoked_exception`; `RotatePhysicalDeviceSecretUseCaseImplTest.revoked_device_throws_device_revoked_and_changes_nothing`; `PhysicalDeviceAdminControllerTest.rotate_on_a_revoked_device_returns_409_with_the_revoked_code`; integration `rotateAfterRevokeResponse` 409 | ✅ COMPLIANT |
| R5 Fleet listing excludes secrets/hashes | Listing returns fleet with no secret/hash anywhere | `ListPhysicalDevicesUseCaseImplTest.returns_all_devices_active_and_revoked_with_no_filtering`; `PhysicalDeviceViewLeakGuardTest`; `PhysicalDeviceAdminControllerTest.list_returns_200_with_no_secret_field_for_any_device`; integration list step | ✅ COMPLIANT |
| R6 ADMIN-only endpoints | STUDENT rejected on register | `SecurityConfigTest.an_authenticated_student_on_a_device_route_is_forbidden` (parameterized, 5 paths incl. register) | ✅ COMPLIANT |
| R6 | INSTRUCTOR rejected on register | `SecurityConfigTest.an_authenticated_instructor_on_a_device_route_is_forbidden` (parameterized) | ✅ COMPLIANT |
| R6 | Anonymous rejected on register | `SecurityConfigTest.an_anonymous_caller_on_a_device_route_is_rejected_with_401` (parameterized); integration `an_anonymous_request_is_rejected_with_401` | ✅ COMPLIANT |
| R6 | STUDENT rejected on revoke | `SecurityConfigTest.an_authenticated_student_on_a_device_route_is_forbidden` (same parameterized source includes revoke path); integration `student_is_rejected_before_reaching_the_controller` | ✅ COMPLIANT |
| R6 | INSTRUCTOR rejected on revoke | `SecurityConfigTest.an_authenticated_instructor_on_a_device_route_is_forbidden` (revoke path); integration `instructor_is_rejected_before_reaching_the_controller` | ✅ COMPLIANT |
| R6 | Anonymous rejected on revoke | `SecurityConfigTest.an_anonymous_caller_on_a_device_route_is_rejected_with_401` (revoke path) | ✅ COMPLIANT |
| R7 Exactly one audit row per action | Registration appends one audit row | `RegisterPhysicalDeviceUseCaseImplTest.appends_exactly_one_audit_row_with_device_registered_and_a_null_previous_value` (+ `verifyNoMoreInteractions`); `PhysicalDeviceAuditRepositoryAdapterTest.append_persists_one_row_with_the_exact_given_fields` | ✅ COMPLIANT |
| R7 | Rotation and revocation each append their own single row | `RotatePhysicalDeviceSecretUseCaseImplTest.appends_one_audit_row_with_old_and_new_secret_hash_prefixes`; `RevokePhysicalDeviceUseCaseImplTest.active_device_transitions_to_revoked_with_one_audit_row`; `PhysicalDeviceAuditRepositoryAdapterTest.rows_are_append_only_and_survive_a_subsequent_device_update`; integration rollback test `an_audit_append_failure_leaves_no_device_row_behind` proves the same-transaction guarantee | ✅ COMPLIANT |

**Compliance summary**: 16/17 scenarios COMPLIANT, 1/17 UNTESTED (R1 Scenario 2)

### Correctness (Static Evidence)
| Requirement | Status | Notes |
|------------|--------|-------|
| D1 SHA-256, not bcrypt/argon2 | ✅ Implemented | `Sha256DeviceSecretHasher` uses `MessageDigest.getInstance("SHA-256")`; known-vector test confirms |
| D2 Additive-only, checkin untouched | ✅ Confirmed | `git diff <pre-#44>..HEAD` is empty for `ProcessPhysicalCheckInUseCaseImpl.java`, `Attendance.java`, `CheckInCommand.java`, `V15__physical_attendances.sql`; `PhysicalConfiguration.java` diff is 90 insertions / 0 deletions; `app.physical.checkin.device-token` references unchanged |
| D3 No `:api:auth` dependency | ✅ Confirmed | `api/physical/build.gradle.kts` depends only on `project(":api:shared")` — `com.menta.auth.*` is not even compilable from `:api:physical`, stronger than an ArchUnit rule |
| D4 One-time reveal (structural) | ✅ Implemented | Only `RegisterPhysicalDeviceUseCase`/`RotatePhysicalDeviceSecretUseCase` return `PhysicalDeviceSecretResult`; `get`/`list`/`revoke` return the secret-less `PhysicalDeviceView` — type-level guarantee, backed by `PhysicalDeviceViewLeakGuardTest` |
| D5 Revocation terminal | ✅ Implemented | `PhysicalDevice.revoke()`/`.rotateSecret()` both throw on a `REVOKED` receiver; two distinct exception types |
| C5 Transactional decorators, one commit | ✅ Confirmed | `PhysicalDeviceAuditRepositoryAdapter.append` uses `@Transactional(propagation = REQUIRED)`; decorators carry `@Transactional`; integration rollback test proves a forced audit failure leaves zero device rows |
| C7 SecurityConfig matcher placement | ✅ Confirmed | `.requestMatchers("/api/v1/admin/physical/devices/**").hasRole("ADMIN")` at line 306, before the generic `/api/v1/admin/**` rule |
| C8 V23 migration | ✅ Confirmed | Matches design DDL exactly: `CHAR(64)` hash, `uq_physical_devices_secret_hash`, FK on audit table |
| C10 No leaked secret/hash in any DTO | ✅ Confirmed | Reflective `PhysicalDeviceViewLeakGuardTest` covers `PhysicalDeviceView`, `PhysicalDeviceResponse`, `PhysicalDeviceListResponse` |
| OpenAPI updated | ✅ Confirmed | `api/openapi/physical-v1.yaml` has 5 new "Gestión de dispositivos" operations with RFC 9457 examples |
| Bruno requests | ✅ Confirmed | 5 files under `bruno/API - Direct/physical/devices/` |

### Coherence (Design)
| Decision | Followed? | Notes |
|----------|-----------|-------|
| C1 Terminal revocation as aggregate invariant | ✅ Yes | Enum + throwing transition methods, not a service-level check |
| C2 Raw secret cannot leak by type | ✅ Yes | Two DTOs; leak guard test |
| C3 Four framework-free out-ports | ✅ Yes | `PhysicalDeviceRepository`, `PhysicalDeviceAuditRepository`, `DeviceSecretGenerator`, `DeviceSecretHasher` |
| C4 One use case per operation, hash-prefix-only audit | ✅ Yes | `PhysicalDeviceAuditSnapshot` restricts to `status`/`secretHashPrefix` (8 hex chars) |
| C6 409 not 422 for revoked-state rejections | ✅ Yes | `PhysicalDeviceExceptionHandler` maps both revoked exceptions to `CONFLICT` |
| C9 PhysicalConfiguration additive-only | ✅ Yes | 0 deletions in the diff |
| 3-slice delivery (P1/P2/P3) | ✅ Yes | Matches the 3 merged PRs (#263/#264/#265) exactly |

### Issues Found

**CRITICAL**:
1. **R1 Scenario 2 ("Registration accepts an optional expiresAt as inert metadata") has no
   covering test at the scenario's own boundary.** The spec scenario is explicitly an HTTP-level
   contract ("WHEN they call `POST .../devices` with a future `expiresAt` THEN the response is
   `201` and echoes that `expiresAt`"). No test drives this through `PhysicalDeviceAdminController`
   or the integration test with a non-null `expiresAt` — confirmed by exhaustive grep across both
   files (zero matches). The domain (`PhysicalDeviceTest`) and persistence
   (`PhysicalDeviceRepositoryAdapterTest`) layers each independently test that *their own* hop
   preserves `expiresAt`, and the full code path (`RegisterPhysicalDeviceUseCaseImpl` →
   `PhysicalDeviceResultMapper.toView` → `PhysicalDeviceResponse.from`) is a branch-free field
   copy with no logic that could silently drop the value — so the actual behavioral risk is low.
   But per this skill's hard rule ("a spec scenario is compliant only when a covering test passed
   at runtime"), a scenario with no covering test at its own layer is CRITICAL/UNTESTED, not a
   judgment call. **Fix**: add one assertion (either to `PhysicalDeviceAdminControllerTest.register_returns_201_with_the_raw_secret`
   or a new focused test) that POSTs a non-null `expiresAt` and asserts it is present in the `201`
   response body; optionally extend the integration test's lifecycle to also cover this.

**WARNING**:
1. `openspec/changes/physical-device-management/proposal.md`'s 9 "Success Criteria" checkboxes are
   all still unchecked (`- [ ]`) even though 8 of the 9 are functionally met by the evidence in
   this report; the 9th ("OpenAPI updated; `./gradlew check` passes") is met for the OpenAPI half
   but the full-monorepo `./gradlew check` half was not independently re-run this session (see
   Build & Tests Execution). Recommend checking off the 8 fully-met criteria and leaving the 9th
   open pending either a full check run or an explicit accepted-risk note.

**SUGGESTION**:
1. Strict TDD evidence for this change lives in `tasks.md`'s per-task RED/GREEN annotations (52
   tasks) rather than a dedicated "TDD Cycle Evidence" table in `apply-progress`, as
   `strict-tdd-verify.md` expects. Cross-checked against the actual file tree: every RED task names
   a test file that exists with real, non-trivial assertions, and every GREEN task names a
   production file that exists — the substance is present, only the table format differs. No
   tautological, ghost-loop, or assertion-free tests were found in the ~20 test files sampled across
   domain/application/infrastructure/web layers (the one CRITICAL finding above is a coverage gap,
   not an assertion-quality defect).

### TDD Compliance
| Check | Result | Details |
|-------|--------|---------|
| TDD Evidence reported | ⚠️ Alternate format | Per-task RED/GREEN in `tasks.md`, not a dedicated table in `apply-progress` (see Suggestion #1) |
| All tasks have tests | ✅ | 52/52 tasks; every RED task's named test file exists on disk |
| RED confirmed (tests exist) | ✅ | Verified for all ~24 physical-device test files + `SecurityConfigTest` extension + `PhysicalDeviceManagementIntegrationTest` |
| GREEN confirmed (tests pass) | ✅ | 4/4 targeted `--rerun-tasks` suites BUILD SUCCESSFUL (see Build & Tests Execution) |
| Triangulation adequate | ✅ | Multiple distinct-value assertions throughout (ACTIVE vs REVOKED, old hash vs new hash, register vs rotate vs revoke audit actions) |
| Safety Net for modified files | ✅ | `SecurityConfig.java`/`SecurityConfigTest.java` (modified, not new) — pre-existing cases untouched and still passing; `PhysicalConfiguration.java` (modified) — existing device-token assertions unchanged per apply-progress and confirmed additive-only via diff |

**TDD Compliance**: 5/6 checks fully passed (1 alternate-format, not a defect)

### Assertion Quality
✅ All assertions verify real behavior — no tautologies, ghost loops, or assertion-free tests
found in the ~20 test files sampled (`PhysicalDeviceTest`, `DeviceIdTest`,
`RegisterPhysicalDeviceUseCaseImplTest`, `RotatePhysicalDeviceSecretUseCaseImplTest`,
`RevokePhysicalDeviceUseCaseImplTest`, `GetPhysicalDeviceUseCaseImplTest`,
`ListPhysicalDevicesUseCaseImplTest`, `PhysicalDeviceAdminControllerTest`,
`PhysicalDeviceViewLeakGuardTest`, `SecureRandomDeviceSecretGeneratorTest`,
`Sha256DeviceSecretHasherTest`, `PhysicalDeviceRepositoryAdapterTest`,
`PhysicalDeviceAuditRepositoryAdapterTest`, `TransactionalRegisterPhysicalDeviceUseCaseTest`,
`SecurityConfigTest`, `PhysicalDeviceManagementIntegrationTest`). The gap found is a missing
scenario, not a trivial assertion.

### Verdict
FAIL
1 CRITICAL finding (R1 Scenario 2 has no covering test at its HTTP boundary — a narrow,
single-scenario, low-behavioral-risk gap, not a broad implementation defect); everything else
(6/7 requirements, 16/17 scenarios, all 52 tasks, all 4 targeted test suites, Checkstyle, both
coverage gates, D1–D6/C1–C10 design fidelity, D2 byte-unchanged confirmation) is genuinely green
with real runtime evidence. Recommend routing back to `sdd-apply` for the one missing test
before `sdd-archive`.

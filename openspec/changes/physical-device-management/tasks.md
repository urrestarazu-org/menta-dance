# Tasks: Physical QR Device Management (#44, US-PHYSICAL-007)

## Fixed Facts (do not reopen)

C1–C10 (design) and D1–D6 (proposal) are locked. No task below re-derives:
SHA-256 over bcrypt/argon2 for a 256-bit CSPRNG preimage (D1); this change
is additive-only, `ProcessPhysicalCheckInUseCaseImpl`/`Attendance`/
`CheckInCommand`/`app.physical.checkin.device-token` are untouched (D2);
the generator/hasher are *replicated* from `:api:auth`'s shape, never
imported (D3); one-time reveal is structural — only `register`/`rotate`
return `PhysicalDeviceSecretResult`, `get`/`list` return the
secret-less `PhysicalDeviceView` (D4, C2); `REVOKED` is absorbing —
`revoke()`/`rotateSecret()` both throw on an already-`REVOKED` device
(D5, C1); D6 (constant-time comparison) has **no call site in this
change** — it is a constraint recorded for the Escenario 5 follow-up,
not a task here; the audit payload carries an 8-hex-char `secretHashPrefix`,
never a hash or the raw secret (C4); transactional decorators live in
`infrastructure/transaction/`, not `@Transactional` on the use case (C5);
409, not 422, for revoked-state rejections (C6); the `SecurityConfig`
matcher goes immediately after line 301, before line 312 (C7); `V23` is
free, `CHAR(64)` for the hash, FK kept on the audit table (C8); no new
ArchUnit rule is required — the leak guard is a dedicated test, not a
rule (C10).

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1,500–1,800 across the whole change (1 aggregate, 3 exceptions, 4 out-ports, 5 in-ports/impls, 3 decorators, 2 JPA entity chains, 1 controller + 4 DTOs + advice, 1 migration, plus tests for all of it) |
| Per-slice estimate | Slice 1 (domain+schema) ~450–550; Slice 2 (use cases+audit) ~500–650; Slice 3 (web+security) ~450–550 |
| 400-line budget risk | High — each slice individually approaches or exceeds 400 lines once tests are counted |
| Chained PRs recommended | Yes |
| Suggested split | 3 PRs, P1 → P2 → P3, matching design's own 3-slice "Migration / Rollout" plan |
| Delivery strategy | ask-on-risk |
| Chain strategy | **Recommended: stacked-to-main** (proven on #39/#38 for this exact module) — pending user confirmation before `sdd-apply` starts |

```text
Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High
```

**Delivery-strategy recommendation.** Keep design's 3 slices as 3 chained
PRs, not 2. Slice 1 alone (aggregate + 3 exceptions + 2 JPA entity chains +
generator/hasher + their tests) is already comparable in size to a full P1
in the #39 precedent (~750–950 total lines split across 2 slices there,
with fewer files). Merging slices 1+2 here would combine the aggregate,
JPA persistence, 4 out-ports, 5 use cases, 3 decorators, and audit
encoding into one diff — a materially harder review than either #39 slice
and the design's own "review budget: High" forecast. Each of the 3 slices
here has a clean, independent finish line (schema unused by anything;
use cases unused until the controller exists; controller is the only
externally-reachable surface), so the stacked-to-main pattern that shipped
#39 (P1 self endpoint → P2 elevated endpoint, both merged to `develop`
independently) applies directly with a third link. **Recommendation:
3 chained PRs, stacked-to-main.**

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| P1 | `PhysicalDevice`, `DeviceId`, `DeviceStatus`, 3 exceptions, `V23`, JPA entities/repositories/mappers/adapters, generator/hasher adapters | PR 1 | `:api:physical:test --tests "*PhysicalDeviceTest*" --tests "*DeviceIdTest*" --tests "*PhysicalDeviceRepositoryAdapterTest*" --tests "*SecureRandomDeviceSecretGeneratorTest*" --tests "*Sha256DeviceSecretHasherTest*"` | `@DataJpaTest` + Testcontainers MySQL 8 for entity/migration round-trip | Revert all P1 files; nothing else reads `PhysicalDevice*` yet |
| P2 | 4 out-ports, `PhysicalDeviceView`/`PhysicalDeviceSecretResult`/`RegisterPhysicalDeviceCommand`, 5 in-ports + `*Impl`, 3 decorators, `PhysicalConfiguration` wiring, audit encoding + leak guard | PR 2 | `:api:physical:test --tests "*RegisterPhysicalDeviceUseCaseImplTest*" --tests "*RotatePhysicalDeviceSecretUseCaseImplTest*" --tests "*RevokePhysicalDeviceUseCaseImplTest*" --tests "*GetPhysicalDeviceUseCaseImplTest*" --tests "*ListPhysicalDevicesUseCaseImplTest*" --tests "*PhysicalConfigurationTest*"` | Mockito unit tests only — no controller yet, so no MockMvc/Testcontainers needed | Revert P2 files only; P1's schema/adapters keep compiling, unused |
| P3 | Controller, DTOs, marker/advice, `SecurityConfig` matcher, OpenAPI, Bruno, security + rollback + full-lifecycle integration tests | PR 3 | `:api:physical:test --tests "*PhysicalDeviceAdminControllerTest*" --tests "*PhysicalDeviceManagementIntegrationTest*"` + `:api:auth:test --tests "*SecurityConfigTest*"` | Testcontainers MySQL 8, full filter chain, for rollback proof and lifecycle integration | Revert P3 files only; endpoints disappear, P1/P2 stay dead code until a future change wires them |

## Phase P1: Domain + schema (R3, R4)

- [x] 1.1 RED: `DeviceIdTest` — `of(UUID)`/`of(String)`/`generate()` happy paths; `of(null/blank/malformed-UUID-string)` → `IllegalArgumentException` (mirrors `SessionIdTest`).
- [x] 1.2 GREEN: `physical/domain/model/DeviceId.java` — `final class`, private constructor, three factories.
- [x] 1.3 GREEN: `physical/domain/model/DeviceStatus.java` — closed enum `ACTIVE`, `REVOKED`.
- [x] 1.4 GREEN: `physical/domain/exception/DeviceNotFoundException.java`, `DeviceAlreadyRevokedException.java`, `DeviceRevokedException.java` — `BusinessException` subclasses with `DEVICE_NOT_FOUND`/`DEVICE_ALREADY_REVOKED`/`DEVICE_REVOKED`.
- [x] 1.5 RED: `PhysicalDeviceTest` — `register(...)` → `ACTIVE`, all fields set; `rotateSecret(...)` on `ACTIVE` returns a new instance with the new hash, receiver unchanged; `rotateSecret(...)` on `REVOKED` → `DeviceRevokedException`; `revoke()` on `ACTIVE` → `REVOKED`; `revoke()` on `REVOKED` → `DeviceAlreadyRevokedException`; each transition is immutable-with-copy (mirrors `PhysicalSessionTest`'s `withCapacity` cases).
- [x] 1.6 GREEN: `physical/domain/model/PhysicalDevice.java` — aggregate with throwing transition methods (C1).
- [x] 1.7 RED: `PhysicalDeviceManagementMigrationIntegrationTest` (mirrors P1 1.2's precedent from #39) — confirms `V23` is free against both `db/migration` and any combined `db/rollback` namespace before creating the file.
- [x] 1.8 GREEN: `api/app/src/main/resources/db/migration/V23__physical_devices.sql` — `physical_devices` + `physical_device_audit`, exact DDL from design C8.
- [x] 1.9 RED: `SecureRandomDeviceSecretGeneratorTest` — emits 43-char Base64url with no `=`, decodes to 32 bytes, two calls never repeat (mirrors `SecureActivationTokenAdaptersTest`).
- [x] 1.10 GREEN: `physical/infrastructure/device/SecureRandomDeviceSecretGenerator.java` — JDK `SecureRandom` only, no `:api:auth` import (D3).
- [x] 1.11 RED: `Sha256DeviceSecretHasherTest` — known lowercase SHA-256 hex vector for a fixed input; rejects `null`.
- [x] 1.12 GREEN: `physical/infrastructure/device/Sha256DeviceSecretHasher.java` — JDK `MessageDigest`/`HexFormat` only, replicated body (D3).
- [x] 1.13 RED: `PhysicalDeviceRepositoryAdapterTest` (`@DataJpaTest` + Testcontainers MySQL 8) — round-trip of all fields incl. null `expiresAt`; `uq_physical_devices_secret_hash` rejects a duplicate hash; `findAll()` ordered by `created_at ASC, id ASC`.
- [x] 1.14 GREEN: `physical/infrastructure/persistence/entity/PhysicalDeviceJpaEntity.java`, `repository/PhysicalDeviceJpaRepository.java`, `mapper/PhysicalDeviceJpaMapper.java`, `adapter/PhysicalDeviceRepositoryAdapter.java`. Deviation from the sketch: filenames use this module's existing `*JpaEntity`/`*JpaMapper` naming (matches `PhysicalSessionJpaEntity`/`PhysicalSessionJpaMapper`), not the bare `*Entity`/`*Mapper` in the task text. Neither the adapter nor the `Device`/`DeviceAudit` adapters implement a port interface yet — `PhysicalDeviceRepository`/`PhysicalDeviceAuditRepository` are created in P2 (task 2.2); wiring an `implements` clause is a one-line P2 addition. `secret_hash` needed an explicit `@Column(unique = true)` on the JPA entity (in addition to the `V23` migration's `UNIQUE KEY`) because `@DataJpaTest` generates its schema from Hibernate DDL-auto, not from the Flyway migration — first RED run passed for the wrong reason (constraint silently absent) until this was added.
- [x] 1.15 RED: `PhysicalDeviceAuditRepositoryAdapterTest` — `append(...)` persists one row with the exact `actorId`/`action`/`previousValue`/`newValue`; rows are append-only and survive a subsequent device update (mirrors `VirtualCourseAuditRepositoryAdapterTest`).
- [x] 1.16 GREEN: `physical/infrastructure/persistence/entity/PhysicalDeviceAuditJpaEntity.java`, `repository/PhysicalDeviceAuditJpaRepository.java`, `adapter/PhysicalDeviceAuditRepositoryAdapter.java`.
- [x] 1.17 Verify: `:api:physical:test --tests "*Device*"` green (28 tests); `:api:physical:jacocoDomainApplicationCoverageVerification` (0.95 floor) and `jacocoInfrastructureCoverageVerification` (0.90 floor) green; `:api:physical:check` (ArchUnit + Checkstyle + both coverage gates) green. P1 ready for PR.

### P1 deviation note: migration version number

**No deviation** — `V23` was independently re-verified free before creation (task 1.7), per this
apply batch's instructions to distrust design's own "V23 is free" claim after the #39 "V21 is
free" precedent proved wrong. Confirmed: `classpath:db/migration`'s highest applied version is
`V22__physical_attendance_history_indexes.sql`; `classpath:db/rollback`'s highest is
`V21__revert_billing_purchase_sessions.sql`. `V23` is disjoint from both, verified by
`PhysicalDeviceManagementMigrationIntegrationTest` (mirrors `PhysicalAttendanceHistoryMigrationIntegrationTest`'s
same verification for V22), including a combined-namespace `target("21")` migrate call that would
throw a `FlywayValidateException` on a real collision.

## Phase P2: Use cases + audit (R1, R2, R3, R5, R7)

- [x] 2.1 GREEN: `physical/application/dto/PhysicalDeviceView.java`, `PhysicalDeviceSecretResult.java`, `RegisterPhysicalDeviceCommand.java` (C2).
- [x] 2.2 GREEN: `physical/application/port/out/PhysicalDeviceRepository.java`, `PhysicalDeviceAuditRepository.java`, `DeviceSecretGenerator.java`, `DeviceSecretHasher.java` (C3). P1's `PhysicalDeviceRepositoryAdapter`/`PhysicalDeviceAuditRepositoryAdapter`/`SecureRandomDeviceSecretGenerator`/`Sha256DeviceSecretHasher` now `implements` these ports, as P1's own deviation notes anticipated; `PhysicalDeviceAuditRepositoryAdapterTest` updated to pass `DeviceId.of(deviceId)` instead of a raw `UUID` for `append(...)`'s first argument.
- [x] 2.3 GREEN: `physical/application/port/in/{Register,Get,RotateSecret,Revoke,List}PhysicalDeviceUseCase.java` — five in-ports, signatures from design C4. Deviation: the list in-port is `ListPhysicalDevicesUseCase` (plural "Devices"), matching design C4's table and the `ListPhysicalDevicesUseCaseImpl`/`ListPhysicalDevicesUseCaseImplTest` names this same task list uses in 2.12/2.13 — the literal brace expansion in this task's own filename pattern would have produced the inconsistent singular `ListPhysicalDeviceUseCase`.
- [x] 2.4 RED: `RegisterPhysicalDeviceUseCaseImplTest` — hashes before persisting, never persists the raw value; returned `rawSecret` equals the generator's output; exactly one audit row with action `DEVICE_REGISTERED`, `previousValue == null`, `newValue` containing `secretHashPrefix` of 8 hex chars; `verifyNoMoreInteractions(auditRepository)`.
- [x] 2.5 GREEN: `physical/application/usecase/RegisterPhysicalDeviceUseCaseImpl.java`.
- [x] 2.6 RED: `GetPhysicalDeviceUseCaseImplTest` — returns `PhysicalDeviceView` for a known id; unknown id → `DeviceNotFoundException`.
- [x] 2.7 GREEN: `physical/application/usecase/GetPhysicalDeviceUseCaseImpl.java`.
- [x] 2.8 RED: `RotatePhysicalDeviceSecretUseCaseImplTest` — replaces the stored hash with a *different* value on an `ACTIVE` device; new `rawSecret` returned; one audit row `DEVICE_SECRET_ROTATED` with old/new `secretHashPrefix` (both 8 hex chars, different); unknown id → `DeviceNotFoundException`; `REVOKED` device → `DeviceRevokedException`, no hash changed, no audit row appended.
- [x] 2.9 GREEN: `physical/application/usecase/RotatePhysicalDeviceSecretUseCaseImpl.java`.
- [x] 2.10 RED: `RevokePhysicalDeviceUseCaseImplTest` — `ACTIVE` → `REVOKED`, one audit row `DEVICE_REVOKED`; unknown id → `DeviceNotFoundException`; already-`REVOKED` → `DeviceAlreadyRevokedException`, no audit row appended.
- [x] 2.11 GREEN: `physical/application/usecase/RevokePhysicalDeviceUseCaseImpl.java`.
- [x] 2.12 RED: `ListPhysicalDevicesUseCaseImplTest` — returns all devices as `PhysicalDeviceView`, `ACTIVE` and `REVOKED` both included, no filtering.
- [x] 2.13 GREEN: `physical/application/usecase/ListPhysicalDevicesUseCaseImpl.java`.
- [x] 2.14 RED: **Leak guard** — extended the register/rotate impl tests with `ArgumentCaptor` assertions that no captured audit `previousValue`/`newValue` contains the raw secret or any 64-char hex string; added `PhysicalDeviceViewLeakGuardTest`, a reflective test asserting `PhysicalDeviceView` declares no component whose name contains `secret` or `hash` (C2, C10).
- [x] 2.15 GREEN: audit encoding lives in `PhysicalDeviceAuditSnapshot` (`registered(...)`/`statusAndHashPrefix(...)`), a package-private helper class in `application/usecase/`, building `status=...;secretHashPrefix=<8 hex>;expiresAt=<iso-8601 or null>` strings per design C4's table. Written alongside 2.5 so the leak-guard tests were green on first run — no separate red/green cycle needed beyond what 2.4/2.8 already exercised.
- [x] 2.16 RED: `TransactionalRegisterPhysicalDeviceUseCaseTest`, `TransactionalRotatePhysicalDeviceSecretUseCaseTest`, `TransactionalRevokePhysicalDeviceUseCaseTest` — each decorator delegates to the wrapped `*Impl` and carries `@Transactional` on its interface method (mirrors `TransactionalCreateVirtualCourseUseCaseTest`; class-level `@Transactional(propagation = REQUIRED)` is Physical's adapter convention, not the decorator's — the decorator itself uses plain `@Transactional`, exactly like `TransactionalCreateVirtualCourseUseCase`).
- [x] 2.17 GREEN: `physical/infrastructure/transaction/Transactional{Register,Rotate,Revoke}PhysicalDeviceUseCase.java` (C5). File names use `TransactionalRotatePhysicalDeviceSecretUseCase`/`TransactionalRevokePhysicalDeviceUseCase`, matching the in-port names (`RotatePhysicalDeviceSecretUseCase`/`RevokePhysicalDeviceUseCase`) rather than this task's abbreviated `RotateSecret`/`Revoke` placeholders.
- [x] 2.18 RED: extended `PhysicalConfigurationTest` — new beans wired (generator, hasher, five use cases, three decorated); existing device-token assertions unchanged (D2, C9).
- [x] 2.19 GREEN: `physical/infrastructure/config/PhysicalConfiguration.java` — additive beans only (C9).
- [x] 2.20 Verify: `:api:physical:test --tests "*PhysicalDevice*UseCase*" --tests "*Transactional*Device*" --tests "*PhysicalConfigurationTest*"` green (45 tests, 0 failures/errors); `:api:physical:jacocoDomainApplicationCoverageVerification`/`jacocoInfrastructureCoverageVerification` green; `:api:physical:test --tests "*ArchitectureTest*"` green (7 tests) confirming no `com.menta.auth.*` import was introduced. P2 ready for PR.

## Phase P3: Web + security (R1, R2, R5, R6)

- [x] 3.1 GREEN: `physical/infrastructure/web/dto/RegisterPhysicalDeviceRequest.java` (`@NotBlank`/`@Size` on name/location), `PhysicalDeviceResponse.java`, `PhysicalDeviceSecretResponse.java`, `PhysicalDeviceListResponse.java` — each with `from(...)` factory (C6).
- [x] 3.2 RED: extend the leak-guard reflective test (2.14) to also cover `PhysicalDeviceResponse` and `PhysicalDeviceListResponse` — no `secret`/`hash` component. Added to the existing `PhysicalDeviceViewLeakGuardTest` class (application/dto package) rather than a new file, importing the two web-layer types.
- [x] 3.3 GREEN: `physical/infrastructure/web/controller/PhysicalDeviceEndpoint.java` (marker) + `PhysicalDeviceExceptionHandler.java` — `DeviceNotFoundException` → `404`, `DeviceAlreadyRevokedException`/`DeviceRevokedException` → `409` with distinct codes, `IllegalArgumentException` → `400 INVALID_REQUEST`, `MethodArgumentNotValidException` → `400` (C6).
- [x] 3.4 RED: `PhysicalDeviceAdminControllerTest` (MockMvc standalone, mirrors `PhysicalCourseAdminControllerTest`) — `201` + secret on register; `200` + secret on rotate; `200` no-secret body on get/list; `DeviceNotFoundException` → `404`; both revoked-state exceptions → `409` with distinct codes; malformed `{deviceId}` → `400`. Deviation: `get`/`rotate`/`revoke` take `String deviceId` at the use-case layer (design C4), not the controller — the controller never parses the id itself, so the malformed-id 400 cases stub the mocked use case to throw `IllegalArgumentException`, reproducing what the real `DeviceId.of(...)` does.
- [x] 3.5 GREEN: `physical/infrastructure/web/controller/PhysicalDeviceAdminController.java` — five endpoints, `@RequestMapping("/api/v1/admin/physical/devices")`, `actingUserId(authentication)` (C6).
- [x] 3.6 RED: extend `SecurityConfigTest` — parameterized cases for all five paths: anonymous → `401`; `STUDENT` → `403`; `INSTRUCTOR` → `403`; plus ADMIN passes the security layer (C7). Used `@ParameterizedTest`/`@MethodSource` over the five paths, per design's explicit ask for "parameterized" cases. As design's Open Questions section predicted, these already pass BEFORE 3.7's matcher exists (the generic `/api/v1/admin/**` → `hasRole("ADMIN")` rule already produces the same outcome) — the matcher's value is explicitness and a regression anchor against a future `hasAnyRole("ADMIN","INSTRUCTOR")` widening, not new behavior today.
- [x] 3.7 GREEN: `api/auth/.../security/SecurityConfig.java` — `.requestMatchers("/api/v1/admin/physical/devices/**").hasRole("ADMIN")` inserted immediately after line 301 (the `/api/v1/admin/physical/sessions/**` matcher), before line 312 (the generic `/api/v1/admin/**` rule) — verified against the actual current file, matching design C7 exactly (no line-number drift since P1/P2).
- [x] 3.8 RED/GREEN: `PhysicalDeviceManagementIntegrationTest` (`:api:app`, Testcontainers MySQL, full filter chain) — **rollback proof (C5)**: `@MockBean PhysicalDeviceAuditRepository` throwing from `append(...)` on register; asserts the response is a 5xx and `deviceRepository.findAll()` is empty afterward, proving `TransactionalRegisterPhysicalDeviceUseCase`'s real Spring-managed transaction (not a mocked boundary) rolls back the device insert together with the failed audit append.
- [x] 3.9 RED/GREEN: same integration class — **full lifecycle**: register → get (no secret field) → rotate (new secret, different from the first) → revoke (`REVOKED`) → second revoke `409 DEVICE_ALREADY_REVOKED` → rotate-after-revoke `409 DEVICE_REVOKED`; list returns the one device with no secret/hash key anywhere in the JSON body. Plus STUDENT/INSTRUCTOR `403` and anonymous `401` end-to-end through the real `SecurityConfig` filter chain.
- [x] 3.10 GREEN: no production gaps surfaced by 3.8/3.9 — P1/P2's test-first implementation was correct as-is. One test-only bug was found and fixed during this task: the integration test's `registerDevice` helper initially hardcoded `Role.ADMIN` in the issued token regardless of the caller passed in, which silently defeated the STUDENT/INSTRUCTOR-403 assertions (they got `201` because the token was always ADMIN) — fixed by threading the actual `Role` through the helper.
- [x] 3.11 Architecture: ran existing `PhysicalArchitectureTest`/`ArchitectureTest` unmodified (`:api:physical:test --tests "*ArchitectureTest*"`) — green, no new violations; no Spring/JPA under `physical/domain` or `physical/application`; no `com.menta.auth.*` import anywhere in `:api:physical` (C10).
- [x] 3.12 Regression: `git status --short` confirms `ProcessPhysicalCheckInUseCaseImpl.java`, `Attendance.java`, `CheckInCommand.java`, and every reference to `app.physical.checkin.device-token` are absent from this diff — byte-unchanged (D2) across the whole 3-phase change.
- [x] 3.13 GREEN: updated `api/openapi/physical-v1.yaml` — new "Gestión de dispositivos" tag, five device endpoint operations (register/get/rotate-secret/revoke/list) with full request/response schemas and RFC 9457 problem examples for 404/409, matching the controller's status-code table exactly.
- [x] 3.14 GREEN: created `bruno/API - Direct/physical/devices/{Register Device,Get Device,Rotate Device Secret,Revoke Device,List Devices}.bru` — five requests, chained via `{{physicalDeviceId}}`/`{{physicalDeviceSecret}}` bru vars set by the register/rotate `script:post-response` blocks, mirroring the existing `admin/` folder's conventions.
- [x] 3.15 Verify: `:api:physical:test --rerun-tasks` (full module, unfiltered) green — 364 tests, 0 failures (XML-confirmed). `:api:auth:test --tests "*SecurityConfigTest*" --rerun-tasks` green — 43 tests. `:api:app:test --tests "*PhysicalDevice*" --rerun-tasks` green — 5 tests (`PhysicalDeviceManagementIntegrationTest`) + 2 tests (`PhysicalDeviceManagementMigrationIntegrationTest`, from P1), 0 failures. `:api:physical:jacocoDomainApplicationCoverageVerification :api:physical:jacocoInfrastructureCoverageVerification` (unfiltered, separate command) green — 95%/90% floors held. `./gradlew check` (full monorepo, final regression gate given the shared `SecurityConfig.java` touch) — see apply-progress for the XML-confirmed result.

## Requirement → Task Coverage (cross-check against design's R1–R7 table)

| # | Requirement | Covered by |
|---|---|---|
| R1 | Register returns raw secret exactly once, full metadata | 2.4–2.5, 3.1, 3.4–3.5, 3.9 |
| R2 | Get returns metadata, no secret field; unknown id → `404` | 2.6–2.7, 3.1, 3.3–3.5, 3.9 |
| R3 | Rotation issues a new secret, old hash no longer stored | 1.5–1.6, 1.13, 2.8–2.9, 3.9 |
| R4 | Revocation terminal: re-revoke and rotate-after-revoke rejected | 1.5–1.6, 2.8, 2.10, 3.9 |
| R5 | List never exposes secret or hash | 2.12–2.14, 3.1–3.2, 3.9 |
| R6 | ADMIN-only; `STUDENT`/`INSTRUCTOR` → `403`, anonymous → `401` | 3.6–3.7 |
| R7 | Exactly one audit row per create/rotate/revoke, same transaction | 2.4, 2.8, 2.10, 2.16–2.17, 3.8 |

## Out of Scope (confirmed in proposal.md)

- Escenario 5 (`DEVICE_EXPIRED` rejection in check-in) — deferred to a follow-up issue; owns the check-in auth swap and retiring `app.physical.checkin.device-token`.
- Any modification to `ProcessPhysicalCheckInUseCaseImpl`, `Attendance`, `CheckInCommand`, `physical_attendances`, or the shared device-token property.
- Automatic `expiresAt` enforcement of any kind.
- Device self-service enrollment, heartbeat/liveness, per-device rate limiting.
- BFF or Android device-administration surfaces.

## Next Steps

After **P3** merges and its integration tests are green: run `sdd-verify`
against all seven requirements (R1–R7), then `sdd-archive`. Open the
Escenario 5 follow-up issue before this change is considered fully closed
(proposal Dependencies section).

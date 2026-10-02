# Tasks: Physical Check-in Device Enforcement (#266)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1450 total: S1 ~600, S2 ~450, S3 ~400 |
| 400-line budget risk | High (total); each slice is under the 800-line budget |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (S1, additive) → PR 2 (S2, swap + reseed) → PR 3 (S3, contract + docs) |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main (semantics; each PR targets `develop` after the previous one merges) |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

Budget flags (800 lines/slice, `additions + deletions`):
- S1 (~600) is the tightest. Largest items: 1.5 authenticator test (~200) and 1.7 adapter test (~120). If S1 exceeds ~750 at apply time, split: PR 1a = exceptions, DTOs, port, micrometer dep, adapter, ArchUnit rule (1.1-1.4, 1.7-1.9, with its own gates and PR task); PR 1b = authenticator + test (1.5-1.6, stacked on 1a, gates and PR task repeated).
- S2 task 2.9 (integration reseed, 12 `DEVICE_TOKEN` refs + `@BeforeEach` seeding) is the churn risk. `PhysicalCheckInIntegrationTest.java` is already 625 lines; do the minimal reseed only.
- S3: put new scenarios in a NEW class `PhysicalCheckInDeviceAuthIntegrationTest` (Checkstyle FileLength 500 is already exceeded by the existing class).

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 (S1) | Exceptions, authenticator, rejection port + log/metric adapter, micrometer dep (nothing wired) | PR 1, base `develop` | `./gradlew :api:physical:test --tests "*PhysicalDeviceAuthenticatorTest" --tests "*LogAndMetricDeviceAuthenticationRejectionAdapterTest" --tests "*ArchitectureTest"` | N/A: purely additive, no runtime path references it | Revert PR 1; no other code depends on it yet |
| 2 (S2) | Swap use case to registry auth, retire property + WARN, handler mappings, minimal integration reseed | PR 2, base `develop` after PR 1 merges | `./gradlew :api:physical:test :api:app:test --tests "*PhysicalCheckInIntegrationTest"` | Existing Testcontainers `PhysicalCheckInIntegrationTest` with a seeded device | Revert PR 2; first set a non-default `app.physical.checkin.device-token` on prod/staging |
| 3 (S3) | New integration scenarios, OpenAPI, Bruno, docs | PR 3, base `develop` after PR 2 merges | `./gradlew :api:app:test --tests "*PhysicalCheckInDeviceAuthIntegrationTest"` | Bruno `QR Check-in.bru` against local API with a registered device | Revert PR 3; code behavior unaffected |

## Phase 1 — S1: additive foundation (PR 1, `Refs #266`)

- [x] 1.1 RED: `api/physical/src/test/java/com/menta/physical/domain/exception/CheckInDeviceRevokedExceptionTest.java` and `DeviceExpiredExceptionTest.java` (same dir): codes `DEVICE_REVOKED` / `DEVICE_EXPIRED`, extend `BusinessException`.
- [x] 1.2 GREEN: create `api/physical/src/main/java/com/menta/physical/domain/exception/CheckInDeviceRevokedException.java` and `DeviceExpiredException.java` (A4; spec: DEVICE_REVOKED context-specific statuses).
- [x] 1.3 Create `api/physical/src/main/java/com/menta/physical/application/dto/DeviceAuthenticationRejectionReason.java` (enum `UNKNOWN_OR_INVALID`, `REVOKED`, `EXPIRED`, fluent `code()` returning `unknown_or_invalid|revoked|expired`) and `DeviceAuthenticationRejection.java` (record `(reason, UUID deviceId nullable)`); create `api/physical/src/main/java/com/menta/physical/application/port/out/DeviceAuthenticationRejectionPort.java` (A5).
- [x] 1.4 Add `implementation("io.micrometer:micrometer-core")` to `api/physical/build.gradle.kts` (mirror `api/billing/build.gradle.kts:36`). Verify dependency lock files resolve.
- [x] 1.5 RED: `api/physical/src/test/java/com/menta/physical/application/usecase/PhysicalDeviceAuthenticatorTest.java` with Mockito for every branch (spec scenarios):
  - non-UUID id, null id, null secret: `verifyNoInteractions(repo)`, report `UNKNOWN_OR_INVALID` with null deviceId, `InvalidDeviceTokenException`;
  - unknown UUID: hasher still called, `UNKNOWN_OR_INVALID(id)`;
  - wrong secret: `UNKNOWN_OR_INVALID`;
  - revoked + correct secret: `REVOKED`; revoked + wrong secret: `UNKNOWN_OR_INVALID`;
  - `expiresAt == now`: `EXPIRED` (inclusive); revoked + expired: `REVOKED` wins;
  - null or future expiry: returns `DeviceId`, port never called;
  - `verify(port, times(1))` per rejection; uppercase UUID normalized; `1-1-1-1-1` rejected (A2).
- [x] 1.6 GREEN: create `api/physical/src/main/java/com/menta/physical/application/usecase/PhysicalDeviceAuthenticator.java` per A1-A3, A6 and the `authenticate` order (regex pre-check, `MessageDigest.isEqual`, `DUMMY_SECRET_HASH`, report-once-then-throw). Refactor: extract the regex and dummy hash as constants.
- [x] 1.7 RED: `api/physical/src/test/java/com/menta/physical/infrastructure/observability/LogAndMetricDeviceAuthenticationRejectionAdapterTest.java` (copy of `api/billing/src/test/java/com/menta/billing/infrastructure/observability/LogAndMetricSubscriptionFulfillmentAlarmAdapterTest.java`): `SimpleMeterRegistry` + logback `ListAppender`; literal (non-constant) assertions on `alarm=physical_checkin_device_rejected`, `reason=revoked deviceId=<uuid>`, `deviceId=none`, `WARN`, counter `physical.checkin.device.rejected` with only tag `reason`, 3 counters pre-registered at 0, one line and one increment per `report`.
- [x] 1.8 GREEN: create `api/physical/src/main/java/com/menta/physical/infrastructure/observability/LogAndMetricDeviceAuthenticationRejectionAdapter.java` (`@Slf4j @Component`, A5, A7; never throws).
- [x] 1.9 RED+GREEN: `api/physical/src/test/java/com/menta/physical/ArchitectureTest.java` add `application_should_not_depend_on_micrometer` (mirror billing's rule); confirm the application port compiles without Micrometer.
- [x] 1.10 Gates: `./gradlew :api:physical:test`, `:api:physical:jacocoTestCoverageVerification` (domain+application 95%, infrastructure 90%), `:api:physical:checkstyleMain :api:physical:checkstyleTest` (no new warnings on added lines; line length 120, file length 500).
- [x] 1.11 Confirm S1 diff is under 800 changed lines (`git diff --shortstat origin/develop...HEAD`). If over, apply the split noted above.
- [x] 1.12 PR 1 to `develop`: conventional title, Spanish body per `skills/prcreator/SKILL.md`, `Refs #266`, no `Closes`.

## Phase 2 — S2: use-case swap and config retirement (PR 2, `Refs #266`)

- [x] 2.1 RED: update `api/physical/src/test/java/com/menta/physical/application/usecase/ProcessPhysicalCheckInUseCaseImplTest.java`: replace `DEVICE_TOKEN` with a mocked `PhysicalDeviceAuthenticator` returning a fixed `DeviceId`; authenticator throws means `verifyNoInteractions` on every other collaborator and Redis; saved attendance stores the UUID string (A10); MANUAL never calls the authenticator; remove `rejects_a_null_device_token` (moved to 1.5).
- [x] 2.2 GREEN: modify `api/physical/src/main/java/com/menta/physical/application/usecase/ProcessPhysicalCheckInUseCaseImpl.java`: swap the `String deviceToken` ctor arg for `PhysicalDeviceAuthenticator` (arity stays 11), gate 1 = `authenticate(...)`, persist `authenticated.toString()`.
- [x] 2.3 RED: modify `api/physical/src/test/java/com/menta/physical/infrastructure/config/PhysicalConfigurationTest.java`: delete the 3 fail-fast tests and 2 reflection helpers; use `new PhysicalConfiguration()`; add an authenticator-bean test and a use-case-bean test.
- [x] 2.4 GREEN: modify `api/physical/src/main/java/com/menta/physical/infrastructure/config/PhysicalConfiguration.java`: drop `@Value`, `Environment`, `@PostConstruct`, `PRODUCTION_PROFILES`, `DEV_DEFAULT_DEVICE_TOKEN`; add `@Bean physicalDeviceAuthenticator(repo, hasher, clock, rejectionPort)` (A9).
- [x] 2.5 RED: create `api/physical/src/test/java/com/menta/physical/infrastructure/config/LegacyCheckInDeviceTokenPropertyWarningTest.java` (`MockEnvironment`, `ListAppender`): property set (also with `prod` active) gives exactly one WARN naming it, value never logged; absent gives no WARN.
- [x] 2.6 GREEN: create `api/physical/src/main/java/com/menta/physical/infrastructure/config/LegacyCheckInDeviceTokenPropertyWarning.java` (A8, `@EventListener(ApplicationReadyEvent.class)`).
- [x] 2.7 RED: update `api/physical/src/test/java/com/menta/physical/infrastructure/web/controller/PhysicalCheckInExceptionHandlerTest.java`: `CheckInDeviceRevokedException` and `DeviceExpiredException` map to 401 with codes `DEVICE_REVOKED` / `DEVICE_EXPIRED`.
- [x] 2.8 GREEN: modify `api/physical/src/main/java/com/menta/physical/infrastructure/web/controller/PhysicalCheckInExceptionHandler.java`; update the `Attendance` javadoc in `api/physical/src/main/java/com/menta/physical/domain/model/Attendance.java` (device_id = authenticated UUID).
- [x] 2.9 Integration reseed (minimal): modify `api/app/src/test/java/com/menta/app/integration/physical/PhysicalCheckInIntegrationTest.java`: remove `@DynamicPropertySource`; seed an ACTIVE device in `@BeforeEach` via `PhysicalDeviceJpaRepository.save(...)` with `new Sha256DeviceSecretHasher().hash(READER_SECRET)`; rename `DEVICE_TOKEN` to `READER_SECRET`; `checkIn(...)` sends the seeded UUID; `deviceRepository.deleteAll()` in `@AfterEach`. All existing QR-path tests green.
- [x] 2.10 Gates: `./gradlew :api:physical:test :api:app:test`, `:api:physical:jacocoTestCoverageVerification`, `:api:app:jacocoTestCoverageVerification`, checkstyle main and test, `*ArchitectureTest`; S2 under 800 lines.
- [x] 2.11 PR 2 to `develop` (after PR 1 merged, rebased): Spanish body, label `Breaking Changes`, runbook note (register readers before deploy, remove the legacy property), `Refs #266`.

## Phase 3 — S3: contract, scenarios, docs (PR 3, `Closes #266`)

- [x] 3.1 RED: create `api/app/src/test/java/com/menta/app/integration/physical/PhysicalCheckInDeviceAuthIntegrationTest.java`: revoked and expired give 401 with their codes; unknown UUID, `reader-1`, wrong secret and the legacy shared secret give identical 401 `INVALID_DEVICE_TOKEN` bodies; revoked/expired + wrong secret gives INVALID; revoked wins over expired; every case `verifyNoInteractions(redisTemplate)`; stored `device_id` equals the UUID; autowired `MeterRegistry` counter `physical.checkin.device.rejected{reason}` increments; MANUAL unaffected. These should pass on the S2 code (characterization); any failure is a real defect to fix in S2 code.
- [x] 3.2 Update `api/openapi/physical-v1.yaml`: check-in 401 `INVALID_DEVICE_TOKEN`, `DEVICE_REVOKED`, `DEVICE_EXPIRED`; `deviceId` is the registered UUID and `deviceToken` its raw secret; document `DEVICE_REVOKED` 401 (check-in) vs 409 (admin rotate).
- [x] 3.3 Update `bruno/API - Direct/physical/check-ins/QR Check-in.bru` (`deviceId` UUID + `deviceToken` secret variables, 401 notes).
- [x] 3.4 Update the check-in parts only of `docs/05-PHYSICAL-API.md`, `docs/user-stories/US-PHYSICAL-007.md`, `docs/diagrams/SEQUENCE-DIAGRAMS.md` section 6; add the runbook (register the reader, remove the legacy property).
- [x] 3.5 Gates: `./gradlew :api:app:test`, `./gradlew check` (full gate: JaCoCo thresholds, Checkstyle on added lines, ArchUnit, OpenAPI lint if wired); S3 under 800 lines.
- [x] 3.6 PR 3 to `develop`: Spanish body, `Closes #266` (only on this final PR).

## Phase 4 — Tracking and follow-up

- [x] 4.1 Board (project 1): issue #266 status In Progress at S1 start, In Review at each PR, Done on S3 merge; fields set per `feedback_track_tasks_on_board`; PR 1 and PR 2 use `Refs #266`, PR 3 uses `Closes #266`.
- [x] 4.2 Create a separate board issue for check-in rate limiting (unauthenticated PK lookup, no `deviceToken` length cap, D9); not part of these code slices. Created as #309 (Backlog, Prioridad Media).

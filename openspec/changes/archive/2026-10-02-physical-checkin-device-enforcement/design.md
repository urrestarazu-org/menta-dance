# Design: Physical Check-in Device Enforcement (#266)

## Technical Approach

Gate 1 of `ProcessPhysicalCheckInUseCaseImpl.checkIn` delegates to a new application collaborator, `PhysicalDeviceAuthenticator`. It authenticates `deviceId` and `deviceToken` against the #44 registry and returns the authenticated `DeviceId`. Each rejection is reported once through a new out-port (a log and counter adapter shaped like #236) and then thrown as a domain exception that maps to 401. This implements the `physical-checkin` spec delta and locked decisions D1-D9. The gate stays Redis-free.

## Architecture Decisions

| # | Topic | Choice | Rejected | Rationale |
|---|---|---|---|---|
| A1 | Authenticator shape | `public class PhysicalDeviceAuthenticator` in `application/usecase` (`@RequiredArgsConstructor`), ctor `(PhysicalDeviceRepository, DeviceSecretHasher, Clock, DeviceAuthenticationRejectionPort)`, method `DeviceId authenticate(String rawDeviceId, String rawSecret)`. Replaces the use case's `String deviceToken` ctor arg; arity stays 11. | In-port interface; inline in use case (+3 ctor args) | Mirrors the `CourseOwnershipGuard` collaborator pattern. One cohesive, fully unit-testable unit. The use case stays focused on the flow. |
| A2 | UUID pre-check | Canonical regex `^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$`. `DeviceId.of` is called only after a match. | `DeviceId.of` + catch `IllegalArgumentException` | `UUID.fromString` is lenient (`1-1-1-1-1`). An unguarded `IllegalArgumentException` would become 400 via the check-in advice. The regex also caps length before any DB access. |
| A3 | Timing | Always `hasher.hash(rawSecret)`, then `MessageDigest.isEqual` on UTF-8 bytes against the stored hash or `DUMMY_SECRET_HASH` (64 hex chars) when the device is unknown. | Early return on unknown | Unknown id and wrong secret share the same DB+hash+compare path (no existence oracle). The malformed-id fast path reveals only the public format rule. |
| A4 | Exceptions | New `CheckInDeviceRevokedException` (`DEVICE_REVOKED`) and `DeviceExpiredException` (`DEVICE_EXPIRED`) in `domain/exception`, both `extends BusinessException`. `InvalidDeviceTokenException` is reused. | Reuse `DeviceRevokedException` | That class means "admin 409, rotate after revoke". The advices are annotation-scoped (`@PhysicalCheckInEndpoint` vs `@PhysicalDeviceEndpoint`), so one code with two statuses causes no technical clash. |
| A5 | Observability | Out-port `DeviceAuthenticationRejectionPort.report(DeviceAuthenticationRejection)`, a record `(reason, UUID deviceId /*nullable*/)` and enum `DeviceAuthenticationRejectionReason` (fluent `code()`). Adapter: `infrastructure/observability/LogAndMetricDeviceAuthenticationRejectionAdapter` (`@Slf4j @Component`, **WARN**). | SLF4J in application; `physical_device_audit` rows | Follows the #236 precedent. Audit rows need `actor_id` and a schema change (D9). |
| A6 | Emission point | The authenticator reports exactly once, then throws. The port contract says it must not throw. | Report from the exception handler | Keeps "one log + one increment per rejection" in the application layer, assertable in a unit test. |
| A7 | Locked contract | `LOG_MARKER = "alarm=physical_checkin_device_rejected"`, line `"{marker} reason={} deviceId={}"` (`none` when null), `COUNTER_NAME = "physical.checkin.device.rejected"`, `METRIC_REASON_TAG = "reason"`, values `unknown_or_invalid`, `revoked`, `expired`. All 3 counters are pre-registered at 0. | Per-device tag | These are the spec-delta literals. Device ids are high-cardinality and never tags. Only canonical UUIDs reach the log (no log injection). |
| A8 | Legacy property | New `@Component LegacyCheckInDeviceTokenPropertyWarning` (`infrastructure/config`), ctor `(Environment)`, `@EventListener(ApplicationReadyEvent.class) public void warnIfPresent()`. It checks `containsProperty("app.physical.checkin.device-token")` and logs one WARN with the property name, never its value. | `@PostConstruct` in `PhysicalConfiguration`; `ApplicationRunner` | `PhysicalConfiguration` sheds `Environment`, `@Value`, `@PostConstruct`, `PRODUCTION_PROFILES` and `DEV_DEFAULT_DEVICE_TOKEN` (implicit no-arg ctor). The method is directly unit-testable with `MockEnvironment`. |
| A9 | Wiring | New `@Bean PhysicalDeviceAuthenticator physicalDeviceAuthenticator(repo, hasher, clock, rejectionPort)`. `processPhysicalCheckInUseCase` takes it instead of the token. | Build inside the use-case bean | Keeps bean-method arity small (7→8, versus 7→10), and the bean is tested independently. |
| A10 | `Attendance.device_id` | `authenticated.toString()` (canonical lowercase UUID). `CheckInCommand` is unchanged. The `Attendance` javadoc is updated. | Persist the submitted text | Spec delta. Historical rows untouched; MANUAL path untouched. |

### `authenticate` order

1. `rawDeviceId` null or no regex match, or `rawSecret` null → report `UNKNOWN_OR_INVALID` (deviceId only if it is a valid UUID) → `InvalidDeviceTokenException`. **No DB access.**
2. `DeviceId.of` → `repository.findById`.
3. Hash, then constant-time compare (A3). On mismatch or unknown → report `UNKNOWN_OR_INVALID(id)` → `InvalidDeviceTokenException`.
4. `REVOKED` → report `REVOKED` → `CheckInDeviceRevokedException` (REVOKED takes precedence over EXPIRED).
5. `expiresAt != null && !expiresAt.isAfter(clock.now())` → report `EXPIRED` → `DeviceExpiredException`.
6. Return the `DeviceId`.

## Data Flow

    Reader ─POST check-ins─→ Controller ─→ UseCase.checkIn
       step1: Authenticator ─→ PhysicalDeviceRepository (PK)
                  │ reject ─→ RejectionPort ─→ LogAndMetric adapter (WARN + counter)
                  └─ DeviceId ─→ steps 2-8 (unchanged) ─→ locks ─→ Attendance(deviceId=UUID)
    Exceptions ─→ PhysicalCheckInExceptionHandler ─→ 401 INVALID_DEVICE_TOKEN | DEVICE_REVOKED | DEVICE_EXPIRED

## File Changes and Slices (stacked to `develop`, 800-line budget)

| Slice | Files (`api/physical/...` unless noted) | Forecast |
|---|---|---|
| **S1** additive | Create: `domain/exception/{CheckInDeviceRevokedException,DeviceExpiredException}` + tests; `application/dto/{DeviceAuthenticationRejection,DeviceAuthenticationRejectionReason}`; `application/port/out/DeviceAuthenticationRejectionPort`; `application/usecase/PhysicalDeviceAuthenticator` + test; `infrastructure/observability/LogAndMetricDeviceAuthenticationRejectionAdapter` + test. Modify: `build.gradle.kts` (+`io.micrometer:micrometer-core`), `ArchitectureTest` (+`application_should_not_depend_on_micrometer`). | ~600 |
| **S2** swap | Modify: `ProcessPhysicalCheckInUseCaseImpl` + test, `PhysicalConfiguration` + test, `PhysicalCheckInExceptionHandler` + test, `domain/model/Attendance` (javadoc), `api/app/.../PhysicalCheckInIntegrationTest` (minimal reseed). Create: `LegacyCheckInDeviceTokenPropertyWarning` + test. | ~450 |
| **S3** contract | `PhysicalCheckInIntegrationTest` (new scenarios), `api/openapi/physical-v1.yaml`, `bruno/API - Direct/physical/check-ins/QR Check-in.bru`, `docs/05-PHYSICAL-API.md`, `docs/user-stories/US-PHYSICAL-007.md`, `docs/diagrams/SEQUENCE-DIAGRAMS.md` §6 | ~400 |

**S1 stays green**: it is purely additive, and nothing references the authenticator yet. The adapter `@Component` needs a `MeterRegistry`, which `:api:app` already provides through actuator (the same as billing #236). **S2 must carry the minimal integration-test reseed**: once the use case is swapped, the shared token no longer authenticates, and about 10 QR-path integration tests would fail. This moves part of the proposal's S3 work forward.

## Testing Strategy (Strict TDD: RED first per slice)

| Slice | Test | Approach |
|---|---|---|
| S1 | `PhysicalDeviceAuthenticatorTest` | Mocks for each branch: non-UUID, null id and null secret (`verifyNoInteractions(repo)`); unknown UUID (hasher still called); wrong secret; revoked with correct secret; revoked with wrong secret → INVALID; `expiresAt == now` → EXPIRED; revoked+expired → REVOKED; null/future expiry → success with no report; `verify(port, times(1))`; uppercase UUID normalized. |
| S1 | Adapter test | Copy of #236: `SimpleMeterRegistry` + logback `ListAppender`; literal (not constant) assertions; WARN level; `deviceId=none`; only the `reason` tag. |
| S2 | `ProcessPhysicalCheckInUseCaseImplTest` | `DEVICE_TOKEN` → mocked authenticator returning a fixed `DeviceId`. When the authenticator throws, every other collaborator gets `verifyNoInteractions`. The saved attendance stores the UUID. MANUAL never calls the authenticator. `rejects_a_null_device_token` moves to the authenticator test. |
| S2 | `PhysicalConfigurationTest` | `new PhysicalConfiguration()`. Delete the 3 fail-fast tests and 2 reflection helpers. Add an authenticator-bean test. |
| S2 | Warning and handler tests | `MockEnvironment` with/without the property: exactly one WARN naming it, value absent, none when unset. Handler: both new exceptions map to 401 with their codes. |
| S2 | Integration reseed | Remove `@DynamicPropertySource`. In `@BeforeEach`, seed an ACTIVE device via `PhysicalDeviceJpaRepository.save(new PhysicalDeviceJpaEntity(id, ..., new Sha256DeviceSecretHasher().hash(READER_SECRET), "ACTIVE", null, now, now))`. `checkIn(...)` sends `readerId`. `DEVICE_TOKEN` (15 refs) is renamed to `READER_SECRET`. `@AfterEach` calls `deviceRepository.deleteAll()`. |
| S3 | Integration scenarios | Revoked/expired/unknown/`reader-1`/legacy secret → 401 with identical INVALID bodies and `verifyNoInteractions(redisTemplate)`. Stored `device_id` equals the UUID. The autowired `MeterRegistry` counter increments. |

**JaCoCo**: the new application code is pure and fully branch-tested (95% domain+application). The adapter and warning component are fully tested (90% infrastructure). The deleted fail-fast was tested, so removing it is coverage-neutral. **ArchUnit**: the existing layered rules cover the new classes; the new micrometer rule enforces A5.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary. (HTTP auth threats are covered by A2/A3/A7 and the spec delta.)

## Migration / Rollout

No schema or data migration. Breaking change for readers: register each reader before deploying (proposal runbook). Rollback: revert S3→S1. Before reverting S2, prod/staging must set a non-default `app.physical.checkin.device-token`.

## Open Questions

- [ ] None blocking. `deviceToken` has no length cap (hash cost on unauthenticated input); deferred together with the rate-limit issue (D9).

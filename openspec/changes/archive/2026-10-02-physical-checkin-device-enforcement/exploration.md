# Exploration: physical-checkin-device-enforcement (issue #266)

> Source: Engram `sdd/physical-checkin-device-enforcement/explore` (2026-10-01).
> Product decisions taken afterwards are locked in Engram `sdd/physical-checkin-device-enforcement/decisions` (D1-D9) and reflected in `proposal.md`.

## Current State

- Check-in auth today: `ProcessPhysicalCheckInUseCaseImpl.checkIn` step 1 calls `verifyDeviceToken`: null-check + `MessageDigest.isEqual` (constant-time) of the request `deviceToken` against ONE shared String injected via constructor (11-arg ctor, `String deviceToken` param). Throws `InvalidDeviceTokenException` (`INVALID_DEVICE_TOKEN`, 401). `checkInManually` (MANUAL, #45) never touches device auth.
- `PhysicalConfiguration`: `@Value("${app.physical.checkin.device-token:<DEV_DEFAULT_DEVICE_TOKEN>}") checkInDeviceToken`; `@PostConstruct validateDeviceTokenNotDefaultInProduction()` fails fast on profiles `prod|production|staging`; passes the field to the `processPhysicalCheckInUseCase` bean. The `Environment` ctor arg and `PRODUCTION_PROFILES` exist ONLY for that fail-fast.
- The property is referenced nowhere in `application*.yml`, docker-compose, CI, scripts or env docs (only the `@Value` default, `PhysicalCheckInIntegrationTest` `@DynamicPropertySource`, and `PhysicalConfigurationTest`). No Android/BFF reader client in the repo (readers are external).
- The HTTP contract already carries per-reader identity: `CheckInRequest(type, qrCredentials, deviceId, deviceToken, studentId)`; the QR variant requires `qrCredentials`, `deviceId`, `deviceToken` non-blank; MANUAL forbids them. `CheckInCommand.qr(sessionId, qr, deviceId, deviceToken)`. `deviceId` is free text stored verbatim in `Attendance.device_id` (VARCHAR(255) NOT NULL, V15; javadoc says "not validated against a registry"; MANUAL stores the actor userId there). Test fixtures use `reader-1`, `reader-01`, `puerta-principal`.
- Device registry (#44, archived 2026-09-26, spec `openspec/specs/physical-device-management/spec.md`): aggregate `PhysicalDevice(id DeviceId(UUID), name, location, secretHash (lowercase SHA-256 hex, 64), status ACTIVE|REVOKED, expiresAt nullable, createdAt, updatedAt)`, immutable-with-copy, revoke is terminal. Table `physical_devices` (V23) with UNIQUE `uq_physical_devices_secret_hash`, index on status; `expires_at` has no index. Raw secret: 32B SecureRandom Base64url (43 chars), shown once.
- Out-port `PhysicalDeviceRepository` has ONLY `save`, `findById(DeviceId)`, `findAll` (no `findBySecretHash`). `DeviceSecretHasher` port + `Sha256DeviceSecretHasher` and `DeviceSecretGenerator` already exist.
- Admin endpoints under `/api/v1/admin/physical/devices` (register, get, rotate-secret, revoke, list), ADMIN-only, audit rows in `physical_device_audit`. `rotateSecret` keeps `expiresAt` unchanged; NO endpoint changes `expiresAt` after registration, so an expired device is a dead end (revoke or re-register). Admin list/get return the persisted status (an expired device still shows ACTIVE). `docs/05-PHYSICAL-API.md` describes derived EXPIRED/rotatedAt/revokedAt/pagination/non-admin paths that were never implemented (docs drift).
- #44 explicitly deferred this work: its proposal Out of Scope, design D2/D6 and archive report assign to #266 the "check-in auth swap, DEVICE_EXPIRED/DEVICE_REVOKED rejection, retiring app.physical.checkin.device-token"; design D6 constraint: compare digests with `MessageDigest.isEqual`. `docs/diagrams/SEQUENCE-DIAGRAMS.md` section 6 (target flow): find device by deviceId -> 401 DEVICE_NOT_FOUND -> verify token hash -> 401 INVALID_DEVICE_TOKEN -> status/expiry -> 401 DEVICE_EXPIRED/DEVICE_REVOKED. US-PHYSICAL-007 Escenario 5 and the US-PHYSICAL-001 sample also send deviceId + deviceToken in the body.
- Existing spec `openspec/specs/physical-checkin/spec.md`, ordered-rejection table row 1: "Device token (constant-time compare) -> 401 INVALID_DEVICE_TOKEN", plus scenario "a flood of malformed/unauthorized scans never reaches Redis". The manual-checkin spec Out of Scope also lists DEVICE_EXPIRED/DEVICE_REVOKED enforcement.
- Collision: `DEVICE_REVOKED` already exists as 409 (`DeviceRevokedException`, rotate-after-revoke) in the admin advice (`PhysicalDeviceExceptionHandler`, scoped by `@PhysicalDeviceEndpoint`). Advices are annotation-scoped, so no technical clash, but one code with two statuses must be documented; a new exception is recommended for check-in.
- Pitfall: `DeviceId.of(String)` throws `IllegalArgumentException` for non-UUID input (e.g. `reader-1`); `PhysicalCheckInExceptionHandler` maps `IllegalArgumentException` -> 400 `INVALID_REQUEST`. A naive `DeviceId.of(command.deviceId())` returns 400 instead of 401.
- No rate limiting for check-ins (public `permitAll` in SecurityConfig); physical has no rate-limit port. A new unauthenticated DB lookup per request is a small DoS amplification; a UUID format pre-check avoids the DB for garbage ids. No logging in the use case; `physical_device_audit` is admin-action only (actor_id NOT NULL, FK device), unsuitable for check-in rejections without a schema change.
- Tests touching the shared token: `ProcessPhysicalCheckInUseCaseImplTest`, `PhysicalConfigurationTest` (3 fail-fast tests + reflection helper), `PhysicalCheckInIntegrationTest` (15 refs; no device seeding helper), `PhysicalCheckInExceptionHandlerTest`, `InvalidDeviceTokenExceptionTest`, `CheckInRequestTest`. Physical coverage gates: 95% domain+application, 90% infrastructure.
- Docs/contract touchpoints: `api/openapi/physical-v1.yaml` (intro, check-in endpoint description, 401 response, `CheckInQrRequest` field descriptions), `bruno/API - Direct/physical/check-ins/QR Check-in.bru`, `docs/05-PHYSICAL-API.md`, `docs/user-stories/US-PHYSICAL-007.md`, `docs/diagrams/SEQUENCE-DIAGRAMS.md` section 6, `openspec/specs/physical-checkin/spec.md`, `Attendance` javadoc.

## Affected Areas

- `application/usecase/ProcessPhysicalCheckInUseCaseImpl.java` (+test): replace shared-token ctor param and `verifyDeviceToken`.
- NEW application component (device authenticator) using `PhysicalDeviceRepository` + `DeviceSecretHasher` + `Clock`.
- NEW domain exceptions (DEVICE_EXPIRED, DEVICE_REVOKED for check-in) + `PhysicalCheckInExceptionHandler` mappings (+tests).
- `infrastructure/config/PhysicalConfiguration.java` (+test): drop `@Value`/property/dev default/`@PostConstruct`/`Environment`/`PRODUCTION_PROFILES`; wire the authenticator.
- `web/dto/CheckInRequest` (optional size caps), `PhysicalCheckInController` (shape unchanged).
- OpenAPI, Bruno, docs (05, US-007, SEQUENCE-DIAGRAMS), physical-checkin spec delta, `Attendance` javadoc.
- `PhysicalCheckInIntegrationTest` (+ device seeding; expired/revoked/unknown cases).
- Untouched: Attendance behaviour, `CheckInCommand`, schema (V23 suffices).

## Approaches

1. **Hard cutover** (recommended): registry-only auth by `deviceId` (UUID, body) + secret in `deviceToken`. Order: parse UUID (no DB) -> `findById` -> constant-time compare of SHA-256(secret) vs stored hash (dummy compare when unknown) -> REVOKED -> expired. Unknown/malformed id and bad secret share 401 `INVALID_DEVICE_TOKEN`. Store the authenticated UUID in `Attendance.device_id`. Remove property + fail-fast. Pros: simplest end state, immediate revocation, matches issue and SEQUENCE-DIAGRAMS. Cons: breaking for deployed readers; rollback only by revert. Effort: Medium.
2. **Dual-accept window**: registry first, legacy shared token accepted only when explicitly configured. Pros: zero-downtime per-reader migration. Cons: a leaked shared secret still defeats revocation during the window; more branches; legacy may linger. Effort: Medium-High.
3. **Secret-only lookup by hash** (`findBySecretHash`, ignore deviceId). Cons: new port method, deviceId stays an unauthenticated claim, diverges from documented contract. Not recommended.

## Recommendation

Approach 1 via a small dedicated authenticator collaborator (replaces the use case's `String deviceToken` ctor arg), delivered as stacked slices: S1 exceptions + authenticator + unit tests; S2 use-case swap + config removal + handler mappings; S3 integration tests + OpenAPI/Bruno/docs/spec delta.

## Risks

- Breaking change for deployed readers (runbook needed; rollback only by revert).
- Non-UUID deviceId must yield 401, not 400 (`IllegalArgumentException` mapping trap).
- `DEVICE_REVOKED` used with 409 (admin) and 401 (check-in); expired devices cannot be revived.
- Docs drift in `docs/05` and SEQUENCE-DIAGRAMS.
- Unauthenticated DB lookup per check-in with no rate limit; no payload size caps on `deviceToken`/`deviceId`.
- Admin list/get keep showing ACTIVE for expired devices.
- Constructor-signature ripples across 3-4 test classes under strict TDD and coverage gates.

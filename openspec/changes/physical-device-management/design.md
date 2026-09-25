# Design: Physical QR device management (#44, US-PHYSICAL-007)

## Technical Approach

One new aggregate, five use cases, five endpoints, two tables. The whole change is additive:
nothing in the shipped check-in path (`ProcessPhysicalCheckInUseCaseImpl`, `Attendance`,
`app.physical.checkin.device-token`) is read or written by any class introduced here (D2).

Physical's established chain, unchanged: domain model → out-port → `*UseCaseImpl` →
`PhysicalConfiguration` bean → controller → JPA adapter. The one addition to that chain is a
transactional decorator layer (C5), which Physical does not have today but Virtual and Auth do,
and which the "one audit row in the same transaction as the state change" requirement forces.

The secret is a 32-byte `SecureRandom` value, Base64 URL-encoded without padding; only its
lowercase SHA-256 hex digest is persisted (D1). The generator and hasher are *replicated* from
`:api:auth`'s shape using JDK `SecureRandom`/`MessageDigest` only — never imported (D3), so the
existing module-boundary ArchUnit rule against `com.menta.auth.*` holds unchanged (C10).

## Architecture Decisions

### C1 — `PhysicalDevice`: terminal revocation is an aggregate invariant, not a service check

`DeviceStatus` is a closed two-constant enum (`ACTIVE`, `REVOKED`), mirroring `SessionStatus`/
`CourseStatus`. `DeviceId` is a `final class` with private constructor and `of(UUID)`/`of(String)`/
`generate()` factories, copied verbatim from `SessionId` — **not** a record, because every other
Physical id follows that shape and a lone record would be the outlier.

The aggregate is immutable-with-copy, like `PhysicalSession`: each transition returns a new
instance and throws before constructing an illegal one.

```java
// domain/model/PhysicalDevice.java
public final class PhysicalDevice {
    private final DeviceId id;
    private final String name;          // required, non-blank
    private final String location;      // required, non-blank
    private final String secretHash;    // lowercase SHA-256 hex, 64 chars
    private final DeviceStatus status;
    private final Instant expiresAt;    // nullable — metadata only in this change
    private final Instant createdAt;
    private final Instant updatedAt;

    public static PhysicalDevice register(
        String name, String location, String secretHash, Instant expiresAt, Instant now);

    /** @throws DeviceRevokedException if status == REVOKED (D5). */
    public PhysicalDevice rotateSecret(String newSecretHash, Instant now);

    /** @throws DeviceAlreadyRevokedException if status == REVOKED (D5). */
    public PhysicalDevice revoke(Instant now);
}
```

| Option | Tradeoff | Decision |
|---|---|---|
| Use cases check `status == REVOKED` before calling a dumb setter | Two use cases must both remember the rule; a third caller (the Escenario 5 follow-up) can forget it silently | Rejected |
| `boolean revoked` flag | Representable-but-meaningless states multiply the moment a third status is needed; no exhaustive `switch` | Rejected |
| **Enum + throwing transition methods on the aggregate** | The illegal transition cannot compile past the aggregate; `PhysicalSession.withCapacity`'s `CapacityBelowAssignedException` is the exact existing precedent | **Chosen** |

**Two distinct exceptions, deliberately** — `revoke()` after revoke and `rotateSecret()` after
revoke are two different spec scenarios and must be independently assertable:

| Exception | Error code | HTTP |
|---|---|---|
| `DeviceNotFoundException` | `DEVICE_NOT_FOUND` | 404 |
| `DeviceAlreadyRevokedException` | `DEVICE_ALREADY_REVOKED` | 409 |
| `DeviceRevokedException` (rotate after revoke) | `DEVICE_REVOKED` | 409 |

All three extend `com.menta.shared.domain.exceptions.BusinessException` with a `private static
final String ERROR_CODE`, exactly as `SessionNotFoundException` does.

### C2 — The raw secret cannot leak, because no persisted or listed type can hold it

Generation and hashing are ports (C3) with JDK-only adapters in
`infrastructure/device/`:

```java
// infrastructure/device/SecureRandomDeviceSecretGenerator.java — replicates
// SecureRandomActivationTokenGenerator; no com.menta.auth import (D3).
private static final int SECRET_BYTES = 32;
byte[] bytes = new byte[SECRET_BYTES];
secureRandom.nextBytes(bytes);
return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

// infrastructure/device/Sha256DeviceSecretHasher.java — replicates Sha256Hex.hash's body
// (MessageDigest.getInstance("SHA-256") + HexFormat.of().formatHex(...)), lowercase hex.
```

`Sha256Hex` lives in `auth`'s **domain** layer, so it is import-reachable from Physical only by
adding the module dependency D3 forbids. The replica lives in `infrastructure/`, not
`physical/domain/crypto/`, because Physical's domain has no crypto today and the aggregate needs
only the already-computed hash string.

The one-time reveal (D4) is **structural, not a policy**. Two separate application DTOs:

```java
// application/dto — has no secret and no hash field to populate or forget to strip.
public record PhysicalDeviceView(
    UUID id, String name, String location, DeviceStatus status,
    Instant expiresAt, Instant createdAt, Instant updatedAt) { }

// Returned by exactly two in-ports: register and rotate. Nothing persists it.
public record PhysicalDeviceSecretResult(PhysicalDeviceView device, String rawSecret) { }
```

`GetPhysicalDeviceUseCase` and `ListPhysicalDevicesUseCase` return `PhysicalDeviceView`, so
"the get/list response contains no secret" is a **type-level** fact a future edit cannot regress
by omission — it would need to change the return type. `secretHash` never crosses the application
boundary at all: it exists on the aggregate and in the JPA entity, and in neither DTO.

Raw-secret lifetime, exactly: `DeviceSecretGenerator.generate()` → local variable in the use case
→ `hasher.hash(raw)` (hash goes to the aggregate) → `PhysicalDeviceSecretResult` → HTTP response
body → garbage. It is never logged (no logger statement in these classes takes it), never passed
to the audit port (C4), and never stored.

**D6 (constant-time comparison) has no call site in this change** — nothing verifies a secret
here. It is recorded as a constraint on the Escenario 5 follow-up, which will compare with
`MessageDigest.isEqual`, matching `ProcessPhysicalCheckInUseCaseImpl.constantTimeEquals`. Adding
an unused comparator now would be dead, untestable code against the coverage gate.

### C3 — Four out-ports, framework-free

```java
// application/port/out/PhysicalDeviceRepository.java
public interface PhysicalDeviceRepository {
    PhysicalDevice save(PhysicalDevice device);          // insert or update, by id
    Optional<PhysicalDevice> findById(DeviceId deviceId);
    List<PhysicalDevice> findAll();                      // ordered by created_at ASC, id ASC
}

// application/port/out/PhysicalDeviceAuditRepository.java — mirrors VirtualCourseAuditRepository
public interface PhysicalDeviceAuditRepository {
    void append(DeviceId deviceId, UUID actorId, String action, String previousValue, String newValue);
}

// application/port/out/DeviceSecretGenerator.java
public interface DeviceSecretGenerator { String generate(); }

// application/port/out/DeviceSecretHasher.java
public interface DeviceSecretHasher { String hash(String rawSecret); }
```

`findAll()` is unpaginated on purpose: a fleet is a handful of door readers, and Physical's
`ListManagedPhysicalCoursesUseCase` sets the same precedent for admin listings. Pagination is a
later additive change if the fleet ever grows.

### C4 — One use case per operation; the audit payload carries a hash *prefix*, never a hash

Five in-ports, five `*Impl`, following `GetPhysicalAttendanceHistoryUseCaseImpl`'s shape
(constructor injection, no Spring annotation, package `application/usecase`):

| In-port | Signature |
|---|---|
| `RegisterPhysicalDeviceUseCase` | `PhysicalDeviceSecretResult register(RegisterPhysicalDeviceCommand cmd, UUID actorId)` |
| `GetPhysicalDeviceUseCase` | `PhysicalDeviceView get(String deviceId)` |
| `RotatePhysicalDeviceSecretUseCase` | `PhysicalDeviceSecretResult rotate(String deviceId, UUID actorId)` |
| `RevokePhysicalDeviceUseCase` | `PhysicalDeviceView revoke(String deviceId, UUID actorId)` |
| `ListPhysicalDevicesUseCase` | `List<PhysicalDeviceView> list()` |

`get` and `list` take no `actorId`: authorization is entirely the `SecurityConfig` matcher's job
(C7) and there is no per-resource ownership concept for a device. The three mutating use cases
take it because the audit row requires an actor.

`Clock` (Physical's existing out-port) supplies `now` for `createdAt`/`updatedAt`/`occurredAt` —
no `Instant.now()` inside `application`, so the timestamps are testable.

**Audit encoding.** `previousValue`/`newValue` are the human-readable snapshot strings
`VirtualCourseAuditRepository`'s javadoc describes, restricted to non-sensitive fields:

| Action | `previousValue` | `newValue` |
|---|---|---|
| `DEVICE_REGISTERED` | `null` (there is no "before") | `status=ACTIVE;secretHashPrefix=<8 hex>;expiresAt=<iso-8601 or null>` |
| `DEVICE_SECRET_ROTATED` | `status=ACTIVE;secretHashPrefix=<old 8 hex>` | `status=ACTIVE;secretHashPrefix=<new 8 hex>` |
| `DEVICE_REVOKED` | `status=ACTIVE;secretHashPrefix=<8 hex>` | `status=REVOKED;secretHashPrefix=<8 hex>` |

Eight hex characters = 32 bits of a 256-bit digest of a 256-bit CSPRNG preimage. It is enough to
prove *that the hash changed* on rotation — which is what the rotation audit row is for — and
useless as a brute-force aid. The full hash is excluded anyway: an offline attacker holding the
complete digest of a device secret would be strictly better off than one holding the prefix, for
no operational gain. A dedicated test asserts no audit row contains the raw secret or a 64-char
hex string.

### C5 — Transactional decorators in `infrastructure/transaction/`, not `@Transactional` on the use case

The state change and its audit row must commit or roll back together. Physical's application
classes carry no Spring annotations (ADR-0021, ArchUnit-enforced), and its adapters declare
`@Transactional(propagation = REQUIRED)` — which *joins* an ambient transaction but does not
create one, so two adapter calls with no outer boundary would be two independent commits.

| Option | Tradeoff | Decision |
|---|---|---|
| `@Transactional` on `*UseCaseImpl` | Spring annotation in `application` — ArchUnit build failure | Rejected |
| `@Transactional` on the controller method | Moves a persistence concern into the web adapter; no precedent in this repo | Rejected |
| Adapter-level `Propagation.REQUIRES_NEW` | Forces the audit into its own transaction — the opposite of what is required | Rejected |
| **`Transactional*UseCase` decorator in `infrastructure/transaction/`** | One five-line class per mutating operation; the exact pattern of `TransactionalCreateVirtualCourseUseCase` and `auth`'s `TransactionalActivateAccountUseCase` | **Chosen** |

Three decorators (`TransactionalRegisterPhysicalDeviceUseCase`,
`TransactionalRotatePhysicalDeviceSecretUseCase`, `TransactionalRevokePhysicalDeviceUseCase`)
wrap the corresponding `*Impl` inside `PhysicalConfiguration`. `Get`/`List` get none — reads
inherit the adapter's `readOnly = true`, the same choice the attendance-history change made.
`infrastructure/transaction/` is a new package for Physical; it is Virtual's existing name.

### C6 — Controller, DTOs, and 409 over 422

`PhysicalDeviceAdminController`, `@RequestMapping("/api/v1/admin/physical/devices")`, carrying a
new `@PhysicalDeviceEndpoint` marker annotation, with `PhysicalDeviceExceptionHandler` scoped by
`@RestControllerAdvice(annotations = PhysicalDeviceEndpoint.class)`. A separate marker/advice
pair, not an extension of `PhysicalCourseExceptionHandler`, mirroring how #39 introduced its own
`@PhysicalAttendanceEndpoint`.

| Method | Path | Success | Body |
|---|---|---|---|
| `POST` | `/` | `201` | `PhysicalDeviceSecretResponse` |
| `GET` | `/{deviceId}` | `200` | `PhysicalDeviceResponse` |
| `POST` | `/{deviceId}/rotate-secret` | `200` | `PhysicalDeviceSecretResponse` |
| `POST` | `/{deviceId}/revoke` | `200` | `PhysicalDeviceResponse` |
| `GET` | `/` | `200` | `PhysicalDeviceListResponse` |

Web DTOs in `infrastructure/web/dto/`, each with the repo's `from(...)` static factory:
`RegisterPhysicalDeviceRequest(name, location, expiresAt)` (bean-validated `@NotBlank`/`@Size`),
`PhysicalDeviceResponse`, `PhysicalDeviceSecretResponse(PhysicalDeviceResponse device, String
secret)`, and `PhysicalDeviceListResponse(List<PhysicalDeviceResponse> devices)` — the last
mirroring `PhysicalCourseManagementListResponse`'s wrapper shape rather than a bare array.

**409, not 422.** This module's handler maps every "well-formed request, wrong resource state"
case to `CONFLICT`: `CourseHasActiveAssignmentsException`, `CapacityBelowAssignedException`, and
`SessionAlreadyOccurredException` are all `409`. `422` appears nowhere in Physical. Re-revoking
and rotating-after-revoke are exactly that shape — the payload is valid, the device's state
forbids the transition — so `409` is both semantically right and the only consistent answer.
Error handling reuses Physical's `ProblemDetails` (RFC 9457) verbatim, including the existing
`IllegalArgumentException` → `400 INVALID_REQUEST` mapping for a malformed `{deviceId}` and
`MethodArgumentNotValidException` → `400`.

`actingUserId(authentication)` = `UUID.fromString(authentication.getName())`, copied from
`PhysicalCourseAdminController`. No `isAdmin(...)` helper: the matcher is the whole gate here.

### C7 — `SecurityConfig`: one matcher, inserted after line 301, before line 312

Verified against the current file. Line 298 is `/api/v1/admin/physical/courses/**`, line 301 is
`/api/v1/admin/physical/sessions/**` (both `hasAnyRole("ADMIN", "INSTRUCTOR")`), lines 303-305
are the virtual admin prefixes, and line 312 is the coarse `/api/v1/admin/**` →
`hasRole("ADMIN")`. Insert **immediately after line 301**:

```java
// #44, US-PHYSICAL-007: device-registry administration is ADMIN-only. Unlike the
// /courses/** and /sessions/** matchers above, an INSTRUCTOR may NOT mint, rotate or
// revoke a reader credential. Declared before the generic /api/v1/admin/** rule below —
// first-match-wins means a matcher placed after it would be unreachable dead code.
.requestMatchers("/api/v1/admin/physical/devices/**").hasRole("ADMIN")
```

**Honest note on redundancy.** Because this prefix is ADMIN-only, line 312 would already produce
the correct `403` for `STUDENT`/`INSTRUCTOR` and `401` for anonymous. This matcher is therefore
*outcome-equivalent today* — its value is that the device rule is stated explicitly next to its
siblings instead of being an accident of ordering, and that the parameterized `SecurityConfigTest`
case pinned to it fails the build if a future edit widens it to `hasAnyRole("ADMIN",
"INSTRUCTOR")` by analogy with the two matchers above it. That mistake is the realistic one here,
and it is exactly what #42/#43's matchers would invite. Its position is not optional even so: it
must precede line 312, or it never evaluates.

Prefix disjointness is verified: `devices` collides with neither `courses`, `sessions`, nor
`/api/v1/admin/physical/attendance/*` (line 291).

### C8 — `V23__physical_devices.sql`

`V22__physical_attendance_history_indexes.sql` is the current head, so `V23` is free. Conventions
read from `V15__physical_attendances.sql` (`BINARY(16)` PK, `VARCHAR(20)` status, `uq_*`/`idx_*`/
`fk_*`) and `V11__virtual_course_management.sql` (the audit-table column set).

```sql
CREATE TABLE physical_devices (
    id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    location VARCHAR(160) NOT NULL,
    secret_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_physical_devices_secret_hash (secret_hash),
    KEY idx_physical_devices_status (status)
);

CREATE TABLE physical_device_audit (
    id BINARY(16) NOT NULL,
    device_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    action VARCHAR(50) NOT NULL,
    previous_value TEXT NULL,
    new_value TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_physical_device_audit_device (device_id),
    CONSTRAINT fk_physical_device_audit_device
        FOREIGN KEY (device_id) REFERENCES physical_devices (id)
);
```

- `CHAR(64)`, not `VARCHAR`: a lowercase SHA-256 hex digest is fixed-width by construction.
- `uq_physical_devices_secret_hash` is a real invariant (two devices sharing a secret would make
  the follow-up's hash lookup ambiguous), and it makes "rotation actually replaced the hash"
  enforceable at the schema level rather than only in a test.
- The audit FK is kept (V15's convention) even though `virtual_course_audit` omits one: a device
  row is never deleted — revocation is a status change — so the FK can never block the append-only
  trail, and it prevents orphan audit rows pointing at a nonexistent device.
- `expires_at` is nullable and **carries no behaviour** in this change (Escenario 5 is out of
  scope); no expiry index is added, because no query filters on it yet.

### C9 — `PhysicalConfiguration` additions are purely additive

Added: two adapter beans (`SecureRandomDeviceSecretGenerator`, `Sha256DeviceSecretHasher`) and
five use-case beans, the three mutating ones wrapped in their decorator (C5). Example:

```java
@Bean
public RegisterPhysicalDeviceUseCase registerPhysicalDeviceUseCase(
    PhysicalDeviceRepository deviceRepository, PhysicalDeviceAuditRepository auditRepository,
    DeviceSecretGenerator secretGenerator, DeviceSecretHasher secretHasher, Clock clock
) {
    return new TransactionalRegisterPhysicalDeviceUseCase(
        new RegisterPhysicalDeviceUseCaseImpl(
            deviceRepository, auditRepository, secretGenerator, secretHasher, clock));
}
```

`DEV_DEFAULT_DEVICE_TOKEN`, the `@Value("${app.physical.checkin.device-token:...}")` field, and
`validateDeviceTokenNotDefaultInProduction()` are **untouched** (D2) — the new registry shares no
property, bean, or constant with the shared check-in token. `PhysicalConfigurationTest` gains
cases; none of its existing assertions change.

### C10 — No new ArchUnit rule is required

| Existing rule | Why it already covers this change |
|---|---|
| `domain` imports no Spring/JPA | `PhysicalDevice`, `DeviceId`, `DeviceStatus`, and the three exceptions use only `java.time`/`java.util` and `shared`'s `BusinessException` |
| `application` imports no Spring/JPA | The five `*Impl`, four out-ports and DTOs are plain Java; `@Transactional` lives in `infrastructure/transaction/` precisely so this stays true (C5) |
| `:api:physical` must not import `com.menta.auth.*` | The generator/hasher are replicated, not imported (D3, C2) — the rule fails the build if anyone "simplifies" by reusing `Sha256Hex` |
| Dependency direction `domain ← application ← infrastructure` | Unchanged; no new inward edge |

The one thing worth an explicit *test* rather than a rule is the leak guard: an assertion that
`PhysicalDeviceView` and `PhysicalDeviceResponse` declare no component whose name contains
`secret` or `hash` (C2).

## Data Flow

    POST /api/v1/admin/physical/devices              [PhysicalDeviceAdminController]
      │  actingUserId = UUID.fromString(auth.getName())
      ▼
    TransactionalRegisterPhysicalDeviceUseCase   @Transactional  ──┐  (C5)
      ▼                                                           │
    RegisterPhysicalDeviceUseCaseImpl                             │
      raw = generator.generate()      32B SecureRandom → Base64url, no padding
      hash = hasher.hash(raw)         lowercase SHA-256 hex                 (C2)
      device = PhysicalDevice.register(name, location, hash, expiresAt, clock.now())
      deviceRepository.save(device)                               │
      auditRepository.append(id, actorId, "DEVICE_REGISTERED",    │  same TX
                             null, "status=ACTIVE;secretHashPrefix=…")  (C4)
      ▼                                                         ──┘
    PhysicalDeviceSecretResult(view, raw)  ← raw exists ONLY on this return path
      ▼
    PhysicalDeviceSecretResponse  →  201 body  →  raw is unreachable forever after (D4)

    POST …/{id}/rotate-secret → findById → orElseThrow(DeviceNotFoundException)
                              → device.rotateSecret(newHash, now)   throws if REVOKED (D5, C1)
    POST …/{id}/revoke        → device.revoke(now)                  throws if REVOKED (D5, C1)
    GET  …/{id}  and  GET …/  → PhysicalDeviceView only — no secret/hash field exists (C2)

## File Changes

| File | Action | Description |
|---|---|---|
| `physical/domain/model/{PhysicalDevice,DeviceId,DeviceStatus}.java` | Create | Aggregate + id VO + closed enum (C1) |
| `physical/domain/exception/{DeviceNotFound,DeviceAlreadyRevoked,DeviceRevoked}Exception.java` | Create | `BusinessException` subclasses (C1) |
| `physical/application/dto/{PhysicalDeviceView,PhysicalDeviceSecretResult,RegisterPhysicalDeviceCommand}.java` | Create | Boundary types; the view has no secret/hash (C2) |
| `physical/application/port/out/{PhysicalDeviceRepository,PhysicalDeviceAuditRepository,DeviceSecretGenerator,DeviceSecretHasher}.java` | Create | Four out-ports (C3) |
| `physical/application/port/in/{Register,Get,RotateSecret,Revoke,List}…UseCase.java` | Create | Five in-ports (C4) |
| `physical/application/usecase/*UseCaseImpl.java` | Create | Five impls (C4) |
| `physical/infrastructure/device/{SecureRandomDeviceSecretGenerator,Sha256DeviceSecretHasher}.java` | Create | JDK-only adapters, no `:api:auth` import (C2, D3) |
| `physical/infrastructure/transaction/Transactional{Register,RotateSecret,Revoke}…UseCase.java` | Create | Decorators — state change + audit in one TX (C5) |
| `physical/infrastructure/persistence/{entity,repository,mapper,adapter}/PhysicalDevice*` | Create | JPA entity, Spring Data repo, mapper, adapter |
| `physical/infrastructure/persistence/{entity,repository,adapter}/PhysicalDeviceAudit*` | Create | Audit entity/repo/adapter, mirroring `VirtualCourseAuditRepositoryAdapter` (C4) |
| `physical/infrastructure/web/controller/PhysicalDeviceAdminController.java` | Create | Five endpoints (C6) |
| `physical/infrastructure/web/controller/{PhysicalDeviceEndpoint,PhysicalDeviceExceptionHandler}.java` | Create | Marker + 404/409/400 mapping (C6) |
| `physical/infrastructure/web/dto/{RegisterPhysicalDeviceRequest,PhysicalDeviceResponse,PhysicalDeviceSecretResponse,PhysicalDeviceListResponse}.java` | Create | Web DTOs (C6) |
| `physical/infrastructure/config/PhysicalConfiguration.java` | Modify | Additive beans only; device-token block untouched (C9, D2) |
| `api/auth/.../security/SecurityConfig.java` | Modify | One matcher after line 301, before line 312 (C7) |
| `api/app/.../db/migration/V23__physical_devices.sql` | Create | Two tables (C8) |
| `api/openapi/physical-v1.yaml` | Modify | Five endpoints (issue DoD) |
| `bruno/API - Direct/physical/*.bru` | Create | Five requests |
| `ProcessPhysicalCheckInUseCaseImpl`, `Attendance`, `CheckInCommand` | **Untouched** | D2 — byte-unchanged, asserted in review |

## Testing Strategy

| Layer | What to test | Approach |
|---|---|---|
| Unit (domain) | `revoke()` on a `REVOKED` device → `DeviceAlreadyRevokedException`; `rotateSecret()` on a `REVOKED` device → `DeviceRevokedException`; `register` → `ACTIVE`; each transition returns a new instance and leaves the receiver unchanged; `DeviceId.of(null/blank/malformed)` → `IllegalArgumentException` | `PhysicalDeviceTest`, `DeviceIdTest` — pure, zero mocks |
| Unit (application) | Register hashes before persisting and never persists the raw value; the returned `rawSecret` equals the generator's output; rotate replaces the stored hash with a *different* value; exactly one audit row per mutating call, with the right action and a `secretHashPrefix` of 8 chars; `previousValue == null` only for register; `get`/`rotate`/`revoke` on an unknown id → `DeviceNotFoundException` | Mockito `verify` + `ArgumentCaptor` on both repositories; `verifyNoMoreInteractions(auditRepository)` |
| Unit (application) | **Leak guard**: no captured audit argument contains the raw secret or any 64-char hex string; `PhysicalDeviceView` declares no `secret`/`hash` component | Captor assertions + a reflective component-name assertion (C10) |
| Unit (infrastructure) | Generator emits 43-char Base64url with no `=`, decoding to 32 bytes, and does not repeat across calls; hasher returns the known lowercase SHA-256 vector for a fixed input and rejects `null` | Mirrors `SecureActivationTokenAdaptersTest` |
| Unit (web) | `201` + secret on register; `200` + secret on rotate; get/list bodies contain **no** `secret` field (JSON-path *absence* assertion); `DeviceNotFoundException` → `404 DEVICE_NOT_FOUND`; both revoked-state exceptions → `409` with distinct codes; malformed `{deviceId}` → `400` | MockMvc slice, mirroring `PhysicalCourseAdminControllerTest` |
| Infra (persistence) | Round-trip of all fields incl. null `expiresAt`; `uq_physical_devices_secret_hash` rejects a duplicate hash; `findAll()` ordering; audit rows are append-only and survive a subsequent device update | `@DataJpaTest` + Testcontainers MySQL 8 |
| Integration | Rollback proof (C5): an audit-append failure leaves **no** device row; end-to-end register → get (no secret) → rotate (hash changed) → revoke → second revoke `409` → rotate-after-revoke `409` | Testcontainers, full filter chain |
| Security | Anonymous → `401`, `STUDENT` → `403`, `INSTRUCTOR` → `403` on all five paths | Parameterized `SecurityConfigTest` cases (C7) |
| Architecture | No Spring/JPA under `physical/domain` or `physical/application`; no `com.menta.auth.*` import anywhere in `:api:physical` | Existing `PhysicalArchitectureTest` run — no new rule (C10) |
| Regression | `ProcessPhysicalCheckInUseCaseImpl` and its tests unchanged | `git diff --name-only` review check (D2) |
| Coverage | 95% domain+application, 90% infrastructure | `./gradlew :api:physical:test jacocoTestCoverageVerification` |

**TDD order (RED → GREEN per slice):** aggregate transitions → `DeviceId` → secret
generator/hasher → register/get use cases → rotate/revoke rejection branches → audit encoding and
leak guard → persistence/migration → controller + advice → `SecurityConfig` 401/403 → rollback
and full-lifecycle integration (last).

## Threat Matrix

N/A — this change introduces no shell command, subprocess, VCS/PR automation, executable-file
classification, or process integration. Its two real adversarial boundaries are covered
structurally rather than by a matrix: secret exposure by the type system (C2) plus the audit and
response leak guards, and authorization by the explicit matcher and its `401`/`403` regression
cases (C7).

## Migration / Rollout

`V23` creates two new tables and alters none — no column added to an existing table, no data
movement, no lock on a populated table. No feature flag: the endpoints do not exist until the
merge lands, and nothing in the shipped check-in path reads either table, so reverting the merge
removes the whole surface with zero behavioural change elsewhere (the payoff of D2). Leave `V23`
applied on revert; the tables are inert.

No configuration property is added, removed, or repurposed, so no deployed reader is affected and
no deployment-time configuration is required.

Three independently deliverable slices, mirroring the proposal's own forecast (`sdd-tasks` owns
the authoritative 400-line guard):

1. **Domain + schema** — aggregate, `DeviceId`, `DeviceStatus`, three exceptions, `V23`, JPA
   entities/repositories/mappers/adapters, generator/hasher adapters.
2. **Use cases + audit** — four out-ports, five in-ports, five `*Impl`, the three transactional
   decorators, `PhysicalConfiguration` wiring, audit encoding and the leak guard.
3. **Web + security** — controller, DTOs, marker/advice, the `SecurityConfig` matcher, OpenAPI,
   Bruno, and the security/integration tests.

Slice 1 is self-contained (schema plus a domain model nothing calls yet); slices 2 and 3 are
purely additive on top.

## Requirement → Component Map

| # | Requirement | Components | Slice |
|---|---|---|---|
| R1 | Register returns the raw secret exactly once, with id/name/location/`ACTIVE`/`expiresAt` | `RegisterPhysicalDeviceUseCaseImpl`, `PhysicalDeviceSecretResult`, controller `201` | **2**/**3** |
| R2 | Get returns metadata with no secret field; unknown id → `404` | `PhysicalDeviceView` (C2), `DeviceNotFoundException`, advice | **2**/**3** |
| R3 | Rotation issues a new secret and the old hash is no longer stored | `PhysicalDevice.rotateSecret`, `uq_*_secret_hash`, adapter | **1**/**2** |
| R4 | Revocation is terminal: re-revoke and rotate-after-revoke both rejected | `DeviceStatus` + throwing transitions (C1) → `409` (C6) | **1**/**3** |
| R5 | List never exposes a secret or hash | `PhysicalDeviceView`/`PhysicalDeviceResponse` type-level absence (C2) | **2** |
| R6 | ADMIN-only; `STUDENT`/`INSTRUCTOR` → `403`, anonymous → `401` | `SecurityConfig` matcher before line 312 (C7) | **3** |
| R7 | Exactly one audit row per create/rotate/revoke, with actor and timestamp, in the same transaction | Audit port/adapter (C4) + transactional decorators (C5) | **2** |

## Open Questions

None blocking. Three items for `sdd-tasks` to carry forward as **facts, not questions**:

- **D6 has no call site here (C2).** Constant-time comparison is a constraint recorded for the
  Escenario 5 follow-up; implementing an unused comparator now would be dead code against the
  coverage gate.
- **The C7 matcher is outcome-equivalent to the existing generic `/api/v1/admin/**` rule today.**
  It is kept for explicitness and as a regression anchor against an over-permissive
  `hasAnyRole("ADMIN", "INSTRUCTOR")` widening, and it must still precede line 312 to evaluate at
  all.
- **`infrastructure/transaction/` is a new package for `:api:physical`** (C5), adopted from
  Virtual/Auth rather than invented — Physical previously had no write path pairing a state change
  with an audit append.

# Proposal: Physical QR device management

**Issue**: #44 (US-PHYSICAL-007, "Gestión de dispositivos QR") · **Milestone**: v0.4.0 · **Input**: Engram `sdd/physical-device-management/explore`

## Intent

There is no device concept anywhere in this repository. The shipped check-in flow
(`ProcessPhysicalCheckInUseCaseImpl.verifyDeviceToken`, #38) authenticates every door reader
with one shared static string from `app.physical.checkin.device-token`, and
`CheckInCommand.deviceId()` is free text stored verbatim on `Attendance` —
`Attendance.java`'s own javadoc states it is "not validated against a registry in the MVP".
Consequences today: one leaked token compromises every reader, revoking a single lost tablet
is impossible without rotating the secret for all of them, and no record says who registered
or revoked what. This change builds the registry — an administrator can register a reader,
receive its secret exactly once, rotate it, revoke it, and list the fleet, with an append-only
audit trail of every such act.

## Scope

### In Scope

- `POST /api/v1/admin/physical/devices` — register a device (name, location, optional
  `expiresAt`). Response reveals the raw secret **exactly once** (issue Escenario 1).
- `GET /api/v1/admin/physical/devices/{deviceId}` — device metadata **without** the secret;
  the raw secret is never recoverable after registration (Escenario 2 → the secret field is
  absent, and an unknown id is `404`).
- `POST /api/v1/admin/physical/devices/{deviceId}/rotate-secret` — issues a new secret,
  invalidates the previous hash, reveals the new raw value once (Escenario 3).
- `POST /api/v1/admin/physical/devices/{deviceId}/revoke` — irreversible; a revoked device
  cannot be re-activated and cannot be rotated (Escenario 4).
- `GET /api/v1/admin/physical/devices` — fleet listing, never exposing secrets or hashes
  (Escenario 6).
- New `PhysicalDevice` aggregate in `:api:physical` (status `ACTIVE`/`REVOKED`, `secretHash`,
  optional `expiresAt`, name, location) with its own domain/application/infrastructure chain.
- Secret generation/hashing ports inside `:api:physical`, replicating `:api:auth`'s shape
  (`SecureRandomActivationTokenGenerator` + `Sha256ActivationTokenHasher`) **without** any
  module dependency on `:api:auth` (see D1, D3).
- Append-only device audit trail (actorId, action, previousValue, newValue, occurredAt),
  replicating `VirtualCourseAuditRepositoryAdapter`'s shape for create/rotate/revoke.
- `V23__physical_devices.sql` — `physical_devices` + `physical_device_audit`, following
  `V15__physical_attendances.sql` conventions (`BINARY(16)` PK, `VARCHAR(20)` status string,
  `uq_*`/`idx_*`/`fk_*` naming). V22 is the current head.
- ADMIN-gated `SecurityConfig` matcher for `/api/v1/admin/physical/devices/**`, declared
  before the generic `/api/v1/admin/**` rule (first-match-wins, same discipline as #42/#43).
- OpenAPI contract update (issue DoD).

### Out of Scope

- **Escenario 5 — "dispositivo expirado rechaza check-in con 401 `DEVICE_EXPIRED`"**.
  Explicitly excluded, deferred to a follow-up issue. Enforcing it requires replacing
  `verifyDeviceToken`'s shared-secret comparison with a per-device registry lookup inside
  `ProcessPhysicalCheckInUseCaseImpl` — an already-shipped, fully-tested use case (#38) whose
  eleven-step ordering is load-bearing — and it is a **breaking contract change** for any
  reader already deployed against the shared token (the device would have to start sending its
  own id + secret). Keeping this change purely additive leaves #38 untouched and lets the
  registry be populated before any reader is cut over. The follow-up issue owns: the check-in
  auth swap, `DEVICE_EXPIRED`/`DEVICE_REVOKED` rejection, and retiring
  `app.physical.checkin.device-token`.
- Any modification to `ProcessPhysicalCheckInUseCaseImpl`, `Attendance`, `CheckInCommand`,
  `physical_attendances`, or `app.physical.checkin.device-token`.
- Automatic expiry enforcement of any kind. `expiresAt` is stored and returned as metadata
  only in this change; it gains behaviour when the check-in path consumes it.
- Device self-service enrollment, device heartbeat/liveness, per-device rate limiting.
- BFF or Android device-administration surfaces.
- #45 (US-PHYSICAL-008, manual check-in) — independent, no anticipation.

## Settled decisions

| # | Topic | Decision |
|---|---|---|
| D1 | Hash algorithm | **SHA-256**, not bcrypt/argon2, despite the issue's literal "hash seguro (bcrypt o argon2)". The device secret is a 256-bit CSPRNG opaque token, not a low-entropy human password. This codebase already draws that exact line: `BCryptPasswordEncoder` via `PasswordEncoderPort` for user passwords, `Sha256Hex` for opaque activation/password-reset tokens. Slow hashing buys nothing against a 32-byte random preimage. **Resolved with the user — not to be re-litigated.** |
| D2 | CRUD-only, additive | The change adds an isolated aggregate and touches no shipped check-in code. **Resolved with the user.** See Out of Scope for Escenario 5's rationale. |
| D3 | No `:api:auth` dependency | `:api:physical` has no dependency on `:api:auth` today and must not gain one. The generator/hasher pattern is *replicated* (JDK `SecureRandom`/`MessageDigest` only), never imported. |
| D4 | One-time reveal | The raw secret exists only in the registration/rotation response body. Only its hash is persisted; there is no endpoint that can return it again. |
| D5 | Revocation is terminal | `REVOKED` is an absorbing state: no un-revoke, no rotate-after-revoke. Both return a domain-level rejection, not a silent no-op. |
| D6 | Comparison | Secret verification, when a consumer eventually needs it, compares digests in constant time (`MessageDigest.isEqual`), mirroring `constantTimeEquals` in the check-in use case. |

## Capabilities

### New Capabilities

- `physical-device-management`: registering, rotating, revoking, and listing QR check-in
  devices; the one-time secret-reveal rule, the terminal revocation rule, and the audit trail.

### Modified Capabilities

- None. `physical-checkin` governs the reader's authentication and is deliberately untouched
  (D2). It will be modified by the Escenario 5 follow-up, not here.

## Approach

Exploration approach 1 (additive-only). A new `PhysicalDevice` aggregate follows the module
chain already established in `:api:physical`: domain model → out-port → `*UseCaseImpl` →
`PhysicalConfiguration` bean wiring → controller → JPA adapter. Domain stays framework-free
(ADR-0021, ArchUnit-enforced); no cross-module import of `com.menta.auth.*` is introduced, so
the existing module-boundary ArchUnit rules hold unchanged. Registration and rotation generate
a 32-byte `SecureRandom` value, Base64 URL-encoded without padding, persist only its lowercase
SHA-256 hex digest, and return the raw value in that single response. Every create/rotate/revoke
appends one audit row in the same transaction as the state change.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/physical/.../domain/model/PhysicalDevice.java` (+ `DeviceId`, `DeviceStatus`) | New | Aggregate, terminal-revocation invariant |
| `api/physical/.../domain/exception/` | New | Device not found / revoked / already revoked |
| `api/physical/.../application/port/out/` | New | `PhysicalDeviceRepository`, `PhysicalDeviceAuditRepository`, `DeviceSecretGenerator`, `DeviceSecretHasher` |
| `api/physical/.../application/port/in/` + `usecase/` | New | Register / RotateSecret / Revoke / List / Get |
| `api/physical/.../infrastructure/device/` | New | `SecureRandomDeviceSecretGenerator`, `Sha256DeviceSecretHasher` (JDK only, no `:api:auth`) |
| `api/physical/.../infrastructure/persistence/` | New | JPA entities, repositories, adapters, mappers |
| `api/physical/.../infrastructure/web/controller/PhysicalDeviceAdminController.java` | New | Five endpoints + exception handler |
| `.../config/PhysicalConfiguration.java` | Modified | Bean wiring only; the existing device-token property and its fail-fast check are untouched |
| `api/auth/.../SecurityConfig.java` | Modified | One `/api/v1/admin/physical/devices/**` → `hasRole("ADMIN")` matcher, before the generic admin rule |
| `api/app/.../db/migration/V23__physical_devices.sql` | New | Two tables, V15 conventions |
| `api/openapi/physical-v1.yaml` | Modified | Device endpoints contract |
| `bruno/API - Direct/physical/` | New | Request files |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| The raw secret leaks into logs, the audit trail, or the list response | High | Only the hash is persisted; audit `previousValue`/`newValue` carry status/hash-prefix metadata, never raw secrets; a dedicated test asserts the list and get responses contain no secret field |
| D1 (SHA-256) read as weaker than the issue's literal bcrypt request | Med | Rationale recorded here and in the spec: 256-bit CSPRNG preimage, matching the codebase's existing token convention. Revisitable, but not silently |
| A reviewer expects Escenario 5 in this change | High | Named explicitly as an exclusion with rationale here and in the PR body; follow-up issue must be opened before this change merges |
| Matcher ordering: `/api/v1/admin/physical/devices/**` placed after `/api/v1/admin/**` | Med | Declared before it, mirroring #42/#43; slice test asserts `403` for `STUDENT`/`INSTRUCTOR` and `401` anonymous |
| Cross-module boundary violated by importing `:api:auth`'s `Sha256Hex` | Med | Replicate, do not import (D3); ArchUnit module-boundary rule already fails the build on `com.menta.auth.*` imports from `:api:physical` |
| Coverage gate: physical is 95% domain+app / 90% infra | Med | Test-first per strict TDD; the revocation/rotation rejection branches need explicit cases |
| Two shipped registry-less readers and the new registry coexist confusingly | Low | The registry is inert until the follow-up wires it; documented in the aggregate javadoc |
| 400-line review budget | Med | `sdd-tasks` forecasts; likely chained slices (domain+migration → use cases → web+security) |

## Rollback Plan

1. Revert the merge commit — the five endpoints disappear. Nothing in the shipped check-in
   path reads `PhysicalDevice`, so no existing behaviour changes (this is the direct payoff
   of D2).
2. `SecurityConfig` returns to its prior matcher list; the added matcher covers only the new
   prefix, so no existing endpoint's authorization changes.
3. Leave `V23` applied — two new, unreferenced tables; no column added to an existing table,
   no data movement. Drop them in a later deliberate migration if desired.
4. No configuration property is removed or repurposed, so no deployed reader is affected.

## Dependencies

- None blocking. No new library, external service, or configuration property.
- A follow-up issue for Escenario 5 (check-in consumes the registry) SHOULD be opened before
  this change merges, so the deferral is tracked rather than lost.

## Success Criteria

- [x] An `ADMIN` registers a device and the response contains the raw secret exactly once,
      plus the device id, name, location, status `ACTIVE`, and `expiresAt`.
- [x] A subsequent `GET` of that device returns its metadata with **no** secret field; an
      unknown device id returns `404`.
- [x] Rotation returns a new raw secret, and the previously issued secret's hash is no longer
      the stored hash.
- [x] Revocation is irreversible: a second revoke, and any rotation after revoke, are rejected.
- [x] Listing returns the fleet with no secret or hash in any element.
- [x] Every create/rotate/revoke appends exactly one audit row carrying actor and timestamp.
- [x] `STUDENT` and `INSTRUCTOR` receive `403` on every device endpoint; anonymous receives `401`.
- [x] `ProcessPhysicalCheckInUseCaseImpl` and its tests are byte-unchanged by this change.
- [x] OpenAPI contract updated; `./gradlew check` passes, including ArchUnit and the physical
      coverage gate.

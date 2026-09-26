# Physical Device Management Specification

## Purpose

Let an `ADMIN` register, inspect, rotate, revoke, and list the QR check-in device fleet, with
a one-time secret reveal, a terminal revocation rule, and an append-only audit trail — without
touching the shipped check-in authentication path.

## Requirements

### Requirement: Device registration with one-time secret reveal

An `ADMIN` MUST be able to register a device via
`POST /api/v1/admin/physical/devices` with `name`, `location`, and an optional `expiresAt`.
Registration MUST create the device in `ACTIVE` status, persist only the SHA-256 hash of a
generated secret, and return `201` with the raw secret in plaintext alongside `id`, `name`,
`location`, `status`, and `expiresAt`. That raw secret value MUST NOT be persisted anywhere and
MUST NOT be retrievable through any endpoint after this response.

#### Scenario: Admin registers a device and receives the raw secret once

- GIVEN an authenticated `ADMIN`
- WHEN they call `POST /api/v1/admin/physical/devices` with `name`, `location`, and no `expiresAt`
- THEN the response is `201` with a raw `secret` field, `status: ACTIVE`, and `expiresAt: null`
- AND only the secret's SHA-256 hash is stored, never the raw value

#### Scenario: Registration accepts an optional expiresAt as inert metadata

- GIVEN an authenticated `ADMIN`
- WHEN they call `POST /api/v1/admin/physical/devices` with a future `expiresAt`
- THEN the response is `201` and echoes that `expiresAt`
- AND no enforcement of that value occurs anywhere in this change

### Requirement: Device metadata read never exposes secret or hash

An `ADMIN` MUST be able to read one device's metadata via
`GET /api/v1/admin/physical/devices/{deviceId}`. The response MUST contain `id`, `name`,
`location`, `status`, `createdAt`, and `expiresAt`, and MUST NOT contain the raw secret or its
hash under any field name. An unknown `deviceId` MUST return `404`.

#### Scenario: Get returns metadata without any secret field

- GIVEN a registered device
- WHEN an `ADMIN` calls `GET /api/v1/admin/physical/devices/{deviceId}`
- THEN the response is `200` and contains no raw secret and no hash field

#### Scenario: Unknown device id returns 404

- GIVEN no device exists with a given id
- WHEN an `ADMIN` calls `GET /api/v1/admin/physical/devices/{deviceId}` with that id
- THEN the response is `404`

### Requirement: Secret rotation invalidates the previous hash

An `ADMIN` MUST be able to rotate an `ACTIVE` device's secret via
`POST /api/v1/admin/physical/devices/{deviceId}/rotate-secret`. Rotation MUST generate a new
secret, persist only its SHA-256 hash, replace the previously stored hash so it no longer
validates, and return `200` with the new raw secret exactly once, following the same one-time
reveal rule as registration.

#### Scenario: Rotation issues a new secret and invalidates the old hash

- GIVEN an `ACTIVE` device with an existing stored hash
- WHEN an `ADMIN` calls `POST /api/v1/admin/physical/devices/{deviceId}/rotate-secret`
- THEN the response is `200` with a new raw secret
- AND the stored hash no longer matches the previously issued secret's hash

### Requirement: Revocation is terminal

An `ADMIN` MUST be able to revoke an `ACTIVE` device via
`POST /api/v1/admin/physical/devices/{deviceId}/revoke`, transitioning it to `REVOKED`.
`REVOKED` MUST be an absorbing state: a second revoke on an already-`REVOKED` device, and any
`rotate-secret` call on a `REVOKED` device, MUST both be rejected with a domain-level error
response, never treated as a silent no-op or successful rotation.

#### Scenario: Revoking an active device succeeds

- GIVEN an `ACTIVE` device
- WHEN an `ADMIN` calls `POST /api/v1/admin/physical/devices/{deviceId}/revoke`
- THEN the response is `200` (or `204`) and the device's status is `REVOKED`

#### Scenario: Revoking an already-revoked device is rejected, not a no-op

- GIVEN a `REVOKED` device
- WHEN an `ADMIN` calls `POST /api/v1/admin/physical/devices/{deviceId}/revoke` again
- THEN the response is a domain-level rejection (`409` or equivalent), not `200`
- AND the device remains `REVOKED`

#### Scenario: Rotating a revoked device is rejected

- GIVEN a `REVOKED` device
- WHEN an `ADMIN` calls `POST /api/v1/admin/physical/devices/{deviceId}/rotate-secret`
- THEN the response is a domain-level rejection (`409` or equivalent)
- AND no new secret is issued and no hash is changed

### Requirement: Fleet listing excludes secrets and hashes

An `ADMIN` MUST be able to list the device fleet via `GET /api/v1/admin/physical/devices`. Each
element MUST contain `name`, `location`, `status`, `createdAt`, and `expiresAt`, and MUST NOT
contain a raw secret or a hash under any field name, for devices in any status.

#### Scenario: Listing returns the fleet with no secret or hash anywhere

- GIVEN an `ACTIVE` device and a `REVOKED` device
- WHEN an `ADMIN` calls `GET /api/v1/admin/physical/devices`
- THEN the response is `200` with both devices listed
- AND no element in the response contains a raw secret or hash field

### Requirement: All device endpoints are ADMIN-only

Every device management endpoint — register, get, rotate-secret, revoke, and list — MUST reject
a `STUDENT` or `INSTRUCTOR` caller with `403` and an unauthenticated caller with `401`. Unlike
`/courses/**` and `/sessions/**`, no `INSTRUCTOR` ownership scoping applies, because a device
fleet has no per-course ownership concept.

#### Scenario: STUDENT is rejected on register

- GIVEN an authenticated caller with only the `STUDENT` role
- WHEN they call `POST /api/v1/admin/physical/devices`
- THEN the response is `403`

#### Scenario: INSTRUCTOR is rejected on register

- GIVEN an authenticated caller with only the `INSTRUCTOR` role
- WHEN they call `POST /api/v1/admin/physical/devices`
- THEN the response is `403`

#### Scenario: Anonymous caller is rejected on register

- GIVEN no authentication credential
- WHEN a request is made to `POST /api/v1/admin/physical/devices`
- THEN the response is `401`

#### Scenario: STUDENT is rejected on revoke

- GIVEN an authenticated caller with only the `STUDENT` role and an existing device
- WHEN they call `POST /api/v1/admin/physical/devices/{deviceId}/revoke`
- THEN the response is `403`

#### Scenario: INSTRUCTOR is rejected on revoke

- GIVEN an authenticated caller with only the `INSTRUCTOR` role and an existing device
- WHEN they call `POST /api/v1/admin/physical/devices/{deviceId}/revoke`
- THEN the response is `403`

#### Scenario: Anonymous caller is rejected on revoke

- GIVEN no authentication credential and an existing device
- WHEN a request is made to `POST /api/v1/admin/physical/devices/{deviceId}/revoke`
- THEN the response is `401`

### Requirement: Every state-changing action appends exactly one audit row

Each `register`, `rotate-secret`, and `revoke` action MUST append exactly one audit row
(`actorId`, `action`, `previousValue`, `newValue`, `occurredAt`) in the same transaction as the
state change. `previousValue`/`newValue` MUST carry status or hash-prefix metadata, never the
raw secret.

#### Scenario: Registration appends one audit row

- GIVEN an authenticated `ADMIN`
- WHEN they register a device
- THEN exactly one audit row exists for that device recording the `ADMIN` as actor and the
  create action
- AND the audit row contains no raw secret

#### Scenario: Rotation and revocation each append their own single audit row

- GIVEN a registered device
- WHEN an `ADMIN` rotates its secret and later revokes it
- THEN exactly one audit row exists for the rotation and exactly one for the revocation
- AND both rows carry the `ADMIN` actor, previous/new status or hash-prefix metadata, and no raw
  secret

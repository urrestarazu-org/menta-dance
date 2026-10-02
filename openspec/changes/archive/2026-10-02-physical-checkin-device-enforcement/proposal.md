# Proposal: Physical Check-in Device Enforcement (#266)

## Intent

QR check-in authenticates every reader with one shared secret (`app.physical.checkin.device-token`). A leaked secret cannot be revoked per reader, and the device registry (#44) is never enforced. Authenticate each reader against the registry so revocation and expiry take effect immediately.

## Scope

### In Scope
- Registry-only QR auth: `deviceId` (UUID, JSON body) + `deviceToken` (raw secret); hard cutover (D1).
- 401 `INVALID_DEVICE_TOKEN` for unknown id, non-UUID id (rejected before any DB hit) or wrong secret (D2, D6).
- 401 `DEVICE_REVOKED` / `DEVICE_EXPIRED` only after the secret is proven; REVOKED wins; expired when `expiresAt <= now` (D3).
- New check-in exceptions; `DEVICE_REVOKED` code reused (401 check-in, 409 admin), documented (D5).
- `Attendance.device_id` stores the authenticated UUID (D6).
- Per-rejection WARN log + Micrometer counter by reason (`unknown_or_invalid`, `revoked`, `expired`); secret never logged (D7).
- Property retired: ignored, startup WARN if present, fail-fast removed (D8).
- OpenAPI, Bruno, check-in parts of docs, spec delta.

### Out of Scope (D4, D9)
- Schema change, `lastSeenAt`, MANUAL check-in, derived EXPIRED admin status, expiry recovery.
- BFF/Android UI, Grafana alert rule, non-check-in docs drift.
- Rate limiting (separate board issue).

## Capabilities

### New Capabilities
None

### Modified Capabilities
- `physical-checkin`: device gate moves from shared-token compare to registry authentication; adds 401 `DEVICE_REVOKED`/`DEVICE_EXPIRED`; persisted device id is the registered UUID.

`physical-device-management` is referenced only; no requirement change.

## Approach

- `PhysicalDeviceAuthenticator` (application): UUID pre-check -> `PhysicalDeviceRepository.findById` -> `DeviceSecretHasher` + `MessageDigest.isEqual` (dummy compare when unknown) -> REVOKED -> expiry via `Clock`. Replaces the use case's `String deviceToken` arg; stays gate 1, Redis-free order kept.
- Rejection out-port + `LogAndMetric*Adapter` (`infrastructure/observability`), mirroring #236's `SubscriptionFulfillmentAlarmPort`.
- `PhysicalConfiguration`: drop `@Value`, dev default, `@PostConstruct`, `Environment`; WARN if property set.

### Delivery (stacked to `develop`, 800-line budget)

| Slice | Content | Forecast |
|---|---|---|
| S1 | Exceptions, authenticator, observability port + adapter, unit tests | ~400 |
| S2 | Use-case swap, config removal + WARN, handler mappings, test churn | ~350 |
| S3 | Integration tests (device seeding), OpenAPI, Bruno, docs, spec delta | ~400 |

## Affected Areas

| Area (`api/physical/src/main/java/com/menta/physical/`) | Impact |
|---|---|
| `application/usecase/ProcessPhysicalCheckInUseCaseImpl.java` | Modified |
| `application/` authenticator + rejection out-port | New |
| `domain/exception/` check-in device exceptions | New |
| `infrastructure/observability/` adapter | New |
| `infrastructure/config/PhysicalConfiguration.java` | Modified |
| `infrastructure/web/controller/PhysicalCheckInExceptionHandler.java` | Modified |
| `api/openapi/physical-v1.yaml`, `bruno/`, `docs/` | Modified |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Breaking change for readers | Low | No production readers; runbook; `Breaking Changes` label |
| Non-UUID id -> `IllegalArgumentException` -> 400 | Med | Pre-validate format; never call `DeviceId.of` unguarded; test |
| `DEVICE_REVOKED` 401 vs 409 | Low | Separate exception; documented in spec + OpenAPI |
| Expired devices show ACTIVE in admin views | Med | Documented; re-register (D4) |
| Unauthenticated DB lookup, no rate limit | Med | UUID pre-check, PK lookup; rate-limit issue |

## Rollback Plan

Revert slice PRs in reverse order; no schema or data migration. UUID values in `device_id` stay valid. Reverting restores the fail-fast, so prod/staging must set `app.physical.checkin.device-token` to a non-default value first.

## Migration / Runbook

No production readers today. Before deploying any reader: ADMIN registers it via `POST /api/v1/admin/physical/devices`, provisions UUID + one-time secret to the reader; remove the legacy property from env.

## Dependencies

- #44 device registry (merged).

## Success Criteria

- [ ] Active, unexpired registered device checks in; attendance stores its UUID.
- [ ] Revoked -> 401 `DEVICE_REVOKED`; expired -> 401 `DEVICE_EXPIRED`; unknown/non-UUID/wrong secret -> 401 `INVALID_DEVICE_TOKEN`; none reaches Redis; non-UUID never hits DB.
- [ ] Each rejection emits one WARN + one counter increment; secret never logged.
- [ ] Legacy property unused; startup WARN when set.
- [ ] Coverage gates and ArchUnit pass; each slice under 800 lines.

## Proposal question round

Covered by locked decisions D1-D9 (business round, 2026-10-01). No open product questions.

# Proposal: Physical Check-in with Ephemeral QR (US-PHYSICAL-001)

## Intent

Students with a confirmed capacity assignment have no way to prove attendance at the door, and staff have no attendance record per session. A door reader must accept an ephemeral QR issued by the student app and turn it into exactly one auditable attendance row, rejecting unauthorized readers, wrong/expired credentials, and students without a confirmed seat — without creating duplicate rows on re-scan.

## Scope

### In Scope

- `POST /api/v1/physical/sessions/{sessionId}/access-qr` — issues an ephemeral signed credential for the authenticated student (403 `CAPACITY_ASSIGNMENT_REQUIRED` without a confirmed assignment).
- `POST /api/v1/physical/sessions/{sessionId}/check-ins` (`QR` variant) — 201 with the new attendance; 200 with the existing one on idempotent re-scan.
- Cheap rejections ordered before any Redis lock: device token, QR shape, session binding, signature, expiry, check-in window, assignment, existing attendance.
- Two Redis locks (QR `jti` + `session:student`) guarding the INSERT; Redis failure fails closed (503 + `Retry-After`).
- New table `physical_attendances` with `UNIQUE (session_id, user_id)` as the schema-level idempotency guard.
- RFC 9457 Problem Details for every rejection reason.

### Out of Scope

- Redis compare-and-delete compensation (issue #38 escenario 6). Product decision: the lock expires by TTL instead.
- `MANUAL` check-in variant — enum value reserved, behavior deferred to issue #45.
- Real HMAC signing; the MVP ships a deterministic placeholder format.
- Android-side visual QR refresh (separate repository/PR).
- Device registry, per-device credentials, and attendance query/reporting endpoints.

## Capabilities

### New Capabilities

- `physical-checkin`: QR issuance, QR redemption, idempotency, check-in window, and door-reader authorization.

### Modified Capabilities

- None.

## Approach

Clean Architecture inside `api:physical`: `Attendance` aggregate + `AttendanceId`/`AttendanceKind` in domain; `IssuePhysicalAccessQrUseCaseImpl` and `ProcessPhysicalCheckInUseCaseImpl` in application behind ports (`AttendanceRepository`, `RedisLockPort`, `QrCredentialSignatureService`, `Clock`); JPA/Redis/web adapters in infrastructure. The door reader authenticates with a shared secret (`app.physical.checkin.device-token`) validated **inside** the use case with constant-time comparison, so the endpoint stays `permitAll()` at the filter level and fails fast on the dev-default token in production profiles. The check-in window (`[scheduledAt-30m, scheduledAt+2h]`) and TTLs are computed at runtime from `app.qr.*`, never persisted.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/physical/.../domain` | New | `Attendance`, `AttendanceId`, `AttendanceKind`, 6 check-in exceptions |
| `api/physical/.../application` | New | Both use cases, ports, DTOs, `QrCredentialParser` |
| `api/physical/.../infrastructure` | New | Controller, Problem Details handler, JPA adapter, `RedisCheckInLockPort`, `QrProperties`, wiring |
| `api/app/.../db/migration/V15__physical_attendances.sql` | New | Attendance table + unique key |
| `api/auth/.../SecurityConfig.java` | Modified | Route rules for both endpoints |
| `api/openapi/physical-v1.yaml`, `docs/` | Modified | Contract and diagrams |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Placeholder signature is forgeable | Med | Shared device token required; HMAC tracked as follow-up |
| Single shared device token leaks | Med | Fail-fast on dev default in prod; per-device credentials deferred (US-PHYSICAL-007) |
| Orphan Redis lock after failed INSERT | Low | Short lock TTL (10s); no compensation by decision |
| Window defaults not requirement-derived | Low | Fully configurable via `app.qr.*` |

## Rollback Plan

Revert the feature branch (module code is additive), revert the `SecurityConfig` route rules, and drop `physical_attendances` with a compensating Flyway migration. No other module reads the table, so rollback is data-local.

## Dependencies

- Confirmed capacity assignments (`physical_capacity_assignments`) and `physical_sessions` already exist.
- Redis 7.4 available; `noeviction` policy.

## Success Criteria

- [ ] Escenarios 1–5 of issue #38 covered by tests.
- [ ] Re-scan never creates a second attendance row.
- [ ] No Redis call happens for a rejected scan.
- [ ] `:api:app` test suite green; Physical module coverage above the 80% profile.
- [ ] ArchUnit clean: domain framework-free, no cross-module JPA/SQL.

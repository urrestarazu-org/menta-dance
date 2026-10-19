# Design: Physical Check-in with Ephemeral QR (US-PHYSICAL-001)

Retrospective design: this documents the architecture as built and verified (230 tests in `:api:physical`, 189 in `:api:app`, 0 failures). It records why the shipped structure is the way it is, not options still open.

## Technical Approach

Clean Architecture inside `api:physical`, one layer per direction: `domain` (framework-free `Attendance` aggregate, `AttendanceId`, `AttendanceKind`, 8 check-in exceptions) → `application/port/in` (2 use-case interfaces) → `application/usecase` (impls plus the package-private `QrCredentialParser` and `AttendanceViewMapper`) → `application/port/out` (`AttendanceRepository`, `Clock`, `QrCredentialSignatureService`, `RedisLockPort`, `PhysicalCapacityAssignmentRepository`) → `infrastructure` (JPA, Redis, QR, web). Authorization for the door reader lives in the use case, not the filter chain; the check-in window and TTLs are computed at runtime, never persisted.

## Architecture Decisions

| # | Decision | Choice | Rejected alternative | Rationale |
|---|---|---|---|---|
| 1 | Module layering | Ports/adapters inside `api:physical`, domain free of Spring | Service + entity directly on JPA | Keeps ArchUnit green and lets the placeholder signature and Redis lock be swapped without touching use-case code |
| 2 | Validation order | Cheap-checks-first: device token → QR shape → session binding → signature → expiry → session/cancel/window → assignment → idempotency, then locks | Lock first, validate inside the critical section | A flood of invalid scans must never create Redis contention (escenario 4); only a request past 8 gates reaches the 2 locks |
| 3 | Lock release | None. `RedisLockPort.acquireIfAbsent` has no counterpart; an orphan lock expires by its 10s TTL | Compare-and-delete compensation (escenario 6) | Explicit product decision, documented in the port Javadoc — two-phase commit per hallway scan is not worth it |
| 4 | Signature | Placeholder `qr:<studentId>:<sessionId>:<jti>:<exp>`; verification recomputes and compares with `MessageDigest.isEqual` | Ship HMAC now; compare raw strings | Recompute-and-compare is forward-compatible: real HMAC changes only `FormatQrCredentialSignatureService`, not the use case. Constant-time compare avoids a timing oracle |
| 5 | Reader auth | Single shared `app.physical.checkin.device-token`, `@Value` default + `@PostConstruct` fail-fast on `prod\|production\|staging`; endpoint is `permitAll()` | Filter-level auth or a device registry | Mirrors `BillingConfiguration.DEV_DEFAULT_WEBHOOK_HMAC_SECRET`; the reader has no user session, so Spring Security cannot express this. Per-device credentials deferred to US-PHYSICAL-007 |
| 6 | Check-in window | Computed `[scheduledAt - before, scheduledAt + after]` from `QrProperties` field defaults (30m/2h), applied on **both** issuance and check-in | Persist window columns; check only at the door | No requirement fixes these numbers; field defaults mirror `BunnyNetProperties`. Issuance-side gate closes a gap found by adversarial review — escenario 1 makes the window a precondition of issuing |
| 7 | Cancelled sessions | `SessionCancelledException` (403 `SESSION_CANCELLED`) right after loading the session, **before** the assignment check | Rely on the assignment check alone | Cancelling does not delete confirmed assignments, so a stale assignment would otherwise open a class that no longer happens (`docs/diagrams/STATE-DIAGRAMS.md:391`). Also found by adversarial review |
| 8 | Idempotency | Two layers: read-through `findBySessionIdAndUserId` before Redis, plus `UNIQUE (session_id, user_id)` in `V15` | Catch the duplicate-key violation only | Read-through keeps a replay off Redis entirely; the constraint is the last line if anything bypasses the use case |
| 9 | Wiring | `PhysicalConfiguration` composes use cases via `@Bean`; `RedisCheckInLockPort` built inline over the autoconfigured `RedisTemplate<String,String>` | `@Component` on use cases; custom Redis bean | Matches `VirtualConfiguration`. Known cost: full-context tests need a Redis-related `@MockBean` when `integration-test` excludes `RedisAutoConfiguration` — a pattern that predates this feature (`BillingPlansIntegrationTest`) |
| 10 | Bean naming | `physicalClock()`, not `clock()` | Reuse `clock()` | Spring registers `@Bean` by method name regardless of declaring class; `clock()` collided with `VirtualConfiguration.clock()` (`BeanDefinitionOverrideException`). Autowiring by type is unaffected |

## Data Flow

    Student app ──POST /access-qr (JWT)──► IssueQr ──► session → cancelled? → window → assignment
                                                  └──► sign(jti, exp) ──► AccessQrResponse

    Reader ──POST /check-ins (deviceToken)──► ProcessCheckIn
        token → parse → sessionId match → signature → expiry     (no I/O beyond Clock)
        → session → cancelled → window → assignment → existing?  (DB reads only)
        → lock(checkin:qr:<jti>) → lock(checkin:attendance:<s>:<u>) → INSERT  (Redis, then DB)

Replay returns 200 with the existing row at the `existing?` gate. Redis failure becomes `CheckInDegradedException` → 503 + `Retry-After: 30`.

## File Changes

| Path | Action | Description |
|------|--------|-------------|
| `api/physical/.../domain/model/{Attendance,AttendanceId,AttendanceKind}.java` | Create | Aggregate + identity + variant (`MANUAL` reserved) |
| `api/physical/.../domain/exception/*` | Create | 8 check-in exceptions carrying stable error codes |
| `api/physical/.../application/{port/in,port/out,usecase,dto}` | Create | 2 use cases, 5 ports, `QrCredentialParser`, `AttendanceViewMapper`, commands/views |
| `api/physical/.../infrastructure/persistence/{entity,mapper,repository,adapter}` | Create | `AttendanceJpaEntity` + adapter over `AttendanceJpaRepository` |
| `api/physical/.../infrastructure/redis/RedisCheckInLockPort.java` | Create | `SET NX EX`; any `RuntimeException` → fail closed |
| `api/physical/.../infrastructure/qr/{QrProperties,FormatQrCredentialSignatureService}.java` | Create | Window/TTL config; placeholder signer |
| `api/physical/.../infrastructure/web/controller/PhysicalCheckIn{Controller,ExceptionHandler,Endpoint}.java` | Create | Both endpoints; RFC 9457 mapping scoped by annotation |
| `api/physical/.../infrastructure/config/PhysicalConfiguration.java` | Modify | `physicalClock()`, both use-case beans, device-token fail-fast |
| `api/app/src/main/resources/db/migration/V15__physical_attendances.sql` | Create | Table + `UNIQUE (session_id, user_id)` + FK |
| `api/auth/.../security/SecurityConfig.java` | Modify | `check-ins` → `permitAll`, `access-qr` → `authenticated` |

## Interfaces / Contracts

```java
Optional<String> acquireIfAbsent(String key, Duration ttl);   // empty = held elsewhere
String sign(String studentId, String sessionId, String jti, long expiresAtEpochSeconds);
Optional<Attendance> findBySessionIdAndUserId(SessionId sessionId, UUID userId);
```

Status map: 201 new / 200 replay / 400 `INVALID_QR_CREDENTIAL`, `INVALID_REQUEST` / 401 `INVALID_DEVICE_TOKEN` / 403 `OUTSIDE_CHECKIN_WINDOW`, `SESSION_CANCELLED`, `CAPACITY_ASSIGNMENT_REQUIRED` / 404 `SESSION_NOT_FOUND` / 409 `ALREADY_PROCESSING` / 410 `EXPIRED_QR_CREDENTIAL` / 503 degraded.

## Testing Strategy

| Layer | What | Approach |
|-------|------|----------|
| Unit | Both use cases, parser, signer, lock port, mappers, handler | JUnit 5 + Mockito, injected fixed `Clock`, one test per rejection reason |
| Integration | Filter-chain reachability, both status codes, replay | `:api:app` `PhysicalCheckInIntegrationTest` through the real `SecurityConfig` |
| Architecture | Domain framework-free, no cross-module JPA | ArchUnit |

## Threat Matrix

N/A — no shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary. HTTP route authorization is covered by decisions 2, 5 and 7 and by the integration tests above.

## Non-Goals (deliberate, not debt hidden)

- **Redis compare-and-delete compensation** — product decision; TTL expiry is the accepted recovery (escenario 6).
- **Real HMAC** — documented risk with an explicit follow-up; the port boundary already isolates the swap.
- **`MANUAL` variant (#45), Android QR refresh, Bruno collection** — out of this PR's scope, still in the ticket.

## Migration / Rollout

Additive. `V15` forward-only; rollback drops `physical_attendances` via a compensating migration and reverts the two `SecurityConfig` matchers. No other module reads the table.

## Open Questions

- [ ] Window defaults (30m/2h) are engineering judgement, not a requirement — confirm with product.
- [ ] HMAC key distribution to door readers must be settled before per-device credentials (US-PHYSICAL-007).

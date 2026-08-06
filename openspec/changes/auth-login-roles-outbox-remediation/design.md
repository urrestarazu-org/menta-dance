# Design: Auth Login, Roles, and Outbox Remediation

## Technical Approach

Keep HTTP contracts and pure application use cases intact. Add Spring-proxied infrastructure decorators so each mutation and its `OutboxAppender` write share one MySQL transaction. Repair `User`/JPA mapping, make Redis projection failures retryable, and write a heartbeat every tick.

## Architecture Decisions

| Decision | Choice | Alternatives considered | Rationale |
|---|---|---|---|
| Auth transaction boundary | Infrastructure `@Component` decorators implement the existing input ports and delegate to the current pure use cases under `@Transactional`. | Annotating application use cases; transactions only on `OutboxJpaAppender`. | Preserves ADR-0021 while ensuring a real Spring proxy encloses all repository and outbox writes. |
| User rehydration | Add `tokenVersion` to `UserJpaEntity`; map it both ways through an explicit domain rehydration factory. | Defaulting version on every load. | `auth_users.token_version` already exists and must survive reload for compromise detection. |
| Redis failures | Re-throw blacklist and heartbeat writes; retain fail-closed reads. | Swallowing all Redis exceptions. | The reconciler can mark a failed projection retryable, while request reads still deny when safety cannot be proven. |
| Retry lifecycle | Query eligible `PENDING` plus due `FAILED` rows; on failure retain `FAILED`, diagnostic, attempt count, and `now + backoff`; on success mark `COMPLETED`. | PENDING-only polling; marking completion after a swallowed failure. | A FAILED row must be retried only when due and never be lost. |

## Data Flow

```mermaid
sequenceDiagram
    participant C as Controller
    participant T as Proxied transaction decorator
    participant U as Pure use case
    participant M as MySQL
    C->>T: existing input port
    T->>U: execute
    U->>M: mutation + outbox PENDING
    alt either write fails
        M-->>T: exception; rollback
        T-->>C: existing error mapping
    else both writes succeed
        T->>M: commit
        T-->>C: existing token/204 response
    end
```

The decorator returns tokens only after commit. Any persistence/outbox failure escapes the proxy and rolls back its mutation and outbox row. Every tick attempts heartbeat writing; blacklist success is `COMPLETED`, while failure is `FAILED + error + next_retry_at`. Public registration validates `null`/`STUDENT` before duplicate lookup, hashing, persistence, or outbox work; internal provisioning bypasses this validation.

## File Changes

| File | Action | Description |
|---|---|---|
| `api/auth/.../domain/model/User.java` | Modify | Add explicit rehydration accepting token version. |
| `api/auth/.../persistence/entity/UserJpaEntity.java` | Modify | Map `token_version`; extend constructor/accessors. |
| `api/auth/.../persistence/mapper/UserMapper.java` | Modify | Round-trip token version via rehydration. |
| `api/auth/.../infrastructure/transaction/Transactional*UseCase.java` | Create | Proxied login, refresh, and logout port decorators. |
| `api/auth/.../infrastructure/config/AuthConfiguration.java` | Modify | Wire decorators as the sole input-port beans. |
| `api/auth/.../application/usecase/RegisterUserUseCaseImpl.java` | Modify | Reject caller-supplied non-STUDENT roles before writes. |
| `api/auth/.../security/TokenBlacklistPortImpl.java` | Modify | Propagate write errors; retain fail-closed reads. |
| `api/auth/.../application/port/out/TokenBlacklistPort.java` | Modify | Add heartbeat-write contract used by app reconciliation. |
| `api/app/.../outbox/OutboxBlacklistReconciler.java` | Modify | Add `REQUIRES_NEW` eligible-row processing, FAILED/backoff transitions, and heartbeat scheduling. |
| `api/auth/.../persistence/repository/OutboxRowJpaRepository.java` | Modify | Select PENDING and due FAILED rows in order. |
| `api/auth/src/test/**`, `api/app/src/test/**` | Modify/Create | Regression, persistence, and Redis-focused coverage. |

## Interfaces / Contracts

```java
public interface TokenBlacklistPort {
    void blacklist(String jti, Duration ttl); // throws on Redis write failure
    void writeHeartbeat();                     // throws on Redis write failure
}

public static User rehydrate(..., long tokenVersion) { ... }
```

Existing `/auth/login`, `/auth/refresh`, `/auth/logout`, and `/auth/register` responses remain unchanged, including degraded `503` with `Retry-After: 30`.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Public role rejection; mapper round-trip; Redis read fail-closed vs write propagation; heartbeat invocation; due-backoff selection. | JUnit/Mockito with deterministic clock. |
| Integration | Login, rotation, family revocation, and logout roll back both mutation and outbox if append persistence fails; token version reloads. | Spring context with real MySQL/Testcontainers (or configured integration MySQL); invoke the proxied port, not a manually constructed use case. |
| Integration | Redis failure marks row FAILED with future retry and skips it before due time; successful due retry completes it; each tick writes heartbeat. | Real Redis/Testcontainers for heartbeat/read behavior; adapter-failure injection for deterministic write failure. |
| Contract | Valid endpoint payload/statuses, locked/invalid behavior, degraded 503, and privileged internal provisioning. | Existing auth flow integration tests plus focused controller tests. |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary.

## Migration / Rollout

No migration required: `auth_users.token_version` and outbox retry columns already exist. Deploy as one remediation; monitor retained FAILED rows and heartbeat freshness. Roll back the application unit if regressions occur, leaving durable PENDING/FAILED rows for recovery.

## Open Questions

- [ ] Confirm the repository's existing MySQL/Redis integration-test profile and Testcontainers availability before task sizing.

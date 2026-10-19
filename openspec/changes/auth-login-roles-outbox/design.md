# Design: auth (login + roles + outbox)

## Technical Approach

Este slice cierra `:api:auth` con tokens (access JWT + refresh), roles y outbox durable-local. **auth-login** aporta `Login`/`Logout` con outbox post-commit en la misma tx (ADR-0027). **auth-refresh** añade rotación por familia + bump `token_version` ante compromiso. **common-outbox** define `OutboxEvent`/`OutboxListener` en `:api:shared` (sin Spring/JPA per ADR-0021) y reconciler scheduled en `:api:app` que proyecta `blacklist:jti:{jti}` a Redis. Fail-closed (ADR-0026) se implementa como guard que consulta `common_outbox_events` antes de emitir/validar.

## Architecture Decisions

| # | Choice | Alternatives | Rationale |
|---|--------|--------------|-----------|
| 1 | JWT HS256 con secret por env, jti UUID-v4, claims `sub/userId/role/tokenVersion/exp` | RS256 + JWKS | Sin KMS en MVP (ADR-0025); evita round-trip a keyserver; secret en `.env`. |
| 2 | Reconciler `@Scheduled` pull-batch `SELECT PENDING ORDER BY id LIMIT N` | `@EventListener(AFTER_COMMIT)+@Async`; broker | ADR-0026 prohíbe bus Redis; ADR-0027 prohíbe FK/SQL cross-module. Pull tolera crash entre commit y tick. |
| 3 | Refresh en MySQL `auth_refresh_tokens` | Redis-first + MySQL index | MySQL autoridad de `token_version` (ADR-0025). Redis-first pierde atomicidad de revocación de familia en una sola tx. |
| 4 | `AuthorizationManager<RequestAuthorizationContext>` + path-matcher | `@PreAuthorize("hasRole…")` puro | Matcher cubre rutas dinámicas sin parsear SpEL. |
| 5 | Caffeine `tokenVersionCache` TTL 30 s, max 10 k | MySQL full-read; Redis | ADR-0026 autoriza Caffeine para datos reconstruibles; TTL = ventana fail-closed evita drift. |
| 6 | Outbox `event_id` ULID (26 chars) | UUID v7; BIGINT auto-increment | ULID comparte orden temporal y es portable sin dependencia extra. Idempotencia por UNIQUE `(aggregate_id, event_type)`. |
| 7 | V2 single BIG migration auth + outbox | V2 auth + V3 outbox separadas | Bloque único garantiza coherencia schema ↔ lecturas en primer arranque; rollback es V3 compensatoria (ADR-0027). |

## Data Flow

```
Login:  LoginUseCase (Tx)  verify pwd → INSERT refresh(ACTIVE) → INSERT outbox(AuthUserLoggedIn,PENDING) → COMMIT → 200
[async] Reconciler tick → SELECT PENDING LIMIT 100 → SET blacklist:jti:{jti} 1 EX {ttl} → COMPLETED

Refresh: RefreshTokenUseCase (Tx)  sha256 lookup → ACTIVE + tokenVersion match? → status=USED + INSERT new + outbox RefreshRotated → COMMIT; si USED/REVOKED/stale → token_version++ + family REVOKED + outbox RefreshRevoked → COMMIT, 401

Logout: LogoutUseCase (Tx atomic)  status=REVOKED + INSERT outbox(UserLoggedOut) → COMMIT → 204
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `api:app db/migration/V2__auth_tokens_and_outbox.sql` | Create | DDL |
| `api:app outbox/OutboxBlacklistReconciler.java` | Create | scheduled |
| `api:shared outbox/{OutboxEvent,OutboxListener,OutboxStatus}.java` | Create | marker API |
| `api:auth domain/exception/{InvalidCredentials,RefreshTokenCompromised,LockedUser}Exception.java` | Create | `BusinessException` |
| `api:auth domain/model/RefreshToken.java` | Create | aggregate |
| `api:auth application/{port in/out, usecase}` | Create | use cases |
| `api:auth infrastructure/persistence/{entity,repository,adapter}` | Create | JPA |
| `api:auth infrastructure/security/{JwtService,RoleAuthorizationManager,TokenUserDetailsService,AuthDegradedGuard}.java` | Create | jjwt+Security |
| `api:auth infrastructure/web/{controller, dto}` | Create | endpoints |
| `api:auth test/ArchitectureTest.java` | Modify | regla nueva |
| `bruno/api/auth/{Login,Refresh,Logout}.bru` | Create | E2E |

## Interfaces / Contracts

```java
public record OutboxEvent(String eventId, String eventType, String aggregateId,
    String payload, OutboxStatus status, Instant createdAt) {}
@FunctionalInterface public interface OutboxListener<E extends OutboxEvent> { void onEvent(E event); }
public interface TokenBlacklistPort { boolean isBlacklisted(String jti); void blacklist(String jti, Duration ttl); }
public final class RoleAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    AuthorizationDecision check(Supplier<Authentication> a, RequestAuthorizationContext c);
}
```

V2 DDL agrega `auth_users.token_version BIGINT NOT NULL DEFAULT 1`; `auth_refresh_tokens` (PK BINARY(16), UNIQUE `token_hash CHAR(64)`, INDEX `(family_id,status)`); `common_outbox_events` (PK BIGINT AUTO_INCREMENT, doble UNIQUE `(aggregate_id,event_type)` y `event_id`, payload JSON + status/attempts/last_error/next_retry_at/created_at/processed_at).

## Testing Strategy

| Layer | Approach |
|-------|----------|
| Unit domain | RefreshToken lifecycle, excepciones, Role — JUnit 5 + AssertJ sin Spring (ADR-0021) |
| Unit application | orquestación, fail-closed, family bump — Mockito sobre puertos |
| Integration | tx atómica, idempotencia UNIQUE, side-effect Redis — Testcontainers MySQL + Redis |
| ArchUnit | `domain_should_not_depend_on_infrastructure`, `application_should_not_use_spring_web`, `controllers_no_repositories` — `ArchitectureTest.java` |
| Manual E2E | happy-path + 503 fail-closed — `bruno/api/auth/{Login,Refresh,Logout}.bru` |

## Threat Matrix

`N/A — slice NO toca routing/shell/subprocess/VCS, ni clasificación de ejecutables ni integración de procesos. Solo HTTP/JPA/Redis con boundaries Spring Security y Flyway forward-only per ADR-0027.`

## Migration / Rollout

- **DB:** V2 single BIG con `token_version DEFAULT 1`; logins pre-V2 válidos hasta próximo login. `RegisterUserUseCase` intacto; HS256 + default aceptan sesiones sin re-login.
- **Feature flag:** `auth.login.enabled=true` (default). `false` → 503 `Retry-After: 30`; reconciler profile `auth-disabled` (skip).
- **Reversibilidad:** V3 compensatoria trunca refresh+outbox + `DROP COLUMN token_version`. Bandera degrada limpio mientras se opera el DROP.

## Open Questions

- [ ] ¿Backoff reconciler configurable (`auth.outbox.reconcile-backoff-seconds=30`) o fijo por código?
- [ ] ¿Hash refresh SHA-256 plain o HMAC-SHA256 con secret separado del JWT?
- [ ] ¿Threshold 30 s de `AUTH_DEGRADED` configurable por yaml?
- [ ] ¿Token version reset (V3) invalida TODAS las sesiones vía `token_version++` global o solo bloquea nuevas emisiones?

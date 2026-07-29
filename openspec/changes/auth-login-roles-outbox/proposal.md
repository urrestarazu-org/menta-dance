# Proposal: auth (login + roles + outbox)

## Intent
Cerrar `:api:auth` con tokens (JWT + refresh), enforcement de roles y outbox durable. Hoy solo se registran usuarios; ningún consumidor puede iniciar sesión sin cruzar boundaries prohibidos.

## Scope
### In Scope
- Migración `V2__auth_tokens_and_outbox.sql` en `:api:app` (ADR-0027): `auth_users.token_version`, `auth_refresh_tokens`, `common_outbox_events`.
- `LoginUseCase`, `RefreshTokenUseCase`, `LogoutUseCase` + puertos + outbox post-commit + excepciones (`InvalidCredentials`, `RefreshTokenCompromised`, `LockedUser`).
- Infra: `JwtService`, `TokenBlacklistPort` (Redis), `RoleAuthorizationManager`, `TokenUserDetailsService`, reconciler scheduled. Tests JaCoCo 100% `:domain`/`:application` + ArchUnit + bruno.

### Out of Scope
- Consumer cross-module de outbox (billing en fase posterior).
- OAuth2 social, MFA, password reset, rate-limit, soft-reactivación.

## Capabilities
### New Capabilities
- `auth-login`: tokens ADR-0025 (HMAC-SHA256, jti, tokenVersion) + register público solo `STUDENT`.
- `auth-refresh`: rotación por familia; compromiso revoca todo + `token_version++`.
- `common-outbox`: tabla durable post-commit + marker `OutboxListener` en `:api:shared`.

### Modified Capabilities
None. `openspec/specs/` está vacío.

## Approach
- Tokens ADR-0025: access JWT 15 min (jti + tokenVersion); refresh UUID SHA-256, 7 d.
- Login → outbox `AuthUserLoggedIn`; refresh rota + reuso → familia + `token_version++` → outbox `RefreshRevoked`; logout → outbox `UserLoggedOut`.
- Outbox **durable-local** (ADR-0026): `common_outbox_events` MySQL + reconciler scheduled popula `blacklist:jti:*` en Redis. **Nunca Redis como bus.** Consumers cross-module fuera.
- Login **fail-closed**: reconciliación atrasada → 503 + `Retry-After: 30`.
- AuthorizationManager compara `Role` del JWT vs matcher de ruta.

## Affected Areas
Bajo `api/app`:
- `db/migration/V2__auth_tokens_and_outbox.sql` — tablas + `token_version`.
- `outbox/OutboxBlacklistReconciler.java` — scheduled reconciler.

Bajo `api/shared`:
- `outbox/{OutboxEvent,OutboxListener,OutboxAppender}.java` — marker + DTO base.

Bajo `api/auth`:
- `application/usecase/{Login,RefreshToken,Logout}UseCaseImpl.java` — use cases + outbox post-commit.
- `domain/exception/{InvalidCredentials,RefreshTokenCompromised,LockedUser}Exception.java`.
- `infrastructure/security/{JwtService,TokenBlacklistPort,RoleAuthorizationManager,TokenUserDetailsService}.java` — Spring Security + jjwt + Redis.
- `infrastructure/persistence/{RefreshTokenJpaEntity,RefreshTokenJpaRepository,RefreshTokenRepositoryAdapter}.java`.
- `infrastructure/web/controller/AuthController.java` — `/login`, `/refresh`, `/logout`.
- `test/ArchitectureTest.java` (modif.) — refuerza `controllers_no_repositories` + `:domain` libre de Spring/JPA.

Tests manuales: `bruno/auth/{Login,Refresh,Logout}.bru`.

## Risks
| Risk | Lik | Mitigation |
|------|-----|------------|
| Latencia p95 por lectura MySQL en cada request autenticado | Med | Caffeine sobre `tokenVersion` (ADR-0026) |
| Compromiso tarda en detectarse | Low-Med | Familia + `token_version++` invalidan TODO al detectar reuso |
| Rollback con V2 ya escrita | Med | V3 compensatoria manual (ADR-0027 forward-only) |

## Rollback Plan
- DB: V3 compensatoria trunca `auth_refresh_tokens` + `common_outbox_events` y remueve `token_version`.
- App: flag `auth.login.enabled=false` → 503 + reconciler droppable por profile `auth-disabled`.
- `RegisterUserUseCase` intacto (zero-risk surface).

## Dependencies
ADR-0025, ADR-0026, ADR-0027. `jjwt 0.12.6`, Spring Security 6, `spring-boot-starter-data-redis` (ya en `api/auth/build.gradle.kts`).

## Success Criteria
- Login emite access (15 min, jti) + refresh (7 d, SHA-256); `token_version=1`.
- Logout invalida refresh + evento en `common_outbox_events` mismo tx.
- Refresh rota; reuso revoca familia + `token_version++` (test).
- Login → 503 + `Retry-After: 30` con reconciliación atrasada.
- Register rechaza `Role != STUDENT` (test de `:domain`).
- JaCoCo `:domain`/`:application` 100%, `:infrastructure` ≥ 50%.
- ArchUnit `domain_should_not_depend_on_infrastructure`, `application_should_not_use_spring_web`, `controllers_no_repositories` en verde.
- bruno `Login+Refresh+Logout` happy-path.

## Decisiones de Round 1 (usuario)
- Outbox durable-local + reconciler Redis. Sin Redis como bus. Consumers cross-module fuera.
- Familia completa + `token_version++` en compromiso. Re-autenticación obligatoria.
- Register público solo `STUDENT`.
- Login fail-closed: 503 + `Retry-After: 30`.

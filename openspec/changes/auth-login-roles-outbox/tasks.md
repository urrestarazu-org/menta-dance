# Tasks: auth (login + roles + outbox)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 800–1100 |
| 400-line budget risk | High |
| Chained PRs | Yes (PR1 Foundation+Domain / PR2 Infrastructure / PR3 Wiring+Tests) |
| Delivery strategy | ask-on-risk |
| Chain strategy | feature-branch-chain |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Work Units

| Unit | Goal | Likely PR | Base branch | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|-------------|----------------------|-----------------|-------------------|
| 1 | V2 Flyway + UserStatus.LOCKED + api:shared outbox + LoginUseCase/RefreshTokenUseCase/LogoutUseCase + RED tests | PR1 | `feature/auth-login-roles-outbox-tracking` (new) | `:api:auth:test --tests "*Login*Test" "*Refresh*Test" "*Logout*Test"` | `./gradlew :api:auth:test --tests "*LoginUseCase*"` | V3 Flyway compensatoria + revert PR1 |
| 2 | JwtService + TokenBlacklistPortImpl + RefreshTokenJpaEntity + OutboxJpaAppender + OutboxBlacklistReconciler | PR2 | branch de PR1 (`feature/auth-login-roles-outbox-pr1`) | `:api:app:test --tests "*OutboxBlacklistReconciler*"` | `./gradlew :api:app:test` | flag `auth.outbox.reconciler.enabled=false` + revert PR2 |
| 3 | AuthController + DTOs + SecurityConfig wiring + AuthConfiguration + AuthFlowIntegrationTest + ArchUnit + bruno | PR3 | branch de PR2 (`feature/auth-login-roles-outbox-pr2`) | `./gradlew check` | smoke `POST /auth/login` con bruno | revert PR3 (cero impacto en runtime) |

## Phase 1: Foundation

- [x] 1.1 Create `api/app/src/main/resources/db/migration/V2__auth_tokens_and_outbox.sql` con `auth_users.token_version`, `auth_refresh_tokens`, `common_outbox_events` (UNIQUE doble).
- [x] 1.2 Extend `api/auth/src/main/java/com/menta/auth/domain/model/UserStatus.java` Agregar `LOCKED`.
- [x] 1.3 Create `api/shared/src/main/java/com/menta/shared/outbox/{OutboxEvent, OutboxListener, OutboxStatus}.java` marker sin Spring/JPA.
- [x] 1.4 Create `api/auth/src/main/java/com/menta/auth/domain/exception/{InvalidCredentials, RefreshTokenCompromised, LockedUser}Exception.java` extends `BusinessException`.
- [x] 1.5 Create `api/shared/src/test/java/com/menta/shared/outbox/OutboxMarkerTest.java` asserts marker compila sin Spring/JPA.

## Phase 2: Core domain
- [x] 2.1 RED `LoginUseCaseImplTest.java` cubre scenarios "Credenciales válidas", "LOCKED", "401 sin discriminar", "Reconciliador 503".
- [x] 2.2 GREEN Create `application/port/in/LoginUseCase.java` + `usecase/LoginUseCaseImpl.java`.
- [x] 2.3 RED `RefreshTokenUseCaseImplTest.java` cubre "Rotación exitosa", "USED revoca familia", ">7d rechazado", "tokenVersion viejo", "REVOKED inmutable".
- [x] 2.4 GREEN Create `RefreshTokenUseCase.java` + `RefreshTokenUseCaseImpl.java`.
- [x] 2.5 RED `LogoutUseCaseImplTest.java` cubre "Logout atómico", "Refresh rotado activa familia".
- [x] 2.6 GREEN Create `LogoutUseCase.java` + `LogoutUseCaseImpl.java`.
- [x] 2.7 REFACTOR Create `application/port/out/{OutboxAppender,RefreshTokenRepository,AuthDegradedGuard,TokenHasher}.java` ports Mockito-friendly. (Note: also extended with `AccessTokenIssuer` + `IssuedAccessToken` for the access-token contract; `AuthDegradedException` lives in `domain.exception` to support the spec's fail-closed 503 scenario.)

## Phase 3: Infrastructure adapters

- [x] 3.1 RED `JwtServiceTest` HS256+jti+tokenVersion. GREEN Create `infrastructure/security/JwtService.java` (jjwt 0.12.6).
- [x] 3.2 RED `TokenBlacklistPortImplTest` Redis mock. GREEN Create `infrastructure/security/TokenBlacklistPortImpl.java`.
- [x] 3.3 Create `infrastructure/persistence/entity/RefreshTokenJpaEntity.java` + `repository/RefreshTokenJpaRepository.java` + `adapter/RefreshTokenRepositoryAdapter.java` + `mapper/RefreshTokenJpaMapper.java`.
- [x] 3.4 Create `infrastructure/persistence/entity/{OutboxEventJpaEntity,OutboxIdMarker}.java` + repository.
- [x] 3.5 Create `infrastructure/security/{RoleAuthorizationManager,TokenUserDetailsService,JwtAuthenticationFilter}.java`.
- [x] 3.6 RED `OutboxJpaAppenderTest` scenario "Inserción duplicada rechazada". GREEN Create `infrastructure/outbox/OutboxJpaAppender.java`.
- [x] 3.7 Create `api/app/src/main/java/com/menta/app/outbox/OutboxBlacklistReconciler.java` `@Scheduled` pull-batch PENDING → SET + COMPLETED; FAILED+backoff.
- [x] 3.8 RED `OutboxBlacklistReconcilerTest` scenarios "procesa lote", "Redis caído FAILED", "Crash retoma". GREEN.

## Phase 4: Wiring

- [ ] 4.1 Create `infrastructure/web/controller/AuthController.java` `/auth/{login,refresh,logout}` + `@ExceptionHandler`.
- [ ] 4.2 Create DTOs `infrastructure/web/dto/{LoginRequest,RefreshRequest,TokenResponse,ErrorResponse,LogoutRequest}.java`.
- [ ] 4.3 Modify `api/auth/src/main/java/com/menta/auth/infrastructure/security/SecurityConfig.java` registrar filtro JWT + `RoleAuthorizationManager`.
- [ ] 4.4 Create `infrastructure/config/AuthConfiguration.java` binds ports → impls.
- [ ] 4.5 Create `integration/AuthFlowIntegrationTest.java` `@SpringBootTest` + Testcontainers login→refresh→logout.

## Phase 5: Tests / ArchUnit / Bruno

- [ ] 5.1 Modify `api/auth/src/test/java/com/menta/auth/ArchitectureTest.java` agrega `controllers_should_not_depend_on_repositories`, `domain_should_not_depend_on_shared_outbox_infrastructure`.
- [ ] 5.2 Activar `tasks.jacocoTestCoverageVerification` en `api/auth/build.gradle.kts` 100% LINE `com.menta.auth.domain.*` y `.application.*`.
- [ ] 5.3 Create `bruno/api/auth/{Login,Refresh,Logout}.bru` happy paths.
- [ ] 5.4 Verify `./gradlew check` en verde. Sin AI attribution en commits.

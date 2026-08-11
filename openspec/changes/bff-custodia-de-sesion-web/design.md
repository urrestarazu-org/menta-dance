# Design: BFF Session Custody (Web)

## Technical Approach

Implement server-side session custody using Spring Session + Redis with Clean Architecture layering. The BFF intercepts Auth API responses, stores tokens in Redis sessions, and exposes only opaque `SESSION` cookies to browsers. Token refresh is transparent via a filter before downstream API calls.

## Architecture Decisions

| Decision | Choice | Alternatives Rejected | Rationale |
|----------|--------|----------------------|-----------|
| Session Store | Spring Session Data Redis | In-memory (not scalable), JDBC (adds DB load) | Redis already operational for Auth blacklist; horizontal scaling built-in |
| Token Refresh | Pre-request filter | Reactive chain, scheduled background | Simplest; refresh only when needed; avoids race conditions |
| Auth API Client | WebClient (blocking adapter) | RestTemplate (deprecated), Feign (extra dep) | Already in BFF deps; supports reactive if needed later |
| Session Serialization | JDK (default) | JSON, Kryo | Simplest; tokens are strings; no complex objects |

## Data Flow

```
Browser                    BFF                         Redis                   Auth API
   |                        |                            |                        |
   |-- POST /login -------->|                            |                        |
   |                        |-- POST /api/v1/auth/login ----------------------->|
   |                        |<-- 200 {accessToken, expiresIn} + X-Refresh-Token |
   |                        |-- SET spring:session:sessions:<id> -------------->|
   |<-- Set-Cookie: SESSION |                            |                        |
   |                        |                            |                        |
   |-- GET /dashboard ----->|                            |                        |
   |   Cookie: SESSION      |-- GET spring:session:sessions:<id> -------------->|
   |                        |<-- session {accessToken, refreshToken, expiresAt} |
   |                        |   [if expired: POST /api/v1/auth/refresh]         |
   |                        |-- Authorization: Bearer <accessToken> ----------->|
   |<-- 200 HTML            |                            |                        |
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `bff/build.gradle.kts` | Modify | Add `spring-boot-starter-data-redis`, `spring-session-data-redis` |
| `bff/src/main/resources/application.yml` | Modify | Add Spring Session + Redis config |
| `bff/src/main/java/.../domain/model/SessionTokens.java` | Create | Value object: accessToken, refreshToken, expiresAt |
| `bff/src/main/java/.../application/port/out/AuthApiPort.java` | Create | Port for Auth API calls |
| `bff/src/main/java/.../application/port/out/SessionTokenRepository.java` | Create | Port for session token storage |
| `bff/src/main/java/.../application/usecase/LoginUseCase.java` | Create | Orchestrates login flow |
| `bff/src/main/java/.../application/usecase/LogoutUseCase.java` | Create | Orchestrates logout + revocation |
| `bff/src/main/java/.../application/dto/LoginCommand.java` | Create | Input DTO for login |
| `bff/src/main/java/.../infrastructure/config/BffSecurityConfig.java` | Create | SecurityFilterChain with form login, CSRF, session |
| `bff/src/main/java/.../infrastructure/config/RedisSessionConfig.java` | Create | `@EnableRedisHttpSession` + cookie config |
| `bff/src/main/java/.../infrastructure/web/controller/LoginController.java` | Create | POST /login, GET /login |
| `bff/src/main/java/.../infrastructure/web/controller/LogoutController.java` | Create | POST /logout |
| `bff/src/main/java/.../infrastructure/web/filter/TokenRefreshFilter.java` | Create | Transparent refresh before API calls |
| `bff/src/main/java/.../infrastructure/adapter/AuthApiAdapter.java` | Create | WebClient adapter for Auth API |
| `bff/src/main/java/.../infrastructure/adapter/SpringSessionTokenRepository.java` | Create | Session attribute adapter |
| `bff/src/main/resources/templates/login.html` | Create | Thymeleaf login form with CSRF |

## Interfaces / Contracts

```java
// domain/model/SessionTokens.java
public record SessionTokens(
    String accessToken,
    String refreshToken,
    Instant expiresAt
) {
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}

// application/port/out/AuthApiPort.java
public interface AuthApiPort {
    TokenPairResult login(String email, String password);
    TokenPairResult refresh(String refreshToken);
    void logout(String refreshToken);
}

// application/port/out/SessionTokenRepository.java
public interface SessionTokenRepository {
    void store(SessionTokens tokens);
    Optional<SessionTokens> load();
    void clear();
}
```

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | LoginUseCase, LogoutUseCase | Mockito mocks for ports |
| Unit | SessionTokens.isExpired | Direct assertions |
| Integration | LoginController + Redis | @SpringBootTest + Testcontainers Redis |
| Integration | TokenRefreshFilter | WebMvcTest + mocked session |
| E2E | Full login/logout flow | Playwright (post-MVP) |

## Threat Matrix

N/A -- no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary.

## Migration / Rollout

No migration required. New session store; existing unauthenticated BFF continues to work. Feature is additive.

## Open Questions

- [ ] None blocking. Cookie name `SESSION` vs custom `MENTA_SESSION` can be decided at config time.

# Tasks: BFF Session Custody (Web)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 850-1100 (14 new files + 2 configs) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (Infrastructure + Domain) → PR 2 (Application + Security) → PR 3 (Token Refresh + E2E) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Infrastructure + Domain (Redis session + domain model) | PR 1 → feature/bff-session-custody | `./gradlew :bff:test --tests "*SessionTokens*" --tests "*RedisSessionConfig*"` | `docker compose up redis` + BFF health check verifying Redis connectivity | Remove Spring Session dependency + domain model + config files; BFF reverts to stateless |
| 2 | Application Layer + Security (Use cases + SecurityFilterChain + Login/Logout) | PR 2 → PR 1 branch | `./gradlew :bff:test --tests "*UseCase*" --tests "*Login*" --tests "*Logout*"` | Manual: POST /login with credentials → receive SESSION cookie → GET /dashboard → POST /logout → verify session cleared in Redis CLI | Remove use cases + controllers + security config; BFF reverts to open (no auth enforcement) |
| 3 | Token Refresh + E2E (Transparent refresh filter + integration tests) | PR 3 → PR 2 branch | `./gradlew :bff:test --tests "*TokenRefresh*" --tests "*Integration*"` | Manual: login → wait 15 min (access token expiry) → make authenticated request → verify transparent refresh in logs | Remove TokenRefreshFilter + integration tests; authenticated requests with expired tokens fail (expected until refresh is implemented) |

---

## Phase 1: Infrastructure Foundation (TDD)

**Goal**: Set up Spring Session + Redis dependency, configuration, and health check without auth logic.

- [ ] 1.1 RED: Write test `RedisSessionConfigTest` verifying Redis connection bean exists and session namespace is `spring:session`
- [ ] 1.2 GREEN: Add `spring-boot-starter-data-redis` + `spring-session-data-redis` to `bff/build.gradle.kts`
- [ ] 1.3 GREEN: Create `RedisSessionConfig` with `@EnableRedisHttpSession` and cookie config (HttpOnly, Secure, SameSite=Lax, 30min TTL)
- [ ] 1.4 GREEN: Add Spring Session + Redis config to `application.yml`: `spring.session.store-type=redis`, namespace, timeout, Redis host/port
- [ ] 1.5 REFACTOR: Extract cookie config values to application.yml properties
- [ ] 1.6 RED: Write `BffHealthCheckTest` verifying Redis connectivity check fails when Redis unavailable
- [ ] 1.7 GREEN: Add Redis health indicator to actuator endpoints
- [ ] 1.8 VERIFY: `docker compose up redis` + `./gradlew :bff:bootRun` + curl `/actuator/health` shows Redis UP

---

## Phase 2: Domain Layer (TDD)

**Goal**: Create immutable domain model for session tokens with zero dependencies.

- [ ] 2.1 RED: Write `SessionTokensTest` verifying record constructor, accessors, and `isExpired(Instant)` method
- [ ] 2.2 GREEN: Create `com.menta.bff.domain.model.SessionTokens` record (accessToken, refreshToken, expiresAt)
- [ ] 2.3 GREEN: Implement `isExpired(Instant now)` method comparing `now.isAfter(expiresAt)`
- [ ] 2.4 RED: Write test verifying `SessionTokens` with null fields throws IllegalArgumentException
- [ ] 2.5 GREEN: Add compact constructor validation rejecting null accessToken, refreshToken, expiresAt
- [ ] 2.6 REFACTOR: Ensure domain package has ZERO Spring/infrastructure imports
- [ ] 2.7 RED: Write `TokenPairResultTest` for DTO holding API response (accessToken, refreshToken, expiresIn)
- [ ] 2.8 GREEN: Create `com.menta.bff.application.dto.TokenPairResult` record
- [ ] 2.9 RED: Write `LoginCommandTest` for DTO holding login input (email, password)
- [ ] 2.10 GREEN: Create `com.menta.bff.application.dto.LoginCommand` record with validation

---

## Phase 3: Application Ports (TDD)

**Goal**: Define Clean Architecture ports (interfaces) without implementations.

- [ ] 3.1 Create `com.menta.bff.application.port.out.AuthApiPort` interface: `login(email, password)`, `refresh(refreshToken)`, `logout(refreshToken)`
- [ ] 3.2 Create `com.menta.bff.application.port.out.SessionTokenRepository` interface: `store(SessionTokens)`, `load()`, `clear()`
- [ ] 3.3 RED: Write `AuthApiPortContractTest` as an abstract test verifying contract behavior (to be inherited by adapter tests)
- [ ] 3.4 RED: Write `SessionTokenRepositoryContractTest` as an abstract test verifying store/load/clear idempotency

---

## Phase 4: Use Cases (TDD)

**Goal**: Implement business logic in application layer with mocked ports.

- [ ] 4.1 RED: Write `LoginUseCaseTest` verifying successful login stores tokens in repository
- [ ] 4.2 GREEN: Create `LoginUseCase` calling `AuthApiPort.login()` then `SessionTokenRepository.store()`
- [ ] 4.3 RED: Write test verifying login with invalid credentials throws AuthenticationException
- [ ] 4.4 GREEN: Handle 401 from Auth API and throw domain exception
- [ ] 4.5 RED: Write test verifying Auth API unavailable (503) propagates as ServiceUnavailableException
- [ ] 4.6 GREEN: Handle 503 from Auth API and throw domain exception
- [ ] 4.7 RED: Write `LogoutUseCaseTest` verifying logout calls Auth API revocation then clears session
- [ ] 4.8 GREEN: Create `LogoutUseCase` calling `AuthApiPort.logout()` then `SessionTokenRepository.clear()`
- [ ] 4.9 RED: Write test verifying logout is idempotent (no exception if session already cleared)
- [ ] 4.10 GREEN: Handle Optional.empty() from `load()` gracefully in logout
- [ ] 4.11 REFACTOR: Extract error handling to shared exception mapper

---

## Phase 5: Infrastructure Adapters (TDD)

**Goal**: Implement ports using Spring Session and WebClient.

- [ ] 5.1 RED: Write `AuthApiAdapterTest` extending `AuthApiPortContractTest` with WireMock
- [ ] 5.2 GREEN: Create `AuthApiAdapter` using WebClient to call Auth API `/api/v1/auth/login`, `/refresh`, `/logout`
- [ ] 5.3 GREEN: Parse JSON response body and `X-Refresh-Token` header into `TokenPairResult`
- [ ] 5.4 RED: Write test verifying 401 from Auth API throws `AuthenticationException`
- [ ] 5.5 GREEN: Map 4xx/5xx responses to domain exceptions
- [ ] 5.6 RED: Write test verifying connection timeout throws `ServiceUnavailableException`
- [ ] 5.7 GREEN: Configure WebClient timeout and error handling
- [ ] 5.8 RED: Write `SpringSessionTokenRepositoryTest` extending `SessionTokenRepositoryContractTest`
- [ ] 5.9 GREEN: Create `SpringSessionTokenRepository` storing/loading `SessionTokens` as session attribute `AUTH_TOKENS`
- [ ] 5.10 GREEN: Implement `store()` using `session.setAttribute()`, `load()` using `session.getAttribute()`, `clear()` using `session.removeAttribute()`
- [ ] 5.11 REFACTOR: Inject `HttpSession` via request context; ensure thread-safe access

---

## Phase 6: Security Configuration (TDD)

**Goal**: Set up Spring Security with form login, CSRF, and session management.

- [ ] 6.1 RED: Write `BffSecurityConfigTest` verifying SecurityFilterChain permits `/login`, `/health`, and requires auth for other endpoints
- [ ] 6.2 GREEN: Create `BffSecurityConfig` with `@EnableWebSecurity` and `SecurityFilterChain` bean
- [ ] 6.3 GREEN: Configure `authorizeHttpRequests`: permit `/login`, `/logout`, `/error`, `/actuator/health`; authenticate all others
- [ ] 6.4 GREEN: Configure `formLogin()` with custom login page `/login` (no Spring default)
- [ ] 6.5 GREEN: Enable CSRF protection for POST/PUT/DELETE
- [ ] 6.6 GREEN: Configure `sessionManagement()`: `SessionCreationPolicy.IF_REQUIRED`, session fixation protection
- [ ] 6.7 RED: Write test verifying unauthenticated request to `/dashboard` redirects to `/login`
- [ ] 6.8 RED: Write test verifying POST without CSRF token returns 403
- [ ] 6.9 REFACTOR: Extract security rules to configuration properties

---

## Phase 7: Web Controllers (TDD)

**Goal**: Implement login/logout HTTP endpoints calling use cases.

- [ ] 7.1 RED: Write `LoginControllerTest` (MockMvc) verifying GET `/login` returns login form with CSRF token
- [ ] 7.2 GREEN: Create `LoginController` with GET `/login` returning `login.html` Thymeleaf template
- [ ] 7.3 GREEN: Create `login.html` with email/password form, hidden CSRF input, and submit button
- [ ] 7.4 RED: Write test verifying POST `/login` with valid credentials redirects to `/dashboard` and sets SESSION cookie
- [ ] 7.5 GREEN: Implement POST `/login` calling `LoginUseCase.execute(LoginCommand)` and redirecting on success
- [ ] 7.6 RED: Write test verifying POST `/login` with invalid credentials returns 401 with error message
- [ ] 7.7 GREEN: Catch `AuthenticationException` and return error view with message
- [ ] 7.8 RED: Write test verifying POST `/login` when Auth API unavailable returns 503 with Retry-After
- [ ] 7.9 GREEN: Catch `ServiceUnavailableException` and return 503 response
- [ ] 7.10 RED: Write `LogoutControllerTest` verifying POST `/logout` with valid CSRF token clears session and redirects to `/login`
- [ ] 7.11 GREEN: Create `LogoutController` with POST `/logout` calling `LogoutUseCase.execute()` and invalidating session
- [ ] 7.12 GREEN: Ensure `session.invalidate()` is called AFTER Auth API revocation
- [ ] 7.13 RED: Write test verifying logout sets `Set-Cookie: SESSION=; Max-Age=0` in response
- [ ] 7.14 GREEN: Explicitly clear SESSION cookie in response
- [ ] 7.15 REFACTOR: Extract redirect URLs to application.yml

---

## Phase 8: Token Refresh Filter (TDD)

**Goal**: Implement transparent access token refresh before API calls.

- [ ] 8.1 RED: Write `TokenRefreshFilterTest` verifying filter loads session tokens on authenticated request
- [ ] 8.2 GREEN: Create `TokenRefreshFilter` extending `OncePerRequestFilter`, loading `SessionTokens` from repository
- [ ] 8.3 RED: Write test verifying filter does nothing if access token NOT expired (expiresAt > now)
- [ ] 8.4 GREEN: Check `sessionTokens.isExpired(Instant.now())` and skip refresh if false
- [ ] 8.5 RED: Write test verifying filter calls Auth API refresh when access token expired
- [ ] 8.6 GREEN: Call `AuthApiPort.refresh(refreshToken)` when expired
- [ ] 8.7 RED: Write test verifying filter updates session with new tokens after successful refresh
- [ ] 8.8 GREEN: Store new `SessionTokens` via `SessionTokenRepository.store()` atomically
- [ ] 8.9 RED: Write test verifying refresh failure (401/423 from Auth API) clears session and redirects to `/login`
- [ ] 8.10 GREEN: Catch refresh exceptions, call `SessionTokenRepository.clear()`, and redirect to login
- [ ] 8.11 RED: Write test verifying filter adds `Authorization: Bearer <accessToken>` header to downstream API calls (future work)
- [ ] 8.12 GREEN: Set request attribute `ACCESS_TOKEN` for controllers to use (or inject via RestTemplate interceptor)
- [ ] 8.13 REFACTOR: Ensure filter is idempotent (refresh only once per request even if called multiple times)
- [ ] 8.14 GREEN: Register filter in `BffSecurityConfig` BEFORE `UsernamePasswordAuthenticationFilter`

---

## Phase 9: Integration Tests (TDD)

**Goal**: Verify end-to-end flows with real Redis (Testcontainers) and mocked Auth API (WireMock).

- [ ] 9.1 RED: Write `LoginIntegrationTest` with `@SpringBootTest` + Testcontainers Redis + WireMock Auth API
- [ ] 9.2 GREEN: Verify POST `/login` with valid credentials creates session in Redis (`spring:session:sessions:<id>` key exists)
- [ ] 9.3 GREEN: Verify session contains serialized `SessionTokens` with expected accessToken, refreshToken, expiresAt
- [ ] 9.4 RED: Write `AuthenticatedRequestIntegrationTest` verifying GET `/dashboard` with SESSION cookie loads tokens from Redis
- [ ] 9.5 GREEN: Mock Auth API, send authenticated request, verify access token used in upstream call
- [ ] 9.6 RED: Write `TokenRefreshIntegrationTest` verifying expired access token triggers transparent refresh
- [ ] 9.7 GREEN: Set `expiresAt` in past, make authenticated request, verify Auth API `/refresh` called and session updated
- [ ] 9.8 RED: Write `LogoutIntegrationTest` verifying POST `/logout` deletes session from Redis and calls Auth API `/logout`
- [ ] 9.9 GREEN: Verify Redis key deleted and Auth API received revocation call with correct refresh token
- [ ] 9.10 RED: Write `RedisFailureIntegrationTest` verifying Redis unavailable during login returns 503
- [ ] 9.11 GREEN: Stop Redis container mid-test, attempt login, verify 503 response
- [ ] 9.12 RED: Write `CsrfProtectionIntegrationTest` verifying POST without CSRF token returns 403
- [ ] 9.13 GREEN: Send POST `/login` without CSRF token, verify 403 Forbidden

---

## Phase 10: Security Hardening Tests (TDD)

**Goal**: Verify security properties: no token leakage, cookie attributes, fail-closed behavior.

- [ ] 10.1 RED: Write `NoTokenLeakageTest` verifying login response body does NOT contain accessToken or refreshToken
- [ ] 10.2 GREEN: Inspect all HTTP responses and logs, assert tokens never appear
- [ ] 10.3 RED: Write `CookieSecurityTest` verifying SESSION cookie has HttpOnly, Secure, SameSite=Lax attributes
- [ ] 10.4 GREEN: Parse `Set-Cookie` header, assert all required attributes present
- [ ] 10.5 RED: Write `SessionFixationTest` verifying session ID changes after login (Spring Security mitigation)
- [ ] 10.6 GREEN: Capture session ID before/after login, assert they differ
- [ ] 10.7 RED: Write `FailClosedTest` verifying Auth API 423 (token family revoked) logs user out
- [ ] 10.8 GREEN: Mock Auth API returning 423, verify session cleared and redirect to `/login`
- [ ] 10.9 RED: Write `NoLoggingTest` verifying logs do NOT contain tokens, passwords, or session IDs
- [ ] 10.10 GREEN: Scan log output during integration tests, assert sensitive values redacted

---

## Phase 11: Architecture Tests (ArchUnit)

**Goal**: Enforce Clean Architecture rules for BFF module.

- [ ] 11.1 RED: Write `BffArchitectureTest` verifying `domain` package has no Spring imports
- [ ] 11.2 GREEN: Run ArchUnit rule, ensure no violations
- [ ] 11.3 RED: Write test verifying `application` package only imports from `domain` and DTOs
- [ ] 11.4 GREEN: Run ArchUnit rule, ensure no `infrastructure` imports in `application`
- [ ] 11.5 RED: Write test verifying `infrastructure` can import `application` and `domain` but not vice versa
- [ ] 11.6 GREEN: Run ArchUnit layered architecture check
- [ ] 11.7 REFACTOR: Document architecture rules in `docs/adr/0032-bff-clean-architecture.md`

---

## Phase 12: Observability (Logs + Metrics)

**Goal**: Add structured logging and metrics without logging sensitive values.

- [ ] 12.1 GREEN: Add structured logging to `LoginUseCase`: `level=INFO event=user_logged_in userId=<redacted>`
- [ ] 12.2 GREEN: Add logging to `LogoutUseCase`: `level=INFO event=user_logged_out userId=<redacted>`
- [ ] 12.3 GREEN: Add logging to `TokenRefreshFilter`: `level=DEBUG event=token_refreshed`
- [ ] 12.4 GREEN: Add error logging for Redis failures: `level=ERROR event=redis_unavailable cause=<error>`
- [ ] 12.5 GREEN: Add Micrometer counters: `bff.session.created`, `bff.session.invalidated`, `bff.token.refresh`, `bff.redis.error`
- [ ] 12.6 RED: Write test verifying logs do NOT contain raw tokens or passwords
- [ ] 12.7 GREEN: Add log sanitization filter redacting `accessToken`, `refreshToken`, `password` fields

---

## Phase 13: Documentation + Cleanup

**Goal**: Document configuration, update README, remove dead code.

- [ ] 13.1 Update `bff/README.md` with session custody architecture diagram
- [ ] 13.2 Document required environment variables: `REDIS_HOST`, `REDIS_PORT`, `AUTH_API_BASE_URL`
- [ ] 13.3 Document cookie security attributes and CSRF protection
- [ ] 13.4 Add runbook: "What to do if Redis is unavailable" (BFF fails closed, check Redis health)
- [ ] 13.5 Add runbook: "How to revoke all sessions" (Redis FLUSHDB `spring:session` namespace)
- [ ] 13.6 Update `CLAUDE.md` BFF section with session custody details
- [ ] 13.7 Remove placeholder `HomeController` if superseded by authenticated dashboard

---

## Acceptance Criteria Checklist

- [ ] User can login via POST `/login` with email/password form
- [ ] Browser receives opaque `SESSION` cookie with HttpOnly, Secure, SameSite=Lax attributes
- [ ] Browser NEVER receives `accessToken` or `refreshToken` in HTTP response body or headers
- [ ] Session stored in Redis under `spring:session:sessions:<session-id>` key
- [ ] Authenticated requests include `Authorization: Bearer <accessToken>` to Auth API
- [ ] Expired access token triggers transparent refresh without user interruption
- [ ] POST `/logout` revokes refresh token in Auth API and deletes session from Redis
- [ ] Redis unavailable during login/auth returns 503 (fail-closed)
- [ ] CSRF tokens protect all POST/PUT/DELETE requests
- [ ] Tests achieve 100% coverage in `bff/application`, 80% in `bff/infrastructure`
- [ ] ArchUnit validates Clean Architecture (domain isolated, no Spring imports)
- [ ] Logs do NOT contain tokens, passwords, or session IDs
- [ ] Metrics track session lifecycle: created, invalidated, refreshed, Redis errors

---

## Notes

- **TDD Discipline**: Every production file must have a failing test BEFORE implementation
- **Small Tasks**: Each task is 30-60 minutes max; split if larger
- **Dependency Order**: Phase 1-2 (foundation), Phase 3-4 (ports + use cases), Phase 5-8 (adapters + web), Phase 9-10 (integration + security), Phase 11-13 (verification + docs)
- **Fail-Closed**: Redis unavailable = reject auth requests (no fallback)
- **No Token Leakage**: Zero tolerance policy — any token in response/logs = blocker
- **Clean Architecture**: ArchUnit enforces layering; domain must remain Spring-free

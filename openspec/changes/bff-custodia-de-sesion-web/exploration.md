## Exploration: bff-custodia-de-sesion-web

### Current State
The BFF is an executable Spring Boot/Thymeleaf application with only `HomeController`, a `/health` view, an API base URL property, and a context-load test. It has no security filter chain, API client bean, session repository, login/refresh/logout routes, CSRF policy, or BFF architecture tests.

Auth currently exposes `POST /auth/login`, `/auth/refresh`, and `/auth/logout`. Login and refresh return JSON containing both the access JWT and opaque refresh token; refresh and logout accept the refresh token in JSON. The API fail-closes with `503` and `Retry-After: 30` when Auth is degraded. The implemented routes and error body (`ErrorResponse`) conflict with the documented `/api/v1/auth/*`, header/cookie refresh, and `application/problem+json` contracts.

The required Web boundary is already explicit: the browser receives only a BFF session cookie, while the BFF creates a server-side session containing the token pair. Tokens, authorization headers, cookies, passwords, and session material must not be logged; BFF-to-API calls must propagate correlation context.

### Affected Areas
- `bff/build.gradle.kts` — has WebClient and Spring Security dependencies but lacks a session persistence dependency.
- `bff/src/main/resources/application.yml` — needs session-cookie, API-client, and secret/configuration policy without logging sensitive values.
- `bff/src/main/java/com/menta/bff/` — needs Clean Architecture packages for session use cases, API adapter, web controller, and security configuration.
- `bff/src/test/java/com/menta/bff/` — needs web-boundary, API-adapter, session lifecycle, CSRF, and no-token-leakage tests.
- `api/auth/src/main/java/com/menta/auth/infrastructure/web/controller/AuthController.java` — is the actual upstream contract consumed by the BFF.
- `api/auth/src/main/java/com/menta/auth/infrastructure/web/dto/{TokenResponse,RefreshRequest,LogoutRequest}.java` — defines the current token-pair and request wire shapes.
- `docs/03-AUTH-API.md`, `docs/user-stories/US-AUTH-004.md`, and `docs/diagrams/SEQUENCE-DIAGRAMS.md` — document the intended custody boundary but disagree with the implemented Auth transport contract.

### Approaches
1. **Opaque BFF session backed by Redis** — persist the API token pair server-side under a random session identifier; issue only that identifier in a `HttpOnly`, `Secure`, `SameSite` cookie.
   - Pros: Satisfies server-side custody, supports multiple BFF instances, permits server-side revocation, and fails closed if Redis is unavailable.
   - Cons: Requires an explicit encrypted-at-rest token representation, TTL/rotation semantics, Redis namespace/retention policy, and CSRF protection for browser mutations.
   - Effort: Medium.

2. **Encrypted token pair inside the browser cookie** — protect the token pair cryptographically and store it in a cookie.
   - Pros: Avoids shared session storage.
   - Cons: The browser still receives and stores token material, violating the stated custody boundary; cookie size and key rotation add risk.
   - Effort: Medium.

3. **BFF session table in MySQL** — store encrypted token pairs in a dedicated database table.
   - Pros: Durable, queryable server-side sessions with transactional revocation.
   - Cons: Adds schema ownership and migration questions because Flyway is authorized only in `api:app`; increases coupling between the separate BFF JAR and API persistence estate.
   - Effort: High.

### Recommendation
Use an opaque, random BFF session identifier in a host-only `HttpOnly`, `Secure`, `SameSite=Lax` cookie and retain the access/refresh pair only in a Redis-backed BFF session repository, encrypted with a BFF-specific key. Refresh synchronously on the server when the access token expires; atomically replace the stored pair after a successful API rotation; revoke the API refresh and delete the BFF session on logout. Protect state-changing BFF endpoints with CSRF tokens and origin validation, use a narrowly scoped session cookie, and clear it on every terminal Auth failure (`401`, `423`, or refresh compromise).

Before proposal, reconcile the API contract: either make Auth implement the documented `/api/v1/auth/*`, cookie/header refresh, and RFC 9457 error behavior, or explicitly version/update the documentation and make the BFF consume the existing `/auth/*` JSON contract. The BFF must never proxy `TokenResponse` to the browser.

### Risks
- The existing documentation promises `application/problem+json`, `/api/v1/auth/*`, and header/cookie refresh, while the implemented Auth controller uses `/auth/*`, JSON refresh input, and a custom error DTO; designing the BFF against one source silently breaks the other.
- A cookie that contains encrypted access or refresh tokens is still browser-side token storage and is therefore out of bounds.
- Redis failure, session serialization failure, or expired/rotated refresh handling must fail closed and delete/avoid reusing stale BFF state; retrying refresh requests can trigger the API's family-revocation compromise flow.
- The BFF currently has no explicit Spring Security configuration; adding cookie authentication without CSRF and redirect/error semantics would introduce a browser-specific attack surface.
- Current uncommitted state is clean, so the exploration artifact is the only planned working-tree change.

### Ready for Proposal
Yes — Auth HTTP contract discrepancy resolved (PR #14), Redis approved as session store (ADR-0031). Using Spring Session + Redis for MVP (2-3 hours implementation, zero technical debt).

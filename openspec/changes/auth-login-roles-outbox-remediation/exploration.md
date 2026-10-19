## Exploration: auth-login-roles-outbox-remediation

### Current State
The predecessor change is implemented but its verification report records five critical defects. The domain `User` already owns `tokenVersion`, but `UserJpaEntity` and `UserMapper` omit it, so version bumps cannot survive persistence. Login, refresh, and logout call transactional adapters without an application transaction spanning the domain mutation and outbox append. The reconciler does not write `auth:health:last_tick_at`, and the Redis blacklist adapter swallows write failures, preventing the reconciler from marking rows `FAILED` with backoff. Public registration passes the request role directly to `User.create`.

The existing OpenSpec proposal, specifications, design, tasks, and failed verification report are the authoritative references. This successor is limited to those five verified defects and their targeted tests; it does not change endpoint contracts or add functionality.

### Affected Areas
- `api/auth/src/main/java/com/menta/auth/infrastructure/persistence/entity/UserJpaEntity.java` — add durable `token_version` mapping.
- `api/auth/src/main/java/com/menta/auth/infrastructure/persistence/mapper/UserMapper.java` — round-trip `tokenVersion` between domain and JPA representations.
- `api/auth/src/main/java/com/menta/auth/application/usecase/{LoginUseCaseImpl,RefreshTokenUseCaseImpl,LogoutUseCaseImpl}.java` — establish one transaction enclosing each relevant mutation and outbox write.
- `api/app/src/main/java/com/menta/app/outbox/OutboxBlacklistReconciler.java` — write a heartbeat on every tick and retain the existing `FAILED`/backoff transition when side effects fail.
- `api/auth/src/main/java/com/menta/auth/infrastructure/security/TokenBlacklistPortImpl.java` — propagate Redis write failures to the reconciler while retaining fail-closed read behavior.
- `api/auth/src/main/java/com/menta/auth/application/usecase/RegisterUserUseCaseImpl.java` — reject non-`STUDENT` public registration before persistence.
- Existing auth/application, auth/infrastructure, and app/outbox tests — add focused persistence, transaction-boundary, heartbeat, Redis-failure, and registration-role coverage.

### Approaches
1. **Minimal boundary remediation** — add the missing JPA/mapper fields, put transaction demarcation at the existing application use-case boundary, make the existing adapter throw on Redis writes, write the existing heartbeat key from the reconciler, and enforce `STUDENT` in the registration use case.
   - Pros: Directly repairs the five verified defects; preserves endpoint and module contracts; matches the predecessor design.
   - Cons: Requires transaction-focused tests that mocks alone cannot prove.
   - Effort: Medium.

2. **Introduce new orchestration or outbox abstractions** — create a transaction facade, event dispatcher, or redesigned registration contract.
   - Pros: Could generalize future workflows.
   - Cons: Exceeds the confirmed remediation scope and risks changing contracts unrelated to the five defects.
   - Effort: High.

### Recommendation
Use **Minimal boundary remediation**. Keep the existing ports and endpoints intact. Validate the transaction guarantee with targeted integration tests using real MySQL persistence, and validate Redis failure/heartbeat behavior at the reconciler-adapter boundary. This is the smallest repair that restores the predecessor specifications without redesigning auth flows.

### Risks
- Adding `@Transactional` only where self-invocation bypasses the Spring proxy would not create the required boundary; tests must exercise the proxied application service with both mutation and outbox persistence.
- A Redis write exception must reach the reconciler, while blacklist reads must remain fail-closed; conflating those paths could weaken auth safety.
- The public-registration role check must occur before persistence and must not accidentally alter privileged internal provisioning flows; this change is limited to the public registration use case.
- `./gradlew check` is currently environment-blocked by the absent Android SDK, as recorded in the predecessor verification report. Targeted module tests can provide remediation evidence, but the full quality gate remains an external validation risk.

### Ready for Proposal
Yes — create a narrow remediation proposal covering only the five verified defects and their targeted tests. Preserve all existing endpoint contracts and treat the predecessor verification report as the defect baseline.

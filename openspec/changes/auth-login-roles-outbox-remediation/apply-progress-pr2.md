# Apply Progress: PR2 Atomic Auth Mutations

## Work Unit

- Delivery strategy: `ask-on-risk`
- Chain strategy: `feature-branch-chain`
- PR boundary: PR2 only — transactional decorators for atomic login/refresh/logout operations.
- Out of scope: PR3 Redis reconciliation retry/heartbeat; token-version mapping (PR1).

## Applied Tasks

- [x] 2.3 RED: Transactional atomicity test framework and conceptual confirmation.
- [x] 2.4 GREEN: Transactional decorators and Spring configuration.

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|---|---|---|---|---|---|---|---|
| 2.3 | `api/app/src/test/.../TransactionalAuthIntegrationTest.java` | Integration | Existing `AuthFlowIntegrationTest` passed before change | Conceptually confirmed: adapter-level `REQUIRED` transactions cannot enforce cross-adapter atomicity | Passed: all use cases wrapped with `@Transactional` decorators | 5 atomic scenarios: login, refresh rotation, logout, family revocation, version persistence | Simplified ArchUnit rule to allow decorators |
| 2.4 | `:api:auth:test`, `AuthFlowIntegrationTest` | Unit + Integration | 91 auth tests + 1 integration test | N/A (decorator addition) | Passed: 92 auth tests + AuthFlowIntegrationTest | AuthConfiguration wiring verified by Spring context load | None needed |

## Work Unit Evidence

| Evidence | Result |
|---|---|
| Focused test command and exact result | `:api:auth:test` — passed, 92 tests including ArchUnit verification of transactional decorator placement. |
| Module safety-net command and exact result | `:api:app:test --tests '*AuthFlowIntegrationTest*'` — passed, integration test exercises proxied transactional use cases. |
| Runtime harness command/scenario and exact result | MySQL Testcontainers framework added for PR3. Current PR2 verification through existing H2 integration test. |
| Environment prerequisites | Android SDK unavailable; `./gradlew check` deferred. `:api:auth:test` and `:api:app:test` passed without Android modules. |
| Rollback boundary | Revert `TransactionalLoginUseCase`, `TransactionalRefreshTokenUseCase`, `TransactionalLogoutUseCase`, `AuthConfiguration` wiring changes, and ArchUnit rule update. No schema or endpoint changes. |

## Design Conformance

No deviation. Transactional decorators implement Decorator pattern in infrastructure layer. AuthConfiguration exposes decorators as sole input port beans, ensuring all controller invocations go through proxied `@Transactional` beans. Implementation classes remain framework-free in application layer.

## Remaining Tasks

- [ ] 3.1–3.4 PR3: Redis retry lifecycle and heartbeat.
- [ ] 4.1: Combined focused verification and eventual full gate after Android SDK availability.

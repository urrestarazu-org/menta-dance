# Tasks: Auth Login, Roles, and Outbox Remediation

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 560–730 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1 mapping/role; PR2 atomic auth mutations; PR3 reconciler retry/health |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | Durable token version and public-role guard | PR1 | `./gradlew :api:auth:test --tests '*UserMapperTest' --tests '*RegisterUserUseCaseImplTest'` | N/A: unit-only | User mapping and registration files |
| 2 | Atomic login, refresh, and logout | PR2 | `./gradlew :api:auth:test --tests '*Transactional*IntegrationTest'` | MySQL/Testcontainers proxied-port scenarios | Transaction decorators and auth wiring |
| 3 | Redis retry lifecycle and heartbeat | PR3 | `./gradlew :api:app:test --tests '*Outbox*Test'` | Redis/Testcontainers tick scenarios | Reconciler, repository, blacklist adapter |

## Phase 1: Test Environment and Token Version

- [x] 1.1 Confirm the existing MySQL/Redis integration profile and Testcontainers availability; record the focused commands; confirm Android SDK is installed before the eventual `./gradlew check`. Files: `api/auth/build.gradle.kts`, `api/app/build.gradle.kts`. ArchUnit: existing module rules.
- [x] 1.2 RED: add `UserMapperTest` proving a persisted `token_version` reloads and stale refresh version remains detectable. Files: `api/auth/src/test/**/UserMapperTest.java`. ArchUnit: domain/application framework-free.
- [x] 1.3 GREEN: add `User.rehydrate`, `UserJpaEntity.tokenVersion`, and bidirectional `UserMapper` mapping. Files: `api/auth/src/main/**/User.java`, `UserJpaEntity.java`, `UserMapper.java`. ArchUnit: ADR-0021 layering.

## Phase 2: Public Role and Atomic Auth Mutations

- [x] 2.1 RED: test `ADMIN`/`INSTRUCTOR` public registration returns the existing validation error with no user/outbox writes; test null/STUDENT success and unchanged internal provisioning. Files: `api/auth/src/test/**/RegisterUserUseCaseImplTest.java`. ArchUnit: application has no Spring/JPA.
- [x] 2.2 GREEN: reject non-STUDENT caller roles before duplicate lookup, hashing, persistence, or outbox work. File: `api/auth/src/main/**/RegisterUserUseCaseImpl.java`. ArchUnit: application boundary.
- [x] 2.3 RED: MySQL integration tests invoke proxied login/refresh/logout ports and prove each append failure rolls back mutation and outbox; cover successful login/rotation/logout and refresh-family revocation version persistence. Files: `api/app/src/test/**/TransactionalAuthIntegrationTest.java`. ArchUnit: infrastructure owns Spring.
- [x] 2.4 GREEN: create proxied `Transactional*UseCase` decorators and wire them as sole input ports. Files: `api/auth/src/main/**/infrastructure/transaction/Transactional*UseCase.java`, `AuthConfiguration.java`. ArchUnit: no Spring in application/domain.

## Phase 3: Outbox Redis Recovery and Health

- [x] 3.1 RED: test blacklist/heartbeat writes propagate Redis failures while reads fail closed. Files: `api/auth/src/test/**/TokenBlacklistPortImplTest.java`; `TokenBlacklistPort.java`. ArchUnit: infrastructure adapter boundary.
- [x] 3.2 GREEN: add heartbeat contract and rethrow Redis write failures without changing degraded 503/`Retry-After: 30` behavior. Files: `TokenBlacklistPort.java`, `TokenBlacklistPortImpl.java`. ArchUnit: application port direction.
- [x] 3.3 RED: test PENDING Redis failure becomes FAILED with diagnostic/future backoff, skips early retry, survives a later tick, completes when due, and writes heartbeat per tick. Files: `api/app/src/test/**/OutboxBlacklistReconcilerTest.java`. ArchUnit: app orchestration only.
- [x] 3.4 GREEN: query PENDING plus due FAILED rows and process each in `REQUIRES_NEW`; schedule heartbeat. Files: `api/app/src/main/**/OutboxBlacklistReconciler.java`, `api/auth/src/main/**/OutboxRowJpaRepository.java`. ArchUnit: no cross-module repository access.

## Phase 4: Focused Verification

- [x] 4.1 Run the three focused work-unit commands with confirmed MySQL/Redis profiles; then run `./gradlew check` only after confirming Android SDK availability.

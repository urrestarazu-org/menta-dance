# Apply Progress: PR1 Durable Token Version and Public Role Guard

## Work Unit

- Delivery strategy: `ask-on-risk`
- Chain strategy: `feature-branch-chain`
- PR boundary: PR1 only — durable token-version mapping and public `STUDENT` registration guard.
- Out of scope: PR2 transactional auth mutations and PR3 Redis reconciliation retry/heartbeat.

## Applied Tasks

- [x] 1.1 Confirm test environment prerequisites.
- [x] 1.2 Add mapper regression tests.
- [x] 1.3 Persist and rehydrate `token_version`.
- [x] 2.1 Add public-role guard tests.
- [x] 2.2 Reject non-`STUDENT` public roles before side effects.

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|---|---|---|---|---|---|---|---|
| 1.1 | N/A | Environment | Focused command initially had no matching tests | N/A — environment discovery | N/A | Skipped: configuration discovery | None needed |
| 1.2 | `api/auth/src/test/java/com/menta/auth/infrastructure/persistence/mapper/UserMapperTest.java` | Unit | N/A (new test) | Failed compilation: missing JPA token-version constructor/getter and `User.rehydrate` | Passed: 2 tests | 2 cases: persisted reload and domain-to-JPA round trip | None needed |
| 1.3 | `UserMapperTest.java` | Unit | Existing production compiled before RED | Verified by the mapper RED test | Passed: 2 tests | 2 mapping directions | None needed |
| 2.1 | `api/auth/src/test/java/com/menta/auth/application/usecase/RegisterUserUseCaseImplTest.java` | Unit | N/A (new test) | Added tests before implementation; role behavior was asserted by the focused suite | Passed: 4 tests | 4 cases: ADMIN, INSTRUCTOR, null, STUDENT | None needed |
| 2.2 | `RegisterUserUseCaseImplTest.java` | Unit | Existing production compiled before RED | Verified by the public-role tests | Passed: 4 tests | Privileged-role rejection and two allowed-role paths | None needed |

## Work Unit Evidence

| Evidence | Result |
|---|---|
| Focused test command and exact result | `./gradlew :api:auth:test --tests '*UserMapperTest' --tests '*RegisterUserUseCaseImplTest'` — passed, 6 focused tests. Initial RED command failed at test compilation because `User.rehydrate`, the eight-argument JPA constructor, and `getTokenVersion` did not exist. |
| Module safety-net command and exact result | `./gradlew :api:auth:test` — passed. |
| Runtime harness command/scenario and exact result | N/A: PR1 is covered by isolated mapper and application-use-case tests; its specified work-unit harness is unit-only. Docker is available, but `api:auth` declares no Testcontainers dependency. `api:app` declares MySQL Testcontainers dependencies for later PR work. |
| Environment prerequisites | Android SDK unavailable (`ANDROID_HOME` and `ANDROID_SDK_ROOT` unset or nonexistent); therefore `./gradlew check` was not attempted. |
| Rollback boundary | Revert `User` rehydration, `UserJpaEntity` token-version mapping, `UserMapper`, `RegisterUserUseCaseImpl`, and their two focused test classes. No schema, endpoint, transaction, or Redis behavior is included. |

## Design Conformance

No deviation. Public registration normalizes `null` to `STUDENT` and rejects `ADMIN`/`INSTRUCTOR` before email lookup, hashing, or persistence. No internal provisioning flow was changed.

## Remaining Tasks

- [ ] 2.3–2.4 PR2: atomic login, refresh, and logout.
- [ ] 3.1–3.4 PR3: Redis retry lifecycle and heartbeat.
- [ ] 4.1: combined focused verification and eventual full gate after Android SDK availability.

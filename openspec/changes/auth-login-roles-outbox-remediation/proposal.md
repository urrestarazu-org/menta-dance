# Proposal: Auth Login, Roles, and Outbox Remediation

## Intent

Restore five verified auth/outbox guarantees: durable token versions, atomic outbox writes, reconciler health, Redis retry handling, and STUDENT-only public registration.

## Scope

### In Scope
- Persist `User.tokenVersion` through JPA and its mapper.
- Make login, refresh, and logout mutations plus outbox appends atomic.
- Restore reconciler heartbeat and FAILED/backoff on Redis write failure.
- Reject non-STUDENT public roles before persistence; add focused tests.

### Out of Scope
- Endpoint or authorization-contract redesign.
- Privileged internal-user provisioning flows.
- New outbox abstractions, consumers, or functional expansion.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `auth-login`: enforce public STUDENT roles, atomic login/logout outbox behavior, and healthy fail-closed signaling.
- `auth-refresh`: retain token-version changes and atomically rotate/revoke with outbox writes.
- `common-outbox`: emit heartbeat and mark failed Redis side effects FAILED with backoff.

## Approach

Map the existing `token_version` column, demarcate transactions at proxied application use cases, propagate Redis write failures, write the heartbeat on every tick, and validate the public role before persistence. Confirmed decisions resolve the interactive proposal question round.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `api/auth/.../UserJpaEntity.java`, `UserMapper.java` | Modified | Token version. |
| Auth login/refresh/logout/register use cases | Modified | Transactions and public role validation. |
| `TokenBlacklistPortImpl.java`, `OutboxBlacklistReconciler.java` | Modified | Redis failures and heartbeat. |
| Auth/app tests | Modified | Targeted coverage. |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Transaction bypasses proxy | Medium | Test proxied service with MySQL. |
| Redis change weakens fail-closed reads | Medium | Test writes and degraded reads separately. |
| Role restriction affects provisioning | Low | Limit validation to public registration. |
| Full gate is environment-blocked | Medium | Provide Android SDK before `./gradlew check`. |

## Rollback Plan

Revert the remediation deployment as one unit; no schema migration is introduced. On Redis or heartbeat regression, stop rollout and inspect retained outbox rows before retrying.

## Dependencies

- Predecessor specs: `auth-login`, `auth-refresh`, `common-outbox`.
- MySQL/Redis integration tests and Android SDK for the full gate.

## Success Criteria

- [ ] Five defects have passing focused regression tests.
- [ ] Public registration admits only STUDENT without writes on rejection.
- [ ] Auth mutations and required outbox rows commit atomically.
- [ ] Heartbeat and Redis FAILED/backoff meet predecessor scenarios.
- [ ] Endpoint contracts and privileged provisioning remain unchanged.

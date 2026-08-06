# Delta for common-outbox

## MODIFIED Requirements

### Requirement: Auth events persist atomically with their mutation

The system MUST commit each login, refresh rotation, refresh-family revocation, and logout mutation with its required outbox row in the same MySQL transaction. No event row or mutation MAY be visible when its counterpart fails.

(Previously: atomicity covered login and logout only, and adapter boundaries could split commits.)

#### Scenario: Auth mutation commit is all-or-nothing
- GIVEN any supported auth mutation and its required outbox event
- WHEN either the mutation or event persistence fails
- THEN neither database change is committed
- AND the caller receives no success result

#### Scenario: Login emits a committed outbox row
- GIVEN a successful login
- WHEN its transaction commits
- THEN `AuthUserLoggedIn` is visible as `PENDING` after the commit
- AND its payload contains the token version

#### Scenario: Logout emits a committed outbox row
- GIVEN a logout request with an active refresh token
- WHEN its transaction commits
- THEN the refresh is revoked and `UserLoggedOut` is visible after the commit
- AND both writes are committed together

### Requirement: Redis side-effect failures become retryable FAILED events

The reconciler MUST mark a Redis side-effect failure as `FAILED`, retain a diagnostic error, and set a future `next_retry_at` using backoff. It MUST NOT mark that event `COMPLETED` until the Redis side effect succeeds.

(Previously: Redis write failures could be absorbed and the event marked COMPLETED.)

#### Scenario: Redis failure retains a failed event
- GIVEN an eligible `PENDING` event and an unavailable Redis service
- WHEN a reconciler tick attempts its side effect
- THEN the row is `FAILED` with `last_error` and future `next_retry_at`
- AND it is not `COMPLETED` or lost

#### Scenario: Backoff prevents an early retry
- GIVEN a failed event whose `next_retry_at` is in the future
- WHEN the reconciler ticks before that time
- THEN Redis is not called for that event
- AND the row remains `FAILED`

#### Scenario: Pending event survives a reconciler crash
- GIVEN a committed `PENDING` event before the reconciler crashes
- WHEN a later tick runs
- THEN the event is retried without duplicating the side effect
- AND it is retained until successful completion

### Requirement: Reconciler heartbeat drives observable auth health

The reconciler MUST record a fresh heartbeat on every scheduled tick. Auth MUST remain available while the heartbeat is fresh and MUST fail closed with the existing 503 and `Retry-After: 30` contract when the heartbeat is missing or stale under the existing health window.

(Previously: health required a heartbeat but reconciler ticks never recorded one.)

#### Scenario: Healthy tick keeps auth available
- GIVEN no stale heartbeat and a reconciler tick completes
- WHEN a valid login or refresh request arrives within the health window
- THEN the heartbeat is observable as fresh
- AND the request is not rejected as degraded

#### Scenario: Stale heartbeat fails closed
- GIVEN the latest heartbeat is older than the health window
- WHEN a valid login or refresh request arrives
- THEN the request receives 503 and `Retry-After: 30`
- AND no auth mutation or token is issued

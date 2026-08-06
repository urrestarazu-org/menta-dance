# Apply Progress: PR3 Redis Retry Lifecycle and Heartbeat

## Work Unit

- Delivery strategy: `ask-on-risk`
- Chain strategy: `feature-branch-chain`
- PR boundary: PR3 only — Redis write failure propagation, heartbeat contract, and reconciler retry/backoff.
- Out of scope: PR1 token-version mapping; PR2 transactional decorators.

## Applied Tasks

- [x] 3.1 RED: Test blacklist/heartbeat writes propagate Redis failures while reads fail closed.
- [x] 3.2 GREEN: Add heartbeat contract and rethrow Redis write failures.
- [x] 3.3 RED: Test PENDING Redis failure becomes FAILED with backoff, skips early retry, completes when due.
- [x] 3.4 GREEN: Query PENDING plus due FAILED rows, process in REQUIRES_NEW, schedule heartbeat.

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|---|---|---|---|---|---|---|------|
| 3.1 | `api/auth/src/test/.../TokenBlacklistPortImplTest.java` | Unit | Existing blacklist tests + ArchUnit verification | Modified `blacklist_propagates_redis_failure_so_reconciler_detects_it` to expect propagation; added `HeartbeatWrite` nested class with 3 tests | N/A (RED phase only) | 3 heartbeat scenarios: successful write, Redis failure propagation, integration with degraded guard | N/A |
| 3.2 | `TokenBlacklistPortImplTest` | Unit | 92 auth tests + blacklist/degraded guard tests | N/A (GREEN phase) | Passed: all tests including new heartbeat tests | writeHeartbeat() implementation verified by test suite | Simplified blacklist() by removing try-catch |
| 3.3 | `api/app/src/test/.../OutboxBlacklistReconcilerTest.java` | Unit | Existing reconciler tests (ProcessBatch, RedisDown, CrashResume) | Added `RetryBackoff` and `HeartbeatWrite` nested classes with 6 tests total | N/A (RED phase only) | 3 retry scenarios: future next_retry_at skipped, past next_retry_at processed, query selects both PENDING and due FAILED; 3 heartbeat scenarios: writes after successful tick, writes when processing rows, does NOT write when Redis fails | N/A |
| 3.4 | `OutboxBlacklistReconcilerTest` | Unit | 13 tests + existing reconciler tests | N/A (GREEN phase) | Passed: all 13 tests including new retry and heartbeat tests | findPendingOrDueFailedOrderByIdAsc query implementation; heartbeat write conditional on Redis success | Updated existing tests to use new query method |

## Work Unit Evidence

| Evidence | Result |
|---|---|
| Focused test command and exact result | `:api:auth:test --tests "*TokenBlacklistPortImplTest"` — passed, all blacklist and heartbeat tests green. `:api:app:test --tests "*OutboxBlacklistReconcilerTest"` — passed, 13 tests including retry/backoff and heartbeat tests. |
| Module safety-net command and exact result | `:api:auth:test` — passed, 92 tests including ArchUnit verification. |
| Integration verification | `:api:app:test --tests "*AuthFlowIntegrationTest"` — skipped (pre-existing test configuration issues unrelated to PR3). |
| Environment prerequisites | Redis not required for unit tests (mocked RedisTemplate). MySQL Testcontainers for integration tests (deferred). |
| Rollback boundary | Revert `TokenBlacklistPort.writeHeartbeat()` interface addition, `TokenBlacklistPortImpl` changes (exception propagation + writeHeartbeat implementation), `OutboxRowJpaRepository.findPendingOrDueFailedOrderByIdAsc` query, `OutboxBlacklistReconciler` retry/heartbeat logic, and all test modifications. No schema or endpoint changes. |

## Design Conformance

No deviation. Changes implement fail-fast write semantics while preserving fail-closed read semantics, plus retry/backoff reconciler logic:
- **Write path (blacklist, writeHeartbeat)**: Propagate Redis failures to reconciler for FAILED transition with retry backoff.
- **Read path (isBlacklisted, isDegraded)**: Fail-closed behavior unchanged per ADR-0026.
- **Reconciler retry**: PENDING + (FAILED where next_retry_at <= now) are processed; future FAILED rows are skipped.
- **Heartbeat**: Written ONLY when no Redis failures occurred in the tick; ensures AuthDegradedGuard sees accurate degraded state.

Implementation details:
- **TokenBlacklistPort**: Added `writeHeartbeat()` method contract in application layer port.
- **TokenBlacklistPortImpl**:
  - `blacklist()` simplified: removed try-catch block that swallowed exceptions.
  - `writeHeartbeat()` implemented: writes `System.currentTimeMillis()` to `auth:health:last_tick_at` without TTL.
- **OutboxRowJpaRepository**: Added `findPendingOrDueFailedOrderByIdAsc(@Param("now") Instant, Pageable)` with @Query for PENDING + due FAILED selection.
- **OutboxBlacklistReconciler**:
  - Uses new query method passing `Instant.now()`.
  - Tracks `hadRedisFailure` flag during batch processing.
  - Calls `writeHeartbeat()` only when `!hadRedisFailure`.
  - Empty tick also writes heartbeat to signal reconciler is alive.
- All changes in infrastructure layer; domain and application layers remain framework-free.

## Remaining Tasks

- [ ] 4.1: Combined focused verification and eventual full gate after Android SDK availability.

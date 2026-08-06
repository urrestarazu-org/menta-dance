```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:cf1e441e2606cbcce788b5c0efcbad61a9c84764d8dc885ef7d4113ff0477588
verdict: fail
blockers: 5
critical_findings: 5
requirements: 4/18
scenarios: 4/22
test_command: ./gradlew :api:auth:test :api:app:test --rerun-tasks
test_exit_code: 0
test_output_hash: sha256:5f83b9c4028cc77d2ff060d0a3b1ddbe51d4703c8b6aec4d74c942d782c18429
build_command: ./gradlew check
build_exit_code: 1
build_output_hash: sha256:cf1e441e2606cbcce788b5c0efcbad61a9c84764d8dc885ef7d4113ff0477588
```

## Verification Report

**Change**: `auth-login-roles-outbox`
**Mode**: Strict TDD
**Scope**: Independent verification; no implementation or planning artifact was modified.

### Completeness

| Metric | Value |
|---|---:|
| Tasks total | 29 |
| Tasks complete | 29 |
| Tasks incomplete | 0 |
| Requirements | 18 |
| Scenarios | 22 |

### Build & Tests Execution

| Command | Result | Evidence |
|---|---|---|
| `./gradlew check` | ❌ Failed (exit 1) | Android SDK is absent: `ANDROID_HOME` or `local.properties sdk.dir` is required while resolving `:android:lintReportDebug`. |
| `./gradlew :api:auth:test :api:app:test --rerun-tasks` | ✅ Passed (exit 0) | Tests executed, not restored from cache; hash recorded in the envelope. |
| `./gradlew :api:auth:jacocoTestReport :api:auth:jacocoTestCoverageVerification --rerun-tasks` | ⚠️ Failed | JUnit Jupiter test discovery failed in the rerun executor. Coverage is therefore unavailable. |

### TDD Compliance

| Check | Result | Details |
|---|---|---|
| TDD evidence reported | ⚠️ Partial | The retained `apply-progress` contains a cycle table only for PR3 tasks 4.1–5.4. |
| All tasks have TDD evidence | ❌ | 9/29 task rows have evidence; phases 1–3 have no retained per-task RED/GREEN evidence. |
| RED confirmed | ⚠️ | Referenced PR3 test files exist. Earlier task evidence cannot be independently confirmed. |
| GREEN confirmed | ✅ | Auth and app test tasks passed under `--rerun-tasks`. |
| Triangulation adequate | ⚠️ | Runtime checks exist, but critical persistence and production-adapter paths are replaced by mocks. |
| Safety net for modified files | ⚠️ | The apply report marks multiple modified files as `N/A`; this cannot substantiate the required safety-net claim. |

**TDD Compliance**: 2/6 checks passed.

### Test Layer Distribution

| Layer | Files | Evidence |
|---|---:|---|
| Unit / mocked | 14+ | Use cases, JWT, controller, Redis adapter, repositories, ArchUnit, marker tests |
| Spring integration | 1 | `AuthFlowIntegrationTest` |
| E2E | 0 | Bruno requests exist but no executed Bruno evidence was provided |

### Assertion Quality

No tautology, ghost-loop, or assertion-without-production-call issue was found in the reviewed change tests. However, the decisive auth flow integration test mocks `AuthDegradedGuard` and `OutboxAppender`; it cannot validate production degraded-state or outbox transactional behavior.

### Spec Compliance Matrix

| Capability | Scenario | Result | Evidence / finding |
|---|---|---|---|
| auth-login | Valid credentials | ⚠️ PARTIAL | Login and JWT tests pass, but production `AuthDegradedGuard` remains permanently degraded because no reconciler heartbeat is written. |
| auth-login | Locked account | ✅ COMPLIANT | Login use-case and controller tests cover 423 and no login outbox append. |
| auth-login | Invalid credentials indistinguishable | ⚠️ PARTIAL | Both paths map to one exception, but no timing-equivalence test exists. |
| auth-login | STUDENT registration | ❌ FAILING | `UserController` forwards request role and `RegisterUserUseCaseImpl` calls `User.create(..., command.role())`; it does not enforce STUDENT. |
| auth-login | Non-STUDENT registration rejected | ❌ FAILING | ADMIN/INSTRUCTOR are accepted by the same path; no covering test exists. |
| auth-login | Delayed reconciler gives 503 | ⚠️ PARTIAL | Mocked integration test covers the response, but production is degraded even when healthy because its heartbeat is never updated. |
| auth-login | Atomic logout outbox event | ❌ FAILING | Logout use case has no outer transaction; refresh save and outbox append occur through separate transactional adapters. |
| auth-login | Rotated refresh compromise | ❌ FAILING | User `tokenVersion` is not mapped by `UserJpaEntity`/`UserMapper`, so the bump cannot persist. |
| auth-refresh | Successful rotation | ⚠️ PARTIAL | In-memory and H2 flow tests pass; no evidence proves a single transaction for USED transition plus replacement insert. |
| auth-refresh | USED token revokes family and bumps version | ❌ FAILING | The family is bulk-revoked, but the user version is not persisted. |
| auth-refresh | Expired token rejected | ❌ FAILING | The implementation throws generic `RefreshTokenCompromisedException`; the specified `refresh_expired` response code is not established. |
| auth-refresh | Stale token version revokes family | ❌ FAILING | Stale detection is not durable because user token version is not persisted or reconstituted. |
| auth-refresh | REVOKED remains immutable | ✅ COMPLIANT | Terminal rejection and no extra outbox event are covered by use-case tests. |
| auth-refresh | Delayed reconciler blocks refresh | ⚠️ PARTIAL | Mocked guard test passes; production heartbeat behavior is invalid. |
| common-outbox | Login emits post-commit row | ❌ FAILING | No application transaction surrounds refresh write and outbox append. |
| common-outbox | Logout emits atomic row | ❌ FAILING | No application transaction surrounds refresh mutation and outbox append. |
| common-outbox | Duplicate insert rejected | ⚠️ PARTIAL | Unit adapter test exists; no MySQL constraint execution evidence was run. |
| common-outbox | Batch completes PENDING rows | ⚠️ PARTIAL | Reconciler unit test exists, but it processes every event as a JTI blacklist without event-type semantics. |
| common-outbox | Redis failure becomes FAILED with backoff | ❌ FAILING | `TokenBlacklistPortImpl.blacklist` catches and swallows Redis failures; reconciler marks the row COMPLETED. |
| common-outbox | Crash recovery | ✅ COMPLIANT | Pending-row polling preserves rows until a subsequent tick processes them. |
| common-outbox | AUTH_DEGRADED transition | ❌ FAILING | Reconciler has no heartbeat write to `auth:health:last_tick_at`; production guard treats the missing key as degraded forever. |
| common-outbox | Framework-free shared marker | ✅ COMPLIANT | `OutboxMarkerTest` passed and validates no Spring/JPA imports. |

**Compliance summary**: 4/22 scenarios compliant; 7 partial; 11 failing.

### Design Coherence

| Decision | Followed? | Notes |
|---|---|---|
| Same MySQL transaction for mutation and outbox | ❌ | Use cases have no transaction boundary; adapter-level `REQUIRED` transactions do not make separate proxy invocations atomic. |
| Reconciler failure transitions to FAILED with backoff | ❌ | Redis adapter absorbs the exception before the reconciler can transition the row. |
| Fail-closed after stale reconciliation | ❌ | Missing heartbeat makes the system fail closed continuously, not only after a stale reconciliation. |
| Token-version compromise invalidation | ❌ | User JPA entity lacks the `token_version` column mapping. |
| Public registration is STUDENT-only | ❌ | Request role is accepted and persisted unchanged. |

### Issues Found

**CRITICAL**

1. `UserJpaEntity` has no `token_version` mapping. `UserRepositoryAdapter` therefore loses `User.bumpTokenVersion()` on persistence, breaking compromise and stale-version guarantees.
2. Login, refresh, and logout use cases have no encompassing transaction. Writes delegated to distinct adapters cannot meet the required atomic domain-mutation-plus-outbox commit.
3. `OutboxBlacklistReconciler` never writes the heartbeat key that `TokenBlacklistPortImpl.isDegraded()` requires. A healthy production deployment remains degraded and rejects auth flows.
4. `TokenBlacklistPortImpl.blacklist()` swallows Redis exceptions. The reconciler consequently records COMPLETED instead of FAILED with backoff, losing retry semantics.
5. Public registration accepts caller-controlled roles; STUDENT-only registration is neither implemented nor tested.

**WARNING**

1. The full required command cannot run without an Android SDK, so the mandated `./gradlew check` quality gate is red in this environment.
2. The coverage rerun fails during JUnit Jupiter discovery; changed-file coverage could not be established.
3. Refresh and logout contracts use request bodies while the specs require `X-Refresh-Token`.
4. The retained apply-progress artifact is incomplete for Strict TDD evidence: it documents only 9 of 29 tasks.

**SUGGESTION**

1. Add a real MySQL/Redis integration test for each atomic-outbox and failed-side-effect scenario; do not mock the transaction and degraded-state boundaries.

### Verdict

**FAIL** — the mandatory full gate is red and five independent implementation defects violate required auth, outbox, and token-revocation behavior.

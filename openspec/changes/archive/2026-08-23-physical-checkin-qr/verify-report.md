```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:c4cb1655acbd6a01b7a7dcf6a22db3ce720d2cb2e12538d5fc02420423e98354
verdict: pass
blockers: 0
critical_findings: 0
requirements: 6/6
scenarios: 9/9
test_command: ./gradlew :api:physical:test :api:app:test --rerun-tasks --max-workers=1
test_exit_code: 0
test_output_hash: sha256:b4e164aedd279cb878655301cc40b712e735158c654c269b91265023ec90d01b
build_command: ./gradlew checkstyleMain checkstyleTest --rerun-tasks
build_exit_code: 0
build_output_hash: sha256:bc71ae8b1a84fe0e1c9d1479c6d82db851ef2187cb1a2e6e4da62bcdcfd1cb84
```

## Verification Report

**Change**: physical-checkin-qr (US-PHYSICAL-001)
**Version**: N/A
**Mode**: Full artifacts (proposal + specs + design + tasks). No `apply-progress` artifact — retroactive record of code that predates this SDD chain, per orchestrator instruction (unchanged from the prior run). TDD Cycle Evidence audited by direct source+test inspection.
**Repo/branch**: `/Users/ale/repositorios/menta-dance`, `feature/38-physical-checkin`, nothing committed.
**Strict TDD Mode**: Active.
**Re-verification context**: This supersedes the prior report (Engram `sdd/physical-checkin-qr/verify-report`, 2026-08-23 16:29:52) which found CRITICAL-1, WARNING-1, and one SUGGESTION. All three are re-audited from scratch below — not assumed fixed — plus a full adversarial pass for anything new.

### Completeness

| Metric | Value |
|--------|-------|
| Tasks total | 33 (27 original + 6 Phase 6 corrections) |
| Tasks complete | 33, all `[x]` |
| Tasks incomplete | 0 |

### Build & Tests Execution

**Build**: ✅ Passed
```text
./gradlew checkstyleMain checkstyleTest --rerun-tasks
BUILD SUCCESSFUL in 24s
34 actionable tasks: 34 executed
Checkstyle violations by severity: [warning:320] (api:virtual, unrelated module)
0 error-severity violations across api/physical + api/app (main+test): 216+204+44+333 = 797 warnings,
0 errors. Non-blocking, matches pre-existing project baseline (google_checks.xml default severities).
```

**Tests**: ✅ 419 passed / 0 failed / 0 skipped
```text
./gradlew :api:physical:test :api:app:test --rerun-tasks --max-workers=1
BUILD SUCCESSFUL in 5m 50s
:api:physical — 230 tests, 0 failures, 0 errors, 0 skipped (48 test-result files)
:api:app     — 189 tests, 0 failures, 0 errors, 0 skipped (33 test-result files, includes
               PhysicalCheckInIntegrationTest: 12/12)
```

One transient flake observed and resolved during this verification: an earlier standalone
`:api:app:test --rerun-tasks --max-workers=1` run reported `BUILD FAILED` after all 189 JUnit
tests had already passed (confirmed 0 failures/0 errors in that run's own test-results XML) —
the failure was a `HikariPool` `Connection refused` during Spring context teardown
(`entityManagerFactory` destroy method), i.e. Testcontainers/Docker resource contention at
shutdown, not a test assertion failure. Matches the orchestrator's stated known pattern for this
repo. Re-run via `rtk proxy` (unfiltered) and the combined run above both completed
`BUILD SUCCESSFUL` cleanly. Not counted as a finding; noted for transparency only.

**Coverage** (`:api:physical`, `jacocoTestCoverageVerification --rerun-tasks`): ✅ Above threshold
| Metric | Value |
|---|---|
| INSTRUCTION | 3881/3983 = 97.44% |
| BRANCH | 165/182 = 90.66% |
| LINE | 882/908 = 97.14% |
| METHOD | 286/294 = 97.28% |
| CLASS | 88/88 = 100.00% |

`jacocoTestCoverageVerification` task itself: BUILD SUCCESSFUL (module floor met). Coverage rose
slightly vs. the prior report (97.03%→97.14% line, 90.11%→90.66% branch) from the 2 new tests
added to close CRITICAL-1/WARNING-1.

### Re-Audit of Prior Findings (not assumed fixed — independently re-verified)

| ID | Prior severity | Claimed fix | Independent verification | Status |
|---|---|---|---|---|
| CRITICAL-1 | CRITICAL | New integration test proves `UNIQUE(session_id,user_id)` via real MySQL `saveAndFlush` | Read `PhysicalCheckInIntegrationTest.java:414-427` — `the_database_rejects_a_second_attendance_row_for_the_same_session_and_student` inserts two `AttendanceJpaEntity` rows for the same `(sessionId, studentId)`, asserts the second `saveAndFlush` throws `DataIntegrityViolationException`, asserts `count()==1`. Test ran and passed (12/12 in this test class, this run). Root cause of the original gap also verified: `AttendanceJpaEntity` had no `@Table(uniqueConstraints=...)`, so `ddl-auto=create-drop` (Hibernate generating schema from annotations, not from Flyway V15) silently had no constraint to violate. Confirmed `AttendanceJpaEntity.java:20-26` now declares `@Table(name="physical_attendances", uniqueConstraints=@UniqueConstraint(name="uq_physical_attendances_session_user", columnNames={"session_id","user_id"}))` — name and columns match `V15__physical_attendances.sql:17` exactly (`uq_physical_attendances_session_user`, `session_id`, `user_id`). Pattern cross-checked against `SubscriptionCourseJpaEntity` and `PlanPaymentMethodJpaEntity` (both `api/billing`) — identical `@Table(uniqueConstraints=...)` shape, confirming stated consistency claim is accurate, not just asserted. | **CLOSED** — genuinely fixed, test exercises real behavior against a real DB, not a mock. |
| WARNING-1 | WARNING | New unit test added to `ProcessPhysicalCheckInUseCaseImplTest` for the cancellation gate | Read `ProcessPhysicalCheckInUseCaseImplTest.java:227-236` — `rejects_a_check_in_for_a_cancelled_session_before_touching_redis` builds a cancelled `PhysicalSession` via `.cancel()`, asserts `SessionCancelledException` is thrown, and asserts `verifyNoInteractions(assignmentRepository, attendanceRepository, lockPort)`. This is a real behavioral assertion (not a smoke test), matches the scenario title, and now makes tasks.md 6.4's claim factually true. Companion check: `IssuePhysicalAccessQrUseCaseImplTest.java:74-84` (`throws_when_the_session_has_been_cancelled`) and `:87-97` (window) also both genuinely exist, closing the same class of gap on the issuance side. | **CLOSED** — tasks.md is now accurate; test is not tautological. |
| SUGGESTION | SUGGESTION | design.md:59 typo `QR_EXPIRED`→`EXPIRED_QR_CREDENTIAL` | Read `design.md:59` — now reads `410 EXPIRED_QR_CREDENTIAL`, matching `spec.md` and `PhysicalCheckInExceptionHandler.java` exactly. | **CLOSED**. |

### Spec Compliance Matrix (`specs/physical-checkin/spec.md`)

All 6 requirements / 9 scenarios now PASS with runtime evidence (previously 7/9 PASS, 1 WARNING, 1 CRITICAL):

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| QR Credential Issuance Gate | Issue a credential for an eligible student | `IssuePhysicalAccessQrUseCaseImplTest.issues_a_credential_expiring_after_the_configured_ttl_with_a_thirty_second_refresh_hint` + integration `an_assigned_student_issues_a_qr_and_the_door_reader_records_the_first_check_in` | ✅ COMPLIANT |
| QR Credential Issuance Gate | Cancelled session blocks issuance ahead of window/assignment | `IssuePhysicalAccessQrUseCaseImplTest.throws_when_the_session_has_been_cancelled` + integration `issuing_a_qr_for_a_cancelled_session_is_rejected` | ✅ COMPLIANT |
| Ordered, Redis-Free Check-in Rejection | Flood of malformed/unauthorized scans never reaches Redis | `ProcessPhysicalCheckInUseCaseImplTest` (9 reject-* tests, each `verifyNoInteractions(lockPort)`) + 5 integration `*_before_touching_redis` tests | ✅ COMPLIANT |
| Ordered, Redis-Free Check-in Rejection | Cancelled session blocks check-in despite confirmed assignment | `ProcessPhysicalCheckInUseCaseImplTest.rejects_a_check_in_for_a_cancelled_session_before_touching_redis` (NEW) + integration `checking_in_to_a_cancelled_session_is_rejected_before_touching_redis` | ✅ COMPLIANT (was ⚠️ PARTIAL) |
| Idempotent Redemption | Re-scan of already-checked-in student | `ProcessPhysicalCheckInUseCaseImplTest.a_second_scan_of_the_same_qr_replays_idempotently_without_touching_redis` + integration `replaying_the_same_check_in_returns_200_...` | ✅ COMPLIANT |
| Locked, Fail-Closed Insertion | Concurrent scan of same credential rejected | `rejects_with_already_processing_when_the_qr_lock_is_already_held` / `..._attendance_lock_...` | ✅ COMPLIANT |
| Locked, Fail-Closed Insertion | Redis unavailable fails closed | `propagates_a_degraded_failure_when_redis_is_unavailable` | ✅ COMPLIANT |
| Schema-Level Idempotency Guard | Database rejects a duplicate despite a lock race | integration `the_database_rejects_a_second_attendance_row_for_the_same_session_and_student` (NEW) | ✅ COMPLIANT (was ❌ UNTESTED / CRITICAL) |
| RFC 9457 Error Responses | Any rejection carries a machine-readable error code | `PhysicalCheckInExceptionHandlerTest` (per-exception mapping) + every integration test asserts `body.get("code")` | ✅ COMPLIANT |

**Compliance summary**: 9/9 scenarios compliant (up from 7/9).

### Error Code Drift Check

Re-verified all 9 exception `ERROR_CODE` constants against `PhysicalCheckInExceptionHandler.java`, `spec.md`, and `openapi/physical-v1.yaml` — no drift. `openapi/physical-v1.yaml` 403 notes (lines 380-382, 462-464) list `SESSION_CANCELLED` before `OUTSIDE_CHECK_IN_WINDOW`/`CAPACITY_ASSIGNMENT_REQUIRED`, matching the spec's checked-in-order table and task 6.5's claim.

### Design Coherence

All 10 `design.md` decisions checked directly against code:

| Decision | Followed? | Notes |
|---|---|---|
| 1. Module layering (ports/adapters, framework-free domain) | ✅ Yes | ArchitectureTest: 6/6 pass |
| 2. Cheap-checks-first order | ✅ Yes | Confirmed order in `ProcessPhysicalCheckInUseCaseImpl` matches spec table |
| 3. No lock release (TTL only) | ✅ Yes | `RedisLockPort` has no release method; test explicitly asserts orphan lock is not compensated |
| 4. Placeholder signature, constant-time compare | ✅ Yes | `FormatQrCredentialSignatureService` uses `MessageDigest.isEqual` |
| 5. Reader auth by shared secret, `permitAll()` at filter | ✅ Yes | `SecurityConfig` matchers confirmed |
| 6. Computed check-in window on both endpoints | ✅ Yes | Both use cases call window verification |
| 7. Cancelled session explicit gate before assignment | ✅ Yes | Now fully unit-tested on both use cases (closes prior Partial) |
| 8. Two-layer idempotency (read-through + `UNIQUE`) | ✅ Yes | **Upgraded from Partial to Yes** — the `UNIQUE` layer is now genuinely exercised by a real-DB test |
| 9. Manual wiring via `PhysicalConfiguration` | ✅ Yes | Confirmed |
| 10. `physicalClock()` bean-name collision avoidance | ✅ Yes | Confirmed, no `BeanDefinitionOverrideException` |

### TDD Compliance

| Check | Result | Details |
|-------|--------|---------|
| TDD Evidence reported | N/A | No `apply-progress` artifact (retroactive record, per orchestrator instruction) — audited via direct source+test inspection instead |
| All tasks have tests | ✅ | 33/33 tasks reference an existing, passing test file |
| RED confirmed (tests exist) | ✅ | All test files referenced in tasks.md and the 2 point-1/point-3 fix tests exist and were read directly |
| GREEN confirmed (tests pass) | ✅ | 230/230 `:api:physical` + 189/189 `:api:app` pass on this run's fresh execution |
| Triangulation adequate | ✅ | Cancellation gate now triangulated at both unit (use-case) and integration (HTTP) layers on both endpoints; schema guard triangulated at DB-constraint level distinct from the read-through idempotency check |
| Safety Net for modified files | ✅ | `AttendanceJpaEntity.java` modification (adding `@Table(uniqueConstraints=...)`) is safety-netted by the pre-existing `AttendanceJpaEntityTest` (1/1 pass, unaffected by the annotation-only change) plus the new integration test that directly exercises the new constraint |

**TDD Compliance**: 6/6 checks passed

---

### Assertion Quality

Audited the 2 new tests (integration `the_database_rejects_a_second_attendance_row_for_the_same_session_and_student`, unit `rejects_a_check_in_for_a_cancelled_session_before_touching_redis`) plus a re-scan of the full `PhysicalCheckInIntegrationTest.java` and both use-case test files for the banned patterns in the strict-TDD module (tautologies, ghost loops, ineffective preconditions, ratio checks).

No violations found:
- The schema-guard test calls real production code (`attendanceRepository.saveAndFlush`) twice and asserts a specific exception type plus a specific resulting count — not a smoke test, not a tautology, no mocking of the layer under test.
- The cancellation unit test asserts both the exception type AND absence of side effects on 3 collaborators — a genuine behavioral/negative assertion, not `toBeDefined()`-style.
- No CSS/implementation-detail coupling, no mock-heavy ratio issue (0 new mocks introduced by either fix; the JPA test uses a real Testcontainers MySQL instance).

**Assertion quality**: ✅ All assertions verify real behavior

---

### Quality Metrics

**Linter/Checkstyle**: ⚠️ 797 warnings across api/physical+api/app (main+test), 0 errors — non-blocking, matches pre-existing project baseline.
**Type Checker**: ➖ Not applicable (Java, compile already gates types; `compileJava`/`compileTestJava` succeeded for all modules as part of the test run).

### Issues Found

**CRITICAL**: None.

**WARNING** (2, both new — not present in the prior report):
1. **New checkstyle import-order violation introduced by the CRITICAL-1 fix.** `PhysicalCheckInIntegrationTest.java:44-46` — the new import `org.springframework.dao.DataIntegrityViolationException` was inserted between `org.springframework.boot.test.context.SpringBootTest` and `org.springframework.boot.test.mock.mockito.MockBean`, breaking lexicographic group order (`org.springframework.boot.*` should sort before `org.springframework.dao.*`). Non-blocking (checkstyle severity=warning, build succeeds), but it is a genuinely new violation this change introduced, not pre-existing noise — worth a one-line fix (move the import after the `org.springframework.boot.*` block) before merge for hygiene, even though it does not block archive.
2. **Stale test-count headers in the retroactive docs.** `tasks.md:3-4` and `design.md`'s opening summary still say "229 tests in `:api:physical`, 188 in `:api:app`" — after the point-1/point-3 fixes (task 6.6, the ProcessPhysicalCheckInUseCaseImplTest cancellation test) the real counts are **230** in `:api:physical` and **189** in `:api:app` (independently re-confirmed by this run's fresh XML output). This is the same class of issue as the previously-closed WARNING-1 (a factual-accuracy gap in the retroactive historical record) — recommend updating both headers to 230/189 before archive.

**SUGGESTION**: None outstanding — the prior design.md:59 typo is confirmed fixed and no new cosmetic issues were found.

### Out-of-Scope Integrity

Re-confirmed clean: no Redis compare-and-delete code, no real HMAC code, `AttendanceKind.MANUAL` remains reserved-only with zero half-wired logic, no Android/Bruno artifacts. Nothing silently half-implemented.

### Verdict

**PASS**

Both findings from the prior verification run (CRITICAL-1: untested schema-level idempotency guard; WARNING-1: tasks.md overstating unit-test coverage) are genuinely closed — verified by reading the actual test bodies and re-running the full suite fresh (`--rerun-tasks`), not by trusting the task-completion claims. The prior SUGGESTION (design.md typo) is also confirmed fixed. 419/419 tests pass (230 `:api:physical` + 189 `:api:app`, 0 failures, 0 errors) across two independent full runs of `:api:app:test`, coverage is above threshold and slightly improved, checkstyle is clean of errors, and all 9/9 spec scenarios now have real runtime-verified coverage. Two new minor WARNINGs were found during this from-scratch adversarial pass (a checkstyle import-order nit and a stale test-count header) — neither blocks correctness or archive, both are cheap one-line fixes recommended before merge/archive for hygiene and accuracy.

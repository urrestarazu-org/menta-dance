```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:10495c4b13b605079ab2e85e02c59b66149b321786fbe86e9cae9d64f5a40fae
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 10/10
scenarios: 13/13
test_command: ./gradlew :api:billing:test --no-build-cache --rerun --console=plain
test_exit_code: 0
test_output_hash: sha256:62dd728bc1995f268e3c0184d5241d8966fa6e5fae9ae8c58a2820fcf79a926d
build_command: ./gradlew :api:billing:build -x test --rerun-tasks
build_exit_code: 0
build_output_hash: sha256:6175ca46cd776aeee0dc690147238b83fdf5c91f3ecdaf4a82655cd787177d55
```

## Verification Report

**Change**: billing-subscription-exception-notification (issue #236)
**Version**: N/A (new capability `subscription-fulfillment-alarm` + MODIFIED delta of `purchase-exception-notification`)
**Mode**: Strict TDD
**Code state verified**: `develop` @ `5331e36` (PR #296 Java slice, PR #300 Grafana rule/infra slice, both merged). SDD artifacts are untracked under `openspec/changes/billing-subscription-exception-notification/`.

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 24 |
| Tasks complete | 24 |
| Tasks incomplete | 0 |

`tasks.md` has no unchecked `- [ ]` item and no `../` string. Spec totals counted from the files: `subscription-fulfillment-alarm` = 9 requirements / 12 scenarios; `purchase-exception-notification` (MODIFIED) = 1 requirement / 1 scenario; total 10 requirements / 13 scenarios.

### Build & Tests Execution
**Build**: PASSED (exit 0)
```text
./gradlew :api:billing:build -x test --rerun-tasks   -> BUILD SUCCESSFUL in 11s, 15 actionable tasks: 15 executed
build_output_hash: sha256:6175ca46cd776aeee0dc690147238b83fdf5c91f3ecdaf4a82655cd787177d55
```

**Tests**: 858 passed / 0 failed / 0 skipped (139 suites; summed from `api/billing/build/test-results/test/*.xml`)
```text
./gradlew :api:billing:test --no-build-cache --rerun --console=plain -> exit 0, BUILD SUCCESSFUL in 57s (test task executed, not cached)
test_output_hash: sha256:62dd728bc1995f268e3c0184d5241d8966fa6e5fae9ae8c58a2820fcf79a926d
```

Other fresh commands (all `--rerun` / `--rerun-tasks`, never FROM-CACHE):
| Command | Exit | Result |
|---|---|---|
| `./gradlew :api:billing:jacocoTestCoverageVerification --rerun` | 0 | BUILD SUCCESSFUL (layered gates domain+application and infrastructure hold) |
| `./gradlew :api:billing:checkstyleMain :api:billing:checkstyleTest --rerun` | 0 | BUILD SUCCESSFUL, 2121 warnings module-wide, 4 on lines added by this change (see Issues) |
| `./gradlew verifyLocalInfrastructureContract --rerun-tasks` | 0 | "Local infrastructure contract verified." |
| `./gradlew :api:billing:test --tests "*ArchitectureTest" --rerun --no-build-cache` | 0 | 8 tests, 0 failures, includes `application_should_not_depend_on_micrometer` |

**Coverage**: layered JaCoCo gates PASS (gate command exit 0). Changed production files: `PaymentFulfillmentService` 41/41 lines and 16/16 branches, `LogAndMetricSubscriptionFulfillmentAlarmAdapter` 17/17 lines and 4/4 branches, `PaymentVerificationService` 47/47 lines and 18/18 branches, both alarm DTOs 100%, port is an interface. `BillingConfiguration` shows 12 missed lines in unrelated pre-existing beans; the changed bean is exercised by `wires_the_payment_fulfillment_service_bean`.

### TDD Compliance
| Check | Result | Details |
|-------|--------|---------|
| TDD Evidence reported | PASS | "TDD Cycle Evidence" table present in apply-progress.md |
| All tasks have tests | PASS | every production task row names a test file; 1.1/1.2/1.3 are dependency/pure-type rows exercised by 2.x/3.x |
| RED confirmed (tests exist) | PASS | all 7 listed test files exist on disk and ran in my run (RED history itself is trusted from the report; the squash merges do not preserve it) |
| GREEN confirmed (tests pass) | PASS | all listed test classes pass in my fresh run: Adapter 7, FulfillmentService 21, VerificationService 24, BillingConfiguration 20, Architecture 8, AlertRuleContract 22 |
| Triangulation adequate | PASS | multiple cases per behavior (first edge, replay, recovery, happy, active, re-grant, physical, subscription_missing x2, both reasons parameterized) |
| Safety net for modified files | PASS | modified test files report 13/13, 24/24, 20/20, 7/7 safety nets |

**TDD Compliance**: 6/6 checks passed

### Test Layer Distribution
| Layer | Tests | Files | Tools |
|-------|-------|-------|-------|
| Unit | 72 | 4 (`PaymentFulfillmentServiceTest` 21, `PaymentVerificationServiceTest` 24, `BillingConfigurationTest` 20, adapter test 7) | JUnit 5, Mockito, AssertJ, Logback ListAppender, SimpleMeterRegistry |
| Contract (file read) | 22 | 1 (`SubscriptionFulfillmentAlertRuleContractTest`) | JUnit 5 parameterized |
| Architecture | 8 | 1 (`ArchitectureTest`) | ArchUnit |
| Script guard | n/a | `verifyLocalInfrastructureContract` | shell via Gradle |
| Integration / E2E | 0 | 0 | none by design (design: no api:app test) |

### Assertion Quality
**Assertion quality**: 0 CRITICAL, 2 WARNING
| File | Assertion | Issue | Severity |
|------|-----------|-------|----------|
| `SubscriptionFulfillmentAlertRuleContractTest` | `contains("noDataState: OK")`, `contains("for: 0s")`, `rule_documents_every_reason_code`, ... | Substring checks on the raw YAML text can be satisfied by a comment or the description text instead of the structural key; a YAML parse would be stricter. Mutation check recorded in apply-progress shows the marker and `noDataState` drift is still detected. | WARNING |
| `SubscriptionFulfillmentAlertRuleContractTest` | (absent) | Reducer `last`/`dropNN`, threshold `gt 0` and the `relativeTimeRange 3600..0` of queries B/C are not pinned by any test | WARNING |

No tautologies, no ghost loops (the enum loop in `the_log_reason_and_the_metric_reason_are_the_same_value_for_each_case` is guarded by `hasSize(2)`), no smoke-only tests.

### Spec Compliance Matrix
Requirement numbering: R1-R9 = `subscription-fulfillment-alarm`, R10 = `purchase-exception-notification` (MODIFIED).

| Req | Scenario | Covering test(s) (all PASSED in my run) | Result |
|-----|----------|------------------------------------------|--------|
| R1 | First transition into EXCEPTION alarms once | `PaymentFulfillmentServiceTest > ensure_raises_one_plan_missing_alarm_after_saving_the_first_transition_into_exception` (InOrder save then raise, fields asserted); `PaymentVerificationServiceTest > a_missing_plan_degrades_the_subscription_to_exception_instead_of_an_empty_snapshot` | COMPLIANT |
| R1 | Replay while already EXCEPTION does not re-alarm | `PaymentFulfillmentServiceTest > ensure_does_not_re_alarm_a_replay_while_the_subscription_is_already_exception` (two runs, `verifyNoInteractions(alarmPort)`) | COMPLIANT |
| R1 | Happy and already-activated paths never alarm | `ensure_never_alarms_on_the_happy_path`, `ensure_never_alarms_for_an_already_active_and_assigned_subscription`, `ensure_never_alarms_when_it_re_grants_an_active_but_unassigned_subscription`, `ensure_never_alarms_for_a_physical_payment` | COMPLIANT |
| R2 | Missing subscription row alarms each time | `PaymentFulfillmentServiceTest > ensure_raises_subscription_missing_on_every_run_and_never_creates_a_subscription` (2 alarms, `save` never); `PaymentVerificationServiceTest > a_completed_virtual_payment_with_no_subscription_never_invents_one` | COMPLIANT |
| R3 | Reason matches across log and metric | `LogAndMetricSubscriptionFulfillmentAlarmAdapterTest > the_log_reason_and_the_metric_reason_are_the_same_value_for_each_case`, `raising_increments_only_the_matching_counter_and_tags_only_the_reason` (tag set == {reason}, no paymentId tag), the two exact-line tests | COMPLIANT |
| R4 | Each rail reaches the alarm | Chokepoint alarm behavior: the `PaymentFulfillmentServiceTest` cases above. Webhook rail end to end through the real service: `PaymentVerificationServiceTest` (both lock tests). Bank-transfer approval and admin correction call the same `ensure`: `ResolvePaymentProofUseCaseImplTest` L86 and `CorrectPaymentUseCaseImplTest` L80 verify `fulfillmentService.ensure(...)` (service mocked there; by design "no new test"). Main code has exactly 4 `.ensure(` call sites (3 rails) | COMPLIANT (rail delegation is verified with the service mocked, see WARNING) |
| R5 | Recovery after an alarm still works | `PaymentFulfillmentServiceTest > ensure_stays_silent_when_a_subscription_left_in_exception_recovers_to_assigned` (saved as ASSIGNED, no alarm); `PaymentVerificationServiceTest > retries_a_subscription_left_exceptional_by_the_first_fulfillment_attempt` | COMPLIANT |
| R5 | Alarm adds no outbox row or mail | `PaymentVerificationServiceTest > a_missing_plan_degrades_...` (virtual EXCEPTION path, physical publish `never()`); `PaymentFulfillmentService` constructor has no outbox, mail or notification collaborator, so none can be emitted | COMPLIANT (negative property proven by construction plus the lock test, see WARNING) |
| R6 | Format-lock test fails on drift | `LogAndMetricSubscriptionFulfillmentAlarmAdapterTest > plan_missing_writes_the_exact_contract_line_at_error_level`, `subscription_missing_writes_none_for_the_absent_subscription_id`, `exposed_contract_constants_equal_the_locked_literals`; `SubscriptionFulfillmentAlertRuleContractTest > query_matches_the_adapter_log_marker`, `query_extracts_and_groups_by_the_reason_key_used_in_the_log_line`, `rule_documents_every_reason_code[1-2]` | COMPLIANT |
| R7 | Architecture rules hold | `ArchitectureTest > application_should_not_depend_on_micrometer`, `application_should_not_depend_on_infrastructure`, `layered_architecture_should_be_respected`; adapter increments by reason: `raising_increments_only_the_matching_counter_and_tags_only_the_reason` | COMPLIANT |
| R8 | Rule loads and fires | `SubscriptionFulfillmentAlertRuleContractTest` (22 cases: contract settings, `uid: loki`, no contact point or routing) PASSED, plus live runtime observation: rule listed and `firing` in the running Grafana, one Alerting instance per reason, no contact point (see Runtime evidence) | COMPLIANT |
| R9 | Mount path is correct | `verifyLocalInfrastructureContract` (fresh `--rerun-tasks`, exit 0) requires `../../../observability/grafana/provisioning:/etc/grafana/provisioning` in the compose file; commit `5331e36` changes exactly that one compose line, so the other compose files are untouched; live Grafana shows the rule and datasource provisioned | COMPLIANT |
| R10 | Virtual EXCEPTION emits no notification event | `PaymentVerificationServiceTest > a_missing_plan_degrades_the_subscription_to_exception_instead_of_an_empty_snapshot` (state EXCEPTION/PENDING, only the alarm raised, physical publish never invoked); constructor-level absence of outbox/mail collaborators | COMPLIANT (see WARNING) |

**Compliance summary**: 13/13 scenarios compliant, 10/10 requirements.

### Correctness (Static Evidence)
| Requirement | Status | Notes |
|------------|--------|-------|
| R1 edge-guarded PLAN_MISSING | Implemented | `PaymentFulfillmentService` L105-113: `firstEdge` read before `save`, `raise` after `save` only if first edge |
| R2 SUBSCRIPTION_MISSING every time | Implemented | L84-92: raise on every `existing.isEmpty()`, then `return`, no save |
| R3 distinct reasons, no paymentId tag | Implemented | enum codes `plan_missing`/`subscription_missing`; counter tag set is only `reason` |
| R4 single chokepoint | Implemented | only `PaymentVerificationService` (x2), `ResolvePaymentProofUseCaseImpl`, `CorrectPaymentUseCaseImpl` call `ensure` |
| R5 no transition/outbox/mail/migration change | Implemented | no new collaborator besides the port; no Flyway file in the change |
| R6 locked log line | Implemented | `alarm=virtual_subscription_fulfillment reason=.. paymentId=.. subscriptionId=<uuid|none> planId=.. userId=..` at ERROR |
| R7 port in application, Micrometer only in infrastructure | Implemented | port `application/port/out`, records in `application/dto`, adapter `infrastructure/observability`, ArchUnit rule |
| R8 Grafana rule as code | Implemented | `alerting/billing-subscription-fulfillment.yml`, no contact point, no Prometheus |
| R9 mount fix | Implemented | compose L95 `../../../observability/grafana/provisioning` |

### Coherence (Design)
| Decision | Followed? | Notes |
|----------|-----------|-------|
| A1 adapter writes log + metric | Yes | log first, then counter, no try/catch |
| A2 port in application, record+enum in `application/dto` | Yes | `SubscriptionFulfillmentAlarmPort`, `SubscriptionFulfillmentAlarm`, `SubscriptionFulfillmentAlarmReason` |
| A3 adapter in `infrastructure/observability`, `@Component` | Yes | |
| A4 `micrometer-core` without version | Yes | `api/billing/build.gradle.kts` L36 |
| A5 counter name, only `reason` tag, pre-registered at 0 | Yes | `both_reason_counters_are_pre_registered_at_zero` |
| A7 read status BEFORE save | Yes | `firstEdge` computed before `save` |
| A8 alarm inside caller tx, no catch | Yes | |
| A9 legacy 6-arg ctor takes the port (7 args) | Yes | no hidden no-op |
| A10 Grafana-managed LogQL rule | Yes | queries A (loki, instant, 3600..0, `sum by (reason) (count_over_time({service_name="menta-dance-api"} \|= "alarm=virtual_subscription_fulfillment" \| logfmt reason [1h]))`), B reduce last/dropNN, C threshold gt 0, condition C |
| Rule parameters | Yes | group `billing`, folder `Menta Dance`, interval 1m, `for: 0s`, `noDataState: OK`, `execErrState: Error`, labels `severity: critical`, `module: billing`, annotations use `{{ $labels.reason }}`, no contact point |
| Datasource `uid: loki` | Yes | `datasources/loki.yml`; live API returns uid `loki` |
| A11 guardrails | Yes | ArchUnit rule, contract test, script `require` line |
| Compose mount fix | Yes | |
| Deviation noted in apply-progress (script pattern must not start with `-`; rule description names both reason codes; extra contract assertions) | Accepted | behavior-neutral, documented |

### Runtime evidence for the Grafana rule (read-only, nothing started/stopped/written)
Observed at 2026-10-01T19:21Z against the running local stack:
- `GET localhost:3000/api/prometheus/grafana/api/v1/rules`: group `billing` (file `Menta Dance`, interval 60) contains rule "Virtual subscription fulfillment alarm", `state: firing`, `health: ok`, labels `module=billing`, `severity=critical`, last evaluation 2026-10-01T19:20:20Z. Two `Alerting` instances, `reason=plan_missing` and `reason=subscription_missing`, both active since 19:00:20Z.
- `GET localhost:3000/api/datasources`: one datasource `Loki`, `id 1`, `uid loki`, `type loki`, url `http://loki:3100`.
- The rule was still inside the 1h window, so Firing was re-observed live. The earlier Loki emission and the in-place uid update of the persisted datasource come from apply-progress task 6.4; only the current state above was re-observed by this phase.

### Issues Found
**CRITICAL**: None

**WARNING**:
1. Checkstyle: 4 `MethodName` warnings on lines added by this change, all in `PaymentFulfillmentServiceTest` (L253, L270, L326, L338, snake_case names). They follow the file's existing snake_case style (22 warnings already in that file) and are tracked repository-wide in issue #298; `checkstyleMain` has 0 warnings on added lines; the new contract test and adapter test have 0. Not a blocker.
2. Rails scenario (R4): bank-transfer approval and admin correction are verified only as delegation to `ensure` with `PaymentFulfillmentService` mocked, by design (no end-to-end rail test). The alarm itself is verified at the chokepoint and through the real service on the webhook rail.
3. "No outbox row or mail" (R5, R10) is a negative property proven by the absence of outbox/mail collaborators plus a `never()` on the physical publish; no assertion directly states "no outbox row appended".
4. Contract test strength: substring matching over the raw YAML (comments or description text can satisfy a check) and queries B/C settings are not pinned (see Assertion Quality).
5. Process: `sdd-attempt status` still shows attempt 1 (work unit "PR1 Java phases 1-4 and 6") as `running` with `evidence_revision` empty; the orchestrator must settle it. The `evidence_revision` value in the envelope is a best-candidate (see Result notes), not a documented digest.

**SUGGESTION**:
1. Replace the substring checks in the contract test with a YAML parse so keys cannot be satisfied by comments, and pin queries B/C.
2. Unrelated to this change: Grafana logs provisioning errors for the missing `dashboards`/`plugins` provisioning folders (noted in apply-progress); create the empty directories or ignore.
3. The OTel agent jar is gitignored and undocumented; without it no alarm line reaches Loki (design open question, outside this change).

### Verdict
PASS WITH WARNINGS
All 24 tasks done, 13/13 scenarios and 10/10 requirements covered by tests that passed fresh in this run (858 tests, 0 failures), build, coverage gates, checkstyle, infrastructure contract and ArchUnit pass, the Grafana rule is observed firing live; remaining items are non-blocking warnings.

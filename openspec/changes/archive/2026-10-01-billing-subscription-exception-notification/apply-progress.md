# Apply Progress: billing-subscription-exception-notification (#236)

**Mode**: Strict TDD. **Batches**: PR 1 (Java slice, merged to develop as c68f4c9) and PR 2 (Grafana rule and dev-stack mount, branch `feature/236-grafana-alert-rule`). Nothing committed, pushed or staged by the apply phase.

## Scope of the batches

- PR 1 done: 1.1-1.3, 2.1-2.3, 3.1-3.3, 4.1-4.5, 6.1. 6.2 was partial (ArchitectureTest only).
- PR 2 done: 5.1-5.5, 6.2 (second half), 6.3.
- Remaining: 6.4 (manual Grafana check, verify phase). 6.5 needs no code.

## TDD Cycle Evidence

| Task | Test file | Layer | Safety net | RED | GREEN | Triangulate | Refactor |
|------|-----------|-------|-----------|-----|-------|-------------|----------|
| 1.1 | (build) | N/A | 64/64 (Fulfillment 13, Verification 24, Architecture 7, BillingConfiguration 20) | n/a: dependency only | `:api:billing:compileJava` OK; `micrometer-core -> 1.15.11` via Boot BOM | skipped: structural | none |
| 1.2, 1.3 | exercised by 2.x/3.x | Unit | N/A (new) | n/a: pure types | compile OK | covered by 2.x/3.x | none |
| 2.1 / 2.2 / 2.3 | `LogAndMetricSubscriptionFulfillmentAlarmAdapterTest` | Unit (Logback ListAppender + SimpleMeterRegistry) | N/A (new) | `compileTestJava`: `cannot find symbol class LogAndMetricSubscriptionFulfillmentAlarmAdapter` (micrometer imports resolved) | 6/6 pass; after refactor 7/7 | exact line per reason, `none`, counter+tag set, pre-registered, no dedup, log/metric reason equality | constants `LOG_MARKER`, `COUNTER_NAME`, `METRIC_REASON_TAG` extracted + pinned by a test |
| 3.1 / 3.2 / 3.3 | `PaymentFulfillmentServiceTest` | Unit (Mockito) | 13/13 | compile error (ctor lacks port); after adding only the ctor param: 2 behavior failures `Wanted but not invoked` (plan_missing edge, subscription_missing x2) | 21/21 pass | first edge, replay while EXCEPTION, recovery to ASSIGNED, happy, active, re-grant, physical (verifyNoInteractions), subscription_missing twice | `raiseAlarm` helper extracted |
| 4.1 / 4.2 | `PaymentVerificationServiceTest` | Unit | 24/24 | compile error (legacy 7-arg ctor); then 2 `Wanted but not invoked` (PLAN_MISSING, SUBSCRIPTION_MISSING) | 24/24 pass | both regression-lock tests | none |
| 4.3 / 4.4 | `BillingConfigurationTest` | Unit | 20/20 | compile error (`paymentFulfillmentService` bean 5 args) | 20/20 pass; full module 804/804 | single wiring assertion | none |
| 4.5 | `ArchitectureTest` | ArchUnit | 7/7 | temporary probe class `application.dto.TempMicrometerLeak` importing `MeterRegistry`: `application_should_not_depend_on_micrometer` FAILED (8 run, 1 failed); probe deleted | 8/8 pass | n/a | none |
| 5.1 / 5.2 / 5.3 | `SubscriptionFulfillmentAlertRuleContractTest` | Unit/contract (file read of the repository-root rule and datasource) | N/A (new test; `verifyLocalInfrastructureContract` baseline green before touching infra files) | `:api:billing:test --tests "*AlertRuleContractTest"`: `1 test completed, 1 failed`, `initializationError` from the `@BeforeAll` file-exists assertion (rule file absent) | 22/22 pass after creating the rule file and adding `uid: loki` | 22 cases across marker, `logfmt reason`/`sum by`, stream selector, both reason codes (EnumSource), no counter/Prometheus, 11 contract settings, 4 forbidden routing words, datasource uid. Mutation check: renaming the marker and setting `noDataState: Alerting` made exactly those 2 cases fail (22 run, 2 failed); restored, 22/22 | none needed |
| 5.4 / 5.5 | `scripts/verify-local-infrastructure-contract.sh` (Gradle `verifyLocalInfrastructureContract`) | Script guard | baseline green before the change | Added the `require` line against the unfixed mount: task FAILED; reverted-mount check after the fix: task FAILED with `expected '...observability/grafana/provisioning:/etc/grafana/provisioning' in infra/docker/database/docker-compose.yml` | fixed mount: `Local infrastructure contract verified.` BUILD SUCCESSFUL, also after restoring | revert-and-restore cycle (fails, then passes) | none |

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command (PR 1) | `./gradlew :api:billing:test --tests "*AlarmAdapterTest" --tests "*PaymentFulfillmentServiceTest" --tests "*PaymentVerificationServiceTest" --tests "*BillingConfigurationTest" --tests "*ArchitectureTest"`: BUILD SUCCESSFUL, 7+21+24+20+8 = 80 tests, 0 failures |
| Focused test command (PR 2) | `./gradlew :api:billing:test --tests "*AlertRuleContractTest" --no-build-cache --rerun`: BUILD SUCCESSFUL, 22 tests, 0 failures; `./gradlew verifyLocalInfrastructureContract --rerun-tasks`: BUILD SUCCESSFUL |
| Runtime harness (PR 1) | N/A: no api:app test by design; `./gradlew test` full run BUILD SUCCESSFUL |
| Runtime harness (PR 2) | Throwaway `grafana/grafana:11.5.2` container (removed after use) mounting the repository-root provisioning directory: `/api/v1/provisioning/alert-rules` returned the rule (uid, group `billing`, condition C, `for` 0s, noDataState OK, execErrState Error, labels, provenance `file`, `notification_settings: null`) and `/api/datasources` returned `Loki` with `uid: loki`. This only proves the provisioning schema loads; it is not the 6.4 Firing check (no Loki or log line involved) |
| Rollback boundary | PR 1: revert `api/billing/build.gradle.kts`, the 3 new application types, the observability adapter, and the edits to `PaymentFulfillmentService`, `PaymentVerificationService`, `BillingConfiguration` plus their tests. PR 2: revert the rule file, the contract test, the loki uid, the compose mount and the script line (the mount fix reverts independently) |

## Verification

PR 1:
- `./gradlew :api:billing:test`: BUILD SUCCESSFUL, 138 suites, 804 tests, 0 failures, 0 errors, 0 skipped.
- Jacoco verification tasks BUILD SUCCESSFUL. `./gradlew test` (full monorepo) BUILD SUCCESSFUL.

PR 2:
- `./gradlew :api:billing:test --no-build-cache --rerun`: BUILD SUCCESSFUL, 139 suites, 858 tests, 0 failures, 0 errors, 0 skipped (836 tests in the 138 pre-existing suites on current develop, plus 22 from the new suite; the 804 recorded for PR 1 predates later merges to develop).
- `./gradlew :api:billing:jacocoTestCoverageVerification --rerun`: BUILD SUCCESSFUL (the new work adds no production Java).
- `./gradlew :api:billing:checkstyleTest --rerun`: the new test file has 0 entries in `build/reports/checkstyle/test.xml` (no warnings at all, including no `MethodName`). Pre-existing warnings elsewhere are untouched.
- 6.2: `ArchitectureTest` passes in `:api:auth`, `:api:virtual`, `:api:physical`, `:api:billing` (8 tests) and `:api:app`; `:api:shared` has none ("No tests found"). The literal root command `./gradlew test --tests "*ArchitectureTest"` cannot run: the `:android:test` task has no `--tests` option ("Unknown command-line option '--tests'"). This is a pre-existing limitation, not caused by this change. `./gradlew verifyLocalInfrastructureContract`: BUILD SUCCESSFUL.
- 5.4 verification: `docker compose -f infra/docker/database/docker-compose.yml config` needs `MYSQL_APP_PASSWORD` and `MYSQL_ROOT_PASSWORD` (not in the environment); with dummy values passed inline it resolved the Grafana provisioning bind source to the repository-root `observability/grafana/provisioning` directory, next to the unchanged Loki and OTel mounts.
- 6.3: `infra/docker/database/observability` held two empty nested directories (`grafana/provisioning`) and zero files, untracked; removed with three `rmdir` calls. `git status` shows no entry for it.

Task 6.4 (manual end-to-end check, run by the orchestrator against the developer's already-running local stack):
- Before: the running `menta-grafana` mounted the stray empty directory (the original mount bug), so no rule or datasource provisioning was loaded; its persisted volume held a `Loki` datasource with the auto-generated uid `P8E80F9AEF21F6940`.
- Only the Grafana container was recreated (`docker compose up -d --no-deps grafana`, volume kept) with the corrected mount. The persisted datasource kept `id 1` and took `uid: loki` in place, so no datasource or volume had to be dropped. The rule loaded as `inactive`/`ok` in folder "Menta Dance" before any alarm was emitted (no false positive with no data).
- Emission: a throwaway main ran the real `LogAndMetricSubscriptionFulfillmentAlarmAdapter` (plan_missing and subscription_missing, the latter with `subscriptionId=none`) under the same `-javaagent` and OTel flags `api:app` uses (service name `menta-dance-api`, OTLP http/protobuf to the collector). Both ERROR lines reached Loki and both Micrometer counters read 1.0. Not exercised: the Spring wiring and the real fulfillment trigger (covered by the PR 1 unit tests).
- The rule's exact LogQL, run through Grafana's datasource proxy (`uid=loki`), returned two series: `reason=plan_missing` = 1 and `reason=subscription_missing` = 1.
- The rule reached `firing` on its first evaluation cycle, with one `Alerting` instance per reason, `severity=critical`, and the summary annotation templated with `{{ $labels.reason }}`. No contact point exists, so nothing was notified (as designed).
- Side effects on the local environment: two test lines (paymentIds ending `a236` and `c236`, planId `plan-check-6-4`) remain in the local Loki; the rule keeps firing until they leave the 1h window. Grafana logs `provisioning.dashboard`/`provisioning.plugins` errors because the provisioning directory has no `dashboards` or `plugins` folders (inferred from the standard Grafana message; the log line is truncated).

## Deviations from design

None in behavior. Notes:
1. PR 1: `SubscriptionFulfillmentAlarmReason` uses Lombok `@Getter @Accessors(fluent = true)`; the adapter exposes `LOG_MARKER`, `COUNTER_NAME`, `METRIC_REASON_TAG` as public constants; the three production call sites changed together.
2. PR 2: the `require` pattern must not start with `-`: the script calls `grep -Eq "$pattern"` without `--`, so a pattern beginning with "- " is parsed as an option and fails for the wrong reason. The line therefore starts at the relative path (`\.\./\.\./\.\./observability/...:/etc/grafana/provisioning`), which still discriminates the corrected mount from the old `./observability/...` one.
3. PR 2: the contract test additionally asserts that the rule does not reference the counter name or Prometheus (the rule is log-based by D5) and that `loki.yml` declares `uid: loki`, the uid the rule references.
4. PR 2: the rule description names both reason codes (design annotations only referenced `{{ $labels.reason }}`) so the contract test can tie the YAML to the `SubscriptionFulfillmentAlarmReason` enum.
5. PR 2: the stray directory held nested empty directories, not a single empty one; still zero files and untracked.

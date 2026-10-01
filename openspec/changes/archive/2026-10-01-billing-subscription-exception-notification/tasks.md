# Tasks: Alarm on silent virtual-subscription fulfillment failures (#236)

Paths: `B=api/billing/src/main/java/com/menta/billing`, `T=api/billing/src/test/java/com/menta/billing`. Strict TDD: every production task is preceded by a RED test task. Module test: `./gradlew :api:billing:test`.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~460 authored (prod Java ~160, build/ArchUnit ~13, tests ~230, YAML/infra ~60) |
| 400-line budget risk | Medium (above 400, within the 800 session budget) |
| Chained PRs recommended | No |
| Suggested split | Single PR. Fallback if review is heavy: PR 1 = Phases 1-4 (Java), PR 2 = Phase 5 (infra, independent) |
| Delivery strategy | auto-chain (800 budget) |
| Chain strategy | pending (not needed) |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Port, dto, adapter, hook, wiring, ArchUnit | PR 1 | `./gradlew :api:billing:test` | N/A: unit and ArchUnit tests cover it; no `api:app` test by design | Revert Java and `build.gradle.kts` changes |
| 2 | Grafana rule, loki uid, mount fix, contract script | PR 1 (or PR 2) | `./gradlew :api:billing:test --tests "*AlertRuleContractTest"` and `./gradlew verifyLocalInfrastructureContract` | `scripts/dev.sh start`, emit one log line, rule shows Firing | Revert YAML, compose and script files; the mount fix reverts independently |

## Phase 1: Foundation (types, dependency)

- [x] 1.1 `api/billing/build.gradle.kts`: add `implementation("io.micrometer:micrometer-core")` (no version). Verify: `./gradlew :api:billing:compileJava`.
- [x] 1.2 Create `B/application/dto/SubscriptionFulfillmentAlarmReason.java` (`PLAN_MISSING("plan_missing")`, `SUBSCRIPTION_MISSING("subscription_missing")`, `code()`) and record `SubscriptionFulfillmentAlarm(reason, paymentId, subscriptionId, planId, userId)`. Verify: compiles; exercised by 2.x tests.
- [x] 1.3 Create `B/application/port/out/SubscriptionFulfillmentAlarmPort.java` (`void raise(SubscriptionFulfillmentAlarm)`). Verify: `./gradlew :api:billing:compileJava`.

## Phase 2: Adapter (infrastructure)

- [x] 2.1 RED: `T/infrastructure/observability/LogAndMetricSubscriptionFulfillmentAlarmAdapterTest.java` using a Logback `ListAppender` (pattern: `ActivationRequestLoggingFilterTest`) and `SimpleMeterRegistry`. Cases: exact ERROR line per reason, `subscriptionId=none` when null, counter +1 with tag set == {reason}, both reason counters pre-registered at 0, log and metric reasons equal. Verify: `./gradlew :api:billing:test --tests "*AlarmAdapterTest"` fails (class missing).
- [x] 2.2 GREEN: create `B/infrastructure/observability/LogAndMetricSubscriptionFulfillmentAlarmAdapter.java` (`@Component`, ctor `MeterRegistry`, logs first, then counter `billing.subscription.fulfillment.alarm{reason}`, no try/catch; use Lombok `@RequiredArgsConstructor` where it fits). Verify: same command passes.
- [x] 2.3 REFACTOR: extract log-marker and counter-name constants (reused by 6.x). Verify: same command stays green.

## Phase 3: Hook in the use case

- [x] 3.1 RED: `T/application/usecase/PaymentFulfillmentServiceTest.java` (ctor at L62 gains a port mock). Cases: (a) first edge raises `PLAN_MISSING` once, `InOrder(save, raise)`; (a) replay with `EXCEPTION` subscription raises nothing; recovery to `ASSIGNED` raises nothing; happy, active and re-grant paths `verifyNoInteractions`; (b) two runs raise two `SUBSCRIPTION_MISSING` alarms and never save; field values asserted. Verify: `./gradlew :api:billing:test --tests "*PaymentFulfillmentServiceTest"` fails.
- [x] 3.2 GREEN: `B/application/usecase/PaymentFulfillmentService.java` (L70-95): add ctor param; case (a) read `getFulfillmentStatus()` before `save`, raise after save only if it was not `EXCEPTION`; case (b) raise when `existing.isEmpty()`. Verify: same command passes.
- [x] 3.3 REFACTOR: extract an alarm-builder helper if duplicated. Verify: same command green.

## Phase 4: Wiring and regression locks

- [x] 4.1 RED: update `T/application/usecase/PaymentVerificationServiceTest.java`: L65 legacy ctor gets the port mock (7th arg). L288-297: keep `never()` on the physical publish, reword the comment, add `raise(PLAN_MISSING)` verify. L300-309: add `raise(SUBSCRIPTION_MISSING)` verify. Verify: `./gradlew :api:billing:test --tests "*PaymentVerificationServiceTest"` fails (compile or assertion).
- [x] 4.2 GREEN: `B/application/usecase/PaymentVerificationService.java` legacy 6-arg ctor takes the port as 7th arg (no hidden no-op) and passes it through. Verify: same command passes.
- [x] 4.3 RED: `T/infrastructure/config/BillingConfigurationTest.java` (L76) supplies the port. Verify: `./gradlew :api:billing:test --tests "*BillingConfigurationTest"` fails to compile.
- [x] 4.4 GREEN: `B/infrastructure/config/BillingConfiguration.java:179` bean `paymentFulfillmentService` gains the port param. Verify: `./gradlew :api:billing:test`.
- [x] 4.5 RED then GREEN: add `application_should_not_depend_on_micrometer` to `T/ArchitectureTest.java` (`noClasses().that().resideInAPackage("..application..").should().dependOnClassesThat().resideInAPackage("io.micrometer..")`). Satisfies the ADR-0021 layering rules. Verify: `./gradlew test --tests "*ArchitectureTest"`.

## Phase 5: Grafana rule and dev-stack mount

- [x] 5.1 RED: create `T/infrastructure/observability/SubscriptionFulfillmentAlertRuleContractTest.java` reading the rule file `observability/grafana/provisioning/alerting/billing-subscription-fulfillment.yml` (the Gradle test working dir is the module directory, so the test resolves it two directory levels up, at the repository root). Asserts: the adapter's marker and constant, `logfmt reason`, `datasourceUid: loki`, both reason codes from the enum, `interval: 1m`, `for: 0s`, `noDataState: OK`, `execErrState: Error`, `severity: critical`, `module: billing`, and no `contactPoint`. Verify: `./gradlew :api:billing:test --tests "*AlertRuleContractTest"` fails.
- [x] 5.2 GREEN: create `observability/grafana/provisioning/alerting/billing-subscription-fulfillment.yml` (group `billing`, uid `billing-subscription-fulfillment-alarm`, queries A/B/C per design). Verify: same command passes.
- [x] 5.3 `observability/grafana/provisioning/datasources/loki.yml`: add `uid: loki`. Verify: 5.1 command stays green.
- [x] 5.4 Fix the mount: `infra/docker/database/docker-compose.yml:95` gets a mount source that points at the repository-root `observability/grafana/provisioning` directory (written relative to the compose file, three directory levels up). Verify: `docker compose -f infra/docker/database/docker-compose.yml config`.
- [x] 5.5 `scripts/verify-local-infrastructure-contract.sh`: add a `require` line pinning the corrected mount. Verify: `./gradlew verifyLocalInfrastructureContract` (temporarily revert 5.4 to confirm it fails).

## Phase 6: Verification and cleanup

- [x] 6.1 Run `./gradlew test` and `./gradlew :api:billing:jacocoTestCoverageVerification` (domain+application 85%, infrastructure 85%). Verify: both pass.
- [x] 6.2 (both halves done: ArchitectureTest in PR 1, `verifyLocalInfrastructureContract` in PR 2) Run `./gradlew test --tests "*ArchitectureTest"` and `./gradlew verifyLocalInfrastructureContract`. Verify: both pass.
- [x] 6.3 Local-only cleanup: delete the empty untracked dir `infra/docker/database/observability` (not part of the diff). Verify: `git status` shows no change for it.
- [x] 6.4 Manual (verify phase): with the OTel agent jar present, `scripts/dev.sh start`, emit one alarm line, and confirm the rule shows Firing in Grafana. Also confirm that a persisted `Loki` datasource picks up its uid (otherwise drop the datasource or volume). (Done: the real adapter was emitted under the OTel agent against the running local stack; the rule reached Firing for both reasons and the persisted datasource took `uid: loki` in place. See apply-progress.md.)
- [x] 6.5 No code task for the #209 MODIFIED delta: it is already authored under `specs/purchase-exception-notification/` and archive promotes it.

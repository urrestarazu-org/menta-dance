# Design: Alarm on silent virtual-subscription fulfillment failures (#236)

## Technical Approach

`PaymentFulfillmentService.ensureSubscription` is the single chokepoint for webhook verification, bank-transfer approval and admin correction (all callers invoke `ensure` only for settled payments: `PaymentVerificationService:158,171`, `ResolvePaymentProofUseCaseImpl:52`, `CorrectPaymentUseCaseImpl:41`). It raises an alarm through one new application outbound port. One infrastructure adapter writes the contract log line (`ERROR`) and increments a Micrometer counter. A Grafana-managed, file-provisioned LogQL rule fires on the log line. There is no mail, outbox, schema or state-machine change (D1-D7).

```
caller tx ──> PaymentFulfillmentService.ensureSubscription
               ├─ no row ───────────────> alarmPort.raise(SUBSCRIPTION_MISSING)   (every time)
               └─ plan missing: wasException = status==EXCEPTION
                    save(exception()) ─> if !wasException: alarmPort.raise(PLAN_MISSING)
LogAndMetric adapter ─> log.error(contract line) ─> OTel agent ─> collector ─> Loki ─> Grafana rule (Firing)
                    └─> Counter billing.subscription.fulfillment.alarm{reason}  (no consumer today)
```

## Architecture Decisions

| # | Topic | Choice | Rejected | Rationale |
|---|---|---|---|---|
| A1 | Where the log lives | The port adapter writes both the log and the metric | SLF4J in application plus a metric-only port (the proposal's Approach text) | The pair cannot drift. Application tests assert one semantic call. The contract format and Micrometer stay in infrastructure. Existing application SLF4J precedent (`PublishPhysicalPaymentCompletedUseCase`) is informational, not contract-bearing |
| A2 | Port | `application/port/out/SubscriptionFulfillmentAlarmPort.raise(SubscriptionFulfillmentAlarm)`; record and `SubscriptionFulfillmentAlarmReason` enum in `application/dto` | Reuse the physical `Reason` enum; separate log/metric ports | Follows the `PurchaseExceptionNotificationPort` + `dto` precedent. The physical `Reason` enum stays untouched. The port is intra-billing (no `api:shared` contract) |
| A3 | Adapter | `infrastructure/observability/LogAndMetricSubscriptionFulfillmentAlarmAdapter`, `@Component`, ctor `MeterRegistry` | Adapter in `api:app` | Keeps billing self-contained; same `@Component` resolution as the SpringMail adapters |
| A4 | Dependency | `implementation("io.micrometer:micrometer-core")` in `api/billing/build.gradle.kts`, without a version (Boot BOM via the root `platform(...)`) | Rely on transitive deps | Billing currently has only `micrometer-observation/commons` (via spring-context), not `micrometer-core`. The repo has no `*.lockfile`, so dependency locking needs no update |
| A5 | Metric | Counter `billing.subscription.fulfillment.alarm`, the only tag is `reason` (2 values), pre-registered for both reasons at 0 | Tags for paymentId, userId or planId; a gauge | Bounded cardinality. Pre-registering makes the meter discoverable before the first increment |
| A6 | Exposure | `api:app` actuator auto-configures an in-memory `SimpleMeterRegistry`. Readable at `/actuator/metrics/billing.subscription.fulfillment.alarm` (not `permitAll`; only `/actuator/health` is). **No consumer**: there is no Prometheus registry dependency, `management.metrics.export.prometheus.enabled` is an inert Spring Boot 2 key, and the OTel agent runs with `otel.metrics.exporter=none` | Add a Prometheus registry/scrape | Out of scope (proposal). Recorded as groundwork |
| A7 | Edge guard (a) | Read `existing.getFulfillmentStatus()` **before** `save`, raise after `save` only if it was not `EXCEPTION` | Raise at `plan.isEmpty()` | Replays re-enter the branch while the subscription stays `PENDING` |
| A8 | Failure semantics | Alarm inside the caller transaction; at-least-once (a rollback means the alarm fires again on retry). The adapter does not catch exceptions: SLF4J and an in-memory counter do not throw | try/catch in the adapter; after-commit hook | Accepted in the proposal. Logging first means the alert signal survives a counter fault |
| A9 | Legacy ctor | Extend the 6-arg `PaymentVerificationService` ctor with the port (its only caller is `PaymentVerificationServiceTest`) | Hidden no-op default | A silent no-op would mask alarms |
| A10 | Rule type | Grafana-managed rule, instant LogQL, `sum by (reason)` gives one instance per reason | Loki ruler (not configured); metric rule (no Prometheus) | Only working signal path (D5) |
| A11 | Guardrails | ArchUnit `application_should_not_depend_on_micrometer`; a Java test pins the YAML rule to the log constants; a `verify-local-infrastructure-contract.sh` line pins the mount path | Docs-only | Makes the D5 contract and the mount fix fail loudly on drift |

## Interfaces / Contracts

```java
public interface SubscriptionFulfillmentAlarmPort { void raise(SubscriptionFulfillmentAlarm alarm); } // must not throw
public enum SubscriptionFulfillmentAlarmReason { PLAN_MISSING("plan_missing"), SUBSCRIPTION_MISSING("subscription_missing"); /* code() */ }
public record SubscriptionFulfillmentAlarm(SubscriptionFulfillmentAlarmReason reason, UUID paymentId,
    UUID subscriptionId /* null for SUBSCRIPTION_MISSING */, String planId /* virtual.planId() */, UUID userId) {}
```

**Log contract (locked by a test, ERROR level, valid logfmt, opaque UUIDs only, no email/name/amount):**

```
alarm=virtual_subscription_fulfillment reason=<plan_missing|subscription_missing> paymentId=<uuid> subscriptionId=<uuid|none> planId=<id> userId=<uuid>
```

**Loki facts (verified):** logs are shipped only by the OTel Java agent in `:api:app:bootRun` (`otel.service.name=menta-dance-api`, `logs.exporter=otlp`). The record body is the formatted message. `loki-config.yml` indexes `service.name`, which becomes the stream label `service_name`. Severity is structured metadata only.

**Rule** `observability/grafana/provisioning/alerting/billing-subscription-fulfillment.yml`: `apiVersion: 1`, group `billing` (folder `Menta Dance`, `interval: 1m`), rule uid `billing-subscription-fulfillment-alarm`.

- A (Loki, `datasourceUid: loki`, instant, `relativeTimeRange 3600..0`): `sum by (reason) (count_over_time({service_name="menta-dance-api"} |= "alarm=virtual_subscription_fulfillment" | logfmt reason [1h]))`
- B reduce `last` (`dropNN`)
- C threshold `> 0`
- `condition: C`, `for: 0s`, `noDataState: OK` (an empty result is the normal state), `execErrState: Error` (a blind Loki must be visible)
- labels `severity: critical`, `module: billing`; annotations summary/description reference `{{ $labels.reason }}` and tell operators to search Loki for `paymentId`

The 1h window and severity can be tuned; they are not part of the contract. No other alerting file is needed. Grafana's built-in default policy points at its placeholder email contact point. Grafana has no SMTP configured, so nothing is delivered: the rule is visible in the UI only.

**Datasource:** add `uid: loki` to `datasources/loki.yml`; the rule references the datasource by uid.

## File Changes

| File | Action |
|---|---|
| `api/billing/.../application/port/out/SubscriptionFulfillmentAlarmPort.java` | Create |
| `api/billing/.../application/dto/SubscriptionFulfillmentAlarm.java`, `SubscriptionFulfillmentAlarmReason.java` | Create |
| `api/billing/.../application/usecase/PaymentFulfillmentService.java` | Modify: ctor param + two alarm points + edge guard |
| `api/billing/.../application/usecase/PaymentVerificationService.java` | Modify: legacy ctor +param |
| `api/billing/.../infrastructure/observability/LogAndMetricSubscriptionFulfillmentAlarmAdapter.java` | Create |
| `api/billing/.../infrastructure/config/BillingConfiguration.java` | Modify: `paymentFulfillmentService` bean +param |
| `api/billing/build.gradle.kts` | Modify: micrometer-core |
| `api/billing/src/test/.../ArchitectureTest.java` | Modify: Micrometer rule |
| `PaymentFulfillmentServiceTest`, `PaymentVerificationServiceTest` (L65, L288-297, L300-309), `BillingConfigurationTest` (L76) | Modify |
| `.../infrastructure/observability/LogAndMetricSubscriptionFulfillmentAlarmAdapterTest.java`, `SubscriptionFulfillmentAlertRuleContractTest.java` | Create |
| `observability/grafana/provisioning/alerting/billing-subscription-fulfillment.yml` | Create |
| `observability/grafana/provisioning/datasources/loki.yml` | Modify: `uid: loki` |
| `infra/docker/database/docker-compose.yml:95` | Modify: `../../../observability/grafana/provisioning` |
| `scripts/verify-local-infrastructure-contract.sh` | Modify: `require` the corrected mount |

Construction sites (verified with `rg`, which corrects the proposal's "BillingConfiguration x2"): the `PaymentVerificationService` legacy ctor, `BillingConfiguration:179`, `BillingConfigurationTest:76`, `PaymentFulfillmentServiceTest:62`, `PaymentVerificationServiceTest:65`. The other tests mock the service.

The stray `infra/docker/database/observability/` directory is empty. Git never tracks empty directories, so it is neither tracked nor gitignored and the diff does not touch it. Delete it locally after the fix; Docker will not recreate it.

## Testing Strategy (strict TDD, RED first)

| Layer | What | Approach |
|---|---|---|
| Unit (application) | (a) first edge raises PLAN_MISSING once, after save; replay with an `EXCEPTION` subscription raises nothing; recovery to `ASSIGNED` raises nothing; happy, active and re-grant paths raise nothing (`verifyNoInteractions`); (b) two runs raise two alarms, never save; record field values | Mockito on the port, `InOrder(save, raise)` |
| Unit (regression lock) | `PaymentVerificationServiceTest` L288-297: keep `never()` on the physical publish; reword the comment; add a `raise(PLAN_MISSING)` verify. L300-309: add a `raise(SUBSCRIPTION_MISSING)` verify | Mockito |
| Unit (infra) | Exact formatted message per reason at `ERROR`; `none` for a null subscriptionId; counter +1 with tag set == {reason}; both meters pre-registered at 0 | Logback `ListAppender` (precedent: `ActivationRequestLoggingFilterTest`) + `SimpleMeterRegistry` |
| Contract | The YAML contains the adapter's marker, `logfmt reason`, `datasourceUid: loki`, and both reason codes from the enum | Read `../../observability/...` from the Gradle test working dir |
| Architecture | Application has no `io.micrometer..` dependency; existing layered rules | ArchUnit |
| Rails | Covered by the existing `order.verify(fulfillmentService).ensure(...)` (`ResolvePaymentProofUseCaseImplTest:86`, `CorrectPaymentUseCaseImplTest:80`) | No new test |
| api:app | No new test: every existing `@SpringBootTest` fails to start if `MeterRegistry` wiring breaks | None |
| Manual | Fix the mount, `scripts/dev.sh start` with the agent jar present, emit one line, and see the rule Firing in Grafana | Verify step |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification or process-integration boundary. The edits to the compose file and the contract script change one path and add one grep line; they add no new execution.

## Migration / Rollout

No migration. Revert the PR to roll back; the mount fix can be reverted independently.

## Review Workload Forecast

Production Java ~160, build/ArchUnit ~13, tests ~230, YAML/infra ~60, for about **460** authored lines (excluding SDD artifacts). Decision needed before apply: No. Chained PRs recommended: No. 400-line budget risk: Medium (above 400, within the 800 session budget).

## Open Questions

- None blocking. These are verification items, not decisions:
  - The OTel agent jar is gitignored (`*.jar`) and nothing documents how to obtain it. Without it no log reaches Loki, and the dockerized API stacks run no agent.
  - Confirm that Grafana updates an existing `Loki` datasource's uid in place in persisted `grafana_data` volumes. If not, delete the datasource or the volume.

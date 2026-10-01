# Proposal: Alarm on silent virtual-subscription fulfillment failures (Issue #236)

**Issue**: #236, retitled "Alarma (log + métrica + regla Grafana) para fallas silenciosas de fulfillment de suscripción virtual" (follow-up of #209) · **Inputs**: `exploration.md` (Engram `sdd/billing-subscription-exception-notification/explore`), locked decisions (Engram `.../decision-q1`) · **PR linkage**: `Closes #236`

> **Naming note.** The change name keeps "notification" only for continuity with existing Engram keys. The approved scope is an **alarm** (structured log + metric + log-based Grafana alert rule), **not** a mail. The original issue title ("Notificar EXCEPTION también para suscripciones virtuales") was reinterpreted because the exploration proved the plan-missing trigger is effectively unreachable in production (see Background): a buyer/ops mail pipeline (~450-600 lines, new outbox event) is disproportionate for a state that requires data corruption, while an alarm keeps a signal if that guarantee ever breaks. The issue has already been retitled and commented with D1-D6 (D7).

## Intent

`PaymentFulfillmentService.ensureSubscription` has two branches where a settled virtual payment ends without access and **nothing is emitted**: no log, no event, no metric. Ops can only learn about them from a buyer complaint. This change makes both branches observable to operators and visible as a firing alert in Grafana.

## Background — verified findings

- **Reachability**: `fk_billing_subscriptions_plan` (`V14__billing_checkout.sql:116-117`) has no `ON DELETE` (default `RESTRICT`); `PlanRepository` has no delete and `api/billing/src/main` has no plan-delete path. Plans only go `INACTIVE`, and `findById` returns inactive plans. An empty `findById` at confirmation needs manual DB surgery, a dropped FK, or mocks. The #209/#237 claims of "reachable in production today" came from code reading only and are corrected here. This change is **defensive observability**.
- **Observability stack**: OTel collector -> Loki -> Grafana 11.5.2. `observability/grafana/provisioning/` contains only `datasources/loki.yml`. There are **no** alert rules, contact points or notification policies anywhere in the repo, no documented alert channel (no Slack/PagerDuty/on-call), and **no Prometheus datasource**. The D2 counter therefore has no consumer today, and the alert rule cannot be metric-based.
- **Provisioning mount (verified with `rg`)**: root `docker-compose.yml:116`, `infra/docker/app/docker-compose.yml:199` and `infra/docker/nginx/docker-compose.yml:198` mount `observability/grafana/provisioning` correctly. The canonical dev stack `infra/docker/database/docker-compose.yml` (used by `scripts/dev.sh`, no `--project-directory`) mounts `./observability/grafana/provisioning` at line 95. That resolves to the non-existent `infra/docker/database/observability/...`, while its own Loki/OTel mounts (lines 62, 78) use `../../../observability/...`. In that stack a new `alerting/` subdirectory, and even the existing Loki datasource, would **not** load.
- **Datasource uid**: `datasources/loki.yml` declares no `uid`. A provisioned alert rule references its datasource by uid.

## Scope

### In Scope

- **Case (a) plan missing** (`plan.isEmpty()` -> `save(existing.exception())`): alarm only on the real edge (persisted `fulfillmentStatus != EXCEPTION` before the save).
- **Case (b) no subscription row** (`existing.isEmpty()` -> silent `return`): alarm on **every** occurrence.
- Each alarm = one structured `log.error` (key=value) + one metric increment, with a **distinct reason per case**.
- An application-layer outbound port for the metric + an infrastructure adapter (Micrometer stays out of `application`).
- **One log-based Grafana alert rule provisioned as code** (LogQL on Loki) under `observability/grafana/provisioning/alerting/`, with **no contact point** (visible as Firing in the Grafana UI only).
- Provisioning prerequisites so the rule actually loads: fix the `infra/docker/database/docker-compose.yml:95` mount path; give the Loki datasource a stable `uid` if design confirms it is needed.
- MODIFIED delta (wording-only) on `purchase-exception-notification` (D6).
- Rewire the 5 `new PaymentFulfillmentService(...)` sites; update the `PaymentVerificationServiceTest` "#209 D.4 regression lock" (~L288-297).

### Out of Scope

- Mail, outbox event, notification handler/port/adapter; admin list for subscription `EXCEPTION`.
- **Contact points, notification policies, or any alert channel (email/Slack/webhook/on-call)**: separate follow-up task.
- A Prometheus datasource/scrape config or a metric-based alert rule.
- Persisted reason/timestamp, dedup state, new table, Flyway migration.
- `Reason` enum change, `Subscription` state-machine change, BFF/Android.

## Locked decisions (from the user's question rounds; not to be reopened)

| # | Decision | Rationale |
|---|---|---|
| D1 | No mail / outbox / notification pipeline | Trigger needs data corruption; mail is disproportionate |
| D2 | Edge-guarded structured `log.error` **plus** a metric (first metric in the project) | Log feeds Loki and the alert; the counter is a cheap signal for a future metrics consumer |
| D3 | Both silent branches in scope, distinct reasons | Case (b) is the same "paid, nobody told" failure in the same method |
| D4 | (a) edge-guarded; (b) alarms every time, no dedup | (a) replays re-enter the branch (subscription stays `PENDING`); (b) has no persisted state; ops group by `paymentId` |
| D5 | Alert rule in scope, provisioned as code, **log-based (LogQL)**, shipped **without** a contact point | No Prometheus datasource exists; no alert channel is documented; adding one is a follow-up |
| D6 | Wording-only MODIFIED delta on "Only physical purchases are in scope" | "byte-identical" becomes false once a log + metric is added; the observable behavior (state transitions, emitted events) is what stays unchanged |
| D7 | #236 already retitled and commented with D1-D6; PR uses `Closes #236` | Issue and proposal stay aligned |

**Contract consequence of D5**: the log message's fixed text and key=value fields (at least the reason key and its values) become a **contract** the LogQL rule depends on. Changing them silently breaks the alert; specs must pin them and a test must lock them.

## Capabilities

### New Capabilities

- `subscription-fulfillment-alarm`: when a settled virtual payment cannot be fulfilled (plan missing, no subscription row), an operator-facing alarm is emitted (log + metric, per-case reason; edge semantics for (a), every occurrence for (b); no buyer contact), and a provisioned log-based Grafana alert rule fires on it.

### Modified Capabilities

- `purchase-exception-notification`: requirement "Only physical purchases are in scope", wording-only. Replace "MUST remain byte-identical" with a statement that the virtual path's observable behavior (state transitions and emitted events) is unchanged (no `billing.PurchaseExceptioned` row, no email) and that an operational alarm (log + metric) is added. The scenario "Virtual EXCEPTION emits no notification event" is unchanged.

## Approach

Hook inside `PaymentFulfillmentService.ensureSubscription`, the single chokepoint for webhook, bank-transfer approval (`ResolvePaymentProof`) and admin correction (`CorrectPayment`). Inject a new alarm port via the constructor; log with SLF4J in application (precedent: `PublishPhysicalPaymentCompletedUseCase`). An infrastructure adapter owns the `MeterRegistry`. Add a Grafana alerting provisioning file whose LogQL query matches the pinned log contract. Test-first per strict TDD, including a test that locks the log format.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `api/billing/.../application/usecase/PaymentFulfillmentService.java` | Modified | Two alarm points, edge guard for (a) |
| `api/billing/.../application/port/out/<alarm port>` | New | Metric port (name: design) |
| `api/billing/.../infrastructure/<adapter>` | New | Micrometer-backed adapter |
| `api/billing/build.gradle.kts` | Modified (likely) | Micrometer dependency, if the adapter lives in billing |
| `BillingConfiguration.java` (x2), `PaymentVerificationService.java` (legacy ctor) | Modified | Constructor rewiring |
| `observability/grafana/provisioning/alerting/<rule>.yml` | New | Log-based alert rule, no contact point |
| `observability/grafana/provisioning/datasources/loki.yml` | Modified (likely) | Stable `uid` for rule reference |
| `infra/docker/database/docker-compose.yml:95` | Modified | Fix provisioning mount path |
| `openspec/specs/purchase-exception-notification/spec.md` | Modified (delta) | D6 wording |
| `PaymentFulfillmentServiceTest`, `PaymentVerificationServiceTest`, adapter test | Modified/New | Alarm assertions, log-contract lock, regression-lock update |

ArchUnit (`api/billing/.../ArchitectureTest`): application must not depend on infrastructure or Micrometer types; satisfied by the port.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The metric has no consumer today (no Prometheus datasource or registry dependency) | High | Accepted (D2); the log-based rule is the working signal; record the counter as groundwork |
| Alert rule without a contact point goes unwatched | High | Accepted (D5); channel is an explicit follow-up task |
| The log format is now a contract; a wording change silently breaks the alert | Med | Pin message and keys in specs; unit test locks the format |
| Canonical dev stack does not load provisioning (mount bug) | High (verified) | One-line mount fix in scope; verify the rule appears in Grafana |
| First metric sets a project precedent (naming, tags, dependency placement) | Med | Decide explicitly in design; document as convention |
| Constructor change across 5 sites | Low | Mechanical; compile-time enforced |
| Case (b) noise on webhook replays | Low | Accepted (D4); `paymentId` in log for grouping |
| Alarm fires but the transaction later rolls back, then re-fires on retry | Low | Emit after `save`; at-least-once alarm accepted |

## Rollback Plan

Revert the PR. No schema, data, outbox rows, or application configuration keys are introduced; subscription transitions are unchanged. Reverting removes the provisioned rule file; Grafana drops file-provisioned rules on restart. The mount-path fix can be reverted independently. The D6 delta reverts with the spec archive.

## Dependencies

- None blocking. Micrometer is available transitively via `spring-boot-starter-actuator` in `api:app` only. Grafana 11.5.2 supports file-provisioned alert rules.

## Success Criteria

- [ ] Case (a): exactly one alarm (log + metric, reason A) on the first `-> EXCEPTION`; zero on replay when already `EXCEPTION`; zero on happy/activated paths.
- [ ] Case (b): one alarm (reason B) per occurrence; still no subscription is created.
- [ ] A unit test locks the log message format and keys the LogQL rule depends on.
- [ ] The alert rule loads from provisioning in the canonical dev stack and shows **Firing** in the Grafana UI when a matching log line reaches Loki.
- [ ] No outbox row, no email, no migration, no contact point; verified by diff/tests.
- [ ] D6 delta reworded; "Virtual EXCEPTION emits no notification event" still holds.
- [ ] `./gradlew :api:billing:test` and ArchUnit pass; billing coverage gates hold.

## Open Design Questions

1. Metric name, tags (`reason` yes; `paymentId` must NOT be a tag, for cardinality), type (counter), and exposure, including the missing Micrometer/Prometheus registry dependency (the `management.metrics.export.prometheus.enabled` key in `application.yml` is the Spring Boot 2 key).
2. Port name and package; adapter placement (`api:billing` vs `api:app`).
3. Reason vocabulary (e.g. `plan_missing`, `subscription_missing`) shared by log, metric and LogQL.
4. Log message wording and keys (`paymentId`, `subscriptionId`, `planId`, `userId`, `reason`).
5. Exact LogQL selector and rule parameters (stream labels emitted by the OTel -> Loki pipeline, line filter or `logfmt` parse, window, threshold, `for`, `noDataState`).
6. Provisioning file path/shape, datasource `uid`, and confirmation that the mount fix loads it in every compose variant.

## Review Workload Forecast

- Estimated authored changed lines: Java production ~90-110; alert rule YAML + datasource uid + mount fix ~50-70; tests ~160-200 → **~300-380**. Specs (new capability + D6 delta) add ~80-100 if they ride in the same PR.
- Decision needed before apply: No
- Chained PRs recommended: No (the alert rule depends on the log contract; splitting yields no independently useful slice)
- 400-line budget risk: Medium (close to the line, especially if SDD artifacts are counted; still inside the 800-line session budget, single PR)

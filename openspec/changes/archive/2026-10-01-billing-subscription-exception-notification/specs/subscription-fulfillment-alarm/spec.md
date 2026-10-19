# Delta for Subscription Fulfillment Alarm

New capability. Scope: when a settled virtual (subscription) payment ends without access and nothing is emitted today, operators get an alarm (structured log + metric + provisioned log-based Grafana alert rule). No buyer contact, no mail, no outbox event. Exact reason values, log message text, key set, metric name/tags and port name are design-phase decisions; this spec fixes their properties, not their literals.

## ADDED Requirements

### Requirement: Alarm on the first transition into EXCEPTION when the plan is missing

When `PaymentFulfillmentService.ensureSubscription` finds a pending, not-activated subscription whose plan cannot be loaded and persists it as `fulfillmentStatus = EXCEPTION`, the system MUST emit exactly one alarm, but ONLY when the persisted `fulfillmentStatus` was not already `EXCEPTION` before that save. The alarm MUST be emitted after the save.

#### Scenario: First transition into EXCEPTION alarms once

- GIVEN a Completed payment whose subscription is `PENDING` with `fulfillmentStatus != EXCEPTION` and whose plan does not exist
- WHEN fulfillment runs
- THEN the subscription is saved as `EXCEPTION`
- AND exactly one alarm (log + metric) with the plan-missing reason is emitted

#### Scenario: Replay while already EXCEPTION does not re-alarm

- GIVEN a subscription already persisted as `EXCEPTION` and the plan still missing
- WHEN fulfillment runs again (webhook replay, duplicate webhook, retry)
- THEN the subscription remains `EXCEPTION`
- AND no alarm (log or metric) is emitted

#### Scenario: Happy and already-activated paths never alarm

- GIVEN a subscription whose plan exists, or one already activated
- WHEN fulfillment runs
- THEN no alarm is emitted

### Requirement: Alarm on every occurrence of a Completed payment with no subscription row

When `ensureSubscription` finds no subscription row for a Completed payment, it MUST emit one alarm per occurrence, with no deduplication and no persisted state, and MUST NOT create a subscription.

#### Scenario: Missing subscription row alarms each time

- GIVEN a Completed payment with no subscription row
- WHEN fulfillment runs twice for that payment
- THEN two alarms with the subscription-missing reason are emitted
- AND no subscription row is created

### Requirement: Distinct reason per case in log and metric

Each alarm MUST carry a reason identifying its case; the plan-missing and subscription-missing reasons MUST be distinct, stable values, and the log and the metric MUST use the same value for the same case. The payment identifier MUST appear in the log (for grouping) and MUST NOT be a metric tag.

#### Scenario: Reason matches across log and metric

- GIVEN an alarm for each of the two cases
- WHEN the log line and the metric increment are inspected
- THEN each case has a different reason value
- AND the log and metric reason values for the same case are equal

### Requirement: All fulfillment rails are covered

The alarm MUST be raised at the shared `ensureSubscription` chokepoint so webhook verification, bank-transfer proof approval and admin payment correction all produce it.

#### Scenario: Each rail reaches the alarm

- GIVEN a missing-plan condition
- WHEN fulfillment is triggered via webhook verification, via bank-transfer approval, or via admin correction
- THEN the alarm is emitted on each rail under the same edge rules

### Requirement: No change to transitions, events or mail

The change MUST NOT alter subscription state transitions, outbox events or email. It MUST NOT append any outbox row, send any email, or add a migration, table or persisted reason. Recovery MUST be unaffected: a later retry that finds the plan MUST still flip the subscription to `ASSIGNED`.

#### Scenario: Recovery after an alarm still works

- GIVEN a subscription left `EXCEPTION` after a plan-missing alarm
- WHEN a later fulfillment attempt finds the plan
- THEN the subscription becomes `ASSIGNED`
- AND no further alarm is emitted

#### Scenario: Alarm adds no outbox row or mail

- GIVEN any alarm-emitting case
- WHEN the transaction completes
- THEN no outbox row is appended and no email is sent

### Requirement: Log format is a locked contract

Each alarm MUST be one structured `ERROR` log with a fixed message text and key=value fields that include at least `reason` and the payment identifier, and the subscription, plan and user identifiers where known. The fixed text, key names and reason values are consumed by the Grafana LogQL rule, and a test MUST fail if any of them changes.

#### Scenario: Format-lock test fails on drift

- GIVEN the alarm log line for each case
- WHEN the test compares its message and key=value set against the contract
- THEN it passes only if message text, key names and reason values match exactly

### Requirement: Metric emitted through an application port

The metric MUST be emitted through an outbound port in the application layer; Micrometer types MUST only appear in infrastructure. Metric name, tags (including `reason`) and type are design-phase decisions.

#### Scenario: Architecture rules hold

- GIVEN the new port and its infrastructure adapter
- WHEN ArchUnit runs
- THEN `application` has no dependency on infrastructure or Micrometer
- AND the adapter increments the metric with the alarm reason when called

### Requirement: Log-based Grafana alert rule provisioned as code

The repository MUST provision, under `observability/grafana/provisioning/alerting/`, one Grafana alert rule whose query is LogQL on the Loki datasource and matches the locked log contract, covering both reasons. It MUST NOT define a contact point, notification policy or any alert channel, and MUST NOT depend on a Prometheus datasource. The datasource the rule references MUST have a stable identifier.

#### Scenario: Rule loads and fires

- GIVEN the dev stack started via `scripts/dev.sh`
- WHEN a log line matching the contract reaches Loki
- THEN the rule is listed in Grafana and shows Firing in the UI
- AND no notification is sent to any channel

### Requirement: Dev-stack provisioning mount resolves to the real directory

In `infra/docker/database/docker-compose.yml`, the Grafana provisioning mount MUST resolve (relative to the compose file) to the repository's `observability/grafana/provisioning` directory, consistently with its Loki and OTel mounts, so datasource and alert rule load in the dev stack.

#### Scenario: Mount path is correct

- GIVEN the compose file run from `scripts/dev.sh`
- WHEN the Grafana provisioning source path is resolved
- THEN it equals the tracked `observability/grafana/provisioning` directory, not a path under `infra/docker/database/`
- AND the other compose files' mounts are unchanged

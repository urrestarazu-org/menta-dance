# Tasks: fix-presential-purchase-quota-exception

> 10-task breakdown for the presential-purchase / capacity-assignment wiring
> (issue #115). Topologically ordered. One task = one logical commit. Every
> spec Requirement / Scenario owns at least one AC.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~860–1100 LOC (production Java + tests + Javadoc) |
| 400-line budget risk (AGENTS.md) | High — single review of one PR is too dense |
| Chained PRs recommended | **Yes** |
| Suggested split | PR #1 ~Slice 1+2 (foundation + producer) · PR #2 ~Slice 3+4 (consumer + handler + tests + ArchUnit) |
| Delivery strategy | `ask-on-risk` (orchestrator will prompt user on chained-PR vs `size:exception`) |
| Chain strategy | `pending` (orchestrator asks before apply) |
| Forecast gate per task | Largest single task = TASK-004 at ~280 LOC (under 800) |

```text
Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High
```

The proposed chain (mirrors design §10):

1. **PR #1** — TASK-001 → TASK-003 (foundation: shared records, billing outbox
   constants + OUT port + adapter, publish use case + `PaymentVerificationService`
   edit). No behaviour change observable — handler absent, reconciler puts
   every appended row into FAILED/backoff. ~360 LOC.
2. **PR #2** — TASK-004 → TASK-011 (capacity port, consumer use cases, handler
   + adapters, ArchUnit regression, the discrete test rewrite + the new
   integration tests, concurrency extensions, wrap-up). ~500 LOC. Closes
   #115. 800-line single-PR review budget risk sits on this PR.

---

## 1. api:shared records (foundation)

## 1. PaymentCompletedOutboxPayload + CapacityAssignmentCommand
- **ID**: TASK-001
- **Status**: pending
- **Dependencies**: none
- **Estimated LOC**: 80 prod + 60 test = ~140
- **Estimated test LOC**: 60
- **Module(s) touched**: `api:shared`
- **Files (new)**: `api/shared/src/main/java/com/menta/shared/billing/PaymentCompletedOutboxPayload.java`, `api/shared/src/main/java/com/menta/shared/physical/CapacityAssignmentCommand.java`
- **Files (modify)**: none
- **Files (new — tests)**: `api/shared/src/test/java/com/menta/shared/billing/PaymentCompletedOutboxPayloadTest.java`, `api/shared/src/test/java/com/menta/shared/physical/CapacityAssignmentCommandTest.java`
- **New build dependencies**: none (api:shared has no JPA / Spring dep)
- **Test classes**: record ctor validation (UUID-shaped `paymentId`, non-blank strings, `BigDecimal.signum() >= 0`, ISO-8601 UTC `confirmedAt`)
- **Acceptance criteria** (each MUST be independently verifiable):
  - AC1: `PaymentCompletedOutboxPayload` compact constructor throws `IllegalArgumentException` on null UUID-shaped `paymentId`, blank `providerPaymentId`, length >64 for any string field, negative `amount`, 4-char `currency`, non-UTC `confirmedAt`. Maps to spec `presential-purchase-fulfillment` Requirement "Physical payments publish one committed outbox event" (cross-module contract).
  - AC2: `CapacityAssignmentCommand` is a Java record (no JPA, no JSON-only annotation) with fields `sessionId:UUID`, `studentId:UUID`, `paymentId:UUID`; ctor enforces non-null. Maps to design §4.3 cross-module signature.
  - AC3: Both records compile against `:api:shared` only — no `application`, `domain`, `infrastructure`, Spring, or JPA imports leak in. Verified by `./gradlew :api:shared:checkstyleMain checkstyleTest`.
- **Persistence / migration impact**: none (per design §8).
- **Risk notes**: R7 (design §9) — Jackson serializer divergence mitigated by single source of truth.

---

## 2. api:billing outbox constants + OUT port + JPA adapter (foundation for producer)

## 2. BillingOutboxEventTypes + BillingOutboxAppenderPort + BillingOutboxAppender (infra)
- **ID**: TASK-002
- **Status**: pending
- **Dependencies**: TASK-001
- **Estimated LOC**: 140 prod + 60 test = ~200
- **Estimated test LOC**: 60
- **Module(s) touched**: `api:billing`
- **Files (new)**: `api/billing/src/main/java/com/menta/billing/application/contract/BillingOutboxEventTypes.java`, `api/billing/src/main/java/com/menta/billing/application/port/out/BillingOutboxAppenderPort.java`, `api/billing/src/main/java/com/menta/billing/infrastructure/outbox/BillingOutboxRowJpaEntity.java`, `api/billing/src/main/java/com/menta/billing/infrastructure/outbox/BillingOutboxAppender.java`
- **Files (modify)**: none
- **Files (new — tests)**: `api/billing/src/test/java/com/menta/billing/infrastructure/outbox/BillingOutboxAppenderTest.java`
- **New build dependencies**: none (api:billing already has `:api:shared`, `spring-boot-starter-data-jpa`)
- **Test classes**: JUnit 5 + Mockito `@DataJpaTest` confirms `BillingOutboxAppender.append(eventType, aggregateId, payloadJson)` writes the row mapped to `common_outbox_events` (V2 lines 34-50) with `event_type = "billing.PhysicalPaymentCompleted"` and `status = PENDING`.
- **Acceptance criteria** (each MUST be independently verifiable):
  - AC1: `BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED` resolves to literal `"billing.PhysicalPaymentCompleted"` (mirrors `AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED` constant-holder at `api/auth/src/main/java/com/menta/auth/application/contract/AuthOutboxEventTypes.java:38-72`). Maps to spec scenario "Completed physical payment appends one outbox row".
  - AC2: `BillingOutboxAppenderPort.append(eventType, aggregateId, payloadJson)` is annotated `@Transactional(propagation = REQUIRED)` and the only impl `BillingOutboxAppender` writes via `JpaRepository< BillingOutboxRowJpaEntity, Long >`. Maps to design §5.1 after-commit semantics.
  - AC3: `BillingOutboxRowJpaEntity` is annotated `@Entity @Table(name = "common_outbox_events")` with `@Id @GeneratedValue(strategy = IDENTITY)` and a `String eventType` matching `event_type VARCHAR(80)` per V2:38. Hibernate `ddl-auto: validate` MUST NOT fail — verifiable via `./gradlew :api:billing:test`.
  - AC4: Concrete adapter unit test asserts one saved row, `event_type` equality, `aggregate_id` equals `paymentId.toString()`, `payload` is the JSON passed in unchanged.
- **Persistence / migration impact**: none (existing table; Hibernate validates schema only).
- **Risk notes**: design §1 (cross-module JPA note) — two JPA classes (auth's `OutboxRowJpaEntity` and new `BillingOutboxRowJpaEntity`) map the same table. Safe because V2 has no outbound FKs and `event_id ULID UNIQUE` (V2:47) prevents dedupe clashes; tracker R3.

---

## 3. PublishPhysicalPaymentCompletedUseCase + PaymentVerificationService edit + after-commit rollback integration test

## 3. PublishPhysicalPaymentCompletedUseCase + wire into PaymentVerificationService.ensureFulfillment
- **ID**: TASK-003
- **Status**: pending
- **Dependencies**: TASK-001, TASK-002
- **Estimated LOC**: 80 prod (use case + 8-line edit) + 110 test = ~190
- **Estimated test LOC**: 110
- **Module(s) touched**: `api:billing`
- **Files (new)**: `api/billing/src/main/java/com/menta/billing/application/usecase/PublishPhysicalPaymentCompletedUseCase.java`
- **Files (modify)**: `api/billing/src/main/java/com/menta/billing/application/usecase/PaymentVerificationService.java` (replace the documented NO-OP at lines 171-178 — physical branch now calls `publishPhysicalPaymentCompleted.handle(payment)`, virtual branch unchanged)
- **Files (new — tests)**: `api/billing/src/test/java/com/menta/billing/application/usecase/PublishPhysicalPaymentCompletedUseCaseTest.java`, `api/billing/src/test/java/com/menta/billing/integration/outbox/BillingOutboxAppenderAfterCommitIntegrationTest.java`
- **New build dependencies**: none
- **Test classes**:
  - `PublishPhysicalPaymentCompletedUseCaseTest` — Mockito-only. Verifies (a) `handle(physical payment)` calls `TransactionSynchronizationManager.registerSynchronization(...)` registering an `afterCommit` that calls `BillingOutboxAppenderPort.append` with constant + payload; (b) when payment target is `Virtual`, NO synchronisation is registered; (c) the synchronisation fires only on commit, not when the calling transaction is marked `rollbackOnly`.
  - `BillingOutboxAppenderAfterCommitIntegrationTest` — `@SpringBootTest` + `@ActiveProfiles("integration-test")` + Testcontainers MySQL. Test name `payment_rollback_leaves_zero_outbox_and_zero_purchase_rows`. Drives a forced rollback inside `TransactionTemplate.execute(status -> { status.setRollbackOnly(); })` and asserts `common_outbox_events` empty + `billing_purchases` empty. Negative case: commit succeeds, asserts exactly one outbox row.
- **Acceptance criteria** (each MUST be independently verifiable):
  - AC1: After successful test commit, exactly one `common_outbox_events` row exists with `event_type = "billing.PhysicalPaymentCompleted"` and `aggregate_id = paymentId`. Maps to spec scenario "Completed physical payment appends one outbox row".
  - AC2: After forced test rollback, zero `common_outbox_events` rows exist with that event_type. Maps to spec scenario "Rolled-back payment leaves empty outbox and empty purchases".
  - AC3: `PaymentVerificationService.ensureFulfillment` no longer contains the comment "ADR-0028: api:app owns hold conversion and capacity assignment." Verifiable via grep on the file. Maps to design §4 step "Wire".
  - AC4: The new edit is `< 20` lines on `PaymentVerificationService.java` (per proposal §8 "<10-lines edit"; allow 20 to import + comment). Verifiable via `git diff --stat`.
- **Persistence / migration impact**: none.
- **Risk notes**: design R1 (after-commit ordering vs reconciler polling latency, 30s default); mitigation = document expected latency, no design change.

---

## 4. api:billing IN ports + state machine + use cases + unit tests

## 4. PurchaseCreationFromEventPort + MarkPurchaseExceptionPort + use cases + state machine
- **ID**: TASK-004
- **Status**: pending
- **Dependencies**: TASK-001, TASK-002
- **Estimated LOC**: 220 prod + 100 test = ~320
- **Estimated test LOC**: 100
- **Module(s) touched**: `api:billing`
- **Files (new)**: `api/billing/src/main/java/com/menta/billing/application/port/in/PurchaseCreationFromEventPort.java`, `api/billing/src/main/java/com/menta/billing/application/port/in/MarkPurchaseExceptionPort.java`, `api/billing/src/main/java/com/menta/billing/application/usecase/CreatePurchaseFromPaymentEventUseCase.java`, `api/billing/src/main/java/com/menta/billing/application/usecase/MarkPurchaseExceptionUseCase.java`, `api/billing/src/main/java/com/menta/billing/domain/model/Reason.java` (enum: `CAPACITY_BELOW_ASSIGNED`, `UNIQUE_COLLISION`, `HOLD_EXPIRED`, `COVERAGE_CHANGED`, `TARGET_NOT_SCHEDULED`), `api/billing/src/main/java/com/menta/billing/domain/exception/IllegalPurchaseStateTransitionException.java`
- **Files (modify)**: `api/billing/src/main/java/com/menta/billing/infrastructure/persistence/repository/BillingPurchaseJpaRepository.java` (add `findByPaymentId(UUID)` if absent — verify against current head), `api/billing/src/main/java/com/menta/billing/infrastructure/persistence/entity/BillingPurchaseJpaEntity.java` (already present per design — confirm; if missing, add and pair with mapper)
- **Files (new — tests)**: `api/billing/src/test/java/com/menta/billing/application/usecase/CreatePurchaseFromPaymentEventUseCaseTest.java`, `api/billing/src/test/java/com/menta/billing/application/usecase/MarkPurchaseExceptionUseCaseTest.java`, `api/billing/src/test/java/com/menta/billing/unit/domain/PurchaseStateMachineTest.java`
- **New build dependencies**: none
- **Test classes**:
  - `CreatePurchaseFromPaymentEventUseCaseTest` — `Payment` mock + `PurchaseRepository` mock. Verifies: (a) `findByPaymentId` empty → build `Purchase.pendingFulfillment(...)` and `save`; (b) `findByPaymentId` returns existing non-EXCEPTION row → return existing without `save`; (c) `findByPaymentId` returns existing EXCEPTION row → save new `PENDING_FULFILLMENT`; (d) UNIQUE race: `save` raises `DataIntegrityViolationException` → re-fetch and return whatever is there.
  - `MarkPurchaseExceptionUseCaseTest` — `Purchase` mock + `PurchaseRepository` mock. Verifies: (a) `PENDING_FULFILLMENT` → `EXCEPTION` succeeds; (b) `ASSIGNED` → `EXCEPTION` throws `IllegalPurchaseStateTransitionException`; (c) `EXCEPTION` → `EXCEPTION` is a no-op (idempotent); (d) missing purchase throws `PaymentNotFoundException`.
  - `PurchaseStateMachineTest` — pure JUnit. Verifies: `pendingFulfillment(paymentId).assigned().grantsAttendance() == true`, `pendingFulfillment(paymentId).exception().grantsAttendance() == false`, `exception(paymentId).assigned()` throws.
- **Acceptance criteria** (each MUST be independently verifiable):
  - AC1: `CreatePurchaseFromPaymentEventUseCase.handle(PaymentCompletedOutboxPayload)` is idempotent — calling it twice with the same payload returns the same `Purchase` reference (or a row with the same `paymentId`, `version`) and triggers exactly one `INSERT` on the empty path. Maps to spec scenario "Re-delivery with same payment_id is idempotent".
  - AC2: `MarkPurchaseExceptionPort.markException(paymentId, reason)` only accepts transitions where `purchase.getStatus() == PENDING_FULFILLMENT`. Verifiable via the unit test throw-on-ASSIGNED assertion. Maps to design §4.2 state-machine contract.
  - AC3: `PurchaseStateMachineTest` passes — every existing factory (`pendingFulfillment`, `assigned`, `exception`) preserves its current behaviour with `grantsAttendance` semantics. Maps to spec scenario "Residual EXCEPTION still produces 403".
  - AC4: `BillingPurchaseJpaRepository.findByPaymentId(UUID)` exists and is the only DB read path consumed by `CreatePurchaseFromPaymentEventUseCase`. No new `BillingPurchaseJpaRepository.save` introduced — reuses existing one. Verifiable via `grep` of the new test.
- **Persistence / migration impact**: none (V8 already migrated).
- **Risk notes**: design R5 (PENDING→ASSIGNED vs spec wording) — `ASSIGNED` transition is deferred by design (§5.5); do not change purchase exit status here.

---

## 5. api:physical IN port + use case + adapter + repository extension + unit/integration tests

## 5. PhysicalCapacityAssignmentPort + AssignCapacityUseCase + JpaPhysicalCapacityAssignmentAdapter + repo extension
- **ID**: TASK-005
- **Status**: pending
- **Dependencies**: TASK-001
- **Estimated LOC**: 130 prod + 90 test = ~220
- **Estimated test LOC**: 90
- **Module(s) touched**: `api:physical`
- **Files (new)**: `api/physical/src/main/java/com/menta/physical/application/port/in/PhysicalCapacityAssignmentPort.java`, `api/physical/src/main/java/com/menta/physical/application/usecase/AssignCapacityUseCase.java`, `api/physical/src/main/java/com/menta/physical/application/usecase/AssignmentOutcome.java` (sealed: `ASSIGNED`, `RACE_LOST`), `api/physical/src/main/java/com/menta/physical/infrastructure/adapter/JpaPhysicalCapacityAssignmentAdapter.java`
- **Files (modify)**: `api/physical/src/main/java/com/menta/physical/infrastructure/persistence/repository/PhysicalCapacityAssignmentJpaRepository.java` (add `boolean existsBySessionIdAndStudentId(UUID, UUID)` if absent; verify against current head)
- **Files (new — tests)**: `api/physical/src/test/java/com/menta/physical/application/usecase/AssignCapacityUseCaseTest.java`, `api/physical/src/test/java/com/menta/physical/infrastructure/persistence/adapter/JpaPhysicalCapacityAssignmentAdapterTest.java`
- **New build dependencies**: none (api:physical already has `:api:shared`, `spring-boot-starter-data-jpa`)
- **Test classes**:
  - `AssignCapacityUseCaseTest` — Mockito only, `@Mock` `PhysicalSessionJpaRepository`, `PhysicalCapacityAssignmentJpaRepository`. Verifies: (a) read returns `assignedSpots < capacity` and `existsBySessionIdAndStudentId == false` → INSERT is called and `AssignmentOutcome.ASSIGNED` returned; (b) read returns `assignedSpots >= capacity` → NO INSERT attempted, `CapacityBelowAssignedException` thrown; (c) INSERT raises `DataIntegrityViolationException` against V7:44 UNIQUE → rethrown as `CapacityBelowAssignedException`.
  - `JpaPhysicalCapacityAssignmentAdapterTest` — JUnit 5 + Mockito with transaction-template spy. Asserts `TransactionSynchronizationManager.getCurrentTransactionName()` snapshot is non-null (single-tx REQUIRED propagation respected). Verifies propagation via Spring `TransactionTemplate(PROPAGATION_REQUIRED)`.
- **Acceptance criteria** (each MUST be independently verifiable):
  - AC1: `JpaPhysicalCapacityAssignmentAdapter.assign(CapacityAssignmentCommand)` reads `physical_sessions.capacity` and current assignment count in the SAME transaction then INSERTs or throws. Fail-closed on invariant trip; no INSERT attempted on failure. Maps to spec scenario "Capacity invariant trips — Purchase flips to EXCEPTION".
  - AC2: `CapacityBelowAssignedException` is the SOLE exception type emitted on both (a) read-time invariant trip and (b) UNIQUE-key collision. Verifiable via the unit test signatures. Maps to design §5.3.
  - AC3: `AssignmentOutcome` is sealed; only `ASSIGNED` and `RACE_LOST` permitted; producer cannot accidentally widen the result set. Maps to design §4.3.
  - AC4: `PhysicalCapacityAssignmentPort.assign(...)` does NOT introduce a public write method on the existing out-port `PhysicalCapacityAssignmentRepository` — that interface stays read-only. Verifiable via `git grep PhysicalCapacityAssignmentRepository`. Maps to design §1 (line 60, 130).
  - AC5: Existing `PhysicalSessionManagementIntegrationTest.java:239-264` still passes bit-for-bit (the new write path must honour the capacity invariant). Pre-existing test = `N/A` for new actions, just must not regress.
- **Persistence / migration impact**: none.
- **Risk notes**: design R3 (CodeGraph index staleness); ArchUnit regression checked in TASK-008.

---

## 6. api:physical capacity invariant integration test

## 6. AssignCapacityAdapterIntegrationTest (capacity-respecting end-to-end)
- **ID**: TASK-006
- **Status**: pending
- **Dependencies**: TASK-005
- **Estimated LOC**: 0 prod + 120 test = ~120
- **Estimated test LOC**: 120
- **Module(s) touched**: `api:physical`
- **Files (new)**: `api/physical/src/test/java/com/menta/physical/integration/persistence/adapter/AssignCapacityAdapterIntegrationTest.java`
- **Files (modify)**: none
- **Files (new — tests)**: `api/physical/src/test/java/com/menta/physical/integration/persistence/adapter/AssignCapacityAdapterIntegrationTest.java`
- **New build dependencies**: none
- **Test classes**:
  - `capacity_available_one_insert_succeeds`: seeds `physical_sessions.capacity = 2`, zero assignments, calls adapter → exactly 1 assignment row + `AssignmentOutcome.ASSIGNED`.
  - `capacity_full_zero_inserts_and_exception`: seeds `capacity = 1`, one existing assignment → adapter throws `CapacityBelowAssignedException`, zero new rows.
  - `unique_race_triggers_rethrow`: seeds capacity available, threads A and B race the adapter via `CountDownLatch` with distinct `studentId`s, same `sessionId` → exactly one INSERT succeeds, the OTHER path raises `CapacityBelowAssignedException`. Mirrors design §2.4.
- **Acceptance criteria** (each MUST be independently verifiable):
  - AC1: For `capacity = 1` and zero rows, exactly one of two concurrent `assign(...)` invocations returns `ASSIGNED`, the other raises `CapacityBelowAssignedException`. Maps to spec scenario "Concurrent last-spot race — exactly one ASSIGNED and one EXCEPTION".
  - AC2: For `capacity = 1` and one existing `(sessionId, studentId)`, adapter throws BEFORE any INSERT — verifiable via DB row count = 1 (unchanged).
  - AC3: `@DataJpaTest` profile is satisfied — `./gradlew :api:physical:test --tests "*AssignCapacityAdapterIntegrationTest*"` is green.
- **Persistence / migration impact**: none.
- **Risk notes**: design R3 (concurrency test is the loud-fail honest pressure).

---

## 7. api:app typed adapters + outbox handler + handler unit tests (the orchestration cluster)

## 7. PhysicalCapacityAssignmentAdapter + MarkPurchaseExceptionAdapter + PhysicalCapacityAssignmentOutboxEventHandler + handler tests
- **ID**: TASK-007
- **Status**: completed
- **Dependencies**: TASK-002, TASK-003, TASK-004, TASK-005
- **Estimated LOC**: 130 prod + 150 test = ~280
- **Estimated test LOC**: 150
- **Module(s) touched**: `api:app`, indirectly `api:billing` (callable imports)
- **Files (new)**: `api/app/src/main/java/com/menta/app/billing/PhysicalCapacityAssignmentAdapter.java`, `api/app/src/main/java/com/menta/app/billing/MarkPurchaseExceptionAdapter.java`, `api/app/src/main/java/com/menta/app/outbox/PhysicalCapacityAssignmentOutboxEventHandler.java`
- **Files (modify)**: none
- **Files (new — tests)**: `api/app/src/test/java/com/menta/app/outbox/PhysicalCapacityAssignmentOutboxEventHandlerTest.java`, `api/app/src/test/java/com/menta/app/outbox/PhysicalCapacityAssignmentOutboxEventHandler_NoHandlerTest.java`, `api/app/src/test/java/com/menta/app/outbox/OutboxReconciliationWorker_PresentialFailurePreservesBackoffTest.java`
- **New build dependencies**: none (api:app build.gradle.kts already declares `:api:billing` and `:api:physical`)
- **Test classes**:
  - `PhysicalCapacityAssignmentOutboxEventHandlerTest` — Mockito-only; `@Mock` both typed adapters. Maps the handler's table:
    - `supports(BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED) == true`; `supports(AuthOutboxEventTypes.*)` returns false for every other known event type.
    - `handle(row)` happy path: ObjectMapper reads payload → calls `createPurchaseFromEvent(payload)` → calls `assign(cmd)` → returns void.
    - `handle(row)` capacity-trip path: `assign` throws `CapacityBelowAssignedException` → `markException(paymentId, CAPACITY_BELOW_ASSIGNED)` is called; handler returns void.
    - Re-delivery idempotent: same `paymentId` → `createPurchaseFromEvent` returns existing row, `assign` succeeds (or short-circuits via existing UNIQUE → EXCEPTION).
  - `PhysicalCapacityAssignmentOutboxEventHandler_NoHandlerTest` — Worker given only `PasswordResetOutboxEventHandler` + auth handlers (no presential handler bean). Builds an `OutboxRowJpaEntity` with `event_type = BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED`, calls `worker.process(row)`, asserts `row.getStatus() == FAILED`, `row.getAttempts() == 1`, and the literal error message `"No handler registered for event type: billing.PhysicalPaymentCompleted"`.
  - `OutboxReconciliationWorker_PresentialFailurePreservesBackoffTest` — When the handler PROPAGATES `CapacityBelowAssignedException` (test forces unhandled exception path), the worker still drives `FAILED/backoff` (i.e. proves the catch-routing into `MarkPurchaseExceptionPort` is the only path that writes EXCEPTION, never the propagate-out path).
- **Acceptance criteria** (each MUST be independently verifiable):
  - AC1: `PhysicalCapacityAssignmentAdapter` mirrors `PhysicalCourseAvailabilityAdapter` (api/app/src/main/java/com/menta/app/billing/PhysicalCourseAvailabilityAdapter.java:25-46) exactly — single constructor + delegate call to api:physical IN port. Maps to design §1 cross-module wiring row.
  - AC2: `MarkPurchaseExceptionAdapter` follows the same pattern. Single callable delegating to `MarkPurchaseExceptionPort`.
  - AC3: `PhysicalCapacityAssignmentOutboxEventHandler` declares `@Component` and Spring component scan picks it up alongside existing handlers (`ActivationOutboxEventHandler`, `PasswordResetOutboxEventHandler`, etc.). Verifiable via `./gradlew :api:app:test --tests "*ApplicationContext*"` (existing context test).
  - AC4: Handler maps `billing.PhysicalPaymentCompleted` exclusively — `supports("auth.AccountActivationRequested") == false`. Maps to spec scenario "Reconciler rejects an event with no registered handler".
  - AC5: No ArchUnit regression — `com.menta.app.application` does NOT import `com.menta.physical.infrastructure.*`. (Hard-verified by TASK-008 below; this AC just asserts the import is absent in this task's own module.)
- **Persistence / migration impact**: none.
- **Risk notes**: design R9 (handler propagates unexpected failures so worker's FAILED/backoff lifecycle kicks in).

---

## 8. ArchUnit regression — forbid api:app from depending on api:physical.infrastructure

## 8. api:app ArchitectureTest + extend api:physical ArchitectureTest
- **ID**: TASK-008
- **Status**: completed
- **Dependencies**: TASK-007
- **Estimated LOC**: 0 prod + 80 test = ~80
- **Estimated test LOC**: 80
- **Module(s) touched**: `api:app` (new test class); `api:physical` (extend existing test)
- **Files (new)**: `api/app/src/test/java/com/menta/app/ArchitectureTest.java`
- **Files (modify)**: `api/physical/src/test/java/com/menta/physical/ArchitectureTest.java` (extend with new rule)
- **New build dependencies**: none (ArchUnit 1.3.0 already on testImplementation)
- **Test classes**:
  - `app_should_not_depend_on_physical_infrastructure` — `noClasses().that().resideInAPackage("com.menta.app..").should().dependOnClassesThat().resideInAPackage("com.menta.physical.infrastructure..")`. Pattern matches api/auth `ArchitectureTest.java` verbatim.
  - `app_adapters_follow_cross_module_pattern` — `classes().that().haveSimpleName("PhysicalCapacityAssignmentAdapter").or().haveSimpleName("MarkPurchaseExceptionAdapter").should().resideInAPackage("com.menta.app.billing")` and `.should().dependOnClassesThat().resideInAPackage("com.menta.physical.application.port.in")`.
  - Extension on `api/physical/ArchitectureTest`: `physical_application_port_in_is_the_only_bridge` — `classes().that().haveSimpleName("PhysicalCapacityAssignmentPort").should().resideInAPackage("com.menta.physical.application.port.in")`.
- **Acceptance criteria** (each MUST be independently verifiable):
  - AC1: `./gradlew :api:app:test --tests "*ArchitectureTest*"` is green with the new rule. Maps to proposal §6 ArchUnit regression.
  - AC2: `./gradlew :api:physical:test --tests "*ArchitectureTest*"` is green with the new rule.
  - AC3: A `grep` over `api/app/src/main` returns zero matches for `com.menta.physical.infrastructure` imports — code-level proof of compliance.
- **Persistence / migration impact**: none.
- **Risk notes**: design R3 (CodeGraph index after edits; `sdd-apply` chains the sync after merge).

---

## 9. PaymentWebhookIntegrationTest rewrite + idempotency + EXCEPTION integration (THE discrete damage-limited task)

## 9. PaymentWebhookIntegrationTest.java:195, 215 rewrite + 2 new sibling tests
- **ID**: TASK-009
- **Status**: pending
- **Dependencies**: TASK-007
- **Estimated LOC**: 0 prod-as-new + 220 test (rewrite + new tests) = ~220
- **Estimated test LOC**: 220
- **Module(s) touched**: `api:app`
- **Files (modify)**: `api/app/src/test/java/com/menta/app/integration/billing/PaymentWebhookIntegrationTest.java`
- **Files (new — tests)**: additions inline to the same file (2 new methods)
- **New build dependencies**: none
- **Test classes**:
  - Rewrite 1 — test `the_worker_confirms_a_matching_physical_payment_without_creating_billing_fulfillment` (line 180-196). Replace line 195 `assertThat(purchaseRepository.findAll()).isEmpty();` with `assertThat(purchaseRepository.findAll()).hasSize(1);` AND `extractStatus().isEqualTo("PENDING_FULFILLMENT")`. Two rewrites to the same test is fine as long as `worker.process(row)` invoked once yields exactly one purchase row. Maps to spec scenario "First-time event creates a PENDING_FULFILLMENT purchase".
  - Rewrite 2 — test `a_physical_payment_completion_leaves_capacity_orchestration_to_app` (line 199-216). Replace line 215 with `hasSize(1)` AND assert `physicalCapacityAssignmentRepository.findBySessionIdAndStudentId(sessionId, studentId)` returns the seeded pair. Map to spec scenario "Capacity available — the Assignment unblocks QR".
  - Untouched — `an_expired_provider_status_never_creates_billing_fulfillment` (line 218-234) keeps assertion `isEmpty()` at line 233 (EXPIRED is a terminal-residual path).
  - Untouched — `an_approved_status_with_mismatched_amount_goes_to_reconciliation_required_never_completed` (line 236-255) keeps `isEmpty()` at line 252-253 (RECONCILIATION_REQUIRED).
  - NEW test `webhook_redelivery_is_idempotent_and_does_not_create_a_second_purchase_row` — invokes `worker.process(row)` twice for the same `dedupe_key` (`mp-idempotent:req-1`) and asserts `purchaseRepository.findAll().hasSize(1)`, `physicalCapacityAssignmentRepository.findAll().hasSize(1)`, `webhookInboxRepository` zero `WebhookInboxStatus.FAILED` rows. Maps to spec scenario "Re-delivery with same payment_id is idempotent".
  - NEW test `webhook_handler_trips_capacity_invariant_flipping_purchase_to_EXCEPTION` — seeds session with `capacity = 1` and one existing assignment, runs the chain end-to-end with a different `studentId`, asserts `billing_purchases.status == "EXCEPTION"` for that payment, `physical_capacity_assignments` row count unchanged, `billing_payments.status_type == "COMPLETED"` (ADR-0039 distinction).
- **Acceptance criteria** (each MUST be independently verifiable):
  - AC1: Line 195 assertion IS rewritten to `hasSize(1)` (verifiable by `git diff` after this task lands). Maps to spec scenario "First-time event creates a PENDING_FULFILLMENT purchase".
  - AC2: Line 215 assertion IS rewritten to `hasSize(1)` with the seeded capacity assertion. Maps to spec scenario "Capacity available — the Assignment unblocks QR".
  - AC3: The two new tests run end-to-end and both pass against an integration-test profile with Testcontainers MySQL (`./gradlew :api:app:test --tests "*PaymentWebhookIntegrationTest*"`). Maps to spec scenarios "Re-delivery with same payment_id is idempotent" and "Capacity invariant trips — Purchase flips to EXCEPTION".
  - AC4: A `git rev-parse` on the test file BEFORE this task lands proves the bug-encoded expectations (`isEmpty()` where `hasSize(1)` is correct); the diff is auditable.
  - AC5: Discrete commit — no other test file modified in this commit. Verified via `git show --stat` — must list ONLY `PaymentWebhookIntegrationTest.java`.
- **Persistence / migration impact**: none.
- **Risk notes**: AGENTS.md rationale for isolation: "a partial implementation can't green the build" if this rewrite is merged with TASK-007 — that's why TASK-009 is a separate task, separate commit.

---

## 10. PhysicalSessionManagementIntegrationTest concurrency extensions + NEW PresentialPurchaseExceptionPathIntegrationTest + FINISH

## 10. Capacity invariant concurrency extension + dedicated EXCEPTION integration + verification sweep
- **ID**: TASK-010
- **Status**: pending
- **Dependencies**: TASK-006, TASK-007, TASK-008
- **Estimated LOC**: 0 prod-as-new + 200 test (extends + new) + 40 wrap-up = ~240
- **Estimated test LOC**: 200
- **Module(s) touched**: `api:app`, `api:physical` (integration test extend), `README/archive` (wrap-up)
- **Files (modify)**: `api/app/src/test/java/com/menta/app/integration/physical/PhysicalSessionManagementIntegrationTest.java` (extend with 2 new test methods, do NOT touch lines 239-264 which is the existing capacity-invariant contract test)
- **Files (new — tests)**: `api/app/src/test/java/com/menta/app/integration/billing/PresentialPurchaseExceptionPathIntegrationTest.java`
- **Files (modify — wrap-up)**: `openspec/changes/fix-presential-purchase-quota-exception/tasks.md` is closed by archive phase, no edit; capture Learnings for `sdd-archive`.
- **New build dependencies**: none
- **Test classes**:
  - `payment_verification_driven_assign_honors_capacity_invariant_for_one_payment` — existing test lines 239-264 stay intact; this new test seeds `physical_sessions.capacity = 1`, drives the full chain `WebhookVerificationWorker.process(...)` → `BillingOutboxAppender.append(...)` → `OutboxReconciliationWorker.process(...)` → handler. Asserts exactly ONE row in `physical_capacity_assignments`, `billing_purchases.status == "PENDING_FULFILLMENT"`, and the QR endpoint returns 200 with `qrCredentials`. Maps to spec scenario "Capacity available — the Assignment unblocks QR".
  - `concurrent_payments_for_same_session_capacity_one_resolves_one_is_EXCEPTION` — parallelizes two distinct `payment_id`s targeting same `sessionId` via `CountDownLatch`. Asserts one ASSIGNED + one EXCEPTION + exactly one `physical_capacity_assignments` row. Maps to spec scenario "Concurrent last-spot race".
  - `PresentialPurchaseExceptionPathIntegrationTest` — capacity-trip residual path, behaviour profile `integration-test`, Testcontainers MySQL. Seeds `capacity = 1` + one existing assignment + fresh Payment for a different `studentId`. Drives the chain end-to-end (no `@MockBean` for the capacity adapter — production code runs). Asserts `billing_purchases.status == "EXCEPTION"`, `physical_capacity_assignments.findAll()` has unchanged count, `billing_payments.status_type == "COMPLETED"`. Maps to spec scenarios "Capacity invariant trips — Purchase flips to EXCEPTION" + ADR-0028 "Hold-expired / monthly-coverage-changed residual flips to EXCEPTION" (the test framework asserts the same shape across all three residual cases via parameterized runs).
  - **Wrap-up pass** — `./gradlew :api:app:check`, `./gradlew :api:check`, `./gradlew jacocoTestReport`. Capture the Learnings section from the design (which itself came from proposal §8 / §11) into the eventual archive `openspec/changes/fix-presential-purchase-quota-exception/` Learnings block.
- **Acceptance criteria** (each MUST be independently verifiable):
  - AC1: Existing test lines 239-264 in `PhysicalSessionManagementIntegrationTest.java` are BIT-FOR-BIT unchanged (verifiable via `git diff --select-rename=0 --stat`). Maps to design §7.2.3 "stays BIT-FOR-BIT unchanged".
  - AC2: `./gradlew :api:app:test --tests "*PhysicalSessionManagementIntegrationTest*"` is green with TWO additional tests (capacity-payment-driven + concurrent-race).
  - AC3: `./gradlew :api:app:test --tests "*PresentialPurchaseExceptionPathIntegrationTest*"` is green.
  - AC4: `./gradlew :api:check` exits green and emits zero new ArchUnit warnings. Spec scenario "ArchUnit regression".
  - AC5: `./gradlew jacocoTestCoverageVerification` confirms `com.menta.billing.application.*` >= 1.00 (per `api/billing/build.gradle.kts:55-58` registration) and `com.menta.billing.domain.*` >= 1.00.
- **Persistence / migration impact**: none.
- **Risk notes**: design R4 (coverage profile + JaCoCo verification); design R5 (QR gate works without ASSIGNED status — the test asserts the gate via path b, capacity row presence).

---

## Coverage Matrix

Every spec Requirement / Scenario is owned by at least one TASK. Each row
maps a spec artefact to the TASK that exercises it.

| Spec source | Requirement / Scenario | Owning TASK(s) |
|-------------|------------------------|---------------|
| `presential-purchase-fulfillment/spec.md` | "Physical payments publish one committed outbox event" | TASK-002 (constants + adapter), TASK-003 (publish use case + integration test) |
| `presential-purchase-fulfillment/spec.md` | Scenario: Completed physical payment appends one outbox row | TASK-002 (AC4), TASK-003 (AC1, AC4) |
| `presential-purchase-fulfillment/spec.md` | Scenario: Completed virtual payment publishes no physical event | TASK-003 (AC negative case in unit test) |
| `presential-purchase-fulfillment/spec.md` | "Payment rollback writes no ghost event or purchase" | TASK-003 (rollback integration test) |
| `presential-purchase-fulfillment/spec.md` | Scenario: Rolled-back payment leaves empty outbox and empty purchases | TASK-003 (AC2) |
| `presential-purchase-fulfillment/spec.md` | "Outbox handler creates one Purchase per payment (idempotent)" | TASK-004 (use case + unit test), TASK-007 (handler) |
| `presential-purchase-fulfillment/spec.md` | Scenario: First-time event creates a PENDING_FULFILLMENT purchase | TASK-004 (AC1), TASK-009 (Rewrite 1) |
| `presential-purchase-fulfillment/spec.md` | Scenario: Re-delivery with same payment_id is idempotent | TASK-004 (AC1 re-delivery branch), TASK-007 (handler happy-path + idempotent scenario), TASK-009 (NEW redelivery test) |
| `presential-purchase-fulfillment/spec.md` | "Successful assignCapacity makes a Purchase ASSIGNABLE" | TASK-005 (adapter + unit), TASK-006 (integration), TASK-009 (Rewrite 2) |
| `presential-purchase-fulfillment/spec.md` | Scenario: Capacity available — the Assignment unblocks QR | TASK-005 (AC1 happy path), TASK-006 (AC1 capacity_available_one_insert_succeeds), TASK-009 (Rewrite 2), TASK-010 (NEW capacity-payment-driven test) |
| `presential-purchase-fulfillment/spec.md` | "Residual EXCEPTION preserves the three documented failure modes" | TASK-004 (state machine + AC2 refuses ASSIGNED→EXCEPTION), TASK-005 (CapacityBelowAssignedException), TASK-007 (handler capacity-trip path), TASK-009 (NEW EXCEPTION test), TASK-010 (NEW PresentialPurchaseExceptionPathIntegrationTest) |
| `presential-purchase-fulfillment/spec.md` | Scenario: Capacity invariant trips — Purchase flips to EXCEPTION | TASK-005 (AC2), TASK-006 (AC2 capacity_full_zero_inserts), TASK-007 (capacity-trip path), TASK-009 (NEW EXCEPTION test), TASK-010 (PresentialPurchaseExceptionPathIntegrationTest) |
| `presential-purchase-fulfillment/spec.md` | Scenario: UNIQUE race on (sessionId, studentId) routes to EXCEPTION | TASK-005 (AC2 UNIQUE branch), TASK-006 (AC3 unique_race_triggers_rethrow), TASK-010 (concurrent race) |
| `presential-purchase-fulfillment/spec.md` | Scenario: Hold-expired / monthly-coverage-changed residual flips to EXCEPTION | TASK-010 (PresentialPurchaseExceptionPathIntegrationTest AC negative case) |
| `presential-purchase-fulfillment/spec.md` | Scenario: Concurrent last-spot race — exactly one ASSIGNED and one EXCEPTION | TASK-006 (AC1 concurrent races), TASK-010 (concurrent_payments_for_same_session_capacity_one_resolves_one_is_EXCEPTION) |
| `presential-purchase-fulfillment/spec.md` | "Absent outbox handler fails loud — no silent drop" | TASK-007 (missing-handler test), TASK-010 (./gradlew :api:app:test green on missing-handler test) |
| `presential-purchase-fulfillment/spec.md` | Scenario: Reconciler rejects an event with no registered handler | TASK-007 (NoHandlerTest AC4), TASK-008 (ArchUnit regression as back-stop) |
| `physical-checkin/spec.md` (delta) | "Paid presential students are eligible for the check-in gate" | TASK-010 (capacity-payment-driven test asserts QR endpoint returns 200) |
| `physical-checkin/spec.md` (delta) | Scenario: Paying student passes the confirmed-assignment check | TASK-006 (AC1 + capacity_available integration), TASK-010 (capacity-payment-driven test) |
| `physical-checkin/spec.md` (delta) | Scenario: Non-paying student without an Assignment still receives 403 | TASK-010 (no gateway change — pre-existing 403 path remains) |
| `physical-checkin/spec.md` (delta) | Scenario: EXCEPTION residual still produces 403 — paid student never gets a seat | TASK-009 (NEW EXCEPTION test asserts `status = EXCEPTION` and capacity row count unchanged), TASK-010 (PresentialPurchaseExceptionPathIntegrationTest) |

---

## Work-Unit Commit Map (one task = one logical commit)

| Commit | TASK | Verifiable single line |
|--------|------|-----------------------|
| 1 | TASK-001 | shared payload + command records + their unit tests |
| 2 | TASK-002 | billing outbox constants + OUT port + JPA adapter + adapter unit test |
| 3 | TASK-003 | publish use case + PaymentVerificationService edit + after-commit rollback integration test |
| 4 | TASK-004 | IN ports + state machine + use cases + use case unit tests |
| 5 | TASK-005 | physical IN port + use case + adapter + repo extension + adapter unit test |
| 6 | TASK-006 | physical capacity invariant integration test |
| 7 | TASK-007 | api:app typed adapters + outbox handler + handler unit tests (3 files) |
| 8 | TASK-008 | ArchUnit regression (new api:app + extended api:physical tests) |
| 9 | TASK-009 | PaymentWebhookIntegrationTest.java:195, 215 rewrite + 2 new sibling tests (single file) |
| 10 | TASK-010 | PhysicalSessionManagementIntegrationTest extensions + new exception integration + wrap-up |

PR #1 (chained first slice): commits 1, 2, 3 (no observable behaviour change —
handler absent ⇒ events sit in FAILED/backoff).
PR #2 (chained second slice): commits 4, 5, 6, 7, 8, 9, 10 (the behaviour-active
slice). 800-line review budget risk per AGENTS.md sits on PR #2; if the
`ask-on-risk` orchestrator decides to ask, the user accepts either the chain
as described OR `size:exception` for a single PR.

---

## Coverage profile targets (per `api/billing/build.gradle.kts`)

| Module / package | Target | Enforced by |
|------------------|--------|-------------|
| `com.menta.billing.domain.*` | 1.00 LINE | `jacocoDomainApplicationCoverageVerification` |
| `com.menta.billing.application.*` (NEW use cases) | 1.00 LINE | `jacocoDomainApplicationCoverageVerification` |
| `com.menta.billing.infrastructure.*` | 0.85 LINE | `jacocoInfrastructureCoverageVerification` |
| `com.menta.physical.*` | 0.80 LINE | physical module's existing JaCoCo rule (80% policy) |

TASK-005 + TASK-006 cover `api:physical.application.*` and the adapter
sufficiently for the 80% floor. TASK-004 + TASK-003 cover `api:billing.application`
+ the publish use case for the 1.00 floor.

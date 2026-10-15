# Tasks: Physical Assignment Type (#521)

Spec shorthand: **[T]** = `specs/physical-assignment-type/spec.md`, **[R]** = `specs/physical-assignment-outbox-routing/spec.md`, **[H]** = `specs/physical-capacity-hold/spec.md`. Path prefixes: `api/shared/.../` = `api/shared/src/{main,test}/java/com/menta/shared/`, `api/physical/.../` = `api/physical/src/{main,test}/java/com/menta/physical/`, `api/app/.../` = `api/app/src/{main,test}/java/com/menta/app/`, `api/billing/.../` = `api/billing/src/{main,test}/java/com/menta/billing/`.

## Review Workload Forecast

| Area | Est. changed lines | Phase |
|---|---|---|
| shared (type, commands, tests) | ~50 | 1 |
| physical main (writer, use cases, port, entity, adapter) | ~70 | 1 |
| physical tests (incl. ~35 entity-ctor call sites, 2 lines each) | ~130 | 1 |
| migration + rollback + migration IT | ~150 | 2 |
| billing (Reason, tests, docs) | ~35 | 3 |
| app handler + mapper | ~75 | 4 |
| app unit tests (handler, mapper) | ~125 | 4 |
| app ITs (purchase + my-sessions) | ~110 | 4 / 5 |
| read side main (projection to Item) | included in physical main | 5 |
| OpenAPI / Bruno / docs | ~30 | 6 |
| **Total** | **~775** | |

Decision (updated at apply start, user-approved 2026-10-15): the code forecast (~775) plus ~627 lines of openspec artifacts exceeds the 800-line review budget, so delivery is three PRs, all against `develop`:

- **PR 0 (docs)**: the `openspec/changes/physical-assignment-type/` artifacts only (proposal, design, specs, tasks). Markdown, no code.
- **PR 1 (write path)**: Phases 1-4 and 7 gates: shared + physical write path + V26 + handler + billing `Reason` + demo seed. Compiles and ships independently (entity column and ctor change are atomic with the writer).
- **PR 2 (read side + contracts)**: Phases 5-6: projection to `MySessionsResponse.Item`, my-sessions tests/IT, OpenAPI, Bruno, docs. Depends on PR 1 (needs the persisted column).

The running diff (`git diff --shortstat origin/develop...HEAD`) is checked at the end of Phase 4 and Phase 5; if PR 1 passes ~800 lines, move work to PR 2 or request `size:exception`.

Phases 1 and 2 cannot be split from each other or from the entity ctor change: the entity requires the column (create-drop ITs and Flyway must agree), and the ctor change touches every call site.

## Phase 1: Shared type, commands, physical write path (strict RED then GREEN)

Order is sequential: the command and port signature changes break compilation across modules, so RED tests are written against the new signatures and GREEN lands as one compilable unit.

- [ ] 1.1 RED: `api/shared/.../physical/CapacityAssignmentCommandTest` and `MultiSessionCapacityAssignmentCommandTest`: null `assignmentType` throws NPE; accessor exposes the value. Proves [T] "A neutral AssignmentType exists in shared".
- [ ] 1.2 GREEN: create `api/shared/.../shared/physical/AssignmentType.java` (`MONTHLY`, `INDIVIDUAL`); append `AssignmentType assignmentType` as last component of `CapacityAssignmentCommand` and `MultiSessionCapacityAssignmentCommand` with `requireNonNull` in the compact ctor. Update shared test fixtures.
- [ ] 1.3 RED: `api/physical/.../application/usecase/AssignCapacityUseCaseTest` and `ConvertCapacityHoldUseCaseTest` verify `writer.assertAssignment(session, student, type)` receives the type; `CreateCapacityHoldUseCaseTest` verifies `convertAll(paymentId, studentId, type)` delegation. Proves [T] "Both write paths persist the resolved type" (legacy `assignAll` persists command type) and [H] "First conversion produces one typed assignment per held session".
- [ ] 1.4 RED: `JpaPhysicalCapacityAssignmentAdapterTest` persists the type; `PhysicalCapacityAssignmentJpaEntityTest` covers the `getAssignmentType()` getter. Proves [T] "Held MONTHLY / INDIVIDUAL purchase persists ...".
- [ ] 1.5 GREEN: `PhysicalCapacityAssignmentWriter.assertAssignment(UUID, UUID, AssignmentType)`; `JpaPhysicalCapacityAssignmentAdapter`; `PhysicalCapacityHoldPort.convertAll(UUID, UUID, AssignmentType)`; `AssignCapacityUseCase`, `ConvertCapacityHoldUseCase`, `CreateCapacityHoldUseCase` thread the type. Entity `PhysicalCapacityAssignmentJpaEntity`: 5-arg ctor replaces 4-arg, field `@Enumerated(STRING) @Column(name="assignment_type", nullable=false, updatable=false, columnDefinition="VARCHAR(20)")`, hand-written getter (no Lombok).
- [ ] 1.6 GREEN (mechanical): update all ~35 test call sites of the entity 4-arg ctor to the 5-arg form (incl. `PhysicalCourseAvailabilityPortImpl` test helper; main class unchanged). Proves [H] "Redelivered conversion ... no-op" regressions stay green.
- [ ] 1.7 RED+GREEN: `api/physical/.../demo/DemoPhysicalCatalogFixture` seeds `INDIVIDUAL`; add/adjust its test. Proves [T] "Demo seed assigns INDIVIDUAL" / "Seeded assignments carry INDIVIDUAL".
- [ ] 1.8 Gate: `./gradlew :api:shared:test :api:physical:test` green (app and billing still reference old signatures until Phase 4; `:api:app` compile is verified in Phase 4).

## Phase 2: V26 migration, rollback, migration IT (parallel with Phase 3; needs 1.5 only for entity alignment)

- [ ] 2.1 RED: new `api/app/src/test/java/com/menta/app/.../PhysicalAssignmentTypeMigrationIntegrationTest` (model on `PhysicalCapacityHoldMigrationIntegrationTest`): migrate to 25, insert a row with `FOREIGN_KEY_CHECKS=0`, migrate to 26; assert row is `MONTHLY`, `is_nullable=NO`, `column_default IS NULL`, and an insert omitting `assignment_type` fails. Proves [T] "Existing rows are labelled MONTHLY", "Insert without a type fails", "Default is dropped".
- [ ] 2.2 GREEN: `api/app/src/main/resources/db/migration/V26__physical_assignment_type.sql` (ADD COLUMN `VARCHAR(20) NOT NULL DEFAULT 'MONTHLY' AFTER student_id`, then `DROP DEFAULT`; header comment states the legacy-assumption caveat and that V25 is head).
- [ ] 2.3 RED: rollback test in the same IT: target 26, then combined migration + rollback locations with `.target("26.1")` and `.ignoreMigrationPatterns("*:ignored")` (V20_2 and V21 sit below 26 and are unapplied, so the ignore pattern is required); assert the column is gone. Proves [T] "Rollback removes the column".
- [ ] 2.4 GREEN: `api/app/src/main/resources/db/rollback/V26_1__revert_physical_assignment_type.sql` (`DROP COLUMN assignment_type`; operator-only header modelled on V20_2).

## Phase 3: Billing `Reason.QUOTE_NOT_FOUND` (parallel with Phase 2; independent of Phase 1)

- [ ] 3.1 RED: `PublishPhysicalPaymentCompletedUseCaseTest.ReasonEnumCoverage` expected list includes `QUOTE_NOT_FOUND`; `SpringMailPurchaseExceptionNotificationAdapterTest` new `@EnumSource(Reason.class)` test asserts the ops mail body contains `reason=<NAME>` (raw code, no per-reason mapping). Proves [R] "Reason exists and is documented", "Exception mail handles the new reason".
- [ ] 3.2 GREEN: `api/billing/.../domain/model/Reason.java` add `QUOTE_NOT_FOUND` with javadoc distinguishing it from `TARGET_NOT_SCHEDULED`.
- [ ] 3.3 Docs: `docs/06-BILLING-API.md` L152-158 add the reason. Proves [R] "Billing defines and documents QUOTE_NOT_FOUND".
- [ ] 3.4 Gate: `./gradlew :api:billing:test` green.

## Phase 4: App mapper and handler (needs Phases 1 and 3)

- [ ] 4.1 RED: `api/app/.../outbox/AssignmentTypeMapperTest` with `@EnumSource(PurchaseType.class)` asserting every constant maps (MONTHLY to MONTHLY, INDIVIDUAL to INDIVIDUAL). Proves [T] "Mapping is exhaustive".
- [ ] 4.2 GREEN: create package-private `api/app/.../outbox/AssignmentTypeMapper.java`, `from(PurchaseType)` as exhaustive switch expression with no `default`.
- [ ] 4.3 RED: `PhysicalCapacityAssignmentOutboxEventHandlerTest`, one test per scenario:
  - held MONTHLY quote passes MONTHLY to `convertAll`; held INDIVIDUAL passes INDIVIDUAL; `quoteRepository.findById` invoked exactly once ([R] "Held MONTHLY / INDIVIDUAL quote converts as ...", "HoldNotFound reuses the loaded quote" including command type);
  - quote missing with hold: `convertAll` never called, `QUOTE_NOT_FOUND` published once, `InOrder` proves `createPurchaseFromPaymentEvent` before publish ([R] "Quote missing, hold present");
  - quote missing without hold: same ([R] "Quote missing, no hold");
  - quote missing and create returns `ASSIGNED` purchase: nothing published, no throw ([R] "Redelivery of a converted hold with the quote missing");
  - quote missing and create returns existing `EXCEPTION` purchase: published once ([R] "Redelivery after a crash between create and publish");
  - coverage shortfall still `TARGET_NOT_SCHEDULED` ([R] "Coverage shortfall keeps its reason");
  - `Converted` and `AlreadyConverted` redelivery ignore the type ([R] "Redelivery of a converted hold with the quote present").
  Update existing tests asserting `TARGET_NOT_SCHEDULED` for a missing quote.
- [ ] 4.4 GREEN: modify `api/app/.../outbox/PhysicalCapacityAssignmentOutboxEventHandler.java`: single quote lookup before conversion; quote-missing branch creates purchase first, publishes `QUOTE_NOT_FOUND` only if `purchase.getStatus() == EXCEPTION`, then returns; delete the old legacy quote-missing branch (L216-228); map type via mapper; pass type to `convertAll` and to `MultiSessionCapacityAssignmentCommand`; update the branch javadoc. No port change (`PurchaseCreationFromEventPort` already returns `Purchase`).
- [ ] 4.5 RED+GREEN (IT): `PhysicalPurchaseIntegrationTest` asserts persisted `assignment_type` equals MONTHLY for a held MONTHLY quote and INDIVIDUAL for a held INDIVIDUAL quote, via both held-conversion and legacy paths where covered. Proves [T] "Held MONTHLY / INDIVIDUAL purchase persists ...", "Legacy assignAll persists the command type".
- [ ] 4.6 Gate: `./gradlew :api:shared:test :api:physical:test :api:billing:test :api:app:test` green. Split checkpoint: run `git diff --shortstat develop...HEAD`; if over ~800 with Phases 5-6 still pending, close PR 1 here.

## Phase 5: Read side (PR 2 if split; needs Phase 1 and 2)

- [ ] 5.1 RED: `PhysicalCapacityAssignmentRepositoryAdapterTest` maps projection `getAssignmentType()` into `AssignedSessionRow`; `ListMyPhysicalSessionsUseCaseImplTest` carries it into `AssignedSessionView`; `PhysicalMySessionsControllerTest` asserts JSON `assignmentType` present and never null. Proves [T] "my-sessions exposes assignmentType, never null".
- [ ] 5.2 GREEN: `PhysicalCapacityAssignmentJpaRepository.findAssignedSessions` JPQL selects `asg.assignmentType`; `AssignedSessionRowProjection.getAssignmentType()`; `PhysicalCapacityAssignmentRepositoryAdapter`; `AssignedSessionRow`; `AssignedSessionView`; `ListMyPhysicalSessionsUseCaseImpl`; `web/dto/MySessionsResponse.Item` appends `AssignmentType assignmentType` after `checkInOpen`.
- [ ] 5.3 RED+GREEN (IT): `PhysicalMySessionsIntegrationTest` covers a MONTHLY session, an INDIVIDUAL session, and a mixed response. Proves [T] "Monthly session", "Individual session", "Mixed types in one response".

## Phase 6: Contract artifacts (parallel with Phase 5; same PR as the read side)

- [ ] 6.1 `api/openapi/physical-v1.yaml` `MySessionResponse`: add `assignmentType` to `required`, `type: string`, `enum: [MONTHLY, INDIVIDUAL]`, Spanish description.
- [ ] 6.2 `bruno/API - Direct/physical/My Sessions.bru`: add assertion/example for `assignmentType`.
- [ ] 6.3 `docs/05-PHYSICAL-API.md` L41-45 and `docs/22-DATA-MODEL.md` L93-94: document the field and column. `docs/diagrams/ERD-DIAGRAM.md` unchanged (L441 already lists the column). Proves [T] "Docs and contract match the response".

## Phase 7: Architecture and full gate

- [ ] 7.1 ArchUnit: no new rule; run `./gradlew test --tests "*ArchitectureTest"` and confirm the billing-no-physical rule and physical's lack of a billing dependency stay green. Proves [T] "Physical stays isolated from billing".
- [ ] 7.2 Full tests: `./gradlew :api:shared:test :api:physical:test :api:billing:test :api:app:test`.
- [ ] 7.3 Style: `./gradlew :api:shared:checkstyleMain :api:shared:checkstyleTest :api:physical:checkstyleMain :api:physical:checkstyleTest :api:billing:checkstyleMain :api:billing:checkstyleTest :api:app:checkstyleMain :api:app:checkstyleTest` (adjust to the task names the build exposes).
- [ ] 7.4 Coverage: `./gradlew :api:shared:jacocoTestCoverageVerification :api:physical:jacocoTestCoverageVerification :api:billing:jacocoTestCoverageVerification :api:app:jacocoTestCoverageVerification` (per-layer ratchets: physical 95/90, billing 85/85, shared/app flat floors).
- [ ] 7.5 Final size check: `git diff --shortstat develop...HEAD` and record the actual line count in the PR body.

## Dependencies and parallelism

Sequential: 1 then 4 then 7. Parallel after 1.5: Phase 2, Phase 3, Phase 5, Phase 6 (Phase 5 also needs Phase 2 for ITs).
Note: `:api:app` does not compile between 1.5 and 4.4 (handler uses old `convertAll`); do not run app tests before Phase 4.

## Non-blocking follow-ups (out of scope)

- [ ] F.1 bff: add `assignmentType` to the `AssignedSession` field issue (separate issue).
- [ ] F.2 Manual validation of the my-sessions response against a running API (Bruno) after merge.

# Design: Physical Assignment Type (#521)

## Technical Approach

Option A from the proposal. A neutral `AssignmentType` lives in `:api:shared`. `api:app` maps billing's `PurchaseType` to it. The value is threaded through both assignment write paths down to the single writer, `JpaPhysicalCapacityAssignmentAdapter.assertAssignment`. V26 persists it NOT NULL. The read chain projects it to `MySessionsResponse.Item`. Billing gains only `Reason.QUOTE_NOT_FOUND`. Physical never imports billing.

## Architecture Decisions

| Decision | Choice | Rejected | Rationale |
|---|---|---|---|
| Type home | `com.menta.shared.physical.AssignmentType {MONTHLY, INDIVIDUAL}` | physical domain enum; reuse `PurchaseType` | Commands are already in `shared.physical`. Reusing `PurchaseType` would make physical depend on billing. |
| Mapper | package-private `com.menta.app.outbox.AssignmentTypeMapper.from(PurchaseType)`: exhaustive switch expression, no `default` | `valueOf(name())`; mapping inside physical | A new `PurchaseType` fails compilation. Only api:app sees both modules. |
| Command shape | Append `AssignmentType assignmentType` as the last component of `CapacityAssignmentCommand` and `MultiSessionCapacityAssignmentCommand`; `requireNonNull` in the compact ctor | Separate arg on `assignAll` | The commands are the cross-module contract. Null fails fast at construction. |
| Writer port | `PhysicalCapacityAssignmentWriter.assertAssignment(UUID sessionId, UUID studentId, AssignmentType type)` | Default overload | Explicit at the one write point. No silent MONTHLY. |
| Hold port | `PhysicalCapacityHoldPort.convertAll(UUID paymentId, UUID studentId, AssignmentType type)` (user decision) | Type stored on hold rows (Option B) | Out of scope per proposal. |
| Entity column | `@Enumerated(EnumType.STRING) @Column(name="assignment_type", nullable=false, updatable=false, columnDefinition="VARCHAR(20)")`, with a hand-written getter | Lombok `@Getter` on the field | ITs run `ddl-auto=create-drop` with Flyway off, so the annotation must mirror the DDL NOT NULL. Follows `PhysicalCourseJpaEntity.status`. The class has no Lombok, and the Lombok rule says no retrofit. |
| Entity ctor | Replace the 4-arg ctor with a 5-arg one and update all ~35 test call sites | Keep a 4-arg ctor that defaults to MONTHLY | A default would contradict "inserts without a type fail". |
| Rollback version | `db/rollback/V26_1__revert_physical_assignment_type.sql` | V27 | Follows the V20_1/V20_2 precedent. A later real V27 still sorts after it. |

## Data Flow

    quote(PurchaseType) --AssignmentTypeMapper--> AssignmentType
       |-> convertAll(paymentId, studentId, type) -> ConvertCapacityHoldUseCase.convertOne -> writer.assertAssignment(s, st, type)
       '-> (HoldNotFound) MultiSessionCapacityAssignmentCommand(.., type) -> AssignCapacityUseCase.claimOne -> writer
    read: JPQL findAssignedSessions(+asg.assignmentType) -> AssignedSessionRowProjection.getAssignmentType()
          -> AssignedSessionRow -> AssignedSessionView -> MySessionsResponse.Item.assignmentType

### Handler control flow (`PhysicalCapacityAssignmentOutboxEventHandler.handle`)

1. Payment missing or non-Physical: unchanged (`TARGET_NOT_SCHEDULED`).
2. `String quoteId = physical.quoteId(); quote = quoteRepository.findById(quoteId).orElse(null)`. This is the ONLY lookup.
3. `quote == null`: log the warning, then **create first, publish second** (user decision 2026-10-15): `purchase = createPurchaseFromPaymentEvent(payload, List.of())`; `if (purchase.getStatus() == FulfillmentStatus.EXCEPTION) publishPaymentFulfillmentFailed(paymentId, userId, Reason.QUOTE_NOT_FOUND)`; return. `convertAll` is not called, so hold rows stay unconverted and expire through TTL. This also covers the old legacy quote-missing branch (lines 216-228, which is deleted). That branch's reason changes from `TARGET_NOT_SCHEDULED` to `QUOTE_NOT_FOUND` (user-approved); existing handler tests asserting the old reason and the handler javadoc describing this branch MUST be updated.
   - Order differs deliberately from the other failure branches (payment missing/non-Physical, shortfall, held-conversion failures), which keep the legacy publish-then-create. Those cannot hit an ASSIGNED purchase, so they are untouched.
   - Port finding: no port change needed. `PurchaseCreationFromEventPort.createPurchaseFromPaymentEvent` already returns the resulting `Purchase` (`getStatus()`), and `CreatePurchaseFromPaymentEventUseCase` returns an existing non-`EXCEPTION` purchase untouched (never saves it).
4. `type = AssignmentTypeMapper.from(quote.getPurchaseType())`. Then `convertAll(..., type)` with the same catch blocks.
5. `Converted` and `AlreadyConverted` are unchanged. A redelivery ignores the type.
6. `HoldNotFound` takes the legacy path and reuses `quote` for `periodStart`, the planner switch and `findSessionIdsAssignedTo`. The command gains `type`. The coverage shortfall stays `TARGET_NOT_SCHEDULED`.

Outcomes of step 3 (publish keyed on the resulting status, not on "was just created"):

| Case | Purchase after create | Publish |
|---|---|---|
| First occurrence (no purchase) | new `EXCEPTION` | exactly one `QUOTE_NOT_FOUND` |
| Redelivery, purchase `ASSIGNED` (converted hold, quote gone) | `ASSIGNED`, untouched | none: no event, no ops mail |
| Crash after create, before publish; redelivery | existing `EXCEPTION` (re-save loses the V8 unique race and returns the existing row) | yes, via the same rule; the V2 aggregate/event-type index dedups if the first publish had landed |

Handler MUST NOT throw on any of them. Residual: a purchase already `EXCEPTION` for another reason (e.g. `CAPACITY_BELOW_ASSIGNED`) that later sees a missing quote also publishes `QUOTE_NOT_FOUND`; the V2 index drops it if a failure event already exists.

## File Changes

| File | Action |
|---|---|
| `api/shared/.../shared/physical/AssignmentType.java` | Create |
| `api/shared/.../shared/physical/{CapacityAssignmentCommand,MultiSessionCapacityAssignmentCommand}.java` | Modify |
| `api/physical/.../port/out/PhysicalCapacityAssignmentWriter.java`, `.../adapter/JpaPhysicalCapacityAssignmentAdapter.java` | Modify (type param, entity ctor) |
| `api/physical/.../usecase/{AssignCapacityUseCase,ConvertCapacityHoldUseCase,CreateCapacityHoldUseCase}.java`, `.../port/in/PhysicalCapacityHoldPort.java` | Modify |
| `api/physical/.../entity/PhysicalCapacityAssignmentJpaEntity.java` | Modify |
| `api/physical/.../repository/{PhysicalCapacityAssignmentJpaRepository,AssignedSessionRowProjection}.java`, `.../adapter/PhysicalCapacityAssignmentRepositoryAdapter.java` | Modify |
| `api/physical/.../dto/{AssignedSessionRow,AssignedSessionView}.java`, `.../usecase/ListMyPhysicalSessionsUseCaseImpl.java`, `.../web/dto/MySessionsResponse.java` | Modify |
| `api/physical/.../demo/DemoPhysicalCatalogFixture.java` | Modify (`INDIVIDUAL`) |
| `PhysicalCourseAvailabilityPortImpl.java` | No main change. It uses accessors only. Its test helper gains one arg. |
| `api/app/.../outbox/AssignmentTypeMapper.java` | Create |
| `api/app/.../outbox/PhysicalCapacityAssignmentOutboxEventHandler.java` | Modify (reorder, create-then-publish in the quote-missing branch, javadoc on that branch) |
| `PurchaseCreationFromEventPort` / `CreatePurchaseFromPaymentEventUseCase` | No change (already returns `Purchase`) |
| `api/billing/.../domain/model/Reason.java` | Modify (`QUOTE_NOT_FOUND` + javadoc vs `TARGET_NOT_SCHEDULED`) |
| `api/app/src/main/resources/db/migration/V26__physical_assignment_type.sql`, `db/rollback/V26_1__revert_physical_assignment_type.sql` | Create |
| `api/openapi/physical-v1.yaml` (`MySessionResponse`), `bruno/API - Direct/physical/My Sessions.bru`, `docs/05-PHYSICAL-API.md` L41-45, `docs/06-BILLING-API.md` L152-158, `docs/22-DATA-MODEL.md` L93-94 | Modify |
| `docs/diagrams/ERD-DIAGRAM.md` | No change. L441 already lists `assignment_type`. The rest of the ERD is aspirational and out of scope. |

## Interfaces / Contracts

```sql
-- V26: physical_capacity_assignments.assignment_type (#521). Existing rows are
-- labelled MONTHLY: a LEGACY ASSUMPTION, safe only because the app is V0 with no
-- real data. The default is dropped right after so every new insert must state it.
-- V25 is the current head; V26 is the next free version.
ALTER TABLE physical_capacity_assignments
    ADD COLUMN assignment_type VARCHAR(20) NOT NULL DEFAULT 'MONTHLY' AFTER student_id;
ALTER TABLE physical_capacity_assignments
    ALTER COLUMN assignment_type DROP DEFAULT;
```

The rollback is `ALTER TABLE physical_capacity_assignments DROP COLUMN assignment_type;`. Its header follows V20_2: operator-only, lossy for the type, applied with both locations and `-target=26.1`.

OpenAPI: add `assignmentType` to `required`, with `type: string, enum: [MONTHLY, INDIVIDUAL]` and a Spanish description. The `Item` record appends `AssignmentType assignmentType` after `checkInOpen`.

`Reason` has no `valueOf`, switch, DB column or OpenAPI enum. It travels as `reason.name()` strings in outbox payloads and in the ops mail (`" reason=" + notification.reason()`), so an unknown value cannot break anything.

## Testing Strategy (Strict TDD: RED, then GREEN, in this order)

| # | Layer | RED test |
|---|---|---|
| 1 | shared unit | `CapacityAssignmentCommandTest` and `MultiSessionCapacityAssignmentCommandTest` reject a null type and expose it |
| 2 | physical app unit | `AssignCapacityUseCaseTest` and `ConvertCapacityHoldUseCaseTest` verify the writer receives the type. `CreateCapacityHoldUseCaseTest` checks delegation with the type. |
| 3 | physical infra | `JpaPhysicalCapacityAssignmentAdapterTest` persists the type. `PhysicalCapacityAssignmentJpaEntityTest` covers the getter. `PhysicalCapacityAssignmentRepositoryAdapterTest` maps the projection. |
| 4 | read unit/web | `ListMyPhysicalSessionsUseCaseImplTest`; `PhysicalMySessionsControllerTest` checks the JSON `assignmentType` |
| 5 | billing | `PublishPhysicalPaymentCompletedUseCaseTest.ReasonEnumCoverage` adds `QUOTE_NOT_FOUND` to the expected list. `SpringMailPurchaseExceptionNotificationAdapterTest` gets an `@EnumSource(Reason.class)` test asserting `reason=<NAME>` in the ops body. |
| 6 | app unit | `AssignmentTypeMapperTest` (`@EnumSource(PurchaseType.class)`). `PhysicalCapacityAssignmentOutboxEventHandlerTest` covers every spec scenario: MONTHLY and INDIVIDUAL type passed; `findById` called once; quote missing with and without a hold gives `QUOTE_NOT_FOUND` with `convertAll` never called and the purchase created before the publish (`InOrder`); quote missing + purchase returned `ASSIGNED` publishes nothing; quote missing + purchase `EXCEPTION` publishes once; shortfall stays `TARGET_NOT_SCHEDULED`; both redelivery cases. Existing tests asserting `TARGET_NOT_SCHEDULED` for the missing quote are updated. |
| 7 | app IT | `PhysicalPurchaseIntegrationTest` persists the matching type for held MONTHLY and INDIVIDUAL. `PhysicalMySessionsIntegrationTest` checks the response field. |
| 8 | migration IT | New `PhysicalAssignmentTypeMigrationIntegrationTest`, modelled on `PhysicalCapacityHoldMigrationIntegrationTest`. Migrate to 25, insert a row with `FOREIGN_KEY_CHECKS=0`, migrate to 26. Assert `MONTHLY`, `is_nullable=NO` and `column_default IS NULL`, and that an insert without the column fails. Rollback test: target 26, then combined locations with `target("26.1")` and `.ignoreMigrationPatterns("*:ignored")` (V20_2 and V21 are below 26 and unapplied), then assert the column is gone. |

ArchUnit: no new rule. The billing rule (`billing` must not depend on `physical..`, ArchitectureTest L111) and physical's lack of a Gradle dependency on billing already enforce the boundary. The existing rules must stay green.

## Size Forecast (additions + deletions)

| Area | Lines |
|---|---|
| shared (main + tests) | ~50 |
| physical main | ~70 |
| physical tests | ~130 |
| app handler + mapper | ~75 |
| app handler/unit tests | ~125 |
| app ITs (entity ctor ~35 sites, 2 lines each, plus assertions) | ~110 |
| migration + rollback + IT | ~150 |
| billing | ~35 |
| docs/OpenAPI/Bruno | ~30 |
| **Total** | **~775** |

This is under 800 and well over 400. Single PR, as the user accepted. If apply exceeds 800, the natural slice is PR 2 = the read side (projection to `Item`, test row 4, the my-sessions IT) plus OpenAPI, Bruno and `docs/05`.

## Threat Matrix

N/A: no routing, shell, subprocess, VCS/PR automation, executable-file classification or process-integration boundary.

## Migration / Rollout

V26 auto-applies. V26_1 is operator-only. Revert the PR and apply V26_1.

## Open Questions

None. Resolved: spec mail wording now states the raw reason code is rendered (no per-reason mapping); the create-then-publish hardening is adopted (step 3).

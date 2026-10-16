# Verify Report: physical-assignment-type (#521)

Verdict: PASS WITH WARNINGS
Evidence: HEAD c33e0dc (origin/develop), evidence_revision (sha256 of `git diff 081f2ab..HEAD`) = 24ce5cdf9515f8a2f019ece4e6a284d43a13fb5308c557110b65f614eba51679

## Gate (single run, BUILD SUCCESSFUL, exit 0)
`:api:shared:test :api:physical:test :api:billing:test :api:app:test :api:physical:checkstyleMain :api:app:checkstyleMain :api:physical:jacocoTestCoverageVerification :api:app:jacocoTestCoverageVerification`
All 30 tasks were UP-TO-DATE (Gradle cache: inputs unchanged, results reused). Counts from build/test-results:

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| shared | 70 | 0 | 0 | 0 |
| physical | 571 | 0 | 0 | 0 |
| billing | 971 | 0 | 0 | 0 |
| app | 533 | 0 | 0 | 0 |
| Total | 2145 | 0 | 0 | 0 |

Checkstyle (physical, app main) and JaCoCo layered verification (physical, app) pass.

## Compliance
12 requirements, 29 scenarios (type 7/14, outbox-routing 4/11, capacity-hold 1/4): all 12 / 29 COMPLIANT by static evidence plus passing named tests.

- Neutral type: AssignmentType in shared; 0 `com.menta.billing` imports in api/physical/src; no billing dep in physical build file; AssignmentTypeMapper is a default-less exhaustive switch; AssignmentTypeMapperTest uses @EnumSource(PurchaseType).
- V26: ADD COLUMN VARCHAR(20) NOT NULL DEFAULT 'MONTHLY' then DROP DEFAULT, legacy assumption in header; V26_1 rollback in db/rollback; PhysicalAssignmentTypeMigrationIntegrationTest covers backfill/default dropped, insert omitting type fails, rollback removes column.
- Write paths: convertAll(payment, student, type) and assertAssignment(..., type); PhysicalPurchaseIntegrationTest.assertAssignmentsTyped; DemoPhysicalCatalogFixture seeds INDIVIDUAL (+ test).
- my-sessions: OpenAPI required + enum; PhysicalMySessionsIntegrationTest monthly / individual / mixed.
- Handler: single findById before convertAll; quote-missing branch is create-then-publish keyed on status == EXCEPTION, returns without convertAll; HoldNotFound reuses the quote; tests for held MONTHLY/INDIVIDUAL, once-only lookup, missing quote with hold, no hold, ASSIGNED redelivery (no publish, no throw), EXCEPTION crash redelivery, shortfall keeps TARGET_NOT_SCHEDULED.
- Billing: Reason.QUOTE_NOT_FOUND, docs/06 line 157, mail test @EnumSource(Reason.class) asserts `reason=<NAME>`.
- Design decisions honored: NOT NULL + dropped default, mapper in api:app, physical never imports billing, QUOTE_NOT_FOUND create-then-publish.
- tasks.md: all phases 1-7 [x]; only F.1/F.2 follow-ups open (explicitly out of scope).

## CRITICAL
None.

## WARNING
1. Bruno `My Sessions.bru` documents `assignmentType` in the docs block but its `assert` block only checks `res.status: eq 200`; the spec ("Bruno request/assertions") and task 6.2 ("assertion/example") imply an assertion. Docs satisfy "example"; no executable assertion.
2. Gate tasks were all UP-TO-DATE (cached results), so tests were not re-executed in this run; counts reflect the last execution on identical inputs.

## SUGGESTION
1. Add `res.body.sessions[0].assignmentType: isString` (or similar) to the Bruno assert block.
2. tasks.md 7.5 mentions "this read-side PR (221)" -- reword on archive for clarity.
3. No dedicated ArchUnit rule forbids physical -> billing (design decision: enforced by the Gradle dependency graph); consider one if the dependency is ever added.

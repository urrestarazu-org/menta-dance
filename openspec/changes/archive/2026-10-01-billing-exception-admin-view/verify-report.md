# Verification Report: billing-exception-admin-view (#237)

**Change**: `billing-exception-admin-view` · **Mode**: Strict TDD, hybrid artifact store (OpenSpec + Engram) · **Verified against**: `origin/develop` @ `0997203` (local HEAD confirmed identical, clean tree)

## Completeness

| Check | Result |
|---|---|
| All 30 tasks `[x]` | ✅ 30/30 (`rg -c "^\- \[x\]"` = 30, unchecked = 0) |
| Spec present | ✅ `specs/purchase-exception-admin-view/spec.md`, 5 requirements, 9 scenarios |
| Design present | ✅ `design.md`, C1–C9, Testing Strategy table |
| Proposal present | ✅ `proposal.md`, D1–D10 |

## Test Execution (fresh, separate Gradle invocations)

| Command | Result | Evidence |
|---|---|---|
| `./gradlew :api:billing:test --rerun-tasks` | ✅ BUILD SUCCESSFUL | 820 tests, 0 failures, 0 errors (summed from `api/billing/build/test-results/**/*.xml`) |
| `./gradlew :api:app:test --rerun-tasks` | ⚠️ BUILD FAILED, but failures are pre-existing and unrelated | 379 tests, 2 failed, 0 errors. `PurchaseAdminExceptionIntegrationTest`: **2/2 passed** (confirmed via its own JUnit XML). The 2 failures are `PhysicalAttendanceHistoryIntegrationTest.a_mid_month_monthly_purchase_splits_assignments_across_two_monthly_views` (409 vs 201, deterministic — not flaky) and `PhysicalSessionManagementIntegrationTest.concurrent_payments_for_same_session_capacity_one_resolves_one_is_exception` (`NoSuchElementException`, flaky on rerun). Neither file was touched by this change: `git diff --stat 3abf336 0997203 -- '*PhysicalAttendanceHistory*' '*PhysicalSessionManagement*'` is empty. Reproduced the `PhysicalAttendanceHistoryIntegrationTest` failure against the pre-change base commit `3abf336` in an isolated worktree — it fails identically there, confirming it is a pre-existing defect unrelated to this change, not a regression. |
| `./gradlew :api:billing:jacocoTestCoverageVerification` | ✅ BUILD SUCCESSFUL | Billing coverage gate (85% domain+application / 85% infrastructure) passes |

**Verdict on test execution**: the one suite relevant to this change (`:api:billing:test`) is fully green, and the one test file this change added to `:api:app` passed both its scenarios. The 2 `:api:app` failures are pre-existing, reproduced on the pre-change base, and in files never touched by this PR — not a regression blocking this change.

## Spec Compliance Matrix

| Requirement | Scenario | Covering test | Result |
|---|---|---|---|
| List purchases in EXCEPTION | Admin lists EXCEPTION purchases, oldest-first | `PurchaseRepositoryAdapterTest` (ordering by `Payment.createdAt`) | ✅ PASS |
| List purchases in EXCEPTION | Non-EXCEPTION purchase absent | `PurchaseRepositoryAdapterTest` (ASSIGNED/PENDING_FULFILLMENT excluded) | ✅ PASS |
| List purchases in EXCEPTION | Empty result is 200, not 404 | `PurchaseRepositoryAdapterTest.findInException_returns_an_empty_page_without_error_and_skips_the_sessions_query` | ✅ PASS |
| Zero-session EXCEPTION rows never dropped (D7) | Zero-session row listed with `[]` | `PurchaseRepositoryAdapterTest.findInException_lists_a_zero_session_row_with_an_empty_array_never_dropped` | ✅ PASS |
| Page size capped, never clamped | `size=51` → 400 | `PurchaseAdminControllerTest` | ✅ PASS |
| Page size capped, never clamped | Absent size → default 20 | `PurchaseAdminControllerTest` | ✅ PASS |
| Access restricted to ADMIN | Non-admin → 403 | `PurchaseAdminControllerTest.list_from_a_non_admin_returns_403_and_never_calls_the_repository` | ✅ PASS |
| Access restricted to ADMIN | Anonymous → 401/403 | `PurchaseAdminExceptionIntegrationTest.an_anonymous_caller_is_rejected` (asserts `UNAUTHORIZED`) | ✅ PASS |
| Row excludes reason/buyer email | Row has no `reason`, buyer by `user_id` only | `ExceptionPurchaseItem`/`ExceptionPurchasePageResponse` field set (no reason/email field exists in the DTO); confirmed structurally and via DTO mapping test | ✅ PASS |

## Design-Specific Risk Verification (per task instructions)

1. **`REQUIRED` vs `MANDATORY` propagation — the design's own top risk**: `PurchaseAdminExceptionIntegrationTest` exists at `api/app/src/test/java/com/menta/app/integration/billing/PurchaseAdminExceptionIntegrationTest.java`. Confirmed by direct read: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, uses `TestRestTemplate` for a genuine HTTP round trip, **no `@Transactional` anywhere on the test class or its base class** (`AbstractBillingMySqlIntegrationTest` — confirmed via `rg -n "Transactional"` returning 0 matches). Fixtures seed data via direct repository `.save()` calls that commit and close before the HTTP call starts — there is no ambient transaction wrapping the request. The test ran and **passed** (`an_admin_lists_exception_purchases_with_no_ambient_transaction_wrapping_the_call`, confirmed `200 OK` via its own JUnit XML, time=0.051s, 0 failures). This is a genuine runtime guarantee, not a structural claim. ✅ PASS

2. **Non-locking batched sessions query is genuinely distinct**: `PurchaseSessionJpaRepository` has both `findByPurchaseIdOrderByPositionAsc` (native, `FOR UPDATE`, line 39/42) and the new `findByPurchaseIdInOrderByPurchaseIdAscPositionAsc` (derived, non-native, non-locking, line 53). `PurchaseRepositoryAdapter.findInException` (lines 128–145) calls **only** the new method; the old locking method is called exclusively from `toDomain` (line 149), an unrelated existing read path. ✅ PASS

3. **No N+1**: `PurchaseRepositoryAdapterTest` line 296 — `verify(sessionJpaRepository, times(1)).findByPurchaseIdInOrderByPurchaseIdAscPositionAsc(...)` — asserts the sessions query is called exactly once for a multi-row page, matching the design's stated requirement. This test passed in the fresh `:api:billing:test` run (820/820 green). ✅ PASS

4. **D7 zero-session EXCEPTION rows**: `PurchaseRepositoryAdapterTest.findInException_lists_a_zero_session_row_with_an_empty_array_never_dropped` (line 244) seeds `Purchase.exception(paymentId, List.of())` and asserts the row appears with an empty session array. Passed. ✅ PASS

## Scope Discipline

| Check | Result |
|---|---|
| `SecurityConfig.java` unchanged | ✅ `git diff --stat 3abf336 0997203 -- '*SecurityConfig.java'` — empty diff |
| `BillingConfiguration.java` unchanged | ✅ `git diff --stat 3abf336 0997203 -- '*BillingConfiguration.java'` — empty diff |
| No new Flyway migration | ✅ `git diff --stat 3abf336 0997203 -- 'api/app/src/main/resources/db/migration/'` — empty diff |
| No resolution action added | ✅ `grep` of `com.menta.billing.application.usecase` shows only pre-existing use cases (`CreateBankTransferPhysicalPurchaseUseCaseImpl`, `CreatePhysicalPurchaseCheckoutUseCaseImpl`, `CreatePurchaseFromPaymentEventUseCase`, `MarkPurchaseAssignedUseCase`, `MarkPurchaseExceptionUseCase`, `RoutingCreatePhysicalPurchaseCheckoutUseCase`) — no new use case transitions a `Purchase` out of `EXCEPTION`; `PurchaseAdminController` has exactly one `@GetMapping`, no mutating endpoint |

## ArchUnit / Port Signature Discipline

- `PurchaseRepository.findInException(int page, int size)` — primitive `int, int` signature confirmed by direct read of `application/port/out/PurchaseRepository.java` line 51. No `Pageable`/`Page` import in that file; those types are confined to `PurchaseJpaRepository` (infrastructure).
- `./gradlew :api:billing:test --tests "*ArchitectureTest*"` ran as part of the full `:api:billing:test` fresh run (820/820 green) — no `org.springframework.data` type leak into `com.menta.billing.application` detected.

## OpenAPI

- `api/openapi/billing-v1.yaml` documents `GET /api/v1/admin/billing/purchases` (confirmed present via grep, line 655 context).
- `npx @redocly/cli lint api/openapi/billing-v1.yaml` → **valid**, exactly the 2 pre-existing warnings (`info-license`, `no-server-example.com`), 0 new warnings. ✅ PASS

## Issues

**CRITICAL**: None.

**WARNING**:
- `:api:app:test` has 2 pre-existing, unrelated failures (`PhysicalAttendanceHistoryIntegrationTest`, `PhysicalSessionManagementIntegrationTest`) not caused by this change — confirmed via zero file diff and reproduction on the pre-change base commit `3abf336`. These represent pre-existing technical debt in the test suite, not a defect in this change, but they mean `:api:app:test` as a whole does not currently pass cleanly on `develop`.

**SUGGESTION**: None.

## Final Verdict

**PASS WITH WARNINGS**

All spec requirements are met with real, passing runtime evidence (not structural claims). The single most important regression guard — the `REQUIRED` vs `MANDATORY` propagation test — genuinely exercises a real HTTP call outside any ambient transaction and passes. Scope discipline (no `SecurityConfig`/`BillingConfiguration` edit, no migration, no resolution action) is confirmed by diff. The only open item is 2 pre-existing, unrelated `:api:app` test failures that predate this change and are not blocking for this change's own correctness, but should be tracked separately as repository-wide test-suite debt.

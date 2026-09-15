## Verification Report — physical-purchase-capacity (#41, US-PHYSICAL-004)

**Change**: `openspec/changes/physical-purchase-capacity/` | **HEAD verified**: `3a46ac8` on `develop` | **Mode**: full artifacts (proposal, 2 specs, design, tasks — all read in full)

### Verdict: **PASS**

### 1. Scope: scenarios 1,2,5,6 implemented and tested; 3,4 absent
- `api/app/src/test/java/com/menta/app/integration/billing/PhysicalPurchaseIntegrationTest.java` has real end-to-end tests for all four in-scope scenarios plus 409/410/401: `monthly_purchase_confirmed_assigns_every_covered_session_end_to_end` (S1), `individual_purchase_confirmed_assigns_exactly_one_session_end_to_end` (S2), `a_duplicate_outbox_redelivery_consumes_no_additional_spots` (S5), `a_capacity_trip_at_confirmation_leaves_payment_completed_and_purchase_exception_end_to_end` (S6).
- No production write path for holds: `PhysicalCapacityHoldJpaEntity.java` javadoc states "No production adapter writes through this entity yet"; `rg` for `.save(` against `PhysicalCapacityHoldJpaRepository` in `api/physical/src/main` returns zero hits — only the interface declaration exists.
- `PhysicalCapacityUnavailableException` javadoc explicitly labels itself non-guaranteed, distinct from the future hold-backed guarantee.

### 2. Locked decisions — all confirmed in code
- **D4/A4** (`CoveragePlanner.java:71-93`): `planMonthly` filters `scheduledAt >= referenceInstant`, `< referenceInstant+120d`, sorts `(scheduledAt,sessionId)`, `.limit(scheduledSessionCount)`; returns `Plan.Insufficient` rather than truncating — extends forward, never truncates.
- **D5/A6/A7** (`CreatePhysicalPurchaseCheckoutUseCaseImpl.java:96-121`): replay check → quote-expiry filter (`410` via `PhysicalCourseQuoteExpiredException`) → `ensureCoverageIsAvailable` (`409` via `PhysicalCapacityUnavailableException`) → `Payment` write. Order matches A7 (410 before 409). `PhysicalPurchaseExceptionHandler` maps them to `GONE`/`CONFLICT` respectively with distinct codes.
- **A1** (`Purchase.java:18-67`, `V20__billing_purchase_sessions.sql`): `Purchase.physicalSessionIds` is `List<String>`; V20 creates `billing_purchase_sessions(purchase_id, position, physical_session_id)`, backfills `SELECT id,0,physical_session_id`, drops the singular column.
- **A2** (`MultiSessionCapacityAssignmentCommand.java:62-96`): compact constructor checks strict ascending `(scheduledAt,sessionId)` pairwise AND separately checks `distinctSessionIds.size() != claims.size()` over the **whole set** via a `Set<UUID>` — not merely adjacent pairs (explicit code comment addresses this exact edge case).
- **A3** (`AssignCapacityUseCase.java:57-131`): single `@Transactional(REQUIRES_NEW)` `assignAll` wraps the whole ordered loop calling private `claimOne`; `assign` is a singleton-list delegation to `assignAll`.
- **A5** (`PaymentTarget.java:36`): `record Physical(String quoteId)` — field is literally named `quoteId`, not `sessionId`.

### 3. P0 defect (#216) fix (#217) verified in current code
- `JpaPhysicalCapacityAssignmentAdapter.assertAssignment` (lines 77-108): decision is made from `sessionRepository.lockCapacityForUpdate(sessionId)` (FOR UPDATE) and `jpaRepository.countBySessionIdForUpdate(sessionId)` (FOR UPDATE) — two independent locking reads, no plain/consistent read anywhere before them.
- `rg -n "findByIdWithAvailabilityForUpdate|lockSessionRow"` across all `.java` files: **zero matches** — the dead code with the false javadoc no longer exists.
- Regression test `concurrent_claims_never_oversell_a_capacity_one_session` (`PhysicalSessionManagementIntegrationTest.java:546`) iterates: `for (int iteration = 0; iteration < OVERSELL_ITERATIONS; iteration++)` — not a single run.

### 4. Three PR8-found bugs — fixes confirmed in place
- `TransactionalCreatePhysicalPurchaseCheckoutUseCase` wraps `CreatePhysicalPurchaseCheckoutUseCaseImpl` and IS the bean returned by `BillingConfiguration.createPhysicalPurchaseCheckoutUseCase` (line 306).
- Outbox handler calls `assignedAdapter.markAssigned(paymentId)` (`PhysicalCapacityAssignmentOutboxEventHandler.java:195`) after a successful `assignAll`, which delegates to `MarkPurchaseAssignedPort.markAssigned` → `Purchase.assigned()`.
- `PhysicalCapacityAssignmentJpaEntity` declares `@Table(uniqueConstraints = @UniqueConstraint(...))` mirroring V7.
- `MarkPurchaseExceptionUseCase.markException` (line 58): `@Transactional(propagation = REQUIRED, noRollbackFor = IllegalPurchaseStateTransitionException.class)`.

### 5. End-to-end integration test
- `PhysicalPurchaseIntegrationTest` uses `@Testcontainers` + real `MySQLContainer("mysql:8.0")`, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, real `TestRestTemplate` HTTP calls with real JWT bearer tokens through the real Spring Security filter chain (separately verified by `checkout_without_a_token_is_rejected` → 401).
- **Minor note (non-blocking)**: `confirmPayment()` drives `webhookWorker.process(row)` directly rather than an actual HTTP POST to the webhook endpoint (it mocks only the external `paymentProviderPort.fetchPayment` call, an appropriate boundary mock for a third-party integration). This matches the project's own established precedent (`PresentialPurchaseExceptionPathIntegrationTest`) and still exercises the real `PaymentVerificationService`, outbox creation, and `assignAll` path for real against real MySQL.

### 6. Security
- `SecurityConfig.java:217`: explicit `.requestMatchers(HttpMethod.POST, "/api/v1/billing/physical/purchases").authenticated()` — not relying on `anyRequest()` fallback.
- `SecurityConfigTest.an_unauthenticated_post_of_the_physical_purchases_route_is_rejected` runs a real `MockMvc` against the real security filter chain and asserts `401`.

### 7. Full test suite — forced, no cache
- `./gradlew test check --rerun-tasks --no-daemon` (full monorepo): `BUILD SUCCESSFUL in 12m 45s`, exit code 0.
- Real JUnit XML aggregation (not console text) across all 454 `TEST-*.xml` files: **2213 tests, 0 failures, 0 errors, 2 skipped**. Per-module counts: `api/app` 277, `api/auth` 489, `api/billing` 576, `api/physical` 243, `api/shared` 47, `api/virtual` 344, `bff` 232, `android` 5.
- `check` includes `jacocoTestCoverageVerification` per module — BUILD SUCCESSFUL confirms billing (100%/85%) and physical (95%/90%) layered floors hold, and all ArchUnit suites passed (a failure there would have failed the build).

### 8. Scope cleanliness
- `git diff --stat 598c4c3~1..3a46ac8 -- bff android` → **empty** — zero lines touched in either directory across the entire 8-PR + P0-fix chain.
- `git diff --stat 598c4c3~1..3a46ac8 -- api/auth api/shared` → exactly 5 files: `SecurityConfig.java`/`SecurityConfigTest.java` (the documented matcher), `PaymentCompletedOutboxPayload.java` (javadoc-only per task 5.5), `MultiSessionCapacityAssignmentCommand.java`/its test (the documented ordered command). Matches the chain's own documentation exactly; no undocumented drift.

### Issues
None CRITICAL. None WARNING. One SUGGESTION (non-blocking, informational): the integration test's `confirmPayment()` helper calls the outbox/webhook worker method directly instead of POSTing to the HTTP webhook endpoint — acceptable given it mirrors an established project precedent and still proves the real confirmation→assignAll path against real MySQL, but worth flagging if a future reviewer expects a literal HTTP webhook call.

### Tasks
All items in `tasks.md` (PR1–PR8) are checked `[x]`, and code inspection confirms each corresponds to real, currently-present code — no phantom completions found.

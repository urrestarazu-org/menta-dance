# Tasks: Admin view for physical purchases in EXCEPTION (#237)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 150–250 (incl. tests) |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: Yes
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Full read model + endpoint (C1–C9) | PR 1 (only) | `./gradlew :api:billing:test :api:app:test --tests "*Purchase*" --tests "*ArchitectureTest*"` | Testcontainers MySQL (`:api:billing`, `:api:app`) — real HTTP route for the MANDATORY-trap test | Revert the merge commit; no migration to undo |

## Phase 1: Port + Query (RED → GREEN)

- [x] 1.1 RED (`PurchaseRepositoryAdapterTest`): `findInException(0,20)` returns EXCEPTION rows oldest-first by `Payment.createdAt`.
- [x] 1.2 RED: an `ASSIGNED`/`PENDING_FULFILLMENT` purchase is absent from results.
- [x] 1.3 RED: a zero-session `EXCEPTION` row (`Purchase.exception(paymentId, List.of())`) is listed with `[]`, never dropped (D7).
- [x] 1.4 RED: empty result set → `totalElements=0`, empty items, no error.
- [x] 1.5 RED: sessions for a multi-purchase page fetched via exactly one call to the new sessions repo method (mock interaction count), never N+1.
- [x] 1.6 GREEN: `ExceptionPurchasePage findInException(int, int)` on `application/port/out/PurchaseRepository.java`, javadoc mirrors `findAwaitingManualVerification` (primitives, no clamping).
- [x] 1.7 GREEN: `ExceptionPurchaseItem`/`ExceptionPurchasePage` records in `application/dto/`.
- [x] 1.8 GREEN: `infrastructure/persistence/projection/ExceptionPurchaseRow.java`, mirrors `PendingVerificationRow`.
- [x] 1.9 GREEN: `PurchaseJpaRepository.findByStatusOrderByPaymentCreatedAt` — explicit `JOIN PaymentJpaEntity pay ON pu.paymentId = pay.id` (not a path expression) + mandatory `countQuery`, `Page<ExceptionPurchaseRow>`.
- [x] 1.10 GREEN: `PurchaseSessionJpaRepository.findByPurchaseIdInOrderByPurchaseIdAscPositionAsc(Collection<UUID>)` — new derived, non-native, non-locking; javadoc warns against reuse vs. the existing `FOR UPDATE` method.
- [x] 1.11 GREEN: `PurchaseRepositoryAdapter.findInException` — `PageRequest.of(page,size)`, run projection query, batch sessions (skip query if page empty), `Collectors.groupingBy` purchase id, assemble page; `@Transactional(propagation = Propagation.REQUIRED, readOnly = true)` (explicit, not `MANDATORY`).
- [x] 1.12 Run `./gradlew :api:billing:test --tests "*PurchaseRepositoryAdapterTest*"`, confirm 1.1–1.5 green.

## Phase 2: Web DTO + Controller (RED → GREEN)

- [x] 2.1 RED: `ExceptionPurchasePageResponse.from(...)` maps every field, `[]` sessions survive.
- [x] 2.2 GREEN: `infrastructure/web/dto/ExceptionPurchasePageResponse.java` (`record` + `from(...)`), mirrors `PendingVerificationPageResponse`.
- [x] 2.3 RED (`PurchaseAdminControllerTest`, standalone MockMvc + `PaymentExceptionHandler` advice, mirrors `PaymentAdminControllerTest`): `size=51` → `400`.
- [x] 2.4 RED: absent `size` → adapter called with `size=20`.
- [x] 2.5 RED: non-`ADMIN` → `403`, `verifyNoInteractions(purchaseRepository)`.
- [x] 2.6 RED: populated page → response body maps every field.
- [x] 2.7 GREEN: `PurchaseAdminController` — `@RestController @RequestMapping("/api/v1/admin/billing/purchases") @PaymentEndpoint @RequiredArgsConstructor`; one `@GetMapping`, required `status` param fixed to `EXCEPTION`, `MAX_PAGE_SIZE=50`/`DEFAULT_PAGE_SIZE=20`, `requireAdmin` copied from `PaymentAdminController`.
- [x] 2.8 Run `./gradlew :api:billing:test --tests "*PurchaseAdminControllerTest*" --tests "*ExceptionPurchasePageResponse*"`, confirm green.

## Phase 3: Integration Guards (RED → GREEN)

- [x] 3.1 RED (`api/app/.../integration/billing/PurchaseAdminExceptionIntegrationTest.java`, Testcontainers): hit the real route with NO ambient transaction → `200` — proves `REQUIRED` propagation, not `MANDATORY` (top design risk; not adapter-unit-only).
- [x] 3.2 RED: same test, anonymous caller → `401` (filter-chain behavior, unreachable from a controller unit test).
- [x] 3.3 GREEN: confirm 3.1–3.2 pass with Phase 1/2 code; no extra production change expected.
- [x] 3.4 Verify: `./gradlew test --tests "*ArchitectureTest*"` — no `org.springframework.data` type in `com.menta.billing.application`.
- [x] 3.5 Verify via `git diff --stat`: no edit to `SecurityConfig.java` or `BillingConfiguration.java`.

## Phase 4: Contract, Tooling, Docs

- [x] 4.1 Add the endpoint to `api/openapi/billing-v1.yaml`, mirroring the pending-verification list entry.
- [x] 4.2 `npx @redocly/cli lint api/openapi/billing-v1.yaml`.
- [x] 4.3 Add `bruno/API - Direct/billing/List Purchases In Exception.bru`, mirroring `List Pending Verification Payments.bru`.
- [x] 4.4 Docs: searched `docs/user-stories/*.md` for `237`, `209`, `purchase-exception-notification`, `PURCHASE_EXCEPTIONED` — no story doc covers this line of work (only unrelated `EXCEPTION`-status mentions in US-BILLING-008/010, US-PHYSICAL-004). No file created or edited.
- [x] 4.5 `./gradlew check` (ArchUnit + billing coverage 85%/85%) before opening the PR.

## Key Learnings

1. The design's top risk is the `Propagation.MANDATORY` trap, so a dedicated `:api:app` Testcontainers test runs outside any ambient transaction rather than trusting an adapter-unit test alone.
2. No `docs/user-stories/` file references issue #237 or #209's line of work, so the plan states that explicitly instead of inventing an update.
3. The new sessions batch-read query is a distinct non-locking method from the existing native `FOR UPDATE` query, and its javadoc must warn against reuse.
4. Zero-session `EXCEPTION` rows are legal by construction, so the row mapper must emit `[]` rather than null or drop the field.
5. The 150–250 estimated changed lines keeps this a single-PR change under `size:exception`, with no chained or stacked PR split.

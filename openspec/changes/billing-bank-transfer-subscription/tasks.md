# Tasks: Subscribe by Bank Transfer with Manual Verification (#31, US-BILLING-003)

## Fixed Facts (do not reopen)

C1–C12 (design) and D1–D3 (proposal) are locked. No task below re-derives:
endpoint shape (`/approve` + `/reject`, C1), the static bank account (C2),
`Subscription.cancelled()` needing zero domain change (C3), the two new
`Payment` transitions never fabricating a `ProviderOutcome` (C4), `PaymentProof`
as its own aggregate keyed unique on `payment_id` (C5), the sweep's separate
`REQUIRES_NEW` bean (C6), the two-Redis-key limiter (C7), owner-from-token +
admin-from-role authorization with `404` not `403` for non-owners (C8),
`updatedAt = statusChangedAt ?? createdAt` needing no new column (C9),
`PaymentFulfillmentService` extraction (C10), the `MultipartFile`→domain-boundary
shape (C11), or the C12 write order in proof submission. They only implement
what design.md already decided.

**Spec correction carried forward (C3):** the `billing-subscriptions` delta's
parenthetical about no path existing to cancel a non-`ACTIVE` subscription is
inaccurate — `Subscription.cancelled()` already does it. R3 below is a
**characterization test**, not production code.

**Migration correction carried forward:** `V21` is not schema-neutral for
`billing_payments` — it also adds `idx_billing_payments_status_created`
(sweep support), contradicting the proposal's "needs no change".

## Sequencing Warning — P5 is not optional

Until **P5** merges, a `Payment` left in `AwaitingManualVerification` with no
proof has **no automatic exit** (design's Risks table, "Scenario 6 quietly
skipped"). P1–P4 leave the flow usable-but-unexpiring. Do **not** archive this
change or consider it complete before P5 merges and its integration test
(both rows, one sweep tick) is green.

## Incident Guardrails (mandatory in every phase's verification step)

Two real incidents from this session must not recur:

1. **CGLIB/`AopConfigException`** — a `@Transactional` bean declared
   `public final class` breaks Spring's proxy generation and fails every
   `@SpringBootTest` in the module, invisible under a `--tests` filter. No
   class touched in this change (`PaymentExpiryWorker`, any
   `Transactional*UseCase` decorator, `PaymentFulfillmentService` if ever
   made `@Transactional`) may be declared `final`. Every phase's
   verification step MUST run `:api:app:test` **without** a `--tests`
   filter and confirm `MentaDanceApplicationTest > contextLoads()` is green.
2. **Self-invocation of `@Transactional`** — a private `@Transactional`
   method called from inside the same bean is never intercepted by Spring
   AOP (#245). C6 already avoids this by design
   (`PaymentExpiryReconciler`/`PaymentExpiryWorker` as separate beans); P5
   MUST verify the shipped code keeps that separation and does not collapse
   it "for simplicity" into one bean with a private method.

Every phase closes with `:api:billing:test` and `:api:app:test`, **no**
`--tests` filter (add `--rerun-tasks` if Gradle's up-to-date cache hides a
stale failure), exactly as done for every PR of #208/#209/#245.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~2,300–2,700 across the whole change (file upload is new territory — C11: first `MultipartFile` boundary in the repo) |
| 400-line budget risk | Low for P1/P3d/P4/P5, Medium for P2/P3a/P3b/P3c |
| Chained PRs recommended | Yes |
| Suggested split | 8 PRs, P1 → P2 → P3a → P3b → P3c → P3d → P4 → P5 |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

```text
Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: Medium
```

Design's own component map splits into P1–P5; **P3 does not survive as one
PR** (domain aggregate + validator + storage adapter + JPA quartet +
notification adapter + submit use case + controller + `SecurityConfig` +
migration + Docker volume is the single largest slice in the change) and is
split into P3a–P3d here, mirroring #208's fine-grained precedent for
comparably large surface.

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| P1 | Domain transitions (`resolveManually`, `expireAwaitingManualVerification`) + `PaymentFulfillmentService` extraction (C10) + R3 characterization | PR 1 | `:api:billing:test --tests "*PaymentTest*" --tests "*PaymentFulfillmentServiceTest*" --tests "*SubscriptionTest*"` | N/A — pure domain + Mockito | Revert 6 files; `PaymentFulfillmentService` is inert (no non-refactor consumer yet) |
| P2 | Routing use case (D3) + bank-transfer creation + rate limiter (C2/C7) | PR 2 | `:api:billing:test --tests "*RoutingCreateSubscriptionCheckoutUseCaseTest*" --tests "*CreateBankTransferSubscriptionUseCaseImplTest*" --tests "*RedisBankTransferRateLimitPortTest*"` | N/A at unit level; MP regression via existing `SubscriptionCheckoutIntegrationTest` | Revert use case + router + adapter + config; MP path routes through the same `CreateSubscriptionCheckoutUseCaseImpl` either way |
| P3a | Proof domain + validation + storage adapter (C5/C11) | PR 3a | `:api:billing:test --tests "*PaymentProofContentValidatorTest*" --tests "*LocalFilesystemPaymentProofStorageAdapterTest*"` | JUnit `@TempDir` | Revert 6-7 files; nothing wired to a use case yet, structurally inert |
| P3b | Proof persistence (JPA quartet) + notification adapter (D2) + `V21` | PR 3b | `:api:billing:test --tests "*PaymentProofRepositoryAdapterTest*" --tests "*SpringMailPaymentProofNotificationAdapterTest*"` | Testcontainers MySQL 8 for the JPA adapter | Revert JPA quartet + mail adapter + migration; `DROP TABLE billing_payment_proofs` compensates `V21` |
| P3c | Submit use case + `PaymentController` POST + wiring + volume (C12) | PR 3c | `:api:billing:test --tests "*SubmitPaymentProofUseCaseImplTest*"` + `:api:app:test --tests "*PaymentControllerTest*"` | Testcontainers + real filter chain (R9 unauthenticated-fetch proof) | Revert use case + controller method + decorator + `SecurityConfig` matcher + compose volume; flow becomes unreachable again, not broken |
| P3d | Read status (R6, C9) | PR 3d | `:api:billing:test --tests "*GetPaymentUseCaseImplTest*"` + `:api:app:test --tests "*PaymentControllerTest*"` | N/A — MockMvc | Revert use case + one controller method + decorator; smallest, independently revertible |
| P4 | Admin resolution (D1/C1, consumes P1's `resolveManually` + `PaymentFulfillmentService`) | PR 4 | `:api:billing:test --tests "*ResolvePaymentProofUseCaseImplTest*"` + `:api:app:test --tests "*PaymentAdminControllerTest*"` | Testcontainers (approve activates with frozen course snapshot) | Revert use case + admin controller + decorator; no `SecurityConfig` change to revert (C8 — already covered by `/api/v1/admin/**`) |
| P5 | Expiry sweep, both rows one transaction (R7/C6) | PR 5 | `:api:billing:test --tests "*PaymentExpiryWorkerTest*"` | Testcontainers MySQL 8, scenario 6 end-to-end | Revert reconciler+worker+repository query+config; rows already `AwaitingManualVerification` must be resolved or expired manually before reverting (Rollback Plan) |

## Phase P1: Domain transitions + fulfillment extraction (R3, cross-cutting)

Base layer for everything else — kept as small and self-contained as
possible per the request; `PaymentVerificationService`'s **existing** test
stays green and **unmodified** as the refactor's own proof.

- [x] 1.1 RED: extend `PaymentTest` (`api/billing/src/test/java/com/menta/billing/domain/model/PaymentTest.java`) — `resolveManually` approves/rejects only from `AwaitingManualVerification`, throws `IllegalPaymentStateTransitionException` from the other six statuses; `expireAwaitingManualVerification` transitions from `AwaitingManualVerification`, no-ops (silent) on the other six (C4).
- [x] 1.2 RED: extend `SubscriptionTest` (`api/billing/src/test/java/com/menta/billing/domain/model/SubscriptionTest.java`) — **characterization**: `cancelled()` on `PENDING` yields `CANCELLED` with `cancellation` absent (R3, C3 — locks existing behavior, no production change).
- [x] 1.3 GREEN: create `ManualVerificationDecision.java` (`api/billing/src/main/java/com/menta/billing/domain/model/ManualVerificationDecision.java`) — `APPROVED, REJECTED`.
- [x] 1.4 GREEN: create `IllegalPaymentStateTransitionException.java` (`api/billing/src/main/java/com/menta/billing/domain/exception/IllegalPaymentStateTransitionException.java`) — maps to `409`.
- [x] 1.5 GREEN: modify `Payment.java` (`api/billing/src/main/java/com/menta/billing/domain/model/Payment.java`) — add `resolveManually(ManualVerificationDecision, Instant)` and `expireAwaitingManualVerification(Instant)` (C4). Do **not** add `awaitingManualVerification`/`statusChangedAt` here — those land in P2/P3d with their consumers.
- [x] 1.6 RED: new `PaymentFulfillmentServiceTest` (`api/billing/src/test/java/com/menta/billing/application/usecase/PaymentFulfillmentServiceTest.java`) — `ensure(payment)` = `activate(confirmedAt, plan.durationDays, plan.courseIds)` + `assigned()`; `release(payment)` = `cancelled()`; plan read by id regardless of status.
- [x] 1.7 GREEN: create `PaymentFulfillmentService.java` (`api/billing/src/main/java/com/menta/billing/application/usecase/PaymentFulfillmentService.java`) — extract `ensureFulfillment`/`releaseFulfillment`/`ensureSubscription` (currently private, `PaymentVerificationService.java:174-224`) unchanged, exposed as `ensure(Payment)`/`release(Payment)`.
- [x] 1.8 GREEN: modify `PaymentVerificationService.java` — delegate to `PaymentFulfillmentService`; **do not** modify `PaymentVerificationServiceTest` — it must stay green unmodified as the refactor's proof (C10).
- [x] 1.9 GREEN: modify `BillingConfiguration.java` (`api/billing/src/main/java/com/menta/billing/infrastructure/config/BillingConfiguration.java`) — wire `PaymentFulfillmentService` bean, inject into `PaymentVerificationService`.
- [x] 1.10 GREEN: create `PaymentEndpoint.java` + `PaymentExceptionHandler.java` (`api/billing/src/main/java/com/menta/billing/infrastructure/web/controller/`) — `@RestControllerAdvice(annotations = PaymentEndpoint.class)`, sibling of `SubscriptionExceptionHandler`; map `IllegalPaymentStateTransitionException → 409` only. Extended in P3a (`400`) and unchanged otherwise later.
- [x] 1.11 Verify: `:api:billing:test :api:billing:jacocoTestCoverageVerification` (85%/85% floors) and `:api:app:test` **no `--tests` filter**, confirm `MentaDanceApplicationTest > contextLoads()` green (Incident Guardrail #1). Confirm no class touched here is `final`.

## Phase P2: Routing + bank-transfer creation (R1, R2, R4)

- [x] 2.1 RED: new `RoutingCreateSubscriptionCheckoutUseCaseTest` — `MERCADO_PAGO` dispatches to `CreateSubscriptionCheckoutUseCaseImpl`, `BANK_TRANSFER` to `CreateBankTransferSubscriptionUseCaseImpl`, nothing else routed.
- [x] 2.2 GREEN: create `RoutingCreateSubscriptionCheckoutUseCase.java` (`api/billing/src/main/java/com/menta/billing/application/usecase/RoutingCreateSubscriptionCheckoutUseCase.java`) — implements `CreateSubscriptionCheckoutUseCase`, dispatches on `paymentMethod` (D3).
- [x] 2.3 RED: update **only the test** — `CreateSubscriptionCheckoutUseCaseImplTest.bank_transfer_is_rejected_before_it_can_be_sent_to_checkout_pro` now asserts the routing boundary, not a flat rejection. `CreateSubscriptionCheckoutUseCaseImpl.java` itself stays **untouched** (design: its own guard is now an internal invariant).
- [x] 2.4 GREEN: modify `Payment.java` — add `awaitingManualVerification(...)` static factory (birth, mirrors `awaitingProvider`), CBU as `expectedMerchantAccountId` (C2).
- [x] 2.5 GREEN: create `BankAccountDetails.java`, `BankTransferInstructions.java` (`api/billing/src/main/java/com/menta/billing/application/dto/`).
- [x] 2.6 GREEN: modify `SubscriptionCheckoutResult.java` — `+BankTransferInstructions` nullable field, mirrors the existing `OverlapNotice`.
- [x] 2.7 GREEN: create `CreateBankTransferSubscriptionUseCase.java` (in-port) + `CreateBankTransferSubscriptionUseCaseImpl.java` (`api/billing/src/main/java/com/menta/billing/application/{port/in,usecase}/`).
- [x] 2.8 RED: new `CreateBankTransferSubscriptionUseCaseImplTest` — Mockito `InOrder`: limiter consulted **before** any repository write; `429` creates neither `Payment` nor `Subscription`; `reference = SUB-{paymentId}`; `expectedMerchantAccountId == configured CBU`.
- [x] 2.9 GREEN: create `BankTransferRateLimitPort.java` (out-port) — `consumeSubscriptionCreation(UUID)`, `consumeProofUpload(PaymentId)` (both declared now; the upload key is consumed by `SubmitPaymentProofUseCaseImpl` in P3c).
- [x] 2.10 RED: new `RedisBankTransferRateLimitPortTest` — mirrors `RedisBillingPlansRateLimitPortTest`: two distinct keys/TTLs (10/day, 3/72h), fail-closed `BillingDegradedException` on Redis unavailability, for **both** methods.
- [x] 2.11 GREEN: create `RedisBankTransferRateLimitPort.java` (`api/billing/src/main/java/com/menta/billing/infrastructure/security/RedisBankTransferRateLimitPort.java`) — reuses `RedisBillingPlansRateLimitPort`'s `CONSUME_SCRIPT` verbatim (C7).
- [x] 2.12 GREEN: modify `TransactionalCreateSubscriptionCheckoutUseCase.java` — now wraps the router, not the MP impl directly.
- [x] 2.13 GREEN: modify `BillingConfiguration.java` — wire router, `CreateBankTransferSubscriptionUseCaseImpl`, `RedisBankTransferRateLimitPort`, bank-account `@Value`s.
- [x] 2.14 GREEN: modify `api/app/src/main/resources/application*.yml` — `billing.bank-transfer.account.{cbu,alias,holder,cuit}`, `billing.bank-transfer.enabled`, rate-limit properties.
- [x] 2.15 Regression: run `SubscriptionCheckoutIntegrationTest` MP scenarios unmodified/green; run `CreatePhysicalPurchaseCheckoutUseCaseImplTest` unmodified (its own `BANK_TRANSFER` rejection for physical purchases, #36, stays untouched).
- [x] 2.16 Verify: `:api:billing:test :api:billing:jacocoTestCoverageVerification` + `:api:app:test` **no filter**, `contextLoads()` green. Confirm no new class is `final`.

## Phase P3a: Proof domain + validation + storage adapter (R5, R9 — C5/C11)

Self-contained, inert until P3c wires it — no use case, no controller yet.

- [ ] 3a.1 GREEN: create `PaymentProof.java`, `PaymentProofId.java` (`api/billing/src/main/java/com/menta/billing/domain/model/`) — `id, paymentId, storageKey, originalFilename, contentType, sizeBytes, uploadedAt` (C5).
- [ ] 3a.2 RED: new `PaymentProofContentValidatorTest` — table-driven: each accepted type (PNG/JPG/JPEG/PDF), oversized (>5MB), declared≠sniffed magic bytes, empty file, PDF renamed `.png` (C11).
- [ ] 3a.3 GREEN: create `PaymentProofContentValidator.java` (`api/billing/src/main/java/com/menta/billing/domain/service/PaymentProofContentValidator.java`) — pure, I/O-free, Spring-free.
- [ ] 3a.4 GREEN: create `PaymentProofRejectedException.java` (`.../domain/exception/`) — maps to `400`.
- [ ] 3a.5 RED: new storage-key factory test — `../../etc/passwd.png`, `a\0.png`, a 300-char name, a non-ASCII name all reduce to `{paymentId}/{proofId}.{ext}` with only UUIDs + whitelisted extension.
- [ ] 3a.6 RED: new `LocalFilesystemPaymentProofStorageAdapterTest` (JUnit `@TempDir`) — writes land under the volume root; a hand-crafted `../` key is refused; `delete` of a missing key is silent.
- [ ] 3a.7 GREEN: create `PaymentProofStoragePort.java` (out-port) + `LocalFilesystemPaymentProofStorageAdapter.java` (`api/billing/src/main/java/com/menta/billing/{application/port/out,infrastructure/storage}/`) — `root.resolve(key).normalize().startsWith(root)` assertion (C5).
- [ ] 3a.8 Update `PaymentExceptionHandler.java` — add `PaymentProofRejectedException → 400`.
- [ ] 3a.9 Verify: `:api:billing:test :api:billing:jacocoTestCoverageVerification` + `:api:app:test` **no filter**, `contextLoads()` green (nothing new is Spring-managed as `@Transactional` here, but the guard still runs).

## Phase P3b: Proof persistence + notification + migration (R5, D2)

- [ ] 3b.1 GREEN: create `api/app/src/main/resources/db/migration/V21__billing_payment_proofs.sql` — `billing_payment_proofs` table (`UNIQUE KEY uq_billing_payment_proofs_payment_id`) **and** `idx_billing_payments_status_created` on `billing_payments` (sweep support for P5 — one migration, not two).
- [ ] 3b.2 GREEN: create `PaymentProofRepository.java` (out-port) — `save`, `findByPaymentId`.
- [ ] 3b.3 RED: new `PaymentProofRepositoryAdapterTest` (Testcontainers MySQL 8) — save then find round-trips; a second `save` for the same `paymentId` overwrites the row (unique key, replacement semantics).
- [ ] 3b.4 GREEN: create JPA quartet (`api/billing/src/main/java/com/menta/billing/infrastructure/persistence/{entity/PaymentProofJpaEntity,mapper/PaymentProofJpaMapper,repository/PaymentProofJpaRepository,adapter/PaymentProofRepositoryAdapter}.java`).
- [ ] 3b.5 RED: new `SpringMailPaymentProofNotificationAdapterTest` — sends to the configured ops address (D2), mirrors `SpringMailPurchaseExceptionNotificationAdapter`'s shape from #209.
- [ ] 3b.6 GREEN: create `PaymentProofNotificationPort.java` (out-port) + `PaymentProofNotification.java` (dto) + `SpringMailPaymentProofNotificationAdapter.java` (`api/billing/src/main/java/com/menta/billing/infrastructure/notification/`) — `@Value`-injected ops address.
- [ ] 3b.7 GREEN: modify `BillingConfiguration.java` — wire `PaymentProofRepositoryAdapter`, `SpringMailPaymentProofNotificationAdapter`.
- [ ] 3b.8 Verify: `:api:billing:test :api:billing:jacocoTestCoverageVerification` + `:api:app:test` **no filter**, `contextLoads()` green. Flyway checksum sanity: `V21` is the first migration after `V20_1`.

## Phase P3c: Submit use case + `PaymentController` POST + wiring (R5, R9 — C8/C12)

This is the first phase where the proof-upload flow becomes reachable — the
R9 "unreachable without authentication" integration test belongs here, now
that a real HTTP path exists.

- [ ] 3c.1 RED: new `SubmitPaymentProofUseCaseImplTest` — Mockito `InOrder` proving the exact C12 order: `consumeProofUpload` after validation, before any write; non-owner → `PaymentNotFoundException`, zero writes; replacement deletes the old storage key **after** the new row is saved; invalid content never touches the limiter or storage.
- [ ] 3c.2 GREEN: create `PaymentProofUpload.java`, `SubmitPaymentProofCommand.java` (dto) + `SubmitPaymentProofUseCase.java` (in-port) + `SubmitPaymentProofUseCaseImpl.java` (`api/billing/src/main/java/com/menta/billing/application/{dto,port/in,usecase}/`).
- [ ] 3c.3 GREEN: create `TransactionalSubmitPaymentProofUseCase.java` (`api/billing/src/main/java/com/menta/billing/infrastructure/transaction/`) — `REQUIRED` decorator; not `final`.
- [ ] 3c.4 RED: new `PaymentControllerTest` (MockMvc) — `POST /{id}/proof`: owner from `Authentication`, never the body (C8); every error is `application/problem+json`.
- [ ] 3c.5 GREEN: create `PaymentController.java` (`api/billing/src/main/java/com/menta/billing/infrastructure/web/controller/PaymentController.java`) — `POST /api/v1/billing/payments/{id}/proof` only (GET added in P3d).
- [ ] 3c.6 GREEN: modify `SecurityConfig.java` (`api/auth/src/main/java/com/menta/auth/infrastructure/security/SecurityConfig.java`) — `.requestMatchers(HttpMethod.POST, "/api/v1/billing/payments/*/proof").authenticated()`, declared so it never shadows `/mercadopago/webhook` (`permitAll`).
- [ ] 3c.7 GREEN: modify `docker-compose.yml` / `infra/docker/**` — proof volume mounted outside any static-resource root; modify `application*.yml` — `spring.servlet.multipart.max-file-size: 6MB` (container backstop above the 5MB business rule, C11).
- [ ] 3c.8 GREEN: modify `BillingConfiguration.java` — wire `SubmitPaymentProofUseCaseImpl` + `TransactionalSubmitPaymentProofUseCase`.
- [ ] 3c.9 RED+GREEN: extend `SecurityConfigTest` — new matcher gates correctly; `/api/v1/billing/payments/mercadopago/webhook` stays `permitAll` (regression, C8).
- [ ] 3c.10 Integration (Testcontainers + real filter chain): stored proof unreachable by an unauthenticated request and by a non-owner (R9); a valid submission → `200` + ops notified; a 2nd submission replaces the 1st and re-notifies; a 4th upload → `429`.
- [ ] 3c.11 Verify: `:api:billing:test :api:billing:jacocoTestCoverageVerification` + `:api:app:test` **no filter**, `contextLoads()` green. Confirm `TransactionalSubmitPaymentProofUseCase` is not `final`.

## Phase P3d: Read payment status (R6 — C9)

Smallest remaining slice; adds a second method to the controller created in P3c.

- [ ] 3d.1 GREEN: modify `Payment.java` — add `statusChangedAt()` derived accessor (switches on the sealed status, C9).
- [ ] 3d.2 RED: new `GetPaymentUseCaseImplTest` — `status`, `createdAt`, `updatedAt = statusChangedAt().orElse(createdAt)`; non-owner → `PaymentNotFoundException`.
- [ ] 3d.3 GREEN: create `PaymentStatusResult.java` (dto) + `GetPaymentUseCase.java` (in-port) + `GetPaymentUseCaseImpl.java`.
- [ ] 3d.4 GREEN: create `TransactionalGetPaymentUseCase.java` — `REQUIRED`, `readOnly = true`; not `final`.
- [ ] 3d.5 RED+GREEN: extend `PaymentControllerTest` + `PaymentController.java` — `GET /api/v1/billing/payments/{id}` → `200` with `status/createdAt/updatedAt`, owner-only.
- [ ] 3d.6 GREEN: create `PaymentStatusResponse.java` (web dto, `api/billing/src/main/java/com/menta/billing/infrastructure/web/dto/`).
- [ ] 3d.7 GREEN: modify `SecurityConfig.java` — `.requestMatchers(HttpMethod.GET, "/api/v1/billing/payments/*").authenticated()`.
- [ ] 3d.8 GREEN: modify `BillingConfiguration.java` — wire `GetPaymentUseCaseImpl` + decorator.
- [ ] 3d.9 Verify: `:api:billing:test :api:billing:jacocoTestCoverageVerification` + `:api:app:test` **no filter**, `contextLoads()` green.

## Phase P4: Admin resolution (R8, D1 — consumes P1's `resolveManually`)

`Payment.resolveManually` and `IllegalPaymentStateTransitionException` already
exist from P1; this phase only wires a consumer for them.

- [ ] 4.1 RED: new `ResolvePaymentProofUseCaseImplTest` — approve → `Completed` + `PaymentFulfillmentService.ensure` (activate+assigned); reject → `Rejected` + `PaymentFulfillmentService.release` (cancelled); an already-`Expired` payment → throws `IllegalPaymentStateTransitionException`, zero writes (`409`, spec: "Resolving an already-expired payment fails" — reachable here by persisting a payment already in `Expired`, no dependency on P5's sweep running).
- [ ] 4.2 GREEN: create `ResolvePaymentProofCommand.java` (dto) + `ResolvePaymentProofUseCase.java` (in-port) + `ResolvePaymentProofUseCaseImpl.java`.
- [ ] 4.3 GREEN: create `TransactionalResolvePaymentProofUseCase.java` — `REQUIRED`; not `final`.
- [ ] 4.4 RED: new `PaymentAdminControllerTest` (MockMvc) — blank `reject` reason → `400` **before** any use-case call (`@NotBlank`); non-admin → `403`, nothing changes.
- [ ] 4.5 GREEN: create `PaymentAdminController.java` (`api/billing/src/main/java/com/menta/billing/infrastructure/web/controller/`) — `POST /api/v1/admin/billing/payments/{id}/approve` (no body), `POST /{id}/reject` (`{"reason": "..."}`, `@NotBlank`); `isAdmin(Authentication)` defense-in-depth boundary check mirrors `SubscriptionAdminController:85-89` (C1/C8).
- [ ] 4.6 GREEN: create `RejectPaymentRequest.java` (web dto).
- [ ] 4.7 GREEN: modify `BillingConfiguration.java` — wire `ResolvePaymentProofUseCaseImpl` + decorator. **No `SecurityConfig` change** — `/api/v1/admin/billing/payments/**` already falls under the generic `/api/v1/admin/**` → `hasRole("ADMIN")` rule; confirm via test only, do not add a redundant matcher.
- [ ] 4.8 Integration (Testcontainers): approve activates with a course snapshot identical to the MP path (frozen at approval time); reject cancels; a second resolve after expiry returns `409` and mutates nothing.
- [ ] 4.9 Verify: `:api:billing:test :api:billing:jacocoTestCoverageVerification` + `:api:app:test` **no filter**, `contextLoads()` green.

## Phase P5: Automatic 72h expiry sweep (R7 — C6, closes the flow)

Last phase. `Payment.expireAwaitingManualVerification` already exists from
P1; this phase only wires a consumer. **Do not archive the change before
this phase merges** (Sequencing Warning above).

- [ ] 5.1 GREEN: modify `PaymentRepository.java` (out-port) — `+findExpirableBankTransferIds(Instant createdBefore, int batchSize)`.
- [ ] 5.2 GREEN: modify `PaymentJpaRepository.java` — `NOT EXISTS` query against `billing_payment_proofs` filtered on `status_type` + `created_at` (uses P3b's `idx_billing_payments_status_created`); implement in `PaymentRepositoryAdapter`.
- [ ] 5.3 RED: new reflection assertion test (mirroring `SubscriptionExpiryWorkerTest`/#209's `PublishPaymentFulfillmentFailedUseCaseTest`) — `PaymentExpiryWorker.expireOne` is annotated `@Transactional(propagation = REQUIRES_NEW)`; `PaymentExpiryReconciler.tick()` carries **no** `@Transactional` and delegates to a **separate** bean (Incident Guardrail #2 — explicit test, not just code review).
- [ ] 5.4 GREEN: create `PaymentExpiryReconciler.java` (`api/billing/src/main/java/com/menta/billing/infrastructure/scheduling/`) — `@Component`, `@ConditionalOnProperty` **on the class**, `@Scheduled(fixedRateString=...)`, no `@Transactional`; query ids, dispatch one by one, log-and-skip failures. **Not** `final`.
- [ ] 5.5 GREEN: create `PaymentExpiryWorker.java` — `@Component`, `@Transactional(propagation = REQUIRES_NEW)`; re-reads `Payment` **and** `Subscription` inside the transaction; `payment.expireAwaitingManualVerification(now)` (no-op if already resolved) then `PaymentFulfillmentService.release(payment)`; one commit, both rows. **Not** `final`.
- [ ] 5.6 GREEN: modify `BillingConfiguration.java` — wire reconciler + worker beans.
- [ ] 5.7 GREEN: modify `application*.yml` — `billing.bank-transfer.expiry.enabled`, `fixedRateString`, batch size.
- [ ] 5.8 Integration (Testcontainers MySQL 8, the success criterion for this whole change): a `Payment` `AwaitingManualVerification` created >72h ago with no proof → after one sweep tick, `Payment` is `Expired` **and** `Subscription` is `CANCELLED`, asserted on both rows in one transaction. A `Payment` with a submitted proof is left untouched by the sweep (spec: "A submitted proof withholds automatic expiry").
- [ ] 5.9 Verify: `:api:billing:test :api:billing:jacocoTestCoverageVerification` + `:api:app:test` **no filter, `--rerun-tasks`**, confirm `MentaDanceApplicationTest > contextLoads()` green (this is the phase most likely to reproduce the CGLIB incident — double-check `PaymentExpiryWorker`/`PaymentExpiryReconciler` are not `final`).
- [ ] 5.10 Regression: full `./gradlew build` green; `BillingArchitectureTest` clean (no `java.nio.file` or `org.springframework.web.multipart` under `domain`/`application`).

## Out of Scope (confirmed in proposal.md)

- One-year retention purge of proofs — separate operational/legal shape, not this change.
- Any payment method other than bank transfer, including presential/in-person transfer (#36) — its own `BANK_TRANSFER` rejection stays untouched.
- Admin listing/queue of pending verifications (#33) — D1 adds only the resolution endpoint.
- Refunds, partial payments, any change to the Mercado Pago Checkout Pro path.

## Next Steps

After **P5** merges and its integration test is green: run `sdd-verify`
against all nine requirements (R1–R9), then `sdd-archive` — the same cycle
already used for #208 and #209 in this session.

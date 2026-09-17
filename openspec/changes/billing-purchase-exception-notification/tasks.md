# Tasks: Notify Student and Operations on Purchase EXCEPTION (#209)

## Fixed Facts (do not reopen)

D1–D10 (proposal) and C1–C5 (design) are locked. No task below re-derives
trigger point, propagation (`REQUIRED` for D1 vs `REQUIRES_NEW` for D10),
event-type count (two, per C3), email-port shape (email-only, per D3/D7),
ops-recipient model (single `@Value` address, D4/D8), copy content (D9/C5),
or the decision to leave `Reason` payload-only with no migration (D6). They
only implement what design.md already decided.

The C1 state-machine defect (`PaymentNotFoundException` still escapes at
sites 130/202/239 and fails the worker row) is **explicitly not fixed
here** — proposal and design both call this out of scope. Task 4.5 files
it as a follow-up issue; no task attempts a repair.

No task depends on external or unconfirmed information. Unlike #208's
Mercado Pago blocker (B6), this change has no third-party contract to
confirm — SMTP is already configured for account activation, and every
interface shape is locked in design.md.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~550–750 (2 new payload records, 1 new port+adapter, 1 modified use case, 1 new use case, 1 new dto+port+mail adapter, 1 new app handler, 1 modified handler, config + yml, plus unit/integration tests) |
| 400-line budget risk | Low per phase, all four phases comfortably under 400 |
| Chained PRs recommended | Yes |
| Suggested split | 4 PRs, cut along design's own TDD order |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

```text
Decision needed before apply: No — no external blocker exists
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: Low
```

### Suggested Work Units

Design's own Testing Strategy table gives the TDD order: RED
billing-application append → RED `REQUIRES_NEW` publisher → RED mail
adapter (two recipients / missing email) → RED handler wiring → RED
integration C2 proof. The natural PR-chain cut mirrors that order, not an
arbitrary layer split:

- **Phase A** is the purchase-level path (D1) plus the cross-module email
  port (D3/D7) it will share with Phase C — self-contained, testable in
  isolation with Mockito only, and structurally identical in shape to
  `PublishPhysicalPaymentCompletedUseCase`, already merged.
- **Phase B** is the payment-level path (D10/C1/C2) — the actual "new
  scope" #209 called out, independently testable because
  `PublishPaymentFulfillmentFailedUseCase` has no consumer yet; the three
  call-site wiring change is provable via the existing
  `PhysicalCapacityAssignmentOutboxEventHandlerTest` class alone.
- **Phase C** is the consumer: one handler dispatching both event types to
  one mail adapter. It is the first phase that can actually **send** a
  notification, and it needs both event types from A and B to exist.
- **Phase D** is proof: end-to-end recipient counting, the C2
  `REQUIRES_NEW`-survives-rollback assertion (the entire point of C2), and
  the virtual regression lock. It needs all three preceding phases merged.

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| A | D1 purchase-level append + email lookup port (D3/D7) | PR 1 | `:api:billing:test --tests "*MarkPurchaseExceptionUseCaseTest*"` + `:api:auth:test --tests "*UserEmailLookupAdapterTest*"` | N/A — Mockito only | Revert 4 files (port, adapter, event-type constant, use-case diff); nothing dispatches the new event yet, so it is inert until Phase C |
| B | D10 payment-level `REQUIRES_NEW` publisher + 3-site handler wiring (C1/C2) | PR 2 | `:api:billing:test --tests "*PublishPaymentFulfillmentFailedUseCaseTest*"` + `:api:app:test --tests "*PhysicalCapacityAssignmentOutboxEventHandlerTest*"` | N/A at unit level | Revert use case + adapter + one handler helper; the append happens but nothing consumes the row yet (worker sees an unregistered type only after Phase C exists — see D.0 ordering note) |
| C | Consumer: mail adapter + app handler dispatching both event types | PR 3 | `:api:billing:test --tests "*SpringMailPurchaseExceptionNotificationAdapterTest*"` + `:api:app:test --tests "*PurchaseExceptionNotificationOutboxEventHandlerTest*"` | N/A at unit level | Revert dto/port/adapter/handler + config bean; A and B's appends become undispatched rows again (safe — mirrors #208 Phase 5's "structurally inert" framing) |
| D | Integration proof: two recipients, C2 rollback survival, regression lock | PR 4 | `:api:app:test --tests "*PhysicalPurchaseIntegrationTest*"` | Testcontainers MySQL 8 + `GreenMail`/mock sender | Revert added test methods only; no production code changes in this phase |

**Ordering note**: merge Phases A, B, and C together before deploying, or
in strict A→B→C order — `OutboxReconciliationWorker.resolveHandler` throws
`IllegalStateException` for an unregistered event type (design's
"Correction to the proposal's rollback plan"). Deploying B without C live
would let the worker append `billing.PaymentFulfillmentFailed` rows with
no handler to dispatch them, and later require purging them exactly as
the design's Migration/Rollout section describes for a revert.

## Phase A: Purchase-level append + email lookup port (design step 1, D1/D3/D7)

- [x] A.1 RED: extend `MarkPurchaseExceptionUseCaseTest` (`api/billing/src/test/java/com/menta/billing/application/usecase/MarkPurchaseExceptionUseCaseTest.java`) — appending exactly once on `PENDING_FULFILLMENT → EXCEPTION`; zero appends on the `EXCEPTION` no-op, the `ASSIGNED` refusal, and the `PaymentNotFoundException` path; payload carries `reason` and a fixed-`Clock` `occurredAt`. Mock `BillingOutboxAppenderPort`.
- [x] A.2 GREEN: create `shared/.../billing/PurchaseExceptionedOutboxPayload.java` (`api/shared/src/main/java/com/menta/shared/billing/PurchaseExceptionedOutboxPayload.java`) — `paymentId, purchaseId, userId, reason, occurredAt`.
- [x] A.3 GREEN: add `PURCHASE_EXCEPTIONED = "billing.PurchaseExceptioned"` to `BillingOutboxEventTypes` (`api/billing/src/main/java/com/menta/billing/application/contract/BillingOutboxEventTypes.java`).
- [x] A.4 GREEN: modify `MarkPurchaseExceptionUseCase` (`api/billing/src/main/java/com/menta/billing/application/usecase/MarkPurchaseExceptionUseCase.java`) — add `BillingOutboxAppenderPort` and `Clock` (out-port, already used elsewhere in billing) plus an `ObjectWriter` for `PurchaseExceptionedOutboxPayload`; append after `purchaseRepository.save(purchase.exception())`, inside the existing `REQUIRED` transaction — mirrors `PublishPhysicalPaymentCompletedUseCase`'s constructor/writer shape exactly. No append on any other branch (`ASSIGNED` refusal, `EXCEPTION` no-op, `PaymentNotFoundException`). Also adds `PaymentRepository` — the payload's `userId` field lives only on `Payment`, never on `Purchase`, so resolving it requires this additional out-port lookup (`paymentRepository.findById(paymentId).map(Payment::getUserId)`, `null` if absent).
- [x] A.5 GREEN: modify `BillingConfiguration` (`api/billing/src/main/java/com/menta/billing/infrastructure/config/BillingConfiguration.java`) — rewire the `markPurchaseExceptionUseCase` bean with the new appender/clock/paymentRepository dependencies.
- [x] A.6 RED: `UserEmailLookupAdapterTest` (`api/auth/src/test/java/com/menta/auth/infrastructure/persistence/adapter/UserEmailLookupAdapterTest.java`) — maps `User.getEmail().getValue()`; unknown id ⇒ `Optional.empty()`. Mock `UserRepository`.
- [x] A.7 GREEN: create `shared/.../auth/UserEmailLookupPort.java` (`api/shared/src/main/java/com/menta/shared/auth/UserEmailLookupPort.java`) — `Optional<String> findEmailById(UUID)`, JavaDoc'd "email only, never a `User`, a name, a role, or a status" exactly per `UserExistencePort`'s own precedent (D7, C4).
- [x] A.8 GREEN: create `auth/.../persistence/adapter/UserEmailLookupAdapter.java` (`api/auth/src/main/java/com/menta/auth/infrastructure/persistence/adapter/UserEmailLookupAdapter.java`) — `@Component` sibling of `UserExistenceAdapter`, delegating to `UserRepository.findById(UserId).map(u -> u.getEmail().getValue())`.
- [x] A.9 Run `:api:billing:test :api:billing:jacocoTestCoverageVerification` (100%/85% floors) and `:api:auth:test :api:auth:jacocoTestCoverageVerification` (100%/85% floors) — confirm no regression. Also confirmed whole-repo `./gradlew clean compileJava compileTestJava` is clean.

## Phase B: Payment-level `REQUIRES_NEW` publisher + 3-site wiring (design step D10/C1/C2, needs Phase A's event-type file)

- [x] B.1 RED: `PublishPaymentFulfillmentFailedUseCaseTest` (`api/billing/src/test/java/com/menta/billing/application/usecase/PublishPaymentFulfillmentFailedUseCaseTest.java`) — mirrors `PublishPhysicalPaymentCompletedUseCaseTest`: serializes a null `userId` without failing; JSON failure throws `IllegalStateException` without appending; append is keyed on `PaymentId` alone (no `Purchase` lookup).
- [x] B.2 GREEN: create `shared/.../billing/PaymentFulfillmentFailedOutboxPayload.java` (`api/shared/src/main/java/com/menta/shared/billing/PaymentFulfillmentFailedOutboxPayload.java`) — `paymentId, userId?, reason, occurredAt` (no `purchaseId`, per design).
- [x] B.3 GREEN: add `PAYMENT_FULFILLMENT_FAILED = "billing.PaymentFulfillmentFailed"` to `BillingOutboxEventTypes`.
- [x] B.4 GREEN: create `billing/.../application/port/in/PublishPaymentFulfillmentFailedPort.java` — `void publish(PaymentId paymentId, UUID userId, Reason reason)`, `userId` documented nullable for site 130 (`payment == null`).
- [x] B.5 GREEN: create `billing/.../application/usecase/PublishPaymentFulfillmentFailedUseCase.java` (`api/billing/src/main/java/com/menta/billing/application/usecase/PublishPaymentFulfillmentFailedUseCase.java`) — implements the port, `@Transactional(propagation = Propagation.REQUIRES_NEW)` (C2 — this is the load-bearing decision under test, not `REQUIRED`).
- [x] B.6 GREEN: modify `BillingConfiguration` — add the `publishPaymentFulfillmentFailedUseCase` bean.
- [x] B.7 GREEN: create `app/.../billing/PublishPaymentFulfillmentFailedAdapter.java` (`api/app/src/main/java/com/menta/app/billing/PublishPaymentFulfillmentFailedAdapter.java`) — typed `api:app` callable, mirrors `MarkPurchaseExceptionAdapter` exactly.
- [x] B.8 RED: extend `PhysicalCapacityAssignmentOutboxEventHandlerTest` (`api/app/src/test/java/com/menta/app/outbox/PhysicalCapacityAssignmentOutboxEventHandlerTest.java`) — a private helper fires the publish call at the three pre-`Purchase` sites (payment absent/non-Physical target; `PhysicalCourseQuote` not found; coverage shortfall) and **not** at the two sites where `createPurchaseFromPaymentEvent` already ran; `DataIntegrityViolationException` thrown by the publish is caught and swallowed, and `markException` is still attempted afterward (unchanged C1 behavior — this task does not fix the pre-existing `PaymentNotFoundException` escape).
- [x] B.9 GREEN: modify `PhysicalCapacityAssignmentOutboxEventHandler` (`api/app/src/main/java/com/menta/app/outbox/PhysicalCapacityAssignmentOutboxEventHandler.java`) — add the `PublishPaymentFulfillmentFailedAdapter` dependency and one private helper called immediately before each of the three pre-`Purchase` `markException` call sites; wrap the call so `DataIntegrityViolationException` (duplicate on redelivery, D5-style) is caught and logged as "already notified", never rethrown.
- [x] B.10 Run `:api:billing:test :api:billing:jacocoTestCoverageVerification` and `:api:app:test --tests "*PhysicalCapacityAssignmentOutboxEventHandlerTest*"` — 5 existing + new call-site assertions all green, no regression.

## Phase C: Consumer — mail adapter + app handler (design steps "mail adapter" / "handler wiring", needs Phases A and B)

- [x] C.1 RED: `SpringMailPurchaseExceptionNotificationAdapterTest` (`api/billing/src/test/java/com/menta/billing/infrastructure/notification/SpringMailPurchaseExceptionNotificationAdapterTest.java`) — sends two `SimpleMailMessage`s (student + ops); unresolved email ⇒ ops still sent, student skipped without throwing; student body contains no `Reason` token and no refund wording (D9); ops body contains the `Reason` value; a thrown `MailException` propagates unchanged. Mockito on `JavaMailSender` + `UserEmailLookupPort`.
- [x] C.2 GREEN: create `billing/.../application/dto/PurchaseExceptionNotification.java` (`api/billing/src/main/java/com/menta/billing/application/dto/PurchaseExceptionNotification.java`) — `paymentId, purchaseId?, userId?, reason, occurredAt, eventType`.
- [x] C.3 GREEN: create `billing/.../application/port/out/PurchaseExceptionNotificationPort.java` — `void notify(PurchaseExceptionNotification)`.
- [x] C.4 GREEN: create `billing/.../infrastructure/notification/SpringMailPurchaseExceptionNotificationAdapter.java` (`api/billing/src/main/java/com/menta/billing/infrastructure/notification/SpringMailPurchaseExceptionNotificationAdapter.java`) — resolve email first (log+skip on `Optional.empty()` or null `userId`, then continue — C4 order), send student message (Spanish, D9), send ops message (may carry `Reason`, C5); inline copy constants, `@Value`-injected `billing.purchase-exception.ops-address` and `billing.purchase-exception.from-address` (D4/D8).
- [x] C.5 RED: new `PurchaseExceptionNotificationOutboxEventHandlerTest` (`api/app/src/test/java/com/menta/app/outbox/PurchaseExceptionNotificationOutboxEventHandlerTest.java`), mirroring `ActivationOutboxEventHandlerTest` — `supports` returns true for both `PURCHASE_EXCEPTIONED` and `PAYMENT_FULFILLMENT_FAILED` and false for anything else; each payload type maps to the correct `PurchaseExceptionNotification` (with/without `purchaseId`); a thrown send failure propagates so the worker marks the row `FAILED`.
- [x] C.6 GREEN: create `app/.../outbox/PurchaseExceptionNotificationOutboxEventHandler.java` (`api/app/src/main/java/com/menta/app/outbox/PurchaseExceptionNotificationOutboxEventHandler.java`) — `supports` both types; deserializes the matching payload record and maps to `PurchaseExceptionNotification`; delegates to `PurchaseExceptionNotificationPort`.
- [x] C.7 GREEN: modify `BillingConfiguration` — wire the notification port bean; modify `api/app/src/main/resources/application*.yml` — add `billing.purchase-exception.ops-address` and `billing.purchase-exception.from-address` properties (plus test-profile values as needed for Phase D).
  - **Deviation**: `BillingConfiguration` itself is unchanged. `SpringMailPurchaseExceptionNotificationAdapter` is `@Component`-annotated and implements `PurchaseExceptionNotificationPort` directly — the same pattern `SpringMailActivationNotificationAdapter`/`ActivationNotificationPort` already use with zero `@Bean` wiring in `AuthConfiguration`. `api:app`'s `@SpringBootApplication(scanBasePackages = "com.menta")` picks it up via component scan; a missing/ambiguous bean would fail the context at startup (proven by the full `MentaDanceApplicationTest > contextLoads()` run below), never a request. Adding a manual `@Bean` on top of `@Component` would register two competing beans for the same port type. `application.yml` was modified as specified (test-profile values not added — no Phase D test yet exercises them).
- [x] C.8 Verify `BillingArchitectureTest` — no `com.menta.auth..` import under `api/billing` (the mail adapter uses only `UserEmailLookupPort` from `shared`), and no contact-data type appears in `billing.application`.
- [x] C.9 Run `:api:billing:test :api:billing:jacocoTestCoverageVerification` and `:api:app:test --tests "*PurchaseExceptionNotificationOutboxEventHandlerTest*"`.

## Phase D: Integration proof (design's final TDD step — C2 proof — needs Phases A, B, C)

- [ ] D.1 RED+GREEN: extend `PhysicalPurchaseIntegrationTest` (`api/app/src/test/java/com/menta/app/integration/billing/PhysicalPurchaseIntegrationTest.java`, Testcontainers MySQL 8 + `GreenMail`/mock sender) — one real `PENDING_FULFILLMENT → EXCEPTION` transition produces exactly one `billing.PurchaseExceptioned` outbox row and **two** recipients (success criterion 3); redelivery of the triggering event still leaves exactly one row and sends no second pair (spec: "Redelivered completion event produces no second notification").
- [ ] D.2 RED+GREEN: same class — a rolled-back `PENDING_FULFILLMENT → EXCEPTION` transition leaves zero `billing.PurchaseExceptioned` rows (spec: "Rolled-back transition leaves no outbox row").
- [ ] D.3 RED+GREEN — **the C2 proof, the whole point of `REQUIRES_NEW`**: force one of the three pre-`Purchase` sites (e.g. missing `PhysicalCourseQuote`, site 202) so `markException` throws `PaymentNotFoundException` and fails the worker's row; assert the `billing.PaymentFulfillmentFailed` outbox row **survives** that rollback (it was appended in its own committed `REQUIRES_NEW` transaction) and that a subsequent retry of the same failing row adds no second `billing.PaymentFulfillmentFailed` row (unique index backstop, D5-style) and no second pair of emails (spec: "Payment-level fallback is also redelivery-safe").
- [ ] D.4 Regression lock: extend the existing virtual EXCEPTION suite (`PaymentVerificationService.ensureSubscription` path) with one added assertion that zero `billing.PurchaseExceptioned` / `billing.PaymentFulfillmentFailed` rows are appended and no email is sent (spec: "Virtual EXCEPTION emits no notification event"; #236 boundary, success criterion 7).
- [ ] D.5 File a follow-up issue for the C1 state-machine defect (`PaymentNotFoundException` still escapes at sites 130/202/239, marking the worker row `FAILED` and retrying to blacklist even though the notification itself is now delivered) — explicitly out of scope for this change per proposal and design; do not attempt a fix here.
- [ ] D.6 Run `./gradlew test check` — all coverage floors hold, 0 failures/errors across `shared`/`auth`/`virtual`/`physical`/`billing`/`app`, confirmed from each module's aggregated JUnit XML `<testsuite>` header.

## Out of Scope (confirmed in proposal.md)

- No fix to the C1 state-machine defect (`PaymentNotFoundException` escaping at sites 130/202/239) — filed as a follow-up in D.5, not repaired here.
- No `reason` column on `billing_purchases`, no migration (D6).
- No virtual subscription EXCEPTION notification (deferred to #236).
- No admin listing/query endpoint for EXCEPTION purchases (deferred to #237).
- No refunds, retries, or auto-remediation; the EXCEPTION state machine's legal transitions are unchanged.
- No push/SMS/in-app channels; no BFF or Android surfaces.

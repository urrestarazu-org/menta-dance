# Tasks: Admin verification of manual (bank-transfer) payments (#33, US-BILLING-005)

## Fixed Facts (do not reopen)

D1 (local HMAC signed-token proof URL, `Mac`+`MessageDigest.isEqual`, no S3/MinIO) — D2
(`billing_reconciliation_tasks` gains `resolved`/`resolved_at`/`resolved_by` via V24, corrections
resolve the tied task) — D3 (`/api/v1/admin/billing/payments/...`, diverging from the issue's
literal route) — D8 (buyer email outside the commit transaction, failure never rolls back a
decision) — D9 (correction is a status transition out of `ReconciliationRequired`, never a data
edit) are resolved with the user. D4 (one `billing_audit_log` table, decision+motivo shape, not a
diff shape), D5 (Spanish buyer emails, two templates), D6 (`@Value` secret, fail-fast, no usable
default), D7 (Lombok on new mutable/constructor-injected classes; existing classes not
retrofitted) are locked. C1–C11 (design) are locked, including three corrections the design phase
made to the proposal, carried here as facts: `SecurityConfig` **is** modified (one `permitAll`
matcher, not "verify only") because `.anyRequest()` grants unmapped paths by default (C6); a new
`:api:shared` port `UserContactPort.emailOf(UUID)` is required since `UserExistencePort`
deliberately never returns an email (C8); `IllegalPaymentStateTransitionException` gains a 4-arg
constructor and `PaymentExceptionHandler`'s `409` detail string is generalized, updating one
existing test assertion (C1). Correction evidence is stored inside the audit `reason` column as
`"{motivo} | evidencia: {evidence}"` (D4/C7) — not a dedicated column.

## Deviations from design (flagged explicitly)

1. **Page-size behavior**: the spec's "List payments awaiting manual verification" requirement and
   its Scenario "Page size above 50 is rejected" require an explicit `400` for `pageSize > 50`.
   Design C10 proposes clamping instead (`size = Math.min(Math.max(size,1),50)`), which would
   enforce the cap but never produce the `400` the spec's own scenario asserts. Spec is what
   `sdd-verify` checks against, so tasks below implement **explicit rejection**, not the clamp.
2. **Absent proof-token status code**: the spec's "Token-authenticated proof file serving"
   requirement and its "Absent token is rejected" Scenario require `401` for a missing token,
   reserving `403` for tampered/expired ones. Design's C2/C5/testing-strategy fold every failure —
   including absence — into one indistinguishable `403`. Tasks below add a controller-level
   explicit "no token" branch returning `401` **before** the request reaches
   `ProofAccessTokenVerifier`; a token that is present but malformed/tampered/expired still yields
   the same indistinguishable `403` design describes, preserving its anti-oracle property while
   satisfying the spec's absent-vs-invalid split.
3. **No buyer email on correction**: design's Data Flow diagram draws an `afterCommit` email after
   a correction commits, but neither the "Audited correction" spec requirement nor the proposal's
   Success Criteria mention buyer notification on correction — only audit + task resolution. Tasks
   below do **not** wire `PaymentDecisionNotificationPort` into `CorrectPaymentUseCaseImpl`; adding
   it later is a separate, spec'd change.

**Slicing (no deviation from design's split)**: design proposes 5 independently deliverable
slices and explicitly defers the final call to this phase. Unlike physical-manual-checkin's 3→4
split, per-slice estimates below stay near but under the 400-line budget without needing a 6th
slice; Slice 4 (proof access) is the one design itself flags for solo review — kept as one PR
rather than fragmented, to preserve that reviewer focus on the whole security-critical surface at
once.

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~1,900–2,200 total (1 migration, 3 new entities/adapters, 2 new domain methods/exceptions, 2 new use cases, 1 new controller, 1 `SecurityConfig` line, 1 new shared port + adapter, 2 notification classes, OpenAPI + Bruno + doc, plus tests for all of it) |
| Per-slice estimate | P1 ~340–400; P2 ~380–440; P3 ~250–300; P4 ~420–480; P5 ~360–420 |
| 400-line budget risk | High — P2 and P4 individually approach or exceed 400 lines once tests are counted; P4 carries the security matcher |
| Chained PRs recommended | Yes |
| Suggested split | 5 PRs, P1 → P2 → P3 → P4 → P5 (design's 5-slice split, no deviation — see rationale above) |
| Delivery strategy | ask-on-risk (no delivery strategy was supplied to this phase; defaulting to the skill's default) |
| Chain strategy | **Recommended: stacked-to-main** (precedent: #44 device-management P1/P2/P3, #45 physical-manual-checkin P1–P4) — pending user confirmation before `sdd-apply` starts |

```text
Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High
```

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| P1 | `billing_audit_log` + V24, `PaymentAuditAction`, reconciliation-resolution columns/query, audit-on-approve/reject | PR 1 | `./gradlew :api:billing:test --tests "*PaymentAuditRepositoryAdapterTest*" --tests "*ReconciliationTaskJpaRepositoryTest*" --tests "*ResolvePaymentProofUseCaseImplTest*"` | None needed — unit/`@DataJpaTest`, no full filter chain | Revert all P1 files; approve/reject lose the audit write, transition/fulfillment untouched |
| P2 | `correctManually`, exception ctor, corrections use case + endpoint | PR 2 | `./gradlew :api:billing:test --tests "*PaymentTest*" --tests "*CorrectPaymentUseCaseImplTest*" --tests "*PaymentAdminControllerTest*"` | Mockito unit tests only | Revert P2 files only; `correctManually`/`CorrectPaymentUseCaseImpl` unreachable again, approve/reject untouched |
| P3 | Pending-verification list query + endpoint, explicit `400` on `pageSize>50` | PR 3 | `./gradlew :api:billing:test --tests "*PaymentJpaRepositoryTest*" --tests "*PaymentAdminControllerTest*"` | `@DataJpaTest` for the query, MockMvc for the endpoint | Revert P3 files only; no admin inbox, nothing else affected |
| P4 | Signer/verifier, storage `read`, serving controller, `SecurityConfig` matcher, detail endpoint | PR 4 | `./gradlew :api:billing:test --tests "*ProofAccessToken*Test*" --tests "*PaymentProofControllerTest*" --tests "*SecurityConfigTest*"` and `:api:app:test --tests "*PaymentProofIntegrationTest*"` | Testcontainers MySQL, full Spring Security filter chain | Revert P4 files only; the token endpoint and `SecurityConfig` line disappear, proofs stay unservable as before |
| P5 | `UserContactPort`, buyer mail adapter, `afterCommit`, OpenAPI/Bruno/US doc | PR 5 | `./gradlew :api:billing:test --tests "*SpringMailPaymentDecisionNotificationAdapterTest*" --tests "*ResolvePaymentProofUseCaseImplTest*"` and `:api:app:test` (full) | `TransactionSynchronizationManager` initialized manually; no external SMTP | Revert P5 files only; approve/reject/correction keep working, buyer just stops receiving email |

## Phase P1: Audit trail + migration + reconciliation resolution

- [x] 1.1 GREEN: `billing/domain/model/PaymentAuditAction.java` (new) — enum `APPROVE`, `REJECT`, `CORRECTION` (D4).
- [x] 1.2 GREEN: `api/app/.../db/migration/V24__billing_audit_log_and_reconciliation_resolution.sql` (new) — `billing_audit_log` (`id`, `payment_id`, `admin_id`, `action`, `reason` nullable, `created_at`) plus additive `resolved BOOLEAN NOT NULL DEFAULT FALSE`, `resolved_at DATETIME(6) NULL`, `resolved_by BINARY(16) NULL` on `billing_reconciliation_tasks` (D2/D4).
- [x] 1.3 RED: `PaymentAuditRepositoryAdapterTest` (new) — `append(...)` persists one row with `payment_id`, `admin_id`, `action`, `reason`, `created_at` (Requirement "Append-only billing_audit_log").
- [x] 1.4 GREEN: `billing/application/port/out/PaymentAuditRepository.java` (new), `billing/infrastructure/persistence/entity/BillingAuditLogJpaEntity.java` (new, Lombok `@Getter`/`@Setter`/`@NoArgsConstructor`/`@AllArgsConstructor` — D7), `BillingAuditLogJpaRepository` (new), `PaymentAuditRepositoryAdapter` (new, `@Component` + `@Transactional(REQUIRED)` + Lombok `@RequiredArgsConstructor` — D7, mirrors `PhysicalDeviceAuditRepositoryAdapter`).
- [x] 1.5 RED: extend `ReconciliationTaskJpaRepositoryTest` — `resolveOpenByPaymentId` sets `resolved`/`resolvedAt`/`resolvedBy` on an open tied row; re-running on an already-resolved row is a no-op (`AND resolved = false`); a payment with no tied task returns `0`, no exception.
- [x] 1.6 GREEN: `ReconciliationTaskJpaEntity.java` (modify: +`resolved`/`resolvedAt`/`resolvedBy`, **hand-written accessors — not retrofitted to Lombok**, matching the file's existing style per D7's "existing classes not retrofitted"), `ReconciliationTaskJpaRepository` (+`resolveOpenByPaymentId` `@Modifying` query, C11).
- [x] 1.7 RED: extend `ResolvePaymentProofUseCaseImplTest` — approve writes one audit row (`action=APPROVE`, no reason); reject writes one row carrying the rejection reason; `adminId` comes from `ResolvePaymentProofCommand.adminId()` (Scenarios "Approve writes an audit row", "Reject writes an audit row with a reason").
- [x] 1.8 GREEN: `ResolvePaymentProofCommand.java` (modify: +`adminId`), `ResolvePaymentProofUseCaseImpl.java` (modify: one added `auditRepository.append(...)` call per branch — transition/fulfillment logic byte-unchanged), `PaymentAdminController.java` (modify: extract `adminId` from `Authentication`, same source `PhysicalCheckInController` uses).
- [x] 1.9 GREEN: `BillingConfiguration.java` (modify) — wire `PaymentAuditRepositoryAdapter` into `ResolvePaymentProofUseCaseImpl`.
- [x] 1.10 Verify: `./gradlew :api:billing:test --tests "*PaymentAuditRepositoryAdapterTest*" --tests "*ReconciliationTaskJpaRepositoryTest*" --tests "*ResolvePaymentProofUseCaseImplTest*"` green; coverage gates (95%/90%) green. P1 ready for PR.

## Phase P2: Domain correction transition + corrections endpoint

- [x] 2.1 RED: extend `IllegalPaymentStateTransitionExceptionTest` — the new 4-arg constructor carries a caller-supplied expected-state name; the existing 3-arg constructor still delegates to `"AwaitingManualVerification"` (byte-unchanged behavior).
- [x] 2.2 GREEN: `IllegalPaymentStateTransitionException.java` (modify: +4-arg ctor, 3-arg delegates; `ERROR_CODE`/getters unchanged, C1).
- [x] 2.3 RED: extend `PaymentTest`, parameterized over `PaymentStatus` — `correctManually(APPROVED, now)`/`correctManually(REJECTED, now)` from `ReconciliationRequired` → `Completed`/`Rejected`; from each of the other six statuses → `IllegalPaymentStateTransitionException` naming `"ReconciliationRequired"`; `resolveManually`'s existing assertions untouched.
- [x] 2.4 GREEN: `Payment.java` (modify: +`correctManually(ManualVerificationDecision, Instant)`, D9/C1 verbatim — `resolveManually` not edited).
- [x] 2.5 RED: extend `PaymentExceptionHandlerTest` — the single `409` detail assertion updates to `"El pago no está en un estado que admita esta operación."` (one generalized string, no new handler method).
- [x] 2.6 GREEN: `PaymentExceptionHandler.java` (modify: generalize the existing `illegalStateTransition` detail string only).
- [x] 2.7 RED: `CorrectPaymentUseCaseImplTest` (new) — `InOrder` (Mockito) asserts `correctManually` → save → fulfillment `ensure`/`release` → `resolveOpenByPaymentId` → `auditRepository.append(paymentId, adminId, CORRECTION, "{reason} | evidencia: {evidence}")`; a `0`-row resolve still succeeds (Scenario "Admin corrects a reconciliation-required payment"; D4 evidence format).
- [x] 2.8 RED: extend the same test — a payment not `ReconciliationRequired` throws `IllegalPaymentStateTransitionException` (→ `409`) with `verifyNoInteractions` on save/resolve/audit (Scenario "Correction on a payment not requiring reconciliation is rejected").
- [x] 2.9 GREEN: `CorrectPaymentUseCase.java` (new in-port) + `CorrectPaymentUseCaseImpl.java` (new, Lombok `@RequiredArgsConstructor` — D7) — `billing/application/usecase/`. Also added (not explicitly named by this task but structurally required by ArchUnit's application→infrastructure boundary): `application/port/out/ReconciliationTaskRepository.java` (new out-port wrapping D2/C11's `resolveOpenByPaymentId`, since the application layer cannot depend on the `ReconciliationTaskJpaRepository` JPA type directly) + `infrastructure/persistence/adapter/ReconciliationTaskRepositoryAdapter.java` (new adapter) + `infrastructure/transaction/TransactionalCorrectPaymentUseCase.java` (new decorator, mirroring `TransactionalResolvePaymentProofUseCase`) + `application/dto/CorrectPaymentCommand.java` (new command record).
- [x] 2.10 RED: extend `PaymentAdminControllerTest` — blank `reason`/`evidence` → `400` (existing `@NotBlank` path); non-admin → `403`, no use-case invocation (Scenario "Non-admin is rejected").
- [x] 2.11 GREEN: `CorrectPaymentRequest.java` (new record, `@NotBlank reason`, `@NotBlank evidence`, `decision`), `PaymentAdminController.java` (modify: +`POST .../{paymentId}/corrections`, D3 prefix — no new `SecurityConfig` matcher, covered by the existing `/api/v1/admin/**` rule per C6).
- [x] 2.12 GREEN: `BillingConfiguration.java` (modify) — wire `CorrectPaymentUseCaseImpl` (+ its `ReconciliationTaskRepository` dependency).
- [x] 2.13 Verify: `./gradlew :api:billing:test --tests "*PaymentTest*" --tests "*IllegalPaymentStateTransitionExceptionTest*" --tests "*PaymentExceptionHandlerTest*" --tests "*CorrectPaymentUseCaseImplTest*" --tests "*PaymentAdminControllerTest*" --tests "*ArchitectureTest*"` green; coverage gates green. P2 ready for PR.

## Phase P3: Pending-verification list

- [x] 3.1 RED: `PaymentJpaRepositoryTest` (extend) — `findAwaitingManualVerification` returns oldest-first results with `hasProof` correctly correlated, via the explicit `countQuery` (Scenario "Admin lists pending-verification payments").
- [x] 3.2 GREEN: `PendingVerificationPage`/`PendingVerificationItem` (new records, `billing/application/dto/`, D7), `PaymentRepository.java` (modify: +`findAwaitingManualVerification(int page, int size)`), `PendingVerificationRow` projection (new), `PaymentJpaRepository` (modify: +query with explicit `countQuery`, C10), `PaymentRepositoryAdapter` (modify: builds `PageRequest`, maps to `PendingVerificationPage`).
- [x] 3.3 RED: extend `PaymentAdminControllerTest` — `pageSize=51` → `400`, no results (Scenario "Page size above 50 is rejected" — explicit reject, not clamp, per Deviations #1); default `size=20`; non-admin → `403`; anonymous → `401`/`403` (Scenarios "Non-admin is rejected", "Anonymous caller is rejected").
- [x] 3.4 GREEN: `PaymentAdminController.java` (modify: +`GET /api/v1/admin/billing/payments` list endpoint; `pageSize > 50` throws a validation exception mapped to `400` via the existing handler — no clamping).
- [x] 3.5 Verify: `./gradlew :api:billing:test --tests "*PaymentJpaRepositoryTest*" --tests "*PaymentAdminControllerTest*"` green; coverage gates green. P3 ready for PR.

## Phase P4: Signed proof access — solo review (design's #1 risk, `SecurityConfig` matcher)

- [x] 4.1 RED: `ProofAccessTokenSignerTest`/`ProofAccessTokenVerifierTest` (new) — round-trip sign→verify with a fixed `Clock`+secret; tampered payload/MAC, swapped halves, missing/extra `.`, non-Base64, wrong version, non-UUID id, expired-by-1s, valid-at-expiry-minus-1s all throw the same `InvalidProofAccessTokenException`/message; signature checked **before** expiry.
- [x] 4.2 GREEN: `InvalidProofAccessTokenException.java` (new, `INVALID_PROOF_TOKEN`), `ProofAccessTokenSigner.java`/`ProofAccessTokenVerifier.java` (new, `billing/infrastructure/proof/`, D1/C2, `Mac`+`MessageDigest.isEqual` lifted from `HmacSha256WebhookSignatureVerifier`).
- [x] 4.3 RED: extend the signer test — absent secret fails Spring context startup; blank secret throws `IllegalArgumentException` at construction (D6/C3).
- [x] 4.4 GREEN: `@Value` secret/TTL constructor wiring (`billing.bank-transfer.proof.token-secret`, `token-ttl-seconds:900`, C3).
- [x] 4.5 RED: extend `LocalFilesystemPaymentProofStorageAdapterTest` — `read` returns stored bytes; a traversal key throws; a missing key throws (never empty).
- [x] 4.6 GREEN: `PaymentProofStoragePort.java` (modify: +`read(String storageKey)`), `LocalFilesystemPaymentProofStorageAdapter.java` (modify: `read` reuses `resolveWithinRoot` verbatim, C4).
- [x] 4.7 RED: `PaymentProofControllerTest` (new, MockMvc standalone) — **absent `?token=` → `401`**, not `403` (Deviations #2); a present-but-tampered/expired/malformed token → `403`; a valid token for a different `{paymentId}` → `403`; `Content-Disposition: inline`/`Cache-Control: no-store` headers on success.
- [x] 4.8 GREEN: `PaymentProofController.java` (new, `@PaymentEndpoint`, `GET /api/v1/billing/payments/{paymentId}/proof?token=`, C5) — explicit missing-token branch returns `401` before invoking the verifier; a present token delegates to `ProofAccessTokenVerifier`; cross-checks the token's embedded payment id against the path variable.
- [x] 4.9 GREEN: `PaymentExceptionHandler.java` (modify: +`@ExceptionHandler(InvalidProofAccessTokenException.class)` → `403`).
- [x] 4.10 RED: extend `PaymentAdminControllerTest` — detail response includes a signed proof URL (15-minute embedded expiry) when a proof exists, absent when not (Scenario "Admin views payment detail with a proof link"); non-admin → `403` (Scenario "Non-admin is rejected").
- [x] 4.11 GREEN: `PaymentAdminController.java` (modify: +`GET /api/v1/admin/billing/payments/{paymentId}` detail endpoint, minting the URL via `ProofAccessTokenSigner.sign(...)`).
- [x] 4.12 RED: `SecurityConfigTest` (extend, parameterized) — the new `GET .../proof` → `permitAll` rule doesn't shadow/get shadowed by the `POST` proof rule, the `GET .../payments/*` detail rule, `mercadopago/webhook`, or `/api/v1/admin/**`, in any declaration order (C6's non-overlap table).
- [x] 4.13 GREEN: `SecurityConfig.java` (modify — **one line**) — `.requestMatchers(HttpMethod.GET, "/api/v1/billing/payments/*/proof").permitAll()`, declared after the existing `POST` proof rule with C6's load-bearing comment.
- [x] 4.14 RED: `PaymentProofIntegrationTest` (new, Testcontainers MySQL, full filter chain) — signed URL serves bytes within 15 minutes; after expiry → `403`; tampered → `403`; **anonymous with no token → `401`, proving the path is matched, not granted by fallthrough** (proposal risk #1; delta Scenarios "Unauthenticated access fails", "A valid signed token reads the file", "An expired or tampered token still fails").
- [x] 4.15 GREEN: confirmation-only unless 4.14 surfaces a wiring gap not caught by 4.1–4.13.
- [x] 4.16 Verify: `./gradlew :api:billing:test --tests "*ProofAccessToken*Test*" --tests "*LocalFilesystemPaymentProofStorageAdapterTest*" --tests "*PaymentProofControllerTest*" --tests "*PaymentAdminControllerTest*" --tests "*SecurityConfigTest*" --tests "*ArchitectureTest*"` and `:api:app:test --tests "*PaymentProofIntegrationTest*"` green; coverage gates green. **Review this slice alone (design's own instruction).** P4 ready for PR.

## Phase P5: Buyer notifications + contract

- [x] 5.1 RED: `UserContactPortTest` (new, `:api:auth`) — `emailOf(userId)` returns the user's email when found, `Optional.empty()` when not.
- [x] 5.2 GREEN: `api/shared/.../auth/UserContactPort.java` (new, `Optional<String> emailOf(UUID)`), `:api:auth` adapter implementing it next to `UserExistenceAdapter` (C8).
- [x] 5.3 RED: `SpringMailPaymentDecisionNotificationAdapterTest` (new) — `notifyApproved` sends the Spanish confirmation template; `notifyRejected` sends the rejection template with `notification.reason()` interpolated verbatim; both resolve the address via `UserContactPort`; a missing address is handled without throwing.
- [x] 5.4 GREEN: `PaymentDecisionNotificationPort.java` (new), `PaymentDecisionNotification.java` (new record, D7), `SpringMailPaymentDecisionNotificationAdapter.java` (new, `@Value` from-address, Lombok where applicable, C8).
- [x] 5.5 RED: extend the adapter test — `afterCommit` fires only after a real commit, never on rollback; outside an active transaction it sends inline; a `RuntimeException` during send is caught and logged, **never rethrown** (Scenario "Email failure does not roll back the decision"; C9/D8).
- [x] 5.6 GREEN: `sendAfterCommit`/`guarded` helpers on the adapter (C9 — the `try/catch` is load-bearing: without it, `afterCommit` propagates SMTP failures back to the caller of `commit()`).
- [x] 5.7 RED: extend `ResolvePaymentProofUseCaseImplTest` — approve calls `notifyApproved` exactly once and **never** `PaymentProofNotificationPort` (ops adapter); reject calls `notifyRejected` with the reason, same `verifyNoInteractions(opsAdapter)` (Scenarios "Approval sends a confirmation email", "Rejection sends a rejection email with the reason").
- [x] 5.8 GREEN: `ResolvePaymentProofUseCaseImpl.java` (modify: one added `notifyApproved`/`notifyRejected` call per branch, after P1's audit call — transition/fulfillment still byte-unchanged).
- [x] 5.9 GREEN: `BillingConfiguration.java` (modify) — wire `UserContactPort` and `PaymentDecisionNotificationPort`.
- [x] 5.10 GREEN: `api/openapi/billing-v1.yaml` (modify) — the four new/changed endpoints, the `401`/`403` proof-token contract, D3's route-divergence note.
- [x] 5.11 GREEN: `bruno/API - Direct/billing/` (new requests) — list, detail, corrections, token-authenticated proof fetch.
- [x] 5.12 GREEN: `docs/user-stories/US-BILLING-005.md` (modify) — lift from `Draft`; record D3's divergence from the issue's literal route.
- [x] 5.13 Verify: `./gradlew check` (full monorepo regression — `UserContactPort` touches `:api:shared`/`:api:auth`); confirm every Success Criterion in `proposal.md`, including that `PaymentProofNotificationPort` is never invoked by approve/reject. P5 ready for PR — after merge, run `sdd-verify` against the 6 `billing-manual-payment-verification` requirements and the 2 `bank-transfer-subscription` delta requirements, then `sdd-archive`.

## Requirement → Task Coverage

| # | Requirement | Covered by |
|---|---|---|
| R1 | Paginated pending-verification list, oldest-first, max 50 (reject > 50) | 3.1–3.5 |
| R2 | Payment detail with a 15-minute signed proof URL | 4.10–4.11 |
| R3 | Token-authenticated proof serving; absent (401)/tampered/expired (403) rejected | 4.1–4.9, 4.12–4.16 |
| R4 | Audited exceptional correction out of `ReconciliationRequired` | 2.1–2.13 |
| R5 | `billing_audit_log` on approve, reject, correction | 1.3–1.9, 2.7–2.9 |
| R6 | Buyer Spanish decision emails, outside the transaction, no rollback on failure | 5.1–5.9 |
| Delta 1 | Admin resolution gains audit + buyer email; transition/`409`/`400` unchanged | 1.7–1.9, 5.7–5.8 |
| Delta 2 | Proof storage not publicly reachable, except a valid signed token | 4.7–4.9, 4.12–4.14 |

## Next Steps

After **P5** merges and its integration tests are green: run `sdd-verify` against all 6
`billing-manual-payment-verification` requirements plus the 2 `bank-transfer-subscription` delta
requirements, then `sdd-archive`.

# Verify report S2 (BFF) - billing-purchase-fulfillment-status (#559)

Verdict: PASS WITH WARNINGS. CRITICAL 0, WARNING 3, SUGGESTION 2.
Scope: commits a0614e9..b9d4c3e vs origin/develop; spec bff-class-purchase-return (6 req / 10 scenarios), tasks 0.1, 2.1-2.9.

## Evidence
- Command: `./gradlew :bff:test :bff:checkstyleMain :bff:checkstyleTest :bff:jacocoTestCoverageVerification --rerun-tasks`
- Second full rerun: BUILD SUCCESSFUL; 821 tests, 0 failures, 0 errors, 2 skipped; checkstyle main/test and JaCoCo verification green.
- First rerun: 9 AuthApiAdapterLoginFailureTest failures (unrelated, no auth files touched), did not reproduce on the next full `--rerun-tasks`.
- PR size: 765 insertions + 26 deletions = 791 changed lines (budget 800); production+tests 141+86 plus docs 12, rest openspec artifacts.
- evidence_revision = sha256 of `git diff origin/develop...HEAD` at HEAD b9d4c3e = 8e56f05f0cb09c61abd274cbced96a31b17f24f0fcc5a87cf8962d11876a8ca8

## Checks
1. EXCEPTION/RESOLVED: titles/bodies exact ("Pago recibido"/"Compra resuelta"), link is the back-to-classes link only; tests assert title/message, no `assignedSessions`, `verifyNoInteractions(getAssignedSessionsUseCase)`. OK.
2. ASSIGNED, PENDING_FULFILLMENT, null keep "Pago confirmado" and call sessions (parameterized test). Unknown/absent map to null in `PurchaseFulfillment.fromUpstream` and adapter test. OK.
3. Non-COMPLETED ignore fulfillment (switch on progress; test with EXCEPTION for PENDING/REJECTED/CANCELLED/EXPIRED). OK.
4. Subscription flow: only stubs wrapped in `statusOf(..., null)`; assertions untouched; `subscriptionNotice(progress)`. OK.
5. Wire: `PaymentWire(status, fulfillment)` String; matches billing-v1.yaml `fulfillment` enum [PENDING_FULFILLMENT, ASSIGNED, EXCEPTION, RESOLVED, null]; unknown -> null. OK.
6. Degradation: see W1.
7. docs/06-BILLING-API.md table matches controller behavior. OK.
8. Size 791/800. OK (tight).

## Findings
- W1 WARNING: a structurally unmappable `fulfillment` (JSON object/array) is not degraded to null. `PaymentWire.fulfillment` is a String; Jackson decode failure surfaces as RuntimeException in `execute(...)` and becomes `ServiceUnavailableException`, i.e. the whole return page shows the error, contradicting spec "cannot be mapped -> null, MUST NOT show an error". Only absent, null and unknown-string values are tested. Fix: type the field as `JsonNode`/`Object` or `@JsonIgnoreProperties`-safe tolerant deserializer, plus a test. Upstream payment-status call failure keeps existing handling (NotFound -> redirect; ServiceUnavailable -> error page), as the spec allows; covered by pre-existing tests.
- W2 WARNING: scenario "Assigned lists sessions" is asserted with an empty session list only (attribute equals `List.of()`); no test with non-empty rows under `ASSIGNED`. Pre-existing COMPLETED+null test covers rows.
- W3 WARNING: flaky AuthApiAdapterLoginFailureTest failures in one run (unrelated to this slice); re-run green.
- S1 SUGGESTION: no explicit assertion on absence of a contact link (only model attribute checks plus the fixed back link).
- S2 SUGGESTION: non-COMPLETED test uses only EXCEPTION, not RESOLVED.

Tasks 0.1, 2.1-2.9 are marked complete and match code state.

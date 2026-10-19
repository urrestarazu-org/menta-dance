```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:0bfff3eb32fd3d0391597601d5f65c28722a99e2f3c1bdd4548a8057ae46cc00
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 8/8
scenarios: 21/21
test_command: ./gradlew :bff:test :bff:checkstyleMain :bff:checkstyleTest :bff:jacocoTestCoverageVerification --rerun-tasks
test_exit_code: 0
test_output_hash: sha256:fad7193bdf5a104ce17de127be9fe31c95c353fc7bc6440ef4c542bda60cff60
build_command: ./gradlew :bff:test :bff:checkstyleMain :bff:checkstyleTest :bff:jacocoTestCoverageVerification --rerun-tasks
build_exit_code: 0
build_output_hash: sha256:fad7193bdf5a104ce17de127be9fe31c95c353fc7bc6440ef4c542bda60cff60
```
# Verify report PR2 (S2 checkout, return, docs): bff-plan-subscription-checkout (#361)

Commit verified: c9883872033963e729d7b224bc4b5a51b4f1223f (branch feature/bff-plan-subscription-checkout-s2c-docs, tip of stack). Code commits fa934c6, c4fc334, 27c21b9, 2cad030; docs 66d2eef, c988387.
Scope: PR2 tasks 5.1-8.3 against bff-plan-subscription-checkout spec (8 requirements, 21 scenarios). PR1 re-checked for regression only.

evidence_revision derivation: sha256 of the string `<HEAD tree hash>\n<gate log sha256>`, with tree f28db90707705bfac053aad8e87d0978549db65c and gate log sha256 fad7193bdf5a104ce17de127be9fe31c95c353fc7bc6440ef4c542bda60cff60 (full Gradle stdout+stderr of the gate run, EXIT 0). The exact separator PR1 used is not recorded in its report, so byte-for-byte equivalence to the PR1 recipe is not proven. test_output_hash and build_output_hash are that same log hash (one combined command).

Verdict: PASS WITH WARNINGS. CRITICAL 0, WARNING 5, SUGGESTION 3.

## Gate
Exit 0, BUILD SUCCESSFUL (--rerun-tasks, Docker up). :bff:test 806 tests in 88 classes, 0 failures, 0 errors, 2 skipped (pre-existing). checkstyleMain, checkstyleTest, jacocoTestCoverageVerification green. BffArchitectureTest 3/3 (ArchUnit). Bundle line coverage 95.0% (1829/1925), branches 592/677; floor 85%.
PR2 classes: SubscriptionController 22/22 lines 6/6 branches; PurchaseFlowErrors 58/58, 26/26; PhysicalPurchaseController 69/69, 29/30; SubscriptionApiAdapter 70/71, 30/32; SubscribeToPlanUseCaseImpl 1/1.
PR2 tests: SubscriptionApiAdapterTest 32, SubscriptionControllerTest 9, SubscriptionReturnControllerTest 9, SubscriptionCheckoutIntegrationTest 10, SubscribeToPlanUseCaseImplTest 2.
PR1 regression: PlansControllerTest 8, GetPlansViewUseCaseImplTest 9, PlansOfferIntegrationTest 8, PlansLoginReturnIntegrationTest 5 all pass.
Class flow: PhysicalPurchaseControllerTest 6 and PhysicalPurchaseIntegrationTest 30 pass; `git diff origin/develop...HEAD` lists no PhysicalPurchase* test file (zero edited assertions).

## Task completeness
5.1-8.2 [x]. 8.3: gate green, PR not opened (open item, expected at this stage). 4.3 same as PR1.

## Spec compliance matrix (bff-plan-subscription-checkout)
Test paths under bff/src/test/java/com/menta/bff/. C = SubscriptionController(Test) , R = SubscriptionReturnControllerTest, I = SubscriptionCheckoutIntegrationTest.
| Requirement / scenario | Covering test | Result |
|---|---|---|
| R1 Missing CSRF rejected, no upstream | I.csrfLess (403, WireMock verify 0) | COMPLIANT |
| R1 Non-student 403, no upstream | I.instructorRefused | COMPLIANT |
| R1 Anonymous POST to login | I.anonymousRedirected | COMPLIANT |
| R2 Body carries plan, MERCADO_PAGO, rendered key, bearer | SubscriptionApiAdapterTest.createsCheckout; SubscribeToPlanUseCaseImplTest.usesTheRenderedKey; I.studentSubscribes (upstream body) | COMPLIANT |
| R2 Double submit same key | SubscribeToPlanUseCaseImplTest.doubleSubmitSameKey | COMPLIANT (see S2) |
| R3 Allowed URL redirects, session {SUBSCRIPTION, PAY-1} | C.redirectsAndStoresSession; I.studentSubscribes | COMPLIANT |
| R3 Disallowed URL not followed, notice | C.rejectedUrl; I.rejectedUrl (no session writes) | COMPLIANT |
| R4 Already active with end date | C.alreadyActiveWithDate, alreadyActiveWithoutDate; I.alreadyActive | COMPLIANT |
| R4 SUBSCRIPTION_CONFLICT distinct | C.conflict; SubscriptionApiAdapterTest 409 mapping | COMPLIANT |
| R4 422 plan not available + link to /plans | C.planNotAvailable | COMPLIANT |
| R4 400 invalid, no leak | C.invalidAndUnavailable; blank planId test | COMPLIANT |
| R4 503 unavailable, no leak | C.invalidAndUnavailable; SubscriptionApiAdapterTest.checkoutFailsClosed | COMPLIANT |
| R5 URL params never decide | R.confirmed (status=approved, payment_id=someone-elses; status call uses stored id); I.returnPageCompleted | COMPLIANT |
| R5 Payment owned by someone else (404) | R.notFound (fallback, session cleared, not /classes); I.returnPageNotFound | COMPLIANT |
| R5 No session state | R.noPaymentInSession (kind=SUBSCRIPTION, no id: fallback + /plans link); I.returnPageWithoutPayment (no kind: redirect /classes) | COMPLIANT WITH DEVIATION (W1) |
| R6 Confirmed "se está activando", no class copy | R.confirmed; I.returnPageCompleted | COMPLIANT |
| R6 Pending, no activation claim | R.pending (keeps session) | COMPLIANT |
| R6 Rejected, link to /plans | R.rejected, cancelled, expired; I.returnPageRejected | COMPLIANT |
| R7 Class return unchanged | PhysicalPurchaseControllerTest, PhysicalPurchaseIntegrationTest (unedited, pass) | COMPLIANT |
| R7 Latest checkout wins | R.classPayClearsKind (subscription then class); C.redirectsAndStoresSession overwrites id and kind (class then subscription implicit, S1) | COMPLIANT |
| R8 D1 deviation documented | docs/06-BILLING-API.md (section "Suscribirse a un plan desde la web", paragraph "Desvío de diseño (D1)") | COMPLIANT (doc inspection) |

## Cross-cutting checks
- Security: `POST /suscripciones` matched `hasRole("STUDENT")` before anyRequest; CSRF default stays on (only /actuator/health exempt); non-STUDENT 403 and anonymous to login proven by I tests through the real filter chain. PASS.
- Redirect only via CheckoutUrlPolicy.isAllowed; rejected URL maps to 503-style notice, URL never logged. PASS.
- Logging: adapter logs only exception class name; controller logs a constant message. No token/PII/URL logged. PASS.
- Idempotency key per plan, rendered at /plans (PR1) and passed untouched. PASS.
- Session discrimination: absent kind = class buyer; `pay()` clears kind; terminal progress clears id and kind; pending keeps both. PASS.
- API finding (task 8.2) independently confirmed: PaymentVerificationService.applyOutcome calls PaymentFulfillmentService.release for any terminal non-settled payment; release saves Subscription.cancelled() when the row occupiesUserSlot (PENDING/ACTIVE); SubscriptionStatus.occupiesUserSlot is false for CANCELLED/EXPIRED; PaymentExpiryWorker and CorrectPaymentUseCaseImpl/ResolvePaymentProofUseCaseImpl also call release. Tests: PaymentVerificationServiceTest.a_rejected_payment_cancels_the_pending_subscription (asserts CANCELLED and occupiesUserSlot false) and a_rejected_payment_with_an_already_released_subscription_writes_nothing. Not re-run in this verify (api module outside the declared gate); source inspection only. design.md Open Questions records this accurately.
- Docs lookup (26-LOCAL-DEV): table billing_payments, columns id (BINARY(16)), expected_external_reference, target_modality, status_type, created_at exist on PaymentJpaEntity; container menta-mysql and database menta exist in docker-compose. Plausible; not run against a live DB (W4).

## Design/deviation judgment
Known deviation (no kind in session redirects to /classes; subscription fallback only when kind=SUBSCRIPTION): ACCEPTABLE. A session with neither attribute is indistinguishable from a class buyer, existing class tests pin the redirect, and the spec scenario "No session state" can only be met when the kind is stored. It is recorded in design.md ("Deviations recorded during PR2 apply") but the spec text and one docs sentence still say otherwise (W1, W2).

## TDD compliance
RED evidence for 5.1, 5.2, 7.1 is plausible from commit order and task text but no run logs exist (apply-progress is a summary). Tasks 6.1/6.2: test and implementation written together, no separate RED (W3). Tests still assert behavior: WireMock verify counts (no upstream call on 403/rejected), exact redirect targets, session attribute values, copy substrings and absence of class-assignment wording, upstream body leak checks. No tautologies, ghost loops, or smoke-only assertions found. Triangulation adequate (409 x2 with/without date, 400/503, five PaymentProgress states, 404 vs no session).

## Issues
CRITICAL: none.
WARNING:
- W1 Spec scenario "No session state" and requirement text say a STUDENT with no `{kind, paymentId}` gets the subscription fallback; implementation redirects to /classes unless kind=SUBSCRIPTION. Accepted deviation, but specs/bff-plan-subscription-checkout/spec.md was not amended; amend at archive.
- W2 docs/06-BILLING-API.md step 5 ends "Sin pago en la sesión o con 404, se muestra un aviso con enlace a /plans en lugar de redirigir a /classes", which overstates: without a stored SUBSCRIPTION kind the page still redirects to /classes. Fix wording.
- W3 Tasks 6.1/6.2 (checkout POST, error mapping) had no separate RED run; admitted by apply. Tests assert real behavior, so risk is low.
- W4 The 26-LOCAL-DEV lookup SQL/container steps were not executed against a live DB or simulator.
- W5 Review budget: s2b is 797 of 800 lines (near limit); 8.3 PR not yet opened and board fields pending.
SUGGESTION:
- S1 Add an explicit test for class checkout then subscription checkout in one session (kind and id overwritten to subscription).
- S2 Add a controller/integration test posting the same form twice and asserting both upstream bodies carry the same key.
- S3 Amend spec R5/"No session state" to the observed behavior and note the class-buyer ambiguity.

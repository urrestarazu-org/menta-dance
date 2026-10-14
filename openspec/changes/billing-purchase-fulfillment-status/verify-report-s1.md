```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:40c657e218fae86daa998a510dd8f808b1f7092a59a18b994b6e020cb284cbb1
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 6/6
scenarios: 10/10
test_command: ./gradlew :api:billing:test :api:app:test :api:billing:checkstyleMain :api:billing:checkstyleTest :api:app:checkstyleMain :api:app:checkstyleTest :api:billing:jacocoTestCoverageVerification :api:app:jacocoTestCoverageVerification --continue --rerun-tasks
test_exit_code: 0
test_output_hash: sha256:c7f67198d6000f8cdd65d692b1dccf931deb405b7bce7d81e44ca5bac1d5f64c
build_command: ./gradlew :api:billing:test :api:app:test :api:billing:checkstyleMain :api:billing:checkstyleTest :api:app:checkstyleMain :api:app:checkstyleTest :api:billing:jacocoTestCoverageVerification :api:app:jacocoTestCoverageVerification --continue --rerun-tasks
build_exit_code: 0
build_output_hash: sha256:c7f67198d6000f8cdd65d692b1dccf931deb405b7bce7d81e44ca5bac1d5f64c
```
# Verify report S1 (API): billing-purchase-fulfillment-status (#559)

evidence_revision = sha256 of `git diff develop...HEAD` at 5c3954b (cfea65d, 5c3954b).
Scope: spec billing-purchase-fulfillment-status (6 requirements / 10 scenarios), tasks 1.1-1.11.
bff-class-purchase-return (slice 2) is OUT OF SCOPE, not failing.

## Gate (re-run with --rerun-tasks, exit 0)
:api:billing:test 964 tests, 0 failures/errors/skipped. :api:app:test 517 tests, 0 failures/errors/skipped.
Checkstyle main/test (billing, app) and jacocoTestCoverageVerification (billing, app) green.
ArchitectureTest 8/8. Targeted: GetPaymentUseCaseImplTest 9, PaymentStatusContractTest 4,
PresentialPurchaseExceptionPathIntegrationTest 1 (real MySQL container), all pass.

## Tasks
1.1-1.11 all [x] and match code state. 1.7's "fails before 1.2/1.6" RED not re-demonstrable (no logs).

## Scenario matrix
| Scenario | Evidence | Result |
|---|---|---|
| R1 PENDING/ASSIGNED/EXCEPTION/RESOLVED | GetPaymentUseCaseImplTest @EnumSource(FulfillmentStatus) (4 values, real Purchase factories); PaymentControllerTest EXCEPTION JSON | COMPLIANT |
| R2 Subscription payment | no Purchase row -> null: GetPaymentUseCaseImplTest.without_a_purchase_row; PaymentControllerTest null serialized (hasKey) | COMPLIANT (no dedicated subscription-target test; same code path) |
| R2 COMPLETED w/o Purchase yet | Integration test: status COMPLETED, fulfillment null before outbox, EXCEPTION after (real bean) | COMPLIANT |
| R3 Another user's payment | a_non_owner_gets_PaymentNotFoundException + verifyNoInteractions(purchaseRepository); purchase lookup is after the owner check in source | COMPLIANT |
| R3 Unknown payment | a_missing_payment_throws_PaymentNotFoundException | COMPLIANT |
| R4 No leak | Response record has only status/createdAt/updatedAt/fulfillment (String name); controller test asserts reason/resolution/resolutionType/resolutionNote absent; contract test forbids them in YAML | COMPLIANT (see W2) |
| R5 Wiring in tx | BillingConfigurationTest instanceOf TransactionalGetPaymentUseCase (readOnly REQUIRED); integration test via @Autowired GetPaymentUseCase | COMPLIANT |
| R5 Architecture | ArchitectureTest 8/8 | COMPLIANT |
| R6 Endpoint in contract | PaymentStatusContractTest reads ../openapi/billing-v1.yaml (= api/openapi/billing-v1.yaml, real file); get 200+404, properties, enum, no forbidden | COMPLIANT |
| R6 Drift fails build | properties == record components; enum == FulfillmentStatus.values()+null | COMPLIANT |

## Focus points
1. findByPaymentId deviation: ACCEPTABLE. Callers of PurchaseRepository.findByPaymentId: GetPaymentUseCaseImpl and CreatePurchaseFromPaymentEventUseCase step 1 only (writers use findByPaymentIdForUpdate, unchanged). Step 1 uses only the status (non-EXCEPTION returns existing); the lost-race path re-reads via findByPaymentIdForUpdate (locking sessions, unchanged). Purchase row and sessions are inserted atomically in one REQUIRES_NEW tx and the only session write is guarded by "if empty", so a snapshot that sees the purchase sees its sessions; sessions never change afterwards. The prior FOR UPDATE on session rows in step 1 gave no extra guarantee (the purchase read before it was a plain snapshot read already). Not weakened. Javadoc on PurchaseSessionJpaRepository.findByPurchaseIdOrderByPositionAsc still lists "findByPaymentIdForUpdate" only, which is consistent.
2. No leak / non-owner: PASS (structural DTO + tests; lookup after ownership).
3. OpenAPI contract test: PASS, real file via Path.of("..","openapi","billing-v1.yaml").
4. docs/06-BILLING-API.md matches behavior (field always serialized, null cases, enum names, 404 not 403, no leak). PASS.
5. Integration test exercises real transactional bean against MySQL Testcontainers. PASS, with W1.

## Issues
CRITICAL: none.
WARNING:
- W1 Integration test only covers null and EXCEPTION (EXCEPTION purchase has an empty session list). The non-locking session read with a NON-empty list (PENDING/ASSIGNED/RESOLVED) is only verified by the mocked PurchaseRepositoryAdapterTest, not on real MySQL.
- W2 The no-leak scenario with a RESOLVED purchase that has type+note is verified structurally (DTO shape) and with a mocked use case at HTTP level; no test seeds a RESOLVED purchase with note and asserts the serialized body.
- W3 Deviation (PurchaseRepositoryAdapter non-locking read) is outside design.md file list; record it in design.md/apply-progress at archive.
SUGGESTION:
- S1 Contract test does not assert the 200 response schema $ref targets PaymentStatusResponse, nor required/status enum; add.
- S2 Add a subscription-target payment case to GetPaymentUseCaseImplTest for explicit scenario traceability.
- S3 Add an integration assertion with a PENDING/ASSIGNED purchase carrying sessions (covers W1).

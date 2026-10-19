```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:24bfca3f5d16c1f9b5bc8f0dcfad47f4e486f91f9c0c756b1b64b04707de2726
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 6/6
scenarios: 13/13
test_command: ./gradlew :bff:test :bff:checkstyleMain :bff:checkstyleTest :bff:jacocoTestCoverageVerification --rerun-tasks
test_exit_code: 0
test_output_hash: sha256:37bea222ecdee4c2b5ee1cbc0bbea198859e18d69f01fd9d392aad217b250f5b
build_command: ./gradlew :bff:test :bff:checkstyleMain :bff:checkstyleTest :bff:jacocoTestCoverageVerification --rerun-tasks
build_exit_code: 0
build_output_hash: sha256:37bea222ecdee4c2b5ee1cbc0bbea198859e18d69f01fd9d392aad217b250f5b
```
# Verify report PR1 (S1 plans-offer): bff-plan-subscription-checkout (#361)

Commit verified: fcced491ecd75058c7817fe1e7f2c42d4402e48d (branch feature/bff-plan-subscription-checkout-s2-checkout-return, code identical to S1; PR1 code commits b2ace59, 5f6df66, 26447f4, e2667ce).
Scope: PR1 only. Requirements/scenarios counted are the bff-plans-view spec (6 requirements, 13 scenarios); the bff-plan-subscription-checkout spec (8 requirements, 21 scenarios) and tasks 4.3 (PR opening), 5.1-8.3 are PR2, reported pending, not failures.

Verdict: PASS WITH WARNINGS. CRITICAL 0, WARNING 4, SUGGESTION 3.

## Gate
Exit 0, BUILD SUCCESSFUL (--rerun-tasks, Docker up). :bff:test 761 tests, 0 failures, 0 errors, 2 skipped (84 classes; skips pre-existing, none in PR1 classes). checkstyleMain, checkstyleTest, jacocoTestCoverageVerification green. BffArchitectureTest 3/3 (ArchUnit). Bundle line coverage 94.7% (1724/1821), floor 85%.
PR1 classes: PlansController 15/15 lines 12/12 branches; GetPlansViewUseCaseImpl 20/20, 7/7; SubscriptionController 2/2; BffSecurityConfig 50/50; SubscriptionApiAdapter 39/40 lines, 15/16 branches.
New tests: SubscriptionApiAdapterTest 17, GetPlansViewUseCaseImplTest 9, PlansControllerTest 8, PlansOfferIntegrationTest 8, PlansLoginReturnIntegrationTest 5 (all pass).

## Task completeness
1.1-4.2 [x] (8/8). 4.3 gate run done, PR not opened (open item, expected). PR2 tasks 5.1-8.3 pending (out of scope).

## Spec compliance matrix (bff-plans-view)
| Requirement / scenario | Covering test | Result |
|---|---|---|
| Anonymous route: anonymous opens /plans, no redirect, shared cache | PlansOfferIntegrationTest.anonymous (200, no /me call); PlansControllerTest.anonymousKeepsSharedCache; BillingPlansViewIntegrationTest | COMPLIANT (see W2) |
| Personalized page not cacheable | PlansControllerTest.studentIsPersonalizedAndNotCacheable, otherRoleIsNotCacheable; PlansOfferIntegrationTest.plansPageAs asserts no-store through real filter chain | COMPLIANT |
| Upstream failures: billing 404/429/503 shared error view | BillingPlansViewIntegrationTest.billingUnavailable_*; PlansControllerTest 404/503 resolution (pre-existing, still green) | COMPLIANT |
| Button by role: anonymous sees button, login, returns to /plans | PlansOfferIntegrationTest.anonymous (href /plans/suscribirme); PlansLoginReturnIntegrationTest.anonymousReturnsToPlansAfterLogin | COMPLIANT |
| Button by role: student sees form with CSRF and render-time key | PlansOfferIntegrationTest.studentWithoutSubscription (_csrf, planId, one key per plan); GetPlansViewUseCaseImplTest.studentWithoutSubscription, keysAreMappedByPlan | COMPLIANT |
| Button by role: other roles no button | GetPlansViewUseCaseImplTest.otherRole; PlansOfferIntegrationTest.otherRole; PlansControllerTest.otherRoleIsNotCacheable | COMPLIANT |
| /me only with token; anonymous makes no call | PlansOfferIntegrationTest.anonymous (WireMock verify 0); GetPlansViewUseCaseImplTest.anonymous/otherRole verifyNoInteractions; SubscriptionApiAdapterTest.active (bearer) | COMPLIANT |
| State table: ACTIVE | GetPlansViewUseCaseImplTest.studentActive; SubscriptionApiAdapterTest.active; PlansOfferIntegrationTest.active | COMPLIANT |
| State table: PENDING full id copyable, no control | studentPending; SubscriptionApiAdapterTest.pending; PlansOfferIntegrationTest.pending | COMPLIANT |
| State table: EXPIRED date + button | studentExpired; SubscriptionApiAdapterTest.expired; PlansOfferIntegrationTest.expired | COMPLIANT |
| State table: 404 NO_SUBSCRIPTION | studentWithoutSubscription; SubscriptionApiAdapterTest.noSubscription; PlansOfferIntegrationTest.studentWithoutSubscription | COMPLIANT |
| Fail-closed: 503 / timeout, no control | SubscriptionApiAdapterTest.failsClosedOnAnyOtherStatus (404-other, 401, 429, 500, 503, 418), timeoutFailsClosed, transportFaultNeverLogsToken, failsClosedOnIncompleteBody; PlansOfferIntegrationTest.failsClosed; PlansControllerTest.studentWithoutTokenFailsClosed | COMPLIANT |
| Fail-closed: unexpected status 500 | PlansOfferIntegrationTest.unexpectedStatusFailsClosed; adapter parameterized | COMPLIANT |

PR1 parts of the checkout spec: none are behavioral; the CSRF form fields (planId, idempotencyKey from render time) are covered above. All other checkout scenarios are PR2 (pending).

## Cross-cutting checks
- Fail-closed /me: adapter maps everything except 200 and 404 NO_SUBSCRIPTION (and incomplete bodies) to ServiceUnavailableException; use case does not catch; controller throws when STUDENT has no token. PASS.
- Role detection: ROLE_STUDENT -> STUDENT, other authenticated -> OTHER, non-Authentication principal -> ANONYMOUS. PASS.
- no-store only when viewer != ANONYMOUS. PASS.
- Shared catalog cache (BillingApiAdapter) is token-less; per-user state lives only in PlansOffer, never in the cached list. PASS.
- /plans/suscribirme: authenticated route (anyRequest), HttpSessionRequestCache scoped to GET /plans/suscribirme, defaultSuccessUrl("/dashboard") without alwaysUse; controller redirects to fixed /plans. Tests: other protected page lands on /dashboard, direct login /dashboard, next= param ignored. PASS.
- Token never logged: adapter logs only exception class name; test asserts log lines. PASS.

## Design deviations (all confirmed, none breaks a spec)
1. SubscriptionCheckout DTO deferred to PR2: confirmed absent; unused in PR1. OK.
2. Enums SubscribeAction/OfferNotice nested in PlansOffer: confirmed. OK.
3. Principal instead of Authentication in PlansController: confirmed (instanceof Authentication). OK.
4. PathPatternRequestMatcher from org.springframework.security.web.servlet.util.matcher: confirmed. OK.

## Issues
CRITICAL: none.
WARNING:
- W1 Review budget: PR1 about 1,161 changed lines (475 main / 686 test) vs 800 budget; user accepted the overrun.
- W2 Spring Security default headers are not customized, so an anonymous /plans response likely carries Security's own Cache-Control (no-cache, no-store). The spec's "shared-cache headers (not no-store)" is only asserted at controller level (controller sends no header); no integration test asserts the anonymous header. Behavior equals pre-change, so no regression, but the scenario's intent is weakly proven.
- W3 Cross-slice risk: PR1 renders a form posting to /suscripciones, which exists only in PR2. If PR1 ships alone, a STUDENT submit yields an error. Merge/deploy PR1 together with PR2, or keep stacked-to-main so S2 lands before release.
- W4 SubscriptionApiAdapter has one uncovered line and one uncovered branch (probably null-body guard); bundle gate still passes.
SUGGESTION:
- S1 Add an integration assertion that anonymous /plans does not carry the personalized no-store marker, or document that Security defaults apply.
- S2 noOpenRedirect test shows saved request keeps the query string (?next=...); harmless because the target is fixed, but could be dropped by a request cache that strips queries.
- S3 TDD evidence: apply-progress (obs 1765) is a summary, with no per-task RED/GREEN/triangulation table; commit-per-unit history and passing tests substantiate, but the cycle is not auditable.

## TDD compliance
Test files exist for every task row 1.1-4.1 and pass on rerun. Assertions are behavioral (WireMock verify counts, response body content, redirect targets, header values); no tautologies, ghost loops or smoke-only tests found. Triangulation adequate (parameterized status/body matrices, 4 state cases, 3 viewer kinds).

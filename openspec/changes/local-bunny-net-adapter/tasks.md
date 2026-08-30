# Tasks: Local Bunny.net Adapter (Issue #129, amended D7)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~200 + ~400 + ~320 + ~630 (design estimate, 4 slices) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1 (D7, ~200) → PR2 (adapter, ~400) → PR3 (fixtures, ~320) → PR4 (Bruno journey, ~630) |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main (base = `develop` per Git Flow; each PR targets the prior PR's branch until merge) |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

Design's own 3-slice sketch overflowed (~950 in one slice); re-sliced to 4 per `design.md` §"PR slicing". PR1 (D7) is independently revertible and MUST ship before PR2-4 so the authorization change never rides inside E2E infrastructure.

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|----|-----------------------|-----------------|--------------------|
| 1 | D7 policy correction | PR1→develop | `./gradlew :api:virtual:test --tests "*LessonAccessPolicyTest*" --tests "*GetPublicLesson*UseCaseImplTest*"` | N/A — pure unit, no E2E needed for D7 | revert PR1 commit; enum/branch fully restored, no migration |
| 2 | Local adapter + wiring | PR2→PR1 | `./gradlew :api:virtual:test --tests "*Bunny*"` | N/A — profile inactive by default, no live server needed | delete `infrastructure/cdn/local/`, revert `VirtualConfiguration` split |
| 3 | E2E fixtures (virtual+billing) | PR3→PR2 | `./gradlew :api:virtual:test :api:billing:test --tests "*E2eBunnyNet*Fixture*"` | N/A — fixtures inert outside `e2e-bunny-net` profile | delete fixture classes, no persisted state |
| 4 | Bruno journey + runner | PR4→PR3 | `scripts/e2e/bunny-net.sh` (full run) | `scripts/e2e/bunny-net.sh` — real Compose stack, real HTTP journey | delete `bruno/E2E/bunny-net/`, env file, runner script |

> Out-of-scope note (do not create a task): `GetPublicLessonStreamUseCaseImpl.get` never calls `findPublishedById`, so an unpublished course can still stream. Pre-existing bug, unrelated to D7. File as a separate issue after this change ships.

## Phase 1 — PR1: D7 Policy Correction (`virtual`)

- [x] 1.1 RED: `LessonAccessPolicyTest` — rename `unplanned_course_is_public_for_an_anonymous_caller` → `unplanned_course_denies_a_protected_lesson_to_an_anonymous_caller`, assert `SUBSCRIPTION_REQUIRED` for `(false,false)` + null user.
- [x] 1.2 RED: `LessonAccessPolicyTest` — add `unplanned_course_denies_a_protected_lesson_to_an_authenticated_caller_without_entitlement`, `(false,false)` + real user → `SUBSCRIPTION_REQUIRED`.
- [x] 1.3 RED: `LessonAccessPolicyTest` — add `unplanned_course_still_honours_a_frozen_paid_entitlement`, `(false,true)` + real user → `SUBSCRIPTION_GRANTED` (pins A6).
- [x] 1.4 RED: `GetPublicLessonUseCaseImplTest` — add `unplanned_course_premium_lesson_is_forbidden_without_exposing_the_video_id`, `(false,false)` → `ForbiddenLessonAccessException`, no `videoId`.
- [x] 1.5 RED: `GetPublicLessonStreamUseCaseImplTest` — add `unplanned_course_premium_lesson_is_denied_without_signing`, `(false,false)` → `AccessDenied`, `verify(signatureService, never()).generateSignedUrl(...)`.
- [x] 1.6 GREEN: `LessonAccessPolicy.java:51-53` — delete `!access.courseInAnyPlan()` branch (A6); cascade becomes free → preview → entitlement.
- [x] 1.7 GREEN: `LessonAccessDecision.java:17` — delete `PUBLIC_UNPLANNED_COURSE` constant + Javadoc line.
- [x] 1.8 REFACTOR: `LessonAccessPolicy.java:11-19` — rewrite class Javadoc (drop "course absent from all plans" as public rule; document ADR-0041).
- [x] 1.9 Update `api/openapi/virtual-v1.yaml` line 58 (drop unplanned-course public route) and line 74 (403 description covers unplanned courses); reuse `LESSON_FORBIDDEN_SUBSCRIPTION_REQUIRED`.
- [x] 1.10 Write `docs/adr/0041-lesson-access-unplanned-course-denial.md` (D7 ruling, A6/A7 rejected-alternative rationale, production data-audit release gate).
- [x] 1.11 Verify: run full `LessonAccessPolicyTest`, `GetPublicLessonUseCaseImplTest`, `GetPublicLessonStreamUseCaseImplTest`, `VirtualConfigurationTest` — confirm no other case implicitly depended on the deleted branch.

## Phase 2 — PR2: Local Adapter + Wiring (`virtual`)

- [x] 2.1 RED: `LocalBunnyNetSignatureServiceTest` (new, `infrastructure/cdn/local/`) — same TTL/videoId twice → identical `sig`; assert `exp` = TTL-derived; assert URL shape and no real credential.
- [x] 2.2 GREEN: `LocalBunnyNetSignatureService` POJO implementing `BunnyNetSignatureService`; `sig = SHA-256("menta-local-e2e|" + videoLibraryId + "|" + videoId + "|" + exp)`, 64 lowercase hex.
- [x] 2.3 RED: `VirtualConfigurationTest` — add case per profile combination (`e2e-bunny-net` vs default) asserting exactly one `BunnyNetSignatureService` bean of the expected type.
- [x] 2.4 RED: `VirtualConfigurationTest` — `e2e-bunny-net` + `prod|production|staging` → context refresh fails.
- [x] 2.5 GREEN: `VirtualConfiguration.java` — split `bunnyNetSignatureService` bean into two `@Profile("e2e-bunny-net")` / `@Profile("!e2e-bunny-net")` `@Bean` methods; guard reads `Environment` inside the local factory and throws on prod-like profiles (A2).
- [x] 2.6 `StringFormatBunnyNetSignatureService.java` — Javadoc-only update marking it the explicit non-local branch; fix ADR reference to `docs/adr/0040`.
- [x] 2.7 Write `docs/adr/0040-local-bunny-net-signature-adapter.md` (deterministic seam, non-secret `sig`, fail-closed guard, A7 debt note on `courseInAnyPlan`).
- [x] 2.8 Verify: `StringFormatBunnyNetSignatureServiceTest` still passes unmodified.

## Phase 3 — PR3: E2E Fixtures (`virtual` + `billing`)

- [ ] 3.1 RED: `E2eBunnyNetVirtualFixtureTest` (new, `api/virtual/.../infrastructure/e2e/`) — asserts fixed-id seeding of `UNPLANNED_COURSE_ID` (preview + protected module, no plan row) and `PLANNED_COURSE_ID` (protected module).
- [ ] 3.2 GREEN: `E2eBunnyNetVirtualFixture`, `@Profile("e2e-bunny-net")`, seeds both courses via Virtual repository ports with id-carrying constructors (A3).
- [ ] 3.3 RED: `E2eBunnyNetBillingFixtureTest` (new, `api/billing/.../infrastructure/e2e/`) — asserts `PLANNED_COURSE_ID` (UUID literal, comment cross-referencing the virtual fixture) is linked to a billing plan.
- [ ] 3.4 GREEN: `E2eBunnyNetBillingFixture`, `@Profile("e2e-bunny-net & e2e-mercadopago")` per D7 scope reduction — only needed for the premium-grant scenario.

## Phase 4 — PR4: Bruno Journey + Runner

- [ ] 4.1 RED: `scripts/e2e/bunny-net-runner-test.sh` (mirrors `catalog-content-runner-test.sh`) — unknown arg exits non-zero; missing Docker prerequisite fails closed; `--clean` isolates only `menta-e2e-bunny-net`.
- [ ] 4.2 GREEN: `scripts/e2e/bunny-net.sh` — `set -euo pipefail`, `readonly` ports (API 18082, MySQL 33307, Redis 36380, Mailpit 38026, SMTP 31026, OTEL 34319/34320, Grafana 33001), `trap stop_api EXIT INT TERM`, Compose project `menta-e2e-bunny-net`, activates `e2e-bunny-net,e2e-catalog-content,e2e-mercadopago`.
- [ ] 4.3 Create `bruno/environments/e2e-bunny-net.bru` pinning `APP_CDN_BUNNYNET_PULLZONEHOSTNAME=https://local-bunny-net.invalid`, `APP_CDN_BUNNYNET_VIDEOLIBRARYID=e2e-library`.
- [ ] 4.4 Create `bruno/E2E/bunny-net/` ordered journey: login student → unplanned preview `/stream` 200 → unplanned protected `/stream` 403 (D7, no `videoId`/signed URL) → planned protected `/stream` 403 (pre-checkout) → checkout → signed approved webhook → poll activation → planned protected `/stream` 200.
- [ ] 4.5 Update Bruno/local-dev docs with the one documented command (`scripts/e2e/bunny-net.sh`) reproducing all three acceptance scenarios.
- [ ] 4.6 Verify: `scripts/e2e/bunny-net.sh` exits zero end-to-end; `catalog-content.sh` unaffected (disjoint Compose project/ports).

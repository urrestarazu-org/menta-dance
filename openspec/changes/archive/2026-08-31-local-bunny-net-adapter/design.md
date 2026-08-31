# Design: Local Bunny.net Adapter

> **Amended for D7.** Decision A4 of the previous revision is superseded by A4'. A1, A2, A3, A5
> and the signed-URL contract are unchanged. A6–A8 are new.

## Technical Approach

`BunnyNetSignatureService` (port) is unchanged. `VirtualConfiguration` keeps its explicit
composition root and exposes two complementary `@Profile` `@Bean` methods (D1). The local adapter
is a POJO producing a deterministic, credential-free signed-shaped URL mapping 1:1 onto the current
`PublicLessonStreamView` (D3). The premium journey composes `e2e-bunny-net,e2e-catalog-content,e2e-mercadopago`
(D2) behind a dedicated runner (D5). **D7 additionally corrects one production authorization rule
inside `LessonAccessPolicy`** — the only part of this change that alters runtime behavior.

## Architecture Decisions

| # | Choice | Rejected alternative | Rationale |
|---|--------|----------------------|-----------|
| A1 | Two `@Profile` `@Bean` methods (`bunnyNetSignatureService` / `localBunnyNetSignatureService`) | Billing's `@Component @Profile` scan | Keeps Virtual's explicit composition root; distinct bean names + complementary profiles ⇒ exactly one candidate; `VirtualConfigurationTest` and `StringFormatBunnyNetSignatureServiceTest` stay green unmodified |
| A2 | Production guard executed inside the local `@Bean` factory (takes `Environment`) | `ApplicationRunner` check (auth-fixture precedent) | Runners execute after the web server is up; a factory throw aborts context refresh, satisfying "fail before accepting any request" |
| A3 | `E2eBunnyNetVirtualFixture` persists fixed-id courses through Virtual repository ports | Bruno creates content via admin API | `VirtualCourse`/`VirtualModule`/`VirtualLesson` expose id-carrying constructors, so ids are deterministic; the journey needs no admin login or id plumbing |
| **A4'** | **Two fixture courses**: `UNPLANNED_COURSE_ID` (preview module + protected module, no billing row ever) and `PLANNED_COURSE_ID` (protected module, linked by `E2eBunnyNetBillingFixture`) | One course + plan link (previous A4) | Under D7 the denial scenario no longer needs `billing_plan_courses`. Two courses prove the whole cascade E2E — public preview, D7 denial (unplanned), entitlement denial (planned, pre-checkout), grant (planned, post-webhook) — instead of collapsing two premium cases onto one row |
| A5 | `sig` binds `exp`; determinism proven by unit tests with explicit expirations | Asserting equal `sig` across two live HTTP calls | TTL is `now + 4h`, so live calls differ by seconds. Bruno asserts the URL contract, not `sig` equality |
| **A6** | **Delete** the `!courseInAnyPlan()` branch; decide protected access purely on `currentEntitlement` | Literal veto `if (!courseInAnyPlan()) return SUBSCRIPTION_REQUIRED;` before the entitlement check | Both deny every caller who has not paid, which is D7's ruling. The veto additionally revokes a **frozen, paid** entitlement (`CourseAccessSnapshot(false, true)`, produced today by `VirtualCourseEntitlementService:42`) the moment an admin unlinks a plan row — a new regression against paying customers, and it contradicts the proposal's "the Billing snapshot rule stays untouched". Deletion matches D7's own wording: cascade becomes free → preview → entitlement |
| **A7** | Keep `CourseAccessSnapshot.courseInAnyPlan` in the shared port | Drop the field | Removing it widens the diff into `api/shared` + `api/billing` + their tests, which the proposal scopes out. After A6 it has no production reader in `virtual`; recorded as follow-up debt in ADR-0041 |
| **A8** | D7 gets its own `docs/adr/0041`; `0040` stays reserved for the adapter (D4) | Give D7 the number `0040` | `BunnyNetSignatureService:18` and `VirtualConfiguration:96` already cite `0040` as the *signing* note. Reusing it for an authorization rule would invalidate D4's zero-cost fix. `0041` merging before `0040` is acceptable — ADR numbers are topic identifiers, not a timeline |

## D7 — concrete diff

`api/virtual/.../application/usecase/LessonAccessPolicy.java` — new `decide()` body (net −5 lines):

```java
if (lesson.isFree())    { return LessonAccessDecision.PUBLIC_FREE; }
if (module.isPreview()) { return LessonAccessDecision.PUBLIC_MODULE_PREVIEW; }
try {
    CourseAccessSnapshot access = entitlementPort.resolveCourseAccess(
        actingUserId, lesson.getCourseId().getValue().toString());
    if (access == null || actingUserId == null) {
        return LessonAccessDecision.SUBSCRIPTION_REQUIRED;   // was: null-check, then unplanned fall-through
    }
    return access.currentEntitlement()
        ? LessonAccessDecision.SUBSCRIPTION_GRANTED
        : LessonAccessDecision.SUBSCRIPTION_REQUIRED;
} catch (RuntimeException unavailable) {
    return LessonAccessDecision.SUBSCRIPTION_REQUIRED;
}
```

Class Javadoc (lines 11–19) — replace *"in the order free lesson, preview module, then a course
absent from all plans"* with: local public rules win in the order **free lesson, then preview
module**; every other lesson is protected and only a current frozen Billing entitlement grants it.
A course absent from every plan is a commercial configuration gap, not a grant (ADR-0041).

`LessonAccessDecision.java` — delete constant `PUBLIC_UNPLANNED_COURSE` and its Javadoc line
(verified: sole producer `LessonAccessPolicy:52`, sole assertion `LessonAccessPolicyTest:53`, no
`switch` over the enum anywhere).

`api/openapi/virtual-v1.yaml` — line 58: drop `o curso que no pertenece a ningún plan` from the
public-route list; line 74: `403` description becomes *"La lección protegida no tiene un entitlement
actual para el caller — incluye cursos sin plan asociado."* No new status code, `code`, or `detail`
string; `LESSON_FORBIDDEN_SUBSCRIPTION_REQUIRED` is reused (proposal round-2 assumption).

### D7 test impact (audited, not assumed)

`GetPublicLessonUseCaseImplTest` and `GetPublicLessonStreamUseCaseImplTest` construct
`CourseAccessSnapshot` only as `(true, true)` or `(true, false)` — **no existing case depends on the
unplanned fall-through, so none breaks.** The gap is the opposite: the leak D7 closes is uncovered.
New RED tests (all fail on `main` today):

| Test | Given | Then |
|---|---|---|
| `LessonAccessPolicyTest.unplanned_course_denies_a_protected_lesson_to_an_anonymous_caller` (replaces `unplanned_course_is_public_for_an_anonymous_caller`) | `(false,false)`, `actingUserId=null` | `SUBSCRIPTION_REQUIRED`; port still consulted once |
| `…unplanned_course_denies_a_protected_lesson_to_an_authenticated_caller_without_entitlement` | `(false,false)`, real `userId` | `SUBSCRIPTION_REQUIRED` (case never covered before) |
| `…unplanned_course_still_honours_a_frozen_paid_entitlement` | `(false,true)`, real `userId` | `SUBSCRIPTION_GRANTED` — pins A6 against a silent flip to the veto variant |
| `GetPublicLessonUseCaseImplTest.unplanned_course_premium_lesson_is_forbidden_without_exposing_the_video_id` | `(false,false)` | `ForbiddenLessonAccessException`; no `PublicLessonPremiumAccessibleView`, no `videoId` |
| `GetPublicLessonStreamUseCaseImplTest.unplanned_course_premium_lesson_is_denied_without_signing` | `(false,false)` | `AccessDenied`; `verify(signatureService, never()).generateSignedUrl(any(), anyLong())` |

## Local signed-URL contract (unchanged)

```
<pullZoneHostname>/<videoLibraryId>/<videoId>?exp=<epochSeconds>&sig=<64 lowercase hex>
sig = SHA-256("menta-local-e2e|" + videoLibraryId + "|" + videoId + "|" + exp)
```

`exp` is the caller's `expirationTimeInSeconds` verbatim. The salt is a public constant, so the
digest is recomputable from public inputs, carries no secret, and is useless against the real CDN.
The runner pins the host to the RFC 2606 `.invalid` TLD
(`APP_CDN_BUNNYNET_PULLZONEHOSTNAME=https://local-bunny-net.invalid`,
`APP_CDN_BUNNYNET_VIDEOLIBRARYID=e2e-library`) so the URL is provably unroutable.

## Data flow

```
GET /lessons/{id}/stream → GetPublicLessonStreamUseCaseImpl
   → LessonAccessPolicy.decide (D7) ── SUBSCRIPTION_REQUIRED → 403, no signing
   → BunnyNetSignatureService (profile-selected) → PublicLessonStreamView
        e2e-bunny-net → LocalBunnyNetSignatureService (SHA-256, no I/O)
        otherwise     → StringFormatBunnyNetSignatureService
```

Journey order (mandatory): fixtures seed both courses + the plan link for the planned one →
login student → **unplanned preview stream 200** → **unplanned protected stream 403 (D7)** →
planned protected stream 403 → checkout → signed approved webhook → poll activation →
planned protected stream 200.

## File Changes

| File | Action | Description |
|---|---|---|
| `api/virtual/.../application/usecase/LessonAccessPolicy.java` | Modify | **D7**: drop unplanned branch; rewrite class Javadoc |
| `api/virtual/.../application/dto/LessonAccessDecision.java` | Modify | **D7**: delete `PUBLIC_UNPLANNED_COURSE` |
| `api/virtual/src/test/.../usecase/{LessonAccessPolicyTest,GetPublicLessonUseCaseImplTest,GetPublicLessonStreamUseCaseImplTest}.java` | Modify | **D7**: 5 tests per the table above |
| `api/openapi/virtual-v1.yaml` | Modify | **D7**: 2 description edits |
| `docs/adr/0041-unplanned-course-denies-protected-lessons.md` | Create | **D7** rationale, blast radius, A7 debt |
| `api/virtual/.../infrastructure/cdn/local/LocalBunnyNetSignatureService.java` | Create | Deterministic adapter (POJO, no Spring) |
| `api/virtual/.../infrastructure/cdn/local/E2eBunnyNetProfileGuard.java` | Create | Rejects `prod\|production\|staging` |
| `api/virtual/.../infrastructure/config/VirtualConfiguration.java` | Modify | Split bean into two `@Profile` methods |
| `api/virtual/.../infrastructure/cdn/StringFormatBunnyNetSignatureService.java` | Modify | Javadoc: explicit non-local branch |
| `api/virtual/.../infrastructure/e2e/E2eBunnyNetVirtualFixture.java` | Create | Two fixed-id published courses (A4') |
| `api/billing/.../infrastructure/e2e/E2eBunnyNetBillingFixture.java` | Create | `@Profile("e2e-bunny-net & e2e-mercadopago")`; idempotent `PlanCourseJpaEntity(PLAN_ID, PLANNED_COURSE_ID)` only |
| `api/{virtual,billing}/src/test/...` | Create | Adapter, guard, config-selection and fixture tests |
| `bruno/E2E/bunny-net/**`, `bruno/environments/e2e-bunny-net.bru` | Create | Journey + env (`baseUrl` 18082, both course ids as vars) |
| `scripts/e2e/bunny-net.sh`, `scripts/e2e/bunny-net-runner-test.sh` | Create | Runner + smoke test |
| `docs/adr/0040-local-bunny-net-signature-adapter.md`, `docs/26-LOCAL-DEV-SETUP-HOWTO.md` | Create/Modify | Seam decision + documented command |

`PLANNED_COURSE_ID` is a UUID literal duplicated in both fixtures (`billing` cannot depend on
`virtual`), each carrying a comment naming its counterpart file; the grant scenario failing is the
detector. Runner isolation: Compose project `menta-e2e-bunny-net`; ports API 18082, MySQL 33307,
Redis 36380, Mailpit 38026, SMTP 31026, OTEL 34319/34320, Grafana 33001 — disjoint from
`catalog-content.sh`, so both may run concurrently in CI.

## Testing Strategy

| Layer | What | How |
|---|---|---|
| Unit | D7 cascade: 5 tests above | Mockito on `VirtualCourseEntitlementPort`, no Spring |
| Unit | Determinism, `exp` passthrough, 64-hex `sig`, distinct video/exp ⇒ distinct `sig`, no credential substring | Direct calls, no Spring context |
| Unit | Guard throws on each production profile (case-insensitive); each `@Bean` returns its implementation; `@Profile` values are exact complements | `MockEnvironment` + reflection on `@Profile` |
| Unit | Both fixtures idempotent, no-op when already seeded; billing fixture writes exactly one row, for `PLANNED_COURSE_ID` only | Mockito on repositories |
| E2E | Preview 200, unplanned protected 403, planned protected 403, planned protected 200 post-webhook, URL contract, no credential and no `videoId` in denied bodies | Bruno via `scripts/e2e/bunny-net.sh` |

Coverage gates hold by construction: D7 removes application-layer lines and adds tests (95% floor);
adapter/guard/fixtures ship with isolated infrastructure tests (90% floor).

## Threat Matrix

| Boundary | Applicability | Design response | Planned RED tests |
|---|---|---|---|
| Documentation-like paths | N/A — no file classification or execution of repo content | — | — |
| Git repository selection / commit / push / PR commands | N/A — runner performs no VCS operation | — | — |
| Shell/subprocess (new runner) | Applicable | `set -euo pipefail`, `readonly` ports, prerequisite checks, `trap stop_api EXIT INT TERM`, dedicated Compose project, `--clean` limited to that project | Runner smoke test: unknown arg fails, missing prerequisite fails, `--clean` touches only the dedicated project |

## Migration / Rollout

Adapter, fixtures, journey and runner: none — without `e2e-bunny-net` the container resolves today's
bean; rollback = revert commits.

**D7 is a production authorization behavior change, not a test-only change.** Any published course
with no `billing_plan_courses` row silently loses public access to its non-free, non-preview lessons.
No code, seeder or fixture dependency exists (verified), but **real data was not audited** — this SDD
verifies local/E2E only. Release gate before promoting to production: query published courses absent
from `billing_plan_courses` and either attach a plan or mark the affected lessons free/preview.
Rollback for D7 alone = revert slice 1; no migration, no backfill, no persisted state depends on it.

## PR Slicing (budget 800 lines; 400-line default guard: High)

The proposal's 3-slice sketch is **adjusted to 4**: merging fixtures into the journey slice
overflows the budget (~950 lines).

| # | Content | Target | Est. changed lines |
|---|---|---|---|
| 1 | **D7 only**: policy + enum + 5 tests + OpenAPI + ADR-0041 | `develop` | ~200 |
| 2 | Local adapter + guard + wiring + unit tests + ADR-0040 | PR1 | ~400 |
| 3 | Both E2E fixtures + fixture tests | PR2 | ~320 |
| 4 | Bruno journey, environment, runner, smoke test, docs | PR3 | ~630 |

Slice 1 is first and independently revertible so the authorization change never rides inside an
E2E-infrastructure diff. Slices 2–4 are pure additions behind an inactive profile.

## Open Questions

- [x] *(previous revision)* A4 adds a Billing file the proposal scoped as "Modified Capabilities:
  None" — **resolved**: the proposal now declares `virtual` as a modified capability and keeps the
  billing fixture scoped to the grant scenario only.
- [ ] **A6 edge, assumed under `auto` mode**: an unplanned course still streams for a caller holding
  a *frozen paid* entitlement. If the product owner intends a plan-unlink to revoke paid access
  mid-subscription, A6 becomes the one-line veto variant and
  `unplanned_course_still_honours_a_frozen_paid_entitlement` inverts.
- [ ] `sdd-spec` has not written a `specs/virtual/` capability delta for D7; the `local-bunny-net`
  spec references it. Confirm before `sdd-tasks`.
- [ ] Out of scope, discovered here: `GET /lessons/{id}/stream` never checks
  `findPublishedById`, so unpublishing a course does **not** stop streaming (the detail endpoint
  does). Unrelated to D7 but it weakens "unpublish is the revocation lever" — worth its own issue.
- [ ] `e2e-catalog-content` is retained per D2 for `E2eCatalogContentAuthFixture` (student identity
  without an email round-trip); its course content is unused by this journey.

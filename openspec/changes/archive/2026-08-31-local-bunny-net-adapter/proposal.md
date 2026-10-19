# Proposal: Local Bunny.net Adapter (Issue #129)

## Intent

Preview and premium playback cannot be validated locally today: `api/virtual` has a
single, unguarded `BunnyNetSignatureService` placeholder wired unconditionally in
`VirtualConfiguration`, so any local stream check either depends on Bunny.net or
proves nothing. Success = one documented command reproduces public preview, premium
denial, and premium access with an active subscription, with no CDN credential.

Design (A4) then exposed a product defect that makes "premium denial" unprovable: a
non-free lesson in a course linked to no plan is currently served as public. D7
corrects that rule, so this change now also carries one deliberate authorization
change — see D7.

## Scope

### In Scope
- Profile-guarded `BunnyNetSignatureService` pair: real (`!e2e-bunny-net`) and local (`e2e-bunny-net`).
- `LocalBunnyNetSignatureService` under `infrastructure/cdn/local/`: deterministic URL preserving signed-URL shape (`exp` derived from the caller's TTL + non-secret, non-reusable `sig`).
- Fail-closed guard: `e2e-bunny-net` together with `prod|production|staging` aborts startup (mirrors `local-mercadopago` spec).
- Unit tests for the local adapter and for profile selection; existing `StringFormatBunnyNetSignatureServiceTest` preserved.
- `bruno/E2E/bunny-net/` ordered journey covering the three acceptance scenarios.
- `bruno/environments/e2e-bunny-net.bru` + `scripts/e2e/bunny-net.sh` (dedicated Compose project/ports).
- `docs/adr/0040-local-bunny-net-signature-adapter.md`.
- **(D7)** Correct the unplanned-course branch of `LessonAccessPolicy` to deny instead of granting public access, with its tests, Javadoc, and contract docs.

### Out of Scope
- HLS manifests, CDN performance/behavior emulation, video bytes.
- Any change to entitlement resolution or the metadata vs `/stream` split (already correct). **Amended by D7**: the unplanned-course branch of `LessonAccessPolicy` IS in scope; the free/preview branches, the Billing snapshot rule, and the port contract stay untouched.
- Production/staging data remediation for courses that relied on the old rule (see Risks).
- Real HMAC signing for the production adapter (pre-existing debt, issue #50).
- New stream payload fields — `PublicLessonStreamView` and `QUALITY_LADDER` stay untouched.
- Extracting a shared bash library out of `scripts/e2e/catalog-content.sh`.

## Capabilities

### New Capabilities
- `local-bunny-net`: profile-guarded deterministic streaming-signature adapter and its local acceptance journey.

### Modified Capabilities
- `virtual` **(D7)**: requirement *Ordered public-access cascade* loses its third public rule. Scenario "A course absent from every plan is public" is replaced by a denial scenario; the free-lesson and preview-module rules are unchanged. Source of record: `openspec/changes/virtual-subscription-access/specs/virtual/spec.md` (not yet archived into `openspec/specs/`).
- `local-mercadopago` is reused unchanged.

## Approach

| # | Decision | Rationale / trade-off |
|---|----------|----------------------|
| D1 | Wire **two `@Profile`-annotated `@Bean` methods** in `VirtualConfiguration` (explore approach 2), not billing's `@Component @Profile` classes | Virtual's composition root is explicit by documented convention; `@Profile("e2e-bunny-net")` and `@Profile("!e2e-bunny-net")` are strictly complementary, so no ambiguous-bean risk; `VirtualConfigurationTest` survives instead of being rewritten, shrinking the diff. Trade-off: idiom differs from `api/billing`; mitigated by identical runtime guarantee (Spring bean selection, never an `if` in code). |
| D2 | Premium scenario **reuses the local Mercado Pago simulator**; no new entitlement seeding | Resolves the explore's open question with evidence: `scripts/e2e/catalog-content.sh:129` already runs `SPRING_PROFILES_ACTIVE=e2e-catalog-content,e2e-mercadopago`. Multi-E2E-profile composition is precedent, not novel. The bunny runner activates all three profiles. A seeding shortcut would fabricate entitlement and weaken the proof. |
| D3 | "Materiales deterministas" = the **existing** `PublicLessonStreamView` shape only | Its fields are already static/deterministic. No new domain concept, per the issue's own limits. |
| D4 | **Write `docs/adr/0040`** rather than delete the citation | `0040` is the next free number, so the currently dangling Javadoc reference becomes valid at zero extra cost; matches ADR-0038/0039 precedent. |
| D5 | **Dedicated** Bruno environment + runner (catalog-content pattern), own Compose project/ports | Closes the gap `local-mercadopago-simulator` left; keeps the merged catalog-content runner and its guard test untouched. Trade-off: duplicated bash lifecycle; extraction deferred as explicit debt. |
| D6 | Budget **explicit unit tests** for the local adapter up front | Prevents repeating the 0%-isolated-coverage warning from issue #128; `virtual` infrastructure floor is 90%. |
| D7 | **An unplanned course is no longer public for protected lessons.** `LessonAccessPolicy.decide` returns `SUBSCRIPTION_REQUIRED` — not `PUBLIC_UNPLANNED_COURSE` — when `!access.courseInAnyPlan()` | Product-owner ruling: "published but attached to no plan" is a commercial configuration gap, not a grant. Today an unplanned course silently opens every non-free, non-preview lesson — fail-open in the one place the module is supposed to fail closed. Verified as an implementation simplification from #141/#56, not a US-VIRTUAL-007 requirement: no seeder or fixture depends on it (`courseInAnyPlan` / `findAllActiveOrderByPriceAsc` appear only in `LessonAccessPolicy`, `VirtualCourseEntitlementService`, `ListPlansUseCaseImpl`, `PlanRepositoryAdapter`, `CourseAccessSnapshot` and their tests). Trade-off: a real deployment relying on the old rule loses access — accepted, tracked as a deploy risk below. |

### D7 — scope detail

1. **Policy**: the `!courseInAnyPlan()` branch collapses into the same denial as "planned course without entitlement". Ordered cascade becomes free → preview module → entitlement.
2. **Enum**: `LessonAccessDecision.PUBLIC_UNPLANNED_COURSE` is **deleted**, not left unused. It has exactly one producer (`LessonAccessPolicy:52`) and one assertion (`LessonAccessPolicyTest:53`); both use-case consumers branch only on `SUBSCRIPTION_REQUIRED` / `PUBLIC_FREE`, so no `switch` loses exhaustiveness. Keeping a term the ubiquitous language no longer means is the ambiguity to avoid.
3. **Tests**: rename and invert `LessonAccessPolicyTest.unplanned_course_is_public_for_an_anonymous_caller` to assert denial. Audit `GetPublicLessonUseCaseImplTest` and `GetPublicLessonStreamUseCaseImplTest` for cases that pass only because of the old fall-through.
4. **Docs/contract**: rewrite the `LessonAccessPolicy` Javadoc (lines 11–19) that states "a course absent from all plans" as a public rule; record the reversal in `docs/adr/0040` (D4) and check `api/openapi/virtual-v1.yaml` for response wording that promises the old behavior.
5. **Blast radius on the public API**: both endpoints change for this input. `GET /lessons/{id}` goes from a premium view **that exposes `videoId`** to `403`; `GET /lessons/{id}/stream` goes from a signed URL to `403`. Deliberate — the old path disclosed a media identifier for a lesson nobody had paid for.
6. **E2E scope reduction (supersedes design A4)**: premium **denial** no longer needs a `billing_plan_courses` row, because an unplanned protected lesson now denies by default. `E2eBunnyNetBillingFixture` (`@Profile("e2e-bunny-net & e2e-mercadopago")`) is still required, but **only** for the premium **grant** scenario (real plan link + active subscription via the Mercado Pago flow). `sdd-spec` and `sdd-design` must re-plan on this basis; D2 is otherwise unchanged.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/virtual/.../infrastructure/config/VirtualConfiguration.java` | Modified | Split `bunnyNetSignatureService` into two `@Profile` bean methods; fix ADR reference |
| `api/virtual/.../infrastructure/cdn/local/` | New | `LocalBunnyNetSignatureService` |
| `api/virtual/.../infrastructure/cdn/StringFormatBunnyNetSignatureService.java` | Modified | Becomes the explicit non-local branch (Javadoc only) |
| `api/virtual/src/test/.../infrastructure/{cdn,config}/` | New/Modified | Local adapter unit tests + profile-selection test |
| `bruno/E2E/bunny-net/`, `bruno/environments/e2e-bunny-net.bru` | New | Ordered journey + environment |
| `scripts/e2e/bunny-net.sh` | New | Isolated runner activating the three E2E profiles |
| `docs/adr/0040-*.md`, Bruno/local-dev docs | New/Modified | Seam decision + documented command |
| `api/virtual/.../application/usecase/LessonAccessPolicy.java` | Modified | **D7**: unplanned branch → `SUBSCRIPTION_REQUIRED`; Javadoc lines 11–19 rewritten |
| `api/virtual/.../application/dto/LessonAccessDecision.java` | Modified | **D7**: remove `PUBLIC_UNPLANNED_COURSE` |
| `api/virtual/src/test/.../application/usecase/{LessonAccessPolicyTest,GetPublicLessonUseCaseImplTest,GetPublicLessonStreamUseCaseImplTest}.java` | Modified | **D7**: invert the unplanned case; audit implicit dependants |
| `api/openapi/virtual-v1.yaml` | Modified (verify) | **D7**: 403 now reachable for unplanned protected lessons |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Three simultaneous E2E profiles interact badly (fixtures/ports) | Med | Two already co-run in `catalog-content.sh`; the third only swaps one outbound port. Verify at design; own Compose project isolates state |
| Bash duplication drifts from `catalog-content.sh` | Med | Bounded copy, recorded as explicit follow-up debt; runner asserted by its own smoke test |
| Local URL mistaken for a real signed URL | Low | `sig` is a documented non-secret digest; ADR-0040 and Javadoc state it; profile fails closed in prod |
| Change exceeds the 400-line review budget | High | `sdd-tasks` to slice: PR1 adapter + tests + ADR, PR2 Bruno journey + runner + docs |
| Reviewer asks why the "real" adapter still does not sign | Med | Declared out of scope, linked to issue #50 |
| **(D7) Deployment regression**: a course already published in a real environment with no plan attached loses public access to its non-free, non-preview lessons — a silent 403 for users who could watch yesterday | Med | No code, seeder, or fixture dependency was found, but data was NOT audited. **Before merging to production**: query published courses with no `billing_plan_courses` row and attach a plan (or mark the lessons free/preview). This SDD verifies local/E2E only — the production data audit is a release gate, not an apply-phase task |
| **(D7) Scope creep**: an authorization change riding inside an E2E-infrastructure PR | Med | Ship D7 as its own first slice (policy + enum + tests + Javadoc + ADR note), independently revertible, ahead of the adapter and journey slices |
| **(D7) Silent contract break** for any client relying on the unplanned-course detail view (which leaked `videoId`) | Low | Treated as a fix, not a feature; documented in ADR-0040 and the OpenAPI contract |

## Rollback Plan

Revert the change commits. The production path is untouched by construction: without
`e2e-bunny-net` the container resolves the same `StringFormatBunnyNetSignatureService`
instance it resolves today. Deleting `bruno/E2E/bunny-net/`, its environment, and
`scripts/e2e/bunny-net.sh` removes all E2E surface with no runtime impact.

**D7 is the exception** and the only part of this change that touches production behavior.
It is therefore delivered as its own slice: reverting that one commit restores
`PUBLIC_UNPLANNED_COURSE` and the previous cascade without touching the adapter, the runner,
or the journey. No migration, no data backfill, and no persisted state depends on it.

## Dependencies

- Merged `local-mercadopago-simulator` (`e2e-mercadopago`) — required for the premium scenario.
- Merged `local-catalog-content-e2e` (`e2e-catalog-content`) — supplies published course/lesson state.
- Docker Compose v2, Node 20.11.1, JDK 21 (same prerequisites as the existing runner).

## Success Criteria

- [ ] With `e2e-bunny-net` active, no request reaches Bunny.net and the stream URL is deterministic across runs.
- [ ] Without `e2e-bunny-net`, the real adapter is the only `BunnyNetSignatureService` bean.
- [ ] Startup fails closed when `e2e-bunny-net` is combined with `prod|production|staging`.
- [ ] `scripts/e2e/bunny-net.sh` exits zero only when public preview, premium denial, and post-subscription premium access all pass; non-zero on any failure.
- [ ] Responses and versioned files contain no secret or reusable credential.
- [ ] `virtual` coverage gates hold (95% domain+application, 90% infrastructure) with the local adapter covered by isolated unit tests.
- [ ] **(D7)** A non-free lesson in a non-preview module of a course linked to no plan returns `403` on both `GET /lessons/{id}` and `GET /lessons/{id}/stream`, for anonymous and authenticated callers alike, and discloses no `videoId`.
- [ ] **(D7)** `PUBLIC_UNPLANNED_COURSE` no longer exists in the codebase, and free-lesson / preview-module / entitled-subscriber access is provably unchanged.
- [ ] **(D7)** Premium denial in the Bruno journey passes with no `billing_plan_courses` row; the billing fixture is exercised only by the premium-grant scenario.

## Proposal question round

Execution mode is `auto`, so these were resolved as assumptions. Flag any you want changed before `sdd-spec`/`sdd-design`:

1. **Wiring idiom (D1)** — assumed intra-module consistency (explicit `@Bean` composition root) outweighs cross-module mirroring of billing's `@Component @Profile`. Prefer literal precedent instead?
2. **Premium path (D2)** — assumed a real checkout+webhook subscription is required for a credible proof, at the cost of a three-profile local stack. Accept a cheaper, less faithful entitlement seed?
3. **Runner shape (D5)** — assumed a dedicated `bunny-net.sh` with duplicated lifecycle. Prefer extending `catalog-content.sh` (~40 lines, but that script becomes a three-journey composite with a misleading name)?
4. **Production signing** — assumed the real adapter stays an unsigned placeholder. Should closing issue #50 join this slice?

### Round 2 — resolved by the product owner (D7)

5. **Unplanned course = public?** — **Answered: no.** A published course with no plan must deny protected lessons. This amendment records that ruling; it widens the change from pure E2E infrastructure to one authorization fix.

Still open after D7, for `sdd-spec` / `sdd-design`:

- Should an unplanned course also stop being *listed* in the public catalog, or only stop granting lesson access? Assumed **access only** — catalog visibility is untouched.
- Does the denial deserve a distinct client-facing reason ("no disponible" vs "requiere suscripción")? Assumed **no** — it reuses `SUBSCRIPTION_REQUIRED` and the existing `/api/v1/billing/plans` prompt, keeping issue #50's richer-reason work in one place.

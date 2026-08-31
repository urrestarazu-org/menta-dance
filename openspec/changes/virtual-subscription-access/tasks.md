# Tasks: virtual-subscription-access

> Topological, strict-TDD implementation plan for issue #56.  The governing
> product sources are US-VIRTUAL-003, US-VIRTUAL-004, US-VIRTUAL-007, and
> US-BILLING-011.  In particular, cancellation preserves paid access through
> `endDate`; it is not an immediate revocation.  This change deliberately
> introduces **no Caffeine entitlement cache**, status event, or listener.

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~900–1,150 LOC (Java, migration, tests, OpenAPI, Bruno) |
| 400-line PR review budget risk | High |
| Chained PRs recommended | **Yes** |
| Suggested chain | PR 1 = TASK-001–003 (model + shared/Billing facts); PR 2 = TASK-004–007 (Virtual enforcement + contracts) |
| Delivery strategy | Ask before apply; preserve one work-unit commit per task |
| Largest task | TASK-005, ~260 LOC; within the 800-LOC per-task limit |

```text
Decision needed before apply: Yes — approve the two stacked PRs above or a
size exception.  PR 2 must target PR 1 and merge after it.
```

## TASK-001 — Add preview-module data with a backwards-compatible migration

- **Status**: completed
- **Dependencies**: none
- **Estimated LOC**: ~190 (75 production/migration, 115 tests)
- **Modules**: `api:app`, `api:virtual`
- **Work**:
  - Test first: establish that `isPreview` defaults to `false`, persists and
    round-trips as `true`, and is represented by the administration surface.
  - Add one Flyway migration in `api:app` that adds
    `virtual_modules.is_preview BOOLEAN NOT NULL DEFAULT FALSE`.
  - Propagate `isPreview` through Virtual's domain model, persistence mapping,
    repository adapter, management command/result DTOs, controller mapping,
    configuration, and existing test fixtures.
- **Acceptance criteria**:
  1. Existing rows validate and read as `isPreview=false` after migration.
  2. An administrator can create/update/read a preview module without a
     framework type entering Virtual domain/application code.
  3. `:api:virtual:test` and Flyway/Hibernate validation tests pass.
- **Persistence impact**: one additive app-owned Flyway migration only.
- **Commit**: `feat(virtual): add module preview flag`

## TASK-002 — Expose one shared Billing course-access read snapshot

- **Status**: completed
- **Dependencies**: none
- **Estimated LOC**: ~150 (65 production, 85 tests)
- **Modules**: `api:shared`, `api:billing`
- **Work**:
  - Test first: add a framework-free shared value that expresses only
    `courseInAnyPlan` and `currentEntitlement`.
  - Replace/extend the boolean-only `VirtualCourseEntitlementPort` with one
    `resolveCourseAccess(UUID userIdOrNull, String courseId)` operation.
    Document responsibility, null-user behavior, snapshot authority, and
    non-obvious cancellation boundary in Javadoc.
  - Implement the Billing service using its plan-course query port and
    subscription snapshot repository; do not expose entities or plan lists.
  - Define null/blank user input as no entitlement without querying a
    subscription.
- **Acceptance criteria**:
  1. A course that belongs to one or more live plans returns
     `courseInAnyPlan=true`; an unplanned course returns `false`.
  2. No Billing JPA type crosses into `api:shared` or Virtual.
  3. Public callers can obtain the plan-membership fact without a subscription
     lookup; access is never granted by the shared contract alone.
- **Persistence impact**: none; reads existing Billing plan/subscription data.
- **Commit**: `feat(shared): expose billing course access snapshot`

## TASK-003 — Implement current paid-snapshot semantics in Billing

- **Status**: completed
- **Dependencies**: TASK-002
- **Estimated LOC**: ~210 (70 production, 140 tests)
- **Modules**: `api:billing`
- **Work**:
  - Test first with a fixed Billing `Clock`.
  - Make `currentEntitlement` true for an `ASSIGNED` frozen snapshot containing
    the requested course when `endDate > now` and status is `ACTIVE` **or
    `CANCELLED`**; deny `PENDING`, `EXCEPTION`, `EXPIRED`, missing/end-boundary,
    and non-containing snapshots.
  - Support more than one current subscription: any qualifying snapshot grants.
  - Prove a live-plan removal/deactivation does not change an already-frozen
    snapshot result.
- **Acceptance criteria**:
  1. Cancellation before `endDate` continues to grant access; expiry at or
     after `endDate` denies on that very read, per US-BILLING-011.
  2. A previously purchased course remains allowed after its live plan changes.
  3. `:api:billing:test` and Billing JaCoCo domain/application verification
     remain green.
- **Persistence impact**: none.
- **Commit**: `fix(billing): honor current cancelled subscription access`

## TASK-004 — Create the Virtual-owned ordered lesson-access policy

- **Status**: completed
- **Dependencies**: TASK-001, TASK-002, TASK-003
- **Estimated LOC**: ~200 (85 production, 115 tests)
- **Modules**: `api:virtual`
- **Work**:
  - Test first: introduce a framework-free application policy and explicit
    decision DTO/enum: `PUBLIC_FREE`, `PUBLIC_MODULE_PREVIEW`,
    `PUBLIC_UNPLANNED_COURSE`, `SUBSCRIPTION_GRANTED`, or
    `SUBSCRIPTION_REQUIRED`.
  - Evaluate local rules in the fixed order free → module preview → unplanned
    course. Query the shared contract only on the remaining planned protected
    branch and only when a caller id is present.
  - Treat an unavailable/malformed Billing result as denied; policy values
    never include a video id, signer input, signed URL, or material URL.
- **Acceptance criteria**:
  1. Each public branch allows anonymous access and verifies zero entitlement
     calls.
  2. Planned premium content grants only for a current Billing entitlement;
     anonymous/no-entitlement/unavailable Billing returns
     `SUBSCRIPTION_REQUIRED`.
  3. The policy has no Spring, JPA, Billing infrastructure, or media-capability
     dependency.
- **Persistence impact**: none.
- **Commit**: `feat(virtual): centralize lesson access policy`

## TASK-005 — Enforce one decision in lesson detail and stream issuance

- **Status**: completed
- **Dependencies**: TASK-004
- **Estimated LOC**: ~260 (105 production, 155 tests)
- **Modules**: `api:virtual`, `api:app` wiring if required
- **Work**:
  - Test first: refactor `GetPublicLessonUseCaseImpl` and
    `GetPublicLessonStreamUseCaseImpl` to delegate to the policy, replacing
    duplicated free/entitlement checks.
  - Wire the shared Billing contract and policy only through configuration;
    `api:app` remains composition-only.
  - Map a protected caller to the existing stable subscription-required
    exception/Problem Detail, upgrading detail's former allowed-but-restricted
    result to the normative 403 boundary.
  - Call Bunny signing only after an allow decision. Detail never returns a
    streaming URL; denials must not invoke the signer or expose `videoId`.
- **Acceptance criteria**:
  1. Detail/stream permit all public paths and an entitled protected path.
  2. Detail and stream each return `403 application/problem+json` for anonymous
     or non-entitled protected access.
  3. Stream denial calls the signer zero times; successful stream calls it once;
     detail contains no signed URL.
  4. No material endpoint is added: it does not currently exist and is outside
     this change.
- **Persistence impact**: none.
- **Commit**: `feat(virtual): enforce subscription access on lessons`

## TASK-006 — Lock the module and cross-module boundaries with regressions

- **Status**: completed
- **Note**: The ArchUnit regression
  (`app_should_not_own_virtual_lesson_access_policy`,
  `api/app/src/test/java/com/menta/app/ArchitectureTest.java:58`) already
  existed from an earlier batch. This closeout adds the missing
  application/controller integration coverage:
  `api/app/src/test/java/com/menta/app/integration/virtual/VirtualLessonAccessIntegrationTest.java`
  (6 scenarios — planned course + active subscription grants detail/stream;
  protected denial for anonymous and authenticated-without-entitlement with
  no-leak assertions; frozen snapshot surviving a live-plan course removal;
  cancellation-before-`endDate` still grants; expiry denies; D7 — a course
  never associated with any plan denies by default, matching the CURRENT
  `specs/virtual/spec.md` contract, not the original proposal's "unplanned
  course is public" semantics). All 6 pass against the real Spring context
  (Testcontainers MySQL), no cross-module port mocked.
- **Dependencies**: TASK-005
- **Estimated LOC**: ~120 (tests only)
- **Modules**: `api:virtual`, `api:billing`, `api:app`
- **Work**:
  - Add/extend ArchUnit regressions preventing Virtual application/domain from
    depending on Billing infrastructure/persistence and preventing app from
    owning lesson-access business rules.
  - Add application/controller integration coverage for public detail/stream,
    entitled premium stream, protected 403/no-leak, frozen snapshot, cancelled
    before end date, and expiry.
  - Assert correlation-aware failure behavior without logging capabilities.
- **Acceptance criteria**:
  1. Tests prove no video id, stream URL, or equivalent media capability is
     generated/exposed on denial.
  2. Tests prove cancellation re-reads Billing and still allows through
     `endDate`; no cache/event/listener is introduced.
  3. Relevant ArchUnit suites and `:api:virtual:test :api:billing:test` pass.
- **Persistence impact**: none.
- **Commit**: `test(virtual): protect subscription access boundaries`

## TASK-007 — Update API contracts and perform the verification sweep

- **Status**: completed
- **Dependencies**: TASK-006
- **Estimated LOC**: ~130 (contracts/tests/documentation)
- **Modules**: OpenAPI, `bruno/`, `api:virtual`, `api:app`
- **Work**:
  - Update existing OpenAPI paths/schemas/responses for lesson detail and
    stream: public/entitled `200`, protected
    `403 application/problem+json`, explicit `preview` and
    `requiresSubscription` metadata, and no URL in lesson detail.
  - Update versioned Bruno requests, variables, and assertions for each public
    branch, entitled stream, and denied protected request.
  - Run focused module gates then repository `check`; record any pre-existing
    environmental failure distinctly from product failures.
- **Acceptance criteria**:
  1. OpenAPI and Bruno describe the delivered endpoint behavior and no obsolete
     `200 requires subscription` contract remains.
  2. Contract tests assert a denial response contains no media URL/identifier.
  3. `./gradlew :api:virtual:test :api:billing:test --no-daemon` and
     `./gradlew check --no-daemon` are green (or failures are documented with
     reproducible evidence).
- **Persistence impact**: none.
- **Commit**: `docs(virtual): document subscription access contract`

## Coverage Matrix

| Specification requirement / scenario | Owning task(s) |
|---|---|
| Ordered public-access cascade: free / preview module / unplanned course | TASK-001, TASK-002, TASK-004, TASK-005 |
| Current paid snapshot, frozen-plan semantics, multiple subscriptions | TASK-002, TASK-003, TASK-005, TASK-006 |
| Protected 403 and capability non-disclosure | TASK-004, TASK-005, TASK-006, TASK-007 |
| Cancellation through `endDate`; expiry denial | TASK-003, TASK-006 |
| Shared decision for detail and stream | TASK-004, TASK-005 |
| Architectural boundaries | TASK-002, TASK-004, TASK-006 |
| OpenAPI / Bruno contract | TASK-007 |

## Explicit non-goals verified during apply

- No Caffeine cache, `SubscriptionStatusChanged` event, listener, TTL, or
  invalidation test is implemented. Direct Billing reads meet next-request
  freshness and prevent an entitlement cache becoming security authority.
- No cancellation API, renewal/expiry scheduler, Bunny signing-parameter
  change, BFF/Android work, plan administration redesign, or material endpoint
  is introduced.
- Any new cancellation policy must first change US-BILLING-011 in a separate
  product development.

## Work-unit Commit Map

| Commit | Task | PR slice |
|---|---|---|
| 1 | TASK-001 | PR 1 |
| 2 | TASK-002 | PR 1 |
| 3 | TASK-003 | PR 1 |
| 4 | TASK-004 | PR 2 (base: PR 1 branch) |
| 5 | TASK-005 | PR 2 |
| 6 | TASK-006 | PR 2 |
| 7 | TASK-007 | PR 2 |

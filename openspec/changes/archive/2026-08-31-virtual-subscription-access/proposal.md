# Proposal: virtual-subscription-access

> Close issue #56 by making Virtual enforce Billing's current subscription
> entitlement before it discloses premium lesson metadata, signed streams, or
> protected materials, while preserving public preview paths.

**Change ID**: `virtual-subscription-access`
**Milestone**: v0.3.0 — Suscripción y aprendizaje virtual E2E
**Feature track**: issue #56
**Related ADRs**: ADR-0021 (Clean Architecture), ADR-0026 (Redis/Caffeine),
ADR-0039 (post-payment fulfillment boundaries)
**Related user stories**: US-VIRTUAL-003, US-VIRTUAL-004, US-VIRTUAL-007
**Dependencies**: #114 (merged; Billing now owns an `ACTIVE` + `ASSIGNED`
subscription snapshot).

---

## 1. Intent

The paid virtual journey is incomplete after #114: Billing persists the
authoritative subscription entitlement, but Virtual must consume it at the
boundary where it publishes lesson content and generates Bunny signed URLs.
Without that enforcement, a caller can obtain protected content independently
of the current subscription snapshot; conversely, a user who has paid has no
complete end-to-end access guarantee.

This change makes Virtual a pull consumer of the existing shared Billing
entitlement contract. It preserves the three anonymous public paths specified
by US-VIRTUAL-007 (free lesson, preview module, and course not included in any
plan), gives a premium caller without entitlement an explicit 403 Problem
response, and treats a signed URL as an authorization capability that must
never be produced before access succeeds.

## 2. Scope

### In scope

- Centralize the US-VIRTUAL-007 access cascade for lesson detail, stream, and
  downloadable-material authorization:
  1. `lesson.isFree` grants access anonymously;
  2. a preview module grants access anonymously;
  3. a course assigned to no plan grants access anonymously; otherwise
  4. the authenticated student's Billing snapshot must be `ACTIVE`, assigned,
     current at the injected clock, and include the course.
- Expose explicit metadata distinguishing preview/public access from protected
  access (including `preview` and `requiresSubscription`) without returning a
  video identifier or signed stream/download URL to a caller lacking the
  required entitlement.
- Return `403 application/problem+json` for a protected endpoint when the
  caller is anonymous or has no active entitlement. The detail response may
  still provide the safe preview metadata needed to explain the restriction;
  it must not leak media identifiers or capabilities.
- Reuse the shared Java `VirtualCourseEntitlementPort`; Billing remains the
  source of truth and Virtual makes no cross-module table, repository, SQL,
  HTTP, or messaging call.
- Add a bounded, per-user Caffeine cache around entitlement reads only if it
  has a direct invalidation path. A `SubscriptionStatusChanged` internal event
  for the affected user evicts that user's entries immediately; no global
  cache clear and no entitlement cache treated as authoritative.
- Add unit, controller, architecture, and application integration regressions
  for the access cascade, denied responses, signed-URL non-leakage, snapshot
  persistence semantics, and immediate event-driven cache eviction.
- Update the existing OpenAPI/Swagger and versioned Bruno requests/assertions
  for changed lesson/stream/material response and error contracts.

### Out of scope

- Creating or modifying Billing subscriptions, payment webhooks, plans, or
  their persisted snapshots (#114 already established the fulfillment side).
- Subscription cancellation APIs and expiration scheduling (US-BILLING-011).
- Changes to Bunny signing parameters, player controls, Android UI, BFF UI,
  course administration, or learning-progress behavior.
- A distributed cache or a cache used as entitlement authority.

## 3. Architectural Approach

1. **One authorization decision before capability issuance.** Virtual resolves
   the lesson/course/module, computes the three public branches locally, and
   asks `VirtualCourseEntitlementPort` only for the protected branch and a
   known authenticated user. Stream and material adapters reuse the same
   decision rather than independently approximating entitlement.
2. **Billing is authoritative.** Its adapter answers from the immutable course
   snapshot on an `ACTIVE`, `ASSIGNED`, unexpired subscription. Virtual neither
   writes a grant nor derives access from the live plan; a plan change after
   purchase cannot revoke paid access.
3. **Fail closed.** A missing user, an absent/expired/cancelled entitlement,
   or an unavailable entitlement port denies protected access. A signed URL is
   generated only after authorization and is never stored in or returned from
   the entitlement cache.
4. **Event-driven invalidation.** Virtual may cache a positive or negative
   per-user entitlement answer for reconstructible performance data. A
   Billing-originated `SubscriptionStatusChanged` event invalidates the
   affected user's cache keys synchronously on receipt, so the next request
   re-evaluates Billing rather than waiting for TTL. TTL remains only a
   resilience bound, not the revocation mechanism.
5. **Keep module boundaries intact.** The shared port and event contract live
   in `api:shared`; Virtual owns its cache adapter/listener and Billing owns
   its subscription transition publication. `api:app` wires adapters only and
   contains no domain access rule.

### Cancellation semantic boundary

US-BILLING-011 currently defines cancellation as stopping renewal while the
student retains access until `endDate`; it explicitly says cancellation must
**not** immediately cut off paid access. Therefore this change's immediate
cache invalidation means the next request immediately reflects Billing's
authoritative decision, not that Virtual invents an early revocation. Expiry
must deny immediately. If product policy instead intends cancellation to
revoke access at once, that is a cross-story business-rule change and must be
made explicitly in US-BILLING-011/Subscription semantics before implementation;
it cannot safely be smuggled into #56.

## 4. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | An anonymous caller receives lesson detail and a signed stream for each public cascade branch: `isFree`, preview module, and course absent from every plan. The response identifies the branch as preview/public metadata. |
| AC-2 | An authenticated caller whose current assigned Billing snapshot includes the premium course receives safe full detail and may obtain a fresh signed stream URL. |
| AC-3 | An anonymous caller or caller with no active, assigned, unexpired snapshot for a protected course receives `403 application/problem+json`; no video ID, signed stream URL, or material download URL is created or exposed. |
| AC-4 | The decision uses the subscription's frozen course snapshot. Removing the course from, or deactivating, the live plan after confirmation does not remove access before the snapshot's expiry. |
| AC-5 | Expiry immediately denies on the next request. A `SubscriptionStatusChanged` notification evicts only the affected user's cached entitlements, so subsequent authorization never waits for cache TTL. |
| AC-6 | Cancellation notification also evicts only the affected user. The post-eviction decision matches Billing's documented cancellation policy (currently access through `endDate`); no stale cache answer survives. |
| AC-7 | Virtual domain/application remain framework-free, no foreign-module infrastructure/table access is introduced, and the cross-module contract is a shared Java interface/event only. |
| AC-8 | OpenAPI and Bruno accurately document/assert the `403 application/problem+json` protected-path contract and absence of media URLs on denial. |

## 5. Verification

- Strict TDD at the decision/use-case level for all four access outcomes and
  snapshot semantics.
- Controller integration tests for anonymous public access, entitled access,
  anonymous/no-entitlement `403 ProblemDetail`, and no signed URL generation
  on the rejection path.
- Cache-listener tests proving a status event evicts only the affected user's
  entries, followed by an integration assertion that Billing is re-read after
  expiry/cancellation notification.
- Module gates: `./gradlew :api:virtual:test :api:billing:test --no-daemon`.
- Whole-repository gate: `./gradlew check --no-daemon`.
- Contract verification through the updated `bruno/` requests and OpenAPI
  validation where the existing project workflow exposes it.

## 6. Risks and Rollback

The primary risk is treating cached authorization as truth or adding a
convenient cross-module JPA/HTTP call. Both violate ADR-0039 and make
revocation unreliable. The design instead accepts a cache miss after an event
and fails closed if Billing cannot answer.

The other material risk is conflating cancellation with expiry. The current
source-of-truth story preserves paid access after cancellation, so implementation
must not silently revoke it. If immediate cancellation revocation becomes a
product decision, it requires a separate Billing-story/ADR refinement.

Rollback removes the Virtual authorization/cache adapter changes and restores
the preceding endpoint behavior; it does not mutate Billing records, schemas,
or external Bunny resources. Retain the regression tests when investigating
any rollback so media-capability leakage cannot recur.

## 7. Next SDD Phase

Create `specs/virtual/spec.md` with normative RFC 2119 requirements and
Given/When/Then scenarios for AC-1 through AC-8. The spec must make the
cancellation-versus-expiry semantic boundary explicit before design and
topologically ordered strict-TDD tasks are created.

# Local Bunny.net Adapter Specification

## Purpose

Let `api/virtual` validate preview and premium streaming locally, with no
Bunny.net credential and no change to the metadata/`/stream` split, by
selecting a deterministic local adapter through Spring profiles. One
deliberate exception: the D7 correction to the unplanned-course
authorization rule (specified in the accompanying `virtual` capability
delta, not here) changes what "denied" means for an unplanned course —
this journey now proves that corrected behavior rather than the old one.

## Requirements

### Requirement: Profile-guarded, mutually exclusive adapter selection

`VirtualConfiguration` MUST expose exactly one `BunnyNetSignatureService`
bean: `LocalBunnyNetSignatureService` when profile `e2e-bunny-net` is
active, `StringFormatBunnyNetSignatureService` otherwise. Startup MUST
fail closed when `e2e-bunny-net` combines with `prod`, `production`, or
`staging`.

#### Scenario: Local profile selects the local adapter

- GIVEN profile `e2e-bunny-net` is active
- WHEN Spring resolves `BunnyNetSignatureService`
- THEN it MUST resolve to `LocalBunnyNetSignatureService`
- AND no outbound request MUST reach Bunny.net

#### Scenario: Default profiles keep the real adapter as sole candidate

- GIVEN the application starts without profile `e2e-bunny-net`
- WHEN Spring resolves `BunnyNetSignatureService`
- THEN it MUST resolve to `StringFormatBunnyNetSignatureService`
- AND no second candidate bean MUST exist

#### Scenario: Local profile fails closed in production-like environments

- GIVEN `e2e-bunny-net` is active together with `prod`, `production`, or
  `staging`
- WHEN the application context starts
- THEN startup MUST fail before accepting any request

### Requirement: Deterministic local signed URL with no reusable credential

`LocalBunnyNetSignatureService` MUST preserve the existing signed-URL
shape (`signedUrl`, `type`, `QUALITY_LADDER`, `expiresAt`), derive
`expiresAt` from the caller's TTL, and produce a `sig` that is
deterministic for identical inputs, non-secret, and unusable against the
real CDN.

#### Scenario: Same inputs yield the same signature and expiry rule

- GIVEN the local adapter is active
- WHEN the same lesson stream is requested twice with the same TTL
- THEN both responses MUST share the same `sig` value
- AND both `expiresAt` values MUST follow the same TTL-derived rule
- AND neither response MUST contain a real Bunny.net credential

### Requirement: Bruno-verified access control without a subscription

The local acceptance journey MUST prove current authorization behavior
using the local adapter: preview lessons stream without a subscription;
non-preview lessons do not, regardless of whether their course is linked
to any billing plan (per the `virtual` capability's D7 correction).

#### Scenario: Preview lesson streams without a subscription

- GIVEN profile `e2e-bunny-net` is active and a published preview lesson
  exists
- WHEN the Bruno journey requests its `/stream` resource with no active
  premium subscription
- THEN the response MUST succeed with a deterministic local signed URL

#### Scenario: Non-preview lesson is denied without a subscription

- GIVEN profile `e2e-bunny-net` is active and a published non-preview
  lesson exists, with no `billing_plan_courses` row required for its course
- WHEN the Bruno journey requests its `/stream` resource with no active
  premium subscription
- THEN the response MUST be denied
- AND no signed URL MUST be present in the response
- AND lesson detail MUST expose no video identifier

### Requirement: Bruno-verified premium access with an active subscription

The local acceptance journey MUST prove premium streaming after a real
subscription is fulfilled through the existing checkout and webhook
flow, composing profiles `e2e-bunny-net`, `e2e-catalog-content`, and
`e2e-mercadopago`, with no entitlement seeded directly.

#### Scenario: Subscribed user streams a premium lesson

- GIVEN the three composed profiles are active and the journey completed
  checkout plus a signed approved webhook activating the subscription
- WHEN the journey requests a published non-preview lesson's `/stream`
  resource
- THEN the response MUST succeed with a deterministic local signed URL

### Requirement: Real adapter stays the sole default outside the local profile

Enabling the local profile MUST NOT alter production-path bean
resolution, and existing real-adapter coverage MUST remain intact.

#### Scenario: Existing real-adapter test suite still passes unmodified

- GIVEN the existing `StringFormatBunnyNetSignatureServiceTest` suite
- WHEN it runs after the local adapter is introduced
- THEN all its assertions MUST still pass with unmodified behavior

### Requirement: Isolated unit coverage of the local adapter

`LocalBunnyNetSignatureService` and its profile-based selection MUST have
dedicated unit tests, independent of the Bruno journey, sufficient to
keep `virtual`'s layered JaCoCo floors (95% domain+application, 90%
infrastructure) intact.

#### Scenario: Local adapter behavior is unit-tested in isolation

- GIVEN a unit test exercises `LocalBunnyNetSignatureService` directly
- WHEN it requests a signed URL for a given lesson stream and TTL
- THEN the test MUST assert the deterministic `sig`, the derived
  `expiresAt`, and the preserved signed-URL shape without a Spring context

#### Scenario: Profile selection is unit-tested for every combination

- GIVEN a slice test loads `VirtualConfiguration` under each profile
  combination from the selection requirement
- WHEN the context resolves `BunnyNetSignatureService`
- THEN exactly one bean of the expected implementation MUST be present
  for each combination

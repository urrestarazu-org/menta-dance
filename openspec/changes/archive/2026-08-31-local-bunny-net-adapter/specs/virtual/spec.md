# Delta for Virtual

> **Source of record**: `openspec/changes/virtual-subscription-access/specs/virtual/spec.md`
> (change not yet archived into `openspec/specs/virtual/spec.md`). This delta is layered on
> top of that pending change and does not edit it. Whichever change archives first, the other
> MUST apply this MODIFIED block against the resulting `virtual` spec text at archive time.
> Every other requirement in that source spec — entitlement snapshot, denial/non-disclosure,
> cancellation semantics, endpoint parity, architecture boundaries — is untouched by this
> change and is not repeated here.

## MODIFIED Requirements

### Requirement: Ordered public-access cascade

Before consulting Billing, Virtual MUST evaluate the requested lesson in this order. The
first matching rule MUST grant access without authentication:

1. `lesson.isFree == true`;
2. the lesson's module has `isPreview == true`.

When neither rule grants access — including when the lesson's course belongs to no plan —
Virtual MUST require an authenticated student and resolve entitlement through the shared
Billing port; absence of a current entitlement MUST deny with `403`. The BFF and every
browser client MUST NOT make this authorization decision.

(Previously: a third rule granted public access whenever the lesson's course belonged to no
plan. D7 removes it — an unplanned course now falls through to the same entitlement check as
a planned course and denies by default, instead of being treated as a permanent public grant.)

#### Scenario: Free lesson is public

- GIVEN an anonymous visitor requests a lesson with `isFree = true`
- WHEN Virtual resolves its access
- THEN it grants the request without consulting Billing
- AND lesson detail returns `200 OK`
- AND the stream endpoint may issue a fresh signed URL

#### Scenario: Preview module is public even when its lesson is not free

- GIVEN an anonymous visitor requests a lesson with `isFree = false` in a module with
  `isPreview = true`
- WHEN Virtual resolves its access
- THEN it grants the request without authentication or Billing entitlement
- AND the response identifies the lesson as preview content

#### Scenario: A course absent from every plan denies protected lessons for an anonymous visitor

- GIVEN a non-free lesson in a non-preview module whose course belongs to no billing plan
- WHEN an anonymous visitor requests lesson detail or its `/stream` resource
- THEN Virtual returns `403 application/problem+json` for both requests
- AND lesson detail exposes no video identifier
- AND the stream endpoint issues no signed URL

#### Scenario: A course absent from every plan denies protected lessons for an authenticated student without entitlement

- GIVEN the same non-free, non-preview lesson whose course belongs to no billing plan
- WHEN an authenticated student without a current Billing entitlement requests lesson detail
  or its `/stream` resource
- THEN Virtual returns `403 application/problem+json` with subscription-required guidance
- AND no video identifier or signed URL is exposed
- AND the response is identical in outcome to a planned course denial, so callers cannot
  distinguish "unplanned" from "planned without entitlement"

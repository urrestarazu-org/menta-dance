# BFF Plans View Specification

## Purpose

Give the BFF a public, anonymous-accessible plans-listing page backed by the
cached billing plans call (see `billing-api-integration`), completing the
browse → sample → convert funnel the lesson-sample CTA (#170) points visitors
toward.

## Requirements

### Requirement: Anonymous-reachable plans route

The BFF MUST expose `GET /plans` without requiring authentication, rendering
every plan returned by the billing plans call.

#### Scenario: Anonymous visitor opens the plans page

- GIVEN a visitor with no session
- WHEN they request `GET /plans`
- THEN the BFF serves the plans page directly with no redirect to `/login`

### Requirement: Plans-list route is permitted without widening access elsewhere

`BffSecurityConfig` MUST add one `HttpMethod.GET`-scoped `permitAll` entry
for the exact path `/plans` — no wildcard, since the route takes no path
variable — placed before `anyRequest().authenticated()`, following the
method-scoping pattern the existing two course/lesson entries established
(#170). This entry MUST match only the plans route. Every route that required
authentication before this change MUST still require it afterwards.

This both-directions requirement is not ceremony: an over-broad matcher fails
open silently, serving protected pages to anonymous callers with no error
anywhere to notice it, so the negative half needs its own regression test
rather than being inferred from the positive one.

#### Scenario: Only the plans route is newly permitted

- GIVEN an anonymous visitor with no session
- WHEN they request `/dashboard`, or any route other than `/login`, `/error`,
  `/actuator/health`, the two existing course/lesson routes, and `/plans`
- THEN the BFF still redirects them to `/login`, exactly as before this
  change

#### Scenario: Anonymous access to the plans route specifically

- GIVEN an anonymous visitor with no session
- WHEN they request `/plans`
- THEN the security chain permits the request with no redirect to `/login`

### Requirement: Each plan renders name, description, price with currency and duration

For every plan returned by the billing plans call, the page MUST render its
name, description, price together with its currency, and its duration period
(`durationDays`). A bare numeric price with neither currency nor duration is
not rendered, since it would be meaningless to a visitor (D5).

#### Scenario: A plan renders its full price context

- GIVEN the billing plans call returns a plan with `price`, `currency`, and
  `durationDays`
- WHEN the plans page renders that plan
- THEN the page shows its name, description, and price together with its
  currency and duration period

### Requirement: Featured plans show a badge, list order is unchanged

When a plan's `featured` flag is `true`, the page MUST render a "Destacado"
badge alongside it. This flag MUST NOT affect the order in which plans are
rendered — the list order follows the order the upstream response returns,
unchanged by `featured` (D5).

#### Scenario: A featured plan shows the badge without reordering

- GIVEN the billing plans call returns plans where one has `featured: true`
  and is not first in the response order
- WHEN the plans page renders
- THEN that plan shows a "Destacado" badge
- AND the rendered list order matches the upstream response order exactly,
  unchanged by which plan is featured

#### Scenario: A non-featured plan shows no badge

- GIVEN a plan has `featured: false`
- WHEN the plans page renders that plan
- THEN no "Destacado" badge is shown for it

### Requirement: Upstream failures degrade to the shared error view

A `404`, `429`, or `503` from the billing plans call MUST render the BFF's
existing shared error view, with no leaked problem-detail body and no
plans-specific error copy (D6). This is the same degradation every other BFF
page uses on upstream failure — consistent handling, and honest: if billing
is unreachable, the entire subscription flow is unavailable regardless of
how the plans page phrases it.

#### Scenario: Billing downtime renders the shared error view

- GIVEN the billing plans call returns `404`, `429`, or `503`
- WHEN a visitor requests `/plans`
- THEN the BFF renders the existing shared error view
- AND the response contains no upstream problem-detail body and no
  plans-specific error message

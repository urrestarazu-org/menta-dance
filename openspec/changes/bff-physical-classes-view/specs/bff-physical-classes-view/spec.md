# BFF Physical Classes View Specification

## Purpose

Give students a web path through the physical line: browse in-person
courses, see live session availability, quote a month or a single session,
and hand off to payment — composed from the public catalog and billing's
physical pricing, quote and purchase endpoints.

## Requirements

### Requirement: Anonymous class list

The BFF MUST expose `GET /classes` without authentication, rendering every
course of modality `PHYSICAL` returned by `GET /api/v1/catalog/courses`, in
upstream order, with its title, level, teacher and weekly schedule. Courses of
any other modality MUST NOT be rendered. The catalog call MUST NOT carry an
`Authorization` header.

#### Scenario: Only physical courses are listed

- GIVEN the catalog list returns one `PHYSICAL` and one `VIRTUAL` course
- WHEN an anonymous visitor requests `GET /classes`
- THEN the page lists the physical course with a link to `/classes/{courseId}`
- AND the virtual course is not listed

#### Scenario: No physical courses

- GIVEN the catalog list returns no `PHYSICAL` course
- WHEN a visitor requests `GET /classes`
- THEN the page renders an empty-state message instead of an empty list

### Requirement: Anonymous class detail with live availability

The BFF MUST expose `GET /classes/{courseId}` without authentication. It MUST
render the course data and each session from the catalog detail, with its
start converted from UTC to the configured academy zone
(`menta.classes.display-zone`). A session with `availableSpots > 0` MUST show
the number of free spots; a session with `availableSpots = 0` MUST show
"Completo" and MUST NOT offer a buy action.

#### Scenario: Free spots and sold-out sessions

- GIVEN the catalog detail returns one session with 17 free spots and one with 0
- WHEN the class page renders
- THEN the first session shows "17 lugares libres" and a buy action
- AND the second shows "Completo" and no buy action

#### Scenario: Session time in the academy zone

- GIVEN a session with `scheduledAt` `2026-10-06T22:00:00Z` and the default zone
- WHEN the class page renders
- THEN the session shows `19:00`

#### Scenario: A non-physical or unknown id

- GIVEN the catalog detail answers `404`, or answers a body whose `modality`
  is not `PHYSICAL`
- WHEN a visitor requests `GET /classes/{courseId}`
- THEN the BFF renders the shared error view with status `404`

### Requirement: Price is shown when published, purchase hidden when not

The class page MUST read `GET /api/v1/billing/physical/courses/{courseId}/pricing`
and show the monthly price with its currency. When that call answers `404`
(no price published yet), the page MUST still render, MUST say the price is not
published yet, and MUST NOT offer any buy action.

#### Scenario: No published price

- GIVEN the pricing call answers `404`
- WHEN the class page renders
- THEN it shows the sessions and a "precio todavía no publicado" message
- AND no buy action is rendered

### Requirement: Buying requires a session

Buy actions MUST only be rendered for an authenticated caller. An anonymous
caller MUST instead see a link to `/login`. `POST /classes/{courseId}/quote`
and `POST /classes/purchases` MUST require authentication.

#### Scenario: Anonymous visitor sees a login prompt

- GIVEN a visitor with no session and a priced class with free spots
- WHEN the class page renders
- THEN it shows "Iniciá sesión para comprar" linking to `/login`
- AND no quote form is rendered

### Requirement: Quote before paying

`POST /classes/{courseId}/quote` with `purchaseType` (`MONTHLY`, or
`INDIVIDUAL` with `sessionId`) MUST call
`POST /api/v1/billing/physical/quotes` with the caller's bearer token and
render a confirmation page with the amount, currency, purchase type and
expiry. When the quote's availability is `UNAVAILABLE`, the page MUST say the
class is full and MUST NOT render the payment form. When billing rejects the
quote (`404`, `422`), the page MUST render a fixed retry message with no
upstream problem detail.

#### Scenario: Available quote offers payment

- GIVEN billing returns an `AVAILABLE` quote of 12000 ARS
- WHEN the student requests a monthly quote
- THEN the confirmation page shows 12000 ARS and a payment form with
  `MERCADO_PAGO` and `BANK_TRANSFER` options

#### Scenario: Sold-out quote offers no payment

- GIVEN billing returns an `UNAVAILABLE` quote
- WHEN the confirmation page renders
- THEN it says the class has no free spots and renders no payment form

### Requirement: Payment is idempotent per confirmation page

The confirmation page MUST carry an idempotency key minted by the BFF when the
page is rendered, and `POST /classes/purchases` MUST forward it unchanged, so a
resubmitted form replays billing's original checkout instead of creating a
second payment.

#### Scenario: Double submit

- GIVEN a rendered confirmation page with key `K`
- WHEN the student submits the payment form twice
- THEN both checkout calls carry idempotency key `K`

### Requirement: Checkout hands off to the chosen payment rail

`POST /classes/purchases` MUST call `POST /api/v1/billing/physical/purchases`
with the caller's bearer token. For `MERCADO_PAGO` it MUST redirect the browser
to the returned `checkoutUrl`. For `BANK_TRANSFER` it MUST render the transfer
instructions (CBU, alias, holder, CUIT, amount and reference). A `409` MUST
render a "no quedan lugares" message and a `410` a "la cotización venció"
message, both linking back to the class page.

#### Scenario: Mercado Pago

- GIVEN billing returns a checkout with `checkoutUrl` `U`
- WHEN the student pays with `MERCADO_PAGO`
- THEN the BFF responds with a redirect to `U`

#### Scenario: Bank transfer

- GIVEN billing returns bank transfer instructions with reference `R`
- WHEN the student pays with `BANK_TRANSFER`
- THEN the page shows the account details and reference `R`

#### Scenario: Capacity taken meanwhile

- GIVEN billing answers `409`
- WHEN the student pays
- THEN the page says no spots are left and links back to `/classes/{courseId}`

### Requirement: Only the class read routes are newly permitted

`BffSecurityConfig` MUST add exactly two `HttpMethod.GET` `permitAll` entries,
`/classes` and `/classes/*`. Every route that required authentication before
this change MUST still require it, and the two `POST` routes MUST require it.

#### Scenario: Read routes open, writes and others closed

- GIVEN an anonymous visitor
- WHEN they request `GET /classes` and `GET /classes/{id}`
- THEN neither redirects to `/login`
- AND `POST /classes/{id}/quote`, `POST /classes/purchases`, `/dashboard`
  and `GET /classes/{id}/anything` still redirect to `/login` (or are rejected)

### Requirement: Upstream failures degrade to the shared error view

A `503`, `429`, timeout or unreachable upstream on any of these calls MUST
render the shared error view with no upstream problem-detail body, the same
degradation every other BFF page uses.

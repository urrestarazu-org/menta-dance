# Proposal: BFF Physical Classes View

## Intent

The physical line is complete on the API side: the public catalog lists
`PHYSICAL` courses and, since #107, its detail publishes the upcoming sessions
with live `availableSpots`; billing publishes per-course pricing, quotes a
`MONTHLY` or `INDIVIDUAL` purchase, and turns a quote into a checkout (Mercado
Pago with a capacity hold, or bank transfer). The BFF has none of it: its seven
pages are all virtual-course, plans or session pages, so a student has no web
path to discover an in-person class, see whether it has room, or pay for it.
This change gives the BFF the student flow for in-person classes, end to end.

## Scope

### In Scope

- **Browse** — `GET /classes` (anonymous): every `PHYSICAL` course from the
  public catalog list, with level, teacher and weekly schedule.
- **Availability** — `GET /classes/{courseId}` (anonymous): course data, the
  course's monthly price (when published), and each upcoming session with its
  local date/time and free spots, or "Completo" when sold out.
- **Quote** — `POST /classes/{courseId}/quote` (authenticated): asks billing
  for a quote for the whole month (`MONTHLY`) or one session (`INDIVIDUAL`) and
  renders a confirmation page with the exact amount and its expiry. A sold-out
  quote shows no payment form.
- **Buy** — `POST /classes/purchases` (authenticated): checks out the quote.
  Mercado Pago redirects to the provider's checkout URL; bank transfer renders
  the transfer instructions and reference.
- Anonymous visitors see "Iniciá sesión para comprar" instead of buy buttons.
- A "Clases presenciales" link on the dashboard.

### Out of Scope

- Any change to `api:*` — every upstream endpoint used here already exists.
- Post-payment pages (Mercado Pago return URLs, "my classes", attendance
  history, the check-in QR). The student flow here ends at the hand-off to
  the payment provider or the transfer instructions.
- Returning to the class page after login (form login keeps its forced
  `/dashboard` success URL).
- Physical ids on the existing `/courses/{id}` virtual page.
- Styling beyond the existing flat, unstyled templates.

## Capabilities

### New Capabilities

- `bff-physical-classes-view`: anonymous class list and class detail with
  live availability, and the authenticated quote → checkout purchase flow.

### Modified Capabilities

- None. `bff-plans-view`'s "only these routes are public" regression scenario
  is extended in spirit by this spec's own both-directions requirement.

## Approach

Two new outbound ports, one per upstream, mirroring the existing per-upstream
port/adapter pairs (D1):

- `PhysicalCatalogApiClient` → `PhysicalCatalogApiAdapter` on the existing
  `virtualApiWebClient` (it already targets the catalog base URL).
- `PhysicalPurchaseApiClient` → `PhysicalPurchaseApiAdapter` on the existing
  `billingApiWebClient`.

Four thin use cases (list, detail, quote, purchase) with sealed result types
where the page has more than one outcome, and one flat `ClassesController`
switching over them exhaustively, like `CourseDetailController`. Expected
business outcomes (sold out, quote expired, quote rejected) render a message on
the page; transport failures stay uncaught `@ResponseStatus` exceptions that
land on the shared error view (same D6 as the plans page).

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Sessions are stored in UTC; showing raw UTC confuses students | High | Render in the academy zone (`menta.classes.display-zone`, default `America/Argentina/Buenos_Aires`, which matches the catalog's documented example: `19:00` start ↔ `22:00Z`) |
| A double-submitted "Pagar" creates two payments | Med | The BFF mints the idempotency key once when it renders the confirmation page; a resubmit replays billing's original checkout |
| Opening `/classes/*` widens anonymous access | Med | GET-only, single-`*` matchers; both-directions security regression test |
| Mercado Pago redirect is an open redirect if the upstream URL were attacker-controlled | Low | The URL comes only from our own billing response, never from the request |

## Rollback Plan

Additive only: new ports, adapters, use cases, controller, four templates, two
security matchers, one dashboard link and one property. Revert the commit; no
schema, migration or upstream change involved.

## Success Criteria

- [ ] Anonymous visitor can open `/classes` and `/classes/{id}` and see free
      spots or "Completo" per session.
- [ ] Logged-in student can quote a month or a single session and see the
      exact amount before paying.
- [ ] Mercado Pago checkout redirects to the provider; bank transfer shows the
      instructions and reference.
- [ ] Sold-out (`409`) and expired quote (`410`) render actionable messages,
      not the error page.
- [ ] Every previously protected route still redirects anonymous callers to
      `/login`.

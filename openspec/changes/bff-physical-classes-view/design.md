# Design: BFF Physical Classes View

## Technical Approach

Two new outbound ports, four thin use cases and one controller, following the
shape the BFF already uses for the virtual and plans pages: flat
`@Controller`, flat templates without fragments, sealed result types switched
exhaustively, uncaught `@ResponseStatus` exceptions for transport failures.

```text
GET  /classes                 → GetClassesViewUseCase      → catalog list (PHYSICAL only)
GET  /classes/{id}            → GetClassDetailViewUseCase  → catalog detail + billing pricing
POST /classes/{id}/quote      → QuoteClassUseCase          → billing quote (bearer)
POST /classes/purchases       → PurchaseClassUseCase       → billing checkout (bearer)
```

## Architecture Decisions

### D1 — One port per upstream, reusing the existing WebClient beans

| Option | Verdict |
|---|---|
| Extend `VirtualApiClient` / `BillingApiClient` | Rejected: both are documented as virtual-only and plans-only; `BillingApiAdapter` carries a plans cache this flow must not share |
| `PhysicalCatalogApiClient` + `PhysicalPurchaseApiClient` on `virtualApiWebClient` / `billingApiWebClient` | **Chosen**: same base URLs and timeouts, no new properties class or bean |

The adapters use `exchangeToMono` and release error bodies unread, so no
upstream problem detail can reach a page.

### D2 — Business outcomes are pages, transport failures are the error view

Sold out (quote `UNAVAILABLE` or checkout `409`), expired quote (`410`) and a
quote billing refuses (`400/404/422`) are things a student can act on, so they
render a message and a link back to the class. `503/429`, timeouts and resets
keep the shared error view (plans page D6).

### D3 — The idempotency key is minted with the confirmation page

`QuoteClassUseCase` takes a `Supplier<String>` (UUIDs in production) and puts
the key in the payment form. A double submit or a browser retry replays
billing's first checkout. Minting at payment time would give every submit a
new key and defeat billing's idempotency.

### D4 — Session times in the academy zone, labels in the use case

Sessions are UTC instants. `menta.classes.display-zone` (default
`America/Argentina/Buenos_Aires`) converts them; the catalog's documented
example (`19:00` start, `22:00Z` session) matches it. Spanish labels for
weekdays, levels and dates are built in `ClassLabels` so templates stay free
of locale logic and unknown codes fall back to the raw value.

### D5 — Buy actions only for a signed-in caller with a price

The controller exposes `signedIn` (access token present). Anonymous visitors
see a login link instead of a form that would bounce through `/login`. A
course without published pricing shows its sessions but no buy action, since
billing would refuse to quote it.

### D6 — Routes

`GET /classes` and `GET /classes/*` join the `permitAll` list, GET-only. The
quote and purchase POSTs fall through to `anyRequest().authenticated()` and
keep CSRF protection (forms use `th:action`).

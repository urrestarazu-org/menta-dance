# Proposal: BFF Plans View

## Intent

Issue #170 shipped a lesson-sample view whose subscription CTA is deliberately a
dead-end message ("no plans page exists yet — vuelve pronto"). The BFF still has
zero billing/plans/subscription integration, so a visitor blocked from a premium
lesson has no path to convert. This change (#177, US-BILLING-013) gives the BFF a
public plans-listing page and wires the existing CTA to a real route, completing
the browse → sample → convert funnel the earlier BFF virtual-learning work set up.

## Scope

### In Scope
- `BillingApiClient` outbound port + `BillingApiAdapter` (WebClient), mirroring
  `AuthApiClient`/`VirtualApiClient` exactly: own properties class, own qualified
  WebClient bean, own nested exceptions, including a new 404/429/503 → typed
  exception mapping (429 has no adapter precedent today).
- A short-lived in-memory cache in front of the upstream call (D1), so N visitor
  requests collapse into one upstream call per TTL period instead of each hitting
  billing's fingerprint-based rate limiter directly.
- Plans-list view + controller + template rendering `id, name, description, price,
  currency, durationDays, featured` for each plan.
- `BffSecurityConfig` `permitAll` GET entry for the plans route, following the
  existing single-`*` wildcard pattern.
- Wiring `lesson.html`'s sample-branch CTA to the new route via a hardcoded
  `th:href="@{/plans}"` template literal (D2).
- Graceful degradation to the existing error view on upstream 404/429/503,
  matching the house convention of leaving `@ResponseStatus`-annotated exceptions
  uncaught.

### Out of Scope
- Any change to `api:billing` (the rate-limiter fingerprint gap is #195).
- Subscription/checkout flow — this story only lists plans.
- Plan-detail page (`/plans/{id}`) — no acceptance criterion needs it.
- Surfacing upstream's `subscription.plansUrl` hint from the lesson endpoint —
  rejected in favor of a BFF-owned route (D2).

## Capabilities

### New Capabilities
- `bff-plans-view`: anonymous-accessible BFF page listing billing plans, backed
  by a cached call to the upstream plans endpoint, with graceful degradation.
- `billing-api-integration`: outbound port/adapter for the billing plans
  endpoint, mirroring `virtual-api-integration`'s shape, including 429 mapping
  and the response cache.

### Modified Capabilities
- `virtual-lesson-view`: the "BFF-assembled sample view on subscription denial"
  requirement currently locks the CTA to "a message with no navigable link...
  until issue #177 delivers a BFF plans page." This change fulfills that
  condition — the requirement's scenario updates from a dead-end message to a
  real link to `/plans`.

## Approach

Mirror the Auth/Virtual port+adapter pattern exactly (D3): a dedicated
`BillingApiProperties`, a `@Qualifier("billingApiWebClient")` bean with an
explicit constructor (Lombok's `@RequiredArgsConstructor` cannot carry a
per-param qualifier, per the existing `WebClientConfig` comment), and
`BillingApiAdapter` implementing `mapErrorStatus`-style status mapping extended
with a 429 branch (`PlanRateLimitedException`, no `Retry-After` passthrough to
the visitor — the error view stays generic like every other upstream failure).

To remove the rate-limit collapse risk (D1), the adapter or use case holds a
short-TTL in-memory cache of the plans response, so the BFF's own call volume to
billing stays low regardless of visitor traffic. Cache shape (TTL value,
invalidation, thread-safety, adapter- vs. use-case-level placement) is a design-
phase decision; caching itself is locked.

`PlansController` mirrors `CourseDetailController`'s style: flat `@Controller`,
no fragments, uncaught upstream exceptions falling through to the existing
`/error` → `error.html` flow. `lesson.html`'s sample branch gets a plain
`th:href="@{/plans}"` literal (D2) — `LessonView.Sample` gains no new field,
preserving the field's deliberate absence locked by #170.

## Affected Areas

| Area | Impact | Description |
|------|--------|--------------|
| `bff/.../application/port/out/BillingApiClient.java` | New | Outbound port, incl. 429 exception type |
| `bff/.../infrastructure/adapter/BillingApiAdapter.java` | New | WebClient adapter + response cache |
| `bff/.../infrastructure/config/BillingApiProperties.java` | New | Dedicated base-url properties |
| `bff/.../infrastructure/config/WebClientConfig.java` | Modified | 3rd qualified `WebClient` bean |
| `bff/.../infrastructure/config/BffSecurityConfig.java` | Modified | `permitAll` for `/plans` |
| `bff/.../application/usecase/*` | New | Plans-list use case + view DTO |
| `bff/.../infrastructure/web/controller/PlansController.java` | New | Plans-list controller |
| `bff/src/main/resources/templates/plans.html` | New | Plans listing view |
| `bff/src/main/resources/templates/lesson.html` | Modified | Sample CTA now links to `/plans` |
| `bff/src/test/**` | New/Modified | WireMock adapter test (incl. 429, cache), security/view integration tests, lesson-sample CTA assertion update |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Cache staleness after a plan price/feature change | Low | 5-minute TTL (D4); plans change by season, not by hour |
| Cache introduces a first-of-its-kind stateful component in a stateless-so-far BFF | Med | Confine to adapter/use-case layer, document TTL and eviction explicitly in design |
| 429 mapping is new territory, could leak upstream detail if mis-implemented | Med | Mirror existing 404/503 uncaught-exception convention; dedicated adapter test for 429 |
| Currency/price formatting has no existing BFF template precedent | Low | Simple `BigDecimal` + currency string rendering; revisit only if a real i18n need appears |
| Residual rate-limit exposure if cache TTL is misconfigured too low | Low | TTL fixed at 5 minutes by D4, with the reasoning recorded — not a default left unexamined |

## Rollback Plan

All changes are additive (new port/adapter/controller/template/route) plus one
template edit (`lesson.html`'s CTA link) and one spec-level requirement update.
Revert by removing the new files, restoring `lesson.html`'s dead-end message,
and dropping the `permitAll` entry; no schema or migration involved.

## Dependencies

- #29 (billing plans endpoint) and #170 (lesson sample view + locked CTA
  absence) — both already merged.
- #195 (billing rate-limiter fingerprint fix) — explicitly NOT a dependency;
  this change works around the limitation via caching rather than waiting on it.

## Success Criteria

- [ ] Anonymous visitor can open `/plans` and see all listed plans with price,
      currency, duration, and featured marker.
- [ ] A non-entitled visitor's lesson-sample CTA links to `/plans` instead of a
      dead-end message.
- [ ] Upstream 404/429/503 degrade to the existing error view with no leaked
      problem detail.
- [ ] Repeated visitor requests within the cache TTL do not each trigger an
      upstream call, verified by an adapter-level test.

## Resolved Questions

All three product questions were put to the user and answered. They are locked
alongside D1–D3; spec and design implement them as stated.

### D4 — Cache TTL is 5 minutes

A price change becomes visible within 5 minutes. With billing's 60 req/min
ceiling, this keeps the BFF's own upstream call volume trivially far from the
limit regardless of visitor traffic, while a dance academy's prices change by
season, not by hour. Rejected 1 minute (upstream ceiling creeps back into
reach with multiple BFF instances or frequent restarts) and 1 hour (a promo
announcement could be contradicted by a stale page for an hour).

### D5 — `featured: true` renders a simple badge, no reordering

The acceptance criteria name only nombre/precio/descripción; `currency` and
`durationDays` are treated as implied by "precio" (a bare number without
currency or period is meaningless to a visitor). `featured` is genuinely
optional, and the user chose to include it: a "Destacado" badge is one
template line, and this is the page whose entire job is converting — marking
a recommended plan is the cheapest commercial lever available. **Badge only**:
the list is not reordered by `featured` in this slice.

### D6 — Billing downtime renders the shared error view

Same degradation as every other BFF page — no bespoke plans-specific error
copy. Two reasons the user accepted: consistency (no new variant to design,
test and maintain), and honesty (if billing is down, the entire subscription
flow is down, so a softer "come back shortly" would promise something the
system cannot deliver anyway).

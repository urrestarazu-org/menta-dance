# Proposal: Billing Subscription Status

## Intent

`SubscriptionController` today exposes only POST `/subscriptions` (checkout) and
DELETE `/subscriptions/me` (self-cancellation). There is no read path at all: a
student who paid cannot ask the API what they bought, when it ends, whether it
already expired, or whether a checkout they abandoned is still completable. Every
downstream surface (BFF dashboard, Android app, support) currently has to infer
subscription state from the checkout response it happened to see once. This
change (#32, US-BILLING-004) adds the two read endpoints that turn subscription
state into a first-class, queryable fact.

## Scope

### In Scope
- `GET /api/v1/billing/subscriptions/me` — current subscription for the acting
  user, covering the four display states of issue #32: active (plan, dates, days
  remaining), expiring soon (`expiringSoon` flag + days remaining), expired
  (renewal hint pointing at the existing `GET /api/v1/billing/plans`), and
  pending payment (surfacing the already-persisted `Subscription.getCheckoutUrl()`
  so the user can finish paying).
- A new domain exception + `NO_SUBSCRIPTION` error code for "user has no
  subscription at all" (D2), rendered as RFC 9457 ProblemDetail through the
  existing `SubscriptionExceptionHandler` / `ProblemDetails` pattern.
- `GET /api/v1/billing/subscriptions/me/history` — full historical list, newest
  first. `billing_subscriptions` already persists one immutable row per checkout,
  so this needs only an explicit `ORDER BY created_at DESC` on the existing
  `findAllByUserId` plus a new response DTO. **No schema change.**
- Read-time computation of `daysRemaining` and `expiringSoon` from `endDate`
  (both absent/meaningless while PENDING).
- Reuse of `SubscriptionController.actingUserId(Authentication)` verbatim, same
  as DELETE `/me`.

### Out of Scope
- Any BFF page or Android screen for "mi suscripción" — API-only, following the
  #29 → #177 split precedent (plans API vs. plans BFF page shipped separately).
- Cancelled-but-still-within-`endDate` subscriptions as the `/me` result (D1):
  `findLatestCancelledWithRemainingAccess` exists but is deliberately not wired
  into this story; possible follow-up.
- `autoRenew` — the concept does not exist in this codebase (checkout is one-shot
  Mercado Pago Checkout Pro), so the field from the issue's example body is not
  implemented.
- Any change to expiry scheduling (`SubscriptionExpiryWorker`), cancellation, or
  checkout behavior.
- Caching — the issue explicitly requires a non-cacheable, always-fresh read.

## Capabilities

### New Capabilities
- `billing-subscription-status`: read-side view of a user's own subscription —
  current state (active / expiring soon / expired / pending payment), computed
  remaining days, a no-subscription error contract, and the chronological
  history listing.

### Modified Capabilities
- None. `billing-subscriptions` covers the write-side lifecycle (checkout,
  cancellation, trial, expiry); none of its existing requirements change. The new
  endpoints only observe states that spec already defines.

## Approach

Both endpoints are additive methods on the existing `SubscriptionController`,
inheriting its `@SubscriptionEndpoint` annotation and therefore the existing
`@RestControllerAdvice(annotations = SubscriptionEndpoint.class)` ProblemDetail
advice for free.

`GET /me` resolves the current subscription over PENDING, ACTIVE and EXPIRED only
(D1). The response DTO is state-shaped rather than one flat body with nullable
everything: a PENDING subscription carries `checkoutUrl` and no dates, an
EXPIRED one carries the end date and a plans hint, an ACTIVE one carries
`daysRemaining` and `expiringSoon`. `SubscriptionStatus` already models all four
values, and `EXPIRED`'s javadoc literally cites US-BILLING-004 — the domain
anticipated this read.

`GET /me/history` maps every persisted row for the user, newest first, to a
compact DTO (id, plan, status, dates). Rows are never mutated into a different
logical subscription, so the table is already a correct audit trail.

### Deliberate divergences from the issue's literal text

| Issue text | Shipped instead | Why |
|---|---|---|
| `expiringsSoon` (prose) vs `expiringSoon` (JSON) | `expiringSoon` | Issue is internally inconsistent; camelCase matches the rest of the project |
| `"id": 123` | UUID string | `Subscription.id` is a UUID |
| `{"error", "message", "suggestion"}` | RFC 9457 ProblemDetail | Established convention across all of `api:billing` |
| `autoRenew` | omitted | No recurring-billing concept exists |

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/billing/.../web/controller/SubscriptionController.java` | Modified | Two new GET methods |
| `api/billing/.../web/dto/*` | New | Current-status + history response DTOs |
| `api/billing/.../application/usecase/*` | New | Query use case(s) for status and history |
| `api/billing/.../domain/exception/*` | New | `NO_SUBSCRIPTION` exception (D2) |
| `api/billing/.../web/SubscriptionExceptionHandler.java` | Modified | Handler branch for the new exception |
| `api/billing/.../port/out/SubscriptionRepository.java` + JPA adapter | Modified | Ordering on `findAllByUserId`; current-status lookup |
| `api/billing/src/test/**` | New/Modified | Use-case unit tests + controller/integration tests for the 6 scenarios |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| History query cost: `findAllByUserId` joins a course snapshot per row, potential N+1 for heavy users | Med | Measure during design/implementation; fetch-join or projection if needed — does not block the proposal |
| A state-shaped `/me` response is harder for clients than one flat body | Low | Keep a single discriminating `status` field so clients branch on one value |
| `expiringSoon` threshold hardcoded now, configurable demanded later | Low | Domain constant with the boundary style (`<=`) mirroring `Subscription.expire()`; trivially promotable to config later |
| New `NO_SUBSCRIPTION` code confused with existing `SUBSCRIPTION_NOT_FOUND` | Low | Distinct codes documented in the spec; the existing one stays deliberately ambiguous for cancellation anti-enumeration |

## Rollback Plan

Every change is additive: two controller methods, new DTOs/use cases, one new
exception + handler branch, and an `ORDER BY` on an existing query. No migration,
no schema change, no behavior change to checkout, cancellation, or expiry. Revert
by deleting the new files and the two controller methods; the `ORDER BY` is safe
to keep or drop.

## Dependencies

- `GET /api/v1/billing/plans` (#29, merged) — the real target for the renewal /
  "ver planes" hints in the expired and no-subscription cases.
- Existing checkout (#128) and cancellation (US-BILLING-011) flows — read-only
  consumers of their persisted state; neither is modified.

## Success Criteria

- [ ] All 6 issue scenarios are exercised by tests: active, expiring soon,
      expired, no subscription, history, pending payment.
- [ ] A user with a PENDING subscription receives the existing `checkoutUrl` and
      no fabricated `daysRemaining`.
- [ ] A user with no subscription receives a ProblemDetail carrying
      `NO_SUBSCRIPTION`, distinct from `SUBSCRIPTION_NOT_FOUND`.
- [ ] History returns every persisted subscription for the user, newest first.
- [ ] Responses are not cached and reflect a status transition immediately after
      the expiry worker runs.
- [ ] Module coverage floors for `api:billing` (100% domain+application, 85%
      infrastructure) still pass.

## Locked Decisions

### D1 — `GET /me` considers PENDING, ACTIVE and EXPIRED only

A cancelled subscription still inside its `endDate` retains access
(`findLatestCancelledWithRemainingAccess` exists for exactly that in the
cancellation flow), but displaying it here is a separate product question about
what "your subscription" means after you cancelled. Kept out of this story to
avoid designing a fifth display state on speculation; a follow-up can add it.

### D2 — "No subscription" gets its own error code, not `SubscriptionNotFoundException`

The existing exception is deliberately ambiguous ("one exception for three
cases") to prevent enumeration during cancellation. Here the user is querying
their own dashboard, so ambiguity buys nothing and costs clarity. New exception,
code `NO_SUBSCRIPTION`, same ProblemDetail pattern and handler.

## Open Design Questions

Non-blocking, for `sdd-design`:

1. **`expiringSoon` threshold**: proposed as a hardcoded domain constant of 7
   days (per the issue), with no external configuration, unless design finds a
   concrete reason to make it configurable.
2. **History query shape**: whether the course-snapshot join per row warrants a
   projection or fetch-join, and whether history needs pagination at all for a
   realistic per-user row count.

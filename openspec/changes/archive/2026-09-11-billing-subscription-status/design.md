# Design: Billing Subscription Status

## Technical Approach

Two additive read paths on the existing `SubscriptionController`, which already
carries `@SubscriptionEndpoint` and therefore inherits the
`@RestControllerAdvice(annotations = SubscriptionEndpoint.class)` ProblemDetail
advice for free. No schema change, no new index, no write path touched.

`GET /me` resolves the display subscription in two deterministic steps (A1),
computes `daysRemaining` / `expiringSoon` in the **domain** from `endDate` and an
injected `Clock` (A2), and returns a flat body discriminated by `status` (A4)
built from a **sealed** application result type. `GET /me/history` reads a
dedicated projection (A3), newest first. "No subscription at all" becomes a new
`NoSubscriptionException` with code `NO_SUBSCRIPTION` (D2), mapped to 404 by one
new handler branch cloned from `subscriptionNotFound`.

One use case per endpoint, matching the module's own granularity
(`ListPlansUseCase` / `GetPlanUseCase` are separate ports, not one query
facade). Neither is wrapped in a `Transactional*` decorator: the existing
read-only use cases (`ListPlansUseCaseImpl`, `GetPlanUseCaseImpl`) are not
either, and every `SubscriptionRepositoryAdapter` query method already declares
`@Transactional(propagation = REQUIRED, readOnly = true)`, so it runs correctly
without an outer transaction.

## Architecture Decisions

### A1 — `GET /me` resolves slot-first, then latest EXPIRED

`findCurrentByUserId` is index-backed on `active_user_id` and matches exactly the
PENDING/ACTIVE slot; EXPIRED rows have released that column, so D1's third state
is unreachable through it.

| Option | Tradeoff | Verdict |
|---|---|---|
| `findCurrentByUserId()`, else new `findLatestExpiredByUserId()` | Two cheap lookups, both index-served; precedence is explicit (a live slot always beats history) and independent of `created_at` skew | **Chosen** |
| One new `findFirstByUserIdAndStatusInOrderByCreatedAtDesc` over PENDING/ACTIVE/EXPIRED | Single query, but makes "which state wins" depend on row timestamps, and loses the unique-slot index in favour of a status scan | Rejected |
| Reuse `findAllByUserId` and pick in the use case | Loads the user's entire history to answer a one-row question | Rejected |

Only one new port method. `findLatestCancelledWithRemainingAccess` stays unwired
(D1).

### A2 — `daysRemaining` / `expiringSoon` live on `Subscription`, threshold hardcoded at 7 days

The constant stays a private domain constant next to the computation, *not*
configuration: nothing in the codebase reads `menta.billing.*` for domain rules
today, no stakeholder has asked to vary it, and promoting it to config later is a
one-line change that no client contract blocks. Adding a property now would buy a
new config surface, a new test axis and a new failure mode for a value nobody
tunes.

```java
private static final int EXPIRING_SOON_THRESHOLD_DAYS = 7;

/** Absent while PENDING (no endDate yet) — never a fabricated zero. */
public OptionalLong daysRemaining(Instant at) { ... }   // ceil(seconds / 86400), floored at 0
public boolean isExpiringSoon(Instant at) { ... }       // ACTIVE && 0 <= days <= 7
```

Boundary style mirrors `expire()`, which expires on `endDate <= at`: the
threshold is inclusive (`<=`), so a subscription with exactly 7 days left *is*
expiring soon. Days are **ceiled**, so 18 remaining hours read as `1`, never `0`,
while the subscription still grants access; `0` appears only in the sweep-lag
window where `endDate` has passed but `SubscriptionExpiryWorker` has not yet
flipped the row — documented, and `expiringSoon` is `true` there.

Placing this on the aggregate (rather than in the use case) keeps it unit-testable
without mocks and reusable by any future reader; `Clock` is a port and time
arrives as a parameter, so the domain stays framework-free for ArchUnit.

### A3 — history reads a projection, not rehydrated aggregates

`findAllByUserId` maps each row through `toDomainWithCourses`, issuing one
`subscription_courses` query per row. History renders id/plan/status/dates and
never courses.

| Option | Tradeoff | Verdict |
|---|---|---|
| New port `List<SubscriptionHistoryEntry> findHistoryByUserId(UUID)`, adapter maps entity → record directly | One query total; never returns a `Subscription` whose course snapshot was silently falsified to empty; precedent exists (`findExpirableIds` is an id-only projection) | **Chosen** |
| Add `ORDER BY` to `findAllByUserId` and reuse it | Keeps the N+1 and hydrates a full aggregate to drop most of it | Rejected |
| Same, but map with `List.of()` courses to skip the join | Faster, but hands the application a domain object that lies about its own state | Rejected |

The deciding argument is representation honesty, not measured latency — a typical
user holds a handful of checkout rows in a lifetime, so the N+1 is not today's
problem. Killing it is a free side effect. **No pagination**: the row count is
bounded by the unique-slot rule (at most one PENDING/ACTIVE at a time, so rows
accrue roughly one per subscription period) and an unpaginated contract can be
paginated later additively; the reverse is a breaking change.

`findAllByUserId` keeps its current callers and its current lack of ordering — it
is not touched.

### A4 — sealed result in `application`, flat record on the wire

`CurrentSubscriptionResult` is a sealed interface (`Active`, `ExpiringSoon` folded
into `Active` via its flag, `Expired`, `PendingPayment`), mirroring the module's
existing `CancellationTarget` / `PaymentTarget` sum types. State legality is then
enforced by the compiler exactly where the 100 % domain+application coverage floor
bites, and the use-case tests need no JSON.

The HTTP body is a single flat record discriminated by `status`:

| Option | Tradeoff | Verdict |
|---|---|---|
| Sealed application type + flat `CurrentSubscriptionResponse` | Matches both house conventions (sealed sum types in `application/dto`, flat records in `web/dto`); clients branch on one field | **Chosen** |
| Jackson-polymorphic sealed web DTO (`@JsonTypeInfo(EXISTING_PROPERTY, property = "status")`) | Truly state-shaped wire, but zero precedent anywhere in `api/billing` and new serialization machinery to test | Rejected |
| Flat DTO only, no sealed application type | Loses compile-time state legality precisely in the layer held to 100 % | Rejected |

Nullable components are a bounded, documented set — `endDate`, `daysRemaining`,
`expiringSoon`, `checkoutUrl` — not "everything nullable", and nulls serialize as
`null` (no `@JsonInclude`, matching `SubscriptionCheckoutResponse`'s explicit
"null, not absent" precedent; billing uses none today).

`plansUrl` (`/api/v1/billing/plans`) is emitted on the EXPIRED body and as a
ProblemDetail property on `NO_SUBSCRIPTION`, sourced from one constant in the web
layer — infrastructure is the only layer that legitimately knows its own routes.

### A5 — new exception clones the existing shape exactly

`NoSubscriptionException extends BusinessException` with
`ERROR_CODE = "NO_SUBSCRIPTION"`, no-arg constructor, English message — byte-for-byte
the `SubscriptionNotFoundException` shape. Its javadoc states the inverse of that
class's anti-enumeration note: here the caller reads their **own** dashboard, so
ambiguity buys nothing (D2). The handler branch mirrors `subscriptionNotFound`:
`ProblemDetails.body(...)` + one `setProperty`, 404.

## Data Flow

    GET /me ─→ SubscriptionController.currentOwn(Authentication)
                    │ actingUserId() = UUID.fromString(auth.getName())   [reused verbatim]
                    ↓
              GetCurrentSubscriptionUseCase
                    ├─ findCurrentByUserId ── PENDING ─→ PendingPayment(checkoutUrl)
                    │                        └ ACTIVE ──→ Active(endDate, daysRemaining, expiringSoon)
                    ├─ else findLatestExpiredByUserId ─→ Expired(endDate, plansUrl)
                    └─ else ───────────────────────────→ throw NoSubscriptionException
                    ↓                                            ↓
        CurrentSubscriptionResponse.from(...)         SubscriptionExceptionHandler
                    ↓                                     404 problem+json NO_SUBSCRIPTION
                200 application/json

    GET /me/history ─→ GetSubscriptionHistoryUseCase ─→ findHistoryByUserId (ORDER BY created_at DESC)
                                                                ↓
                                              List<SubscriptionHistoryItemResponse>  200 (possibly [])

An empty history is `200 []`, never `NO_SUBSCRIPTION` — the error contract belongs
to `/me` only.

## File Changes

| File | Action | Description |
|---|---|---|
| `domain/model/Subscription.java` | Modify | `EXPIRING_SOON_THRESHOLD_DAYS`, `daysRemaining(Instant)`, `isExpiringSoon(Instant)` (A2) |
| `domain/exception/NoSubscriptionException.java` | Create | `BusinessException`, code `NO_SUBSCRIPTION` (A5) |
| `application/port/out/SubscriptionRepository.java` | Modify | `+ findLatestExpiredByUserId(UUID)`, `+ findHistoryByUserId(UUID)` |
| `infrastructure/persistence/repository/SubscriptionJpaRepository.java` | Modify | `findFirstByUserIdAndStatusOrderByEndDateDesc`, `findAllByUserIdOrderByCreatedAtDesc` |
| `infrastructure/persistence/adapter/SubscriptionRepositoryAdapter.java` | Modify | Both methods, `readOnly = true`; history maps entity → record with no course lookup |
| `application/dto/SubscriptionHistoryEntry.java` | Create | Projection record: id, planId, status, startDate, endDate, createdAt |
| `application/dto/CurrentSubscriptionResult.java` | Create | Sealed: `Active`, `Expired`, `PendingPayment` (A4) |
| `application/port/in/GetCurrentSubscriptionUseCase.java` | Create | `CurrentSubscriptionResult current(UUID userId)` |
| `application/port/in/GetSubscriptionHistoryUseCase.java` | Create | `List<SubscriptionHistoryEntry> history(UUID userId)` |
| `application/usecase/GetCurrentSubscriptionUseCaseImpl.java` | Create | A1 resolution + `Clock` |
| `application/usecase/GetSubscriptionHistoryUseCaseImpl.java` | Create | Delegates to the projection port |
| `infrastructure/config/BillingConfiguration.java` | Modify | Two beans, unwrapped (read-only, like `listPlansUseCase`) |
| `infrastructure/web/dto/CurrentSubscriptionResponse.java` | Create | Flat record + `from(...)` switch over the sealed result |
| `infrastructure/web/dto/SubscriptionHistoryItemResponse.java` | Create | Flat record + `from(...)` |
| `infrastructure/web/controller/SubscriptionController.java` | Modify | `@GetMapping("/me")`, `@GetMapping("/me/history")` |
| `infrastructure/web/controller/SubscriptionExceptionHandler.java` | Modify | `NoSubscriptionException → 404 + plansUrl` |
| `api/app/.../config/SecurityConfig.java` | Verify | Both GETs must fall under `.authenticated()`; add explicit matchers only if the existing rule does not already cover them |

## Interfaces / Contracts

```java
// application/dto — state legality enforced by the compiler (A4)
public sealed interface CurrentSubscriptionResult {
    record Active(String subscriptionId, String planId, Instant startDate, Instant endDate,
                  long daysRemaining, boolean expiringSoon) implements CurrentSubscriptionResult {}
    record Expired(String subscriptionId, String planId, Instant startDate, Instant endDate)
        implements CurrentSubscriptionResult {}
    record PendingPayment(String subscriptionId, String planId, String checkoutUrl)
        implements CurrentSubscriptionResult {}
}
```

```jsonc
// GET /me — one discriminating field; the nullable set is bounded and documented
{"subscriptionId":"…","planId":"…","status":"ACTIVE","startDate":"…","endDate":"…",
 "daysRemaining":5,"expiringSoon":true,"checkoutUrl":null,"plansUrl":null}
{"…","status":"PENDING","startDate":null,"endDate":null,
 "daysRemaining":null,"expiringSoon":null,"checkoutUrl":"https://…","plansUrl":null}
{"…","status":"EXPIRED","endDate":"…","daysRemaining":null,"expiringSoon":null,
 "checkoutUrl":null,"plansUrl":"/api/v1/billing/plans"}
```

```jsonc
// 404 application/problem+json
{"type":"about:blank","title":"Not Found","status":404,
 "detail":"No tenés ninguna suscripción.","errorCode":"NO_SUBSCRIPTION",
 "plansUrl":"/api/v1/billing/plans"}
```

No `Cache-Control` is set by hand: nothing caches these routes today, and the
endpoints are authenticated, so the NFR is satisfied by asserting no caching
header is emitted rather than by adding one.

## Testing Strategy

| Layer | What to test | Approach |
|---|---|---|
| Domain (`SubscriptionTest`) | `daysRemaining` ceiling; absent while PENDING; `0` when `endDate` already passed; `expiringSoon` true at exactly 7 days (inclusive `<=`), false at 8, false for EXPIRED/PENDING | Plain JUnit, fixed `Instant`, no mocks |
| Domain (`NoSubscriptionExceptionTest`) | Carries `NO_SUBSCRIPTION`, distinct from `SUBSCRIPTION_NOT_FOUND` | Plain JUnit |
| Application (`GetCurrentSubscriptionUseCaseImplTest`) | Slot-first precedence (ACTIVE beats an older EXPIRED); PENDING → `PendingPayment` with `checkoutUrl` and no dates; EXPIRED fallback; neither → `NoSubscriptionException`; CANCELLED-with-remaining-access is **not** returned (D1) | Mockito on `SubscriptionRepository` + fixed `Clock` |
| Application (`GetSubscriptionHistoryUseCaseImplTest`) | Delegates; preserves repository order; empty list stays empty (no exception) | Mockito |
| Adapter (`SubscriptionRepositoryAdapterTest`) | `findLatestExpiredByUserId` picks the latest EXPIRED only; history ordering is `created_at DESC`; history issues no per-row course query | Existing adapter-test pattern |
| Web (`SubscriptionControllerTest`) | `actingUserId` is read from the token, never a param; both mappings; sealed → flat DTO mapping per state | Plain unit test, mocked use cases |
| Web (`SubscriptionExceptionHandlerTest`) | `NoSubscriptionException` → 404, `application/problem+json`, `errorCode`, `plansUrl` | Existing handler-test pattern |
| Integration (`SubscriptionStatusIntegrationTest`, Testcontainers) | The 6 issue scenarios end-to-end: active, expiring soon, expired, no subscription (404), history desc, pending payment; plus 401 unauthenticated and immediate reflection of a status flip after the expiry sweep | Mirrors `SubscriptionCancellationIntegrationTest` |
| Architecture | `BillingArchitectureTest` still green (domain gains no Spring/JPA import) | Existing ArchUnit run |

TDD order per slice: RED domain computation → RED use case → RED handler mapping →
RED integration, each before its implementation.

## Threat Matrix

Routing is the only listed boundary touched: two authenticated GET routes with no
path variable, no user-supplied identifier (the subject is always the token
subject), no shell, subprocess, VCS/PR automation, executable-file classification
or process integration. Covered by the 401 and token-subject tests above.
Remaining rows: N/A.

## Migration / Rollout

No migration, no schema change, no new index — `idx_billing_subscriptions_user_status
(user_id, status)` serves both new queries on its `user_id` prefix, and the
`ORDER BY created_at` sort runs over a per-user row count in the single digits.
Rollback is deletion of the new files plus the two controller methods, the two
repository methods and the handler branch; no persisted state depends on any of it.

## Dependency Order (for `sdd-tasks`, not a PR split)

1. `SubscriptionJpaRepository` + port + adapter (`findLatestExpiredByUserId`, `findHistoryByUserId`, `SubscriptionHistoryEntry`)
2. Domain: `daysRemaining` / `isExpiringSoon` / threshold, then `NoSubscriptionException`
3. Application: `CurrentSubscriptionResult`, the two in-ports, the two use-case impls
4. Wiring: `BillingConfiguration` beans
5. Web: the two response DTOs, then the two controller methods
6. `SubscriptionExceptionHandler` branch (+ `plansUrl` constant)
7. Integration tests for the 6 scenarios

Steps 1 and 2 are independent of each other; everything from 3 onward is strictly
sequential. The history path (1-history, 3-history, 5-history) is separable from
the `/me` path end-to-end and is the natural cut line if the work needs slicing.

## Open Questions

None. Both questions the proposal left open are answered above: A2 keeps the
7-day threshold hardcoded in the domain with an inclusive boundary, and A3 uses a
dedicated projection with no pagination.

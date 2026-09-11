# Billing API Integration Specification

## Purpose

Give the BFF an outbound port and WebClient adapter for the upstream billing
plans endpoint, mirroring the existing `AuthApiClient`/`AuthApiAdapter` and
`VirtualApiClient`/`VirtualApiAdapter` pattern exactly: a dedicated properties
class, a qualified `WebClient` bean, and status-to-typed-exception mapping —
extended here with the first BFF-side `429` mapping. A short-lived in-memory
cache sits in front of the upstream call so that visitor traffic never
translates 1:1 into upstream requests.

## Requirements

### Requirement: Dedicated outbound port and adapter for the plans call

The BFF MUST define an outbound port covering plans-list retrieval
(`GET /api/v1/billing/plans`), implemented by a dedicated WebClient adapter
bound to its own `BillingApiProperties` base-url configuration and its own
`@Qualifier`-annotated `WebClient` bean in `WebClientConfig`, mirroring the
Auth/Virtual precedent. The adapter's constructor MUST be explicit (not
Lombok `@RequiredArgsConstructor`) so the `@Qualifier` on the injected
`WebClient` parameter is honored.

#### Scenario: Adapter is wired to its own qualified WebClient bean

- GIVEN `WebClientConfig` defines a `billingApiWebClient` bean qualified for
  the billing base URL
- WHEN the billing adapter is constructed
- THEN it receives that qualified bean via an explicit constructor parameter,
  not a Lombok-generated one

### Requirement: The plans call never carries an Authorization header

The plans endpoint is public upstream (`@PublicBillingEndpoint`, permitAll in
`SecurityConfig`) and its response is caller-agnostic: every visitor sees the
same plans at the same prices. The port method therefore MUST NOT accept an
access-token parameter at all, and the outbound request MUST NOT include an
`Authorization` header under any circumstance, authenticated visitor or not.

This mirrors the catalog call's existing rule in `virtual-api-integration`
and rests on the same reasoning: sending a token to an endpoint that ignores
it changes nothing in the response and widens token exposure for no gain.
Making the port method take no token enforces this structurally rather than
by convention — there is no parameter an implementer could accidentally
thread through.

Note this is also what makes the shared cache (below) correct: a
caller-agnostic response is the same for every visitor, so one cached copy
serves all of them without leaking one visitor's data to another.

#### Scenario: Authenticated visitor's plans call is still unauthenticated

- GIVEN an authenticated visitor with a valid access token in session
- WHEN the BFF calls the upstream plans endpoint on their behalf
- THEN the outbound request carries no `Authorization` header

### Requirement: Upstream statuses map to typed exceptions, including 429

The adapter MUST map `404` to a not-found exception, `429` to a
rate-limited exception, and `503` to an upstream-unavailable exception, for
the plans call. None of these mappings MUST expose the raw RFC 9457
problem-detail body to callers. The `429` mapping is new: no existing BFF
adapter maps this status today.

#### Scenario: Adapter maps 404/429/503 to typed exceptions

- GIVEN the upstream plans endpoint returns `404`, `429`, or `503`
- WHEN the adapter processes the response
- THEN it raises the matching typed exception (not-found, rate-limited, or
  upstream-unavailable) without exposing the raw problem-detail body

### Requirement: Plans response is cached for 5 minutes

The adapter (or the use case immediately in front of it) MUST cache the
plans-list response in memory for 5 minutes (D4) after a successful upstream
call, so that repeated visitor requests within that window are served from
the cache instead of each triggering a new upstream call. This removes the
risk that visitor traffic volume, rather than the BFF's own low call volume,
determines how close the BFF gets to billing's rate limit (D1) — billing's
limiter keys on raw `remoteAddr` with no `X-Forwarded-For` handling, so every
server-to-server call from the BFF is indistinguishable from every other to
billing's limiter.

A failed upstream call (404/429/503) MUST NOT populate or extend the cache;
only a successful response is cached.

#### Scenario: Repeated requests within the TTL trigger exactly one upstream call

- GIVEN the cache is empty
- WHEN two plans-page requests arrive within 5 minutes of each other
- THEN only one outbound call is made to the upstream plans endpoint, and
  both requests are served the same cached data

#### Scenario: A request after the TTL expires triggers a fresh upstream call

- GIVEN the cache holds a plans response older than 5 minutes
- WHEN a new plans-page request arrives
- THEN the adapter makes a new outbound call to the upstream plans endpoint
  and replaces the cached value on success

A failed upstream call MUST NOT be answered from an expired cache entry
either: once an entry is past its TTL it is never served, whether the refresh
succeeds or fails. Billing downtime therefore always surfaces as the shared
error view (D6), never as silently-old prices — a warm process must not turn
D6's answer into the exception rather than the rule, and there is no upper
bound on how old a served-on-failure entry could become during a long outage.

#### Scenario: A failed upstream call does not populate the cache

- GIVEN the cache is empty
- WHEN the upstream plans call returns `404`, `429`, or `503`
- THEN the adapter raises the matching typed exception and the cache remains
  empty, so the next request retries the upstream call rather than serving a
  failure result from cache

#### Scenario: An expired entry is not served when the refresh fails

- GIVEN the cache holds a plans response older than its TTL
- WHEN a new request triggers a refresh and the upstream call returns `404`,
  `429`, or `503`
- THEN the adapter raises the matching typed exception rather than returning
  the expired entry
- AND the expired entry is left untouched rather than being re-dated, so the
  next request attempts a fresh upstream call again

# Exploration: Billing plans rate-limit fingerprint ignores X-Forwarded-For

**Change**: `billing-plans-rate-limit-fingerprint`
**Issue**: [#195](https://github.com/urrestarazu-org/menta-dance/issues/195) — "El fingerprint de rate limit de billing ignora X-Forwarded-For"
**Date**: 2026-09-30
**Phase**: explore (read-only investigation; no implementation)

## Current state

### Billing's `ClientFingerprint`

`api/billing/src/main/java/com/menta/billing/infrastructure/web/controller/ClientFingerprint.java` hashes
`request.getRemoteAddr()` raw — confirmed, matches the issue exactly. Its own javadoc already names the
gap: *"Deliberately simpler than auth's `ClientFingerprint`... If billing ever sits behind the same
reverse-proxy setup auth does, revisit this rather than silently under-counting by IP."*

**That premise is already stale.** `infra/docker/nginx/conf/conf.d/locations.conf` proxies
`/api/v1/billing/` (`limit_req zone=billing burst=10`) through the identical nginx setup as
`/api/v1/auth/` — same `proxy_params.conf`, same `X-Real-IP`/`X-Forwarded-For` headers. Billing is
already behind the reverse-proxy setup the javadoc treats as hypothetical.

### Auth's `ClientFingerprint` (the precedent)

Only trusts `X-Forwarded-For` when the immediate TCP peer (`remoteAddr`, unspoofable) matches a
configured trusted-proxy CIDR list (`auth.trusted-proxy-cidrs`, default `172.16.0.0/12`, flagged in
**ADR-0035** as a dev-only convenience needing per-environment narrowing), and then takes the
**last** hop in the header (the one nginx itself appended), never the first. Three unit tests cover
this, including an explicit "ignores spoofed header from untrusted peer" case. Skipping the CIDR
gate would let any direct caller inject `X-Forwarded-For: <anything>` and get a fresh bucket per
request — a classic forwarded-header spoof (CWE-290) that fully defeats the limiter.

**ADR-0035** (`docs/adr/0035-trusted-client-origin-propagation.md`, Accepted & Implemented
2026-08-15) already solved this exact three-layer problem (Nginx → BFF → API) for auth's login
path. Its pattern: Nginx sanitizes/builds the headers (already true for billing too), the BFF
captures the canonical origin and re-emits it as a single-value `X-Forwarded-For` downstream, and
the downstream service trusts it only from a CIDR-gated peer. This is the reusable precedent if
billing ever needs the full fix.

### The gap the issue doesn't name: the BFF call bypasses nginx entirely

`BillingApiAdapter.fetchPlans()` (`bff/src/main/java/com/menta/bff/infrastructure/adapter/BillingApiAdapter.java:98-106`)
calls `billingApiWebClient` (`WebClientConfig.java:51-56`) with **zero headers** — a bare
`webClient.get().uri(PLANS_ENDPOINT).exchange()`, container-to-container, never through nginx.
**Aligning billing's fingerprint with auth's alone would not fix the scenario the issue
describes** — it would additionally require BFF-side origin propagation (mirroring ADR-0035's
role for `AuthApiAdapter`), a two-module change, not a single-file mirror.

### The actual exposure today is already bounded

`ClientFingerprint`/`RedisBillingPlansRateLimitPort` is single-purpose: used only by
`PlanController` (`GET /api/v1/billing/plans` and `/{planId}`), 60 req/min, fail-closed. Issue #177
(BFF plans view) is **shipped and archived**
(`openspec/changes/archive/2026-09-11-bff-plans-view/`) — `BillingApiAdapter` holds a single-entry,
single-flight, 5-minute-TTL cache with no stale-on-failure, so the BFF calls billing's plans
endpoint **at most once per 5 minutes**, nowhere near the 60/min budget even fully collapsed.
Android calls the endpoint directly through nginx with its own real per-device IP — not a
collapsing scenario. No other billing-plans caller exists in the codebase today.

## The real shape of the decision

1. **Align fingerprint with auth's model** — only closes the gap for direct nginx clients (already
   fine); the BFF scenario needs a second piece (BFF-side propagation) not covered by a
   single-file mirror.
2. **Drop IP as the rate-limit dimension** — matches the stated anti-scraping intent better than a
   per-IP budget an existing cache already renders mostly moot, but needs its own design decision
   (what replaces it) rather than mechanical reuse.
3. **Leave as-is, document the decision** — the only known live caller is already bounded by its
   5-minute cache; correct the now-stale javadoc regardless; revisit if a second, non-caching
   server-side caller is ever built.

## Recommendation

Approach 3. No live caller is harmed today (cache-TTL and topology evidence above). Correct the
javadoc's now-inaccurate "if billing ever sits behind the same reverse-proxy setup" framing, and
record the decision. Revisit with the ADR-0035 two-piece pattern only if/when a second,
non-caching server-side caller is actually built.

## SDD depth

This is a single-decision issue per its own "Hecho cuando" ("la decisión está tomada y
documentada"), not a feature build. No capability changes, no spec/design/tasks phases warranted —
a proposal documenting the decision plus a direct one-file fix is proportionate.

## Key Learnings

1. Billing's `ClientFingerprint` javadoc already names X-Forwarded-For/trusted-proxy handling as
   its own gap, but its stated trigger condition (billing behind a reverse proxy) is already true
   today, making the javadoc itself stale.
2. Auth's `ClientFingerprint` only trusts `X-Forwarded-For` from a CIDR-gated peer, defended by an
   explicit spoofing-ignored test — the precedent this decision would follow if ever implemented.
3. ADR-0035 already solved this exact three-layer (Nginx/BFF/API) problem for auth's login path
   and is the reusable pattern if billing's decision is ever revisited.
4. The BFF's call to billing's plans endpoint bypasses nginx entirely via a headerless WebClient,
   so fixing billing's fingerprint alone would not close the scenario the issue describes.
5. Issue #177's BFF plans view is already shipped with a 5-minute single-flight cache, keeping the
   BFF's upstream call volume far below the 60/min budget regardless of fingerprint scheme.

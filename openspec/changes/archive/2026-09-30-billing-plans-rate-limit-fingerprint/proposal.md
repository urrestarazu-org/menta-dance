# Proposal: Document the billing plans rate-limit fingerprint decision

**Issue**: #195 ("El fingerprint de rate limit de billing ignora X-Forwarded-For") · **Input**: `openspec/changes/billing-plans-rate-limit-fingerprint/exploration.md`

## Intent

Issue #195 is a decision issue, not a feature request — its own "Hecho cuando" is satisfied by the
decision being made and documented, whichever way it goes. The investigation found:

- Billing's `ClientFingerprint` hashes the raw peer IP with no `X-Forwarded-For` handling, and its
  own javadoc already names this as a known simplification, conditioned on billing never sitting
  behind the same reverse-proxy setup as auth — a condition already true today (nginx proxies both
  identically).
- But the one real server-side caller in the codebase, the BFF (`BillingApiAdapter`), calls billing
  through a headerless `WebClient` that never goes through nginx and is protected by its own
  5-minute single-flight cache (#177, shipped) — at most 1 upstream call per 5 minutes, nowhere
  near the 60 req/min budget even fully collapsed under one fingerprint.
- Aligning the fingerprint with auth's trusted-proxy model alone would **not** fix the scenario the
  issue describes for a hypothetical future non-caching caller — that needs a second piece (BFF-side
  origin propagation, mirroring ADR-0035) not built today.

No live caller is harmed by the current behavior. This proposal documents that decision rather than
building unused machinery against a risk that is not active in production.

## Scope

### In Scope

- Correct `ClientFingerprint`'s javadoc: its "if billing ever sits behind the same reverse-proxy
  setup" framing is stale — billing already does. Replace with an accurate statement of the actual
  live exposure and the decision below.
- Record the decision (D1) so it isn't silently re-litigated or mistaken for an oversight.

### Out of Scope

- No change to `ClientFingerprint`'s actual behavior — it keeps hashing the raw peer IP.
- No trusted-proxy CIDR list, no BFF-side origin propagation (ADR-0035's pattern) — deferred until
  a real non-caching server-side caller exists.
- No change to the rate-limit dimension (still per-IP) or its budget (60 req/min).
- No change to #177's BFF caching behavior.

## Settled decisions

| # | Topic | Decision |
|---|---|---|
| D1 | **Leave the fingerprint as IP-only; document why** | Billing's `ClientFingerprint` keeps hashing the raw peer IP, with no `X-Forwarded-For`/trusted-proxy handling. The only known caller that could otherwise collapse (the BFF) is already bounded by #177's 5-minute single-flight cache — at most 1 upstream call per 5 minutes, far under the 60/min budget regardless of fingerprint scheme. Aligning with auth's model (trusted-proxy CIDR + last-hop `X-Forwarded-For`) would additionally require BFF-side origin propagation to actually close the gap for a non-caching caller — a real two-module change not justified by a risk with no live instance today. Revisit if a second, non-caching, server-side caller of billing's public endpoints is ever built — at that point, ADR-0035's pattern (Nginx sanitizes → caller propagates canonical origin → API trusts only a CIDR-gated peer) is the precedent to follow. **Resolved with the user — not to be re-litigated absent a new caller.** |

## Approach

Correct the javadoc on `ClientFingerprint` to state the real, current exposure and the decision,
replacing the stale "if billing ever sits behind a reverse-proxy" conditional (already false) with
the actual reasoning: the endpoint is already behind the same proxy as auth, the one real caller is
cache-bounded, and the fix is deferred pending a caller that would actually need it.

## Affected Areas

| Area | Impact | Description |
|------|--------|--------------|
| `api/billing/.../infrastructure/web/controller/ClientFingerprint.java` | Modified | Javadoc only — no behavior change |
| `docs/adr/` | Unchanged | ADR-0035 remains the precedent to follow if this is ever revisited; no new ADR needed for a decision to defer |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| A future non-caching server-side caller (e.g. a new gateway, a second BFF) reintroduces the collapsing risk | Low today | The javadoc explicitly states the trigger condition for revisiting (a non-caching caller) and names ADR-0035's pattern as the fix, so it won't need rediscovery |
| Reader mistakes "documented, deferred" for "the issue was ignored" | Low | The corrected javadoc, this proposal, and the closing issue comment all state the reasoning and the revisit condition explicitly |

## Rollback Plan

Javadoc-only change; reverting the commit fully restores prior text with zero functional impact
either way, since no behavior changes in either direction.

## Dependencies

- **ADR-0035** (`docs/adr/0035-trusted-client-origin-propagation.md`) — the precedent to follow
  if/when this decision is revisited. Not implemented here.
- **#177** (`bff-plans-view`, archived) — its 5-minute single-flight cache is the load-bearing fact
  that makes D1 safe today.

## Success Criteria

- [x] The decision is made and documented (issue's own "Hecho cuando").
- [x] `ClientFingerprint`'s javadoc no longer states a false premise ("if billing ever sits behind
      a reverse-proxy setup") about its own deployment topology.
- [x] The revisit condition (a non-caching server-side caller) and the fix precedent (ADR-0035) are
      both stated explicitly, not left implicit.
- [x] Issue #195 closed with the decision and rationale recorded in the closing comment.

# Archive Report: billing-plans-rate-limit-fingerprint

**Change**: `billing-plans-rate-limit-fingerprint` (#195, "El fingerprint de rate limit de billing ignora X-Forwarded-For")

**Archived on**: 2026-09-30

**Status**: COMPLETE

## Cycle Summary

This was a single-decision issue, not a feature build — its own "Hecho cuando" was satisfied by the
decision being made and documented. Full spec/design/tasks phases were judged disproportionate
during exploration and skipped; this change went directly from explore → propose → a one-file
javadoc fix.

## Decision (D1)

Billing's `ClientFingerprint` keeps hashing the raw peer IP, with no `X-Forwarded-For`/trusted-proxy
handling. Investigation found the one real server-side caller (the BFF, via `BillingApiAdapter`)
already bounded by issue #177's shipped 5-minute single-flight cache — at most one upstream call
per 5 minutes, far under the 60 req/min budget regardless of fingerprint scheme. Aligning with
auth's trusted-proxy model would not even close the gap on its own, since the BFF's call to billing
bypasses nginx entirely today (a headerless `WebClient`) — the real fix would need a second,
BFF-side piece (ADR-0035's pattern) not justified by a risk with no live instance.

No behavior changed. `ClientFingerprint`'s javadoc was corrected — it previously stated a false
premise ("if billing ever sits behind the same reverse-proxy setup auth does"), when billing
already does. The corrected javadoc records the real exposure, the decision, and the explicit
revisit condition (a second, non-caching server-side caller), naming auth's `ClientFingerprint` /
ADR-0035 as the precedent to follow if that day comes.

## Artifacts

- `openspec/changes/archive/2026-09-30-billing-plans-rate-limit-fingerprint/exploration.md`
- `openspec/changes/archive/2026-09-30-billing-plans-rate-limit-fingerprint/proposal.md`
- Engram `sdd/billing-plans-rate-limit-fingerprint/explore` (observation id 1664)

## Code Changed

- `api/billing/src/main/java/com/menta/billing/infrastructure/web/controller/ClientFingerprint.java` — javadoc only, no behavior change.

## Verification

- `./gradlew :api:billing:test --rerun-tasks` → BUILD SUCCESSFUL, full suite green.
- No spec, no design, no tasks artifacts — none applicable for a javadoc-only decision record.

## Dependencies Noted, Not Implemented

- ADR-0035 (`docs/adr/0035-trusted-client-origin-propagation.md`) remains the precedent for a future
  fix, if a second, non-caching server-side caller of billing's public endpoints is ever built.

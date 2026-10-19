# Proposal: Physical Course Detail with Session Availability in the Public Catalog

## Intent

`GET /api/v1/catalog/courses/{id}` is virtual-only since #47: a PHYSICAL id answers 404, so prospects cannot see upcoming classes or free spots (#107). Create the physical detail with live session availability. Separately, the physical session query uses an inclusive `BETWEEN`, violating the documented half-open `[from, to)` contract billing relies on.

## Scope

### In Scope (decisions D1-D7)
- Physical detail: PHYSICAL id -> 200 with course data + detail-only `sessions` (virtual-first resolution; virtual path unchanged).
- Window `[now, now + 30d)`, length configurable by property; started sessions excluded; ascending; cap 100.
- Session fields: `sessionId`, `scheduledAt` (ISO-8601 UTC), `capacity`, `availableSpots`. Sold-out shown; cancelled excluded.
- Sessions lookup failure -> 503 `CATALOG_DEGRADED` + `Retry-After: 30`.
- Fix `BETWEEN` -> `>= :from AND < :to` (own slice, integration test).
- Invert the 3 tests pinning 404; delete dead `getCourse()`.
- Sync `catalog-v1.yaml` (`oneOf`), Bruno, `docs/07-CATALOG-API.md`, `US-PHYSICAL-003`.

### Out of Scope (D8)
- List endpoint; prices/`quoteEndpoint`; `from`/`to` params; pagination; BFF/Android.
- Catalog cache and rate limit -> #312.
- Pre-existing UTC-vs-academy-zone session creation.

## Capabilities

### New Capabilities
- `catalog-course-detail`: public detail contract (virtual/physical resolution, physical sessions window, cap, public fields, 404/503 semantics).
- `physical-session-availability`: scheduled-session availability read is half-open `[from, to)`, SCHEDULED-only, ascending, `availableSpots = max(0, capacity - assigned - activeHolds)`.

### Modified Capabilities
- None (`physical-capacity-hold` holds stay invisible; only `availableSpots` is published).

## Approach

Exploration approach A. `getCourseDetail` returns a sealed detail type: virtual lookup first; on miss `findActiveById`, then `listSessions(now, now + window)` through `callPort()`, truncated to 100. New physical detail record + detail-only block; injected `Clock`; window property. No new cross-module edge: existing `api:shared` port only.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/app/.../catalog/*` | Modified | Composition, controller, sealed detail, new records, property, clock |
| `api/physical/.../PhysicalSessionJpaRepository.java` | Modified | Half-open bound |
| `api/openapi/catalog-v1.yaml` | Modified | Detail `oneOf` (virtual + physical) |
| `bruno/API - Direct/catalog/*.bru` | Modified | Physical detail example |
| `docs/07-CATALOG-API.md`, `docs/user-stories/US-PHYSICAL-003.md` | Modified | Fields, window, no from/to |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| BFF receives physical body instead of 404 | Low | Accepted; BFF only requests VIRTUAL ids |
| Bound fix changes billing at the `to` edge | Low | Integration test; matches documented contract |
| Physical gates 95/90 | Low | Test-only additions |
| `oneOf` breaks tooling | Med | Validate spec |
| Unthrottled live-availability reads | Med | Window + cap; #312 |

## Rollback Plan

Revert slice PRs in reverse order (S3, S2, S1). No schema or data change.

## Dependencies

- None. #312 is follow-up, not prerequisite.

## Delivery Outline (stacked onto `develop`, budget 800)

| Slice | Content | Forecast |
|-------|---------|----------|
| S1 | `BETWEEN` fix + physical integration test | ~60 |
| S2 | Composition, sealed detail, records, property, clock, controller, `getCourse` removal, unit tests (2 inverted) | ~380 |
| S3 | Catalog integration test (1 inverted), OpenAPI, Bruno, docs, US | ~250 |

## Success Criteria

- [ ] PHYSICAL id -> 200 with at most 100 upcoming sessions in `[now, now + 30d)`.
- [ ] Session exactly at `to` excluded (integration test).
- [ ] Virtual detail and list responses unchanged.
- [ ] Sessions failure -> 503 `CATALOG_DEGRADED`.
- [ ] `assignedSpots` / `activeCapacityHolds` never serialized.
- [ ] `./gradlew check` green; OpenAPI, Bruno, docs aligned.

## Proposal Question Round

Covered by the business round (D1-D8, Engram `sdd/catalog-physical-sessions-availability/decisions`).

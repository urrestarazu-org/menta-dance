# Proposal: Physical Assignment Type (#521)

## Intent

`physical_capacity_assignments` does not record whether a seat came from a MONTHLY or an INDIVIDUAL purchase, so `GET` my-sessions cannot tell the student why they hold a class. Persist the type at write time and expose it, always non-null.

## Scope

### In Scope
- Neutral `AssignmentType {MONTHLY, INDIVIDUAL}` in `:api:shared`.
- V26 migration: `assignment_type VARCHAR NOT NULL DEFAULT 'MONTHLY'` (header labels the legacy assumption; V0, no real data), then drop the default; rollback script in `db/rollback/` plus migration IT. No check constraint (string-mapped enum).
- Type carried by both commands, `PhysicalCapacityHoldPort.convertAll(paymentId, studentId, type)`, `ConvertCapacityHoldUseCase`, and `PhysicalCapacityAssignmentWriter`.
- api:app handler resolves the quote BEFORE `convertAll` and maps `PurchaseType` to `AssignmentType`.
- Read side: projection to `MySessionsResponse.Item.assignmentType`.
- New `Reason.QUOTE_NOT_FOUND` in `billing` (domain enum, javadoc, `docs/06-BILLING-API.md` reason list, exception-mail mapping coverage) so a missing quote is distinguishable from `TARGET_NOT_SCHEDULED`. User decision 2026-10-15: keep failure cases separate even if unlikely.
- Demo seed passes INDIVIDUAL; OpenAPI, Bruno, `docs/05-PHYSICAL-API.md` (lines 44-45) synced.

### Out of Scope
- bff `AssignedSession` consumer (additive field; follow-up issue).
- Persisting type on hold rows or changing billing's checkout/hold model (Option B rejected). Billing change is limited to the new `Reason` value.
- Inferring type from claim count; backfilling real data.

## Capabilities

### New Capabilities
- `physical-assignment-type`: every assignment stores an explicit type; my-sessions returns it non-null.

### Modified Capabilities
- `physical-capacity-hold`: conversion writes assignments with the caller-resolved type; idempotency unchanged.

## Approach

Option A. The handler loads the quote once after resolving the Physical target. Proposal decisions:
- **Quote missing before `convertAll`**: route to a dedicated `QUOTE_NOT_FOUND` EXCEPTION so support can tell it apart from "no scheduled sessions". Order (user decision 2026-10-15): create the purchase first via `createPurchaseFromPaymentEvent(payload, List.of())`, then publish the failure ONLY if the resulting purchase is `EXCEPTION`. An already-`ASSIGNED` purchase is returned untouched and nothing is published. No port change is needed (the port already returns the `Purchase`). Held rows stay unconverted and expire through TTL. Assumption: quotes are never deleted.
- **Redelivery**: `AlreadyConverted` stays a no-op. Type is ignored for converted rows.
- **Legacy `HoldNotFound` path** reuses the already-loaded quote. Outcomes are unchanged EXCEPT one user-approved change: when no hold exists AND the quote is missing, the reason changes from `TARGET_NOT_SCHEDULED` to `QUOTE_NOT_FOUND`. Existing handler tests asserting the old reason and the handler javadoc for this branch are updated.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `api/shared` | New | `AssignmentType` |
| `api/physical` (usecase, ports, JPA adapter, projection, controller DTO) | Modified | type write/read |
| `api/app/.../PhysicalCapacityAssignmentOutboxEventHandler.java` | Modified | quote lookup moved before `convertAll`; create-then-publish on missing quote; javadoc + tests updated |
| `api/billing/.../domain/model/Reason.java`, `docs/06-BILLING-API.md` | Modified | `QUOTE_NOT_FOUND` |
| `api/app/src/main/resources/db/migration`, `db/rollback` | New | V26 + rollback |
| OpenAPI, `bruno/`, `docs/05-PHYSICAL-API.md` | Modified | contract sync |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Redelivery with the quote missing emits a spurious failure for an ASSIGNED purchase | Resolved by design | Create-then-publish: the failure is published only when the resulting purchase is `EXCEPTION`; covered by handler tests |
| Legacy MONTHLY default mislabels pre-existing INDIVIDUAL rows | Low | V0, no real data; header documents it |
| Forecast of 400-550 lines exceeds the 400-line budget | Med | Single PR accepted by the user |

## Rollback Plan

Revert the PR. Apply `db/rollback/` script (drops `assignment_type`) operator-side. The migration IT proves the script.

## Dependencies

- None.

## Success Criteria

- [ ] Every new assignment row has an explicit type; inserts without a type fail.
- [ ] Held MONTHLY/INDIVIDUAL purchases persist the matching type.
- [ ] my-sessions returns `assignmentType` non-null; docs/OpenAPI/Bruno match.
- [ ] Redelivery of a converted hold with the quote missing emits no event and no mail; the purchase stays ASSIGNED.
- [ ] First occurrence of a missing quote yields an EXCEPTION purchase and exactly one `QUOTE_NOT_FOUND` failure event.
- [ ] HoldNotFound path behaves as today, except missing quote now reports `QUOTE_NOT_FOUND`.

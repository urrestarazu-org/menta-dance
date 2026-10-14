# Archive Report: billing-purchase-fulfillment-status (#559)

**Archived**: 2026-10-14
**Archive path**: `openspec/changes/archive/2026-10-14-billing-purchase-fulfillment-status/`
**Artifact store**: hybrid (OpenSpec + Engram)
**Status at close**: closed. Both slices merged to `develop`. One non-blocking manual follow-up (F.1) remains open by design.

## Delivered Slices

| Slice | PR | Merge commit | Scope |
|-------|----|--------------|-------|
| S1 API | #578 | 366b150 | Nullable `fulfillment` on the owner-checked `GET /api/v1/billing/payments/{paymentId}`, OpenAPI path and schema, `docs/06-BILLING-API.md`, tests |
| S2 BFF + openspec artifacts | #579 | 31d4ddc | BFF `PurchaseFulfillment` / `PaymentStatusView`, class return page branching on EXCEPTION and RESOLVED |

Delivery strategy as executed: two independent PRs, both against `develop`. The `stacked-to-main` chain planned in `tasks.md` was not used.

## Artifact Observation IDs (Engram, project `menta-dance`)

| Artifact | Engram topic key | Observation ID |
|----------|------------------|----------------|
| Proposal | `sdd/billing-purchase-fulfillment-status/proposal` | 1771 |
| Spec (delta) | `sdd/billing-purchase-fulfillment-status/spec` | 1773 |
| Design | `sdd/billing-purchase-fulfillment-status/design` | 1774 |
| Tasks | `sdd/billing-purchase-fulfillment-status/tasks` | 1775 |
| Verify report, S1 (API) | `sdd/billing-purchase-fulfillment-status/verify-report-s1` | 1777 |
| Verify report, S2 (BFF) | `sdd/billing-purchase-fulfillment-status/verify-report-s2` | 1778 |

There is no single `verify-report` topic key. Each slice has its own, so both are recorded here. Engram observations are intermediate snapshots. Where a later source changed a fact, the report follows the final-state ranking (native review authority, then tasks artifact, then launch prompt, then snapshots).

## Spec Merges

Both delta specs describe new capabilities. Neither had a main spec, so each was copied mechanically (`cp`) to the main specs. No existing main spec was modified or removed.

| Domain | Action | Requirements added |
|--------|--------|--------------------|
| `billing-purchase-fulfillment-status` | Created `openspec/specs/billing-purchase-fulfillment-status/spec.md` | 6 |
| `bff-class-purchase-return` | Created `openspec/specs/bff-class-purchase-return/spec.md` | 6 |

Requirement counts are the `### Requirement:` headings in each source spec.

## Task Reconciliation

- Checkboxes were checked against the tracked `tasks.md` before archive. Tasks 0.1, 1.1 to 1.11, and 2.1 to 2.9 are already `[x]`. No stale-checkbox reconciliation was needed.
- Only F.1 is unchecked. It sits under "Follow-up (non-blocking)", not under implementation tasks. The orchestrator explicitly instructed that it stay unchecked. It is an open manual validation follow-up: drive a purchase to EXCEPTION on the local stack (API 8081, BFF 8080), check the title, body, absence of sessions and contact link, then repeat for RESOLVED.
- `tasks.md` gained an "Archive note" under the Review Workload Forecast. It records that the planned `stacked-to-main` chain was replaced by two independent PRs against `develop`. The forecast table itself is unchanged as the historical plan.

## Design Note Added

`design.md` gained a section "Implementation Deviations (recorded at archive)". It documents that `PurchaseRepositoryAdapter.findByPaymentId` maps through `toDomainWithoutLocking`, a non-locking session read. The stated reason is that MySQL rejects `FOR UPDATE` inside the READ ONLY transaction of the student status read. The section also notes that the read is safe under REPEATABLE READ and that the deviation was not in the design file list. Source: `verify-report-s1` W3.

Code check: `PurchaseRepositoryAdapter.toDomainWithoutLocking` carries the same rationale in its javadoc. The adapter's class javadoc also documents a second, separate reason for the plain `findByPaymentId` read: it is the pre-insert existence check in `CreatePurchaseFromPaymentEventUseCase`, and locking it would self-block the insert through an InnoDB gap lock. That second reason is not in the new design note, which covers only the read-only-transaction reason.

## Verification Status at Close

### S1 (API), verify-report-s1, observation 1777

Verdict at verification time: `pass_with_warnings`. 0 CRITICAL, 3 WARNING, 3 SUGGESTION. Snapshot evidence: billing 964 tests, app 517, 0 failures or skips, checkstyle and JaCoCo green. Evidence revision was taken against `@5c3954b`.

| Item | Status at close |
|------|-----------------|
| W1 integration test covers only null and EXCEPTION (no non-empty session list on real MySQL) | **Fixed** before merge of #578: an integration test with an ASSIGNED purchase carrying sessions was added in `PresentialPurchaseExceptionPathIntegrationTest`; it fails with the MySQL read-only error if the non-locking read is reverted. |
| W2 RESOLVED with note no-leak verified only structurally and at HTTP level with a mocked use case | Accepted, not fixed: the no-leak guarantee is covered at DTO level (the response type has no reason/resolution/note fields). Recorded as a known gap. |
| W3 non-locking read deviation outside design.md | Resolved by this archive: documented in `design.md`, "Implementation Deviations". |
| S1 contract test does not assert the 200 `$ref` or the `status` enum | Suggestion, not addressed in the evidence available. |
| S2 subscription-target case in `GetPaymentUseCaseImplTest` | Suggestion, not addressed in the evidence available. |
| S3 integration assertion with PENDING/ASSIGNED purchase carrying sessions (covers W1) | Done via the W1 fix above. |

### S2 (BFF), verify-report-s2, observation 1778

Verdict at verification time: `pass_with_warnings`. 0 CRITICAL, 3 WARNING, 2 SUGGESTION. Snapshot evidence: 821 tests, 0 failures, checkstyle and JaCoCo green on rerun, 791 of 800 lines covered.

| Item | Status at close |
|------|-----------------|
| W1 structurally malformed `fulfillment` (JSON object or array) not degraded to null, which would fail the whole page | **Fixed** in commit "fix(bff): degradar a null un fulfillment que no es un string y probar sesiones asignadas (#559)", included in #579. Code check: `PhysicalPurchaseApiAdapter` passes `fulfillment` as text only when `isTextual()`, otherwise null. `PurchaseFulfillment.fromUpstream` maps null and unknown values to null. |
| W2 ASSIGNED test asserted only an empty session list | **Fixed** in the same commit (test "probar sesiones asignadas"). Based on the commit message; the test body was not re-inspected for this report. |
| W3 one flaky `AuthApiAdapterLoginFailureTest` run, unrelated to this change | Not fixed here. Unrelated to this change; recorded as an observed flake. |
| S1, S2 suggestions | Not addressed in the evidence available. |

## Final Numbers

Test counts and line coverage above are snapshot values from the verify reports, not final-state values. No later source in the evidence set restates them, so they are reported as at verification time.

## Known Gaps and Open Items

1. F.1 manual validation on the local stack is open (non-blocking, see Task Reconciliation).
2. S1 W2: no serialized-body no-leak test for a RESOLVED purchase with a note (accepted gap; DTO-level only).
3. S1 suggestions S1 and S2 were not addressed.
4. S2 W3 is a flaky test in an unrelated area (`AuthApiAdapterLoginFailureTest`).

## Mechanical Readback

- Spec copies: `diff -r` empty for both new main specs.
- Folder move: `diff -r` between the pre-move snapshot and the archive folder is empty. The source directory is gone from `openspec/changes/`.
- This report was written after the readback and is additive. It is excluded from the comparison.

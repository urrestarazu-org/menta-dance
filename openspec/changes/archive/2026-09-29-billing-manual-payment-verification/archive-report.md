# Archive Report: billing-manual-payment-verification

**Change**: `billing-manual-payment-verification` (#33, US-BILLING-005, "Verificación de pagos manuales (Admin)")

**Archived on**: 2026-09-29

**Status**: COMPLETE

## Cycle Summary

This change delivered the operational surface around an existing payment approval/rejection decision: a paginated admin inbox for pending verification, a time-limited signed proof URL with a token-authenticated file endpoint, an audited exceptional correction out of `RECONCILIATION_REQUIRED`, a queryable audit log, and buyer-facing Spanish decision emails. Five chained PRs (#277–#281) merged to `develop` @ commit `694c42f`.

- **All 57 implementation tasks** marked complete across P1–P5
- **All 6 `billing-manual-payment-verification` requirements** verified PASS
- **All 2 `bank-transfer-subscription` delta requirements** verified PASS
- **Coverage gates** green: 95%/90% (billing), 100%/85% (auth)
- **Verification verdict**: PASS (0 CRITICAL, 2 WARNING documentation-hygiene, 1 SUGGESTION)

## Artifacts Present at Archive Time

| Artifact | Location | Status |
|---|---|---|
| proposal.md | openspec/changes/archive/2026-09-29-billing-manual-payment-verification/ | Present; Success Criteria boxes checked (documentation-hygiene WARNING resolved) |
| specs/billing-manual-payment-verification/spec.md | openspec/changes/archive/2026-09-29-billing-manual-payment-verification/ | Present; 6 requirements |
| specs/bank-transfer-subscription/spec.md | openspec/changes/archive/2026-09-29-billing-manual-payment-verification/ | Present; 2 modified requirements (delta) |
| design.md | openspec/changes/archive/2026-09-29-billing-manual-payment-verification/ | Present; 11 components (C1–C11) |
| tasks.md | openspec/changes/archive/2026-09-29-billing-manual-payment-verification/ | Present; 57/57 tasks marked `[x]` |
| verify-report.md | openspec/changes/archive/2026-09-29-billing-manual-payment-verification/ | Present; PASS verdict |

## Specs Synced to Main

| Domain | File | Action | Details |
|--------|------|--------|---------|
| billing-manual-payment-verification | openspec/specs/billing-manual-payment-verification/spec.md | Created | NEW: 6 requirements (R1–R6) |
| bank-transfer-subscription | openspec/specs/bank-transfer-subscription/spec.md | Modified | 2 MODIFIED requirements (D1, D2); all other 4 requirements preserved unchanged |

### Spec Merge Details

**bank-transfer-subscription merge**:
- **Requirement: Admin resolution of a pending proof (D1)** — Updated to reflect that approval/rejection now MUST write `billing_audit_log` entries and send buyer Spanish decision emails; email delivery runs outside the transaction and its failure never rolls back the approval/rejection. The "Resolving an already-expired payment fails" scenario now additionally states "no audit row is written and no buyer email is sent."
- **Requirement: Proof storage is not publicly reachable** — Updated to include one sanctioned exception: a valid, unexpired, HMAC-signed proof-access token (D1) MAY read the file through a dedicated token-authenticated endpoint. Two new scenarios added: "A valid signed token reads the file" and "An expired or tampered token still fails." Previous requirement text preserved with explicit "(Previously: ...)" note marking the change boundary.
- **All other 4 requirements** unchanged and preserved in place.

## Implementation Coverage

| Phase | PR | Tasks | Status | Evidence |
|---|---|---|---|---|
| P1 | #277 | 1.1–1.10 | GREEN | `billing_audit_log` + V24, reconciliation resolution, audit-on-approve/reject |
| P2 | #278 | 2.1–2.13 | GREEN | `correctManually`, exception ctor, corrections endpoint |
| P3 | #279 | 3.1–3.5 | GREEN | Pending-verification list query + endpoint, explicit `400` on `pageSize>50` |
| P4 | #280 | 4.1–4.16 | GREEN | Signed proof token signer/verifier, storage read, serving controller, `SecurityConfig` matcher, detail endpoint |
| P5 | #281 | 5.1–5.13 | GREEN | `UserContactPort`, buyer mail adapter, `afterCommit`, OpenAPI/Bruno/US doc |

**Test Evidence**:
- `:api:billing:test` — 789 tests pass, 0 failures
- `:api:auth:test` — 527 tests pass, 0 failures
- `PaymentProofIntegrationTest` — 5/5 pass (Testcontainers MySQL, full filter chain)
- ArchUnit — 7/7 (billing) + 14/14 (auth) pass
- Coverage gates — 95%/90% (billing) and 100%/85% (auth) green

## Verification State at Archive

**Verdict**: PASS

**Critical Issues**: 0

**Warnings** (documentation-hygiene, resolved at archive):
1. ✅ **RESOLVED**: proposal.md's Success Criteria checklist lines 124–132 had unchecked `[ ]` boxes despite all criteria being independently confirmed true by test/runtime evidence. All 9 boxes now checked `[x]` as part of archiving (documentation-hygiene cleanup as described in the verify-report).
2. ℹ️ **INFO**: docs/user-stories/US-BILLING-005.md lifted from Draft status and D3 route divergence recorded (task 5.12, marked `[x]`) — not re-verified in this archive pass as it is documentation-only and outside tested code surface; low risk.

**Suggestions** (informational):
- Evidence composed into audit `reason` column as `"{motivo} | evidencia: {evidence}"` (D4/C7) is a documented compromise; a future dedicated `evidence` column is a one-line migration change, not a defect of the current implementation.

## Design Decisions (Locked; Not Re-litigated)

| # | Topic | Decision |
|---|---|---|
| D1 | Signed proof URL mechanism | Local signed-token HMAC endpoint with embedded expiry (15 min), serving from existing local filesystem storage; no S3/MinIO migration |
| D2 | Reconciliation task lifecycle | `billing_reconciliation_tasks` gains `resolved`/`resolved_at`/`resolved_by` columns via Flyway V24 |
| D3 | Admin route naming | All endpoints use `/api/v1/admin/billing/payments/...` (diverges from issue's literal `/api/v1/billing/admin/payments`; documented in proposal, design, and PR body) |
| D4 | Audit log shape | One table `billing_audit_log`, append-only, columns `id`, `payment_id`, `admin_id`, `action`, `reason` (nullable), `created_at` |
| D5 | Email content language | Buyer-facing decision emails in Spanish; code in English |
| D6 | Signing secret configuration | Configuration property (`@Value`-injected), fail-fast on absent/blank at startup |
| D7 | Lombok in new code | New mutable/constructor-injected classes use Lombok per CLAUDE.md (2026-09-27 onwards); existing classes not retrofitted |
| D8 | Email-failure transaction boundary | Buyer email outside commit transaction; failure never rolls back approval/rejection |
| D9 | Correction semantics | Correction is a status transition out of `ReconciliationRequired`, not a data edit |

## Files Merged / Moved

### Specs Created/Updated
- ✅ `openspec/specs/billing-manual-payment-verification/spec.md` — **NEW** (6 requirements)
- ✅ `openspec/specs/bank-transfer-subscription/spec.md` — **MODIFIED** (2 requirements updated; 4 preserved)

### Change Folder Archived
- ✅ Source: `openspec/changes/billing-manual-payment-verification/` → Destination: `openspec/changes/archive/2026-09-29-billing-manual-payment-verification/`
- ✅ Verified with `diff -r`: **NO DIFFERENCES** (mechanical copy validated)
- ✅ All artifacts confirmed present in archive (proposal, specs, design, tasks, verify-report, archive-report)

## Task Completion Audit

**57/57 tasks marked `[x]`** across P1–P5 in the archived tasks.md. Cross-checked against merged commits:
- `46a2a85` (P1/PR #277) ✓
- `a8b9045` (P2/PR #278) ✓
- `130e15d` (P3/PR #279) ✓
- `7d2ae4c` (P4/PR #280) ✓
- `773817f` (P5/PR #281) ✓

## Authority Gates Passed

### Native Review Receipt Gate
Not applicable — receipt-driven development was not enabled for this candidate; review was not discovered.

### Task Completion Gate
✅ **PASS** — All 57 implementation tasks marked complete. No stale unchecked tasks remain in archived tasks.md. The single documentation-hygiene gap (unchecked Success Criteria boxes) was reconciled at archive time with explicit reasoning: every criterion had been independently verified true by test/runtime evidence in verify-report, so checking them off is mechanically correct and resolves WARNING 1.

### Edit-Authority Guard
Not applicable — all changes remain within the repo.

## Next Steps

The SDD cycle for `billing-manual-payment-verification` is **complete and closed**. The three new endpoints, audit log, and buyer notification surface are now part of the project's permanent spec and shipped codebase.

- No further re-work is required on this capability.
- A future enhancement (e.g., buyer notification on corrections, dedicated audit evidence column, S3 migration) becomes a separate change with its own SDD cycle.

## Final Record

This archive report is the terminal audit trail for this change. The facts recorded here reflect the state at archival (2026-09-29):
- All implementation tasks are complete.
- All verification requirements pass.
- All delta specs have been merged into the main specs.
- The change folder has been mechanically moved to the archive with byte-identity verified.
- The two documentation-hygiene warnings have been resolved.

The change is ready for production deployment and is no longer subject to modification within the SDD lifecycle — only code reviews, merge, and release follow from this point onward.

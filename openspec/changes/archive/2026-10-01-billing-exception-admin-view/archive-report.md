# Archive Report: Admin view for physical purchases in EXCEPTION (#237)

**Change Name**: billing-exception-admin-view
**Issue**: #237 ("Vista administrativa de compras en EXCEPTION")
**Archived**: 2026-10-01
**Repository**: menta-dance (monorepo)
**Artifact Store**: openspec + Engram (hybrid mode)

## Verdict

**PASS WITH WARNINGS** — 0 CRITICAL, 1 WARNING, 0 SUGGESTION

The change is complete, implemented, verified, and ready for production delivery.

### Verification Status

- **Changed Lines**: ~150–250 (incl. tests)
- **Single PR**: #292, merged to `develop` at commit `0997203`
- **Fresh Test Counts** (orchestrator-verified):
  - `:api:billing:test`: 820/820 passed, 0 failures
  - `:api:app:test`: 379 total, 2 pre-existing unrelated failures
  - `PurchaseAdminExceptionIntegrationTest`: 2/2 passed (proves `Propagation.REQUIRED` works end-to-end with zero ambient transaction)
- **Coverage Gate**: `:api:billing` 85%/85% (domain+application / infrastructure) — PASS
- **OpenAPI**: Valid, 2 pre-existing style warnings, 0 new

### The ONE WARNING (Non-Blocking)

Per verify-report (observation #1671), `:api:app:test`'s full run shows 2 pre-existing, unrelated test failures:
- `PhysicalAttendanceHistoryIntegrationTest`: 409 instead of 201 (deterministic; reproduced identically on pre-change base commit `3abf336` in isolated worktree)
- `PhysicalSessionManagementIntegrationTest`: flaky `NoSuchElementException` (pre-existing suite debt)

These are repository-wide test debt unrelated to `billing-exception-admin-view`'s scope — explicitly NOT fixed as part of this change. Left for separate follow-up investigation.

## Artifacts Retrieved from Engram

| Artifact | Observation ID | Created | Type |
|----------|---|----------|------|
| Proposal | #1666 | 2026-09-30 19:04:58 | architecture |
| Specification | #1667 | 2026-09-30 19:08:23 | architecture |
| Technical Design | #1668 | 2026-09-30 19:10:53 | architecture |
| Tasks & Forecast | #1669 | 2026-09-30 19:14:32 | architecture |
| Verify Report | #1671 | 2026-09-30 22:35:04 | architecture |

## Final-State Facts

### Scope Discipline

✅ Zero changes to `SecurityConfig.java`, `BillingConfiguration.java`
✅ No new Flyway migration
✅ No resolution action added (read-only observability only)
✅ Confirmed via `git diff --stat` against base commit

### Locked Decisions Shipped As Designed

All 10 settled decisions from proposal/design implemented and tested:

| # | Topic | Outcome |
|---|---|---|
| D1 | Scope: physical only, v1 | Physical `Purchase` only; Subscription #236 deferred |
| D2 | Read-only, no resolution action | Pure observability confirmed; no transition methods added |
| D3 | Port signature discipline | Primitive `int page, int size`; no Spring `Page`/`Pageable` in `application` (ArchUnit passed) |
| D4 | Page size | `MAX_PAGE_SIZE = 50` rejected with `400`, never clamped; default 20 |
| D5 | Row context via INNER join | `billing_purchases` joined to `billing_payments` on FK-enforced `uq_billing_purchases_payment_id`; ordering on `Payment.createdAt` asc |
| D6 | Sessions from non-locking query | New `PurchaseSessionJpaRepository.findByPurchaseIdInOrderByPurchaseIdAscPositionAsc` — distinct from existing native `FOR UPDATE` query |
| D7 | Zero-session rows legal | `EXCEPTION` rows with empty session array listed and never dropped; test confirmed |
| D8 | New controller, existing security | New `PurchaseAdminController` under existing `/api/v1/admin/**` → `ADMIN` rule; no `SecurityConfig` edit needed |
| D9 | Lombok on new classes | `@RequiredArgsConstructor` on constructor-injected classes; DTOs remain `record` |
| D10 | Route naming | `/api/v1/admin/billing/purchases`, consistent with sibling `/api/v1/admin/billing/payments` (#33) |

### Design's Top Risk — Resolved & Tested

The adapter's `Propagation.REQUIRED` (not `MANDATORY`, which other fulfillment-transaction-bound methods use) was proven correct via a real end-to-end HTTP integration test (`PurchaseAdminExceptionIntegrationTest`) with zero `@Transactional` anywhere in its class hierarchy — not just a structural claim. Test runs 2/2 PASS.

### Task Completion

All 30/30 tasks in `tasks.md` are marked complete [x]:
- Phase 1 (Port + Query): 1.1–1.12 ✅
- Phase 2 (Web DTO + Controller): 2.1–2.8 ✅
- Phase 3 (Integration Guards): 3.1–3.5 ✅
- Phase 4 (Contract, Tooling, Docs): 4.1–4.5 ✅

## Success Criteria — All Met

- [x] `GET /api/v1/admin/billing/purchases?status=EXCEPTION` returns `200`, oldest-first, paginated, with buyer, `createdAt`, amount/currency, target reference, covered sessions
- [x] Only `EXCEPTION` purchases appear; `PENDING_FULFILLMENT`/`ASSIGNED` absent — asserted by test
- [x] Zero-session `EXCEPTION` purchase (#238) listed with empty session array, neither dropped nor erroring (D7)
- [x] `size = 51` → `400` (rejected, not clamped); absent `size` defaults to 20; empty result → `200` empty page, not `404`
- [x] Non-`ADMIN` authenticated → `403`; anonymous → `401/403`
- [x] No migration, no domain change, no outbox event, no `SecurityConfig` matcher — verified by diff
- [x] `MarkPurchaseExceptionUseCase` and #209 notification path keep existing tests untouched and passing
- [x] OpenAPI + Bruno updated; `./gradlew check` passes including ArchUnit and billing coverage gate (85%/85%)

## Filesystem Archive

**Source Folder**: `/Users/ale/repositorios/menta-dance/openspec/changes/billing-exception-admin-view/`

**Archived To**: `/Users/ale/repositorios/menta-dance/openspec/changes/archive/2026-10-01-billing-exception-admin-view/`

**Archive Contents**:
```
2026-10-01-billing-exception-admin-view/
├── proposal.md
├── design.md
├── tasks.md (30/30 tasks [x])
├── verify-report.md
├── exploration.md
├── specs/
│   └── purchase-exception-admin-view/
│       └── spec.md
└── archive-report.md (this file, added post-archive)
```

**Byte-Perfect Verification**: `diff -r` output was empty (only the archive-report.md added post-move, which is excluded from verification per the skill contract).

## Spec Merge

**Delta Spec**: `/Users/ale/repositorios/menta-dance/openspec/changes/billing-exception-admin-view/specs/purchase-exception-admin-view/spec.md`

**Action**: NEW capability (Proposal's "Modified Capabilities: None")

**Merged Into**: `/Users/ale/repositorios/menta-dance/openspec/specs/purchase-exception-admin-view/spec.md` (NEW file created)

**Byte-Perfect Verification**: `diff` output empty (mechanically copied via shell).

## Delivery & Dependencies

- **Single PR #292**: merged to `develop`, final commit `0997203`
- **No blocking dependencies**: builds on #209 (`purchase-exception-notification`, archived) and #33 (`billing-manual-payment-verification`, archived)
- **#236 (Subscription in EXCEPTION)**: remains open, neither blocked nor designed by this change

## Change Complete

The SDD cycle for `billing-exception-admin-view` is **CLOSED**. The change has been fully planned (proposal + design + tasks), implemented (PR #292, merged), verified (all requirements met, warnings documented, top risk validated), and archived (artifacts moved, spec synced, audit trail recorded).

Ready for the next change.

---

**Archive Report Metadata**

- Archived: 2026-10-01
- Skill: sdd-archive/SKILL.md
- Mode: hybrid (openspec + Engram)
- Phase Authority: Formal-State Authority per sdd-phase-common.md — archive reflects final state AT CLOSE per orchestrator's launch prompt with explicit final-state facts, not stale intermediate snapshots

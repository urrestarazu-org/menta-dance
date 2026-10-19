# Archive Report: billing-real-course-catalog-adapter

**Change**: billing-real-course-catalog-adapter
**Issue**: #108 (real adapter for `CourseCatalogPort`)
**Project**: menta-dance
**Archive date**: 2026-10-03
**Archive path**: `openspec/changes/archive/2026-10-03-billing-real-course-catalog-adapter/`
**Artifact store**: hybrid (openspec + Engram)

## Outcome

`GET /api/v1/billing/plans` and `GET /api/v1/billing/plans/{id}` now return the real course title in `courses[].name` for every plan course that resolves to a PUBLISHED virtual course or an ACTIVE physical course (it was always `null` because the only implementation of `CourseCatalogPort` was a placeholder that threw). Names are resolved in batch, once per request, instead of one lookup per course. Unresolvable courses keep `name: null` and the response stays 200.

## Delivery

Three PRs merged into `develop`:

| PR | Slice | Commit | Lines | Content |
|----|-------|--------|-------|---------|
| #319 | S1 | `28cc690` | +617/-0 | batch lookups `findPublishedByIds` (virtual) and `findActiveByIds` (physical) with MySQL parity tests |
| #320 | S2 | `ff6cbf0` | +356/-49 | billing port changed to a batch contract and `PlanCourseResolver` resolves names once per request |
| #321 | S3 | `d5a8f09` | +629/-70 | real `CourseCatalogPortAdapter` in `api:app`, placeholder removed, integration tests, docs and OpenAPI description; `Closes #108` |

Issue #108 is closed and Done on the board. Follow-ups: #312 (catalog cache and rate limit), #309, #310.

## Spec promoted

New capability; no existing main spec covered it. The delta spec was copied verbatim to `openspec/specs/billing-plan-course-names/spec.md`: 7 requirements, 18 scenarios.

## Verification

One verify attempt: **PASS WITH WARNINGS** on `develop` @ `d5a8f09`. evidence_revision `sha256:8197259b39a27fc4b4177bb7ad2b5422feb1ad3e0e67ce32b610b87d93093655`, report sha256 `2622bbbcd7ab352eb6766dbe4e22ca21e4890bbe4e4878425e18e0c331e7c26b` (the file in this folder is the byte-exact attested artifact). 0 critical, 5 warnings, 4 suggestions; 7/7 requirements and 18/18 scenarios; 56/56 tasks.

As reported by the verify agent from a fresh `--no-build-cache --rerun-tasks` run (test command and build command exit 0; redocly lint exit 0 with 7 pre-existing warnings): 2156 tests (virtual 355, physical 474, billing 869, app 458), 0 failures, errors or skipped. JaCoCo line coverage: virtual domain+application 97.98% and infrastructure 95.20%, physical 98.09% and 97.53%, billing 96.82% and 91.56%, `api:app` 95.94% (floor 0.90). ArchUnit: virtual 7, physical 8, billing 8, app 7, all passing. Checkstyle on added lines: 25 `MethodName` findings on snake_case test names (tracked in #298) and nothing else. The orchestrator re-checked the report hash and the spec and task counts; it did not re-run the gate, so the gate, JaCoCo, ArchUnit and Checkstyle figures are the verify agent's.

## Native SDD ledger

Attempt 1 was settled `passed` with the verify evidence_revision after the planning artifacts were declared as intended untracked. The ledger charged 2254 changed lines against the 800-line budget (the three slices were 617, 405 and 699 changed lines each; bases merged into the branch during the attempt are charged to it) and set `maintainer_decision`. The maintainer explicitly chose to archive **without** an `sdd-attempt reset`. Native status reported `archive: ready` and nextRecommended `archive`. No reset happened in this change.

## Decisions (final)

1. A plan course that is DRAFT or ARCHIVED (virtual) or inactive (physical) resolves to `name: null`, like the public catalog.
2. An unresolved course keeps `name: null` (the nullable contract of `PlanCourseResponse.name` is unchanged).
3. The N+1 is removed in this change through a batch port `Map<String, String> courseNames(Collection<String>)`, with no cache; a cache is deferred to #312.
4. Lombok is added to `api:app` and used by the adapter.
5. The adapter is sequential: virtual first, physical only for the ids virtual did not answer. A failing module logs a WARN (`module`, `courseIds`) and degrades only its own ids to `null`.

## Open suggestions (non-blocking, not fixed)

1. W1: the constant-cost claim (R4) is pinned by mock call counts with up to 3 plans and 3 ids; no test scales the input and no real SQL statement count is asserted.
2. W2: failure isolation is covered at unit level only; no test drives the real adapter plus resolver to an HTTP 200 under a throwing module, and "malformed id emits no WARN" rests on module tests.
3. W3: minor design deviations: `resolveNames` takes the port as a parameter, the resolver WARN has its own text, and the adapter test captures logs with a logback `ListAppender`.
4. W4: `docs/adr/0037-catalog-course-id-routing.md` still says "parallel" while the adapter is sequential and virtual-first; a follow-up issue is pending.
5. W5: 25 `MethodName` warnings on added lines (#298).
6. A stale `courseNames(any())` stub in `GetPlanUseCaseImplTest`; the WARN omits the exception class by design; RED for tasks 1.8, 1.9 and 3.6 was established by temporary mutation.

## Known unverified by design

- Partial-failure behavior over HTTP and the apply-phase mutation proofs were not repeated by verify.
- The root `./gradlew check` is unrunnable in this environment because of the android module; verification ran per module.

## Rollback

Revert the PRs in reverse order (#321, #320, #319). No schema or data change.

## Traceability

Engram topics under `sdd/billing-real-course-catalog-adapter/`: `explore`, `decisions`, `proposal`, `spec`, `design`, `tasks`, `apply-progress`, `verify-report` and `archive-report`.

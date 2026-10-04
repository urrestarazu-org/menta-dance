# Archive Report: checkstyle-cleanup (#298)

Archived 2026-10-04. Written by the orchestrator from verified facts.

## Delivery

14 PRs merged into `develop`, stacked, each one a sub-issue of #298:

| Unit | PR | Scope |
|---|---|---|
| W0 | #333 (efd9fed) | Policy config (LineLength 120, relaxed test-name rules, narrowed Javadoc rules), per-task warning ceilings, ADR-0043 |
| W1.1-W1.5 | #334, #335, #336, #337, #338 | Mechanical formatting: import order, Javadoc paragraphs (google-java-format, SHAs in `.git-blame-ignore-revs`) |
| W1.6 | #339 | Remaining Phase 1 rules |
| W2.1-W2.5 | #352, #386, #399, #400, #401 | Per-module cleanup to zero findings and strict flip: shared/bff/app, auth, physical, billing, virtual |
| W3 | #402 (4f332bd) | Default severity `error`, `maxWarnings = 0`, ratchet machinery deleted |

Result: 6768 findings across 7 JVM modules down to 0. Any new Checkstyle finding now fails the build.

## Verification

`sdd-verify`: `pass_with_warnings`, 0 critical, 10 requirements / 28 scenarios admitted by the validator. Re-run at the final state (develop @ 4f332bd): 14 Checkstyle tasks with `--rerun-tasks --no-build-cache` at 0 findings, `build --dry-run` exit 0, `:api:shared:test` 64 tests.

Warnings and their resolution:
1. `.git-blame-ignore-revs` lacked the W1.3 SHA (131d332): fixed in the archive PR (#403).
2. 16 unchecked boxes in `tasks.md` (97 checked), all orchestrator/process bookkeeping (O-1..O-9, W1.x PR evidence, W3-10); no implementation task is open. Left as recorded.
3. Requirements R1-R5 (ceilings, strict modules, locks) cannot be exercised after W3 removed that machinery by design; they were proven in the PR CI of W0-W2.

## Spec promotion

`openspec/specs/code-style-gate/spec.md` holds R6-R10 (5 requirements, 12 scenarios): style policy in the config, no dead suppression file, end state any finding fails, policy documentation, blame-ignore revisions. R1-R5 (ratchet) are excluded from the live spec and kept in the archived delta spec, with a History note.

## Accepted deviations and follow-ups

- D2: camelCase test rename deferred (issue #323); snake_case tolerated via relaxed `@Test` suppressions.
- W3 removed 23 per-rule locks, not 22 (`TypeName` appears twice).
- Not created, noted only: migration off deprecated `@MockBean` (354 uses in 38 `api/app` test files; javac `[removal]` warnings, removal in Spring Boot 4), flaky `api:app` integration tests, 8 stale comments in billing/virtual (see PRs #400/#401). Version catalog review is #347.
- Ledger: `gentle-ai sdd-attempt settle` returns `blocked: maintainer_decision` because the objective exceeded the 800-line / attempt budget across the 14 slices. The maintainer chose to archive WITHOUT resetting the ledger (same decision as #108); the attempt stays unsettled on purpose.

## Engram

Source artifacts: `sdd/checkstyle-cleanup/{explore,proposal,spec,design,tasks,apply-progress,verify-report}`; decisions: `#298 decisions: checkstyle-cleanup question round`; tracking: `#298 checkstyle-cleanup: tracking state and sub-issue map`.

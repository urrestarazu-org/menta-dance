# Tasks: Checkstyle cleanup and ratchet (#298)

> Inputs: `proposal.md`, `specs/code-style-gate/spec.md` (R1-R10 below follow the spec's requirement order), `design.md`, `exploration.md`, Engram `sdd/checkstyle-cleanup/decisions` (D1-D5). This artifact exceeds the usual word budget on purpose: the orchestrator asked for per-unit detail for a 14-19 PR stack.
> Conventions: `W<unit>-<n>` is task n of a work unit; `[O]` marks an orchestrator-only task (branches, commits, PRs, board, Engram, native ledger). Apply agents NEVER create PRs, run the native ledger or write board items. Code and identifiers in English; PR bodies, ADR-0043 and docs in Spanish. No AI attribution anywhere; conventional commits.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | About 4,900-7,900 in total over 13-18 PRs: Phase 0 300-450; Phase 1 about 2,500-3,800 over 7 PRs; Phase 2 about 2,000-3,500 over 5-9 PRs (volume is a guess: about 4 lines per missing Javadoc); Phase 3 50-120 |
| 400-line budget risk | High against 400 (nearly every slice is above it); Medium against the real 800 review budget, High for the uncertain slices listed below |
| Chained PRs recommended | Yes |
| Suggested split | W0 -> W1.1 -> W1.2 -> W1.3 -> W1.4 -> W1.5 -> W1.6 -> W1.7 -> W2.0 checkpoint -> W2.1..W2.n (module slices) -> W3 (does NOT fold into W2.5 (decided at W2.0)) |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main (each PR targets `develop` after the previous one merged; each branch is created from the updated `develop`) |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

Review budget per PR: 800 changed lines (added + removed). A slice that cannot fit STOPS apply and asks the orchestrator (D5: no `size:exception`).

Per-slice estimates (from the proposal slice table and the design; honest ranges, not measured):

| Unit | Estimate (changed lines) | Confidence | Budget flag |
|------|--------------------------|------------|-------------|
| W0 `config-ratchet-docs` | 300-450 (config about 60, build about 35, ADR-0043 about 150, docs/ADR fixes about 80, suppressions deletion about 12) | Medium | Safe; ADR text is the swing |
| W1.1 `imports-pilot` | 300-400 | Low until pilot | Tool behavior unknown (gjf parity with `CustomImportOrder`) |
| W1.2 `imports-auth` | 350-500 | Medium after W1.1 | Safe |
| W1.3 `imports-bff` | 450-650 | Medium after W1.1 | Tight |
| W1.4 `javadoc-paragraph-a` | 300-450 | Low until script pilot | Script unpiloted; `<p>` insertion can lengthen lines (LineLength risk) |
| W1.5 `javadoc-paragraph-b` | 300-450 | Low until script pilot | Same |
| W1.6 `text-blocks` | 300-600 | Low | Manual, about 52 blocks across unknown modules |
| W1.7 `line-length-and-small-rules` | 500-750 | Medium | TIGHTEST of Phase 1: LineLength 116 (about 3 lines per wrap) plus about 99 small findings; natural split at the rule boundary |
| W2.x module slices | 100-750 each, 5-9 PRs | Low | Billing, auth and bff are the risky ones; auth and bff likely 2 PRs each |
| W3 `severity-error-default` | 50-120 | High | Safe; does NOT fold into W2.5 (decided at W2.0) |

Uncertain until a pilot: all of W1.1-W1.5 (tool behavior), W1.6 (block count and layout), all of W2 (Javadoc volume after Phase 1; module order and splits are PROVISIONAL until the W2.0 checkpoint). Total is about 14-19 PRs plus the SDD archive PR (orchestrator, out of this checklist).

### Suggested Work Units

| Unit | Goal | PR / branch | Focused verification command | Runtime harness | Rollback boundary |
|------|------|-------------|------------------------------|-----------------|-------------------|
| W0 | Policy D1-D3, delete dead `suppressions.xml`, ceilings map + strict set + `configureEach`, tamper evidence, ADR-0043 and doc fixes | PR 0, `feature/checkstyle-config-ratchet-docs` | `rtk proxy ./gradlew <CS14> --rerun-tasks --no-build-cache --continue` (14 tasks, exact ceilings) plus T1-T5 | The tamper runs T1-T5 on the real 14 tasks | Revert PR 0: warning-only behavior returns; no schema or runtime change |
| W1.1 | Import order pilot: `google-java-format --fix-imports-only` on app test and non-auth/bff files | PR 1, `feature/checkstyle-imports-pilot` | `rtk proxy ./gradlew <CS14> --rerun-tasks --no-build-cache --continue` then JUnit count and `<ALL_ARCH>` | Checkstyle reports plus `git diff -U0` import-only check | Revert PR 1 (code and ceilings come back together) |
| W1.2 | Import order in `api:auth` | PR 2, `feature/checkstyle-imports-auth` | `rtk proxy ./gradlew :api:auth:checkstyleMain :api:auth:checkstyleTest --rerun-tasks --no-build-cache --continue` | Same | Revert PR 2 |
| W1.3 | Import order in `bff`; lock `CustomImportOrder` | PR 3, `feature/checkstyle-imports-bff` | `rtk proxy ./gradlew :bff:checkstyleMain :bff:checkstyleTest --rerun-tasks --no-build-cache --continue` then `<CS14>` (lock check) | Same | Revert PR 3 (lock and code together) |
| W1.4 | JavadocParagraph script, module group A | PR 4, `feature/checkstyle-javadoc-paragraph-a` | `<CS(group A modules)>` per module, `--rerun-tasks --no-build-cache --continue` | `git diff -U0` shows comment lines only; LineLength unchanged | Revert PR 4 |
| W1.5 | JavadocParagraph script, module group B; lock | PR 5, `feature/checkstyle-javadoc-paragraph-b` | `<CS14>` then per-module | Same | Revert PR 5 |
| W1.6 | TextBlockGoogleStyleFormatting, manual; lock | PR 6, `feature/checkstyle-text-blocks` | `<CS(touched modules)>` plus `<tests of touched modules>` | Module tests are the content safety net | Revert PR 6 |
| W1.7 | LineLength 116 and the small formatting rules; locks | PR 7, `feature/checkstyle-line-length-small-rules` | `<CS14>` plus `<ALL_TEST>`, `<ALL_ARCH>`, `<ALL_JACOCO>` | Checkstyle reports; ArchUnit/JaCoCo | Revert PR 7 |
| W2.0 | Checkpoint: re-measure residuals, fix the module order and the auth/bff splits | no PR of its own (decision recorded by the orchestrator) | `<MEASURE>` block | Per-module, per-rule residual table | N/A (decision record) |
| W2.1-W2.7 | Per module to 0/0, module flips to strict (PROVISIONAL order) | one PR per module (auth and bff 2 each), `feature/checkstyle-module-<name>` | `rtk proxy ./gradlew :<mod>:checkstyleMain :<mod>:checkstyleTest --rerun-tasks --no-build-cache` then module test/ArchUnit/JaCoCo | Module at 0/0 under `severity=error` | Revert the module PR (code, keys removal and strict flip together) |
| W3 | Default severity `error`, `maxWarnings = 0`, delete the mechanism, final docs | final PR, `feature/checkstyle-severity-error-default` (or folded into the last W2 PR) | `rtk proxy ./gradlew <CS14> --rerun-tasks --no-build-cache` plus injection run | Injected finding fails each of the 14 tasks | Revert W3: strict modules still hold their own flips if W3 was separate |

PR order is strictly sequential (stacked-to-main): ceilings share one map (one key per line) and every slice re-measures from the updated `develop`.

## Shared commands (used by every unit)

All Gradle runs: `rtk proxy ./gradlew`, one Gradle invocation at a time. Never use a bare root `checkstyleMain`/`test` (the root selects the android module and NO-SOURCE tasks): always module-qualified task paths.

- `<CS14>` (the 14 tasks): `:api:shared:checkstyleMain :api:shared:checkstyleTest :api:auth:checkstyleMain :api:auth:checkstyleTest :api:billing:checkstyleMain :api:billing:checkstyleTest :api:virtual:checkstyleMain :api:virtual:checkstyleTest :api:physical:checkstyleMain :api:physical:checkstyleTest :api:app:checkstyleMain :api:app:checkstyleTest :bff:checkstyleMain :bff:checkstyleTest`
- `<MEASURE>`: `rtk proxy ./gradlew <CS14> --rerun-tasks --no-build-cache --continue`, then `rg -c --include-zero '<error ' api/*/build/reports/checkstyle/{main,test}.xml bff/build/reports/checkstyle/{main,test}.xml` (14 counts).
- `<RULES>` (per rule): `rg -o --no-filename 'source="[^"]+"' api/*/build/reports/checkstyle/{main,test}.xml bff/build/reports/checkstyle/{main,test}.xml | sort | uniq -c | sort -rn`
- `<FILES(Rule)>` (offending files): `rg -P -U -o --no-filename -r '$1' '<file name="([^"]+)">(?:(?!</file>)[\s\S])*?source="[^"]*\.<Rule>Check"' api/*/build/reports/checkstyle/{main,test}.xml bff/build/reports/checkstyle/{main,test}.xml`
- `<ALL_TEST>`: `:api:shared:test :api:auth:test :api:billing:test :api:virtual:test :api:physical:test :api:app:test :bff:test` (to compare counts run with `:<mod>:cleanTest` before each `:<mod>:test`; Testcontainers needs Docker).
- `<ALL_ARCH>`: `:api:auth:test --tests "*ArchitectureTest" :api:billing:test --tests "*ArchitectureTest" :api:virtual:test --tests "*ArchitectureTest" :api:physical:test --tests "*ArchitectureTest" :api:app:test --tests "*ArchitectureTest"` (ArchUnit is `api:*` except `shared`; `bff` has none).
- `<ALL_JACOCO>`: `:api:shared:jacocoTestCoverageVerification :api:auth:jacocoTestCoverageVerification :api:billing:jacocoTestCoverageVerification :api:virtual:jacocoTestCoverageVerification :api:physical:jacocoTestCoverageVerification :api:app:jacocoTestCoverageVerification :bff:jacocoTestCoverageVerification` (no threshold lowered; thresholds in `CLAUDE.md` and `moduleCoverageFloor`).
- `<ALL_CHECK>`: `:api:shared:check :api:auth:check :api:billing:check :api:virtual:check :api:physical:check :api:app:check :bff:check` (equivalent of CI `clean build` for the JVM modules).
- `<JUNIT(dir)>`: after the module test task ran, `rg -o --no-filename -r '$1' '<testsuite [^>]*?\btests="(\d+)"' <dir>/build/test-results/test/*.xml | paste -sd+ - | bc` (same with `skipped=` for the skipped total). `<dir>` is `api/<mod>` or `bff`. The BEFORE value is taken at branch start on the updated `develop`; the AFTER value at the end of the slice; they MUST be equal for every slice that edits Java (no tests added or removed).
- `<SIZE>`: `git diff --shortstat origin/develop...HEAD` (added + removed <= 800).

## Phase 0: Tracking (orchestrator performs; not code)

- [ ] O-1 [O] Precondition: `git fetch origin`; `gh pr list --state open` (re-check before PR 0 merges: any open feature PR turns red once the ceilings exist, by design); `develop` still at `56ed6e4` or newer (if newer, W0-2 re-establishes the baseline).
- [ ] O-2 [O] Ask the user ONE question: value of the board field "Prioridad" for #298 and its sub-issues (still unset). Do not guess; ask and stop.
- [ ] O-3 [O] Board (project 1): create a sub-issue under #298 per work unit (W0, W1.1-W1.7, W3) with fields Modulo `infra`, Tipo `tech-debt`, Tamano from the slice estimate (verify field options with `gh project field-list`), Prioridad per O-2; W2.x sub-issues are created after the W2.0 checkpoint when modules and splits are known. Set status In progress when the unit's branch starts and In review when its PR opens.
- [ ] O-4 [O] Branch names (each created from the updated `develop`): `feature/checkstyle-config-ratchet-docs`, `feature/checkstyle-imports-pilot`, `feature/checkstyle-imports-auth`, `feature/checkstyle-imports-bff`, `feature/checkstyle-javadoc-paragraph-a`, `feature/checkstyle-javadoc-paragraph-b`, `feature/checkstyle-text-blocks`, `feature/checkstyle-line-length-small-rules`, `feature/checkstyle-module-<name>`, `feature/checkstyle-severity-error-default`.
- [ ] O-5 [O] For every PR use `skills/prcreator/SKILL.md`: `gh pr list --head <branch>` first, base `develop`, Spanish body with the measurement table (14 counts before/after, ceilings), the JUnit table (before/after per module), the exact command or script for mechanical slices (W1.x), the T1-T5 evidence (W0 only), `Refs #298` (plus `Closes #<sub-issue>` if a sub-issue exists for the unit; orchestrator decision) and `Closes #298` ONLY on the final PR (W3, or the last W2 PR if W3 is folded into it). Confirm the preview before push.
- [ ] O-6 [O] After each merge: update `develop`, branch the next unit from it, and update Engram (`sdd/checkstyle-cleanup/apply-progress`: unit done, measured counts, ceilings, SHA). Never run the native ledger from an apply agent.
- [ ] O-7 [O] `.git-blame-ignore-revs` follow-ups for W1.1-W1.5 only (spec R10): after the squash merge take the SHA with `gh pr view <n> --json mergeCommit -q .mergeCommit.oid`; append `# <slice> (#<n>)` plus the full SHA as the first commit of the next PR (create the file with the W1.1 SHA; a standalone `chore` PR if no PR follows); verify `git cat-file -t <sha>` prints `commit`. Do NOT list W1.6, W1.7, W2.x or W3 SHAs.
- [ ] O-8 [O] After W2.0: record the fixed Phase 2 order and splits in Engram and amend this file (module names, section order, split of auth and bff); create the W2 sub-issues.
- [ ] O-9 [O] Before PR 0 merges: check whether release PRs into `main` are squashed (design assumption: blame-ignore SHAs from `develop` may not exist on `main`); note the finding in the PR 0 body.

## W0: `config-ratchet-docs` (PR 0, branch `feature/checkstyle-config-ratchet-docs`)

Satisfies R1-R7, R9 and the Phase 0 half of R10 awareness (no blame file here). Strict TDD: policy edits are verified by the measured count delta; the ratchet is verified by tamper runs T1-T5 with RED captured BEFORE GREEN. No CLAUDE.md edit: the design's File Changes do not list it and D2 forbids presenting snake_case as official there.

- [x] W0-1 Branch-start JUnit baseline (R8 no-behavior-change): run `<ALL_TEST>` with `cleanTest`, record `<JUNIT(dir)>` tests and skipped totals for the 7 modules in a table (kept for the PR body).
- [x] W0-2 Sanity: unmodified tree, run `<MEASURE>`; expect 6,768 total (main 2,793 / test 3,975). A different total means develop moved: record it and use it as the baseline of the delta explanation in W0-10; if wildly different, stop and report.
- [x] W0-3 In `config/checkstyle/google_checks.xml` set LineLength `max` 100 -> 120 (line 57), keep the `ignorePattern` (line 58); update the comment at line 324 ("LineLength=100" -> "LineLength=120"). Verify: `rg -n 'LineLength' config/checkstyle/google_checks.xml` shows 120 and no 100.
- [x] W0-4 Same file: relax the `@Test` MethodName suppression `message` regex (line 471) to `[A-Za-z][A-Za-z0-9]*(?:_[A-Za-z0-9]+)*` and add a comment at lines 463-464 that the suppression keys on the pinned English message (fragility named in ADR-0043); add a second `@Test` XPath suppression for `AbbreviationAsWordInName` WITHOUT `message`, modeled on the existing MethodName XPath suppression (lines 466-472). (R6b)
- [x] W0-5 Same file: MissingJavadocMethod (lines 441-443) drop `CTOR_DEF` and `COMPACT_CTOR_DEF` from `tokens` and append `Bean, GetMapping, PostMapping, PutMapping, PatchMapping, DeleteMapping, RequestMapping, ExceptionHandler, Scheduled` to `allowedAnnotations`. (R6c)
- [x] W0-6 Same file: exempt nested types from MissingJavadocType with an XPath suppression (`[ancestor::OBJBLOCK]` shape; the design says to verify 113 -> 85 in W0-10). Keep the upstream optional `SuppressionFilter`/`SuppressionXpathFilter` modules (lines 35-39, 492-497) untouched. (R6d)
- [x] W0-7 Delete `config/checkstyle/suppressions.xml` with `git rm` (its only entry, MethodName in `ArchitectureTest.java`, is redundant under the relaxed regex). Verify `rg -n 'suppressions\.xml' --glob '!openspec/changes/archive/**' .` returns only the intended doc mentions of ADR-0043 later; remove any other reference. (R7)
- [x] W0-8 `openspec/config.yaml` line 17: `LineLength=100` -> `LineLength=120`. Verify with `rg -n 'LineLength' openspec/config.yaml`. (R9)
- [x] W0-9 Fresh baseline on the policy-only tree: `<MEASURE>` plus `<RULES>`. Record the 14 counts, their sum and the per-rule table. This is the ONLY source for the ceilings (never the 1,866 projection).
- [x] W0-10 Compare with the projection (total 1,866; app 24/139, auth 382/92, bff 252/328, billing 216/67, physical 140/11, shared 10/7, virtual 182/16; main 1,206 / test 660) and explain EVERY per-task delta in a table BEFORE freezing ceilings (R6 "Phase 0 projection": additional config changes may lower it, for example the deleted suppression file was never loaded so it should change nothing). Verify MissingJavadocType 113 -> 85 and run `rg -P -U '<file name="[^"]*ArchitectureTest\.java">(?:(?!</file>)[\s\S])*?MethodName' api/*/build/reports/checkstyle/test.xml` (no match). Unexplained delta: stop and ask the orchestrator.
- [x] W0-11 In root `build.gradle.kts`: add `import org.gradle.api.plugins.quality.Checkstyle`; add `val checkstyleWarningCeiling = mapOf(...)` (14 entries, task-path keys, sorted, one per line, trailing comma, values = W0-9 counts) and `val checkstyleStrictModules = setOf<String>()` directly below `moduleCoverageFloor`; in the JVM branch next to the `checkstyle {}` block (lines 163-166) add `val strict = project.path in checkstyleStrictModules`, the conditional `configProperties = mapOf("org.checkstyle.google.severity" to "error")`, and the `tasks.withType<Checkstyle>().configureEach` block, EXACTLY as the design's contract code (strict wins with a stale-key `logger.warn`; missing key throws `GradleException` naming the task path and ADR-0043). (R1, R2, R4)
- [x] W0-12 T1 RED: delete the key `:api:billing:checkstyleTest` from the map; run `:api:billing:checkstyleTest`; expect a failing build whose message contains `:api:billing:checkstyleTest` (R2). Capture trimmed output. Restore the key.
- [x] W0-13 T2 RED: temporarily set all 14 ceilings to measured minus 1; `rtk proxy ./gradlew <CS14> --rerun-tasks --no-build-cache --continue`; expect 14 task failures with `[warning:N]` each (R1 "every task is gated"; every count is > 0). Capture trimmed output. Restore exact ceilings.
- [x] W0-14 T3 cache: with exact ceilings run `<CS14>` green (with `--rerun-tasks --no-build-cache`), then set one ceiling to N-1 and run WITHOUT `--rerun-tasks`/`--no-build-cache`; expect a failure, not `UP-TO-DATE`/`FROM-CACHE` (proves `maxWarnings` is a task input). Restore.
- [x] W0-15 T4 strict: temporarily add `:api:shared` to `checkstyleStrictModules`; run `:api:shared:checkstyleMain :api:shared:checkstyleTest`; expect failure with `[error:N]` (R4). Revert, then run a non-strict module (for example `:api:billing:checkstyleMain`) with `-Dorg.checkstyle.google.severity=error` and confirm outcome and count are unchanged. Capture trimmed output.
- [x] W0-16 T5 GREEN: exact ceilings, `rtk proxy ./gradlew <CS14> --rerun-tasks --no-build-cache` passes (14 tasks), then `rtk proxy ./gradlew <ALL_CHECK>` passes.
- [x] W0-17 Create `docs/adr/0043-checkstyle-policy-ratchet.md` (Spanish, from `docs/adr/template.md`, title "Politica de Checkstyle y trinquete"): Contexto (warning-only, 6,768, false ADR-0042 claim), Factores, Opciones (status quo, big-bang, techos por tarea [elegida], supresiones de baseline, herramienta diff), Decision, Justificacion (known limit: rule swaps and slack), Consecuencias (feature PRs red by design, "corregir o bajar, nunca subir", only exception: a Checkstyle version bump PR recalibrates; LineLength 120; Javadoc scope; snake_case tolerated and camelCase rename deferred; dependence on the pinned English message; suppressions live only in `google_checks.xml`; strictness locks), Riesgos y reversibilidad, Referencias (#298, ADR-0042, ADR-0024, `docs/14-TEST-STRATEGY.md`). (R9)
- [x] W0-18 `docs/adr/README.md`: add the index row for 0043 following the existing row format.
- [x] W0-19 `docs/adr/0042-security-tooling-baseline.md`: line 15 "8 módulos JVM" -> "7 módulos JVM (14 tareas)"; lines 24-25 replace "ocho módulos" with the 7 JVM modules count and replace "falla el build" with the real behavior (fails only above the per-task ceiling, see ADR-0043); add a dated correction note. (R9; the 8 is an error: root has only NO-SOURCE tasks)
- [x] W0-20 `docs/adr/0024-technology-baseline.md` line 26: Checkstyle 10.23.0 -> 13.8.0 (verify with `rg -n 'checkstyle' gradle/libs.versions.toml`). (R9)
- [x] W0-21 `docs/14-TEST-STRATEGY.md`: add the Spanish section "Estilo: trinquete de Checkstyle" (after the coverage section, before the technical notes): map location, "corregir o bajar, nunca subir", the `<MEASURE>` and JUnit-count commands, per-module strict flip, end state. (R9)
- [x] W0-22 Consistency sweep: `rg -n 'suppressions\.xml|LineLength=100|10\.23\.0|ocho módulos' docs config openspec/config.yaml build.gradle.kts CLAUDE.md` has no stale hit (archive excluded).
- [x] W0-23 Gates: `<ALL_TEST>` (after `cleanTest`) with `<JUNIT(dir)>` equal to W0-1, `<ALL_ARCH>`, `<ALL_JACOCO>` green with no threshold lowered.
- [x] W0-24 Size gate `<SIZE>` <= 800 (target 300-450); if above, STOP and ask (D5). Prepare the PR-body evidence text (T1-T5 trimmed outputs, 14-count table with delta explanation, JUnit table) for the orchestrator.

## W1.1: `imports-pilot` (PR 1, branch `feature/checkstyle-imports-pilot`)

Scope: `CustomImportOrder` in `api:app` test (85) and the others (42), NOT auth and bff. The pilot sub-tasks decide the command reused by W1.2 and W1.3.

- [x] W1.1-1 Branch-start: `<JUNIT(dir)>` before for all 7 modules; `<MEASURE>` and `<FILES(CustomImportOrder)>`; keep only files outside `api/auth` and `bff`.
- [x] W1.1-2 Pilot: obtain the google-java-format CLI locally (not added to the build); with the documented JDK 21 `--add-exports` flags run `--fix-imports-only --skip-removing-unused-imports --replace` on ONE file from `api/app/src/test`; verify the diff touches only import lines and that `:api:app:checkstyleTest` no longer reports `CustomImportOrder` for it.
- [x] W1.1-3 Pilot decisions recorded for the PR body: (a) the order matches `STATIC###THIRD_PARTY_PACKAGE`, ASCII sort, one blank line between groups; (b) whether to drop `--skip-removing-unused-imports` (default: keep it); (c) any file where the tool disagrees with Checkstyle: fix by hand if few, report to the orchestrator if more than about 30 (fallback is the IDE layout, per the design).
- [x] W1.1-4 Run the validated command over the remaining files of W1.1-1 (modules shared, billing, virtual, physical, app main and test).
- [x] W1.1-5 Verify import-only diff: `git diff -U0 | rg '^[+-]' | rg -v '^(\+\+\+|---)' | rg -v '^[+-](import |$)'` returns nothing.
- [x] W1.1-6 Re-measure `<MEASURE>`; set each touched task's ceiling in `build.gradle.kts` to the exact new count (never raise); `CustomImportOrder` should remain only in auth (154) and bff (209). Verify `<CS14>` passes with `--rerun-tasks --no-build-cache`.
- [x] W1.1-7 Gates: `<JUNIT(dir)>` after == before for every touched module; `<ALL_ARCH>`; `<ALL_JACOCO>`.
- [x] W1.1-8 `<SIZE>` <= 800 (target 300-400); if above, STOP and ask.
- [x] W1.1-9 PR evidence: exact command (flags and JDK `--add-exports`), pilot decisions, 14-count table, JUnit table.
- [ ] W1.1-10 [O] After merge: create `.git-blame-ignore-revs` with this slice's SHA (O-7).

## W1.2: `imports-auth` (PR 2, branch `feature/checkstyle-imports-auth`)

- [x] W1.2-1 Branch from updated `develop`; `<JUNIT(api/auth)>` before; `<MEASURE>`; `<FILES(CustomImportOrder)>` restricted to `api/auth`.
- [x] W1.2-2 Run the exact command validated in W1.1 over those files.
- [x] W1.2-3 Verify import-only diff (same `rg` check as W1.1-5).
- [x] W1.2-4 Re-measure; set `:api:auth:checkstyleMain` and `:api:auth:checkstyleTest` ceilings to the exact counts; `rtk proxy ./gradlew :api:auth:checkstyleMain :api:auth:checkstyleTest --rerun-tasks --no-build-cache` passes.
- [x] W1.2-5 Gates: `<JUNIT(api/auth)>` unchanged; `:api:auth:test --tests "*ArchitectureTest"`; `:api:auth:jacocoTestCoverageVerification` (auth 100%/85%, no threshold lowered).
- [x] W1.2-6 `<SIZE>` <= 800 (target 350-500); if above, STOP and ask.
- [x] W1.2-7 PR evidence (command, 14-count table, JUnit table).
- [ ] W1.2-8 [O] After merge: append this SHA to `.git-blame-ignore-revs` (O-7).

## W1.3: `imports-bff` (PR 3, branch `feature/checkstyle-imports-bff`)

- [x] W1.3-1 Branch from updated `develop`; `<JUNIT(bff)>` before; `<MEASURE>`; `<FILES(CustomImportOrder)>` restricted to `bff`.
- [x] W1.3-2 Run the exact command validated in W1.1 over those files.
- [x] W1.3-3 Verify import-only diff (same `rg` check as W1.1-5).
- [x] W1.3-4 Re-measure; set `:bff:checkstyleMain` and `:bff:checkstyleTest` ceilings to the exact counts.
- [x] W1.3-5 Lock (R5): confirm `CustomImportOrder` is 0 across all 14 tasks with `<RULES>`; only then add `<property name="severity" value="error"/>` to its module in `config/checkstyle/google_checks.xml`; `<CS14>` still passes (a premature lock fails the build).
- [x] W1.3-6 Gates: `<JUNIT(bff)>` unchanged; `:bff:jacocoTestCoverageVerification`; `<ALL_ARCH>` (bff has no ArchUnit).
- [x] W1.3-7 `<SIZE>` <= 800 (target 450-650, tight); if above, STOP and ask.
- [x] W1.3-8 PR evidence (command, 14-count table, JUnit table, `<RULES>` proof of 0).
- [ ] W1.3-9 [O] After merge: append this SHA to `.git-blame-ignore-revs` (O-7).

## W1.4: `javadoc-paragraph-a` (PR 4, branch `feature/checkstyle-javadoc-paragraph-a`)

Scope: JavadocParagraph (413) for module group A. The script is NOT committed; its exact text goes in the PR body.

- [x] W1.4-1 Branch-start: `<JUNIT(dir)>` before for the group; `<MEASURE>`; per-module JavadocParagraph counts from `<FILES(JavadocParagraph)>`; split the modules into groups A and B of about 200 findings each, keeping main and test of one module in the same PR; record the grouping.
- [x] W1.4-2 Write the one-off script outside the repo (session scratchpad) per the design spec: only inside `/** */`; after a blank ` *` line prefix the next text line with `<p>` unless it starts with `@`, `<p>` or a block tag (`<ul> <ol> <pre> <table> <h1-6> <blockquote>`); merge a lone `<p>` line into the next text line; `<p> x` -> `<p>x`.
- [x] W1.4-3 Pilot on one file of the smallest group-A module: diff shows comment lines only; `JavadocParagraph` reported 0 for that file.
- [x] W1.4-4 Run the script over group A files; verify `git diff -U0 | rg '^[+-]' | rg -v '^(\+\+\+|---)' | rg -v '^[+-]\s*(/\*\*|\*|\*/)'` returns nothing.
- [x] W1.4-5 Re-measure; LineLength must not increase (a merged `<p>` line can exceed 120): fix any new LineLength by manual wrap inside the comment; set ceilings of touched tasks to the exact counts (never raise).
- [x] W1.4-6 Gates: `<JUNIT(dir)>` unchanged; ArchUnit and JaCoCo for the group's modules.
- [x] W1.4-7 `<SIZE>` <= 800 (target 300-450); if above, STOP and ask.
- [x] W1.4-8 PR evidence: full script text, grouping, 14-count table, JUnit table.
- [ ] W1.4-9 [O] After merge: append this SHA to `.git-blame-ignore-revs` (O-7).

## W1.5: `javadoc-paragraph-b` (PR 5, branch `feature/checkstyle-javadoc-paragraph-b`)

- [x] W1.5-1 Branch from updated `develop`; `<JUNIT(dir)>` before for group B; `<MEASURE>`; confirm group B equals the remaining JavadocParagraph findings.
- [x] W1.5-2 Run the same script (as validated in W1.4) over group B files; verify comment-only diff (same `rg` check as W1.4-4).
- [x] W1.5-3 Re-measure; LineLength must not increase; set touched ceilings to exact counts.
- [x] W1.5-4 Lock (R5): `<RULES>` proves JavadocParagraph 0 across the 14 tasks, then add its `severity` `error` property in `google_checks.xml`; `<CS14>` passes.
- [x] W1.5-5 Gates: `<JUNIT(dir)>` unchanged; ArchUnit and JaCoCo for group B modules.
- [x] W1.5-6 `<SIZE>` <= 800 (target 300-450); if above, STOP and ask.
- [x] W1.5-7 PR evidence (script text, tables).
- [ ] W1.5-8 [O] After merge: append this SHA to `.git-blame-ignore-revs` (O-7).

## W1.6: `text-blocks` (PR 6, branch `feature/checkstyle-text-blocks`)

Scope: TextBlockGoogleStyleFormatting (156 findings, about 52 blocks), manual. Not a blame-ignore slice.

- [x] W1.6-1 Branch from updated `develop`; `<JUNIT(dir)>` before for touched modules; `<FILES(TextBlockGoogleStyleFormatting)>` list and per-module block count.
- [x] W1.6-2 Per block, manual edit per the design: opening `"""` on its own line, closing `"""` on its own line vertically aligned with the opening, content shifted by the same delta; if the original had no trailing newline, end the last content line with `\`. Work module by module and record the block count per module.
- [x] W1.6-3 Content identity check: for each edited block record old and new rendered value reasoning (closing-delimiter indentation drives stripping) and run the module tests that assert it; list blocks without an asserting test in the PR body for reviewer attention.
- [x] W1.6-4 Re-measure; set touched ceilings to exact counts; Lock (R5) `TextBlockGoogleStyleFormatting` once `<RULES>` shows 0 repo-wide.
- [x] W1.6-5 Gates: `<JUNIT(dir)>` unchanged for touched modules; their ArchUnit and JaCoCo.
- [x] W1.6-6 `<SIZE>` <= 800 (target 300-600); if above, STOP and propose a split by module group (D5).
- [x] W1.6-7 PR evidence: block list and per-module counts, tables.

## W1.7: `line-length-and-small-rules` (PR 7, branch `feature/checkstyle-line-length-small-rules`)

Scope: LineLength 116 plus about 99 findings: RegexpSinglelineJava 15, AvoidStarImport 14, EmptyLineSeparator 13, RightCurlyAlone 12, LeftCurlyEol 12, RequireEmptyLineBeforeBlockTagGroup 11, SingleLineJavadoc 10, Indentation 5, JavadocContentLocation 4, OverloadMethodsDeclarationOrder 2, JavadocTagContinuationIndentation 1. Tightest Phase 1 slice.

- [x] W1.7-1 Branch from updated `develop`; `<JUNIT(dir)>` before for all 7 modules; `<MEASURE>`, `<RULES>`, `<FILES(<Rule>)>` per rule above (counts may have shifted after W1.1-W1.6; record them).
- [x] W1.7-2 LineLength (about 116): wrap before binary operators and `?`, before `.` and `::`, after `,` and `->`, continuation +4; an unwrappable single token: shorten the identifier (no `CHECKSTYLE.SUPPRESS` comments). Run `<CS14>` after each module to confirm the wrap did not move other rules.
- [x] W1.7-3 AvoidStarImport: replace each star import with explicit imports (compile the module to find the used classes); keep `CustomImportOrder` at 0.
- [x] W1.7-4 Braces and layout: RightCurlyAlone, LeftCurlyEol, EmptyLineSeparator, Indentation (formatting only, no semantic edits).
- [x] W1.7-5 Javadoc layout: RequireEmptyLineBeforeBlockTagGroup, SingleLineJavadoc, JavadocContentLocation, JavadocTagContinuationIndentation.
- [x] W1.7-6 OverloadMethodsDeclarationOrder (2): move overloads adjacent without changing code; RegexpSinglelineJava (15): read the rule's regex and message in `google_checks.xml`, fix each hit by the minimal edit that satisfies it (no suppression).
- [x] W1.7-7 Re-measure; set touched ceilings to exact counts; Lock (R5) every rule that `<RULES>` shows at 0 repo-wide (expected: the small rules and LineLength); `<CS14>` passes.
- [x] W1.7-8 Gates: `<JUNIT(dir)>` unchanged for all modules (overload moves must not drop a test); `<ALL_ARCH>`; `<ALL_JACOCO>`.
- [x] W1.7-9 `<SIZE>` <= 800 (target 500-750). If above, STOP and ask; the proposed split is at the rule boundary (LineLength = W1.7a; small rules = W1.7b).
- [ ] W1.7-10 PR evidence (per-rule before/after table, locks added, tables).

## W2.0: Phase 2 checkpoint (MANDATORY, no PR of its own)

The module list and order below are PROVISIONAL (current D1-D3 totals: shared 17, physical 151, app 163, virtual 198, billing 283, auth 474, bff 580; those include imports and paragraphs fixed in Phase 1).

- [x] W2.0-1 After W1.7 merges, from the updated `develop`: `<MEASURE>` and `<RULES>`; build the per-module, per-rule residual table (MissingJavadocMethod/Type, SummaryJavadoc, VariableDeclarationUsageDistance, MethodName 5, AbbreviationAsWordInName 2, TypeName 1 and anything else left).
- [x] W2.0-2 Order modules ascending by residual; estimate lines per module (about 4 per missing Javadoc, 1-3 for the rest); decide the auth and bff split into 2 PRs each (and whether any other module needs 2, or two small modules can share one PR) so each PR stays <= 800.
- [x] W2.0-3 Record the fixed order, split and per-PR estimates; decide whether W3 will fold into the last W2 PR (needs an estimate <= 800 for that PR plus 50-120).
- [x] W2.0-4 [O] Record the checkpoint in Engram, amend this file's W2 sections (rename and reorder as needed) and create the W2 sub-issues (O-8).

**W2.0 checkpoint result (recorded 2026-10-04, `develop` @ c275e5c, fresh `--rerun-tasks --no-build-cache`; user-approved):** 592 findings left, all Phase 2 rules. Per module main/test: shared 2/0, bff 2/2, app 17/31, auth 84/3, physical 123/5, billing 143/5, virtual 170/5. Per rule: MissingJavadocMethod 390, MissingJavadocType 85, SummaryJavadoc 64, VariableDeclarationUsageDistance 45, MethodName 5, AbbreviationAsWordInName 2, TypeName 1. FIXED order and split (replaces the provisional list below): **W2.1** shared+bff+app (one PR, ~170 lines), **W2.2** auth (one PR, ~320), **W2.3** physical (~480), **W2.4** billing (~560), **W2.5** virtual (~700, tightest: split by package if > 800), then **W3** as its own PR (does NOT fold: W2.5 is too close to the budget). Each module flips to strict in its own PR. Sub-issues: W2.1 #342, W2.2 #343, W2.3 #344, W2.4 #345, W2.5 #346, W3 #332. Javadoc quality bar: follow `AGENTS.md` (responsibility, semantics, invariants, non-obvious effects; never restate the signature).


## W2.x: per-module slices (FIXED at the W2.0 checkpoint: shared+bff+app -> auth -> physical -> billing -> virtual)

Module task template (each section below instantiates tasks n-1..n-9 for its module `<mod>`; focused verification: `rtk proxy ./gradlew :<mod>:checkstyleMain :<mod>:checkstyleTest --rerun-tasks --no-build-cache` plus `:<mod>:cleanTest :<mod>:test`, `:<mod>:test --tests "*ArchitectureTest"` where the module has ArchUnit, and `:<mod>:jacocoTestCoverageVerification`). Rules for every module: no behavior change, no `CHECKSTYLE.SUPPRESS`, Javadoc truthful (third-person summary sentence ending with a period), `final` only where order is semantically required.

Template task text:
- n-1 Branch from updated `develop`; `<JUNIT(dir)>` before; `<MEASURE>`; `<FILES(MissingJavadocMethod)>`, `<FILES(MissingJavadocType)>`, per-rule list for the module's two tasks.
- n-2 Write the missing Javadoc (MissingJavadocMethod and MissingJavadocType) in main and test sources per the report.
- n-3 Fix SummaryJavadoc and any other leftover rule of the module (RequireEmptyLine, SingleLineJavadoc, etc.).
- n-4 Fix VariableDeclarationUsageDistance by moving the declaration next to its first use.
- n-5 Renames where they fall in the module (see section notes), including leftover `TypeName` and `AbbreviationAsWordInName` hits; rename call sites in the same module only.
- n-6 Decision: `<SIZE>`; if > 800, STOP and ask for a split by package (never `size:exception`).
- n-7 Verify 0/0: `:<mod>:checkstyleMain :<mod>:checkstyleTest` report 0 findings; then `<JUNIT(dir)>` unchanged, ArchUnit, JaCoCo for the module.
- n-8 Flip (R4): add `:<mod>` to `checkstyleStrictModules` and delete its two ceiling keys in the SAME PR; run the module's two tasks again (no stale warning, passing under `severity=error`).
- n-9 Lock any rule that now reaches 0 repo-wide (R5) and update the PR evidence tables.

- [x] W2.1 `api:shared` + `bff` + `api:app` (sub-issue #342; residual 2 + 4 + 48; one PR, ~170 lines). Tasks W2.1-1..W2.1-9 per template, applied to the three modules; n-5 includes the 3 `aXxx` helper renames in `api/app/src/test/java/com/menta/app/catalog/CatalogControllerTest.java`; n-8 flips all three modules to strict and deletes their six ceiling keys; no ArchUnit for bff. Extra: W2.1-10 first flip only: inject one temporary finding in `api:shared` main and confirm failure with `error` severity (R4 "Finding in a strict module"), then revert the injection.
- [x] W2.2 `api:auth` (sub-issue #343; residual 87; one PR, ~320 lines). Tasks W2.2-1..W2.2-9 per template; flips to strict in this PR.
- [x] W2.3 `api:physical` (sub-issue #344; residual 128; ~480 lines). Tasks W2.3-1..W2.3-9 per template; n-5 includes the `TypeName` (1) and `AbbreviationAsWordInName` (2) leftovers (done: `AssignmentOutcome.ASSIGNED`/`RACE_LOST` -> `Assigned`/`RaceLost`; flipped to strict; `TypeName` and `AbbreviationAsWordInName` locked).
- [x] W2.4 `api:billing` (sub-issue #345; residual 148; ~560 lines). Tasks W2.4-1..W2.4-9 per template (done: 143 Javadocs, 5 declaration moves/`final`, flipped to strict; 707 changed lines).
- [x] W2.5 `api:virtual` (sub-issue #346; residual 175; ~700 lines, tightest). Tasks W2.5-1..W2.5-9 per template; n-5 includes the 2 `aCourseEntity` renames in `api/virtual/src/test/java/com/menta/virtual/infrastructure/persistence/adapter/VirtualCourseRepositoryAdapterTest.java` (lines 40 and 44 and call sites); W2.5-10 split boundary decision by package if the estimate exceeds 800 (never `size:exception`). (done: 168 Javadocs, 4 declaration moves/`final`, `aCourseEntity` -> `courseEntity`, flipped to strict, ceiling map now empty, 5 rules locked; 404 changed lines, no split needed).

## W3: `severity-error-default` (final PR, branch `feature/checkstyle-severity-error-default`, does NOT fold into W2.5 (decided at W2.0))

Satisfies R8 and the end-state half of R9. Strict TDD: RED (injection) before GREEN.

- [x] W3-1 Branch from updated `develop`; `<JUNIT(dir)>` before for all 7 modules; `<MEASURE>`: 14 tasks at 0 warnings and 0 errors, all 7 modules in `checkstyleStrictModules`, ceilings map empty or only stale keys. Any non-zero: stop and report.
- [x] W3-2 (own PR, decided at W2.0) Decision: if the last W2 PR plus about 120 lines fits 800, do W3-3..W3-8 inside that PR (it then carries `Closes #298`); otherwise continue as its own PR.
- [x] W3-3 `config/checkstyle/google_checks.xml` line 23: `default="warning"` -> `default="error"`; remove the per-rule `severity` `error` locks that are now redundant (rules inheriting the default); keep `<RULES>` proof of 0 before removing.
- [x] W3-4 Root `build.gradle.kts`: delete `checkstyleWarningCeiling`, `checkstyleStrictModules`, the `configureEach` block, the conditional `configProperties` and the `Checkstyle` import; set `maxWarnings = 0` in the `checkstyle {}` block for all JVM modules.
- [x] W3-5 RED injection (R8 "Any finding fails"): add one temporary violation (for example a star import) to one main and one test source of each of the 7 modules; `rtk proxy ./gradlew <CS14> --rerun-tasks --no-build-cache --continue` fails in all 14 tasks; capture trimmed output; revert every injected file with `git restore <file>` and confirm `git status` shows no leftover.
- [x] W3-6 GREEN: `rtk proxy ./gradlew <CS14> --rerun-tasks --no-build-cache` passes with 0 warnings and 0 errors; `rg -n 'checkstyleWarningCeiling|checkstyleStrictModules' build.gradle.kts` has no match; then `<ALL_CHECK>`.
- [x] W3-7 Docs (Spanish): ADR-0043 end-state note (default `error`, map deleted, per-rule locks removed); ADR-0042 statement updated to "cualquier hallazgo falla el build"; `docs/14-TEST-STRATEGY.md` section updated (remove ceilings workflow, keep measurement commands and the rule that new findings fail); `rg -n 'techo|ceiling' docs/14-TEST-STRATEGY.md docs/adr/0042-security-tooling-baseline.md docs/adr/0043-checkstyle-policy-ratchet.md` shows no stale instruction.
- [x] W3-8 Gates (R8 "No behavior change"): `<ALL_TEST>` after `cleanTest` with `<JUNIT(dir)>` equal to the W3-1 values and explained against the W0-1 table (differences only from feature PRs merged in between, listed from `git log`); `<ALL_ARCH>`; `<ALL_JACOCO>` with no threshold lowered.
- [x] W3-9 `<SIZE>` <= 800 (target 50-120, or the folded PR total); if above, STOP and ask.
- [ ] W3-10 [O] The PR body carries `Closes #298`; after merge run the verify/archive flow (outside this checklist).

## Requirement traceability

| Req | Spec requirement | Implemented by | Verified by |
|-----|------------------|----------------|-------------|
| R1 | Per-task warning ceiling | W0-11 (map and `maxWarnings`), W1.x/W2.x ceiling lowering tasks | W0-13 (T2: 14 failures), W0-14 (T3 cache), W0-16, every slice's re-measure task |
| R2 | A missing ceiling fails loudly | W0-11 (`GradleException` in `configureEach`), strict-wins branch | W0-12 (T1 names `:api:billing:checkstyleTest`); strict-needs-no-ceiling covered by W2.1-8 and W0-15 |
| R3 | Ceilings lowered, never raised | Per-slice "set ceilings to exact count" tasks (W1.x-6/-4, W2.x n-1/n-7, O-5) and the measurement tables in each PR body | PR diff review of the map (process); each slice's `--rerun-tasks` run (count equals ceiling); slack decision (A, no fail-on-slack) stated in design and ADR-0043 (W0-17) |
| R4 | Module strictness | W0-11 (`configProperties`, strict set), W2.x n-8 flips | W0-15 (T4 `[error:N]`, `-D` property no effect), W2.1-10 (injection), W2.x n-8 |
| R5 | Per-rule lock | W1.3-5, W1.5-4, W1.6-4, W1.7-7, W2.x n-9; W3-3 removal | `<RULES>` proof of 0 before each lock; `<CS14>` still green; premature lock fails the build |
| R6 | Style policy in the config | W0-3, W0-4, W0-5, W0-6 | W0-9, W0-10 (count delta, 113 -> 85, ArchitectureTest rg check) |
| R7 | No dead suppression file | W0-7 (delete) | W0-7 `rg`, W0-22 sweep, W0-16 (14 tasks still run) |
| R8 | End state, any finding fails | W3-3, W3-4 | W3-5 (RED injection), W3-6 (GREEN), W3-8 (JUnit, ArchUnit, JaCoCo); JUnit/ArchUnit/JaCoCo checks in every slice |
| R9 | Policy documentation | W0-8, W0-17, W0-18, W0-19, W0-20, W0-21; W3-7 | W0-22 sweep, W0-3 (comment 324), `rg` checks in W0-8/W0-20 |
| R10 | Blame-ignore revisions | O-7, W1.1-10, W1.2-8, W1.3-9, W1.4-9, W1.5-8 | `git cat-file -t <sha>` = `commit` after each merge; W1.6, W1.7, W2.x, W3 SHAs are NOT listed |

## Task counts

| Unit | Tasks |
|------|-------|
| Tracking (O-1..O-9) | 9 (all orchestrator) |
| W0 | 24 |
| W1.1 | 10 (1 orchestrator) |
| W1.2 | 8 (1 orchestrator) |
| W1.3 | 9 (1 orchestrator) |
| W1.4 | 9 (1 orchestrator) |
| W1.5 | 8 (1 orchestrator) |
| W1.6 | 7 |
| W1.7 | 10 |
| W2.0 | 4 (1 orchestrator) |
| W2.x | 7 provisional sections (9 template tasks each, 9 PR slots with auth and bff split) |
| W3 | 10 (1 orchestrator) |

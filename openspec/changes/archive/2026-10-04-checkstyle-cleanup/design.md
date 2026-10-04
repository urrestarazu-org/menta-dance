# Design: Checkstyle cleanup and ratchet (#298)

> Inputs: `proposal.md`, `exploration.md`, `specs/code-style-gate/spec.md`, Engram `sdd/checkstyle-cleanup/decisions` (D1-D5). Size note: this design exceeds the 800-word skill budget because the orchestrator requested 10 decisions plus per-slice guidance; tables keep it scannable.

## Technical Approach

Native Gradle ratchet, no custom task: every JVM `Checkstyle` task gets `maxWarnings` from a root map keyed by **task path**; a missing key throws at task configuration; a strict module runs with `configProperties` severity `error`. Phase 0 changes policy (D1-D3) first, then measures a fresh baseline, then freezes ceilings. Phases 1-2 fix warnings and lower ceilings in the same PR; Phase 3 makes `error` the default and deletes the mechanism.

## Architecture Decisions

| # | Topic | Options and tradeoffs | Decision |
|---|---|---|---|
| 1 | Location | (a) Inline in root `build.gradle.kts`: one consumer, ~20 lines, deleted in Phase 3 by editing one file. (b) `buildSrc` helper like `LayeredCoverage.kt`: reusable/testable in theory, but a single consumer, any edit recompiles every build script, and buildSrc has no test source set (Gradle 8+ does not run buildSrc tests during a normal build, so a unit test would not gate CI without extra wiring). | **(a) Inline.** Map `checkstyleWarningCeiling` directly below `moduleCoverageFloor`; mechanism in the `subprojects` JVM branch next to the existing `checkstyle {}` block (lines 163-166). Key = `Task.path` (e.g. `:api:auth:checkstyleTest`), so no source-set parsing; it matches the CI task list and Gradle's own output. One entry per line, sorted by key, trailing comma. |
| 2 | Missing key | Throw in `tasks.withType<Checkstyle>().configureEach`; vs eager validation in `afterEvaluate`; vs default ceiling. | **Throw `GradleException` in `configureEach`** naming the task path and the map. Fails when the task is realized (`build`/`check`/CI always realize it). Cache safety: the lookup runs at configuration time and only an `Int` reaches the task; `maxWarnings` and `configProperties` are task `@Input`s (per Gradle API docs; re-verified by tamper run T3), so changing a ceiling changes the cache key; no `project` access at execution time. Note: `gradle.properties` has `org.gradle.configuration-cache=false` (contradicts `openspec/config.yaml:16`); the design is CC-compatible regardless. |
| 3 | Slack | (A) Fail only when count > ceiling (native). (B) Also fail when count < ceiling: needs XML parsing in a `doLast`/extra task, is bypassed on `FROM-CACHE`, and forces every feature PR that removes a warning to edit the map (more stacked-PR conflicts). Still does not catch rule swaps. (C) Non-failing "can be lowered to N" log: same custom parsing for a temporary phase. | **(A).** Slack passes (spec scenario "Slack outcome": pass). D4 "every PR lowers its ceilings" is enforced as process: each cleanup PR re-measures and commits exact counts, with the measurement table in the PR body; any slack left by feature PRs is reclaimed by the next cleanup PR touching that module. |
| 4 | Strictness | Ceiling-only vs per-module `strictModules` vs per-rule locks. | **All three, with fixed precedence.** `checkstyleStrictModules: Set<String>` (module paths). Strict module: `configProperties["org.checkstyle.google.severity"]="error"`, `maxWarnings=0`; it needs no ceiling. If a strict module still has a ceiling entry: **strict wins**, the entry is ignored and a `logger.warn` names it as stale (keeps spec scenario "Finding in a strict module" satisfiable; failing instead would require a spec amendment). Non-strict module: both keys mandatory. Per-rule lock: `<property name="severity" value="error"/>` on the rule's module in `google_checks.xml`, only when the rule is 0 across all 14 tasks; locked findings are errors (default `maxErrors=0`) and never consume a ceiling. A module flips in the Phase 2 PR that brings both its tasks to 0: same PR deletes its two keys and adds it to the set. Flip order = Phase 2 order, ascending residual re-measured after Phase 1. |
| 5 | `suppressions.xml` | Wire via `${config_loc}`: note Gradle's `config_loc` is `checkstyle.configDirectory` (documented default `<rootProject>/config/checkstyle`), not the config file's directory; whether `configDirectory` is a task input is unverified. Delete: its only entry (`MethodName` in `ArchitectureTest.java`) is redundant because every `ArchitectureTest` method is `@Test` with names matching the relaxed regex (verified by reading auth `ArchitectureTest`). | **Delete.** One suppression surface (inline in `google_checks.xml`), no cache-input question. Upstream optional `SuppressionFilter`/`SuppressionXpathFilter` modules (lines 35-39, 492-497) stay untouched to minimize divergence from upstream; ADR-0043 states suppressions live only in `google_checks.xml`. |
| 6 | Tamper checks | Committed TestKit/functional test (needs fixture project, Checkstyle download, new buildSrc test wiring, not run by CI without changes) vs PR evidence. | **PR evidence**, ordered RED before GREEN (see Testing Strategy). Precedent: the #96 JaCoCo gate was proven empirically and documented. After merge, CI on every PR is the standing regression test of the gate. No new dependency. |
| 7 | Measurement helper | Committed script vs documented command vs Gradle report task. | **Documented commands, no committed helper** (two commands; avoids a new script surface). Written in `docs/14-TEST-STRATEGY.md`. |

## Data Flow

    root build.gradle.kts
      checkstyleWarningCeiling (task path -> Int)   checkstyleStrictModules
                 \                                   /
      subprojects { JVM branch: checkstyle{} + tasks.withType<Checkstyle>().configureEach }
                 |  (configuration time: lookup or throw; Int + configProperties as @Input)
                 v
      :mod:checkstyle{Main,Test} -> Checkstyle 13.8.0 + google_checks.xml
                 |
                 v
      build/reports/checkstyle/{main,test}.xml -> Gradle compares [warning:N] vs maxWarnings,
                                                  [error:M] vs maxErrors=0 -> pass/fail
                 |
                 v
      CI: `./gradlew clean build` (check -> checkstyle*) + explicit 14-task step

## Interfaces / Contracts

```kotlin
// Root build.gradle.kts, below moduleCoverageFloor. Values are calibrated in Phase 0.
val checkstyleWarningCeiling = mapOf(
    ":api:app:checkstyleMain" to 24,
    ":api:app:checkstyleTest" to 139,
    // ... 14 entries, sorted, one per line
)
val checkstyleStrictModules = setOf<String>()

// subprojects JVM branch (import org.gradle.api.plugins.quality.Checkstyle)
val strict = project.path in checkstyleStrictModules
checkstyle {
    toolVersion = checkstyleVersion
    configFile = rootProject.file("config/checkstyle/google_checks.xml")
    if (strict) configProperties = mapOf("org.checkstyle.google.severity" to "error")
}
tasks.withType<Checkstyle>().configureEach {
    val ceiling = checkstyleWarningCeiling[path]
    maxWarnings = when {
        strict -> 0.also { if (ceiling != null) logger.warn("Stale Checkstyle ceiling for $path (strict module); remove it.") }
        ceiling != null -> ceiling
        else -> throw GradleException("No Checkstyle warning ceiling for $path: add it to checkstyleWarningCeiling in the root build.gradle.kts with its measured count (ADR-0043).")
    }
}
```

Phase 3 end state: `google_checks.xml:23` `default="error"`, `checkstyle { maxWarnings = 0 }` for all JVM modules (belt for any rule explicitly set to `warning`), map/set/`configureEach`/`configProperties` and the now-redundant per-rule locks removed.

## Measurement Procedure (Phase 0 baseline and every PR)

One Gradle run at a time; `rtk proxy` to get unfiltered output. Explicit task list (same as CI) instead of name-matched `checkstyleMain`, which also selects the root project's NO-SOURCE tasks.

```bash
T=":api:shared:checkstyleMain :api:shared:checkstyleTest :api:auth:checkstyleMain :api:auth:checkstyleTest :api:billing:checkstyleMain :api:billing:checkstyleTest :api:virtual:checkstyleMain :api:virtual:checkstyleTest :api:physical:checkstyleMain :api:physical:checkstyleTest :api:app:checkstyleMain :api:app:checkstyleTest :bff:checkstyleMain :bff:checkstyleTest"
rtk proxy ./gradlew $T --rerun-tasks --no-build-cache --continue
rg -c --include-zero '<error ' api/*/build/reports/checkstyle/{main,test}.xml bff/build/reports/checkstyle/{main,test}.xml
# per rule (lock decisions): rg -o --no-filename 'source="[^"]+"' <same files> | sort | uniq -c
```

`--continue` keeps reports for every task even when one exceeds its ceiling (reports are written before the threshold check). Phase 0 compares the 14 counts to the projection (1,866: app 24/139, auth 382/92, bff 252/328, billing 216/67, physical 140/11, shared 10/7, virtual 182/16) and explains any delta before freezing. JUnit totals per module (before/after every PR): sum `tests=`/`skipped=` of `<testsuite>` in `<module>/build/test-results/test/*.xml`.

## Slicing Guidance

| Slice | Tool / rule | PR must verify |
|---|---|---|
| 0 | Config D1-D3, delete `suppressions.xml`, baseline, map, docs | T1-T5 below; 14 counts table; no `ArchitectureTest` MethodName finding |
| 1.1 pilot | `google-java-format --fix-imports-only --skip-removing-unused-imports --replace` (CLI, not added to build; JDK 21 needs gjf's documented `--add-exports`). **Piloted:** order match with `CustomImportOrder` (`STATIC###THIRD_PARTY_PACKAGE`, ASCII sort, blank line between groups) and whether to drop `--skip-removing-unused-imports` | CustomImportOrder 0 on touched tasks; diff touches only import lines |
| 1.2-1.3 | Same command as validated in 1.1 | Same |
| 1.4-1.5 | One-off JavadocParagraph script (**piloted**, not committed; exact script in PR body). Spec: only inside `/** */`; after a blank ` *` line, prefix the next text line with `<p>` unless it starts with `@`, `<p>` or a block HTML tag (`<ul> <ol> <pre> <table> <h1-6> <blockquote>`); merge a lone `<p>` line into the next text line; `<p> x` -> `<p>x` | JavadocParagraph 0; `git diff -U0` shows only `*` comment lines; LineLength unchanged |
| 1.6 | TextBlockGoogleStyleFormatting, manual: opening `"""` on its own line, closing `"""` on its own line vertically aligned with the opening; shift content by the same delta; if the original had no trailing newline, end the last content line with `\` | Tests pass; reviewer checks each block's content is byte-identical |
| 1.7 | LineLength 120 + small rules. Wrap: before binary operators/`?` (OperatorWrap NL), before `.` and `::` (SeparatorWrap nl), after `,` and `->`, continuation +4. Unwrappable single token: shorten the identifier (no `CHECKSTYLE.SUPPRESS` comments) | Counts lowered; tests/ArchUnit/JaCoCo green |
| 2.x | Per module to 0: Javadoc (truthful summary sentence, third person, ends with period), SummaryJavadoc, VariableDeclarationUsageDistance (move declaration; `final` only when order is semantically required), 5 MethodName + 2 AbbreviationAsWordInName + 1 TypeName renames; flip to strict | Module 0/0; keys removed; module in set |
| 3 | Default `error`, `maxWarnings = 0`, delete mechanism, docs | 14 tasks 0/0; end-state injection fails |

Every PR: measurement table, ceilings equal to measured for changed tasks, JUnit totals unchanged, `*ArchitectureTest` and `jacocoTestCoverageVerification` green, CI green. When a rule reaches 0 repo-wide, the same PR adds its lock.

`.git-blame-ignore-revs` (mechanical slices 1.1-1.5 only, per spec): after squash merge, take `gh pr view <n> --json mergeCommit -q .mergeCommit.oid`, append `# <slice> (#<n>)` plus the full SHA as the first commit of the next PR (standalone `chore` PR if none follows); verify `git cat-file -t <sha>` = `commit`.

## File Changes

Phase 0:

| File | Action | Description |
|---|---|---|
| `build.gradle.kts` | Modify | Ceilings map + strict set below `moduleCoverageFloor`; `configureEach` and conditional `configProperties` in JVM branch; `Checkstyle` import |
| `config/checkstyle/google_checks.xml` | Modify | LineLength 120 (57) and comment (324); MethodName suppression regex (471) + comment on the pinned English message (463-464); AbbreviationAsWordInName `@Test` XPath suppression (no `message`); MissingJavadocMethod tokens without `CTOR_DEF`/`COMPACT_CTOR_DEF`, `allowedAnnotations` += 9 (441-443); MissingJavadocType nested-type XPath suppression (`[ancestor::OBJBLOCK]`, verify 113 -> 85) |
| `config/checkstyle/suppressions.xml` | Delete | Dead and redundant |
| `docs/adr/0043-checkstyle-policy-ratchet.md` | Create | Spanish ADR (outline below) |
| `docs/adr/README.md` | Modify | Index row 0043 |
| `docs/adr/0042-security-tooling-baseline.md` | Modify | Lines 15/24: "7 módulos JVM (14 tareas)"; failure only above the per-task ceiling (ADR-0043), with a dated correction note |
| `docs/adr/0024-technology-baseline.md` | Modify | Line 26: 13.8.0 |
| `docs/14-TEST-STRATEGY.md` | Modify | Spanish section "Estilo: trinquete de Checkstyle": map location, "corregir o bajar, nunca subir", measurement commands, strict flip, end state |
| `openspec/config.yaml` | Modify | Line 17: LineLength=120 |

Later phases: `api/*/src`, `bff/src` (code/comments only), `build.gradle.kts` (ceilings, strict set; Phase 3 deletion), `google_checks.xml` (locks; Phase 3 default), `.git-blame-ignore-revs` (Create after 1.1), `docs/14-TEST-STRATEGY.md` + ADR-0043 (Phase 3 end-state note).

ADR-0043 outline ("Política de Checkstyle y trinquete", template `docs/adr/template.md`): Contexto (warning-only, 6,768, ADR-0042 claim); Factores; Opciones (status quo, big-bang, techos por tarea [elegida], supresiones de baseline, herramienta diff); Decisión; Justificación (known limit: rule swaps, slack); Consecuencias (feature PRs red by design; only exception to "never raise": a Checkstyle version bump PR recalibrates; snake_case tolerated, camelCase deferred; message-pin fragility; LineLength 120 and Javadoc scope); Riesgos y reversibilidad; Referencias (#298, ADR-0042, ADR-0024, docs/14).

## Testing Strategy

Strict TDD for build config = RED captured before GREEN, recorded in the PR body (commands + trimmed output).

| Id | Evidence | Expected |
|---|---|---|
| T1 RED | Wiring with one key removed (`:api:billing:checkstyleTest`) | Build fails; message names the path |
| T2 RED | All 14 ceilings = measured - 1, `--continue` | 14 task failures with `[warning:N]` (also covers "every task is gated": all 14 counts > 0) |
| T3 cache | Green at N, set one ceiling to N-1, run **without** `--rerun-tasks/--no-build-cache` | Fails (not `UP-TO-DATE`/`FROM-CACHE`), proving `maxWarnings` is an input |
| T4 strict | Temporarily add `:api:shared` to the strict set | Fails `[error:N]`; `-Dorg.checkstyle.google.severity=error` on a non-strict module changes nothing |
| T5 GREEN | Exact ceilings | 14 tasks pass; `clean build` green |

Policy changes are verified by the Phase 0 count delta and by `rg -P -U '<file name="[^"]*ArchitectureTest\.java">(?:(?!</file>)[\s\S])*?MethodName' api/*/build/reports/checkstyle/test.xml` (no match). Cleanup PRs: unchanged JUnit totals, ArchUnit and JaCoCo are the behavior safety net.

## Threat Matrix

N/A: no routing, subprocess, VCS/PR automation, executable-file classification or process-integration boundary; commands are manual developer steps and one-off scripts are not committed.

## Migration / Rollout

No open PRs today (re-check `gh pr list --state open` before merging Phase 0). After Phase 0, a feature PR that adds warnings is red by design: fix (or lower), never raise. Two PRs touching the same key: the later rebases and re-measures; never resolve a ceiling conflict by choosing a number. Rollback: revert in reverse merge order; each PR carries its own ceilings, and reverting Phase 0 restores warning-only behavior.

## Assumptions / Unverified

- `maxWarnings`/`configProperties` as task inputs and `config_loc` = `configDirectory`: from Gradle docs, not re-read here; T3 covers the first.
- gjf ordering parity, JavadocParagraph script, text-block rule details: piloted by implementation.
- If release PRs into `main` are squashed, develop SHAs in `.git-blame-ignore-revs` do not exist on `main`; check `git blame` behavior there.
- Design defaults (not user decisions, reversible in review): no `CHECKSTYLE.SUPPRESS` comments for unwrappable lines; blame-ignore limited to 1.1-1.5 as the spec states; ADR-0042 "ocho módulos" read as an error (7 JVM modules carry Checkstyle; root has only NO-SOURCE tasks).

## Open Questions

- None blocking design. (Board field "Prioridad" for #298 remains a non-design item from the proposal.)

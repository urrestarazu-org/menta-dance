# Proposal: Checkstyle cleanup and ratchet (#298)

> Inputs: `exploration.md` (Engram `sdd/checkstyle-cleanup/explore`) and the locked user decisions D1-D5 (Engram `sdd/checkstyle-cleanup/decisions`). The proposal question round was completed by the orchestrator before this phase; decisions are not re-litigated here.

## Intent

- Checkstyle is warning-only: `google_checks.xml:23` defaults severity to `warning`, and the root `build.gradle.kts` sets no `maxWarnings`. The build cannot fail on style.
- `docs/adr/0042-security-tooling-baseline.md:24` claims Checkstyle "falla el build" (false); `docs/adr/0024-technology-baseline.md:26` lists 10.23.0 (real: 13.8.0).
- Debt grows with every PR (e.g. 25 MethodName warnings on the lines added by #108). Baseline on `develop` @ `56ed6e4`: **6,768** warnings (main 2,793 / test 3,975).
- Why now: no open PRs (no in-flight conflicts), the root cause of the MethodName noise is understood, and a native Gradle ratchet was verified by spike.
- Success: zero Checkstyle findings in the 14 JVM tasks, every finding fails the build, and the policy is documented in ADR-0043.

## Scope

### In Scope
- Config policy (D1-D3): LineLength 100 -> 120 (keep the ignore pattern); relaxed `@Test` MethodName suppression regex `'[A-Za-z][A-Za-z0-9]*(?:_[A-Za-z0-9]+)*'` plus a `@Test` XPath suppression for AbbreviationAsWordInName (no `message`); MissingJavadocMethod without `CTOR_DEF`/`COMPACT_CTOR_DEF` and with `allowedAnnotations` += `Bean, GetMapping, PostMapping, PutMapping, PatchMapping, DeleteMapping, RequestMapping, ExceptionHandler, Scheduled`; MissingJavadocType exempts nested types through an XPath suppression. Projection: 6,768 -> **1,866**.
- Repair the dead `config/checkstyle/suppressions.xml` (wire through `${config_loc}` or delete it).
- Ratchet that breaks CI from Phase 0 (D4): per-task `maxWarnings` ceilings map (module x main/test) beside `moduleCoverageFloor`, failing loudly on a missing key; per-module `configProperties` severity `error` at zero; final PR makes `error` the default and deletes the map.
- Fix all 1,866 residual warnings across `api:*` and `bff`, sliced to the 800 changed-line budget (D5).
- Docs: new ADR-0043 "Politica de Checkstyle y trinquete" (written in Spanish), ADR-0042/0024 corrections, ratchet rule documentation (Spanish), `openspec/config.yaml:17` and `google_checks.xml:324` wording (LineLength 120).

### Out of Scope (non-goals)
- camelCase rename of the ~2,700 snake_case test methods (deferred, D2). Snake_case test names are tolerated, not endorsed; ADR-0043 records the deferral.
- Full formatter adoption (google-java-format/Spotless, `.editorconfig`): continuation indent 4 vs gjf 8 conflicts with the adapted ruleset.
- Android style tooling.
- Any runtime behavior change; any Checkstyle version bump; CI workflow changes (`pr-develop.yml`/`pr-main.yml` already run `clean build` and the `checkstyle*` tasks).

## Capabilities

### New Capabilities
- `code-style-gate`: build-level guarantees for the JVM modules: Checkstyle findings above a task ceiling fail the build, a missing ceiling key fails the build, a module at zero runs with severity `error`, and the end state is "any finding fails the build". Thin spec (about 5-7 requirements, command-verifiable scenarios).

### Modified Capabilities
- None.

## Approach

### Ratchet design (D4) and known limit
- Numbers in one root map (mirrors `moduleCoverageFloor`); mechanism through `tasks.withType<Checkstyle>` (location, root vs `buildSrc`, is a design decision).
- Known limit: a total per task masks rule swaps (fix 3 of rule A, add 3 of rule B) and slack hides regressions.
- Mitigations: (1) every cleanup PR sets its ceilings to the exact new measured count in the same PR; (2) per-rule `severity=error` lock in `google_checks.xml` once a rule reaches 0 repo-wide; (3) per-module `severity=error` once both source sets reach 0. Design should also evaluate failing when count < ceiling (forces lowering, removes slack).

### Phased delivery (stacked onto `develop`, 800-line budget per PR)

| # | Slice | Contents | Est. changed lines | Confidence |
|---|---|---|---|---|
| 0 | `config-ratchet-docs` | Suppression fixes, suppressions.xml wiring/deletion, LineLength 120, Javadoc scope, ceilings calibrated **after** those fixes from a fresh `--rerun-tasks --no-build-cache` baseline, ADR-0043 + doc fixes, tamper evidence (ceiling-1 fails, missing key fails) per module x source set | 250-400 | Medium |
| 1.1 | `imports-pilot` | CustomImportOrder app test 85 + others 42, google-java-format `--fix-imports-only` one-off CLI (pilot: verify its order matches Checkstyle and decide unused-import removal) | 300-400 | Low (pilot) |
| 1.2 | `imports-auth` | CustomImportOrder auth 154 | 350-500 | Medium after pilot |
| 1.3 | `imports-bff` | CustomImportOrder bff 209 | 450-650 | Medium after pilot |
| 1.4-1.5 | `javadoc-paragraph-*` | JavadocParagraph 413, scripted (script not piloted; command in PR body), split by module group | 2 x 300-450 | Low (pilot) |
| 1.6 | `text-blocks` | TextBlockGoogleStyleFormatting 156 (~52 blocks), manual | 300-600 | Low |
| 1.7 | `line-length-and-small-rules` | LineLength 116 + formatting rules (~99: RegexpSinglelineJava, AvoidStarImport, EmptyLineSeparator, curly, Javadoc layout, Indentation, Overload order) | 500-750 | Medium |
| 2.x | `module-<name>` | Per module to zero: MissingJavadocMethod/Type, SummaryJavadoc, VariableDeclarationUsageDistance, renames (MethodName 5, AbbreviationAsWordInName 2, TypeName 1); flip module to `error` | 5-8 PRs, 100-750 each | Low |
| 3 | `severity-error-default` | Default severity `error`, delete ceilings map and per-module overrides, doc update (may merge into the last 2.x PR if it fits) | 50-120 | High |

- Phase 2 order: smallest to largest by residual re-measured after Phase 1. Provisional (current D1-D3 totals): shared 17, physical 151, app 163, virtual 198, billing 283, auth 474, bff 580. auth and bff likely need 2 PRs each.
- Total: about **14-19 PRs** plus the SDD archive PR. Uncertain: all Phase 1 tool slices and Phase 2 Javadoc volume (~4 lines per method).
- Ceiling-lowering rule: every PR re-measures each touched task and commits the exact count; a slice that cannot fit 800 lines stops and asks (D5).

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `build.gradle.kts` (root), possibly `buildSrc/` | Modified | Ceilings map, Checkstyle task wiring, per-module `configProperties` |
| `config/checkstyle/google_checks.xml` | Modified | Rules, suppressions, per-rule locks, final default severity |
| `config/checkstyle/suppressions.xml` | Modified/Removed | Wired or deleted |
| `docs/adr/0043-*.md` | New | Policy and ratchet ADR (Spanish) |
| `docs/adr/0042-*.md`, `docs/adr/0024-*.md`, `docs/14-TEST-STRATEGY.md`, `openspec/config.yaml` | Modified | Doc drift, ratchet rule |
| `api/*/src`, `bff/src` | Modified | Imports, formatting, Javadoc, local renames; no behavior change |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Ceilings frozen before the MethodName fix (~1,127 too loose) | Med | Phase 0 order: config fixes first, then baseline |
| Feature PRs turn red | High | Intended (D4); ADR-0043 documents "fix or lower, never raise" |
| Checkstyle bump raises counts | Low | Bump PRs recalibrate ceilings; no Dependabot today |
| Squash merges: blame-ignore SHAs exist only after merge | Med | See open question 2 |
| gjf import order disagrees with CustomImportOrder | Med | Pilot slice 1.1 first; fall back to IDE layout |
| Text-block fixes change string content | Med | Tests as safety net; review closing-delimiter indentation |
| Stacked PRs conflict with each other (shared ceilings map) and with feature work | Med | One key per line; touch only the slice's keys; rebase the stack |
| `message` regex tied to the pinned English message (`google_checks.xml:463-464`) | Low | Comment in config and ADR-0043 |

## Rollback Plan

- Revert PRs in reverse merge order; each cleanup PR carries its own ceilings, so a revert restores code and ceilings atomically.
- Reverting Phase 0 restores warning-only behavior. Ceilings and severity are config-only; no schema, data, or runtime change.

## Dependencies

- Fresh baseline on `develop` at Phase 0 start; google-java-format CLI available locally for slices 1.1-1.3 (not added to the build).

## Success Criteria

- [ ] After every PR: `./gradlew checkstyleMain checkstyleTest --rerun-tasks --no-build-cache` passes with each task count <= its ceiling, and equal to it after every cleanup PR.
- [ ] Phase 0: lowering any ceiling by 1, or removing a key, makes the build fail.
- [ ] End state: 0 warnings and 0 errors in all 14 tasks; default severity `error`; ceilings map deleted.
- [ ] CI `clean build` green on every PR; `./gradlew test --tests "*ArchitectureTest"` and `jacocoTestCoverageVerification` green with no threshold lowered.
- [ ] Executed test count per module unchanged (JUnit XML totals before/after).

## Open Questions

1. #298 "Prioridad" board field is still unset.
2. Add `.git-blame-ignore-revs` with the squash SHAs of the Phase 1 mechanical PRs (recommended, in a follow-up commit)?

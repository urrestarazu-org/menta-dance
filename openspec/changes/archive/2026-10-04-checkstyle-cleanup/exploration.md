# Exploration: checkstyle-cleanup (#298)

**Issue**: #298 — resolve the ~6,700 Checkstyle warnings of the monorepo (module:infra, type:tech-debt)
**Baseline**: `develop` @ `56ed6e4`, `--rerun-tasks`: **6,768** warnings (main 2,793 / test 3,975).
**Method**: read-only `sdd-explore` agent (no shell) + orchestrator verification and spikes (Checkstyle CLI 13.8.0 on config copies, plus Gradle init-script spikes; the repo tree was left untouched). Claims below marked **[verified]** were reproduced by the orchestrator.

## Current state

### Checkstyle wiring [verified]
- Root `build.gradle.kts` only: plugin applied at line 120, `toolVersion` + `configFile` at 163-166. No `maxWarnings`, `maxErrors`, `ignoreFailures`, `configProperties` or per-module override.
- `config/checkstyle/google_checks.xml:23` makes severity `${org.checkstyle.google.severity}` with default `warning`. Nothing sets it, so every finding is a warning and **Checkstyle cannot fail the build today**.
- `docs/adr/0042-security-tooling-baseline.md:24` claims Checkstyle "falla el build" — false. `docs/adr/0024-technology-baseline.md:26` lists Checkstyle 10.23.0 — real version is 13.8.0.
- Dead suppression file: `google_checks.xml:35-39` loads `${org.checkstyle.google.suppressionfilter.config}` (default `checkstyle-suppressions.xml`, `optional=true`). Gradle never sets the property and no file with that name exists, so `config/checkstyle/suppressions.xml` (MethodName in `ArchitectureTest`) is never loaded. Same for the XPath suppression file property (`:492-497`).
- No formatter (no Spotless / google-java-format / `.editorconfig`). Android has no style tooling; the 6,768 are JVM-only.
- CI: `pr-develop.yml:36` (`clean build`, includes `check`) plus explicit `checkstyleMain/Test` steps. No workflow change is needed if the ratchet lives in Gradle.

### Coverage ratchet precedent
`moduleCoverageFloor` map (root `build.gradle.kts:34`) + mechanism in `buildSrc/.../LayeredCoverage.kt` + rationale in `docs/14-TEST-STRATEGY.md` ("trinquete, no una meta"), hooked to `check`. Pattern to mirror: numbers in one root map, mechanism in buildSrc, docs in Spanish.

## MethodName anomaly — solved [verified]
The XPath suppression (`google_checks.xml:466-472`) filters on a `message` regex `'[a-z][a-z0-9][a-zA-Z0-9]*(?:_[a-z][a-z0-9][a-zA-Z0-9]*)*'`. Every underscore segment must start with two lowercase/digit chars, so a `@Test` name is **not** suppressed if it has a 1-letter segment (`_a_`), a digit-leading segment (`_401`, `_24_`), an uppercase-leading segment (`_LessonNotFoundException`) or an uppercase first char. That is exactly the 1,127 flagged test methods (the "58% not flagged" were simply names without such segments). The XPath itself is fine.

Spike results:
- S1 (CLI, `VirtualCourseCatalogPortImplTest`): 5 hits at lines 61, 70, 148, 176, 182; none at 108/120/129/136/163 — as predicted.
- S2 (config copy with relaxed regex `'[A-Za-z][A-Za-z0-9]*(?:_[A-Za-z0-9]+)*'`): 0 hits on that file; across all test sources only **5** remain (helpers `aCourseEntity` x2 in `VirtualCourseRepositoryAdapterTest`, and 3 in `CatalogControllerTest`). Test warnings drop 3,975 → 2,848 (**−1,127**); total 6,768 → **5,641**.
- Latent fragility: the suppression keys on the English message pinned at `google_checks.xml:463-464`; if the pin is removed, the localized message silently re-breaks it.
- The snake_case test naming (~2,700 methods) is a deliberate, undocumented convention. Only trace: the comment in `config/checkstyle/suppressions.xml:7`.

## Ratchet mechanism — spikes [verified]
- Gradle init-script `maxWarnings` on `:api:shared:checkstyleMain` (36 warnings): ceiling 35 → `BUILD FAILED`, `[warning:36]`; ceiling 36 → pass. Native failure, no custom task.
- `-Dorg.checkstyle.google.severity=error` as a JVM property has **no effect**; `checkstyle { configProperties = mapOf("org.checkstyle.google.severity" to "error") }` works (`[error:36]`, `BUILD FAILED`). So per-module "flip to error" is feasible by Gradle config.
- Options: (A) per-task `maxWarnings` ceilings map beside `moduleCoverageFloor` (recommended, ~15 lines, native), plus per-module severity=error when a module reaches 0 and per-rule `severity=error` locks; (B) custom XML-diff verifier (rejected: reinvents Gradle); (C) generated line-based baseline suppressions (rejected: brittle); (D) diff-based tool (rejected: new tool).
- Known limit of (A): it is a total per task, so rule swaps are masked and slack hides regressions; mitigated by the "every PR lowers its ceilings in the same PR" rule and the zero-locks.

## Policy data
- **LineLength** [verified]: 3,278 at 100. At **120: 116 remain** (app test 14, auth 3+4, bff 6+11, billing 19+30, physical 7+5, shared 3, virtual 9+5). Total at 120 (with MethodName fix): 2,479. Cost at 100: ~3 changed lines per wrap, ~13+ PRs, plus ~90 unwrappable long test names.
- **CustomImportOrder** (490): rule matches recent code; offenders are older auth 154, bff 209, app test 85, others 42. Fixable with google-java-format `--fix-imports-only` (not yet piloted) or IDE layout. Do NOT adopt the full formatter (continuation indent 4 vs gjf 8).
- **JavadocParagraph** (413): comment-only; no config relief except disabling; needs a script (not piloted). `allowNewlineParagraph=true` would only halve the messages.
- **TextBlockGoogleStyleFormatting** (156, ~52 blocks): manual/small PR; string content can change, tests are the safety net.
- **MissingJavadocMethod** (956 main) [verified variants]: (a) drop `CTOR_DEF, COMPACT_CTOR_DEF` → 524 (−432); (c) allow `Bean, GetMapping, PostMapping, PutMapping, PatchMapping, DeleteMapping, RequestMapping, ExceptionHandler, Scheduled` → 822 (−134); (a)+(c) → **390** (−566). **MissingJavadocType** 113 unaffected by these.
- Tiny rules (~120): AvoidStarImport 14, EmptyLineSeparator 13, LeftCurly 12, RightCurly 12, RequireEmptyLineBeforeBlockTagGroup 11, SingleLineJavadoc 10, Indentation 5, JavadocContentLocation 4, OverloadMethodsDeclarationOrder 2, JavadocTagContinuationIndentation 1, TypeName 1, RegexpSinglelineJava 15, AbbreviationAsWordInName 20 (also covered by a `@Test` XPath suppression without `message`). SummaryJavadoc 64 and VariableDeclarationUsageDistance 45 are semantic/manual → module PRs.

## Environment facts [verified]
- No open PRs → no in-flight conflict today. No `size` / `size:exception` label exists in the repo (`gh label list`); `size:exception` only appears as per-change user decisions in archived openspec reports. Squash merges; no `.git-blame-ignore-revs`. ADRs end at 0042 (0043 is free).

## Validation of the hybrid plan (corrections)
1. Inside Phase 0: **fix the MethodName suppression first, then freeze ceilings** (otherwise ceilings encode 1,127 phantom warnings).
2. Wire `suppressions.xml` through `${config_loc}` or delete it (today dead).
3. Flip severity per module (and per rule at zero), not only at the end.
4. Every PR lowers its ceilings in the same PR.
5. Phase 3 order should be recomputed after Phase 2; estimated residuals (shared ~20, bff ~190, auth ~460, app ~585, physical ~665, virtual ~880, billing ~1,900) suggest shared, bff, auth, app, physical, virtual, billing (differs from the earlier order).
6. Android is out of scope.

## Phase 0 sketch (~150 changed lines)
0.1 decisions record (ADR-0043, Spanish) · 0.2 MethodName/AbbreviationAsWordInName suppression fix + wire/delete `suppressions.xml` · 0.3 re-baseline (`--rerun-tasks --no-build-cache`) · 0.4 `checkstyleWarningCeiling` map + `tasks.withType<Checkstyle>` (fail on missing key), empty `strictModules` · 0.5 docs (ratchet rule, fix ADR-0042/0024, test-naming convention in CLAUDE.md, `openspec/config.yaml:17` wording) · 0.6 tamper test per module × source set.

## Risks
- Ceilings frozen before the MethodName fix would be ~1,127 too loose.
- Feature PRs that add warnings turn red after the ratchet (none open today).
- Checkstyle version bumps (no Dependabot/Renovate) can raise counts and break ceilings.
- gjf import order vs Checkstyle ordering may disagree on edge cases (pilot first).
- Squash merges: blame-ignore SHAs only exist after merge (follow-up commit).
- Text-block fixes can change string content.

## Open questions for the user
1. LineLength: 100 vs 120 (recommended 120; 116 residual).
2. Is descriptive snake_case test naming the official convention (recommended yes; relax the regex, document it).
3. Javadoc scope for MissingJavadocMethod/Type (recommended narrowed: constructors + framework-annotated methods exempt → 390 left).
4. Should the ratchet fail CI for feature PRs from Phase 0 on (recommended yes).
5. Allow `size:exception` for tool-generated mechanical PRs when the PR body carries the reproducible command (recommended yes).

# Code Style Gate Specification

## Purpose

Build-level guarantees for the Checkstyle gate of the JVM modules (`api:shared`, `api:auth`, `api:billing`, `api:virtual`, `api:physical`, `api:app`, `bff`). From #298 forward, any Checkstyle finding fails the build (default severity `error`, `maxWarnings = 0`). The style policy (line length, test naming, Javadoc scope) and strictness locks are documented below. Android, the camelCase rename of snake_case test methods (deferred, #323), formatter adoption and Checkstyle version bumps are out of scope.

Terms: the **14 tasks** are `:api:{shared,auth,billing,virtual,physical,app}:checkstyle{Main,Test}` and `:bff:checkstyle{Main,Test}`. The **count** of a task is the number of findings it reports on a fresh run (`--rerun-tasks --no-build-cache`).

Verification classes: **[build]** requirements are observable from a Gradle task outcome, exit code or reported count; **[file]** from file content; **[process]** only by PR review or repository history, never enforced by the build.

## History

Change #298 used a phased delivery over 14 PRs (W0-W3): Phase 0 established the style policy and a temporary warning ceiling (`maxWarnings`) per task to avoid breaking feature PRs during cleanup. Phases 1-2 fixed findings and lowered ceilings; Phase 3 (PR #402, commit 4f332bd) removed the ceiling mechanism and made default severity `error`. Requirements R1-R5 from the migration phase (per-task ceiling, missing-ceiling detection, lower-never-raise process, module strictness override, per-rule lock) existed during the ratchet climb and are no longer active; they are archived with the change. This spec records the policy and end-state behavior only.

## Requirements

### Requirement: Style policy in the Checkstyle config [file, build]

`config/checkstyle/google_checks.xml` MUST encode: (a) LineLength max 120 keeping the existing ignore pattern; (b) the `@Test` MethodName suppression accepts names matching `[A-Za-z][A-Za-z0-9]*(?:_[A-Za-z0-9]+)*` and `AbbreviationAsWordInName` is suppressed for `@Test` methods; (c) MissingJavadocMethod does not apply to constructors or compact constructors nor to methods annotated with `Bean`, `GetMapping`, `PostMapping`, `PutMapping`, `PatchMapping`, `DeleteMapping`, `RequestMapping`, `ExceptionHandler` or `Scheduled`; (d) MissingJavadocType does not apply to nested types. Snake_case test names are tolerated, not endorsed.

#### Scenario: Line length and test names

- GIVEN a 120-char line and a `@Test` method `should_return_401_when_token_is_expired`
- WHEN Checkstyle runs
- THEN neither is reported, while a 121-char line not matching the ignore pattern and a non-test method `aCourseEntity` are reported

#### Scenario: Javadoc scope

- GIVEN undocumented constructors, compact constructors, `@GetMapping` methods and nested types, plus an undocumented public plain method and top-level public type
- WHEN Checkstyle runs
- THEN only the plain method and the top-level type are reported

### Requirement: No dead suppression file [file, build]

`config/checkstyle/suppressions.xml` MUST either be loaded by the Checkstyle run or be removed from the repository, and no configuration or documentation MUST reference a removed file. A suppression file that exists but is never loaded MUST NOT remain.

#### Scenario: File is wired

- GIVEN the file remains in the repository
- WHEN an entry of it is removed and a task covering the entry's target runs
- THEN the finding it suppressed reappears, proving the file is loaded

#### Scenario: File is removed

- GIVEN the file was deleted
- WHEN `config/checkstyle/` and the config are searched for `suppressions.xml`
- THEN no file and no reference remains and the 14 tasks still run

### Requirement: End state, any finding fails [build]

The default severity MUST be `error` and all 14 tasks MUST report 0 warnings and 0 errors. CI (`./gradlew clean build` and the explicit `checkstyleMain`/`checkstyleTest` steps) MUST pass. The change MUST NOT alter behavior: the executed test count per module MUST equal the pre-change JUnit XML total, `*ArchitectureTest` MUST pass and `jacocoTestCoverageVerification` MUST pass with no threshold lowered.

#### Scenario: Clean by default

- GIVEN the final state
- WHEN `./gradlew checkstyleMain checkstyleTest --rerun-tasks --no-build-cache` runs
- THEN all 14 tasks succeed with 0 warnings and 0 errors

#### Scenario: Any finding fails

- GIVEN the final state and one finding in any of the 14 tasks
- WHEN that task runs
- THEN it fails with a non-zero exit

#### Scenario: No behavior change

- GIVEN the JUnit XML test totals per module before the change and after final delivery
- WHEN `./gradlew test`, `*ArchitectureTest` and `jacocoTestCoverageVerification` run
- THEN totals per module are equal, and all gates pass with unchanged thresholds

### Requirement: Policy documentation [file]

ADR-0043 (`docs/adr/0043-*.md`, Spanish, "Politica de Checkstyle y trinquete") MUST record the LineLength, test-naming and Javadoc policy, the deferral of the camelCase test rename, and the dependence of the suppression on the pinned English message. ADR-0042 MUST NOT state anything about Checkstyle that is inconsistent with the enforced behavior at the commit it lands. ADR-0024 MUST list the Checkstyle version in `gradle/libs.versions.toml` (13.8.0). `openspec/config.yaml` and `google_checks.xml` wording MUST say LineLength 120.

#### Scenario: ADR-0043

- GIVEN `docs/adr/`
- WHEN ADR-0043 is read
- THEN it exists, is in Spanish and covers each topic listed above

#### Scenario: Corrected ADRs

- GIVEN ADR-0042 and ADR-0024 after final delivery
- WHEN their Checkstyle statements are compared to the build
- THEN ADR-0042 describes the final behavior and ADR-0024 lists 13.8.0

#### Scenario: LineLength wording

- GIVEN `openspec/config.yaml` and the LineLength comment in `google_checks.xml`
- WHEN searched for "LineLength"
- THEN both state 120, not 100

### Requirement: Blame-ignore revisions [process]

After each mechanical Phase 1 PR (imports, Javadoc paragraphs) is squash-merged, a follow-up commit SHOULD add its squash SHA to `.git-blame-ignore-revs`. This is a process convention; the build does not enforce it.

#### Scenario: SHA recorded

- GIVEN a mechanical Phase 1 PR squash-merged at SHA S
- WHEN the follow-up commit lands
- THEN `.git-blame-ignore-revs` contains the full SHA S and `git cat-file -t S` reports `commit`

#### Scenario: Non-mechanical PRs

- GIVEN a Phase 2 or Phase 3 PR with manual changes
- WHEN `.git-blame-ignore-revs` is inspected
- THEN its SHA is not listed

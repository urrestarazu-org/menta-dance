# Code Style Gate Specification

## Purpose

Build-level guarantees for the Checkstyle gate of the JVM modules (`api:shared`, `api:auth`, `api:billing`, `api:virtual`, `api:physical`, `api:app`, `bff`). Until #298 Checkstyle is warning-only (`google_checks.xml` defaults `org.checkstyle.google.severity` to `warning`, no `maxWarnings`), so style debt (6,768 warnings on `develop` @ `56ed6e4`: main 2,793 / test 3,975) grows unchecked. This capability fixes the style policy, a ratchet that cannot be bypassed or raised silently, the strictness locks, and the end state "any finding fails the build". Android, the camelCase rename of snake_case test methods (deferred, D2), formatter adoption and Checkstyle version bumps are out of scope and NOT requirements here.

Terms: the **14 tasks** are `:api:{shared,auth,billing,virtual,physical,app}:checkstyle{Main,Test}` and `:bff:checkstyle{Main,Test}`. The **count** of a task is the number of warnings it reports on a fresh run (`--rerun-tasks --no-build-cache`). The **ceiling** of a task is its declared maximum allowed warnings.

Verification classes: **[build]** requirements are observable from a Gradle task outcome, exit code or reported count; **[file]** from file content; **[process]** only by PR review or repository history, never enforced by the build.

## Requirements

### Requirement: Per-task warning ceiling [build]

Each of the 14 tasks MUST fail when its count exceeds its ceiling and MUST pass when its count equals its ceiling. The failure MUST be a non-zero Gradle exit with the Checkstyle violation report. Whether a count strictly below the ceiling also fails (slack) is a design decision; this requirement neither requires nor forbids it (see "Ceilings are lowered, never raised").

#### Scenario: Count equals ceiling

- GIVEN a task with count N and ceiling N
- WHEN that task runs
- THEN it succeeds with exit code 0

#### Scenario: Count exceeds ceiling

- GIVEN a task with count N > 0 and its ceiling set to N-1
- WHEN that task runs
- THEN the build fails with a non-zero exit and reports N warnings

#### Scenario: Every task is gated

- GIVEN each of the 14 tasks in turn, with ceiling 0 and a single injected warning-level finding
- WHEN `./gradlew checkstyleMain checkstyleTest --rerun-tasks --no-build-cache` runs
- THEN the injected task fails, proving that task has a ceiling enforced

### Requirement: A missing ceiling fails loudly [build]

A Checkstyle task that has no declared ceiling and whose module is not under strict severity MUST fail the build with a message that names the task path. A new Checkstyle task (for example a new source set) MUST NOT run unbounded.

#### Scenario: Ceiling key removed

- GIVEN the ceiling of `:api:billing:checkstyleTest` is deleted
- WHEN that task runs
- THEN the build fails and the message contains `:api:billing:checkstyleTest`

#### Scenario: New source set

- GIVEN a module gains a new source set whose Checkstyle task has no declared ceiling
- WHEN that task runs
- THEN the build fails and the message names the new task

#### Scenario: Strict module needs no ceiling

- GIVEN a module declared strict and a task of it with no declared ceiling
- WHEN that task runs
- THEN it does not fail for a missing ceiling

### Requirement: Ceilings are lowered, never raised [process, partly build]

A change that touches the count of a task MUST commit, in the same PR, a ceiling equal to the exact re-measured count of that task. A PR MUST NOT raise any ceiling relative to its base branch. [process] The build-verifiable part is Requirement 1: a ceiling below the count fails. Equality of ceiling and count after every cleanup PR is enforced by the build only if the design adopts fail-on-slack; otherwise it is verified by re-measuring. Raising is enforced only by reviewing the diff of the ceilings.

#### Scenario: Cleanup PR lowers to the measured count

- GIVEN a cleanup PR reduces the count of a task from 120 to 95
- WHEN the PR's ceiling for that task is inspected
- THEN it is 95 and a fresh run of the task reports 95

#### Scenario: Raise attempt is detectable

- GIVEN a PR whose diff changes a ceiling from 95 to 100
- WHEN the ceilings diff is reviewed against the base
- THEN the increase is visible and the PR does not satisfy this requirement

#### Scenario: Slack outcome depends on the design

- GIVEN a task with count 90 and ceiling 95
- WHEN that task runs
- THEN the outcome is pass if the design does not fail on slack, or failure naming the task if it does; the design artifact states which

### Requirement: Module strictness [build]

A module declared strict MUST run both of its Checkstyle tasks with severity `error`, set through the Checkstyle `configProperties` entry `org.checkstyle.google.severity`, so that any finding fails the task regardless of ceilings. The `-D` system property MUST NOT be the mechanism, and passing it MUST NOT change any outcome.

#### Scenario: Finding in a strict module

- GIVEN a strict module with a ceiling larger than its count and one new violation
- WHEN its checkstyle task runs
- THEN the task fails and the report shows the finding with severity `error`

#### Scenario: Clean strict module

- GIVEN a strict module with 0 findings
- WHEN `checkstyleMain checkstyleTest` run for it
- THEN both succeed

#### Scenario: System property has no effect

- GIVEN a non-strict module with count at or under its ceiling
- WHEN a task runs with `-Dorg.checkstyle.google.severity=error`
- THEN its outcome and reported count are unchanged

### Requirement: Per-rule lock [file, build]

A rule that has 0 findings across all 14 tasks MAY be set to severity `error` in `config/checkstyle/google_checks.xml`. A locked rule MUST fail any task that contains a finding of that rule, regardless of ceilings. A lock MUST NOT be committed while any task reports a finding of that rule.

#### Scenario: Regression in a locked rule

- GIVEN `AvoidStarImport` has 0 findings and is locked, and a non-strict module has slack in its ceiling
- WHEN a star import is added in that module
- THEN its task fails with an `error` finding of `AvoidStarImport`

#### Scenario: Unlocked rules keep ceiling behavior

- GIVEN a locked rule and an unlocked rule, with one new finding of the unlocked rule within the ceiling
- WHEN the task runs
- THEN it succeeds

#### Scenario: Premature lock

- GIVEN a rule with 3 findings repo-wide
- WHEN it is set to severity `error`
- THEN `checkstyleMain checkstyleTest` fails, so the lock cannot be merged green

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

#### Scenario: Phase 0 projection

- GIVEN `develop` @ `56ed6e4` with only the policy changes (a)-(d) applied
- WHEN `./gradlew checkstyleMain checkstyleTest --rerun-tasks --no-build-cache` runs and the 14 counts are summed
- THEN the total is 1,866 (main 1,206 / test 660), down from 6,768; any additional Phase 0 config change, for example loading the suppression file, may lower it and is recalibrated before ceilings are frozen

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

At the end of the change the default severity MUST be `error`, the ceilings map and any per-module strictness overrides MUST be removed, and all 14 tasks MUST report 0 warnings and 0 errors. CI (`./gradlew clean build` and the explicit `checkstyleMain`/`checkstyleTest` steps) MUST pass. The change MUST NOT alter behavior: the executed test count per module MUST equal the pre-change JUnit XML total, `*ArchitectureTest` MUST pass and `jacocoTestCoverageVerification` MUST pass with no threshold lowered.

#### Scenario: Clean and strict by default

- GIVEN the final state
- WHEN `./gradlew checkstyleMain checkstyleTest --rerun-tasks --no-build-cache` runs
- THEN all 14 tasks succeed with 0 warnings and 0 errors, and no ceilings map exists in the build files

#### Scenario: Any finding fails

- GIVEN the final state and one injected finding in any of the 14 tasks
- WHEN that task runs
- THEN it fails with a non-zero exit

#### Scenario: No behavior change

- GIVEN the JUnit XML test totals per module before the change and after the final PR
- WHEN `./gradlew test`, `*ArchitectureTest` and `jacocoTestCoverageVerification` run
- THEN totals per module are equal, and all gates pass with unchanged thresholds

### Requirement: Policy documentation [file]

ADR-0043 (`docs/adr/0043-*.md`, Spanish, "Politica de Checkstyle y trinquete") MUST record the LineLength, test-naming and Javadoc policy, the ratchet rule "fix or lower, never raise", the strictness locks, the deferral of the camelCase test rename, and the dependence of the suppression on the pinned English message. ADR-0042 MUST NOT state anything about Checkstyle failing the build that is inconsistent with the enforced behavior at the commit it lands. ADR-0024 MUST list the Checkstyle version in `build.gradle.kts` (13.8.0). `openspec/config.yaml` and `google_checks.xml` wording MUST say LineLength 120.

#### Scenario: ADR-0043

- GIVEN `docs/adr/`
- WHEN ADR-0043 is read
- THEN it exists, is in Spanish and covers each topic listed above

#### Scenario: Corrected ADRs

- GIVEN ADR-0042 and ADR-0024 after Phase 0
- WHEN their Checkstyle statements are compared to the build
- THEN ADR-0042 describes the ceiling failure and ADR-0024 lists 13.8.0

#### Scenario: LineLength wording

- GIVEN `openspec/config.yaml` and the LineLength comment in `google_checks.xml`
- WHEN searched for "LineLength"
- THEN both state 120, not 100

### Requirement: Blame-ignore revisions [process]

After each mechanical Phase 1 PR (imports, Javadoc paragraphs) is squash-merged, a follow-up commit SHOULD add its squash SHA to `.git-blame-ignore-revs`. This is a process convention; the build does not enforce it.

#### Scenario: SHA recorded

- GIVEN a mechanical Phase 1 PR squash-merged to `develop` at SHA S
- WHEN the follow-up commit lands
- THEN `.git-blame-ignore-revs` contains the full SHA S and `git cat-file -t S` reports `commit`

#### Scenario: Non-mechanical PRs

- GIVEN a Phase 2 module PR with manual changes
- WHEN `.git-blame-ignore-revs` is inspected
- THEN its SHA is not listed

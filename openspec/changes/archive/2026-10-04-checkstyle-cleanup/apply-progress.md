# Apply progress: checkstyle-cleanup (#298)

Cumulative record. Mode: Strict TDD (build-config adaptation: policy edits verified by measured count delta; ratchet verified by tamper runs T1-T5, RED captured before GREEN).

## Status

| Unit | Status |
|---|---|
| W0 `config-ratchet-docs` (sub-issue #324) | Applied, uncommitted in the working tree (branch `feature/checkstyle-config-ratchet-docs`); 24/24 tasks `[x]` |
| W1.1 `imports-pilot` (sub-issue #325) | Applied, uncommitted (branch `feature/checkstyle-imports-pilot`); W1.1-1..9 `[x]`, W1.1-10 orchestrator |
| W1.2 `imports-auth` (sub-issue #326) | Applied, uncommitted (branch `feature/checkstyle-imports-auth`); W1.2-1..7 `[x]`, W1.2-8 orchestrator |
| W1.3 `imports-bff` (sub-issue #327) | Applied, uncommitted (branch `feature/checkstyle-imports-bff`); W1.3-1..8 `[x]`, W1.3-9 orchestrator; `CustomImportOrder` locked at `error` |
| W1.4 `javadoc-paragraph-a` (sub-issue #328) | Applied, uncommitted (branch `feature/checkstyle-javadoc-paragraph-a`); W1.4-1..8 `[x]`, W1.4-9 orchestrator |
| W1.5 `javadoc-paragraph-b` (sub-issue #329) | Applied, uncommitted (branch `feature/checkstyle-javadoc-paragraph-b`); W1.5-1..7 `[x]`, W1.5-8 orchestrator; `JavadocParagraph` locked at `error` |
| W1.6 `text-blocks` (sub-issue #330) | Applied and merged (#339) |
| W1.7 `line-length-and-small-rules` (sub-issue #331) | Applied, uncommitted (branch `feature/checkstyle-line-length-small-rules`); W1.7-1..9 `[x]`, W1.7-10 orchestrator; 12 rules locked at `error` |
| W2.0 checkpoint | Done (recorded in tasks.md) |
| W2.1 `shared + bff + app` (sub-issue #342) | Applied, uncommitted (branch `feature/checkstyle-module-shared-bff-app`); W2.1-1..10 `[x]`; three modules flipped to strict |
| W2.2 `api:auth` (sub-issue #343) | Applied, uncommitted; flipped to strict |
| W2.3 `api:physical` (sub-issue #344) | Applied, uncommitted (branch `feature/checkstyle-module-physical`); flipped to strict; `TypeName` and `AbbreviationAsWordInName` locked |
| W2.4 `api:billing` (sub-issue #345) | Applied, uncommitted (branch `feature/checkstyle-module-billing`); flipped to strict |
| W2.5 `api:virtual` (sub-issue #346) | Applied, uncommitted (branch `feature/checkstyle-module-virtual`); flipped to strict; `checkstyleWarningCeiling` now empty; `MissingJavadocMethod`, `MissingJavadocType`, `SummaryJavadoc`, `VariableDeclarationUsageDistance`, `MethodName` locked |
| W3 `severity-error-default` (sub-issue #332) | Applied, uncommitted (branch `feature/checkstyle-severity-error-default`); W3-1..9 `[x]`, W3-10 orchestrator; ALL SLICES DONE (W0, W1.1-W1.7, W2.1-W2.5, W3) |

## W0: frozen ceilings (measured on the policy-only tree, `--rerun-tasks --no-build-cache`)

| Task | Ceiling |
|---|---|
| `:api:app:checkstyleMain` / `Test` | 24 / 139 |
| `:api:auth:checkstyleMain` / `Test` | 382 / 92 |
| `:api:billing:checkstyleMain` / `Test` | 216 / 67 |
| `:api:physical:checkstyleMain` / `Test` | 140 / 11 |
| `:api:shared:checkstyleMain` / `Test` | 10 / 7 |
| `:api:virtual:checkstyleMain` / `Test` | 182 / 16 |
| `:bff:checkstyleMain` / `Test` | 252 / 328 |
| Total | 1,866 (main 1,206 / test 660), down from 6,768 (main 2,793 / test 3,975) |

All 14 deltas versus the CLI projection are 0.

## TDD Cycle Evidence (W0)

| Task | Evidence | Layer | Safety net | RED | GREEN | Triangulate | Refactor |
|---|---|---|---|---|---|---|---|
| W0-1/W0-2 | JUnit baseline 2,979 tests / 2 skipped; unmodified tree 6,768 | Gradle | baseline | n/a | measured | n/a | n/a |
| W0-3..W0-8 (policy) | count delta 6,768 -> 1,866; MissingJavadocType 113 -> 85; ArchitectureTest MethodName rg: no match | Gradle measurement | W0-2 | pre-policy rules measured (LineLength 3,278, MethodName 1,132, MissingJavadocMethod 957) | W0-9 equals projection | per-rule table | n/a |
| W0-11 wiring | build.gradle.kts map + strict set + configureEach | Gradle | T1-T5 | T1, T2 | T5 | T3, T4 | n/a |
| T1 | missing key | Gradle | n/a | build FAILED naming `:api:billing:checkstyleTest` | restored | n/a | n/a |
| T2 | ceilings = measured - 1 | Gradle | n/a | 14 task failures, `[warning:N]` each | restored | n/a | n/a |
| T3 | cache | Gradle | n/a | ceiling N-1 without rerun flags: task re-executed and FAILED `[warning:7]`, 13 others UP-TO-DATE | exact ceilings pass | n/a | n/a |
| T4 | strict | Gradle | n/a | `:api:shared` strict: `[error:10]`/`[error:7]` + stale-key warnings | `-D` on non-strict billing: `[warning:216]`, exit 0, unchanged | n/a | n/a |
| T5 | green | Gradle | n/a | n/a | `<CS14>` 14 tasks pass; `<ALL_CHECK>` pass | n/a | n/a |

Evidence (trimmed outputs, tables) kept outside the repo in the session scratchpad `w0-evidence.md`; the orchestrator folds it into the PR body.

## Work Unit Evidence (W0)

| Evidence | Value |
|---|---|
| Focused test command and result | `rtk proxy ./gradlew <CS14> --rerun-tasks --no-build-cache` exit 0, 14 tasks executed, counts equal ceilings; T1-T5 as above |
| Runtime harness | The tamper runs T1-T5 on the real 14 tasks; `<ALL_CHECK>` exit 0; ArchUnit auth 14 / billing 8 / virtual 7 / physical 8 / app 7 tests, 0 failures; JUnit before/after identical per module |
| Rollback boundary | Revert PR 0: `build.gradle.kts` (map, strict set, configureEach), `config/checkstyle/google_checks.xml`, restore `suppressions.xml`, docs; warning-only behavior returns |

## Deviations

- `docs/14-TEST-STRATEGY.md`: the new section is appended at the end of the file (a top-level `##` section) rather than between the coverage text and the technical notes, because the `rate-ms` technical note is a `###` subsection of "Cobertura" and inserting the `##` section before it would re-parent that note.
- MissingJavadocMethod pre-policy count measured as 957 (decisions record said 956); it does not change any post-policy figure.

## SIZE

416 changed lines (396 added, 20 removed) including the new ADR-0043 (248 lines); budget 800.

## W1.1: `imports-pilot`

Command (JDK 21; google-java-format 1.37.0 all-deps jar kept outside the repo, never added to the build):
`java --add-exports jdk.compiler/com.sun.tools.javac.{api,file,parser,tree,util,code}=ALL-UNNAMED -jar google-java-format-1.37.0-all-deps.jar --fix-imports-only --skip-removing-unused-imports --replace <files>` (60 files outside `api/auth` and `bff`, 127 offenders).

Pilot decisions: (a) gjf order matches `STATIC###THIRD_PARTY_PACKAGE` (ASCII, one blank line between groups), verified by Checkstyle, 0 disagreements; (b) keep `--skip-removing-unused-imports` (no need to drop it); (c) 0 files fixed by hand. Pilot file: `CatalogControllerTest.java` (85 -> 84 in `:api:app:checkstyleTest`).

| Task | Before (ceiling) | After (new ceiling) |
|---|---|---|
| app main / test | 24 / 139 | 22 / 54 |
| billing main / test | 216 / 67 | 208 / 49 |
| physical main / test | 140 / 11 | 138 / 10 |
| shared main / test | 10 / 7 | 10 / 1 |
| virtual main / test | 182 / 16 | 181 / 12 |
| auth, bff | unchanged | unchanged |

`CustomImportOrder` remains only in auth (154) and bff (209). JUnit before == after for every module (shared 64/0, auth 527/0, billing 869/0, virtual 355/0, physical 474/0, app 458/0, bff 232/2). Import-only diff check empty; `<CS14>` and `build --dry-run -q` exit 0; ArchUnit green (auth 14, billing 8, virtual 7, physical 8, app 7); JaCoCo green. SIZE: 61 files, 89 added, 101 removed (190 lines).

## W1.2: `imports-auth`

Command: `gjf.sh <files>` = the W1.1 command (google-java-format 1.37.0, `--fix-imports-only --skip-removing-unused-imports --replace`, six JDK 21 `--add-exports`), over 50 files of `api/auth` (main + test) taken from the fresh Checkstyle XML (`<FILES(CustomImportOrder)>` run per report file: a single rg call over two reports returned nothing). 154 offenders (main 99 + test 55), 0 files fixed by hand, 0 gjf disagreements.

| Task | Before (ceiling) | After (new ceiling) |
|---|---|---|
| auth main / test | 382 / 92 | 283 / 37 |
| all other 12 tasks | unchanged | unchanged |

`CustomImportOrder` remains only in bff (46 main + 163 test = 209). JUnit auth before == after: 527 tests / 0 skipped. Import-only diff check empty; `<CS14>` and `build --dry-run -q` exit 0; `:api:auth:test --tests "*ArchitectureTest"` green (14 tests); `:api:auth:jacocoTestCoverageVerification` green. SIZE: 51 files, 41 added, 127 removed (168 lines).

## W1.3: `imports-bff`

Command: `gjf.sh <files>` = the W1.1 command (google-java-format 1.37.0, `--fix-imports-only --skip-removing-unused-imports --replace`, six JDK 21 `--add-exports`), over 47 files of `bff` (main + test) taken from the fresh Checkstyle XML (run per report file). 209 offenders (main 46 + test 163), 0 files fixed by hand, 0 gjf disagreements (bff test files with statics last were handled correctly).

| Task | Before (ceiling) | After (new ceiling) |
|---|---|---|
| bff main / test | 252 / 328 | 206 / 165 |
| all other 12 tasks | unchanged | unchanged |

`<RULES>` over the 14 tasks shows `CustomImportOrder` at 0 (14 report counts equal the ceilings). Lock (R5): added `<property name="severity" value="error"/>` to the `CustomImportOrder` module in `config/checkstyle/google_checks.xml`; `<CS14>` still exit 0. Lock RED/GREEN: one import moved out of order in `BillingApiClient.java` made `:bff:checkstyleMain` fail with `[warning:206, error:1]` (`CustomImportOrder`); file restored, `git diff` shows only the intended changes.

JUnit bff before == after: 232 tests / 2 skipped. Import-only diff check empty; `build --dry-run -q` exit 0; `:bff:jacocoTestCoverageVerification` green (no threshold touched; bff has no ArchUnit). SIZE: 49 files, 177 added, 212 removed (389 lines).

## W1.4: `javadoc-paragraph-a`

Grouping (W1.4-1): A = auth 135 (main 111 + test 24) + billing 10 + app 13 (main 5 + test 8) + shared 6 (main 5 + test 1) + physical 1 = 165 findings in 82 files; B = bff 248 (W1.5, which adds the lock); virtual has 0. Main and test of each module stay in the same PR.

Script: `jd_paragraph.py` (Python 3, one-off, outside the repo, full text in the PR evidence). Lexer skips strings, chars, text blocks, `//` and plain `/* */`; edits only multi-line `/** */` blocks. Rules: R1 `<p> x` -> `<p>x`; R2 lone `<p>` merged into the next text line; R3 `<p>` not preceded by a blank ` *` line gets one inserted; R4 the first text line after a blank ` *` is prefixed with `<p>` unless it starts with `@`, `<p>`, a block tag or a closing tag. `<pre>` bodies and open `{@...` tags are not touched; `</p>` closers are left alone. Idempotent (second run: 0 files). Pilot: `JpaPhysicalCapacityAssignmentAdapter.java` (physical main 138 -> 137, 0 JavadocParagraph, 1 inserted blank comment line). Run: 82 files, 81 more changed after the pilot.

| Task | Before (ceiling) | After (new ceiling) |
|---|---|---|
| app main / test | 22 / 54 | 17 / 46 |
| auth main / test | 283 / 37 | 172 / 13 |
| billing main / test | 208 / 49 | 198 / 49 |
| physical main / test | 138 / 10 | 137 / 10 |
| shared main / test | 10 / 1 | 5 / 0 |
| virtual, bff | unchanged | unchanged |

`JavadocParagraph` 413 -> 248 (only bff left); LineLength 116 -> 116 (no merged line exceeded 120); no other rule rose except `SummaryJavadoc` +1 (see hand fix). Hand fix: `OutboxRowJpaRepository.java` method Javadoc "(due for retry)" gained a final period because the new `<p>` ended the summary sentence there (inside the comment only). Comment-only diff check empty. JUnit before == after: shared 64/0, auth 527/0, billing 869/0, physical 474/0, app 458/0. `<CS14>` and `build --dry-run -q` exit 0; ArchUnit green (auth 14, billing 8, physical 8, app 7); `jacocoTestCoverageVerification` green for the 5 touched modules. SIZE: 83 files, 174 added, 165 removed (339 lines, includes 16 in `build.gradle.kts`).

## W1.5: `javadoc-paragraph-b`

Scope: bff only (248 findings: main 197 + test 51, 56 files). Same script as W1.4, unchanged (no extension needed; bff shapes matched). Idempotent (second run: 0 files). Dry run and run: 56/56 files.

| Task | Before (ceiling) | After (new ceiling) |
|---|---|---|
| bff main / test | 206 / 165 | 9 / 114 |
| all others | unchanged | unchanged (shared 5/0, auth 172/13, billing 198/49, virtual 181/12, physical 137/10, app 17/46) |

`JavadocParagraph` 248 -> 0 across the 14 tasks; per-rule table before/after: only `JavadocParagraph` dropped, nothing rose (LineLength 116 = 116, SummaryJavadoc 64 = 64 after the hand fix). Hand fix: `AuthenticatedRequestIntegrationTest.java` class-level method Javadoc "...architectural limitations:" became "limitations." because the new `<p>` ended the summary (SummaryJavadoc +1 otherwise; comment only). ASCII-list file: `AuthenticatedRequestIntegrationTest.java` (`<p>1. ...`, `<p>Verifies:`). Lock: `severity` `error` added to `JavadocParagraph` in `google_checks.xml`; RED: removing a `<p>` in `LoginCommand.java` made `:bff:checkstyleMain` fail with `[error:1]` (reported as a Javadoc parse error under `JavadocParagraph`); restored byte-identical; GREEN: `<CS14>` exit 0. Comment-only diff check empty. JUnit bff before == after: 232 / 2. `build --dry-run -q` exit 0; `:bff:jacocoTestCoverageVerification` green. SIZE: 58 files, 252 added, 251 removed (503 lines).

## W1.6: `text-blocks`

Scope: 52 text blocks in 21 files (156 findings = 52 x 3): auth main 15, billing main 5, billing test 3, bff test 29. Every block had its opening `"""` after code on the statement line; the fix moves only that delimiter to its own line, indented like the closing delimiter (equal to the minimal content indent in all 52 blocks), so content and closing lines stay untouched and the rendered value is unchanged. One-off Python scripts outside the repo (lexer, fixer, content-identity proof). Proof: 52 -> 52 blocks, all rendered values equal (stripIndent + translateEscapes), code identical after rejoining the delimiter: ALL OK.

| Task | Before (ceiling) | After (new ceiling) |
|---|---|---|
| auth main | 172 | 127 |
| billing main / test | 198 / 49 | 183 / 40 |
| bff test | 114 | 27 |
| all others | unchanged | unchanged (shared 5/0, auth test 13, virtual 181/12, physical 137/10, app 17/46, bff main 9) |

Per-rule table: only `TextBlockGoogleStyleFormatting` 156 -> 0; nothing rose (LineLength 116). Lock: `severity` `error` added to its module; `<CS14>` exit 0. Lock RED: opening delimiter moved back in `PaymentJpaRepository.java` made `:api:billing:checkstyleMain` fail with `[warning:183, error:3]`; restored byte-identical. JUnit before == after (full `test` of the touched modules): auth 527/0, billing 869/0, bff 232/2. ArchUnit green (auth 14, billing 8); JaCoCo green for auth, billing, bff; `build --dry-run -q` exit 0. SIZE: 23 files, 111 added, 57 removed (168 lines).

## W1.7: `line-length-and-small-rules`

Scope: LineLength 116 and the small formatting rules (RegexpSinglelineJava 15, AvoidStarImport 14, EmptyLineSeparator 13, LeftCurlyEol 12, RightCurlyAlone 12, RequireEmptyLineBeforeBlockTagGroup 11, SingleLineJavadoc 10, Indentation 5, JavadocContentLocation 4, OverloadMethodsDeclarationOrder 2, JavadocTagContinuationIndentation 1), 215 findings in 111 Java files. The 12 + 12 curly findings (807 total instead of 783) were still present in the reports under source `LeftCurlyCheck#LeftCurlyEol` / `RightCurlyCheck#RightCurlyAlone`. The slice fits the 800-line budget in one PR (736 changed lines), so no W1.7a/W1.7b split was needed.

Tools (one-off, outside the repo, texts in the PR evidence): `w17_wrap.py` (LineLength wrapper: break after `,`, `->`, `=`, before `&&`/`||`/`+`/`?` and `.method(`; picks the break with the fewest resulting lines, continuation +4; 89 lines), `w17_javadoc.py` (Javadoc comment-only layout: `/** x */` to three lines, long comment lines wrapped at token boundaries; 28 comments), `w17_star.py` (explicit static imports from `javap` member lists, used members found by word match; 13 star imports in 10 bff test files; `UseCaseConfig` by hand), `w17_manual.py` (content-anchored one-off edits), `w17_tokcmp.py` (token-stream equivalence). Hand edits: 4 string literals split with `+` (value unchanged), `{ }` to `{}` (15 findings), one-line accessors expanded in `ActivationTokenJpaEntity`.

| Rule | Before | After |
|---|---|---|
| LineLength | 116 | 0 |
| RegexpSinglelineJava | 15 | 0 |
| AvoidStarImport | 14 | 0 |
| EmptyLineSeparator | 13 | 0 |
| LeftCurlyEol / RightCurlyAlone | 12 / 12 | 0 / 0 |
| RequireEmptyLineBeforeBlockTagGroup | 11 | 0 |
| SingleLineJavadoc | 10 | 0 |
| Indentation | 5 | 0 |
| JavadocContentLocation | 4 | 0 |
| OverloadMethodsDeclarationOrder | 2 | 0 |
| JavadocTagContinuationIndentation | 1 | 0 |
| MissingJavadocMethod / MissingJavadocType / SummaryJavadoc / VariableDeclarationUsageDistance / MethodName / AbbreviationAsWordInName / TypeName | 390 / 85 / 64 / 45 / 5 / 2 / 1 | unchanged |
| Total | 807 | 592 |

| Task | Before (ceiling) | After (new ceiling) |
|---|---|---|
| app main / test | 17 / 46 | 17 / 31 |
| auth main / test | 127 / 13 | 84 / 3 |
| billing main / test | 183 / 40 | 143 / 5 |
| physical main / test | 137 / 10 | 123 / 5 |
| shared main / test | 5 / 0 | 2 / 0 |
| virtual main / test | 181 / 12 | 170 / 5 |
| bff main / test | 9 / 27 | 2 / 2 |

Renames and semantic-risk edits (the token-stream comparison over the 113 changed files differs only here): one test method renamed because its single identifier exceeded 120 columns (`PhysicalPurchaseIntegrationTest`: `an_exception_transition_appends_one_exceptioned_row_and_notifies_two_recipients_once_despite_redelivery`, only definition, no references); `Purchase.exception()` moved next to the static overload `exception(...)` and `PhysicalPurchaseIntegrationTest.headersFor(UUID, Role)` moved next to `headersFor(UUID)` (pure moves); three fully qualified repository types in `JpaPhysicalCapacityAssignmentAdapter` replaced by imports (three import lines added, names unique).

Locks (R5): `severity` `error` added to LineLength (max 120 and ignorePattern kept), RegexpSinglelineJava, AvoidStarImport, EmptyLineSeparator, LeftCurlyEol, RightCurlyAlone, RequireEmptyLineBeforeBlockTagGroup, SingleLineJavadoc, Indentation, JavadocContentLocation, OverloadMethodsDeclarationOrder and JavadocTagContinuationIndentation (12 modules). Lock RED: a 121-column line appended to `CapacityAssignmentCommand.java` made `:api:shared:checkstyleMain` fail with `[warning:2, error:1]` (`LineLength`); file restored byte-identical (md5); GREEN: `<CS14>` exit 0.

Gates: JUnit before == after for all 7 modules (shared 64/0, auth 527/0, billing 869/0, virtual 355/0, physical 474/0, app 458/0, bff 232/2), checked after two full `cleanTest test` runs; `*ArchitectureTest` green (auth 14, billing 8, virtual 7, physical 8, app 7); `jacocoTestCoverageVerification` green for all 7 modules (no threshold touched); `build --dry-run -q` exit 0. SIZE: 113 files, 502 added, 234 removed (736 lines, includes 24 in `build.gradle.kts` and 30 in `google_checks.xml`).

## W2.1: `shared + bff + app`

Scope: 54 findings (shared 2/0, bff 2/2, app 17/31) taken to 0/0 and the three modules flipped to strict.

| Task | Before | After |
|---|---|---|
| shared main / test | 2 / 0 | 0 / 0 |
| bff main / test | 2 / 2 | 0 / 0 |
| app main / test | 17 / 31 | 0 / 0 |

Repo total 592 -> 538 (auth 84/3, billing 143/5, physical 123/5, virtual 170/5 unchanged). Per rule: MissingJavadocMethod 390 -> 371, SummaryJavadoc 64 -> 60, VariableDeclarationUsageDistance 45 -> 17, MethodName 5 -> 2; nothing rose; no rule reached 0 repo-wide, so no new lock.

Edits: 19 real Javadocs (responsibility, semantics, non-obvious effects; written after reading each method, its port and callers), 2 SummaryJavadoc capitalizations (comment only), 3 helper renames in `CatalogControllerTest` (`aPhysicalCourse` -> `physicalCourse`, `aVirtualCourse` -> `virtualCourse`, `aVirtualDetail` -> `virtualDetail`). VariableDeclarationUsageDistance (28): 13 declarations moved next to their first use only where the jumped statements are independent (reads of already-received responses, `issueUser`, Mockito stubs, independent inserts), 15 declarations marked `final` where order is semantically required (seed before the action under test, snapshot before a later mutation, or a derived-value domino). `final` is Checkstyle's own remedy and changes no behavior. Details per declaration in the session evidence file `w21-evidence.md`. Token-stream comparison: main sources identical to HEAD; test diffs are exactly the moves, `final` inserts and renames.

Flip (R4): `checkstyleStrictModules` = `":api", ":api:app", ":api:shared", ":bff"`; six ceiling keys deleted (the other eight unchanged). `<CS14>` exit 0 with no stale-ceiling warning; `build --dry-run -q` exit 0. W2.1-10: a Javadoc-less public method injected in `Email.java` made `:api:shared:checkstyleMain` fail with `[error:1]`; reverted byte-identical (cmp).

Gates: JUnit before == after (shared 64/0, bff 232/2, app 458/0); `:api:app:test --tests "*ArchitectureTest"` green (7 tests); `jacocoTestCoverageVerification` green for the three modules (no threshold touched). SIZE: 29 files, 230 added, 53 removed (283 lines).

## W2.2: `api:auth`

Scope: 87 findings (auth main 84 = MissingJavadocMethod 68 + SummaryJavadoc 16; test 3 = VariableDeclarationUsageDistance) taken to 0/0 and `:api:auth` flipped to strict.

| Task | Before | After |
|---|---|---|
| auth main / test | 84 / 3 | 0 / 0 |

Repo total 538 -> 451 (billing 143/5, physical 123/5, virtual 170/5 unchanged). Per rule: MissingJavadocMethod 371 -> 303, SummaryJavadoc 60 -> 44, VariableDeclarationUsageDistance 17 -> 14; nothing rose; no rule reached 0 repo-wide, so no new lock.

Edits: 68 real Javadocs written after reading each method with its port, implementation and callers (use-case entry points with exception contracts, repository ports with locking/idempotency notes, token aggregates with status precedence and invariants, mappers with null semantics, Spring Data queries with their conditional-update semantics); 16 SummaryJavadoc fixes add a proper first sentence before an existing block-tag-only comment (comment only). VariableDeclarationUsageDistance (3): `raw` in `LogoutUseCaseImplTest` (2 methods) moved next to its first use (pure random value, jumped statements never reference it); `nonexistent` in `ResendActivationUseCaseImplTest` marked `final` (the first resend result must be captured before the stubs are re-pointed).

Flip (R4): `checkstyleStrictModules` = `":api", ":api:app", ":api:auth", ":api:shared", ":bff"`; the two auth ceiling keys deleted (billing/physical/virtual unchanged). `<CS14>` exit 0 with no stale-ceiling warning; `build --dry-run -q` exit 0.

Gates: JUnit before == after (auth 527/0, no failures); `:api:auth:test --tests "*ArchitectureTest"` green (14 tests); `jacocoTestCoverageVerification` green (no threshold touched). Token-stream comparison (52 files): all main files identical; only the two test files differ (moved `raw` declarations, one `final`). SIZE: 52 files, 487 added, 6 removed (493 lines).

## W2.3: `api:physical`

Scope: 128 findings (main 123 + test 5). Taken to main 3 / test 0 (Gradle, `--rerun-tasks --no-build-cache`); the 3 left are `AssignmentOutcome.java` (`AbbreviationAsWordInName` on `ASSIGNED` and `RACE_LOST`, `TypeName` on `RACE_LOST`).

| Task | Before | After |
|---|---|---|
| physical main / test | 123 / 5 | 3 / 0 |

Per rule: MissingJavadocMethod 80 -> 0, MissingJavadocType 24 -> 0, SummaryJavadoc 16 -> 0, VariableDeclarationUsageDistance 5 -> 0, AbbreviationAsWordInName 2 and TypeName 1 unchanged.

Edits: 118 real Javadocs inserted by a one-off script (`w23_apply.py`, outside the repo; bottom-up per file, 120-column assertion) after reading each method with its port, adapter, callers and tests; 2 hand fixes (`ConvertOutcome` first sentence now ends with a period, `MonthlyAttendanceAssemblerTest` summary capitalized). VariableDeclarationUsageDistance: `authenticatedDevice` in `ProcessPhysicalCheckInUseCaseImpl` marked `final` (authentication gate must stay step 1); `actorId` moved next to its first use in 3 `ProcessPhysicalCheckInUseCaseImplTest` tests and 1 `RotatePhysicalDeviceSecretUseCaseImplTest` test (pure random value, jumped stubs never reference it).

Renames (user-authorized, touching `api:app` tests): `AssignmentOutcome.ASSIGNED` -> `Assigned` and `RACE_LOST` -> `RaceLost` (classes, constructors, `permits`, `INSTANCE` types, Javadoc links). References updated: `AssignCapacityUseCase:79`, `PhysicalCapacityAssignmentPort` Javadoc, `AssignCapacityUseCaseTest:70`, `AssignCapacityAdapterIntegrationTest:84`, `PhysicalCapacityAssignmentOutboxEventHandlerTest:187,191`. `FulfillmentStatus.ASSIGNED` (billing) untouched. Final rg over the monorepo: no old reference left outside `openspec/` historical files; `docs/` has none.

After the rename physical is 0/0; repo total 451 -> 323 -> (billing 143/5, virtual 170/5 left; per rule MissingJavadocMethod 223, MissingJavadocType 61, SummaryJavadoc 28, VariableDeclarationUsageDistance 9, MethodName 2). `TypeName` and `AbbreviationAsWordInName` are 0 repo-wide, so both are locked at `error` (TypeName's two modules and AbbreviationAsWordInName; the `@Test` XPath suppression is kept). Lock RED: a `Bad_Lock` class in billing main made `:api:billing:checkstyleMain` fail with `[warning:143, error:1]` (`TypeName`); file removed.

Flip (R4): `checkstyleStrictModules` = `":api", ":api:app", ":api:auth", ":api:physical", ":api:shared", ":bff"`; both physical ceiling keys deleted. `<CS14>` exit 0 with no stale-ceiling warning; `build --dry-run -q` exit 0.

Gates: JUnit before == after (physical 474/0, app 458/0); `*ArchitectureTest` green (physical 8, app 7); `jacocoTestCoverageVerification` green for both (no threshold touched). Token-stream comparison (89 files): differences only in the renames, the `final` insert and the moved `actorId` declarations. SIZE: 89 files, 503 added, 27 removed (530 lines).

## W2.4: `api:billing`

Scope: 148 findings (billing main 143 = MissingJavadocMethod 103 + MissingJavadocType 20 + SummaryJavadoc 20; test 5 = VariableDeclarationUsageDistance) taken to 0/0 and `:api:billing` flipped to strict.

| Task | Before | After |
|---|---|---|
| billing main / test | 143 / 5 | 0 / 0 |

Repo total 323 -> 175 (virtual 170/5 unchanged). Per rule: MissingJavadocMethod 223 -> 120, MissingJavadocType 61 -> 41, SummaryJavadoc 28 -> 8, VariableDeclarationUsageDistance 9 -> 4, MethodName 2 unchanged; nothing rose; no rule reached 0 repo-wide, so no new lock.

Edits: 143 real Javadocs inserted by a one-off script (`w24_apply.py`, outside the repo; bottom-up per file, 120-column assertion, cross-checked against the Checkstyle XML so every finding has exactly one entry), each written after reading the method with its port or use case, adapter or JPA entity, callers and tests (per-Javadoc verification list in the session evidence file `w24-evidence.md`). Two existing comments got a punctuation fix so the first sentence ends with a period (`MarkPurchaseAssignedUseCase`, `PurchaseSessionJpaRepository`). VariableDeclarationUsageDistance (5): `quoteRepository`, `availabilityPort` (`BankTransferCreationBudgetIntegrationTest`), `fulfillmentService` (`PaymentExpiryWorkerTest`) and `row` (`WebhookVerificationWorkerTest`) moved next to their first use (independent mocks or a pure factory; jumped statements never reference them); `task` in `ReconciliationTaskJpaRepositoryTest` marked `final` (the insert must precede the resolve). Five stale existing comments were found and left untouched (listed in the evidence file).

Flip (R4): `checkstyleStrictModules` = `":api", ":api:app", ":api:auth", ":api:billing", ":api:physical", ":api:shared", ":bff"`; the two billing ceiling keys deleted (virtual unchanged). `<CS14>` exit 0 with no stale-ceiling warning; `build --dry-run -q` exit 0.

Gates: JUnit before == after (billing 869/0, 139 suites, no failures); `*ArchitectureTest` green (8 tests); `jacocoTestCoverageVerification` green after a fresh full `test` (no threshold touched). Token-stream comparison (109 files): all 104 main files identical; only 4 test files differ (moved declarations and one `final`). SIZE: 109 files, 697 added, 10 removed (707 lines).

## W2.5: `api:virtual`

Scope: 175 findings (virtual main 170 = MissingJavadocMethod 120 + MissingJavadocType 41 + SummaryJavadoc 7 + VariableDeclarationUsageDistance 2; test 5 = SummaryJavadoc 1 + VariableDeclarationUsageDistance 2 + MethodName 2) taken to 0/0 and `:api:virtual` flipped to strict.

| Task | Before | After |
|---|---|---|
| virtual main / test | 170 / 5 | 0 / 0 |

Repo total 175 -> 0: every rule is at 0 across the 14 tasks (checked per rule from the XML reports).

Edits: 168 real Javadocs inserted by a one-off script (`w25_apply.py`, outside the repo; bottom-up per file, 120-column assertion, entry set cross-checked against the Checkstyle XML so every finding is covered exactly once), each written after reading the method with its use case impl, adapter/JPA query, mapper or domain class, callers, the ownership guards, the `VirtualConfiguration` wiring and the existing comments (per-Javadoc verification list in the session evidence file `w25-evidence.md`). One existing test comment got a punctuation fix (`VirtualCourseCatalogPortImplAdminDetailTest`: trailing ':' to '.'). VariableDeclarationUsageDistance (4): `before` in `UpdateVirtualLessonUseCaseImpl` and `UpdateVirtualModuleUseCaseImpl` marked `final` (it documents the pre-update snapshot); the `lessons` and `publish` mocks in `E2eCatalogContentVirtualFixtureTest` moved next to their first use (independent mock creation, no stub references them). MethodName (2): `aCourseEntity` renamed to `courseEntity` in `VirtualCourseRepositoryAdapterTest` (2 overloads, 11 call sites). Three discrepancies between existing comments and code were found and left untouched (listed in the evidence file: free lessons in `GetPublicLessonStreamUseCase`/`PublicLessonStreamResult` comments, "three buckets" in `LessonAccessDecision`, `videoId` in `PublicLessonFreeResponse`).

Flip (R4): `checkstyleStrictModules` now includes `":api:virtual"` (all 7 JVM modules plus `:api`); both virtual ceiling keys deleted; `checkstyleWarningCeiling` is `emptyMap<String, Int>()` (the map and mechanism are removed in W3). `<CS14>` exit 0 with no stale-ceiling warning; `build --dry-run -q` exit 0.

Locks (R5): `severity` `error` added to `VariableDeclarationUsageDistance`, `SummaryJavadoc`, `MissingJavadocMethod`, `MissingJavadocType` and `MethodName` (the `@Test` XPath suppression is kept); `<CS14>` exit 0. Lock RED: removing the Javadoc of `Clock.now()` made `:api:virtual:checkstyleMain` fail with `[error:1]`; restored byte-identical (cmp); GREEN afterwards. The rules still at default `warning` (all 0 findings) are listed in the evidence file for the W3 default flip.

Gates: JUnit before == after (virtual 355/0); `*ArchitectureTest` green (7 tests); `jacocoTestCoverageVerification` green right after a fresh full `cleanTest test` (no threshold touched). Token-stream comparison (100 files): all main files identical except the two `final` inserts; the test diffs are the moved mock declarations and the helper rename. SIZE: 100 files, 380 added, 24 removed (404 lines).

## W3: `severity-error-default` (final PR, closes #298)

Scope: no Java change. `google_checks.xml` default severity `warning` -> `error`; 23 per-rule `severity` `error` locks removed (8 modules unwrapped to compact form, 15 lines dropped from modules with other properties); no rule left with an explicit `warning`/`info`/`ignore` severity (checked with `rg`); comment added at the default (ADR-0043). Root `build.gradle.kts`: deleted `checkstyleWarningCeiling`, `checkstyleStrictModules`, the `tasks.withType<Checkstyle>().configureEach` block, the conditional `configProperties`, the `strict` val and the `Checkstyle` import; `checkstyle {}` now sets `maxWarnings = 0` for all JVM modules. No other reference to the removed symbols outside ADR-0043 history and `openspec/`.

W3-1 baseline: `<MEASURE>` 14 tasks at 0 findings (no `<error`, no `severity=` entries). JUnit baseline (fresh `cleanTest test`): shared 64/0, auth 527/0, billing 869/0, virtual 355/0, physical 474/0, app 458/0, bff 255/2.

W3-5 RED (one star import injected in one main and one test file of each of the 7 modules, 14 files): all 14 tasks FAILED with `Checkstyle violations by severity: [error:N]` (N between 1 and 23, import-order cascades included; the `AvoidStarImport` message appears for all 14). All 14 files restored with `git restore`; `git status` clean of Java changes. W3-6 GREEN: `<CS14>` exit 0 with 0 findings; no match of the removed symbols in `build.gradle.kts`; `<ALL_CHECK>` (`--rerun-tasks --no-build-cache`, 68 tasks, 12m49s) green; `build --dry-run -q` exit 0.

W3-8: JUnit after == W3-1 baseline for all 7 modules (identical). Versus the W0-1 table: shared/auth/billing/virtual/physical/app identical; bff 232 -> 255 (+23, 2 skipped unchanged) explained by `ced6a3f fix(bff): leer el rol del JWT ... (#353)`, which added `JwtAccessTokenClaimsReaderTest` and extended `LoginUseCaseTest`. `*ArchitectureTest` green (auth 14, billing 8, virtual 7, physical 8, app 7); `jacocoTestCoverageVerification` ran fresh for all 7 modules inside `<ALL_CHECK>` (no threshold touched).

Docs (Spanish): ADR-0043 (status line, end-state section "Estado final (vigente)", historical section renamed, consequences note, references), ADR-0042 (Checkstyle statement and dated correction now say any finding fails the build), `docs/14-TEST-STRATEGY.md` (section retitled "Estilo: Checkstyle estricto", ceilings/strict/lock mechanics removed, measurement commands kept). Sweep: no stale instruction left.

SIZE: `git diff --shortstat` 5 files, 80 added, 131 removed (211 lines changed, tracked files only).

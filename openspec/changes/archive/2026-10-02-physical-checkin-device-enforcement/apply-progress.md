# Apply Progress: physical-checkin-device-enforcement (#266)

**Mode**: Strict TDD | **Store**: hybrid | **Delivery**: auto-chain, stacked onto `develop`
**Branch (S1)**: `feature/266-physical-checkin-device-enforcement-s1`; **Branch (S2)**: `feature/266-physical-checkin-device-enforcement-s2`; **Branch (S3)**: `feature/266-physical-checkin-device-enforcement-s3` (working tree only, nothing staged or committed)

## Batch 1: Phase 1 (S1, PR 1) — tasks 1.1 to 1.11 complete; 1.12 (PR creation) left to the orchestrator

### Completed Tasks
- [x] 1.1 RED exception tests
- [x] 1.2 GREEN `CheckInDeviceRevokedException`, `DeviceExpiredException`
- [x] 1.3 `DeviceAuthenticationRejectionReason`, `DeviceAuthenticationRejection`, `DeviceAuthenticationRejectionPort`
- [x] 1.4 `io.micrometer:micrometer-core` in `api/physical/build.gradle.kts` (no lock files exist in the repo; nothing to refresh)
- [x] 1.5 RED `PhysicalDeviceAuthenticatorTest`
- [x] 1.6 GREEN `PhysicalDeviceAuthenticator`
- [x] 1.7 RED `LogAndMetricDeviceAuthenticationRejectionAdapterTest`
- [x] 1.8 GREEN `LogAndMetricDeviceAuthenticationRejectionAdapter`
- [x] 1.9 `ArchitectureTest.application_should_not_depend_on_micrometer`
- [x] 1.10 Gates (see evidence)
- [x] 1.11 S1 size check: 731 changed lines (all additions)
- [ ] 1.12 PR creation (orchestrator)

### TDD Cycle Evidence
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1/1.2 | `domain/exception/CheckInDeviceRevokedExceptionTest`, `DeviceExpiredExceptionTest` | Unit | N/A (new) | Written first; `compileTestJava` failed with 4 `cannot find symbol` errors | 4/4 pass | 2 cases per class (code, type; revoked also asserts it is NOT `DeviceRevokedException`) | Added constructor javadoc after Checkstyle |
| 1.3/1.5/1.6 | `application/usecase/PhysicalDeviceAuthenticatorTest` | Unit (Mockito) | N/A (new) | Written first; compile failed with 48 errors across both new test classes | 23/23 pass | 9 malformed ids (incl. `1-1-1-1-1`, trailing newline), null id, null secret, unknown UUID, wrong secret, revoked right/wrong secret, expired inclusive/past/wrong secret, revoked beats expired, null/future expiry, uppercase id | Constants extracted (`CANONICAL_UUID`, `DUMMY_SECRET_HASH`), single `rejected(...)` report-then-throw helper |
| 1.7/1.8 | `infrastructure/observability/LogAndMetricDeviceAuthenticationRejectionAdapterTest` | Unit (logback `ListAppender` + `SimpleMeterRegistry`) | N/A (new) | Written first (same compile failure) | 8/8 pass | revoked line, `deviceId=none`, 3 counters pre-registered at 0, only matching counter increments, only `reason` tag, 2 reports give 2 lines and count 2, log reason equals metric reason for all 3 | None needed (mirrors #236 adapter) |
| 1.9 | `ArchitectureTest` | ArchUnit | 7/7 existing rules green before | Rule is an absence check; it passes vacuously until a violating class exists, so no failing RED is possible | 8/8 pass | N/A | N/A |
| 1.4 | build config | N/A | N/A | Needed by the adapter test (compile) | resolves, tests run | Triangulation skipped: structural dependency line | N/A |

**Mutation sanity checks on the authenticator** (applied then reverted, file restored byte-identical): replacing `!expiresAt.isAfter(now)` with `expiresAt.isBefore(now)` made 1 test fail (inclusive-boundary test); replacing the UUID regex check with `false` made 9 tests fail.

Note on ordering: tests for 1.5 and 1.7 were written before the DTOs/port (1.3) because they define those types' shape; 1.3 was implemented right after the RED run.

### Work Unit Evidence
| Evidence | Value |
|---|---|
| Focused tests | `./gradlew :api:physical:test --tests "*PhysicalDeviceAuthenticatorTest" --tests "*LogAndMetricDeviceAuthenticationRejectionAdapterTest" --tests "*CheckInDeviceRevokedExceptionTest" --tests "*DeviceExpiredExceptionTest"` exit 0; 23 + 8 + 2 + 2 = 35 tests, 0 failures. `*ArchitectureTest`: 8 tests, 0 failures |
| Full module gate | `./gradlew :api:physical:test :api:physical:jacocoTestReport :api:physical:jacocoTestCoverageVerification :api:physical:checkstyleMain :api:physical:checkstyleTest` exit 0 (BUILD SUCCESSFUL, 2m 15s); 457 tests total, 0 failures, 0 errors, 0 skipped |
| JaCoCo | domain+application LINE 98.05% (803 covered / 16 missed, gate 95%); infrastructure LINE 97.52% (708 / 18, gate 90%); `PhysicalDeviceAuthenticator` 28/28 lines and 20/20 branches; adapter 14/14 lines, 4/4 branches |
| Checkstyle | Main sources: 0 violations on the new files. Test sources: 12 `MethodName` warnings on the new snake_case test names, same as every sibling test in the module (unavoidable under the file style); no other warnings. Overall module warning count is pre-existing (main 401, test 451 before the doc fixes) |
| Runtime harness | N/A: purely additive; nothing in the runtime path references the new classes (the `@Component` adapter is picked up by the app context, which already provides a `MeterRegistry` for the billing #236 adapter; not executed here, the Testcontainers app suite was not run) |
| Rollback boundary | Revert PR 1; no other code depends on it |

### Slice Size (1.11)
`git diff --numstat` (tracked): `api/physical/build.gradle.kts` +4, `ArchitectureTest.java` +14 = 18. Untracked new files under `api/` (line counts): main 266, tests 447 = 713. **Total 731 changed lines, 0 deletions** (under the 800 budget and under the ~750 split trigger). OpenSpec artifacts (tasks.md, apply-progress.md) are not counted.

### Deviations from Design
None for A1-A7 (A8-A10 belong to S2). Small implementation choices inside the design: `DUMMY_SECRET_HASH` is 64 zeros; the report-then-throw helper is a generic method returning the exception; the UUID regex keeps `^...$` anchors and is applied with `matches()`.

### Issues Found
- No dependency lock files exist in the repo even though locking is activated, so there was nothing to refresh after adding micrometer-core.
- `rtk` rewrites Gradle output heavily; test counts above were read from the JUnit XML reports, not from console output.

### Remaining
- 1.12 PR 1 (orchestrator), then Phase 2 and 3.

---

## Batch 2: Phase 2 (S2, PR 2, cutover) — tasks 2.1 to 2.10 complete; 2.11 (PR creation) left to the orchestrator

### Completed Tasks
- [x] 2.1 RED `ProcessPhysicalCheckInUseCaseImplTest` (mocked `PhysicalDeviceAuthenticator`)
- [x] 2.2 GREEN `ProcessPhysicalCheckInUseCaseImpl` (authenticator in place of `String deviceToken`, arity 11, persists `authenticated.toString()`)
- [x] 2.3 RED `PhysicalConfigurationTest` (no-arg ctor, fail-fast tests removed, authenticator-bean test)
- [x] 2.4 GREEN `PhysicalConfiguration` (no Environment, @Value token, dev default, @PostConstruct, PRODUCTION_PROFILES; new `physicalDeviceAuthenticator` bean)
- [x] 2.5 RED `LegacyCheckInDeviceTokenPropertyWarningTest`
- [x] 2.6 GREEN `LegacyCheckInDeviceTokenPropertyWarning`
- [x] 2.7 RED handler tests for `DEVICE_REVOKED` / `DEVICE_EXPIRED`
- [x] 2.8 GREEN `PhysicalCheckInExceptionHandler` mappings; `Attendance` and `InvalidDeviceTokenException` javadoc updated
- [x] 2.9 Minimal integration reseed of `PhysicalCheckInIntegrationTest`
- [x] 2.10 Gates (see evidence)
- [ ] 2.11 PR 2 creation (orchestrator)

### TDD Cycle Evidence (S2)
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 2.1/2.2 | `application/usecase/ProcessPhysicalCheckInUseCaseImplTest` | Unit (Mockito) | 91/91 across the 5 touched classes before editing (15+15+1+21+14+25 incl. nested) | Written first; `compileTestJava` failed: `PhysicalDeviceAuthenticator cannot be converted to String` | 16 + 15 nested pass | Parameterized over the 3 rejections (invalid, revoked, expired) each asserting `verifyNoInteractions` on all collaborators and no clock read; uppercase submitted id vs lowercase stored UUID; MANUAL asserts `verifyNoInteractions(deviceAuthenticator)` | Removed the now-dead `verifyDeviceToken`; javadoc updated |
| 2.3/2.4 | `infrastructure/config/PhysicalConfigurationTest` | Unit | included above | Written first; compile failed (no-arg ctor, bean method missing) | 19 pass | Authenticator bean tested behaviorally: unknown UUID walks repository lookup, hash, and rejection report | Dropped unused imports and constants |
| 2.5/2.6 | `infrastructure/config/LegacyCheckInDeviceTokenPropertyWarningTest` | Unit (MockEnvironment + logback ListAppender) | N/A (new) | Written first; class missing | 4/4 pass | set (one WARN naming the key), value never logged, `prod` profile still only WARN, unset gives no event | None needed |
| 2.7/2.8 | `infrastructure/web/controller/PhysicalCheckInExceptionHandlerTest` | Unit | included above | Written first; compile failed (`deviceRevoked`/`deviceExpired` missing) | 17 pass | revoked, expired, and a test that the three 401 bodies carry only the `code` property | None needed |
| 2.9 | `api:app` `PhysicalCheckInIntegrationTest` | Integration (Testcontainers MySQL) | N/A (the old suite was the net; it would fail after the swap) | Reseed written together with the swap, existing 22 tests plus one added assertion block | 23/23 pass | Happy path now asserts stored `device_id` equals the seeded UUID and kind `QR`; wrong-secret test hits the seeded device | None needed |

Mutation sanity check (applied then reverted, file restored byte-identical): persisting `command.deviceId()` instead of `authenticatedDevice.toString()` made the use-case test class fail.

### Work Unit Evidence (S2)
| Evidence | Value |
|---|---|
| Focused tests | `./gradlew :api:physical:test --tests "*ProcessPhysicalCheckInUseCaseImplTest" --tests "*PhysicalConfigurationTest" --tests "*PhysicalCheckInExceptionHandlerTest" --tests "*LegacyCheckInDeviceTokenPropertyWarningTest" --tests "*InvalidDeviceTokenExceptionTest" --tests "*CheckInRequestTest"` exit 0: 16+15, 19, 17, 4, 1, 25 tests, 0 failures |
| Runtime harness | `./gradlew :api:app:test --tests "*PhysicalCheckInIntegrationTest"` exit 0 (Docker available): 23 tests, 0 failures, 0 errors. This also proves the app context loads: the `@Component` rejection adapter gets its `MeterRegistry`, and `physicalDeviceAuthenticator` wires |
| Module gate | `./gradlew :api:physical:test :api:physical:jacocoTestReport :api:physical:jacocoTestCoverageVerification :api:physical:checkstyleMain :api:physical:checkstyleTest` exit 0 (run twice, final after the Checkstyle line fixes): 463 tests, 0 failures, 0 errors, 0 skipped; `*ArchitectureTest` 8/8 |
| App gate | `./gradlew :api:app:test :api:app:jacocoTestReport :api:app:jacocoTestCoverageVerification` exit 0: 379 tests, 0 failures, 0 errors, 0 skipped |
| JaCoCo (physical, LINE) | domain 96.91% (251/8) and application 98.57% (550/8), combined above the 95% gate (verification task passed); infrastructure 97.51% (706/18), gate 90% |
| Checkstyle | Warnings only, none failing the build. Overall count after S2: main 401, test 452 (S1 end: main 401, test 451). On added lines the only remaining warnings are `MethodName` on snake_case test names (same as every sibling) and `VariableDeclarationUsageDistance` for `authenticatedDevice` (gate 1 must stay first, so the variable is declared far from its single use) |
| Rollback boundary | Revert PR 2. Before reverting, set a non-default `app.physical.checkin.device-token` on prod/staging (the old fail-fast returns) |

### Slice Size (S2)
`git diff --numstat` (tracked, excluding openspec/): 201 additions + 160 deletions = 361. Untracked new files: 38 + 84 = 122. **Total 483 changed lines** (under 800 budget).

### Deviations from Design
- A8: the warning component uses `@RequiredArgsConstructor` (Lombok) instead of a hand-written constructor; behavior identical.
- `InvalidDeviceTokenException` javadoc was rewritten (it described the retired shared secret); not listed in the design but doc-only.
- Handler 401 `detail` texts: "The device has been revoked." and "The device credential has expired." (the design fixed only the codes).
- `CheckInRequestTest` and `InvalidDeviceTokenExceptionTest` needed no change: they test presence validation and the error code only, both unaffected.

### Issues Found
- The shell `rg` is shadowed by an rtk rewrite that rejects `-g`; `/opt/homebrew/bin/rg` works.
- Gradle console output is mangled by rtk; all counts above come from JUnit and JaCoCo XML reports.
- `PhysicalConfiguration` still contains `@Value` for two other beans, so that import stays.

### Remaining
- 2.11 PR 2 (orchestrator), then Phase 3 and 4.

---

## Batch 3: Phase 3 (S3, PR 3, contract + scenarios + docs) — tasks 3.1 to 3.5 complete; 3.6 (PR creation) left to the orchestrator

### Completed Tasks
- [x] 3.1 New `PhysicalCheckInDeviceAuthIntegrationTest` (characterization over S2 code)
- [x] 3.2 `api/openapi/physical-v1.yaml` (intro, endpoint description, 401 with 3 examples, `CheckInQrRequest`, rotate-secret note, stale "inert expiry" text)
- [x] 3.3 Bruno `QR Check-in.bru` (+ one stale doc line in `Register Device.bru`)
- [x] 3.4 Check-in docs: `docs/05-PHYSICAL-API.md` (new subsection + runbook), `US-PHYSICAL-007.md`, `US-PHYSICAL-001.md`, `SEQUENCE-DIAGRAMS.md` section 6; stale javadoc in `PhysicalCheckInController` and in `PhysicalCheckInIntegrationTest`
- [x] 3.5 Gates (see evidence)
- [ ] 3.6 PR 3 creation (orchestrator)

### TDD Cycle Evidence (S3)
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 3.1 | `api/app/.../integration/physical/PhysicalCheckInDeviceAuthIntegrationTest` | Integration (Testcontainers MySQL, real HTTP, real hashing) | `PhysicalCheckInIntegrationTest` 23/23 untouched (only a javadoc line edited) | Characterization: the scenarios target S2 code that already exists, so no failing RED over production code was possible. First run: 5 of 20 failed, all test defects (blank id is request validation = 400 by spec; root appender missed logs because `logback-spring.xml` sets `additivity=false` on `com.menta`; boundary test mixed a rejection and a pass in one test so `verifyNoInteractions(redis)` saw the second request). No S2 production defect found | 20/20 pass after fixing the tests (final: 19/19 after dropping one redundant test) | Each rule has a positive and a negative twin: expiry `==now` rejected vs `now+1s` accepted (pinned clock); non-UUID never queries the registry vs unknown UUID does (spy); correct vs wrong secret on revoked/expired; unknown/non-UUID/wrong-secret/legacy bodies equal; 4 non-UUID ids; 4 blank/absent body variants | Alarm marker extracted to a constant; checkstyle line-length and variable-distance warnings fixed |
| 3.2-3.4 | N/A (contract and docs) | N/A | OpenAPI linted before/after | N/A | `redocly lint` unchanged: 16 `security-defined` errors + 1 `info-license` + 1 `no-server-example-com`, identical to baseline | N/A | N/A |

Mutation sanity checks on `PhysicalDeviceAuthenticator` (applied, run, then restored; file restored byte-identical, `git diff` empty): (A) `isBefore` instead of `!isAfter` for expiry made `a_device_whose_expiry_equals_the_current_instant_is_expired` fail (1 failed); (B) letting non-UUID ids fall through to a random-UUID registry lookup made 6 tests fail (4 non-UUID cases, the identical-bodies test and the no-device-id log test).

### Scenario coverage (`PhysicalCheckInDeviceAuthIntegrationTest`, 19 tests)
Active device without expiry checks in and `device_id` equals the UUID (QR kind, no alarm, no counter); unknown UUID / `reader-1` / wrong secret / legacy shared secret / legacy secret on a registered id give identical 401 `INVALID_DEVICE_TOKEN` bodies and 5 `unknown_or_invalid` increments; non-UUID ids (`reader-1`, `not-a-uuid`, `1-1-1-1-1`, `0`) give 401 with `verifyNoInteractions` on the device registry spy, and an unknown UUID does hit it; revoked, expired (past) and expired (`expiresAt == now`, pinned `Clock`) give their codes; `now+1s` still checks in (201); revoked beats expired; wrong secret on revoked/expired gives INVALID with no revoked/expired increments; WARN line asserted literally (`alarm=physical_checkin_device_rejected reason=revoked deviceId=<uuid>`, `reason=unknown_or_invalid deviceId=none`) at level WARN, exactly one per rejection; counter `physical.checkin.device.rejected{reason}` increments only the matching reason; neither raw id `reader-1` nor any secret appears in any captured log line; blank/absent `deviceId`/`deviceToken` keep 400 `INVALID_REQUEST` with no registry access or alarm; MANUAL check-in runs no device authentication. Every rejection case uses an otherwise fully valid scan, and asserts no attendance row and `verifyNoInteractions(redisTemplate)`.

### Work Unit Evidence (S3)
| Evidence | Value |
|---|---|
| Focused tests | `./gradlew :api:app:test --tests "*PhysicalCheckInDeviceAuthIntegrationTest"` exit 0: 20 tests (before trim) then 19 tests (final, in the full app run), 0 failures, 0 errors, 0 skipped |
| Runtime harness | Same command on Testcontainers MySQL (Docker 28.1.1) over `RANDOM_PORT` HTTP with the real `SecurityConfig` chain, `Sha256DeviceSecretHasher`, a `@SpyBean` registry port and an autowired `MeterRegistry`; Bruno was not run against a live API (no local API process started) |
| Full gate | `./gradlew check` exit 0 (BUILD SUCCESSFUL 3m 36s) with 20 new tests; test counts from JUnit XML: physical 463, app 399, shared 64, auth 527, billing 858, virtual 344, 0 failures/errors/skipped |
| Final gate after last test edits | `./gradlew :api:physical:test :api:physical:jacocoTestReport :api:physical:jacocoTestCoverageVerification :api:physical:checkstyleMain :api:physical:checkstyleTest :api:app:test :api:app:jacocoTestReport :api:app:jacocoTestCoverageVerification :api:app:checkstyleMain :api:app:checkstyleTest --rerun-tasks` exit 0 (3m 41s): physical 463/0, app 399/0, `ArchitectureTest` 8/8, `PhysicalCheckInIntegrationTest` 23/23. After dropping one redundant test: `./gradlew :api:app:test :api:app:jacocoTestReport :api:app:jacocoTestCoverageVerification :api:app:checkstyleTest` exit 0, app 398/0 |
| JaCoCo | verification tasks passed for physical and app (no production code changed except a javadoc); physical LINE 97.79% (1507/34), BRANCH 92.88%; app LINE 92.96%, BRANCH 74.19% (module-level totals from the XML report; thresholds enforced by the verification task) |
| Checkstyle | new test file: only `MethodName` warnings on snake_case test names (13, same as every sibling); app test total 893 (880 before), main counts unchanged (physical main 401, test 452, app main 82). No LineLength, import or distance warnings on added lines |
| OpenAPI lint | CI `openapi-validation` only lints `auth-v1.yaml` and `billing-v1.yaml` (`.github/workflows/pr-develop.yml`), so `physical-v1.yaml` is not gated. Ran `docker run redocly/cli:latest lint` on baseline and new copies: both 16 errors (`security-defined`, pre-existing: no `securitySchemes`) + 2 warnings, identical |
| Rollback boundary | Revert PR 3: test class, OpenAPI, Bruno, docs and javadoc only; runtime behavior is unaffected |

### Slice Size (S3)
`git diff --numstat` (tracked, excluding openspec/): 180 additions + 52 deletions = 232. Untracked new file: `PhysicalCheckInDeviceAuthIntegrationTest.java` 550 lines. **Total 782 changed lines** (budget 800; the ~400 forecast was exceeded because the new class carries 19 scenarios, but it stays under budget). Docs and OpenAPI account for 180 of the changed lines.

### Deviations from Design
- The tasks listed `verifyNoInteractions(redisTemplate)` and MANUAL checks per case; all are done, plus a `@SpyBean` on the device registry port to prove "non-UUID never touches the DB" (design only required it for the unit test).
- A `@TestConfiguration` with a `@Primary` pinnable `Clock` (system clock unless pinned) is used to hit the exact `expiresAt == now` boundary over HTTP; this gives the class its own Spring context (separate from `PhysicalCheckInIntegrationTest`).
- Bruno needed no new variable: `physicalDeviceId` and `physicalDeviceSecret` are already set by `Register Device.bru` and `Rotate Device Secret.bru` post-response scripts, so `QR Check-in.bru` now reads those (the old `physicalDeviceToken` was defined nowhere).
- Stale "inert expiry" statements (OpenAPI register description and `expiresAt` schema, Bruno `Register Device.bru`) were rewritten because #266 makes them false; this goes slightly beyond the check-in-only docs list.

### Issues Found
- No defect in S2 production code: all characterization scenarios pass on it. The 5 first-run failures were test defects (listed above).
- Stale-but-out-of-scope (D9), left untouched: `docs/05-PHYSICAL-API.md` and `US-PHYSICAL-007.md` still show `/api/v1/physical/devices` paths (real routes are `/api/v1/admin/physical/devices`), say the secret hash is bcrypt/argon2 (it is SHA-256), and `docs/05` still says the admin list derives an `EXPIRED` status. SEQUENCE-DIAGRAMS section 6 also keeps other drifted codes after the device block (`INVALID_QR_SIGNATURE`, `QR_EXPIRED`, `QR_ALREADY_USED`).
- `logback-spring.xml` sets `additivity=false` for `com.menta`, so a test that captures logs must attach its appender to `com.menta`, not only to root.
- Shell `rg` is shadowed by rtk; `timeout` does not exist on macOS; Gradle console output is mangled, counts were read from JUnit/JaCoCo/Checkstyle XML.

### Remaining
- 3.6 PR 3 (orchestrator, `Closes #266`), then Phase 4 (board, rate-limit issue, archive-time prose trim).

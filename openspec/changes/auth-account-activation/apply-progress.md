# Apply progress: auth-account-activation

## PR1 — Domain foundation

### Completed

- Tasks 0.2 and 0.3: RED tests for pending registration and non-enumerating login.
- Task 1.1: `PENDING_ACTIVATION`, `User.register()` and guarded activation.
- Task 1.2: `ActivationToken` lifecycle with explicit time input.
- Task 1.3: additive Flyway V3 for hashed tokens and encrypted delivery envelopes.
- Task 1.4: application-layer ports for the activation slice —
  `ActivationTokenRepository`, `ActivationTokenGenerator`,
  `ActivationTokenHasher`, `ActivationDeliveryCipher` (+ `DeliveryEnvelope`
  value type), `ActivationRateLimitPort` (+ `RateLimitDecision` value type),
  and the `AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED` outbox
  contract constant.

### TDD evidence

- RED (0.2/0.3/1.1/1.2/1.3, prior batch): focused compilation failed because
  `PENDING_ACTIVATION`, `User.register()`, `ActivationToken`, and
  `ActivationTokenStatus` did not exist.
- GREEN (prior batch): focused tests passed after the minimum domain
  implementation.
- RED (1.4): `./gradlew :api:auth:compileTestJava` failed with `cannot find
  symbol` for `ActivationTokenRepository`, `ActivationTokenGenerator`,
  `ActivationTokenHasher`, `ActivationDeliveryCipher`, `DeliveryEnvelope`,
  `ActivationRateLimitPort`, `RateLimitDecision`, and
  `AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED` — every symbol
  referenced by the three new test files in
  `api/auth/src/test/java/com/menta/auth/application/port/out/`.
- GREEN (1.4): added the minimal interfaces plus two value types
  (`DeliveryEnvelope`, `RateLimitDecision`) carrying real invariants (AES-GCM
  12-byte nonce, positive key version, non-empty ciphertext; allowed vs.
  limited retry-after consistency). `./gradlew :api:auth:test --tests
  "com.menta.auth.application.port.out.*"` → 3 test classes, all green
  (`ActivationApplicationPortsContractTest` 5/5,
  `DeliveryEnvelopeTest` 4/4, `RateLimitDecisionTest` 4/4).
- Regression: `./gradlew :api:auth:test` (full module) passed.
- ArchUnit: `./gradlew :api:auth:test --tests "*ArchitectureTest*"` passed,
  including `application_should_not_use_spring_web_annotations` (the ports
  live in `application.port.out` and import only `java.*`/domain types).
- Quality: `./gradlew :api:auth:check` passed (JaCoCo + Checkstyle);
  Checkstyle reported only pre-existing non-blocking warnings, none on the
  new task-1.4 files.
- Changed lines for task 1.4: 435 (7 new production files ≈184 lines, 3 new
  test files ≈200 lines, +8-line edit to `AuthOutboxEventTypes.java`) — well
  under the 550-line ledger budget for this work unit.

### TDD Cycle Evidence (task 1.4)

| Step | RED | GREEN | REFACTOR |
|---|---|---|---|
| `ActivationTokenRepository` | compile failure in `ActivationApplicationPortsContractTest` | minimal interface + in-memory test fake | not needed — 3-method interface |
| `ActivationTokenGenerator` / `ActivationTokenHasher` | compile failure in same file | minimal single-method interfaces | not needed |
| `ActivationDeliveryCipher` / `DeliveryEnvelope` | compile failure across `ActivationApplicationPortsContractTest` and `DeliveryEnvelopeTest` | interface + immutable value type with AES-GCM nonce/keyVersion/ciphertext validation | none — invariants are the minimal contract |
| `ActivationRateLimitPort` / `RateLimitDecision` | compile failure across `ActivationApplicationPortsContractTest` and `RateLimitDecisionTest` | interface + immutable value type with allowed/limited invariants | none |
| `AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED` | compile failure in `ActivationApplicationPortsContractTest` | added constant matching spec string `auth.AccountActivationRequested` | none |

### Task 1.5 — atomic register wiring

- `RegisterUserUseCaseImpl` now depends on 8 collaborators (was 2):
  `UserRepository`, `PasswordEncoderPort`, `ActivationTokenRepository`,
  `ActivationTokenGenerator`, `ActivationTokenHasher`,
  `ActivationDeliveryCipher`, `ActivationRateLimitPort`, `OutboxAppender`.
  Flow: rate-limit check (email SHA-256 fingerprint computed in-use-case +
  opaque client fingerprint from the command) → duplicate-email check →
  password hash → `User.register()` + save → generate/hash activation token
  → `ActivationToken.issue()` with a 24h TTL (`Instant.now()`, deferred to
  task 1.7 for injectable clock) → encrypt `email|rawToken` into a
  `DeliveryEnvelope` → persist token+envelope → append exactly one
  `auth.AccountActivationRequested` outbox event carrying only
  `activationTokenId` (never the raw token).
- New `TransactionalRegisterUserUseCase` decorator
  (`infrastructure/transaction/`) wraps the impl with `@Transactional`,
  mirroring `TransactionalLoginUseCase`/`TransactionalLogoutUseCase`; it is
  now the only `RegisterUserUseCase` bean `AuthConfiguration` exposes.
- New `ActivationRateLimitedException` (`domain/exception/`, extends
  `BusinessException`) carries `retryAfter` for the future 429 mapping
  (task 3.x).
- **Deviation from the frozen task-1.4 port contract (flagged, not
  re-litigated silently)**: `ActivationTokenRepository` gained one additive
  `default` method — `save(ActivationToken, DeliveryEnvelope)` — because the
  V3 schema stores `delivery_ciphertext`/`nonce`/`key_version` in the same
  row as the token, but the frozen `ActivationToken` domain aggregate cannot
  reference `DeliveryEnvelope` (an application-layer type) without violating
  the domain→application dependency direction. The default delegates to the
  existing single-arg `save` (discarding the envelope) so the task-1.4
  in-memory fake in `ActivationApplicationPortsContractTest` keeps compiling
  unmodified in behavior; the real JPA adapter (task 2.1) MUST override it.
  One additive test was appended to that same contract-test file to keep
  the application-layer JaCoCo bundle at 100% line coverage for the new
  default-method body.
- `RegisterUserCommand` gained a `clientFingerprint` field (opaque, may be
  `null` until task 3.1 derives it from the HTTP request per design.md
  "Puertos principales"). `UserController` was updated mechanically (one
  call site) to pass `null` with a `// TODO(task 3.1)` marker — this is a
  compile-boundary consequence of the DTO change, not new behavior.
- Compile-boundary strategy per plan: 5 placeholder infra adapters in
  `infrastructure/activation/` (`NotImplementedActivationTokenRepository`,
  `NotImplementedActivationRateLimitPort`,
  `NotImplementedActivationDeliveryCipher`,
  `NotImplementedActivationTokenGenerator`,
  `NotImplementedActivationTokenHasher`), each throwing
  `UnsupportedOperationException` with a `// TODO(PR2 task 2.x)` pointer.
  `AuthConfiguration` wires all 5 as individual `@Bean`s (reusable by the
  task-1.6 `ActivateAccountUseCase`/`ResendActivationUseCase` beans) plus
  the updated `registerUserUseCase` bean, which now returns
  `TransactionalRegisterUserUseCase` wrapping `RegisterUserUseCaseImpl`.

#### TDD evidence (task 1.5)

- RED: `./gradlew :api:auth:compileTestJava` failed —
  `RegisterUserUseCaseImplTest.java:59: error: constructor
  RegisterUserUseCaseImpl in class RegisterUserUseCaseImpl cannot be
  applied to given types` — the rewritten test's 8-arg constructor call
  didn't match the still-2-arg production constructor.
- GREEN: implemented the 8-arg constructor + atomic flow described above.
  One intermediate RED surfaced during GREEN validation: a test stub used
  `"h".repeat(64)` as a fake token hash, and `h` is not a valid
  `[0-9a-f]` hex char, so `ActivationToken.issue()` legitimately rejected
  it (`IllegalArgumentException: tokenHash must be a lowercase SHA-256 hex
  digest`) — fixed the test fixture to `"c".repeat(64)`, not the
  production code.
- Focused suite green: `RegisterUserUseCaseImplTest` (12 tests, admin/
  instructor rejection, rate-limit rejection, duplicate-email rejection,
  role defaulting, 24h TTL, outbox payload excludes the raw token and
  contains `activationTokenId`, delivery envelope built from
  `email|rawToken`, rate limit consumed with a 64-char SHA-256 email
  fingerprint never the raw address), `TransactionalRegisterUserUseCaseTest`
  (2 tests: delegation + `@Transactional` presence via reflection),
  `NotImplementedActivationAdaptersTest` (4 tests, one per placeholder
  class group), `ActivationApplicationPortsContractTest` (now 6 tests,
  +1 for the new default `save(token, envelope)` overload) — all green.
- Full regression: `./gradlew :api:auth:test` → 138 tests across the whole
  module, 0 failures, 0 errors.
- ArchUnit: `./gradlew :api:auth:test --tests "*ArchitectureTest*"` → 14/14
  green, including `application_should_not_use_spring_web_annotations` and
  the domain/application dependency-direction rules (the port's new default
  method stays inside `application.port.out`; the domain `ActivationToken`
  aggregate was deliberately NOT touched to avoid a domain→application
  violation).
- Quality gate: `./gradlew :api:auth:check` passed — JaCoCo BUNDLE
  (`domain.*` + `application.*`) at 1.00 LINE, infrastructure BUNDLE
  comfortably above the 0.50 floor; Checkstyle reported only WARN-level
  (non-blocking) violations, consistent with the project's existing
  baseline (snake_case JUnit method names, import-order nits), none of
  which fail the build.

#### Changed-line budget — flagged overage

Precise before/after diff for every file touched in this task (new files
counted at full size; edited files diffed against their pre-task-1.5
content) totals **671 changed lines**, exceeding the acquired ledger
budget of 550 by 121 lines. Breakdown:

| Group | Lines |
|---|---|
| 5 placeholder adapters (new) | 141 |
| Placeholder adapters' optional trivial test (new) | 75 |
| `TransactionalRegisterUserUseCase` (new) + test (new) | 85 |
| `ActivationRateLimitedException` (new) | 26 |
| `RegisterUserUseCaseImpl` rewrite (+83/-8) | 91 |
| `RegisterUserUseCaseImplTest` rewrite (+153/-16) | 169 |
| `AuthConfiguration` wiring (+55/-2) | 57 |
| `ActivationTokenRepository` default overload (+17/-0) | 17 |
| `ActivationApplicationPortsContractTest` +1 test (+12/-0) | 12 |
| `RegisterUserCommand` field (+8/-1) | 9 |
| `UserController` call-site fix (+2/-1) | 3 |
| **Total** | **671** |

No line of this is PR2 real infrastructure (JPA/Redis/AES-GCM) — every
placeholder adapter is a same-shape `UnsupportedOperationException` stub.
The overage is fully attributable to faithfully implementing every
deliverable explicitly enumerated for task 1.5 (RED/GREEN suite, the
transactional decorator + its own test, all 5 placeholder adapters wired
through `AuthConfiguration`, and the minimal port/DTO extensions needed for
the module to compile) under strict TDD with 100%-line domain/application
coverage. Flagged for the orchestrator's visibility; not silently
absorbed.

### Task 1.6 — ActivateAccountUseCase and ResendActivationUseCase

- New `ActivateAccountUseCaseImpl` (`application/usecase/`) hashes the raw
  token via `ActivationTokenHasher`, looks it up via
  `ActivationTokenRepository.findByHash`, and delegates every lifecycle rule
  to the aggregates themselves — `ActivationToken.statusAt(now)` for the
  vigente/no-usado/no-invalidado check and `ActivationToken.consume(now)` +
  `User.activate()` for the transition — rather than reimplementing expiry
  or status logic in the use case. Any failure (token not found, not
  `ACTIVE` per `statusAt`, or an `IllegalStateException` from a racing
  `user.activate()`/`token.consume()` call) collapses into the single
  generic `ActivationTokenInvalidException` so a caller cannot distinguish
  the reason over the wire (design.md "Flujo de activación" +
  auth-account-activation spec "Reutilización"/"Expiración o
  invalidación"/"Activación concurrente"). Nothing is persisted unless both
  the token consume and the user activate succeed, so a mid-way failure
  never leaves a partial state, even before the transactional wrapper is
  considered.
- New `TransactionalActivateAccountUseCase` decorator
  (`infrastructure/transaction/`) wraps the impl with `@Transactional`,
  mirroring `TransactionalRegisterUserUseCase`/`TransactionalLoginUseCase`.
- New `ResendActivationUseCaseImpl` (`application/usecase/`) reuses
  `RegisterUserUseCaseImpl`'s rate-limit-first ordering (email SHA-256
  fingerprint + opaque client fingerprint) and its
  generate/hash/encrypt/persist/outbox-append sequence for issuing a fresh
  token. It returns the exact same `ResendActivationResult.ACKNOWLEDGED`
  value for a nonexistent email, an already-active email, and a pending
  email — only when the looked-up user exists AND is
  `PENDING_ACTIVATION` does it invalidate prior active tokens via
  `activationTokenRepository.invalidateActiveByUserId` and issue+persist a
  new one, appending exactly one `auth.AccountActivationRequested` outbox
  event carrying only `activationTokenId` (never the raw token) — matching
  design.md decision #6 ("Respuesta uniforme para registro/reenvío").
- New `ResendActivationResult` (`application/dto/`) is a deliberately
  field-less record so its shape cannot leak account existence/state;
  `ResendActivationResult.ACKNOWLEDGED` is the single shared instance
  returned on every path.
- New `TransactionalResendActivationUseCase` decorator wraps the impl with
  `@Transactional`, same pattern as the register/activate decorators.
- New `ActivationTokenInvalidException` (`domain/exception/`, extends
  `BusinessException`) mirrors `InvalidCredentialsException`'s
  non-discriminating shape — one error code, one generic message, no
  reason-specific subclassing.
- New port-in interfaces `ActivateAccountUseCase` and
  `ResendActivationUseCase` (`application/port/in/`), and new commands
  `ActivateAccountCommand` (raw token) and `ResendActivationCommand`
  (email + opaque `clientFingerprint`, mirroring
  `RegisterUserCommand#clientFingerprint`).
- **Deferred, not silently skipped**: `AuthConfiguration` wiring for these
  two new use cases was left out per the task's explicit optionality
  clause — no controller exists yet (task 3.1/3.2 are PR3 scope) to invoke
  either bean, so wiring them now would add dead configuration surface
  without a caller. This is explicitly deferred to task 3.4, which already
  owns "wiring en `AuthConfiguration` y wrappers transaccionales" for the
  whole slice.

#### TDD evidence (task 1.6)

- RED: `./gradlew :api:auth:compileTestJava` failed with `cannot find
  symbol` for every new type referenced by the four new test files —
  `ActivateAccountCommand`, `ResendActivationCommand`,
  `ResendActivationResult`, `ActivationTokenInvalidException`,
  `ActivateAccountUseCase`, `ActivateAccountUseCaseImpl`,
  `ResendActivationUseCase`, `ResendActivationUseCaseImpl` — none of them
  existed before this task.
- GREEN: added the minimal DTOs, port-in interfaces, the two use case
  implementations, the one new domain exception, and the two transactional
  decorators described above.
- Focused suite green: `./gradlew :api:auth:test --tests
  "com.menta.auth.application.usecase.ActivateAccountUseCaseImplTest"
  --tests
  "com.menta.auth.application.usecase.ResendActivationUseCaseImplTest"
  --tests
  "com.menta.auth.infrastructure.transaction.TransactionalActivateAccountUseCaseTest"
  --tests
  "com.menta.auth.infrastructure.transaction.TransactionalResendActivationUseCaseTest"`
  → BUILD SUCCESSFUL; 6 + 5 + 2 + 2 = 15 tests, 0 failures, 0 errors
  (verified by reading each test's JUnit XML report directly, since Gradle
  reports `UP-TO-DATE`/`from cache` for tasks with no changed inputs on a
  second run).
  - `ActivateAccountUseCaseImplTest` (6): happy-path activation + token
    consumption in one call, unknown token hash, already-used token,
    expired token, invalidated token, and a racing activation where the
    token is `ACTIVE` but the user is no longer `PENDING_ACTIVATION` — all
    five failure cases assert the single `ActivationTokenInvalidException`
    type and zero persistence calls.
  - `ResendActivationUseCaseImplTest` (5): rate-limited request rejected
    before any lookup, nonexistent email returns the uniform
    acknowledgement with no side effects, already-active email same, a
    pending email reissues a token (invalidate-prior + generate/hash/
    encrypt/persist/outbox with `activationTokenId` only, never the raw
    token), and one dedicated test asserting the three response values
    (nonexistent/active/pending) are `.isEqualTo(...)` each other and the
    shared `ACKNOWLEDGED` constant — locking in the non-enumeration
    property at the type level.
  - `TransactionalActivateAccountUseCaseTest` (2) /
    `TransactionalResendActivationUseCaseTest` (2): delegation +
    `@Transactional` presence via reflection, mirroring
    `TransactionalRegisterUserUseCaseTest`.
- Full regression: `./gradlew :api:auth:test` → 153 tests across the whole
  module (138 prior + 15 new), 0 failures, 0 errors (counted by parsing
  every `TEST-*.xml`'s `tests=`/`failures=`/`errors=` attributes, since a
  cached Gradle run reports `UP-TO-DATE` without re-printing a summary).
- ArchUnit: `./gradlew :api:auth:test --tests "*ArchitectureTest*"` → 14/14
  green — the two new use cases stay in `application.usecase` importing
  only `domain.*`/`application.port.out.*`, and the two new decorators stay
  in `infrastructure.transaction` importing only `application.port.in.*` +
  Spring's `@Transactional`, so no dependency-direction rule is at risk.
- Quality gate: `./gradlew :api:auth:check` → BUILD SUCCESSFUL. JaCoCo
  `jacocoTestCoverageVerification` passed on its own re-run (all inputs
  `UP-TO-DATE`, confirming the domain+application 100%-line bundle
  threshold still holds with the new code covered). Checkstyle reported
  only WARN-level violations across both main and test sources — import
  ordering, `<p>` after a blank Javadoc line, line length, snake_case test
  method names — consistent with the project's pre-existing baseline
  (the same categories already present in files this task did not touch,
  e.g. `AuthControllerTest.java`, `JwtServiceTest.java`); none fail the
  build.

#### Changed-line count for task 1.6 (this task only, not cumulative)

All 14 files below are brand-new (no tracked file was modified by this
task):

| File | Lines |
|---|---|
| `application/dto/ActivateAccountCommand.java` | 8 |
| `application/dto/ResendActivationCommand.java` | 12 |
| `application/dto/ResendActivationResult.java` | 12 |
| `domain/exception/ActivationTokenInvalidException.java` | 22 |
| `application/port/in/ActivateAccountUseCase.java` | 19 |
| `application/port/in/ResendActivationUseCase.java` | 17 |
| `application/usecase/ActivateAccountUseCaseImpl.java` | 72 |
| `application/usecase/ResendActivationUseCaseImpl.java` | 134 |
| `infrastructure/transaction/TransactionalActivateAccountUseCase.java` | 31 |
| `infrastructure/transaction/TransactionalResendActivationUseCase.java` | 33 |
| `test/.../ActivateAccountUseCaseImplTest.java` | 185 |
| `test/.../ResendActivationUseCaseImplTest.java` | 182 |
| `test/.../TransactionalActivateAccountUseCaseTest.java` | 39 |
| `test/.../TransactionalResendActivationUseCaseTest.java` | 43 |
| **Total** | **809** |

This exceeds the 550-line ledger budget for the `pr1-activate-resend` work
unit by 259 lines. Flagged for the orchestrator's visibility, same as the
task 1.5 overage: the overage is attributable to faithfully implementing
both use cases plus both transactional decorators plus the full RED/GREEN
Mockito suite the task explicitly required (happy path + 4 rejection
scenarios for activate; rate-limit + 2 no-op + 1 reissue + 1
identical-shape scenario for resend, per the design's non-enumeration
requirement), under strict TDD. `AuthConfiguration` wiring was
deliberately skipped (see above) to avoid adding further unreviewed lines
for beans nothing yet calls.

### Task 1.7 — Clock/TTL injection (REFACTOR, PR1's last task)

- New application-layer port `Clock` (`application/port/out/Clock.java`,
  single method `Instant now()`) and its production adapter `SystemClock`
  (`infrastructure/time/SystemClock.java`, `@Component`-scanned, delegates
  to `Instant.now()`) — mirrors the existing `OutboxClock`/`SystemOutboxClock`
  pair for the outbox-specific concern, kept as a separate port because it is
  a cross-use-case application concern, not outbox-specific.
- `RegisterUserUseCaseImpl` and `ResendActivationUseCaseImpl` each gained two
  new constructor params — `Clock clock` and `Duration activationTokenTtl` —
  replacing the duplicated `private static final Duration
  ACTIVATION_TOKEN_TTL = Duration.ofHours(24)` constant and every internal
  `Instant.now()` call with `clock.now()`. `ActivateAccountUseCaseImpl`
  gained only `Clock clock` (it never issues a TTL).
- `AuthConfiguration` gained `@Value("${auth.activation.token-ttl:PT24H}")
  private Duration activationTokenTtl;` (Spring binds ISO-8601 duration
  strings to `java.time.Duration` natively — same `@Value` pattern already
  used for `auth.jwt.base64-secret`/`auth.access-token-ttl-seconds`) and
  passes `clock`/`activationTokenTtl` into the `registerUserUseCase` bean.
  `Clock` itself needs no explicit `@Bean` method — `SystemClock` is
  `@Component`-scanned, same as `SystemOutboxClock`.
  `ActivateAccountUseCase`/`ResendActivationUseCase` beans remain
  deliberately unwired (deferred to task 3.4, per task 1.6's note — no
  controller exists yet).
- `api/app/src/main/resources/application.yml` gained
  `auth.activation.token-ttl: ${AUTH_ACTIVATION_TOKEN_TTL:PT24H}` alongside
  the other `auth.*` keys, documenting the single source of truth for the
  design.md decision #3 default (24h, configurable).
- `ActivationToken.toString()` no longer calls `statusAt(Instant.now())` —
  it now prints the raw `usedAt`/`invalidatedAt` fields instead of a
  derived "current" status, since deriving that status requires the domain
  to summon its own `now()`, which task 1.7 explicitly forbids
  ("eliminar `now()` del dominio"). This was the only domain-layer
  `Instant.now()` call in the activation slice; `consume`/`invalidate`/
  `statusAt`/`requireActive`/`issue` already took `now` as a parameter from
  task 1.2 and needed no change.
- Deliberately out of scope (confirmed via `rg -n "Instant.now()\|
  LocalDateTime.now()"` across `api/auth/src/main`): `User.java`,
  `RefreshToken.java`, `LoginUseCaseImpl.java`, `RefreshTokenUseCaseImpl.java`,
  `JwtService.java`, `RefreshTokenRepositoryAdapter.java`. None of these are
  part of the activation slice task 1.7 targets; touching them would exceed
  this task's scope and budget without being asked for.

#### The one legitimate new RED/GREEN pocket: configurable TTL

- RED: `RegisterUserUseCaseImplTest.uses_the_injected_ttl_instead_of_a_hardcoded_duration`
  constructs a second `RegisterUserUseCaseImpl` instance via a new
  `newUseCase(Duration ttl)` test helper with `Duration.ofHours(6)` instead
  of the default 24h, then asserts the persisted token's
  `expiresAt - createdAt == 6h` (and `!= DEFAULT_TTL`). Before this task,
  TTL was a hardcoded `private static final Duration`, so this test could
  not even compile against the 8-arg constructor — a legitimate compile-
  boundary RED consistent with every other RED in this REFACTOR task.
- GREEN: the constructor/field/`clock.now().plus(activationTokenTtl)`
  changes described above satisfy it without touching any other behavior.
  The existing `issues_and_persists_an_activation_token_with_a_24_hour_ttl`
  test is untouched and still passes, proving the *default* stayed 24h.
- A parallel deterministic-`toString()` test
  (`ActivationTokenTest.to_string_reports_raw_fields_without_deriving_a_live_status`)
  was added for the same reason: JaCoCo's BUNDLE 100%-line gate requires
  every domain line executed, and the rewritten `toString()` needed its own
  assertion of the new raw-fields shape (and the absence of `"status="`).

#### REFACTOR-only churn (mechanical, not new behavior)

- All three use cases' existing unit tests
  (`RegisterUserUseCaseImplTest`, `ActivateAccountUseCaseImplTest`,
  `ResendActivationUseCaseImplTest`) needed their direct `new
  XxxUseCaseImpl(...)` constructor calls updated for the new `Clock`/`TTL`
  params — expected churn, not new RED, per the task's own instructions.
  The three `Transactional*UseCaseTest` files were untouched: they mock the
  port-in interface, never the impl, so their constructor calls were never
  affected.
- `ActivateAccountUseCaseImplTest` was additionally rewritten for
  determinism: every test that reaches `token.statusAt(now)` now stubs
  `clock.now()` to return a single fixed `NOW = Instant.parse(
  "2026-08-14T00:00:00Z")` (via a new `stubClock()` helper, called only by
  the 5 tests whose production path actually invokes `clock.now()` — NOT
  the "unknown token hash" test, which fails before ever calling the port,
  and correctly has no stub, avoiding a Mockito strict-stubbing
  `UnnecessaryStubbingException`) and every previously-relative
  `Instant.now().minus(...)` construction became `NOW.minus(...)`. This
  directly serves the task's stated goal ("tests deterministas") — the
  suite no longer depends on wall-clock time at all. `RegisterUserUseCaseImplTest`
  and `ResendActivationUseCaseImplTest` did not need equivalent literal
  rewrites: neither test file asserted against a specific `Instant`, only
  against `Duration.between(...)`/`any(Instant.class)`, so stubbing
  `clock.now()` to one fixed value inside `stubHappyPathCollaborators()`/
  `stubTokenIssuance()` was sufficient.

#### TDD evidence (task 1.7)

- Full regression: `./gradlew :api:auth:test --rerun-tasks` → BUILD
  SUCCESSFUL. Parsed every `TEST-*.xml`'s `tests=`/`failures=`/`errors=`
  attributes directly (a cached/UP-TO-DATE run does not reprint a summary):
  **155 tests, 0 failures, 0 errors** (153 prior + 2 new: the TTL test and
  the `toString()` test).
- ArchUnit: `./gradlew :api:auth:test --tests "*ArchitectureTest*"
  --rerun-tasks` → `TEST-com.menta.auth.ArchitectureTest.xml` shows
  **14/14 green**, including `application_should_not_use_spring_web_annotations`
  and the domain/application dependency-direction rules — the new `Clock`
  port lives in `application.port.out` importing only `java.time.Instant`;
  `SystemClock` lives in `infrastructure.time` importing the port plus
  Spring's `@Component`.
- Quality gate: `./gradlew :api:auth:jacocoTestCoverageVerification
  --rerun-tasks` → BUILD SUCCESSFUL — the domain+application BUNDLE stayed
  at the required 1.00 LINE ratio with the new `Clock` interface (no
  executable lines) and the rewritten `toString()`/use-case bodies fully
  exercised. `./gradlew :api:auth:check` reported only pre-existing
  WARN-level Checkstyle violations (line length, snake_case test method
  names — the same categories already present in files this task did not
  touch), zero ERROR-level violations.
- Cross-module compile sanity: `./gradlew :api:app:compileTestJava` → BUILD
  SUCCESSFUL, confirming `TransactionalAuthIntegrationTest` (the one
  `@SpringBootTest` in `api:app` touching this wiring) still compiles
  against the new constructor signatures; its Testcontainers-backed
  execution was out of scope for this focused task per the task's own
  `./gradlew :api:auth:test` / `*ArchitectureTest*` / `:api:auth:check`
  verification list.

#### Changed-line count for task 1.7 (this task only, not cumulative)

Computed by diffing exact before/after snippets reconstructed from this
session's own pre-edit `Read` output (for the two files already tracked in
git — `RegisterUserUseCaseImpl.java`, `AuthConfiguration.java` — a plain
`git diff` would double-count tasks 1.1–1.6's still-uncommitted changes) and
via `git diff` directly for files untouched before this task
(`application.yml`, `tasks.md`):

| File | Changed lines |
|---|---|
| `application/port/out/Clock.java` (new) | 16 |
| `infrastructure/time/SystemClock.java` (new) | 19 |
| `domain/model/ActivationToken.java` (`toString()` only) | 3 |
| `test/.../domain/model/ActivationTokenTest.java` (new test) | 13 |
| `application/usecase/RegisterUserUseCaseImpl.java` | 17 |
| `application/usecase/ActivateAccountUseCaseImpl.java` | 8 |
| `application/usecase/ResendActivationUseCaseImpl.java` | 17 |
| `infrastructure/config/AuthConfiguration.java` | 16 |
| `api/app/.../application.yml` | 4 |
| `openspec/.../tasks.md` (checkbox only) | 1 |
| `test/.../RegisterUserUseCaseImplTest.java` | 40 |
| `test/.../ActivateAccountUseCaseImplTest.java` | 26 |
| `test/.../ResendActivationUseCaseImplTest.java` | 7 |
| **Total** | **187** |

Well under the 550-line ledger budget for the `pr1-clock-ttl-refactor` work
unit — this task's scope stayed intentionally narrow (only the activation
slice's `now()` calls and duplicated TTL constant, not every `Instant.now()`
in the module).

### Fase 1 status: COMPLETE

Tasks 1.1 through 1.7 are all `[x]` in `tasks.md` — PR1's full scope
(`feature/auth-account-activation-domain`) is implemented: domain model
(`UserStatus.PENDING_ACTIVATION`, `User.register()`, `ActivationToken`),
the additive V3 migration, every application-layer activation port, the
atomic register/activate/resend use cases (transactional decorators +
placeholder infra adapters for PR2), and now injectable `Clock`/TTL for
deterministic tests. Ledger work-unit `pr1-clock-ttl-refactor` settled
`outcome: passed`.

### Pending in PR1

- Task 0.1 is deferred until immediately before the outbox dispatch refactor
  so its RED expectation remains meaningful — this is a Fase 0
  characterization task, not part of PR1's Fase 1 scope, and remains the
  only unchecked item before Fase 2 (PR2, infrastructure) can begin.

## Post-1.7 review fixes

Out-of-band bugfix work triggered by an independent code review of the
completed PR1 work (Fase 1, tasks 1.1–1.7), not a numbered `tasks.md` item.
Both findings were pre-verified against the code before this work started;
this session's job was to fix them under strict TDD, not re-investigate
whether they were real. Work-unit `pr1-review-fixes` (ledger token
`sha256:81264f9c0dfa0688635be897c672256adec91ebf1ffc89249cbab806dc0a42b5`).

### Blocker 1 — live endpoint wired to placeholder adapters

`UserController`'s pre-existing `POST /api/v1/users/register` route was
still wired (via `AuthConfiguration`'s `registerUserUseCase` bean) to the
5 `NotImplemented*` placeholder adapters introduced by task 1.5. Any live
call would throw `UnsupportedOperationException` from inside a
`@Transactional` boundary. `tasks.md`'s own task 3.1 already plans
"exponer `POST /api/v1/auth/register` y mantener alias temporal
`/api/v1/users/register` sobre el mismo port-in" for PR3 — i.e. the plan
always intended to (re)wire this route only once PR2's real adapters exist.

**Fix**: `UserController.register()` now returns `503 Service Unavailable`
immediately, without ever calling `registerUserUseCase.register(...)` —
the placeholder chain is unreachable from this live route. The unused
imports (`RegisterUserCommand`, `UserResult`) were removed; the
`RegisterUserUseCase` constructor dependency was kept (harmless — no
Checkstyle rule in this repo's `google_checks.xml` flags unused private
fields) to keep the diff minimal and the constructor shape stable for
task 3.1's eventual rewiring. A one-line comment points at task 3.1 as the
tracked restoration point.

- RED: new `UserControllerTest` (`register_returns_503_and_never_invokes_the_use_case`,
  MockMvc standalone setup mirroring `AuthControllerTest`'s pattern) failed
  against the pre-fix controller (`./gradlew :api:auth:test --tests
  "com.menta.auth.infrastructure.web.controller.UserControllerTest"` → 1
  failed).
- GREEN: same test passed after the controller change (`verifyNoInteractions(registerUserUseCase)`
  confirms the placeholder chain is never reached).
- Docs: `docs/26-LOCAL-DEV-SETUP-HOWTO.md` had two `curl .../users/register`
  snippets asserting a working `201 Created` end-to-end flow — both now
  carry an explicit "temporarily disabled, 503, see PR3 task 3.1" note.
  `docs/29-NGINX-REVERSE-PROXY.md` was checked and left untouched: its two
  `users/register` mentions are about rate-limiting configuration and path
  existence, not about the route currently completing a working
  registration, so nothing there was misleading.

### Blocker 2 — silent envelope discard on `ActivationTokenRepository`

`ActivationTokenRepository#save(token, envelope)` had a `default` body that
silently delegated to `save(token)` and discarded the encrypted delivery
envelope entirely. Any future implementer (PR2's real JPA adapter) that
forgot to override it would compile fine, confirm user + token + outbox,
and never persist the envelope needed to actually deliver the activation
email — a silent, hard-to-detect data-loss failure mode, exactly the kind
of thing a `default` method should not make possible for a MUST-persist
contract.

**Fix**: `save(token, envelope)` is now a plain abstract interface method
(no default body), forcing every implementer to provide a real
implementation or fail to compile. Javadoc rewritten to drop the
"Defaults to..." paragraph and state implementers MUST persist the
envelope durably alongside the token in the same write.

Ripple effect, all within `ActivationApplicationPortsContractTest.java`:
- `NotImplementedActivationTokenRepository` already explicitly overrode
  this method (throws `UnsupportedOperationException`) — verified it still
  compiles unchanged.
- `InMemoryActivationTokenRepository` (the test's private fake) gained a
  second `Map<String, DeliveryEnvelope> envelopeByHash` and a real
  `save(token, envelope)` override that actually stores the envelope,
  plus a package-private `findEnvelopeByHash(tokenHash)` accessor used
  only by the test — a genuine reference implementation of correct
  behavior, not a compile-satisfying stub.
- The old test `repository_default_save_with_envelope_delegates_to_token_only_save`
  (which locked in the *wrong*, bug behavior as expected) was replaced by
  `repository_save_with_envelope_preserves_the_delivery_envelope`, which
  asserts the envelope is retrievable via `findEnvelopeByHash` after
  `save(token, envelope)` — proving preservation, not discard.

- RED: `./gradlew :api:auth:compileTestJava` failed —
  `InMemoryActivationTokenRepository is not abstract and does not override
  abstract method save(ActivationToken,DeliveryEnvelope) in
  ActivationTokenRepository` (the fake no longer compiled once the
  `default` was removed, since it never overrode the two-arg overload).
- GREEN: the fake's new override + `findEnvelopeByHash` + the rewritten
  test all pass (`./gradlew :api:auth:test --tests
  "com.menta.auth.application.port.out.ActivationApplicationPortsContractTest"`).

### Full regression after both fixes

- `./gradlew :api:auth:test --rerun-tasks` → **156 tests, 0 failures, 0
  errors** (155 prior + 1 new: `UserControllerTest`; the contract test's
  total count is unchanged since one test was renamed/rewritten in place,
  not added).
- `./gradlew :api:auth:test --tests "*ArchitectureTest*" --rerun-tasks` →
  BUILD SUCCESSFUL, including `controllers_should_not_depend_on_repositories`
  (the 503 controller depends on nothing but the port-in interface it
  already had) and the domain/application dependency-direction rules
  (`ActivationTokenRepository` stays in `application.port.out`).
- `./gradlew :api:auth:check --rerun-tasks` → BUILD SUCCESSFUL. JaCoCo
  `jacocoTestCoverageVerification` passed (domain+application 100%-line
  BUNDLE still holds — `UserController` itself is infrastructure, not in
  that bundle, but its own class shows 100% line coverage in the JaCoCo
  HTML report). Checkstyle reported only pre-existing WARN-level violations
  (import order, `<p>` after blank Javadoc line, snake_case test method
  names) across touched and untouched files alike — same categories as the
  project's existing baseline, zero ERROR-level violations, consistent
  with tasks 1.4–1.7's own Checkstyle results.
- `./gradlew :api:app:compileTestJava --rerun-tasks` → BUILD SUCCESSFUL,
  confirming no `:api:app` test (including
  `TransactionalAuthIntegrationTest`) breaks against the narrowed
  `ActivationTokenRepository` contract or the changed controller. A
  targeted search (`rg -n "users/register"
  api/app/src/test`) confirmed zero references to the disabled route from
  any `:api:app` test, matching the orchestrator's pre-verification.

### Changed-line count for this task (not cumulative with PR1's tasks)

Computed hunk-by-hunk against this session's own pre-edit content (these
files are all still untracked/uncommitted from earlier PR1 tasks, so a
plain `git diff` against HEAD would double-count tasks 1.1–1.7):

| File | +/- |
|---|---|
| `UserController.java` (method body + imports) | +6 / -19 |
| `ActivationTokenRepository.java` (javadoc + signature) | +6 / -6 |
| `ActivationApplicationPortsContractTest.java` (test + fake) | +24 / -3 |
| `docs/26-LOCAL-DEV-SETUP-HOWTO.md` (2 notes) | +7 / -0 |
| `UserControllerTest.java` (new file) | +59 / -0 |
| **Total** | **130 changed lines** |

Well under the 550-line ledger budget for `pr1-review-fixes`.

### Status

Both blockers fixed and green. `tasks.md` was not modified by this session
— task 3.1 correctly stays unchecked (PR3 scope), and no other numbered
task needed touching. Ledger work-unit `pr1-review-fixes` settled
`outcome: passed`.

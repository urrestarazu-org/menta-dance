# Tasks: local-catalog-content-e2e

> Topological, strict-TDD implementation plan for issue #127. The governing
> sources are the approved proposal, normative E2E-local specification and
> design in this change. This work deliberately excludes Mercado Pago (#128)
> and Bunny.net (#129) simulation.

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~850–1,150 LOC (shell, Java profile fixtures/tests, Bruno, docs) |
| 400-line PR review budget risk | High |
| Chained PRs recommended | **Yes** |
| Suggested chain | PR 1 = TASK-001–003 (safe runner + profile/fixture foundation); PR 2 = TASK-004–006 (Bruno journey, docs, full verification) |
| Delivery strategy | Ask before apply; preserve one work-unit commit per task |
| Largest task | TASK-003, ~260 LOC; within the 800-LOC per-task limit |

Decision needed before apply: approve the two stacked PRs above or a size
exception. PR 2 must target PR 1 and merge after it.

## TASK-001 — Add the scoped E2E runner and lifecycle safety

- **Status**: completed
- **Dependencies**: none
- **Estimated LOC**: ~190 (90 production shell, 100 shell tests/fixtures)
- **Modules**: scripts/, infra/docker/
- **Work**:
  - Test first: characterize argument parsing, prerequisite validation,
    dedicated Compose project arguments, safe reset/--clean, process cleanup,
    health polling and non-zero failure propagation.
  - Add scripts/e2e/catalog-content.sh using only
    infra/docker/database/docker-compose.yml and the explicit
    menta-e2e-catalog-content project name.
  - Require Docker Compose v2, JDK 21, Node 20.11.1, curl, jq, .env, and the
    Gradle wrapper before any E2E state mutation.
  - Launch the API in the fixture profile, own only its child process, retain
    healthy infrastructure for diagnosis by default, and clean solely its own
    Compose project on --clean.
- **Acceptance criteria**:
  1. No reset command can target the default/root Compose project or ordinary
     developer volumes.
  2. Health checks poll Docker/API readiness without fixed sleeps.
  3. A prerequisite, health, process, or later command failure exits non-zero
     with a safe diagnostic.
  4. ShellCheck passes for every added/changed shell file.
- **Persistence impact**: dedicated ephemeral/local Compose state only; no
  migration and no application schema write.
- **Commit**: feat(e2e): add isolated catalog content runner

## TASK-002 — Guard and seed the Auth E2E baseline

- **Status**: completed
- **Dependencies**: TASK-001
- **Estimated LOC**: ~180 (75 production, 105 tests)
- **Modules**: api:auth, api:app composition only if required
- **Work**:
  - Test first: prove the e2e-catalog-content profile is opt-in, is rejected
    when combined with prod/production, and creates/looks up exactly one
    deterministic local administrator identity/role on repeated invocation.
  - Implement the profile-scoped Auth fixture through Auth-owned application
    ports or a narrowly added Auth application port. Never directly seed
    auth_* tables from api:app or another module.
  - Expose fixture readiness only as composition/profile wiring; no endpoint,
    production config, raw token, or production secret is introduced.
- **Acceptance criteria**:
  1. Production startup with the fixture profile fails before data loading.
  2. Re-running the Auth fixture is idempotent and retains the correct local
     admin authorization semantics.
  3. Fixture code has no foreign-module infrastructure/table dependency and
     ArchUnit remains green.
- **Persistence impact**: writes only Auth-owned local fixture rows through
  normal module behavior; Flyway remains the only schema authority.
- **Commit**: feat(auth): seed guarded local e2e administrator

## TASK-003 — Seed Virtual published and draft baseline content

- **Status**: completed
- **Dependencies**: TASK-002
- **Estimated LOC**: ~260 (110 production, 150 tests)
- **Modules**: api:virtual, api:shared only if an existing minimal user lookup
  contract must be reused
- **Work**:
  - Test first: establish deterministic, idempotent baseline published and
    draft courses with module/lesson data; prove the data is created through
    Virtual-owned application boundaries and resolves its fixture administrator
    through an existing neutral contract where required.
  - Add profile-scoped Virtual fixture initialization after Auth readiness.
  - Preserve standard course creation/publish validations; never call Bunny,
    never generate stream capabilities, and do not add Billing dependencies.
  - Add architecture regression if fixture placement could otherwise reach
    foreign infrastructure.
- **Acceptance criteria**:
  1. Repeated initialization produces one logical published fixture and one
     logical draft fixture, each with stable assertions fields.
  2. Public catalog can read the published fixture but does not disclose the
     draft through its normal contract.
  3. No foreign JPA, repository, SQL, or schema access is introduced.
- **Persistence impact**: writes only Virtual-owned data through normal
  adapters; no migration.
- **Commit**: feat(virtual): seed local catalog e2e fixtures

## TASK-004 — Build the real Bruno catalog/content journey

- **Status**: completed
- **Dependencies**: TASK-001, TASK-002, TASK-003
- **Estimated LOC**: ~240 (Bruno requests/assertions and runner token handoff)
- **Modules**: bruno/, scripts/e2e/
- **Work**:
  - Test first: define the ordered request fixtures and an intentional
    assertion-failure case showing the runner propagates Bruno's non-zero exit.
  - Add bruno/E2E/catalog-content requests for health; unique student
    registration; real activation; student login; admin login; admin course,
    module and lesson creation; publication; public list/detail; and draft
    non-disclosure.
  - Retrieve the Mailpit activation delivery at runtime in the runner, pass it
    as a process/runtime Bruno variable, and redact it from shell diagnostics.
  - Store only opaque IDs and access tokens as Bruno runtime variables; no raw
    activation/refresh/access value appears in a versioned environment file.
- **Acceptance criteria**:
  1. Every request asserts its status and the relevant contract fields.
  2. The sequence proves real activation and authenticated administration,
     rather than direct DB setup or auto-activation.
  3. The published journey course appears publicly and the draft fixture stays
     non-enumerable.
  4. No request reaches Mercado Pago, Bunny.net, /stream, or production URLs.
- **Persistence impact**: only normal API-created E2E data in the dedicated
  local schema.
- **Commit**: test(e2e): cover catalog content journey with bruno

## TASK-005 — Document the canonical local E2E workflow

- **Status**: completed
- **Dependencies**: TASK-004
- **Estimated LOC**: ~110 (documentation only)
- **Modules**: docs/, bruno/
- **Work**:
  - Update docs/26-LOCAL-DEV-SETUP-HOWTO.md and bruno/README.md with the exact
    single command, prerequisites, expected lifecycle, dedicated-state cleanup,
    runtime-secret handling, and safe troubleshooting.
  - Remove/qualify deprecated root Compose and placeholder wording only where
    it contradicts the catalog/content E2E workflow.
  - Cross-check all paths/commands against the delivered runner and Bruno
    folder; keep generic local development guidance distinct from this E2E
    journey.
- **Acceptance criteria**:
  1. A clean checkout can follow only versioned documentation to execute #127.
  2. Docs clearly state that ordinary developer volumes are never reset and
     --clean targets only the E2E project.
  3. Docs never instruct users to persist activation or authentication tokens.
- **Persistence impact**: none.
- **Commit**: docs(e2e): document local catalog content workflow

## TASK-006 — Perform the end-to-end verification sweep

- **Status**: pending
- **Dependencies**: TASK-001, TASK-002, TASK-003, TASK-004, TASK-005
- **Estimated LOC**: ~70 (test hardening/verification records)
- **Modules**: api:auth, api:virtual, api:app, scripts/, bruno/, docs/
- **Work**:
  - Add/extend regressions for profile conflict, fixture idempotency, module
    boundaries, token non-persistence, runner non-zero propagation, and draft
    non-disclosure.
  - Run the complete runner twice from dedicated state: first after reset,
    then without reset to prove idempotency.
  - Run focused module tests, ShellCheck, Compose validation, Bruno CLI,
    ./gradlew check --no-daemon, and git diff --check.
  - Record any reproducible environmental failure separately from application
    failures; do not weaken tests, timeouts, or assertions to hide it.
- **Acceptance criteria**:
  1. The command satisfies all #127 criteria twice in succession.
  2. ./gradlew check --no-daemon is green or any environmental exception is
     documented with exact evidence.
  3. The repository has no secret/token artifact and no stale documentation
     conflicting with the delivered workflow.
- **Persistence impact**: none.
- **Commit**: test(e2e): verify reproducible catalog content setup

## Coverage Matrix

| Requirement / scenario | Owning task(s) |
|---|---|
| Dedicated isolated lifecycle and non-destructive reset | TASK-001, TASK-006 |
| Profile guard and deterministic Auth administrator | TASK-002, TASK-006 |
| Module-owned published/draft fixture baseline | TASK-003, TASK-006 |
| Registration, real activation, login and runtime token handling | TASK-004, TASK-006 |
| Authenticated content administration and publishing | TASK-004 |
| Public catalog visibility and draft non-disclosure | TASK-003, TASK-004, TASK-006 |
| Canonical documentation and Bruno operation | TASK-005, TASK-006 |
| No provider simulation or cross-module persistence | TASK-001–006 |

## Explicit non-goals verified during apply

- No Mercado Pago simulator, webhook fake, provider credential, or checkout
  work: #128 owns it.
- No Bunny.net adapter, stream resource, CDN secret, or playback work: #129
  owns it.
- No root/default Compose volume deletion, Flyway seed migration, test-only
  auto-activation, cross-module SQL/JPA/repository access, or production
  fixture profile is introduced.

## Work-unit Commit Map

| Commit | Task | PR slice |
|---|---|---|
| 1 | TASK-001 | PR 1 |
| 2 | TASK-002 | PR 1 |
| 3 | TASK-003 | PR 1 |
| 4 | TASK-004 | PR 2 (base: PR 1 branch) |
| 5 | TASK-005 | PR 2 |
| 6 | TASK-006 | PR 2 |

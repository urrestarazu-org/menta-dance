# Design: local-catalog-content-e2e

## Context

Issue #127 needs a true local E2E baseline, not another collection of manually
prepared requests. The current repository has valid infrastructure Compose,
Bruno API requests, real auth/catalog endpoints, and an application launcher,
but they are not composed into a deterministic journey. The root Compose file
and parts of the local setup documentation are deprecated or contradictory;
the canonical infrastructure definition is
`infra/docker/database/docker-compose.yml`.

The design must preserve the modular-monolith boundary. Fixtures cannot reach
across JPA repositories merely because they are local, and they cannot weaken
registration/activation or public catalog behavior.

## Decisions

### 1. One shell runner owns the lifecycle

Add `scripts/e2e/catalog-content.sh` as the single supported command. It uses:

```text
Compose project: menta-e2e-catalog-content
Compose file:    infra/docker/database/docker-compose.yml
Spring profile:  e2e-catalog-content
Bruno folder:    bruno/E2E/catalog-content
```

The runner validates Docker Compose v2, JDK 21, Node `20.11.1`, `curl`, `jq`,
and the Gradle wrapper before it mutates E2E state. It invokes Compose with
both `--project-name menta-e2e-catalog-content` and the explicit compose file.
Its reset is therefore scoped to those named containers/volumes; it never
executes a root/default-project `down -v`.

The runner starts dependencies, polls their health and the API actuator health,
launches the API with the explicit fixture profile, waits for it, runs Bruno,
and reports a phase-specific error. A shell `trap` stops only the API process
created by this invocation. Infrastructure remains running by default for
debugging; `--clean` tears down only the dedicated E2E Compose project.

**Why:** E2E reproducibility requires a single owner for state and process
lifecycle. Compose project isolation protects a developer's existing local
data, while leaving successful infrastructure available makes failures
diagnosable without violating cleanup safety.

### 2. Fixture loading is profile-scoped and module-owned

Introduce a small fixture SPI in `api:shared` only if `api:app` needs to order
module fixture readiness. Otherwise, profile-scoped infrastructure
configurations in Auth and Virtual register their own fixture initializers.
The concrete loader MUST be activated only by `e2e-catalog-content`; a startup
guard rejects that profile whenever `prod` or `production` is also active.

Auth owns creation/lookup of deterministic local roles and an administration
identity. Virtual owns deterministic baseline content: at least one published
course and one draft course, each with known title/metadata, module and lesson.
Each initializer calls the owning module's application boundary or existing
domain factory plus its own persistence adapter; it may not use a foreign
module's persistence type or table. If a necessary narrow fixture operation is
not represented by an application port, add that port to the owning module
instead of allowing `api:app` to reach into module repositories.

The loaders upsert by stable fixture keys (for example, fixture email and
course slug/title marker), rather than generated IDs, so re-execution is
idempotent. IDs returned by real API responses remain runtime data for Bruno.

**Why:** Local fixtures are still application data. Owning them per module
keeps the test harness from becoming an architectural backdoor.

### 3. Bruno owns user-visible transitions; fixtures only establish baseline

The fixture profile seeds an administrator required for content management and
baseline published/draft data. It does *not* seed the journey student as active.
The Bruno journey registers `catalog.e2e.student@menta.local`, activates it
through the real endpoint, and logs in normally. The seeded administrator logs
in through the same HTTP contract before it creates the journey course/module/
lesson and publishes it.

The runner queries Mailpit's local API only after registration to obtain the
new activation delivery, extracts the raw value in memory, and supplies it to
Bruno through an environment/process variable. It neither writes the token to
a versioned Bruno file nor prints the Mailpit payload. Bruno request scripts
persist only runtime variables for request chaining (access token, created
course/module/lesson IDs).

**Why:** This proves activation and authorization as users experience them,
without reducing Auth security or checking secrets into the repository.

### 4. The E2E journey has a baseline and a created-content path

`bruno/E2E/catalog-content` is ordered numerically and asserts the following:

1. infrastructure/API health;
2. new-student registration;
3. activation with the runner-provided runtime value;
4. student login;
5. admin login from the profile-only deterministic identity;
6. admin create course, module, and lesson;
7. admin publish the created course;
8. anonymous public catalog list/detail includes the created published course;
9. anonymous list/detail does not disclose the known draft fixture.

The collection asserts HTTP status, response shape, and returned opaque IDs at
every state transition. It makes no `/stream` request and never initializes
Billing, Mercado Pago, or Bunny.net.

**Why:** The created path proves administration and publication; baseline draft
data proves public non-enumeration. Together they cover #127 without coupling
to later provider simulators.

### 5. Documentation names one canonical workflow

Update `docs/26-LOCAL-DEV-SETUP-HOWTO.md` and `bruno/README.md` to reference
the runner, its prerequisites, its isolated reset semantics, normal retained
infrastructure, `--clean`, and safe diagnostics. Existing generic local-start
documentation can remain, but it MUST not be advertised as the reproducible
catalog/content E2E command. Root deprecated Compose examples are removed from
this journey's instructions.

## Component Boundaries

```text
scripts/e2e/catalog-content.sh
  ├── docker compose --project-name menta-e2e-catalog-content
  ├── ./gradlew :api:app:bootRun (e2e-catalog-content profile)
  ├── Mailpit local API (runtime activation delivery retrieval only)
  └── npx --yes @usebruno/cli (versioned HTTP assertions)

api:app (composition only)
  └── optional profile guard / fixture readiness ordering

api:auth infrastructure (profile-only)
  └── deterministic local admin fixture through Auth-owned application ports

api:virtual infrastructure (profile-only)
  └── published/draft baseline fixtures through Virtual-owned application ports
```

No component may access a foreign module's repository, JPA entity, SQL, or
table. No fixture is a Flyway migration. Flyway continues to create/validate
schema before any profile initializer runs.

## Failure and Security Semantics

| Condition | Result |
|---|---|
| Missing prerequisite, invalid Node version, missing `.env` | fail before resetting/starting state |
| Compose/API health timeout | non-zero; show safe container/API log command |
| `prod` + fixture profile | fail closed before loading data |
| Re-run fixture initializer | no duplicate logical users/content |
| Mailpit delivery missing/ambiguous | non-zero; no raw payload output |
| Bruno assertion failure | non-zero; retain scoped state for diagnosis |
| `--clean` | remove only `menta-e2e-catalog-content` project resources |

The static local admin password is a non-production fixture credential and is
documented only for the local E2E profile. Activation/access/refresh tokens
remain runtime-only and are redacted from script diagnostics.

## Test Strategy

1. **Red:** shell-level tests or testable helper coverage for argument parsing,
   scoped Compose arguments, health timeout, and non-zero propagation.
2. **Red:** Auth/Virtual fixture tests prove idempotency, stable data and
   profile guard; add architecture regression that prevents fixture code from
   importing foreign infrastructure.
3. **Red:** Bruno requests assert every ordered HTTP transition and expected
   draft non-disclosure.
4. **Green:** focused module tests, ShellCheck, Compose config, then execute
   the real runner twice from the dedicated state.
5. **Regression:** `./gradlew check --no-daemon`, OpenAPI validation when an
   endpoint contract changes (none is planned), and `git diff --check`.

## Rejected Alternatives

### Root `docker compose up -d` plus manual setup

Rejected: the root file is deprecated, manual state is non-reproducible, and
resetting its default volumes risks developer data.

### SQL seed migration or cross-module JPA script

Rejected: migrations run in every profile and cannot be safely local-only;
cross-module persistence breaks ownership and ArchUnit intent.

### Test-only auto-activation or static activation token

Rejected: it bypasses the security contract the issue explicitly needs to
exercise. Mailpit runtime retrieval retains the real delivery/activation path.

### Include Mercado Pago or Bunny adapters

Rejected: #128 and #129 own those dependencies. #127 must stay a catalog and
content baseline without external provider behavior.

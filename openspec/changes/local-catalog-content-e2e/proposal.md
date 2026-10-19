# Proposal: local-catalog-content-e2e

> Close issue #127 by providing one repeatable, local-only command that resets
> the catalog/content test state, starts the required dependencies, loads known
> fixtures, and executes a versioned Bruno journey against the real API.

**Change ID**: `local-catalog-content-e2e`
**Milestone**: v0.2.0 — Catálogo y gestión de contenido local
**Feature track**: issue #127
**Related ADRs**: ADR-0013 (observability), ADR-0021 (Clean Architecture),
ADR-0037 (catalog course routing)
**Related user stories**: US-AUTH activation/login, US-VIRTUAL-001/002/003/004,
and the public catalog contract
**Dependencies**: catalog composition and Virtual administration are merged;
this change must not depend on Mercado Pago (#128) or Bunny.net (#129).

---

## 1. Intent

The repository has Docker infrastructure, a local Bruno environment, and
individual API requests, but no reproducible way to start from a clean checkout
and prove the real authentication → content administration → publication →
public-catalog journey. Documentation currently describes several competing
commands (`docker compose`, `scripts/start-infra.sh`, `scripts/dev.sh`, and
`infra/docker/manage.sh`), while the root compose file is explicitly deprecated.

This change establishes a single, versioned E2E entry point for the catalog and
content slice. It creates only local deterministic fixtures and exercises the
existing HTTP contracts. It does not add test-only shortcuts to domain rules or
make external-provider calls.

## 2. Scope

### In scope

- A documented local E2E command that:
  1. validates JDK 21, Docker Compose, the Gradle wrapper, and Node 20.11.1;
  2. starts the canonical MySQL/Redis/Mailpit/observability Compose stack;
  3. clears only the dedicated E2E local state and waits for health;
  4. starts the API with an explicit local-fixture profile;
  5. loads idempotent, known users/roles and a Virtual course/module/lesson
     fixture through production application boundaries; and
  6. invokes the versioned Bruno collection sequentially, failing on any
     assertion.
- A profile-scoped fixture mechanism that is impossible to activate in a
  production profile and contains only deterministic, non-production data.
- Versioned Bruno requests and a dedicated `bruno/E2E/catalog-content` runner
  journey covering registration/activation/login, content administration,
  publication, and public catalog list/detail reads.
- Contract assertions for status codes, RFC 9457 errors where applicable, and
  fixture identifiers/data that prove the public catalog sees the published
  content and hides drafts.
- Documentation that replaces conflicting local-E2E instructions with the one
  supported command and explains cleanup, prerequisites, and failure recovery.
- Script/Compose/Bruno validation in the existing repository checks; no
  credentials or tokens are versioned.

### Out of scope

- Mercado Pago checkout/webhook simulation (#128), Bunny.net stream simulation
  (#129), real provider credentials, or provider-network calls.
- Any change to catalog, publishing, authorization, pricing, or media business
  rules merely to simplify E2E setup.
- Browser/Playwright E2E, BFF UI testing, Android testing, load testing, or
  production deployment changes.
- Sharing JPA repositories or SQL across modules to insert fixtures.

## 3. Architectural Approach

1. **One explicit local fixture profile.** A profile such as
   `e2e-catalog-content` is opt-in and guarded against `prod`/`production`.
   It is not the default application profile, is not present in container
   production configuration, and uses only deterministic local credentials.
2. **Fixtures remain module-owned.** Each module exposes/uses its existing
   application ports to create its own fixture data. A small `api:app`
   profile-only orchestrator may sequence those ports, but contains no catalog
   or authorization business rule and never accesses module JPA entities or
   repositories. Existing Flyway migrations remain the sole schema authority.
3. **HTTP remains the acceptance boundary.** The fixture loader only makes the
   starting state reproducible. Bruno performs the actual user journey through
   API endpoints, with runtime-only access/activation values. The collection
   never calls databases, reads logs for secrets, or bypasses auth.
4. **Dedicated disposable local state.** The command must target an E2E-named
   Compose project/volumes or otherwise positively identify the fixtures it is
   allowed to remove. It must never run a broad `docker compose down -v` against
   a developer's ordinary local stack. Reset is deterministic and idempotent.
5. **Single source of operation.** The new runner owns the supported sequence
   and invokes the canonical `infra/docker/database/docker-compose.yml`, not
   the deprecated root compose file or legacy `docker-compose` binary. It must
   wait for service and API health rather than use fixed sleeps.
6. **No external coupling.** The course fixture may have a stable opaque media
   identifier but the catalog journey does not request `/stream`; no Bunny
   adapter is invoked. Billing is not required because the fixture focuses on
   catalog/content administration and public reads.

## 4. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | From a clean checkout, the documented E2E command starts the required local dependencies, resets only its dedicated local state, loads known fixtures, and reports a non-zero exit on any failed setup or Bruno assertion. |
| AC-2 | Fixtures are idempotent and contain known local-only users/roles plus draft and published Virtual catalog data. They do not require production secrets, provider credentials, or network access to Mercado Pago/Bunny.net. |
| AC-3 | The Bruno sequence proves account registration/activation/login, authorized course/module/lesson administration, publication, public catalog list, public catalog detail, and draft non-disclosure through HTTP. |
| AC-4 | Every request asserts relevant response status and contract data. Runtime activation/access values remain runtime variables and are never committed to `.bru`, environment, scripts, logs, or docs. |
| AC-5 | Production cannot accidentally start with E2E fixtures; profile/configuration tests demonstrate the guard. Fixtures preserve module ownership and Flyway remains the only schema writer. |
| AC-6 | `docs/26-LOCAL-DEV-SETUP-HOWTO.md` and `bruno/README.md` name one canonical command, prerequisites, cleanup behavior, and troubleshooting. They no longer recommend deprecated root Compose or unactionable placeholder flows for this journey. |
| AC-7 | ShellCheck, Compose validation, focused fixture/application tests, Bruno CLI, and repository `./gradlew check` pass (environmental failures are recorded separately with reproducible evidence). |

## 5. Risks and Decisions to Validate in Design

- **Fixture API surface:** establish whether existing module application ports
  can construct the required admin/content state. If one narrow port is absent,
  add it in the owning module rather than use JPA from `api:app`.
- **Auth activation:** use the real activation contract, with a local-only
  deterministic delivery mechanism, rather than reducing activation security
  for the runner. The design must state how the Bruno runner acquires the
  runtime token without persisting it.
- **Process lifecycle:** the runner must specify whether it leaves healthy
  infrastructure/API running for debugging or tears it down on success/failure;
  cleanup must be opt-in for developer-owned containers.
- **Volume isolation:** name and validate the Compose project so a reset cannot
  erase non-E2E local MySQL/Redis data.

## 6. Verification

- Test the fixture/profile guard before implementation and prove repeated seed
  execution yields the same logical users and content.
- Test the orchestration only through module ports and maintain existing
  ArchUnit boundaries.
- Run the runner from a clean dedicated E2E state, then run it again to prove
  idempotency and no external-provider calls.
- Execute `npx --yes @usebruno/cli run "bruno/E2E/catalog-content" --env local`
  as part of the runner and verify an intentional assertion failure propagates
  a non-zero exit.
- Run `./gradlew check --no-daemon`, Compose config validation, ShellCheck for
  the new/changed scripts, and `git diff --check`.

## 7. Next SDD Phase

Create the normative specification for the runner lifecycle, profile guard,
fixture identity/idempotency, and ordered Bruno scenarios before selecting the
concrete fixture ports, scripts, and tests.

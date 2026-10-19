# Local Catalog and Content E2E Specification

## Purpose

Provide a deterministic, local-only acceptance journey for issue #127. The
journey proves the real HTTP path from account registration and activation,
through authenticated Virtual content administration and publication, to public
catalog list/detail reads. It must be reproducible without production secrets,
external payment/CDN calls, or test-only changes to business behavior.

## Requirements

### Requirement: One safe, repeatable E2E entry point

The repository MUST provide one documented command for the catalog/content E2E
journey. It MUST validate its prerequisites, create or select a dedicated E2E
Compose project/state, reset only that state, start the canonical infrastructure
Compose file, wait for health, start the API with the explicit fixture profile,
load fixtures, and run the Bruno journey in order.

The command MUST return a non-zero exit code when a prerequisite, startup,
fixture, health, or Bruno assertion fails. It MUST NOT use the deprecated root
`docker-compose.yml`, the legacy `docker-compose` binary, a fixed-duration
sleep as a health check, or a destructive command that can remove ordinary
developer volumes.

#### Scenario: Clean dedicated state produces a passing journey

- GIVEN no containers or volumes exist for the dedicated E2E Compose project
- WHEN the documented E2E command runs from a clean checkout with valid local
  prerequisites
- THEN it starts MySQL, Redis, Mailpit, and required observability dependencies
- AND waits until each required service and the API report healthy
- AND loads the known fixtures
- AND runs the catalog/content Bruno folder sequentially
- AND exits zero only when every assertion passes

#### Scenario: A runner failure is observable and fails the command

- GIVEN any required service never becomes healthy or a Bruno assertion fails
- WHEN the E2E command runs
- THEN it exits non-zero
- AND reports the failed phase and safe diagnostic location
- AND it does not silently continue to subsequent phases

#### Scenario: Reset does not erase a developer's ordinary stack

- GIVEN a developer has a non-E2E local MySQL or Redis Compose volume
- WHEN the E2E command resets its fixture state
- THEN it targets only its dedicated Compose project/volume names
- AND it does not execute a broad `down -v` against the default local project

### Requirement: Profile-scoped deterministic fixtures

Fixtures MUST be available only through an explicit E2E local profile. That
profile MUST be rejected or inactive whenever `prod` or `production` is active,
MUST NOT be selected by default, and MUST NOT be enabled by application
production Docker configuration.

The fixture state MUST be idempotent: a second load after a successful load
MUST create neither duplicate users/roles nor duplicate logical catalog/content
records. It MUST include stable local-only identities sufficient for content
administration and public catalog assertions, plus at least one published and
one draft course that are distinguishable by their stable fixture data.

Fixtures MUST use owning-module application ports and normal persistence
adapters. `api:app` MAY sequence those ports as profile-only composition but
MUST NOT access foreign JPA entities, repositories, tables, or SQL. Flyway
remains the only schema writer.

#### Scenario: Re-running the fixture loader is idempotent

- GIVEN the dedicated E2E state already contains the known fixture set
- WHEN the loader executes again
- THEN every known identity and logical course/module/lesson remains unique
- AND the published and draft fixture states remain unchanged
- AND no production business rule is bypassed

#### Scenario: Production cannot start with E2E fixtures

- GIVEN `prod` or `production` is active
- WHEN configuration attempts to enable the E2E fixture profile
- THEN startup fails closed before fixtures are loaded
- AND the failure exposes neither secrets nor fixture credentials

### Requirement: Real activation and runtime-secret handling

The Bruno journey MUST register a fresh local student through the normal
registration endpoint and activate it through the normal single-use activation
endpoint. It MUST NOT manufacture an active account by directly writing Auth
tables or weakening token generation, hashing, expiry, or one-time-use rules.

The runner MAY obtain the locally delivered activation value from Mailpit only
at runtime, pass it to Bruno as a process/runtime variable, and discard it when
the process ends. Raw activation tokens, access tokens, refresh tokens,
passwords beyond documented non-secret local fixtures, and Mailpit message
payloads MUST NOT be committed, printed in verbose logs, or stored in a
versioned `.bru` environment.

#### Scenario: Newly registered student completes real activation and login

- GIVEN the fixture API and Mailpit are healthy
- WHEN the Bruno sequence registers its unique E2E student
- THEN the API emits the normal activation delivery to Mailpit
- WHEN the runner supplies that runtime-only token to the activation request
- THEN the student activates successfully
- AND a subsequent normal login returns usable runtime authentication state

### Requirement: Ordered HTTP catalog/content journey

The versioned `bruno/E2E/catalog-content` folder MUST execute requests in an
explicit order and assert each transition. It MUST cover, at minimum:

1. health/readiness;
2. registration, activation, and login for the journey student;
3. authenticated/authorized administration of a Virtual course, module, and
   lesson using the production API contract;
4. publication of the created course;
5. public catalog list and public catalog detail containing the published
   fixture/course; and
6. public non-disclosure of a draft course.

The collection MUST assert relevant status codes and response fields, including
opaque IDs passed between requests as runtime variables. It MUST prove only
published data becomes public and MUST not request a stream URL or invoke a
payment/CDN integration.

#### Scenario: Published content appears in the public catalog

- GIVEN an authorized actor creates the required course/module/lesson state
- WHEN the actor publishes the course through its normal API endpoint
- THEN the public list returns that course
- AND public detail returns its safe published metadata and lesson tree

#### Scenario: Draft content is not publicly enumerable

- GIVEN the fixture set contains a draft Virtual course
- WHEN an anonymous caller queries public catalog list and detail
- THEN the draft is absent from the list
- AND its public detail behaves as the existing non-enumerating contract
  specifies

### Requirement: Documentation and verification stay aligned

The local-development and Bruno documentation MUST name the single supported
E2E command, prerequisites, dedicated-state cleanup behavior, runtime-secret
handling, and safe troubleshooting steps. It MUST not recommend deprecated
root Compose or describe placeholders as the catalog/content E2E path.

The implementation MUST add automated tests for profile guard, fixture
idempotency, and orchestration boundaries; it MUST preserve ArchUnit module
rules. The runner MUST use `npx --yes @usebruno/cli` or an equivalently pinned,
documented CLI invocation that works from a clean checkout.

## Out of Scope

Mercado Pago or Bunny.net simulation, real provider calls or credentials,
browser/UI automation, Android/BFF journeys, production seeding, schema writes
outside Flyway, cross-module repository access, and any change to business rules
for authorization, publication, catalog composition, or account activation.

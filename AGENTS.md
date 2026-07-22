# Repo snapshot (read first)
- Repo is **documentation-only today**. Before writing code, review `docs/02-ARCHITECTURE.md`, `docs/27-CLEAN-ARCHITECTURE-GUIDE.md`, `docs/24-LOCAL-DEV-SETUP.md`, and relevant ADRs; the scaffold you create must satisfy those contracts.
- Target stack: Java 21 + Spring Boot 3 modular monolith (`api`) composed by `api:app`, Kotlin + Jetpack Compose Android client, Node 20.11.1 for web assets, Docker Compose for infra helpers.

# Module boundaries & ownership
- API is a single JAR built from modules `auth`, `billing`, `virtual`, `physical`, plus `api:shared` for slim cross-module contracts. `api:app` wires everything; it may host orchestration but **no domain logic**.
- The web interface is a separate BFF JAR that talks to the API over HTTP; browser clients never talk to the API directly or store tokens.
- Each backend module must follow `domain -> application -> infrastructure` (no reverse deps). `domain` stays framework-free, `application` exposes ports/use cases, `infrastructure` owns adapters, controllers, persistence, config.
- Cross-module collaboration always goes through Java interfaces (typically defined in `api:shared`). Prohibited: sharing JPA entities, repositories, SQL joins, HTTP calls, messaging, or any infrastructure from another module.
- Android mirrors the same direction (`presentation -> domain <- data`), with Hilt wiring isolated in `di`.

# Data & migrations
- Single MySQL schema `menta`; tables are prefixed per module (`auth_`, `billing_`, etc.). Foreign keys and queries must never cross module prefixes.
- Flyway in `api:app` is the only component allowed to change the schema. Hibernate stays on `ddl-auto:validate`; failed migrations are fixed via new migrations, not rollbacks.
- Redis is required with `maxmemory-policy noeviction` for locks/blacklists; Caffeine caches only rebuildable data.

# Local environment
- Required tooling: JDK 21, Node 20.11.1, Docker Compose, Gradle wrapper (generate in Fase 0), Bruno HTTP client (`bruno/` collection is versioned). Do **not** rely on globally installed Gradle.
- `docker compose up -d` must start MySQL 8 (`menta` DB, non-root app user), Redis, and the observability stack (OTel/Loki). Credentials come from a non-versioned `.env`.
- Once scaffolded, the baseline commands expected to exist are:
  ```bash
  docker compose up -d
  ./gradlew check      # runs style + tests + ArchUnit + quality gates
  ./gradlew :api:app:bootRun
  ./gradlew :bff:bootRun
  ```

# Quality gates & testing
- Each module must ship ArchUnit tests enforcing: no Spring/JPA in `domain`/`application`, no access to foreign infrastructure or repositories, controllers delegating through ports only.
- CI (GitHub Actions) is expected to run: backend build, Checkstyle, unit + ArchUnit tests, JaCoCo, SonarCloud, Playwright, Gatling, and Trivy before merge. Wire these into `./gradlew check`/workflows as code lands.
- Observability (Logback JSON + OpenTelemetry) is part of the definition of done—ensure new services emit `correlationId` and respect 90-day retention rules outlined in docs.

# Workflow & delivery
- Branching follows Git Flow: `feature/*` → `develop`; releases originate from `release/*`, merged into `master` then back to `develop`; hotfixes start from `master`.
- Use Conventional Commits for both commit messages and PR titles. Releases are tagged `vMAJOR.MINOR.PATCH` and publish JAR + GHCR images using immutable digests.
- PRs merge via squash once CI is green; never add AI attributions in commit trailers. Deployment workflows must pin every GitHub Action to a full SHA (no `latest`).

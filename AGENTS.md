# Repository Guidelines

## Project Structure & Module Organization

- This repository is currently documentation-first. `README.md` states the product goal; `docs/` contains architecture, ADRs, testing guidance, user stories, diagrams, and the development plan.
- The planned product uses Java 21 and Spring Boot 3 for REST APIs, plus Kotlin and Jetpack Compose for Android.
- Follow the documented Clean Architecture boundaries when source modules are added: `domain/` for business rules, `application/` for use cases, and `infrastructure/` for frameworks and adapters.
- Keep tests beside their owning module under the conventional `src/test/` tree. Store non-code assets in the relevant module rather than a shared catch-all directory.

## Build, Test, and Development Commands

The build scaffold is not committed yet, so these documented Gradle and Docker commands are targets, not currently runnable:

```bash
./gradlew test                         # Run all tests
./gradlew test --tests Class.method    # Run one test
./gradlew checkstyleMain               # Check Java style
./gradlew jacocoTestCoverageVerification # Enforce coverage
docker-compose up -d                   # Start local dependencies
```

When build files land, prefer the committed Gradle wrapper; do not rely on a globally installed Gradle version.

## Coding Style & Naming Conventions

- Use four-space indentation for Java and Kotlin. Let committed formatter and Checkstyle configuration remain authoritative.
- Name types with `PascalCase`, methods and fields with `camelCase`, and constants with `UPPER_SNAKE_CASE`.
- Keep dependency flow inward: infrastructure may depend on application/domain contracts, never the reverse.
- Name ADRs as `docs/adr/NNNN-short-decision.md` and user stories as `docs/user-stories/US-AREA-NNN.md`.

## Testing Guidelines

- The planned stack is JUnit 5, Mockito, H2, JaCoCo, and ArchUnit.
- Structure tests as Given–When–Then and use names such as `whenCondition_thenExpected` with clear `@DisplayName` text.
- Target at least 80% line and 70% branch coverage; critical business and security rules require complete coverage.

## Commit & Pull Request Guidelines

- Git history is minimal and does not yet establish a message pattern. Use Conventional Commits, for example `feat(auth): add token validation` or `docs: clarify local setup`.
- Never add AI attribution or `Co-Authored-By` trailers.
- PRs must explain scope and verification, link relevant issues or user stories, and include screenshots for UI changes. Require passing checks and one approval before merging to `main`.

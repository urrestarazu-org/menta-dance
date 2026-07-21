# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Menta Dance is a dance academy management system with two business lines:
- **Virtual**: Online courses (modules, lessons, videos)
- **Physical**: In-person classes (schedules, attendance, check-in)

## Architecture

**Monorepo + Modular Monolith** with Gradle multi-module.

```
menta-dance/
├── api/                     # Backend API (Spring Boot 3)
│   ├── shared/              # :api:shared — common code
│   ├── auth/                # :api:auth — authentication
│   ├── virtual/             # :api:virtual — online courses
│   ├── physical/            # :api:physical — in-person classes
│   ├── billing/             # :api:billing — payments
│   └── app/                 # :api:app — assembles everything
├── bff/                     # Web frontend (Thymeleaf)
└── android/                 # Mobile app (Kotlin)
```

## Tech Stack

- **API/BFF**: Java 21, Spring Boot 3, Gradle (Kotlin DSL)
- **Android**: Kotlin, Jetpack Compose, Hilt
- **Database**: MySQL 8.0
- **Testing**: JUnit 5, Mockito, Testcontainers, ArchUnit

## Clean Architecture (Mandatory)

Every module follows Clean Architecture with three layers:

```
module/
└── src/main/java/com/menta/{module}/
    ├── domain/           # Entities (POJOs), Value Objects, Domain Services
    ├── application/      # Use Cases, Ports, DTOs
    └── infrastructure/   # Controllers, JPA, External services
```

**Dependency Rule**: `domain ← application ← infrastructure`

- `domain` has NO external dependencies (no Spring, no JPA)
- `application` depends only on `domain`
- `infrastructure` depends on `application` and `domain`

**Enforced with ArchUnit** — tests fail if violated.

## Build Commands

```bash
# Full build
./gradlew build

# Run API
./gradlew :api:app:bootRun

# Run BFF
./gradlew :bff:bootRun

# Tests
./gradlew test                                    # All tests
./gradlew :api:auth:test                          # Module tests
./gradlew test --tests "*.UserServiceTest"        # Single class
./gradlew test --tests "*ArchitectureTest"        # Architecture tests

# Coverage
./gradlew jacocoTestReport
./gradlew jacocoTestCoverageVerification
```

## Test Strategy (100/80/0)

- **100% coverage**: `billing.domain`, `billing.application`, `auth.domain`, `auth.application`
- **80% coverage**: `virtual.*`, `physical.*`
- **0% unit tests**: DTOs, configs, JPA repository interfaces

## Module Communication

Modules communicate via **Java interfaces** (not HTTP):

```java
// In billing module
@RequiredArgsConstructor
public class SubscriptionUseCase {
    private final UserQueryPort userQuery; // Interface from shared
    // ...
}
```

## Key ADRs

- [ADR-0019](docs/adr/0019-monorepo-structure.md): Monorepo
- [ADR-0020](docs/adr/0020-modular-monolith.md): Modular Monolith
- [ADR-0021](docs/adr/0021-clean-architecture-mandatory.md): Clean Architecture

## Ports

| Service | Port |
|---------|------|
| API     | 8081 |
| BFF     | 8080 |
| MySQL   | 3306 |

## Branch Naming

- `feature/{description}`
- `bugfix/{description}`
- `hotfix/{description}`

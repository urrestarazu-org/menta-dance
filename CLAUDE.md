# CLAUDE.md

Este archivo proporciona guía a Claude Code (claude.ai/code) al trabajar con código en este repositorio.

## Visión General del Proyecto

Menta Dance es un sistema de gestión de academia de danza con dos líneas de negocio:
- **Virtual**: Cursos online (módulos, lecciones, videos)
- **Physical**: Clases presenciales (horarios, asistencia, check-in)

## Arquitectura

**Monorepo + Monolito Modular** con Gradle multi-módulo.

```
menta-dance/
├── api/                     # Backend API (Spring Boot 3)
│   ├── shared/              # :api:shared — código común
│   ├── auth/                # :api:auth — autenticación
│   ├── virtual/             # :api:virtual — cursos online
│   ├── physical/            # :api:physical — clases presenciales
│   ├── billing/             # :api:billing — pagos
│   └── app/                 # :api:app — ensambla todo
├── bff/                     # Frontend web (Thymeleaf)
└── android/                 # App móvil (Kotlin)
```

## Stack Tecnológico

- **API/BFF**: Java 21, Spring Boot 3, Gradle (Kotlin DSL)
- **Android**: Kotlin, Jetpack Compose, Hilt
- **Base de datos**: MySQL 8.0
- **Testing**: JUnit 5, Mockito, Testcontainers, ArchUnit

## Clean Architecture (Obligatoria)

Cada módulo sigue Clean Architecture con tres capas:

```
module/
└── src/main/java/com/menta/{module}/
    ├── domain/           # Entidades (POJOs), Value Objects, Servicios de Dominio
    ├── application/      # Casos de Uso, Puertos, DTOs
    └── infrastructure/   # Controllers, JPA, Servicios externos
```

**Regla de Dependencia**: `domain ← application ← infrastructure`

- `domain` NO tiene dependencias externas (sin Spring, sin JPA)
- `application` depende solo de `domain`
- `infrastructure` depende de `application` y `domain`

**Validado con ArchUnit** — los tests fallan si se viola.

## Comandos de Build

```bash
# Build completo
./gradlew build

# Ejecutar API
./gradlew :api:app:bootRun

# Ejecutar BFF
./gradlew :bff:bootRun

# Tests
./gradlew test                                    # Todos los tests
./gradlew :api:auth:test                          # Tests de módulo
./gradlew test --tests "*.UserServiceTest"        # Clase específica
./gradlew test --tests "*ArchitectureTest"        # Tests de arquitectura

# Cobertura
./gradlew jacocoTestReport
./gradlew jacocoTestCoverageVerification
```

## Estrategia de Tests (100/80/0)

- **100% cobertura**: `billing.domain`, `billing.application`, `auth.domain`, `auth.application`
- **80% cobertura**: `virtual.*`, `physical.*`
- **0% tests unitarios**: DTOs, configs, interfaces de repositorio JPA

## Comunicación entre Módulos

Los módulos se comunican via **interfaces Java** (no HTTP):

```java
// En módulo billing
@RequiredArgsConstructor
public class SubscriptionUseCase {
    private final UserQueryPort userQuery; // Interface de shared
    // ...
}
```

## ADRs Clave

- [ADR-0019](docs/adr/0019-monorepo-structure.md): Monorepo
- [ADR-0020](docs/adr/0020-modular-monolith.md): Monolito Modular
- [ADR-0021](docs/adr/0021-clean-architecture-mandatory.md): Clean Architecture

## Puertos

| Servicio | Puerto |
|----------|--------|
| API      | 8081   |
| BFF      | 8080   |
| MySQL    | 3306   |

## Git Flow

| Rama | Propósito |
|------|-----------|
| `main` | Producción (protegida, requiere PR) |
| `develop` | Integración (rama default) |
| `feature/*` | Nuevas funcionalidades |
| `release/*` | Preparación de release |
| `hotfix/*` | Fixes urgentes |

**Flujo**: `feature/*` → `develop` → `release/*` → `main` → tag `vX.Y.Z`

## Selección de Modelo

Antes de iniciar una tarea, considerar el modelo apropiado:

| Modelo | Mejor para | Costo |
|--------|------------|-------|
| **Fable 5** | Investigación, tareas de varios días, más capaz | $$$ |
| **Opus 4.8** | Proyectos complejos, agentes, programación | $$ |
| **Sonnet 5** | Tareas diarias, escritura, balanceado | $ |
| **Haiku 4.5** | Respuestas rápidas, alto volumen | ¢ |

### Referencia Rápida

- **Scaffold/refactor multi-archivo** → Opus 4.8
- **Diseño de arquitectura** → Fable 5
- **Edición de archivo único** → Sonnet 5
- **Documentación** → Sonnet 5
- **Verificación rápida** → Haiku 4.5

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
└── android/                 # App móvil (Kotlin + Compose + Hilt)
```

## Stack Tecnológico

- **API/BFF**: Java 21, Spring Boot 3, Gradle (Kotlin DSL)
- **Android**: Kotlin, Jetpack Compose, Hilt, Clean Architecture (`presentation/domain/data/di`)
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

## Convenciones de Código

### Idioma

**Regla**: Código en **inglés**, UI/documentación en **español** (idioma del negocio).

- ✅ **Código** (clases, métodos, variables, enums, constantes): **Inglés**
  ```java
  public enum Role { ADMIN, INSTRUCTOR, STUDENT }
  public class UserService { ... }
  private String firstName;
  ```

- ✅ **UI/Mensajes al usuario**: **Español**
  ```java
  throw new ValidationException("El email ya está registrado");
  return ResponseEntity.badRequest().body("Contraseña inválida");
  ```

- ✅ **Documentación pública** (READMEs, guías de usuario): **Español**
- ✅ **Comentarios técnicos/JavaDoc**: **Inglés** (opcional español para lógica de negocio compleja)

**Justificación**: Esta es una best practice universal que facilita colaboración internacional, reutilización de código, y onboarding de nuevos desarrolladores.

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

## Estrategia de Tests

Cada módulo gatea cobertura **por capa** (JaCoCo, agregación BUNDLE), no
con un número plano por módulo. El umbral es un trinquete calibrado justo
debajo de la cobertura real vigente — nunca una meta aspiracional muy por
encima, ni un piso muy por debajo que deje de proteger nada.

| Módulo | domain + application | infrastructure |
|---|---|---|
| `auth` | 100% | 85% |
| `billing` | 100% | 85% |
| `virtual` | 95% | 90% |
| `physical` | 95% | 90% |

`shared`, `app` y `bff` no tienen capas propias; llevan un piso plano
declarado en `moduleCoverageFloor` (root `build.gradle.kts`), todos en
85% o más. Ningún umbral del proyecto está por debajo de 85%.

El mecanismo (`registerLayeredCoverageVerification`) vive en
`buildSrc/` y se documenta en detalle, junto al bug de JaCoCo #96 que
motivó el diseño, en `docs/14-TEST-STRATEGY.md`. `./gradlew
jacocoAggregatedReport` genera el reporte combinado del monorepo
(sin umbral propio — sólo reporting).

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

## Skills del Proyecto

Cuando el usuario pida "crear un PR", "prcreator", o variantes, leer y ejecutar:
- **prcreator**: @skills/prcreator/SKILL.md

Cuando el usuario pida "prsync", "actualizar la descripción del PR", "sincronizar el PR",
o variantes, leer y ejecutar:
- **prsync**: @skills/prsync/SKILL.md

### ⚠️ Lección importante sobre PRs

**SIEMPRE verificar PRs existentes antes de crear uno nuevo:**

```bash
gh pr list --head $(git branch --show-current)
```

**Por qué es crítico:**
- Un mismo branch puede tener múltiples PRs si apuntan a diferentes bases
- Esto causa confusión en code review y duplica recursos de CI
- Si existe PR con base incorrecta → actualizar base o cerrarlo, NO crear duplicado

**Flujo correcto:**
1. Verificar PRs existentes del branch actual
2. Si existe con base correcta → actualizar descripción
3. Si existe con base incorrecta → `gh pr edit --base <correct>` o cerrar y recrear
4. Si no existe → crear nuevo PR

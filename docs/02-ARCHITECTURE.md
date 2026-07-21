# Arquitectura del Proyecto

[← Volver al índice](./README.md)

---

## Visión General

Menta Dance es un **monorepo** con **monolito modular** que gestiona una academia de danzas con dos líneas de negocio:

| Academia | Modalidad | Contenido |
|----------|-----------|-----------|
| **Virtual** | Online | Cursos → Módulos → Lecciones (videos) |
| **Física** | Presencial | Cursos, horarios, control de asistencia |

---

## Arquitectura de Alto Nivel

```
┌───────────────────────────────────────────────────────────────────┐
│                           CLIENTES                                │
│                                                                   │
│   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐          │
│   │  Web Browser │   │  Admin Panel │   │ Android App  │          │
│   │  (Alumnos)   │   │  (Gestión)   │   │  (Check-in)  │          │
│   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘          │
└──────────┼──────────────────┼──────────────────┼──────────────────┘
           │                  │                  │
           └────────┬─────────┘                  │
                    ▼                            │
           ┌─────────────────┐                   │
           │       BFF       │                   │
           │─────────────────│                   │
           │  Spring Boot 3  │                   │
           │  + Thymeleaf    │                   │
           │─────────────────│                   │
           │ • Portal Alumnos│                   │
           │ • Panel Admin   │                   │
           └────────┬────────┘                   │
                    │                            │
                    └──────────────┬─────────────┘
                                   ▼
                    ┌─────────────────────────┐
                    │      API (Monolito)     │
                    │─────────────────────────│
                    │     Spring Boot 3       │
                    │─────────────────────────│
                    │  ┌─────┐ ┌─────────┐    │
                    │  │auth │ │ virtual │    │
                    │  └─────┘ └─────────┘    │
                    │  ┌────────┐ ┌───────┐   │
                    │  │physical│ │billing│   │
                    │  └────────┘ └───────┘   │
                    │       ┌──────┐          │
                    │       │shared│          │
                    │       └──────┘          │
                    └───────────┬─────────────┘
                                │
                                ▼
                    ┌─────────────────────────┐
                    │       MySQL 8.0         │
                    │─────────────────────────│
                    │    Una BD, schemas      │
                    │    lógicos por módulo   │
                    └─────────────────────────┘
```

---

## Componentes del Sistema

| Componente | Tecnología | Responsabilidad | Puerto |
|------------|------------|-----------------|--------|
| **API** | Spring Boot 3, Gradle | REST API monolítica con módulos internos | 8081 |
| **BFF** | Spring Boot 3, Thymeleaf | Portal alumnos + Panel admin, consume API | 8080 |
| **Android** | Kotlin, Jetpack Compose | App móvil para check-in presencial | - |
| **MySQL** | MySQL 8.0 | Base de datos relacional | 3306 |

---

## Estructura del Monorepo

```
menta-dance/
├── api/                              # Backend API REST
│   ├── shared/                       # :api:shared — código común
│   │   └── src/main/java/com/menta/shared/
│   │       ├── domain/               # Excepciones base, value objects
│   │       ├── application/          # Interfaces comunes, ports
│   │       └── infrastructure/       # Security config, utils
│   │
│   ├── auth/                         # :api:auth — autenticación
│   │   └── src/main/java/com/menta/auth/
│   │       ├── domain/               # User, Role, Token
│   │       ├── application/          # UseCases, ports
│   │       └── infrastructure/       # Controllers, JPA, JWT
│   │
│   ├── virtual/                      # :api:virtual — cursos online
│   │   └── src/main/java/com/menta/virtual/
│   │       ├── domain/
│   │       ├── application/
│   │       └── infrastructure/
│   │
│   ├── physical/                     # :api:physical — presenciales
│   │   └── src/main/java/com/menta/physical/
│   │       ├── domain/
│   │       ├── application/
│   │       └── infrastructure/
│   │
│   ├── billing/                      # :api:billing — pagos
│   │   └── src/main/java/com/menta/billing/
│   │       ├── domain/
│   │       ├── application/
│   │       └── infrastructure/
│   │
│   └── app/                          # :api:app — ensambla todo
│       └── src/main/java/com/menta/
│           └── MentaApiApplication.java
│
├── bff/                              # Frontend Web
│   └── src/main/java/com/menta/bff/
│       ├── domain/
│       ├── application/
│       └── infrastructure/
│           ├── controller/           # Thymeleaf controllers
│           ├── client/               # HTTP clients → API
│           └── config/
│
├── android/                          # App Android
│   └── app/src/main/java/com/menta/
│       ├── domain/                   # Entities, UseCases
│       ├── data/                     # Retrofit, Room
│       ├── presentation/             # ViewModels, Compose
│       └── di/                       # Hilt
│
├── build.gradle.kts                  # Root build
├── settings.gradle.kts               # Incluye todos los módulos
└── gradle/
    └── libs.versions.toml            # Version catalog
```

---

## Módulos Gradle

### Dependencias entre módulos

```
:api:app
   ├── :api:auth
   ├── :api:virtual
   ├── :api:physical
   └── :api:billing
          │
          └── todos dependen de → :api:shared
```

### Configuración en `settings.gradle.kts`

```kotlin
rootProject.name = "menta-dance"

// API modules
include(":api:shared")
include(":api:auth")
include(":api:virtual")
include(":api:physical")
include(":api:billing")
include(":api:app")

// BFF
include(":bff")

// Android
include(":android:app")
```

---

## Clean Architecture (Obligatoria)

Cada módulo sigue Clean Architecture con tres capas:

```
┌─────────────────────────────────────────────────────┐
│                  INFRASTRUCTURE                      │
│  Controllers, JPA Entities, Repositories impl,      │
│  External services, Configs                         │
├─────────────────────────────────────────────────────┤
│                   APPLICATION                        │
│  Use Cases, Ports (interfaces), DTOs                │
├─────────────────────────────────────────────────────┤
│                     DOMAIN                           │
│  Entities (POJO), Value Objects, Domain Services,   │
│  Repository interfaces, Business rules              │
└─────────────────────────────────────────────────────┘
```

### Regla de dependencias

```
domain ← application ← infrastructure
```

- `domain` NO depende de nada externo
- `application` depende solo de `domain`
- `infrastructure` depende de `application` y `domain`

### Enforcement con ArchUnit

```java
@ArchTest
static final ArchRule domain_should_not_depend_on_application =
    noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAPackage("..application..");

@ArchTest
static final ArchRule domain_should_not_depend_on_infrastructure =
    noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAPackage("..infrastructure..");

@ArchTest
static final ArchRule application_should_not_depend_on_infrastructure =
    noClasses()
        .that().resideInAPackage("..application..")
        .should().dependOnClassesThat()
        .resideInAPackage("..infrastructure..");
```

Estas reglas se complementan con las fronteras ejecutables de
[25-ARCHITECTURE-RULES.md](./25-ARCHITECTURE-RULES.md): ningún módulo puede
importar infraestructura ajena ni usar transporte de red para colaborar con
otro módulo.

---

## Comunicación entre Módulos

Dado que es un monolito, los módulos se comunican **directamente via interfaces Java**, no HTTP:

```java
// En billing application, inyecta un puerto implementado por auth.
// El wiring de Spring pertenece a infrastructure.
@RequiredArgsConstructor
public class SubscriptionService {
    private final UserQueryPort userQueryPort; // Interface de auth module

    public void createSubscription(Long userId, PlanId planId) {
        User user = userQueryPort.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        // ...
    }
}
```

### Puertos compartidos en `shared`

```java
// api/shared/.../application/port/UserQueryPort.java
public interface UserQueryPort {
    Optional<UserInfo> findById(Long id);
    Optional<UserInfo> findByEmail(String email);
}

// api/auth/.../infrastructure/adapter/UserQueryAdapter.java
@Component
public class UserQueryAdapter implements UserQueryPort {
    private final UserRepository userRepository;
    // implementación...
}
```

---

## Evolución futura

La arquitectura mantiene límites explícitos para permitir cambios futuros sin
convertir escenarios hipotéticos en infraestructura del MVP:

1. **Módulos Gradle aislados**: Cada módulo tiene sus propias dependencias
2. **Comunicación vía interfaces**: Los contratos no dependen de implementaciones
3. **Clean Architecture**: Domain no conoce infraestructura
4. **Schemas lógicos**: El ownership de datos está definido por módulo

Cualquier cambio de topología requiere un ADR nuevo. Hasta entonces, la única
arquitectura permitida es un JAR API con comunicación interna mediante puertos
Java.

---

## Escala Objetivo (MVP)

- **100 RPM** (requests per minute) = ~1.7 RPS
- **200 usuarios** activos estimados
- **1 servidor** para API + BFF + MySQL

---

[Siguiente: API de Autenticación →](./03-AUTH-API.md)

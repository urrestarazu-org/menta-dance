# Guía de Clean Architecture

[← Volver al índice](./README.md)

---

## Regla Principal

**Clean Architecture es OBLIGATORIA** en todos los módulos del proyecto.

Ver [ADR-0021](./adr/0021-clean-architecture-mandatory.md) para la justificación completa.

---

## Estructura de Cada Módulo

```
module/
└── src/main/java/com/menta/{module}/
    ├── domain/           # Capa interna (sin dependencias)
    ├── application/      # Capa de aplicación (casos de uso)
    └── infrastructure/   # Capa externa (frameworks)
```

---

## Capas en Detalle

### 1. Domain (Núcleo)

La capa más interna. **NO depende de nada externo**.

```
domain/
├── model/                # Entidades de dominio (POJOs puros)
│   ├── User.java
│   ├── UserId.java       # Value Object
│   └── Email.java        # Value Object
├── repository/           # Interfaces de repositorio (puertos)
│   └── UserRepository.java
├── service/              # Servicios de dominio (lógica pura)
│   └── PasswordPolicy.java
└── exception/            # Excepciones de dominio
    └── UserNotFoundException.java
```

**Reglas:**
- Sin anotaciones de Spring (`@Service`, `@Component`)
- Sin anotaciones de JPA (`@Entity`, `@Table`)
- Sin imports de `org.springframework.*`
- Entidades son POJOs con lógica de negocio

**Ejemplo de entidad de dominio:**

```java
// domain/model/User.java
public class User {
    private final UserId id;
    private final Email email;
    private String passwordHash;
    private UserStatus status;

    // Lógica de negocio en la entidad
    public void activate() {
        if (this.status != UserStatus.PENDING) {
            throw new IllegalStateException("Only pending users can be activated");
        }
        this.status = UserStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }
}
```

---

### 2. Application (Casos de Uso)

Orquesta la lógica de negocio. Depende solo de `domain`.

```
application/
├── port/
│   ├── in/               # Puertos de entrada (lo que expone el módulo)
│   │   └── RegisterUserUseCase.java
│   └── out/              # Puertos de salida (lo que necesita)
│       ├── UserPersistencePort.java
│       └── EmailSenderPort.java
├── usecase/              # Implementación de casos de uso
│   └── RegisterUserUseCaseImpl.java
└── dto/                  # DTOs de aplicación (Commands/Queries)
    ├── RegisterUserCommand.java
    └── UserResult.java
```

**Reglas:**
- No usa anotaciones de Spring; el wiring pertenece a `infrastructure`
- NO importa nada de `infrastructure`
- Define interfaces (ports) para dependencias externas

**Ejemplo de caso de uso:**

```java
// application/usecase/RegisterUserUseCaseImpl.java
@RequiredArgsConstructor
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {
    private final UserPersistencePort userPersistence;
    private final PasswordEncoderPort passwordEncoder;
    private final EmailSenderPort emailSender;

    @Override
    public UserResult execute(RegisterUserCommand command) {
        // Validación
        if (userPersistence.existsByEmail(command.email())) {
            throw new EmailAlreadyExistsException(command.email());
        }

        // Crear entidad de dominio
        User user = User.create(
            command.email(),
            passwordEncoder.encode(command.password())
        );

        // Persistir
        userPersistence.save(user);

        // Efecto secundario
        emailSender.sendActivationEmail(user.getEmail());

        return UserResult.from(user);
    }
}
```

---

### 3. Infrastructure (Adaptadores)

Implementa los puertos y conecta con frameworks. Depende de `application` y `domain`.

```
infrastructure/
├── persistence/
│   ├── entity/           # Entidades JPA
│   │   └── UserJpaEntity.java
│   ├── repository/       # Repositorios Spring Data
│   │   └── UserJpaRepository.java
│   ├── adapter/          # Implementación de puertos
│   │   └── UserPersistenceAdapter.java
│   └── mapper/           # Mappers Entity ↔ Domain
│       └── UserMapper.java
├── web/
│   ├── controller/       # REST Controllers
│   │   └── UserController.java
│   └── dto/              # Request/Response DTOs
│       ├── RegisterUserRequest.java
│       └── UserResponse.java
├── email/
│   └── SmtpEmailSender.java
├── security/
│   └── JwtService.java
└── config/
    └── SecurityConfig.java
```

**Reglas:**
- Aquí van TODAS las anotaciones de frameworks
- Implementa los puertos definidos en `application`
- Controllers mapean entre DTOs web y DTOs de aplicación

**Ejemplo de adaptador de persistencia:**

```java
// infrastructure/persistence/adapter/UserPersistenceAdapter.java
@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserPersistencePort {
    private final UserJpaRepository jpaRepository;
    private final UserMapper mapper;

    @Override
    public void save(User user) {
        UserJpaEntity entity = mapper.toEntity(user);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email)
            .map(mapper::toDomain);
    }
}
```

**Ejemplo de controller:**

```java
// infrastructure/web/controller/UserController.java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final RegisterUserUseCase registerUser;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterUserRequest request) {
        // Mapear Request → Command
        RegisterUserCommand command = new RegisterUserCommand(
            request.email(),
            request.password()
        );

        // Ejecutar caso de uso
        UserResult result = registerUser.execute(command);

        // Mapear Result → Response
        return ResponseEntity.ok(UserResponse.from(result));
    }
}
```

---

## Regla de Dependencias

```
┌─────────────────────────────────────────────┐
│             INFRASTRUCTURE                   │
│  (Spring, JPA, HTTP, External Services)     │
├─────────────────────────────────────────────┤
│              APPLICATION                     │
│  (Use Cases, Ports, DTOs)                   │
├─────────────────────────────────────────────┤
│               DOMAIN                         │
│  (Entities, Value Objects, Business Rules)  │
└─────────────────────────────────────────────┘
         ▲
         │  Las dependencias apuntan
         │  HACIA ADENTRO (hacia domain)
```

---

## Enforcement con ArchUnit

Cada módulo DEBE tener estos tests:

```java
@AnalyzeClasses(packages = "com.menta.auth")
class ArchitectureTest {

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

    @ArchTest
    static final ArchRule domain_should_not_use_spring =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..");

    @ArchTest
    static final ArchRule domain_should_not_use_jpa =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..");
}
```

---

## Comunicación entre Módulos

Los módulos del único JAR API se comunican mediante **interfaces Java**. Nunca
usan HTTP, RabbitMQ, service discovery, circuit breakers ni API keys M2M entre
sí. Los contratos que realmente necesitan varios módulos pueden vivir en
`api:shared`; un contrato específico debe permanecer en el módulo propietario.

```java
// api/shared/src/main/java/com/menta/shared/application/port/UserQueryPort.java
public interface UserQueryPort {
    Optional<UserInfo> findById(Long id);
    Optional<UserInfo> findByEmail(String email);
}

// api/auth/src/main/java/com/menta/auth/infrastructure/adapter/UserQueryAdapter.java
@Component
public class UserQueryAdapter implements UserQueryPort {
    // Implementación...
}

// api/billing/src/main/java/com/menta/billing/application/usecase/CreateSubscriptionUseCase.java
@RequiredArgsConstructor
public class CreateSubscriptionUseCase {
    private final UserQueryPort userQuery; // Inyectado desde auth

    public void execute(CreateSubscriptionCommand cmd) {
        UserInfo user = userQuery.findById(cmd.userId())
            .orElseThrow(() -> new UserNotFoundException(cmd.userId()));
        // ...
    }
}
```

### Límites inter-módulo

- Un módulo no importa entidades, repositorios ni adaptadores de otro módulo.
- Solo `api:app` conoce todos los módulos para realizar la composición y
  orquestar casos de uso cross-module mediante contratos neutrales de `shared`.
- Los módulos de negocio pueden depender de contratos mínimos de `api:shared`.
- `shared` no es un cajón de utilidades: no aloja reglas específicas de Auth,
  Virtual, Physical o Billing.
- El BFF está fuera del JAR API y lo consume por HTTP. La administración web es
  una interfaz del BFF, no un módulo backend adicional.

ArchUnit debe verificar tanto capas como fronteras. Además de las reglas
anteriores, se requieren reglas equivalentes a:

```java
@ArchTest
static final ArchRule no_module_accesses_foreign_infrastructure =
    noClasses()
        .that().resideInAPackage("com.menta.billing..")
        .should().dependOnClassesThat()
        .resideInAnyPackage(
            "com.menta.auth.infrastructure..",
            "com.menta.virtual.infrastructure..",
            "com.menta.physical.infrastructure.."
        );

@ArchTest
static final ArchRule controllers_do_not_access_repositories =
    noClasses()
        .that().resideInAPackage("..infrastructure.web.controller..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..repository..", "..persistence..repository..");
```

Cada módulo debe declarar la variante simétrica que excluya su propia
infraestructura y prohíba la infraestructura ajena. Ver también
[25-ARCHITECTURE-RULES.md](./25-ARCHITECTURE-RULES.md).

---

## Android: Clean Architecture + MVVM

Android aplica la misma dirección de dependencias con nombres adaptados:

```text
presentation -> domain <- data
```

```text
android/app/src/main/java/com/menta/
├── domain/        # modelos, puertos y casos de uso puros
├── data/          # Retrofit, Room, DTOs, mappers y repositorios
├── presentation/  # ViewModels, estado de UI y pantallas Compose
└── di/            # composición con Hilt
```

**Reglas:**

- `domain` no importa Android SDK, Retrofit, Room, Compose ni Hilt.
- `data` implementa los puertos definidos por `domain`.
- `presentation` depende de casos de uso, nunca de Retrofit/Room directamente.
- ViewModels contienen coordinación y estado de UI, no reglas de negocio.
- Composables reciben estado y emiten eventos; no acceden a red o persistencia.
- `di` ensambla implementaciones sin invertir la dirección de dependencias.

---

## Checklist para Nuevas Features

- [ ] Entidad de dominio es POJO puro (sin @Entity, sin @Service)
- [ ] Caso de uso define sus puertos de entrada y salida
- [ ] Controller solo mapea y delega al caso de uso
- [ ] Tests de dominio no necesitan Spring context
- [ ] Colaboración inter-módulo usa interfaces Java, no transporte de red
- [ ] No hay imports de infraestructura ajena
- [ ] En Android, ViewModels dependen de casos de uso
- [ ] ArchUnit tests pasan

---

## Referencias

- [ADR-0021: Clean Architecture Obligatoria](./adr/0021-clean-architecture-mandatory.md)
- [02-ARCHITECTURE.md](./02-ARCHITECTURE.md)
- [Clean Architecture - Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

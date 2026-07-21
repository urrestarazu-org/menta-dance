# ADR-0027: Estrategia de Base de Datos MySQL y Migraciones Flyway

**Estado:** Aceptado
**Fecha:** 2026-07-21
**Decisores:** Equipo de desarrollo

## Contexto y Problema

El monolito modular necesita una estrategia clara para:

1. **Estructura de esquemas:** ¿Un esquema único o uno por módulo?
2. **Migraciones:** ¿Cómo organizar scripts Flyway en un monorepo multi-módulo?
3. **Conexiones:** ¿Un datasource o múltiples?
4. **Separación lógica:** ¿Cómo mantener límites entre módulos a nivel de datos?

## Factores Clave

* MVP corre en un único servidor MySQL
* Monolito modular con 4 módulos: Auth, Billing, Virtual, Physical
* Clean Architecture exige que el dominio no conozca detalles de infraestructura
* Simplicidad operativa es prioritaria para MVP
* Debe prepararse para eventual separación de módulos (pero no prematuramente)

## Decisión

### 1. Esquema Único con Prefijos de Tabla

Un solo esquema `menta` con prefijos por módulo:

```
menta
├── auth_users
├── auth_roles
├── auth_refresh_tokens
├── billing_plans
├── billing_subscriptions
├── billing_payments
├── virtual_courses
├── virtual_modules
├── virtual_lessons
├── virtual_progress
├── physical_sessions
├── physical_attendances
└── shared_* (tablas comunes si las hay)
```

**Razones:**
- Simplicidad de gestión (un datasource, un backup)
- Foreign keys entre módulos cuando sea necesario
- Sin overhead de conexiones múltiples

### 2. Flyway en `:api:app`

Las migraciones viven en el módulo ensamblador:

```
api/app/src/main/resources/
└── db/migration/
    ├── V001__create_auth_tables.sql
    ├── V002__create_billing_tables.sql
    ├── V003__create_virtual_tables.sql
    ├── V004__create_physical_tables.sql
    └── V005__add_indexes.sql
```

**Convención de versionado:**

| Rango | Módulo | Ejemplo |
|-------|--------|---------|
| V001-V099 | Auth | `V001__create_users.sql` |
| V100-V199 | Billing | `V100__create_plans.sql` |
| V200-V299 | Virtual | `V200__create_courses.sql` |
| V300-V399 | Physical | `V300__create_sessions.sql` |
| V900-V999 | Cross-module | `V900__add_fk_constraints.sql` |

### 3. Configuración de Datasource

Un único datasource en `:api:app`:

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:3306/menta
    username: ${DB_USER:menta}
    password: ${DB_PASS}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

### 4. Entidades JPA por Módulo

Cada módulo define sus entidades en su paquete de infraestructura:

```
api/auth/src/main/java/com/menta/auth/
└── infrastructure/
    └── persistence/
        ├── entity/
        │   ├── UserJpaEntity.java
        │   └── RefreshTokenJpaEntity.java
        └── repository/
            └── UserJpaRepository.java

api/billing/src/main/java/com/menta/billing/
└── infrastructure/
    └── persistence/
        ├── entity/
        │   └── PlanJpaEntity.java
        └── repository/
            └── PlanJpaRepository.java
```

**Entity scanning en `:api:app`:**

```java
@SpringBootApplication
@EntityScan(basePackages = {
    "com.menta.auth.infrastructure.persistence.entity",
    "com.menta.billing.infrastructure.persistence.entity",
    "com.menta.virtual.infrastructure.persistence.entity",
    "com.menta.physical.infrastructure.persistence.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.menta.auth.infrastructure.persistence.repository",
    "com.menta.billing.infrastructure.persistence.repository",
    "com.menta.virtual.infrastructure.persistence.repository",
    "com.menta.physical.infrastructure.persistence.repository"
})
public class MentaApiApplication { }
```

### 5. Límites entre Módulos

**Permitido:**
- Foreign keys entre tablas de diferentes módulos
- JOINs en queries cuando son necesarios (reports)

**Prohibido:**
- Un módulo accediendo directamente a repositorios de otro módulo
- Queries que violen Clean Architecture (dominio consultando infra de otro módulo)

**Comunicación entre módulos:**
- A través de interfaces de aplicación (ports)
- No a través de acceso directo a tablas

```java
// ❌ Prohibido: Billing accede a UserJpaRepository de Auth
@Service
public class SubscriptionService {
    private final UserJpaRepository userRepo; // NO
}

// ✅ Correcto: Billing usa el port definido en shared
@Service
public class SubscriptionService {
    private final UserQueryPort userQuery; // Port en shared
}
```

## Infraestructura

### Docker Compose (desarrollo)

```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: menta
      MYSQL_USER: menta
      MYSQL_PASSWORD: menta
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    command: >
      --character-set-server=utf8mb4
      --collation-server=utf8mb4_unicode_ci
      --default-authentication-plugin=mysql_native_password

volumes:
  mysql_data:
```

### Testing con Testcontainers

```java
@Testcontainers
@SpringBootTest
class IntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
}
```

## Consecuencias

### Positivas

* Un solo punto de gestión para backups y migraciones
* Foreign keys disponibles para integridad referencial
* Sin complejidad de múltiples datasources
* Testcontainers simple (un contenedor)

### Negativas / Deuda Técnica

* Prefijos de tabla son convención, no enforcement
* Extraer módulo a microservicio requiere migración de datos
* Un módulo lento puede afectar a otros (pool compartido)

### Riesgos y Mitigación

| Riesgo | Mitigación |
|--------|------------|
| Violación de límites modulares | ArchUnit tests, code review |
| Pool exhaustion por módulo | Monitoreo de conexiones, query timeouts |
| Migraciones conflictivas | Rangos de versión por módulo |

## Referencias

* [Flyway Documentation](https://flywaydb.org/documentation/)
* [ADR-0020](./0020-modular-monolith.md) — Monolito modular
* [ADR-0024](./0024-technology-baseline.md) — Technology baseline (MySQL 8.0, Flyway 10.x)
* [22-DATA-MODEL.md](../22-DATA-MODEL.md) — Modelo de datos por módulo

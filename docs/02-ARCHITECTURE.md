# Arquitectura del Proyecto

[← Volver al índice](./README.md)

---

## Visión General

Menta Dance es un **monolito modular** que gestiona una academia de danza con dos
líneas de negocio: cursos virtuales (video on-demand) y clases presenciales
(sesiones con cupo limitado).

```mermaid
flowchart TB
    subgraph Clients["Clientes"]
        Browser["🌐 Browser"]
        Android["📱 Android"]
        QR["📷 Lector QR"]
    end

    subgraph Backend["Backend"]
        BFF["BFF<br/>(Spring Boot)"]
        API["API<br/>(Monolito Modular)"]
    end

    subgraph Data["Persistencia"]
        MySQL[("MySQL 8<br/>schema: menta")]
        Redis[("Redis<br/>locks/blacklist")]
    end

    subgraph External["Externos"]
        MP["Mercado Pago"]
        Bunny["Bunny CDN"]
    end

    Browser -->|HTTPS| BFF
    BFF -->|HTTP interno| API
    Android -->|HTTPS| API
    QR -->|HTTPS| API

    API --> MySQL
    API --> Redis
    API -->|webhooks| MP
    API -->|signed URLs| Bunny

    style API fill:#fff9c4
    style BFF fill:#e3f2fd
```

**Principios clave:**
- El BFF es la única frontera web: el navegador nunca recibe tokens JWT
- Android guarda refresh tokens en Keystore; nunca los loguea
- La API centraliza toda la lógica de negocio en módulos aislados

---

## Estructura del Proyecto

```
menta-dance/
├── api/                          # Backend API (Spring Boot 3)
│   ├── app/                      # :api:app — composición y orquestación
│   ├── shared/                   # :api:shared — contratos entre módulos
│   ├── auth/                     # :api:auth — identidad y sesiones
│   ├── billing/                  # :api:billing — pagos y suscripciones
│   ├── virtual/                  # :api:virtual — cursos online
│   └── physical/                 # :api:physical — clases presenciales
├── bff/                          # Web frontend (Thymeleaf)
├── android/                      # App móvil (Kotlin + Compose)
└── docs/                         # Documentación técnica
```

---

## Módulos y Ownership

```mermaid
flowchart TB
    subgraph API["API (Monolito Modular)"]
        App["api:app<br/>🎯 Orquestador"]

        subgraph Modules["Módulos de Dominio"]
            Auth["🔐 Auth<br/>auth_*"]
            Billing["💰 Billing<br/>billing_*"]
            Virtual["🎥 Virtual<br/>virtual_*"]
            Physical["🏃 Physical<br/>physical_*"]
        end

        Shared["📋 api:shared<br/>Contratos"]
    end

    App --> Auth
    App --> Billing
    App --> Virtual
    App --> Physical

    Auth -.-> Shared
    Billing -.-> Shared
    Virtual -.-> Shared
    Physical -.-> Shared

    style App fill:#fff9c4,stroke:#f9a825
    style Shared fill:#e1bee7,stroke:#8e24aa
```

| Módulo | Es dueño de | Tablas |
|--------|-------------|--------|
| **Auth** | Identidad, roles, sesiones, tokens | `auth_users`, `auth_refresh_tokens`, `auth_outbox` |
| **Billing** | Planes, suscripciones, pagos, quotes | `billing_payments`, `billing_subscriptions`, `billing_outbox` |
| **Virtual** | Cursos online, lecciones, progreso | `virtual_courses`, `virtual_lessons`, `virtual_progress` |
| **Physical** | Clases presenciales, sesiones, cupos, QR | `physical_sessions`, `physical_attendance`, `physical_qr_devices` |

**Reglas de aislamiento:**
- Cada módulo es dueño exclusivo de sus tablas (prefijo `{módulo}_`)
- No se permiten FKs, JOINs ni queries entre módulos
- La comunicación es mediante **puertos Java** definidos en `api:shared`

---

## Clean Architecture

Cada módulo sigue la misma estructura de capas:

```mermaid
flowchart TB
    subgraph Module["Módulo (ej: api:auth)"]
        subgraph Infra["🔧 infrastructure"]
            Controllers["Controllers"]
            JPA["JPA Repositories"]
            Redis["Redis Adapters"]
        end

        subgraph App["⚙️ application"]
            UseCases["Use Cases"]
            Ports["Ports (interfaces)"]
            DTOs["DTOs"]
        end

        subgraph Domain["🎯 domain"]
            Entities["Entities (POJOs)"]
            VOs["Value Objects"]
            Services["Domain Services"]
        end
    end

    Controllers --> UseCases
    JPA -.->|implements| Ports
    Redis -.->|implements| Ports
    UseCases --> Entities
    UseCases --> Ports

    style Domain fill:#c8e6c9,stroke:#2e7d32
    style App fill:#fff3e0,stroke:#ef6c00
    style Infra fill:#e3f2fd,stroke:#1565c0
```

**Regla de dependencia:** `infrastructure → application → domain`

```mermaid
flowchart LR
    I["infrastructure"] --> A["application"] --> D["domain"]
    I -.->|❌| D

    style D fill:#c8e6c9
    style A fill:#fff3e0
    style I fill:#e3f2fd
```

| Capa | Contiene | NO puede tener |
|------|----------|----------------|
| **domain** | Entidades, Value Objects, servicios de dominio | Spring, JPA, Redis, ningún framework |
| **application** | Use cases, puertos, DTOs | Controllers, repositories concretos |
| **infrastructure** | Controllers, JPA, adapters externos | Lógica de negocio |

> Validado con **ArchUnit** — los tests fallan si se viola la regla de dependencia.

---

## Comunicación entre Módulos

Los módulos **nunca** se comunican directamente. Usan puertos definidos en `api:shared`:

```mermaid
flowchart TB
    subgraph App["api:app"]
        Orch["PurchaseOrchestrator"]
    end

    subgraph Shared["api:shared"]
        UserPort["UserQueryPort"]
        CapacityPort["CapacityPort"]
        PaymentPort["PaymentPort"]
    end

    subgraph Auth["api:auth"]
        UserAdapter["UserQueryAdapter"]
    end

    subgraph Billing["api:billing"]
        PaymentAdapter["PaymentAdapter"]
    end

    subgraph Physical["api:physical"]
        CapacityAdapter["CapacityAdapter"]
    end

    Orch --> UserPort
    Orch --> CapacityPort
    Orch --> PaymentPort

    UserAdapter -.->|implements| UserPort
    PaymentAdapter -.->|implements| PaymentPort
    CapacityAdapter -.->|implements| CapacityPort

    style Shared fill:#e1bee7
    style App fill:#fff9c4
```

**Prohibido entre módulos:**
- HTTP interno, WebClient, RestTemplate
- RabbitMQ, Kafka o cualquier broker
- Service discovery o circuit breakers
- JOINs SQL o FKs cruzadas

---

## Catálogo Unificado

El catálogo de cursos es una **proyección de lectura** compuesta por `api:app`:

```mermaid
flowchart LR
    subgraph Clients
        Browser
        Android
    end

    subgraph App["api:app"]
        Catalog["GET /catalog/courses"]
    end

    subgraph Modules
        Virtual["Virtual<br/>cursos online"]
        Physical["Physical<br/>clases presenciales"]
    end

    Browser --> Catalog
    Android --> Catalog
    Catalog --> Virtual
    Catalog --> Physical

    style Catalog fill:#fff9c4
```

- `GET /api/v1/catalog/courses` — lista unificada de cursos
- `GET /api/v1/catalog/courses/{courseId}` — detalle de un curso
- Cada curso tiene modalidad `VIRTUAL` o `PHYSICAL` y UUID único
- `api:app` enruta por modalidad al módulo dueño
- No existe tabla compartida ni JOIN entre módulos

---

## Persistencia

```mermaid
flowchart TB
    subgraph API
        Flyway["Flyway<br/>(api:app)"]
        Hibernate["Hibernate<br/>ddl-auto:validate"]
    end

    subgraph Storage
        MySQL[("MySQL 8<br/>schema: menta")]
        Redis[("Redis<br/>noeviction")]
        Caffeine["Caffeine<br/>(in-memory)"]
    end

    Flyway -->|única autoridad| MySQL
    Hibernate -->|valida, no crea| MySQL
    API --> Redis
    API --> Caffeine

    style MySQL fill:#e8f5e9
    style Redis fill:#ffecb3
    style Caffeine fill:#e3f2fd
```

| Componente | Rol | Política |
|------------|-----|----------|
| **MySQL** | Fuente de verdad | Schema único `menta`, prefijos por módulo |
| **Flyway** | Migraciones | Solo en `api:app`, inmutables, forward-only |
| **Hibernate** | ORM | `ddl-auto:validate` — nunca modifica schema |
| **Redis** | Blacklist, locks, rate limiting | `noeviction` — falla si lleno |
| **Caffeine** | Caché local | Solo datos reconstruibles, nunca seguridad |

**Fail-closed:** Si Redis no está disponible, toda ruta autenticada falla.

---

## Observabilidad

```mermaid
flowchart LR
    subgraph API
        Logback["Logback JSON"]
        OTel["OpenTelemetry"]
    end

    subgraph Monitoring
        Loki["Grafana Loki"]
        Tempo["Grafana Tempo"]
    end

    Logback -->|logs| Loki
    OTel -->|traces| Tempo

    style Loki fill:#fff3e0
    style Tempo fill:#e3f2fd
```

- **Logs:** JSON estructurado con `correlationId` en cada request
- **Trazas:** OpenTelemetry exporta a Grafana Cloud
- **Retención:** Mínimo 90 días para errores, auditoría de pagos e incidentes
- **Seguridad:** Nunca se loguean passwords, tokens, cookies ni datos de tarjeta

---

## Evolución Futura

La separación en microservicios requiere:
1. Un ADR nuevo que justifique la decisión
2. Migración explícita de datos
3. Infraestructura distribuida (service mesh, brokers)

**El MVP no anticipa esta complejidad.** El monolito modular permite escalar
verticalmente y mantener simplicidad operativa.

---

## Referencias

- [Clean Architecture Guide](./27-CLEAN-ARCHITECTURE-GUIDE.md)
- [Module Dependencies](./diagrams/MODULE-DEPENDENCIES.md)
- [C4 Diagrams](./diagrams/C4-SYSTEM.md)
- [ADR-0020: Modular Monolith](./adr/0020-modular-monolith.md)
- [ADR-0021: Clean Architecture Mandatory](./adr/0021-clean-architecture-mandatory.md)

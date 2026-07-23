# Diagrama de Dependencias de Módulos

Relaciones y boundaries entre módulos del monolito modular.

## Dependencias de Alto Nivel

```mermaid
flowchart TB
    subgraph Clients["Clientes"]
        Browser
        Android
        QRReader["QR Reader"]
    end

    subgraph API["API (Monolito Modular)"]
        App["api:app<br/>(Orquestador)"]

        subgraph Modules["Módulos de Dominio"]
            Auth
            Billing
            Virtual
            Physical
        end

        Shared["api:shared<br/>(Contratos)"]
    end

    Browser --> BFF
    BFF --> App
    Android --> App
    QRReader --> App

    App --> Auth
    App --> Billing
    App --> Virtual
    App --> Physical

    Auth --> Shared
    Billing --> Shared
    Virtual --> Shared
    Physical --> Shared

    style App fill:#fff9c4
    style Shared fill:#e1bee7
```

---

## Matriz de Dependencias

### Dependencias Permitidas

```mermaid
flowchart LR
    subgraph allowed["✓ Permitido"]
        direction TB
        A1["Módulo"] --> A2["Shared"]
        A3["api:app"] --> A4["Cualquier módulo"]
        A5["infrastructure"] --> A6["application"]
        A7["application"] --> A8["domain"]
    end

    subgraph forbidden["✗ Prohibido"]
        direction TB
        B1["Módulo A"] -.->|X| B2["Módulo B"]
        B3["domain"] -.->|X| B4["infrastructure"]
        B5["Módulo"] -.->|X| B6["api:app"]
    end

    style allowed fill:#c8e6c9
    style forbidden fill:#ffcdd2
```

### Tabla de Dependencias

| Desde | Puede depender de | No puede depender de |
|-------|-------------------|---------------------|
| `api:app` | Auth, Billing, Virtual, Physical, Shared | - |
| `api:auth` | Shared | Billing, Virtual, Physical, app |
| `api:billing` | Shared | Auth, Virtual, Physical, app |
| `api:virtual` | Shared | Auth, Billing, Physical, app |
| `api:physical` | Shared | Auth, Billing, Virtual, app |
| `api:shared` | - | Ningún módulo |

---

## Comunicación entre Módulos

### Patrón: Puertos en Shared

```mermaid
flowchart TB
    subgraph App["api:app"]
        Orchestrator["PurchaseOrchestrator"]
    end

    subgraph Shared["api:shared"]
        UserPort["UserQueryPort"]
        CapacityPort["CapacityAssignmentPort"]
        PaymentPort["PaymentCompletionPort"]
    end

    subgraph Auth["api:auth"]
        UserAdapter["UserQueryAdapter"]
    end

    subgraph Physical["api:physical"]
        CapacityAdapter["CapacityAdapter"]
    end

    subgraph Billing["api:billing"]
        PaymentAdapter["PaymentAdapter"]
    end

    Orchestrator --> UserPort
    Orchestrator --> CapacityPort
    Orchestrator --> PaymentPort

    UserAdapter -.->|implements| UserPort
    CapacityAdapter -.->|implements| CapacityPort
    PaymentAdapter -.->|implements| PaymentPort

    style Shared fill:#e1bee7
    style App fill:#fff9c4
```

### Ejemplo: Flujo de Compra

El orden correcto es: Payment PENDING → Payment COMPLETED → Purchase PENDING_FULFILLMENT → Purchase ASSIGNED.

```mermaid
sequenceDiagram
    participant App as api:app
    participant Shared as UserQueryPort
    participant Auth as api:auth
    participant Billing as api:billing
    participant Physical as api:physical

    App->>Shared: getUserById(userId)
    Shared->>Auth: [implementación]
    Auth-->>Shared: User
    Shared-->>App: User

    %% Checkout: primero reserva capacidad, luego crea Payment
    App->>Physical: createCapacityHold(projected sessions)
    alt Sin cupo
        Physical-->>App: CAPACITY_UNAVAILABLE
    else Hold creado
        Physical-->>App: HoldCreated
        App->>Billing: initiatePayment(quote)
        Billing-->>App: Payment PENDING

        Note over App: Webhook confirma pago → Payment COMPLETED

        %% Purchase se crea DESPUÉS de Payment COMPLETED
        App->>Billing: createPurchase(paymentId)
        Billing-->>App: Purchase PENDING_FULFILLMENT

        App->>Physical: assignCapacity(userId, sessions)
        Physical-->>App: Assignments

        App->>Billing: completePurchase(purchaseId)
        Billing-->>App: Purchase ASSIGNED
    end
```

**Secuencia**: El checkout crea primero un hold atómico; si no hay cupo no crea
Payment. La Purchase se crea después de que el proveedor confirma el pago
(`Payment → COMPLETED`).

---

## Dependencias por Capa (Clean Architecture)

Cada módulo sigue la misma estructura interna:

```mermaid
flowchart TB
    subgraph Module["Módulo (ej: api:auth)"]
        subgraph Infra["infrastructure"]
            Controllers
            JpaRepos["JPA Repositories"]
            RedisAdapter
        end

        subgraph Application["application"]
            UseCases
            Ports
            DTOs
        end

        subgraph Domain["domain"]
            Entities
            ValueObjects
            DomainServices
        end
    end

    Controllers --> UseCases
    JpaRepos --> Ports
    RedisAdapter --> Ports
    UseCases --> Entities
    UseCases --> Ports
    Ports --> Domain

    style Domain fill:#c8e6c9
    style Application fill:#fff3e0
    style Infra fill:#e3f2fd
```

### Regla de Dependencia

```mermaid
flowchart LR
    I["infrastructure"] --> A["application"] --> D["domain"]

    I -.->|X| D

    style D fill:#c8e6c9
    style A fill:#fff3e0
    style I fill:#e3f2fd
```

**Domain no depende de nada externo**: Sin Spring, sin JPA, sin Redis.

---

## Grafo de Módulos Gradle

```mermaid
flowchart TB
    subgraph Gradle["Módulos Gradle"]
        app[":api:app"]
        shared[":api:shared"]
        auth[":api:auth"]
        billing[":api:billing"]
        virtual[":api:virtual"]
        physical[":api:physical"]
    end

    app --> auth
    app --> billing
    app --> virtual
    app --> physical
    app --> shared

    auth --> shared
    billing --> shared
    virtual --> shared
    physical --> shared

    style app fill:#fff9c4
    style shared fill:#e1bee7
```

### build.gradle.kts (api:app)

```kotlin
dependencies {
    implementation(project(":api:shared"))
    implementation(project(":api:auth"))
    implementation(project(":api:billing"))
    implementation(project(":api:virtual"))
    implementation(project(":api:physical"))
}
```

### build.gradle.kts (api:auth)

```kotlin
dependencies {
    implementation(project(":api:shared"))
    // NO puede depender de billing, virtual, physical, ni app
}
```

---

## Eventos y Outbox

### Patrón Transactional Outbox

El commit de negocio y la inserción en outbox ocurren en la **misma transacción MySQL**.

```mermaid
flowchart TB
    subgraph Module["Módulo (ej: Auth)"]
        UseCase["Use Case"]
        subgraph TX["Misma Transacción MySQL"]
            DB[(Tablas de negocio)]
            Outbox["auth_outbox"]
        end
    end

    subgraph Worker["Outbox Worker (async)"]
        Processor["Event Processor"]
    end

    subgraph External["Sistemas Externos"]
        Redis
    end

    UseCase -->|"1. BEGIN TX"| TX
    UseCase -->|"2. Write business data"| DB
    UseCase -->|"3. Insert event"| Outbox
    UseCase -->|"4. COMMIT"| TX

    Processor -->|"5. Poll pending"| Outbox
    Processor -->|"6. Process"| Redis
    Processor -->|"7. Mark sent"| Outbox

    style TX fill:#e8f5e9,stroke:#2e7d32
    style Outbox fill:#fff3e0
```

**Garantía**: Si el commit falla, tanto el dato de negocio como el evento se revierten. El worker procesa asincrónicamente solo eventos ya confirmados.

### Eventos por Módulo

| Módulo | Eventos en Outbox |
|--------|-------------------|
| Auth | Token revocation, Blacklist sync, Version update |
| Billing | Payment state change |
| Physical | Capacity assigned, Attendance recorded |
| Virtual | Lesson completed, Progress updated |

---

## Validación con ArchUnit

```java
@ArchTest
static final ArchRule modules_should_not_depend_on_each_other =
    noClasses()
        .that().resideInAPackage("..auth..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..billing..", "..virtual..", "..physical..");

@ArchTest
static final ArchRule domain_should_not_depend_on_infrastructure =
    noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..infrastructure..", "org.springframework..");

@ArchTest
static final ArchRule only_app_can_depend_on_all_modules =
    noClasses()
        .that().resideInAnyPackage("..auth..", "..billing..", "..virtual..", "..physical..")
        .should().dependOnClassesThat()
        .resideInAPackage("..app..");
```

---

## Anti-Patrones Detectados por ArchUnit

| Anti-Patrón | Descripción | Regla |
|-------------|-------------|-------|
| Módulo A → Módulo B | Dependencia directa entre módulos | `modules_should_not_depend_on_each_other` |
| Domain → Spring | Entidad con `@Entity` de JPA | `domain_should_be_framework_free` |
| JOIN cross-module | Repository con JOIN a tabla de otro módulo | `repositories_should_not_join_cross_module` |
| FK cross-module | Migración con FK a tabla de otro módulo | Revisión manual de Flyway |
| HTTP interno | RestTemplate/WebClient entre módulos | `no_http_between_modules` |

---

## Resumen de Boundaries

```mermaid
flowchart TB
    subgraph Legend["Leyenda"]
        L1[Orquestador] --- L2[Puede llamar a todos]
        L3[Contratos] --- L4[Define interfaces]
        L5[Módulo] --- L6[Solo usa Shared]
    end

    subgraph System["Sistema"]
        App["api:app 🎯"]
        Shared["api:shared 📋"]

        Auth["api:auth 🔐"]
        Billing["api:billing 💰"]
        Virtual["api:virtual 🎥"]
        Physical["api:physical 🏃"]
    end

    App ==> Auth
    App ==> Billing
    App ==> Virtual
    App ==> Physical

    Auth -.-> Shared
    Billing -.-> Shared
    Virtual -.-> Shared
    Physical -.-> Shared

    style App fill:#fff9c4,stroke:#f9a825
    style Shared fill:#e1bee7,stroke:#8e24aa
    style Auth fill:#e3f2fd,stroke:#1565c0
    style Billing fill:#fff3e0,stroke:#ef6c00
    style Virtual fill:#e8f5e9,stroke:#2e7d32
    style Physical fill:#fce4ec,stroke:#c2185b
```

**Principios clave:**
1. `api:app` orquesta, no tiene lógica de dominio
2. `api:shared` define contratos, no implementaciones
3. Módulos solo conocen `shared`, nunca otros módulos
4. Sin HTTP, JOINs, FKs ni brokers entre módulos

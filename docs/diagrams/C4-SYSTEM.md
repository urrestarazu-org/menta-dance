# Diagramas C4 de Menta Dance

Este documento contiene los diagramas C4 del sistema usando sintaxis Mermaid.

## Nivel 1: Contexto del Sistema

```mermaid
C4Context
    title Sistema Menta Dance - Diagrama de Contexto

    Person(alumno, "Alumno", "Estudiante de danza que toma clases virtuales o presenciales")
    Person(profesor, "Profesor", "Instructor que dicta cursos y gestiona sesiones")
    Person(recepcionista, "Recepcionista", "Personal que registra asistencia manual")
    Person(admin, "Administrador", "Gestiona el sistema completo")

    System(mentaDance, "Menta Dance", "Sistema de gestión de academia de danza con cursos virtuales y clases presenciales")

    System_Ext(mercadoPago, "Mercado Pago", "Procesador de pagos externo")
    System_Ext(bunny, "Bunny CDN", "Streaming de video con URLs firmadas")

    Rel(alumno, mentaDance, "Consulta cursos, paga, toma clases", "HTTPS")
    Rel(profesor, mentaDance, "Gestiona cursos y sesiones", "HTTPS")
    Rel(recepcionista, mentaDance, "Registra asistencia", "HTTPS")
    Rel(admin, mentaDance, "Administra todo", "HTTPS")

    Rel(mentaDance, mercadoPago, "Procesa pagos", "HTTPS/Webhooks")
    Rel(mentaDance, bunny, "URLs firmadas de video", "HTTPS")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

## Nivel 2: Contenedores

```mermaid
C4Container
    title Sistema Menta Dance - Diagrama de Contenedores

    Person(usuario, "Usuario", "Alumno, Profesor, Recepcionista o Admin")

    Container_Boundary(cliente, "Clientes") {
        Container(browser, "Navegador Web", "HTML/Thymeleaf", "Interfaz web para usuarios")
        Container(android, "App Android", "Kotlin/Compose", "Aplicación móvil nativa")
        Container(lectorQR, "Lector QR", "Dispositivo", "Hardware de check-in en recepción")
    }

    Container_Boundary(backend, "Backend") {
        Container(bff, "BFF", "Spring Boot/Thymeleaf", "Backend for Frontend. Gestiona sesiones web, no expone tokens al browser")
        Container(api, "API", "Spring Boot 3", "Monolito modular con Auth, Billing, Virtual y Physical")
    }

    Container_Boundary(datos, "Persistencia") {
        ContainerDb(mysql, "MySQL 8", "Database", "Schema único 'menta' con prefijos por módulo")
        ContainerDb(redis, "Redis", "Cache/Locks", "Blacklist de tokens, rate limiting, locks de concurrencia")
    }

    System_Ext(mercadoPago, "Mercado Pago", "Procesador de pagos")

    Rel(browser, bff, "Usa", "HTTPS")
    Rel(bff, api, "Llama", "HTTP interno")
    Rel(android, api, "Llama", "HTTPS")
    Rel(android, lectorQR, "Muestra QR efímero", "Código QR visual")
    Rel(lectorQR, api, "Envía check-in QR", "HTTPS")

    Rel(api, mysql, "Lee/Escribe", "JDBC")
    Rel(api, redis, "Cache/Locks", "Lettuce")
    Rel(api, mercadoPago, "Pagos", "HTTPS")
    Rel(mercadoPago, api, "Webhooks", "HTTPS")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

## Nivel 3: Componentes (API)

```mermaid
C4Component
    title API Menta Dance - Diagrama de Componentes

    Container_Boundary(api, "API - Monolito Modular") {

        Component(app, "app", "Spring Boot", "Composición y orquestación. Sin lógica de dominio")

        Container_Boundary(modulos, "Módulos de Dominio") {
            Component(auth, "Auth", "Módulo", "Identidad, roles, sesiones, tokens. Dueño de auth_*")
            Component(billing, "Billing", "Módulo", "Planes, suscripciones, pagos, quotes. Dueño de billing_*")
            Component(virtual, "Virtual", "Módulo", "Cursos online, lecciones, progreso. Dueño de virtual_*")
            Component(physical, "Physical", "Módulo", "Clases presenciales, sesiones, cupos, QR. Dueño de physical_*")
        }

        Component(shared, "Shared", "Librería", "Contratos Java, puertos e interfaces entre módulos")
    }

    ContainerDb(mysql, "MySQL", "Database", "Schema 'menta'")
    ContainerDb(redis, "Redis", "Cache", "Tokens, locks")
    System_Ext(mp, "Mercado Pago", "Pagos")

    Rel(app, auth, "Usa puertos")
    Rel(app, billing, "Usa puertos")
    Rel(app, virtual, "Usa puertos")
    Rel(app, physical, "Usa puertos")

    Rel(auth, shared, "Implementa")
    Rel(billing, shared, "Implementa")
    Rel(virtual, shared, "Implementa")
    Rel(physical, shared, "Implementa")

    Rel(auth, mysql, "auth_*")
    Rel(billing, mysql, "billing_*")
    Rel(virtual, mysql, "virtual_*")
    Rel(physical, mysql, "physical_*")

    Rel(auth, redis, "Blacklist/Version")
    Rel(physical, redis, "Check-in locks")
    Rel(billing, mp, "Webhooks")

    UpdateLayoutConfig($c4ShapeInRow="4", $c4BoundaryInRow="2")
```

## Arquitectura Interna de un Módulo

Cada módulo sigue Clean Architecture con tres capas:

```mermaid
flowchart TB
    subgraph infrastructure["Infrastructure"]
        direction TB
        controllers["Controllers<br/>(REST endpoints)"]
        jpa["JPA Repositories<br/>(Adapters)"]
        external["External Services<br/>(MP, Redis)"]
    end

    subgraph application["Application"]
        direction TB
        usecases["Use Cases"]
        ports["Ports<br/>(interfaces)"]
        dtos["DTOs"]
    end

    subgraph domain["Domain"]
        direction TB
        entities["Entities<br/>(POJOs puros)"]
        valueobjects["Value Objects"]
        domainservices["Domain Services"]
    end

    infrastructure --> application
    application --> domain

    style domain fill:#e8f5e9,stroke:#2e7d32
    style application fill:#fff3e0,stroke:#ef6c00
    style infrastructure fill:#e3f2fd,stroke:#1565c0
```

**Regla de dependencia**: `domain ← application ← infrastructure`

- **Domain** no tiene dependencias externas (sin Spring, sin JPA)
- **Application** solo depende de Domain
- **Infrastructure** depende de Application y Domain

## Flujo de Comunicación entre Módulos

> **Nota**: Este es un diagrama simplificado para mostrar la comunicación entre módulos.
> Para el flujo completo con webhooks, holds y manejo de errores, ver [SEQUENCE-DIAGRAMS.md](SEQUENCE-DIAGRAMS.md).

```mermaid
sequenceDiagram
    participant BFF as BFF
    participant App as api:app
    participant Auth as Auth Module
    participant Billing as Billing Module
    participant Physical as Physical Module

    BFF->>App: POST /api/v1/billing/physical/purchases
    App->>Auth: validateToken(jwt)
    Auth-->>App: UserContext

    App->>Physical: createCapacityHold(projected sessions)
    alt Sin cupo
        Physical-->>App: CAPACITY_UNAVAILABLE
    else Hold creado
        Physical-->>App: HoldCreated
        App->>Billing: initiatePayment(quoteId, userId)
        Note over Billing: Valida quote, crea Payment PENDING
        Billing-->>App: PaymentCreated(paymentId)

        Note over App: ... Webhook de MP confirma pago ...

        App->>Billing: onPaymentCompleted(paymentId)
        Billing-->>App: PaymentCompleted

        App->>Billing: createPurchase(paymentId)
        Note over Billing: Purchase PENDING_FULFILLMENT

        App->>Physical: assignCapacity(userId, sessions)
        Physical-->>App: CapacityAssigned

        App->>Billing: completePurchase(purchaseId)
        Billing-->>App: PurchaseAssigned
    end
```

## Notas de Implementación

1. **Sin HTTP interno**: Los módulos se comunican mediante interfaces Java, no llamadas HTTP.
2. **Sin JOINs entre módulos**: Cada módulo solo accede a sus propias tablas (`auth_*`, `billing_*`, etc.).
3. **Orquestación en app**: `api:app` coordina flujos multi-módulo usando puertos de `shared`.
4. **MySQL como fuente de verdad**: Redis es caché y locks; ante falla, se cierra el acceso.

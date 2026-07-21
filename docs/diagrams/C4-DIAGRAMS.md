# Diagramas C4 - Arquitectura Menta Dance

[← Volver al índice](../README.md)

Estos diagramas representan la arquitectura vigente definida por
[ADR-0019](../adr/0019-monorepo-structure.md),
[ADR-0020](../adr/0020-modular-monolith.md) y
[ADR-0021](../adr/0021-clean-architecture-mandatory.md).

## Level 1: Contexto del Sistema

```mermaid
flowchart LR
    alumno["Alumno"]
    admin["Administrador / Recepción"]
    android["App Android"]
    system["Menta Dance"]
    mp["Mercado Pago"]
    video["Bunny.net"]
    calendar["Google Calendar"]
    fcm["Firebase FCM"]
    smtp["SMTP"]

    alumno -->|Web| system
    admin -->|Panel web| system
    android -->|HTTPS| system
    system --> mp
    system --> video
    system --> calendar
    system --> fcm
    system --> smtp
```

| Actor o sistema | Relación vigente |
|-----------------|------------------|
| Alumno | Usa la web servida por el BFF o la app Android |
| Administrador / recepción | Usa una página web servida por el BFF |
| Sistemas externos | Son adaptadores de infraestructura del módulo propietario |

## Level 2: Contenedores

```mermaid
flowchart TB
    subgraph clients["Clientes"]
        browser["Navegador web"]
        admin_browser["Navegador - Panel de administración"]
        android["App Android\nKotlin + Compose"]
        qr["Lector QR"]
    end

    proxy["Reverse proxy / TLS\nOpcional en el borde"]
    bff["BFF Web - 1 JAR\nSpring Boot + Thymeleaf\nPuerto 8080"]
    api["API - 1 JAR\nSpring Boot\nPuerto 8081"]
    mysql[("MySQL 8\nSchemas lógicos por módulo")]
    redis[("Redis\nCache / sesiones cuando aplique")]
    external["Servicios externos\nMP, video, calendario, FCM, SMTP"]

    browser -->|HTTPS| proxy
    admin_browser -->|HTTPS| proxy
    proxy -->|Web| bff
    bff -->|HTTP REST| api
    android -->|HTTPS REST| proxy
    qr -->|HTTPS REST| proxy
    proxy -->|/api/*| api
    api -->|JPA / JDBC| mysql
    api -->|Cache si aplica| redis
    bff -->|Sesión si aplica| redis
    api -->|HTTPS / protocolos externos| external
```

### Responsabilidades

| Contenedor | Responsabilidad | Regla de comunicación |
|------------|-----------------|-----------------------|
| BFF | SSR, sesión web, ViewModels y adaptación para la UI | Consume únicamente la API por HTTP; no contiene reglas de negocio |
| API | Casos de uso y reglas de Auth, Virtual, Physical y Billing | Un JAR; módulos internos mediante interfaces Java |
| Android | Cliente móvil con Clean Architecture + MVVM | Consume la API pública por HTTPS |
| MySQL | Persistencia con ownership lógico por módulo | Sin consultas directas entre módulos |
| Reverse proxy | TLS y routing perimetral | No es service discovery ni comunica módulos |

## Level 3: Componentes del JAR API

```mermaid
flowchart TB
    subgraph api["API - monolito modular"]
        app[":api:app\nComposition root + orquestación cross-module"]
        entry["Adaptadores de entrada HTTP\nControllers / Security"]
        shared[":api:shared\nContratos neutrales de colaboración"]
        auth[":api:auth\nIdentidad y autenticación"]
        virtual[":api:virtual\nContenido y progreso"]
        physical[":api:physical\nClases y asistencia"]
        billing[":api:billing\nPlanes, precios, cotizaciones,\ncompras y pagos"]

        app -. "ensambla" .-> entry
        app -. "ensambla" .-> auth
        app -. "ensambla" .-> virtual
        app -. "ensambla" .-> physical
        app -. "ensambla" .-> billing
        entry --> auth
        entry --> virtual
        entry --> physical
        entry --> billing
        auth --> shared
        virtual --> shared
        physical --> shared
        billing --> shared

        app -. "orquesta vía shared" .-> physical
        app -. "orquesta vía shared" .-> billing
        app -. "orquesta vía shared" .-> auth
    end

```

Las flechas punteadas son llamadas en proceso mediante interfaces Java. No
representan HTTP, mensajería ni autenticación M2M. La dirección concreta depende
del caso de uso y debe respetar el ownership del contrato.

### Responsabilidades, puertos y adaptadores por módulo

| Módulo | Responsabilidad | Puertos publicados | Puertos consumidos | Adaptadores propietarios |
|--------|-----------------|--------------------|-------------------|--------------------------|
| `shared` | Contratos neutrales e inmutables de colaboración | Comandos, resultados e IDs; sin entidades ni repositorios | Ninguno | Ninguno |
| `auth` | Identidad y autenticación | Consultas de identidad y capacidades de autenticación | Persistencia, email y seguridad | JPA, JWT y SMTP |
| `virtual` | Contenido online y progreso | Consultas/comandos de contenido y progreso | Acceso financiero de Billing y video | JPA y Bunny.net |
| `physical` | Cursos recurrentes, profesor, sesiones, capacidad, holds técnicos, asignaciones y asistencia | Calendario y asignación idempotente de cupo | Solo infraestructura propia | JPA, Google Calendar y FCM |
| `billing` | Planes, precios, cotizaciones, snapshots, compras y pagos | Cotización y estado financiero | Solo infraestructura propia | JPA, Mercado Pago, archivos y SMTP |
| `app` | Arranque, composición y orquestación cross-module | Casos coordinados de compra | Contratos neutrales de `shared` implementados por los módulos | Configuración de Spring únicamente |

Un puerto consumido que representa una capacidad de otro módulo se resuelve en
proceso mediante una interfaz Java. Los puertos de infraestructura se
implementan dentro del módulo propietario; `api:app` no adquiere ownership de
persistencia ni integraciones externas.

### Estructura interna de cada módulo API

```mermaid
flowchart LR
    infra["infrastructure\nControllers, JPA, adaptadores"] --> app["application\nCasos de uso y puertos"]
    app --> domain["domain\nReglas y modelos puros"]
```

ArchUnit verifica la dirección de capas y que ningún módulo acceda a la
infraestructura de otro. Ver
[Reglas de Arquitectura](../25-ARCHITECTURE-RULES.md).

## Componentes del BFF

```mermaid
flowchart LR
    web["Controllers web\nAlumno + Administración"] --> usecases["Casos de uso de presentación"]
    usecases --> client["Cliente HTTP de la API"]
    usecases --> views["ViewModels"]
    views --> thymeleaf["Templates Thymeleaf"]
    client -->|HTTP| api["API - 1 JAR"]
```

El BFF puede agregar respuestas para una vista, pero precios, descuentos,
combos, permisos y estados financieros se calculan en el módulo propietario de
la API.

## Android: Clean Architecture + MVVM

```mermaid
flowchart LR
    compose["Compose UI"] --> vm["ViewModel"]
    vm --> usecase["Domain / Use Cases"]
    data["Data\nRepositories + Retrofit/Room"] --> usecase
    data -->|HTTPS| api["API - 1 JAR"]
```

`domain` no depende del Android SDK. `presentation` no accede directamente a
Retrofit o Room.

## Deployment MVP

```mermaid
flowchart TB
    internet["Internet"] --> proxy["Reverse proxy / TLS"]

    subgraph host["Host único MVP"]
        proxy --> bff["BFF Web - 1 JAR\n:8080"]
        proxy --> api["API modular - 1 JAR\n:8081"]
        bff -->|HTTP| api
        api --> mysql[("MySQL 8")]
        bff --> redis[("Redis, si aplica")]
        api --> redis
    end

    api --> external["Servicios externos"]
```

| Unidad desplegable | Cantidad MVP | Contenido |
|--------------------|--------------|-----------|
| API | 1 | `shared`, `auth`, `virtual`, `physical`, `billing`, `app` |
| BFF | 1 | Web de alumnos y panel de administración |
| Android | Distribuida | Aplicación cliente |

No se despliegan procesos separados por módulo. RabbitMQ, service discovery,
circuit breakers y API keys M2M no forman parte de la comunicación interna.

## Secuencias arquitectónicas de referencia

### Web y panel de administración

```mermaid
sequenceDiagram
    actor U as Usuario o administrador
    participant BFF as BFF Web
    participant API as API - 1 JAR
    participant M as Módulo propietario

    U->>BFF: Solicitud web
    BFF->>API: HTTP REST
    API->>M: Invocación de puerto Java
    M-->>API: Resultado del caso de uso
    API-->>BFF: DTO HTTP
    BFF-->>U: HTML
```

### Colaboración entre módulos

```mermaid
sequenceDiagram
    participant A as api:app Orchestrator
    participant P as Puerto Java público
    participant B as Módulo propietario

    A->>P: Invocar operación
    P->>B: Ejecutar caso de uso en proceso
    B-->>A: Resultado tipado
    Note over A,B: Sin HTTP, broker ni credenciales M2M
```

### Billing como owner financiero

```mermaid
sequenceDiagram
    actor U as Usuario
    participant BFF as BFF Web
    participant API as API - 1 JAR
    participant App as api:app Orchestrator
    participant Billing as Módulo Billing
    participant Physical as Módulo Physical

    U->>BFF: Cotizar mensualidad o sesión individual
    BFF->>API: HTTP REST
    API->>App: Solicitar cotización o iniciar pago
    App->>Billing: Consultar pricing / persistir quote
    App->>Physical: Consultar sesiones y capacidad
    Physical-->>App: Sesiones programadas / capacidad
    App->>Billing: Calcular precio y guardar snapshot
    App->>Physical: Crear o convertir CapacityHold
    Physical-->>App: Cupos asignados y hold convertido
    App->>Billing: Completar Payment y Purchase
    App-->>API: Cotización o compra confirmada
    API-->>BFF: Respuesta
    BFF-->>U: Vista renderizada
```

Los detalles funcionales de endpoints, QR, refresh tokens y proveedores se
mantienen en sus documentos específicos y no se redefinen aquí. Para clases
presenciales, véase
[Pagos de Clases Presenciales](../28-PHYSICAL-CLASS-PAYMENTS.md). Billing conserva
el ownership de precios y pagos; Physical conserva calendario y capacidad.

[Siguiente: ADRs →](../10-ADRS.md)

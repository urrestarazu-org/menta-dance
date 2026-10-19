# Menta Dance

Sistema de gestión integral para academia de danza con dos líneas de negocio: cursos virtuales (video on-demand) y clases presenciales (sesiones con cupo limitado).

> **Trabajo de Fin de Máster** — Máster de Desarrollo con IA (BIG School)

---

## Descripción General

**Menta Dance** es una plataforma que digitaliza la operación completa de una academia de danza, permitiendo:

- **Cursos Virtuales**: Catálogo de cursos online con módulos, lecciones y videos bajo demanda. Los alumnos pueden suscribirse a planes mensuales para acceder al contenido premium.

- **Clases Presenciales**: Gestión de clases físicas con horarios, cupos limitados y sistema de check-in mediante código QR. Los alumnos compran paquetes de clases y registran su asistencia al llegar.

- **Gestión Unificada**: Panel administrativo para profesores y administradores donde gestionar cursos, sesiones, alumnos, pagos y reportes.

### Problema que Resuelve

Las academias de danza suelen operar con herramientas fragmentadas: WhatsApp para comunicación, planillas Excel para asistencia, transferencias manuales para cobros. Menta Dance unifica todo en una plataforma moderna que:

1. Automatiza el control de cupos y asistencia
2. Procesa pagos online con Mercado Pago
3. Entrega contenido virtual con streaming seguro
4. Proporciona métricas de negocio en tiempo real

---

## Stack Tecnológico

### Backend (API)

| Tecnología | Versión | Propósito |
|------------|---------|-----------|
| Java | 21 (LTS) | Lenguaje principal |
| Spring Boot | 3.x | Framework de aplicación |
| Gradle | 8.x (Kotlin DSL) | Build system |
| MySQL | 8.0 | Base de datos principal |
| Redis | 7.x | Cache, locks y blacklist de tokens |
| Flyway | 10.x | Migraciones de base de datos |
| JUnit 5 | 5.x | Testing unitario |
| Testcontainers | 1.x | Testing de integración |
| ArchUnit | 1.x | Validación de arquitectura |

### Frontend Web (BFF)

| Tecnología | Propósito |
|------------|-----------|
| Spring Boot | Backend for Frontend |
| Thymeleaf | Templates HTML |
| HTMX | Interactividad sin SPA |
| Tailwind CSS | Estilos |

### Mobile (Android)

| Tecnología | Propósito |
|------------|-----------|
| Kotlin | Lenguaje principal |
| Jetpack Compose | UI declarativa |
| Hilt | Inyección de dependencias |
| Clean Architecture Android | `presentation/domain/data/di` |
| Android Keystore | Almacenamiento seguro de tokens |

### Infraestructura

| Servicio | Propósito |
|----------|-----------|
| Docker Compose | Entorno local |
| GitHub Actions | CI/CD |
| Mercado Pago | Procesamiento de pagos |
| Bunny CDN | Streaming de video |
| Grafana Cloud | Observabilidad (logs y trazas) |

### Arquitectura

El proyecto implementa un **Monolito Modular** con **Clean Architecture**:

```
┌─────────────────────────────────────────────────────────────┐
│                        Clientes                             │
│   Browser ─── BFF ───┐                                      │
│   Android ───────────┼──► API (Monolito Modular)            │
│   Lector QR ─────────┘         │                            │
└────────────────────────────────┼────────────────────────────┘
                                 │
┌────────────────────────────────┼────────────────────────────┐
│                         Módulos                             │
│   ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐          │
│   │  Auth   │ │ Billing │ │ Virtual │ │Physical │          │
│   │ auth_*  │ │billing_*│ │virtual_*│ │physical_│          │
│   └─────────┘ └─────────┘ └─────────┘ └─────────┘          │
│                         │                                   │
│                    api:shared                               │
│                   (contratos)                               │
└────────────────────────────────┼────────────────────────────┘
                                 │
┌────────────────────────────────┼────────────────────────────┐
│                     Persistencia                            │
│            MySQL (schema: menta) + Redis                    │
└─────────────────────────────────────────────────────────────┘
```

Cada módulo sigue la regla de dependencia: `infrastructure → application → domain`

---

## Instalación y Ejecución

### Requisitos Previos

- **JDK 21** (recomendado: Eclipse Temurin o Amazon Corretto)
- **Docker** y **Docker Compose**
- **Node.js 20.x** (para assets web)
- **Android Studio** (para desarrollo móvil)

### 1. Clonar el Repositorio

```bash
git clone https://github.com/urrestarazu-org/menta-dance.git
cd menta-dance
```

### 2. Configurar Variables de Entorno

Crear archivo `.env` en la raíz del proyecto:

```env
# Base de datos
MYSQL_ROOT_PASSWORD=root_secret
MYSQL_DATABASE=menta
MYSQL_USER=menta_app
MYSQL_PASSWORD=app_secret

# Redis
REDIS_PASSWORD=redis_secret

# JWT
JWT_SECRET=your-256-bit-secret-key-here

# Mercado Pago (sandbox)
MP_ACCESS_TOKEN=TEST-xxx
MP_PUBLIC_KEY=TEST-xxx

# Bunny CDN
BUNNY_API_KEY=xxx
BUNNY_LIBRARY_ID=xxx
```

### 3. Iniciar Servicios

**Opción A - Script consolidado (Recomendado):**

```bash
./scripts/dev.sh start
```

Esto levanta:
- **Infraestructura**: MySQL 8, Redis, OpenTelemetry, Loki, Grafana
- **API**: Spring Boot en puerto `8081`
- **BFF**: Frontend web en puerto `8080`

Verificar estado:
```bash
./scripts/dev.sh status
```

Ver logs en tiempo real:
```bash
./scripts/dev.sh logs api   # Logs de API
./scripts/dev.sh logs bff   # Logs de BFF
```

Detener todo:
```bash
./scripts/dev.sh stop
```

**Opción B - Ejecución manual:**

```bash
# Terminal 1: Infraestructura
docker compose up -d

# Terminal 2: API
./gradlew :api:app:bootRun

# Terminal 3: BFF
./gradlew :bff:bootRun
```

### 4. Testing

**Tests unitarios y de integración:**

```bash
# Todos los tests
./gradlew test

# Tests de arquitectura
./gradlew test --tests "*ArchitectureTest"

# Cobertura
./gradlew jacocoTestReport
```

### Colecciones Bruno

Las colecciones HTTP versionadas están en [`bruno/`](bruno/). Abrí esa carpeta
como colección raíz en Bruno y seleccioná el entorno `local`.

- **API - Direct**: pruebas directas contra la API local (`http://localhost:8081`),
  incluyendo Auth y health; Physical y Virtual son placeholders mientras sus
  endpoints no estén implementados.
- **BFF - Session**: flujo de sesión contra el BFF local (`http://localhost:8080`):
  login, dashboard autenticado, logout y verificación posterior al logout.

También pueden ejecutarse con Bruno CLI:

```bash
cd bruno
npx @usebruno/cli run --env local --folder "API - Direct" .
npx @usebruno/cli run --env local --folder "BFF - Session" .
```

Consultá [`bruno/README.md`](bruno/README.md) para el flujo, variables de entorno
y requisitos de cada colección.

---

## Estructura del Proyecto

```
menta-dance/
├── api/                          # Backend API
│   ├── app/                      # :api:app — composición y orquestación
│   ├── shared/                   # :api:shared — contratos entre módulos
│   ├── auth/                     # :api:auth — autenticación y sesiones
│   ├── billing/                  # :api:billing — pagos y suscripciones
│   ├── virtual/                  # :api:virtual — cursos online
│   └── physical/                 # :api:physical — clases presenciales
│
├── bff/                          # Backend for Frontend (Thymeleaf)
│
├── android/                      # Aplicación móvil
│   └── src/main/java/com/menta/android/
│       ├── presentation/         # ViewModels, UI state y pantallas Compose
│       ├── domain/               # Casos de uso, modelos y puertos
│       ├── data/                 # Repositorios y adaptadores
│       └── di/                   # Wiring con Hilt
│
├── docs/                         # Documentación técnica
│   ├── adr/                      # Architecture Decision Records
│   ├── diagrams/                 # Diagramas C4, ERD, secuencias
│   └── user-stories/             # Historias de usuario
│
├── bruno/                        # Colección de requests HTTP
├── docker-compose.yml            # Infraestructura local
└── build.gradle.kts              # Build principal
```

### Módulos del Backend

| Módulo | Responsabilidad | Tablas |
|--------|-----------------|--------|
| **auth** | Registro, login, tokens JWT, roles | `auth_users`, `auth_refresh_tokens` |
| **billing** | Planes, suscripciones, pagos, webhooks | `billing_plans`, `billing_payments` |
| **virtual** | Cursos online, lecciones, progreso | `virtual_courses`, `virtual_lessons` |
| **physical** | Clases presenciales, cupos, check-in | `physical_sessions`, `physical_attendance` |

---

## Funcionalidades Principales

### Para Alumnos

| Funcionalidad | Descripción |
|---------------|-------------|
| **Registro y Login** | Crear cuenta, activar por email, iniciar sesión |
| **Ver Catálogo** | Explorar cursos virtuales y clases presenciales |
| **Suscribirse** | Contratar plan mensual para cursos virtuales |
| **Ver Videos** | Reproducir lecciones con streaming seguro |
| **Seguir Progreso** | Marcar lecciones completadas, retomar donde quedó |
| **Comprar Clases** | Adquirir paquetes de clases presenciales |
| **Check-in QR** | Registrar asistencia escaneando código en recepción |
| **Ver Historial** | Consultar pagos y asistencias |

### Para Profesores

| Funcionalidad | Descripción |
|---------------|-------------|
| **Gestionar Cursos** | Crear, editar, publicar cursos virtuales |
| **Subir Contenido** | Agregar módulos, lecciones y videos |
| **Programar Sesiones** | Definir horarios y cupos de clases presenciales |
| **Ver Asistencia** | Consultar quiénes asistieron a cada sesión |

### Para Administradores

| Funcionalidad | Descripción |
|---------------|-------------|
| **Gestión Completa** | Acceso total a cursos, usuarios y configuración |
| **Verificar Pagos** | Aprobar transferencias manuales |
| **Conciliación** | Resolver discrepancias con el procesador de pagos |
| **Dispositivos QR** | Registrar y administrar lectores de check-in |
| **Reportes** | Métricas de ingresos, asistencia y crecimiento |

### Para Recepcionistas

| Funcionalidad | Descripción |
|---------------|-------------|
| **Check-in Manual** | Registrar asistencia cuando el QR falla |
| **Consultar Sesiones** | Ver quiénes están inscritos en cada clase |

---

## Datos Demo y Credenciales de Prueba

El perfil `demo` siembra, al arrancar la API, usuarios por rol, catálogo, sesiones, planes y una
suscripción de prueba, para recorrer la aplicación con datos realistas. Solo para desarrollo local:

```bash
./gradlew :api:app:bootRun --args='--spring.profiles.active=demo'
```

| Rol | Email | Contraseña por defecto | Variable de entorno |
|-----|-------|------------------------|---------------------|
| Admin | `admin@menta.dance` | `Admin123!` | `DEMO_ADMIN_PASSWORD` |
| Profesor | `profesor@menta.dance` | `Prof123!` | `DEMO_INSTRUCTOR_PASSWORD` |
| Alumno | `alumno@menta.dance` | `Alumno123!` | `DEMO_STUDENT_PASSWORD` |
| Recepcionista | `recepcion@menta.dance` | `Recep123!` | `DEMO_RECEPTIONIST_PASSWORD` |

Otras variables del perfil (definidas en `api/app/src/main/resources/application-demo.yml`):

- `DEMO_QR_READER_SECRET`: secreto del lector QR demo. Sin valor, el lector no se siembra (se registra un
  WARN y el arranque continúa); si cambia entre arranques, se rota el hash guardado.
- `BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET`: el perfil `demo` trae un valor por defecto solo para demo;
  nunca reutilizarlo en otro entorno.

Datos sembrados: los cursos virtuales "Bachata Sensual" y "Bachata Tradicional" (2 módulos cada uno, con
lecciones gratuitas y restringidas, servidas por el adaptador local de Bunny.net); los cursos presenciales
"Bachata Sensual Presencial" (martes y jueves 19:00, cupo 12) y "Bachata Tradicional Presencial" (sábados
11:00, cupo 15), con sesiones para las próximas 4 semanas y una sesión "rolling" unos 10 minutos en el
futuro; precios presenciales de 25000 y 20000 ARS (recargo individual del 20%); "Plan Virtual"
(30000 ARS, 30 días, ambos cursos virtuales) y "Plan Completo" (60000 ARS, 30 días, los 4 cursos); y un
TRIAL de 90 días sobre "Plan Completo" para el alumno, que además tiene cupo confirmado en la próxima
sesión de Sensual y en la sesión rolling. Los horarios se expresan en UTC.

Comportamiento a tener en cuenta:

- **Guarda de arranque**: combinar `demo` con `prod`, `production` o `staging` (sin distinguir
  mayúsculas) aborta el arranque antes de sembrar nada.
- **Mercado Pago real**: el perfil no reemplaza los adaptadores de Mercado Pago; usar credenciales sandbox.
- **Re-ejecuciones idempotentes**: reiniciar no duplica datos ni restablece contraseñas existentes; la
  sesión rolling se reutiliza o se reprograma, nunca se acumula.
- **Check-in tras reiniciar**: si ya existe asistencia en la sesión rolling, un nuevo check-in devuelve
  la asistencia existente. Para un check-in nuevo hay que recrear la base de datos
  (`docker compose down -v`). Es un comportamiento aceptado.

Ver [docs/26-LOCAL-DEV-SETUP-HOWTO.md](docs/26-LOCAL-DEV-SETUP-HOWTO.md#perfil-demo-datos-de-prueba) para el
paso a paso.

---

## Demo y Recursos

| Recurso | URL |
|---------|-----|
| **Repositorio** | [github.com/urrestarazu-org/menta-dance](https://github.com/urrestarazu-org/menta-dance) |
| **Despliegue** | _Próximamente_ |
| **Presentación (Slides)** | _Próximamente_ |
| **Video Explicativo** | _Próximamente_ |
| **Documentación Técnica** | [docs/README.md](docs/README.md) |

---

## Documentación Adicional

| Documento | Descripción |
|-----------|-------------|
| [Arquitectura](docs/02-ARCHITECTURE.md) | Visión general del sistema |
| [API de Auth](docs/03-AUTH-API.md) | Endpoints de autenticación |
| [API de Virtual](docs/04-VIRTUAL-API.md) | Endpoints de cursos online |
| [API de Physical](docs/05-PHYSICAL-API.md) | Endpoints de clases presenciales |
| [API de Billing](docs/06-BILLING-API.md) | Endpoints de pagos |
| [Modelo de Datos](docs/22-DATA-MODEL.md) | Entidades y relaciones |
| [Plan de Desarrollo](docs/13-DEVELOPMENT-PLAN.md) | Fases de implementación |
| [ADRs](docs/adr/README.md) | Decisiones de arquitectura |

---

## Licencia

Este proyecto fue desarrollado como Trabajo de Fin de Máster y es de uso académico.

---

## Autor

**Alejandro Urrestarazu**

- GitHub: [@urrestarazu-alejandro](https://github.com/urrestarazu-alejandro)
- LinkedIn: [linkedin.com/in/urrestarazu](https://linkedin.com/in/urrestarazu)

---

> Desarrollado como TFM del **Máster de Desarrollo con IA** — [BIG School](https://big.school)

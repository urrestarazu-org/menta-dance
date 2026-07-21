# ADR-0024: Technology Baseline

**Estado:** Aceptado
**Fecha:** 2026-07-21
**Decisores:** Equipo de desarrollo

## Contexto y Problema

El proyecto necesita una referencia canónica de versiones y tecnologías que
sirva como baseline para el scaffold inicial. Actualmente las versiones están
dispersas entre README.md, notas personales y documentos históricos, generando
ambigüedad al momento de implementar.

Este ADR consolida el stack tecnológico autorizado para el MVP.

## Factores Clave

* Java 21 LTS es la versión estable con virtual threads y mejoras de rendimiento
* Spring Boot 3.5.x es la línea actual con soporte a largo plazo
* El equipo tiene experiencia en el ecosistema Spring
* MVP con presupuesto limitado: ~$20-40/mes en infraestructura
* Escala objetivo: 100 RPM (~1.7 RPS), 200 usuarios activos

## Stack Tecnológico Canónico

### Runtime y Framework

| Tecnología | Versión | Justificación |
|------------|---------|---------------|
| **Java** | 21 LTS | Virtual threads, pattern matching, records |
| **Spring Boot** | 3.5.x | Última línea estable, Jakarta EE 10 |
| **Spring Security** | 6.x | OAuth2 resource server, JWT |
| **Spring Data JPA** | 3.x | Hibernate 6, Jakarta Persistence |
| **Spring WebFlux** | 6.x | WebClient para APIs externas |
| **Spring Mail** | 3.x | Envío de emails transaccionales |
| **Spring Actuator** | 3.x | Health checks, métricas, info |

### Build y Gestión de Dependencias

| Tecnología | Versión | Uso |
|------------|---------|-----|
| **Gradle** | 8.x | Build system, Kotlin DSL |
| **Gradle Wrapper** | 8.x | Reproducibilidad |
| **Lombok** | 1.18.x | Reducción de boilerplate |
| **MapStruct** | 1.5.x | Mapeo de DTOs |

### Persistencia

| Tecnología | Versión | Uso |
|------------|---------|-----|
| **MySQL** | 8.0 | Base de datos principal |
| **HikariCP** | 5.x | Connection pooling (incluido en Spring Boot) |
| **Flyway** | 10.x | Migraciones de schema |

### Caché y Estado Compartido

| Tecnología | Versión | Uso |
|------------|---------|-----|
| **Caffeine** | 3.x | Caché local en memoria (cursos, planes) |
| **Redis** | 7.x standalone | JWT blacklist, rate limiting, locks distribuidos |

> Ver [ADR-0026](./0026-redis-caffeine-strategy.md) para detalles de uso.

### Integraciones Externas

| Servicio | SDK/Versión | Uso |
|----------|-------------|-----|
| **Mercado Pago** | sdk-java 2.5.x | Procesamiento de pagos |
| **Bunny.net CDN** | bunny-sdk 0.0.x | Streaming de videos |
| **Google Calendar API** | 2.5.x | Sincronización de horarios |
| **Hashids** | 1.0.x | Ofuscación de IDs públicos |
| **OpenHTMLtoPDF** | 1.0.x | Generación de PDFs (recibos) |
| **libphonenumber** | 8.13.x | Validación de teléfonos |

### Frontend (BFF)

| Tecnología | Versión | Uso |
|------------|---------|-----|
| **Thymeleaf** | 3.1.x | Motor de plantillas server-side |
| **Tailwind CSS** | 3.4.x | Estilos utilitarios |
| **Alpine.js** | 3.x | Interactividad ligera |
| **PostCSS** | 8.x | Procesamiento CSS |
| **Autoprefixer** | 10.x | Compatibilidad de navegadores |
| **Node.js** | 20 LTS | Build de assets (solo desarrollo) |

### Testing

| Herramienta | Versión | Uso |
|-------------|---------|-----|
| **JUnit 5** | 5.10.x | Tests unitarios e integración |
| **Mockito** | 5.x | Mocking |
| **AssertJ** | 3.x | Assertions fluidas |
| **ArchUnit** | 1.x | Tests de arquitectura |
| **Testcontainers** | 1.19.x | MySQL real en tests de integración |
| **H2** | - | **NO USAR** — Testcontainers MySQL obligatorio |
| **Spring Boot Test** | 3.5.x | @SpringBootTest, test slices |
| **Playwright** | 1.x | Tests E2E del BFF |
| **Gatling** | 3.x (Scala 2.13) | Tests de carga |

### Calidad de Código

| Herramienta | Versión | Uso |
|-------------|---------|-----|
| **JaCoCo** | 0.8.x | Cobertura de código |
| **Checkstyle** | 10.x | Estilo de código (Google style) |
| **SonarCloud** | - | Análisis estático continuo |
| **Trivy** | latest | Escaneo de vulnerabilidades |

### Infraestructura y DevOps

| Tecnología | Versión | Uso |
|------------|---------|-----|
| **Docker** | 24.x | Contenedores |
| **Podman** | 4.x | Alternativa a Docker (compatible) |
| **Nginx** | 1.25.x | Reverse proxy, SSL termination |
| **GitHub Actions** | - | CI/CD |
| **Google Analytics 4** | - | Analytics web |

## Estructura del Proyecto

```
menta-dance/
├── api/                          # Backend API
│   ├── shared/                   # :api:shared
│   ├── auth/                     # :api:auth
│   ├── virtual/                  # :api:virtual
│   ├── physical/                 # :api:physical
│   ├── billing/                  # :api:billing
│   └── app/                      # :api:app (assembler)
├── bff/                          # :bff (Thymeleaf frontend)
├── android/                      # App móvil (Kotlin + Compose)
├── docs/                         # Documentación
├── gradle/                       # Gradle wrapper
├── build.gradle.kts              # Build raíz
├── settings.gradle.kts           # Módulos Gradle
├── docker-compose.yml            # Desarrollo local
└── .github/workflows/            # CI/CD
```

## Versiones Mínimas de Herramientas de Desarrollo

| Herramienta | Versión mínima |
|-------------|----------------|
| JDK | 21 |
| Gradle | 8.5 |
| Node.js | 20.x LTS |
| Docker/Podman | 24.x / 4.x |
| Git | 2.40 |

## Infraestructura MVP

| Componente | Cantidad | Recursos |
|------------|----------|----------|
| API (:api:app) | 1 instancia | 1-2 GB RAM, 1-2 vCPU |
| BFF (:bff) | 1 instancia | 512 MB RAM, 1 vCPU |
| MySQL | 1 instancia | 1-2 GB RAM, 1 vCPU |
| Redis | 1 standalone | 256 MB RAM |
| Nginx | 1 instancia | 128 MB RAM |

**Costo estimado:** $20-40/mes en VPS (Hetzner, DigitalOcean, o similar).

## Consecuencias

### Positivas

* Stack unificado y versionado para todo el equipo
* Reproducibilidad garantizada con Gradle wrapper y lockfiles
* Tecnologías maduras con amplia documentación
* Costo de infraestructura bajo para MVP

### Negativas / Deuda Técnica

* Node.js requerido solo para build de Tailwind (no runtime)
* Gatling requiere Scala 2.13 (solo para tests de carga)

### Riesgos y Reversibilidad

* **Riesgo Principal:** Actualizaciones de Spring Boot pueden requerir ajustes
* **Plan de Mitigación:** Seguir release notes, actualizar trimestralmente
* **Reversibilidad:** Cambios de versión menores son de bajo impacto

## Referencias

* [Spring Boot 3.5.x Release Notes](https://spring.io/projects/spring-boot)
* [Java 21 Features](https://openjdk.org/projects/jdk/21/)
* Complementa a: [ADR-0019](./0019-monorepo-structure.md), [ADR-0020](./0020-modular-monolith.md)

# Menta Dance - Documentación

**Fecha:** 2026-07-19
**Versión:** 3.0

---

## Resumen

Menta Dance es un sistema para gestionar una academia de danzas con dos líneas de negocio:

| Academia | Modalidad | Contenido |
|----------|-----------|-----------|
| **Virtual** | Online | Cursos → Módulos → Lecciones (videos) |
| **Física** | Presencial | Cursos, horarios, control de asistencia |

### Stack Tecnológico

| Componente | Tecnología |
|------------|------------|
| **API** | Java 21, Spring Boot 3, Gradle |
| **BFF** | Spring Boot 3, Thymeleaf |
| **Android** | Kotlin, Jetpack Compose |
| **Base de datos** | MySQL 8.0 |

### Escala MVP

- **100 RPM** (~1.7 RPS)
- **200 usuarios** activos

---

## Índice de Documentos

### Estados documentales

- **Vigente:** autoridad compatible con ADR-0019, ADR-0020 y ADR-0021.
- **Histórico:** conserva contexto; no debe usarse como guía de implementación.
- **Pendiente de alineación:** puede contener topología o contratos anteriores;
  **no implementar** sus prescripciones hasta completar la revisión indicada.

### Arquitectura

| Documento | Estado | Descripción |
|-----------|--------|-------------|
| [02-ARCHITECTURE.md](./02-ARCHITECTURE.md) | **Vigente** | Arquitectura del monolito modular |
| [10-ADRS.md](./10-ADRS.md) | **Vigente** | Entrada al índice canónico de ADRs |
| [25-ARCHITECTURE-RULES.md](./25-ARCHITECTURE-RULES.md) | **Vigente** | Límites modulares ejecutables |
| [27-CLEAN-ARCHITECTURE-GUIDE.md](./27-CLEAN-ARCHITECTURE-GUIDE.md) | **Vigente** | Clean Architecture y ArchUnit |
| [adr/](./adr/README.md) | **Vigente + histórico identificado** | Índice canónico y ciclo de vida de decisiones |
| [diagrams/C4-DIAGRAMS.md](./diagrams/C4-DIAGRAMS.md) | **Vigente** | C4 y deployment canónicos |

### Desarrollo

| Documento | Estado | Descripción |
|-----------|--------|-------------|
| [06-MIGRATION-STRATEGY.md](./06-MIGRATION-STRATEGY.md) | **Histórico; pendiente de alineación — no implementar** | Estrategia anterior |
| [08-MVP-SIMPLIFIED.md](./08-MVP-SIMPLIFIED.md) | **Histórico; pendiente de alineación — no implementar** | Diseño MVP anterior |
| [13-DEVELOPMENT-PLAN.md](./13-DEVELOPMENT-PLAN.md) | **Histórico; pendiente de alineación — no implementar** | Plan anterior |
| [22-DATA-MODEL.md](./22-DATA-MODEL.md) | **Vigente** | Modelo lógico por módulo y pagos presenciales |
| [24-LOCAL-DEV-SETUP.md](./24-LOCAL-DEV-SETUP.md) | **Pendiente de alineación** | Entorno local |
| [14-TEST-STRATEGY.md](./14-TEST-STRATEGY.md) | **Vigente** | Estrategia de testing (modelo 100/80/0) |
| [16-CICD-PIPELINE.md](./16-CICD-PIPELINE.md) | **Pendiente de alineación** | Pipeline CI/CD |

### APIs

| Documento | Estado | Descripción |
|-----------|--------|-------------|
| [03-AUTH-API.md](./03-AUTH-API.md) | **Vigente** | Endpoints de autenticación (módulo Auth) |
| [04-VIRTUAL-API.md](./04-VIRTUAL-API.md) | **Vigente** | Endpoints de cursos online (módulo Virtual) |
| [05-PHYSICAL-API.md](./05-PHYSICAL-API.md) | **Vigente** | Calendario, sesiones, capacidad y asistencia de Physical |
| [06-BILLING-API.md](./06-BILLING-API.md) | **Vigente** | Planes, suscripciones, pagos y cotizaciones |

### Reglas funcionales vigentes

| Documento | Estado | Descripción |
|-----------|--------|-------------|
| [28-PHYSICAL-CLASS-PAYMENTS.md](./28-PHYSICAL-CLASS-PAYMENTS.md) | **Vigente** | Precios, cotizaciones, pagos y cupos de clases presenciales |
| [09-PHYSICAL-PRICING-LOGIC.md](./09-PHYSICAL-PRICING-LOGIC.md) | **Histórico; reemplazado — no implementar** | Formulación inicial, superseded por el documento 28 |

### User Stories

| Documento | Estado | Descripción |
|-----------|--------|-------------|
| [user-stories/](./user-stories/README.md) | **Vigencia mixta** | Billing y Physical alineados; Auth y Virtual pendientes de revisión funcional |

### Operaciones

| Documento | Estado | Descripción |
|-----------|--------|-------------|
| [TRIVY.md](./TRIVY.md) | **Pendiente de alineación** | Escaneo de seguridad |
| [TESTING-GUIDE.md](./TESTING-GUIDE.md) | **Vigente** | Guía práctica de testing (alineada con 14-TEST-STRATEGY) |

Los documentos raíz no listados en este índice se consideran **pendientes de
alineación** por defecto y no pueden contradecir la autoridad vigente.

---

## Decisiones Clave

| Decisión | ADR |
|----------|-----|
| Monorepo | [ADR-0019](./adr/0019-monorepo-structure.md) |
| Monolito modular | [ADR-0020](./adr/0020-modular-monolith.md) |
| Clean Architecture obligatoria | [ADR-0021](./adr/0021-clean-architecture-mandatory.md) |
| JWT para autenticación | [ADR-0001](./adr/0001-jwt-vs-session-auth.md) |
| BFF con Thymeleaf | [ADR-0002](./adr/0002-bff-vs-api-gateway.md) |

---

## Quick Start

```bash
# 1. Levantar MySQL
docker run -d --name menta-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=menta -p 3306:3306 mysql:8.0

# 2. Levantar API
./gradlew :api:app:bootRun

# 3. Levantar BFF
./gradlew :bff:bootRun
```

Ver [24-LOCAL-DEV-SETUP.md](./24-LOCAL-DEV-SETUP.md) para más detalles.

---

## Notas de Versión

**Versión 3.0 (2026-07-19):**
- Arquitectura redefinida: monorepo con monolito modular
- Clean Architecture obligatoria con ArchUnit
- ADRs de arquitectura distribuida marcados como reemplazados (no aplican)
- Documentación simplificada y actualizada

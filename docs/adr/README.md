# Architecture Decision Records (ADRs)

Este es el **índice canónico** de decisiones arquitectónicas de Menta Dance.
Los ADRs se conservan como registro histórico: cuando cambia una decisión se
actualiza su estado o se delimita su vigencia, sin borrar el contexto original.

## Autoridad arquitectónica vigente

| ADR | Título | Estado | Fecha |
|-----|--------|--------|-------|
| 0019 | [Estructura Monorepo](./0019-monorepo-structure.md) | Aceptado | 2026-07-19 |
| 0020 | [Monolito Modular](./0020-modular-monolith.md) | Aceptado | 2026-07-19 |
| 0021 | [Clean Architecture Obligatoria](./0021-clean-architecture-mandatory.md) | Aceptado | 2026-07-19 |

ADR-0019, ADR-0020 y ADR-0021 prevalecen ante cualquier descripción histórica
incompatible. La API es un monolito modular en un JAR; el BFF es un JAR separado
que consume la API por HTTP; dentro de la API los módulos usan interfaces Java.

## Decisiones vigentes con alcance actualizado

| ADR | Título | Estado | Alcance actual |
|-----|--------|--------|----------------|
| 0001 | [Autenticación Stateless con JWT](./0001-jwt-vs-session-auth.md) | Aceptado | Clientes externos y único API |
| 0002 | [Patrón BFF con Thymeleaf](./0002-bff-vs-api-gateway.md) | Aceptado | BFF separado → API por HTTP |
| 0004 | [Separación Lógica de BD](./0004-schemas-separados-vs-db-separadas.md) | Aceptado | Schemas propiedad de módulos |
| 0006 | [Ofuscación con Hashids](./0006-mantener-hashids-vs-uuid.md) | Aceptado | Adaptadores HTTP externos |
| 0007 | [Caché con Redis](./0007-cache-redis-vs-local.md) | Aceptado | Caché/sesiones; no bus interno |
| 0008 | [Versionado por URL](./0008-api-versioning-url-vs-header.md) | Aceptado | API pública única |
| 0009 | [Restricciones de Infraestructura MVP](./0009-restricciones-infraestructura-mvp.md) | Aceptado; topología reemplazada por 0020 | Un host; un JAR API + un JAR BFF |
| 0010 | [Centralización de Pagos](./0010-centralizacion-pagos-billing-api.md) | Aceptado | Ownership del módulo Billing |
| 0013 | [Observabilidad con OTel y Grafana](./0013-observabilidad-otel-grafana-cloud.md) | Aceptado | API, BFF e integraciones externas |
| 0015 | [Rate Limiting con Bucket4j](./0015-rate-limiting-bucket4j.md) | Aceptado | Entradas HTTP, no módulos internos |
| 0017 | [Almacenamiento Local de Comprobantes](./0017-almacenamiento-comprobantes-pago.md) | Aceptado | Módulo Billing |
| 0018 | [CI/CD con GitHub Actions](./0018-cicd-github-actions-gitflow.md) | Aceptado | Monorepo Gradle multi-module |
| 0022 | [Estrategia JWT Simplificada](./0022-jwt-monolith-strategy.md) | Aceptado | HS256 para monolito; reemplaza firma RS256 |
| 0023 | [Resilience4j para Integraciones Externas](./0023-resilience4j-external-integrations.md) | Aceptado | Circuit breaker/retry para Mercado Pago, Bunny, Google Calendar |
| 0024 | [Technology Baseline](./0024-technology-baseline.md) | Aceptado | Stack canónico: Java 21, Spring Boot 3.5.x, MySQL 8, Redis 7, Tailwind 3.4 |
| 0025 | [Estrategia de Tokens de Autenticación](./0025-auth-token-strategy.md) | Aceptado | Access JWT con jti + Refresh opaco; blacklist en Redis |
| 0026 | [Estrategia de Caché Redis vs Caffeine](./0026-redis-caffeine-strategy.md) | Aceptado | Caffeine para lectura local; Redis para estado compartido |
| 0027 | [Estrategia MySQL y Flyway](./0027-mysql-flyway-strategy.md) | Aceptado | Esquema único con prefijos; Flyway en :api:app |

## Decisiones reemplazadas o eliminadas

| ADR | Decisión histórica | Estado | Reemplazo |
|-----|--------------------|--------|-----------|
| 0003 | Event Bus RabbitMQ vs Kafka | Reemplazado; archivo eliminado | [ADR-0020](./0020-modular-monolith.md) |
| 0005 | Sync vs Async User Events | Reemplazado; archivo eliminado | [ADR-0020](./0020-modular-monolith.md) |
| 0011 | Service Discovery Docker DNS | Reemplazado; archivo eliminado | [ADR-0020](./0020-modular-monolith.md) |
| 0012 | Circuit Breaking Resilience4j (entre servicios) | Reemplazado; archivo eliminado | [ADR-0020](./0020-modular-monolith.md); ver [ADR-0023](./0023-resilience4j-external-integrations.md) para integraciones externas |
| 0014 | API Keys M2M | Reemplazado; archivo eliminado | [ADR-0020](./0020-modular-monolith.md) |
| 0016 | [JWT HS256 compartido entre servicios](./0016-validacion-jwt-simetrica.md) | Reemplazado | [ADR-0020](./0020-modular-monolith.md); diseño de firma pendiente |

Las decisiones eliminadas no son dependencias normativas. Sus números se
reservan para no reescribir la historia.

## Cómo crear un ADR

1. Copiar [template.md](./template.md) como `NNNN-titulo-corto.md`.
2. Completar contexto, alternativas, decisión y consecuencias.
3. Relacionar decisiones reemplazadas o complementarias.
4. Actualizar **este índice**; no crear índices paralelos.

## Estados

- **Propuesto:** bajo revisión.
- **Aceptado:** vigente dentro de su alcance declarado.
- **Reemplazado:** otra decisión gobierna ese alcance.
- **Deprecado:** ya no aplica.
- **Rechazado:** no seleccionado.

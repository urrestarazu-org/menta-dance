# Menta Dance — Documentación de Diseño

**Estado:** diseño inicial vigente. La implementación comienza con el scaffold de Fase 0.

## Autoridad de diseño

Menta Dance es un **monolito modular**: un JAR API con módulos Auth, Billing,
Virtual y Physical; un BFF web separado; y una aplicación Android. Hay un único
schema MySQL `menta`, con ownership por prefijos de tabla. Los módulos no se
comunican por HTTP, broker, repositorios, `JOIN` ni claves foráneas entre sí.

## Documentos vigentes

| Área | Documento | Contenido canónico |
|---|---|---|
| Arquitectura | [02-ARCHITECTURE.md](02-ARCHITECTURE.md) | Topología, módulos y límites |
| Decisiones | [adr/README.md](adr/README.md) | Índice de ADRs aceptados |
| Reglas | [25-ARCHITECTURE-RULES.md](25-ARCHITECTURE-RULES.md) | Límites ejecutables con ArchUnit |
| Clean Architecture | [27-CLEAN-ARCHITECTURE-GUIDE.md](27-CLEAN-ARCHITECTURE-GUIDE.md) | Guía de implementación por capas |
| APIs | [03-AUTH-API.md](03-AUTH-API.md), [04-VIRTUAL-API.md](04-VIRTUAL-API.md), [05-PHYSICAL-API.md](05-PHYSICAL-API.md), [06-BILLING-API.md](06-BILLING-API.md) | Contratos HTTP |
| Catálogo | [07-CATALOG-API.md](07-CATALOG-API.md) | Proyección de lectura unificada por API/BFF |
| Datos | [22-DATA-MODEL.md](22-DATA-MODEL.md) | Modelo lógico y ownership |
| Pagos presenciales | [28-PHYSICAL-CLASS-PAYMENTS.md](28-PHYSICAL-CLASS-PAYMENTS.md) | Pricing, cupos y conciliación |
| Calidad y entrega | [14-TEST-STRATEGY.md](14-TEST-STRATEGY.md), [16-CICD-PIPELINE.md](16-CICD-PIPELINE.md), [24-LOCAL-DEV-SETUP.md](24-LOCAL-DEV-SETUP.md) | Scaffold, CI/CD y operación local |
| Operación | [18-SLA-DEFINITION.md](18-SLA-DEFINITION.md), [21-NFR-REQUIREMENTS.md](21-NFR-REQUIREMENTS.md) | Objetivos y restricciones |
| Plan | [13-DEVELOPMENT-PLAN.md](13-DEVELOPMENT-PLAN.md) | Fases de implementación |
| Costos y riesgos | [12-COST-ANALYSIS.md](12-COST-ANALYSIS.md), [15-RISK-MATRIX.md](15-RISK-MATRIX.md) | Supuestos operativos y mitigaciones |
| Historias | [user-stories/README.md](user-stories/README.md) | Backlog funcional |
| Diagramas | [diagrams/](diagrams/) | Diagramas arquitectónicos (ver abajo) |

## Diagramas

| Diagrama | Contenido |
|---|---|
| [C4-SYSTEM.md](diagrams/C4-SYSTEM.md) | Contexto, contenedores y componentes (C4 Model) |
| [SEQUENCE-DIAGRAMS.md](diagrams/SEQUENCE-DIAGRAMS.md) | Flujos de autenticación, pagos, check-in y webhooks |
| [STATE-DIAGRAMS.md](diagrams/STATE-DIAGRAMS.md) | Ciclos de vida de Payment, Purchase, Session, Device |
| [ERD-DIAGRAM.md](diagrams/ERD-DIAGRAM.md) | Modelo entidad-relación por módulo |
| [MODULE-DEPENDENCIES.md](diagrams/MODULE-DEPENDENCIES.md) | Dependencias entre módulos y reglas ArchUnit |

Todos los diagramas usan sintaxis Mermaid y son renderizables en GitHub/GitLab.

Los documentos eliminados durante la consolidación no son autoridad ni deben
restaurarse sin una nueva decisión de diseño.

## Principios no negociables

- MySQL es la fuente de verdad de Auth; Redis debe reflejarla. Si MySQL falla o
  Redis no se reconcilia, `AUTH_DEGRADED` bloquea rutas autenticadas.
- Ningún pago externo se reintenta automáticamente.
- Todas las APIs usan `application/problem+json` (RFC 9457).
- Flyway sólo avanza; las migraciones destructivas se difieren una release.
- CI verde es obligatorio para mergear a `develop` y `master`.

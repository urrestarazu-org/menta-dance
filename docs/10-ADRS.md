# Architecture Decision Records (ADRs)

[← Volver al índice](./README.md)

Este documento mantiene una entrada estable para enlaces históricos. El **índice
canónico y único** de decisiones se encuentra en [adr/README.md](./adr/README.md).

## Autoridad vigente

Las decisiones que definen la topología actual son:

1. [ADR-0019: Estructura Monorepo](./adr/0019-monorepo-structure.md).
2. [ADR-0020: Monolito Modular](./adr/0020-modular-monolith.md).
3. [ADR-0021: Clean Architecture Obligatoria](./adr/0021-clean-architecture-mandatory.md).

Ante una contradicción, estas decisiones más recientes prevalecen. En particular:

- la API se entrega como **un JAR** compuesto por los módulos Gradle `shared`,
  `auth`, `virtual`, `physical`, `billing` y `app`;
- el BFF es otro JAR y consume la API por HTTP;
- los módulos internos colaboran mediante interfaces Java y puertos, nunca por
  HTTP, RabbitMQ, service discovery, circuit breakers ni API keys M2M;
- Android aplica Clean Architecture con MVVM;
- el panel de administración es una página web servida por el BFF.

## Ciclo de vida

Los ADRs históricos se conservan para explicar el contexto original. Una
decisión cuyo alcance fue reemplazado incluye una sección **Vigencia actual** y
no debe interpretarse como instrucción de implementación.

| Estado | Significado |
|--------|-------------|
| **Propuesto** | En discusión |
| **Aceptado** | Vigente dentro del alcance indicado |
| **Reemplazado** | Conservado como historia; otra decisión gobierna el alcance |
| **Deprecado** | Ya no aplica y no tiene reemplazo directo |
| **Rechazado** | Considerado pero no seleccionado |

Para consultar decisiones, estados y relaciones use únicamente
[docs/adr/README.md](./adr/README.md).

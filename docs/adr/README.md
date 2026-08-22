# Architecture Decision Records

## ADRs vigentes

| ADR | Decisión |
|---|---|
| [0013](0013-observabilidad-otel-grafana-cloud.md) | Logback JSON, OpenTelemetry y Grafana/Loki |
| [0018](0018-cicd-github-actions-gitflow.md) | GitHub Actions, Git Flow y releases |
| [0019](0019-monorepo-structure.md) | Monorepo |
| [0020](0020-modular-monolith.md) | Monolito modular |
| [0021](0021-clean-architecture-mandatory.md) | Clean Architecture |
| [0023](0023-resilience4j-external-integrations.md) | Resiliencia externa segura |
| [0024](0024-technology-baseline.md) | Baseline exacto |
| [0025](0025-auth-token-strategy.md) | Tokens, sesión y revocación |
| [0026](0026-redis-caffeine-strategy.md) | Redis/ Caffeine |
| [0027](0027-mysql-flyway-strategy.md) | MySQL y Flyway |
| [0028](0028-physical-capacity-precheck-hold.md) | Precheck de cupo y hold en checkout |
| [0032](0032-activation-delivery-cipher-nonce-policy.md) | Nonce y rotación de clave del activation delivery cipher |
| [0033](0033-activation-rate-limiting-strategy.md) | Rate limiting atómico para registro y reenvío de activación |
| [0034](0034-activation-token-generation-hashing.md) | Generación y hashing del token de activación de cuenta |
| [0035](0035-trusted-client-origin-propagation.md) | Propagación confiable del origen del cliente entre Nginx, BFF y API |
| [0036](0036-android-agp9-tooling-migration.md) | Migración de tooling Android a AGP 9.x |
| [0037](0037-catalog-course-id-routing.md) | Ruteo de courseId a su módulo dueño en el catálogo |
| [0038](0038-payment-webhook-state-machine-and-worker.md) | Máquina de estados de Payment, worker de webhook y puerto a Mercado Pago |
| [0039](0039-post-payment-fulfillment-boundaries.md) | Límites del fulfillment post-pago |

Los ADRs no listados fueron eliminados por contener decisiones incompatibles con
el diseño vigente. Sus números no se reutilizan.

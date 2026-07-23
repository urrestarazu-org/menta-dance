# Plan de Desarrollo

## Regla de secuencia

No se implementan funcionalidades de negocio hasta completar la **Fase 0**. El
primer entregable debe ser ejecutable, repetible y verificable.

## Fase 0 — Scaffold y plataforma

1. Crear monorepo Gradle: `api:shared`, `api:auth`, `api:billing`,
   `api:virtual`, `api:physical`, `api:app`, `bff` y `android:app`.
2. Configurar Java 21, Spring Boot 3.5.14, wrapper, catálogo/version locking,
   Checkstyle, JaCoCo, SonarCloud y ArchUnit.
3. Crear `docker-compose.yml` local para MySQL, Redis y auxiliares; usar usuario
   no-root, Flyway y `ddl-auto:validate`.
4. Configurar GitHub Actions para CI en PR y workflow de release.
5. Agregar Logback JSON, OpenTelemetry, Grafana/Loki y health checks.
6. Validar que `./gradlew check`, pruebas de arquitectura y Compose funcionen.

## Fase 1 — Identidad y límites

Implementar Auth, RBAC, BFF token custody, refresh rotation/reuse detection,
Redis fail-closed, outbox de revocación y dispositivos QR. Cubrir rutas críticas
con tests unitarios, integración y seguridad.

## Fase 2 — Catálogo y cursos virtuales

Implementar catálogo, streaming firmado y progreso por lección/curso; el acceso
se consulta por puerto de Billing.

## Fase 3 — Presencial y pagos

Implementar sesiones/capacidad/asistencia, pricing con snapshots, pagos
idempotentes, inbox/outbox, conciliación administrativa y cupos automáticos.

## Fase 4 — Entrega y operación

Completar E2E, carga, dashboards, runbooks de conciliación, release automática y
rollback de imagen. Cada fase se integra mediante PR, CI verde y Conventional
Commit.

# Requisitos No Funcionales

## Seguridad

- Auth usa access JWT con `jti` y `tokenVersion`, y refresh UUID opaco hasheado.
- Refresh rota en cada uso; reutilización, logout-all y reset/cambio de contraseña
  revocan sesiones e incrementan `token_version` primero en MySQL. MySQL es
  autoridad absoluta; si Redis no refleja la revocación, `AUTH_DEGRADED` bloquea
  login, refresh y tráfico autenticado hasta reparación. Si MySQL no está
  disponible, esos flujos también fallan cerrados.
- Web: el BFF custodia tokens; el navegador nunca los recibe. Android usa
  Keystore/EncryptedSharedPreferences y `X-Refresh-Token`.
- Redis usa `noeviction` y es fail-closed para cualquier ruta autenticada.
- RBAC: `ALUMNO`, `PROFESOR`, `RECEPCIONISTA`, `ADMIN`; los QR readers son
  dispositivos técnicos.
- Todas las APIs devuelven errores RFC 9457 `application/problem+json`.

## Modularidad y datos

- Un schema `menta` con prefijos por módulo y un solo datasource.
- Sin FK, JOIN, repositorio, acceso JPA ni HTTP entre módulos.
- ArchUnit convierte estas reglas en fallos de CI; la comunicación permitida usa
  puertos Java o eventos internos explícitos.
- Flyway es forward-only y Hibernate valida el esquema. Una migración destructiva
  se difiere hasta una release posterior.

## Fiabilidad y operación

- Pagos externos no se reintentan; mismatch, resultado desconocido o fallo de
  proveedor = `PENDING/RECONCILIATION_REQUIRED` y tarea administrativa.
- Webhooks usan inbox idempotente. La recuperación de pagos tras incidente de BD
  es manual y auditable.
- Webhooks validan HMAC timing-safe para `data.id`, `x-request-id` y timestamp,
  persisten primero su inbox durable y luego consultan Mercado Pago. La consulta
  valida referencia, merchant, importe, moneda y estado aprobado; sus fallos se
  retienen para reintento. Redis es sólo una defensa adicional.
- Logback JSON + OpenTelemetry + Grafana/Loki; retención mínima de 90 días.
- Local y CI usan Compose, MySQL no-root, Redis, Flyway, Checkstyle, JaCoCo,
  SonarCloud, ArchUnit, Playwright, Gatling y Trivy según corresponda.

## Entrega

GitHub Actions exige CI verde para merges a `develop`/`master`. Releases SemVer
se publican como JAR y contenedor inmutable en GitHub Packages/GHCR y se
despliegan por digest; un health check fallido restaura el digest anterior sólo
con evidencia de compatibilidad de la aplicación anterior con schema y datos
migrados.

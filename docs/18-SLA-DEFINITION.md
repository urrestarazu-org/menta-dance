# SLA y Operación del MVP

## Alcance

El servicio cubre BFF, API modular, MySQL, Redis, integraciones de pago y
observabilidad. Redis es requisito de seguridad para rutas autenticadas: una
caída bloquea dichas rutas con error controlado.

## Recuperación

| Activo | Objetivo | Estrategia |
|---|---|---|
| Aplicación | rollback tras health check fallido | digest anterior sólo con evidencia de compatibilidad de app anterior, schema migrado y datos nuevos; si no, intervención manual |
| Base de datos | RPO hasta 1 hora | backup/PITR según infraestructura; intervención manual |
| Pagos | sin promesa de RPO 0 | conciliación manual contra proveedor |
| Sesiones/tokens | restablecimiento mediante login | Redis no se restaura como fuente de verdad |

Una falla de Flyway o una recuperación de base de datos requiere intervención
manual. Los pagos se revisan con IDs de proveedor, inbox/outbox, auditoría y
correlation IDs; no se inventa un reintento automático.

## Observabilidad e incidentes

Logback produce JSON estructurado. OpenTelemetry correlaciona BFF, API e
integraciones; Grafana/Loki centraliza consulta y alertas. Errores, auditoría de
pagos e incidentes se retienen al menos 90 días. Se redactan secretos, tokens,
cookies, contraseñas y datos completos de tarjeta.

Todo incidente conserva línea de tiempo, impacto, `correlationId`, causa,
reconciliación necesaria y acciones correctivas.
